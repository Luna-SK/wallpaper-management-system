import { http } from './http'
import type { UserStatus } from './users'

interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId?: string | null
}

export interface Permission {
  id: string
  code: string
  name: string
  resource: string
  action: string
}

export interface RoleBrief {
  id: string
  code: string
  name: string
}

export interface AuthUser {
  id: string
  username: string
  displayName: string
  email: string | null
  phone: string | null
  status: UserStatus
  roles: RoleBrief[]
  permissions: Permission[]
}

export interface LoginResponse {
  username: string
  tokenType: 'Bearer'
  accessToken: string
  refreshToken: string
  accessTokenExpiresAt: string
  refreshTokenExpiresAt: string
  user: AuthUser
  permissions: string[]
  sessionPolicy: SessionPolicy
}

export interface SessionPolicy {
  idleTimeoutEnabled: boolean
  idleTimeoutMinutes: number
  absoluteLifetimeEnabled: boolean
  absoluteLifetimeDays: number
  absoluteExpiresAt: string | null
  serverTime: string
}

export interface RegisterRequest {
  username: string
  password: string
  displayName: string
  email?: string | null
  phone?: string | null
}

export async function login(username: string, password: string) {
  const response = await http.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })
  return response.data.data
}

export async function register(payload: RegisterRequest) {
  const response = await http.post<ApiResponse<LoginResponse>>('/auth/register', payload)
  return response.data.data
}

export async function refreshSession(refreshToken: string) {
  const response = await http.post<ApiResponse<LoginResponse>>('/auth/refresh', { refreshToken })
  return response.data.data
}

export async function getSessionPolicy() {
  const response = await http.get<ApiResponse<SessionPolicy>>('/auth/session-policy')
  return response.data.data
}

export async function logout() {
  await http.post<ApiResponse<void>>('/auth/logout')
}

export async function getMe() {
  const response = await http.get<ApiResponse<AuthUser>>('/auth/me')
  return response.data.data
}

export async function updateProfile(payload: { displayName: string; email?: string | null; phone?: string | null }) {
  const response = await http.patch<ApiResponse<AuthUser>>('/auth/profile', payload)
  return response.data.data
}

export async function changePassword(payload: { currentPassword: string; newPassword: string }) {
  await http.patch<ApiResponse<void>>('/auth/password', payload)
}
