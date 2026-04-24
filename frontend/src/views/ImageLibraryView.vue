<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { Download, Search, UploadFilled } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import {
  cancelUploadSession,
  confirmUploadSession,
  createUploadSession,
  deleteImage,
  downloadImage,
  getImages,
  imageBlobUrl,
  retryUploadSessionItem,
  updateImage,
  uploadSessionItem,
  type ImageRecord,
  type UploadBatch,
  type UploadBatchItem,
} from '../api/images'
import { getCategories, getTags, type Category, type Tag } from '../api/taxonomy'

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
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadConfirming = ref(false)
const uploadCancelling = ref(false)
const uploadMode = ref<'SINGLE' | 'BATCH'>('SINGLE')
const uploadFileList = ref<UploadUserFile[]>([])
const singleUploadPreviewUrl = ref('')
const uploadTags = ref<Tag[]>([])
const uploadSession = ref<UploadBatch | null>(null)
const uploadAbortController = ref<AbortController | null>(null)
const uploadItemFiles = reactive<Record<string, File>>({})
const uploadForm = reactive({ categoryId: '', tagIds: [] as string[] })

const categoryOptions = computed(() => categories.value.filter((category) => category.enabled))
const tagOptions = computed(() => tags.value.filter((tag) => tag.enabled))
const uploadTagOptions = computed(() => uploadTags.value.filter((tag) => tag.enabled))
const selectedSingleUploadFile = computed(() => uploadFileList.value[0] ?? null)
const uploadHasStagedItems = computed(() => Boolean(uploadSession.value?.items.some((item) => item.status === 'STAGED' || item.status === 'DUPLICATE')))
const uploadHasFailedItems = computed(() => Boolean(uploadSession.value?.items.some((item) => item.status === 'FAILED')))
const uploadCanConfirmSession = computed(() => Boolean(uploadSession.value && uploadHasStagedItems.value && !uploadHasFailedItems.value))
const uploadLocked = computed(() => Boolean(uploadSession.value) || uploadLoading.value || uploadConfirming.value || uploadCancelling.value)

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

function uploadFileSize(file: UploadUserFile) {
  return typeof file.size === 'number' ? formatBytes(file.size) : '未知大小'
}

function uploadFileKey(file: UploadUserFile) {
  const raw = file.raw as unknown as File | undefined
  if (raw) return `${raw.name}:${raw.size}:${raw.lastModified}`
  return `${file.name}:${file.size ?? 0}`
}

function isAcceptedUploadFile(file: UploadUserFile) {
  const raw = file.raw as unknown as File | undefined
  const mimeType = raw?.type || ''
  const name = file.name.toLowerCase()
  return ['image/jpeg', 'image/png', 'image/webp'].includes(mimeType) || /\.(jpe?g|png|webp)$/.test(name)
}

function normalizeUploadFiles(files: UploadUserFile[]) {
  const seen = new Set<string>()
  const normalized: UploadUserFile[] = []
  let skippedDuplicate = false
  let skippedUnsupported = false
  for (const file of files) {
    if (!isAcceptedUploadFile(file)) {
      skippedUnsupported = true
      continue
    }
    const key = uploadFileKey(file)
    if (seen.has(key)) {
      skippedDuplicate = true
      continue
    }
    seen.add(key)
    normalized.push(file)
  }
  if (skippedUnsupported) {
    ElMessage.warning('已忽略不支持的文件，仅支持 JPG、PNG、WebP')
  }
  if (skippedDuplicate) {
    ElMessage.warning('已忽略重复选择的图片')
  }
  return normalized.slice(0, uploadModeLimit())
}

function selectedUploadFiles() {
  return uploadFileList.value.flatMap((item) => (item.raw ? [item.raw as unknown as File] : []))
}

function revokeSingleUploadPreview() {
  if (singleUploadPreviewUrl.value) {
    URL.revokeObjectURL(singleUploadPreviewUrl.value)
    singleUploadPreviewUrl.value = ''
  }
}

