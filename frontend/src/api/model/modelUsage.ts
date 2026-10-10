export const MODEL_IN_USE_ERROR_CODE = 2300

export const KNOWN_MODEL_USAGE_BINDINGS = [
  'embedding_model',
  'summary_model',
  'image_processing_model',
  'vlm_model',
  'asr_model',
  'wiki_synthesis_model',
  'chat_model',
  'rerank_model',
  'query_understand_model',
  'follow_up_model',
  'extract_model',
] as const

export type KnownModelUsageBinding = typeof KNOWN_MODEL_USAGE_BINDINGS[number]

export interface ModelUsageResource {
  id: string
  name: string
  bindings: string[]
}

export interface ModelUsageDetails {
  knowledgeBases: ModelUsageResource[]
  agents: ModelUsageResource[]
  longTermMemory: {
    bindings: string[]
  }
  knowledgeBaseTotal: number
  agentTotal: number
}

export class ModelInUseError extends Error {
  readonly details: ModelUsageDetails

  constructor(details: ModelUsageDetails) {
    super('model is in use')
    this.name = 'ModelInUseError'
    this.details = details
  }
}

type UnknownRecord = Record<string, unknown>

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseBindings(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.some(binding => typeof binding !== 'string' || binding.trim() === '')) {
    return null
  }
  return [...value]
}

function parseResources(value: unknown): ModelUsageResource[] | null {
  if (!Array.isArray(value)) return null

  const resources: ModelUsageResource[] = []
  for (const item of value) {
    if (!isRecord(item) || typeof item.id !== 'string' || item.id.trim() === '' || typeof item.name !== 'string') {
      return null
    }
    const bindings = parseBindings(item.bindings)
    if (!bindings || bindings.length === 0) return null
    resources.push({ id: item.id, name: item.name, bindings })
  }
  return resources
}

function parseTotal(value: unknown, listed: number): number {
  if (typeof value === 'number' && Number.isFinite(value) && value >= listed) {
    return Math.floor(value)
  }
  return listed
}

export function parseModelUsageDetails(value: unknown): ModelUsageDetails | null {
  if (!isRecord(value) || !isRecord(value.longTermMemory)) return null

  const knowledgeBases = parseResources(value.knowledgeBases)
  const agents = parseResources(value.agents)
  const memoryBindings = parseBindings(value.longTermMemory.bindings)
  if (!knowledgeBases || !agents || !memoryBindings) return null

  const details: ModelUsageDetails = {
    knowledgeBases: knowledgeBases,
    agents,
    longTermMemory: { bindings: memoryBindings },
    knowledgeBaseTotal: parseTotal(value.knowledgeBaseTotal, knowledgeBases.length),
    agentTotal: parseTotal(value.agentTotal, agents.length),
  }
  if (knowledgeBases.length === 0 && agents.length === 0 && memoryBindings.length === 0) {
    return null
  }
  return details
}

export function modelInUseErrorFromRequest(error: unknown): ModelInUseError | null {
  if (!isRecord(error)) return null
  // 响应拦截器会把错误体**摊平到顶层**（utils/request.ts 的错误分支：
  // `{status, message, ...data}`）⇒ 实际形状是 `{status, message, code, data}`。
  // 旧写法读 `error.error.code` / `error.error.details` 是 **axios 形态残留** ⇒ 恒 undefined
  // ⇒ 本函数恒返回 null ⇒「模型被引用」的结构化用量弹窗**永不出现**，用户只见通用 toast
  //（B229 修正；测试夹具也一并换成真实形态，旧夹具曾让这条 bug 长期常绿）。
  // 兼容分支：万一调用方直接拿到原始 axios error（含 .error 嵌套），也认。
  const body: Record<string, unknown> = isRecord(error.error) ? error.error : error
  if (body.code !== MODEL_IN_USE_ERROR_CODE) return null
  const details = parseModelUsageDetails(body.details ?? body.data)
  return details ? new ModelInUseError(details) : null
}

const knownBindings = new Set<string>(KNOWN_MODEL_USAGE_BINDINGS)

export function modelUsageBindingI18nKey(binding: string): string {
  const key = knownBindings.has(binding) ? binding : 'unknown'
  return `modelSettings.usage.bindings.${key}`
}

export function modelUsageResourceCount(resources: readonly ModelUsageResource[], total?: number): number {
  return typeof total === 'number' && Number.isFinite(total) && total >= resources.length
    ? Math.floor(total)
    : resources.length
}

export function modelUsageListTruncated(resources: readonly ModelUsageResource[], total?: number): boolean {
  return modelUsageResourceCount(resources, total) > resources.length
}

export type ModelUsageResourceKind = 'knowledge_base' | 'agent'

export type KnowledgeBaseModelUsageSection = 'models' | 'multimodal' | 'asr'

export type AgentModelUsageSection = 'model' | 'multimodal' | 'suggestions'

const knowledgeBaseUsageSections: Partial<Record<KnownModelUsageBinding, KnowledgeBaseModelUsageSection>> = {
  embedding_model: 'models',
  summary_model: 'models',
  wiki_synthesis_model: 'models',
  image_processing_model: 'multimodal',
  vlm_model: 'multimodal',
  asr_model: 'asr',
}

const agentUsageSections: Partial<Record<KnownModelUsageBinding, AgentModelUsageSection>> = {
  chat_model: 'model',
  rerank_model: 'model',
  query_understand_model: 'model',
  vlm_model: 'multimodal',
  asr_model: 'multimodal',
  follow_up_model: 'suggestions',
}

export function modelUsageKnowledgeBaseSection(
  bindings: readonly string[],
): KnowledgeBaseModelUsageSection {
  for (const binding of bindings) {
    const section = knowledgeBaseUsageSections[binding as KnownModelUsageBinding]
    if (section) return section
  }
  return 'models'
}

export function modelUsageAgentSection(
  bindings: readonly string[],
): AgentModelUsageSection {
  for (const binding of bindings) {
    const section = agentUsageSections[binding as KnownModelUsageBinding]
    if (section) return section
  }
  return 'model'
}

export function modelUsageResourceRoute(
  kind: ModelUsageResourceKind,
  id: string,
  bindings: readonly string[] = [],
) {
  if (kind === 'knowledge_base') {
    return { path: `/platform/knowledge-bases/${encodeURIComponent(id)}` }
  }
  return {
    path: '/platform/agents',
    query: { edit: id, section: modelUsageAgentSection(bindings) },
  }
}
