<route lang="yaml">
  name: home
  meta:
    requiresAuth: true
</route>

<script setup lang="ts">
import ChatBox from './modules/chat-box.vue'
import ChatList from './modules/chat-list.vue'

defineOptions({
  name: 'IndexPage',
})
</script>

<template>
  <div grid grid-cols="[300px_1fr]" h-full grid-rows="[auto_1fr_auto]">
    <ChatList row-span-3 />
    <TheHeader />
    <ChatBox />
  </div>
</template>

<style scoped lang="scss">
:deep(.ant-sender) {
  width: 50%;
  position: fixed;
  bottom: 40px;

  .ant-sender-actions-btn {
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
