import assert from 'node:assert/strict'
import { test } from 'node:test'

import { recoverInterruptedTurn } from './streamRecovery.ts'

const noSleep = async () => {}

function pendingMessage(id = 'm1') {
  return { id, role: 'assistant', completed: false }
}

test('服务端已完成：用快照恢复并停止轮询', async () => {
  const message = pendingMessage()
  let attempts = 0
  const outcome = await recoverInterruptedTurn({
    findPending: () => message,
    fetchSnapshot: async () => {
      attempts += 1
      return { id: 'm1', role: 'assistant', completed: true, content: 'done' }
    },
    applySnapshot: (pending, row) => {
      Object.assign(pending as object, { completed: true, content: (row as { content: string }).content })
      return true
    },
    markInterrupted: () => assert.fail('不应该走到收束分支'),
    sleep: noSleep,
  })

  assert.equal(outcome, 'completed')
  assert.equal(attempts, 1)
  assert.deepEqual(message, { id: 'm1', role: 'assistant', completed: true, content: 'done' })
})

test('首次未完成、第二次完成：重试后恢复', async () => {
  const message = pendingMessage()
  let attempts = 0
  const outcome = await recoverInterruptedTurn({
    findPending: () => message,
    fetchSnapshot: async () => {
      attempts += 1
      return { id: 'm1', completed: attempts >= 2, content: 'done' }
    },
    applySnapshot: (pending, row) => {
      if (!(row as { completed: boolean }).completed) return false
      Object.assign(pending as object, { completed: true })
      return true
    },
    markInterrupted: () => assert.fail('不应该走到收束分支'),
    sleep: noSleep,
  })

  assert.equal(outcome, 'completed')
  assert.equal(attempts, 2)
  assert.equal(message.completed, true)
})

test('始终拿不到完成快照：收束消息并返回 interrupted', async () => {
  const message = pendingMessage()
  let attempts = 0
  const outcome = await recoverInterruptedTurn({
    findPending: () => message,
    fetchSnapshot: async () => {
      attempts += 1
      return { id: 'm1', completed: false }
    },
    applySnapshot: () => false,
    markInterrupted: (pending) => Object.assign(pending as object, { completed: true, error: 'connection lost' }),
    attempts: 3,
    sleep: noSleep,
  })

  assert.equal(outcome, 'interrupted')
  assert.equal(attempts, 3)
  assert.deepEqual(message, { id: 'm1', role: 'assistant', completed: true, error: 'connection lost' })
})

test('拉取快照抛错会重试，最终仍收束而不是静默放弃', async () => {
  const message = pendingMessage()
  let attempts = 0
  const outcome = await recoverInterruptedTurn({
    findPending: () => message,
    fetchSnapshot: async () => {
      attempts += 1
      throw new Error('network down')
    },
    applySnapshot: () => false,
    markInterrupted: (pending) => Object.assign(pending as object, { completed: true }),
    attempts: 2,
    sleep: noSleep,
  })

  assert.equal(outcome, 'interrupted')
  assert.equal(attempts, 2)
  assert.equal(message.completed, true)
})

test('没有未完成消息：不做任何事', async () => {
  let fetched = false
  const outcome = await recoverInterruptedTurn({
    findPending: () => undefined,
    fetchSnapshot: async () => {
      fetched = true
      return undefined
    },
    applySnapshot: () => assert.fail('不应调用'),
    markInterrupted: () => assert.fail('不应调用'),
    sleep: noSleep,
  })

  assert.equal(outcome, 'none')
  assert.equal(fetched, false)
})
