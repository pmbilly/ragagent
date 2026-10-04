import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

// B65 回归守卫：渠道发布密钥（publishToken）在**列表刷新**后不能丢。
// 授权边界：列表行与 PUT 响应都不带 token，只有详情/创建/轮换带（api/embed 顶部注释）。
// 因此面板必须把「本会话见过的 token」记住并在每次 load() 之后贴回——
// 旧实现直接 `allChannels.value = res || []`，保存（PUT → load）后嵌入代码立刻
// 退化成 `<!-- 加载渠道密钥失败，请关闭后重新打开该渠道。 -->`（真机复现）。

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..')

test('嵌入渠道面板：密钥跨 load() 保留（remember / hydrate / forget 三处接线）', () => {
  const panel = readFileSync(join(SRC, 'components/AgentEmbedChannelPanel.vue'), 'utf8')

  assert.match(
    panel,
    /allChannels\.value = channelTokens\.hydrate\(res\)/,
    'load() 必须用本会话记忆贴回 token——直接把列表行赋给 allChannels 会让保存后密钥丢失',
  )
  assert.doesNotMatch(
    panel,
    /allChannels\.value = res \|\| \[\]/,
    '不得再用裸列表行覆盖：列表行不带 publishToken（授权边界），会冲掉已在抽屉里展示的密钥',
  )
  assert.match(
    panel,
    /function mergeChannelDetail\(detail: EmbedChannel\) \{\s*\/\/[^\n]*\n\s*channelTokens\.remember\(detail\)/,
    'mergeChannelDetail 必须记住详情/创建/轮换响应里的 token（那是唯一的取值来源）',
  )
  assert.match(
    panel,
    /const removeChannel = async \(id: string\) => \{\s*await deleteEmbedChannel\(id\)\s*channelTokens\.forget\(id\)/,
    '删除渠道后要清掉本会话记忆，避免 id 复用或残留凭据',
  )
  assert.match(
    panel,
    /const tokenFor = \(ch: EmbedChannel\) => ch\.publishToken \|\| ''/,
    'tokenFor 仍只从行上取（记忆通过 hydrate 注入行），保持单一数据来源',
  )
})
