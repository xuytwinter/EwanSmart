<template>
  <div class="file-preview-container">
    <!-- 预览头部 -->
    <div class="preview-header">
      <div class="flex items-center gap-2">
        <SvgIcon :local-icon="getFileIcon(fileName)" class="text-16" />
        <span class="font-medium">{{ fileName }}</span>
      </div>
      <div class="flex items-center gap-2">
        <NButton size="small" @click="downloadFile" :loading="downloading">
          <template #icon>
            <icon-mdi-download />
          </template>
          下载
        </NButton>
        <NButton size="small" @click="closePreview">
          <template #icon>
            <icon-mdi-close />
          </template>
        </NButton>
      </div>
    </div>
    
    <!-- 预览内容 -->
    <div class="preview-content">
      <template v-if="loading">
        <div class="flex items-center justify-center h-full">
          <NSpin size="large" />
        </div>
      </template>
      <template v-else-if="error">
        <div class="flex flex-col items-center justify-center h-full text-gray-500">
          <icon-mdi-alert-circle class="text-48 mb-4" />
          <p>{{ error }}</p>
        </div>
      </template>
      <template v-else>
        <div class="content-wrapper">
          <template v-if="previewType === 'pdf' && previewUrl">
            <div class="pdf-preview-stack">
              <div v-if="hasEvidenceDetails" class="evidence-panel">
                <div class="evidence-row">
                  <span class="evidence-label">命中方式</span>
                  <span class="evidence-value">{{ retrievalLabel || fallbackRetrievalLabel }}</span>
                </div>
                <div v-if="evidenceSnippet" class="evidence-row">
                  <span class="evidence-label">相关内容</span>
                  <span class="evidence-value">{{ evidenceSnippet }}</span>
                </div>
                <div v-if="matchedChunkPreview" class="evidence-row">
                  <div class="evidence-row-head">
                    <span class="evidence-label">命中原文</span>
                    <NButton
                      v-if="showExpandEvidenceButton"
                      quaternary
                      size="tiny"
                      @click="expandedEvidence = !expandedEvidence"
                    >
                      {{ expandedEvidence ? '收起' : '展开全文' }}
                    </NButton>
                  </div>
                  <div class="evidence-value evidence-block">{{ matchedChunkPreview }}</div>
                </div>
                <div class="evidence-meta">
                  <span v-if="pageNumber">页码：第 {{ pageNumber }} 页</span>
                  <span>文件：{{ fileName }}</span>
                  <span v-if="displayScore !== ''">相关分数：{{ displayScore }}</span>
                </div>
              </div>
              <PdfDocumentViewer
                :url="resolvedPreviewUrl"
                :source-url="resolvedSourceUrl"
                :file-name="fileName"
                :page-number="pageNumber"
                :single-page-mode="singlePageMode"
                :source-page-number="sourcePageNumber"
                :anchor-text="resolvedHighlightAnchor"
                :search-text="resolvedHighlightSearchText"
                :visible="visible"
              />
            </div>
          </template>
          <template v-else-if="previewType === 'image' && resolvedPreviewUrl">
            <div class="image-preview-shell">
              <img :src="resolvedPreviewUrl" :alt="fileName" class="preview-image" />
            </div>
          </template>
          <template v-else-if="previewType === 'text'">
            <pre class="preview-text">{{ content }}</pre>
          </template>
          <template v-else>
            <div class="download-placeholder">
              <SvgIcon :local-icon="getFileIcon(fileName)" class="text-24" />
              <p>当前文件类型暂不支持在线预览</p>
              <NButton type="primary" @click="downloadFile">
                <template #icon>
                  <icon-mdi-download />
                </template>
                下载后查看
              </NButton>
            </div>
          </template>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { NButton, NSpin } from 'naive-ui';
import PdfDocumentViewer from '@/components/custom/pdf-document-viewer.vue';
import SvgIcon from '@/components/custom/svg-icon.vue';
import { request } from '@/service/request';
import { getServiceBaseURL } from '@/utils/service';
import { getFileExt } from '@/utils/common';

interface Props {
  fileName: string;
  fileMd5?: string;
  pageNumber?: number;
  anchorText?: string;
  searchText?: string;
  retrievalMode?: Api.Chat.ReferenceEvidence['retrievalMode'];
  retrievalLabel?: string;
  evidenceSnippet?: string;
  matchedChunkText?: string;
  score?: number | null;
  chunkId?: number | null;
  visible: boolean;
}

