import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  buildSkillFilePreview,
  describeDeleteImpact,
  EMPTY_SKILL_FORM,
  hasErrors,
  toCreatePayload,
  validateSkillForm,
} from './skillManagementForm.ts'

const valid = {
  slug: 'kb-faq-curator',
  name: 'kb-faq-curator',
  description: '把文档整理成 FAQ',
  content: '## 步骤\n1. 先检索',
}

test('合法表单无错误', () => {
  assert.equal(hasErrors(validateSkillForm(valid)), false)
})

test('slug 规则与后端一致（小写/数字/连字符，首尾非连字符）', () => {
  assert.equal(validateSkillForm({ ...valid, slug: 'KB_FAQ' }).slug, 'slugInvalid')
  assert.equal(validateSkillForm({ ...valid, slug: '-lead' }).slug, 'slugInvalid')
  assert.equal(validateSkillForm({ ...valid, slug: 'trail-' }).slug, 'slugInvalid')
  assert.equal(validateSkillForm({ ...valid, slug: 'a'.repeat(65) }).slug, 'slugInvalid')
  assert.equal(validateSkillForm({ ...valid, slug: '' }).slug, 'slugRequired')
})

test('名称不能含空格（运行期会静默回落成 slug，后端也会拒绝）', () => {
  assert.equal(validateSkillForm({ ...valid, name: 'KB FAQ 整理' }).name, 'nameInvalid')
  // 中文/下划线/连字符合法（与运行期 NAME_PATTERN 一致）
  assert.equal(validateSkillForm({ ...valid, name: '知识库FAQ整理' }).name, undefined)
  assert.equal(validateSkillForm({ ...valid, name: 'kb_faq-1' }).name, undefined)
})

test('描述与正文必填', () => {
  assert.equal(validateSkillForm({ ...valid, description: '   ' }).description, 'descriptionRequired')
  assert.equal(validateSkillForm({ ...valid, content: '\n' }).content, 'contentRequired')
  assert.equal(hasErrors(validateSkillForm(EMPTY_SKILL_FORM)), true)
})

test('预览形状与后端 assembleSkillFile 一致（含单引号转义）', () => {
  const preview = buildSkillFilePreview({
    slug: 'skill-a',
    name: 'skill-a',
    description: "it's a test: with colons",
    content: '正文',
  })
  assert.match(preview, /^---\nname: 'skill-a'\nslug: 'skill-a'\ndescription: 'it''s a test: with colons'\n---\n\n正文\n$/)
})

test('提交载荷去空白', () => {
  const payload = toCreatePayload({ ...valid, slug: ' kb-faq ', content: '  正文  ' })
  assert.equal(payload.slug, 'kb-faq')
  assert.equal(payload.content, '正文')
})

test('删除影响面：无引用返回 null，有引用给出数量/名称/allMode', () => {
  assert.equal(describeDeleteImpact([]), null)
  const impact = describeDeleteImpact([
    { agentId: '1', agentName: 'a', allMode: false },
    { agentId: '2', agentName: 'b', allMode: true },
  ])
  assert.equal(impact?.count, 2)
  assert.deepEqual(impact?.names, ['a', 'b'])
  assert.equal(impact?.hasAllMode, true)
})
