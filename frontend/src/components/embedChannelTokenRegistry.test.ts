import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  createEmbedChannelTokenRegistry,
  type EmbedChannelTokenCarrier,
} from './embedChannelTokenRegistry.ts'

// B65：渠道发布密钥的本会话记忆。
// 故障背景（真机复现）：列表行与 PUT 响应都不带 publishToken（授权边界），
// 而保存后 `load()` 用列表行整体重建数组 → 打开抽屉时合并的 token 被冲掉 →
// 嵌入代码退化成「加载渠道密钥失败」。

test('列表刷新后 token 贴回；详情/PUT 响应不带 token 也不丢', () => {
  const reg = createEmbedChannelTokenRegistry()
  // 打开抽屉：详情响应带 token
  reg.remember({ id: 'c1', publishToken: 'em_abc123' })

  // 保存后 load()：列表行**不带** token
  const rows: Array<EmbedChannelTokenCarrier & { name: string }> = [
    { id: 'c1', name: '渠道一' },
    { id: 'c2', name: '渠道二' },
  ]
  const hydrated = reg.hydrate(rows)
  assert.equal(hydrated[0].publishToken, 'em_abc123', '本会话见过的渠道要贴回 token')
  assert.equal('publishToken' in hydrated[1], false, '没见过的渠道不得凭空造 token')
  assert.equal('publishToken' in rows[0], false, '不得原地改动入参（保持纯函数）')
})

test('轮换后用新值覆盖；删除后清理（避免 id 复用串号）', () => {
  const reg = createEmbedChannelTokenRegistry()
  reg.remember({ id: 'c1', publishToken: 'em_old' })
  reg.remember({ id: 'c1', publishToken: 'em_new' }) // 轮换
  const rotated: EmbedChannelTokenCarrier[] = reg.hydrate([{ id: 'c1' }])
  assert.equal(rotated[0].publishToken, 'em_new')
  assert.equal(reg.peek('c1'), 'em_new')

  reg.forget('c1')
  const afterForget: EmbedChannelTokenCarrier[] = reg.hydrate([{ id: 'c1' }])
  assert.equal(afterForget[0].publishToken, undefined, '删除后不得再贴回')
  assert.equal(reg.peek('c1'), '')
})

test('空/缺失 token 不入册；空列表安全', () => {
  const reg = createEmbedChannelTokenRegistry()
  reg.remember({ id: 'c1' })            // 列表行（无 token）
  reg.remember({ id: 'c2', publishToken: '' })
  reg.remember(null)
  reg.remember(undefined)
  assert.equal(reg.peek('c1'), '')
  assert.equal(reg.peek('c2'), '')
  assert.deepEqual(reg.hydrate([]), [])
  assert.deepEqual(reg.hydrate(null), [])
})
