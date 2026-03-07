<script setup lang="ts">
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { nextTick } from 'vue';
import { VueMarkdownIt } from '@/vendor/vue-markdown-shiki';
import FilePreview from '@/components/custom/file-preview.vue';
import { formatDate } from '@/utils/common';
defineOptions({ name: 'ChatMessage' });

const props = defineProps<{
  msg: Api.Chat.Message,
  sessionId?: string
}>();

const authStore = useAuthStore();
const previewVisible = ref(false);
const previewFileName = ref('');
const previewFileMd5 = ref('');
const previewPageNumber = ref<number | undefined>(undefined);
const previewAnchorText = ref('');

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

const chatStore = useChatStore();

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{fileName: string, id: string, referenceNumber: number, fileMd5?: string, pageNumber?: number}>>([]);

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  // 重置来源文件列表，避免重复
  sourceFiles.value = [];

  // 新格式（带页码）：匹配 (来源#数字: 文件名 | 第X页) 的正则表达式，兼容全角括号
  // 格式示例：(来源#1: test.pdf | 第5页) 或 (来源#1: test.pdf|第5页)
  const pagePattern = /([\(（])来源#(\d+):\s*([^|\n\r（）]+?)\s*\|\s*第(\d+)页([\)）])/g;

  // 先处理带页码的新格式
  let processedText = text.replace(pagePattern, (_match, leftParen, sourceNum, fileName, pageNum, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息
    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber,
      pageNumber: parseInt(pageNum, 10)
    });

    const lp = leftParen === '(' ? '(' : '（';
    const rp = rightParen === ')' ? ')' : '）';

    // 显示格式：来源#1: test.pdf (第5页)
    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName} (第${pageNum}页)</span>${rp}`;
  });

  // 旧格式（带MD5）：匹配 (来源#数字: 文件名 | MD5:xxx) 的正则表达式，兼容全角括号
  const md5Pattern = /([\(（])来源#(\d+):\s*([^|\n\r（）]+?)\s*\|\s*MD5:\s*([a-fA-F0-9]+)([\)）])/g;

  processedText = processedText.replace(md5Pattern, (_match, leftParen, sourceNum, fileName, fileMd5, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const trimmedMd5 = fileMd5.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息（包含文件名和MD5）
    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber,
      fileMd5: trimmedMd5
    });

    const lp = leftParen === '(' ? '(' : '（';
    const rp = rightParen === ')' ? ')' : '）';

    // 显示格式：来源#1: test.txt（不显示MD5）
    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName}</span>${rp}`;
  });

  // 简单格式：匹配 (来源#数字: 文件名) 的正则表达式，兼容全角括号
  const simplePattern = /([\(（])来源#(\d+):\s*([^\n\r（）|]+?)([\)）])/g;

  processedText = processedText.replace(simplePattern, (_match, leftParen, sourceNum, fileName, rightParen) => {
    const linkClass = 'source-file-link';
    const trimmedFileName = fileName.trim();
    const fileId = `source-file-${sourceFiles.value.length}`;
    const referenceNumber = parseInt(sourceNum, 10);

    // 存储文件信息
    sourceFiles.value.push({
      fileName: trimmedFileName,
      id: fileId,
      referenceNumber
    });

    const lp = leftParen || '';
    const rp = rightParen || '';

    return `${lp}来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${trimmedFileName}</span>${rp}`;
  });

  return processedText;
}

const content = computed(() => {
  chatStore.scrollToBottom?.();
  const rawContent = props.msg.content ?? '';

  // 只对助手消息处理来源链接
  if (props.msg.role === 'assistant') {
    return processSourceLinks(rawContent);
  }

  return rawContent;
});

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  // 检查点击的是否是文件链接
  if (target.classList.contains('source-file-link')) {
    const fileId = target.getAttribute('data-file-id');
    if (fileId) {
      const file = sourceFiles.value.find(f => f.id === fileId);
      if (file) {
        handleSourceFileClick({
          fileName: file.fileName,
          referenceNumber: file.referenceNumber,
          fileMd5: file.fileMd5
        });
      }
    }
  }
}

