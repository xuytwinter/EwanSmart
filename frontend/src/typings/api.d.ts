declare namespace Api {
  interface PageParams {
    page?: number
    size?: number
    keyword?: string
  }

  interface PageList<T> {
    totalElements: number
    totalPages: number
    size: number
    number: number
    content: T[]
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

    interface Params extends PageParams {
      orgTag?: string
      status?: UserStatus
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

    interface List extends PageList<Item> { }
  }

  interface OrgTag {
    tagId?: string
    name: string
    description: string
    parentTag?: string
    disabled?: boolean
    children?: OrgTag[]
  }

  namespace File {
    interface Params extends PageParams {
      fileName?: string
    }

    interface Item {
      fileName: string
      totalSize: number
      status: 0 | 1 // 0=上传中,1=已完成
      orgTag: string
      isPublic: boolean
      createdAt: string
    }

    interface List extends PageList<Item> { }
  }
}
