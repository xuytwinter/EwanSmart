import type { RouteLocationNamedRaw, RouteLocationRaw } from 'vue-router'
import type { RouteNamedMap } from 'vue-router/auto-routes'

export function useRouterPush() {
  const router = useRouter()
  const route = useRoute()

  const routerPush = router.push

  async function routerPushByKey(key: keyof RouteNamedMap, options?: App.Global.RouterPushOptions) {
    const { query, params } = options || {}

    const routeLocation: RouteLocationNamedRaw = {
      name: key,
    }

    if (Object.keys(query || {}).length) {
      (routeLocation as RouteLocationNamedRaw).query = query
    }

    if (Object.keys(params || {}).length) {
      routeLocation.params = params
    }

    return routerPush(routeLocation as RouteLocationRaw)
  }

  /**
   * Navigate to login page
   *
   * @param redirectUrl The redirect url, if not specified, it will be the current route fullPath
   */
  async function toLogin(redirectUrl?: string) {
    const redirect = redirectUrl || route.fullPath

    const options: App.Global.RouterPushOptions = {
      query: {
        redirect,
      },
    }

    return routerPushByKey('sign-in', options)
  }

  async function toHome() {
    return routerPushByKey('home')
  }

  /**
   * Redirect from login
   *
   * @param [needRedirect] Whether to redirect after login. Default is `true`
   */
  async function redirectFromLogin(needRedirect = true) {
    const redirect = route.query?.redirect as string

    if (needRedirect && redirect) {
      await routerPush(redirect)
    }
    else {
      await toHome()
    }
  }

  return {
    redirectFromLogin,
    toLogin,
  }
}
