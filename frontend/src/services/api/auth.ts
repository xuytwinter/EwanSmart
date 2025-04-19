/**
 * Register
 *
 * @param username User name
 * @param password Password
 */
export function fetchSignUp(username: string, password: string) {
  return alova.Post('/v1/users/register', { username, password })
}

/**
 * Login
 *
 * @param username User name
 * @param password Password
 */
export function fetchSignIn(username: string, password: string) {
  return alova.Post<Api.LoginToken>('/v1/users/login', { username, password })
}

/**
 * Refresh token
 *
 * @param refreshToken Refresh token
 */
export function fetchRefreshToken(refreshToken: string) {
  return alova.Post<Api.LoginToken>(
    '/v1/auth/refreshToken',
    { refreshToken },
    {
      meta: {
        authRole: 'refreshToken',
      },
    },
  )
}

/**
 * return custom backend error
 *
 * @param code error code
 * @param msg error message
 */
export function fetchCustomBackendError(code: string, msg: string) {
  return alova.Get('/auth/error', {
    params: { code, msg },
    shareRequest: false,
  })
}
