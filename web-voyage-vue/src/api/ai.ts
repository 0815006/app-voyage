import request from '@/utils/request'

/**
 * AI 会话 API。
 */

/** 同步聊天请求参数 */
export interface ChatParams {
  prompt: string
}

/** 同步聊天响应 */
export interface ChatReply {
  reply: string
}

/**
 * 同步聊天：一次性返回完整回复。
 */
export function chat(params: ChatParams): Promise<ChatReply> {
  return request.post('/ai/chat', params)
}

/**
 * 流式聊天：返回 SSE 连接的 fetch，调用方自行读取 ReadableStream。
 * 注意：不走 axios（axios 不支持流式读取），直接使用原生 fetch。
 */
export function chatStream(params: ChatParams): Promise<Response> {
  const empNo = localStorage.getItem('voyage_emp_no') || ''
  return fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Emp-No': empNo,
    },
    body: JSON.stringify(params),
  })
}

// ==================== Agent Demo API ====================

/** Agent 对话请求参数 */
export interface AgentChatParams {
  prompt: string
  filePath?: string
}

/**
 * Agent 流式对话（SSE）：通过 ai-agent-sdk 驱动 ReAct 循环，
 * 返回 SSE 流，包含 step_start / tool_start / tool_end / text_chunk / error / complete 事件。
 */
export function agentChatStream(params: AgentChatParams): Promise<Response> {
  return fetch('/api/agent/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  })
}

/**
 * 上传文件供 Agent 分析。
 * 返回 { fileId, fileName, filePath }。
 */
export function uploadFile(file: File): Promise<{ fileId: string; fileName: string; filePath: string }> {
  const formData = new FormData()
  formData.append('file', file)
  return fetch('/api/agent/upload', {
    method: 'POST',
    body: formData,
  }).then(res => {
    if (!res.ok) throw new Error(`上传失败 (${res.status})`)
    return res.json()
  })
}
