import axios, { type InternalAxiosRequestConfig } from 'axios'
import { clearStoredToken, getStoredToken } from '../stores/authToken'

let unauthorizedHandler: (() => void) | null = null
let handlingUnauthorized = false

interface AuthAwareRequestConfig extends InternalAxiosRequestConfig {
  _authToken?: string
}

export function setupUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const authConfig = config as AuthAwareRequestConfig
  authConfig._authToken = token
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error)
    }
    const config = error.config as AuthAwareRequestConfig | undefined
    const requestToken = config?._authToken ?? ''
    const isLoginRequest = config?.url?.startsWith('/auth/login')
    const isCurrentSession = requestToken.length > 0 && requestToken === getStoredToken()
    if (error.response?.status === 401 && !isLoginRequest && isCurrentSession) {
      clearStoredToken()
      if (!handlingUnauthorized) {
        handlingUnauthorized = true
        unauthorizedHandler?.()
        window.setTimeout(() => {
          handlingUnauthorized = false
        }, 1000)
      }
      return new Promise(() => undefined)
    }
    return Promise.reject(error)
  },
)
