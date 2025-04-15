<script setup lang="ts">
const authStore = useAuthStore()

const settingsStore = useSettingsStore()
function showSettings() {
  settingsStore.visible = true
}
</script>

<template>
  <a-dropdown>
    <a-button type="text">
      <slot>
        {{ authStore.userInfo.username }}
      </slot>
    </a-button>
    <template #overlay>
      <a-menu w-150px>
        <a-menu-item @click="showSettings">
          <div flex gap-4>
            <div i-carbon-settings />
            设置
          </div>
        </a-menu-item>
        <a-menu-item @click="authStore.signOut">
          <div flex gap-4>
            <div i-carbon-logout />
            退出登录
          </div>
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<style scoped></style>
