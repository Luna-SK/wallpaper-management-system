<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index'
import 'element-plus/es/components/message/style/css'
import { isAxiosError } from 'axios'
import {
  closeFeedback,
  createFeedback,
  getAdminFeedback,
  getMyFeedback,
  handleFeedback,
  type FeedbackRecord,
  type FeedbackStatus,
} from '../api/interactions'
import { getImages, imageBlobUrl, type ImageRecord } from '../api/images'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const adminLoading = ref(false)
const activeTab = ref('submit')
const statusFilter = ref<FeedbackStatus | ''>('')
const adminStatusFilter = ref<FeedbackStatus | ''>('')
const adminKeyword = ref('')
const rows = ref<FeedbackRecord[]>([])
const adminRows = ref<FeedbackRecord[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const adminPagination = reactive({ page: 1, size: 20, total: 0 })
const form = reactive({ type: 'GENERAL', title: '', content: '', imageId: '' })
const submitting = ref(false)
const imageOptions = ref<ImageRecord[]>([])
const imageSearchLoading = ref(false)
let imageSearchToken = 0
let imageSearchDebounceTimer: ReturnType<typeof setTimeout> | null = null
const imageOptionThumbnailUrls = reactive<Record<string, string>>({})
const imageOptionThumbnailKeys = reactive<Record<string, string>>({})
const imageOptionThumbnailLoading = reactive<Record<string, boolean>>({})
const imageOptionThumbnailFailed = reactive<Record<string, boolean>>({})
const selectedFeedbackImage = ref<ImageRecord | null>(null)
const selectedImageThumbnailUrl = ref('')
const selectedImageThumbnailLoading = ref(false)
const selectedImageThumbnailFailed = ref(false)
let selectedImageThumbnailToken = 0
const handlingId = ref('')
const handleForm = reactive<{ status: FeedbackStatus; response: string }>({ status: 'IN_PROGRESS', response: '' })
const handleDialogVisible = ref(false)
const selectedFeedback = ref<FeedbackRecord | null>(null)
const canManage = computed(() => auth.hasPermission('interaction:manage'))
const recentMineFeedback = computed(() => rows.value.slice(0, 5))
const selectedImageTagLimit = 6
type SelectedImageTagGroup = { key: string; label: string; names: string[] }

function groupImageTags(tags: ImageRecord['tags']): SelectedImageTagGroup[] {
  const groups = new Map<string, SelectedImageTagGroup>()
  tags.forEach((tag) => {
    const label = tag.groupName?.trim() || '未分组'
    const key = tag.groupId || label
    const group = groups.get(key) ?? { key, label, names: [] }
    group.names.push(tag.name)
    groups.set(key, group)
  })
  return Array.from(groups.values())
}

const selectedImageTagGroups = computed(() => {
  const image = selectedFeedbackImage.value
  if (!image?.tags.length) return []
  return groupImageTags(image.tags.slice(0, selectedImageTagLimit))
})
const selectedImageHiddenTagGroups = computed(() => {
  const image = selectedFeedbackImage.value
  if (!image || image.tags.length <= selectedImageTagLimit) return []
  return groupImageTags(image.tags.slice(selectedImageTagLimit))
})
const selectedImageHiddenTagCount = computed(() => Math.max(0, (selectedFeedbackImage.value?.tags.length ?? 0) - selectedImageTagLimit))

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function statusLabel(status: FeedbackStatus) {
  const labels: Record<FeedbackStatus, string> = {
    OPEN: '待处理',
    IN_PROGRESS: '处理中',
    RESOLVED: '已解决',
    CLOSED: '已关闭',
  }
  return labels[status] ?? status
}

function statusTagType(status: FeedbackStatus) {
  if (status === 'OPEN') return 'warning'
  if (status === 'IN_PROGRESS') return 'primary'
  if (status === 'RESOLVED') return 'success'
  return 'info'
}

function formatDateTime(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function formatBytes(value: number | null | undefined) {
  if (!value) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = value
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex += 1
  }
  return `${size >= 10 || unitIndex === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unitIndex]}`
}

function imageOptionMeta(image: ImageRecord) {
  const category = image.category?.name ?? '未分类'
  return `${image.originalFilename} · ${category} · ${formatBytes(image.sizeBytes)}`
}

function imageDimensions(image: ImageRecord) {
  if (image.width && image.height) return `${image.width} × ${image.height}`
  return '未知尺寸'
}

function imageOptionThumbnailKey(image: ImageRecord) {
  return `${image.id}:${image.updatedAt ?? ''}`
}

function revokeImageOptionThumbnail(imageId: string) {
  if (imageOptionThumbnailUrls[imageId]) {
    URL.revokeObjectURL(imageOptionThumbnailUrls[imageId])
    delete imageOptionThumbnailUrls[imageId]
  }
  delete imageOptionThumbnailKeys[imageId]
  delete imageOptionThumbnailLoading[imageId]
  delete imageOptionThumbnailFailed[imageId]
}

function clearImageOptionThumbnails() {
  Object.keys(imageOptionThumbnailUrls).forEach(revokeImageOptionThumbnail)
  Object.keys(imageOptionThumbnailLoading).forEach((imageId) => delete imageOptionThumbnailLoading[imageId])
  Object.keys(imageOptionThumbnailFailed).forEach((imageId) => delete imageOptionThumbnailFailed[imageId])
}

function revokeSelectedImageThumbnail() {
  if (selectedImageThumbnailUrl.value) {
    URL.revokeObjectURL(selectedImageThumbnailUrl.value)
    selectedImageThumbnailUrl.value = ''
  }
  selectedImageThumbnailLoading.value = false
  selectedImageThumbnailFailed.value = false
}

function clearSelectedFeedbackImage() {
  selectedImageThumbnailToken++
  selectedFeedbackImage.value = null
  revokeSelectedImageThumbnail()
}

function pruneImageOptionThumbnails(images: ImageRecord[]) {
  const activeKeys = new Map(images.map((image) => [image.id, imageOptionThumbnailKey(image)]))
  const knownIds = new Set([
    ...Object.keys(imageOptionThumbnailUrls),
    ...Object.keys(imageOptionThumbnailLoading),
    ...Object.keys(imageOptionThumbnailFailed),
  ])
  knownIds.forEach((imageId) => {
    const activeKey = activeKeys.get(imageId)
    if (!activeKey || imageOptionThumbnailKeys[imageId] !== activeKey) {
      revokeImageOptionThumbnail(imageId)
    }
  })
}

async function loadImageOptionThumbnail(image: ImageRecord, token: number) {
  const thumbnailKey = imageOptionThumbnailKey(image)
  if (imageOptionThumbnailUrls[image.id] && imageOptionThumbnailKeys[image.id] === thumbnailKey) return
  if (imageOptionThumbnailLoading[image.id]) return
  imageOptionThumbnailLoading[image.id] = true
  delete imageOptionThumbnailFailed[image.id]
  try {
    const thumbnailUrl = await imageBlobUrl(image.id, 'thumbnail', thumbnailKey)
    const stillVisible = token === imageSearchToken
      && imageOptions.value.some((item) => item.id === image.id && imageOptionThumbnailKey(item) === thumbnailKey)
    if (!stillVisible) {
      URL.revokeObjectURL(thumbnailUrl)
      return
    }
    const previousUrl = imageOptionThumbnailUrls[image.id]
    imageOptionThumbnailUrls[image.id] = thumbnailUrl
    imageOptionThumbnailKeys[image.id] = thumbnailKey
    if (previousUrl) {
      URL.revokeObjectURL(previousUrl)
    }
  } catch {
    if (token === imageSearchToken) {
      imageOptionThumbnailFailed[image.id] = true
    }
  } finally {
    if (token === imageSearchToken) {
      imageOptionThumbnailLoading[image.id] = false
    } else {
      delete imageOptionThumbnailLoading[image.id]
    }
  }
}

function loadImageOptionThumbnails(images: ImageRecord[], token: number) {
  images.forEach((image) => {
    void loadImageOptionThumbnail(image, token)
  })
}

async function loadSelectedImageThumbnail(image: ImageRecord) {
  const token = ++selectedImageThumbnailToken
  revokeSelectedImageThumbnail()
  selectedImageThumbnailLoading.value = true
  try {
    const thumbnailUrl = await imageBlobUrl(image.id, 'thumbnail', imageOptionThumbnailKey(image))
    if (token !== selectedImageThumbnailToken || selectedFeedbackImage.value?.id !== image.id) {
      URL.revokeObjectURL(thumbnailUrl)
      return
    }
    selectedImageThumbnailUrl.value = thumbnailUrl
  } catch {
    if (token === selectedImageThumbnailToken) {
      selectedImageThumbnailFailed.value = true
    }
  } finally {
    if (token === selectedImageThumbnailToken) {
      selectedImageThumbnailLoading.value = false
    }
  }
}

async function runFeedbackImageSearch(query: string) {
  const token = ++imageSearchToken
  imageSearchLoading.value = true
  try {
    const page = await getImages({
      keyword: query?.trim() || undefined,
      status: 'ACTIVE',
      page: 1,
      size: 20,
    })
    if (token === imageSearchToken) {
      imageOptions.value = page.items
      pruneImageOptionThumbnails(page.items)
      loadImageOptionThumbnails(page.items, token)
    }
  } catch (error) {
    if (token === imageSearchToken) {
      imageOptions.value = []
      pruneImageOptionThumbnails([])
      ElMessage.error(errorMessage(error, '图片候选加载失败'))
    }
  } finally {
    if (token === imageSearchToken) {
      imageSearchLoading.value = false
    }
  }
}

function searchFeedbackImages(query: string) {
  if (imageSearchDebounceTimer) {
    clearTimeout(imageSearchDebounceTimer)
  }
  imageSearchDebounceTimer = setTimeout(() => {
    imageSearchDebounceTimer = null
    void runFeedbackImageSearch(query)
  }, 250)
}

function handleImageSelectVisibleChange(visible: boolean) {
  if (visible) {
    if (imageOptions.value.length === 0) {
      void runFeedbackImageSearch('')
      return
    }
    const token = ++imageSearchToken
    pruneImageOptionThumbnails(imageOptions.value)
    loadImageOptionThumbnails(imageOptions.value, token)
    return
  }
  if (!visible) {
    if (imageSearchDebounceTimer) {
      clearTimeout(imageSearchDebounceTimer)
      imageSearchDebounceTimer = null
    }
    imageSearchToken++
    imageSearchLoading.value = false
    clearImageOptionThumbnails()
  }
}

function handleFeedbackImageChange(value: string | number | boolean | undefined) {
  const imageId = typeof value === 'string' ? value : ''
  if (!imageId) {
    clearSelectedFeedbackImage()
    return
  }
  const image = imageOptions.value.find((item) => item.id === imageId) ?? null
  if (!image) {
    clearSelectedFeedbackImage()
    return
  }
  selectedFeedbackImage.value = image
  void loadSelectedImageThumbnail(image)
}

async function loadMine() {
  loading.value = true
  try {
    const page = await getMyFeedback({
      status: statusFilter.value,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = page.items
    pagination.page = page.page
    pagination.size = page.size
    pagination.total = page.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '反馈列表加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadAdmin() {
  if (!canManage.value) return
  adminLoading.value = true
  try {
    const page = await getAdminFeedback({
      keyword: adminKeyword.value || undefined,
      status: adminStatusFilter.value,
      page: adminPagination.page,
      size: adminPagination.size,
    })
    adminRows.value = page.items
    adminPagination.page = page.page
    adminPagination.size = page.size
    adminPagination.total = page.total
  } catch (error) {
    ElMessage.error(errorMessage(error, '反馈管理列表加载失败'))
  } finally {
    adminLoading.value = false
  }
}

function feedbackMatchesStatus(record: FeedbackRecord, status: FeedbackStatus | '') {
  return !status || record.status === status
}

function feedbackMatchesKeyword(record: FeedbackRecord, keyword: string) {
  const query = keyword.trim().toLowerCase()
  if (!query) return true
  return [record.title, record.content, record.username, record.displayName]
    .filter(Boolean)
    .some((value) => value.toLowerCase().includes(query))
}

function feedbackMatchesAdminFilters(record: FeedbackRecord) {
  return feedbackMatchesStatus(record, adminStatusFilter.value) && feedbackMatchesKeyword(record, adminKeyword.value)
}

function prependMineFeedback(record: FeedbackRecord, incrementTotal = true) {
  const existed = rows.value.some((item) => item.id === record.id)
  rows.value = [record, ...rows.value.filter((item) => item.id !== record.id)].slice(0, pagination.size)
  if (!existed && incrementTotal) {
    pagination.total += 1
  }
}

function prependAdminFeedback(record: FeedbackRecord, incrementTotal = true) {
  if (!canManage.value || !feedbackMatchesAdminFilters(record)) return
  const existed = adminRows.value.some((item) => item.id === record.id)
  adminRows.value = [record, ...adminRows.value.filter((item) => item.id !== record.id)].slice(0, adminPagination.size)
  if (!existed && incrementTotal) {
    adminPagination.total += 1
  }
}

async function refreshAdminIfAllowed(record?: FeedbackRecord) {
  if (!canManage.value) return
  await loadAdmin()
  if (record) {
    prependAdminFeedback(record, false)
  }
}

async function submitFeedback() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写反馈标题和内容')
    return
  }
  submitting.value = true
  try {
    const created = await createFeedback({
      type: form.type,
      title: form.title,
      content: form.content,
      imageId: form.imageId || null,
    })
    ElMessage.success('反馈已提交')
    form.title = ''
    form.content = ''
    form.imageId = ''
    imageOptions.value = []
    clearSelectedFeedbackImage()
    statusFilter.value = ''
    pagination.page = 1
    prependMineFeedback(created)
    prependAdminFeedback(created)
    await loadMine()
    prependMineFeedback(created, false)
    await refreshAdminIfAllowed(created)
    activeTab.value = 'mine'
  } catch (error) {
    ElMessage.error(errorMessage(error, '反馈提交失败'))
  } finally {
    submitting.value = false
  }
}

async function closeMine(row: FeedbackRecord) {
  try {
    await closeFeedback(row.id)
    ElMessage.success('反馈已关闭')
    await loadMine()
    await refreshAdminIfAllowed()
  } catch (error) {
    ElMessage.error(errorMessage(error, '反馈关闭失败'))
  }
}

function openHandleDialog(row: FeedbackRecord) {
  selectedFeedback.value = row
  handleForm.status = row.status === 'OPEN' ? 'IN_PROGRESS' : row.status
  handleForm.response = row.response ?? ''
  handleDialogVisible.value = true
}

async function submitHandle() {
  if (!selectedFeedback.value) return
  handlingId.value = selectedFeedback.value.id
  try {
    await handleFeedback(selectedFeedback.value.id, {
      status: handleForm.status,
      response: handleForm.response || null,
    })
    ElMessage.success('反馈处理状态已保存')
    handleDialogVisible.value = false
    await loadAdmin()
    await loadMine()
  } catch (error) {
    ElMessage.error(errorMessage(error, '反馈处理失败'))
  } finally {
    handlingId.value = ''
  }
}

function applyMineFilter() {
  pagination.page = 1
  void loadMine()
}

function handleMinePageSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  void loadMine()
}

function handleMinePageChange(page: number) {
  pagination.page = page
  void loadMine()
}

function applyAdminFilter() {
  adminPagination.page = 1
  void loadAdmin()
}

function handleAdminPageSizeChange(size: number) {
  adminPagination.size = size
  adminPagination.page = 1
  void loadAdmin()
}

function handleAdminPageChange(page: number) {
  adminPagination.page = page
  void loadAdmin()
}

function handleTabChange(name: string | number) {
  if (name === 'mine') {
    void loadMine()
    return
  }
  if (name === 'admin') {
    void loadAdmin()
  }
}

onMounted(async () => {
  await loadMine()
  if (canManage.value) {
    await loadAdmin()
  }
})

onBeforeUnmount(() => {
  if (imageSearchDebounceTimer) {
    clearTimeout(imageSearchDebounceTimer)
    imageSearchDebounceTimer = null
  }
  imageSearchToken++
  imageSearchLoading.value = false
  clearImageOptionThumbnails()
  selectedImageThumbnailToken++
  revokeSelectedImageThumbnail()
})
</script>

<template>
  <section class="workspace-page">
    <div class="surface surface-pad workspace-fixed-panel feedback-page">
      <el-tabs v-model="activeTab" class="workspace-fixed-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="提交反馈" name="submit">
          <div class="workspace-tab-panel">
            <div class="workspace-table-scroll-region">
              <div class="feedback-submit-workspace">
                <section class="feedback-submit-panel">
                  <el-form label-width="86px">
                    <el-form-item label="类型">
                      <el-select v-model="form.type">
                        <el-option label="一般反馈" value="GENERAL" />
                        <el-option label="图片问题" value="IMAGE" />
                        <el-option label="功能建议" value="SUGGESTION" />
                        <el-option label="缺陷报告" value="BUG" />
                      </el-select>
                    </el-form-item>
                    <el-form-item label="关联图片">
                      <el-select
                        v-model="form.imageId"
                        class="feedback-image-select"
                        clearable
                        filterable
                        remote
                        reserve-keyword
                        :remote-method="searchFeedbackImages"
                        :loading="imageSearchLoading"
                        placeholder="可选，搜索并选择图片"
                        no-data-text="暂无可选图片"
                        no-match-text="未找到匹配图片"
                        @change="handleFeedbackImageChange"
                        @visible-change="handleImageSelectVisibleChange"
                      >
                        <el-option
                          v-for="image in imageOptions"
                          :key="image.id"
                          :label="image.title"
                          :value="image.id"
                          class="feedback-image-select-option"
                          style="height: 58px; padding-top: 6px; padding-bottom: 6px"
                        >
                          <div class="feedback-image-option">
                            <div class="feedback-image-option-thumb">
                              <img v-if="imageOptionThumbnailUrls[image.id]" :src="imageOptionThumbnailUrls[image.id]" alt="" />
                              <span v-else>{{ imageOptionThumbnailFailed[image.id] ? '无图' : '加载中' }}</span>
                            </div>
                            <div class="feedback-image-option-main">
                              <strong>{{ image.title }}</strong>
                              <span>{{ imageOptionMeta(image) }}</span>
                            </div>
                          </div>
                        </el-option>
                      </el-select>
                    </el-form-item>
                    <el-form-item label="标题">
                      <el-input v-model="form.title" maxlength="160" show-word-limit />
                    </el-form-item>
                    <el-form-item label="内容">
                      <el-input v-model="form.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
                    </el-form-item>
                    <el-form-item>
                      <el-button type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
                    </el-form-item>
                  </el-form>
                </section>

                <aside class="feedback-submit-context">
                  <section class="feedback-context-section">
                    <div class="feedback-context-head">
                      <strong>关联图片预览</strong>
                      <span>确认问题对象</span>
                    </div>
                    <div v-if="selectedFeedbackImage" class="feedback-selected-image">
                      <div class="feedback-selected-image-thumb">
                        <img v-if="selectedImageThumbnailUrl" :src="selectedImageThumbnailUrl" alt="" />
                        <span v-else>{{ selectedImageThumbnailFailed ? '无图' : selectedImageThumbnailLoading ? '加载中' : '暂无预览' }}</span>
                      </div>
                      <div class="feedback-selected-image-meta">
                        <strong :title="selectedFeedbackImage.title">{{ selectedFeedbackImage.title }}</strong>
                        <span :title="selectedFeedbackImage.originalFilename">{{ selectedFeedbackImage.originalFilename }}</span>
                        <div class="feedback-selected-image-facts">
                          <span>{{ selectedFeedbackImage.category?.name ?? '未分类' }}</span>
                          <span>{{ imageDimensions(selectedFeedbackImage) }}</span>
                          <span>{{ formatBytes(selectedFeedbackImage.sizeBytes) }}</span>
                        </div>
                        <div class="feedback-selected-image-details">
                          <div>
                            <small>上传时间</small>
                            <strong>{{ formatDateTime(selectedFeedbackImage.createdAt) }}</strong>
                          </div>
                          <div>
                            <small>类型</small>
                            <strong :title="selectedFeedbackImage.mimeType">{{ selectedFeedbackImage.mimeType }}</strong>
                          </div>
                        </div>
                        <div class="feedback-selected-image-tags">
                          <small>标签</small>
                          <div v-if="selectedImageTagGroups.length" class="feedback-selected-image-tag-groups">
                            <span
                              v-for="group in selectedImageTagGroups"
                              :key="group.key"
                              class="feedback-selected-image-tag-group"
                              :title="`${group.label}：${group.names.join('、')}`"
                            >
                              <strong>{{ group.label }}：</strong>{{ group.names.join('、') }}
                            </span>
                            <el-tooltip
                              v-if="selectedImageHiddenTagCount"
                              effect="light"
                              placement="top"
                              popper-class="feedback-selected-image-tag-tooltip"
                            >
                              <template #content>
                                <div class="feedback-selected-image-tag-tooltip-content">
                                  <div v-for="group in selectedImageHiddenTagGroups" :key="group.key">
                                    <strong>{{ group.label }}：</strong>{{ group.names.join('、') }}
                                  </div>
                                </div>
                              </template>
                              <span class="feedback-selected-image-tag-more">+{{ selectedImageHiddenTagCount }}</span>
                            </el-tooltip>
                          </div>
                          <span v-else class="feedback-selected-image-tag-empty">暂无标签</span>
                        </div>
                      </div>
                    </div>
                    <div v-else class="feedback-context-empty">
                      可选择一张图片帮助定位问题
                    </div>
                  </section>

                  <section class="feedback-context-section">
                    <div class="feedback-context-head">
                      <strong>我的近期反馈</strong>
                      <span>最近 {{ recentMineFeedback.length }} 条</span>
                    </div>
                    <div v-if="recentMineFeedback.length" class="feedback-recent-list">
                      <div v-for="item in recentMineFeedback" :key="item.id" class="feedback-recent-item">
                        <div class="feedback-recent-main">
                          <strong :title="item.title">{{ item.title }}</strong>
                          <span>{{ formatDateTime(item.createdAt) }}</span>
                        </div>
                        <el-tag :type="statusTagType(item.status)" size="small" effect="light">{{ statusLabel(item.status) }}</el-tag>
                      </div>
                    </div>
                    <div v-else class="feedback-context-empty">
                      暂无历史反馈
                    </div>
                  </section>
                </aside>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="我的反馈" name="mine">
          <div class="workspace-tab-panel">
            <div class="toolbar-row">
              <el-select v-model="statusFilter" class="feedback-status-select" placeholder="状态" clearable @change="applyMineFilter">
                <el-option label="待处理" value="OPEN" />
                <el-option label="处理中" value="IN_PROGRESS" />
                <el-option label="已解决" value="RESOLVED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
            </div>

          <div class="workspace-table-scroll-region">
            <el-table v-loading="loading" :data="rows" stripe>
              <el-table-column label="标题" min-width="220">
                <template #default="{ row }">
                  <div class="feedback-title-cell">
                    <strong>{{ row.title }}</strong>
                    <span>{{ row.content }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="关联图片" min-width="180">
                <template #default="{ row }">
                  <span class="feedback-linked-image" :title="row.imageTitle || ''">{{ row.imageTitle || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="回复" min-width="220">
                <template #default="{ row }">{{ row.response || '-' }}</template>
              </el-table-column>
              <el-table-column label="提交时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button v-if="row.status !== 'CLOSED'" link type="primary" @click="closeMine(row)">关闭</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
            <div class="pagination-row workspace-pagination-row">
              <el-pagination
                v-model:current-page="pagination.page"
                v-model:page-size="pagination.size"
                :page-sizes="[20, 50, 100]"
                :total="pagination.total"
                background
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleMinePageSizeChange"
                @current-change="handleMinePageChange"
              />
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canManage" label="反馈管理" name="admin">
          <div class="workspace-tab-panel">
            <div class="toolbar-row">
              <el-input v-model="adminKeyword" class="feedback-admin-keyword" placeholder="标题、内容、用户" clearable />
              <el-select v-model="adminStatusFilter" class="feedback-status-select" placeholder="状态" clearable>
                <el-option label="待处理" value="OPEN" />
                <el-option label="处理中" value="IN_PROGRESS" />
                <el-option label="已解决" value="RESOLVED" />
                <el-option label="已关闭" value="CLOSED" />
              </el-select>
              <el-button @click="applyAdminFilter">筛选</el-button>
            </div>
          <div class="workspace-table-scroll-region">
            <el-table v-loading="adminLoading" :data="adminRows" stripe>
              <el-table-column label="反馈" min-width="260">
                <template #default="{ row }">
                  <div class="feedback-title-cell">
                    <strong>{{ row.title }}</strong>
                    <span>{{ row.content }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="用户" width="140">
                <template #default="{ row }">{{ row.displayName || row.username }}</template>
              </el-table-column>
              <el-table-column label="关联图片" min-width="180">
                <template #default="{ row }">
                  <span class="feedback-linked-image" :title="row.imageTitle || ''">{{ row.imageTitle || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="时间" width="170">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openHandleDialog(row)">处理</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
            <div class="pagination-row workspace-pagination-row">
              <el-pagination
                v-model:current-page="adminPagination.page"
                v-model:page-size="adminPagination.size"
                :page-sizes="[20, 50, 100]"
                :total="adminPagination.total"
                background
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="handleAdminPageSizeChange"
                @current-change="handleAdminPageChange"
              />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog v-model="handleDialogVisible" title="处理反馈" width="560px">
      <el-form label-width="86px">
        <el-form-item label="状态">
          <el-select v-model="handleForm.status">
            <el-option label="待处理" value="OPEN" />
            <el-option label="处理中" value="IN_PROGRESS" />
            <el-option label="已解决" value="RESOLVED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复">
          <el-input v-model="handleForm.response" type="textarea" :rows="5" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="Boolean(handlingId)" @click="submitHandle">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.feedback-page {
  display: flex;
  flex-direction: column;
}

.feedback-submit-workspace {
  display: grid;
  gap: 28px;
  grid-template-columns: minmax(420px, 0.95fr) minmax(320px, 0.65fr);
  min-height: 100%;
}

.feedback-submit-panel {
  max-width: 780px;
  min-width: 0;
}

.feedback-submit-context {
  border-left: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
  padding-left: 28px;
}

.feedback-context-section {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.feedback-context-head {
  align-items: baseline;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-width: 0;
}

.feedback-context-head strong {
  color: #17201f;
  font-size: 14px;
}

.feedback-context-head span {
  color: #66736f;
  font-size: 12px;
}

.feedback-context-empty {
  align-items: center;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #64748b;
  display: flex;
  font-size: 13px;
  justify-content: center;
  min-height: 112px;
  padding: 18px;
}

.feedback-image-select {
  width: 100%;
}

.feedback-image-select-option {
  height: 58px;
  padding-bottom: 6px;
  padding-top: 6px;
}

.feedback-image-option {
  align-items: center;
  display: flex;
  gap: 10px;
  min-width: 0;
  width: 100%;
}

.feedback-image-option-thumb {
  align-items: center;
  aspect-ratio: 4 / 3;
  background: #eef2f7;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #94a3b8;
  display: flex;
  flex: 0 0 44px;
  font-size: 11px;
  justify-content: center;
  overflow: hidden;
}

.feedback-image-option-thumb img {
  display: block;
  height: 100%;
  object-fit: cover;
  width: 100%;
}

.feedback-image-option-main {
  display: flex;
  flex-direction: column;
  justify-content: center;
  line-height: 1.35;
  min-width: 0;
}

.feedback-image-option-main strong {
  overflow: hidden;
  color: #17201f;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-image-option-main span {
  overflow: hidden;
  color: #66736f;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-selected-image {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.feedback-selected-image-thumb {
  align-items: center;
  aspect-ratio: 16 / 10;
  background: #eef2f7;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #94a3b8;
  display: flex;
  font-size: 13px;
  justify-content: center;
  overflow: hidden;
  width: 100%;
}

.feedback-selected-image-thumb img {
  display: block;
  height: 100%;
  object-fit: contain;
  width: 100%;
}

.feedback-selected-image-meta {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.feedback-selected-image-meta strong,
.feedback-recent-main strong {
  color: #17201f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-selected-image-meta span,
.feedback-recent-main span {
  color: #66736f;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-selected-image-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.feedback-selected-image-facts span {
  background: #f1f5f9;
  border-radius: 999px;
  color: #475569;
  max-width: 100%;
  padding: 3px 8px;
}

.feedback-selected-image-details {
  display: grid;
  gap: 8px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding-top: 2px;
}

.feedback-selected-image-details div,
.feedback-selected-image-tags {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.feedback-selected-image-details small,
.feedback-selected-image-tags small {
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.3;
}

.feedback-selected-image-details strong {
  color: #475569;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
}

.feedback-selected-image-tag-groups {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.feedback-selected-image-tags .feedback-selected-image-tag-group,
.feedback-selected-image-tags .feedback-selected-image-tag-more,
.feedback-selected-image-tags .feedback-selected-image-tag-empty {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  color: #475569;
  font-size: 12px;
  line-height: 1.35;
  max-width: 100%;
  overflow: hidden;
  padding: 4px 7px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-selected-image-tags .feedback-selected-image-tag-empty {
  background: transparent;
  border-color: transparent;
  color: #94a3b8;
  padding-left: 0;
}

.feedback-selected-image-tag-group strong {
  color: #334155;
  font-size: 12px;
  font-weight: 650;
}

:global(.feedback-selected-image-tag-tooltip) {
  max-width: 320px;
}

:global(.feedback-selected-image-tag-tooltip-content) {
  display: grid;
  gap: 5px;
}

:global(.feedback-selected-image-tag-tooltip-content div) {
  color: #475569;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

:global(.feedback-selected-image-tag-tooltip-content strong) {
  color: #334155;
  font-weight: 650;
}

.feedback-recent-list {
  display: grid;
  gap: 8px;
}

.feedback-recent-item {
  align-items: center;
  border-bottom: 1px solid #edf2f7;
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(0, 1fr) auto;
  min-width: 0;
  padding: 0 0 8px;
}

.feedback-recent-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.feedback-recent-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.feedback-linked-image {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.feedback-status-select {
  width: 160px;
}

.feedback-admin-keyword {
  width: 260px;
}

.feedback-title-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.feedback-title-cell span {
  color: #66736f;
  line-height: 1.5;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .feedback-submit-workspace {
    grid-template-columns: 1fr;
  }

  .feedback-submit-context {
    border-left: 0;
    border-top: 1px solid #e2e8f0;
    padding-left: 0;
    padding-top: 20px;
  }

  .pagination-row {
    justify-content: flex-start;
  }

  .feedback-status-select,
  .feedback-admin-keyword {
    width: 100%;
  }
}
</style>
