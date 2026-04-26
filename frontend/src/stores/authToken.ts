const TOKEN_KEY = 'wzut-wallpaper-token'
const AUTH_STATE_KEY = 'wzut-wallpaper-auth'

export interface StoredAuthState {
  accessToken: string
  refreshToken: string
  user: unknown
  permissions: string[]
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
