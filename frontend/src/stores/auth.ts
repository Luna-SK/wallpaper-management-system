import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '../api/auth'
import { clearStoredToken, getStoredToken, setStoredToken } from './authToken'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(getStoredToken())
  const username = ref('admin')

  const isAuthenticated = computed(() => token.value.length > 0)

  async function login(nextUsername: string, password: string) {
    const data = await loginApi(nextUsername, password)
    username.value = data.username
    token.value = data.accessToken
    setStoredToken(token.value)
  }

  function logout() {
    token.value = ''
    clearStoredToken()
  }

  return { token, username, isAuthenticated, login, logout }
})
