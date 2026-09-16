export interface EmbedThinkingMessage {
  thinking?: boolean
  showThink?: boolean
  isAgentMode?: boolean
  agentEventStream?: Array<{ type?: string; thinking?: boolean; done?: boolean }>
}

/**
 * Whether a thinking process is currently in progress for a message.
 * Quick-answer (RAG) mode mirrors the `thinking` flag parsed from <think> tags
 * by useChatStreamHandler; agent mode scans the event stream for unfinished
 * thinking events.
 */
export function isThinkingInProgress(message?: EmbedThinkingMessage | null): boolean {
  if (!message) return false
  if (message.isAgentMode) {
    return (message.agentEventStream ?? []).some(
      (e) => e?.type === 'thinking' && e.thinking === true && e.done !== true,
    )
  }
  return message.thinking === true
}
