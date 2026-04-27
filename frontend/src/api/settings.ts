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
  watermarkPreviewEnabled: boolean
  watermarkText: string
  watermarkMode: 'CORNER' | 'TILED'
  watermarkPosition:
    | 'TOP_LEFT'
    | 'TOP_CENTER'
    | 'TOP_RIGHT'
    | 'CENTER_LEFT'
    | 'CENTER'
    | 'CENTER_RIGHT'
    | 'BOTTOM_LEFT'
    | 'BOTTOM_CENTER'
    | 'BOTTOM_RIGHT'
  watermarkOpacityPercent: number
  watermarkTileDensity: 'SPARSE' | 'NORMAL' | 'DENSE'
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
