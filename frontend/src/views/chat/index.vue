<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
import ConversationSidebar from './modules/conversation-sidebar.vue';
import ReferencePreviewPage from './modules/reference-preview-page.vue';

const route = useRoute();
const showReferencePreview = computed(() => route.query.preview === 'reference');
</script>

<template>
  <div v-if="showReferencePreview" class="h-full">
    <ReferencePreviewPage />
  </div>
  <div v-else class="flex h-full gap-0">
    <ConversationSidebar />
    <div class="flex min-w-0 flex-1 flex-col bg-white dark:bg-[#1c1c1c]">
      <ChatList />
      <InputBox />
    </div>
  </div>
</template>

<style scoped></style>
