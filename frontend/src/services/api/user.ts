/** Get user info */
export function fetchGetUserInfo() {
  return alova.Get<Api.User.Info>('/v1/users/me')
}

export function fetchGetUserList(params: Api.User.Params) {
  params.page ??= 1
  params.size ??= 10
  return alova.Get<Api.User.List>('/v1/admin/users', { params })
}
