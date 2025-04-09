<script setup lang="ts">
const store = useAuthStore()
</script>

<template>
  <a-dropdown>
    <a-button type="text">
      <slot>
        {{ store.userInfo.username }}
      </slot>
    </a-button>
    <template #overlay>
      <a-menu w-150px>
        <a-menu-item @click="store.signOut">
          <div flex gap-4>
            <div i-carbon-settings />
            设置
          </div>
        </a-menu-item>
        <a-menu-item @click="store.signOut">
          <div flex gap-4>
            <div i-carbon-logout />
            退出登录
          </div>
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<style scoped>

</style>
