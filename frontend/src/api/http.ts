import axios, { type InternalAxiosRequestConfig } from 'axios'
import { clearStoredToken, getStoredToken } from '../stores/authToken'

let unauthorizedHandler: (() => void) | null = null
let refreshHandler: (() => Promise<string>) | null = null
let handlingUnauthorized = false
let refreshPromise: Promise<string> | null = null

interface AuthAwareRequestConfig extends InternalAxiosRequestConfig {
  _authToken?: string
  _retryAfterRefresh?: boolean
}

export function setupUnauthorizedHandler(handler: () => void) {
  unauthorizedHandler = handler
}

export function setupRefreshHandler(handler: () => Promise<string>) {
  refreshHandler = handler
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
  async (error) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error)
    }
    const config = error.config as AuthAwareRequestConfig | undefined
    const requestToken = config?._authToken ?? ''
    const isAuthRequest = config?.url?.startsWith('/auth/login')
      || config?.url?.startsWith('/auth/register')
      || config?.url?.startsWith('/auth/refresh')
    const isCurrentSession = requestToken.length > 0 && requestToken === getStoredToken()
    if (config && error.response?.status === 401 && !isAuthRequest && isCurrentSession && !config._retryAfterRefresh && refreshHandler) {
      try {
        refreshPromise ??= refreshHandler()
        const token = await refreshPromise
        refreshPromise = null
        config._retryAfterRefresh = true
        config.headers.Authorization = `Bearer ${token}`
        return http(config)
      } catch (refreshError) {
        refreshPromise = null
        clearStoredToken()
        notifyUnauthorized()
        return Promise.reject(refreshError)
      }
    }
    if (error.response?.status === 401 && !isAuthRequest && isCurrentSession) {
      clearStoredToken()
      notifyUnauthorized()
      return new Promise(() => undefined)
    }
    return Promise.reject(error)
  },
)

function notifyUnauthorized() {
  if (!handlingUnauthorized) {
    handlingUnauthorized = true
    unauthorizedHandler?.()
    window.setTimeout(() => {
      handlingUnauthorized = false
    }, 1000)
  }
}
