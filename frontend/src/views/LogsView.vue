<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { Download, Refresh, Timer } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { computed, onMounted, reactive, ref } from 'vue'
import {
  createAuditArchiveRun,
  getAuditArchiveRuns,
  getAuditLogs,
  getAuditRetention,
  type AuditLog,
  type AuditArchiveRun,
} from '../api/audit'

const loading = ref(false)
const archiving = ref(false)

const retention = reactive({
  days: 180,
  enabled: true,
  schedule: '每天 02:30',
  storage: 'RustFS',
  expiredCount: 0,
})

const archiveRuns = ref<AuditArchiveRun[]>([])
const auditLogs = ref<AuditLog[]>([])
const keyword = ref('')

const latestRunLabel = computed(() => {
  const latestRun = archiveRuns.value[0]
  return latestRun ? formatDateTime(latestRun.startedAt) : '暂无归档记录'
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
}

async function loadArchiveRuns() {
  archiveRuns.value = await getAuditArchiveRuns()
}

async function loadAuditLogs() {
  auditLogs.value = await getAuditLogs({ keyword: keyword.value || undefined, limit: 100 })
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

function exportCurrentLogs() {
  const header = ['操作', '对象', '时间', '详情']
  const lines = auditRows.value.map((row) => [row.action, row.target, row.time, row.detailJson ?? ''])
  const csv = [header, ...lines]
    .map((line) => line.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(','))
    .join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'audit-logs.csv'
  link.click()
  URL.revokeObjectURL(url)
}

async function archiveNow() {
  archiving.value = true
  try {
    const run = await createAuditArchiveRun()
    ElMessage.success(run.status === 'SUCCESS' ? '审计日志归档清理已完成' : '审计日志归档任务已记录')
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
      <div class="head-actions">
        <el-button :icon="Download" @click="exportCurrentLogs">导出当前筛选</el-button>
        <el-button type="primary" :icon="Refresh" :loading="archiving" @click="archiveNow">
          立即归档并清理
        </el-button>
      </div>
    </div>

    <div v-loading="loading" class="surface surface-pad">
      <div class="retention-strip">
        <div class="retention-item">
          <span class="retention-label">数据库保留</span>
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
      </div>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="系统只会清理已经成功写入 RustFS 归档文件的历史日志；归档失败时数据库记录会保留。"
      />

      <div class="section-title">
        <div>
          <h2>近期审计记录</h2>
          <p>用于快速查看最近保留期内的操作记录。</p>
        </div>
      </div>

      <div class="toolbar-row">
        <el-input v-model="keyword" placeholder="搜索操作或对象" style="max-width: 320px" clearable />
        <el-button @click="loadAuditLogs">筛选</el-button>
      </div>

      <el-table :data="auditRows" stripe>
        <el-table-column prop="action" label="操作" width="180" />
        <el-table-column prop="target" label="对象" />
        <el-table-column prop="time" label="时间" width="180" />
        <el-table-column prop="detailJson" label="详情" min-width="220" show-overflow-tooltip />
      </el-table>

      <div class="section-title archive-title">
        <div>
          <h2>归档历史</h2>
          <p>记录每次保存和清理结果，便于审计追溯。</p>
        </div>
        <el-tag type="success" effect="light">
          <el-icon><Timer /></el-icon>
          最近执行 {{ latestRunLabel }}
        </el-tag>
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
    </div>
  </section>
</template>

<style scoped>
.head-actions {
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

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 24px 0 12px;
}

.section-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
}

.section-title p {
  margin: 0;
  color: #64748b;
}

.archive-title .el-tag {
  gap: 6px;
}

@media (max-width: 720px) {
  .page-head,
  .section-title {
    align-items: flex-start;
    flex-direction: column;
  }

  .head-actions {
    justify-content: flex-start;
  }
}
</style>
