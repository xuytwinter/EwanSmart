<script setup lang="ts">
const authStore = useAuthStore();

const chatStore = useChatStore();
const { input, list } = storeToRefs(chatStore);

const latestMessage = computed(() => {
  return list.value[list.value.length - 1];
});
const isSending = computed(() => {
  return latestMessage.value.role === 'assistant' && ['loading', 'pending'].includes(latestMessage.value?.status || '');
});

const handleSend = () => {
  if (isSending.value) {
    // TODO 调用后端接口，停止响应

    list.value[list.value.length - 1].status = 'done';
    if (!latestMessage.value.content) list.value.pop();
    return;
  }

  list.value.push({
    content: input.value.message,
    role: 'user'
  });
  input.value.message = '';
  chatStore.wsSend(JSON.stringify({ message: input.value.message }));
  list.value.push({
    content: '',
    role: 'assistant',
    status: 'loading'
  });
};

async function createNewChat() {
  // TODO 调用后端接口，停止响应

  const { error, data } = await request<Api.Chat.Conversation>({
    url: 'conversation/create',
    method: 'POST',
    data: { userId: authStore.userInfo.id }
  });

  if (!error) {
    list.value = [];
    chatStore.conversationId = data.conversationId;
  }
}
</script>

<template>
  <div class="relative w-full b-1 b-#1c1c1c20 bg-#fff p-4 card-wrapper dark:bg-#1c1c1c">
    <NButton type="primary" class="absolute left-1/2 top--60px -translate-x-1/2" @click="createNewChat">
      <template #icon>
        <icon-akar-icons:chat-add />
      </template>
      创建新对话
    </NButton>
    <textarea
      v-model.trim="input.message"
      placeholder="给 派聪明 发送消息"
      class="min-h-10 w-full cursor-text resize-none b-none bg-transparent color-#333 caret-[rgb(var(--primary-color))] outline-none dark:color-#f1f1f1"
    />
    <div class="flex justify-end">
      <NButton :disabled="!input.message" strong circle type="primary" @click="handleSend">
        <template #icon>
          <icon-material-symbols:stop-rounded v-if="isSending" />
          <icon-guidance:send v-else />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped></style>
