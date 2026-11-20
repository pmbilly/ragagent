# Port 对照表

> 由 `scripts/gen-port-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。
> 读代码时先查这张表：接口 → 实现在哪个类 → 谁在调用。

## 统计

- 域间（解耦）：**27**
- 复合面（随复合端口）：**7**
- 跨模块（必要）：**6**

## 明细

| Port | 实现类 | 实现模块 | 调用方域 | 类型 |
|---|---|---|---|---|
| `AdminUserCommandPort` | `AdminUserAdapter` | app | system | 域间（解耦） |
| `AdminUserPort` | `AdminUserAdapter` | app | system | 域间（解耦） |
| `AuditWritePort` | `AuditWriteAdapter` | app | agentmanagement、auth、datasource、framework、knowledge、system | 跨模块（必要） |
| `ChunkFaqReadPort` | — | ? | — | 复合面（随复合端口） |
| `ChunkPort` | `ChunkPortAdapter` | app | wiki | 域间（解耦） |
| `DocReaderAdminPort` | `DocReaderAdminAdapter` | app | knowledge、system | 域间（解耦） |
| `DocReaderParsePort` | `DocReaderParseAdapter` | app | im、initialization、session | 域间（解耦） |
| `DocumentChunkingPort` | `DocumentChunkingAdapter` | app | session | 域间（解耦） |
| `EmbedSessionPort` | `EmbedSessionAdapter` | app | embedchannel | 域间（解耦） |
| `EmbeddingModelPort` | `EmbeddingModelAdapter` | app | wiki | 域间（解耦） |
| `EmbeddingPort` | `ModelRuntimeEmbeddingAdapter` | model-runtime | knowledge、memory、retrieval-engine | 跨模块（必要） |
| `FileUrlPort` | `DefaultFileUrlPort` | app | framework、knowledge、session、storage | 跨模块（必要） |
| `GlobalFileServicePort` | `GlobalFileServiceAdapter` | app | session | 域间（解耦） |
| `KnowledgeBaseCatalogPort` | `KnowledgeBaseCatalogAdapter` | app | agentmanagement | 域间（解耦） |
| `KnowledgeBaseReadPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeBaseWritePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeCatalogPort` | `KnowledgeCatalogAdapter` | app | datasource、embedchannel、im、initialization、knowledge、session | 域间（解耦） |
| `KnowledgeContentWritePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeDeletePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeEntryReadPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeFinalizePort` | `KnowledgeFinalizeAdapter` | app | wiki | 域间（解耦） |
| `KnowledgeIngestLookupPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeMaintenancePort` | `KnowledgeMaintenanceAdapter` | app | websearch | 域间（解耦） |
| `KnowledgeSpanPort` | `KnowledgeSpanAdapter` | app | wiki | 域间（解耦） |
| `McpToolRegistrationPort` | `McpToolRegistrationAdapter` | app | agent-engine | 跨模块（必要） |
| `MessageTranscriptPort` | `MessageTranscriptAdapter` | app | memory | 域间（解耦） |
| `ModelFactsPort` | `ModelService` | app | retrieval-engine | 跨模块（必要） |
| `ParserEngineCatalogPort` | `ParserEngineCatalog` | app | system | 域间（解耦） |
| `ProviderFileStorePort` | `ProviderFileStoreAdapter` | app | knowledge | 域间（解耦） |
| `ResourceCatalogPort` | `ResourceCatalogAdapter` | app | knowledge、session | 域间（解耦） |
| `SessionQaPort` | `SessionQaAdapter` | app | im | 域间（解耦） |
| `StorageBackendPort` | `StorageBackendPortAdapter` | app | initialization、knowledge、storage | 域间（解耦） |
| `StorageConnectivityPort` | `StorageConnectivityService` | app | storage、system | 域间（解耦） |
| `TenantStorageQuotaPort` | `TenantStorageQuotaAdapter` | app | knowledge、system | 域间（解耦） |
| `ToolRegistryPort` | `ToolRegistryPortImpl` | agent-engine | agent-engine、mcp | 跨模块（必要） |
| `UserLookupPort` | `UserLookupAdapter` | app | agentmanagement | 域间（解耦） |
| `WebSearchProviderPort` | `WebSearchSupportAdapter` | app | session | 域间（解耦） |
| `WebSearchTempStatePort` | `WebSearchSupportAdapter` | app | session | 域间（解耦） |
| `WikiIngestPort` | `WikiIngestService` | app | knowledge | 域间（解耦） |
| `WikiPageOpsPort` | `WikiPageOpsAdapter` | app | session | 域间（解耦） |
