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
        <span class="toolbar-status">第 {{ currentPage }} / {{ totalPages || 1 }} 页</span>
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

    <div class="pdf-viewer-body">
      <aside class="page-sidebar">
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
          <span class="page-nav-number">P{{ page.pageNumber }}</span>
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
            <span>第 {{ currentPage }} 页</span>
            <span v-if="currentPage === targetPageNumber">引用定位页</span>
            <span v-else-if="highlightCount > 0">已匹配到相关文本</span>
            <span v-else>浏览当前页</span>
          </div>

          <div ref="pageShellRef" class="pdf-page-shell">
            <canvas ref="canvasRef" class="pdf-canvas" />
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
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

GlobalWorkerOptions.workerSrc = workerSrc;

interface Props {
  url: string;
  fileName?: string;
  pageNumber?: number;
  anchorText?: string;
}

interface PageSummary {
  pageNumber: number;
  summary: string;
}

const props = defineProps<Props>();

const minZoom = 0.7;
const maxZoom = 2.2;

const pdfDocument = shallowRef<PDFDocumentProxy | null>(null);
const documentLoading = ref(false);
const pageRendering = ref(false);
const renderError = ref('');
const totalPages = ref(0);
const currentPage = ref(1);
const zoom = ref(1);
const highlightCount = ref(0);
const pageSummaries = ref<PageSummary[]>([]);

const stageRef = ref<HTMLDivElement | null>(null);
const pageShellRef = ref<HTMLDivElement | null>(null);
const canvasRef = ref<HTMLCanvasElement | null>(null);
const textLayerRef = ref<HTMLDivElement | null>(null);

const targetPageNumber = computed(() => clampPage(props.pageNumber || 1, totalPages.value || 1));
const normalizedAnchor = computed(() => normalizeForMatch(props.anchorText || ''));

let loadingTask: PDFDocumentLoadingTask | null = null;
let renderTask: RenderTask | null = null;
let textLayerTask: TextLayer | null = null;
let lifecycleToken = 0;

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
  async pageNumber => {
    if (!pageNumber || !totalPages.value) return;
    currentPage.value = clampPage(pageNumber, totalPages.value);
    await renderCurrentPage();
  }
);

watch(
  () => props.anchorText,
  () => {
    applyHighlight();
  }
);

watch(currentPage, async () => {
  if (!pdfDocument.value) return;
  await renderCurrentPage();
});

watch(zoom, async () => {
  if (!pdfDocument.value) return;
  await renderCurrentPage();
});

