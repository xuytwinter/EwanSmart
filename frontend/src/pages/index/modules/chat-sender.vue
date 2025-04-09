<script setup lang="tsx">
import { message as messageAnt } from 'ant-design-vue'
import { Attachments, Sender } from 'ant-design-x-vue'

const [message, contextHolder] = messageAnt.useMessage()

const open = ref(false)
const items = ref<any[]>([])
const text = ref('')

const senderRef = ref<InstanceType<typeof Sender> | null>(null)

function senderSubmit() {
  items.value = []
  text.value = ''
  message.success('Send message successfully!')
}

function setOpen(v: boolean) {
  open.value = v
}

function handleFileChange({ fileList }: { fileList: any[] }) {
  items.value = fileList
}
</script>

<template>
  <context-holder />
  <Sender ref="senderRef" placeholder="给小派说句话吧" :value="text" @change="v => text = v" @submit="senderSubmit">
    <template #header>
      <Sender.Header title="附件" :open="open" force-render @open-change="setOpen">
        <Attachments
          :before-upload="() => false"
          :items="items"
          :placeholder="(type) =>
            type === 'drop'
              ? { title: '拖拽文件到这里' }
              : {
                icon: h('div', { class: 'i-carbon-cloud-upload text-4xl' }),
                title: '上传文件',
                description: '点击或拖拽文件到这里上传',
              }
          "
          @change="handleFileChange"
        />
      </Sender.Header>
    </template>
    <template #prefix>
      <a-button type="text" class="p-2!" @click="() => open = !open">
        <div i-carbon-attachment />
      </a-button>
    </template>
  </Sender>
</template>

<style scoped>

</style>
