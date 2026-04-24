import { http } from './http'

interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId?: string | null
}

export interface LoginResponse {
  username: string
  tokenType: 'Bearer'
  accessToken: string
}

export async function login(username: string, password: string) {
  const response = await http.post<ApiResponse<LoginResponse>>('/auth/login', { username, password })
  return response.data.data
}
