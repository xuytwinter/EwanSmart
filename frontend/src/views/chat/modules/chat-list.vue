<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import ChatMessage from './chat-message.vue';

const authStore = useAuthStore();
const chatStore = useChatStore();
const { list } = storeToRefs(chatStore);

const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  nextTick(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999,
      behavior: 'auto'
    });
  });
}

const range = ref<[number, number]>([dayjs().subtract(7, 'day').valueOf(), Date.now()]);

const params = computed(() => {
  return {
    userId: authStore.userInfo.id,
    start: dayjs(range.value[0]).format('YYYY-MM-DD HH:mm:ss'),
    end: dayjs(range.value[1]).format('YYYY-MM-DD HH:mm:ss')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'users/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
  }
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});
</script>

<template>
  <NScrollbar ref="scrollbarRef" class="h-0 flex-auto">
    <Teleport defer to="#header-extra">
      <NDatePicker v-model:value="range" type="datetimerange" class="dr mx-6" />
    </Teleport>
    <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" />
  </NScrollbar>
</template>

<style scoped lang="scss">
.dr {
  :deep(.n-input) {
    background: #0000;
    --n-border: none !important;
    --n-border-hover: none !important;
    --n-border-focus: none !important;
    --n-box-shadow-focus: none !important;
  }
}
</style>
