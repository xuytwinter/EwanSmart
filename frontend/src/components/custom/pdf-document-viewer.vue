<template>
  <div class="pdf-viewer-shell">
    <div class="pdf-viewer-toolbar">
      <div class="toolbar-copy">
        <span class="viewer-badge">PDF 语义预览</span>
        <span class="viewer-title">{{ fileName || '未命名文档' }}</span>
        <span v-if="anchorText" class="viewer-anchor">{{ anchorText }}</span>
      </div>
      <div class="toolbar-actions">
        <NButton size="tiny" quaternary :disabled="currentPage <= 1" @click="goToPage(currentPage - 1)">
          <template #icon>
            <icon-mdi-chevron-left />
          </template>
        </NButton>
        <span class="toolbar-status">
          <template v-if="singlePagePreviewActive">第 {{ displayCurrentPage }} 页 / 单页预览</template>
          <template v-else>第 {{ displayCurrentPage }} / {{ totalPages || 1 }} 页</template>
        </span>
        <NButton size="tiny" quaternary :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">
          <template #icon>
            <icon-mdi-chevron-right />
          </template>
        </NButton>
        <NButton size="tiny" quaternary :disabled="zoom <= minZoom" @click="zoomOut">
          <template #icon>
            <icon-mdi-magnify-minus-outline />
          </template>
        </NButton>
        <span class="toolbar-status">{{ Math.round(zoom * 100) }}%</span>
        <NButton size="tiny" quaternary :disabled="zoom >= maxZoom" @click="zoomIn">
          <template #icon>
            <icon-mdi-magnify-plus-outline />
          </template>
        </NButton>
        <NButton size="tiny" secondary @click="resetZoom">适应宽度</NButton>
        <NButton size="tiny" secondary @click="openInNewTab">
          <template #icon>
            <icon-mdi-open-in-new />
          </template>
          新窗口
        </NButton>
      </div>
    </div>

    <div class="pdf-viewer-body" :class="{ 'is-single-page': singlePagePreviewActive }">
      <aside v-if="!singlePagePreviewActive" class="page-sidebar">
        <button
          v-for="page in pageSummaries"
          :key="page.pageNumber"
          type="button"
          class="page-nav-item"
          :class="{
            'is-active': page.pageNumber === currentPage,
            'is-target': page.pageNumber === targetPageNumber
          }"
          @click="goToPage(page.pageNumber)"
        >
          <span class="page-nav-number">P{{ displayPageNumber(page.pageNumber) }}</span>
          <span class="page-nav-summary">{{ page.summary || `第 ${page.pageNumber} 页` }}</span>
        </button>
      </aside>

      <div ref="stageRef" class="page-stage">
        <div v-if="documentLoading" class="stage-feedback">
          <NSpin size="large" />
          <span>正在加载 PDF 文档</span>
        </div>
        <div v-else-if="renderError" class="stage-feedback is-error">
          <icon-mdi-alert-circle class="text-24" />
          <span>{{ renderError }}</span>
        </div>
        <div v-else class="page-scroll-shell">
          <div class="page-meta-row">
            <span>第 {{ displayCurrentPage }} 页</span>
            <span v-if="currentPage === targetPageNumber">引用定位页</span>
            <span v-else-if="highlightCount > 0">已匹配到相关文本</span>
            <span v-else>浏览当前页</span>
          </div>

          <div ref="pageShellRef" class="pdf-page-shell">
            <canvas ref="canvasRef" class="pdf-canvas" />
            <div v-if="highlightRects.length" class="pdf-highlight-overlay">
              <div
                v-for="(rect, index) in highlightRects"
                :key="`${index}-${rect.left}-${rect.top}`"
                class="pdf-highlight-rect"
                :style="{
                  left: `${rect.left}px`,
                  top: `${rect.top}px`,
                  width: `${rect.width}px`,
                  height: `${rect.height}px`
                }"
              />
            </div>
            <div ref="textLayerRef" class="pdf-text-layer textLayer" />
            <div v-if="pageRendering" class="page-loading-mask">
              <NSpin size="small" />
              <span>正在渲染页面</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { useResizeObserver } from '@vueuse/core';
import { GlobalWorkerOptions, TextLayer, getDocument } from 'pdfjs-dist';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import type { TextItem } from 'pdfjs-dist/types/src/display/api';
import { NButton, NSpin } from 'naive-ui';
import { getAuthorization } from '@/service/request/shared';
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

GlobalWorkerOptions.workerSrc = workerSrc;

interface Props {
  url: string;
  sourceUrl?: string;
  fileName?: string;
  pageNumber?: number;
  singlePageMode?: boolean;
  sourcePageNumber?: number;
  anchorText?: string;
  searchText?: string;
  visible?: boolean;
}

