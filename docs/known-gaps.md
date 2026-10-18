# 已知缺口（Known Gaps）

> 本文件登记「当前实现不支持的能力」。读代码遇到空分支、恒 404、no-op 或降级路径时，先查这里。
> 每项标注**状态**（设计取舍 / 待实现 / 外部依赖）、**影响**与**替代路径**。
> 代码里的类注释只留一行指针：`见 docs/known-gaps.md §N`。

## 1. 跨租户共享（org-share / shared-agent）

- **状态**：设计取舍——本仓不实现跨租户授予链。
- **影响**：知识库、chunk、Wiki、标签的跨租户授权不存在；非本租户访问一律 403/404。
- **替代**：同租户内按成员角色（owner/admin/editor/viewer）授权；API Key 主体按租户隔离。
- **涉及**：`KnowledgeSearchService`、`ChunkAccessGuard`、`KnowledgeTagService`、`WikiKbAccessGuard`、`FileAccessResolver`、`TenantProperties.enableCrossTenantAccess`（保留键、不生效）。

## 2. 资源目录 / 共享授权解析（产物、附件）

- **状态**：设计取舍——只做 owner 判定，不做资源目录解析。
- **影响**：`/sessions/{id}/artifacts/{aid}` 与文件代理对非 owner 恒 404 `artifact not accessible`。
- **替代**：owner 取会话/消息归属；公开资源走预签名 URL。
- **涉及**：`SessionController`、`MessageFileProxyController`。

## 3. 初始化：storageBackendId 解析分支

- **状态**：待实现。
- **影响**：`PUT /initialization` 携带 `storageBackendId` 时不解析、按缺省存储处理。
- **替代**：先走 `POST /storage-backends` 建后端，再在系统设置里切换。
- **涉及**：`InitializationController`、`StorageBackendResolver`。

## 4. `POST /system/sandbox-check`

- **状态**：待实现（依赖 sandbox 能力，本部署未启用）。
- **影响**：路由未登记，Spring 返回 404。
- **替代**：无。
- **涉及**：`RbacRoutePolicies`（注册表注释）、`SystemController`。

## 5. 连接器目录中的规划项

- **状态**：待实现（按产品计划逐项落地）。
- **影响**：`ConnectorCatalog` 中未实现的类型不可创建数据源；前端下拉可见但创建会失败。
- **替代**：已实现的连接器见目录中 `implemented=true` 的条目。
- **涉及**：`ConnectorCatalog`。

## 6. Agent 推荐问题：Wiki fallback

- **状态**：设计取舍——推荐问题只从 FAQ/chunk 生成。
- **影响**：没有 FAQ/chunk 的知识库，推荐问题恒为空。
- **替代**：手工配置开场问题。
- **涉及**：`CustomAgentService`、`AgentSuggestedQuestions`。

## 7. `allowed_tools → capability` 派生表

- **状态**：待实现。
- **影响**：agent 配置里的 `allowedTools` 只做白名单过滤，不派生能力表。
- **涉及**：`CustomAgentService`。

## 8. 模型运行时：debug / langfuse 装饰器

- **状态**：设计取舍——装饰器只有 `LangfuseChatClient`/`LangfuseReranker` 两条。
- **影响**：Embedding 与 Reranker 的 debug 日志装饰、LLM 的 debug 装饰不存在（等价于未启用路径）。
- **涉及**：`EmbedderFactory`、`RerankerFactory`、`LlmChatClients`。

## 9. Langfuse 概率采样

- **状态**：待实现。
- **影响**：`sampleRate` 在 (0,1) 区间时不生效，trace 恒采样。
- **替代**：`LANGFUSE_ENABLED=false` 整体关闭。
- **涉及**：`LangfuseConfig`、`LangfuseTracing`。

## 10. 提示词缓存指纹元数据

- **状态**：待实现（非行为面）。
- **影响**：`prompt_prefix_fingerprint` / `call_purpose` 未写入 span 元数据。
- **涉及**：`PipelineCommon`、`LangfuseChatClient`。

## 11. 消息建议的 Langfuse span

- **状态**：待实现。
- **影响**：`AttachTraceparent` / `StartSpan` 在消息建议链路未接线，链路无 trace。
- **涉及**：`MessageSuggestionService`。

## 12. 向量店：进程内引擎注册表

- **状态**：设计取舍——注册表为空，连通性探测走厂商直连。
- **影响**：`VectorStoreConfigService` 中注册表段落为 no-op。
- **涉及**：`VectorStoreConfigService`。

## 13. OpenSearch：超大批量 scroll

- **状态**：待实现。
- **影响**：单次写入超过批量上限时不分页，超大知识库入库可能失败。
- **替代**：调小分块批量。
- **涉及**：`OpenSearchRetrieveRepository`。

## 14. Ollama：私部署分支

- **状态**：设计取舍。
- **影响**：仅支持本地 Ollama（`localhost`），不处理自定义私部署路径。
- **涉及**：`OllamaService`。

## 15. 工具 SPI 已迁入 `contract.tool`（已关闭，C5c）

- **状态**：已关闭。`AgentTool` / `BaseTool` / `ToolDefinitions` 均在 `contract.tool`；`BaseTool` 为纯 JDK 基类。
- **说明**：schema JSON 解析留在技术适配 `platform.tool.ToolSchemas`；注册/执行逻辑仍在各 runtime。

## 16. 审批闸门未装配时的分支

- **状态**：设计取舍——`Gate` 由装配层注入，未接线时相关端点直接 500。
- **影响**：`EmbedChannelDelegateOps` 的 MCP OAuth / 工具审批受理分支在缺少 Gate 时返回 500，而不是静默放行。
- **涉及**：`EmbedChannelDelegateOps`。

## 17. IM 知识桥接的存疑接线

- **状态**：待核查（疑似未接线，非重构问题）。
- **影响**：IM 渠道的知识检索可能走空实现。
- **涉及**：`ImKnowledgeBridgeOps`。

## 18. Wiki 页面 → chunks 同步

- **状态**：待实现。
- **影响**：Wiki 页面内容不写入 `chunks` 表，不参与常规 RAG 检索（走 Wiki 专用检索）。
- **替代**：需要参与 RAG 的内容请走知识库文件；Wiki 检索走 `/api/v1/knowledgebase/{kbId}/wiki` 接口。
- **涉及**：`WikiChunkCleaner`、`WikiIngestService`。
