<script setup lang="ts">
import { storeToRefs } from 'pinia'
import OrgTags from './org-tags/index.vue'
import UserManage from './user-manage/index.vue'

const { visible } = storeToRefs(useSettingsStore())

const activeKey = ref('org-tags')

const { isAdmin } = storeToRefs(useAuthStore())
</script>

<template>
  <a-modal v-model:open="visible" title="设置" centered class="w-1200px!" :mask-closable="false">
    <a-tabs v-model:active-key="activeKey" tab-position="left" :tab-bar-gutter="0">
      <a-tab-pane v-if="isAdmin" key="org-tags">
        <template #tab>
          <span class="tab">
            <div i-carbon-chart-treemap />
            组织标签
          </span>
        </template>
        <OrgTags />
      </a-tab-pane>
      <a-tab-pane v-if="isAdmin" key="user-manage">
        <template #tab>
          <span class="tab">
            <div i-carbon-id-management />
            用户管理
          </span>
        </template>
        <UserManage />
      </a-tab-pane>
      <a-tab-pane key="account">
        <template #tab>
          <span class="tab">
            <div i-carbon-user-avatar />
            账号
          </span>
        </template>
        account
      </a-tab-pane>
      <a-tab-pane key="about">
        <template #tab>
          <span class="tab">
            <div i-carbon-information />
            关于
          </span>
        </template>
        about
      </a-tab-pane>
    </a-tabs>
  </a-modal>
</template>

<style scoped lang="scss">
.tab {
  display: flex;
  gap: 8px;
  align-items: center;
}
:deep(.ant-tabs-tab) {
  padding-left: 0 !important;
}

:deep(.ant-tabs-content) {
  height: 500px;
}
</style>
