<script lang="ts" setup>
import type { CascaderOption } from 'naive-ui';

defineOptions({
  name: 'OrgTagCascader'
});
const props = defineProps<{
  options?: Api.OrgTag.Item[];
}>();
const model = defineModel<string | number | Array<number | string> | undefined | null>('value', { required: true });

const opts = ref<CascaderOption[]>([]);

async function getOptions() {
  const { error, data } = await fetchGetOrgTagList();
  if (!error) opts.value = data.data as unknown as CascaderOption[];
}

onMounted(() => {
  if (props.options) {
    opts.value = props.options as unknown as CascaderOption[];
  } else {
    getOptions();
  }
});
</script>

<template>
  <NCascader
    v-model:value="model"
    placeholder="请选择组织标签"
    :options="opts"
    value-field="tagId"
    label-field="name"
    expand-trigger="hover"
  />
</template>
