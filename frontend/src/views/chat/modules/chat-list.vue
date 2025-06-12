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
      <NForm :model="params" label-placement="left" :show-feedback="false" inline class="mx-10">
        <NFormItem label="时间">
          <NDatePicker v-model:value="range" type="datetimerange" />
        </NFormItem>
      </NForm>
    </Teleport>
    <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" />
  </NScrollbar>
</template>

<style scoped lang="scss"></style>
