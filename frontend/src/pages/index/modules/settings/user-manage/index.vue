<script setup lang="ts">
import type { FormInstance, PaginationProps } from 'ant-design-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import OrgTagsSettingModal from './org-tags-setting-modal.vue'

dayjs.extend(relativeTime)

const columns = [
  { title: '用户名', dataIndex: 'username', width: 120 },
  { title: '邮箱', dataIndex: 'email', width: 200 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '最后登录时间', dataIndex: 'lastLoginTime', width: 150 },
  { title: '组织标签', dataIndex: 'orgTags' },
  { title: '操作', dataIndex: 'action', width: 100 },
]

const data = ref<Api.User.Item[]>([])

const form = ref<Api.User.Params>({
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
  const res = await fetchGetUserList({ ...form.value, page: pagination.value.current, size: pagination.value.pageSize })
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

const statusOptions = [
  { label: '启用', value: 1 },
  { label: '禁用', value: 0 },
]

const modalRef = ref<InstanceType<typeof OrgTagsSettingModal>>()
function handleOrg(record: Api.User.Item) {
  modalRef.value?.open(record)
}
</script>

<template>
  <div flex-y gap-4 h-full>
    <div flex-bc gap-2>
      <a-form ref="formRef" :model="form" layout="inline" size="small" :colon="false">
        <a-form-item label="用户名" name="keyword" w-250px>
          <a-input v-model:value="form.keyword" placeholder="请输入用户名查询" />
        </a-form-item>
        <a-form-item label="组织标签" name="orgTag" w-250px>
          <OrgTagSelect v-model:value="form.orgTag" />
        </a-form-item>
        <a-form-item label="状态" name="status" w-250px>
          <a-select v-model:value="form.status" :options="statusOptions" placeholder="请选择状态" />
        </a-form-item>
      </a-form>
      <div flex gap-2>
        <a-button type="primary" ghost size="small" @click="reset()">
          重置
        </a-button>
        <a-button type="primary" size="small" @click="get()">
          查询
        </a-button>
      </div>
    </div>
    <a-table
      row-key="userId" :scroll="{ y: 420 }" :pagination="pagination" :columns="columns" :data-source="data"
      size="small" :loading="loading"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">
            {{ record.status === 1 ? '启用' : '禁用' }}
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'lastLoginTime'">
          {{ dayjs(record.lastLoginTime).fromNow() }}
        </template>
        <template v-if="column.dataIndex === 'orgTags'">
          <a-tag v-for="tag in record.orgTags" :key="tag" :color="record.primaryOrg === tag ? 'blue' : 'default'" cursor-pointer>
            <OrgTagSpan :tag="tag" />
          </a-tag>
        </template>
        <template v-if="column.dataIndex === 'action'">
          <a-button type="link" size="small" class="b-#f0a020 color-#f0a020!" @click="handleOrg(record as Api.User.Item)">
            组织设置
          </a-button>
        </template>
      </template>
    </a-table>
    <OrgTagsSettingModal ref="modalRef" @submitted="get" />
  </div>
</template>

<style scoped>

</style>
