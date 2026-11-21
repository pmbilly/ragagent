# Port 对照表

> 由 `scripts/gen-port-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。
> 读代码时先查这张表：接口 → 实现在哪个类 → 谁在调用。

## 统计

- 域内（可评估内联）：**11**
- 域间（解耦）：**26**
- 复合面（随复合端口）：**7**
- 装配点实现（不参与内联判定）：**6**
- 跨模块（必要）：**11**

## 明细

| Port | 实现类 | 实现模块 | 调用方域 | 类型 |
|---|---|---|---|---|
| `AdminUserCommandPort` | `AdminUserAdapter` | backend/app | auth | 域内（可评估内联） |
| `AdminUserPort` | `AdminUserAdapter` | backend/app | auth | 域内（可评估内联） |
| `AgentSuggestionPort` | `CustomAgentService` | backend/app | session | 域间（解耦） |
| `AuditWritePort` | `AuditWriteAdapter` | backend/app | agentmanagement、auth、datasource、knowledge | 域间（解耦） |
| `ChunkFaqReadPort` | — | ? | — | 复合面（随复合端口） |
| `ChunkPort` | `ChunkPortAdapter` | backend/app | knowledge | 域内（可评估内联） |
| `ChunkQueryPort` | `KnowledgeChunkPipelineAdapter` | backend/app | session | 域间（解耦） |
| `ChunkQuestionPort` | `ChunkQuestionService` | backend/app | knowledge | 域内（可评估内联） |
| `DataSourceSchedulePort` | `Scheduler` | backend/app | datasource | 域内（可评估内联） |
| `DocReaderAdminPort` | `DocReaderAdminAdapter` | backend/app | auth、knowledge | 域间（解耦） |
| `DocReaderParsePort` | `DocReaderParseAdapter` | backend/app | im、initialization、session | 域间（解耦） |
| `DocumentChunkingPort` | `DocumentChunkingAdapter` | backend/app | session | 域间（解耦） |
| `EmbedSessionPort` | `EmbedSessionAdapter` | backend/app | embedchannel | 域间（解耦） |
| `EmbeddingModelPort` | `EmbeddingModelAdapter` | backend/app | knowledge | 域内（可评估内联） |
| `EmbeddingPort` | `ModelRuntimeEmbeddingAdapter` | backend/model-runtime | backend/retrieval-engine、knowledge、memory | 跨模块（必要） |
| `EntityExtractionPort` | `EntityExtractionAdapter` | backend/model-runtime | initialization、knowledge、session | 跨模块（必要） |
| `FileProxyPort` | `FileProxyService` | backend/app | embedchannel、knowledge、session | 域间（解耦） |
| `FileUrlPort` | `DefaultFileUrlPort` | backend/app | backend/framework、knowledge、session、storage | 跨模块（必要） |
| `GlobalFileServicePort` | `GlobalFileServiceAdapter` | backend/app | session | 域间（解耦） |
| `HybridSearchPort` | `HybridSearchService` | backend/retrieval-engine | knowledge、session | 跨模块（必要） |
| `KnowledgeBaseBatchQueryPort` | `QaWiring` | backend/app | session | 装配点实现（不参与内联判定） |
| `KnowledgeBaseCatalogPort` | `KnowledgeBaseCatalogAdapter` | backend/app | agentmanagement | 域间（解耦） |
| `KnowledgeBaseQueryPort` | `QaWiring` | backend/app | session | 装配点实现（不参与内联判定） |
| `KnowledgeBaseReadPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeBaseWritePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeBatchQueryPort` | `QaWiring` | backend/app | session | 装配点实现（不参与内联判定） |
| `KnowledgeCatalogPort` | `KnowledgeCatalogAdapter` | backend/app | datasource、embedchannel、im、initialization、knowledge、session | 域间（解耦） |
| `KnowledgeContentWritePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeDeletePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeDocumentQueryPort` | `QaWiring` | backend/app | session | 装配点实现（不参与内联判定） |
| `KnowledgeEntryReadPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeFinalizePort` | `KnowledgeFinalizeAdapter` | backend/app | knowledge | 域内（可评估内联） |
| `KnowledgeIngestLookupPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeMaintenancePort` | `KnowledgeMaintenanceAdapter` | backend/app | websearch | 域间（解耦） |
| `KnowledgeQaEventPort` | `KnowledgeQaEventAdapter` | backend/app | — | 域内（可评估内联） |
| `KnowledgeReadPort` | `KnowledgeService` | backend/app | knowledge | 域内（可评估内联） |
| `KnowledgeSpanPort` | `KnowledgeSpanAdapter` | backend/app | knowledge | 域内（可评估内联） |
| `McpToolRegistrationPort` | `McpToolRegistrationAdapter` | backend/app | backend/agent-engine | 跨模块（必要） |
| `MemoryQueryPort` | `MemoryPipelineAdapter` | backend/app | session | 域间（解耦） |
| `MessageTranscriptPort` | `MessageTranscriptAdapter` | backend/app | memory | 域间（解耦） |
| `ModelCatalogPort` | `ModelCatalogAdapter` | backend/app | backend/model-runtime、initialization、knowledge、mcp、memory、session | 跨模块（必要） |
| `ModelFactoryPort` | `QaWiring` | backend/app | session | 装配点实现（不参与内联判定） |
| `ModelFactsPort` | `ModelService` | backend/app | backend/retrieval-engine | 跨模块（必要） |
| `OllamaAdminPort` | `OllamaService` | backend/model-runtime | model | 跨模块（必要） |
| `ParserEngineCatalogPort` | `ParserEngineCatalog` | backend/app | auth | 域间（解耦） |
| `ProviderFileStorePort` | `ProviderFileStoreAdapter` | backend/app | knowledge | 域间（解耦） |
| `ResourceCatalogPort` | `ResourceCatalogAdapter` | backend/app | knowledge、session | 域间（解耦） |
| `SessionQaPort` | `SessionQaAdapter` | backend/app | im | 域间（解耦） |
| `SkillCatalogPort` | `SkillCatalogService` | backend/agent-engine | agentmanagement、backend/agent-engine | 跨模块（必要） |
| `StorageBackendPort` | `StorageBackendPortAdapter` | backend/app | initialization、knowledge、storage | 域间（解耦） |
| `StorageConnectivityPort` | `StorageConnectivityService` | backend/app | auth、storage | 域间（解耦） |
| `TenantStorageQuotaPort` | `TenantStorageQuotaAdapter` | backend/app | auth、knowledge | 域间（解耦） |
| `TenantWebSearchConfigPort` | `QaWiring` | backend/app | session | 装配点实现（不参与内联判定） |
| `ToolRegistryPort` | `ToolRegistryPortImpl` | backend/agent-engine | backend/agent-engine、mcp | 跨模块（必要） |
| `UserLookupPort` | `UserLookupAdapter` | backend/app | agentmanagement | 域间（解耦） |
| `VectorStoreEngineCatalogPort` | `VectorStoreEngineCatalog` | backend/retrieval-engine | vectorstore | 跨模块（必要） |
| `WebSearchProviderPort` | `WebSearchSupportAdapter` | backend/app | session | 域间（解耦） |
| `WebSearchRunPort` | `WebSearchBackendsAdapter` | backend/app | session | 域间（解耦） |
| `WebSearchTempStatePort` | `WebSearchSupportAdapter` | backend/app | session | 域间（解耦） |
| `WikiIngestPort` | `WikiIngestService` | backend/app | knowledge | 域内（可评估内联） |
| `WikiPageOpsPort` | `WikiPageOpsAdapter` | backend/app | session | 域间（解耦） |
