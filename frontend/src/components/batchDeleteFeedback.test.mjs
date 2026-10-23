import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./menu.vue', import.meta.url), 'utf8')

/**
 * 回归背景：删除接口成功时 `ApiResponse.ok()` 的 data 为 null，响应拦截器解包后
 * 调用方拿到的就是 null。旧实现用 `if (res !== undefined && res !== null)` 判成功，
 * 于是「后端已删成功」却被前端判成失败并弹「删除失败」，刷新后会话已消失。
 */
test('批量删除的成功提示不得依赖返回值（删除接口 data=null）', () => {
  assert.doesNotMatch(
    source,
    /res !== undefined && res !== null/,
    '不能用返回值判成功：data=null 是正常成功响应',
  )
  assert.doesNotMatch(
    source,
    /res = await (batchDelSessions|deleteAllSessions)\(/,
    '删除结果不再承接返回值',
  )
  assert.match(
    source,
    /await batchDelSessions\(\[\.\.\.batchSelectedIds\.value\]\)/,
    '批量删除应直接 await；非 2xx / code!=0 由拦截器 reject',
  )
  assert.match(source, /MessagePlugin\.success\(t\('batchManage\.deleteSuccess'\)\)/)
  assert.match(source, /MessagePlugin\.error\(t\('batchManage\.deleteFailed'\)\)/)
})
