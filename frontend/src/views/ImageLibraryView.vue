<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance, UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import { ArrowLeft, ArrowRight, Delete, Download, RefreshLeft, Search, UploadFilled } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import {
  batchDisableImages,
  batchPurgeImages,
  batchRestoreImages,
  cancelUploadSession,
  confirmUploadSession,
  createUploadSession,
  deleteImage,
  downloadImage,
  downloadImagesZip,
  getImages,
  imageBlobUrl,
  purgeImage,
  purgeDeletedImages,
  retryUploadSessionItem,
  restoreImage,
  updateImage,
  uploadSessionItem,
  type ImageRecord,
  type UploadBatch,
  type UploadBatchItem,
} from '../api/images'
import { getCategories, getTags, type Category, type Tag } from '../api/taxonomy'
import { useAuthStore } from '../stores/auth'
import { useDialogEnterSubmit } from '../utils/dialogEnterSubmit'

const auth = useAuthStore()
const loading = ref(false)
const keyword = ref('')
const selectedCategoryId = ref('')
const selectedTagId = ref('')
const imageScope = ref<'ACTIVE' | 'DELETED'>('ACTIVE')
const displayMode = ref<'grid' | 'list'>('grid')
const rows = ref<ImageRecord[]>([])
const imageTableRef = ref<TableInstance>()
const selectedRows = ref<ImageRecord[]>([])
const batchDownloadLoading = ref(false)
const batchDisableLoading = ref(false)
const batchRestoreLoading = ref(false)
const batchPurgeLoading = ref(false)
const purgeDeletedLoading = ref(false)
const pagination = reactive({ page: 1, size: 20, total: 0 })
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const thumbnailUrls = reactive<Record<string, string>>({})
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewIndex = ref(-1)
const previewImageId = ref('')
const previewUrl = ref('')
const editing = ref<ImageRecord | null>(null)
const editVisible = ref(false)
const editForm = reactive({ title: '', status: 'ACTIVE', categoryId: '', tagIds: [] as string[] })
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadConfirming = ref(false)
const uploadCancelling = ref(false)
const uploadMode = ref<'SINGLE' | 'BATCH'>('SINGLE')
const uploadFileList = ref<UploadUserFile[]>([])
const singleUploadPreviewUrl = ref('')
const batchUploadPreviewUrls = reactive<Record<string, string>>({})
const uploadTags = ref<Tag[]>([])
const uploadSession = ref<UploadBatch | null>(null)
const uploadAbortController = ref<AbortController | null>(null)
const uploadItemFiles = reactive<Record<string, File>>({})
const uploadForm = reactive({ categoryId: '', tagIds: [] as string[] })

const categoryOptions = computed(() => categories.value.filter((category) => category.enabled))
const tagOptions = computed(() => tags.value.filter((tag) => tag.enabled))
const uploadTagOptions = computed(() => uploadTags.value.filter((tag) => tag.enabled))
const selectedRowIds = computed(() => selectedRows.value.map((row) => row.id))
const selectedRowIdSet = computed(() => new Set(selectedRowIds.value))
const selectedRowsOnPageCount = computed(() => rows.value.filter((row) => selectedRowIdSet.value.has(row.id)).length)
const gridAllSelected = computed(() => rows.value.length > 0 && selectedRowsOnPageCount.value === rows.value.length)
const gridSelectionIndeterminate = computed(() => selectedRowsOnPageCount.value > 0 && selectedRowsOnPageCount.value < rows.value.length)
const showingDeletedImages = computed(() => imageScope.value === 'DELETED')
const canView = computed(() => auth.hasPermission('image:view'))
const canUpload = computed(() => auth.hasPermission('image:upload'))
const canEdit = computed(() => auth.hasPermission('image:edit'))
const canDelete = computed(() => auth.hasPermission('image:delete'))
const currentPreview = computed(() => previewIndex.value >= 0 ? rows.value[previewIndex.value] ?? null : null)
const canPreviewPrevious = computed(() => previewIndex.value > 0)
const canPreviewNext = computed(() => previewIndex.value >= 0 && previewIndex.value < rows.value.length - 1)
const selectedSingleUploadFile = computed(() => uploadFileList.value[0] ?? null)
const uploadHasStagedItems = computed(() => Boolean(uploadSession.value?.items.some((item) => item.status === 'STAGED' || item.status === 'DUPLICATE')))
const uploadHasFailedItems = computed(() => Boolean(uploadSession.value?.items.some((item) => item.status === 'FAILED')))
const uploadCanConfirmSession = computed(() => Boolean(uploadSession.value && uploadHasStagedItems.value && !uploadHasFailedItems.value))
const uploadLocked = computed(() => Boolean(uploadSession.value) || uploadLoading.value || uploadConfirming.value || uploadCancelling.value)
const uploadFailedItems = computed(() => uploadSession.value?.items.filter((item) => item.status === 'FAILED') ?? [])
const uploadProcessedCount = computed(() => {
  if (!uploadSession.value) return 0
  const processed = uploadSession.value.successCount + uploadSession.value.failedCount + uploadSession.value.duplicateCount
  return Math.min(uploadSession.value.totalCount, processed)
})
const uploadProgressPercentage = computed(() => uploadSession.value?.progressPercent ?? 0)
const uploadProgressStatus = computed(() => {
  if (!uploadSession.value) return undefined
  if (uploadFailedItems.value.length > 0) return 'exception'
  return uploadProgressPercentage.value >= 100 ? 'success' : undefined
})
const uploadProgressTitle = computed(() => {
  if (!uploadSession.value) return ''
  if (uploadFailedItems.value.length > 0) return '部分图片上传失败'
  if (uploadProgressPercentage.value >= 100) return '图片处理完成'
  return '正在上传图片'
})
const uploadProgressSummary = computed(() => {
  if (!uploadSession.value) return ''
  const duplicateText = uploadSession.value.duplicateCount > 0 ? `，${uploadSession.value.duplicateCount} 张重复` : ''
  if (uploadFailedItems.value.length > 0) {
    return `${uploadFailedItems.value.length} 张失败，可单独重新上传${duplicateText}`
  }
  return `已处理 ${uploadProcessedCount.value} / ${uploadSession.value.totalCount} 张${duplicateText}`
})
const gridTagDisplayLimit = 4

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }
  return fallback
}

