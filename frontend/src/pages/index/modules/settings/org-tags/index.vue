<script setup lang="ts">
import OrgTagsOperateModal from './org-tags-operate-modal.vue'

const columns = [
  { title: '标签名', dataIndex: 'name' },
  { title: '描述', dataIndex: 'description', ellipsis: true },
  { title: '操作', dataIndex: 'action', width: 200 },
]

const data = ref<Api.OrgTag[]>([])

onMounted(async () => {
  await get()
})

const loading = ref(false)
const store = useSettingsStore()
async function get() {
  loading.value = true
  const res = await store.getOrgTags()
  data.value = res
  loading.value = false
}

const modalRef = ref<InstanceType<typeof OrgTagsOperateModal>>()
function showAdd(tagId?: string) {
  modalRef.value?.openAdd(tagId)
}

function showEdit(tag: Api.OrgTag) {
  modalRef.value?.openEdit(tag)
}

async function remove(tagId: string) {
  const res = await fetchDeleteOrgTag(tagId)
  if (res)
    get()
}
</script>

<template>
  <div flex-y h-full>
    <div mb-2 flex-bc>
      <a-typography-title :level="5">
        组织标签管理
      </a-typography-title>
      <div flex gap-2>
        <a-button size="small" @click="get()">
          刷新
        </a-button>
        <a-button type="primary" size="small" @click="showAdd()">
          新增
        </a-button>
      </div>
    </div>
    <a-table
      row-key="tagId" :scroll="{ y: 420 }" :pagination="false" :columns="columns" :data-source="data"
      size="small" :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <div v-if="column.dataIndex === 'action'" flex gap-2>
          <a-button type="primary" size="small" ghost @click="showAdd(record.tagId)">
            新增
          </a-button>
          <a-button type="primary" size="small" ghost class="color-#f0a020! b-#f0a020!" @click="showEdit(record as Api.OrgTag)">
            编辑
          </a-button>
          <a-popconfirm title="确定删除该标签吗？" @confirm="remove(record.tagId)">
            <a-button danger size="small">
              删除
            </a-button>
          </a-popconfirm>
        </div>
      </template>
    </a-table>
    <OrgTagsOperateModal ref="modalRef" @submitted="get" />
  </div>
</template>

<style scoped>
:deep(.ant-table-cell-with-append) {
  display: flex;
}
</style>
