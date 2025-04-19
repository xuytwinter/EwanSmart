<script setup lang="ts">
import { TreeSelect } from 'ant-design-vue'

const emit = defineEmits<{
  submitted: []
}>()
const SHOW_PARENT = TreeSelect.SHOW_PARENT
const visible = ref(false)
const title = ref('组织标签设置')

const model = ref<{ orgTags: string[] }>({ orgTags: [] })

let userId = ''
function open(user: Api.User.Item) {
  title.value = `组织标签设置 - ${user.username}`
  userId = user.userId
  model.value.orgTags = user.orgTags
  visible.value = true
}

const formRef = ref()
const loading = ref(false)
async function submit() {
  await formRef.value.validate()
  loading.value = true
  await alova.Put(`/v1/admin/users/${userId}/org-tags`, model.value)
  loading.value = false
  window.$message?.success('操作成功')
  visible.value = false
  emit('submitted')
}

defineExpose({
  open,
})
</script>

<template>
  <a-modal
    v-model:open="visible" :title="title" centered class="w-500px!" :mask-closable="false"
    :confirm-loading="loading" @ok="submit"
  >
    <a-form ref="formRef" :model="model" :colon="false" autocomplete="off" :label-col="{ span: 5 }" @finish="submit">
      <a-form-item label="组织标签" name="orgTags" :rules="[{ required: true, message: '请选择组织标签' }]">
        <OrgTagSelect v-model:value="model.orgTags" tree-checkable :show-checked-strategy="SHOW_PARENT" private-disabled />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped lang="scss"></style>
