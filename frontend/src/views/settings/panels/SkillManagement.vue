<template>
  <div class="skill-management">
    <header class="section-header sm-header">
      <div class="sm-title-block">
        <h2>{{ t('skillManagement.title') }}</h2>
        <p class="section-description">{{ t('skillManagement.description') }}</p>
      </div>
      <div class="sm-actions">
        <t-button
          size="small"
          variant="outline"
          :disabled="loading"
          @click="reload"
        >
          <template #icon><t-icon name="refresh" /></template>
          {{ t('skillManagement.refresh') }}
        </t-button>
        <t-button size="small" theme="primary" @click="openCreate">
          <template #icon><t-icon name="add" /></template>
          {{ t('skillManagement.create') }}
        </t-button>
      </div>
    </header>

    <div v-if="error" class="sm-state sm-state--error" role="alert">
      <t-icon name="error-circle" size="20px" />
      <div class="sm-state-copy">
        <strong>{{ t('skillManagement.loadFailed') }}</strong>
        <span>{{ error }}</span>
      </div>
      <t-button size="small" variant="outline" @click="reload">{{ t('skillManagement.refresh') }}</t-button>
    </div>

    <div v-else-if="!loading && skills.length === 0" class="sm-state">
      <t-icon name="extension" size="22px" />
      <div class="sm-state-copy">
        <strong>{{ t('skillManagement.empty') }}</strong>
        <span>{{ t('skillManagement.emptyHint') }}</span>
      </div>
      <t-button size="small" theme="primary" @click="openCreate">{{ t('skillManagement.create') }}</t-button>
    </div>

    <t-table
      v-else
      :data="skills"
      :columns="columns"
      row-key="id"
      size="small"
      :loading="loading"
      class="sm-table"
    >
      <template #name="{ row }">
        <span class="sm-name">{{ row.name }}</span>
        <t-tag v-if="row.readOnly" size="small" variant="light-outline" class="sm-builtin-tag">
          {{ t('skillManagement.builtin') }}
        </t-tag>
      </template>
      <template #slug="{ row }">
        <code class="sm-slug">{{ row.slug }}</code>
      </template>
      <template #referencedBy="{ row }">
        <t-tag v-if="row.referencedBy?.length" size="small" variant="light-outline" theme="primary">
          {{ t('skillManagement.referencedByCount', { count: row.referencedBy.length }) }}
        </t-tag>
        <span v-else class="sm-muted">{{ t('skillManagement.referencedByNone') }}</span>
      </template>
      <template #updatedAt="{ row }">
        {{ formatDateTime(row.updatedAt) }}
      </template>
      <template #actions="{ row }">
        <div class="sm-row-actions">
          <!-- 平台内置层只读（后端 403）：只给「查看」，不给编辑/删除 -->
          <template v-if="row.readOnly">
            <t-link theme="primary" hover="color" @click="openView(row)">
              {{ t('skillManagement.view') }}
            </t-link>
          </template>
          <template v-else>
            <t-link theme="primary" hover="color" @click="openEdit(row)">
              {{ t('skillManagement.edit') }}
            </t-link>
            <t-link theme="danger" hover="color" @click="askDelete(row)">
              {{ t('skillManagement.delete') }}
            </t-link>
          </template>
        </div>
      </template>
    </t-table>

    <!-- 新建 / 编辑：同一个弹窗两种模式；frontmatter 始终由服务端组装 -->
    <t-dialog
      v-model:visible="editorVisible"
      :header="editorHeader"
      width="720px"
      :footer="editorMode !== 'view'"
      :confirm-btn="{
        content: editorMode === 'create' ? t('skillManagement.createSubmit') : t('skillManagement.saveSubmit'),
        loading: submitting,
        disabled: saveBlocked,
      }"
      :cancel-btn="{ content: t('skillManagement.createCancel') }"
      @confirm="submitEditor"
      @close="closeEditor"
    >
      <div v-if="loadingDraft" class="sm-draft-loading">{{ t('skillManagement.loadingDraft') }}</div>

      <div v-else class="sm-form">
        <div class="sm-field">
          <label>{{ t('skillManagement.slugLabel') }}</label>
          <t-input
            v-model="form.slug"
            :placeholder="t('skillManagement.slugPlaceholder')"
            :disabled="editorMode !== 'create'"
            :status="errors.slug ? 'error' : undefined"
          />
          <p v-if="errors.slug" class="sm-field-error">{{ t(`skillManagement.${errors.slug}`) }}</p>
          <p v-else class="sm-field-hint">
            {{ editorMode === 'create' ? t('skillManagement.slugHint') : t('skillManagement.slugImmutable') }}
          </p>
        </div>

        <div class="sm-field">
          <label>{{ t('skillManagement.nameLabel') }}</label>
          <t-input
            v-model="form.name"
            :placeholder="t('skillManagement.namePlaceholder')"
            :disabled="editorMode === 'view'"
            :status="errors.name || renameBlock ? 'error' : undefined"
          />
          <p v-if="errors.name" class="sm-field-error">{{ t(`skillManagement.${errors.name}`) }}</p>
          <p v-else-if="renameBlock" class="sm-field-error">
            {{ t('skillManagement.renameBlocked', {
              count: renameBlock.count,
              agents: renameBlock.names.join('、'),
            }) }}
            <span v-if="renameBlock.hasAllMode">{{ t('skillManagement.deleteAllModeHint') }}</span>
          </p>
          <p v-else class="sm-field-hint">{{ t('skillManagement.nameHint') }}</p>
        </div>

        <div class="sm-field">
          <label>{{ t('skillManagement.descriptionLabel') }}</label>
          <t-input
            v-model="form.description"
            :placeholder="t('skillManagement.descriptionPlaceholder')"
            :disabled="editorMode === 'view'"
            :status="errors.description ? 'error' : undefined"
          />
          <p v-if="errors.description" class="sm-field-error">{{ t(`skillManagement.${errors.description}`) }}</p>
        </div>

        <div class="sm-field">
          <label>{{ t('skillManagement.contentLabel') }}</label>
          <t-textarea
            v-model="form.content"
            :placeholder="t('skillManagement.contentPlaceholder')"
            :autosize="{ minRows: 6, maxRows: 14 }"
            :disabled="editorMode === 'view'"
            :status="errors.content ? 'error' : undefined"
          />
          <p v-if="errors.content" class="sm-field-error">{{ t(`skillManagement.${errors.content}`) }}</p>
        </div>

        <details class="sm-preview">
          <summary>{{ t('skillManagement.previewTitle') }}</summary>
          <pre>{{ preview }}</pre>
        </details>
      </div>
    </t-dialog>

    <!-- 删除：被引用时展示影响面并要求二次确认（force） -->
    <t-dialog
      v-model:visible="deleteVisible"
      theme="warning"
      :header="t('skillManagement.deleteTitle')"
      width="520px"
      :confirm-btn="{
        content: deleteImpact ? t('skillManagement.deleteForce') : t('skillManagement.deleteSubmit'),
        theme: 'danger',
        loading: deleting,
      }"
      :cancel-btn="{ content: t('skillManagement.createCancel') }"
      @confirm="confirmDelete"
    >
      <p>{{ t('skillManagement.deleteConfirm', { name: deleteTarget?.name || '' }) }}</p>
      <p v-if="deleteImpact" class="sm-warn">
        {{ t('skillManagement.deleteReferenced', {
          count: deleteImpact.count,
          agents: deleteImpact.names.join('、'),
        }) }}
        <br />
        <span v-if="deleteImpact.hasAllMode">{{ t('skillManagement.deleteAllModeHint') }}</span>
      </p>
    </t-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useI18n } from 'vue-i18n'

