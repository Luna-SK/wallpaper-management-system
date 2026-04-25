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
  auditArchiveEnabled: true,
  auditRetentionDays: 180,
  auditArchiveCron: '0 30 2 * * *',
  auditArchiveStorage: 'RUSTFS' as AuditArchiveStorage,
  auditArchiveBatchSize: 1000,
})

const loading = ref(false)
const saving = ref(false)
const uploadLimitsLoaded = computed(() => settings.maxFileHardLimitMb > 0 && settings.maxBatchHardLimitMb > 0)

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
</style>
