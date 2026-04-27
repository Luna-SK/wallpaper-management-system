<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { isAxiosError } from 'axios'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  getAuditRetention,
  updateAuditRetention,
  type AuditArchiveStorage,
} from '../api/audit'
import { getSystemSettings, updateSystemSettings, type SystemSettings } from '../api/settings'

const settings = reactive({
  maxFileSizeMb: 50,
  maxBatchSizeMb: 500,
  maxFileHardLimitMb: 0,
  maxBatchHardLimitMb: 0,
  previewQuality: 'ORIGINAL' as SystemSettings['previewQuality'],
  softDeleteRetentionDays: 180,
  softDeleteCleanupEnabled: false,
  softDeleteCleanupCron: '0 0 3 * * SUN',
  watermarkEnabled: true,
  watermarkPreviewEnabled: false,
  watermarkText: '仅供授权使用',
  watermarkMode: 'CORNER' as SystemSettings['watermarkMode'],
  watermarkPosition: 'BOTTOM_RIGHT' as SystemSettings['watermarkPosition'],
  watermarkOpacityPercent: 16,
  watermarkTileDensity: 'SPARSE' as SystemSettings['watermarkTileDensity'],
  sessionIdleTimeoutEnabled: true,
  sessionIdleTimeoutMinutes: 120,
  sessionAbsoluteLifetimeEnabled: true,
  sessionAbsoluteLifetimeDays: 7,
  auditArchiveEnabled: true,
  auditRetentionDays: 180,
  auditArchiveCron: '0 30 2 * * *',
  auditArchiveStorage: 'RUSTFS' as AuditArchiveStorage,
  auditArchiveBatchSize: 1000,
})

const loading = ref(false)
const saving = ref(false)
const uploadLimitsLoaded = computed(() => settings.maxFileHardLimitMb > 0 && settings.maxBatchHardLimitMb > 0)
const watermarkActive = computed(() => settings.watermarkEnabled || settings.watermarkPreviewEnabled)
const watermarkPositions: Array<{ label: string; value: SystemSettings['watermarkPosition'] }> = [
  { label: '左上', value: 'TOP_LEFT' },
  { label: '上中', value: 'TOP_CENTER' },
  { label: '右上', value: 'TOP_RIGHT' },
  { label: '左中', value: 'CENTER_LEFT' },
  { label: '居中', value: 'CENTER' },
  { label: '右中', value: 'CENTER_RIGHT' },
  { label: '左下', value: 'BOTTOM_LEFT' },
  { label: '下中', value: 'BOTTOM_CENTER' },
  { label: '右下', value: 'BOTTOM_RIGHT' },
]

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function syncUploadLimitRange() {
  if (!uploadLimitsLoaded.value) {
    return
  }
  if (settings.maxFileSizeMb > settings.maxFileHardLimitMb) {
    settings.maxFileSizeMb = settings.maxFileHardLimitMb
  }
  if (settings.maxBatchSizeMb > settings.maxBatchHardLimitMb) {
    settings.maxBatchSizeMb = settings.maxBatchHardLimitMb
  }
  if (settings.maxBatchSizeMb < settings.maxFileSizeMb) {
    settings.maxBatchSizeMb = settings.maxFileSizeMb
  }
}

