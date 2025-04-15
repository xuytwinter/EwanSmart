/** Get user info */
export function fetchGetOrgTree() {
  return alova.Get<Api.OrgTag[]>('/v1/admin/org-tags/tree')
}
