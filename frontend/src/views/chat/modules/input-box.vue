<script setup lang="ts">
const store = useChatStore();
const { input, list } = storeToRefs(store);

const handleSend = () => {
  list.value.push({
    content: input.value.message,
    role: 'user'
  });
  input.value.message = '';
  store.wsSend(JSON.stringify({ message: input.value.message }));
  list.value.push({
    content: '',
    role: 'assistant'
  });
};
</script>

<template>
  <div class="w-full b-1 b-#1c1c1c20 bg-#fff p-4 card-wrapper dark:bg-#1c1c1c">
    <textarea
      v-model.trim="input.message"
      placeholder="给 派聪明 发送消息"
      class="min-h-10 w-full cursor-text resize-none b-none bg-transparent color-#333 caret-[rgb(var(--primary-color))] outline-none dark:color-#f1f1f1"
    ></textarea>
    <div class="flex justify-end">
      <NButton :disabled="!input.message" strong circle type="primary" @click="handleSend">
        <template #icon>
          <icon-guidance:send />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped></style>
