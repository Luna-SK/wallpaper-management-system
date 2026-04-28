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
import type { UploadFile, UploadInstance } from 'element-plus'
import { isAxiosError } from 'axios'
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserAvatar from '../components/UserAvatar.vue'
import { useAuthStore } from '../stores/auth'

type SidebarMode = 'pinned' | 'auto'

const sidebarModeKey = 'wzut-sidebar-mode'
const avatarCropDefaultSize = 280
const avatarOutputSize = 160
const avatarDefaultMaxZoomRatio = 4
const avatarAbsoluteMaxZoomRatio = 16

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
const avatarSaving = ref(false)
const avatarCropVisible = ref(false)
const avatarUploadRef = ref<UploadInstance>()
const avatarCropFrameRef = ref<HTMLDivElement>()
const avatarCropImageUrl = ref('')
const avatarCropSourceImage = ref<HTMLImageElement | null>(null)
const passwordSaving = ref(false)
const profileForm = reactive({ displayName: '', email: '', phone: '' })
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const avatarCrop = reactive({
  naturalWidth: 0,
  naturalHeight: 0,
  baseDisplayScale: 1,
  zoomRatio: 1,
  offsetX: 0,
  offsetY: 0,
  dragging: false,
  startClientX: 0,
  startClientY: 0,
  startOffsetX: 0,
  startOffsetY: 0,
})
const sidebarModeLabel = computed(() => sidebarMode.value === 'pinned' ? '常驻菜单' : '自动隐藏')
const appShellClasses = computed(() => ({
  'is-sidebar-collapsed': sidebarMode.value !== 'pinned',
  'is-sidebar-overlay-open': sidebarMode.value !== 'pinned' && sidebarHoverOpen.value,
}))
const visibleMenuItems = computed(() => menuItems.filter((item) => auth.hasAnyPermission(item.permissions)))
const avatarCropReady = computed(() => Boolean(avatarCropImageUrl.value && avatarCropSourceImage.value && avatarCrop.naturalWidth > 0 && avatarCrop.naturalHeight > 0))
const avatarCropDisplayScale = computed(() => avatarCrop.baseDisplayScale * avatarCrop.zoomRatio)
const avatarCropImageStyle = computed(() => ({
  height: `${avatarCrop.naturalHeight * avatarCropDisplayScale.value}px`,
  transform: `translate(calc(-50% + ${avatarCrop.offsetX}px), calc(-50% + ${avatarCrop.offsetY}px))`,
  width: `${avatarCrop.naturalWidth * avatarCropDisplayScale.value}px`,
}))
const avatarCropCircleStyle = computed(() => ({
  '--avatar-crop-radius': `${getAvatarCropCircleSize() / 2}px`,
}))
const avatarCropZoomPercent = computed({
  get: () => Math.round(avatarCrop.zoomRatio * 100),
  set: (value: number) => {
    setAvatarCropZoomRatio(Math.max(100, Math.min(avatarCropMaxZoomPercent.value, value)) / 100)
  },
})
const avatarCropMaxZoomPercent = computed(() => Math.round(getAvatarCropMaxZoomRatio() * 100))

onBeforeUnmount(cleanupAvatarCrop)

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

async function handleAvatarChange(uploadFile: UploadFile) {
  const file = uploadFile.raw
  avatarUploadRef.value?.clearFiles()
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    ElMessage.warning('头像仅支持 JPEG 或 PNG')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像图片不能超过 2MB')
    return
  }
  prepareAvatarCrop(file)
}

function prepareAvatarCrop(file: File) {
  cleanupAvatarCrop()
  const objectUrl = URL.createObjectURL(file)
  const image = new Image()
  image.onload = async () => {
    avatarCropImageUrl.value = objectUrl
    avatarCropSourceImage.value = image
    avatarCrop.naturalWidth = image.naturalWidth
    avatarCrop.naturalHeight = image.naturalHeight
    avatarCropVisible.value = true
    await nextTick()
    resetAvatarCropView()
  }
  image.onerror = () => {
    URL.revokeObjectURL(objectUrl)
    ElMessage.error('头像图片无法识别')
  }
  image.src = objectUrl
}

function cleanupAvatarCrop() {
  if (avatarCropImageUrl.value) {
    URL.revokeObjectURL(avatarCropImageUrl.value)
  }
  avatarCropImageUrl.value = ''
  avatarCropSourceImage.value = null
  avatarCrop.naturalWidth = 0
  avatarCrop.naturalHeight = 0
  avatarCrop.baseDisplayScale = 1
  avatarCrop.zoomRatio = 1
  avatarCrop.offsetX = 0
  avatarCrop.offsetY = 0
  avatarCrop.dragging = false
}

function closeAvatarCrop() {
  if (avatarSaving.value) return
  avatarCropVisible.value = false
}

