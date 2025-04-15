<script setup lang="ts">
import { Welcome } from 'ant-design-x-vue'
</script>

<template>
  <div class="flex-y w-full items-center">
    <Welcome
      class="w-50vw"
      icon="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*s5sNRo5LjfQAAAAAAAAAAAAADgCCAQ/fmt.webp"
      title="我是派聪明，很高兴见到你！"
      description="派聪明是一个基于Java技术体系的「大模型问答系统」项目，都将成为你技术成长的加速器。通过这个项目，你不仅能系统掌握企业级开发全流程，还能获得AI时代最前沿的工程实践经验，为你的简历增添一抹亮色。"
    />
  </div>
</template>

<style scoped>
:deep(.ant-welcome) {
  background-image: linear-gradient(97deg, rgba(90, 196, 255, 0.12) 0%, rgba(174, 136, 255, 0.12) 100%);
  .ant-welcome-icon {
    width: 82px;
  }
  .ant-welcome-title {
    text-align: left;
  }
  .ant-welcome-description {
    text-align: left;
  }
}
</style>
