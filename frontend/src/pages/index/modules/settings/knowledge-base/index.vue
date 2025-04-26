<script setup lang="ts">
import type { FormInstance, PaginationProps } from 'ant-design-vue'
import dayjs from 'dayjs'
import { fileSize } from '~/utils'
import FileUploadDialog from './file-upload-dialog.vue'

const columns = [
  { title: '文件名', dataIndex: 'fileName', width: 120 },
  { title: '文件大小', dataIndex: 'totalSize', width: 200 },
  { title: '文件状态', dataIndex: 'status', width: 100 },
  { title: '组织标签', dataIndex: 'orgTag' },
  { title: '是否公开', dataIndex: 'isPublic' },
  { title: '创建时间', dataIndex: 'createdAt' },
  { title: '操作', dataIndex: 'action', width: 100 },
]

const data = ref<Api.File.Item[]>([])

const form = ref<Api.File.Params>({
  page: 1,
  size: 10,
})

onMounted(async () => {
  await get()
})

const loading = ref(false)
const pagination = ref<PaginationProps>({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number, pageSize: number) => {
    pagination.value.current = page
    pagination.value.pageSize = pageSize
    get()
  },
})

async function get() {
  loading.value = true
  const res = await fetchGetFileList({ ...form.value, page: pagination.value.current, size: pagination.value.pageSize })
  if (res) {
    data.value = res.content
    pagination.value.total = res.totalElements
  }
  loading.value = false
}

const formRef = ref<FormInstance>()
function reset() {
  pagination.value.current = 1
  pagination.value.pageSize = 10
  formRef.value?.resetFields()
  get()
}

const dialogRef = ref<InstanceType<typeof FileUploadDialog>>()
function add() {
  dialogRef.value?.open()
}

async function remove(fileMd5: string) {
  const res = await fetchDeleteFile(fileMd5)
  if (res)
    get()
}
</script>

<template>
  <div flex-y gap-4 h-full>
    <div flex-bc gap-2>
      <a-form ref="formRef" :model="form" layout="inline" size="small" :colon="false">
        <a-form-item label="文件名" name="keyword" w-250px>
          <a-input v-model:value="form.keyword" placeholder="请输入文件名查询" />
        </a-form-item>
      </a-form>
      <div flex gap-2>
        <a-button size="small" @click="reset">
          重置
        </a-button>
        <a-button type="primary" ghost size="small" @click="get">
          查询
        </a-button>
        <a-button type="primary" size="small" @click="add">
          新增
        </a-button>
      </div>
    </div>
    <a-table
      row-key="userId" :scroll="{ y: 420 }" :pagination="pagination" :columns="columns" :data-source="data"
      size="small" :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 0 ? 'blue' : 'green'">
            {{ record.status === 0 ? '上传中' : '已完成' }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'totalSize'">
          {{ fileSize(record.totalSize) }}
        </template>
        <template v-if="column.dataIndex === 'isPublic'">
          <a-tag :color="record.isPublic ? 'green' : 'red'">
            {{ record.isPublic ? '是' : '否' }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'orgTag'">
          <OrgTagSpan :tag="record.orgTag" />
        </template>
        <template v-if="column.dataIndex === 'createdAt'">
          {{ dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-if="column.dataIndex === 'action'">
          <a-popconfirm
            title="确定要删除该文件吗？"
            ok-text="确定"
            cancel-text="取消"
            @confirm="remove(record.fileMd5)"
          >
            <a-button type="link" size="small" class="b-#f0a020 color-#f0a020!">
              删除
            </a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
    <FileUploadDialog ref="dialogRef" @submitted="get()" />
  </div>
</template>

<style scoped></style>
