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
      </template>
      <template #slug="{ row }">
        <code class="sm-slug">{{ row.slug }}</code>
      </template>
      <template #description="{ row }">
        <span class="sm-desc">{{ row.description }}</span>
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
          <t-link theme="primary" hover="color" @click="openDetail(row)">
            {{ t('skillManagement.view') }}
          </t-link>
          <t-link theme="danger" hover="color" @click="askDelete(row)">
            {{ t('skillManagement.delete') }}
          </t-link>
        </div>
      </template>
    </t-table>

    <!-- 新建：只填 slug/名称/描述/正文，frontmatter 由服务端组装 -->
    <t-dialog
      v-model:visible="createVisible"
      :header="t('skillManagement.createTitle')"
      width="720px"
      :confirm-btn="{ content: t('skillManagement.createSubmit'), loading: creating }"
      :cancel-btn="{ content: t('skillManagement.createCancel') }"
      @confirm="submitCreate"
      @close="closeCreate"
    >
      <div class="sm-form">
        <div class="sm-field">
          <label>{{ t('skillManagement.slugLabel') }}</label>
          <t-input
            v-model="form.slug"
            :placeholder="t('skillManagement.slugPlaceholder')"
            :status="errors.slug ? 'error' : undefined"
          />
          <p v-if="errors.slug" class="sm-field-error">{{ t(`skillManagement.${errors.slug}`) }}</p>
          <p v-else class="sm-field-hint">{{ t('skillManagement.slugHint') }}</p>
        </div>

        <div class="sm-field">
          <label>{{ t('skillManagement.nameLabel') }}</label>
          <t-input
            v-model="form.name"
            :placeholder="t('skillManagement.namePlaceholder')"
            :status="errors.name ? 'error' : undefined"
          />
          <p v-if="errors.name" class="sm-field-error">{{ t(`skillManagement.${errors.name}`) }}</p>
          <p v-else class="sm-field-hint">{{ t('skillManagement.nameHint') }}</p>
        </div>

        <div class="sm-field">
          <label>{{ t('skillManagement.descriptionLabel') }}</label>
          <t-input
            v-model="form.description"
            :placeholder="t('skillManagement.descriptionPlaceholder')"
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

    <!-- 详情：完整 SKILL.md 原文（只读） -->
    <t-drawer
      v-model:visible="detailVisible"
      :header="detailTitle"
      size="640px"
      :footer="false"
    >
      <p class="sm-drawer-hint">{{ t('skillManagement.detailHint') }}</p>
      <div v-if="detailLoading" class="sm-drawer-loading">{{ t('skillManagement.loadingContent') }}</div>
      <pre v-else class="sm-content">{{ detailContent }}</pre>
    </t-drawer>

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
      <p v-if="deleteImpact" class="sm-delete-impact">
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
  getSkillFileContent,
  listSkillCatalog,
  type SkillCatalogItem,
} from '@/api/skills'
import {
  buildSkillFilePreview,
  describeDeleteImpact,
  EMPTY_SKILL_FORM,
  hasErrors,
  toCreatePayload,
  validateSkillForm,
  type SkillFormErrors,
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
  { colKey: 'description', title: t('skillManagement.columns.description'), ellipsis: true },
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

// ── 新建 ──────────────────────────────────────────────────────────────
const createVisible = ref(false)
const creating = ref(false)
const form = ref<SkillFormValues>({ ...EMPTY_SKILL_FORM })
const errors = ref<SkillFormErrors>({})

const preview = computed(() => buildSkillFilePreview(form.value))

function openCreate() {
  form.value = { ...EMPTY_SKILL_FORM }
  errors.value = {}
  createVisible.value = true
}

function closeCreate() {
  createVisible.value = false
  errors.value = {}
}

async function submitCreate() {
  const found = validateSkillForm(form.value)
  errors.value = found
  if (hasErrors(found)) return
  creating.value = true
  try {
    await createSkill(toCreatePayload(form.value))
    MessagePlugin.success(t('skillManagement.createSuccess'))
    createVisible.value = false
    await reload()
  } catch (e: any) {
    MessagePlugin.error(e?.message || t('skillManagement.createFailed'))
  } finally {
    creating.value = false
  }
}

// ── 详情 ──────────────────────────────────────────────────────────────
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailContent = ref('')
const detailTarget = ref<SkillCatalogItem | null>(null)
const detailTitle = computed(() =>
  detailTarget.value ? `${t('skillManagement.detailTitle')} · ${detailTarget.value.name}` : t('skillManagement.detailTitle'),
)

async function openDetail(row: SkillCatalogItem) {
  detailTarget.value = row
  detailContent.value = ''
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getSkillFileContent(row.id)
    detailContent.value = res?.content || ''
  } catch (e: any) {
    detailContent.value = e?.message || t('skillManagement.loadFailed')
  } finally {
    detailLoading.value = false
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

.sm-title-block h2 {
  margin: 0 0 6px;
  font-size: 18px;
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

.sm-desc {
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

.sm-drawer-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--td-text-color-placeholder);
}

.sm-drawer-loading {
  color: var(--td-text-color-secondary);
}

.sm-content {
  margin: 0;
  padding: 12px;
  background: var(--td-bg-color-container-hover);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.sm-delete-impact {
  margin: 10px 0 0;
  color: var(--td-warning-color);
  font-size: 13px;
  line-height: 1.6;
}
</style>
