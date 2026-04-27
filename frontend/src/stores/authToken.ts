import type { SessionPolicy } from '../api/auth'

const TOKEN_KEY = 'wzut-wallpaper-token'
const AUTH_STATE_KEY = 'wzut-wallpaper-auth'

export interface StoredAuthState {
  accessToken: string
  refreshToken: string
  user: unknown
  permissions: string[]
  sessionPolicy?: SessionPolicy | null
  lastActivityAt?: number
}

export function getStoredAuthState(): StoredAuthState | null {
  const raw = localStorage.getItem(AUTH_STATE_KEY)
  if (!raw) {
    return null
  }
  try {
    const parsed = JSON.parse(raw) as Partial<StoredAuthState>
    if (typeof parsed.accessToken === 'string' && typeof parsed.refreshToken === 'string') {
      return {
        accessToken: parsed.accessToken,
        refreshToken: parsed.refreshToken,
        user: parsed.user ?? null,
        permissions: Array.isArray(parsed.permissions) ? parsed.permissions.filter((item): item is string => typeof item === 'string') : [],
        sessionPolicy: isSessionPolicy(parsed.sessionPolicy) ? parsed.sessionPolicy : null,
        lastActivityAt: typeof parsed.lastActivityAt === 'number' ? parsed.lastActivityAt : Date.now(),
      }
    }
  } catch {
    clearStoredToken()
  }
  return null
}

export function getStoredToken() {
  return getStoredAuthState()?.accessToken ?? localStorage.getItem(TOKEN_KEY) ?? ''
}

export function getStoredRefreshToken() {
  return getStoredAuthState()?.refreshToken ?? ''
}

export function setStoredAuthState(state: StoredAuthState) {
  localStorage.setItem(AUTH_STATE_KEY, JSON.stringify(state))
  localStorage.setItem(TOKEN_KEY, state.accessToken)
}

export function setStoredToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearStoredToken() {
  localStorage.removeItem(AUTH_STATE_KEY)
  localStorage.removeItem(TOKEN_KEY)
}

function isSessionPolicy(value: unknown): value is SessionPolicy {
  if (!value || typeof value !== 'object') {
    return false
  }
  const policy = value as Partial<SessionPolicy>
  return typeof policy.idleTimeoutEnabled === 'boolean'
    && typeof policy.idleTimeoutMinutes === 'number'
    && typeof policy.absoluteLifetimeEnabled === 'boolean'
    && typeof policy.absoluteLifetimeDays === 'number'
}
