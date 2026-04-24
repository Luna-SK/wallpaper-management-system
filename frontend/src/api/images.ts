import { http } from './http'

export interface ImageCategory {
  id: string
  code: string
  name: string
}

export interface ImageTag {
  id: string
  categoryId: string
  name: string
}

export interface ImageRecord {
  id: string
  title: string
  originalFilename: string
  mimeType: string
  sizeBytes: number
  width: number | null
  height: number | null
  status: string
  viewCount: number
  downloadCount: number
  categories: ImageCategory[]
  tags: ImageTag[]
  createdAt: string
}

export interface UploadBatchItem {
  id: string
  imageId: string | null
  candidateImageId: string | null
  originalFilename: string
  status: string
  progressPercent: number
  retryCount: number
  errorMessage: string | null
}

export interface UploadBatch {
  id: string
  status: string
  totalCount: number
  successCount: number
  failedCount: number
  duplicateCount: number
  progressPercent: number
  createdAt: string
  finishedAt: string | null
  mode: 'SINGLE' | 'BATCH'
  categoryId: string | null
  tagIds: string[]
  expiresAt: string | null
  confirmedAt: string | null
  items: UploadBatchItem[]
}

export interface Statistics {
  imageTotal: number
  todayUploaded: number
  viewCount: number
  downloadCount: number
  storageBytes: number
}

export async function getImages(params: { keyword?: string; categoryId?: string; tagId?: string } = {}) {
  const response = await http.get<ImageRecord[]>('/images', { params })
  return response.data
}

export async function updateImage(id: string, payload: { title: string; status: string; categoryIds: string[]; tagIds: string[] }) {
  const response = await http.patch<ImageRecord>(`/images/${id}`, payload)
  return response.data
}

export async function deleteImage(id: string) {
  await http.delete(`/images/${id}`)
}

export async function uploadImages(files: File[], categoryId: string, tagIds: string[]) {
  let session = await createUploadSession({
    mode: files.length === 1 ? 'SINGLE' : 'BATCH',
    categoryId,
    tagIds,
    totalCount: files.length,
  })
  for (const file of files) {
    session = await uploadSessionItem(session.id, file)
  }
  return confirmUploadSession(session.id)
}

export async function getUploadBatch(id: string) {
  const response = await http.get<UploadBatch>(`/image-upload-batches/${id}`)
  return response.data
}

export async function retryUploadItem(batchId: string, itemId: string) {
  const response = await http.post<UploadBatch>(`/image-upload-batches/${batchId}/items/${itemId}/retry`)
  return response.data
}

export async function createUploadSession(payload: { mode: 'SINGLE' | 'BATCH'; categoryId: string; tagIds: string[]; totalCount: number }) {
  const response = await http.post<UploadBatch>('/image-upload-sessions', payload)
  return response.data
}

export async function getUploadSession(id: string) {
  const response = await http.get<UploadBatch>(`/image-upload-sessions/${id}`)
  return response.data
}

export async function uploadSessionItem(sessionId: string, file: File, signal?: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<UploadBatch>(`/image-upload-sessions/${sessionId}/items`, form, { timeout: 120000, signal })
  return response.data
}

export async function retryUploadSessionItem(sessionId: string, itemId: string, file: File, signal?: AbortSignal) {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<UploadBatch>(`/image-upload-sessions/${sessionId}/items/${itemId}/retry`, form, { timeout: 120000, signal })
  return response.data
}

export async function confirmUploadSession(id: string) {
  const response = await http.post<UploadBatch>(`/image-upload-sessions/${id}/confirm`)
  return response.data
}

export async function cancelUploadSession(id: string) {
  const response = await http.post<UploadBatch>(`/image-upload-sessions/${id}/cancel`)
  return response.data
}

export async function imageBlobUrl(id: string, kind: 'thumbnail' | 'preview') {
  const response = await http.get<Blob>(`/images/${id}/${kind}`, { responseType: 'blob' })
  return URL.createObjectURL(response.data)
}

export async function downloadImage(id: string, filename: string) {
  const response = await http.get<Blob>(`/images/${id}/download`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export async function getStatistics() {
  const response = await http.get<Statistics>('/statistics')
  return response.data
}
