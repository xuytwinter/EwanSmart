/** The storage namespace */
declare namespace StorageType {
  interface Session {
    /** The theme color */
    themeColor: string
    /** The token */
    token: string
  }

  interface Local {
    /** The token */
    token: string
    userId: string
    /** The refresh token */
    refreshToken: string
    /** The theme color */
    themeColor: string
    auth: string[]
  }
}
