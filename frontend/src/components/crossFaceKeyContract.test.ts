import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

/**
 * 跨面对照守卫（源码扫描）——本仓反复栽在"写的人按一套键名、读的人按另一套"上
 * （HANDOFF §15.1.1 B19/B20 教训：静默失效，不报错）。
 *
 * 本文件钉三类形态：
 * ① 侧栏会话"乐观行"：写入侧（views 里 updataMenuChildren({...})）与读取侧
 *    （components/menu.vue 的 menuChildToSessionRow）必须用同一套 camel 键；
 *    曾写成 created_at/updated_at → 时间戳读成 undefined → 新会话落「更早」。
 *    （2026-10-03 点检实锤）
 * ② 表格死插槽：`<template #x>` 的名字必须等于同文件某个 `colKey: 'x'`，
 *    否则插槽永不生效、单元格回落原始值（曾出现 #created_at vs colKey 'createdAt'
 *    → 审计页时间列显示原始 UTC 字符串而不是格式化后的本地时间）。（同上）
 * ③ 知识面卡片视图模型的键一律 camel：Go 时代遗留的 original_file_name /
 *    display_name / error_message 属内部映射层键（接口给的是 fileName/errorMessage），
 *    写读两套命名会静默失效 —— 其中 error_message 全仓无人读，失败原因因此在
 *    卡片上不可见。（2026-10-04 收口，见 §15.1.1）
 */
const SRC = fileURLToPath(new URL('..', import.meta.url))

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) walk(full, out)
    else if (full.endsWith('.vue') || full.endsWith('.ts')) out.push(full)
  }
  return out
}

const files = walk(SRC).filter((f) => !f.endsWith('.test.ts'))

/** 花括号配平地取出从 `start`（指向 `{`）开始的对象字面量文本。 */
function sliceObject(source: string, start: number): string {
  let depth = 0
  for (let i = start; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(start, i + 1)
    }
  }
  return source.slice(start)
}

/**
 * 取出所有会话乐观行的对象字面量。两种写法都要覆盖（红态探针实测过：
 * 只认内联对象会漏掉 `const obj = {...}; updataMenuChildren(obj)` 这种形态，
 * 而 creatChat.vue 恰好就是它 —— 守卫会静默失去判别力）。
 */
