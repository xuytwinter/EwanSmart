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
            <PdfDocumentViewer
              :url="previewUrl"
              :file-name="fileName"
              :page-number="pageNumber"
              :anchor-text="anchorText"
              :visible="visible"
            />
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
import { getFileExt } from '@/utils/common';

interface Props {
  fileName: string;
  fileMd5?: string;
  pageNumber?: number;
  anchorText?: string;
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
const resolvedPreviewUrl = computed(() => {
  if (!previewUrl.value) return '';
  return previewUrl.value;
});

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
    await loadPreviewContent();
  }
}, { immediate: true });

// 监听可见性变化
watch(() => props.visible, async (visible) => {
  if (visible && props.fileName) {
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
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
          fileMd5: props.fileMd5,
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
        previewType?: 'pdf' | 'image' | 'text' | 'download';
      }>({
        url: '/documents/preview',
        params: {
          fileName: props.fileName,
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
