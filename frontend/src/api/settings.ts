import { http } from './http'

export interface SystemSettings {
  maxFileSizeMb: number
  maxBatchSizeMb: number
  maxFileHardLimitMb: number
  maxBatchHardLimitMb: number
  previewQuality: 'ORIGINAL' | 'HIGH' | 'STANDARD'
  softDeleteRetentionDays: number
  softDeleteCleanupEnabled: boolean
  softDeleteCleanupCron: string
  watermarkEnabled: boolean
  watermarkText: string
}

export type SystemSettingsUpdate = Omit<SystemSettings, 'maxFileHardLimitMb' | 'maxBatchHardLimitMb'>

export async function getSystemSettings() {
  const response = await http.get<SystemSettings>('/system-settings')
  return response.data
}

export async function updateSystemSettings(settings: SystemSettingsUpdate) {
  const response = await http.patch<SystemSettings>('/system-settings', settings)
  return response.data
}
