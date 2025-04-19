import { defineStore } from 'pinia'
import { useRouterPush } from '~/composables/router'
import { localStg } from '~/utils/storage'
import { getDefaultUserInfo, getTokenStg } from './shared'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getTokenStg())

  const isLogin = computed(() => Boolean(token.value))

  const userInfo = ref<Api.UserInfo>(getDefaultUserInfo())

  const { redirectFromLogin, toLogin } = useRouterPush()

  async function signIn(username: string, password: string, redirect = true) {
    const res = await fetchSignIn(username, password)
    if (!res)
      return

    localStg.set('token', res.token)
    token.value = res.token

    const pass = await getUserInfo()
    if (pass)
      await redirectFromLogin(redirect)
  }

  function signOut() {
    window.$modal?.confirm({
      title: '退出登录',
      content: '确定要退出登录吗？',
      okText: '确定',
      cancelText: '取消',
      onOk: () => {
        localStg.remove('token')
        token.value = ''
        userInfo.value = getDefaultUserInfo()
        toLogin()
      },
    })
  }
  async function getUserInfo() {
    const res = await fetchGetUserInfo()
    if (res) {
      userInfo.value = res
      return true
    }
    return false
  }

  return {
    signIn,
    signOut,
    userInfo,
    isLogin,
    getUserInfo,
  }
})
