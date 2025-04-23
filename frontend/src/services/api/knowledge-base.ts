export function fetchGetFileList(params: Api.File.Params) {
  params.page ??= 1
  params.size ??= 10
  return alova.Get<Api.File.List>('/v1/admin/files', { params })
}