function getAvatarCropBoxSize() {
  return avatarCropFrameRef.value?.clientWidth || avatarCropDefaultSize
}

function getAvatarCropCircleSize() {
  if (!avatarCropReady.value) return getAvatarCropBoxSize()
  const boxSize = getAvatarCropBoxSize()
  const scaledWidth = avatarCrop.naturalWidth * avatarCropDisplayScale.value
  const scaledHeight = avatarCrop.naturalHeight * avatarCropDisplayScale.value
  return Math.max(1, Math.min(boxSize, scaledWidth, scaledHeight))
}

function getAvatarCropCircleOffset() {
  return (getAvatarCropBoxSize() - getAvatarCropCircleSize()) / 2
}

function getAvatarCropMaxZoomRatio() {
  if (!avatarCropReady.value) return avatarDefaultMaxZoomRatio
  const minSourceSide = Math.min(avatarCrop.naturalWidth, avatarCrop.naturalHeight)
  return Math.max(avatarDefaultMaxZoomRatio, Math.min(avatarAbsoluteMaxZoomRatio, minSourceSide / avatarOutputSize))
}

function getAvatarCropContainScale(boxSize = getAvatarCropBoxSize()) {
  return Math.min(boxSize / avatarCrop.naturalWidth, boxSize / avatarCrop.naturalHeight)
}

function resetAvatarCropView() {
  if (!avatarCropReady.value) return
  avatarCrop.baseDisplayScale = getAvatarCropContainScale()
  avatarCrop.zoomRatio = 1
  avatarCrop.offsetX = 0
  avatarCrop.offsetY = 0
  clampAvatarCropOffset()
}

function clampAvatarCropOffset() {
  if (!avatarCropReady.value) return
  const circleSize = getAvatarCropCircleSize()
  const scaledWidth = avatarCrop.naturalWidth * avatarCropDisplayScale.value
  const scaledHeight = avatarCrop.naturalHeight * avatarCropDisplayScale.value
  const maxX = Math.max(0, (scaledWidth - circleSize) / 2)
  const maxY = Math.max(0, (scaledHeight - circleSize) / 2)
  avatarCrop.offsetX = Math.max(-maxX, Math.min(maxX, avatarCrop.offsetX))
  avatarCrop.offsetY = Math.max(-maxY, Math.min(maxY, avatarCrop.offsetY))
}

function getAvatarCropPoint(event: PointerEvent | WheelEvent) {
  const rect = avatarCropFrameRef.value?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  return {
    x: event.clientX - rect.left - rect.width / 2,
    y: event.clientY - rect.top - rect.height / 2,
  }
}

function setAvatarCropZoomRatio(nextRatio: number, anchor?: { x: number, y: number }) {
  if (!avatarCropReady.value) return
  const previousDisplayScale = avatarCropDisplayScale.value
  const zoomRatio = Math.max(1, Math.min(getAvatarCropMaxZoomRatio(), nextRatio))
  const nextDisplayScale = avatarCrop.baseDisplayScale * zoomRatio
  if (anchor && previousDisplayScale > 0) {
    const sourceX = (anchor.x - avatarCrop.offsetX) / previousDisplayScale
    const sourceY = (anchor.y - avatarCrop.offsetY) / previousDisplayScale
    avatarCrop.offsetX = anchor.x - sourceX * nextDisplayScale
    avatarCrop.offsetY = anchor.y - sourceY * nextDisplayScale
  }
  avatarCrop.zoomRatio = zoomRatio
  clampAvatarCropOffset()
}

function zoomAvatarCrop(direction: 1 | -1) {
  setAvatarCropZoomRatio(avatarCrop.zoomRatio * (direction > 0 ? 1.12 : 0.88))
}

function handleAvatarCropWheel(event: WheelEvent) {
  if (!avatarCropReady.value || avatarSaving.value) return
  setAvatarCropZoomRatio(avatarCrop.zoomRatio * (event.deltaY < 0 ? 1.08 : 0.92), getAvatarCropPoint(event))
}

function startAvatarCropDrag(event: PointerEvent) {
  if (!avatarCropReady.value || avatarSaving.value) return
  avatarCrop.dragging = true
  avatarCrop.startClientX = event.clientX
  avatarCrop.startClientY = event.clientY
  avatarCrop.startOffsetX = avatarCrop.offsetX
  avatarCrop.startOffsetY = avatarCrop.offsetY
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function moveAvatarCropDrag(event: PointerEvent) {
  if (!avatarCrop.dragging) return
  avatarCrop.offsetX = avatarCrop.startOffsetX + event.clientX - avatarCrop.startClientX
  avatarCrop.offsetY = avatarCrop.startOffsetY + event.clientY - avatarCrop.startClientY
  clampAvatarCropOffset()
}

function stopAvatarCropDrag(event: PointerEvent) {
  if (!avatarCrop.dragging) return
  avatarCrop.dragging = false
  const target = event.currentTarget as HTMLElement
  if (target.hasPointerCapture(event.pointerId)) {
    target.releasePointerCapture(event.pointerId)
  }
}

function canvasToAvatarFile(canvas: HTMLCanvasElement) {
  return new Promise<File>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('头像裁剪失败'))
        return
      }
      resolve(new File([blob], 'avatar.png', { type: 'image/png' }))
    }, 'image/png')
  })
}