function refreshSingleUploadPreview() {
  revokeSingleUploadPreview()
  const raw = uploadFileList.value[0]?.raw as unknown as File | undefined
  if (uploadMode.value === 'SINGLE' && raw) {
    singleUploadPreviewUrl.value = URL.createObjectURL(raw)
  }
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

async function loadUploadTags() {
  uploadForm.tagIds = []
  uploadTags.value = uploadForm.categoryId ? await getTags(uploadForm.categoryId) : []
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

async function openUploadDialog() {
  if (categories.value.length === 0) {
    await loadCategories()
  }
  const textile = categories.value.find((category) => category.code === 'TEXTILE_DEFECT' && category.enabled)
  const fallback = categoryOptions.value[0]
  uploadMode.value = 'SINGLE'
  uploadForm.categoryId = textile?.id ?? fallback?.id ?? ''
  uploadForm.tagIds = []
  uploadFileList.value = []
  revokeSingleUploadPreview()
  uploadSession.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
  uploadTags.value = uploadForm.categoryId ? await getTags(uploadForm.categoryId) : []
  uploadVisible.value = true
}

function handleUploadModeChange() {
  uploadFileList.value = []
  revokeSingleUploadPreview()
  uploadSession.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
}

function handleUploadExceed() {
  ElMessage.warning(uploadMode.value === 'SINGLE' ? '单次只能选择 1 张图片' : '已超过本次上传数量限制')
}

function handleUploadFileChange(_file: UploadFile, files: UploadFiles) {
  const normalized = normalizeUploadFiles(files as UploadUserFile[])
  if (uploadMode.value === 'SINGLE') {
    uploadFileList.value = normalized.slice(0, 1)
    refreshSingleUploadPreview()
    return
  }
  uploadFileList.value = normalized
}

function removeSelectedUploadFile(uid?: number) {
  if (uploadLocked.value) return
  uploadFileList.value = uid === undefined ? [] : uploadFileList.value.filter((file) => file.uid !== uid)
  if (uploadMode.value === 'SINGLE') {
    refreshSingleUploadPreview()
  }
}

function clearSelectedUploadFiles() {
  if (uploadLocked.value) return
  uploadFileList.value = []
  revokeSingleUploadPreview()
}

function validateUploadSelection() {
  const files = selectedUploadFiles()
  if (files.length === 0) {
    ElMessage.warning('请先选择图片文件')
    return null
  }
  if (uploadMode.value === 'SINGLE' && files.length !== 1) {
    ElMessage.warning('单张上传只能选择 1 张图片')
    return null
  }
  if (!uploadForm.categoryId) {
    ElMessage.warning('请先选择图片分类')
    return null
  }
  if (uploadForm.tagIds.length === 0) {
    ElMessage.warning('请至少选择一个标签')
    return null
  }
  return files
}

async function stageSelectedUploadFiles(files: File[]) {
  uploadLoading.value = true
  uploadAbortController.value = new AbortController()
  try {
    let session = await createUploadSession({
      mode: uploadMode.value,
      categoryId: uploadForm.categoryId,
      tagIds: uploadForm.tagIds,
      totalCount: files.length,
    })
    uploadSession.value = session
    for (const file of files) {
      session = await uploadSessionItem(session.id, file, uploadAbortController.value.signal)
      const latestItem = session.items.at(-1)
      if (latestItem) {
        uploadItemFiles[latestItem.id] = file
      }
      uploadSession.value = session
    }
    uploadSession.value = session
    return session
  } catch (error) {
    if (!uploadCancelling.value) {
      ElMessage.error(errorMessage(error, '图片上传失败'))
    }
  } finally {
    uploadLoading.value = false
    uploadAbortController.value = null
  }
  return null
}

async function retryUploadItem(item: UploadBatchItem) {
  const file = uploadItemFiles[item.id]
  if (!uploadSession.value || !file) {
    ElMessage.warning('找不到该失败文件，请重新打开上传弹窗选择文件')
    return
  }
  uploadLoading.value = true
  uploadAbortController.value = new AbortController()
  try {
    uploadSession.value = await retryUploadSessionItem(uploadSession.value.id, item.id, file, uploadAbortController.value.signal)
    ElMessage.success('文件已重新上传')
  } catch (error) {
    if (!uploadCancelling.value) {
      ElMessage.error(errorMessage(error, '文件重试失败'))
    }
  } finally {
    uploadLoading.value = false
    uploadAbortController.value = null
  }
}

async function confirmUploadDialog() {
  if (!uploadSession.value) {
    const files = validateUploadSelection()
    if (!files) return
    uploadConfirming.value = true
    const session = await stageSelectedUploadFiles(files)
    if (!session) {
      uploadConfirming.value = false
      return
    }
    if (session.failedCount > 0) {
      ElMessage.error('部分图片上传失败，请重试失败项或取消本次上传')
      uploadConfirming.value = false
      return
    }
  }
  if (!uploadSession.value) {
    ElMessage.warning('请先选择并上传图片')
    return
  }
  if (uploadHasFailedItems.value) {
    ElMessage.warning('存在失败文件，请先重试或取消本次上传')
    return
  }
  if (!uploadCanConfirmSession.value) {
    ElMessage.warning('没有可入库的文件')
    return
  }
  uploadConfirming.value = true
  try {
    const session = await confirmUploadSession(uploadSession.value.id)
    ElMessage.success(session.duplicateCount > 0 ? '图片已上传入库，重复图片已关联既有记录' : '图片已上传入库')
    uploadVisible.value = false
    resetUploadDialog()
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '上传确认失败'))
  } finally {
    uploadConfirming.value = false
  }
}

