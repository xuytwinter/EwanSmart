<script setup lang="ts">
import { App } from 'ant-design-vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import dayjs from 'dayjs'
import { createTextVNode, defineComponent } from 'vue'
import 'dayjs/locale/zh-cn'

defineOptions({
  name: 'AppProvider',
})

dayjs.locale('zh-cn')

const locale = zhCN

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
  <a-config-provider :theme="theme" :locale="locale">
    <App class="h-full">
      <ContextHolder />
      <slot />
    </App>
  </a-config-provider>
</template>

<style scoped></style>
