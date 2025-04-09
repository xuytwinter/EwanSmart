import { createAlovaMockAdapter } from '@alova/mock'
import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import mock from '../mocks'
import { getAuthorization } from './shared'

export const mockRequestAdapter = createAlovaMockAdapter(
  [mock],
  { // 全局控制是否启用mock接口，默认为true
    enable: true,
    // 非模拟请求适配器，用于未匹配mock接口时发送请求
    httpAdapter: adapterFetch(),
    // mock接口响应延迟，单位毫秒
    delay: 1000,
    // 是否打印mock接口请求信息
    mockRequestLogger: true,
    matchMode: 'methodurl',

    onMockResponse: ({ body, responseHeaders, status = 200, statusText = 'ok' }) => {
      return {
        response: new Response(JSON.stringify(body), {
          status,
          statusText,
        }),
        headers: new Headers(responseHeaders),
      }
    },
  },
)

export const alova = createAlova(
  {
    baseURL: '/api',
    // 当使用alova/fetch请求适配器时，由于window.fetch的特点
    // 只有在连接超时或连接中断时才会触发onError拦截器
    // 其他情况均会触发onSuccess拦截器
    // https://developer.mozilla.org/docs/Web/API/fetch
    requestAdapter: import.meta.env.DEV ? mockRequestAdapter : adapterFetch(),
    beforeRequest(method) {
      const auth = getAuthorization()
      if (auth)
        method.config.headers.Authorization = auth

      if (!method.meta)
        method.meta = {}

      if (['POST', 'PUT', 'DELETE'].includes(method.type) && (method.meta.autoTips === null || method.meta.autoTips === undefined)) {
        method.meta.autoTips = true
      }
    },
    // 使用 responded 对象分别指定请求成功的拦截器和请求失败的拦截器
    responded: {
      // 请求成功的拦截器
      // 当使用 `alova/fetch` 请求适配器时，第一个参数接收Response对象
      // 第二个参数为当前请求的method实例，你可以用它同步请求前后的配置信息
      onSuccess: async (response, method) => {
        if (response.status >= 400)
          throw new Error(response.statusText)

        const json = await response.json()

        // 业务逻辑成功
        if (json.code >= 200 && json.code < 300) {
          if (method.config?.meta?.autoTips === true)
            window.$message?.success('操作成功')
          return Promise.resolve(json.data)
        }

        throw new Error(json.message || '请求失败')
      },

      // 请求失败的拦截器
      // 请求错误时将会进入该拦截器。
      // 第二个参数为当前请求的method实例，你可以用它同步请求前后的配置信息
      onError: (error) => {
        window.$message?.error(error.message)
        return Promise.resolve()
      },
      // 请求完成的拦截器
      // 当你需要在请求不论是成功、失败、还是命中缓存都需要执行的逻辑时
      // 可以在创建alova实例时指定全局的`onComplete`拦截器，例如关闭请求 loading 状态。
      // 参数为当前请求的method实例
      onComplete: () => {
        // 处理请求完成逻辑
      },
    },

  },

)
