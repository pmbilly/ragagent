# 业务能力地图（一页纸）

> 回答一个问题：**一个业务需求，从哪进、经过谁、落在哪。**
> 问答细节见 [`qa-flow.md`](qa-flow.md)，入库细节见 [`ingest-flow.md`](ingest-flow.md)；
> 本页只做"业务链路 → 模块/包/类"的索引，不重复时序。

## 六条链路

| 业务链路 | 入口（HTTP） | 编排 | 关键阶段 | 能力/适配 | 数据落点 |
|---|---|---|---|---|---|
| **知识生产** | `KnowledgeController`（文件/URL/手工）· `DataSourceController`（同步） | `KnowledgeService` → `KnowledgeProcessWorker` | 解析 → 分块 → 向量化 → 索引 → 富化（摘要/问题/图谱/wiki） | `DocReaderParsePort` · `EmbeddingPort` · `IndexEngine` · `KnowledgeCatalogPort` | `knowledges` / `chunks` / `embeddings` / `knowledge_processing_spans` |
| **知识消费（问答）** | `KnowledgeQaController`（SSE）· `SessionStreamController`（续流） | `QaEntryRunner` → `QaTurnExecutor` → `SessionKnowledgeQaService` | 历史 → 理解 → 检索 → 重排 → 合并 → 生成 → 推流 | `HybridSearchPort` · `ModelRuntime` · `ToolRegistryPort` | `sessions` / `messages` / 流缓冲（`StreamManager`） |
| **Agent** | 同问答（`agentEnabled` / `agentId`） | `SessionAgentQaService` → `AgentEngineFactory` | 工具装配 → ReAct 循环（Think/Act/Observe）→ 事件桥 | `contract.agent.*`（工厂/句柄/运行说明）· `ToolRegistryPort` | `agent` 配置表 · 会话消息 |
| **渠道接入** | `ImCallbackController` · `EmbedChannelController` | `ImTurnOrchestrator` / `EmbedSessionAdapter` | 渠道消息归一化 → 走问答链 → 回写渠道 | `contract.<渠道>` 端口 · `SseSink` | `im_channels` · `channel_sessions` · `embed_tokens` |
| **能力配置** | `ModelController` · `VectorStoreController` · `StorageBackendController` · `WebSearchProviderController` · `McpServiceController` · `DataSourceController` | 各域 service | 配置 CRUD + 连通性测试 + 凭据托管 | `contract.model` · `contract.vectorstore` · `contract.storage` · `contract.websearch` | 各配置表（凭据 AES-GCM） |
| **初始化** | `InitializationController` | `InitializationConfigService` | 安装向导：配置 → 连通性 → 首次入库 | `contract.model.ModelRuntime` · `contract.tenant` | `system_settings` / 租户 KV |

## 改需求去哪

| 想改什么 | 先去 | 不要 |
|---|---|---|
| 检索编排（多路召回/融合） | `session/rag/plugin/PluginSearch*` + `retrieval-engine/HybridSearchService` | 改 `ChatManage` 字段当开关 |
| 加一种检索后端 | `retrieval-engine/.../engine/`（实现 `IndexEngine` 并注册） | 在 knowledge 域写分支 |
| 改入库阶段（解析/分块/向量化） | `knowledge/task/Knowledge*Stage` | 往 `KnowledgeProcessWorker.processInner` 塞分支 |
| 改提示词 | `platform/prompt/`（或域内 prompt 常量） | 散在 plugin 里拼字符串 |
| 改模型接入 | `model-runtime/.../adapter/provider/`（实现 `LlmChatClient`） | 在 domains 自建 HTTP 客户端 |
| 加一个管线步骤 | 新增 `Plugin` + `QaWiring` 注册 + 重跑 `gen-pipeline-map.py` | 修改 `PipelineEventType` 顺序而不改文档 |
| 改后台资源 CRUD | `<域>/controller` → `<域>/service` → `<域>/repository` | controller 直连 mapper |
| 改权限 | `boot/config/RbacRoutePolicies` + `platform/web/RbacInterceptor` | 在 controller 里手写角色判断 |

## 三个"在哪找"

| 想知道 | 看 |
|---|---|
| 接口实现在哪个类 | [`port-map.md`](port-map.md)（32 个 Port 对照表） |
| 事件有哪些插件、读写什么 | [`pipeline-map.md`](pipeline-map.md) |
| 模块/包边界 | [`module-package-map.md`](module-package-map.md) |

## 三条不变量

1. **租户**：跨虚拟线程必须 `TenantContextSnapshot.capture()` + `TenantScope.run()`；直接 start 会读到租户 0。
2. **顺序**：memo 异步写 → 并发守卫 → 落库 → 建流 → 异步执行 → 推流；不可换。
3. **失败**：插件返回 `PluginError` 即终止整条链；SSE 的 `error`+done 是终态，写出即退出轮询。
