# Port 对照表

> 由 `scripts/gen-port-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。
> 读代码时先查这张表：接口 → 实现在哪个类 → 谁在调用。

## 统计

- 域间（解耦）：**21**
- 跨模块（必要）：**10**

## 明细

| Port | 实现类 | 实现模块 | 调用方域 | 类型 |
|---|---|---|---|---|
| `AuditWritePort` | `AuditWriteAdapter` | domains | agentmanagement、auth、datasource、knowledge、system | 域间（解耦） |
| `DocReaderAdminPort` | `DocReaderAdminAdapter` | domains | system | 域间（解耦） |
| `DocReaderParsePort` | `DocReaderParseAdapter` | domains | im、initialization、session | 域间（解耦） |
| `DocumentChunkingPort` | `DocumentChunkingAdapter` | domains | session | 域间（解耦） |
| `EmbedSessionPort` | `EmbedSessionAdapter` | domains | embedchannel | 域间（解耦） |
| `EmbeddingModelPort` | `EmbeddingModelAdapter` | domains | wiki | 域间（解耦） |
| `EmbeddingPort` | `EmbedderClient` | domains | knowledge、memory、retrieval-engine | 跨模块（必要） |
| `EntityExtractionPort` | `EntityExtractionAdapter` | model-runtime | initialization、knowledge、session | 跨模块（必要） |
| `FileUrlPort` | `DefaultFileUrlPort` | domains | platform、session、storage | 跨模块（必要） |
| `GlobalFileServicePort` | `GlobalFileServiceAdapter` | domains | session | 域间（解耦） |
| `HybridSearchPort` | `HybridSearchService` | retrieval-engine | knowledge、session | 跨模块（必要） |
| `KnowledgeBaseCatalogPort` | `KnowledgeBaseCatalogAdapter` | domains | agentmanagement | 域间（解耦） |
| `KnowledgeCatalogPort` | `KnowledgeCatalogAdapter` | domains | datasource、embedchannel、evaluation、im、initialization、knowledge、session | 域间（解耦） |
| `KnowledgeMaintenancePort` | `KnowledgeMaintenanceAdapter` | domains | websearch | 域间（解耦） |
| `KnowledgeSpanPort` | `KnowledgeSpanAdapter` | domains | wiki | 域间（解耦） |
| `McpToolRegistrationPort` | `McpToolRegistrationAdapter` | domains | agent-engine | 跨模块（必要） |
| `MessageTranscriptPort` | `MessageTranscriptAdapter` | domains | memory | 域间（解耦） |
| `ModelCatalogPort` | `ModelCatalogAdapter` | domains | evaluation、initialization、knowledge、mcp、memory、model-runtime、session、wiki | 跨模块（必要） |
| `ModelFactsPort` | `ModelService` | domains | retrieval-engine | 跨模块（必要） |
| `OllamaAdminPort` | `OllamaService` | model-runtime | initialization | 跨模块（必要） |
| `ProviderFileStorePort` | `ProviderFileStoreAdapter` | domains | knowledge | 域间（解耦） |
| `ResourceCatalogPort` | `ResourceCatalogAdapter` | domains | knowledge、session | 域间（解耦） |
| `SessionQaPort` | `SessionQaAdapter` | domains | im | 域间（解耦） |
| `SkillCatalogPort` | `SkillCatalogService` | agent-engine | agent-engine、agentmanagement | 跨模块（必要） |
| `StorageBackendPort` | `StorageBackendPortAdapter` | domains | knowledge、system | 域间（解耦） |
| `TenantStorageQuotaPort` | `TenantStorageQuotaAdapter` | domains | knowledge、system | 域间（解耦） |
| `ToolRegistryPort` | `ToolRegistryPortImpl` | agent-engine | agent-engine、mcp | 跨模块（必要） |
| `UserLookupPort` | `UserLookupAdapter` | domains | agentmanagement | 域间（解耦） |
| `WebSearchProviderPort` | `WebSearchSupportAdapter` | domains | session | 域间（解耦） |
| `WebSearchTempStatePort` | `WebSearchSupportAdapter` | domains | session | 域间（解耦） |
| `WikiPageOpsPort` | `WikiPageOpsAdapter` | domains | session | 域间（解耦） |
