<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isAxiosError } from 'axios'
import * as echarts from 'echarts'
import type { ECharts, EChartsOption } from 'echarts'
import { getStatistics, type Statistics, type StatisticsRankingItem } from '../api/images'

const loading = ref(false)
const trendChartEl = ref<HTMLElement | null>(null)
const categoryChartEl = ref<HTMLElement | null>(null)
let trendChart: ECharts | null = null
let categoryChart: ECharts | null = null

const stats = ref<Statistics>({
  imageTotal: 0,
  todayUploaded: 0,
  viewCount: 0,
  downloadCount: 0,
  storageBytes: 0,
  uploadTrend: [],
  categoryDistribution: [],
  topViewedImages: [],
  topDownloadedImages: [],
})

const hasUploadTrendActivity = computed(() => stats.value.uploadTrend.some((item) => item.count > 0))
const hasCategoryDistribution = computed(() => stats.value.categoryDistribution.length > 0)
const maxViewedCount = computed(() => Math.max(1, ...stats.value.topViewedImages.map((item) => item.viewCount)))
const maxDownloadedCount = computed(() => Math.max(1, ...stats.value.topDownloadedImages.map((item) => item.downloadCount)))
const engagementTotal = computed(() => stats.value.viewCount + stats.value.downloadCount)
const viewShare = computed(() => percentage(stats.value.viewCount, engagementTotal.value))
const downloadShare = computed(() => percentage(stats.value.downloadCount, engagementTotal.value))

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function formatBytes(value: number) {
  if (value <= 0) return '0 KB'
  if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KB`
  if (value < 1024 * 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${(value / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function shortDate(value: string) {
  return value.slice(5)
}

function percentage(value: number, total: number) {
  if (total <= 0) return 0
  return Math.round((value / total) * 100)
}

function rankingWidth(item: StatisticsRankingItem, mode: 'view' | 'download') {
  const max = mode === 'view' ? maxViewedCount.value : maxDownloadedCount.value
  const value = mode === 'view' ? item.viewCount : item.downloadCount
  return `${Math.max(4, Math.round((value / max) * 100))}%`
}

async function load() {
  loading.value = true
  try {
    stats.value = await getStatistics()
    await nextTick()
    renderCharts()
  } catch (error) {
    ElMessage.error(errorMessage(error, '统计数据加载失败'))
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTrendChart()
  renderCategoryChart()
}

function renderTrendChart() {
  if (!trendChartEl.value) return
  trendChart ??= echarts.init(trendChartEl.value)
  const option: EChartsOption = {
    color: ['#0f766e', '#7c3aed'],
    grid: { top: 28, right: 18, bottom: 32, left: 42 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: stats.value.uploadTrend.map((item) => shortDate(item.date)),
      axisLine: { lineStyle: { color: '#d8e2dd' } },
      axisTick: { show: false },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf2f0' } },
      axisLabel: { color: '#64748b' },
    },
    series: [
      {
        name: '上传量',
        type: 'bar',
        data: stats.value.uploadTrend.map((item) => item.count),
        barMaxWidth: 18,
        itemStyle: { borderRadius: [4, 4, 0, 0] },
      },
      {
        name: '趋势',
        type: 'line',
        data: stats.value.uploadTrend.map((item) => item.count),
        smooth: true,
        symbolSize: 5,
      },
    ],
  }
  trendChart.setOption(option, true)
}

function renderCategoryChart() {
  if (!categoryChartEl.value) return
  categoryChart ??= echarts.init(categoryChartEl.value)
  const option: EChartsOption = {
    color: ['#0f766e', '#2563eb', '#7c3aed', '#ca8a04', '#dc2626', '#64748b'],
    tooltip: { trigger: 'item' },
    legend: {
      bottom: 0,
      type: 'scroll',
      icon: 'circle',
      textStyle: { color: '#64748b' },
    },
    series: [
      {
        name: '图片数量',
        type: 'pie',
        radius: ['48%', '72%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}', color: '#334155' },
        data: stats.value.categoryDistribution.map((item) => ({
          name: item.name,
          value: item.count,
        })),
      },
    ],
  }
  categoryChart.setOption(option, true)
}

function resizeCharts() {
  trendChart?.resize()
  categoryChart?.resize()
}

onMounted(() => {
  void load()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  categoryChart?.dispose()
})
</script>

<template>
  <section class="workspace-page">
    <div v-loading="loading" class="stats-dashboard">
      <div class="stats-dashboard-toolbar">
        <el-button @click="load">刷新</el-button>
      </div>

      <div class="stats-dashboard-scroll workspace-scroll-region">
        <div class="metric-grid">
          <div class="metric stat-metric">
            <span>图片总量</span>
            <strong>{{ formatNumber(stats.imageTotal) }}</strong>
            <small>不含已停用图片</small>
          </div>
          <div class="metric stat-metric">
            <span>今日上传</span>
            <strong>{{ formatNumber(stats.todayUploaded) }}</strong>
            <small>自然日新增入库</small>
          </div>
          <div class="metric stat-metric">
            <span>浏览次数</span>
            <strong>{{ formatNumber(stats.viewCount) }}</strong>
            <div class="metric-bar"><i :style="{ width: `${viewShare}%` }" /></div>
          </div>
          <div class="metric stat-metric">
            <span>下载次数</span>
            <strong>{{ formatNumber(stats.downloadCount) }}</strong>
            <div class="metric-bar metric-bar-alt"><i :style="{ width: `${downloadShare}%` }" /></div>
          </div>
        </div>

        <div class="chart-grid">
          <section class="surface surface-pad chart-panel">
            <div class="panel-head">
              <div>
                <h2>近 30 天上传趋势</h2>
                <p>按图片入库日期统计，每天补齐 0 值。</p>
              </div>
            </div>
            <div ref="trendChartEl" class="chart-canvas" />
            <div v-if="!hasUploadTrendActivity" class="chart-empty">近 30 天暂无上传记录</div>
          </section>

          <section class="surface surface-pad chart-panel">
            <div class="panel-head">
              <div>
                <h2>分类分布</h2>
                <p>按图片关联分类统计，未分类单独归集。</p>
              </div>
            </div>
            <div ref="categoryChartEl" class="chart-canvas" />
            <div v-if="!hasCategoryDistribution" class="chart-empty">暂无分类统计数据</div>
          </section>
        </div>

        <div class="insight-grid">
          <section class="surface surface-pad storage-panel">
            <div>
              <span>当前图片原图存储占用</span>
              <strong>{{ formatBytes(stats.storageBytes) }}</strong>
            </div>
            <div class="storage-track">
              <i />
            </div>
          </section>

          <section class="surface surface-pad ranking-panel">
            <div class="panel-head">
              <div>
                <h2>访问 Top 5</h2>
                <p>按累计浏览次数排序。</p>
              </div>
            </div>
            <div v-if="stats.topViewedImages.length" class="ranking-list">
              <div v-for="item in stats.topViewedImages" :key="item.id" class="ranking-row">
                <div class="ranking-main">
                  <span>{{ item.title }}</span>
                  <strong>{{ formatNumber(item.viewCount) }}</strong>
                </div>
                <div class="ranking-track"><i :style="{ width: rankingWidth(item, 'view') }" /></div>
              </div>
            </div>
            <div v-else class="list-empty">暂无访问数据</div>
          </section>

          <section class="surface surface-pad ranking-panel">
            <div class="panel-head">
              <div>
                <h2>下载 Top 5</h2>
                <p>按累计下载次数排序。</p>
              </div>
            </div>
            <div v-if="stats.topDownloadedImages.length" class="ranking-list">
              <div v-for="item in stats.topDownloadedImages" :key="item.id" class="ranking-row">
                <div class="ranking-main">
                  <span>{{ item.title }}</span>
                  <strong>{{ formatNumber(item.downloadCount) }}</strong>
                </div>
                <div class="ranking-track ranking-track-alt"><i :style="{ width: rankingWidth(item, 'download') }" /></div>
              </div>
            </div>
            <div v-else class="list-empty">暂无下载数据</div>
          </section>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.stats-dashboard {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.stats-dashboard-toolbar {
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.stats-dashboard-scroll {
  padding-right: 4px;
}

.stat-metric {
  overflow: hidden;
}

.stat-metric small {
  color: #94a3b8;
  display: block;
  margin-top: 10px;
}

.metric-bar,
.ranking-track,
.storage-track {
  background: #e8efec;
  border-radius: 999px;
  height: 8px;
  margin-top: 14px;
  overflow: hidden;
}

.metric-bar i,
.ranking-track i,
.storage-track i {
  background: #0f766e;
  border-radius: inherit;
  display: block;
  height: 100%;
  min-width: 4px;
}

.metric-bar-alt i,
.ranking-track-alt i {
  background: #7c3aed;
}

.chart-grid,
.insight-grid {
  display: grid;
  gap: 16px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 16px;
}

.insight-grid {
  grid-template-columns: minmax(260px, 0.8fr) repeat(2, minmax(0, 1fr));
}

.chart-panel {
  min-height: 360px;
  min-width: 0;
  position: relative;
}

.panel-head {
  align-items: flex-start;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-head h2 {
  font-size: 16px;
  margin: 0;
}

.panel-head p {
  color: #64748b;
  font-size: 13px;
  margin: 6px 0 0;
}

.chart-canvas {
  height: 280px;
  min-width: 0;
  width: 100%;
}

.chart-empty,
.list-empty {
  align-items: center;
  color: #94a3b8;
  display: flex;
  justify-content: center;
  min-height: 120px;
}

.chart-empty {
  inset: 96px 16px 22px;
  pointer-events: none;
  position: absolute;
}

.storage-panel {
  align-items: stretch;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 190px;
  min-width: 0;
}

.storage-panel span {
  color: #64748b;
  display: block;
  font-size: 13px;
  margin-bottom: 12px;
}

.storage-panel strong {
  color: #0d433c;
  display: block;
  font-size: 30px;
  line-height: 1;
}

.storage-track {
  height: 10px;
}

.storage-track i {
  background: linear-gradient(90deg, #0f766e, #2563eb);
  width: 68%;
}

.ranking-panel {
  min-height: 190px;
  min-width: 0;
}

.ranking-list {
  display: grid;
  gap: 12px;
}

.ranking-main {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
}

.ranking-main span {
  color: #334155;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-main strong {
  color: #0d433c;
  flex: 0 0 auto;
}

.ranking-track {
  height: 7px;
  margin-top: 8px;
}

@media (max-width: 1080px) {
  .chart-grid,
  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