interface Emits {
  (e: 'close'): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

const loading = ref(false);
const downloading = ref(false);
const content = ref('');
const error = ref('');
const previewType = ref<'pdf' | 'image' | 'text' | 'download'>('text');
const previewUrl = ref('');
const sourceUrl = ref('');
const singlePageMode = ref(false);
const sourcePageNumber = ref<number | undefined>(undefined);
const expandedEvidence = ref(false);
const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
const { baseURL: serviceBaseUrl } = getServiceBaseURL(import.meta.env, isHttpProxy);

const resolvedPreviewUrl = computed(() => resolveFileAccessUrl(previewUrl.value));
const resolvedSourceUrl = computed(() => resolveFileAccessUrl(sourceUrl.value));
const hasEvidenceDetails = computed(() => Boolean(props.retrievalLabel || props.evidenceSnippet || props.matchedChunkText));
const fallbackRetrievalLabel = computed(() => {
  if (props.retrievalMode === 'TEXT_ONLY') {
    return '关键词召回';
  }
  if (props.retrievalMode === 'HYBRID') {
    return '混合召回（语义相关 + 关键词命中）';
  }
  return '';
});
const showExpandEvidenceButton = computed(() => (props.matchedChunkText || '').length > 220);
const matchedChunkPreview = computed(() => {
  const sourceText = props.matchedChunkText || '';
  if (!sourceText) return '';
  if (expandedEvidence.value || sourceText.length <= 220) {
    return sourceText;
  }
  return `${sourceText.slice(0, 220)}…`;
});
const resolvedHighlightAnchor = computed(() => props.evidenceSnippet || props.anchorText || '');
const resolvedHighlightSearchText = computed(() => {
  return [props.evidenceSnippet, props.matchedChunkText, props.searchText, props.anchorText]
    .map(text => text?.trim())
    .filter((text, index, values): text is string => Boolean(text) && values.indexOf(text) === index)
    .join('\n');
});
const displayScore = computed(() => {
  if (typeof props.score !== 'number' || Number.isNaN(props.score)) {
    return '';
  }
  return props.score.toFixed(3);
});

function resolveFileAccessUrl(url: string) {
  if (!url) return '';
  if (/^(https?:)?\/\//i.test(url) || /^(blob:|data:)/i.test(url)) {
    return url;
  }

  if (url.startsWith('/api/')) {
    if (serviceBaseUrl.startsWith('/proxy-')) {
      return `${serviceBaseUrl}${url.replace(/^\/api\/v\d+/, '')}`;
    }

    if (/^https?:\/\//i.test(serviceBaseUrl)) {
      return `${new URL(serviceBaseUrl).origin}${url}`;
    }

    const serviceOrigin = serviceBaseUrl.replace(/\/api(?:\/v\d+)?\/?$/, '');
    return `${serviceOrigin}${url}`;
  }

  if (url.startsWith('/')) {
    return url;
  }

  return `${serviceBaseUrl.replace(/\/$/, '')}/${url.replace(/^\//, '')}`;
}

// 获取文件图标
function getFileIcon(fileName: string) {
  const ext = getFileExt(fileName);
  if (ext) {
    const supportedIcons = ['pdf', 'doc', 'docx', 'txt', 'md', 'jpg', 'jpeg', 'png', 'gif'];
    return supportedIcons.includes(ext.toLowerCase()) ? ext : 'dflt';
  }
  return 'dflt';
}

// 监听文件名变化，加载预览内容
watch(() => props.fileName, async (newFileName) => {
  if (newFileName && props.visible) {
    expandedEvidence.value = false;
    await loadPreviewContent();
  }
}, { immediate: true });

// 监听可见性变化
watch(() => props.visible, async (visible) => {
  if (visible && props.fileName) {
    expandedEvidence.value = false;
    await loadPreviewContent();
  }
});

// 加载预览内容
async function loadPreviewContent() {
  if (!props.fileName) return;

  console.log('[文件预览] 开始加载预览内容:', {
    fileName: props.fileName,
    fileMd5: props.fileMd5,
    visible: props.visible
  });

  loading.value = true;
  error.value = '';
  content.value = '';
  previewUrl.value = '';
  sourceUrl.value = '';
  singlePageMode.value = false;
  sourcePageNumber.value = undefined;
  previewType.value = 'text';

  try {
    const token = localStorage.getItem('token');

    // 优先使用 MD5 预览（如果存在）
    if (props.fileMd5) {
      console.log('[文件预览] 使用MD5模式预览，请求参数:', {
        fileName: props.fileName,
        fileMd5: props.fileMd5,
        hasToken: !!token
      });

      const { error: requestError, data } = await request<{
        fileName: string;
        fileSize: number;
        fileMd5?: string;
        content?: string;
        previewUrl?: string;
        sourceUrl?: string;
        singlePageMode?: boolean;
        sourcePageNumber?: number;
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          fileMd5: props.fileMd5,
          pageNumber: props.pageNumber,
          token: token || undefined
        }
      });

      console.log('[文件预览] MD5模式API响应:', {
        hasError: !!requestError,
        error: requestError,
        hasData: !!data,
        contentLength: data?.content?.length || 0,
        contentPreview: data?.content?.substring(0, 100) || ''
      });

      if (requestError) {
        error.value = '预览失败：' + (requestError.message || '未知错误');
      } else if (data) {
        previewType.value = data.previewType || 'download';
        content.value = data.content || '';
        previewUrl.value = data.previewUrl || '';
        sourceUrl.value = data.sourceUrl || data.previewUrl || '';
        singlePageMode.value = Boolean(data.singlePageMode);
        sourcePageNumber.value = data.sourcePageNumber || props.pageNumber;
      }
    } else {
      // 降级：使用文件名预览（向后兼容）
      console.log('[文件预览] 使用文件名模式预览（降级）, 请求参数:', {
        fileName: props.fileName,
        hasToken: !!token
      });

      const { error: requestError, data } = await request<{
        fileName: string;
        fileSize: number;
        fileMd5?: string;
        content?: string;
        previewUrl?: string;
        sourceUrl?: string;
        singlePageMode?: boolean;
        sourcePageNumber?: number;
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          pageNumber: props.pageNumber,
          token: token || undefined
        }
      });

      console.log('[文件预览] 文件名模式API响应:', {
        hasError: !!requestError,
        error: requestError,
        hasData: !!data,
        contentLength: data?.content?.length || 0,
        contentPreview: data?.content?.substring(0, 100) || ''
      });

      if (requestError) {
        error.value = '预览失败：' + (requestError.message || '未知错误');
      } else if (data) {
        previewType.value = data.previewType || 'download';
        content.value = data.content || '';
        previewUrl.value = data.previewUrl || '';
        sourceUrl.value = data.sourceUrl || data.previewUrl || '';
        singlePageMode.value = Boolean(data.singlePageMode);
        sourcePageNumber.value = data.sourcePageNumber || props.pageNumber;
      }
    }
  } catch (err: any) {
    error.value = '预览失败：' + (err.message || '网络错误');
  } finally {
    loading.value = false;
  }
}

