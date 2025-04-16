<script setup lang="ts">
function showAdd() {

}

const columns = [
  {
    title: '标签名',
    dataIndex: 'name',
    key: 'name',
  },
  {
    title: '描述',
    dataIndex: 'description',
    key: 'description',
    ellipsis: true,
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
  },
]

const data: Api.OrgTag[] = [
  {
    tagId: '1',
    description: '1',
    name: 'John Brown sr.',
    children: [
      {
        name: 'John Brown',
        tagId: '1',
        description: '1',
        children: [],
      },
    ],
  },
]

function showEdit(record: Api.OrgTag) {
  console.log('%c [ record ]: ', 'color: #bf2c9f; background: pink; font-size: 13px;', record)
}

function onDelete(tagId: string) {
  console.log('%c [ tagId ]: ', 'color: #bf2c9f; background: pink; font-size: 13px;', tagId)
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
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-button type="primary" ghost size="small" @click="showEdit(record as Api.OrgTag)">
            编辑
          </a-button>
          <a-popconfirm
            title="确定删除该标签吗？"
            @confirm="onDelete(record.tagId)"
          >
            <a-button danger ghost size="small" ml-4 @click="onDelete(record.tagId)">
              删除
            </a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>
  </div>
</template>

<style scoped></style>
