<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadUserFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { retryUploadItem, uploadImages, type UploadBatch } from '../api/images'
import { getCategories, getTags, type Category, type Tag } from '../api/taxonomy'

const router = useRouter()
const fileList = ref<UploadUserFile[]>([])
const uploading = ref(false)
const batch = ref<UploadBatch | null>(null)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const categoryId = ref('')
const tagIds = ref<string[]>([])

const enabledCategories = computed(() => categories.value.filter((category) => category.enabled))
const enabledTags = computed(() => tags.value.filter((tag) => tag.enabled))

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

async function loadTags() {
  tagIds.value = []
  tags.value = categoryId.value ? await getTags(categoryId.value) : []
}

async function submitUpload() {
  const files = fileList.value.flatMap((item) => (item.raw ? [item.raw as unknown as File] : []))
  if (files.length === 0) {
    ElMessage.warning('请先选择图片文件')
    return
  }
  uploading.value = true
  try {
    batch.value = await uploadImages(files, categoryId.value || undefined, tagIds.value)
    ElMessage.success('上传批次已处理完成')
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片上传失败'))
  } finally {
    uploading.value = false
  }
}

async function retry(itemId: string) {
  if (!batch.value) return
  try {
    batch.value = await retryUploadItem(batch.value.id, itemId)
    ElMessage.info('已记录重试请求')
  } catch (error) {
    ElMessage.error(errorMessage(error, '重试失败'))
  }
}

onMounted(async () => {
  categories.value = await getCategories()
  const textile = categories.value.find((category) => category.code === 'TEXTILE_DEFECT')
  categoryId.value = textile?.id ?? categories.value[0]?.id ?? ''
  await loadTags()
})
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>批量上传</h1>
        <p>原图保持原质量，系统为列表和预览生成独立派生图。</p>
      </div>
      <el-button type="primary" :loading="uploading" @click="submitUpload">开始上传</el-button>
    </div>

    <div class="surface surface-pad">
      <div class="upload-options">
        <el-select v-model="categoryId" placeholder="选择分类" @change="loadTags">
          <el-option v-for="category in enabledCategories" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-select v-model="tagIds" multiple placeholder="选择标签">
          <el-option v-for="tag in enabledTags" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
      </div>

      <el-upload
        v-model:file-list="fileList"
        drag
        multiple
        accept="image/jpeg,image/png,image/webp"
        action="#"
        :auto-upload="false"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖入 JPG / PNG / WebP 文件或点击选择</div>
      </el-upload>

      <div v-if="batch" class="batch-result">
        <div class="batch-summary">
          <el-progress :percentage="batch.progressPercent" />
          <div>
            成功 {{ batch.successCount }}，重复 {{ batch.duplicateCount }}，失败 {{ batch.failedCount }}
          </div>
        </div>
        <el-table :data="batch.items" stripe>
          <el-table-column prop="originalFilename" label="文件" min-width="220" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="progressPercent" label="进度" width="120" />
          <el-table-column prop="errorMessage" label="说明" min-width="260" show-overflow-tooltip />
          <el-table-column label="操作" width="170">
            <template #default="{ row }">
              <el-button v-if="row.status === 'FAILED'" link type="primary" @click="retry(row.id)">重试</el-button>
              <el-button v-if="row.imageId" link type="primary" @click="router.push('/images')">查看图片库</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>

<style scoped>
.upload-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.upload-options .el-select {
  width: 260px;
}

.batch-result {
  margin-top: 20px;
}

.batch-summary {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}
</style>
