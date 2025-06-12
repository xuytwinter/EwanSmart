<script setup lang="ts">
import type { NScrollbar } from 'naive-ui';
import ChatMessage from '../chat/modules/chat-message.vue';

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
    url: 'admin/conversation',
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
  <div class="h-full">
    <Teleport defer to="#header-extra">
      <NForm :model="params" label-placement="left" :show-feedback="false" inline class="mx-10">
        <NFormItem label="用户" path="userId">
          <TheSelect
            v-model:value="params.userId"
            url="admin/users/list"
            :params="{ page: 1, size: 999 }"
            key-field="content"
            class="clear w-200px!"
            :clearable="false"
          />
        </NFormItem>
        <NFormItem label="时间" path="start">
          <NDatePicker v-model:value="range" type="datetimerange" class="clear" />
        </NFormItem>
      </NForm>
    </Teleport>
    <NScrollbar ref="scrollbarRef">
      <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" />
    </NScrollbar>
  </div>
</template>

<style scoped lang="scss">
// .clear {
//   :deep(.n-input),
//   :deep(.n-base-selection),
//   :deep(.n-base-selection--active) {
//     background: #0000;
//     --n-border: none !important;
//     --n-border-active: none !important;
//     --n-border-hover: none !important;
//     --n-border-focus: none !important;
//     --n-box-shadow-active: none !important;
//     --n-box-shadow-hover: none !important;
//     --n-box-shadow-focus: none !important;
//   }
//   :deep(.n-base-selection) {
//     .n-base-selection-label {
//       background: #0000;
//     }
//   }
// }
</style>
