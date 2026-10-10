import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import test from 'node:test'

/**
 * 源码扫描守卫（B228）：**裸 fetch 必须自己补解包，或显式标记豁免**。
 *
 * ## 为什么需要它
 *
 * `src/utils/request.ts` 的响应拦截器是**全前端唯一解包点**（2xx 时把「外壳」
 * `{code,message,data}` 拆成 `data`）。但有些场景必须绕过 axios 直接 `fetch`：
 * FormData 上传、SSE 流、二进制下载、独立发布物、以及刻意模拟外部视角的"请求演练"。
 *
 * 这些调用点**必须自己补解包** —— 否则拿到的是外壳，读 `.id` / `.launcherIcon` 恒 `undefined`，
 * 而且**不报错**，只是"没数据"。本仓已因此踩过三次（同一个坑换了三个地方）：
 *   · widget 浮标图标（B226）· API 集成页请求演练卡在第一步（B227）· initialization 多模态（潜伏）
 *
 * 所以：**新增裸 fetch 时，要么调 `unwrapEnvelope`，要么写一行 `raw-fetch-ok: <理由>`**。
 * 两者都没有 ⇒ 这条测试当场变红。（红态已实测验证，见 B228 的批次记录。）
 *
 * ## 边界
 *
 * 只扫 `src/`。`public/` 下的独立发布物（如 `weknora-widget.js`）**不能** import 本仓工具
 * （第三方页面没有依赖），它的契约由 `src/views/embed/widgetPublicConfigContract.test.ts`
 * 单独守（那条守卫要求"两种外壳形状都吃"）。
 */

const SRC_ROOT = new URL('../', import.meta.url).pathname

/** 真 fetch 调用。`\b` 保证不误伤 `Refetch(` 这类名字里含 fetch 的标识符。 */
const FETCH_RE = /\bfetch\s*\(/

/** 合规证据：解包调用，或显式豁免标记。 */
const OK_RE = /unwrapEnvelope|raw-fetch-ok/

/** 判定窗口：fetch 前后各 3 行 —— 要求**就地交代**（注释紧邻调用，或就在附近解包）。
 *  刻意不放大：解包若在几十行之外，本处就该留一句 `raw-fetch-ok: 响应在下方解包`，
 *  否则读者（和守卫）都无法从调用点判断这里有没有处理外壳。 */
const WINDOW = 3

/** 跳过整行注释 —— 文档里提到 `fetch(...)` 不该被当成调用。 */
const COMMENT_RE = /^\s*(\/\/|\*|\/\*)/

function collectSourceFiles(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      collectSourceFiles(full, out)
      continue
    }
    if (!/\.(ts|vue)$/.test(name)) continue
    if (/\.test\.ts$/.test(name)) continue
    out.push(full)
  }
  return out
}

test('裸 fetch 必须调用 unwrapEnvelope，或标记 raw-fetch-ok（含理由）', () => {
  const violations: string[] = []

  for (const file of collectSourceFiles(SRC_ROOT)) {
    const lines = readFileSync(file, 'utf8').split('\n')
    lines.forEach((line, index) => {
      if (COMMENT_RE.test(line) || !FETCH_RE.test(line)) return
      const from = Math.max(0, index - WINDOW)
      const to = Math.min(lines.length, index + WINDOW + 1)
      const window = lines.slice(from, to).join('\n')
      if (OK_RE.test(window)) return
      violations.push(`${file.replace(SRC_ROOT, 'src/')}:${index + 1}  ${line.trim().slice(0, 90)}`)
    })
  }

  assert.deepEqual(
    violations,
    [],
    '以下裸 fetch 既没有解包（unwrapEnvelope）、也没有豁免标记（raw-fetch-ok: 理由）。\n'
      + '绕过唯一解包点 ⇒ 拿到的是外壳 {code,message,data}，读字段恒 undefined 且静默。\n'
      + '修法二选一：① import { unwrapEnvelope } from \'@/utils/request\' 后包一层；'
      + '② 若确实不需要（SSE 流 / blob 二进制 / 代码片段模板），在调用上一行写 `// raw-fetch-ok: <理由>`。\n'
      + violations.join('\n'),
  )
})