import {
  createSkill,
  deleteSkill,
  getSkillCatalogItem,
  listSkillCatalog,
  updateSkill,
  type SkillCatalogItem,
  type SkillReference,
} from '@/api/skills'
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
  type SkillFormErrors,
  type SkillFormMode,
  type SkillFormValues,
} from './skillManagementForm'

const { t } = useI18n()

const skills = ref<SkillCatalogItem[]>([])
const loading = ref(false)
const loadedOnce = ref(false)
const error = ref('')

const columns = computed(() => [
  { colKey: 'name', title: t('skillManagement.columns.name'), width: 180 },
  { colKey: 'slug', title: t('skillManagement.columns.slug'), width: 180 },
  {
    colKey: 'description',
    title: t('skillManagement.columns.description'),
    // 描述过长时 TDesign 的 ellipsis 气泡**直接复用单元格 VNode**（ellipsis.mjs：
    // content: () => cellNode），所以次要色必须下放到列级 className（td 上），
    // 不能再包一层带颜色的 span——否则灰字会被带进深色气泡里，看不清。
    ellipsis: true,
  },
  { colKey: 'referencedBy', title: t('skillManagement.columns.referencedBy'), width: 130 },
  { colKey: 'updatedAt', title: t('skillManagement.columns.updatedAt'), width: 170 },
  { colKey: 'actions', title: t('skillManagement.columns.actions'), width: 130, fixed: 'right' as const },
])