function ensureOperationAllowed(allowed: boolean) {
  if (!allowed) {
    ElMessage.warning('当前用户没有此操作权限')
    return false
  }
  return true
}

function formatBytes(value: number) {
  if (value < 1024 * 1024) return `${Math.max(1, Math.round(value / 1024))} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

function statusLabel(status: string) {
  if (status === 'DELETED') return '已停用'
  if (status === 'DISABLED') return '停用'
  return '启用'
}

function statusTagType(status: string) {
  if (status === 'DELETED') return 'info'
  if (status === 'DISABLED') return 'warning'
  return 'success'
}

function categoryText(row: ImageRecord) {
  return row.category?.name ?? '-'
}

function tagOptionLabel(tag: Tag) {
  return tag.groupName ? `${tag.groupName} / ${tag.name}` : tag.name
}

function visibleGridTags(row: ImageRecord) {
  return row.tags.slice(0, gridTagDisplayLimit)
}

function hiddenGridTagCount(row: ImageRecord) {
  return Math.max(0, row.tags.length - gridTagDisplayLimit)
}

function hiddenGridTags(row: ImageRecord) {
  return row.tags.slice(gridTagDisplayLimit)
}

function revokeThumbnailUrl(id: string) {
  if (thumbnailUrls[id]) {
    URL.revokeObjectURL(thumbnailUrls[id])
    delete thumbnailUrls[id]
  }
}

function revokeStaleThumbnailUrls() {
  const activeIds = new Set(rows.value.map((row) => row.id))
  Object.keys(thumbnailUrls).forEach((id) => {
    if (!activeIds.has(id)) {
      revokeThumbnailUrl(id)
    }
  })
}

function revokeAllThumbnailUrls() {
  Object.keys(thumbnailUrls).forEach(revokeThumbnailUrl)
}

function revokePreviewUrl() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function uploadFileSize(file: UploadUserFile) {
  return typeof file.size === 'number' ? formatBytes(file.size) : '未知大小'
}

function uploadFileKey(file: UploadUserFile) {
  const raw = file.raw as unknown as File | undefined
  if (raw) return `${raw.name}:${raw.size}:${raw.lastModified}`
  return `${file.name}:${file.size ?? 0}`
}

function uploadPreviewKey(file: UploadUserFile) {
  return file.uid === undefined ? uploadFileKey(file) : String(file.uid)
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

function revokeBatchUploadPreview(key: string) {
  if (batchUploadPreviewUrls[key]) {
    URL.revokeObjectURL(batchUploadPreviewUrls[key])
    delete batchUploadPreviewUrls[key]
  }
}

function revokeAllBatchUploadPreviews() {
  Object.keys(batchUploadPreviewUrls).forEach(revokeBatchUploadPreview)
}

function syncBatchUploadPreviews(files = uploadFileList.value) {
  if (uploadMode.value !== 'BATCH') {
    revokeAllBatchUploadPreviews()
    return
  }
  const activeKeys = new Set<string>()
  files.forEach((file) => {
    const key = uploadPreviewKey(file)
    activeKeys.add(key)
    if (!batchUploadPreviewUrls[key]) {
      const raw = file.raw as unknown as File | undefined
      if (raw) {
        batchUploadPreviewUrls[key] = URL.createObjectURL(raw)
      }
    }
  })
  Object.keys(batchUploadPreviewUrls).forEach((key) => {
    if (!activeKeys.has(key)) {
      revokeBatchUploadPreview(key)
    }
  })
}

async function loadCategories() {
  categories.value = await getCategories()
  tags.value = await getTags()
}

async function loadTagsForFilter() {
  selectedTagId.value = ''
  pagination.page = 1
  if (tags.value.length === 0) {
    tags.value = await getTags()
  }
}

async function loadUploadTags() {
  uploadTags.value = await getTags()
}

async function loadImages() {
  loading.value = true
  try {
    const activePreviewId = previewImageId.value
    const page = await getImages({
      keyword: keyword.value || undefined,
      categoryId: selectedCategoryId.value || undefined,
      tagId: selectedTagId.value || undefined,
      status: showingDeletedImages.value ? 'DELETED' : undefined,
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = page.items
    pagination.page = page.page
    pagination.size = page.size
    pagination.total = page.total
    selectedRows.value = []
    imageTableRef.value?.clearSelection()
    revokeStaleThumbnailUrls()
    if (activePreviewId) {
      const nextPreviewIndex = rows.value.findIndex((row) => row.id === activePreviewId)
      if (nextPreviewIndex === -1) {
        previewVisible.value = false
        closePreview()
      } else {
        previewIndex.value = nextPreviewIndex
      }
    }
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

function applyFilters() {
  pagination.page = 1
  void loadImages()
}

function resetFilters() {
  keyword.value = ''
  selectedCategoryId.value = ''
  selectedTagId.value = ''
  tags.value = []
  pagination.page = 1
  void loadImages()
}

function handleSelectionChange(selection: ImageRecord[]) {
  selectedRows.value = selection
}

function isRowSelected(row: ImageRecord) {
  return selectedRowIdSet.value.has(row.id)
}

function toggleGridSelection(row: ImageRecord, checked: boolean) {
  if (checked) {
    if (!isRowSelected(row)) {
      selectedRows.value = [...selectedRows.value, row]
    }
    return
  }
  selectedRows.value = selectedRows.value.filter((item) => item.id !== row.id)
}

function handleGridSelectionChange(row: ImageRecord, value: unknown) {
  toggleGridSelection(row, Boolean(value))
}

function handleGridSelectAllChange(value: unknown) {
  selectedRows.value = Boolean(value) ? [...rows.value] : []
}

async function syncTableSelection() {
  if (displayMode.value !== 'list') return
  const selectedIds = new Set(selectedRows.value.map((row) => row.id))
  await nextTick()
  const table = imageTableRef.value
  if (!table) return
  table.clearSelection()
  rows.value.forEach((row) => {
    if (selectedIds.has(row.id)) {
      table.toggleRowSelection(row, true)
    }
  })
}

function handleDisplayModeChange() {
  void syncTableSelection()
}

function handlePageChange(page: number) {
  pagination.page = page
  void loadImages()
}

function handlePageSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  void loadImages()
}

function handleImageScopeChange() {
  pagination.page = 1
  selectedRows.value = []
  imageTableRef.value?.clearSelection()
  if (previewVisible.value) {
    previewVisible.value = false
    closePreview()
  }
  void loadImages()
}

async function openUploadDialog() {
  if (!ensureOperationAllowed(canUpload.value)) return
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
  revokeAllBatchUploadPreviews()
  uploadSession.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
  uploadTags.value = await getTags()
  uploadVisible.value = true
}

function handleUploadModeChange() {
  uploadFileList.value = []
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
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
    revokeAllBatchUploadPreviews()
    return
  }
  uploadFileList.value = normalized
  syncBatchUploadPreviews(normalized)
}

function removeSelectedUploadFile(file?: UploadUserFile) {
  if (uploadLocked.value) return
  uploadFileList.value = file === undefined
    ? []
    : uploadFileList.value.filter((item) => uploadPreviewKey(item) !== uploadPreviewKey(file))
  if (uploadMode.value === 'SINGLE') {
    refreshSingleUploadPreview()
  } else {
    syncBatchUploadPreviews()
  }
}

function clearSelectedUploadFiles() {
  if (uploadLocked.value) return
  uploadFileList.value = []
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
}

function validateUploadSelection() {
  if (!ensureOperationAllowed(canUpload.value)) {
    return null
  }
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
  if (!ensureOperationAllowed(canUpload.value)) {
    return null
  }
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
    revokeAllBatchUploadPreviews()
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
  if (!ensureOperationAllowed(canUpload.value)) return
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
  if (!ensureOperationAllowed(canUpload.value)) return
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
    ElMessage.success(session.duplicateCount > 0 ? '图片已上传，重复图片已关联既有记录' : '图片已上传')
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
        ElMessage.success('已取消上传')
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
  revokeAllBatchUploadPreviews()
  uploadSession.value = null
  uploadLoading.value = false
  uploadConfirming.value = false
  uploadCancelling.value = false
  uploadAbortController.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
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
  if (item.status === 'FAILED') return '上传失败'
  return ''
}

async function showPreviewAt(index: number) {
  const row = rows.value[index]
  if (!row) return
  previewLoading.value = true
  try {
    const url = await imageBlobUrl(row.id, 'preview')
    revokePreviewUrl()
    previewIndex.value = index
    previewImageId.value = row.id
    previewUrl.value = url
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片预览加载失败'))
  } finally {
    previewLoading.value = false
  }
}

async function preview(row: ImageRecord) {
  const index = rows.value.findIndex((item) => item.id === row.id)
  await showPreviewAt(index)
}

function closePreview() {
  revokePreviewUrl()
  previewIndex.value = -1
  previewImageId.value = ''
  previewLoading.value = false
}

async function previewPrevious() {
  if (canPreviewPrevious.value) {
    await showPreviewAt(previewIndex.value - 1)
  }
}

async function previewNext() {
  if (canPreviewNext.value) {
    await showPreviewAt(previewIndex.value + 1)
  }
}

function handlePreviewKeydown(event: KeyboardEvent) {
  if (!previewVisible.value || previewLoading.value) return
  if (event.key === 'ArrowLeft' && canPreviewPrevious.value) {
    event.preventDefault()
    void previewPrevious()
  }
  if (event.key === 'ArrowRight' && canPreviewNext.value) {
    event.preventDefault()
    void previewNext()
  }
}

async function openEdit(row: ImageRecord) {
  if (!ensureOperationAllowed(canEdit.value)) return
  editing.value = row
  editVisible.value = true
  editForm.title = row.title
  editForm.status = row.status
  editForm.categoryId = row.category?.id ?? ''
  editForm.tagIds = row.tags.map((tag) => tag.id)
  tags.value = await getTags()
}

async function onEditCategoryChange() {
  if (tags.value.length === 0) {
    tags.value = await getTags()
  }
}

async function saveEdit() {
  if (!editing.value) return
  if (!ensureOperationAllowed(canEdit.value)) return
  try {
    await updateImage(editing.value.id, {
      title: editForm.title,
      status: editForm.status,
      categoryId: editForm.categoryId || null,
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
  if (!ensureOperationAllowed(canDelete.value)) return
  try {
    await ElMessageBox.confirm(`确定停用图片“${row.title}”？`, '停用图片', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteImage(row.id)
    ElMessage.success('图片已停用')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片停用失败'))
  }
}

async function batchDisableSelected() {
  if (!ensureOperationAllowed(canDelete.value)) return
  const ids = [...selectedRowIds.value]
  if (ids.length === 0) {
    ElMessage.warning('请先选择图片')
    return
  }
  try {
    await ElMessageBox.confirm(`确定停用选中的 ${ids.length} 张图片？`, '批量停用图片', { type: 'warning' })
  } catch {
    return
  }
  batchDisableLoading.value = true
  try {
    await batchDisableImages(ids)
    ElMessage.success('已停用选中的图片')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '批量停用失败'))
  } finally {
    batchDisableLoading.value = false
  }
}

async function restore(row: ImageRecord) {
  if (!ensureOperationAllowed(canDelete.value)) return
  try {
    await restoreImage(row.id)
    ElMessage.success('图片已恢复')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片恢复失败'))
  }
}

async function batchRestoreSelected() {
  if (!ensureOperationAllowed(canDelete.value)) return
  const ids = [...selectedRowIds.value]
  if (ids.length === 0) {
    ElMessage.warning('请先选择图片')
    return
  }
  batchRestoreLoading.value = true
  try {
    await batchRestoreImages(ids)
    ElMessage.success('已恢复选中的图片')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '批量恢复失败'))
  } finally {
    batchRestoreLoading.value = false
  }
}

async function purge(row: ImageRecord) {
  if (!ensureOperationAllowed(canDelete.value)) return
  try {
    await ElMessageBox.confirm(`彻底删除图片“${row.title}”？此操作不可恢复。`, '彻底删除图片', {
      confirmButtonText: '彻底删除',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await purgeImage(row.id)
    ElMessage.success('图片已彻底删除')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片彻底删除失败'))
  }
}

async function batchPurgeSelected() {
  if (!ensureOperationAllowed(canDelete.value)) return
  const ids = [...selectedRowIds.value]
  if (ids.length === 0) {
    ElMessage.warning('请先选择图片')
    return
  }
  try {
    await ElMessageBox.confirm(`彻底删除选中的 ${ids.length} 张图片？此操作不可恢复。`, '批量彻底删除', {
      confirmButtonText: '彻底删除',
      type: 'warning',
    })
  } catch {
    return
  }
  batchPurgeLoading.value = true
  try {
    await batchPurgeImages(ids)
    ElMessage.success('已彻底删除选中的图片')
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '批量彻底删除失败'))
  } finally {
    batchPurgeLoading.value = false
  }
}

async function purgeAllDeleted() {
  if (!ensureOperationAllowed(canDelete.value)) return
  if (pagination.total === 0) {
    ElMessage.info('当前没有已停用图片')
    return
  }
  try {
    await ElMessageBox.confirm(`确定彻底删除全部 ${pagination.total} 张已停用图片？此操作不可恢复。`, '清空已停用图片', {
      confirmButtonText: '清空并彻底删除',
      type: 'warning',
    })
  } catch {
    return
  }
  purgeDeletedLoading.value = true
  try {
    const result = await purgeDeletedImages()
    ElMessage.success(`已彻底删除 ${result.count} 张已停用图片`)
    pagination.page = 1
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '清空已停用图片失败'))
  } finally {
    purgeDeletedLoading.value = false
  }
}

async function download(row: ImageRecord) {
  if (!ensureOperationAllowed(canView.value)) return
  try {
    await downloadImage(row.id, row.originalFilename)
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片下载失败'))
  }
}

async function batchDownloadSelected() {
  if (!ensureOperationAllowed(canView.value)) return
  const ids = [...selectedRowIds.value]
  if (ids.length === 0) {
    ElMessage.warning('请先选择图片')
    return
  }
  batchDownloadLoading.value = true
  try {
    await downloadImagesZip(ids)
    ElMessage.success('批量下载已开始')
  } catch (error) {
    ElMessage.error(errorMessage(error, '批量下载失败'))
  } finally {
    batchDownloadLoading.value = false
  }
}

function isUploadConfirmDisabled() {
  return uploadCancelling.value || uploadConfirming.value || uploadLoading.value || Boolean(uploadSession.value && !uploadCanConfirmSession.value)
}

useDialogEnterSubmit(uploadVisible, confirmUploadDialog, { disabled: isUploadConfirmDisabled })
useDialogEnterSubmit(editVisible, saveEdit)

onMounted(async () => {
  window.addEventListener('keydown', handlePreviewKeydown)
  await loadCategories()
  await loadImages()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handlePreviewKeydown)
  revokeAllThumbnailUrls()
  revokePreviewUrl()
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
})
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <p>按关键词、分类、标签、上传者和时间范围检索图片。</p>
      </div>
      <el-button v-if="canUpload" type="primary" :icon="UploadFilled" @click="openUploadDialog">上传图片</el-button>
    </div>

    <div class="surface surface-pad">
      <div class="image-scope-row">
        <el-radio-group v-model="imageScope" @change="handleImageScopeChange">
          <el-radio-button label="ACTIVE">在库图片</el-radio-button>
          <el-radio-button label="DELETED">已停用</el-radio-button>
        </el-radio-group>
      </div>

      <div class="toolbar-row image-toolbar">
        <div class="image-filter-group">
          <el-input v-model="keyword" placeholder="文件名、标签、备注" :prefix-icon="Search" clearable />
          <el-select v-model="selectedCategoryId" placeholder="分类" clearable @change="loadTagsForFilter">
            <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="selectedTagId" placeholder="标签" clearable>
            <el-option v-for="tag in tagOptions" :key="tag.id" :label="tagOptionLabel(tag)" :value="tag.id" />
          </el-select>
          <div class="filter-actions">
            <el-button @click="applyFilters">筛选</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </div>
        <el-radio-group v-model="displayMode" class="display-mode-toggle" @change="handleDisplayModeChange">
          <el-radio-button label="grid">网格</el-radio-button>
          <el-radio-button label="list">列表</el-radio-button>
        </el-radio-group>
      </div>

      <div class="batch-toolbar">
        <div class="batch-toolbar-summary">
          <el-checkbox
            v-if="displayMode === 'grid'"
            class="grid-select-all"
            :model-value="gridAllSelected"
            :indeterminate="gridSelectionIndeterminate"
            :disabled="rows.length === 0 || loading"
            aria-label="选择当前页图片"
            @change="handleGridSelectAllChange"
          />
          <span>已选择 {{ selectedRows.length }} 张</span>
        </div>
        <div v-if="!showingDeletedImages" class="batch-toolbar-actions">
          <el-button
            v-if="canView"
            type="primary"
            plain
            :icon="Download"
            :disabled="selectedRows.length === 0 || batchDisableLoading"
            :loading="batchDownloadLoading"
            @click="batchDownloadSelected"
          >
            批量下载
          </el-button>
          <el-button
            v-if="canDelete"
            type="danger"
            plain
            :icon="Delete"
            :disabled="selectedRows.length === 0 || batchDownloadLoading"
            :loading="batchDisableLoading"
            @click="batchDisableSelected"
          >
            批量停用
          </el-button>
        </div>
        <div v-else-if="canDelete" class="batch-toolbar-actions">
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="pagination.total === 0 || batchRestoreLoading || batchPurgeLoading"
            :loading="purgeDeletedLoading"
            @click="purgeAllDeleted"
          >
            清空已停用
          </el-button>
          <el-button
            type="primary"
            plain
            :icon="RefreshLeft"
            :disabled="selectedRows.length === 0 || batchPurgeLoading || purgeDeletedLoading"
            :loading="batchRestoreLoading"
            @click="batchRestoreSelected"
          >
            批量恢复
          </el-button>
          <el-button
            type="danger"
            plain
            :icon="Delete"
            :disabled="selectedRows.length === 0 || batchRestoreLoading || purgeDeletedLoading"
            :loading="batchPurgeLoading"
            @click="batchPurgeSelected"
          >
            批量彻底删除
          </el-button>
        </div>
      </div>

      <div v-if="displayMode === 'grid'" v-loading="loading" class="image-grid-view">
        <div v-if="rows.length" class="image-grid">
          <article v-for="row in rows" :key="row.id" class="image-grid-card" :class="{ 'is-selected': isRowSelected(row) }">
            <el-checkbox
              class="image-grid-check"
              :model-value="isRowSelected(row)"
              :aria-label="`选择 ${row.title}`"
              @click.stop
              @change="handleGridSelectionChange(row, $event)"
            />
            <button class="image-grid-thumb" type="button" :aria-label="`预览 ${row.title}`" @click="preview(row)">
              <img v-if="thumbnailUrls[row.id]" :src="thumbnailUrls[row.id]" alt="" />
              <span v-else>预览</span>
            </button>
            <div class="image-grid-body">
              <div class="image-grid-title">
                <strong :title="row.title">{{ row.title }}</strong>
                <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
              </div>
              <span class="image-grid-file" :title="row.originalFilename">{{ row.originalFilename }} · {{ formatBytes(row.sizeBytes) }}</span>
              <div class="image-grid-taxonomy">
                <div class="image-grid-taxonomy-row">
                  <span class="image-grid-taxonomy-label">分类</span>
                  <span class="image-grid-category" :title="categoryText(row)">
                    {{ row.category?.name ?? '未分类' }}
                  </span>
                </div>
                <div class="image-grid-taxonomy-row">
                  <span class="image-grid-taxonomy-label">标签</span>
                  <div class="image-grid-chip-row">
                    <el-tag v-for="tag in visibleGridTags(row)" :key="tag.id" class="image-grid-chip" size="small" effect="light">
                      {{ tag.name }}
                    </el-tag>
                    <el-popover
                      v-if="hiddenGridTagCount(row)"
                      trigger="hover"
                      placement="top"
                      :width="260"
                      popper-class="image-grid-hidden-tags-popper"
                    >
                      <template #reference>
                        <el-tag class="image-grid-chip image-grid-more-chip" size="small" effect="plain">
                          …
                        </el-tag>
                      </template>
                      <div class="image-grid-hidden-tags" :aria-label="`剩余 ${hiddenGridTagCount(row)} 个标签`">
                        <el-tag
                          v-for="tag in hiddenGridTags(row)"
                          :key="tag.id"
                          class="image-grid-hidden-tag"
                          size="small"
                          effect="light"
                        >
                          {{ tag.name }}
                        </el-tag>
                      </div>
                    </el-popover>
                    <span v-if="row.tags.length === 0" class="taxonomy-empty">暂无标签</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="!showingDeletedImages || canDelete" class="image-grid-actions">
              <template v-if="!showingDeletedImages">
                <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
                <el-button v-if="canView" link type="primary" :icon="Download" @click="download(row)">下载</el-button>
                <el-button v-if="canDelete" link type="danger" @click="remove(row)">停用</el-button>
              </template>
              <template v-else>
                <el-button v-if="canDelete" link type="primary" @click="restore(row)">恢复</el-button>
                <el-button v-if="canDelete" link type="danger" @click="purge(row)">彻底删除</el-button>
              </template>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无图片" />
      </div>

      <el-table v-else ref="imageTableRef" v-loading="loading" :data="rows" stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="44" />
        <el-table-column label="图片" min-width="260">
          <template #default="{ row }">
            <div class="image-cell">
              <button class="thumbnail-button" type="button" :aria-label="`预览 ${row.title}`" @click="preview(row)">
                <img v-if="thumbnailUrls[row.id]" :src="thumbnailUrls[row.id]" alt="" />
                <span v-else>预览</span>
              </button>
              <div class="image-meta">
                <strong>{{ row.title }}</strong>
                <span>{{ row.originalFilename }} · {{ formatBytes(row.sizeBytes) }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="150">
          <template #default="{ row }">{{ categoryText(row) }}</template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag.id" class="tag-chip" effect="light">{{ tag.name }}</el-tag>
            <span v-if="row.tags.length === 0">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!showingDeletedImages || canDelete" label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <template v-if="!showingDeletedImages">
              <el-button v-if="canEdit" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canView" link type="primary" :icon="Download" @click="download(row)">下载</el-button>
              <el-button v-if="canDelete" link type="danger" @click="remove(row)">停用</el-button>
            </template>
            <template v-else>
              <el-button v-if="canDelete" link type="primary" @click="restore(row)">恢复</el-button>
              <el-button v-if="canDelete" link type="danger" @click="purge(row)">彻底删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :page-sizes="[20, 50, 100]"
          :total="pagination.total"
          background
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-dialog v-model="previewVisible" :title="currentPreview?.title ?? '图片预览'" width="72%" @closed="closePreview">
      <div class="preview-shell" v-loading="previewLoading">
        <el-button
          class="preview-nav"
          type="primary"
          :icon="ArrowLeft"
          :disabled="!canPreviewPrevious || previewLoading"
          aria-label="上一张"
          @click="previewPrevious"
        >
          上一张
        </el-button>
        <img v-if="previewUrl" class="preview-image" :src="previewUrl" alt="图片预览" />
        <el-empty v-else description="暂无预览" />
        <el-button
          class="preview-nav"
          type="primary"
          :icon="ArrowRight"
          :disabled="!canPreviewNext || previewLoading"
          aria-label="下一张"
          @click="previewNext"
        >
          下一张
        </el-button>
      </div>
      <div v-if="currentPreview" class="preview-meta">
        <span>{{ currentPreview.originalFilename }}</span>
        <span>{{ previewIndex + 1 }} / {{ rows.length }}</span>
      </div>
      <div v-if="currentPreview" class="preview-details">
        <div class="preview-detail-row">
          <span class="preview-detail-label">分类</span>
          <span class="preview-detail-text">{{ currentPreview.category?.name ?? '未分类' }}</span>
        </div>
        <div class="preview-detail-row">
          <span class="preview-detail-label">标签</span>
          <div class="preview-detail-tags">
            <el-tag v-for="tag in currentPreview.tags" :key="tag.id" size="small" effect="light">{{ tag.name }}</el-tag>
            <span v-if="currentPreview.tags.length === 0" class="taxonomy-empty">暂无标签</span>
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-if="canUpload" v-model="uploadVisible" title="上传图片" width="720px" :before-close="handleUploadBeforeClose">
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
                  <el-button link type="primary" :disabled="uploadLocked" @click="removeSelectedUploadFile(selectedSingleUploadFile)">重新选择</el-button>
                  <el-button link type="danger" :disabled="uploadLocked" @click="removeSelectedUploadFile(selectedSingleUploadFile)">移除</el-button>
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
            <div v-if="!uploadSession && uploadFileList.length" class="batch-preview-grid">
              <div v-for="file in uploadFileList" :key="uploadPreviewKey(file)" class="batch-preview-card">
                <div class="batch-preview-thumb">
                  <img v-if="batchUploadPreviewUrls[uploadPreviewKey(file)]" :src="batchUploadPreviewUrls[uploadPreviewKey(file)]" alt="" />
                  <div v-else class="batch-preview-fallback">预览</div>
                </div>
                <div class="batch-preview-main">
                  <strong :title="file.name">{{ file.name }}</strong>
                  <span>{{ uploadFileSize(file) }}</span>
                </div>
                <el-button class="batch-preview-remove" link type="danger" :disabled="uploadLocked" @click="removeSelectedUploadFile(file)">移除</el-button>
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
            :disabled="uploadLocked"
          >
            <el-option v-for="tag in uploadTagOptions" :key="tag.id" :label="tagOptionLabel(tag)" :value="tag.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="uploadSession" class="upload-progress-panel">
        <div class="upload-progress-head">
          <strong>{{ uploadProgressTitle }}</strong>
          <span>{{ uploadProgressSummary }}</span>
        </div>
        <el-progress
          :percentage="uploadProgressPercentage"
          :status="uploadProgressStatus"
          :stroke-width="10"
        />
      </div>
      <div v-if="uploadSession && uploadFailedItems.length" class="upload-failed-list">
        <div class="upload-failed-head">失败项</div>
        <div v-for="item in uploadFailedItems" :key="item.id" class="upload-result-row">
          <div class="upload-result-main">
            <div class="upload-result-title">
              <strong>{{ item.originalFilename }}</strong>
              <el-tag type="danger" effect="light">失败</el-tag>
            </div>
            <span>{{ uploadItemMeta(item) }}</span>
            <p v-if="uploadItemDescription(item)">{{ uploadItemDescription(item) }}</p>
          </div>
          <el-button link type="primary" :loading="uploadLoading" @click="retryUploadItem(item)">重新上传</el-button>
        </div>
      </div>
      <template #footer>
        <el-button :loading="uploadCancelling" @click="cancelUploadDialog()">取消</el-button>
        <el-button type="primary" :loading="uploadConfirming || uploadLoading" :disabled="uploadCancelling || Boolean(uploadSession && !uploadCanConfirmSession)" @click="confirmUploadDialog">
          确认
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="canEdit" v-model="editVisible" title="编辑图片信息" width="520px">
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
          <el-select v-model="editForm.categoryId" clearable placeholder="选择分类" @change="onEditCategoryChange">
            <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="editForm.tagIds" class="full-tag-select" multiple filterable clearable placeholder="搜索并选择标签">
            <el-option v-for="tag in tagOptions" :key="tag.id" :label="tagOptionLabel(tag)" :value="tag.id" />
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

.image-filter-group {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.image-scope-row {
  display: flex;
  margin-bottom: 14px;
}

.image-filter-group .el-input,
.image-filter-group .el-select {
  width: 220px;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.display-mode-toggle {
  flex: 0 0 auto;
}

.batch-toolbar {
  align-items: center;
  border-top: 1px solid #e2e8f0;
  color: #64748b;
  display: flex;
  font-size: 13px;
  justify-content: space-between;
  margin-bottom: 16px;
  margin-top: 14px;
  padding-top: 12px;
}

.batch-toolbar-summary {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.grid-select-all {
  height: 28px;
}

.batch-toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-grid-view {
  min-height: 220px;
}

.image-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
}

.image-grid-card {
  background: #ffffff;
  border: 1px solid #dfe6e2;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  position: relative;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.image-grid-card:hover,
.image-grid-card.is-selected {
  border-color: #93c5fd;
  box-shadow: 0 10px 24px rgb(15 23 42 / 8%);
}

.image-grid-card:hover {
  transform: translateY(-1px);
}

.image-grid-card.is-selected {
  box-shadow: 0 0 0 2px rgb(37 99 235 / 12%);
}

.image-grid-check {
  left: 10px;
  position: absolute;
  top: 8px;
  z-index: 1;
}

.image-grid-check :deep(.el-checkbox__inner) {
  background: rgb(255 255 255 / 92%);
  border-color: #94a3b8;
  height: 18px;
  width: 18px;
}

.image-grid-card.is-selected .image-grid-check :deep(.el-checkbox__inner),
.image-grid-check :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: #2563eb !important;
  border-color: #2563eb !important;
}

.image-grid-card.is-selected .image-grid-check :deep(.el-checkbox__inner::after),
.image-grid-check :deep(.el-checkbox__input.is-checked .el-checkbox__inner::after) {
  border-color: #ffffff !important;
}

.image-grid-thumb {
  aspect-ratio: 4 / 3;
  background: #f1f5f9;
  border: 0;
  color: #64748b;
  cursor: pointer;
  display: block;
  overflow: hidden;
  padding: 0;
  width: 100%;
}

.image-grid-thumb:hover img {
  transform: scale(1.025);
}

.image-grid-thumb img,
.image-grid-thumb span {
  display: block;
  height: 100%;
  width: 100%;
}

.image-grid-thumb img {
  object-fit: cover;
  transition: transform 0.2s ease;
}

.image-grid-thumb span {
  align-items: center;
  display: flex;
  font-size: 13px;
  justify-content: center;
}

.image-grid-body {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 12px 12px 8px;
}

.image-grid-title {
  align-items: center;
  display: flex;
  gap: 8px;
  justify-content: space-between;
  min-width: 0;
}

.image-grid-title strong,
.image-grid-file {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-grid-title strong {
  color: #1f2937;
  font-size: 14px;
  min-width: 0;
}

.image-grid-title .el-tag {
  flex: 0 0 auto;
}

.image-grid-file {
  color: #64748b;
  font-size: 12px;
  min-width: 0;
}

.image-grid-taxonomy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.image-grid-taxonomy-row {
  align-items: flex-start;
  display: grid;
  gap: 6px;
  grid-template-columns: 30px minmax(0, 1fr);
  min-width: 0;
}

.image-grid-taxonomy-label,
.preview-detail-label {
  color: #64748b;
  flex: 0 0 auto;
  font-size: 12px;
  line-height: 22px;
}

.image-grid-category,
.preview-detail-text {
  color: #334155;
  font-size: 12px;
  line-height: 22px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.image-grid-chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 48px;
  min-width: 0;
  overflow: hidden;
}

.image-grid-chip {
  margin: 0;
  max-width: 100%;
}

.image-grid-more-chip {
  min-width: 28px;
  text-align: center;
}

.image-grid-hidden-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 236px;
}

.image-grid-hidden-tag {
  margin: 0;
  max-width: 100%;
}

.image-grid-chip :deep(.el-tag__content),
.image-grid-hidden-tag :deep(.el-tag__content),
.preview-detail-tags :deep(.el-tag__content) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:global(.image-grid-hidden-tags-popper) {
  max-width: 280px;
}

.taxonomy-empty {
  color: #94a3b8;
  font-size: 12px;
  line-height: 22px;
}

.image-grid-actions {
  align-items: center;
  border-top: 1px solid #eef2f7;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin-top: auto;
  min-height: 40px;
  padding: 6px 10px 8px;
}

.image-cell {
  align-items: center;
  display: flex;
  gap: 12px;
}

.thumbnail-button {
  background: #f1f5f9;
  border: 0;
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  display: block;
  flex: 0 0 auto;
  height: 58px;
  overflow: hidden;
  padding: 0;
  width: 76px;
}

.thumbnail-button:hover {
  outline: 2px solid #93c5fd;
  outline-offset: 2px;
}

.thumbnail-button img,
.thumbnail-button span {
  display: block;
  height: 100%;
  width: 100%;
}

.thumbnail-button img {
  object-fit: cover;
}

.thumbnail-button span {
  align-items: center;
  display: flex;
  font-size: 12px;
  justify-content: center;
}

.image-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
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

.preview-shell {
  align-items: center;
  display: grid;
  gap: 18px;
  grid-template-columns: 96px minmax(0, 1fr) 96px;
  min-height: 280px;
}

.preview-nav {
  border-radius: 999px;
  box-shadow: 0 12px 28px rgb(37 99 235 / 22%);
  font-weight: 600;
  height: 46px;
  justify-self: center;
  min-width: 92px;
}

.preview-nav.is-disabled {
  box-shadow: none;
}

.preview-meta {
  color: #64748b;
  display: flex;
  font-size: 13px;
  justify-content: space-between;
  margin-top: 12px;
}

.preview-details {
  border-top: 1px solid #e2e8f0;
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
}

.preview-detail-row {
  align-items: flex-start;
  display: grid;
  gap: 10px;
  grid-template-columns: 42px minmax(0, 1fr);
}

.preview-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
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
.batch-preview-main strong,
.upload-result-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.single-preview-info span,
.batch-upload-actions span,
.upload-progress-head span,
.batch-preview-main span,
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

.batch-preview-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fill, minmax(118px, 1fr));
  margin-top: 12px;
  max-height: 330px;
  overflow: auto;
  padding-right: 2px;
}

.upload-progress-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
}

.upload-progress-head {
  align-items: center;
  display: flex;
  gap: 10px;
  justify-content: space-between;
  min-width: 0;
}

.upload-progress-head strong {
  color: #1f2937;
  font-size: 14px;
}

.upload-failed-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.upload-failed-head {
  color: #64748b;
  font-size: 13px;
}

.batch-preview-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  padding: 8px;
}

.batch-preview-thumb {
  aspect-ratio: 4 / 3;
  background: #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
  width: 100%;
}

.batch-preview-thumb img,
.batch-preview-fallback {
  height: 100%;
  width: 100%;
}

.batch-preview-thumb img {
  display: block;
  object-fit: cover;
}

.batch-preview-fallback {
  align-items: center;
  color: #64748b;
  display: flex;
  font-size: 13px;
  justify-content: center;
}

.batch-preview-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.batch-preview-main strong {
  color: #1f2937;
  font-size: 13px;
  line-height: 1.3;
}

.batch-preview-main span {
  font-size: 12px;
}

.batch-preview-remove {
  align-self: flex-start;
  min-height: 22px;
  padding: 0;
}

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

@media (max-width: 980px) {
  .image-toolbar {
    align-items: stretch;
  }

  .image-filter-group,
  .display-mode-toggle,
  .batch-toolbar,
  .batch-toolbar-summary,
  .batch-toolbar-actions {
    width: 100%;
  }

  .image-filter-group .el-input,
  .image-filter-group .el-select,
  .filter-actions {
    width: 100%;
  }

  .filter-actions .el-button {
    flex: 1;
  }

  .batch-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .image-grid {
    grid-template-columns: repeat(auto-fill, minmax(156px, 1fr));
  }
}
</style>
