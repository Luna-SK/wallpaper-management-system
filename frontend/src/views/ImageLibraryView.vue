<script setup lang="ts">
import {
  computed,
  defineComponent,
  h,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
  type Component,
  type PropType,
  type VNode,
} from 'vue'
import { ElButton, ElInput, ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance, UploadFile, UploadFiles, UploadUserFile } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  Crop,
  Delete,
  Download,
  EditPen,
  RefreshLeft,
  RefreshRight,
  Search,
  UploadFilled,
  Warning,
  ZoomIn,
} from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import UserAvatar from '../components/UserAvatar.vue'
import {
  batchDisableImages,
  batchPurgeImages,
  batchRestoreImages,
  cancelUploadSession,
  confirmUploadSession,
  createUploadSession,
  deleteImageVersion,
  deleteImage,
  editImageContent,
  downloadImage,
  downloadImagesZip,
  getImage,
  getImages,
  getImageVersions,
  imageEditSourceUrl,
  imageBlobUrl,
  imageVersionBlobUrl,
  purgeImage,
  purgeDeletedImages,
  retryUploadSessionItem,
  restoreImageVersion,
  restoreImage,
  updateImage,
  uploadSessionItem,
  type ImageSortBy,
  type ImageSortDirection,
  type ImageStatus,
  type ImageRecord,
  type ImageVersionRecord,
  type UploadDuplicateImage,
  type UploadBatch,
  type UploadBatchItemStatus,
  type UploadBatchItem,
  type UploadBatchStatus,
} from '../api/images'
import {
  createImageComment,
  deleteImageComment,
  favoriteImage,
  getImageComments,
  likeImage,
  unfavoriteImage,
  unlikeImage,
  updateImageComment,
  type ImageComment,
  type InteractionState,
} from '../api/interactions'
import { getCategories, getTags, type Category, type Tag } from '../api/taxonomy'
import { useAuthStore } from '../stores/auth'
import { useDialogEnterSubmit } from '../utils/dialogEnterSubmit'

type EditorMode = 'crop' | 'pan'
type EditorCropHandle = 'n' | 's' | 'e' | 'w' | 'nw' | 'ne' | 'sw' | 'se'
type EditorDragType = 'pan' | 'draw' | 'move' | EditorCropHandle

interface EditorPoint {
  x: number
  y: number
}

interface EditorCropSnapshot {
  x: number
  y: number
  width: number
  height: number
}

interface EditorDragState {
  type: EditorDragType
  pointerId: number
  startCanvas: EditorPoint
  startImage: EditorPoint
  startCrop: EditorCropSnapshot
  startOffsetX: number
  startOffsetY: number
}

const EDITOR_MIN_VIEW_ZOOM = 0.25
const EDITOR_MAX_VIEW_ZOOM = 4
const EDITOR_VIEW_PADDING = 48
const EDITOR_HANDLE_SIZE = 9
const EDITOR_MIN_CROP_SIZE = 8

const auth = useAuthStore()
const loading = ref(false)
const keyword = ref('')
const selectedCategoryId = ref('')
const selectedTagId = ref('')
const favoriteOnly = ref(false)
const sortBy = ref<ImageSortBy>('createdAt')
const sortDirection = ref<ImageSortDirection>('desc')
const imageScope = ref<ImageStatus>('ACTIVE')
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
const detachedPreviewImage = ref<ImageRecord | null>(null)
const editing = ref<ImageRecord | null>(null)
const editVisible = ref(false)
const editForm = reactive<{ title: string; status: ImageStatus; categoryId: string; tagIds: string[] }>({
  title: '',
  status: 'ACTIVE',
  categoryId: '',
  tagIds: [],
})
const editSaving = ref(false)
const editImageLoading = ref(false)
const editCanvasRef = ref<HTMLCanvasElement>()
const editSourceImage = ref<HTMLImageElement | null>(null)
const editSourceUrl = ref('')
const imageVersions = ref<ImageVersionRecord[]>([])
const imageVersionsLoading = ref(false)
const imageVersionActionId = ref('')
const imageVersionLoadToken = ref(0)
const versionThumbnailUrls = reactive<Record<string, string>>({})
const versionThumbnailFailed = reactive<Record<string, boolean>>({})
const versionPreviewVisible = ref(false)
const versionPreviewLoading = ref(false)
const versionPreviewUrl = ref('')
const versionPreviewVersion = ref<ImageVersionRecord | null>(null)
const versionPreviewRequestToken = ref(0)
const comments = ref<ImageComment[]>([])
const commentsLoading = ref(false)
const commentSubmitting = ref(false)
const commentDraft = ref('')
const replyDraft = ref('')
const replyingCommentId = ref('')
const collapsedCommentIds = ref<Set<string>>(new Set())
const commentEditingId = ref('')
const commentEditContent = ref('')
const commentPagination = reactive({ page: 1, size: 20, total: 0, commentTotal: 0 })
const editorDrag = ref<EditorDragState | null>(null)
const editorHoverHandle = ref<EditorDragType | null>(null)
const imageEditor = reactive({
  naturalWidth: 0,
  naturalHeight: 0,
  cropX: 0,
  cropY: 0,
  cropWidth: 0,
  cropHeight: 0,
  rotation: 0,
  outputScale: 100,
  viewZoom: 1,
  viewOffsetX: 0,
  viewOffsetY: 0,
  mode: 'crop' as EditorMode,
  dirty: false,
})
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
const uploadTitles = reactive<Record<string, string>>({})
const uploadDuplicatePreviewImages = reactive<Record<string, ImageRecord>>({})
const uploadDuplicateThumbnailUrls = reactive<Record<string, string>>({})
const uploadDuplicateLoading = reactive<Record<string, boolean>>({})
const uploadDuplicateErrors = reactive<Record<string, string>>({})
const uploadDuplicateLoadToken = ref(0)
const uploadForm = reactive({ categoryId: '', tagIds: [] as string[] })
const confirmableUploadItemStatuses: UploadBatchItemStatus[] = ['STAGED', 'DUPLICATE']
const terminalUploadBatchStatuses: UploadBatchStatus[] = ['CONFIRMED', 'CANCELLED', 'EXPIRED']
const sortOptions: Array<{ label: string; value: ImageSortBy }> = [
  { label: '上传时间', value: 'createdAt' },
  { label: '更新时间', value: 'updatedAt' },
  { label: '标题', value: 'title' },
  { label: '文件大小', value: 'sizeBytes' },
  { label: '分辨率', value: 'resolution' },
  { label: '评论数', value: 'commentCount' },
  { label: '收藏数', value: 'favoriteCount' },
  { label: '点赞数', value: 'likeCount' },
  { label: '浏览数', value: 'viewCount' },
  { label: '下载数', value: 'downloadCount' },
]

const categoryOptions = computed(() => categories.value.filter((category) => category.enabled))
const tagOptions = computed(() => tags.value.filter((tag) => tag.enabled))
const uploadTagOptions = computed(() => uploadTags.value.filter((tag) => tag.enabled))
const tagOptionGroups = computed(() => groupTagsByGroup(tagOptions.value))
const uploadTagOptionGroups = computed(() => groupTagsByGroup(uploadTagOptions.value))
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
const canManageInteractions = computed(() => auth.hasPermission('interaction:manage'))
const currentPreview = computed(() => detachedPreviewImage.value ?? (previewIndex.value >= 0 ? rows.value[previewIndex.value] ?? null : null))
const currentPreviewActionable = computed(() => Boolean(currentPreview.value && currentPreview.value.status !== 'DELETED'))
const currentPreviewTagGroups = computed(() => groupTagsByGroup(currentPreview.value?.tags ?? []))
const imageEditorReady = computed(() => Boolean(editSourceImage.value && imageEditor.cropWidth > 0 && imageEditor.cropHeight > 0))
const imageEditorCursor = computed(() => {
  if (!imageEditorReady.value) return 'default'
  if (editorDrag.value?.type === 'pan') return 'grabbing'
  if (imageEditor.mode === 'pan') return 'grab'
  return cursorForEditorHandle(editorDrag.value?.type ?? editorHoverHandle.value)
})
const editorOriginalSizeText = computed(() => imageEditor.naturalWidth > 0 ? `${imageEditor.naturalWidth} × ${imageEditor.naturalHeight}` : '-')
const editorCropSizeText = computed(() => imageEditorReady.value ? `${imageEditor.cropWidth} × ${imageEditor.cropHeight}` : '-')
const editorCropOriginText = computed(() => imageEditorReady.value ? `X ${imageEditor.cropX}，Y ${imageEditor.cropY}` : '-')
const editorOutputSizeText = computed(() => {
  if (!imageEditorReady.value) return '-'
  const scale = imageEditor.outputScale / 100
  const width = Math.max(1, Math.round(imageEditor.cropWidth * scale))
  const height = Math.max(1, Math.round(imageEditor.cropHeight * scale))
  const normalizedRotation = normalizedEditorRotation()
  return normalizedRotation === 90 || normalizedRotation === 270 ? `${height} × ${width}` : `${width} × ${height}`
})
const currentVersion = computed(() => imageVersions.value.find((version) => version.current) ?? null)
const versionPreviewTitle = computed(() => {
  const version = versionPreviewVersion.value
  if (!version) return '版本预览'
  return `版本预览 V${version.versionNo}${version.current ? '（当前）' : ''}`
})
const canPreviewPrevious = computed(() => !detachedPreviewImage.value && previewIndex.value > 0)
const canPreviewNext = computed(() => !detachedPreviewImage.value && previewIndex.value >= 0 && previewIndex.value < rows.value.length - 1)
const previewPositionText = computed(() => previewIndex.value >= 0 ? `${previewIndex.value + 1} / ${rows.value.length}` : '已有图片')
const selectedSingleUploadFile = computed(() => uploadFileList.value[0] ?? null)
const uploadHasStagedItems = computed(() => Boolean(uploadSession.value?.items.some((item) => confirmableUploadItemStatuses.includes(item.status))))
const uploadHasFailedItems = computed(() => Boolean(uploadSession.value?.items.some((item) => item.status === 'FAILED')))
const uploadDuplicateItems = computed(() => uploadSession.value?.items.filter((item) => item.status === 'DUPLICATE') ?? [])
const uploadCanConfirmSession = computed(() => Boolean(uploadSession.value && !terminalUploadBatchStatuses.includes(uploadSession.value.status) && uploadHasStagedItems.value && !uploadHasFailedItems.value))
const uploadCanFinishConfirmedDuplicates = computed(() => uploadSession.value?.status === 'CONFIRMED' && uploadDuplicateItems.value.length > 0)
const uploadLocked = computed(() => Boolean(uploadSession.value) || uploadLoading.value || uploadConfirming.value || uploadCancelling.value)
const uploadFailedItems = computed(() => uploadSession.value?.items.filter((item) => item.status === 'FAILED') ?? [])
const uploadPrimaryButtonText = computed(() => uploadCanFinishConfirmedDuplicates.value ? '完成' : '确认')
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
  if (uploadSession.value.duplicateCount > 0 && uploadSession.value.successCount === 0 && uploadProgressPercentage.value >= 100) return '发现重复图片'
  if (uploadProgressPercentage.value >= 100) return '图片处理完成'
  return '正在上传图片'
})
const uploadProgressSummary = computed(() => {
  if (!uploadSession.value) return ''
  const duplicateText = uploadSession.value.duplicateCount > 0 ? `，${uploadSession.value.duplicateCount} 张重复` : ''
  if (uploadFailedItems.value.length > 0) {
    return `${uploadFailedItems.value.length} 张失败，可单独重新上传${duplicateText}`
  }
  if (uploadSession.value.duplicateCount > 0 && uploadSession.value.successCount === 0) {
    return `发现 ${uploadSession.value.duplicateCount} 张重复，未新增图片`
  }
  if (uploadSession.value.duplicateCount > 0) {
    return `已处理 ${uploadProcessedCount.value} / ${uploadSession.value.totalCount} 张，其中 ${uploadSession.value.duplicateCount} 张重复`
  }
  return `已处理 ${uploadProcessedCount.value} / ${uploadSession.value.totalCount} 张${duplicateText}`
})

function countCommentReplies(comment: ImageComment): number {
  return (comment.replies ?? []).reduce((total, reply) => total + 1 + countCommentReplies(reply), 0)
}

function isEditedComment(comment: ImageComment): boolean {
  const createdAt = Date.parse(comment.createdAt)
  const updatedAt = Date.parse(comment.updatedAt)
  if (!Number.isFinite(createdAt) || !Number.isFinite(updatedAt)) {
    return comment.updatedAt !== comment.createdAt
  }
  return Math.abs(updatedAt - createdAt) > 1000
}

