import type { App } from 'vue'
import { createPinia } from 'pinia'

/** Setup Vue store plugin pinia */
export function setupStore(app: App) {
  const store = createPinia()

  // store.use(resetSetupStore)

  app.use(store)
}