async function reload() {
  loading.value = true
  error.value = ''
  try {
    const res = await listSkillCatalog()
    skills.value = res?.skills || []
    loadedOnce.value = true
  } catch (e: any) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

onMounted(reload)

// ── 新建 / 编辑 / 查看（同一弹窗三种模式）──────────────────────────────
// view 只用于平台内置层（readOnly）：字段全禁用、隐藏页脚（后端也只读）。
type EditorMode = SkillFormMode | 'view'

const editorVisible = ref(false)
const editorMode = ref<EditorMode>('create')
const editorTarget = ref<SkillCatalogItem | null>(null)
const editorRefs = ref<SkillReference[]>([])
const loadingDraft = ref(false)
const submitting = ref(false)
const form = ref<SkillFormValues>({ ...EMPTY_SKILL_FORM })
const errors = ref<SkillFormErrors>({})

const preview = computed(() => buildSkillFilePreview(form.value))

const editorHeader = computed(() => {
  const name = editorTarget.value?.name || ''
  if (editorMode.value === 'create') return t('skillManagement.createTitle')
  if (editorMode.value === 'view') return t('skillManagement.viewTitle', { name })
  return t('skillManagement.editTitle', { name })
})

/**
 * 改名拦截：name 是运行期身份（agent 的 selectedSkills 存的是它），被引用时改名会让那些
 * 智能体静默失去技能（后端也会 409）——这里先行挡住并说明原因。
 */
const renameBlock = computed(() => (
  editorMode.value === 'edit'
    ? describeRenameBlock(editorTarget.value?.name || '', form.value.name, editorRefs.value)
    : null
))

const saveBlocked = computed(() => loadingDraft.value || !!renameBlock.value)

function openCreate() {
  editorMode.value = 'create'
  editorTarget.value = null
  editorRefs.value = []
  form.value = { ...EMPTY_SKILL_FORM }
  errors.value = {}
  editorVisible.value = true
}

/** 平台内置层：只读查看（后端 PUT/DELETE 也是 403）。 */
async function openView(row: SkillCatalogItem) {
  return openEditorWithDraft('view', row)
}

async function openEdit(row: SkillCatalogItem) {
  return openEditorWithDraft('edit', row)
}

async function openEditorWithDraft(mode: EditorMode, row: SkillCatalogItem) {
  editorMode.value = mode
  editorTarget.value = row
  editorRefs.value = row.referencedBy || []
  // 先用列表里的字段占位，正文等草稿（GET /catalog/{id}）回来后再填
  form.value = formFromDetail(row)
  errors.value = {}
  editorVisible.value = true
  loadingDraft.value = true
  try {
    const detail = await getSkillCatalogItem(row.id)
    form.value = formFromDetail(detail)
    editorRefs.value = detail.referencedBy || editorRefs.value
  } catch (e: any) {
    MessagePlugin.error(e?.message || t('skillManagement.loadDraftFailed'))
    editorVisible.value = false
  } finally {
    loadingDraft.value = false
  }
}

function closeEditor() {
  editorVisible.value = false
  errors.value = {}
  editorTarget.value = null
  editorRefs.value = []
}

async function submitEditor() {
  if (editorMode.value === 'view') return
  const mode: SkillFormMode = editorMode.value
  const found = validateSkillForm(form.value, mode)
  errors.value = found
  if (hasErrors(found) || saveBlocked.value) {
    if (renameBlock.value) MessagePlugin.warning(t('skillManagement.renameBlocked', {
      count: renameBlock.value.count,
      agents: renameBlock.value.names.join('、'),
    }))
    return
  }
  submitting.value = true
  try {
    if (mode === 'create') {
      await createSkill(toCreatePayload(form.value))
      MessagePlugin.success(t('skillManagement.createSuccess'))
    } else {
      await updateSkill(editorTarget.value!.id, toUpdatePayload(form.value))
      MessagePlugin.success(t('skillManagement.saveSuccess'))
    }
    editorVisible.value = false
    await reload()
  } catch (e: any) {
    MessagePlugin.error(e?.message || t(mode === 'create' ? 'skillManagement.createFailed' : 'skillManagement.saveFailed'))
  } finally {
    submitting.value = false
  }
}

// ── 删除 ──────────────────────────────────────────────────────────────
const deleteVisible = ref(false)
const deleting = ref(false)
const deleteTarget = ref<SkillCatalogItem | null>(null)
const deleteImpact = computed(() => describeDeleteImpact(deleteTarget.value?.referencedBy || []))

function askDelete(row: SkillCatalogItem) {
  deleteTarget.value = row
  deleteVisible.value = true
}

async function confirmDelete() {
  const row = deleteTarget.value
  if (!row) return
  deleting.value = true
  try {
    await deleteSkill(row.id, !!deleteImpact.value)
    MessagePlugin.success(t('skillManagement.deleteSuccess'))
    deleteVisible.value = false
    await reload()
  } catch (e: any) {
    MessagePlugin.error(e?.message || t('skillManagement.deleteFailed'))
  } finally {
    deleting.value = false
  }
}

function formatDateTime(value?: string) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped lang="less">
.skill-management {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sm-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.sm-title-block {
  h2 {
    margin: 0 0 8px;
    color: var(--td-text-color-primary);
    font-size: 20px;
    font-weight: 600;
    line-height: 1.3;
    letter-spacing: -0.01em;
  }

  // 与系统管理组其它页（RuntimeQueues 等）同一份头部说明样式：
  // 这类 .section-* 类没有全局定义，各页在自己的 scoped 块里自备。
  .section-description {
    max-width: 560px;
    margin: 0;
    color: var(--td-text-color-secondary);
    font-size: 14px;
    line-height: 1.6;
    text-wrap: pretty;
  }
}

.sm-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.sm-state {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 28px 20px;
  border: 1px dashed var(--td-component-stroke);
  border-radius: 6px;
  color: var(--td-text-color-secondary);

  &--error {
    border-style: solid;
    border-color: var(--td-error-color-3);
    color: var(--td-error-color);
  }
}

.sm-state-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;

  strong {
    color: var(--td-text-color-primary);
    font-weight: 600;
  }
}

.sm-name {
  font-weight: 500;
}

.sm-slug {
  font-family: var(--td-font-family-monospace, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  color: var(--td-text-color-secondary);
}

.sm-builtin-tag {
  margin-left: 6px;
}

// 描述列的次要色：挂在 td 上（列级 className），悬浮气泡里是纯文本，
// 因此气泡使用 tooltip 自身的文字色，不会变成灰字黑底。
:deep(.sm-desc-cell) {
  color: var(--td-text-color-secondary);
}

.sm-muted {
  color: var(--td-text-color-placeholder);
}

.sm-row-actions {
  display: flex;
  gap: 12px;
}

.sm-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.sm-draft-loading {
  padding: 24px 0;
  color: var(--td-text-color-secondary);
}

.sm-field {
  display: flex;
  flex-direction: column;
  gap: 6px;

  label {
    font-size: 13px;
    color: var(--td-text-color-primary);
  }
}

.sm-field-hint,
.sm-field-error {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

.sm-field-hint {
  color: var(--td-text-color-placeholder);
}

.sm-field-error {
  color: var(--td-error-color);
}

.sm-preview {
  summary {
    cursor: pointer;
    font-size: 13px;
    color: var(--td-text-color-secondary);
  }

  pre {
    margin: 8px 0 0;
    padding: 10px 12px;
    max-height: 220px;
    overflow: auto;
    background: var(--td-bg-color-container-hover);
    border-radius: 4px;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}

.sm-warn {
  margin: 10px 0 0;
  color: var(--td-warning-color);
  font-size: 13px;
  line-height: 1.6;
}
</style>
