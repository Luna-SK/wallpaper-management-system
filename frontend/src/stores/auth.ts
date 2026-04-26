import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  changePassword as changePasswordApi,
  getMe,
  login as loginApi,
  logout as logoutApi,
  register as registerApi,
  refreshSession,
  updateProfile as updateProfileApi,
  type AuthUser,
  type LoginResponse,
  type RegisterRequest,
} from '../api/auth'
import { clearStoredToken, getStoredAuthState, getStoredRefreshToken, setStoredAuthState } from './authToken'

export const useAuthStore = defineStore('auth', () => {
  const stored = getStoredAuthState()
  const token = ref(stored?.accessToken ?? '')
  const refreshToken = ref(stored?.refreshToken ?? '')
  const user = ref<AuthUser | null>(stored?.user as AuthUser | null ?? null)
  const permissions = ref<string[]>(stored?.permissions ?? [])
  const profileLoaded = ref(Boolean(stored?.user))

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
    persist()
    return data
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
    profileLoaded.value = false
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
    profileLoaded.value = true
    persist()
  }

  function persist() {
    if (!token.value || !refreshToken.value) return
    setStoredAuthState({
      accessToken: token.value,
      refreshToken: refreshToken.value,
      user: user.value,
      permissions: permissions.value,
    })
  }

  return {
    token,
    refreshToken,
    user,
    permissions,
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
    hasPermission,
    hasAnyPermission,
  }
})
