import assert from 'node:assert/strict'
import test from 'node:test'

import { isThinkingInProgress } from './embedThinkingStatus.ts'

test('isThinkingInProgress: quick-answer mode tracks thinking flag', () => {
  assert.equal(isThinkingInProgress({ thinking: true }), true)
  assert.equal(isThinkingInProgress({ thinking: false }), false)
  assert.equal(isThinkingInProgress({ showThink: true }), false)
  assert.equal(isThinkingInProgress(null), false)
  assert.equal(isThinkingInProgress(undefined), false)
})

test('isThinkingInProgress: agent mode scans thinking events', () => {
  assert.equal(
    isThinkingInProgress({
      isAgentMode: true,
      agentEventStream: [{ type: 'thinking', thinking: true, done: false }],
    }),
    true,
  )
  assert.equal(
    isThinkingInProgress({
      isAgentMode: true,
      agentEventStream: [{ type: 'thinking', thinking: false, done: true }],
    }),
    false,
  )
  assert.equal(
    isThinkingInProgress({
      isAgentMode: true,
      agentEventStream: [{ type: 'tool_call', thinking: false }],
    }),
    false,
  )
  assert.equal(isThinkingInProgress({ isAgentMode: true }), false)
})
