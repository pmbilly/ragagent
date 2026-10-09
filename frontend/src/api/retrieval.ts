import { get, put } from '@/utils/request'

// RetrievalConfig represents the global retrieval/search configuration for a tenant.
// Shared by knowledge search and message search.
export interface RetrievalConfig {
  embeddingTopK: number
  vectorThreshold: number
  keywordThreshold: number
  rerankTopK: number
  rerankThreshold: number
  rerankModelId: string
  // 以下三键由高级配置/API 设定，本仓界面不渲染；服务端取 0/0.0 时省略键，故为可选。
  // 必须原样透传：PUT 是整体替换，漏带即被重置为服务端缺省（60 / 0.7 / 0.3）。
  rrfK?: number
  rrfVectorWeight?: number
  rrfKeywordWeight?: number
}

// Get tenant retrieval config via KV API
export async function getTenantRetrievalConfig(): Promise<{ data: RetrievalConfig }> {
  // 后端 200 裸配置对象（§2.1）；适配成消费端既有的 { data } 契约
  const resp = (await get('/api/v1/tenants/kv/retrieval-config')) as unknown as RetrievalConfig
  return { data: resp }
}

// Update tenant retrieval config via KV API
export async function updateTenantRetrievalConfig(config: RetrievalConfig): Promise<{ data: RetrievalConfig }> {
  // 后端 200 裸配置对象（§2.1）；适配成消费端既有的 { data } 契约
  const resp = (await put('/api/v1/tenants/kv/retrieval-config', config)) as unknown as RetrievalConfig
  return { data: resp }
}
