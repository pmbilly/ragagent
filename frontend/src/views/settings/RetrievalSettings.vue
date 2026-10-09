<template>
  <div class="retrieval-settings">
    <div class="section-header">
      <h2>{{ t('retrievalSettings.title') }}</h2>
      <p class="section-description">{{ t('retrievalSettings.description') }}</p>
    </div>

    <div class="settings-group">
      <!-- Rerank Model -->
      <div class="setting-item">
        <div class="setting-label">
          <span>{{ t('retrievalSettings.rerankModelLabel') }} <span class="required-mark">*</span></span>
        </div>
        <p class="setting-desc">{{ t('retrievalSettings.rerankModelDescription') }}</p>
        <p v-if="!localConfig.rerankModelId" class="setting-desc warning-text">
          {{ t('retrievalSettings.rerankModelRequired') }}
        </p>
        <div class="setting-control-full">
          <ModelSelector
            model-type="Rerank"
            :selected-model-id="localConfig.rerankModelId"
            :disabled="!canEdit"
            @update:selected-model-id="handleModelChange"
          />
        </div>
      </div>

      <!-- Embedding Top K -->
      <div class="setting-item">
        <div class="setting-label-row">
          <span>{{ t('retrievalSettings.embeddingTopKLabel') }}</span>
          <span class="value-display">{{ localConfig.embeddingTopK }}</span>
        </div>
        <t-slider
          v-model="localConfig.embeddingTopK"
          :min="1"
          :max="100"
          :step="1"
          :disabled="!canEdit"
          @change="handleParamChange"
        />
      </div>

      <!-- Vector Threshold -->
      <div class="setting-item">
        <div class="setting-label-row">
          <span>{{ t('retrievalSettings.vectorThresholdLabel') }}</span>
          <span class="value-display">{{ localConfig.vectorThreshold.toFixed(2) }}</span>
        </div>
        <t-slider
          v-model="localConfig.vectorThreshold"
          :min="0"
          :max="1"
          :step="0.05"
          :disabled="!canEdit"
          @change="handleParamChange"
        />
      </div>

      <!-- Keyword Threshold -->
      <div class="setting-item">
        <div class="setting-label-row">
          <span>{{ t('retrievalSettings.keywordThresholdLabel') }}</span>
          <span class="value-display">{{ localConfig.keywordThreshold.toFixed(2) }}</span>
        </div>
        <t-slider
          v-model="localConfig.keywordThreshold"
          :min="0"
          :max="1"
          :step="0.05"
          :disabled="!canEdit"
          @change="handleParamChange"
        />
      </div>

      <!-- Rerank Top K -->
      <div class="setting-item">
        <div class="setting-label-row">
          <span>{{ t('retrievalSettings.rerankTopKLabel') }}</span>
          <span class="value-display">{{ localConfig.rerankTopK }}</span>
        </div>
        <t-slider
          v-model="localConfig.rerankTopK"
          :min="1"
          :max="100"
          :step="1"
          :disabled="!canEdit"
          @change="handleParamChange"
        />
      </div>

      <!-- Rerank Threshold -->
      <div class="setting-item">
        <div class="setting-label-row">
          <span>{{ t('retrievalSettings.rerankThresholdLabel') }}</span>
          <span class="value-display">{{ localConfig.rerankThreshold.toFixed(2) }}</span>
        </div>
        <t-slider
          v-model="localConfig.rerankThreshold"
          :min="-10"
          :max="10"
          :step="0.1"
          :disabled="!canEdit"
          @change="handleParamChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, nextTick } from 'vue'
