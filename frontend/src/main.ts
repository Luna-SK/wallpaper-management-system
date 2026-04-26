import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import { setupRefreshHandler, setupUnauthorizedHandler } from './api/http'

setupRefreshHandler(() => useAuthStore(pinia).refresh())

setupUnauthorizedHandler(() => {
  useAuthStore(pinia).clearSession()
  ElMessage.warning('登录状态已失效，请重新登录')
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

createApp(App).use(pinia).use(router).use(ElementPlus).mount('#app')
