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
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const adminLoading = ref(false)
const activeTab = ref('mine')
const statusFilter = ref<FeedbackStatus | ''>('')
const adminStatusFilter = ref<FeedbackStatus | ''>('')
const adminKeyword = ref('')
const rows = ref<FeedbackRecord[]>([])
const adminRows = ref<FeedbackRecord[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const adminPagination = reactive({ page: 1, size: 20, total: 0 })
const form = reactive({ type: 'GENERAL', title: '', content: '', imageId: '' })
const submitting = ref(false)
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

async function submitFeedback() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写反馈标题和内容')
    return
  }
  submitting.value = true
  try {
    await createFeedback({
      type: form.type,
      title: form.title,
      content: form.content,
      imageId: form.imageId || null,
    })
    ElMessage.success('反馈已提交')
    form.title = ''
    form.content = ''
    form.imageId = ''
    pagination.page = 1
    await loadMine()
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

function applyAdminFilter() {
  adminPagination.page = 1
  void loadAdmin()
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
    <div class="page-head">
      <div>
        <p>提交问题、建议或图片相关反馈，并跟踪处理状态。</p>
      </div>
    </div>

    <div class="surface surface-pad workspace-scroll-region feedback-page">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="我的反馈" name="mine">
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
                <el-input v-model="form.imageId" clearable placeholder="可选，填写图片 ID" />
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

          <div class="toolbar-row">
            <el-select v-model="statusFilter" placeholder="状态" clearable @change="applyMineFilter">
              <el-option label="待处理" value="OPEN" />
              <el-option label="处理中" value="IN_PROGRESS" />
              <el-option label="已解决" value="RESOLVED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </div>

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
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              :total="pagination.total"
              background
              layout="total, prev, pager, next"
              @current-change="loadMine"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane v-if="canManage" label="反馈管理" name="admin">
          <div class="toolbar-row">
            <el-input v-model="adminKeyword" placeholder="标题、内容、用户" clearable />
            <el-select v-model="adminStatusFilter" placeholder="状态" clearable>
              <el-option label="待处理" value="OPEN" />
              <el-option label="处理中" value="IN_PROGRESS" />
              <el-option label="已解决" value="RESOLVED" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
            <el-button @click="applyAdminFilter">筛选</el-button>
          </div>
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
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="adminPagination.page"
              v-model:page-size="adminPagination.size"
              :total="adminPagination.total"
              background
              layout="total, prev, pager, next"
              @current-change="loadAdmin"
            />
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
  margin-bottom: 18px;
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
</style>
