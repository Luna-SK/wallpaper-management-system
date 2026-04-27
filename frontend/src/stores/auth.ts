import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  changePassword as changePasswordApi,
  getMe,
  getSessionPolicy,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
  refreshSession,
  updateProfile as updateProfileApi,
  type AuthUser,
  type LoginResponse,
  type RegisterRequest,
  type SessionPolicy,
} from '../api/auth'
import { clearStoredToken, getStoredAuthState, getStoredRefreshToken, setStoredAuthState } from './authToken'

const AUTH_ACTIVITY_KEY = 'wzut-wallpaper-auth-activity'
const AUTH_EXPIRED_KEY = 'wzut-wallpaper-auth-expired'
const ACTIVITY_EVENTS = ['mousedown', 'keydown', 'scroll', 'touchstart', 'wheel', 'pointerdown']
const ACTIVITY_BROADCAST_INTERVAL_MS = 5000
const DEFAULT_CHECK_INTERVAL_MS = 60_000

export const useAuthStore = defineStore('auth', () => {
  const stored = getStoredAuthState()
  const token = ref(stored?.accessToken ?? '')
  const refreshToken = ref(stored?.refreshToken ?? '')
  const user = ref<AuthUser | null>(stored?.user as AuthUser | null ?? null)
  const permissions = ref<string[]>(stored?.permissions ?? [])
  const sessionPolicy = ref<SessionPolicy | null>(stored?.sessionPolicy ?? null)
  const lastActivityAt = ref(stored?.lastActivityAt ?? Date.now())
  const profileLoaded = ref(Boolean(stored?.user))
  let lifecycleTimer: ReturnType<typeof window.setTimeout> | null = null
  let lifecycleStarted = false
  let lifecycleExpiredHandler: ((message: string) => void) | null = null
  let lastActivityBroadcastAt = 0

  const username = computed(() => user.value?.username ?? '')
  const displayName = computed(() => user.value?.displayName || user.value?.username || '用户')
  const isAuthenticated = computed(() => token.value.length > 0 && refreshToken.value.length > 0)

  async function login(nextUsername: string, password: string) {
    const data = await loginApi(nextUsername, password)
    applyAuthResponse(data)
  }

  async function register(payload: RegisterRequest) {
    const data = await registerApi(payload)
    applyAuthResponse(data)
  }

  async function refresh() {
    const data = await refreshSession(refreshToken.value || getStoredRefreshToken())
    applyAuthResponse(data)
    return data.accessToken
  }

  async function loadMe() {
    if (!isAuthenticated.value) return null
    const data = await getMe()
    user.value = data
    permissions.value = data.permissions.map((permission) => permission.code)
    profileLoaded.value = true
    sessionPolicy.value = await getSessionPolicy()
    persist()
    scheduleLifecycleCheck()
    return data
  }

  async function syncSessionPolicy() {
    if (!isAuthenticated.value) return null
    sessionPolicy.value = await getSessionPolicy()
    persist()
    scheduleLifecycleCheck()
    return sessionPolicy.value
  }

  async function updateProfile(payload: { displayName: string; email?: string | null; phone?: string | null }) {
    const data = await updateProfileApi(payload)
    user.value = data
    profileLoaded.value = true
    persist()
    return data
  }

  async function changePassword(payload: { currentPassword: string; newPassword: string }) {
    await changePasswordApi(payload)
  }

  async function logout() {
    try {
      if (token.value) {
        await logoutApi()
      }
    } finally {
      clearSession()
    }
  }

  function clearSession() {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    permissions.value = []
    sessionPolicy.value = null
    profileLoaded.value = false
    clearLifecycleTimer()
    clearStoredToken()
  }

  function hasPermission(permission: string) {
    return permissions.value.includes(permission)
  }

  function hasAnyPermission(nextPermissions?: string[]) {
    return !nextPermissions || nextPermissions.length === 0 || nextPermissions.some(hasPermission)
  }

  function applyAuthResponse(data: LoginResponse) {
    token.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user
    permissions.value = data.permissions
    sessionPolicy.value = data.sessionPolicy
    profileLoaded.value = true
    recordSessionActivity(false)
    persist()
    scheduleLifecycleCheck()
  }

  function persist() {
    if (!token.value || !refreshToken.value) return
    setStoredAuthState({
      accessToken: token.value,
      refreshToken: refreshToken.value,
      user: user.value,
      permissions: permissions.value,
      sessionPolicy: sessionPolicy.value,
      lastActivityAt: lastActivityAt.value,
    })
  }

  function startSessionLifecycleMonitor(handler: (message: string) => void) {
    lifecycleExpiredHandler = handler
    if (!lifecycleStarted) {
      ACTIVITY_EVENTS.forEach((event) => window.addEventListener(event, recordSessionActivityFromEvent, { passive: true }))
      window.addEventListener('storage', handleStorageEvent)
      lifecycleStarted = true
    }
    if (isAuthenticated.value) {
      scheduleLifecycleCheck()
      void syncSessionPolicy().catch(() => undefined)
    }
  }

  function stopSessionLifecycleMonitor() {
    if (lifecycleStarted) {
      ACTIVITY_EVENTS.forEach((event) => window.removeEventListener(event, recordSessionActivityFromEvent))
      window.removeEventListener('storage', handleStorageEvent)
      lifecycleStarted = false
    }
    clearLifecycleTimer()
    lifecycleExpiredHandler = null
  }

  function recordSessionActivity(broadcast = true) {
    if (!isAuthenticated.value) return
    const now = Date.now()
    if (now <= lastActivityAt.value) return
    lastActivityAt.value = now
    persist()
    if (broadcast && now - lastActivityBroadcastAt >= ACTIVITY_BROADCAST_INTERVAL_MS) {
      lastActivityBroadcastAt = now
      localStorage.setItem(AUTH_ACTIVITY_KEY, String(now))
    }
    scheduleLifecycleCheck()
  }

  function checkSessionLifecycle() {
    if (!isAuthenticated.value) {
      clearLifecycleTimer()
      return false
    }
    const policy = sessionPolicy.value
    if (!policy) {
      scheduleLifecycleCheck(DEFAULT_CHECK_INTERVAL_MS)
      return true
    }
    const now = Date.now()
    if (policy.absoluteLifetimeEnabled && policy.absoluteExpiresAt) {
      const absoluteExpiresAt = Date.parse(policy.absoluteExpiresAt)
      if (Number.isFinite(absoluteExpiresAt) && now >= absoluteExpiresAt) {
        expireSession('登录已超过最长会话时长，请重新登录')
        return false
      }
    }
    if (policy.idleTimeoutEnabled) {
      const idleExpiresAt = lastActivityAt.value + policy.idleTimeoutMinutes * 60_000
      if (now >= idleExpiresAt) {
        expireSession('登录空闲超时，请重新登录')
        return false
      }
    }
    scheduleLifecycleCheck()
    return true
  }

  function scheduleLifecycleCheck(delayOverride?: number) {
    clearLifecycleTimer()
    if (!isAuthenticated.value) return
    const policy = sessionPolicy.value
    if (!policy) {
      lifecycleTimer = window.setTimeout(checkSessionLifecycle, delayOverride ?? DEFAULT_CHECK_INTERVAL_MS)
      return
    }
    const now = Date.now()
    const candidates: number[] = []
    if (policy.idleTimeoutEnabled) {
      candidates.push(lastActivityAt.value + policy.idleTimeoutMinutes * 60_000 - now)
    }
    if (policy.absoluteLifetimeEnabled && policy.absoluteExpiresAt) {
      const absoluteExpiresAt = Date.parse(policy.absoluteExpiresAt)
      if (Number.isFinite(absoluteExpiresAt)) {
        candidates.push(absoluteExpiresAt - now)
      }
    }
    const nextDelay = candidates.length > 0 ? Math.min(...candidates) : DEFAULT_CHECK_INTERVAL_MS
    lifecycleTimer = window.setTimeout(checkSessionLifecycle, Math.max(0, Math.min(nextDelay, DEFAULT_CHECK_INTERVAL_MS)))
  }

  function clearLifecycleTimer() {
    if (lifecycleTimer !== null) {
      window.clearTimeout(lifecycleTimer)
      lifecycleTimer = null
    }
  }

  function expireSession(message: string) {
    clearSession()
    localStorage.setItem(AUTH_EXPIRED_KEY, JSON.stringify({ message, at: Date.now() }))
    lifecycleExpiredHandler?.(message)
  }

  function recordSessionActivityFromEvent() {
    recordSessionActivity()
  }

  function handleStorageEvent(event: StorageEvent) {
    if (event.key === AUTH_ACTIVITY_KEY && event.newValue) {
      const activityAt = Number(event.newValue)
      if (Number.isFinite(activityAt) && activityAt > lastActivityAt.value) {
        lastActivityAt.value = activityAt
        persist()
        scheduleLifecycleCheck()
      }
      return
    }
    if (event.key === AUTH_EXPIRED_KEY && event.newValue) {
      let message = '登录状态已失效，请重新登录'
      try {
        const parsed = JSON.parse(event.newValue) as { message?: string }
        if (typeof parsed.message === 'string' && parsed.message) {
          message = parsed.message
        }
      } catch {
        // ignore malformed storage events
      }
      clearSession()
      lifecycleExpiredHandler?.(message)
    }
  }

  return {
    token,
    refreshToken,
    user,
    permissions,
    sessionPolicy,
    lastActivityAt,
    profileLoaded,
    username,
    displayName,
    isAuthenticated,
    login,
    register,
    refresh,
    loadMe,
    updateProfile,
    changePassword,
    logout,
    clearSession,
    syncSessionPolicy,
    startSessionLifecycleMonitor,
    stopSessionLifecycleMonitor,
    recordSessionActivity,
    checkSessionLifecycle,
    hasPermission,
    hasAnyPermission,
  }
})