function applySystemSettings(system: SystemSettings) {
  settings.maxFileHardLimitMb = system.maxFileHardLimitMb
  settings.maxBatchHardLimitMb = system.maxBatchHardLimitMb
  settings.maxFileSizeMb = system.maxFileSizeMb
  settings.maxBatchSizeMb = system.maxBatchSizeMb
  settings.previewQuality = system.previewQuality
  settings.softDeleteRetentionDays = system.softDeleteRetentionDays
  settings.softDeleteCleanupEnabled = system.softDeleteCleanupEnabled
  settings.softDeleteCleanupCron = system.softDeleteCleanupCron
  settings.watermarkEnabled = system.watermarkEnabled
  settings.watermarkPreviewEnabled = system.watermarkPreviewEnabled
  settings.watermarkText = system.watermarkText
  settings.watermarkMode = system.watermarkMode
  settings.watermarkPosition = system.watermarkPosition
  settings.watermarkOpacityPercent = system.watermarkOpacityPercent
  settings.watermarkTileDensity = system.watermarkTileDensity
  settings.sessionIdleTimeoutEnabled = system.sessionIdleTimeoutEnabled
  settings.sessionIdleTimeoutMinutes = system.sessionIdleTimeoutMinutes
  settings.sessionAbsoluteLifetimeEnabled = system.sessionAbsoluteLifetimeEnabled
  settings.sessionAbsoluteLifetimeDays = system.sessionAbsoluteLifetimeDays
  syncUploadLimitRange()
}

