import { localStg } from '~/utils/storage'

/** Get token */
export function getTokenStg() {
  return localStg.get('token') || ''
}

export function getDefaultUserInfo(): Api.UserInfo {
  return {
    id: 0,
    username: '',
    role: 'USER',
    orgTags: [],
    primaryOrg: '',
  }
}

type WithChildren<T> = T & {
  children?: WithChildren<T>[]
}

export function flatArray<T>(data: WithChildren<T>[]): T[] {
  let result: T[] = []

  data.forEach((item) => {
    const newItem = { ...item }
    delete newItem.children
    result.push(newItem)
    if (item.children && item.children.length > 0) {
      result = result.concat(flatArray(item.children))
    }
  })

  return result
}
