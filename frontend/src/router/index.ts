import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppLayout from '../layouts/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import ImageLibraryView from '../views/ImageLibraryView.vue'
import TaxonomyView from '../views/TaxonomyView.vue'
import UsersView from '../views/UsersView.vue'
import LogsView from '../views/LogsView.vue'
import StatisticsView from '../views/StatisticsView.vue'
import SettingsView from '../views/SettingsView.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/images' },
      { path: 'images', name: 'images', component: ImageLibraryView, meta: { title: '图片库' } },
      { path: 'upload', redirect: '/images' },
      { path: 'taxonomy', name: 'taxonomy', component: TaxonomyView, meta: { title: '分类标签' } },
      { path: 'users', name: 'users', component: UsersView, meta: { title: '用户权限' } },
      { path: 'logs', name: 'logs', component: LogsView, meta: { title: '审计日志' } },
      { path: 'statistics', name: 'statistics', component: StatisticsView, meta: { title: '数据统计' } },
      { path: 'settings', name: 'settings', component: SettingsView, meta: { title: '系统设置' } },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  document.title = `${to.meta.title ?? '工作台'} - 图片管理系统`

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})
