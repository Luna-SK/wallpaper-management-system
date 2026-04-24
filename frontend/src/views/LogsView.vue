<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Download, Refresh, Search, Timer } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createAuditArchiveRun,
  exportAuditLogs,
  getAuditArchiveRuns,
  getAuditLogs,
  getAuditRetention,
  type AuditLog,
  type AuditArchiveRun,
} from '../api/audit'

const loading = ref(false)
const archiving = ref(false)
const exporting = ref(false)
const auditTab = ref<'logs' | 'archives'>('logs')

const retention = reactive({
  days: 180,
  enabled: true,
  schedule: '每天 02:30',
  storage: 'RustFS',
  expiredCount: 0,
  expiredArchiveRunCount: 0,
})

const archiveRuns = ref<AuditArchiveRun[]>([])
const auditLogs = ref<AuditLog[]>([])
const keyword = ref('')
const auditFilterDateRange = ref<[string, string] | [] | null>([])
const auditPagination = reactive({ page: 1, size: 20, total: 0 })
const archivePagination = reactive({ page: 1, size: 20, total: 0 })

const latestRunLabel = computed(() => {
  const latestRun = archiveRuns.value[0]
  return latestRun ? formatDateTime(latestRun.startedAt) : '暂无归档记录'
})

const archiveRunSummary = computed(() => {
  if (archivePagination.total === 0) {
    return '暂无归档记录'
  }
  if (archivePagination.page !== 1) {
    return `第 ${archivePagination.page} 页 / 共 ${archivePagination.total} 条`
  }
  return `最近执行 ${latestRunLabel.value}`
})

const archiveRunRows = computed(() =>
  archiveRuns.value.map((run) => ({
    ...run,
    startedAtText: formatDateTime(run.startedAt),
    triggerTypeText: triggerLabel(run.triggerType),
    cutoffTimeText: formatDateTime(run.cutoffTime),
    statusText: statusLabel(run.status),
    objectKey: run.archiveObjectKey ?? '-',
  })),
)

const auditRows = computed(() =>
  auditLogs.value.map((log) => ({
    ...log,
    target: [log.targetType, log.targetId].filter(Boolean).join(' / ') || '-',
    time: formatDateTime(log.createdAt),
  })),
)

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function formatDateTime(value: string | null) {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').slice(0, 19)
}

function triggerLabel(triggerType: string) {
  if (triggerType === 'SCHEDULED') {
    return '定时'
  }
  if (triggerType === 'MANUAL') {
    return '手动'
  }
  return triggerType
}

function statusLabel(status: string) {
  if (status === 'SUCCESS') {
    return '成功'
  }
  if (status === 'FAILED') {
    return '失败'
  }
  if (status === 'RUNNING') {
    return '执行中'
  }
  return status
}

function cronLabel(cron: string) {
  const match = cron.match(/^0\s+(\d{1,2})\s+(\d{1,2})\s+\*\s+\*\s+\*$/)
  if (!match) {
    return cron
  }
  const minute = match[1].padStart(2, '0')
  const hour = match[2].padStart(2, '0')
  return `每天 ${hour}:${minute}`
}

async function loadRetention() {
  const data = await getAuditRetention()
  retention.days = data.settings.retentionDays
  retention.enabled = data.settings.archiveEnabled
  retention.schedule = cronLabel(data.settings.archiveCron)
  retention.storage = data.settings.archiveStorage === 'RUSTFS' ? 'RustFS' : data.settings.archiveStorage
  retention.expiredCount = data.expiredCount
  retention.expiredArchiveRunCount = data.expiredArchiveRunCount ?? 0
}

async function loadArchiveRuns() {
  const page = await getAuditArchiveRuns({ page: archivePagination.page, size: archivePagination.size })
  archiveRuns.value = page.items
  archivePagination.page = page.page
  archivePagination.size = page.size
  archivePagination.total = page.total
}

async function loadAuditLogs() {
  const page = await getAuditLogs({
    ...auditFilterParams(),
    page: auditPagination.page,
    size: auditPagination.size,
  })
  auditLogs.value = page.items
  auditPagination.page = page.page
  auditPagination.size = page.size
  auditPagination.total = page.total
}

