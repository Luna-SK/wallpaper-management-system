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
import { ElMessage } from 'element-plus'
import { isAxiosError } from 'axios'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

type SidebarMode = 'pinned' | 'auto'

const sidebarModeKey = 'wzut-sidebar-mode'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const menuItems = [
  { path: '/images', label: '图片库', icon: Picture, permissions: ['image:view'] },
  { path: '/taxonomy', label: '分类标签', icon: CollectionTag, permissions: ['taxonomy:manage'] },
  { path: '/users', label: '用户权限', icon: User, permissions: ['user:manage', 'role:manage'] },
  { path: '/logs', label: '审计日志', icon: Document, permissions: ['audit:view'] },
  { path: '/statistics', label: '数据统计', icon: DataAnalysis, permissions: ['image:view'] },
  { path: '/feedback', label: '用户反馈', icon: Document },
  { path: '/settings', label: '系统设置', icon: Setting, permissions: ['setting:manage'] },
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
const profileVisible = ref(false)
const passwordVisible = ref(false)
const profileSaving = ref(false)
const passwordSaving = ref(false)
const profileForm = reactive({ displayName: '', email: '', phone: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const sidebarModeLabel = computed(() => sidebarMode.value === 'pinned' ? '常驻菜单' : '自动隐藏')
const appShellClasses = computed(() => ({
  'is-sidebar-collapsed': sidebarMode.value !== 'pinned',
  'is-sidebar-overlay-open': sidebarMode.value !== 'pinned' && sidebarHoverOpen.value,
}))
const visibleMenuItems = computed(() => menuItems.filter((item) => auth.hasAnyPermission(item.permissions)))

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

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

function openProfile() {
  profileForm.displayName = auth.user?.displayName ?? ''
  profileForm.email = auth.user?.email ?? ''
  profileForm.phone = auth.user?.phone ?? ''
  profileVisible.value = true
}

async function submitProfile() {
  profileSaving.value = true
  try {
    await auth.updateProfile(profileForm)
    ElMessage.success('个人资料已保存')
    profileVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error, '个人资料保存失败'))
  } finally {
    profileSaving.value = false
  }
}

function openPassword() {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordVisible.value = true
}

async function submitPassword() {
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  passwordSaving.value = true
  try {
    await auth.changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码已修改')
    passwordVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error, '密码修改失败'))
  } finally {
    passwordSaving.value = false
  }
}

async function logout() {
  await auth.logout()
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
        <el-menu-item v-for="item in visibleMenuItems" :key="item.path" :index="item.path">
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
            {{ auth.displayName }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openProfile">个人资料</el-dropdown-item>
              <el-dropdown-item @click="openPassword">修改密码</el-dropdown-item>
              <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <section class="workspace">
        <RouterView />
      </section>
    </main>

    <el-dialog v-model="profileVisible" title="个人资料" width="480px">
      <el-form label-width="86px">
        <el-form-item label="姓名"><el-input v-model="profileForm.displayName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="profileForm.email" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="profileForm.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="480px">
      <el-form label-width="98px">
        <el-form-item label="当前密码"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
        <el-form-item label="确认新密码"><el-input v-model="passwordForm.confirmPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSaving" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
