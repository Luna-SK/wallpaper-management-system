import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import AppLayout from '../layouts/AppLayout.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import ImageLibraryView from '../views/ImageLibraryView.vue'
import TaxonomyView from '../views/TaxonomyView.vue'
import UsersView from '../views/UsersView.vue'
import LogsView from '../views/LogsView.vue'
import StatisticsView from '../views/StatisticsView.vue'
import SettingsView from '../views/SettingsView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
    title?: string
    permissions?: string[]
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { title: '登录', guestOnly: true },
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterView,
    meta: { title: '注册', guestOnly: true },
  },
  {
    path: '/',
    component: AppLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/images' },
      { path: 'images', name: 'images', component: ImageLibraryView, meta: { title: '图片库', permissions: ['image:view'] } },
      { path: 'upload', redirect: '/images' },
      { path: 'taxonomy', name: 'taxonomy', component: TaxonomyView, meta: { title: '分类标签', permissions: ['taxonomy:manage'] } },
      { path: 'users', name: 'users', component: UsersView, meta: { title: '用户权限', permissions: ['user:manage', 'role:manage'] } },
      { path: 'logs', name: 'logs', component: LogsView, meta: { title: '审计日志', permissions: ['audit:view'] } },
      { path: 'statistics', name: 'statistics', component: StatisticsView, meta: { title: '数据统计', permissions: ['image:view'] } },
      { path: 'settings', name: 'settings', component: SettingsView, meta: { title: '系统设置', permissions: ['setting:manage'] } },
      { path: 'forbidden', name: 'forbidden', component: ForbiddenView, meta: { title: '无权限' } },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  document.title = `${to.meta.title ?? '工作台'} - 图片管理系统`

  if (to.meta.guestOnly && auth.isAuthenticated) {
    return { name: 'images' }
  }

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAuth && auth.isAuthenticated && !auth.profileLoaded) {
    try {
      await auth.loadMe()
    } catch {
      auth.clearSession()
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }

  if (to.meta.requiresAuth && to.name !== 'forbidden' && !auth.hasAnyPermission(to.meta.permissions)) {
    return { name: 'forbidden' }
  }
})
