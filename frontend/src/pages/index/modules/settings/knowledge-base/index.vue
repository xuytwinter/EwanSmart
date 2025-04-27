<script setup lang="ts">
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

const { list } = storeToRefs(useKnowledgeBaseStore())

onMounted(async () => {
  await get()
})

const loading = ref(false)

async function get() {
  loading.value = true
  const res = await fetchGetFileList()
  if (res) {
    list.value = res
  }
  loading.value = false
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
    <div mb-2 flex-bc>
      <a-typography-title :level="5">
        知识库管理
      </a-typography-title>
      <div flex gap-2>
        <a-button size="small" @click="get()">
          刷新
        </a-button>
        <a-button type="primary" size="small" @click="add()">
          新增
        </a-button>
      </div>
    </div>
    <a-table
      row-key="userId" :scroll="{ y: 420 }" :pagination="false" :columns="columns" :data-source="list"
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
          <a-popconfirm title="确定要删除该文件吗？" ok-text="确定" cancel-text="取消" @confirm="remove(record.fileMd5)">
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
