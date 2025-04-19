import { defineStore } from 'pinia'

export const useSettingsStore = defineStore('settings', () => {
  const visible = ref(false)

  const orgTags = ref<Api.OrgTag[]>([])

  async function getOrgTags(privateDisabled = false) {
    const res = await fetchGetOrgTree()
    if (privateDisabled) {
      res.forEach((item) => {
        item.disabled = item.tagId?.startsWith('PRIVATE_')
      })
    }

    orgTags.value = res || []
    return orgTags.value
  }

  return {
    visible,
    orgTags,
    getOrgTags,
  }
})
