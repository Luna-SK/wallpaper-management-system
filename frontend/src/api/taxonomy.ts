import { http } from './http'

export interface Category {
  id: string
  code: string
  name: string
  sortOrder: number
  enabled: boolean
  imageCount: number
}

export interface TagGroup {
  id: string
  code: string
  name: string
  sortOrder: number
  enabled: boolean
  tagCount: number
}

export interface Tag {
  id: string
  groupId: string
  groupName: string | null
  name: string
  sortOrder: number
  enabled: boolean
}

export interface ReferenceImpact {
  resourceType: string
  resourceId: string
  imageCount: number
  uploadBatchCount: number
  tagCount: number
}

export async function getCategories() {
  const response = await http.get<Category[]>('/categories')
  return response.data
}

export async function saveCategory(payload: Partial<Category> & { code: string; name: string }) {
  const body = {
    code: payload.code,
    name: payload.name,
    sortOrder: payload.sortOrder ?? 0,
    enabled: payload.enabled ?? true,
  }
  const response = payload.id
    ? await http.patch<Category>(`/categories/${payload.id}`, body)
    : await http.post<Category>('/categories', body)
  return response.data
}

export async function restoreCategory(id: string) {
  const response = await http.post<Category>(`/categories/${id}/restore`)
  return response.data
}

export async function purgeCategory(id: string, force = false) {
  const response = await http.delete<ReferenceImpact>(`/categories/${id}/purge`, { params: { force } })
  return response.data
}

export async function getTagGroups() {
  const response = await http.get<TagGroup[]>('/tag-groups')
  return response.data
}

export async function saveTagGroup(payload: Partial<TagGroup> & { code: string; name: string }) {
  const body = {
    code: payload.code,
    name: payload.name,
    sortOrder: payload.sortOrder ?? 0,
    enabled: payload.enabled ?? true,
  }
  const response = payload.id
    ? await http.patch<TagGroup>(`/tag-groups/${payload.id}`, body)
    : await http.post<TagGroup>('/tag-groups', body)
  return response.data
}

export async function restoreTagGroup(id: string) {
  const response = await http.post<TagGroup>(`/tag-groups/${id}/restore`)
  return response.data
}

export async function purgeTagGroup(id: string, force = false) {
  const response = await http.delete<ReferenceImpact>(`/tag-groups/${id}/purge`, { params: { force } })
  return response.data
}

export async function getTags(groupId?: string) {
  const response = await http.get<Tag[]>('/tags', { params: groupId ? { groupId } : undefined })
  return response.data
}

export async function saveTag(payload: Partial<Tag> & { groupId: string; name: string }) {
  const body = {
    groupId: payload.groupId,
    name: payload.name,
    sortOrder: payload.sortOrder ?? 0,
    enabled: payload.enabled ?? true,
  }
  const response = payload.id
    ? await http.patch<Tag>(`/tags/${payload.id}`, body)
    : await http.post<Tag>('/tags', body)
  return response.data
}

export async function restoreTag(id: string) {
  const response = await http.post<Tag>(`/tags/${id}/restore`)
  return response.data
}

export async function purgeTag(id: string, force = false) {
  const response = await http.delete<ReferenceImpact>(`/tags/${id}/purge`, { params: { force } })
  return response.data
}
