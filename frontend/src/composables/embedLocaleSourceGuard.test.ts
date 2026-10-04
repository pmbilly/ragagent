import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

// B63 回归守卫：渠道语言「跟随浏览器/宿主」必须真的跟随。
// 实锤过的故障：embed 把**渠道默认语言**写进 localStorage（weknora-embed-locale），
// 之后 resolveInitialEmbedLocale() 永远先读它 → 渠道改回「跟随」后访客仍停在旧语言。
// 三条不变量：① 派生语言（渠道默认/URL/浏览器）不落持久值；② 跟随模式要清陈旧值；
// ③ 宿主声明语言走 URL 参数（首屏即对语言），且面板打开前的 setLocale 不丢。

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..')

test('embed 语言：派生不落盘 / 跟随清陈旧值 / 宿主声明走 URL', () => {
  const bridge = readFileSync(join(SRC, 'composables/useEmbedBridge.ts'), 'utf8')
  assert.match(
    bridge,
    /applyDerivedEmbedLocale\(res\.defaultLocale/,
    '渠道默认语言必须走派生路径（不写持久值），否则渠道改语言对老访客永远不生效',
  )
  assert.doesNotMatch(
    bridge,
    /applyEmbedLocale\(res\.defaultLocale/,
    '渠道默认语言不得走持久化路径——它每次加载都能重新推导，写进存储会让「跟随浏览器/宿主」永久失效',
  )
  assert.match(
    bridge,
    /clearStoredEmbedLocale\(\)/,
    '渠道设为「跟随浏览器/宿主」时必须清掉可能的历史持久值，再按浏览器语言渲染',
  )
  assert.match(
    bridge,
    /resolveBrowserEmbedLocale\(\)/,
    '跟随模式要显式取浏览器语言',
  )

  const embed = readFileSync(join(SRC, 'i18n/embed.ts'), 'utf8')
  assert.match(
    embed,
    /export function applyDerivedEmbedLocale\(raw: string, localeRef\?: LocaleRef\) \{\s*setActiveEmbedLocale\(raw, localeRef, false\)/,
    'applyDerivedEmbedLocale 必须是 setActiveEmbedLocale(..., persist=false)',
  )
  assert.match(
    embed,
    /export function applyEmbedLocale\(raw: string, localeRef\?: LocaleRef\) \{\s*setActiveEmbedLocale\(raw, localeRef, true\)/,
    'applyEmbedLocale（显式选择）才允许 persist=true',
  )
  assert.match(
    embed,
    /export function syncEmbedLocaleFromUrl\(localeRef: LocaleRef\): boolean \{[\s\S]{0,200}?applyDerivedEmbedLocale\(fromUrl, localeRef\)/,
    'URL ?locale= 属宿主声明（每次加载都会重新给出）→ 走派生路径，不得写持久值',
  )

  const widget = readFileSync(join(SRC, '../public/weknora-widget.js'), 'utf8')
  assert.match(
    widget,
    /embedUrl \+= '\?locale=' \+ encodeURIComponent\(hostLocale\)/,
    'widget 脚本要把宿主声明的 locale 拼进 iframe URL（首屏即对语言），否则「跟随宿主」只能等 iframe 加载后再切',
  )
  assert.match(
    widget,
    /if \(!iframeReady\) \{\s*\/\/[^\n]*\n\s*pendingLocale = loc;/,
    '面板打开前调用的 setLocale 必须先记住、握手后再发（旧实现直接 post → 静默丢失）',
  )
  assert.match(
    widget,
    /if \(pendingLocale\) \{\s*postHostPayload\('set_locale'/,
    'ready 握手后要补发 pendingLocale',
  )
})
