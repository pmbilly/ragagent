import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

import {
  hasEmbedTokenRefresher,
  refreshEmbedTokenShared,
  registerEmbedTokenRefresher,
  resetEmbedTokenRefreshForTests,
} from './embedTokenRefresh.ts'

test.afterEach(() => {
  resetEmbedTokenRefreshForTests()
})

test('no registered refresher resolves null (caller keeps the original 401)', async () => {
  assert.equal(hasEmbedTokenRefresher(), false)
  assert.equal(await refreshEmbedTokenShared(), null)
})

test('concurrent 401s share a single exchange call', async () => {
  let calls = 0
  let release!: (token: string) => void
  const pending = new Promise<string>((resolve) => {
    release = resolve
  })
  registerEmbedTokenRefresher(async () => {
    calls += 1
    return pending
  })

  const first = refreshEmbedTokenShared()
  const second = refreshEmbedTokenShared()
  // 换票在下一个微任务才真正发起：先让出一次，再断言只调用了一次。
  await Promise.resolve()
  assert.equal(calls, 1)

  release('ems_fresh')
  assert.deepEqual(await Promise.all([first, second]), ['ems_fresh', 'ems_fresh'])
  assert.equal(calls, 1)
})

test('a failing refresher resolves null instead of rejecting', async () => {
  registerEmbedTokenRefresher(async () => {
    throw new Error('exchange failed')
  })
  assert.equal(await refreshEmbedTokenShared(), null)
})

test('a null token from the refresher surfaces as null', async () => {
  registerEmbedTokenRefresher(async () => null)
  assert.equal(await refreshEmbedTokenShared(), null)
})

test('registration can be cleared and re-armed', async () => {
  registerEmbedTokenRefresher(async () => 'ems_1')
  assert.equal(await refreshEmbedTokenShared(), 'ems_1')
  registerEmbedTokenRefresher(null)
  assert.equal(await refreshEmbedTokenShared(), null)
  registerEmbedTokenRefresher(async () => 'ems_2')
  assert.equal(await refreshEmbedTokenShared(), 'ems_2')
})

test('both request planes and the bridge wire the shared refresh', () => {
  const requestSource = readFileSync(new URL('./request.ts', import.meta.url), 'utf8')
  assert.match(requestSource, /refreshEmbedTokenShared/)
  assert.match(requestSource, /_embedRetry/)
  // 换票请求自身不能再触发重试（与单飞调用互等）
  assert.match(requestSource, /includes\('\/exchange'\)/)

  const streamSource = readFileSync(
    new URL('../api/chat/streame.ts', import.meta.url), 'utf8')
  assert.match(streamSource, /refreshEmbedToken/)
  assert.match(streamSource, /Embed \$\{authToken\}/)

  const bridgeSource = readFileSync(
    new URL('../composables/useEmbedBridge.ts', import.meta.url), 'utf8')
  assert.match(bridgeSource, /registerEmbedTokenRefresher\(refreshToken\)/)
  assert.match(bridgeSource, /registerEmbedTokenRefresher\(null\)/)
  // 宿主再次推票必须走"换票更新"（不能因 bootstrapped 直接丢弃）
  assert.match(bridgeSource, /await adoptHostToken\(embedToken\)/)
})