const CommentThreadNode: Component = defineComponent({
  name: 'CommentThreadNode',
  props: {
    comment: { type: Object as PropType<ImageComment>, required: true },
    editingId: { type: String, default: '' },
    editContent: { type: String, default: '' },
    canManageInteractions: { type: Boolean, default: false },
    level: { type: Number, default: 0 },
    replyTargetId: { type: String, default: '' },
    replyDraft: { type: String, default: '' },
    replySubmitting: { type: Boolean, default: false },
    collapsedIds: { type: Array as PropType<string[]>, default: () => [] },
  },
  emits: [
    'reply',
    'edit',
    'save',
    'cancel',
    'delete',
    'submit-reply',
    'cancel-reply',
    'toggle-collapse',
    'update:editContent',
    'update:replyDraft',
  ],
  setup(props, { emit }): () => VNode {
    const forwardEvents = (comment: ImageComment): Record<string, unknown> => ({
      onReply: (value: ImageComment) => emit('reply', value),
      onEdit: (value: ImageComment) => emit('edit', value),
      onSave: (value: ImageComment) => emit('save', value),
      onCancel: () => emit('cancel'),
      onDelete: (value: ImageComment) => emit('delete', value),
      onSubmitReply: (value: ImageComment) => emit('submit-reply', value),
      onCancelReply: () => emit('cancel-reply'),
      onToggleCollapse: (value: ImageComment) => emit('toggle-collapse', value),
      'onUpdate:editContent': (value: string) => emit('update:editContent', value),
      'onUpdate:replyDraft': (value: string) => emit('update:replyDraft', value),
      comment,
      editingId: props.editingId,
      editContent: props.editContent,
      canManageInteractions: props.canManageInteractions,
      level: props.level + 1,
      replyTargetId: props.replyTargetId,
      replyDraft: props.replyDraft,
      replySubmitting: props.replySubmitting,
      collapsedIds: props.collapsedIds,
    })

    return () => {
      const comment = props.comment
      const deleted = comment.deleted
      const isEditing = props.editingId === comment.id
      const replies = comment.replies ?? []
      const replyCount = countCommentReplies(comment)
      const hasVisibleReplies = replies.length > 0
      const hasAnyReplies = comment.hasReplies || hasVisibleReplies
      const collapsed = props.collapsedIds.includes(comment.id)
      const isReplying = props.replyTargetId === comment.id

      return h(
        'article',
        {
          class: [
            'comment-thread-node',
            {
              'is-deleted': deleted,
              'is-reply': props.level > 0,
              'is-collapsed': collapsed,
              'has-replies': hasVisibleReplies,
            },
          ],
        },
        [
          h('div', { class: 'comment-thread-rail' }, [
            h(UserAvatar, {
              class: 'comment-avatar',
              name: deleted ? '已删除评论' : comment.authorName,
              avatarUrl: deleted ? null : comment.authorAvatarUrl,
              size: props.level === 0 ? 34 : 30,
            }),
            hasVisibleReplies
              ? h(
                  'button',
                  {
                    type: 'button',
                    class: 'comment-rail-toggle',
                    'aria-label': collapsed ? `展开 ${replyCount} 条回复` : '折叠回复',
                    onClick: () => emit('toggle-collapse', comment),
                  },
                  () => (collapsed ? '+' : '−'),
                )
              : null,
          ]),
          h('div', { class: 'comment-thread-content' }, [
            h('div', { class: 'comment-thread-body' }, [
              h('div', { class: 'comment-thread-head' }, [
                h('strong', deleted ? '已删除评论' : comment.authorName),
                h('span', formatDateTime(comment.createdAt)),
                isEditedComment(comment) ? h('span', { class: 'comment-edited' }, '已编辑') : null,
              ]),
              isEditing
                ? [
                    h(ElInput, {
                      modelValue: props.editContent,
                      'onUpdate:modelValue': (value: string) => emit('update:editContent', value),
                      type: 'textarea',
                      rows: 3,
                      maxlength: 1000,
                      showWordLimit: true,
                    }),
                    h('div', { class: 'comment-actions' }, [
                      h(ElButton, { link: true, type: 'primary', onClick: () => emit('save', comment) }, () => '保存'),
                      h(ElButton, { link: true, onClick: () => emit('cancel') }, () => '取消'),
                    ]),
                  ]
                : [
                    deleted
                      ? h('p', { class: 'comment-deleted-text' }, '该评论已删除')
                      : h('p', comment.content ?? ''),
                    h('div', { class: 'comment-actions' }, [
                      !deleted
                        ? h(ElButton, { link: true, type: 'primary', onClick: () => emit('reply', comment) }, () => '回复')
                        : null,
                      !deleted && comment.mine && !hasAnyReplies
                        ? h(ElButton, { link: true, type: 'primary', onClick: () => emit('edit', comment) }, () => '编辑')
                        : null,
                      !deleted && (comment.mine || props.canManageInteractions)
                        ? h(ElButton, { link: true, type: 'danger', onClick: () => emit('delete', comment) }, () => '删除')
                        : null,
                      hasVisibleReplies
                        ? h(
                            ElButton,
                            { link: true, type: 'info', onClick: () => emit('toggle-collapse', comment) },
                            () => (collapsed ? `展开 ${replyCount} 条回复` : '折叠'),
                          )
                        : null,
                    ]),
                  ],
            ]),
            isReplying && !deleted
              ? h('div', { class: 'comment-inline-reply' }, [
                  h('div', { class: 'comment-reply-target' }, [
                    h('span', `回复 @${comment.authorName}`),
                    h(ElButton, { link: true, type: 'primary', onClick: () => emit('cancel-reply') }, () => '取消回复'),
                  ]),
                  h(ElInput, {
                    modelValue: props.replyDraft,
                    'onUpdate:modelValue': (value: string) => emit('update:replyDraft', value),
                    type: 'textarea',
                    rows: 2,
                    maxlength: 1000,
                    showWordLimit: true,
                    placeholder: `回复 @${comment.authorName}`,
                  }),
                  h('div', { class: 'comment-actions' }, [
                    h(
                      ElButton,
                      {
                        class: 'comment-reply-submit-button',
                        type: 'primary',
                        loading: props.replySubmitting,
                        onClick: () => emit('submit-reply', comment),
                      },
                      () => '发布回复',
                    ),
                  ]),
                ])
              : null,
            hasVisibleReplies && !collapsed
              ? h(
                  'div',
                  { class: 'comment-children' },
                  replies.map((reply) => h(CommentThreadNode, { key: reply.id, ...forwardEvents(reply) })),
                )
              : null,
          ]),
        ],
      )
    }
  },
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

function formatDateTime(value: string | null) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function formatDimensions(row: ImageRecord) {
  if (row.width && row.height) return `${row.width} × ${row.height}`
  return '未知'
}

function categoryText(row: ImageRecord) {
  return row.category?.name ?? '-'
}

function groupTagsByGroup<T extends { id: string; groupId: string; groupName: string | null; name: string }>(source: T[]) {
  const groups = new Map<string, { key: string; label: string; tags: T[] }>()
  source.forEach((tag) => {
    const key = tag.groupId || '__ungrouped__'
    const label = tag.groupName?.trim() || '未分组'
    if (!groups.has(key)) {
      groups.set(key, { key, label, tags: [] })
    }
    groups.get(key)?.tags.push(tag)
  })
  return [...groups.values()]
}

function replaceImageRecord(record: ImageRecord) {
  const index = rows.value.findIndex((row) => row.id === record.id)
  if (index !== -1) {
    rows.value[index] = record
  }
  if (detachedPreviewImage.value?.id === record.id) {
    detachedPreviewImage.value = record
  }
  return record
}

function applyInteractionState(state: InteractionState) {
  const row = rows.value.find((item) => item.id === state.imageId)
  if (row) {
    row.favoriteCount = state.favoriteCount
    row.likeCount = state.likeCount
    row.commentCount = state.commentCount
    row.favoritedByMe = state.favoritedByMe
    row.likedByMe = state.likedByMe
  }
  if (detachedPreviewImage.value?.id === state.imageId) {
    detachedPreviewImage.value = {
      ...detachedPreviewImage.value,
      favoriteCount: state.favoriteCount,
      likeCount: state.likeCount,
      commentCount: state.commentCount,
      favoritedByMe: state.favoritedByMe,
      likedByMe: state.likedByMe,
    }
  }
}

async function refreshImageRecord(id: string) {
  return replaceImageRecord(await getImage(id))
}

async function refreshImageRecordQuietly(id: string) {
  try {
    await refreshImageRecord(id)
  } catch {
    ElMessage.warning('图片计数刷新失败，请稍后刷新列表')
  }
}

async function refreshOpenPreviewImage(imageId: string, record: ImageRecord, reloadBlob: boolean) {
  if (!previewVisible.value || previewImageId.value !== imageId) return
  replaceImageRecord(record)
  if (!reloadBlob) return
  try {
    const nextPreviewUrl = await imageBlobUrl(imageId, 'preview', `${record.updatedAt || ''}-${Date.now()}`)
    if (!previewVisible.value || previewImageId.value !== imageId) {
      URL.revokeObjectURL(nextPreviewUrl)
      return
    }
    const previousPreviewUrl = previewUrl.value
    previewUrl.value = nextPreviewUrl
    if (previousPreviewUrl) {
      URL.revokeObjectURL(previousPreviewUrl)
    }
  } catch {
    ElMessage.warning('图片预览刷新失败，请稍后重试')
  }
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

async function refreshThumbnailUrl(id: string, cacheKey: string | number = Date.now()) {
  try {
    const nextThumbnailUrl = await imageBlobUrl(id, 'thumbnail', cacheKey)
    const previousThumbnailUrl = thumbnailUrls[id]
    thumbnailUrls[id] = nextThumbnailUrl
    if (previousThumbnailUrl) {
      URL.revokeObjectURL(previousThumbnailUrl)
    }
  } catch {
    ElMessage.warning('图片缩略图刷新失败，请稍后刷新列表')
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

function revokeUploadDuplicateThumbnailUrl(imageId: string) {
  if (uploadDuplicateThumbnailUrls[imageId]) {
    URL.revokeObjectURL(uploadDuplicateThumbnailUrls[imageId])
    delete uploadDuplicateThumbnailUrls[imageId]
  }
}

function clearUploadDuplicateDetails() {
  uploadDuplicateLoadToken.value++
  Object.keys(uploadDuplicateThumbnailUrls).forEach(revokeUploadDuplicateThumbnailUrl)
  Object.keys(uploadDuplicatePreviewImages).forEach((imageId) => delete uploadDuplicatePreviewImages[imageId])
  Object.keys(uploadDuplicateLoading).forEach((imageId) => delete uploadDuplicateLoading[imageId])
  Object.keys(uploadDuplicateErrors).forEach((imageId) => delete uploadDuplicateErrors[imageId])
}

function pruneUploadDuplicateDetails(activeImageIds: Set<string>) {
  Object.keys(uploadDuplicatePreviewImages).forEach((imageId) => {
    if (!activeImageIds.has(imageId)) {
      delete uploadDuplicatePreviewImages[imageId]
    }
  })
  Object.keys(uploadDuplicateThumbnailUrls).forEach((imageId) => {
    if (!activeImageIds.has(imageId)) {
      revokeUploadDuplicateThumbnailUrl(imageId)
    }
  })
  Object.keys(uploadDuplicateLoading).forEach((imageId) => {
    if (!activeImageIds.has(imageId)) {
      delete uploadDuplicateLoading[imageId]
    }
  })
  Object.keys(uploadDuplicateErrors).forEach((imageId) => {
    if (!activeImageIds.has(imageId)) {
      delete uploadDuplicateErrors[imageId]
    }
  })
}

function revokeEditSourceUrl() {
  if (editSourceUrl.value) {
    URL.revokeObjectURL(editSourceUrl.value)
    editSourceUrl.value = ''
  }
}

function revokeVersionThumbnailUrl(versionId: string) {
  if (versionThumbnailUrls[versionId]) {
    URL.revokeObjectURL(versionThumbnailUrls[versionId])
    delete versionThumbnailUrls[versionId]
  }
  delete versionThumbnailFailed[versionId]
}

function revokeAllVersionThumbnailUrls() {
  Object.keys(versionThumbnailUrls).forEach(revokeVersionThumbnailUrl)
  Object.keys(versionThumbnailFailed).forEach((versionId) => delete versionThumbnailFailed[versionId])
}

function pruneVersionThumbnailUrls(activeIds: Set<string>) {
  Object.keys(versionThumbnailUrls).forEach((versionId) => {
    if (!activeIds.has(versionId)) {
      revokeVersionThumbnailUrl(versionId)
    }
  })
  Object.keys(versionThumbnailFailed).forEach((versionId) => {
    if (!activeIds.has(versionId)) {
      delete versionThumbnailFailed[versionId]
    }
  })
}

function revokeVersionPreviewUrl() {
  if (versionPreviewUrl.value) {
    URL.revokeObjectURL(versionPreviewUrl.value)
    versionPreviewUrl.value = ''
  }
}

function closeVersionPreview() {
  versionPreviewRequestToken.value += 1
  revokeVersionPreviewUrl()
  versionPreviewVisible.value = false
  versionPreviewLoading.value = false
  versionPreviewVersion.value = null
}

function resetImageEditor() {
  imageVersionLoadToken.value += 1
  revokeEditSourceUrl()
  closeVersionPreview()
  revokeAllVersionThumbnailUrls()
  editSourceImage.value = null
  imageEditor.naturalWidth = 0
  imageEditor.naturalHeight = 0
  imageEditor.cropX = 0
  imageEditor.cropY = 0
  imageEditor.cropWidth = 0
  imageEditor.cropHeight = 0
  imageEditor.rotation = 0
  imageEditor.outputScale = 100
  imageEditor.viewZoom = 1
  imageEditor.viewOffsetX = 0
  imageEditor.viewOffsetY = 0
  imageEditor.mode = 'crop'
  imageEditor.dirty = false
  imageVersions.value = []
  imageVersionsLoading.value = false
  imageVersionActionId.value = ''
  editorDrag.value = null
  editorHoverHandle.value = null
  drawEditorPreview()
}

function uploadFileSize(file: UploadUserFile) {
  return typeof file.size === 'number' ? formatBytes(file.size) : '未知大小'
}

function rawUploadFileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`
}

function uploadFileKey(file: UploadUserFile) {
  const raw = file.raw as unknown as File | undefined
  if (raw) return rawUploadFileKey(raw)
  return `${file.name}:${file.size ?? 0}`
}

function uploadTitleKey(file: UploadUserFile) {
  return uploadFileKey(file)
}

function uploadPreviewKey(file: UploadUserFile) {
  return file.uid === undefined ? uploadFileKey(file) : String(file.uid)
}

function uploadDefaultTitle(filename: string) {
  const cleaned = filename.replace(/\\/g, '/').split('/').pop() || 'image'
  const dot = cleaned.lastIndexOf('.')
  return dot > 0 ? cleaned.slice(0, dot) : cleaned
}

function uploadTitlePlaceholder(file: UploadUserFile, includeLabel = false) {
  const defaultTitle = uploadDefaultTitle(file.name)
  return includeLabel ? `图片标题，默认：${defaultTitle}` : `默认：${defaultTitle}`
}

function normalizedUploadTitle(file: UploadUserFile) {
  const title = uploadTitles[uploadTitleKey(file)]?.trim()
  return title ? title : null
}

function clearUploadTitles() {
  Object.keys(uploadTitles).forEach((key) => delete uploadTitles[key])
}

function syncUploadTitles(files = uploadFileList.value) {
  const activeKeys = new Set(files.map(uploadTitleKey))
  Object.keys(uploadTitles).forEach((key) => {
    if (!activeKeys.has(key)) {
      delete uploadTitles[key]
    }
  })
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

function selectedUploadEntries() {
  return uploadFileList.value.flatMap((item) => {
    const raw = item.raw as unknown as File | undefined
    return raw ? [{ file: raw, title: normalizedUploadTitle(item) }] : []
  })
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
      favoriteOnly: favoriteOnly.value || undefined,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value,
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
    if (activePreviewId && !detachedPreviewImage.value) {
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
  favoriteOnly.value = false
  sortBy.value = 'createdAt'
  sortDirection.value = 'desc'
  tags.value = []
  pagination.page = 1
  void loadImages()
}

function handleSortChange() {
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
  clearUploadTitles()
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
  clearUploadDuplicateDetails()
  uploadSession.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
  uploadTags.value = await getTags()
  uploadVisible.value = true
}

function handleUploadModeChange() {
  uploadFileList.value = []
  clearUploadTitles()
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
  clearUploadDuplicateDetails()
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
    syncUploadTitles()
    refreshSingleUploadPreview()
    revokeAllBatchUploadPreviews()
    return
  }
  uploadFileList.value = normalized
  syncUploadTitles()
  syncBatchUploadPreviews(normalized)
}

function removeSelectedUploadFile(file?: UploadUserFile) {
  if (uploadLocked.value) return
  if (file === undefined) {
    clearUploadTitles()
  } else {
    delete uploadTitles[uploadTitleKey(file)]
  }
  uploadFileList.value = file === undefined
    ? []
    : uploadFileList.value.filter((item) => uploadPreviewKey(item) !== uploadPreviewKey(file))
  syncUploadTitles()
  if (uploadMode.value === 'SINGLE') {
    refreshSingleUploadPreview()
  } else {
    syncBatchUploadPreviews()
  }
}

function clearSelectedUploadFiles() {
  if (uploadLocked.value) return
  uploadFileList.value = []
  clearUploadTitles()
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
}

function validateUploadSelection() {
  if (!ensureOperationAllowed(canUpload.value)) {
    return null
  }
  const entries = selectedUploadEntries()
  if (entries.length === 0) {
    ElMessage.warning('请先选择图片文件')
    return null
  }
  if (uploadMode.value === 'SINGLE' && entries.length !== 1) {
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
  return entries
}

async function stageSelectedUploadFiles(entries: Array<{ file: File; title: string | null }>) {
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
      totalCount: entries.length,
    })
    uploadSession.value = session
    revokeAllBatchUploadPreviews()
    for (const entry of entries) {
      session = await uploadSessionItem(session.id, entry.file, entry.title, uploadAbortController.value.signal)
      const latestItem = session.items.at(-1)
      if (latestItem) {
        uploadItemFiles[latestItem.id] = entry.file
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
    uploadSession.value = await retryUploadSessionItem(uploadSession.value.id, item.id, file, item.title, uploadAbortController.value.signal)
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
  if (uploadCanFinishConfirmedDuplicates.value) {
    uploadVisible.value = false
    resetUploadDialog()
    return
  }
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
    uploadSession.value = session
    if (session.duplicateCount > 0) {
      if (session.successCount === 0) {
        ElMessage.warning('发现重复图片，未新增')
      } else {
        ElMessage.success(`已上传 ${session.successCount} 张，${session.duplicateCount} 张重复`)
        await loadImages()
      }
      return
    }
    ElMessage.success('图片已上传')
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
  const cancellableSession = uploadSession.value && !terminalUploadBatchStatuses.includes(uploadSession.value.status)
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
  clearUploadDuplicateDetails()
  uploadSession.value = null
  uploadLoading.value = false
  uploadConfirming.value = false
  uploadCancelling.value = false
  uploadAbortController.value = null
  Object.keys(uploadItemFiles).forEach((key) => delete uploadItemFiles[key])
  clearUploadTitles()
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
  if (item.status === 'DUPLICATE') return uploadDuplicateGroupMeta(item)
  return '等待处理结果'
}

function uploadItemDescription(item: UploadBatchItem) {
  if (item.errorMessage) return item.errorMessage
  if (item.status === 'FAILED') return '上传失败'
  return ''
}

function uploadDuplicateGroupMeta(item: UploadBatchItem) {
  if (item.duplicateImages.length > 0) return `系统中已有 ${item.duplicateImages.length} 张相同图片`
  if (item.duplicateSessionItems.length > 0) return `与本次上传中的 ${item.duplicateSessionItems.length} 张图片重复`
  return '图片内容重复'
}

function uploadDuplicateGroupDescription(item: UploadBatchItem) {
  if (item.duplicateImages.length > 0) return item.errorMessage || '系统中已有相同图片，确认后不会新增'
  if (item.duplicateSessionItems.length > 0) return '与本次上传中的其他图片重复，确认后不会新增'
  return item.errorMessage || '确认后不会新增'
}

function uploadDuplicateImageMeta(image: UploadDuplicateImage) {
  return `${image.originalFilename} · ${formatBytes(image.sizeBytes)}`
}

function uploadDuplicateImageDescription(image: UploadDuplicateImage) {
  if (uploadDuplicateErrors[image.id]) return uploadDuplicateErrors[image.id]
  const resolution = image.width && image.height ? `${image.width} × ${image.height}` : '未知分辨率'
  return `${resolution} · ${formatDateTime(image.createdAt)}`
}

function uploadDuplicateSessionTitle(item: UploadBatchItem['duplicateSessionItems'][number]) {
  return item.title || item.originalFilename
}

function uploadDuplicateSessionMeta(item: UploadBatchItem['duplicateSessionItems'][number]) {
  if (item.imageId) return `${item.originalFilename} · 已入库`
  return `${item.originalFilename} · ${item.status === 'STAGED' ? '待确认' : '本次重复'}`
}

async function syncUploadDuplicateDetails(session = uploadSession.value) {
  const duplicateItems = session?.items.filter((item) => item.status === 'DUPLICATE') ?? []
  const activeImageIds = new Set(duplicateItems.flatMap((item) => item.duplicateImages.map((image) => image.id)))
  pruneUploadDuplicateDetails(activeImageIds)
  for (const item of duplicateItems) {
    for (const image of item.duplicateImages) {
      void loadUploadDuplicateThumbnail(image)
    }
  }
}

async function loadUploadDuplicateThumbnail(image: UploadDuplicateImage) {
  if (uploadDuplicateLoading[image.id] || uploadDuplicateThumbnailUrls[image.id]) return
  const loadToken = uploadDuplicateLoadToken.value
  uploadDuplicateLoading[image.id] = true
  delete uploadDuplicateErrors[image.id]
  try {
    const thumbnailUrl = await imageBlobUrl(image.id, 'thumbnail')
    if (loadToken === uploadDuplicateLoadToken.value) {
      uploadDuplicateThumbnailUrls[image.id] = thumbnailUrl
    } else {
      URL.revokeObjectURL(thumbnailUrl)
    }
  } catch {
    if (loadToken === uploadDuplicateLoadToken.value) {
      uploadDuplicateErrors[image.id] = '重复图片缩略图加载失败'
    }
  } finally {
    if (loadToken === uploadDuplicateLoadToken.value) {
      uploadDuplicateLoading[image.id] = false
    }
  }
}

async function previewUploadDuplicate(image: UploadDuplicateImage) {
  let record = uploadDuplicatePreviewImages[image.id]
  if (!record) {
    const loadToken = uploadDuplicateLoadToken.value
    uploadDuplicateLoading[image.id] = true
    delete uploadDuplicateErrors[image.id]
    try {
      record = await getImage(image.id)
      if (loadToken !== uploadDuplicateLoadToken.value) return
      uploadDuplicatePreviewImages[image.id] = record
    } catch {
      if (loadToken === uploadDuplicateLoadToken.value) {
        uploadDuplicateErrors[image.id] = '重复图片详情加载失败'
      }
      ElMessage.warning('暂时无法打开已有图片')
      return
    } finally {
      if (loadToken === uploadDuplicateLoadToken.value) {
        uploadDuplicateLoading[image.id] = false
      }
    }
  }
  await showPreviewRecord(record, rows.value.findIndex((row) => row.id === record.id))
}

async function showPreviewRecord(row: ImageRecord, index = rows.value.findIndex((item) => item.id === row.id)) {
  previewLoading.value = true
  try {
    const url = await imageBlobUrl(row.id, 'preview')
    revokePreviewUrl()
    previewIndex.value = index
    detachedPreviewImage.value = index === -1 ? row : null
    previewImageId.value = row.id
    previewUrl.value = url
    previewVisible.value = true
    await refreshImageRecordQuietly(row.id)
    await loadComments(row.id)
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片预览加载失败'))
  } finally {
    previewLoading.value = false
  }
}

async function showPreviewAt(index: number) {
  const row = rows.value[index]
  if (!row) return
  await showPreviewRecord(row, index)
}

async function preview(row: ImageRecord) {
  const index = rows.value.findIndex((item) => item.id === row.id)
  await showPreviewRecord(row, index)
}

function closePreview() {
  revokePreviewUrl()
  previewIndex.value = -1
  previewImageId.value = ''
  detachedPreviewImage.value = null
  previewLoading.value = false
  comments.value = []
  commentDraft.value = ''
  replyDraft.value = ''
  replyingCommentId.value = ''
  collapsedCommentIds.value = new Set()
  commentEditingId.value = ''
  commentEditContent.value = ''
  commentPagination.page = 1
  commentPagination.total = 0
  commentPagination.commentTotal = 0
}

async function loadComments(imageId = previewImageId.value) {
  if (!imageId) return
  commentsLoading.value = true
  try {
    const page = await getImageComments(imageId, {
      page: commentPagination.page,
      size: commentPagination.size,
    })
    comments.value = page.items
    commentPagination.page = page.page
    commentPagination.size = page.size
    commentPagination.total = page.total
    commentPagination.commentTotal = page.commentTotal
    const row = rows.value.find((item) => item.id === imageId)
    if (row) row.commentCount = page.commentTotal
  } catch (error) {
    ElMessage.error(errorMessage(error, '评论加载失败'))
  } finally {
    commentsLoading.value = false
  }
}

async function submitComment() {
  const imageId = previewImageId.value
  if (!imageId) return
  if (!commentDraft.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  commentSubmitting.value = true
  try {
    await createImageComment(imageId, commentDraft.value)
    commentDraft.value = ''
    await refreshImageRecordQuietly(imageId)
    await loadComments(imageId)
  } catch (error) {
    ElMessage.error(errorMessage(error, '评论提交失败'))
  } finally {
    commentSubmitting.value = false
  }
}

function startReplyComment(comment: ImageComment) {
  if (comment.deleted) return
  replyingCommentId.value = comment.id
  replyDraft.value = ''
  commentEditingId.value = ''
}

function cancelReplyComment() {
  replyingCommentId.value = ''
  replyDraft.value = ''
}

async function submitReplyComment(comment: ImageComment) {
  const imageId = previewImageId.value
  if (!imageId || comment.deleted) return
  if (!replyDraft.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  commentSubmitting.value = true
  try {
    await createImageComment(imageId, replyDraft.value, comment.id, comment.updatedAt)
    replyDraft.value = ''
    replyingCommentId.value = ''
    await refreshImageRecordQuietly(imageId)
    await loadComments(imageId)
  } catch (error) {
    ElMessage.error(errorMessage(error, '回复提交失败'))
  } finally {
    commentSubmitting.value = false
  }
}

function toggleCommentCollapse(comment: ImageComment) {
  const next = new Set(collapsedCommentIds.value)
  if (next.has(comment.id)) {
    next.delete(comment.id)
  } else {
    next.add(comment.id)
  }
  collapsedCommentIds.value = next
}

function startEditComment(comment: ImageComment) {
  if (comment.deleted) return
  commentEditingId.value = comment.id
  commentEditContent.value = comment.content ?? ''
}

async function saveComment(comment: ImageComment) {
  if (!previewImageId.value || !commentEditContent.value.trim()) return
  try {
    await updateImageComment(previewImageId.value, comment.id, commentEditContent.value)
    commentEditingId.value = ''
    commentEditContent.value = ''
    await loadComments(previewImageId.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '评论保存失败'))
  }
}

async function removeComment(comment: ImageComment) {
  if (!previewImageId.value) return
  try {
    await ElMessageBox.confirm(
      comment.hasReplies
        ? h('div', { class: 'comment-delete-confirm-message' }, [
            h('div', '确定删除这条评论？'),
            h('div', '其下回复会保留，当前评论将显示为已删除。'),
          ])
        : '确定删除这条评论？',
      '删除评论',
      {
        confirmButtonText: '删除',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await deleteImageComment(previewImageId.value, comment.id)
    await refreshImageRecordQuietly(previewImageId.value)
    await loadComments(previewImageId.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '评论删除失败'))
  }
}

function handleCommentPageChange(page: number) {
  commentPagination.page = page
  void loadComments()
}

async function toggleFavorite(row: ImageRecord) {
  try {
    const state = row.favoritedByMe ? await unfavoriteImage(row.id) : await favoriteImage(row.id)
    applyInteractionState(state)
    if (favoriteOnly.value && !state.favoritedByMe) {
      await loadImages()
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '收藏操作失败'))
  }
}

async function toggleLike(row: ImageRecord) {
  try {
    const state = row.likedByMe ? await unlikeImage(row.id) : await likeImage(row.id)
    applyInteractionState(state)
  } catch (error) {
    ElMessage.error(errorMessage(error, '点赞操作失败'))
  }
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

function clampEditorCrop() {
  const maxWidth = imageEditor.naturalWidth
  const maxHeight = imageEditor.naturalHeight
  if (maxWidth <= 0 || maxHeight <= 0) {
    imageEditor.cropX = 0
    imageEditor.cropY = 0
    imageEditor.cropWidth = 0
    imageEditor.cropHeight = 0
    return
  }
  imageEditor.cropX = Math.max(0, Math.min(Math.round(imageEditor.cropX), Math.max(0, maxWidth - 1)))
  imageEditor.cropY = Math.max(0, Math.min(Math.round(imageEditor.cropY), Math.max(0, maxHeight - 1)))
  imageEditor.cropWidth = Math.max(1, Math.min(Math.round(imageEditor.cropWidth), maxWidth - imageEditor.cropX))
  imageEditor.cropHeight = Math.max(1, Math.min(Math.round(imageEditor.cropHeight), maxHeight - imageEditor.cropY))
  imageEditor.outputScale = Math.max(10, Math.min(Math.round(imageEditor.outputScale), 100))
}

function normalizedEditorRotation() {
  return ((imageEditor.rotation % 360) + 360) % 360
}

function getEditorCanvasPoint(event: PointerEvent | WheelEvent): EditorPoint {
  const canvas = editCanvasRef.value
  if (!canvas) return { x: 0, y: 0 }
  const rect = canvas.getBoundingClientRect()
  return {
    x: ((event.clientX - rect.left) * canvas.width) / rect.width,
    y: ((event.clientY - rect.top) * canvas.height) / rect.height,
  }
}

function getEditorFitScale() {
  const canvas = editCanvasRef.value
  if (!canvas || imageEditor.naturalWidth <= 0 || imageEditor.naturalHeight <= 0) return 1
  return Math.min(
    (canvas.width - EDITOR_VIEW_PADDING) / imageEditor.naturalWidth,
    (canvas.height - EDITOR_VIEW_PADDING) / imageEditor.naturalHeight,
  )
}

function getEditorImageScale() {
  return getEditorFitScale() * imageEditor.viewZoom
}

function getEditorImageRect() {
  const canvas = editCanvasRef.value
  const scale = getEditorImageScale()
  const width = imageEditor.naturalWidth * scale
  const height = imageEditor.naturalHeight * scale
  return {
    x: (canvas ? (canvas.width - width) / 2 : 0) + imageEditor.viewOffsetX,
    y: (canvas ? (canvas.height - height) / 2 : 0) + imageEditor.viewOffsetY,
    width,
    height,
    scale,
  }
}

function canvasToEditorImage(point: EditorPoint) {
  const rect = getEditorImageRect()
  return {
    x: Math.max(0, Math.min(imageEditor.naturalWidth, (point.x - rect.x) / rect.scale)),
    y: Math.max(0, Math.min(imageEditor.naturalHeight, (point.y - rect.y) / rect.scale)),
  }
}

function editorImageToCanvas(point: EditorPoint) {
  const rect = getEditorImageRect()
  return {
    x: rect.x + point.x * rect.scale,
    y: rect.y + point.y * rect.scale,
  }
}

function getEditorCropSnapshot(): EditorCropSnapshot {
  return {
    x: imageEditor.cropX,
    y: imageEditor.cropY,
    width: imageEditor.cropWidth,
    height: imageEditor.cropHeight,
  }
}

function cropSnapshotToCanvasRect(crop = getEditorCropSnapshot()) {
  const topLeft = editorImageToCanvas({ x: crop.x, y: crop.y })
  const bottomRight = editorImageToCanvas({ x: crop.x + crop.width, y: crop.y + crop.height })
  return {
    x: topLeft.x,
    y: topLeft.y,
    width: bottomRight.x - topLeft.x,
    height: bottomRight.y - topLeft.y,
  }
}

function fitEditorView() {
  imageEditor.viewZoom = 1
  imageEditor.viewOffsetX = 0
  imageEditor.viewOffsetY = 0
}

function cursorForEditorHandle(handle: EditorDragType | null) {
  if (!handle) return imageEditor.mode === 'crop' ? 'crosshair' : 'grab'
  if (handle === 'move') return 'move'
  if (handle === 'pan') return 'grabbing'
  if (handle === 'draw') return 'crosshair'
  if (handle === 'n' || handle === 's') return 'ns-resize'
  if (handle === 'e' || handle === 'w') return 'ew-resize'
  if (handle === 'nw' || handle === 'se') return 'nwse-resize'
  return 'nesw-resize'
}

function hitTestEditorCrop(point: EditorPoint): EditorDragType | null {
  if (!imageEditorReady.value) return null
  const rect = cropSnapshotToCanvasRect()
  const handles: Array<{ type: EditorCropHandle; x: number; y: number }> = [
    { type: 'nw', x: rect.x, y: rect.y },
    { type: 'n', x: rect.x + rect.width / 2, y: rect.y },
    { type: 'ne', x: rect.x + rect.width, y: rect.y },
    { type: 'e', x: rect.x + rect.width, y: rect.y + rect.height / 2 },
    { type: 'se', x: rect.x + rect.width, y: rect.y + rect.height },
    { type: 's', x: rect.x + rect.width / 2, y: rect.y + rect.height },
    { type: 'sw', x: rect.x, y: rect.y + rect.height },
    { type: 'w', x: rect.x, y: rect.y + rect.height / 2 },
  ]
  for (const handle of handles) {
    if (Math.abs(point.x - handle.x) <= EDITOR_HANDLE_SIZE && Math.abs(point.y - handle.y) <= EDITOR_HANDLE_SIZE) {
      return handle.type
    }
  }
  const inside = point.x >= rect.x && point.x <= rect.x + rect.width && point.y >= rect.y && point.y <= rect.y + rect.height
  return inside ? 'move' : null
}

function applyEditorCropFromEdges(left: number, top: number, right: number, bottom: number) {
  const minSize = Math.min(EDITOR_MIN_CROP_SIZE, imageEditor.naturalWidth, imageEditor.naturalHeight)
  left = Math.max(0, Math.min(left, imageEditor.naturalWidth - minSize))
  top = Math.max(0, Math.min(top, imageEditor.naturalHeight - minSize))
  right = Math.max(left + minSize, Math.min(right, imageEditor.naturalWidth))
  bottom = Math.max(top + minSize, Math.min(bottom, imageEditor.naturalHeight))
  imageEditor.cropX = Math.round(left)
  imageEditor.cropY = Math.round(top)
  imageEditor.cropWidth = Math.max(1, Math.round(right - left))
  imageEditor.cropHeight = Math.max(1, Math.round(bottom - top))
  clampEditorCrop()
}

function drawEditorCropOverlay(context: CanvasRenderingContext2D, canvas: HTMLCanvasElement) {
  const cropRect = cropSnapshotToCanvasRect()
  context.save()
  context.fillStyle = 'rgb(15 23 42 / 48%)'
  context.beginPath()
  context.rect(0, 0, canvas.width, canvas.height)
  context.rect(cropRect.x, cropRect.y, cropRect.width, cropRect.height)
  context.fill('evenodd')
  context.strokeStyle = '#2563eb'
  context.lineWidth = 2
  context.strokeRect(cropRect.x, cropRect.y, cropRect.width, cropRect.height)
  context.strokeStyle = 'rgb(255 255 255 / 75%)'
  context.lineWidth = 1
  context.setLineDash([6, 5])
  context.beginPath()
  context.moveTo(cropRect.x + cropRect.width / 3, cropRect.y)
  context.lineTo(cropRect.x + cropRect.width / 3, cropRect.y + cropRect.height)
  context.moveTo(cropRect.x + (cropRect.width * 2) / 3, cropRect.y)
  context.lineTo(cropRect.x + (cropRect.width * 2) / 3, cropRect.y + cropRect.height)
  context.moveTo(cropRect.x, cropRect.y + cropRect.height / 3)
  context.lineTo(cropRect.x + cropRect.width, cropRect.y + cropRect.height / 3)
  context.moveTo(cropRect.x, cropRect.y + (cropRect.height * 2) / 3)
  context.lineTo(cropRect.x + cropRect.width, cropRect.y + (cropRect.height * 2) / 3)
  context.stroke()
  context.setLineDash([])
  const handles = [
    [cropRect.x, cropRect.y],
    [cropRect.x + cropRect.width / 2, cropRect.y],
    [cropRect.x + cropRect.width, cropRect.y],
    [cropRect.x + cropRect.width, cropRect.y + cropRect.height / 2],
    [cropRect.x + cropRect.width, cropRect.y + cropRect.height],
    [cropRect.x + cropRect.width / 2, cropRect.y + cropRect.height],
    [cropRect.x, cropRect.y + cropRect.height],
    [cropRect.x, cropRect.y + cropRect.height / 2],
  ]
  context.fillStyle = '#ffffff'
  context.strokeStyle = '#2563eb'
  for (const [x, y] of handles) {
    context.beginPath()
    context.rect(x - 4, y - 4, 8, 8)
    context.fill()
    context.stroke()
  }
  context.restore()
}

function buildEditedCanvas() {
  const source = editSourceImage.value
  if (!source || imageEditor.cropWidth <= 0 || imageEditor.cropHeight <= 0) {
    return null
  }
  clampEditorCrop()
  const scale = imageEditor.outputScale / 100
  const cropWidth = imageEditor.cropWidth
  const cropHeight = imageEditor.cropHeight
  const scaledWidth = Math.max(1, Math.round(cropWidth * scale))
  const scaledHeight = Math.max(1, Math.round(cropHeight * scale))
  const normalizedRotation = normalizedEditorRotation()
  const rotated = normalizedRotation === 90 || normalizedRotation === 270
  const canvas = document.createElement('canvas')
  canvas.width = rotated ? scaledHeight : scaledWidth
  canvas.height = rotated ? scaledWidth : scaledHeight
  const context = canvas.getContext('2d')
  if (!context) return null
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.translate(canvas.width / 2, canvas.height / 2)
  context.rotate((normalizedRotation * Math.PI) / 180)
  context.drawImage(
    source,
    imageEditor.cropX,
    imageEditor.cropY,
    cropWidth,
    cropHeight,
    -scaledWidth / 2,
    -scaledHeight / 2,
    scaledWidth,
    scaledHeight,
  )
  return canvas
}

function drawEditorPreview() {
  const canvas = editCanvasRef.value
  if (!canvas) return
  const context = canvas.getContext('2d')
  if (!context) return
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.fillStyle = '#f8fafc'
  context.fillRect(0, 0, canvas.width, canvas.height)
  const source = editSourceImage.value
  if (!source || imageEditor.naturalWidth <= 0 || imageEditor.naturalHeight <= 0) {
    context.fillStyle = '#f1f5f9'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.fillStyle = '#64748b'
    context.textAlign = 'center'
    context.fillText('图片加载中', canvas.width / 2, canvas.height / 2)
    return
  }
  const rect = getEditorImageRect()
  context.imageSmoothingEnabled = true
  context.imageSmoothingQuality = 'high'
  context.drawImage(source, rect.x, rect.y, rect.width, rect.height)
  drawEditorCropOverlay(context, canvas)
}

function markEditorDirty() {
  imageEditor.dirty = true
  drawEditorPreview()
}

function resetEditorCrop() {
  imageEditor.cropX = 0
  imageEditor.cropY = 0
  imageEditor.cropWidth = imageEditor.naturalWidth
  imageEditor.cropHeight = imageEditor.naturalHeight
  imageEditor.rotation = 0
  imageEditor.outputScale = 100
  fitEditorView()
  imageEditor.dirty = false
  drawEditorPreview()
}

function rotateEditor(delta: number) {
  imageEditor.rotation = ((imageEditor.rotation + delta) % 360 + 360) % 360
  markEditorDirty()
}

function setEditorMode(mode: EditorMode) {
  imageEditor.mode = mode
  editorDrag.value = null
  editorHoverHandle.value = null
  drawEditorPreview()
}

function handleEditorModeChange(mode: string | number | boolean | undefined) {
  if (mode === 'crop' || mode === 'pan') {
    setEditorMode(mode)
  }
}

function handleEditorWheel(event: WheelEvent) {
  if (!imageEditorReady.value) return
  const point = getEditorCanvasPoint(event)
  const oldRect = getEditorImageRect()
  const anchor = {
    x: (point.x - oldRect.x) / oldRect.scale,
    y: (point.y - oldRect.y) / oldRect.scale,
  }
  const nextZoom = Math.max(
    EDITOR_MIN_VIEW_ZOOM,
    Math.min(EDITOR_MAX_VIEW_ZOOM, imageEditor.viewZoom * (event.deltaY < 0 ? 1.12 : 0.88)),
  )
  imageEditor.viewZoom = Number(nextZoom.toFixed(3))
  const nextRect = getEditorImageRect()
  imageEditor.viewOffsetX += point.x - (nextRect.x + anchor.x * nextRect.scale)
  imageEditor.viewOffsetY += point.y - (nextRect.y + anchor.y * nextRect.scale)
  drawEditorPreview()
}

function handleEditorPointerDown(event: PointerEvent) {
  if (!imageEditorReady.value || event.button !== 0) return
  const canvas = editCanvasRef.value
  if (!canvas) return
  canvas.setPointerCapture(event.pointerId)
  const point = getEditorCanvasPoint(event)
  const imagePoint = canvasToEditorImage(point)
  const hit = imageEditor.mode === 'crop' ? hitTestEditorCrop(point) : null
  editorDrag.value = {
    type: imageEditor.mode === 'pan' ? 'pan' : hit ?? 'draw',
    pointerId: event.pointerId,
    startCanvas: point,
    startImage: imagePoint,
    startCrop: getEditorCropSnapshot(),
    startOffsetX: imageEditor.viewOffsetX,
    startOffsetY: imageEditor.viewOffsetY,
  }
  if (editorDrag.value.type === 'draw') {
    applyEditorCropFromEdges(imagePoint.x, imagePoint.y, imagePoint.x + EDITOR_MIN_CROP_SIZE, imagePoint.y + EDITOR_MIN_CROP_SIZE)
    imageEditor.dirty = true
    drawEditorPreview()
  }
}

function handleEditorPointerMove(event: PointerEvent) {
  if (!imageEditorReady.value) return
  const point = getEditorCanvasPoint(event)
  const drag = editorDrag.value
  if (!drag) {
    editorHoverHandle.value = imageEditor.mode === 'crop' ? hitTestEditorCrop(point) : null
    return
  }
  if (drag.pointerId !== event.pointerId) return
  if (drag.type === 'pan') {
    imageEditor.viewOffsetX = drag.startOffsetX + point.x - drag.startCanvas.x
    imageEditor.viewOffsetY = drag.startOffsetY + point.y - drag.startCanvas.y
    drawEditorPreview()
    return
  }
  const scale = getEditorImageScale()
  const currentImage = canvasToEditorImage(point)
  if (drag.type === 'draw') {
    applyEditorCropFromEdges(
      Math.min(drag.startImage.x, currentImage.x),
      Math.min(drag.startImage.y, currentImage.y),
      Math.max(drag.startImage.x, currentImage.x),
      Math.max(drag.startImage.y, currentImage.y),
    )
  } else if (drag.type === 'move') {
    const deltaX = (point.x - drag.startCanvas.x) / scale
    const deltaY = (point.y - drag.startCanvas.y) / scale
    imageEditor.cropX = Math.round(Math.max(0, Math.min(imageEditor.naturalWidth - drag.startCrop.width, drag.startCrop.x + deltaX)))
    imageEditor.cropY = Math.round(Math.max(0, Math.min(imageEditor.naturalHeight - drag.startCrop.height, drag.startCrop.y + deltaY)))
  } else {
    let left = drag.startCrop.x
    let top = drag.startCrop.y
    let right = drag.startCrop.x + drag.startCrop.width
    let bottom = drag.startCrop.y + drag.startCrop.height
    if (drag.type.includes('w')) left = currentImage.x
    if (drag.type.includes('e')) right = currentImage.x
    if (drag.type.includes('n')) top = currentImage.y
    if (drag.type.includes('s')) bottom = currentImage.y
    if (right < left) [left, right] = [right, left]
    if (bottom < top) [top, bottom] = [bottom, top]
    applyEditorCropFromEdges(left, top, right, bottom)
  }
  imageEditor.dirty = true
  drawEditorPreview()
}

function handleEditorPointerUp(event: PointerEvent) {
  const drag = editorDrag.value
  if (!drag || drag.pointerId !== event.pointerId) return
  const canvas = editCanvasRef.value
  if (canvas?.hasPointerCapture(event.pointerId)) {
    canvas.releasePointerCapture(event.pointerId)
  }
  editorDrag.value = null
  editorHoverHandle.value = imageEditor.mode === 'crop' ? hitTestEditorCrop(getEditorCanvasPoint(event)) : null
}

function handleEditorPointerLeave() {
  if (!editorDrag.value) {
    editorHoverHandle.value = null
  }
}

function editedImageFileName(row: ImageRecord) {
  const filename = row.originalFilename || row.title || 'image'
  const dot = filename.lastIndexOf('.')
  const base = dot > 0 ? filename.slice(0, dot) : filename
  return `${base || 'image'}-edited.png`
}

function canvasToBlob(canvas: HTMLCanvasElement) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob)
      } else {
        reject(new Error('图片编辑结果生成失败'))
      }
    }, 'image/png')
  })
}

async function loadEditSource(row: ImageRecord) {
  editImageLoading.value = true
  resetImageEditor()
  try {
    const url = await imageEditSourceUrl(row.id)
    editSourceUrl.value = url
    const image = new Image()
    image.src = url
    await image.decode()
    editSourceImage.value = image
    imageEditor.naturalWidth = image.naturalWidth
    imageEditor.naturalHeight = image.naturalHeight
    resetEditorCrop()
  } catch (error) {
    if (isAxiosError(error) && error.response?.status === 404) {
      ElMessage.error('后端编辑源接口不可用，请重启后端服务后重试')
    } else if (isAxiosError(error) && error.response?.status === 403) {
      ElMessage.error('当前用户没有图片编辑权限')
    } else {
      ElMessage.error(errorMessage(error, '图片编辑源加载失败'))
    }
  } finally {
    editImageLoading.value = false
  }
}

async function loadImageVersions(id: string) {
  const loadToken = ++imageVersionLoadToken.value
  imageVersionsLoading.value = true
  try {
    const versions = await getImageVersions(id)
    if (loadToken !== imageVersionLoadToken.value) return
    imageVersions.value = versions
    pruneVersionThumbnailUrls(new Set(versions.map((version) => version.id)))
    await Promise.allSettled(versions.map((version) => loadVersionThumbnail(id, version, loadToken)))
  } catch (error) {
    if (loadToken !== imageVersionLoadToken.value) return
    if (isAxiosError(error) && error.response?.status === 404) {
      ElMessage.error('后端版本接口不可用，请重启后端服务后重试')
    } else if (isAxiosError(error) && error.response?.status === 403) {
      ElMessage.error('当前用户没有图片版本查看权限')
    } else {
      ElMessage.error(errorMessage(error, '图片版本加载失败'))
    }
  } finally {
    if (loadToken === imageVersionLoadToken.value) {
      imageVersionsLoading.value = false
    }
  }
}

async function loadVersionThumbnail(imageId: string, version: ImageVersionRecord, loadToken: number) {
  if (versionThumbnailUrls[version.id] || versionThumbnailFailed[version.id]) return
  try {
    const url = await imageVersionBlobUrl(imageId, version.id, 'thumbnail')
    if (loadToken !== imageVersionLoadToken.value || !imageVersions.value.some((item) => item.id === version.id)) {
      URL.revokeObjectURL(url)
      return
    }
    versionThumbnailUrls[version.id] = url
  } catch {
    if (loadToken === imageVersionLoadToken.value) {
      versionThumbnailFailed[version.id] = true
    }
  }
}

function versionOperationLabel(operationType: string) {
  if (operationType === 'UPLOAD') return '上传'
  if (operationType === 'EDIT') return '编辑'
  return operationType || '-'
}

function versionDimensions(version: ImageVersionRecord) {
  if (version.width && version.height) return `${version.width} × ${version.height}`
  return '未知尺寸'
}

async function openVersionPreview(version: ImageVersionRecord) {
  if (!editing.value) return
  const requestToken = ++versionPreviewRequestToken.value
  revokeVersionPreviewUrl()
  versionPreviewVersion.value = version
  versionPreviewVisible.value = true
  versionPreviewLoading.value = true
  try {
    const url = await imageVersionBlobUrl(editing.value.id, version.id, 'preview')
    if (requestToken !== versionPreviewRequestToken.value || versionPreviewVersion.value?.id !== version.id || !versionPreviewVisible.value) {
      URL.revokeObjectURL(url)
      return
    }
    versionPreviewUrl.value = url
  } catch (error) {
    if (requestToken === versionPreviewRequestToken.value) {
      versionPreviewVisible.value = false
      ElMessage.error(errorMessage(error, '图片版本预览加载失败'))
    }
  } finally {
    if (requestToken === versionPreviewRequestToken.value) {
      versionPreviewLoading.value = false
    }
  }
}

async function restoreVersion(version: ImageVersionRecord) {
  if (!editing.value || !ensureOperationAllowed(canEdit.value) || version.current) return
  try {
    await ElMessageBox.confirm(`确定恢复到版本 V${version.versionNo}？当前版本会保留在历史记录中。`, '恢复图片版本', {
      type: 'warning',
      confirmButtonText: '确认恢复',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  imageVersionActionId.value = version.id
  try {
    const saved = await restoreImageVersion(editing.value.id, version.id)
    editing.value = await refreshImageRecord(saved.id)
    await refreshThumbnailUrl(saved.id, `${editing.value.updatedAt || ''}-${Date.now()}`)
    if (previewImageId.value === saved.id) {
      await refreshOpenPreviewImage(saved.id, editing.value, true)
    }
    if (versionPreviewVersion.value?.id === version.id) {
      closeVersionPreview()
    }
    await nextTick()
    await loadEditSource(saved)
    await loadImageVersions(saved.id)
    ElMessage.success('图片版本已恢复')
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片版本恢复失败'))
  } finally {
    imageVersionActionId.value = ''
  }
}

async function removeVersion(version: ImageVersionRecord) {
  if (!editing.value || !ensureOperationAllowed(canDelete.value) || version.current) return
  try {
    await ElMessageBox.confirm(`确定删除版本 V${version.versionNo}？该版本对象会从存储中清理。`, '删除图片版本', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  imageVersionActionId.value = version.id
  try {
    await deleteImageVersion(editing.value.id, version.id)
    if (versionPreviewVersion.value?.id === version.id) {
      closeVersionPreview()
    }
    revokeVersionThumbnailUrl(version.id)
    await loadImageVersions(editing.value.id)
    ElMessage.success('图片版本已删除')
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片版本删除失败'))
  } finally {
    imageVersionActionId.value = ''
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
  await nextTick()
  await loadEditSource(row)
  await loadImageVersions(row.id)
}

async function onEditCategoryChange() {
  if (tags.value.length === 0) {
    tags.value = await getTags()
  }
}

async function saveEdit() {
  if (!editing.value) return
  if (!ensureOperationAllowed(canEdit.value)) return
  if (editSaving.value) return
  try {
    await ElMessageBox.confirm(
      imageEditor.dirty
        ? '本次保存会将图像编辑结果写入新版本，原始对象会继续保留。确定保存吗？'
        : '确定保存当前图片信息吗？',
      imageEditor.dirty ? '保存图片新版本' : '保存图片信息',
      {
        type: imageEditor.dirty ? 'warning' : 'info',
        confirmButtonText: '确认保存',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  editSaving.value = true
  try {
    let saved = await updateImage(editing.value.id, {
      title: editForm.title,
      status: editForm.status,
      categoryId: editForm.categoryId || null,
      tagIds: editForm.tagIds,
    })
    if (imageEditor.dirty) {
      const editedCanvas = buildEditedCanvas()
      if (!editedCanvas) {
        throw new Error('图片编辑结果生成失败')
      }
      const blob = await canvasToBlob(editedCanvas)
      const file = new File([blob], editedImageFileName(saved), { type: 'image/png' })
      saved = await editImageContent(saved.id, file, JSON.stringify({
        crop: {
          x: imageEditor.cropX,
          y: imageEditor.cropY,
          width: imageEditor.cropWidth,
          height: imageEditor.cropHeight,
        },
        rotation: imageEditor.rotation,
        scale: imageEditor.outputScale,
      }))
      await refreshThumbnailUrl(saved.id, `${saved.updatedAt || ''}-${Date.now()}`)
    }
    await refreshOpenPreviewImage(saved.id, saved, imageEditor.dirty)
    ElMessage.success('图片信息已保存')
    editing.value = null
    editVisible.value = false
    resetImageEditor()
    await loadImages()
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片信息保存失败'))
  } finally {
    editSaving.value = false
  }
}

function closeEditDialog() {
  editVisible.value = false
  editing.value = null
  resetImageEditor()
}

async function remove(row: ImageRecord): Promise<boolean> {
  if (!ensureOperationAllowed(canDelete.value)) return false
  try {
    await ElMessageBox.confirm(`确定停用图片“${row.title}”？`, '停用图片', { type: 'warning' })
  } catch {
    return false
  }
  try {
    await deleteImage(row.id)
    ElMessage.success('图片已停用')
    await loadImages()
    return true
  } catch (error) {
    ElMessage.error(errorMessage(error, '图片停用失败'))
    return false
  }
}

async function removeFromPreview(row: ImageRecord) {
  if (await remove(row)) {
    previewVisible.value = false
    closePreview()
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
    await refreshImageRecordQuietly(row.id)
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
    await loadImages()
    ElMessage.success('批量下载已开始')
  } catch (error) {
    ElMessage.error(errorMessage(error, '批量下载失败'))
  } finally {
    batchDownloadLoading.value = false
  }
}

function isUploadConfirmDisabled() {
  return uploadCancelling.value
    || uploadConfirming.value
    || uploadLoading.value
    || Boolean(uploadSession.value && !uploadCanConfirmSession.value && !uploadCanFinishConfirmedDuplicates.value)
}

useDialogEnterSubmit(uploadVisible, confirmUploadDialog, { disabled: isUploadConfirmDisabled })
useDialogEnterSubmit(editVisible, saveEdit, { disabled: () => editSaving.value })

watch(uploadSession, (session) => {
  void syncUploadDuplicateDetails(session)
})

onMounted(async () => {
  window.addEventListener('keydown', handlePreviewKeydown)
  await loadCategories()
  await loadImages()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handlePreviewKeydown)
  revokeAllThumbnailUrls()
  revokePreviewUrl()
  resetImageEditor()
  revokeSingleUploadPreview()
  revokeAllBatchUploadPreviews()
  clearUploadDuplicateDetails()
})
</script>

<template>
  <section class="workspace-page">
    <div class="surface surface-pad image-library-panel">
      <div class="image-scope-row">
        <el-radio-group v-model="imageScope" @change="handleImageScopeChange">
          <el-radio-button value="ACTIVE">启用</el-radio-button>
          <el-radio-button value="DELETED">已停用</el-radio-button>
        </el-radio-group>
        <el-button v-if="canUpload" type="primary" :icon="UploadFilled" @click="openUploadDialog">上传图片</el-button>
      </div>

      <div class="toolbar-row image-toolbar">
        <div class="image-filter-group">
          <el-input v-model="keyword" placeholder="标题" :prefix-icon="Search" clearable @keyup.enter="applyFilters" />
          <el-select v-model="selectedCategoryId" placeholder="分类" clearable @change="loadTagsForFilter">
            <el-option v-for="category in categoryOptions" :key="category.id" :label="category.name" :value="category.id" />
          </el-select>
          <el-select v-model="selectedTagId" placeholder="标签" clearable>
            <el-option-group v-for="group in tagOptionGroups" :key="group.key" :label="group.label">
              <el-option v-for="tag in group.tags" :key="tag.id" :label="tag.name" :value="tag.id" />
            </el-option-group>
          </el-select>
          <el-checkbox v-model="favoriteOnly" :disabled="showingDeletedImages">只看我的收藏</el-checkbox>
          <div class="filter-actions">
            <el-button @click="applyFilters">筛选</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </div>
        <div class="image-toolbar-actions">
          <div class="image-sort-controls">
            <el-select v-model="sortBy" class="image-sort-select" placeholder="排序" @change="handleSortChange">
              <el-option v-for="option in sortOptions" :key="option.value" :label="option.label" :value="option.value" />
            </el-select>
            <el-radio-group v-model="sortDirection" class="sort-direction-toggle" @change="handleSortChange">
              <el-radio-button value="desc">降序</el-radio-button>
              <el-radio-button value="asc">升序</el-radio-button>
            </el-radio-group>
          </div>
          <el-radio-group v-model="displayMode" class="display-mode-toggle" @change="handleDisplayModeChange">
            <el-radio-button value="grid">网格</el-radio-button>
            <el-radio-button value="list">列表</el-radio-button>
          </el-radio-group>
        </div>
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
            type="warning"
            plain
            :icon="Warning"
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

      <div class="image-results-scroll-region">
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
                </div>
                <span class="image-grid-file" :title="row.originalFilename">{{ row.originalFilename }} · {{ formatBytes(row.sizeBytes) }}</span>
                <div
                  class="image-grid-counts"
                  :aria-label="`浏览 ${row.viewCount}，下载 ${row.downloadCount}，收藏 ${row.favoriteCount}，点赞 ${row.likeCount}`"
                >
                  <span :title="`浏览 ${row.viewCount}`"><span aria-hidden="true">👁</span>{{ row.viewCount }}</span>
                  <span :title="`下载 ${row.downloadCount}`"><span aria-hidden="true">⬇️</span>{{ row.downloadCount }}</span>
                  <span :title="`收藏 ${row.favoriteCount}`"><span aria-hidden="true">⭐</span>{{ row.favoriteCount }}</span>
                  <span :title="`点赞 ${row.likeCount}`"><span aria-hidden="true">👍</span>{{ row.likeCount }}</span>
                </div>
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
                            +{{ hiddenGridTagCount(row) }}
                          </el-tag>
                        </template>
                        <div class="image-grid-hidden-tags-panel" :aria-label="`剩余 ${hiddenGridTagCount(row)} 个标签`">
                          <div class="image-grid-hidden-tags-title">剩余 {{ hiddenGridTagCount(row) }} 个标签</div>
                          <div class="image-grid-hidden-tags">
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
                        </div>
                      </el-popover>
                      <span v-if="row.tags.length === 0" class="taxonomy-empty">暂无标签</span>
                    </div>
                  </div>
                </div>
              </div>
              <div v-if="!showingDeletedImages || canDelete" class="image-grid-actions">
                <template v-if="!showingDeletedImages">
                  <div class="image-action-group" aria-label="图片操作">
                    <div class="image-action-cluster">
                      <el-tooltip v-if="canView" :content="row.favoritedByMe ? '取消收藏' : '收藏'" placement="top">
                        <el-button
                          class="image-action-button image-action-emoji-button"
                          :class="{ 'is-active': row.favoritedByMe }"
                          :aria-label="row.favoritedByMe ? '取消收藏' : '收藏'"
                          circle
                          @click="toggleFavorite(row)"
                        >
                          <span aria-hidden="true">⭐</span>
                        </el-button>
                      </el-tooltip>
                      <el-tooltip v-if="canView" :content="row.likedByMe ? '取消点赞' : '点赞'" placement="top">
                        <el-button
                          class="image-action-button image-action-emoji-button"
                          :class="{ 'is-active': row.likedByMe }"
                          :aria-label="row.likedByMe ? '取消点赞' : '点赞'"
                          circle
                          @click="toggleLike(row)"
                        >
                          <span aria-hidden="true">👍</span>
                        </el-button>
                      </el-tooltip>
                    </div>
                    <div class="image-action-cluster">
                      <el-tooltip v-if="canEdit" content="编辑" placement="top">
                        <el-button class="image-action-button" :icon="EditPen" aria-label="编辑" circle @click="openEdit(row)" />
                      </el-tooltip>
                      <el-tooltip v-if="canView" content="下载" placement="top">
                        <el-button class="image-action-button" :icon="Download" aria-label="下载" circle @click="download(row)" />
                      </el-tooltip>
                    </div>
                    <div class="image-action-cluster image-action-cluster-single">
                      <el-tooltip v-if="canDelete" content="停用" placement="top">
                        <el-button
                          class="image-action-button is-warning"
                          type="warning"
                          plain
                          :icon="Warning"
                          aria-label="停用"
                          circle
                          @click="remove(row)"
                        />
                      </el-tooltip>
                    </div>
                  </div>
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
          <el-table-column label="数据" width="130">
            <template #default="{ row }">
              <div class="image-counts-cell">
                <span>浏览 {{ row.viewCount }}</span>
                <span>下载 {{ row.downloadCount }}</span>
                <span>收藏 {{ row.favoriteCount }}</span>
                <span>点赞 {{ row.likeCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column v-if="!showingDeletedImages || canDelete" label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <template v-if="!showingDeletedImages">
                <div class="image-action-group" aria-label="图片操作">
                  <div class="image-action-cluster">
                    <el-tooltip v-if="canView" :content="row.favoritedByMe ? '取消收藏' : '收藏'" placement="top">
                      <el-button
                        class="image-action-button image-action-emoji-button"
                        :class="{ 'is-active': row.favoritedByMe }"
                        :aria-label="row.favoritedByMe ? '取消收藏' : '收藏'"
                        circle
                        @click="toggleFavorite(row)"
                      >
                        <span aria-hidden="true">⭐</span>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="canView" :content="row.likedByMe ? '取消点赞' : '点赞'" placement="top">
                      <el-button
                        class="image-action-button image-action-emoji-button"
                        :class="{ 'is-active': row.likedByMe }"
                        :aria-label="row.likedByMe ? '取消点赞' : '点赞'"
                        circle
                        @click="toggleLike(row)"
                      >
                        <span aria-hidden="true">👍</span>
                      </el-button>
                    </el-tooltip>
                  </div>
                  <div class="image-action-cluster">
                    <el-tooltip v-if="canEdit" content="编辑" placement="top">
                      <el-button class="image-action-button" :icon="EditPen" aria-label="编辑" circle @click="openEdit(row)" />
                    </el-tooltip>
                    <el-tooltip v-if="canView" content="下载" placement="top">
                      <el-button class="image-action-button" :icon="Download" aria-label="下载" circle @click="download(row)" />
                    </el-tooltip>
                  </div>
                  <div class="image-action-cluster image-action-cluster-single">
                    <el-tooltip v-if="canDelete" content="停用" placement="top">
                      <el-button
                        class="image-action-button is-warning"
                        type="warning"
                        plain
                        :icon="Warning"
                        aria-label="停用"
                        circle
                        @click="remove(row)"
                      />
                    </el-tooltip>
                  </div>
                </div>
              </template>
              <template v-else>
                <el-button v-if="canDelete" link type="primary" @click="restore(row)">恢复</el-button>
                <el-button v-if="canDelete" link type="danger" @click="purge(row)">彻底删除</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </div>

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

    <el-dialog v-model="previewVisible" :title="currentPreview?.title ?? '图片预览'" width="72%" top="5vh" @closed="closePreview">
      <div class="preview-shell" v-loading="previewLoading">
        <el-button
          class="preview-nav"
          :disabled="!canPreviewPrevious || previewLoading"
          aria-label="上一张"
          @click="previewPrevious"
        >
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <img v-if="previewUrl" class="preview-image" :src="previewUrl" alt="图片预览" />
        <el-empty v-else description="暂无预览" />
        <el-button
          class="preview-nav"
          :disabled="!canPreviewNext || previewLoading"
          aria-label="下一张"
          @click="previewNext"
        >
          <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
      <div v-if="currentPreview" class="preview-meta">
        <span>{{ currentPreview.originalFilename }}</span>
        <span>{{ previewPositionText }}</span>
      </div>
      <div v-if="currentPreview" class="preview-details">
        <div class="preview-detail-row">
          <span class="preview-detail-label">文件名</span>
          <span class="preview-detail-text" :title="currentPreview.originalFilename">{{ currentPreview.originalFilename }}</span>
        </div>
        <div class="preview-detail-row">
          <span class="preview-detail-label">分类</span>
          <span class="preview-detail-text">{{ currentPreview.category?.name ?? '未分类' }}</span>
        </div>
        <div class="preview-detail-row">
          <span class="preview-detail-label">标签</span>
          <div class="preview-detail-tags">
            <template v-for="group in currentPreviewTagGroups" :key="group.key">
              <span class="preview-detail-tag-group">{{ group.label }}</span>
              <el-tag v-for="tag in group.tags" :key="tag.id" size="small" effect="light">{{ tag.name }}</el-tag>
            </template>
            <span v-if="currentPreview.tags.length === 0" class="taxonomy-empty">暂无标签</span>
          </div>
        </div>
        <div class="preview-detail-grid">
          <div class="preview-detail-row">
            <span class="preview-detail-label">尺寸</span>
            <span class="preview-detail-text">{{ formatDimensions(currentPreview) }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">大小</span>
            <span class="preview-detail-text">{{ formatBytes(currentPreview.sizeBytes) }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">类型</span>
            <span class="preview-detail-text">{{ currentPreview.mimeType }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">上传</span>
            <span class="preview-detail-text">{{ formatDateTime(currentPreview.createdAt) }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">访问</span>
            <span class="preview-detail-text">{{ currentPreview.viewCount }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">下载</span>
            <span class="preview-detail-text">{{ currentPreview.downloadCount }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">收藏</span>
            <span class="preview-detail-text">{{ currentPreview.favoriteCount }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">点赞</span>
            <span class="preview-detail-text">{{ currentPreview.likeCount }}</span>
          </div>
          <div class="preview-detail-row">
            <span class="preview-detail-label">评论</span>
            <span class="preview-detail-text">{{ currentPreview.commentCount }}</span>
          </div>
        </div>
      </div>
      <div v-if="currentPreview" class="preview-interactions">
        <div v-if="currentPreviewActionable" class="preview-interaction-actions">
          <el-button v-if="canView" type="primary" plain @click="toggleFavorite(currentPreview)">
            {{ currentPreview.favoritedByMe ? '取消收藏' : '收藏' }}
          </el-button>
          <el-button v-if="canView" type="primary" plain @click="toggleLike(currentPreview)">
            {{ currentPreview.likedByMe ? '取消点赞' : '点赞' }}
          </el-button>
          <el-button v-if="canEdit" :icon="EditPen" plain @click="openEdit(currentPreview)">
            编辑
          </el-button>
          <el-button v-if="canView" :icon="Download" plain @click="download(currentPreview)">
            下载
          </el-button>
          <el-button v-if="canDelete" type="warning" plain :icon="Warning" @click="removeFromPreview(currentPreview)">
            停用
          </el-button>
        </div>
        <section class="preview-comments" v-loading="commentsLoading">
          <div class="preview-comments-head">
            <strong>评论</strong>
            <span>{{ commentPagination.commentTotal }} 条</span>
          </div>
          <div class="comment-editor">
            <el-input
              v-model="commentDraft"
              type="textarea"
              :rows="3"
              maxlength="1000"
              show-word-limit
              placeholder="写下评论"
            />
            <el-button type="primary" :loading="commentSubmitting" @click="submitComment">发布评论</el-button>
          </div>
          <div class="comment-list">
            <CommentThreadNode
              v-for="comment in comments"
              :key="comment.id"
              v-model:edit-content="commentEditContent"
              v-model:reply-draft="replyDraft"
              :comment="comment"
              :editing-id="commentEditingId"
              :can-manage-interactions="canManageInteractions"
              :reply-target-id="replyingCommentId"
              :reply-submitting="commentSubmitting"
              :collapsed-ids="[...collapsedCommentIds]"
              @reply="startReplyComment"
              @edit="startEditComment"
              @save="saveComment"
              @cancel="commentEditingId = ''"
              @delete="removeComment"
              @submit-reply="submitReplyComment"
              @cancel-reply="cancelReplyComment"
              @toggle-collapse="toggleCommentCollapse"
            />
            <el-empty v-if="comments.length === 0" description="暂无评论" />
          </div>
          <el-pagination
            v-if="commentPagination.total > commentPagination.size"
            v-model:current-page="commentPagination.page"
            :page-size="commentPagination.size"
            :total="commentPagination.total"
            background
            small
            layout="prev, pager, next"
            @current-change="handleCommentPageChange"
          />
        </section>
      </div>
    </el-dialog>

    <el-dialog v-if="canUpload" v-model="uploadVisible" title="上传图片" width="720px" :before-close="handleUploadBeforeClose">
      <el-form label-width="88px">
        <el-form-item label="上传模式">
          <el-radio-group v-model="uploadMode" :disabled="uploadLocked" @change="handleUploadModeChange">
            <el-radio-button value="SINGLE">单张</el-radio-button>
            <el-radio-button value="BATCH">批量</el-radio-button>
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
                <div class="single-upload-title-field">
                  <span class="single-upload-title-label">图片标题</span>
                  <el-input
                    v-model="uploadTitles[uploadTitleKey(selectedSingleUploadFile)]"
                    class="single-upload-title"
                    clearable
                    maxlength="255"
                    aria-label="图片标题"
                    :disabled="uploadLocked"
                    :placeholder="uploadTitlePlaceholder(selectedSingleUploadFile)"
                  />
                </div>
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
            <div v-if="!uploadSession && uploadFileList.length" class="batch-upload-hint">
              可为每张图片填写标题，不填则使用文件名（不含后缀）
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
                  <el-input
                    v-model="uploadTitles[uploadTitleKey(file)]"
                    class="batch-title-input"
                    clearable
                    maxlength="255"
                    size="small"
                    aria-label="图片标题"
                    :disabled="uploadLocked"
                    :placeholder="uploadTitlePlaceholder(file, true)"
                  />
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
            <el-option-group v-for="group in uploadTagOptionGroups" :key="group.key" :label="group.label">
              <el-option v-for="tag in group.tags" :key="tag.id" :label="tag.name" :value="tag.id" />
            </el-option-group>
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
      <div v-if="uploadSession && uploadDuplicateItems.length" class="upload-duplicate-list">
        <div class="upload-result-head">重复图片</div>
        <div v-for="item in uploadDuplicateItems" :key="item.id" class="upload-duplicate-group">
          <div class="upload-duplicate-group-head">
            <div class="upload-result-title">
              <strong>{{ item.originalFilename }}</strong>
              <el-tag type="warning" effect="light">重复</el-tag>
            </div>
            <span>{{ uploadDuplicateGroupMeta(item) }}</span>
            <p>{{ uploadDuplicateGroupDescription(item) }}</p>
          </div>
          <div v-if="item.duplicateImages.length" class="upload-duplicate-targets">
            <div v-for="image in item.duplicateImages" :key="image.id" class="upload-result-row upload-duplicate-row">
              <div class="upload-result-thumb">
                <img v-if="uploadDuplicateThumbnailUrls[image.id]" :src="uploadDuplicateThumbnailUrls[image.id]" alt="" />
                <span v-else>重复</span>
              </div>
              <div class="upload-result-main">
                <div class="upload-result-title">
                  <strong>{{ image.title }}</strong>
                  <el-tag type="info" effect="light">{{ image.status === 'ACTIVE' ? '启用' : '已停用' }}</el-tag>
                </div>
                <span>{{ uploadDuplicateImageMeta(image) }}</span>
                <p>{{ uploadDuplicateImageDescription(image) }}</p>
              </div>
              <div class="upload-result-actions">
                <el-button
                  link
                  type="primary"
                  :loading="uploadDuplicateLoading[image.id]"
                  @click="previewUploadDuplicate(image)"
                >
                  查看
                </el-button>
              </div>
            </div>
          </div>
          <div v-else-if="item.duplicateSessionItems.length" class="upload-duplicate-targets">
            <div v-for="sessionItem in item.duplicateSessionItems" :key="sessionItem.id" class="upload-result-row upload-duplicate-row">
              <div class="upload-result-thumb">
                <span>本次</span>
              </div>
              <div class="upload-result-main">
                <div class="upload-result-title">
                  <strong>{{ uploadDuplicateSessionTitle(sessionItem) }}</strong>
                  <el-tag type="info" effect="light">本次上传</el-tag>
                </div>
                <span>{{ uploadDuplicateSessionMeta(sessionItem) }}</span>
                <p>确认后只保留其中一张为新图片</p>
              </div>
            </div>
          </div>
          <div v-else class="upload-result-row upload-duplicate-row">
            <div class="upload-result-thumb">
              <span>重复</span>
            </div>
            <div class="upload-result-main">
              <div class="upload-result-title">
                <strong>{{ item.originalFilename }}</strong>
                <el-tag type="warning" effect="light">重复</el-tag>
              </div>
              <span>图片内容重复</span>
              <p>{{ item.errorMessage || '确认后不会新增' }}</p>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button :loading="uploadCancelling" @click="cancelUploadDialog()">{{ uploadCanFinishConfirmedDuplicates ? '关闭' : '取消' }}</el-button>
        <el-button type="primary" :loading="uploadConfirming || uploadLoading" :disabled="isUploadConfirmDisabled()" @click="confirmUploadDialog">
          {{ uploadPrimaryButtonText }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-if="canEdit" v-model="editVisible" title="编辑图片信息" width="920px" @closed="resetImageEditor">
      <div class="edit-dialog-layout">
        <div class="image-editor-panel" v-loading="editImageLoading">
          <div class="image-editor-head">
            <div>
              <h3>图像编辑</h3>
              <p>滚轮缩放视图，移动模式拖动画布；裁剪模式拖出或调整选区。</p>
            </div>
            <el-button :icon="RefreshLeft" :disabled="!imageEditorReady" @click="resetEditorCrop">重置</el-button>
          </div>
          <canvas
            ref="editCanvasRef"
            class="image-edit-canvas"
            width="620"
            height="360"
            :style="{ cursor: imageEditorCursor }"
            @wheel.prevent="handleEditorWheel"
            @pointerdown="handleEditorPointerDown"
            @pointermove="handleEditorPointerMove"
            @pointerup="handleEditorPointerUp"
            @pointercancel="handleEditorPointerUp"
            @pointerleave="handleEditorPointerLeave"
          />
          <div class="image-editor-tools">
            <el-radio-group v-model="imageEditor.mode" :disabled="!imageEditorReady" @change="handleEditorModeChange">
              <el-radio-button value="crop">裁剪</el-radio-button>
              <el-radio-button value="pan">移动</el-radio-button>
            </el-radio-group>
            <el-button-group>
              <el-button :icon="RefreshLeft" :disabled="!imageEditorReady" @click="rotateEditor(-90)">左转</el-button>
              <el-button :icon="RefreshRight" :disabled="!imageEditorReady" @click="rotateEditor(90)">右转</el-button>
            </el-button-group>
            <div class="image-editor-scale">
              <el-icon><ZoomIn /></el-icon>
              <span class="image-editor-scale-label">输出比例</span>
              <el-slider
                v-model="imageEditor.outputScale"
                :min="10"
                :max="100"
                :step="5"
                :disabled="!imageEditorReady"
                @input="markEditorDirty"
              />
              <span class="image-editor-scale-value">{{ imageEditor.outputScale }}%</span>
            </div>
          </div>
          <div class="image-editor-info-grid">
            <div>
              <span>原图尺寸</span>
              <strong>{{ editorOriginalSizeText }}</strong>
            </div>
            <div>
              <span>裁剪选区</span>
              <strong>{{ editorCropSizeText }}</strong>
            </div>
            <div>
              <span>选区起点</span>
              <strong>{{ editorCropOriginText }}</strong>
            </div>
            <div>
              <span>输出尺寸</span>
              <strong>{{ editorOutputSizeText }}</strong>
            </div>
          </div>
          <div class="image-editor-status">
            <el-icon><Crop /></el-icon>
            <span>视图 {{ Math.round(imageEditor.viewZoom * 100) }}%</span>
            <span v-if="imageEditor.dirty">待保存为新版本</span>
            <span v-else>未修改图像</span>
          </div>
        </div>

        <div class="image-edit-side">
          <el-form class="image-meta-form" label-width="72px">
            <div class="image-meta-form-head">
              <el-icon><EditPen /></el-icon>
              <span>元信息</span>
            </div>
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
                <el-option-group v-for="group in tagOptionGroups" :key="group.key" :label="group.label">
                  <el-option v-for="tag in group.tags" :key="tag.id" :label="tag.name" :value="tag.id" />
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-form>

          <div class="image-version-panel" v-loading="imageVersionsLoading">
            <div class="image-version-head">
              <strong>版本记录</strong>
              <span v-if="currentVersion">当前 V{{ currentVersion.versionNo }}</span>
            </div>
            <el-empty v-if="!imageVersionsLoading && imageVersions.length === 0" description="暂无版本" :image-size="48" />
            <div v-else class="image-version-list">
              <div v-for="version in imageVersions" :key="version.id" class="image-version-item" :class="{ 'is-current': version.current }">
                <button class="image-version-thumb" type="button" :aria-label="`预览版本 V${version.versionNo}`" @click="openVersionPreview(version)">
                  <img v-if="versionThumbnailUrls[version.id]" :src="versionThumbnailUrls[version.id]" alt="" />
                  <span v-else>{{ versionThumbnailFailed[version.id] ? '不可预览' : '加载中' }}</span>
                </button>
                <div class="image-version-main">
                  <div class="image-version-title">
                    <strong>V{{ version.versionNo }}</strong>
                    <el-tag v-if="version.current" size="small" type="success" effect="light">当前</el-tag>
                    <el-tag v-else size="small" effect="plain">{{ versionOperationLabel(version.operationType) }}</el-tag>
                  </div>
                  <span>{{ versionDimensions(version) }} · {{ formatBytes(version.sizeBytes) }}</span>
                  <span>{{ formatDateTime(version.createdAt) }}</span>
                </div>
                <div class="image-version-actions">
                  <el-button link type="primary" :loading="versionPreviewLoading && versionPreviewVersion?.id === version.id" @click="openVersionPreview(version)">预览</el-button>
                  <el-button link type="primary" :disabled="version.current || !canEdit" :loading="imageVersionActionId === version.id" @click="restoreVersion(version)">恢复</el-button>
                  <el-button link type="danger" :disabled="version.current || !canDelete" :loading="imageVersionActionId === version.id" @click="removeVersion(version)">删除</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="editSaving" @click="closeEditDialog">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="saveEdit">
          {{ imageEditor.dirty ? '保存信息与新版本' : '保存信息' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionPreviewVisible" :title="versionPreviewTitle" width="760px" top="6vh" @closed="closeVersionPreview">
      <div class="version-preview-dialog" v-loading="versionPreviewLoading">
        <div class="version-preview-stage">
          <img v-if="versionPreviewUrl" :src="versionPreviewUrl" alt="版本预览" />
          <el-empty v-else description="暂无预览" :image-size="60" />
        </div>
        <div v-if="versionPreviewVersion" class="version-preview-meta">
          <span>类型：{{ versionOperationLabel(versionPreviewVersion.operationType) }}</span>
          <span>尺寸：{{ versionDimensions(versionPreviewVersion) }}</span>
          <span>大小：{{ formatBytes(versionPreviewVersion.sizeBytes) }}</span>
          <span>MIME：{{ versionPreviewVersion.mimeType }}</span>
          <span>时间：{{ formatDateTime(versionPreviewVersion.createdAt) }}</span>
          <span>文件：{{ versionPreviewVersion.originalFilename }}</span>
        </div>
      </div>
      <template #footer>
        <el-button @click="versionPreviewVisible = false">关闭</el-button>
        <el-button
          v-if="versionPreviewVersion && !versionPreviewVersion.current && canEdit"
          type="primary"
          :loading="imageVersionActionId === versionPreviewVersion.id"
          @click="restoreVersion(versionPreviewVersion)"
        >
          恢复此版本
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.image-library-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.image-toolbar {
  align-items: flex-start;
  flex: 0 0 auto;
  gap: 10px 12px;
  justify-content: space-between;
  margin-bottom: 0;
}

.image-filter-group {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-width: 0;
}

.image-scope-row {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  gap: 12px;
  justify-content: space-between;
  margin-bottom: 10px;
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

.image-sort-controls {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.image-sort-select {
  width: 150px;
}

.sort-direction-toggle {
  flex: 0 0 auto;
}

.image-toolbar-actions {
  align-items: center;
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
  justify-content: flex-end;
}

.batch-toolbar {
  align-items: center;
  border-top: 1px solid #e2e8f0;
  color: #64748b;
  display: flex;
  flex: 0 0 auto;
  font-size: 13px;
  justify-content: space-between;
  margin-bottom: 12px;
  margin-top: 10px;
  padding-top: 10px;
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

.image-results-scroll-region {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding-right: 4px;
}

.image-results-scroll-region > .el-table {
  width: 100%;
}

.image-grid-view {
  min-height: 100%;
}

.image-grid {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
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

.image-grid-counts {
  align-items: center;
  color: #475569;
  display: flex;
  flex-wrap: wrap;
  font-size: 12px;
  gap: 4px;
  min-width: 0;
}

.image-grid-counts > span {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 999px;
  display: inline-flex;
  gap: 3px;
  line-height: 1.2;
  min-width: 0;
  padding: 2px 6px;
  white-space: nowrap;
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

.image-grid-hidden-tags-panel {
  display: grid;
  gap: 8px;
}

.image-grid-hidden-tags-title {
  color: #64748b;
  font-size: 12px;
  line-height: 1;
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
  flex-wrap: nowrap;
  gap: 6px;
  justify-content: flex-start;
  margin-top: auto;
  min-height: 40px;
  padding: 6px 10px 8px;
}

.image-action-group {
  align-items: center;
  display: flex;
  flex-wrap: nowrap;
  gap: 10px;
  min-width: 0;
}

.image-action-cluster {
  align-items: center;
  display: flex;
  flex-wrap: nowrap;
  gap: 3px;
}

.image-action-cluster-single {
  justify-content: center;
}

.image-action-button {
  background: #ffffff;
  border-color: #d7dee8;
  color: #475569;
  height: 32px;
  margin-left: 0;
  transition:
    background-color 0.16s ease,
    border-color 0.16s ease,
    color 0.16s ease,
    box-shadow 0.16s ease;
  width: 32px;
}

.image-action-button + .image-action-button {
  margin-left: 0;
}

.image-action-button:hover,
.image-action-button:focus-visible {
  background: #f8fafc;
  border-color: #93c5fd;
  color: #2563eb;
}

.image-action-button.is-active {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #2563eb;
}

.image-action-emoji-button {
  font-size: 15px;
}

.image-action-emoji-button span {
  line-height: 1;
}

.image-action-button.is-warning {
  background: #fffbeb;
  border-color: #fbbf24;
  color: #d97706;
}

.image-action-button.is-warning:hover,
.image-action-button.is-warning:focus-visible {
  background: #fffbeb;
  border-color: #fbbf24;
  color: #b45309;
}

.image-action-button :deep(.el-icon) {
  font-size: 15px;
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

.image-counts-cell {
  color: #475569;
  display: grid;
  font-size: 12px;
  gap: 3px;
  line-height: 1.35;
}

.preview-image {
  display: block;
  margin: 0 auto;
  max-height: min(64vh, calc(100vh - 300px));
  max-width: 100%;
  object-fit: contain;
}

.preview-shell {
  align-items: stretch;
  display: grid;
  gap: 10px;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  min-height: 280px;
}

.preview-nav {
  align-self: stretch;
  background: #f8fafc;
  border-color: #e2e8f0;
  border-radius: 10px;
  color: #64748b;
  display: inline-flex;
  flex-direction: column;
  gap: 10px;
  height: 100%;
  justify-self: center;
  min-height: 280px;
  min-width: 0;
  padding: 0;
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
  width: 100%;
}

.preview-nav :deep(.el-icon) {
  font-size: 20px;
}

.preview-nav:not(.is-disabled):hover,
.preview-nav:not(.is-disabled):focus-visible {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #2563eb;
}

.preview-nav.is-disabled {
  box-shadow: none;
  opacity: 0.46;
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
  grid-template-columns: 48px minmax(0, 1fr);
}

.preview-detail-grid {
  display: grid;
  gap: 10px 18px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.preview-detail-tags {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
}

.preview-detail-tag-group {
  color: #64748b;
  font-size: 12px;
  line-height: 22px;
  margin-right: 2px;
}

.preview-interactions {
  border-top: 1px solid #e2e8f0;
  display: grid;
  gap: 14px;
  margin-top: 14px;
  padding-top: 14px;
}

.preview-interaction-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-start;
}

.preview-interaction-actions :deep(.el-button) {
  margin-left: 0;
}

.preview-comments {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 12px;
  padding: 14px;
}

.preview-comments-head {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.preview-comments-head span,
.preview-comments :deep(.comment-thread-head span) {
  color: #64748b;
  font-size: 13px;
}

.comment-editor {
  display: grid;
  gap: 10px;
}

.comment-reply-target {
  align-items: center;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  color: #475569;
  display: flex;
  font-size: 13px;
  justify-content: space-between;
  padding: 8px 10px;
}

.comment-editor .el-button {
  justify-self: flex-start;
}

.comment-list {
  display: grid;
  gap: 6px;
}

.preview-comments :deep(.comment-thread-node) {
  display: grid;
  column-gap: 8px;
  grid-template-columns: 42px minmax(0, 1fr);
  min-width: 0;
  position: relative;
}

.preview-comments :deep(.comment-thread-node.is-reply) {
  margin-top: 6px;
}

.preview-comments :deep(.comment-thread-node.is-reply)::before {
  border-bottom: 2px solid #d6dee8;
  border-left: 2px solid #d6dee8;
  border-bottom-left-radius: 12px;
  content: '';
  height: 18px;
  left: -14px;
  position: absolute;
  top: 14px;
  width: 18px;
}

.preview-comments :deep(.comment-thread-rail) {
  align-items: center;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 100%;
  position: relative;
}

.preview-comments :deep(.comment-thread-node.has-replies:not(.is-collapsed) > .comment-thread-rail)::after {
  background: #d6dee8;
  border-radius: 999px;
  content: '';
  flex: 1 1 auto;
  margin-top: 4px;
  min-height: 18px;
  width: 2px;
}

.preview-comments :deep(.comment-avatar) {
  box-shadow: 0 0 0 3px #f8fafc;
  position: relative;
  z-index: 1;
}

.preview-comments :deep(.comment-rail-toggle) {
  align-items: center;
  background: transparent;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  color: #64748b;
  cursor: pointer;
  display: inline-flex;
  font-size: 14px;
  font-weight: 700;
  height: 18px;
  justify-content: center;
  line-height: 1;
  margin-top: 6px;
  padding: 0;
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
  width: 18px;
  z-index: 1;
}

.preview-comments :deep(.comment-rail-toggle:hover),
.preview-comments :deep(.comment-thread-node:hover > .comment-thread-rail .comment-rail-toggle) {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #2563eb;
}

.preview-comments :deep(.comment-thread-node:hover > .comment-thread-rail)::after,
.preview-comments :deep(.comment-thread-node:hover::before) {
  border-color: #93c5fd;
  background: #93c5fd;
}

.preview-comments :deep(.comment-thread-content) {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.preview-comments :deep(.comment-thread-body) {
  border-radius: 8px;
  display: grid;
  gap: 7px;
  min-width: 0;
  padding: 6px 8px;
  transition: background-color 0.16s ease;
}

.preview-comments :deep(.comment-thread-body:hover) {
  background: #f1f5f9;
}

.preview-comments :deep(.comment-thread-node.is-deleted > .comment-thread-content > .comment-thread-body) {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.preview-comments :deep(.comment-thread-head) {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-comments :deep(.comment-thread-head strong) {
  color: #334155;
  font-size: 13px;
}

.preview-comments :deep(.comment-thread-head .comment-edited) {
  color: #94a3b8;
  font-size: 12px;
}

.preview-comments :deep(.comment-thread-body p) {
  color: #334155;
  line-height: 1.55;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.preview-comments :deep(.comment-thread-body .comment-deleted-text) {
  color: #94a3b8;
  font-style: italic;
}

.preview-comments :deep(.comment-children) {
  display: grid;
  gap: 4px;
  margin-top: 2px;
}

.preview-comments :deep(.comment-actions) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-comments :deep(.comment-actions .el-button) {
  font-size: 12px;
  min-height: 22px;
  padding: 0;
}

.preview-comments :deep(.comment-inline-reply) {
  background: #ffffff;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  margin: 2px 0 4px;
  padding: 10px;
}

.preview-comments :deep(.comment-inline-reply .comment-reply-submit-button) {
  min-height: 32px;
  padding: 0 14px;
}

.edit-dialog-layout {
  display: grid;
  gap: 18px;
  grid-template-columns: minmax(0, 1fr) 290px;
}

.image-editor-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.image-editor-head {
  align-items: flex-start;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.image-editor-head h3 {
  color: #1f2937;
  font-size: 16px;
  margin: 0 0 4px;
}

.image-editor-head p,
.image-editor-status {
  color: #64748b;
  font-size: 13px;
  margin: 0;
}

.image-edit-canvas {
  background: #f8fafc;
  border: 1px solid #dbe4ef;
  border-radius: 8px;
  height: auto;
  max-width: 100%;
  touch-action: none;
  user-select: none;
  width: 100%;
}

.image-editor-tools {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.image-editor-scale {
  align-items: center;
  display: grid;
  flex: 1;
  gap: 8px;
  grid-template-columns: 18px 56px minmax(140px, 1fr) 46px;
  min-width: 280px;
}

.image-editor-scale-label {
  color: #64748b;
  font-size: 13px;
  white-space: nowrap;
}

.image-editor-scale-value {
  color: #475569;
  font-size: 13px;
  text-align: right;
}

.image-editor-info-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.image-editor-info-grid div {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 4px;
  min-width: 0;
  padding: 9px 10px;
}

.image-editor-info-grid span {
  color: #64748b;
  font-size: 12px;
}

.image-editor-info-grid strong {
  color: #1f2937;
  font-size: 13px;
  font-weight: 600;
  min-width: 0;
  overflow-wrap: anywhere;
}

.image-editor-status {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.image-edit-side {
	border-left: 1px solid #e2e8f0;
	display: grid;
	gap: 18px;
	min-width: 0;
	padding-left: 18px;
}

.image-meta-form {
	min-width: 0;
}

.image-meta-form-head {
	align-items: center;
	color: #334155;
  display: flex;
  font-weight: 600;
  gap: 6px;
	margin-bottom: 16px;
}

.image-version-panel {
	border-top: 1px solid #e2e8f0;
	display: grid;
	gap: 10px;
	min-height: 120px;
	padding-top: 16px;
}

.image-version-head {
	align-items: center;
	display: flex;
	gap: 8px;
	justify-content: space-between;
}

.image-version-head strong {
	color: #334155;
	font-size: 14px;
}

.image-version-head span,
.image-version-main span {
	color: #64748b;
	font-size: 12px;
}

.image-version-list {
	display: grid;
	gap: 8px;
	max-height: 260px;
	overflow: auto;
	padding-right: 2px;
}

.image-version-item {
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
	display: grid;
	grid-template-columns: 72px minmax(0, 1fr);
	gap: 8px;
	padding: 10px;
}

.image-version-item.is-current {
	background: #f0fdf4;
	border-color: #bbf7d0;
}

.image-version-main {
	display: grid;
	gap: 4px;
	min-width: 0;
}

.image-version-thumb {
	align-items: center;
	appearance: none;
	background: #e2e8f0;
	border: 1px solid #cbd5e1;
	border-radius: 6px;
	color: #64748b;
	cursor: pointer;
	display: flex;
	font-size: 12px;
	height: 54px;
	justify-content: center;
	line-height: 1.3;
	overflow: hidden;
	padding: 0;
	width: 72px;
}

.image-version-thumb:hover,
.image-version-thumb:focus-visible {
	border-color: #93c5fd;
	color: #2563eb;
	outline: none;
}

.image-version-thumb img {
	height: 100%;
	object-fit: cover;
	width: 100%;
}

.image-version-title {
	align-items: center;
	display: flex;
	gap: 6px;
	justify-content: space-between;
}

.image-version-title strong {
	color: #1f2937;
	font-size: 13px;
}

.image-version-actions {
	grid-column: 2;
	display: flex;
	gap: 8px;
	justify-content: flex-end;
}

.version-preview-dialog {
	display: grid;
	gap: 14px;
}

.version-preview-stage {
	align-items: center;
	background: #0f172a;
	border-radius: 8px;
	display: flex;
	justify-content: center;
	min-height: 360px;
	overflow: hidden;
}

.version-preview-stage img {
	max-height: min(62vh, 520px);
	max-width: 100%;
	object-fit: contain;
}

.version-preview-meta {
	color: #475569;
	display: grid;
	font-size: 13px;
	gap: 8px 16px;
	grid-template-columns: repeat(2, minmax(0, 1fr));
}

.pagination-row {
  border-top: 1px solid #e2e8f0;
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 12px;
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

.single-upload-title,
.batch-title-input {
  width: 100%;
}

.single-upload-title-field {
  display: grid;
  gap: 4px;
}

.single-upload-title-label {
  color: #475569;
  font-size: 13px;
  font-weight: 600;
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

.batch-upload-hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  margin-top: 10px;
}

.batch-preview-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
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

.upload-failed-list,
.upload-duplicate-list {
  display: grid;
  gap: 8px;
  margin-top: 12px;
}

.upload-failed-head,
.upload-result-head {
  color: #64748b;
  font-size: 13px;
}

.upload-duplicate-group {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  display: grid;
  gap: 8px;
  padding: 10px;
}

.upload-duplicate-group-head {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.upload-duplicate-group-head span,
.upload-duplicate-group-head p {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
}

.upload-duplicate-targets {
  display: grid;
  gap: 8px;
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
  flex-wrap: wrap;
  gap: 12px;
  justify-content: space-between;
  padding: 10px 12px;
}

.upload-duplicate-row {
  align-items: flex-start;
}

.upload-result-thumb {
  align-items: center;
  aspect-ratio: 4 / 3;
  background: #e2e8f0;
  border-radius: 6px;
  color: #64748b;
  display: flex;
  flex: 0 0 72px;
  font-size: 12px;
  justify-content: center;
  overflow: hidden;
}

.upload-result-thumb img {
  display: block;
  height: 100%;
  object-fit: cover;
  width: 100%;
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

.upload-result-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-result-main p {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
  margin: 0;
}

.upload-result-actions {
  align-items: flex-start;
  display: flex;
  flex: 0 0 auto;
}

@media (max-width: 980px) {
  .image-toolbar {
    align-items: stretch;
  }

  .image-scope-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .image-filter-group,
  .image-toolbar-actions,
  .batch-toolbar,
  .batch-toolbar-summary,
  .batch-toolbar-actions {
    width: 100%;
  }

  .image-toolbar-actions {
    align-items: flex-start;
    justify-content: flex-start;
  }

  .image-toolbar-actions .display-mode-toggle {
    width: auto;
  }

  .image-sort-controls {
    justify-content: flex-start;
    width: 100%;
  }

  .image-sort-select {
    flex: 1;
    min-width: 160px;
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
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }

  .preview-detail-grid {
    grid-template-columns: 1fr;
  }

  .preview-shell {
    gap: 8px;
    grid-template-columns: 36px minmax(0, 1fr) 36px;
  }

  .preview-nav {
    min-height: 240px;
  }

  .preview-interaction-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .edit-dialog-layout {
    grid-template-columns: 1fr;
  }

  .image-edit-side {
    border-left: 0;
    border-top: 1px solid #e2e8f0;
    padding-left: 0;
    padding-top: 16px;
  }

  .image-editor-info-grid {
    grid-template-columns: 1fr;
  }

  .image-version-item {
    grid-template-columns: 60px minmax(0, 1fr);
  }

  .image-version-thumb {
    height: 48px;
    width: 60px;
  }

  .version-preview-meta {
    grid-template-columns: 1fr;
  }
}
</style>
