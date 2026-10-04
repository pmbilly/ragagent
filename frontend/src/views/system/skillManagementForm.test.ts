import assert from 'node:assert/strict'
import { test } from 'node:test'

import {
  buildSkillFilePreview,
  describeDeleteImpact,
  describeRenameBlock,
  EMPTY_SKILL_FORM,
  formFromDetail,
  hasErrors,
  toCreatePayload,
  toUpdatePayload,
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

test('编辑模式：跳过 slug 校验，提交载荷不带 slug', () => {
  // slug 只读展示、服务端不接收 → 编辑态不该因 slug 报错
  assert.equal(validateSkillForm({ ...valid, slug: '' }, 'edit').slug, undefined)
  assert.equal(validateSkillForm({ ...valid, slug: '' }, 'create').slug, 'slugRequired')
  const payload = toUpdatePayload({ ...valid, name: ' 新名 ', description: ' 说明 ', content: ' 正文 ' })
  assert.deepEqual(payload, { name: '新名', description: '说明', content: '正文' })
  assert.equal('slug' in payload, false)
})

test('编辑草稿回填为表单值', () => {
  assert.deepEqual(
    formFromDetail({ slug: 's', name: 'n', description: 'd', content: 'c' }),
    { slug: 's', name: 'n', description: 'd', content: 'c' },
  )
  assert.deepEqual(formFromDetail({}), EMPTY_SKILL_FORM)
})

test('改名拦截：只改描述/正文放行；被引用时改名被挡（与后端 409 同判据）', () => {
  const refs = [{ agentId: '1', agentName: 'probe', allMode: false }]
  assert.equal(describeRenameBlock('a', 'a', refs), null, '同名不算改名')
  assert.equal(describeRenameBlock('a', ' a ', refs), null, '去空白后同名也算改名')
  assert.equal(describeRenameBlock('a', 'b', []), null, '无引用可改名')
  const blocked = describeRenameBlock('a', 'b', refs)
  assert.equal(blocked?.count, 1)
  assert.deepEqual(blocked?.names, ['probe'])
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