async function loadSettings() {
  loading.value = true
  try {
    const [data, system] = await Promise.all([getAuditRetention(), getSystemSettings()])
    applySystemSettings(system)
    settings.auditArchiveEnabled = data.settings.archiveEnabled
    settings.auditRetentionDays = data.settings.retentionDays
    settings.auditArchiveCron = data.settings.archiveCron
    settings.auditArchiveStorage = data.settings.archiveStorage
    settings.auditArchiveBatchSize = data.settings.batchSize
  } catch (error) {
    ElMessage.error(errorMessage(error, '系统设置加载失败'))
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  saving.value = true
  try {
    syncUploadLimitRange()
    const [saved, system] = await Promise.all([
      updateAuditRetention({
        retentionDays: settings.auditRetentionDays,
        archiveEnabled: settings.auditArchiveEnabled,
        archiveCron: settings.auditArchiveCron,
        archiveStorage: settings.auditArchiveStorage,
        batchSize: settings.auditArchiveBatchSize,
      }),
      updateSystemSettings({
        maxFileSizeMb: settings.maxFileSizeMb,
        maxBatchSizeMb: settings.maxBatchSizeMb,
        previewQuality: settings.previewQuality,
        softDeleteRetentionDays: settings.softDeleteRetentionDays,
        softDeleteCleanupEnabled: settings.softDeleteCleanupEnabled,
        softDeleteCleanupCron: settings.softDeleteCleanupCron,
        watermarkEnabled: settings.watermarkEnabled,
        watermarkPreviewEnabled: settings.watermarkPreviewEnabled,
        watermarkText: settings.watermarkText,
        watermarkMode: settings.watermarkMode,
        watermarkPosition: settings.watermarkPosition,
        watermarkOpacityPercent: settings.watermarkOpacityPercent,
        watermarkTileDensity: settings.watermarkTileDensity,
        sessionIdleTimeoutEnabled: settings.sessionIdleTimeoutEnabled,
        sessionIdleTimeoutMinutes: settings.sessionIdleTimeoutMinutes,
        sessionAbsoluteLifetimeEnabled: settings.sessionAbsoluteLifetimeEnabled,
        sessionAbsoluteLifetimeDays: settings.sessionAbsoluteLifetimeDays,
      }),
    ])
    applySystemSettings(system)
    settings.auditArchiveEnabled = saved.archiveEnabled
    settings.auditRetentionDays = saved.retentionDays
    settings.auditArchiveCron = saved.archiveCron
    settings.auditArchiveStorage = saved.archiveStorage
    settings.auditArchiveBatchSize = saved.batchSize
    ElMessage.success('系统设置已保存')
  } catch (error) {
    ElMessage.error(errorMessage(error, '系统设置保存失败'))
  } finally {
    saving.value = false
  }
}

watch(() => settings.maxFileSizeMb, syncUploadLimitRange)
watch(() => settings.maxBatchSizeMb, syncUploadLimitRange)

onMounted(loadSettings)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <p>上传限制、预览质量、已停用图片清理和审计日志保留配置。</p>
      </div>
      <el-button type="primary" :loading="saving" @click="saveSettings">保存系统设置</el-button>
    </div>

    <div v-loading="loading" class="surface surface-pad">
      <el-form label-width="150px" style="max-width: 760px">
        <div class="form-section">
          <h2>基础设置</h2>
          <p v-if="uploadLimitsLoaded" class="section-copy">
            可设置范围：单文件最高 {{ settings.maxFileHardLimitMb }} MB，批量最高 {{ settings.maxBatchHardLimitMb }} MB。
          </p>
          <el-form-item v-if="uploadLimitsLoaded" label="单文件上限">
            <el-input-number v-model="settings.maxFileSizeMb" :min="1" :max="settings.maxFileHardLimitMb" controls-position="right" />
            <span class="unit-label">MB</span>
          </el-form-item>
          <el-form-item v-if="uploadLimitsLoaded" label="批量上传上限">
            <el-input-number v-model="settings.maxBatchSizeMb" :min="settings.maxFileSizeMb" :max="settings.maxBatchHardLimitMb" controls-position="right" />
            <span class="unit-label">MB</span>
          </el-form-item>
          <el-form-item label="预览质量">
            <el-radio-group v-model="settings.previewQuality">
              <el-radio-button label="ORIGINAL">原图</el-radio-button>
              <el-radio-button label="HIGH">高清</el-radio-button>
              <el-radio-button label="STANDARD">标准</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="软删除保留期">
            <el-input-number v-model="settings.softDeleteRetentionDays" :min="1" :max="3650" controls-position="right" />
            <span class="unit-label">天</span>
          </el-form-item>
          <el-form-item label="自动清理已停用图片">
            <el-switch v-model="settings.softDeleteCleanupEnabled" />
          </el-form-item>
          <el-form-item label="清理执行计划">
            <div class="cron-field">
              <el-input v-model="settings.softDeleteCleanupCron" placeholder="例如：0 0 3 * * SUN" />
              <div class="cron-help">
                <p>Spring cron 使用 6 段格式：秒 分 时 日 月 周。</p>
                <p>示例：`0 0 3 * * SUN` 每周日 03:00；`0 0 3 * * *` 每天 03:00；`0 0 * * * *` 每小时一次。</p>
              </div>
            </div>
          </el-form-item>
        </div>

        <div class="form-section">
          <h2>水印版权保护</h2>
          <p class="section-copy">下载和导出默认保留版权水印；预览水印可单独开启，避免影响后台选图和版本核对。</p>
          <el-form-item label="下载/导出水印">
            <el-switch v-model="settings.watermarkEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="预览加水印">
            <el-switch v-model="settings.watermarkPreviewEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="水印文字" required>
            <el-input
              v-model="settings.watermarkText"
              maxlength="64"
              show-word-limit
              :disabled="!watermarkActive"
              placeholder="例如：仅供授权使用"
            />
          </el-form-item>
          <el-form-item label="水印样式">
            <el-radio-group v-model="settings.watermarkMode" :disabled="!watermarkActive">
              <el-radio-button label="CORNER">角落水印</el-radio-button>
              <el-radio-button label="TILED">斜向平铺</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="settings.watermarkMode === 'CORNER'" label="水印位置">
            <el-radio-group v-model="settings.watermarkPosition" class="watermark-position-grid" :disabled="!watermarkActive">
              <el-radio-button v-for="position in watermarkPositions" :key="position.value" :label="position.value">
                {{ position.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="settings.watermarkMode === 'TILED'" label="平铺密度">
            <el-radio-group v-model="settings.watermarkTileDensity" :disabled="!watermarkActive">
              <el-radio-button label="SPARSE">稀疏</el-radio-button>
              <el-radio-button label="NORMAL">标准</el-radio-button>
              <el-radio-button label="DENSE">密集</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="水印透明度">
            <el-slider
              v-model="settings.watermarkOpacityPercent"
              :min="5"
              :max="40"
              :step="1"
              show-input
              :disabled="!watermarkActive"
            />
          </el-form-item>
        </div>

        <div class="form-section">
          <h2>会话生命周期</h2>
          <p class="section-copy">默认空闲 2 小时自动退出，单次登录最长保留 7 天。</p>
          <el-form-item label="空闲超时退出">
            <el-switch v-model="settings.sessionIdleTimeoutEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="空闲超时时长">
            <el-input-number
              v-model="settings.sessionIdleTimeoutMinutes"
              :min="15"
              :max="1440"
              :step="15"
              controls-position="right"
              :disabled="!settings.sessionIdleTimeoutEnabled"
            />
            <span class="unit-label">分钟</span>
          </el-form-item>
          <el-form-item label="绝对会话时长">
            <el-switch v-model="settings.sessionAbsoluteLifetimeEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="最长登录时长">
            <el-input-number
              v-model="settings.sessionAbsoluteLifetimeDays"
              :min="1"
              :max="30"
              :step="1"
              controls-position="right"
              :disabled="!settings.sessionAbsoluteLifetimeEnabled"
            />
            <span class="unit-label">天</span>
          </el-form-item>
        </div>

        <div class="form-section">
          <h2>审计日志保留策略</h2>
          <p class="section-copy">历史日志先保存到 RustFS，确认成功后再从数据库批量清理。</p>
          <el-form-item label="自动归档">
            <el-switch
              v-model="settings.auditArchiveEnabled"
              active-text="启用"
              inactive-text="停用"
            />
          </el-form-item>
          <el-form-item label="数据库保留天数">
            <el-input-number
              v-model="settings.auditRetentionDays"
              :min="7"
              :max="3650"
              :step="30"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="执行计划">
            <div class="cron-field">
              <el-input v-model="settings.auditArchiveCron" placeholder="例如：0 30 2 * * *" />
              <div class="cron-help">
                <p>Spring cron 使用 6 段格式：秒 分 时 日 月 周。</p>
                <p>常用符号：`*` 表示任意值，`,` 表示多个值，`-` 表示范围，`/` 表示步长，`?` 表示不指定日或周。</p>
                <p>示例：`0 30 2 * * *` 每天 02:30；`0 0 3 * * MON` 每周一 03:00；`0 0 2 1 * *` 每月 1 日 02:00。</p>
              </div>
            </div>
          </el-form-item>
          <el-form-item label="归档位置">
            <el-select v-model="settings.auditArchiveStorage">
              <el-option label="RustFS 对象存储" value="RUSTFS" />
            </el-select>
          </el-form-item>
          <el-form-item label="每批清理条数">
            <el-input-number
              v-model="settings.auditArchiveBatchSize"
              :min="100"
              :max="10000"
              :step="100"
              controls-position="right"
            />
          </el-form-item>
        </div>
      </el-form>
    </div>
  </section>
</template>

<style scoped>
.form-section + .form-section {
  border-top: 1px solid #e5e7eb;
  margin-top: 26px;
  padding-top: 22px;
}

.form-section h2 {
  margin: 0 0 16px;
  font-size: 18px;
}

.section-copy {
  margin: -6px 0 18px;
  color: #64748b;
}

.el-select,
.el-input {
  max-width: 360px;
}

.unit-label {
  margin-left: 8px;
  color: #64748b;
}

.cron-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.cron-help {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.cron-help p {
  margin: 0;
}

.watermark-position-grid {
  display: grid;
  gap: 6px;
  grid-template-columns: repeat(3, 64px);
}

.watermark-position-grid :deep(.el-radio-button__inner) {
  border-left: var(--el-border);
  border-radius: 4px;
  padding: 8px 0;
  text-align: center;
  width: 64px;
}

.watermark-position-grid :deep(.el-radio-button:first-child .el-radio-button__inner),
.watermark-position-grid :deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 4px;
}
</style>
