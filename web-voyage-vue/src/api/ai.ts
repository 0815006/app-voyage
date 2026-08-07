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
