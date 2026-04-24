import { http } from './http'

export type AuditArchiveStorage = 'RUSTFS'

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
}

export interface AuditArchiveRun {
  id: string
  triggerType: 'SCHEDULED' | 'MANUAL' | string
  cutoffTime: string | null
  archiveBucket: string | null
  archiveObjectKey: string | null
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | string
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

export async function getAuditRetention() {
  const response = await http.get<ApiResponse<AuditRetentionPayload>>('/audit-log-retention')
  return response.data.data
}

export async function updateAuditRetention(settings: AuditRetentionSettings) {
  const response = await http.patch<ApiResponse<AuditRetentionSettings>>('/audit-log-retention', settings)
  return response.data.data
}

export async function getAuditArchiveRuns(limit = 20) {
  const response = await http.get<ApiResponse<AuditArchiveRun[]>>('/audit-log-archives', {
    params: { limit },
  })
  return response.data.data
}

export async function createAuditArchiveRun() {
  const response = await http.post<ApiResponse<AuditArchiveRun>>('/audit-log-archives')
  return response.data.data
}

export async function getAuditLogs(params: { keyword?: string; limit?: number } = {}) {
  const response = await http.get<AuditLog[]>('/audit-logs', { params })
  return response.data
}
