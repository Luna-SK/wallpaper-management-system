import { http } from './http'

export type AuditArchiveStorage = 'RUSTFS'
export type AuditArchiveRunStatus = 'RUNNING' | 'SUCCESS' | 'FAILED'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId?: string | null
}

export interface AuditRetentionSettings {
  retentionDays: number
  archiveEnabled: boolean
  archiveCron: string
  archiveStorage: AuditArchiveStorage
  batchSize: number
}

export interface AuditRetentionPayload {
  settings: AuditRetentionSettings
  expiredCount: number
  expiredArchiveRunCount: number
}

export interface AuditArchiveRun {
  id: string
  triggerType: 'SCHEDULED' | 'MANUAL' | string
  cutoffTime: string | null
  archiveBucket: string | null
  archiveObjectKey: string | null
  status: AuditArchiveRunStatus
  archivedCount: number
  deletedCount: number
  startedAt: string
  finishedAt: string | null
  errorMessage: string | null
}

export interface AuditLog {
  id: string
  action: string
  targetType: string | null
  targetId: string | null
  detailJson: string | null
  createdAt: string
}

export interface AuditPage<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export async function getAuditRetention() {
  const response = await http.get<ApiResponse<AuditRetentionPayload>>('/audit-log-retention')
  return response.data.data
}

export async function updateAuditRetention(settings: AuditRetentionSettings) {
  const response = await http.patch<ApiResponse<AuditRetentionSettings>>('/audit-log-retention', settings)
  return response.data.data
}

export async function getAuditArchiveRuns(params: { page?: number; size?: number } = {}) {
  const response = await http.get<ApiResponse<AuditPage<AuditArchiveRun>>>('/audit-log-archives', {
    params,
  })
  return response.data.data
}

export async function createAuditArchiveRun() {
  const response = await http.post<ApiResponse<AuditArchiveRun>>('/audit-log-archives')
  return response.data.data
}

export async function getAuditLogs(params: { keyword?: string; startDate?: string; endDate?: string; page?: number; size?: number } = {}) {
  const response = await http.get<AuditPage<AuditLog>>('/audit-logs', { params })
  return response.data
}

export async function exportAuditLogs(params: { keyword?: string; startDate?: string; endDate?: string } = {}) {
  const response = await http.get<Blob>('/audit-logs/export', {
    params,
    responseType: 'blob',
  })
  downloadBlob(response.data, filenameFromDisposition(response.headers['content-disposition']) ?? 'audit-logs.csv')
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function filenameFromDisposition(value: unknown) {
  if (typeof value !== 'string') return null
  const encoded = value.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  if (encoded) return decodeURIComponent(encoded)
  const quoted = value.match(/filename="([^"]+)"/i)?.[1]
  return quoted ?? value.match(/filename=([^;]+)/i)?.[1] ?? null
}
