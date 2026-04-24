import { http } from './http'

export interface SystemSettings {
  maxFileSizeMb: number
  maxBatchSizeMb: number
  watermarkEnabled: boolean
  previewQuality: 'ORIGINAL' | 'HIGH' | 'STANDARD'
  softDeleteRetentionDays: number
}

export async function getSystemSettings() {
  const response = await http.get<SystemSettings>('/system-settings')
  return response.data
}

export async function updateSystemSettings(settings: SystemSettings) {
  const response = await http.patch<SystemSettings>('/system-settings', settings)
  return response.data
}
