<script setup lang="ts">
import { App } from 'ant-design-vue'
import { createTextVNode, defineComponent } from 'vue'

defineOptions({
  name: 'AppProvider',
})

const ContextHolder = defineComponent({
  name: 'ContextHolder',
  setup() {
    const { message, modal, notification } = App.useApp()

    function register() {
      window.$message = message
      window.$modal = modal
      window.$notification = notification
    }

    register()

    return () => createTextVNode()
  },
})

const theme = ref({
  token: {
    colorPrimary: '#00b96b',
  },
})
</script>

<template>
  <a-config-provider :theme="theme">
    <App class="h-full">
      <ContextHolder />
      <slot />
    </App>
  </a-config-provider>
</template>

<style scoped></style>
