<script setup lang="ts">
import MarkdownIt from 'markdown-it';
import mila from 'markdown-it-link-attributes';
import mdKatex from '@traptitech/markdown-it-katex';
import hljs from 'highlight.js';
import 'github-markdown-css';

defineOptions({ name: 'ChatMessage' });

const props = defineProps<{ msg: Api.Chat.Message }>();

const authStore = useAuthStore();

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value;
    }
    return hljs.highlightAuto(code).value;
  }
});

md.use(mila, { attrs: { target: '_blank', rel: 'noopener' } });
md.use(mdKatex, { blockClass: 'katexmath-block rounded-md p-[10px]', errorColor: ' #cc0000' });

const chatStore = useChatStore();
const content = computed(() => {
  const value = props.msg.content ?? '';
  if (props.msg.role === 'assistant') {
    chatStore.scrollToBottom?.();
    return md.render(value);
  }
  return value;
});
</script>

<template>
  <div class="mb-8 flex-col gap-2">
    <div v-if="msg.role === 'user'" class="flex items-center gap-4">
      <NAvatar class="bg-success">
        <SvgIcon icon="ph:user-circle" class="text-icon-large color-white" />
      </NAvatar>
      <NText>{{ authStore.userInfo.username }}</NText>
    </div>
    <div v-else class="flex items-center gap-4">
      <NAvatar class="bg-primary">
        <SystemLogo class="text-6 text-white" />
      </NAvatar>
      <NText>派聪明</NText>
    </div>
    <NText v-if="msg.status === 'pending'">
      <icon-eos-icons:three-dots-loading class="ml-12 mt-2 text-8" />
    </NText>
    <NText v-else-if="msg.status === 'error'" class="ml-12 mt-2 italic">服务器繁忙，请稍后再试</NText>
    <div v-else class="markdown-body ml-12 mt-2" v-html="content"></div>
    <NDivider class="ml-12 w-[calc(100%-3rem)] mb-0! mt-2!" />
    <div class="ml-12 flex gap-4">
      <NButton quaternary @click="handleCopy(msg.content)">
        <template #icon>
          <icon-mynaui:copy />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.markdown-body {
  box-sizing: border-box;
  padding-left: 50px;
}

@media (prefers-color-scheme: dark) {
  .markdown-body {
    --bgColor-default: #121212;
  }
}
</style>