interface PageSummary {
  pageNumber: number;
  summary: string;
}

interface HighlightRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

interface HighlightFragment {
  div: HTMLElement;
  startRatio: number;
  endRatio: number;
}

const props = defineProps<Props>();

const minZoom = 0.7;
const maxZoom = 2.2;
const pdfRangeChunkSize = 256 * 1024;
const eagerSummaryRadius = 1;

const pdfDocument = shallowRef<PDFDocumentProxy | null>(null);
const documentLoading = ref(false);
const pageRendering = ref(false);
const renderError = ref('');
const totalPages = ref(0);
const currentPage = ref(1);
const zoom = ref(1);
const highlightCount = ref(0);
const highlightRects = ref<HighlightRect[]>([]);
const pageSummaries = ref<PageSummary[]>([]);

const stageRef = ref<HTMLDivElement | null>(null);
const pageShellRef = ref<HTMLDivElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const textLayerRef = ref<HTMLDivElement | null>(null);

const targetPageNumber = computed(() => clampPage(props.pageNumber || 1, totalPages.value || 1));
const matchCandidates = computed(() => buildMatchCandidates(props.searchText || props.anchorText || ''));
const singlePagePreviewActive = computed(() => Boolean(props.singlePageMode && props.sourcePageNumber));
const displayCurrentPage = computed(() => {
  if (singlePagePreviewActive.value) {
    return props.sourcePageNumber || props.pageNumber || 1;
  }
  return currentPage.value;
});

let loadingTask: PDFDocumentLoadingTask | null = null;
let renderTask: RenderTask | null = null;
let textLayerTask: TextLayer | null = null;
let lifecycleToken = 0;
let renderTimer: number | null = null;
let queuedRenderVersion = 0;
let activeRenderVersion = 0;
let activeRenderPromise: Promise<void> | null = null;
let rerenderAfterCurrent = false;
let lastObservedStageWidth = 0;
let lastSuccessfulRenderSignature = '';
let summaryLoadTimer: number | null = null;
let activeSummaryPromise: Promise<void> | null = null;
let summaryQueue: number[] = [];
let summaryLoadedPages = new Set<number>();
let summaryLoadingPages = new Set<number>();

watch(
  () => props.url,
  async url => {
    if (!url) return;
    await loadDocument(url);
  },
  { immediate: true }
);

watch(
  () => props.pageNumber,
  pageNumber => {
    if (!pageNumber || !totalPages.value) return;
    currentPage.value = clampPage(pageNumber, totalPages.value);
  }
);

watch(
  () => props.anchorText,
  () => {
    applyHighlight();
  }
);

watch(
  () => props.searchText,
  () => {
    applyHighlight();
  }
);

watch(
  () => props.visible,
  async visible => {
    if (!visible || !pdfDocument.value) return;
    await nextTick();
    void scheduleRender({ immediate: true });
  }
);

watch(currentPage, () => {
  if (!pdfDocument.value) return;
  void scheduleRender({ immediate: true });
  scheduleSummaryLoading(getPrioritySummaryPages(currentPage.value), { prioritize: true });
});

watch(zoom, () => {
  if (!pdfDocument.value) return;
  void scheduleRender({ immediate: true });
});

useResizeObserver(stageRef, entries => {
  if (!pdfDocument.value || documentLoading.value || props.visible === false) return;

  const observedWidth = Math.round(entries[0]?.contentRect.width || stageRef.value?.clientWidth || 0);
  if (observedWidth <= 0) return;
  if (Math.abs(observedWidth - lastObservedStageWidth) < 2) return;

  lastObservedStageWidth = observedWidth;
  void scheduleRender({ delay: 120 });
});

onBeforeUnmount(() => {
  lifecycleToken++;
  void cleanupPdfState();
});

function clampPage(page: number, maxPage: number) {
  return Math.min(Math.max(page, 1), Math.max(maxPage, 1));
}

