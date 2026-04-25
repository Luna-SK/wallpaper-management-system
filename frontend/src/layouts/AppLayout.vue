<script setup lang="ts">
import {
  CollectionTag,
  DataAnalysis,
  Document,
  Fold,
  Picture,
  Setting,
  User,
} from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

type SidebarMode = 'pinned' | 'auto'

const sidebarModeKey = 'wzut-sidebar-mode'

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

function readSidebarMode(): SidebarMode {
  const storedMode = localStorage.getItem(sidebarModeKey)
  if (storedMode === 'auto') return 'auto'
  if (storedMode === 'manual') {
    localStorage.setItem(sidebarModeKey, 'auto')
    return 'auto'
  }
  return 'pinned'
}

const sidebarMode = ref<SidebarMode>(readSidebarMode())
const sidebarHoverOpen = ref(false)
const sidebarModeLabel = computed(() => sidebarMode.value === 'pinned' ? '常驻菜单' : '自动隐藏')
const appShellClasses = computed(() => ({
  'is-sidebar-collapsed': sidebarMode.value !== 'pinned',
  'is-sidebar-overlay-open': sidebarMode.value !== 'pinned' && sidebarHoverOpen.value,
}))

function setSidebarMode(mode: SidebarMode) {
  sidebarMode.value = mode
  sidebarHoverOpen.value = false
  localStorage.setItem(sidebarModeKey, mode)
}

function toggleSidebarMode() {
  setSidebarMode(sidebarMode.value === 'pinned' ? 'auto' : 'pinned')
}

function openSidebarOverlay() {
  if (sidebarMode.value !== 'pinned') {
    sidebarHoverOpen.value = true
  }
}

function closeSidebarOverlay() {
  if (sidebarMode.value !== 'pinned') {
    sidebarHoverOpen.value = false
  }
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-shell" :class="appShellClasses">
    <aside
      class="sidebar"
      @mouseenter="openSidebarOverlay"
      @mouseleave="closeSidebarOverlay"
      @focusin="openSidebarOverlay"
    >
      <section class="brand-block">
        <img class="brand-logo" src="/logo.png" alt="" />
        <p class="brand-name">图片管理系统</p>
      </section>

      <el-menu class="sidebar-menu" :default-active="route.path" router>
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <main class="main-region">
      <header class="topbar">
        <div class="topbar-left">
          <el-button
            class="sidebar-mode-button"
            plain
            :aria-label="`侧边栏：${sidebarModeLabel}`"
            :title="`侧边栏：${sidebarModeLabel}`"
            @click="toggleSidebarMode"
          >
            <el-icon><Fold /></el-icon>
          </el-button>
          <h1 class="topbar-title">{{ route.meta.title }}</h1>
        </div>
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
