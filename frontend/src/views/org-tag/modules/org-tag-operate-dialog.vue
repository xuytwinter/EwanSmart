<script setup lang="ts">
import type { FormRules } from 'naive-ui';
import type { FlatResponseData } from '~/packages/axios/src';

defineOptions({
  name: 'OrgTagOperateDialog'
});

const props = defineProps<{
  operateType: NaiveUI.TableOperateType;
  rowData: Api.OrgTag.Item;
  data: Api.OrgTag.Item[];
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const title = computed(() => {
  const titles: Record<NaiveUI.TableOperateType, string> = {
    add: '新增',
    edit: '编辑',
    addChild: '新增下级'
  };
  return titles[props.operateType];
});

const model = ref<Api.OrgTag.Item>(createDefaultModel());

function createDefaultModel(): Api.OrgTag.Item {
  return {
    name: '',
    description: '',
    parentTag: null
  };
}

const rules = ref<FormRules>({
  name: defaultRequiredRule,
  description: defaultRequiredRule
});

async function handleUpdateModelWhenEdit() {
  model.value = createDefaultModel();

  if (props.operateType === 'edit') model.value = props.rowData;
  else if (props.operateType === 'addChild') model.value.parentTag = props.rowData.tagId!;
}

function close() {
  visible.value = false;
}

async function handleSubmit() {
  await validate();
  let res: FlatResponseData;
  if (model.value.tagId) res = await request({ url: `/admin/org-tags/${model.value.tagId}`, method: 'PUT' });
  else res = await request({ url: '/admin/org-tags', method: 'POST' });
  if (!res.error) {
    window.$message?.success('操作成功');
    close();
    emit('submitted');
  }
}

watch(visible, () => {
  if (visible.value) {
    handleUpdateModelWhenEdit();
    restoreValidation();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    :title="title"
    :show-icon="false"
    :mask-closable="false"
    class="w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <NFormItem label="标签名称" path="name">
        <NInput v-model:value="model.name" placeholder="请输入标签名称" maxlength="30" />
      </NFormItem>
      <NFormItem label="所属标签" path="parentTag">
        <OrgTagCascader v-model:value="model.parentTag" :options="data" />
      </NFormItem>
      <NFormItem label="标签描述" path="description">
        <NInput
          v-model:value="model.description"
          type="textarea"
          placeholder="请输入标签描述"
          maxlength="300"
          clearable
          show-count
          :autosize="{ minRows: 3, maxRows: 10 }"
        />
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" @click="handleSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped></style>