function normalizeForMatch(value: string) {
  return value
    .normalize('NFKC')
    .replace(/[…]/g, '')
    .replace(/[“”"「」『』《》]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function buildMatchCandidates(value: string | string[]) {
  const rawValues = Array.isArray(value) ? value : value.split(/\n+/);
  const values = rawValues.map(item => item.trim()).filter(Boolean);
  if (!values.length) return [];

  const candidates = new Set<string>();

  values.forEach(item => {
    const normalizedFull = normalizeForMatch(item);
    if (normalizedFull.length >= 6) {
      candidates.add(normalizedFull);
    }

    const normalizedSource = item.normalize('NFKC').replace(/\s+/g, ' ').trim();
    const quotePattern = /[“"「『《](.+?)[”"」』》]/g;
    for (const match of normalizedSource.matchAll(quotePattern)) {
      const quoted = normalizeForMatch(match[1] || '');
      if (quoted.length >= 4) {
        candidates.add(quoted);
      }
    }

    const withoutCitations = normalizedSource.replace(/(?:\(|（)?来源#\d+:[^)）;；。！？!?]*/g, ' ');
    const segments = withoutCitations
      .split(/[：:；;，,。！？!?、\n\r]/)
      .map(segment => normalizeForMatch(segment))
      .filter(segment => segment.length >= 6);

    segments
      .sort((left, right) => right.length - left.length)
      .forEach(segment => candidates.add(segment));
  });

  return [...candidates];
}

function summarizeText(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

function buildFallbackSummary(pageNumber: number) {
  return `第 ${pageNumber} 页`;
}

function buildSummaryFromItems(items: unknown[]) {
  return summarizeText(
    items
      .filter((item): item is TextItem => typeof item === 'object' && item !== null && 'str' in item)
      .map(item => item.str)
      .join(' ')
  ).slice(0, 80);
}

function updatePageSummary(pageNumber: number, items?: unknown[]) {
  const summary = items ? buildSummaryFromItems(items) : '';
  pageSummaries.value[pageNumber - 1] = {
    pageNumber,
    summary: summary || buildFallbackSummary(pageNumber)
  };
  summaryLoadedPages.add(pageNumber);
}

function getPrioritySummaryPages(centerPage: number) {
  const pages = new Set<number>([centerPage, targetPageNumber.value]);

  for (let offset = 1; offset <= eagerSummaryRadius; offset += 1) {
    pages.add(centerPage - offset);
    pages.add(centerPage + offset);
  }

  return [...pages].filter(pageNumber => pageNumber >= 1 && pageNumber <= totalPages.value);
}

function scheduleSummaryLoading(pageNumbers: number[], options?: { delay?: number; prioritize?: boolean }) {
  if (!pdfDocument.value) return;

  if (pageNumbers.length) {
    const nextPages = pageNumbers.filter(pageNumber => {
      return !summaryLoadedPages.has(pageNumber) && !summaryLoadingPages.has(pageNumber);
    });

    if (!nextPages.length && !summaryQueue.length) return;

    if (nextPages.length) {
      if (options?.prioritize) {
        summaryQueue = [...nextPages, ...summaryQueue.filter(pageNumber => !nextPages.includes(pageNumber))];
      } else {
        const existingPages = new Set(summaryQueue);
        nextPages.forEach(pageNumber => {
          if (!existingPages.has(pageNumber)) {
            summaryQueue.push(pageNumber);
          }
        });
      }
    }
  }

  if (summaryLoadTimer) {
    window.clearTimeout(summaryLoadTimer);
  }

  summaryLoadTimer = window.setTimeout(() => {
    summaryLoadTimer = null;
    void flushSummaryQueue();
  }, options?.delay ?? 0);
}

async function flushSummaryQueue() {
  if (!pdfDocument.value || activeSummaryPromise || !summaryQueue.length) return;

  const summaryPromise = loadQueuedSummaries();
  activeSummaryPromise = summaryPromise;

  try {
    await summaryPromise;
  } finally {
    if (activeSummaryPromise === summaryPromise) {
      activeSummaryPromise = null;
    }

    if (summaryQueue.length) {
      scheduleSummaryLoading([], { delay: 120 });
    }
  }
}

async function loadQueuedSummaries() {
  const documentProxy = pdfDocument.value;
  if (!documentProxy) return;

  while (summaryQueue.length && pdfDocument.value === documentProxy && props.visible !== false) {
    const pageNumber = summaryQueue.shift();
    if (!pageNumber || summaryLoadedPages.has(pageNumber) || summaryLoadingPages.has(pageNumber)) continue;

    summaryLoadingPages.add(pageNumber);

    try {
      const page = await documentProxy.getPage(pageNumber);
      if (pdfDocument.value !== documentProxy) return;

      const textContent = await page.getTextContent();
      if (pdfDocument.value !== documentProxy) return;

      updatePageSummary(pageNumber, textContent.items);
    } catch (error) {
      pageSummaries.value[pageNumber - 1] = {
        pageNumber,
        summary: buildFallbackSummary(pageNumber)
      };
      summaryLoadedPages.add(pageNumber);
    } finally {
      summaryLoadingPages.delete(pageNumber);
    }
  }
}

function goToPage(page: number) {
  currentPage.value = clampPage(page, totalPages.value || 1);
}

function displayPageNumber(pageNumber: number) {
  if (singlePagePreviewActive.value) {
    return props.sourcePageNumber || props.pageNumber || pageNumber;
  }
  return pageNumber;
}

function zoomIn() {
  zoom.value = Math.min(Number((zoom.value + 0.1).toFixed(2)), maxZoom);
}

function zoomOut() {
  zoom.value = Math.max(Number((zoom.value - 0.1).toFixed(2)), minZoom);
}

function resetZoom() {
  zoom.value = 1;
}

function openInNewTab() {
  const targetUrl = props.sourceUrl || props.url;
  if (!targetUrl) return;

  const page = singlePagePreviewActive.value ? (props.sourcePageNumber || props.pageNumber || 1) : currentPage.value;
  window.open(`${targetUrl}#page=${page}`, '_blank', 'noopener,noreferrer');
}

async function loadDocument(url: string) {
  lifecycleToken += 1;
  const currentToken = lifecycleToken;

  documentLoading.value = true;
  renderError.value = '';
  totalPages.value = 0;
  currentPage.value = 1;
  highlightCount.value = 0;
  highlightRects.value = [];
  pageSummaries.value = [];
  lastObservedStageWidth = 0;
  lastSuccessfulRenderSignature = '';

  await cleanupPdfState();

  try {
    const shouldAttachAuthHeaders = !/^https?:\/\//i.test(url) || url.includes('/api/v1/documents/page-preview');
    const authorization = getAuthorization();
    loadingTask = getDocument({
      url,
      withCredentials: false,
      disableAutoFetch: true,
      disableStream: true,
      rangeChunkSize: pdfRangeChunkSize,
      httpHeaders: shouldAttachAuthHeaders && authorization ? { Authorization: authorization } : undefined
    });

    const documentProxy = await loadingTask.promise;
    if (currentToken !== lifecycleToken) {
      await documentProxy.destroy();
      return;
    }

    pdfDocument.value = documentProxy;
    totalPages.value = documentProxy.numPages;
    currentPage.value = targetPageNumber.value;
    pageSummaries.value = Array.from({ length: documentProxy.numPages }, (_, index) => ({
      pageNumber: index + 1,
      summary: buildFallbackSummary(index + 1)
    }));
    summaryLoadedPages = new Set();
    summaryLoadingPages = new Set();
    summaryQueue = [];
    scheduleSummaryLoading(getPrioritySummaryPages(currentPage.value), { prioritize: true });

    await nextTick();
    await waitForStageReady(currentToken);
    if (currentToken === lifecycleToken) {
      documentLoading.value = false;
    }
    await nextTick();
    await forceRender(currentToken);
  } catch (error) {
    if (currentToken !== lifecycleToken) return;
    console.error('[PDF 预览] 加载失败:', error);
    renderError.value = 'PDF 加载失败，请尝试新窗口打开或重新预览。';
  } finally {
    if (currentToken === lifecycleToken) {
      documentLoading.value = false;
    }
  }
}

async function scheduleRender(options?: { immediate?: boolean; delay?: number }) {
  if (!pdfDocument.value || documentLoading.value || props.visible === false) return;

  queuedRenderVersion += 1;
  const renderVersion = queuedRenderVersion;
  const delay = options?.immediate ? 0 : options?.delay ?? 0;

  if (renderTimer) {
    window.clearTimeout(renderTimer);
  }

  renderTimer = window.setTimeout(() => {
    renderTimer = null;
    void flushRenderQueue(renderVersion);
  }, delay);
}

async function forceRender(expectedToken = lifecycleToken) {
  queuedRenderVersion += 1;
  await flushRenderQueue(queuedRenderVersion, expectedToken);
}

async function flushRenderQueue(renderVersion: number, expectedToken = lifecycleToken) {
  if (!pdfDocument.value || documentLoading.value || props.visible === false) return;
  if (expectedToken !== lifecycleToken) return;

  if (activeRenderPromise) {
    rerenderAfterCurrent = true;
    renderTask?.cancel();
    textLayerTask?.cancel();
    return;
  }

  activeRenderVersion = renderVersion;
  pageRendering.value = true;
  renderError.value = '';

  const renderPromise = renderCurrentPage(expectedToken, renderVersion);
  activeRenderPromise = renderPromise;

  try {
    await renderPromise;
  } finally {
    if (activeRenderPromise === renderPromise) {
      activeRenderPromise = null;
    }

    const shouldRenderAgain =
      rerenderAfterCurrent || (queuedRenderVersion > activeRenderVersion && expectedToken === lifecycleToken);

    rerenderAfterCurrent = false;

    if (shouldRenderAgain && pdfDocument.value && !documentLoading.value) {
      await waitForAnimationFrame();
      await flushRenderQueue(queuedRenderVersion, lifecycleToken);
      return;
    }

    if (expectedToken === lifecycleToken) {
      pageRendering.value = false;
    }
  }
}

async function renderCurrentPage(expectedToken = lifecycleToken, renderVersion = queuedRenderVersion) {
  const documentProxy = pdfDocument.value;
  const canvas = canvasRef.value;
  const textLayer = textLayerRef.value;
  let stage = stageRef.value;

  if (!documentProxy || !canvas || !textLayer || !stage) return;

  highlightCount.value = 0;
  highlightRects.value = [];

  try {
    renderTask?.cancel();
    renderTask = null;
    textLayerTask?.cancel();
    textLayerTask = null;

    const page = await documentProxy.getPage(currentPage.value);
    if (expectedToken !== lifecycleToken) return;

    await waitForStageReady(expectedToken);
    stage = stageRef.value;
    if (!stage || expectedToken !== lifecycleToken) return;

    const baseViewport = page.getViewport({ scale: 1 });
    const availableWidth = Math.max(stage.clientWidth - 72, 320);
    const renderSignature = `${expectedToken}:${currentPage.value}:${zoom.value}:${Math.round(availableWidth)}`;

    if (lastSuccessfulRenderSignature === renderSignature && canvas.width > 0 && canvas.height > 0) {
      highlightCount.value = applyHighlight();
      return;
    }

    const fitScale = availableWidth / baseViewport.width;
    const renderScale = fitScale * zoom.value;
    const viewport = page.getViewport({ scale: renderScale });

    const context = canvas.getContext('2d', { alpha: false });
    if (!context) {
      renderError.value = '无法初始化 PDF 画布。';
      return;
    }

    const devicePixelRatio = window.devicePixelRatio || 1;
    canvas.width = Math.floor(viewport.width * devicePixelRatio);
    canvas.height = Math.floor(viewport.height * devicePixelRatio);
    canvas.style.width = `${viewport.width}px`;
    canvas.style.height = `${viewport.height}px`;

    context.setTransform(1, 0, 0, 1, 0, 0);
    context.clearRect(0, 0, canvas.width, canvas.height);

    textLayer.innerHTML = '';
    textLayer.style.width = `${viewport.width}px`;
    textLayer.style.height = `${viewport.height}px`;

    renderTask = page.render({
      canvas,
      canvasContext: context,
      viewport,
      transform: devicePixelRatio === 1 ? undefined : [devicePixelRatio, 0, 0, devicePixelRatio, 0, 0]
    });

    await renderTask.promise;
    if (expectedToken !== lifecycleToken || renderVersion !== activeRenderVersion) return;

    const textContent = await page.getTextContent({
      includeMarkedContent: true
    });
    updatePageSummary(currentPage.value, textContent.items);

    textLayerTask = new TextLayer({
      textContentSource: textContent,
      container: textLayer,
      viewport
    });

    await textLayerTask.render();
    await nextTick();
    if (expectedToken !== lifecycleToken || renderVersion !== activeRenderVersion) return;
    lastSuccessfulRenderSignature = renderSignature;
    highlightCount.value = applyHighlight();
  } catch (error: any) {
    if (isBenignRenderError(error) || expectedToken !== lifecycleToken || renderVersion !== activeRenderVersion) {
      return;
    }
    console.error('[PDF 预览] 页面渲染失败:', error);
    renderError.value = 'PDF 页面渲染失败，请稍后重试。';
  }
}

function applyHighlight() {
  const textLayer = textLayerRef.value;
  if (!textLayerTask || !textLayer) return 0;

  const textDivs = textLayerTask.textDivs;
  textDivs.forEach(div => div.classList.remove('matched-text'));
  highlightRects.value = [];

  const candidates = matchCandidates.value;
  if (!candidates.length) return 0;

  const itemRanges: Array<{ index: number; start: number; end: number }> = [];
  let mergedText = '';

  textLayerTask.textContentItemsStr.forEach((item, index) => {
    const normalized = normalizeForMatch(item);
    if (!normalized) return;

    if (mergedText) {
      mergedText += ' ';
    }

    const start = mergedText.length;
    mergedText += normalized;
    itemRanges.push({
      index,
      start,
      end: mergedText.length
    });
  });

  const matchRange = resolveMatchRange(mergedText, candidates);
  if (!matchRange) return 0;

  const [matchStart, matchEnd] = matchRange;
  let firstMatch: HTMLElement | undefined;
  const matchedFragments: HighlightFragment[] = [];

  itemRanges.forEach(({ index, start, end }) => {
    if (end <= matchStart || start >= matchEnd) return;
    const div = textDivs[index];
    if (!div) return;

    const itemLength = end - start;
    if (itemLength <= 0) return;

    const overlapStart = Math.max(start, matchStart);
    const overlapEnd = Math.min(end, matchEnd);
    const startRatio = (overlapStart - start) / itemLength;
    const endRatio = (overlapEnd - start) / itemLength;

    div.classList.add('matched-text');
    firstMatch ??= div;
    matchedFragments.push({
      div,
      startRatio,
      endRatio
    });
  });

  highlightRects.value = buildHighlightRects(textLayer, matchedFragments);

  if (firstMatch) {
    firstMatch.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    });
  }

  return highlightRects.value.length;
}

function resolveMatchRange(target: string, anchors: string[]): [number, number] | null {
  if (!target || !anchors.length) return null;

  for (const anchor of anchors) {
    if (!anchor) continue;

    const exactMatchIndex = target.indexOf(anchor);
    if (exactMatchIndex >= 0) {
      return [exactMatchIndex, exactMatchIndex + anchor.length];
    }

    const partialAnchor = anchor.slice(0, Math.min(anchor.length, 48)).trim();
    if (partialAnchor.length < 8) continue;

    const partialMatchIndex = target.indexOf(partialAnchor);
    if (partialMatchIndex >= 0) {
      return [partialMatchIndex, partialMatchIndex + partialAnchor.length];
    }
  }

  return resolveFuzzyMatchRange(target, anchors);
}

function resolveFuzzyMatchRange(target: string, anchors: string[]): [number, number] | null {
  const ranges = buildPhraseRanges(target);
  if (!ranges.length) return null;

  const sortedAnchors = [...anchors]
    .filter(anchor => anchor && anchor.length >= 6)
    .sort((left, right) => right.length - left.length);

  let bestMatch: { start: number; end: number; score: number; weightedScore: number } | null = null;

  for (const anchor of sortedAnchors) {
    let bestRangeForAnchor: { start: number; end: number; score: number; weightedScore: number } | null = null;

    ranges.forEach(range => {
      const score = calculateDiceCoefficient(anchor, range.text);
      if (score < 0.18) return;

      const weightedScore = score * Math.min(anchor.length, range.text.length);
      if (
        !bestRangeForAnchor ||
        weightedScore > bestRangeForAnchor.weightedScore ||
        (weightedScore === bestRangeForAnchor.weightedScore && score > bestRangeForAnchor.score)
      ) {
        bestRangeForAnchor = {
          start: range.start,
          end: range.end,
          score,
          weightedScore
        };
      }
    });

    if (!bestRangeForAnchor) continue;

    if (
      (anchor.length >= 18 && bestRangeForAnchor.score >= 0.2) ||
      (anchor.length >= 12 && bestRangeForAnchor.score >= 0.28)
    ) {
      return [bestRangeForAnchor.start, bestRangeForAnchor.end];
    }

    if (
      !bestMatch ||
      bestRangeForAnchor.weightedScore > bestMatch.weightedScore ||
      (bestRangeForAnchor.weightedScore === bestMatch.weightedScore && bestRangeForAnchor.score > bestMatch.score)
    ) {
      bestMatch = bestRangeForAnchor;
    }
  }

  return bestMatch ? [bestMatch.start, bestMatch.end] : null;
}

function buildPhraseRanges(target: string) {
  const ranges: Array<{ start: number; end: number; text: string }> = [];
  const phrasePattern = /[^，,；;。！？!?]+[，,；;。！？!?]?/g;

  for (const match of target.matchAll(phrasePattern)) {
    const text = match[0]?.trim();
    const start = match.index ?? -1;
    if (!text || start < 0) continue;

    const normalized = normalizeForMatch(text);
    if (normalized.length < 6) continue;

    ranges.push({
      start,
      end: start + match[0].length,
      text: normalized
    });
  }

  return ranges;
}

function calculateDiceCoefficient(left: string, right: string) {
  const leftBigrams = buildBigrams(left);
  const rightBigrams = buildBigrams(right);
  if (!leftBigrams.size || !rightBigrams.size) return 0;

  let overlap = 0;
  leftBigrams.forEach((count, bigram) => {
    const rightCount = rightBigrams.get(bigram) || 0;
    overlap += Math.min(count, rightCount);
  });

  const leftSize = [...leftBigrams.values()].reduce((sum, count) => sum + count, 0);
  const rightSize = [...rightBigrams.values()].reduce((sum, count) => sum + count, 0);
  return (2 * overlap) / (leftSize + rightSize);
}

function buildBigrams(value: string) {
  const compact = value.replace(/\s+/g, '');
  const bigrams = new Map<string, number>();

  if (compact.length < 2) {
    if (compact) {
      bigrams.set(compact, 1);
    }
    return bigrams;
  }

  for (let index = 0; index < compact.length - 1; index += 1) {
    const bigram = compact.slice(index, index + 2);
    bigrams.set(bigram, (bigrams.get(bigram) || 0) + 1);
  }

  return bigrams;
}

async function waitForStageReady(expectedToken = lifecycleToken) {
  for (let attempt = 0; attempt < 24; attempt += 1) {
    if (expectedToken !== lifecycleToken) return;

    const stage = stageRef.value;
    const shell = pageShellRef.value;
    const hasStableStage =
      Boolean(stage) &&
      Boolean(shell) &&
      stage!.clientWidth > 240 &&
      stage!.clientHeight > 120 &&
      stage!.getClientRects().length > 0;

    if (hasStableStage) {
      return;
    }

    await waitForAnimationFrame();
  }
}

function waitForAnimationFrame() {
  return new Promise<void>(resolve => {
    requestAnimationFrame(() => resolve());
  });
}

function isBenignRenderError(error: unknown) {
  if (!error || typeof error !== 'object') return false;

  const errorName = 'name' in error ? String(error.name) : '';
  const errorMessage = 'message' in error ? String(error.message) : '';
  const benignNames = new Set(['RenderingCancelledException', 'AbortException', 'InvalidStateError']);

  if (benignNames.has(errorName)) {
    return true;
  }

  return /cancelled|canceled|abort/i.test(errorMessage);
}

function buildHighlightRects(container: HTMLElement, matchedFragments: HighlightFragment[]) {
  if (!matchedFragments.length) return [];

  const containerRect = container.getBoundingClientRect();
  const rects = matchedFragments
    .map(({ div, startRatio, endRatio }) => {
      const rect = div.getBoundingClientRect();
      if (rect.width < 2 || rect.height < 2) return null;

      const insetY = Math.max(1, rect.height * 0.2);
      const safeStartRatio = Math.min(Math.max(startRatio, 0), 1);
      const safeEndRatio = Math.min(Math.max(endRatio, safeStartRatio), 1);
      const segmentLeft = rect.left + rect.width * safeStartRatio;
      const segmentWidth = rect.width * (safeEndRatio - safeStartRatio);
      if (segmentWidth < 2) return null;

      const insetX = Math.min(1.5, segmentWidth * 0.06);

      return {
        left: Math.max(0, segmentLeft - containerRect.left - insetX),
        top: Math.max(0, rect.top - containerRect.top + insetY),
        width: segmentWidth + insetX * 2,
        height: Math.max(6, rect.height - insetY * 1.35)
      };
    })
    .filter((rect): rect is HighlightRect => Boolean(rect));

  return mergeHighlightRects(rects);
}

function mergeHighlightRects(rects: HighlightRect[]) {
  if (rects.length <= 1) return rects;

  const sortedRects = [...rects].sort((a, b) => {
    if (Math.abs(a.top - b.top) > 2) {
      return a.top - b.top;
    }
    return a.left - b.left;
  });

  const merged: HighlightRect[] = [];

  sortedRects.forEach(rect => {
    const previousRect = merged.at(-1);
    if (!previousRect) {
      merged.push({ ...rect });
      return;
    }

    const topDelta = Math.abs(previousRect.top - rect.top);
    const heightDelta = Math.abs(previousRect.height - rect.height);
    const gap = rect.left - (previousRect.left + previousRect.width);
    const lineTolerance = Math.max(3, Math.min(previousRect.height, rect.height) * 0.35);

    if (topDelta <= lineTolerance && heightDelta <= lineTolerance && gap <= 12) {
      const right = Math.max(previousRect.left + previousRect.width, rect.left + rect.width);
      previousRect.top = Math.min(previousRect.top, rect.top);
      previousRect.height = Math.max(previousRect.height, rect.height);
      previousRect.width = right - previousRect.left;
      return;
    }

    merged.push({ ...rect });
  });

  return merged;
}

async function cleanupPdfState() {
  if (summaryLoadTimer) {
    window.clearTimeout(summaryLoadTimer);
    summaryLoadTimer = null;
  }

  if (renderTimer) {
    window.clearTimeout(renderTimer);
    renderTimer = null;
  }

  queuedRenderVersion = 0;
  activeRenderVersion = 0;
  activeRenderPromise = null;
  rerenderAfterCurrent = false;
  lastObservedStageWidth = 0;
  lastSuccessfulRenderSignature = '';
  activeSummaryPromise = null;
  summaryQueue = [];
  summaryLoadedPages = new Set();
  summaryLoadingPages = new Set();

  renderTask?.cancel();
  renderTask = null;

  textLayerTask?.cancel();
  textLayerTask = null;

  if (loadingTask) {
    await loadingTask.destroy();
    loadingTask = null;
  }

  if (pdfDocument.value) {
    await pdfDocument.value.destroy();
    pdfDocument.value = null;
  }

  if (canvasRef.value) {
    const context = canvasRef.value.getContext('2d');
    context?.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height);
  }

  if (textLayerRef.value) {
    textLayerRef.value.innerHTML = '';
  }

  highlightRects.value = [];
}
</script>

<style scoped lang="scss">
.pdf-viewer-shell {
  @apply flex h-full min-h-0 flex-col rounded-2xl border border-stone-200 bg-stone-50;
}

.pdf-viewer-toolbar {
  @apply flex items-start justify-between gap-4 border-b border-stone-200 bg-white px-4 py-3;
}

.toolbar-copy {
  @apply flex min-w-0 flex-col gap-1;
}

.viewer-badge {
  @apply inline-flex w-fit rounded-full bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-700;
}

.viewer-title {
  @apply truncate text-sm font-semibold text-stone-700;
}

.viewer-anchor {
  @apply line-clamp-2 max-w-520px text-xs text-stone-500;
}

.toolbar-actions {
  @apply flex flex-wrap items-center justify-end gap-2;
}

.toolbar-status {
  @apply text-xs text-stone-500;
}

.pdf-viewer-body {
  @apply grid min-h-0 flex-1 overflow-hidden grid-cols-[220px_minmax(0,1fr)];
}

.pdf-viewer-body.is-single-page {
  @apply block;
}

.page-sidebar {
  @apply min-h-0 overflow-y-auto border-r border-stone-200 bg-stone-100 p-3;
}

.page-nav-item {
  @apply mb-2 flex w-full flex-col items-start gap-1 rounded-2xl border border-transparent bg-white/80 px-3 py-3 text-left transition;
}

.page-nav-item:hover {
  @apply border-stone-300 bg-white;
}

.page-nav-item.is-active {
  @apply border-stone-700 bg-stone-900 text-white shadow-sm;
}

.page-nav-item.is-target {
  box-shadow: inset 0 0 0 1px rgba(245, 158, 11, 0.9);
}

.page-nav-number {
  @apply text-xs font-semibold opacity-80;
}

.page-nav-summary {
  @apply line-clamp-3 text-xs leading-5;
}

.page-stage {
  @apply relative min-h-0 overflow-auto bg-[radial-gradient(circle_at_top,_rgba(251,191,36,0.12),_transparent_35%),linear-gradient(180deg,_#f8fafc_0%,_#f1f5f9_100%)] p-6;
}

.stage-feedback {
  @apply flex h-full min-h-320px flex-col items-center justify-center gap-3 rounded-3xl border border-dashed border-stone-300 bg-white/80 text-stone-500;
}

.stage-feedback.is-error {
  @apply text-red-500;
}

.page-scroll-shell {
  @apply flex min-h-full flex-col items-center gap-4;
}

.page-meta-row {
  @apply flex w-full max-w-960px items-center justify-between rounded-full border border-white/60 bg-white/75 px-4 py-2 text-xs text-stone-500 shadow-sm backdrop-blur;
}

.pdf-page-shell {
  @apply relative rounded-[28px] border border-stone-200 bg-white p-6 shadow-[0_30px_80px_rgba(15,23,42,0.08)];
}

.pdf-canvas {
  @apply relative z-0 block rounded-2xl;
}

.pdf-highlight-overlay {
  @apply pointer-events-none absolute inset-6 z-[5] overflow-hidden rounded-2xl;
}

.pdf-highlight-rect {
  @apply absolute;
  border-radius: 6px;
  background: linear-gradient(
    180deg,
    rgba(254, 240, 138, 0.14) 0%,
    rgba(250, 204, 21, 0.5) 22%,
    rgba(250, 204, 21, 0.7) 55%,
    rgba(245, 158, 11, 0.44) 100%
  );
  box-shadow: 0 0 0 1px rgba(245, 158, 11, 0.08);
  mix-blend-mode: multiply;
  opacity: 0.96;
}

.pdf-text-layer {
  @apply absolute inset-6 z-10 overflow-hidden;
}

.pdf-text-layer :deep(span),
.pdf-text-layer :deep(br) {
  color: transparent;
  position: absolute;
  transform-origin: 0 0;
  white-space: pre;
  cursor: text;
  line-height: 1;
  margin: 0;
  padding: 0;
}

.pdf-text-layer :deep(span.matched-text) {
  background: rgba(250, 204, 21, 0.42);
  box-shadow: inset 0 -0.52em rgba(245, 158, 11, 0.34);
  border-radius: 4px;
  color: transparent;
  text-decoration: underline;
  text-decoration-color: rgba(217, 119, 6, 0.9);
  text-decoration-thickness: 2px;
  text-underline-offset: 1px;
}

.pdf-text-layer :deep(.endOfContent) {
  @apply absolute left-0 top-full block h-px w-px opacity-0;
}

.page-loading-mask {
  @apply absolute inset-6 z-20 flex items-center justify-center gap-2 rounded-2xl bg-white/75 text-sm text-stone-500 backdrop-blur-sm;
}
</style>
