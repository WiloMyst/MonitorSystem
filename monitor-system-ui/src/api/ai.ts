/**
 * AI 聊天 SSE 流式接口
 *
 * 通过 fetch + ReadableStream 消费后端 SSE 流，支持以下事件类型:
 *   - message:         正常回复内容（逐 chunk 推送）
 *   - conversation_id:  会话 ID（首次对话时返回）
 *   - error:           错误消息
 *   - hitl_pending:    Human-in-the-Loop 审批等待
 *   - fallback_notice: 降级模式通知
 */
export interface StreamCallbacks {
  onMessage: (chunk: string) => void
  onConversationId?: (id: string) => void
  onError?: (msg: string) => void
  onHitlPending?: (data: HitlPendingData) => void
  onFallbackNotice?: (msg: string) => void
}

/** HITL 审批等待数据 */
export interface HitlPendingData {
  request_id: string
  question: string
  options?: Array<{ id: string; label: string }>
  timeout_seconds?: number
}

/** 从 SSE data 字段中安全提取 content，兼容纯字符串和 JSON 对象两种格式 */
function safeExtractContent(raw: string): string {
  try {
    const parsed = JSON.parse(raw)
    if (typeof parsed === 'string') return parsed
    if (parsed && typeof parsed === 'object' && 'content' in parsed) {
      return String(parsed.content)
    }
    return raw
  } catch {
    return raw
  }
}

export const askAiStream = async (
  question: string,
  callbacks: StreamCallbacks,
  conversationId?: string
) => {
  const token = localStorage.getItem('sa-token') || ''

  const body: Record<string, string> = { question }
  if (conversationId) {
    body.conversation_id = conversationId
  }

  const response = await fetch(import.meta.env.VITE_APP_BASE_API + '/ai/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'satoken': token
    },
    body: JSON.stringify(body)
  })

  if (!response.ok) {
    const errorText = await response.text().catch(() => '')
    throw new Error(errorText || `请求失败 (${response.status})`)
  }

  const reader = response.body?.getReader()
  if (!reader) return

  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    let currentEvent = 'message'
    for (const line of lines) {
      if (line.startsWith('event:')) {
        currentEvent = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (!data) continue

        if (currentEvent === 'conversation_id') {
          const convId = safeExtractContent(data)
          callbacks.onConversationId?.(convId)
        } else if (currentEvent === 'error') {
          const errMsg = safeExtractContent(data)
          callbacks.onError?.(errMsg)
        } else if (currentEvent === 'hitl_pending') {
          try {
            const parsed: HitlPendingData = JSON.parse(data)
            callbacks.onHitlPending?.(parsed)
          } catch {
            callbacks.onHitlPending?.({ request_id: '', question: data })
          }
        } else if (currentEvent === 'fallback_notice') {
          const noticeMsg = safeExtractContent(data)
          callbacks.onFallbackNotice?.(noticeMsg)
        } else {
          const content = safeExtractContent(data)
          callbacks.onMessage(content)
        }
        currentEvent = 'message'
      }
    }
  }
}
