import request from '@/utils/request'

/**
 * 健康检查响应。
 */
export interface HealthResponse {
  status: string
  timestamp: string
  operator: string
  java: string
}

/**
 * 获取系统健康状态。
 */
export function getHealthInfo(): Promise<HealthResponse> {
  return request.get('/health')
}
