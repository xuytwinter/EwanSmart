<script setup lang="ts">
import type { UploadProps } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'

const visible = ref(false)

function createDefaultModel(): Api.File.Form {
  return {
    orgTag: null,
    isPublic: false,
    fileList: [],
  }
}

const model = ref<Api.File.Form>(createDefaultModel())

const rules: Record<string, Rule[]> = {
  orgTag: [{ required: true, message: '请选择组织标签' }],
  isPublic: [{ required: true, message: '请选择是否公开' }],
  fileList: [{ required: true, message: '请选择文件' }],
}

function open() {
  model.value = createDefaultModel()
  visible.value = true
}

const formRef = ref()
const loading = ref(false)
const store = useKnowledgeBaseStore()
async function submit() {
  await formRef.value.validate()
  loading.value = true
  if (model.value.fileList!.length > 0)
    await store.enqueueUpload(model.value)

  loading.value = false
  window.$message?.success('操作成功')
  visible.value = false
}

const handleRemove: UploadProps['onRemove'] = () => {
  model.value.fileList = []
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  model.value.fileList = [file]
  return false
}

defineExpose({
  open,
})
</script>

<template>
  <a-modal
    v-model:open="visible" title="上传文件" centered class="w-500px!" :mask-closable="false"
    :confirm-loading="loading" @ok="submit"
  >
    <a-form
      ref="formRef" :model="model" :rules="rules" :colon="false" autocomplete="off" :label-col="{ span: 5 }"
      @finish="submit"
    >
      <a-form-item label="组织标签" name="orgTag">
        <OrgTagSelect v-model:value="model.orgTag" />
      </a-form-item>
      <a-form-item label="是否公开" name="isPublic">
        <a-switch v-model:checked="model.isPublic" />
      </a-form-item>
      <a-form-item label="选择文件" name="fileList">
        <a-upload
          accept=".pdf,.doc,.docx,.xls,.xlsx,.txt,.md" :file-list="model.fileList" name="file" :multiple="false"
          :max-count="1" :before-upload="beforeUpload" @remove="handleRemove"
        >
          <a-button>
            <div flex gap-2 items-center>
              <div i-carbon-cloud-upload />
              选择文件
            </div>
          </a-button>
        </a-upload>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped lang="scss">
</style>
