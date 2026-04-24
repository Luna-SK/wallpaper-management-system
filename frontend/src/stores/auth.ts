import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '../api/auth'

const TOKEN_KEY = 'wzut-wallpaper-token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) ?? '')
  const username = ref('admin')

  const isAuthenticated = computed(() => token.value.length > 0)

  async function login(nextUsername: string, password: string) {
    const data = await loginApi(nextUsername, password)
    username.value = data.username
    token.value = data.accessToken
    localStorage.setItem(TOKEN_KEY, token.value)
  }

  function logout() {
    token.value = ''
    localStorage.removeItem(TOKEN_KEY)
  }

  return { token, username, isAuthenticated, login, logout }
})
