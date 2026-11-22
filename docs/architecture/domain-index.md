<!-- 由 scripts/gen-domain-index.py 生成；不要手改。 -->
# 业务域索引（按能力种类）

> 三个问题：这个域属于哪类能力？有没有 HTTP 面？对外提供哪些契约面（Port/Gateway）？
> 分类配置在 [`scripts/capability-kinds.json`](../../scripts/capability-kinds.json)；
> 端口权威表在 [`port-map.md`](port-map.md)。

## 5 类能力

| 种类 | 域数 | 文件 | 控制器 | 说明 |
|---|---:|---:|---:|---|
| **产品能力** | 6 | 996 | 21 | 直接产生用户价值、按产品需求演进的业务能力。 |
| **集成适配** | 5 | 631 | 15 | 对接外部系统/渠道，变更主要由第三方 API 与协议驱动。 |
| **控制面** | 2 | 192 | 9 | 租户、身份、系统设置与审计，变更主要由安全合规驱动。 |
| **能力配置** | 2 | 58 | 5 | 模型/检索等运行时能力的配置面，本身不实现运行时。 |
| **初始化向导** | 1 | 11 | 1 | 首次配置的编排面，只调契约端口，不承载业务规则。 |

## 域明细

| 域 | 种类 | 文件 | 控制器 | 对外契约面 | 前端页面 |
|---|---|---:|---:|---:|---|
| `knowledge` | 产品能力 | 559 | 9 | — | `views/knowledge · views/knowledge/wiki` |
| `session` | 产品能力 | 215 | 8 | — | `views/chat` |
| `memory` | 产品能力 | 110 | 1 | — | `views/settings/MemorySettings.vue · views/settings/MemoryWorkspaceSettings.vue` |
| `agenttools` | 产品能力 | 68 | — | — | `views/chat（工具结果渲染）` |
| `agentmanagement` | 产品能力 | 27 | 2 | — | `views/agent` |
| `favorite` | 产品能力 | 17 | 1 | — | `views/agent · views/knowledge（列表置顶/收藏）` |
| `mcp` | 集成适配 | 176 | 4 | — | `views/settings/McpSettings.vue` |
| `datasource` | 集成适配 | 147 | 2 | — | `views/knowledge/settings/DataSourceSettings.vue` |
| `channel` | 集成适配 | 142 | 3 | — | `views/embed · views/settings（挂件渠道与 IM 渠道面板，合并批不改前端）` |
| `storage` | 集成适配 | 103 | 3 | — | `views/settings/StorageBackendSettings.vue` |
| `websearch` | 集成适配 | 63 | 3 | — | `views/settings/WebSearchSettings.vue` |
| `auth` | 控制面 | 164 | 8 | — | `views/auth · views/settings/TenantMembers.vue · views/settings/panels/PlatformAPIKeys.vue · views/settings/panels` |
| `audit` | 控制面 | 28 | 1 | — | `views/settings/panels/SystemAuditLog.vue` |
| `model` | 能力配置 | 38 | 4 | — | `views/settings/ModelSettings.vue` |
| `vectorstore` | 能力配置 | 20 | 1 | — | `views/settings/VectorStoreSettings.vue` |
| `initialization` | 初始化向导 | 11 | 1 | — | `views/settings/OllamaSettings.vue · views/settings/ParserEngineSettings.vue` |

> 文件数含 `package-info`，与 `module-package-map.md` 同口径；契约面含 `*Port` 与 `*Gateway`（后者在 C5b 统一为 `*Port`）。

## 入口与契约面

| 域 | HTTP 入口 | 对外契约面 |
|---|---|---|
| `knowledge` | `ChunkController`、`ChunkerPreviewController`、`FaqController`、`KnowledgeBaseController`、`KnowledgeBaseFileProxyController`、`KnowledgeController`、`KnowledgeOperationsController`、`KnowledgeTagController`、`WikiPageController` | — |
| `session` | `KnowledgeQaController`、`MessageController`、`MessageFileProxyController`、`MessageSuggestionController`、`SessionController`、`SessionStreamController`、`SteerController`、`TemporaryDocumentController` | — |
| `memory` | `MemoryController` | — |
| `agenttools` | —（无 HTTP 面） | — |
| `agentmanagement` | `AgentController`、`SkillController` | — |
| `favorite` | `UserFavoriteController` | — |
| `mcp` | `AgentToolApprovalController`、`McpCredentialsController`、`McpOAuthController`、`McpServiceController` | — |
| `datasource` | `DataSourceController`、`DataSourceCredentialsController` | — |
| `channel` | `EmbedChannelController`、`ImCallbackController`、`ImChannelController` | — |
| `storage` | `FileProxyController`、`StorageBackendController`、`StorageEngineController` | — |
| `websearch` | `WebSearchController`、`WebSearchProviderController`、`WebSearchProviderCredentialsController` | — |
| `auth` | `AuthController`、`SystemAdminController`、`SystemController`、`TenantAPIKeyController`、`TenantAPIPrincipalController`、`TenantCatalogController`、`TenantInvitationController`、`TenantMemberController` | — |
| `audit` | `AuditLogController` | — |
| `model` | `ModelController`、`ModelCredentialsController`、`ModelDebugController`、`ModelOllamaController` | — |
| `vectorstore` | `VectorStoreController` | — |
| `initialization` | `InitializationController` | — |

## 怎么用

1. **找需求落点**：先按“种类”判断，再看域明细；新增域必须先在 [`capability-kinds.json`](../../scripts/capability-kinds.json) 登记。
2. **依赖规则**：`check-capability-kinds.py` 上线后执行（产品能力不得直连集成/控制面等）。
3. **端口明细**的权威来源是 [`port-map.md`](port-map.md)；本页只做域级汇总。
4. **前端页面**是人工登记；改页面路由时同步改 JSON——路径不存在时 `--check` 会报红。
