import { createApp } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import 'element-plus/es/components/message/style/css'
import './style.css'
import App from './App.vue'
import { router } from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import { setupRefreshHandler, setupUnauthorizedHandler } from './api/http'

const auth = useAuthStore(pinia)

function redirectToLogin(message: string) {
  ElMessage.warning(message)
  const current = router.currentRoute.value
  if (current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
}

setupRefreshHandler(() => auth.refresh())

setupUnauthorizedHandler(() => {
  auth.clearSession()
  redirectToLogin('登录状态已失效，请重新登录')
})

auth.startSessionLifecycleMonitor(redirectToLogin)

createApp(App).use(pinia).use(router).mount('#app')