async function cancelUploadDialog(showMessage = true) {
  const cancellableSession = uploadSession.value && !['CONFIRMED', 'CANCELLED', 'EXPIRED'].includes(uploadSession.value.status)
  if (cancellableSession) {
    uploadCancelling.value = true
  }
  uploadAbortController.value?.abort()
  if (cancellableSession && uploadSession.value) {
    try {
      uploadSession.value = await cancelUploadSession(uploadSession.value.id)
      if (showMessage) {
        ElMessage.success('已清理本次未确认上传文件')
      }
    } catch (error) {
      ElMessage.error(errorMessage(error, '上传会话取消失败'))
      uploadCancelling.value = false
      return false
    }
    uploadCancelling.value = false
  }
  uploadVisible.value = false
  resetUploadDialog()
  return true
}

function handleUploadBeforeClose(done: () => void) {
  void cancelUploadDialog(false).then((closed) => {
    if (closed) {
      done()
    }
  })
}

function resetUploadDialog() {
  uploadFileList.value = []
  revokeSingleUploadPreview()
  uploadSession.value = null
  uploadLoading.value = false
  uploadConfirming.value = false
  uploadCancelling.value = false
  uploadAbortController.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
}

function uploadStatusType(status: string) {
  if (status === 'STAGED' || status === 'CONFIRMED') return 'success'
  if (status === 'DUPLICATE') return 'warning'
  if (status === 'FAILED' || status === 'CANCELLED') return 'danger'
  return 'info'
}

function uploadStatusText(status: string) {
  const labels: Record<string, string> = {
    PROCESSING: '处理中',
    STAGED: '待确认',
    DUPLICATE: '重复',
    FAILED: '失败',
    CONFIRMED: '已入库',
    CANCELLED: '已取消',
  }
  return labels[status] ?? status
}

function uploadModeLimit() {
  return uploadMode.value === 'SINGLE' ? 1 : 100
}

function uploadDialogTip() {
  return uploadMode.value === 'SINGLE'
    ? '拖入 1 张 JPG / PNG / WebP 图片或点击选择'
    : '拖入多张 JPG / PNG / WebP 图片或点击选择'
}

function uploadItemMeta(item: UploadBatchItem) {
  const file = uploadItemFiles[item.id]
  if (file) return `${file.type || '图片文件'} · ${formatBytes(file.size)}`
  if (item.status === 'DUPLICATE') return '系统中已有相同图片'
  return '等待处理结果'
}

