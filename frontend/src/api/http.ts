import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('wzut-wallpaper-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