import { MessagePlugin } from 'tdesign-vue-next'
import { useI18n } from 'vue-i18n'
import ModelSelector from '@/components/ModelSelector.vue'
import {
  getTenantRetrievalConfig,
  updateTenantRetrievalConfig,
  type RetrievalConfig,
} from '@/api/retrieval'
import { useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
// PUT /tenants/kv/retrieval-config requires Admin+ on the server. Hide the
// banner + lock all controls for non-Admins so they can read the
// configuration without tripping a 403 mid-edit.
const canEdit = computed(() => authStore.hasRole('admin'))

const defaultConfig: RetrievalConfig = {
  embeddingTopK: 50,
  vectorThreshold: 0.15,
  keywordThreshold: 0.3,
  rerankTopK: 10,
  rerankThreshold: 0.2,
  rerankModelId: '',
}

const localConfig = reactive<RetrievalConfig>({ ...defaultConfig })
let initialConfig: RetrievalConfig = { ...defaultConfig }
let isInitializing = true

const loadConfig = async () => {
  try {
    const response = await getTenantRetrievalConfig()
    if (response.data) {
      const cfg = response.data
      Object.assign(localConfig, {
        embeddingTopK: cfg.embeddingTopK || defaultConfig.embeddingTopK,
        vectorThreshold: cfg.vectorThreshold || defaultConfig.vectorThreshold,
        keywordThreshold: cfg.keywordThreshold || defaultConfig.keywordThreshold,
        rerankTopK: cfg.rerankTopK || defaultConfig.rerankTopK,
        rerankThreshold: cfg.rerankThreshold ?? defaultConfig.rerankThreshold,
        rerankModelId: cfg.rerankModelId || '',
        // rrf 三键本页不渲染，仅原样透传（服务端省略键时保持 undefined，序列化自动丢弃）
        rrfK: cfg.rrfK,
        rrfVectorWeight: cfg.rrfVectorWeight,
        rrfKeywordWeight: cfg.rrfKeywordWeight,
      })
      initialConfig = { ...localConfig }
    }
  } catch (error: any) {
    console.error('Failed to load retrieval config:', error)
  } finally {
    await nextTick()
    await nextTick()
    setTimeout(() => { isInitializing = false }, 100)
  }
}

const hasConfigChanged = (): boolean => {
  return JSON.stringify(localConfig) !== JSON.stringify(initialConfig)
}

const saveConfig = async () => {
  if (!hasConfigChanged()) return
  try {
    const response = await updateTenantRetrievalConfig({ ...localConfig })
    if (response.data) {
      initialConfig = { ...localConfig }
    }
    MessagePlugin.success(t('retrievalSettings.toasts.saveSuccess'))
  } catch (error: any) {
    console.error('Failed to save retrieval config:', error)
    const errorMessage = error?.message || 'Unknown error'
    MessagePlugin.error(t('retrievalSettings.toasts.saveFailed', { message: errorMessage }))
  }
}

let saveTimer: number | null = null
const debouncedSave = () => {
  if (isInitializing) return
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = window.setTimeout(() => {
    saveConfig().catch(() => {})
  }, 500)
}

const handleParamChange = () => debouncedSave()
const handleModelChange = (modelId: string) => {
  localConfig.rerankModelId = modelId
  debouncedSave()
}

onMounted(async () => {
  isInitializing = true
  await loadConfig()
})
</script>

<style lang="less" scoped>
.retrieval-settings {
  width: 100%;
}

.section-header {
  margin-bottom: 24px;

  h2 {
    font-size: 20px;
    font-weight: 600;
    color: var(--td-text-color-primary);
    margin: 0 0 6px 0;
  }

  .section-description {
    font-size: 13px;
    color: var(--td-text-color-secondary);
    margin: 0;
    line-height: 1.5;
  }
}

.settings-group {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.setting-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--td-component-stroke);

  &:last-child {
    border-bottom: none;
  }
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--td-text-color-primary);
  margin-bottom: 4px;
}

.setting-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 500;
  color: var(--td-text-color-primary);
  margin-bottom: 10px;
}

.setting-desc {
  font-size: 12px;
  color: var(--td-text-color-secondary);
  margin: 0 0 8px 0;
  line-height: 1.5;
}

.required-mark {
  color: var(--td-error-color);
}

.warning-text {
  color: var(--td-warning-color) !important;
}

.setting-control-full {
  width: 100%;
}

.value-display {
  font-size: 13px;
  font-weight: 600;
  color: var(--td-brand-color);
  font-family: var(--app-font-family-mono);
}
</style>