async function createCroppedAvatarFile() {
  const image = avatarCropSourceImage.value
  if (!image || !avatarCropReady.value) {
    throw new Error('头像图片未加载')
  }
  const boxSize = getAvatarCropBoxSize()
  const circleSize = getAvatarCropCircleSize()
  const circleOffset = getAvatarCropCircleOffset()
  const displayScale = avatarCropDisplayScale.value
  const scaledWidth = avatarCrop.naturalWidth * displayScale
  const scaledHeight = avatarCrop.naturalHeight * displayScale
  const imageLeft = boxSize / 2 + avatarCrop.offsetX - scaledWidth / 2
  const imageTop = boxSize / 2 + avatarCrop.offsetY - scaledHeight / 2
  const outputScale = avatarOutputSize / circleSize
  const outputImageLeft = (imageLeft - circleOffset) * outputScale
  const outputImageTop = (imageTop - circleOffset) * outputScale
  const canvas = document.createElement('canvas')
  canvas.width = avatarOutputSize
  canvas.height = avatarOutputSize
  const context = canvas.getContext('2d')
  if (!context) {
    throw new Error('头像裁剪失败')
  }
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.fillStyle = '#f8fafc'
  context.fillRect(0, 0, avatarOutputSize, avatarOutputSize)
  context.drawImage(
    image,
    0,
    0,
    avatarCrop.naturalWidth,
    avatarCrop.naturalHeight,
    outputImageLeft,
    outputImageTop,
    scaledWidth * outputScale,
    scaledHeight * outputScale,
  )
  return canvasToAvatarFile(canvas)
}

