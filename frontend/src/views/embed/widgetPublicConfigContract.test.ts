import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

/**
 * 源码扫描守卫：public/ 下的 widget 是独立发布物（纯 JS，不在 vue-tsc / 单测构建面内），
 * 公开 config 契约漂移不会被类型检查发现。
 *
 * 背景（**两段，别只看一段** —— 这个端点吃过两次方向相反的亏）：
 * ① 2026-10-03 点检：公开 config 当时是「裸对象 + camelCase」，而 widget 仍在读更早的
 *    「snake 键」⇒ 浮标图标静默失效、回落默认气泡。据此写了本守卫，并断言"禁止读 payload.data"。
 * ② 2026-10-11 用户实测：浮标**又不显示**配置的图片。真因是 **B200（2026-10-10 全仓 API 外壳化）**
 *    把该端点也包成了 `{code,message,data}`（契约金片 emb-pub-config.json 已是信封形状），
 *    而 widget 直读 `cfg.launcherIcon` ⇒ undefined。**注意 ① 留下的那条"禁止读 payload.data"
 *    恰好会拦住这次正确的修法** —— 教训：守卫要跟着契约走，不能反过来把过时契约钉死。
 *
 * 现行真不变量（守卫按它写）：
 *  - **外壳形状可变**（裸对象 ↔ `{code,message,data}` 都出现过）⇒ widget 必须两种都吃；
 *    （嵌入脚本一旦发布就散落在第三方页面、无法统一升级）
 *  - **内层键名恒为 camelCase** ⇒ snake 键读法永久禁止。
 */
const widgetSource = readFileSync(
  new URL('../../../public/ragagent-widget.js', import.meta.url),
  'utf8',
)

test('widget 读取公开 config 用 camelCase 键（内层键名不变量）', () => {
  assert.match(widgetSource, /typeof cfg\.primaryColor === 'string'/)
  assert.match(widgetSource, /typeof cfg\.launcherIcon === 'string'/)
})

test('widget 兼容服务端两种外壳形状（裸对象 / {code,message,data}）', () => {
  // 形状在 B200 变过一次（裸对象 → 信封），将来仍可能变；嵌入脚本无法统一升级 ⇒ 必须两种都吃。
  assert.match(widgetSource, /var cfg = payload && payload\.data \? payload\.data : payload/)
})

test('widget 内层键名不带 snake（永久禁止的旧读法）', () => {
  // 用**通用 snake 模式**断言，而不是逐个列举键名 —— 两个原因：
  // ① 本守卫是源码扫描，"列举"会把 snake 字面量本身写进源码，反过来触发
  //    「前端契约键棘轮」守卫（B226 首版即踩，B230 修正）；
  // ② 通用模式更严格：任何 `cfg.xxx_yyy` 形态（不止列举的那几个）都拦得住。
  assert.doesNotMatch(widgetSource, /cfg\.[A-Za-z]+_[A-Za-z]+/)
})
