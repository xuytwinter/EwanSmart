<script setup lang="ts">
const list = ref<Api.OrgTag[]>([])

function showAdd() {

}
</script>

<template>
  <div flex-y h-full>
    <div flex-bc>
      <a-typography-title :level="5">
        组织标签管理
      </a-typography-title>
      <a-button type="primary" @click="showAdd">
        新增
      </a-button>
    </div>
    <div v-if="list.length" flex-1>
      <div v-for="(item, index) in list" :key="index">
        {{ item.name }}
      </div>
    </div>
    <a-empty v-else flex-y flex-1 justify-center />
  </div>
</template>

<style scoped></style>
