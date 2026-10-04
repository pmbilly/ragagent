import type { CreateSkillPayload, SkillReference } from '@/api/skills'

/**
 * 技能新建表单的**纯逻辑**（校验 + SKILL.md 预览 + 删除提示语料）。
 *
 * 抽出来的理由：这些规则与后端 `SkillCatalogService` 一一对应（后端会 400 兜底），
 * 放在这里可以做无 DOM 单测——UI 只负责渲染错误文案。
 */

/** 与后端 `SkillCatalogService.SLUG_PATTERN` 完全一致。 */
export const SLUG_PATTERN = /^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$/

/** 与运行期 `Skill.NAME_PATTERN` 一致：字母/数字/连字符/下划线（\p{L} 含中文），不能有空格。 */
export const NAME_PATTERN = /^[\p{L}\p{N}_-]+$/u

export interface SkillFormValues {
  slug: string
  name: string
  description: string
  content: string
}

/** 校验错误：键 = 字段名，值 = i18n key 后缀（页面负责翻译）。 */
export type SkillFormErrors = Partial<Record<keyof SkillFormValues, string>>

export const EMPTY_SKILL_FORM: SkillFormValues = {
  slug: '',
  name: '',
  description: '',
  content: '',
}

export function validateSkillForm(values: SkillFormValues): SkillFormErrors {
  const errors: SkillFormErrors = {}
  const slug = values.slug.trim()
  if (!slug) {
    errors.slug = 'slugRequired'
  } else if (!SLUG_PATTERN.test(slug)) {
    errors.slug = 'slugInvalid'
  }

  const name = values.name.trim()
  if (!name) {
    errors.name = 'nameRequired'
  } else if (!NAME_PATTERN.test(name)) {
    // 带空格的名称会被运行期静默改名（applyInstallName 回落 slug），后端也会拒绝
    errors.name = 'nameInvalid'
  }

  if (!values.description.trim()) {
    errors.description = 'descriptionRequired'
  }
  if (!values.content.trim()) {
    errors.content = 'contentRequired'
  }
  return errors
}

export function hasErrors(errors: SkillFormErrors): boolean {
  return Object.keys(errors).length > 0
}

/**
 * 预览：服务端会组装成什么形状的 SKILL.md（单引号 YAML 标量 + 正文）。
 * 与后端 `assembleSkillFile` 同形——这里只用于给人看，不参与提交。
 */
export function buildSkillFilePreview(values: SkillFormValues): string {
  const quote = (v: string) => `'${v.replace(/\r?\n/g, ' ').trim().replace(/'/g, "''")}'`
  const lines = ['---', `name: ${quote(values.name)}`, `slug: ${quote(values.slug)}`]
  if (values.description.trim()) {
    lines.push(`description: ${quote(values.description)}`)
  }
  lines.push('---', '', values.content.trim(), '')
  return lines.join('\n')
}

export function toCreatePayload(values: SkillFormValues): CreateSkillPayload {
  return {
    slug: values.slug.trim(),
    name: values.name.trim(),
    description: values.description.trim(),
    content: values.content.trim(),
  }
}

/**
 * 删除前的影响面：被引用且未强删时后端会 409，因此页面要先提示。
 * 返回 null = 可以直接删。
 */
export function describeDeleteImpact(refs: SkillReference[]): { count: number; names: string[]; hasAllMode: boolean } | null {
  if (!refs || refs.length === 0) {
    return null
  }
  return {
    count: refs.length,
    names: refs.map((r) => r.agentName),
    hasAllMode: refs.some((r) => r.allMode),
  }
}
