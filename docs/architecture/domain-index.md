<!-- 由 scripts/gen-domain-index.py 生成；不要手改。 -->
# 业务域索引（按能力种类）

> 三个问题：这个域属于哪类能力？有没有 HTTP 面？对外提供哪些契约面（Port/Gateway）？
> 分类配置在 [`scripts/capability-kinds.json`](../../scripts/capability-kinds.json)；
> 端口权威表在 [`port-map.md`](port-map.md)。

## 六类能力

| 种类 | 域数 | 文件 | 控制器 | 说明 |
|---|---:|---:|---:|---|
| **产品能力** | 8 | 805 | 23 | 直接产生用户价值、按产品需求演进的业务能力。 |
| **集成适配** | 6 | 516 | 14 | 对接外部系统/渠道，变更主要由第三方 API 与协议驱动。 |
| **控制面** | 4 | 157 | 9 | 租户、身份、系统设置与审计，变更主要由安全合规驱动。 |
| **能力配置** | 2 | 50 | 4 | 模型/检索等运行时能力的配置面，本身不实现运行时。 |
| **初始化向导** | 1 | 13 | 1 | 首次配置的编排面，只调契约端口，不承载业务规则。 |
| **策略库** | 1 | 7 | 0 | 无 HTTP、无持久化的纯策略/判定逻辑。 |

## 域明细

| 域 | 种类 | 文件 | 控制器 | 对外契约面 | 前端页面 |
|---|---|---:|---:|---:|---|
| `knowledge` | 产品能力 | 252 | 8 | 13 | `views/knowledge` |
| `session` | 产品能力 | 184 | 8 | 2 | `views/chat` |
| `wiki` | 产品能力 | 163 | 1 | 1 | `views/knowledge/wiki` |
| `memory` | 产品能力 | 87 | 1 | — | `views/settings/MemorySettings.vue · views/settings/MemoryWorkspaceSettings.vue` |
| `agenttools` | 产品能力 | 58 | — | — | `views/chat（工具结果渲染）` |
| `evaluation` | 产品能力 | 26 | 1 | — | —（暂无页面） |
| `agentmanagement` | 产品能力 | 24 | 3 | — | `views/agent` |
| `favorite` | 产品能力 | 11 | 1 | — | `views/agent · views/knowledge（列表置顶/收藏）` |
| `mcp` | 集成适配 | 147 | 4 | 2 | `views/settings/McpSettings.vue` |
| `datasource` | 集成适配 | 145 | 2 | — | `views/knowledge/settings/DataSourceSettings.vue` |
| `im` | 集成适配 | 98 | 2 | — | `views/integrations/IntegrationSettingsSection.vue` |
| `storage` | 集成适配 | 54 | 2 | 5 | `views/settings/StorageBackendSettings.vue` |
| `websearch` | 集成适配 | 49 | 3 | 2 | `views/settings/WebSearchSettings.vue` |
| `embedchannel` | 集成适配 | 23 | 1 | — | `views/embed` |
| `auth` | 控制面 | 110 | 6 | 1 | `views/auth · views/settings/TenantMembers.vue · views/system/PlatformAPIKeys.vue` |
| `audit` | 控制面 | 20 | 1 | 1 | `views/system/SystemAuditLog.vue` |
| `system` | 控制面 | 17 | 2 | 1 | `views/system` |
| `tenant` | 控制面 | 10 | — | 1 | `views/settings/TenantInfo.vue` |
| `model` | 能力配置 | 32 | 3 | 2 | `views/settings/ModelSettings.vue` |
| `vectorstore` | 能力配置 | 18 | 1 | — | `views/settings/VectorStoreSettings.vue` |
| `initialization` | 初始化向导 | 13 | 1 | — | `views/settings/OllamaSettings.vue · views/settings/ParserEngineSettings.vue` |
| `approval` | 策略库 | 7 | — | — | `views/chat/components/ToolApprovalCard.vue` |

> 文件数含 `package-info`，与 `module-package-map.md` 同口径；契约面含 `*Port` 与 `*Gateway`（后者在 C5b 统一为 `*Port`）。

## 入口与契约面

| 域 | HTTP 入口 | 对外契约面 |
|---|---|---|
| `knowledge` | `ChunkController`、`ChunkerPreviewController`、`FaqController`、`KnowledgeBaseController`、`KnowledgeBaseFileProxyController`、`KnowledgeController`、`KnowledgeOperationsController`、`KnowledgeTagController` | `ChunkSearchGateway`、`DocReaderAdminPort`、`DocReaderParsePort`、`DocumentChunkingPort`、`EmbeddingModelPort`、`EmbeddingPort`、`KnowledgeBaseCatalogPort`、`KnowledgeBaseGateway`、`KnowledgeBaseSearchGateway`、`KnowledgeCatalogPort`、`KnowledgeDocumentGateway`、`KnowledgeMaintenancePort`、`KnowledgeSpanPort` |
| `session` | `KnowledgeQaController`、`MessageController`、`MessageFileProxyController`、`MessageSuggestionController`、`SessionController`、`SessionStreamController`、`SteerController`、`TemporaryDocumentController` | `EmbedSessionPort`、`SessionQaPort` |
| `wiki` | `WikiPageController` | `WikiPageOpsPort` |
| `memory` | `MemoryController` | — |
| `agenttools` | —（无 HTTP 面） | — |
| `evaluation` | `EvaluationController` | — |
| `agentmanagement` | `AgentController`、`SkillCatalogController`、`SkillsCatalogController` | — |
| `favorite` | `UserFavoriteController` | — |
| `mcp` | `AgentToolApprovalController`、`McpCredentialsController`、`McpOAuthController`、`McpServiceController` | `McpToolRegistrationPort`、`McpToolingGateway` |
| `datasource` | `DataSourceController`、`DataSourceCredentialsController` | — |
| `im` | `ImCallbackController`、`ImChannelController` | — |
| `storage` | `FileProxyController`、`StorageBackendController` | `FileUrlPort`、`GlobalFileServicePort`、`ProviderFileStorePort`、`ResourceCatalogPort`、`StorageBackendPort` |
| `websearch` | `WebSearchController`、`WebSearchProviderController`、`WebSearchProviderCredentialsController` | `WebSearchProviderPort`、`WebSearchTempStatePort` |
| `embedchannel` | `EmbedChannelController` | — |
| `auth` | `AuthController`、`TenantAPIKeyController`、`TenantAPIPrincipalController`、`TenantCatalogController`、`TenantInvitationController`、`TenantMemberController` | `UserLookupPort` |
| `audit` | `AuditLogController` | `AuditWritePort` |
| `system` | `SystemAdminController`、`SystemController` | `SystemSettingGateway` |
| `tenant` | —（无 HTTP 面） | `TenantStorageQuotaPort` |
| `model` | `ModelController`、`ModelCredentialsController`、`ModelDebugController` | `ModelCatalogPort`、`ModelFactsPort` |
| `vectorstore` | `VectorStoreController` | — |
| `initialization` | `InitializationController` | — |
| `approval` | —（无 HTTP 面） | — |

## 怎么用

1. **找需求落点**：先按“种类”判断，再看域明细；新增域必须先在 [`capability-kinds.json`](../../scripts/capability-kinds.json) 登记。
2. **依赖规则**：`check-capability-kinds.py` 上线后执行（产品能力不得直连集成/控制面等）。
3. **端口明细**的权威来源是 [`port-map.md`](port-map.md)；本页只做域级汇总。
4. **前端页面**是人工登记；改页面路由时同步改 JSON，否则 `--check` 不报（它只校验代码侧）。
