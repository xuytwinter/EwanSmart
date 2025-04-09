import { createRouter, createWebHistory } from 'vue-router'
import { routes } from 'vue-router/auto-routes'
import { localStg } from '~/utils/storage'

const router = createRouter({
  routes,
  history: createWebHistory(import.meta.env.BASE_URL),
})

router.beforeEach((to, from, next) => {
  const isLogin = Boolean(localStg.get('token'))

  const store = useAuthStore()
  if (isLogin)
    store.getUserInfo()

  if (to.meta.requiresAuth && (!isLogin))
    next('/auth/sign-in')
  else
    next()
})

export default router
