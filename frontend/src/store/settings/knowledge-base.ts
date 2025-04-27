export const useKnowledgeBaseStore = defineStore('knowledge-base', () => {
  const list = ref<Api.File.Item[]>([])

  return {
    list,
  }
})
