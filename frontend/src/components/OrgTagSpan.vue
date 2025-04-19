<script setup lang="ts">
const { tag } = defineProps<{
  tag: string
}>()

const store = useSettingsStore()
const name = computed(() => {
  return findName(store.orgTags)
})

// 递归遍历store.orgTags, 根据tagId找到对应的name
function findName(tags: Api.OrgTag[]) {
  for (const item of tags) {
    if (item.tagId === tag) {
      return item.name
    }
    if (item.children && item.children.length > 0) {
      return findName(item.children)
    }
  }
  return '--'
}
</script>

<template>
  <span :id="tag">{{ name }}</span>
</template>

<style scoped>

</style>