// 下载文件
async function downloadFile() {
  if (!props.fileName) return;

  downloading.value = true;

  try {
    const token = localStorage.getItem('token');

    // 优先使用 MD5 下载（如果存在）
    if (props.fileMd5) {
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
        fileMd5?: string;
      }>({
        url: '/documents/download-by-md5',
        params: {
          fileMd5: props.fileMd5,
          token: token || undefined
        }
      });

      if (requestError) {
        window.$message?.error('下载失败：' + (requestError.message || '未知错误'));
      } else if (data) {
        // 使用预签名URL下载文件
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success('开始下载文件');
      }
    } else {
      // 降级：使用文件名下载（向后兼容）
      const { error: requestError, data } = await request<{
        fileName: string;
        downloadUrl: string;
        fileSize: number;
      }>({
        url: '/documents/download',
        params: {
          fileName: props.fileName,
          token: token || undefined
        }
      });

      if (requestError) {
        window.$message?.error('下载失败：' + (requestError.message || '未知错误'));
      } else if (data) {
        // 使用预签名URL下载文件
        const link = document.createElement('a');
        link.href = data.downloadUrl;
        link.download = data.fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.$message?.success('开始下载文件');
      }
    }
  } catch (err: any) {
    window.$message?.error('下载失败：' + (err.message || '网络错误'));
  } finally {
    downloading.value = false;
  }
}

// 关闭预览
function closePreview() {
  emit('close');
}

</script>

<style scoped lang="scss">
.file-preview-container {
  @apply flex h-full min-h-0 flex-col bg-white;
  height: min(80vh, calc(100vh - 96px));
  min-height: min(560px, calc(100vh - 48px));
  
  .preview-header {
    @apply flex items-center justify-between border-b border-stone-200 bg-stone-50 px-5 py-4;
  }
  
  .preview-content {
    @apply min-h-0 flex-1 overflow-hidden bg-stone-100;
    
    .content-wrapper {
      @apply h-full min-h-0 overflow-hidden p-5;
    }

    .pdf-preview-stack {
      @apply flex h-full min-h-0 flex-col gap-4;
    }

    .evidence-panel {
      @apply rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-stone-700 shadow-sm;
    }

    .evidence-row {
      @apply mb-3 last:mb-0;
    }

    .evidence-row-head {
      @apply mb-1 flex items-center justify-between gap-3;
    }

    .evidence-label {
      @apply mb-1 block text-xs font-600 tracking-wide text-amber-700;
    }

    .evidence-value {
      @apply leading-6 text-stone-700;
    }

    .evidence-block {
      @apply rounded-xl bg-white px-3 py-2 text-stone-600 shadow-sm;
    }

    .evidence-meta {
      @apply mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-stone-500;
    }
    
    .preview-text {
      @apply m-0 h-full overflow-auto rounded-xl border border-stone-200 bg-white p-5 text-sm whitespace-pre-wrap break-words shadow-sm;
      font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
      line-height: 1.5;
    }
    .image-preview-shell {
      @apply flex h-full min-h-0 overflow-auto items-center justify-center rounded-2xl border border-stone-200 bg-white p-6 shadow-sm;
    }

    .preview-image {
      @apply max-h-full max-w-full rounded-xl object-contain;
    }

    .download-placeholder {
      @apply flex h-full min-h-320px flex-col items-center justify-center gap-4 rounded-2xl border border-dashed border-stone-300 bg-white text-stone-500 shadow-sm;
    }
  }
}
</style>
