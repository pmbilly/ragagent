# Port 对照表

> 由 `scripts/gen-port-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。
> 读代码时先查这张表：接口 → 实现在哪个类 → 谁在调用。

## 统计

- 域间（解耦）：**24**
- 复合面（随复合端口）：**7**
- 跨模块（必要）：**3**

## 明细

| Port | 实现类 | 实现模块 | 调用方域 | 类型 |
|---|---|---|---|---|
| `AdminUserCommandPort` | `AdminUserAdapter` | backend/app | system | 域间（解耦） |
| `AdminUserPort` | `AdminUserAdapter` | backend/app | system | 域间（解耦） |
| `AuditWritePort` | `AuditWriteAdapter` | backend/app | agentmanagement、auth、backend/framework、datasource、knowledge、system | 跨模块（必要） |
| `ChunkFaqReadPort` | — | ? | — | 复合面（随复合端口） |
| `ChunkPort` | `ChunkPortAdapter` | backend/app | wiki | 域间（解耦） |
| `DocReaderAdminPort` | `DocReaderAdminAdapter` | backend/app | knowledge、system | 域间（解耦） |
| `DocReaderParsePort` | `DocReaderParseAdapter` | backend/app | im、initialization、session | 域间（解耦） |
| `EmbedSessionPort` | `EmbedSessionAdapter` | backend/app | embedchannel | 域间（解耦） |
| `EmbeddingModelPort` | `EmbeddingModelAdapter` | backend/app | wiki | 域间（解耦） |
| `EmbeddingPort` | `ModelRuntimeEmbeddingAdapter` | backend/model-runtime | backend/retrieval-engine、knowledge、memory | 跨模块（必要） |
| `FileUrlPort` | `DefaultFileUrlPort` | backend/app | backend/framework、knowledge、session、storage | 跨模块（必要） |
| `GlobalFileServicePort` | `GlobalFileServiceAdapter` | backend/app | session | 域间（解耦） |
| `KnowledgeBaseCatalogPort` | `KnowledgeBaseCatalogAdapter` | backend/app | agentmanagement | 域间（解耦） |
| `KnowledgeBaseReadPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeBaseWritePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeCatalogPort` | `KnowledgeCatalogAdapter` | backend/app | datasource、embedchannel、im、initialization、knowledge、session | 域间（解耦） |
| `KnowledgeContentWritePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeDeletePort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeEntryReadPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeFinalizePort` | `KnowledgeFinalizeAdapter` | backend/app | wiki | 域间（解耦） |
| `KnowledgeIngestLookupPort` | — | ? | — | 复合面（随复合端口） |
| `KnowledgeMaintenancePort` | `KnowledgeMaintenanceAdapter` | backend/app | websearch | 域间（解耦） |
| `KnowledgeSpanPort` | `KnowledgeSpanAdapter` | backend/app | wiki | 域间（解耦） |
| `ParserEngineCatalogPort` | `ParserEngineCatalog` | backend/app | system | 域间（解耦） |
| `ProviderFileStorePort` | `ProviderFileStoreAdapter` | backend/app | knowledge | 域间（解耦） |
| `ResourceCatalogPort` | `ResourceCatalogAdapter` | backend/app | knowledge、session | 域间（解耦） |
| `SessionQaPort` | `SessionQaAdapter` | backend/app | im | 域间（解耦） |
| `StorageBackendPort` | `StorageBackendPortAdapter` | backend/app | initialization、knowledge、storage | 域间（解耦） |
| `StorageConnectivityPort` | `StorageConnectivityService` | backend/app | storage、system | 域间（解耦） |
| `TenantStorageQuotaPort` | `TenantStorageQuotaAdapter` | backend/app | knowledge、system | 域间（解耦） |
| `UserLookupPort` | `UserLookupAdapter` | backend/app | agentmanagement | 域间（解耦） |
| `WebSearchProviderPort` | `WebSearchSupportAdapter` | backend/app | session | 域间（解耦） |
| `WebSearchTempStatePort` | `WebSearchSupportAdapter` | backend/app | session | 域间（解耦） |
| `WikiIngestPort` | `WikiIngestService` | backend/app | knowledge | 域间（解耦） |
