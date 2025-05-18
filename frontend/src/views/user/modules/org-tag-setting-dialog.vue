<script setup lang="ts">
import type { FormRules } from 'naive-ui';

defineOptions({
  name: 'OrgTagSettingDialog'
});

const props = defineProps<{
  rowData: Api.User.Item;
}>();

const emit = defineEmits<{ submitted: [] }>();

const visible = defineModel<boolean>('visible', { default: false });

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

type Model = {
  orgTags: string[];
};

const model = ref<Model>(createDefaultModel());

function createDefaultModel(): Model {
  return {
    orgTags: []
  };
}

const rules = ref<FormRules>({
  orgTags: defaultRequiredRule
});

async function handleUpdateModelWhenEdit() {
  model.value = createDefaultModel();
  model.value.orgTags = props.rowData.orgTags.map(tag => tag.tagId!);
}

function close() {
  visible.value = false;
}

async function handleSubmit() {
  await validate();
  const res = await request({ url: `/admin/users/${props.rowData.userId}/org-tags`, method: 'PUT' });
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
    title="组织标签设置"
    :show-icon="false"
    :mask-closable="false"
    class="w-500px!"
    @positive-click="handleSubmit"
  >
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100" mt-10>
      <NFormItem label="组织标签" path="orgTags">
        <NInput :value="rowData.username" readonly />
      </NFormItem>
      <NFormItem label="组织标签" path="orgTags">
        <OrgTagCascader v-model:value="model.orgTags" multiple />
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
