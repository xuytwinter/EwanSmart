declare namespace Api {
  interface PageParams {
    page?: number
    size?: number
    keyword?: string
  }

  type UserStatus = 0 | 1 // 0=禁用，1=启用

  interface LoginToken {
    token: string
  }

  namespace User {
    interface Info {
      id: number
      username: string
      role: 'USER' | 'ADMIN'
      orgTags: string[]
      primaryOrg: string
    }
    interface Item {
      userId: string
      username: string
      email: string
      orgTags: string[]
      primaryOrg: string
      createTime: string
      lastLoginTime: string
      status: UserStatus
    }

    interface Params extends PageParams {
      orgTag?: string
      status?: UserStatus
    }
    interface List {
      totalElements: number
      totalPages: number
      size: 20
      number: 0
      content: Item[]
    }
  }

  interface OrgTag {
    tagId?: string
    name: string
    description: string
    parentTag?: string
    disabled?: boolean
    children?: OrgTag[]
  }

}
