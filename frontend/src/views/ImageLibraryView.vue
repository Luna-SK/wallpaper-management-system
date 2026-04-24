<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Search, UploadFilled } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { deleteImage, downloadImage, getImages, imageBlobUrl, updateImage, type ImageRecord } from '../api/images'
import { getCategories, getTags, type Category, type Tag } from '../api/taxonomy'

const router = useRouter()
const loading = ref(false)
const keyword = ref('')
const selectedCategoryId = ref('')
const selectedTagId = ref('')
const rows = ref<ImageRecord[]>([])
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const thumbnailUrls = reactive<Record<string, string>>({})
const previewVisible = ref(false)
const previewUrl = ref('')
const editing = ref<ImageRecord | null>(null)
const editVisible = ref(false)
const editForm = reactive({ title: '', status: 'ACTIVE', categoryIds: [] as string[], tagIds: [] as string[] })

const categoryOptions = computed(() => categories.value.filter((category) => category.enabled))
const tagOptions = computed(() => tags.value.filter((tag) => tag.enabled))

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function formatBytes(value: number) {
  if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

async function loadCategories() {
  categories.value = await getCategories()
  if (selectedCategoryId.value) {
    tags.value = await getTags(selectedCategoryId.value)
  }
}

async function loadTagsForFilter() {
  selectedTagId.value = ''
  tags.value = selectedCategoryId.value ? await getTags(selectedCategoryId.value) : []
}

async function loadImages() {
  loading.value = true
  try {
    rows.value = await getImages({
      keyword: keyword.value || undefined,
      categoryId: selectedCategoryId.value || undefined,
      tagId: selectedTagId.value || undefined,
    })
    await Promise.all(rows.value.map(async (row) => {
      if (!thumbnailUrls[row.id]) {
        thumbnailUrls[row.id] = await imageBlobUrl(row.id, 'thumbnail')
      }
    }))
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片列表加载失败'))
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  selectedCategoryId.value = ''
  selectedTagId.value = ''
  tags.value = []
  void loadImages()
}

async function preview(row: ImageRecord) {
  try {
    previewUrl.value = await imageBlobUrl(row.id, 'preview')
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片预览加载失败'))
  }
}

async function openEdit(row: ImageRecord) {
  editing.value = row
  editVisible.value = true
  editForm.title = row.title
  editForm.status = row.status
  editForm.categoryIds = row.categories.map((category) => category.id)
  editForm.tagIds = row.tags.map((tag) => tag.id)
  tags.value = editForm.categoryIds[0] ? await getTags(editForm.categoryIds[0]) : []
}

async function onEditCategoryChange(ids: string[]) {
  editForm.tagIds = []
  tags.value = ids[0] ? await getTags(ids[0]) : []
}

async function saveEdit() {
  if (!editing.value) return
  try {
    await updateImage(editing.value.id, {
      title: editForm.title,
      status: editForm.status,
      categoryIds: editForm.categoryIds,
      tagIds: editForm.tagIds,
    })
    ElMessage.success('图片信息已保存')
    editing.value = null
    editVisible.value = false
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片信息保存失败'))
  }
}

async function remove(row: ImageRecord) {
  await ElMessageBox.confirm(`确定停用图片“${row.title}”？`, '停用图片', { type: 'warning' })
  try {
    await deleteImage(row.id)
    ElMessage.success('图片已停用')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片停用失败'))
  }
}

async function download(row: ImageRecord) {
  try {
    await downloadImage(row.id, row.originalFilename)
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片下载失败'))
  }
}

onMounted(async () => {
  await loadCategories()
  await loadImages()
})
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>图片库</h1>
        <p>按关键词、分类、标签、上传者和时间范围检索图片。</p>
      </div>
      <el-button type="primary" :icon="UploadFilled" @click="router.push('/upload')">上传图片</el-button>
    </div>

    <div class="surface surface-pad">
      <div class="toolbar-row image-toolbar">
        <el-input v-model="keyword" placeholder="文件名、标签、备注" :prefix-icon="Search" clearable />
        <el-select v-model="selectedCategoryId" placeholder="分类" clearable @change="loadTagsForFilter">
          <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
        </el-select>
        <el-select v-model="selectedTagId" placeholder="标签" clearable :disabled="!selectedCategoryId">
          <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
        </el-select>
        <div>
          <el-button @click="loadImages">筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe>
        <el-table-column label="图片" min-width="260">
          <template #default="{ row }">
            <div class="image-cell">
              <img v-if="thumbnailUrls[row.id]" :src="thumbnailUrls[row.id]" alt="" />
              <div class="image-meta">
                <strong>{{ row.title }}</strong>
                <span>{{ row.originalFilename }} · {{ formatBytes(row.sizeBytes) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="150">
          <template #default="{ row }">{{ row.categories.map((item: any) => item.name).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag.id" class="tag-chip" effect="light">{{ tag.name }}</el-tag>
            <span v-if="row.tags.length === 0">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="preview(row)">预览</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" :icon="Download" @click="download(row)">下载</el-button>
            <el-button link type="danger" @click="remove(row)">停用</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="previewVisible" title="图片预览" width="72%">
      <img class="preview-image" :src="previewUrl" alt="图片预览" />
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑图片信息" width="520px">
      <el-form label-width="88px">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.categoryIds" multiple @change="onEditCategoryChange">
            <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="editForm.tagIds" multiple>
            <el-option v-for="tag in tagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.image-toolbar {
  align-items: flex-start;
}

.image-toolbar .el-input,
.image-toolbar .el-select {
  width: 220px;
}

.image-cell {
  align-items: center;
  display: flex;
  gap: 12px;
}

.image-cell img {
  background: #f1f5f9;
  border-radius: 6px;
  height: 58px;
  object-fit: cover;
  width: 76px;
}

.image-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.image-meta span {
  color: #64748b;
  font-size: 13px;
}

.tag-chip {
  margin: 0 6px 6px 0;
}

.preview-image {
  display: block;
  margin: 0 auto;
  max-height: 72vh;
  max-width: 100%;
  object-fit: contain;
}
</style>