// 处理来源文件点击事件
async function handleSourceFileClick(fileInfo: { fileName: string, referenceNumber: number, fileMd5?: string }) {
  const { fileName, referenceNumber, fileMd5: extractedMd5 } = fileInfo;
  console.log('点击了来源文件:', fileName, '引用编号:', referenceNumber, '提取的MD5:', extractedMd5, '会话ID:', props.sessionId);

  try {
    window.$message?.loading(`正在定位引用内容: ${fileName}`, {
      duration: 0,
      closable: false
    });

    let detail: Api.Document.ReferenceDetailResponse | null = null;

    if (props.sessionId) {
      try {
        const { error: detailError, data: detailData } = await request<Api.Document.ReferenceDetailResponse>({
          url: 'documents/reference-detail',
          params: {
            sessionId: props.sessionId,
            referenceNumber: referenceNumber.toString()
          },
          baseURL: '/proxy-api'
        });

        if (!detailError && detailData?.fileMd5) {
          detail = detailData;
        }
      } catch (detailErr) {
        console.warn('通过API查询引用详情失败:', detailErr);
      }
    }

    const targetMd5 = detail?.fileMd5 || extractedMd5 || null;
    if (targetMd5) {
      previewFileName.value = detail?.fileName || fileName;
      previewFileMd5.value = targetMd5;
      previewPageNumber.value = detail?.pageNumber || undefined;
      previewAnchorText.value = detail?.anchorText || '';
      previewVisible.value = true;
      window.$message?.destroyAll();
      return;
    }

    // 降级方案：使用文件名下载（向后兼容）
    console.log('降级方案: 使用文件名下载', fileName);
    const { error, data } = await request<Api.Document.DownloadResponse>({
      url: 'documents/download',
      params: {
        fileName: fileName,
        token: authStore.token
      },
      baseURL: '/proxy-api'
    });

    window.$message?.destroyAll();

    if (error) {
      window.$message?.error(`文件下载失败: ${error.response?.data?.message || '未知错误'}`);
      return;
    }

    if (data?.downloadUrl) {
      window.open(data.downloadUrl, '_blank');
      window.$message?.success(`文件下载链接已打开: ${data.fileName || fileName}`);
    } else {
      window.$message?.error('未能获取到下载链接');
    }
  } catch (err) {
    window.$message?.destroyAll();
    console.error('文件下载失败:', err);
    window.$message?.error(`文件下载失败: ${fileName}`);
  }
}

function closePreview() {
  previewVisible.value = false;
  previewFileName.value = '';
  previewFileMd5.value = '';
  previewPageNumber.value = undefined;
  previewAnchorText.value = '';
}
</script>

<template>
  <div class="mb-8 flex-col gap-2">
    <div v-if="msg.role === 'user'" class="flex items-center gap-4">
      <NAvatar class="bg-success">
        <SvgIcon icon="ph:user-circle" class="text-icon-large color-white" />
      </NAvatar>
      <div class="flex-col gap-1">
        <NText class="text-4 font-bold">{{ authStore.userInfo.username }}</NText>
        <NText class="text-3 color-gray-500">{{ formatDate(msg.timestamp) }}</NText>
      </div>
    </div>
    <div v-else class="flex items-center gap-4">
      <NAvatar class="bg-primary">
        <SystemLogo class="text-6 text-white" />
      </NAvatar>
      <div class="flex-col gap-1">
        <NText class="text-4 font-bold">派聪明</NText>
        <NText class="text-3 color-gray-500">{{ formatDate(msg.timestamp) }}</NText>
      </div>
    </div>
    <NText v-if="msg.status === 'pending'">
      <icon-eos-icons:three-dots-loading class="ml-12 mt-2 text-8" />
    </NText>
    <NText v-else-if="msg.status === 'error'" class="ml-12 mt-2 italic">服务器繁忙，请稍后再试</NText>
    <div v-else-if="msg.role === 'assistant'" class="mt-2 pl-12" @click="handleContentClick">
      <VueMarkdownIt :content="content" />
    </div>
    <NText v-else-if="msg.role === 'user'" class="ml-12 mt-2 text-4">{{ content }}</NText>
    <NDivider class="ml-12 w-[calc(100%-3rem)] mb-0! mt-2!" />
    <div class="ml-12 flex gap-4">
      <NButton quaternary @click="handleCopy(msg.content)">
        <template #icon>
          <icon-mynaui:copy />
        </template>
      </NButton>
    </div>
    <NModal v-model:show="previewVisible" preset="card" title="引用文档预览" style="width: 80%; max-width: 1000px;">
      <FilePreview
        :file-name="previewFileName"
        :file-md5="previewFileMd5"
        :page-number="previewPageNumber"
        :anchor-text="previewAnchorText"
        :visible="previewVisible"
        @close="closePreview"
      />
    </NModal>
  </div>
</template>

<style scoped lang="scss">
:deep(.source-file-link) {
  color: #1890ff;
  cursor: pointer;
  text-decoration: underline;
  transition: color 0.2s;

  &:hover {
    color: #40a9ff;
    text-decoration: none;
  }

  &:active {
    color: #096dd9;
  }
}
</style>
