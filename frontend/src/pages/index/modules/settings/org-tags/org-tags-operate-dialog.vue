<script setup lang="ts">
import { cloneDeep } from 'lodash-es'

const emit = defineEmits<{
  submitted: []
}>()

const visible = ref(false)
const title = ref('')

function createDefaultModel() {
  return {
    tagId: '',
    name: '',
    description: '',
    parentTag: '',
  }
}

const model = ref<Api.OrgTag>(createDefaultModel())

let type: 'add' | 'edit' = 'add'

function openAdd(tagId?: string) {
  type = 'add'
  model.value = createDefaultModel()
  if (tagId) {
    model.value.parentTag = tagId
    title.value = '新增子标签'
  }
  else {
    title.value = '新增标签'
  }
  visible.value = true
}

function openEdit(tag: Api.OrgTag) {
  title.value = '编辑'
  model.value = cloneDeep(tag)
  type = 'edit'
  visible.value = true
}

const formRef = ref()
const loading = ref(false)
async function submit() {
  await formRef.value.validate()
  loading.value = true
  if (type === 'add') {
    await alova.Post('/v1/admin/org-tags', model.value)
  }
  else {
    await alova.Put(`/v1/admin/org-tags/${model.value.tagId}`, model.value)
  }
  loading.value = false
  window.$message?.success('操作成功')
  visible.value = false
  emit('submitted')
}

defineExpose({
  openAdd,
  openEdit,
})
</script>

<template>
  <a-modal
    v-model:open="visible" :title="title" centered class="w-500px!" :mask-closable="false"
    :confirm-loading="loading" @ok="submit"
  >
    <a-form ref="formRef" :model="model" :colon="false" autocomplete="off" :label-col="{ span: 5 }" @finish="submit">
      <a-form-item label="标签名称" name="name" :rules="[{ required: true, message: '请输入标签名称' }]">
        <a-input v-model:value="model.name" clearable placeholder="请输入标签名称" />
      </a-form-item>
      <a-form-item label="标签描述" name="description" :rules="[{ required: true, message: '请输入标签描述' }]">
        <a-input v-model:value="model.description" clearable placeholder="请输入标签描述" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped lang="scss"></style>
