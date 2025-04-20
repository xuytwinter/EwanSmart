/** Get user info */
export function fetchGetOrgTree() {
  return alova.Get<Api.OrgTag[]>('/v1/admin/org-tags/tree')
}

export function fetchDeleteOrgTag(tagId: string) {
  return alova.Delete(`/v1/admin/org-tags/${tagId}`)
}

export function fetchSetPrimaryOrg(params: { userId: string, primaryOrg: string }) {
  return alova.Put(`/v1/users/primary-org`, params)
}
