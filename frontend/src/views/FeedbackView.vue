<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
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
import { getImages, type ImageRecord } from '../api/images'
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
const handlingId = ref('')
const handleForm = reactive<{ status: FeedbackStatus; response: string }>({ status: 'IN_PROGRESS', response: '' })
const handleDialogVisible = ref(false)
const selectedFeedback = ref<FeedbackRecord | null>(null)
const canManage = computed(() => auth.hasPermission('interaction:manage'))

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

async function searchFeedbackImages(query: string) {
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
    }
  } catch (error) {
    if (token === imageSearchToken) {
      imageOptions.value = []
      ElMessage.error(errorMessage(error, '图片候选加载失败'))
    }
  } finally {
    if (token === imageSearchToken) {
      imageSearchLoading.value = false
    }
  }
}

function handleImageSelectVisibleChange(visible: boolean) {
  if (visible && imageOptions.value.length === 0) {
    void searchFeedbackImages('')
  }
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
</script>

<template>
  <section class="workspace-page">
    <div class="surface surface-pad workspace-fixed-panel feedback-page">
      <el-tabs v-model="activeTab" class="workspace-fixed-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="提交反馈" name="submit">
          <div class="workspace-tab-panel">
            <div class="workspace-table-scroll-region">
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
                      @visible-change="handleImageSelectVisibleChange"
                    >
                      <el-option v-for="image in imageOptions" :key="image.id" :label="image.title" :value="image.id">
                        <div class="feedback-image-option">
                          <strong>{{ image.title }}</strong>
                          <span>{{ imageOptionMeta(image) }}</span>
                        </div>
                      </el-option>
                    </el-select>
                  </el-form-item>
                  <el-form-item label="标题">
                    <el-input v-model="form.title" maxlength="160" show-word-limit />
                  </el-form-item>
                  <el-form-item label="内容">
                    <el-input v-model="form.content" type="textarea" :rows="4" maxlength="2000" show-word-limit />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="submitting" @click="submitFeedback">提交反馈</el-button>
                  </el-form-item>
                </el-form>
              </section>
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

.feedback-submit-panel {
  max-width: 760px;
}

.feedback-image-select {
  width: 100%;
}

.feedback-image-option {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  line-height: 1.35;
}

.feedback-image-option strong {
  overflow: hidden;
  color: #17201f;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.feedback-image-option span {
  overflow: hidden;
  color: #66736f;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  .pagination-row {
    justify-content: flex-start;
  }

  .feedback-status-select,
  .feedback-admin-keyword {
    width: 100%;
  }
}
</style>
