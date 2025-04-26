<script setup lang="ts">
import type { UploadProps } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'

const emit = defineEmits<{
  submitted: []
}>()

const visible = ref(false)

function createDefaultModel(): Api.File.Form {
  return {
    orgTag: null,
    isPublic: false,
    fileMd5: '',
    chunkIndex: 0,
    totalSize: 0,
    fileName: '',
  }
}

const model = ref<Api.File.Form>(createDefaultModel())

const rules: Record<string, Rule[]> = {
  orgTag: [{ required: true, message: '请选择组织标签' }],
  isPublic: [{ required: true, message: '请选择是否公开' }],
}

function open() {
  model.value = createDefaultModel()
  visible.value = true
}

const fileList = ref<UploadProps['fileList']>([])
const formRef = ref()
const loading = ref(false)
const headers = ref({})
async function submit() {
  await formRef.value.validate()
  loading.value = true

  loading.value = false
  window.$message?.success('操作成功')
  visible.value = false
  emit('submitted')
}

const handleRemove: UploadProps['onRemove'] = () => {
  fileList.value = []
}

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  fileList.value = [file]
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
      <a-form-item label="选择文件" name="fileName" required>
        <a-upload
          :file-list="fileList" name="file" :multiple="false" :headers="headers" :before-upload="beforeUpload"
          @remove="handleRemove"
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

<style scoped lang="scss"></style>
