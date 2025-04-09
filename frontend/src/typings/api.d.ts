declare namespace Api {
  namespace Auth {
    interface LoginToken {
      token: string
    }

    interface UserInfo {
      id: number
      username: string
      role: "USER" | "ADMIN"
      orgTags: string[]
      primaryOrg: string
    }
  }
}
