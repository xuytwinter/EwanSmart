<script setup lang="ts">
import { NScrollbar } from 'naive-ui';

const authStore = useAuthStore();
const chatStore = useChatStore();
const { list } = storeToRefs(chatStore);

const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();
watch(
  () => [...list.value],
  () => {
    nextTick(() => {
      scrollbarRef.value?.scrollBy({
        top: 999999,
        behavior: 'auto'
      });
    });
  }
);

async function getList() {
  const { error, data } = await request<Api.Chat.Message[]>({
    url: '/conversation/history',
    params: {
      userId: authStore.userInfo.id
    }
  });
  if (!error) {
    list.value = data;
  }
}

onMounted(() => {
  getList();
});

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}
</script>

<template>
  <NScrollbar ref="scrollbarRef" class="h-0 flex-auto">
    <div v-for="(item, index) in list" :key="index" class="mb-8 flex-col gap-2">
      <div v-if="item.role === 'user'" class="flex items-center gap-4">
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
      <NText v-if="item.status === 'pending'">
        <icon-eos-icons:three-dots-loading class="ml-12 mt-2 text-8" />
      </NText>
      <NText v-else-if="item.status === 'loading'" class="ml-12 mt-2"></NText>
      <NText v-else-if="item.status === 'error'" class="ml-12 mt-2">服务器繁忙，请稍后再试</NText>
      <NText v-else class="ml-12 mt-2">{{ item.content }}</NText>
      <NDivider class="ml-12 w-[calc(100%-3rem)] mb-0! mt-2!" />
      <div class="ml-12 flex gap-4">
        <NButton quaternary @click="handleCopy(item.content)">
          <template #icon>
            <icon-mynaui:copy />
          </template>
        </NButton>
      </div>
    </div>
  </NScrollbar>
</template>

<style scoped></style>
