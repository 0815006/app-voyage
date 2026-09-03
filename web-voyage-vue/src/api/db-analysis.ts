import request from '@/utils/request'
import { getCurrentEmpNo } from '@/utils/currentUser'

/**
 * 数据库性能分析 API。
 * 依据 PRD「零密码落盘」：密码仅存在于前端内存/本地，随分析请求透传后端，
 * 会话持久化仅保存不含密码的元数据快照。
 */

/** 选中的数据库连接配置（含临时密码，仅前端内存持有） */
export interface DbConnectionConfig {
  alias: string
  dialect: string
  host: string
  port: number
  dbName: string
  user: string
  password: string
}

/** 会话实体（不含密码） */
export interface SessionDbAnalysis {
  id: string
  sessionTitle: string
  selectedDbMeta: string
  contextMessages: string
  executionTrace: string
  createdAt: string
  updateTime: string
}

/** 会话列表响应：直接返回 SessionDbAnalysis 数组（axios 已解包 data） */
export function listSessions(): Promise<SessionDbAnalysis[]> {
  return request.get('/db-analysis/sessions')
}

/** 会话详情 */
export function getSession(id: string): Promise<SessionDbAnalysis> {
  return request.get(`/db-analysis/session/${id}`)
}

/** 新建会话 */
export function createSession(sessionTitle: string, selectedDbMeta: string): Promise<SessionDbAnalysis> {
  return request.post('/db-analysis/session', { sessionTitle, selectedDbMeta })
}

/** 删除会话 */
export function deleteSession(id: string): Promise<void> {
  return request.delete(`/db-analysis/session/${id}`)
}

/** 测试连接响应 */
export interface TestConnectionResult {
  success: boolean
  alias: string
  message?: string
}

/**
 * 测试单个数据库连接是否可用（供连接管理弹窗"测试连接"按钮使用）。
 */
export function testConnection(config: DbConnectionConfig): Promise<TestConnectionResult> {
  return request.post('/db-analysis/test-connection', config)
}

/** 流式分析请求参数 */
export interface AnalyzeParams {
  sessionId: string
  prompt: string
  activeDbConnections: DbConnectionConfig[]
}

/**
 * 发起数据库性能分析（SSE 流式）。
 * 不走 axios（axios 不支持流式读取），直接使用原生 fetch。
 * 事件：step_start / thought_chunk / tool_start / tool_end / text_chunk / suspend / complete / error
 */
export function analyzeStream(params: AnalyzeParams): Promise<Response> {
  return fetch('/api/db-analysis/analyze', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Emp-No': getCurrentEmpNo(),
    },
    body: JSON.stringify({
      sessionId: params.sessionId,
      prompt: params.prompt,
      active_db_connections: params.activeDbConnections,
    }),
  })
}

/** 人工审批恢复请求参数 */
export interface ApproveParams {
  suspendId: string
  approved: boolean
  comment: string
}

/**
 * 人工审批后恢复挂起的 Agent 执行（SSE 流式）。
 * 事件：text_chunk / complete / error
 */
export function approveStream(params: ApproveParams): Promise<Response> {
  return fetch('/api/db-analysis/approve', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Emp-No': getCurrentEmpNo(),
    },
    body: JSON.stringify(params),
  })
}