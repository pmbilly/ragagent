import assert from 'node:assert/strict'
import test from 'node:test'

import {
  MODEL_IN_USE_ERROR_CODE,
  ModelInUseError,
  modelInUseErrorFromRequest,
  modelUsageAgentSection,
  modelUsageBindingI18nKey,
  modelUsageKnowledgeBaseSection,
  modelUsageListTruncated,
  modelUsageResourceCount,
  modelUsageResourceRoute,
  parseModelUsageDetails,
} from './modelUsage'

const details = {
  knowledgeBases: [
    { id: 'kb-1', name: 'Product docs', bindings: ['vlm_model'] },
    { id: 'kb-2', name: 'Engineering', bindings: ['vlm_model'] },
  ],
  agents: [
    { id: 'agent-1', name: 'Support', bindings: ['chat_model', 'follow_up_model'] },
  ],
  longTermMemory: { bindings: ['extract_model'] },
  knowledgeBaseTotal: 2,
  agentTotal: 1,
}

test('parses grouped model usage details without dropping multiple bindings', () => {
  assert.deepEqual(parseModelUsageDetails(details), details)
})

test('fills missing totals from the listed collections', () => {
  const { knowledgeBaseTotal, agentTotal, ...withoutTotals } = details
  assert.deepEqual(parseModelUsageDetails(withoutTotals), details)
  assert.equal(knowledgeBaseTotal, 2)
  assert.equal(agentTotal, 1)
})

test('keeps untruncated totals when the listed collections are capped', () => {
  const parsed = parseModelUsageDetails({
    ...details,
    knowledgeBaseTotal: 80,
    agentTotal: 12,
  })
  assert.equal(parsed?.knowledgeBaseTotal, 80)
  assert.equal(parsed?.agentTotal, 12)
  assert.equal(modelUsageResourceCount(parsed!.knowledgeBases, parsed!.knowledgeBaseTotal), 80)
  assert.equal(modelUsageListTruncated(parsed!.knowledgeBases, parsed!.knowledgeBaseTotal), true)
  assert.equal(modelUsageListTruncated(parsed!.agents, parsed!.agentTotal), true)
})

test('recognizes only the dedicated model-in-use code with a valid payload', () => {
  // 真实形态：响应拦截器把错误体**摊平**到顶层（`{status, message, code, data}`）。
  // 旧夹具用的 `{error:{code,details}}` 嵌套形态是 axios 残留 —— 它让 B229 那条
  // "用量弹窗永不出现"的 bug 长期常绿（测试照着实现写，两边一起错）。
  const conflict = modelInUseErrorFromRequest({
    status: 400,
    message: 'model is used by 3 resource(s)',
    code: MODEL_IN_USE_ERROR_CODE,
    data: details,
  })

  assert.ok(conflict instanceof ModelInUseError)
  assert.deepEqual(conflict.details, details)
  assert.equal(modelInUseErrorFromRequest({ error: { code: 1000, details } }), null)
})

test('rejects malformed details instead of guessing from the server message', () => {
  assert.equal(parseModelUsageDetails({ knowledgeBases: [], agents: [] }), null)
  assert.equal(parseModelUsageDetails({
    knowledgeBases: [],
    agents: [],
    longTermMemory: { bindings: [] },
  }), null)
  assert.equal(parseModelUsageDetails({
    knowledgeBases: [{ id: 'kb-1', name: 'Docs', bindings: [] }],
    agents: [],
    longTermMemory: { bindings: [] },
  }), null)
  assert.equal(parseModelUsageDetails({
    knowledgeBases: [{ id: 'kb-1', name: 'Docs', bindings: 'vlm_model' }],
    agents: [],
    longTermMemory: { bindings: [] },
  }), null)
  // 码对、但载荷不含任何可用引用 ⇒ 仍返回 null（不拿服务端文案去猜结构）
  assert.equal(modelInUseErrorFromRequest({
    status: 400,
    message: 'model is used by 1 knowledge base(s)',
    code: MODEL_IN_USE_ERROR_CODE,
    data: null,
  }), null)
})

test('tolerates the legacy nested error shape (defensive branch kept)', () => {
  // 实现里保留了一条兼容分支（万一调用方拿到的是未摊平的 axios error）。
  // 这条测试钉住它，同时说明"主路径是顶层形态"。
  const legacy = modelInUseErrorFromRequest({
    status: 400,
    error: { code: MODEL_IN_USE_ERROR_CODE, details },
  })
  assert.ok(legacy instanceof ModelInUseError)
})

test('keeps navigation and localization stable for all resource kinds', () => {
  assert.deepEqual(
    modelUsageResourceRoute('knowledge_base', 'kb/with slash'),
    { path: '/platform/knowledge-bases/kb%2Fwith%20slash' },
  )
  assert.deepEqual(
    modelUsageResourceRoute('agent', 'agent-1'),
    { path: '/platform/agents', query: { edit: 'agent-1', section: 'model' } },
  )
  assert.deepEqual(
    modelUsageResourceRoute('agent', 'agent-1', ['vlm_model']),
    { path: '/platform/agents', query: { edit: 'agent-1', section: 'multimodal' } },
  )
  assert.deepEqual(
    modelUsageResourceRoute('agent', 'agent-1', ['follow_up_model']),
    { path: '/platform/agents', query: { edit: 'agent-1', section: 'suggestions' } },
  )
  assert.equal(modelUsageBindingI18nKey('vlm_model'), 'modelSettings.usage.bindings.vlm_model')
  assert.equal(modelUsageBindingI18nKey('future_binding'), 'modelSettings.usage.bindings.unknown')
})

test('maps knowledge-base bindings to the configuration section that owns them', () => {
  assert.equal(modelUsageKnowledgeBaseSection(['embedding_model']), 'models')
  assert.equal(modelUsageKnowledgeBaseSection(['summary_model']), 'models')
  assert.equal(modelUsageKnowledgeBaseSection(['wiki_synthesis_model']), 'models')
  assert.equal(modelUsageKnowledgeBaseSection(['image_processing_model']), 'multimodal')
  assert.equal(modelUsageKnowledgeBaseSection(['vlm_model']), 'multimodal')
  assert.equal(modelUsageKnowledgeBaseSection(['asr_model']), 'asr')
  assert.equal(modelUsageKnowledgeBaseSection(['future_binding']), 'models')
  assert.equal(modelUsageKnowledgeBaseSection(['summary_model', 'vlm_model']), 'models')
})

test('maps agent bindings to the configuration section that owns them', () => {
  assert.equal(modelUsageAgentSection(['chat_model']), 'model')
  assert.equal(modelUsageAgentSection(['rerank_model']), 'model')
  assert.equal(modelUsageAgentSection(['query_understand_model']), 'model')
  assert.equal(modelUsageAgentSection(['vlm_model']), 'multimodal')
  assert.equal(modelUsageAgentSection(['asr_model']), 'multimodal')
  assert.equal(modelUsageAgentSection(['follow_up_model']), 'suggestions')
  assert.equal(modelUsageAgentSection(['future_binding']), 'model')
  assert.equal(modelUsageAgentSection(['chat_model', 'vlm_model']), 'model')
  assert.equal(modelUsageAgentSection(['vlm_model', 'follow_up_model']), 'multimodal')
})
