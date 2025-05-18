<script setup lang="ts">
import { enableStatusOptions } from '@/constants/common';

defineOptions({
  name: 'UserSearch'
});

const emit = defineEmits<{
  reset: [];
  search: [];
}>();

const { formRef, validate, restoreValidation } = useNaiveForm();

const model = defineModel<Api.User.SearchParams>('model', { required: true });

async function reset() {
  await restoreValidation();
  emit('reset');
}

async function search() {
  await validate();
  emit('search');
}
</script>

<template>
  <NCard :bordered="false" size="small" class="card-wrapper">
    <NCollapse>
      <NCollapseItem title="搜索" name="user-search">
        <NForm ref="formRef" :model="model" label-placement="left" :label-width="100">
          <NGrid responsive="screen" item-responsive>
            <NFormItemGi span="24 s:12 m:6" label="关键词" path="keyword" class="pr-24px">
              <NInput v-model:value="model.keyword" placeholder="请输入关键词" clearable />
            </NFormItemGi>
            <NFormItemGi span="24 s:12 m:6" label="组织标签" path="userGender" class="pr-24px">
              <OrgTagCascader v-model:value="model.orgTag" clearable />
            </NFormItemGi>
            <NFormItemGi span="24 s:12 m:6" label="启用状态" path="status" class="pr-24px">
              <NSelect
                v-model:value="model.status"
                placeholder="请选择启用状态"
                :options="enableStatusOptions"
                clearable
              />
            </NFormItemGi>
            <NFormItemGi span="24 m:6">
              <NSpace class="w-full" justify="end">
                <NButton @click="reset">
                  <template #icon>
                    <icon-ic-round-refresh class="text-icon" />
                  </template>
                  重置
                </NButton>
                <NButton type="primary" ghost @click="search">
                  <template #icon>
                    <icon-ic-round-search class="text-icon" />
                  </template>
                  搜索
                </NButton>
              </NSpace>
            </NFormItemGi>
          </NGrid>
        </NForm>
      </NCollapseItem>
    </NCollapse>
  </NCard>
</template>

<style scoped></style>
