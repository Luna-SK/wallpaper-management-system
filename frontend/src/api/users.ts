import { http } from './http'

export interface Permission {
  id: string
  code: string
  name: string
  resource: string
  action: string
}

export interface Role {
  id: string
  code: string
  name: string
  enabled: boolean
  userCount: number
  permissions: Permission[]
}

export interface User {
  id: string
  username: string
  displayName: string
  email: string | null
  phone: string | null
  status: string
  roles: Array<{ id: string; code: string; name: string }>
}

export async function getUsers() {
  const response = await http.get<User[]>('/users')
  return response.data
}

export async function saveUser(payload: Partial<User> & { username: string; displayName: string }) {
  const body = {
    username: payload.username,
    displayName: payload.displayName,
    email: payload.email,
    phone: payload.phone,
    status: payload.status ?? 'ACTIVE',
  }
  const response = payload.id ? await http.patch<User>(`/users/${payload.id}`, body) : await http.post<User>('/users', body)
  return response.data
}

export async function updateUserRoles(userId: string, roleIds: string[]) {
  const response = await http.put<User>(`/users/${userId}/roles`, { roleIds })
  return response.data
}

export async function getRoles() {
  const response = await http.get<Role[]>('/roles')
  return response.data
}

export async function saveRole(payload: Partial<Role> & { code: string; name: string }) {
  const body = {
    code: payload.code,
    name: payload.name,
    enabled: payload.enabled ?? true,
  }
  const response = payload.id ? await http.patch<Role>(`/roles/${payload.id}`, body) : await http.post<Role>('/roles', body)
  return response.data
}

export async function updateRolePermissions(roleId: string, permissionIds: string[]) {
  const response = await http.put<Role>(`/roles/${roleId}/permissions`, { permissionIds })
  return response.data
}

export async function getPermissions() {
  const response = await http.get<Permission[]>('/permissions')
  return response.data
}
