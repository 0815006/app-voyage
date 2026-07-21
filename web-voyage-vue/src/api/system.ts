import request from '@/utils/request'

/**
 * 系统信息接口响应。
 */
export interface SystemInfoResponse {
  loginIp: string
  serverTime: string
}

/**
 * 获取系统信息（用于 StatusBar 展示 Login IP）。
 */
export function getSystemInfo(): Promise<SystemInfoResponse> {
  return request.get('/system/info')
}
