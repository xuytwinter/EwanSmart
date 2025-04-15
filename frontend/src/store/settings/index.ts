import { defineStore } from 'pinia'

export const useSettingsStore = defineStore('settings', () => {
  const visible = ref(false)

  return {
    visible,
  }
})
