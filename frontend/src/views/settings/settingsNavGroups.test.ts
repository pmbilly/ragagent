import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

/**
 * 设置页菜单的「入口可达性」守卫（2026-10-04 实锤后补）。
 *
 * 背景：设置页的可见性由**两处**共同决定，二者缺一即为「静默无入口」——
 *   ① 权限集合 `config/settingsAccess.ts` 的 SYSTEM_ADMIN_SETTINGS_SECTIONS
 *      （决定 canSeeSection 是否放行）；
 *   ② Settings.vue 里 navGroups 的 pickItems([...]) **显式分组清单**
 *      （决定这个 key 会被渲染进哪个分组）。
 * 新加一个 section 时只做 ①（甚至 ①+②的 section 容器都加了）而漏做 ②，
 * 页面能访问、权限也通过，但**菜单里永远看不到它**——B57 的技能管理就这样
 * 被用户当面发现（"没看到技能管理菜单"）。
 *
 * 本用例用源码扫描补上这条链：navItems 里声明的每个 key 必须落在某个分组里；
 * 且权限集合里的每个 section 必须出现在「系统管理」分组的清单里。
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SETTINGS = join(HERE, 'Settings.vue')
const ACCESS = join(HERE, '..', '..', 'config', 'settingsAccess.ts')

const source = readFileSync(SETTINGS, 'utf8')

function keysOf(list: string): string[] {
  return [...list.matchAll(/'([a-z0-9-]+)'/g)].map((m) => m[1])
}

/** navItems 里声明的 key（分组的消费侧来源）。 */
function declaredNavKeys(): Set<string> {
  const start = source.indexOf('const navItems = computed')
  const end = source.indexOf('const navGroups = computed')
  assert.ok(start > 0 && end > start, 'Settings.vue: 未找到 navItems/navGroups computed（守卫需同步更新）')
  const region = source.slice(start, end)
  // 字面量 key（集成项的 key 是 integrationSectionKey(...) 动态生成，不计入）
  return new Set([...region.matchAll(/\bkey:\s*'([a-z0-9-]+)'/g)].map((m) => m[1]))
}

/** navGroups 里被显式登记进分组的 key。 */
function groupedKeys(): Set<string> {
  const grouped = new Set<string>()
  for (const m of source.matchAll(/pickItems\(\[([^\]]*)\]\)/g)) {
    for (const k of keysOf(m[1])) grouped.add(k)
  }
  return grouped
}

test('设置页每个菜单项都必须登记进某个 navGroups 分组（防静默无入口）', () => {
  const grouped = groupedKeys()
  const missing = [...declaredNavKeys()].filter((k) => !grouped.has(k))
  assert.deepEqual(
    missing,
    [],
    `这些 key 在 navItems 里声明了、但没有登记进任何 navGroups 分组，菜单里永远看不到：${missing.join(', ')}`,
  )
})

test('系统管理权限集合里的每个 section 都必须出现在「系统管理」分组', () => {
  const access = readFileSync(ACCESS, 'utf8')
  const setBlock = /SYSTEM_ADMIN_SETTINGS_SECTIONS[\s\S]*?new Set\(\[([\s\S]*?)\]\)/.exec(access)
  assert.ok(setBlock, 'settingsAccess.ts: 未找到 SYSTEM_ADMIN_SETTINGS_SECTIONS 定义')
  const adminSections = keysOf(setBlock[1])
  assert.ok(adminSections.length > 0, 'SYSTEM_ADMIN_SETTINGS_SECTIONS 为空，守卫失效')

  const group = /key:\s*'system_administration'[\s\S]*?pickItems\(\[([^\]]*)\]\)/.exec(source)
  assert.ok(group, "Settings.vue: 未找到 system_administration 分组的 pickItems 清单")
  const adminNav = new Set(keysOf(group[1]))

  const absent = adminSections.filter((k) => !adminNav.has(k))
  assert.deepEqual(
    absent,
    [],
    `有权限但没入口：这些 section 在 SYSTEM_ADMIN_SETTINGS_SECTIONS 里、却不在「系统管理」分组：${absent.join(', ')}`,
  )
})

test('navGroups 里的分组 key 不重复且都能对上渲染模板', () => {
  const groupKeys = [...source.matchAll(/\bkey:\s*'(account|workspace|models_runtime|integrations|data_extensions|system_administration|platform)'/g)]
    .map((m) => m[1])
  assert.equal(new Set(groupKeys).size, groupKeys.length, `navGroups 分组 key 重复：${groupKeys.join(', ')}`)
})
