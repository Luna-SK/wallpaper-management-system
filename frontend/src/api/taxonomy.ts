import { http } from './http'

export interface Category {
  id: string
  code: string
  name: string
  sortOrder: number
  enabled: boolean
  tagCount: number
}

export interface Tag {
  id: string
  categoryId: string
  name: string
  sortOrder: number
  enabled: boolean
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

export async function getTags(categoryId: string) {
  const response = await http.get<Tag[]>(`/categories/${categoryId}/tags`)
  return response.data
}

export async function saveTag(categoryId: string, payload: Partial<Tag> & { name: string }) {
  const body = {
    name: payload.name,
    sortOrder: payload.sortOrder ?? 0,
    enabled: payload.enabled ?? true,
  }
  const response = payload.id
    ? await http.patch<Tag>(`/categories/${categoryId}/tags/${payload.id}`, body)
    : await http.post<Tag>(`/categories/${categoryId}/tags`, body)
  return response.data
}