function uploadItemDescription(item: UploadBatchItem) {
  if (item.errorMessage) return item.errorMessage
  if (item.status === 'STAGED') return '已处理，等待入库确认'
  if (item.status === 'DUPLICATE') return '重复图片不会重复写入存储'
  if (item.status === 'CONFIRMED') return '已入库'
  if (item.status === 'PROCESSING') return '正在处理图片'
  return ''
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

onBeforeUnmount(() => {
  revokeSingleUploadPreview()
})
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h1>图片库</h1>
        <p>按关键词、分类、标签、上传者和时间范围检索图片。</p>
      </div>
      <el-button type="primary" :icon="UploadFilled" @click="openUploadDialog">上传图片</el-button>
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

    <el-dialog v-model="uploadVisible" title="上传图片" width="720px" :before-close="handleUploadBeforeClose">
      <el-form label-width="88px">
        <el-form-item label="上传模式">
          <el-radio-group v-model="uploadMode" :disabled="uploadLocked" @change="handleUploadModeChange">
            <el-radio-button label="SINGLE">单张</el-radio-button>
            <el-radio-button label="BATCH">批量</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="图片文件" required>
          <div v-if="uploadMode === 'SINGLE'" class="single-upload-zone">
            <el-upload
              v-if="!selectedSingleUploadFile"
              v-model:file-list="uploadFileList"
              class="single-upload-drop"
              drag
              :show-file-list="false"
              :limit="1"
              :multiple="false"
              accept="image/jpeg,image/png,image/webp"
              action="#"
              :auto-upload="false"
              :disabled="uploadLocked"
              :on-change="handleUploadFileChange"
              :on-exceed="handleUploadExceed"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">{{ uploadDialogTip() }}</div>
            </el-upload>
            <div v-else class="single-preview-card">
              <img v-if="singleUploadPreviewUrl" :src="singleUploadPreviewUrl" alt="" />
              <div v-else class="single-preview-fallback">预览</div>
              <div class="single-preview-info">
                <strong>{{ selectedSingleUploadFile.name }}</strong>
                <span>{{ uploadFileSize(selectedSingleUploadFile) }}</span>
                <div class="single-preview-actions">
                  <el-button link type="primary" :disabled="uploadLocked" @click="removeSelectedUploadFile(selectedSingleUploadFile.uid)">重新选择</el-button>
                  <el-button link type="danger" :disabled="uploadLocked" @click="removeSelectedUploadFile(selectedSingleUploadFile.uid)">移除</el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="batch-upload-zone">
            <div class="batch-upload-actions">
              <el-upload
                v-model:file-list="uploadFileList"
                class="batch-upload-picker"
                :show-file-list="false"
                :limit="uploadModeLimit()"
                multiple
                accept="image/jpeg,image/png,image/webp"
                action="#"
                :auto-upload="false"
                :disabled="uploadLocked"
                :on-change="handleUploadFileChange"
                :on-exceed="handleUploadExceed"
              >
                <el-button type="primary" plain :icon="UploadFilled" :disabled="uploadLocked">选择图片</el-button>
              </el-upload>
              <span>已选择 {{ uploadFileList.length }} / {{ uploadModeLimit() }} 张，可重复点击追加</span>
              <el-button v-if="uploadFileList.length && !uploadSession" link type="danger" :disabled="uploadLocked" @click="clearSelectedUploadFiles">
                清空
              </el-button>
            </div>
            <div v-if="!uploadSession && uploadFileList.length" class="batch-file-list">
              <div v-for="file in uploadFileList" :key="file.uid" class="batch-file-row">
                <div class="batch-file-main">
                  <strong>{{ file.name }}</strong>
                  <span>{{ uploadFileSize(file) }}</span>
                </div>
                <el-button link type="danger" :disabled="uploadLocked" @click="removeSelectedUploadFile(file.uid)">移除</el-button>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="uploadForm.categoryId" placeholder="选择分类" :disabled="uploadLocked" @change="loadUploadTags">
            <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签" required>
          <el-select
            v-model="uploadForm.tagIds"
            class="full-tag-select"
            multiple
            filterable
            clearable
            placeholder="搜索并选择至少一个标签"
            :disabled="!uploadForm.categoryId || uploadLocked"
          >
            <el-option v-for="tag in uploadTagOptions" :key="tag.id" :label="tag.name" :value="tag.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="uploadSession"
        class="upload-session-tip"
        type="info"
        :closable="false"
        show-icon
        title="上传失败或中断时，取消会清理本次未完成文件。"
      />
      <div v-if="uploadSession" class="upload-result-list">
        <div v-for="item in uploadSession.items" :key="item.id" class="upload-result-row">
          <div class="upload-result-main">
            <div class="upload-result-title">
              <strong>{{ item.originalFilename }}</strong>
              <el-tag :type="uploadStatusType(item.status)" effect="light">{{ uploadStatusText(item.status) }}</el-tag>
            </div>
            <span>{{ uploadItemMeta(item) }}</span>
            <p v-if="uploadItemDescription(item)">{{ uploadItemDescription(item) }}</p>
          </div>
          <el-button v-if="item.status === 'FAILED'" link type="primary" :loading="uploadLoading" @click="retryUploadItem(item)">重试</el-button>
        </div>
      </div>
      <template #footer>
        <el-button :loading="uploadCancelling" @click="cancelUploadDialog()">取消</el-button>
        <el-button type="primary" :loading="uploadConfirming || uploadLoading" :disabled="uploadCancelling || Boolean(uploadSession && !uploadCanConfirmSession)" @click="confirmUploadDialog">
          确认
        </el-button>
      </template>
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
          <el-select v-model="editForm.tagIds" class="full-tag-select" multiple filterable clearable placeholder="搜索并选择标签">
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

.upload-session-tip {
  margin-bottom: 12px;
}

.full-tag-select {
  width: 100%;
}

.full-tag-select :deep(.el-select__wrapper) {
  align-items: center;
  height: auto;
  min-height: 34px;
  padding-bottom: 4px;
  padding-top: 4px;
}

.full-tag-select :deep(.el-select__selection) {
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.full-tag-select :deep(.el-tag) {
  margin: 2px 0;
  max-width: 100%;
}

.full-tag-select :deep(.el-select__tags-text) {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.single-upload-zone,
.batch-upload-zone {
  width: 100%;
}

.single-upload-drop {
  width: 100%;
}

.single-upload-drop :deep(.el-upload),
.single-upload-drop :deep(.el-upload-dragger) {
  width: 100%;
}

.single-preview-card {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
  display: flex;
  gap: 14px;
  min-height: 128px;
  padding: 12px;
  width: 100%;
}

.single-preview-card img,
.single-preview-fallback {
  background: #e2e8f0;
  border-radius: 6px;
  flex: 0 0 auto;
  height: 104px;
  object-fit: cover;
  width: 142px;
}

.single-preview-fallback {
  align-items: center;
  color: #64748b;
  display: flex;
  justify-content: center;
}

.single-preview-info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.single-preview-info strong,
.batch-file-main strong,
.upload-result-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.single-preview-info span,
.batch-upload-actions span,
.batch-file-main span,
.upload-result-main span,
.upload-result-main p {
  color: #64748b;
  font-size: 13px;
}

.single-preview-actions {
  display: flex;
  gap: 10px;
  margin-top: 4px;
}

.batch-upload-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.batch-file-list,
.upload-result-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.batch-file-row,
.upload-result-row {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: flex;
  gap: 12px;
  justify-content: space-between;
  padding: 10px 12px;
}

.batch-file-main,
.upload-result-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.upload-result-title {
  align-items: center;
  display: flex;
  gap: 8px;
  min-width: 0;
}

.upload-result-main p {
  margin: 0;
}
</style>