async function refreshAuditState() {
  loading.value = true
  try {
    await Promise.all([loadRetention(), loadArchiveRuns(), loadAuditLogs()])
  } catch (error) {
    ElMessage.error(errorMessage(error, '审计日志归档状态加载失败'))
  } finally {
    loading.value = false
  }
}

function auditFilterParams() {
  const params: { keyword?: string; startDate?: string; endDate?: string } = {
    keyword: keyword.value || undefined,
  }
  if (auditFilterDateRange.value?.length === 2) {
    const [startDate, endDate] = auditFilterDateRange.value
    params.startDate = startDate
    params.endDate = endDate
  }
  return params
}

async function exportFilteredAuditLogs() {
  exporting.value = true
  try {
    await exportAuditLogs(auditFilterParams())
  } catch (error) {
    ElMessage.error(errorMessage(error, '审计日志导出失败'))
  } finally {
    exporting.value = false
  }
}

function applyAuditFilter() {
  auditPagination.page = 1
  void loadAuditLogs()
}

function resetAuditFilter() {
  keyword.value = ''
  auditFilterDateRange.value = []
  auditPagination.page = 1
  void loadAuditLogs()
}

function handleAuditPageChange(page: number) {
  auditPagination.page = page
  void loadAuditLogs()
}

function handleAuditPageSizeChange(size: number) {
  auditPagination.size = size
  auditPagination.page = 1
  void loadAuditLogs()
}

function handleArchivePageChange(page: number) {
  archivePagination.page = page
  void loadArchiveRuns()
}

function handleArchivePageSizeChange(size: number) {
  archivePagination.size = size
  archivePagination.page = 1
  void loadArchiveRuns()
}

async function archiveNow() {
  archiving.value = true
  try {
    const run = await createAuditArchiveRun()
    ElMessage.success(run.status === 'SUCCESS' ? '审计日志归档清理已完成' : '审计日志归档任务已记录')
    archivePagination.page = 1
    await refreshAuditState()
  } catch (error) {
    ElMessage.error(errorMessage(error, '审计日志归档清理失败'))
  } finally {
    archiving.value = false
  }
}

