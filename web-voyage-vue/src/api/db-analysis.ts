import request from '@/utils/request'
import { getCurrentEmpNo } from '@/utils/currentUser'

/**
 * 数据库性能分析 API。
 * 数据库连接采用「落表管理」：连接配置保存于后端 db_connection_config 表（按操作员工号隔离），
 * 密码以 AES-256-GCM 加密存储，列表/会话仅返回不含密码的脱敏数据；
 * 发起分析/测试连接时携带连接 id，由后端自动解密出明文密码（仅存于请求内存）。
 */

/** 数据库连接配置（已保存连接 id 非空且 password 为空，由后端按 id 自动解密） */
export interface DbConnectionConfig {
  id?: string
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

/* ==================== 数据库连接落表管理 ==================== */

/** 连接列表：返回当前操作者已保存连接（不含密码） */
export function listConnections(): Promise<DbConnectionConfig[]> {
  return request.get('/db-analysis/connections')
}

/**
 * 新增或更新连接。
 * 新增时 password 必填；编辑时 password 留空表示不修改密码（后端保留原密文）。
 */
export function saveConnection(config: DbConnectionConfig): Promise<DbConnectionConfig> {
  return request.post('/db-analysis/connection', config)
}

/** 删除连接（仅限归属当前操作者） */
export function deleteConnection(id: string): Promise<void> {
  return request.delete(`/db-analysis/connection/${id}`)
}

/** 测试连接响应 */
export interface TestConnectionResult {
  success: boolean
  alias: string
  message?: string
}

/**
 * 测试单个数据库连接是否可用。
 * 携带 id（已保存连接）时后端自动解密；临时改配未保存时请连同 password 一起传入。
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
