import type { MentionedItem } from './mention'

export type ChatRole = 'user' | 'assistant' | 'system'

/**
 * 会话消息：已知字段显式声明，其余后端扩展字段走索引签名（不再整体 Record<unknown>）。
 */
export interface ChatMessage {
  id?: string
  role?: ChatRole | (string & {})
  content?: string
  completed?: boolean
  isAgentMode?: boolean
  isRagMode?: boolean
  agentEventStream?: ChatMessage[]
  _eventMap?: Map<string, ChatMessage>
  _pendingToolCalls?: Map<string, unknown>
  _steerPending?: boolean
  knowledgeReferences?: unknown
  usedMemories?: unknown
  mentionedItems?: MentionedItem[]
  requestId?: string
  assistantMessageId?: string
  createdAt?: string
  steerId?: string
  isSteer?: boolean
  thinking?: boolean
  fallback?: boolean
  error?: string
  modelId?: string
  agentDurationMs?: number
  eventId?: string
  toolCallId?: string
  totalDurationMs?: number
  tool_data?: Record<string, unknown>
  [key: string]: unknown
}

export type AgentEventType =
  | 'thinking'
  | 'answer'
  | 'toolCall'
  | 'toolResult'
  | 'commandOutput'
  | 'reflection'
  | 'contextCompacted'
  | 'toolApprovalRequired'
  | 'toolApprovalResolved'
  | 'mcpOauthRequired'
  | 'mcpOauthResolved'
  | 'userMessageInjected'
  | 'agentComplete'
  | 'stop'
  | 'plan_task_change'
  // 保留后向兼容：服务端新增事件类型时前端应先能渲染
  | (string & {})

/** 时间线上的一个 agent 事件（thinking / tool / answer / 审批 / 压缩 …）。 */
export interface AgentEvent {
  type: AgentEventType
  eventId?: string
  content?: string
  done?: boolean
  thinking?: boolean
  startTime?: number
  durationMs?: number
  completedAt?: number
  title?: string
  superseded?: boolean
  toolCallId?: string
  toolName?: string
  args?: Record<string, unknown>
  result?: unknown
  success?: boolean
  error?: string
  output?: string
  data?: unknown
  task?: string
  summary?: string
  pendingId?: string
  serviceName?: string
  mcpToolName?: string
  description?: string
  argsJson?: string
  timeoutSeconds?: number
  requestedAtUnix?: number
  resolved?: boolean
  approved?: boolean
  reason?: string
  timedOut?: boolean
  canceled?: boolean
  round?: number
  tokensBefore?: number
  tokensAfter?: number
  messagesBefore?: number
  messagesAfter?: number
  degraded?: boolean
  splitTurn?: boolean
  [key: string]: unknown
}

export type StreamResponseType =
  | 'agentQuery'
  | 'thinking'
  | 'answer'
  | 'toolCall'
  | 'toolResult'
  | 'commandOutput'
  | 'reflection'
  | 'contextCompacted'
  | 'userMessageInjected'
  | 'toolApprovalRequired'
  | 'toolApprovalResolved'
  | 'mcpOauthRequired'
  | 'mcpOauthResolved'
  | 'references'
  | 'memoryRecalled'
  | 'sessionTitle'
  | 'stop'
  | 'complete'
  | 'error'

const KNOWN_RESPONSE_TYPES = new Set<string>([
  'agentQuery',
  'thinking',
  'answer',
  'toolCall',
  'toolResult',
  'commandOutput',
  'reflection',
  'contextCompacted',
  'userMessageInjected',
  'toolApprovalRequired',
  'toolApprovalResolved',
  'mcpOauthRequired',
  'mcpOauthResolved',
  'references',
  'memoryRecalled',
  'sessionTitle',
  'stop',
  'complete',
  'error',
])

/** 后端 SSE 载荷（归一后）。未知 responseType 落到 `unknown`，不再静默穿透。 */
export interface BackendStreamEvent {
  id: string
  responseType: StreamResponseType | 'unknown'
  rawResponseType: string
  content: string
  done: boolean
  data?: Record<string, unknown>
  sessionId?: string
  assistantMessageId?: string
  requestId?: string
  [key: string]: unknown
}

/**
 * 把 JSON.parse 后的 SSE 载荷归一为带判别字段的事件。
 *
 * 非对象 / 缺 responseType 的载荷返回 null（调用方丢弃并记日志）；
 * 未知 responseType 保留原文到 rawResponseType，responseType 记为 `unknown`。
 */
export function normalizeStreamEvent(raw: unknown): BackendStreamEvent | null {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return null
  const record = raw as Record<string, unknown>
  const rawResponseType = String(
    record.responseType ?? record.response_type ?? record.type ?? '',
  ).trim()
  if (!rawResponseType) return null

  const payload =
    record.data && typeof record.data === 'object' && !Array.isArray(record.data)
      ? (record.data as Record<string, unknown>)
      : undefined

  return {
    ...record,
    id: record.id == null ? '' : String(record.id),
    responseType: KNOWN_RESPONSE_TYPES.has(rawResponseType)
      ? (rawResponseType as StreamResponseType)
      : 'unknown',
    rawResponseType,
    content: record.content == null ? '' : String(record.content),
    done: Boolean(record.done),
    data: payload,
    sessionId: record.sessionId == null ? undefined : String(record.sessionId),
    assistantMessageId:
      record.assistantMessageId == null ? undefined : String(record.assistantMessageId),
    requestId: record.requestId == null ? undefined : String(record.requestId),
  }
}
