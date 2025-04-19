declare namespace Api {
  namespace Auth {
    interface LoginToken {
      token: string
    }

    interface UserInfo {
      id: number
      username: string
      role: 'USER' | 'ADMIN'
      orgTags: string[]
      primaryOrg: string
    }
  }

  interface OrgTag {
    tagId?: string
    name: string
    description: string
    parentTag?: string
    children?: OrgTag[]
  }
}