function extractMenuChildObjects(source: string): string[] {
  const out: string[] = []
  for (const m of source.matchAll(/updataMenuChildren\(\s*([A-Za-z_$][\w$]*|\{)/g)) {
    const arg = m[1]
    if (arg === '{') {
      out.push(sliceObject(source, (m.index ?? 0) + m[0].length - 1))
      continue
    }
    // 变量形态：取调用点之前、最近的一次同名对象字面量赋值
    const assignRe = new RegExp(`(?:const|let|var)\\s+${arg}\\s*=\\s*\\{`, 'g')
    let last: RegExpExecArray | null = null
    for (let ex = assignRe.exec(source); ex; ex = assignRe.exec(source)) {
      if (ex.index > (m.index ?? 0)) break
      last = ex
    }
    if (last) {
      out.push(sliceObject(source, last.index + last[0].length - 1))
    } else {
      out.push('') // 解析不到 → 由断言报红，避免静默漏检
    }
  }
  return out
}

test('侧栏乐观会话行：写入侧必须提供读取侧要读的时间键（camel）', () => {
  const menu = readFileSync(join(SRC, 'components/menu.vue'), 'utf8')
  // 读取侧契约（改动这里必须同步改所有写入侧，本用例即为同步闸门）
  assert.match(menu, /createdAt: typeof item\.createdAt === 'string'/)
  assert.match(menu, /updatedAt: typeof item\.updatedAt === 'string'/)

  const writers: Array<{ file: string; block: string }> = []
  for (const file of files) {
    for (const block of extractMenuChildObjects(readFileSync(file, 'utf8'))) {
      writers.push({ file, block })
    }
  }
  assert.ok(writers.length >= 1, '未找到任何 updataMenuChildren 写入点（守卫需同步更新）')

  for (const { file, block } of writers) {
    assert.match(block, /createdAt\s*:/, `${file}: 乐观行缺 createdAt（读取侧读不到 → 会话落「更早」）`)
    assert.match(block, /updatedAt\s*:/, `${file}: 乐观行缺 updatedAt`)
    assert.doesNotMatch(block, /\bcreated_at\s*:/, `${file}: 乐观行不得用 snake created_at`)
    assert.doesNotMatch(block, /\bupdated_at\s*:/, `${file}: 乐观行不得用 snake updated_at`)
  }
})

test('知识库文件面：时间读侧必须用接口的 camel 键', () => {
  // 2026-10-03 点检实锤：这几处读 created_at/updated_at（接口给的是 createdAt/updatedAt，
  // 见 api/knowledge 的文件列表与 KB 详情接口）→ 时间恒为空/NaN、信息卡"创建时间"行永不显示。
  const targets = [
    'hooks/useKnowledgeBase.ts',
    'views/knowledge/components/DocumentCardView.vue',
    'views/knowledge/components/DocumentListView.vue',
    'components/KBInfoPopover.vue',
  ]
  for (const rel of targets) {
    const source = readFileSync(join(SRC, rel), 'utf8')
    assert.doesNotMatch(source, /\.(created_at|updated_at)\b/,
      `${rel}: 读接口时间请用 createdAt/updatedAt（camel）`)
  }
})

test('表格插槽名必须等于同文件某个 colKey（防死插槽）', () => {
  for (const file of files.filter((f) => f.endsWith('.vue'))) {
    const source = readFileSync(file, 'utf8')
    const colKeys = new Set([...source.matchAll(/colKey:\s*'([^']+)'/g)].map((m) => m[1]))
    for (const m of source.matchAll(/<template\s+#([a-z0-9]+_[a-z0-9_]+)=/g)) {
      const slot = m[1]
      assert.ok(
        colKeys.has(slot),
        `${file}: 插槽 #${slot} 在同文件里没有对应 colKey（插槽永不生效；colKeys=${[...colKeys].join(', ')}）`,
      )
    }
  }
})

test('知识面卡片视图模型：键一律 camel（防 snake 遗留回流）', () => {
  // 2026-10-04 收口。这三个键只活在前端映射层（useKnowledgeBase 把接口项映射成卡片
  // 视图模型），接口下发的是 camel（fileName / errorMessage）：
  //   original_file_name → originalFileName（全名，供下载）
  //   display_name       → displayName（去扩展名，供卡片展示）
  //   error_message      → errorMessage（失败原因；旧名全仓无人读写=死字段，
  //                        紧凑模式时间线又不显示原因，于是卡片上永远看不到失败原因）
  // 旧名一旦回流，读侧拿到的就是 undefined 且不报错，正是本仓反复踩的坑。
  const legacy: Array<[string, string]> = [
    ['original_file_name', 'originalFileName'],
    ['display_name', 'displayName'],
    ['error_message', 'errorMessage'],
  ]
  const knowledgeFace = [
    ...walk(join(SRC, 'views/knowledge')).filter((f) => !f.endsWith('.test.ts')),
    join(SRC, 'hooks/useKnowledgeBase.ts'),
  ]
  for (const file of knowledgeFace) {
    const source = readFileSync(file, 'utf8')
    for (const [oldKey, camelKey] of legacy) {
      assert.doesNotMatch(
        source,
        new RegExp(`\\b${oldKey}\\b`),
        `${file}: 内部视图模型键请用 camel ${camelKey}（旧名 ${oldKey} 读到的恒为 undefined）`,
      )
    }
  }

  // 写入侧（映射层）必须真的提供这三个 camel 键 —— 只删旧名不补新名同样是断链。
  const mapping = readFileSync(join(SRC, 'hooks/useKnowledgeBase.ts'), 'utf8')
  assert.match(mapping, /\boriginalFileName\s*:/, 'useKnowledgeBase: 卡片映射缺 originalFileName')
  assert.match(mapping, /\bdisplayName\s*[,:]/, 'useKnowledgeBase: 卡片映射缺 displayName')
  // 读取侧：下载名解析（原名称 → camel 键）
  const download = readFileSync(join(SRC, 'views/knowledge/knowledgeDownloadFileName.ts'), 'utf8')
  assert.match(download, /\boriginalFileName\b/, 'knowledgeDownloadFileName: 需读 camel originalFileName')

  // 失败原因必须被消费：接口给 errorMessage，卡片浮层是唯一出口
  // （紧凑模式时间线只渲染阶段点+耗时，不含 lastError）。
  const card = readFileSync(join(SRC, 'views/knowledge/components/DocumentCardView.vue'), 'utf8')
  assert.match(card, /\berrorMessage\b/, 'DocumentCardView: 需消费接口 errorMessage（否则失败原因不可见）')
})
