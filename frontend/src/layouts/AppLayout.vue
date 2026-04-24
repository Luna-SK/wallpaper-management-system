<script setup lang="ts">
import {
  CollectionTag,
  DataAnalysis,
  Document,
  Picture,
  Setting,
  User,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menuItems = [
  { path: '/images', label: '图片库', icon: Picture },
  { path: '/taxonomy', label: '分类标签', icon: CollectionTag },
  { path: '/users', label: '用户权限', icon: User },
  { path: '/logs', label: '审计日志', icon: Document },
  { path: '/statistics', label: '数据统计', icon: DataAnalysis },
  { path: '/settings', label: '系统设置', icon: Setting },
]

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <section class="brand-block">
        <img class="brand-logo" src="/logo.png" alt="" />
        <p class="brand-name">图片管理系统</p>
      </section>

      <el-menu :default-active="route.path" router>
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="main-region">
      <header class="topbar">
        <h1 class="topbar-title">{{ route.meta.title }}</h1>
        <el-dropdown>
          <el-button plain>
            {{ auth.username }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <section class="workspace">
        <RouterView />
      </section>
    </main>
  </div>
</template>
