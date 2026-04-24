<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isAxiosError } from 'axios'
import { getStatistics, type Statistics } from '../api/images'

const loading = ref(false)
const stats = ref<Statistics>({
  imageTotal: 0,
  todayUploaded: 0,
  viewCount: 0,
  downloadCount: 0,
  storageBytes: 0,
})

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function formatBytes(value: number) {
  if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

async function load() {
  loading.value = true
  try {
    stats.value = await getStatistics()
  } catch (error) {
    ElMessage.error(errorMessage(error, '统计数据加载失败'))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>数据统计</h1>
        <p>图片数量、访问量、下载量和存储占用。</p>
      </div>
      <el-button @click="load">刷新</el-button>
    </div>

    <div v-loading="loading" class="metric-grid">
      <div class="metric"><span>图片总量</span><strong>{{ stats.imageTotal }}</strong></div>
      <div class="metric"><span>今日上传</span><strong>{{ stats.todayUploaded }}</strong></div>
      <div class="metric"><span>浏览次数</span><strong>{{ stats.viewCount }}</strong></div>
      <div class="metric"><span>下载次数</span><strong>{{ stats.downloadCount }}</strong></div>
    </div>

    <div class="surface surface-pad">
      <div class="storage-line">
        <span>当前图片原图存储占用</span>
        <strong>{{ formatBytes(stats.storageBytes) }}</strong>
      </div>
    </div>
  </section>
</template>

<style scoped>
.storage-line {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  min-height: 72px;
}

.storage-line span {
  color: #64748b;
}

.storage-line strong {
  font-size: 24px;
}
</style>
