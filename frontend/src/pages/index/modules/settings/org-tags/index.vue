<script setup lang="ts">
import type { UnwrapRef } from 'vue'
import { cloneDeep } from 'lodash-es'

function showAdd() {

}

const columns = [
  {
    title: '标签名',
    dataIndex: 'name',
  },
  {
    title: '描述',
    dataIndex: 'description',
    ellipsis: true,
  },
  {
    title: '操作',
    dataIndex: 'action',
    width: 200,
  },
]

const data = ref<Api.OrgTag[]>([
  {
    tagId: '1',
    description: '1',
    name: 'John Brown sr.',
    children: [
      {
        name: 'John Brown',
        tagId: '2',
        description: '2',
        children: [],
      },
    ],
  },
])
const editableData: UnwrapRef<Record<string, Api.OrgTag>> = reactive({})

function addChild(tagId: string) {
  data.value.filter(item => tagId === item.tagId)[0].children.push({
    tagId: '3',
    name: 'John Brown',
    description: '3',
    children: [],
  })
}

function edit(tagId: string) {
  editableData[tagId] = cloneDeep(data.value.filter(item => tagId === item.tagId)[0])
}
function save(tagId: string) {
  Object.assign(data.value.filter(item => tagId === item.tagId)[0], editableData[tagId])
  delete editableData[tagId]
}
function cancel(key: string) {
  delete editableData[key]
}
function remove(tagId: string) {
  console.log(tagId)
}
</script>

<template>
  <div flex-y h-full>
    <div mb-2 flex-bc>
      <a-typography-title :level="5">
        组织标签管理
      </a-typography-title>
      <a-button type="primary" @click="showAdd">
        新增
      </a-button>
    </div>
    <a-table :columns="columns" :data-source="data" size="small">
      <template #bodyCell="{ column, text, record }">
        <template v-if="['name', 'description'].includes(column.dataIndex as string)">
          <a-input
            v-if="editableData[record.tagId]"
            v-model:value="editableData[record.tagId][column.dataIndex as 'name' | 'description']" m-y--5px flex-1
          />
          <template v-else>
            {{ text }}
          </template>
        </template>
        <div v-if="column.dataIndex === 'action'" flex gap-2>
          <template v-if="editableData[record.tagId]">
            <a-button type="primary" size="small" ghost @click="save(record.tagId)">
              保存
            </a-button>
            <a-popconfirm title="确认取消编辑吗?" @confirm="cancel(record.tagId)">
              <a-button size="small">
                取消
              </a-button>
            </a-popconfirm>
          </template>
          <template v-else>
            <a-button type="primary" size="small" ghost @click="addChild(record.tagId)">
              新增
            </a-button>
            <a-button type="primary" size="small" ghost @click="edit(record.tagId)">
              编辑
            </a-button>
            <a-popconfirm title="确定删除该标签吗？" @confirm="remove(record.tagId)">
              <a-button danger size="small">
                删除
              </a-button>
            </a-popconfirm>
          </template>
        </div>
      </template>
    </a-table>
  </div>
</template>

<style scoped>
:deep(.ant-table-cell-with-append) {
  display: flex;
}
</style>
