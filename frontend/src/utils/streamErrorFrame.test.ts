import assert from 'node:assert/strict'
import { test } from 'node:test'

import { shouldStopStreamOnErrorFrame } from './streamErrorFrame.ts'

test('工具级失败帧（done=false）不得终止整条流', () => {
  const frame = {
    responseType: 'error',
    done: false,
    data: { toolName: 'call_mcp_tool', toolCallId: 'c1', success: false },
  }
  assert.equal(shouldStopStreamOnErrorFrame(frame), false)
})

test('终态错误帧（done=true）才收束', () => {
  assert.equal(shouldStopStreamOnErrorFrame({ responseType: 'error', done: true }), true)
})

test('缺 done 字段的错误帧按“未终态”处理', () => {
  assert.equal(shouldStopStreamOnErrorFrame({ responseType: 'error' }), false)
})

test('非错误帧一律不收束', () => {
  for (const type of ['answer', 'toolCall', 'toolResult', 'complete', undefined]) {
    assert.equal(shouldStopStreamOnErrorFrame({ responseType: type, done: true }), false)
  }
})

test('空帧安全：不抛错也不收束', () => {
  assert.equal(shouldStopStreamOnErrorFrame(null), false)
  assert.equal(shouldStopStreamOnErrorFrame(undefined), false)
})