async function saveCroppedAvatar() {
  avatarSaving.value = true
  try {
    const file = await createCroppedAvatarFile()
    await auth.uploadAvatar(file)
    ElMessage.success('头像已更新')
    avatarCropVisible.value = false
  } catch (error) {
    ElMessage.error(errorMessage(error, '头像上传失败'))
  } finally {
    avatarSaving.value = false
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
          <el-button class="topbar-user-button" plain>
            <span class="topbar-user-content">
              <UserAvatar :name="auth.displayName" :avatar-url="auth.user?.avatarUrl" :size="24" />
              <span>{{ auth.displayName }}</span>
            </span>
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
        <div class="profile-avatar-panel">
          <el-upload
            ref="avatarUploadRef"
            class="profile-avatar-upload"
            accept="image/png,image/jpeg"
            :auto-upload="false"
            :disabled="avatarSaving"
            :show-file-list="false"
            :on-change="handleAvatarChange"
          >
            <button class="profile-avatar-trigger" type="button" :disabled="avatarSaving" aria-label="更换头像">
              <UserAvatar :name="auth.displayName" :avatar-url="auth.user?.avatarUrl" :size="68" />
              <span class="profile-avatar-overlay">{{ avatarSaving ? '上传中' : '更换头像' }}</span>
            </button>
          </el-upload>
          <div class="profile-avatar-actions">
            <span>支持 JPEG / PNG，最大 2MB</span>
          </div>
        </div>
        <el-form-item label="姓名"><el-input v-model="profileForm.displayName" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="profileForm.email" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="profileForm.phone" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" :loading="profileSaving" @click="submitProfile">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="avatarCropVisible"
      title="裁剪头像"
      width="520px"
      :close-on-click-modal="!avatarSaving"
      :close-on-press-escape="!avatarSaving"
      @closed="cleanupAvatarCrop"
    >
      <div class="avatar-crop-dialog">
        <div
          ref="avatarCropFrameRef"
          class="avatar-crop-frame"
          :class="{ 'is-dragging': avatarCrop.dragging }"
          @pointerdown="startAvatarCropDrag"
          @pointermove="moveAvatarCropDrag"
          @pointerup="stopAvatarCropDrag"
          @pointercancel="stopAvatarCropDrag"
          @wheel.prevent="handleAvatarCropWheel"
        >
          <img
            v-if="avatarCropImageUrl"
            class="avatar-crop-image"
            :src="avatarCropImageUrl"
            :style="avatarCropImageStyle"
            alt=""
            draggable="false"
          />
          <div class="avatar-crop-square" aria-hidden="true" />
          <div class="avatar-crop-circle" :style="avatarCropCircleStyle" aria-hidden="true" />
        </div>
        <p class="avatar-crop-hint">圆形区域为头像实际显示范围，默认取图片短边内最大圆。</p>
        <div class="avatar-crop-controls">
          <el-button plain :disabled="!avatarCropReady || avatarSaving" @click="zoomAvatarCrop(-1)">缩小</el-button>
          <el-slider
            v-model="avatarCropZoomPercent"
            class="avatar-crop-slider"
            :min="100"
            :max="avatarCropMaxZoomPercent"
            :step="1"
            :disabled="!avatarCropReady || avatarSaving"
          />
          <el-button plain :disabled="!avatarCropReady || avatarSaving" @click="zoomAvatarCrop(1)">放大</el-button>
          <el-button plain :disabled="!avatarCropReady || avatarSaving" @click="resetAvatarCropView">重置</el-button>
        </div>
        <span class="avatar-crop-zoom-label">当前缩放 {{ avatarCropZoomPercent }}%</span>
      </div>
      <template #footer>
        <el-button :disabled="avatarSaving" @click="closeAvatarCrop">取消</el-button>
        <el-button type="primary" :loading="avatarSaving" :disabled="!avatarCropReady" @click="saveCroppedAvatar">保存头像</el-button>
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

<style scoped>
.topbar-user-button {
  --el-button-size: 32px;
}

.topbar-user-button :deep(.el-button__content) {
  display: inline-flex;
}

.topbar-user-content {
  align-items: center;
  display: inline-flex;
  gap: 8px;
  min-width: 0;
}

.profile-avatar-panel {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0 0 18px;
}

.profile-avatar-upload {
  flex: 0 0 auto;
}

.profile-avatar-trigger {
  background: transparent;
  border: 0;
  border-radius: 999px;
  cursor: pointer;
  display: block;
  height: 68px;
  overflow: hidden;
  padding: 0;
  position: relative;
  width: 68px;
}

.profile-avatar-trigger:disabled {
  cursor: wait;
}

.profile-avatar-trigger:focus-visible {
  outline: 3px solid rgba(37, 99, 235, 0.28);
  outline-offset: 3px;
}

.profile-avatar-overlay {
  align-items: center;
  background: rgba(15, 23, 42, 0.56);
  color: #ffffff;
  display: flex;
  font-size: 12px;
  font-weight: 700;
  inset: 0;
  justify-content: center;
  opacity: 0;
  position: absolute;
  transition: opacity 0.16s ease;
}

.profile-avatar-trigger:hover .profile-avatar-overlay,
.profile-avatar-trigger:focus-visible .profile-avatar-overlay,
.profile-avatar-trigger:disabled .profile-avatar-overlay {
  opacity: 1;
}

.profile-avatar-actions {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;
  text-align: center;
}

.profile-avatar-actions span {
  color: #64748b;
  font-size: 12px;
}

.avatar-crop-dialog {
  align-items: center;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.avatar-crop-frame {
  aspect-ratio: 1;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 12px;
  cursor: grab;
  max-width: 100%;
  overflow: hidden;
  position: relative;
  touch-action: none;
  width: min(280px, 72vw);
}

.avatar-crop-frame.is-dragging {
  cursor: grabbing;
}

.avatar-crop-image {
  display: block;
  left: 50%;
  max-width: none;
  position: absolute;
  top: 50%;
  transform-origin: center;
  user-select: none;
  will-change: transform;
}

.avatar-crop-square {
  border: 2px solid rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, 0.12);
  inset: 0;
  pointer-events: none;
  position: absolute;
}

.avatar-crop-circle {
  background: radial-gradient(
    circle at center,
    transparent 0 calc(var(--avatar-crop-radius, 50%) - 2px),
    rgba(37, 99, 235, 0.92) calc(var(--avatar-crop-radius, 50%) - 1px) calc(var(--avatar-crop-radius, 50%) + 1px),
    rgba(15, 23, 42, 0.22) calc(var(--avatar-crop-radius, 50%) + 2px) 100%
  );
  inset: 0;
  pointer-events: none;
  position: absolute;
}

.avatar-crop-hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  text-align: center;
}

.avatar-crop-controls {
  align-items: center;
  display: grid;
  gap: 10px;
  grid-template-columns: auto minmax(160px, 1fr) auto auto;
  width: min(420px, 100%);
}

.avatar-crop-slider {
  min-width: 0;
}

.avatar-crop-zoom-label {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1;
}

@media (max-width: 560px) {
  .avatar-crop-controls {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .avatar-crop-slider {
    grid-column: 1 / -1;
    grid-row: 1;
  }
}
</style>