onMounted(refreshAuditState)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>审计日志</h1>
        <p>记录登录、上传、标签、预览、下载和管理操作，历史日志归档后再清理。</p>
      </div>
    </div>

    <div v-loading="loading" class="surface surface-pad">
      <div class="retention-strip">
        <div class="retention-item">
          <span class="retention-label">近期记录保留</span>
          <strong>{{ retention.days }} 天</strong>
        </div>
        <div class="retention-item">
          <span class="retention-label">自动归档</span>
          <el-tag :type="retention.enabled ? 'success' : 'info'" effect="light">
            {{ retention.enabled ? '已启用' : '已停用' }}
          </el-tag>
        </div>
        <div class="retention-item">
          <span class="retention-label">执行时间</span>
          <strong>{{ retention.schedule }}</strong>
        </div>
        <div class="retention-item">
          <span class="retention-label">归档位置</span>
          <strong>{{ retention.storage }}</strong>
        </div>
        <div class="retention-item">
          <span class="retention-label">待归档</span>
          <strong>{{ retention.expiredCount }} 条</strong>
        </div>
        <div class="retention-item">
          <span class="retention-label">归档历史保留</span>
          <strong>{{ retention.days }} 天</strong>
        </div>
        <div class="retention-item">
          <span class="retention-label">待清理历史</span>
          <strong>{{ retention.expiredArchiveRunCount }} 条</strong>
        </div>
      </div>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        :title="`近期审计记录在数据库保留 ${retention.days} 天；成功写入 ${retention.storage} 后会清理过期日志，归档历史数据库记录也按 ${retention.days} 天保留。`"
      />

      <div class="audit-scope-row">
        <el-radio-group v-model="auditTab">
          <el-radio-button label="logs">近期审计记录</el-radio-button>
          <el-radio-button label="archives">归档历史</el-radio-button>
        </el-radio-group>
      </div>

      <div v-if="auditTab === 'logs'" class="audit-panel">
        <div class="toolbar-row audit-toolbar">
          <div class="audit-filter-row">
            <el-input
              v-model="keyword"
              placeholder="搜索操作或对象"
              :prefix-icon="Search"
              clearable
              @clear="applyAuditFilter"
              @keyup.enter="applyAuditFilter"
            />
            <el-date-picker
              v-model="auditFilterDateRange"
              class="audit-filter-date-range"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
            />
            <div class="audit-filter-actions">
              <el-button @click="applyAuditFilter">筛选</el-button>
              <el-button @click="resetAuditFilter">重置</el-button>
            </div>
          </div>

          <div class="audit-export-actions">
            <el-button type="primary" plain :icon="Download" :loading="exporting" @click="exportFilteredAuditLogs">
              导出筛选结果
            </el-button>
          </div>
        </div>

        <el-table :data="auditRows" stripe>
          <el-table-column prop="action" label="操作" width="180" />
          <el-table-column prop="target" label="对象" />
          <el-table-column prop="time" label="时间" width="180" />
          <el-table-column prop="detailJson" label="详情" min-width="220" show-overflow-tooltip />
        </el-table>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="auditPagination.page"
            v-model:page-size="auditPagination.size"
            :page-sizes="[20, 50, 100]"
            :total="auditPagination.total"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleAuditPageSizeChange"
            @current-change="handleAuditPageChange"
          />
        </div>
      </div>

      <div v-else class="audit-panel">
        <div class="archive-toolbar">
          <div class="archive-actions">
            <el-tag type="success" effect="light">
              <el-icon><Timer /></el-icon>
              {{ archiveRunSummary }}
            </el-tag>
            <el-button type="primary" :icon="Refresh" :loading="archiving" @click="archiveNow">
              立即归档并清理
            </el-button>
          </div>
        </div>

        <el-table :data="archiveRunRows" stripe>
          <el-table-column prop="startedAtText" label="开始时间" width="170" />
          <el-table-column prop="triggerTypeText" label="触发方式" width="110" />
          <el-table-column prop="cutoffTimeText" label="归档截止" width="170" />
          <el-table-column prop="archivedCount" label="保存条数" width="110" />
          <el-table-column prop="deletedCount" label="清理条数" width="110" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'" effect="light">
                {{ row.statusText }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="objectKey" label="归档对象" min-width="360" show-overflow-tooltip />
        </el-table>

        <div class="pagination-row">
          <el-pagination
            v-model:current-page="archivePagination.page"
            v-model:page-size="archivePagination.size"
            :page-sizes="[20, 50, 100]"
            :total="archivePagination.total"
            background
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleArchivePageSizeChange"
            @current-change="handleArchivePageChange"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.archive-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.retention-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.retention-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 14px;
  background: #f8fafc;
}

.retention-label {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 13px;
}

.audit-scope-row {
  display: flex;
  margin: 18px 0 14px;
}

.audit-panel {
  min-width: 0;
}

.audit-toolbar {
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.audit-toolbar .el-input {
  width: 260px;
}

.audit-filter-row,
.audit-filter-actions,
.audit-export-actions {
  display: flex;
  gap: 8px;
}

.audit-filter-row,
.audit-export-actions {
  align-items: center;
  flex-wrap: wrap;
}

.audit-export-actions {
  justify-content: flex-end;
}

.audit-filter-row :deep(.audit-filter-date-range.el-date-editor) {
  flex: 0 0 300px;
  width: 300px;
}

.archive-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.archive-actions {
  align-items: center;
}

.archive-actions .el-tag {
  gap: 6px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 720px) {
  .page-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .archive-actions,
  .archive-toolbar,
  .audit-toolbar,
  .audit-export-actions,
  .pagination-row {
    justify-content: flex-start;
  }

  .audit-filter-row,
  .audit-export-actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .audit-toolbar .el-input {
    width: 100%;
  }

  .audit-filter-row :deep(.audit-filter-date-range.el-date-editor) {
    flex-basis: 100%;
    width: 100%;
  }
}
</style>
