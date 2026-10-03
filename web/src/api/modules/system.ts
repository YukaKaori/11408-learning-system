import { api } from '@/api/http'

export interface SystemInfo {
  name: string
  activeProfiles: string[]
  serverTime: string
}

export function getSystemInfo() {
  return api.get<SystemInfo>('/v1/system/info')
}