useResizeObserver(stageRef, () => {
  if (!pdfDocument.value || documentLoading.value) return;
  void renderCurrentPage();
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
    .replace(/[…]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

function summarizeText(value: string) {
  return value.replace(/\s+/g, ' ').trim();
}

function goToPage(page: number) {
  currentPage.value = clampPage(page, totalPages.value || 1);
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
  if (!props.url) return;
  window.open(`${props.url}#page=${currentPage.value}`, '_blank', 'noopener,noreferrer');
}

async function loadDocument(url: string) {
  lifecycleToken += 1;
  const currentToken = lifecycleToken;

  documentLoading.value = true;
  renderError.value = '';
  totalPages.value = 0;
  currentPage.value = 1;
  highlightCount.value = 0;
  pageSummaries.value = [];

  await cleanupPdfState();

  try {
    loadingTask = getDocument({
      url,
      withCredentials: false
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
      summary: ''
    }));

    void loadPageSummaries(documentProxy, currentToken);

    await nextTick();
    await renderCurrentPage(currentToken);
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

async function loadPageSummaries(documentProxy: PDFDocumentProxy, token: number) {
  for (let pageNumber = 1; pageNumber <= documentProxy.numPages; pageNumber += 1) {
    if (token !== lifecycleToken) return;
    try {
      const page = await documentProxy.getPage(pageNumber);
      const textContent = await page.getTextContent();
      const summary = summarizeText(
        textContent.items
          .filter((item): item is TextItem => 'str' in item)
          .map(item => item.str)
          .join(' ')
      ).slice(0, 80);

      pageSummaries.value[pageNumber - 1] = {
        pageNumber,
        summary: summary || `第 ${pageNumber} 页`
      };
    } catch (error) {
      pageSummaries.value[pageNumber - 1] = {
        pageNumber,
        summary: `第 ${pageNumber} 页`
      };
    }
  }
}

async function renderCurrentPage(expectedToken = lifecycleToken) {
  const documentProxy = pdfDocument.value;
  const canvas = canvasRef.value;
  const textLayer = textLayerRef.value;
  const stage = stageRef.value;

  if (!documentProxy || !canvas || !textLayer || !stage) return;

  pageRendering.value = true;
  renderError.value = '';
  highlightCount.value = 0;

  try {
    renderTask?.cancel();
    renderTask = null;
    textLayerTask?.cancel();
    textLayerTask = null;

    const page = await documentProxy.getPage(currentPage.value);
    if (expectedToken !== lifecycleToken) return;

    const baseViewport = page.getViewport({ scale: 1 });
    const availableWidth = Math.max(stage.clientWidth - 72, 320);
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
    if (expectedToken !== lifecycleToken) return;

    const textContent = await page.getTextContent({
      includeMarkedContent: true
    });

    textLayerTask = new TextLayer({
      textContentSource: textContent,
      container: textLayer,
      viewport
    });

    await textLayerTask.render();
    highlightCount.value = applyHighlight();
  } catch (error: any) {
    if (error?.name === 'RenderingCancelledException') {
      return;
    }
    console.error('[PDF 预览] 页面渲染失败:', error);
    renderError.value = 'PDF 页面渲染失败，请稍后重试。';
  } finally {
    if (expectedToken === lifecycleToken) {
      pageRendering.value = false;
    }
  }
}

function applyHighlight() {
  if (!textLayerTask) return 0;

  const textDivs = textLayerTask.textDivs;
  textDivs.forEach(div => div.classList.remove('matched-text'));

  const anchor = normalizedAnchor.value;
  if (!anchor) return 0;

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

  const matchRange = resolveMatchRange(mergedText, anchor);
  if (!matchRange) return 0;

  const [matchStart, matchEnd] = matchRange;
  let firstMatch: HTMLElement | undefined;
  let matches = 0;

  itemRanges.forEach(({ index, start, end }) => {
    if (end <= matchStart || start >= matchEnd) return;
    const div = textDivs[index];
    if (!div) return;
    div.classList.add('matched-text');
    firstMatch ??= div;
    matches += 1;
  });

  if (firstMatch) {
    firstMatch.scrollIntoView({
      block: 'center',
      behavior: 'smooth'
    });
  }

  return matches;
}

function resolveMatchRange(target: string, anchor: string): [number, number] | null {
  if (!target || !anchor) return null;

  const exactMatchIndex = target.indexOf(anchor);
  if (exactMatchIndex >= 0) {
    return [exactMatchIndex, exactMatchIndex + anchor.length];
  }

  const partialAnchor = anchor.slice(0, Math.min(anchor.length, 48)).trim();
  if (partialAnchor.length < 8) return null;

  const partialMatchIndex = target.indexOf(partialAnchor);
  if (partialMatchIndex >= 0) {
    return [partialMatchIndex, partialMatchIndex + partialAnchor.length];
  }

  return null;
}

async function cleanupPdfState() {
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
  @apply grid min-h-0 flex-1 grid-cols-[220px_minmax(0,1fr)];
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
}

.pdf-text-layer :deep(span.matched-text) {
  border-radius: 4px;
  background: rgba(250, 204, 21, 0.4);
  color: rgba(41, 37, 36, 0.92);
}

.pdf-text-layer :deep(.endOfContent) {
  @apply absolute left-0 top-full block h-px w-px opacity-0;
}

.page-loading-mask {
  @apply absolute inset-6 z-20 flex items-center justify-center gap-2 rounded-2xl bg-white/75 text-sm text-stone-500 backdrop-blur-sm;
}
</style>
