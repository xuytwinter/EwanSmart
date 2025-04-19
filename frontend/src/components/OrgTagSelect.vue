<script setup lang="ts">
import { cloneDeep } from 'lodash-es'

const { privateDisabled = false } = defineProps<{
  privateDisabled?: boolean
}>()

const value = defineModel<string | string[] | undefined>('value')

const store = useSettingsStore()

const orgTags = computed(() => {
  const tags = cloneDeep(store.orgTags)
  if (privateDisabled) {
    tags.forEach((item) => {
      item.disabled = item.tagId?.startsWith('PRIVATE_')
    })
  }
  return tags
})
</script>

<template>
  <a-tree-select
    v-model:value="value" :tree-data="orgTags" :field-names="{ label: 'name', value: 'tagId' }"
    show-search tree-node-filter-prop="name" allow-clear placeholder="请选择组织标签"
  />
</template>

<style scoped>

</style>
