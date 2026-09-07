import request from '@/utils/request'
import { getCurrentEmpNo } from '@/utils/currentUser'

/**
 * 文件分析（Document Studio）API。
 * 工作区物理隔离 + 按操作员工号（X-Emp-No）归属隔离；
 * 文件轻量化存储（磁盘 + 元数据），对话经由后端 Agent（ai-tool-doc）SSE 流式返回。
 */

/** 历史工作区实体 */
export interface WorkspaceSubDir {
  subDirId: string
  empNo: string
  dirName: string
  absPath: string
  createTime: string
  updateTime: string
}

/** 工作区文件树轻量条目 */
export interface WorkspaceFileItem {
  fileId: string
  fileName: string
  fileType: string
  fileSize: number
  createTime: string
  createTimeText: string
}

/** 文件完整元数据（含 ai-tool-doc 预解析内容，预览接口返回） */
export interface WorkspaceFileDetail extends WorkspaceFileItem {
  subDirId: string
  parsedMarkdown: string | null
  parsedSchema: string | null
}

/** 工作区文本分析会话 */
export interface SessionTextAnalysis {
  sessionId: string
  subDirId: string
  sessionTitle: string
  contextMessages: string
  executionTrace: string
  createTime: string
  updateTime: string
}

/* ==================== 工作区管理 ==================== */

/** 查询当前操作者的历史工作区列表 */
export function listWorkspaces(): Promise<WorkspaceSubDir[]> {
  return request.get('/workspace/list')
}

/** 新建工作区（物理创建 UUID 隔离子目录） */
export function createWorkspace(dirName?: string): Promise<WorkspaceSubDir> {
  return request.post('/workspace/create', { dirName: dirName ?? '' })
}

/** 删除工作区（递归物理删除 + 数据库级联） */
export function deleteWorkspace(subDirId: string): Promise<void> {
  return request.delete(`/workspace/${subDirId}`)
}

/** 查询指定工作区当前会话（含 contextMessages 历史） */
export function getSubDirSession(subDirId: string): Promise<SessionTextAnalysis | null> {
  return request.get(`/workspace/${subDirId}/session`)
}

/* ==================== 工作区文件 ==================== */

/** 获取指定工作区文件树 */
export function listFiles(subDirId: string): Promise<WorkspaceFileItem[]> {
  return request.get(`/workspace/${subDirId}/files`)
}

/**
 * 上传文件到工作区（FormData，axios 自动注入 X-Emp-No）。
 * <p>
 * 必须显式覆盖 'Content-Type': 'multipart/form-data'：
 * 本 axios 实例默认 Content-Type 为 application/json，若不覆盖，
 * transformRequest 命中 JSON 分支会执行 formDataToJSON + JSON.stringify，
 * 把 FormData 序列化为 JSON，导致后端 multipart 解析失败(400)。
 * 显式指定 multipart 后，浏览器 XHR 会自动补全 boundary 发送真正的 multipart 体，
 * 后端以 @RequestParam("file") MultipartFile 正常接收。
 */
export function uploadFile(subDirId: string, file: File): Promise<WorkspaceFileDetail> {
  const form = new FormData()
  form.append('file', file)
  return request.post(`/workspace/${subDirId}/file/upload`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 预览：JSON 内容视图（含 parsed_markdown / parsed_schema） */
export function previewFile(fileId: string): Promise<WorkspaceFileDetail> {
  return request.get(`/workspace/file/${fileId}/preview`)
}

/** 预览原始文件（PDF/图片内联流，需带 X-Emp-No 才能通过后端鉴权） */
export function previewFileUrl(fileId: string): string {
  const qs = new URLSearchParams({ raw: 'true', t: Date.now().toString() })
  return `/api/workspace/file/${fileId}/preview?${qs.toString()}`
}

/** 下载原文件或 Agent 生成的报告（附件下载） */
export function downloadFileUrl(fileId: string): string {
  return `/api/workspace/file/${fileId}/download`
}

/** 下载（走 fetch 携带 X-Emp-No，处理后端归属校验） */
export async function downloadFile(fileId: string): Promise<void> {
  const resp = await fetch(downloadFileUrl(fileId), {
    headers: { 'X-Emp-No': getCurrentEmpNo() },
  })
  if (!resp.ok) {
    throw new Error(`下载失败: HTTP ${resp.status}`)
  }
  const blob = await resp.blob()
  const disposition = resp.headers.get('Content-Disposition') || ''
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition)
  const fileName = match ? decodeURIComponent(match[1]) : `file-${fileId}`
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
}

/** 删除文件（同步清理物理磁盘与数据库记录） */
export function deleteFile(fileId: string): Promise<void> {
  return request.delete(`/workspace/file/${fileId}`)
}

/** 文件清单对账结果 */
export interface ReconcileResult {
  addedCount: number
  removedCount: number
  removedNames: string[]
}

/**
 * 文件清单对账（刷新）：磁盘 ↔ 数据库双向往返同步。
 * 磁盘有新增受控文件 → 登记入库；数据库有记录但磁盘已缺失 → 删除记录。
 */
export function reconcileFiles(subDirId: string): Promise<ReconcileResult> {
  return request.post(`/workspace/${subDirId}/files/reconcile`)
}

/* ==================== 文件分析对话（SSE） ==================== */

/** 文件分析对话请求参数 */
export interface TextAnalysisChatParams {
  subDirId: string
  prompt: string
}

/**
 * 发起文件分析对话（SSE 流式，原生 fetch 直连）。
 * 事件：step_start / thought_chunk / tool_start / tool_end / text_chunk / complete / error
 * complete 事件 data 中含 finalText / sessionId / generatedFiles(fileIds) 等。
 */
export function textAnalysisChat(params: TextAnalysisChatParams): Promise<Response> {
  return fetch('/api/session/text-analysis/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Emp-No': getCurrentEmpNo(),
    },
    body: JSON.stringify(params),
  })
}
