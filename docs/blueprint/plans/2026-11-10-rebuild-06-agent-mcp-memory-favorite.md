# RagAgent 重建：agent、mcp、memory、favorite 迁入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `agentmanagement`、`mcp`、`memory`、`favorite` 四个域按新规范重写进 `backend/app`，并交付技能子系统的管理面、MCP 协议客户端与审批持久化，使四域相关页面在新栈上与旧系统一致。

**Architecture:** 每域按路线图第 4 节八步序列建 `api/controller/service/repository/mapper/entity/vo/bo/assembler`。agent 域同时拥有技能库。mcp 域只管服务配置、凭据、OAuth 令牌与审批记录，协议客户端用官方 SDK `mcp-core` 放 `mcp/adapter`，工具包装迁出给计划 08。memory 域的蒸馏任务由 JobRunr 承担，不再有自研队列。

**Tech Stack:** Spring Boot 3.5.16、MyBatis-Plus、Flyway、Spring Modulith、Sa-Token、JobRunr、Caffeine、Redisson、MCP Java SDK `mcp-core`、Nimbus `oauth2-oidc-sdk`、Testcontainers PostgreSQL、Playwright。

**规格来源**：`docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图：`docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 06 号计划。

## Global Constraints

继承路线图第 3 节全部约定，下列为本计划最常用的几条，原文以路线图为准：

- JDK 21、Spring Boot 3.5.16。Java 缩进 tab、行宽 140、中文 Javadoc、`@author jiangguifan`、魔法字符串收口常量或枚举。
- 业务错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号：agent 013、mcp 014、memory 015、favorite 016。枚举放各域 `api` 包，命名 `<Domain>Code`。
- 文案 key `code_<错误码>`，业务域文案放 `backend/app/src/main/resources/i18n/app-messages*.properties`，七语种加默认文件齐全。
- Flyway 脚本放 `backend/app/src/main/resources/db/migration/allenv/`，命名 `V{yyyyMMdd}_{序号}_{DDL|DML}_rebuild_{域}_{描述}_jiangguifan.sql`。
- 主键 bigint 雪花 ID，序列化为字符串。租户表 `tenant_id bigint NOT NULL` 加行级安全，策略用计划 01 Task 5 的 fail-closed 模板。软删 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
- 路径三组：`/api/admin/v1`、`/api/v1`、`/api/open/v1`。后台用户用到的接口全归 admin。请求体 VO 加 `@Valid`，响应 `RestResponse`。
- 代码质量按路线图 3.11：方法 40 行、类 400 行、参数 5 个、构造器依赖 7 个。类名不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common`，单实现不建接口，构造器注入用 `@RequiredArgsConstructor`。写法以 `docs/migration/golden-sample.md` 为参照，偏离在 `deviations.md` 登记。
- Maven 命令统一带 `-s C:/Users/jiangguifan/.m2/settings-coding-uat.xml`，`-pl` 必带 `-am`。下文用 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`。
- `git add` 只写显式路径。每个任务一次提交，提交后在 `docs/migration/progress.md` 追加一行。

## 本计划新做的设计决定（待用户核对）

只列 spec 与路线图没有定、由本计划新定的决定，按重要度排序。纯按 spec 规则机械翻译的不列。

### 一、新表结构与聚合根划分

1. **技能库归 agent 域，平台内置技能单独成表。** 决定：agent 域拥有 `skills` 管理面与 `agent-engine` 的技能目录 spi 实现。租户技能表 `skills` 带 `tenant_id` 与行级安全。平台内置层拆成全局表 `platform_skills`，无 `tenant_id`、无行级安全，只读，由 Flyway DML 写入。理由：技能引用保护要查本域 `agents.config`，同域内一个 Repository 就能查；旧表用 `tenant_id IS NULL` 表示平台层，与行级安全的 `NOT NULL` 和 fail-closed 策略冲突。否决：`tenant_id = 0` 哨兵加放宽策略，会让每张表的策略模板多一个例外分支。
2. **内置智能体 ID 用预留 bigint，HTTP 层仍用 `builtin-*` 字符串。** 决定：`agents` 表主键为雪花 bigint，内置智能体行额外有 `builtin_code`，且 `(tenant_id, builtin_code, deleted_at)` 唯一。旧代码认得 8 个内置智能体，其中 7 个在列表里展示，`builtin-wiki-fixer` 不展示。8 个在 `agent.api` 的 `BuiltinAgentCode` 枚举里各占一个 1 到 99 的预留 ID。`AgentIds.decode(String)` 与 `encode(long)` 在 HTTP 边界转换，前端常量 `builtin-quick-answer` 等保持不变。理由：前端和渠道配置里存的是 `builtin-*` 字符串，其余域按 spec 5.7.1 统一 bigint，这样两头都不用改。否决：全仓 agent ID 保持 varchar，违反 5.7.1 第 1 条。
3. **`mcp_tool_approvals` 改作待审批表，旧同名的按工具策略表改名 `mcp_tool_policies`。** 决定：旧表存的是每个工具的 `require_approval` 与 `enabled` 开关，不是待审批记录。spec 5.12 第 5 条要求待审批写入 `mcp_tool_approvals`，所以旧表改名，新表按 spec 命名。待审批表带 `kind`（`TOOL_CALL`、`OAUTH`）两种，会话中途的 OAuth 授权等待复用同一机制。理由：OAuth 等待同样阻塞线程，spec 只写了工具审批，两者不统一就要留一套线程阻塞的旧 Gate。否决：OAuth 等待保留旧 Gate，两套机制并存。
4. **记忆的主体与 MCP 令牌的主体都拆成 `principal_type` 加 `principal_id` 两列。** 决定：旧 memory 用 `web_user:<uuid>` 这类拼接串当 `subject_id`，mcp 令牌表用两列。新表统一两列，主体来自计划 03 的当前主体接口。理由：按类型批量清理、建索引都不用解析字符串。否决：保留拼接串，需要在 SQL 里 `split_part`。
5. **`user_resource_favorites.resource_id` 保留字符串。** 决定：存对外展示的资源 ID 原文，agent 收藏可能是 `builtin-quick-answer`。`user_id`、`tenant_id` 改 bigint。理由：收藏不验证资源存在，只存不透明键，不必引入编解码依赖。否决：存 bigint，收藏内置智能体要走 `AgentIds`。
6. **记忆表按四个聚合根划分写属主。** 决定：`MemoryItemRepository` 拥有 `memory_items`、`memory_item_embeddings`、`memory_tombstones`；`MemorySubjectRepository` 拥有 `memory_subjects`、`memory_extraction_sessions`；`MemoryTopicRepository` 拥有 `memory_topic_stats`；`MemoryDocAffinityRepository` 拥有 `memory_doc_affinity`。理由：拒绝一条推断记忆时要在同一事务里改条目并写墓碑，墓碑与条目是一个生命周期。否决：一张表一个 Repository，spec 反目标第 3 条。

### 二、新接口路径与 VO 形状

1. **审批与 OAuth 恢复接口合并。** 旧 `POST /api/v1/agent/tool-approvals/{pendingId}`、`POST /api/v1/agent/mcp-oauth-resolutions/{pendingId}`、`.../cancel` 三个接口，合并为 `POST /api/admin/v1/mcp-approvals/{id}/resolution`，请求体 `{decision, modifiedArgs, reason}`，`decision` 取 `APPROVE`、`REJECT`、`AUTHORIZE`、`CANCEL`。前端 API 层函数 `resolveToolApproval`、`resolveMCPOAuth`、`cancelMCPOAuth` 保持签名，在内部映射。理由：两类待审批共用一张表与一套状态机。否决：保留三条路径，控制器要重复三份状态校验。
2. **工具策略路径改为 `/mcp-services/{id}/tools/{toolName}/policy`。** 旧路径 `/tool-approvals/{toolName}` 与表改名不一致，会与新待审批资源混淆。前端 `getMCPToolApprovals`、`setMCPToolApproval`、`setMCPToolEnabled` 保持签名。
3. **OAuth 回调是公开路由。** 新路径 `GET /api/admin/v1/mcp-oauth/callback`，靠一次性 state 自证，在 Sa-Token 路由规则里放行。前端常量 `MCP_OAUTH_CALLBACK_PATH` 随之更新。已有的第三方授权服务器登记了旧回调地址，部署时要更新。
4. **agents、mcp-services、memory 同时挂 admin 与 open 两个前缀。** 控制器用路径数组映射，Service 只有一份。理由：旧 API Key 策略允许集成 Key 读写智能体、管理 MCP 服务、操作记忆（full-access），新体系里这三类 Key 属于 open 账号。技能、收藏、审批与 OAuth 恢复只挂 admin，旧系统里它们对 API Key 默认拒绝。否决：open 前缀单建控制器，同一资源两份代码。
5. **智能体响应的 `id`：内置智能体返回 `builtin-*`，自建智能体返回雪花字符串。** 路径变量 `{id}` 两种形式都接受。

### 三、Provider 与 spi 的签名

下列签名由本计划新定，名称与包以后续计划执行时核对为准，不一致按偏离登记。

1. **技能目录 spi 的实现。** 计划 02 在 `agentengine.spi` 声明 `SkillCatalog`，期望形状 `List<SkillRecord> listVisible(long tenantId)`。agent 域用 `VisibleSkillCatalog implements SkillCatalog` 实现，合并租户技能与 `platform_skills`。
2. **`AgentQueryApi`，替换旧 `AgentLookup`。** 放 `agent.api`：`boolean isOwned(long tenantId, long agentId)`；`boolean hasRow(long tenantId, long agentId)`；`Optional<AgentBo> find(long agentId, Locale locale)`；`List<Long> builtinIds()`；`AgentConfigBo normalizeConfig(String rawConfigJson)`；`List<SuggestedQuestionBo> suggestedQuestions(long agentId, List<Long> kbIds, int limit)`。channel、session 在上级调用。
3. **`AgentDeletedEvent(long tenantId, long agentId)`，替换旧 `AgentChannelCleaner`。** 放 `agent.api`，删除提交后发布，计划 07 的 channel 监听并软删该智能体的渠道。理由：channel 依赖 agent 做归属校验，agent 再反调 channel 就成环。否决：保留 Cleaner 端口让 agent 域依赖 channel，Modulith 报环。
4. **`ModelReferenceProvider` 的 agent 实现。** `AgentModelReferenceProvider` 查 `agents.config` 里引用该模型的智能体，覆盖 `modelId`、`rerankModelId`、`vlmModelId`、`asrModelId`、`queryUnderstandModelId`、`questionSuggestions.followUps.modelId`。
5. **mcp 对外四个 api。** 见下面“对外承诺”，签名在该节。
6. **`ApprovalResolutionListener`。** 由 mcp 在 `mcp.api` 声明，计划 08 的 session 实现：`void onResolved(ApprovalResolvedBo resolved)`。用户决定后 mcp 调用它，session 负责把决定经 steer 通道注入运行中的轮次，轮次已结束则开后续轮次。
7. **`MemoryTranscriptProvider`。** 由 memory 在 `memory.api` 声明，计划 08 的 session 实现：`List<TranscriptLineBo> readSince(long sessionId, TranscriptCursor cursor, int limit)`。理由：memory 是业务域，session 是编排域，只能由上级实现下级声明的接口。
8. **mcp 工具包装迁出到 agenttools。** 决定：旧 `mcp/tool` 包 13 个类共 3,308 行与两个 Adapter 不留在 mcp 域，由计划 08 接收。清单：`McpToolWrapper`、`McpCatalog`、`McpCatalogPagination`、`McpDiscoverTool`、`McpCallTool`、`McpExposure`、`McpTooling`、`McpInputSchemaValidator`、`McpJsonCodec`、`McpToolNaming`、`McpRegisteredTool`、`McpOAuthPromptSupport`，丢弃 `ApprovalEventBuses`、`McpToolRegistrationAdapter`、`McpApprovalOpsAdapter`。逐类说明与新依赖的 api 见“对外承诺”。理由：spec 5.12 第 4 条，mcp 域只管配置、凭据、令牌与审批记录。否决：工具包装留在 mcp，则 mcp 要依赖 agent-engine 的工具类型，上下级倒挂。
9. **对计划 04 的要求 `KnowledgeQuestionApi`。** agent 的推荐问题需要读知识库数据，旧实现直接查 `chunks`、`knowledge_bases`、`knowledge_tags`。要求 knowledge 在 `knowledge.api` 提供：`List<Long> filterExistingKbIds(List<Long> kbIds)`；`List<Long> resolveKnowledgeIdsByTags(long kbId, List<Long> tagIds)`；`List<String> listFaqQuestions(KnowledgeScopeBo scope, int limit)`；`List<String> listGeneratedQuestions(KnowledgeScopeBo scope, int limit)`。名称以计划 04 为准，缺失时本计划 Task 8 先停下登记。

### 四、与旧行为不一致的地方

1. **工具审批不再占用线程。** 旧 Gate 在工具调用线程里阻塞等人，最长 10 分钟。新实现把待审批写库后本轮挂起，用户决定后注入或续跑。SSE 事件序列在审批前后会比旧系统多一次“轮次挂起与恢复”，审批卡片与按钮的界面和交互不变。这是 spec 5.12 第 5 条的明确要求，影响计划 08 的回归对拍，对拍时审批场景单列。
2. **删除 Redis Pub/Sub 与 fail-open 开关。** 策略查询失败一律要求审批，不再提供放行开关。
3. **MCP stdio 传输永久拒绝。** 保存与连接时返回错误码，字段与列保留。
4. **内置智能体行按租户唯一。** 旧表主键 `(id, tenant_id)` 已含租户，但 `SkillCatalogService.listReferences` 的 SQL 带 `OR is_builtin` 兜底，注释称内置行 `tenant_id` 取自首个物化它的空间。两处说法冲突，**待执行方核对**冻结点开发库里内置行的实际分布。新表用唯一索引保证每租户每个内置智能体只有一行，引用检查不再兜底。
5. **内置智能体只允许改 `config`。** 旧 `updateBuiltinAgent` 对内置智能体忽略名称、描述、头像，只写配置，无库行时按 YAML 模板首次创建。新实现保持。复制智能体时名称后缀旧代码写死“ (副本)”，新实现改用文案 key `agent.copy.suffix`，七语种各给一个后缀，避免非中文环境出现中文后缀。
6. **记忆导出在无数据时返回 `items: []`。** 旧实现故意返回 `null`，前端类型 `MemoryItem[] | null` 两种都能处理，新实现统一为空数组。
7. **记忆蒸馏队列由 JobRunr 承担。** 防抖、最小间隔、重试预算 2 次的语义保留，投递不再有 Redis 与进程内两套实现。
8. **ID 全部字符串化。** 前端类型里 `tenantId: number` 的字段，API 层适配后仍给 number，具体字段见各域前端表。

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 | framework 的 `RestResponse`、`BusinessRuntimeException`、`CodeCache`、`@IgnoreTenant`、`@Encrypted`、`TenantAwareJdbcTemplate`、`PostgresContainerTest`；`@ragagent/api-client`；Task 16 的 `docs/optimization/spikes/2026-11-11-mcp-sdk-verification.md`；Task 17 的 `docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md`；e2e 的 `tests/e2e/PAGES.md` |
| 计划 02 | `agentengine.spi.SkillCatalog`、`SkillRecord`、`SkillFrontmatter` 的 `api` 封装；`agentengine.api` 的 `ToolCancellation`；model-runtime 的 `LlmChatClient`、`EmbeddingPort`；Spring Bean 形式的 `SsrfGuard` |
| 计划 03 | `auth.api` 的当前主体接口，本计划按 `CurrentPrincipal.get()` 返回 `Principal(type, id)` 消费；`tenant.api` 的租户配置读取（`memory-config` 一项）；`model.api` 的模型查询与 `ModelReferenceProvider` 声明；`audit.api` 的审计写入；`docs/migration/golden-sample.md`；权限点命名 |
| 计划 04 | `KnowledgeQuestionApi`；JobRunr 的 `JobScheduler` Bean 与角色开关 |

### 交付

1. 四个域的表、接口、Service、前端 API 层，以及技能库管理面。
2. `mcp.api` 的四个 api，供计划 08 的 agenttools 与计划 07 的 channel 调用。
3. 迁出到 agenttools 的 MCP 工具类清单，见下节。
4. 对路线图第 6 节交接表的增补，见下节。

### 对外承诺

**mcp 对外四个 api**，包 `com.ragagent.mcp.api`：

| 接口 | 方法 | 消费方 |
|---|---|---|
| `McpToolGatewayApi` | `List<McpServiceBo> listEnabledServices(McpServiceSelection selection)`；`McpCatalogBo loadCatalog(long serviceId)`；`McpCallResultBo callTool(McpCallBo call, ToolCancellation cancellation)` | agenttools |
| `McpToolPolicyApi` | `boolean needsApproval(long serviceId, String toolName)`；`boolean isEnabled(long serviceId, String toolName)`；`Map<String, Boolean> enabledTools(long serviceId, List<String> toolNames)` | agenttools |
| `McpApprovalApi` | `PendingApprovalBo openToolApproval(OpenToolApprovalBo request)`；`PendingApprovalBo openOAuthAuthorization(OpenOAuthBo request)`；`Optional<ApprovedCallBo> claimApprovedCall(long approvalId)`；`void resolve(long approvalId, ApprovalDecisionBo decision)` | agenttools、channel |
| `McpOAuthApi` | `McpOAuthAuthorizationBo authorize(long serviceId, String redirectUri)`；`McpOAuthStatusBo status(long serviceId, String attemptId)` | channel |

`McpServiceSelection` 取 `ALL`、`SELECTED` 加服务 ID 列表。`claimApprovedCall` 用 `UPDATE ... WHERE status='APPROVED'` 原子置 `EXECUTING`，保证一次决定只执行一次。`resolve` 在落库后调用 `ApprovalResolutionListener`。channel 的访客审批入口调用 `resolve`，替换旧 `McpApprovalOps` 契约。

**memory 四个 api**，包 `com.ragagent.memory.api`：`MemoryRecallApi`（`boolean isAvailable()`、`MemoryRecallBo recall(String query)`、`MemoryRetrievalContextBo retrievalContext()`、`MemorySearchBo search(String query, int limit)`）；`MemoryExtractionApi`（`void scheduleExtraction(long sessionId, long messageId, long chatModelId)`）；`MemoryDocumentApi`（`Map<Long, Integer> affinityOf(List<Long> knowledgeIds)`、`void recordAnswerSources(List<DocAffinityBo> refs)`）；`MemoryTopicApi`（`List<String> observeQuestionTopics(List<String> topics)`）。租户与主体从当前请求或 JobRunr 任务参数里的租户上下文取，不作为参数传入，沿用旧语义。

**agent 的 api**：`AgentQueryApi`、`AgentDeletedEvent`、`AgentIds`、`BuiltinAgentCode`，签名见上。

**迁出到 agenttools 的 MCP 工具类**（计划 08 接收，本计划只在 Task 29 产出交接文档 `docs/migration/handoff-mcp-to-agenttools.md`，不建 agenttools 包）：

| 旧类 | 行数 | 新位置与说明 |
|---|---|---|
| `McpToolWrapper` | 497 | agenttools，改为调用 `McpToolGatewayApi.callTool` 与 `McpToolPolicyApi`，审批改为 `openToolApproval` 后挂起 |
| `McpCatalog` | 543 | agenttools，按需目录，数据来自 `McpToolGatewayApi.loadCatalog` |
| `McpCatalogPagination` | 215 | agenttools |
| `McpDiscoverTool`、`McpCallTool` | 276、132 | agenttools，发现与调用两个代理工具 |
| `McpExposure`、`McpTooling` | 314、309 | agenttools，注册编排；`McpToolRegistrationAdapter` 的逻辑并入 |
| `McpInputSchemaValidator`、`McpJsonCodec`、`McpToolNaming`、`McpRegisteredTool` | 440、108、84、80 | agenttools |
| `McpOAuthPromptSupport` | 271 | agenttools，改为 `openOAuthAuthorization` 后挂起 |
| `ApprovalEventBuses` | 31 | 丢弃，事件由 session 的事件流发射 |

**对路线图第 6 节的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| agent（06） | `AgentQueryApi`、`AgentDeletedEvent` | channel 校验归属、删除智能体时清渠道 | channel（07）监听事件，session（08）调用查询 |
| mcp（06） | `ApprovalResolutionListener` | 审批决定后恢复轮次 | session（08） |
| memory（06） | `MemoryTranscriptProvider` | 读会话消息做蒸馏 | session（08） |
| knowledge（04） | `KnowledgeQuestionApi` | 推荐问题数据源 | 计划 04 |
| agent-engine（02） | `SkillCatalog` spi | 技能披露 | agent（06） |
| model（03） | `ModelReferenceProvider` | 模型被引用 | agent（06）增补实现 |

# 1. 盘点

以下数据取自冻结点 `rebuild-freeze` 的工作区。执行方在 Task 3、11、20 的第一步核对，有差异先改计划。

## 1.1 规模

| 域 | 旧位置 | 文件 | 行数 | 旧测试 | 备注 |
|---|---|---|---|---|---|
| agent | `domains/.../agentmanagement` | 27 | 3,097 | 3 个，221 行 | 另有技能子系统在 `agent-engine/.../agent/skills`（`SkillCatalogService` 376、`SkillEntity` 68）与 `contract/.../SkillCatalogPort` 102 行 |
| mcp | `domains/.../mcp` | 171 | 18,111 | 29 个，5,615 行 | `protocol` 3,413、`oauth` 3,647、`tool` 3,308、`approval` 1,622 |
| memory | `domains/.../memory` | 91 | 12,856 | 11 个，3,349 行 | 契约类在 `contract/.../memory`（`MemoryConfig` 289、`MemoryKeys` 322、`MemoryKinds` 127 等） |
| favorite | `domains/.../favorite` | 13 | 415 | 0 | |

## 1.2 多实现点盘点

路线图 3.11 要求只在真实多实现处建“窄接口加 Spring 登记表”。本计划盘点结论：

| 位置 | 实现数 | 结论 |
|---|---|---|
| MCP 鉴权方式：无、API Key 头、Bearer、OAuth 逐用户令牌 | 4 | **建接口。** `McpAuthStrategy` 一个方法 `void apply(McpRequestHeaders headers, McpAuthContext context)`，4 个实现注册为 Bean，`McpClientFactory` 注入 `Map<McpAuthType, McpAuthStrategy>`。这是真实变化点：旧代码在 `McpAuthHeaders` 里用分支堆了这 4 种，新增签名或双向 TLS 方式时只加一个类 |
| MCP 传输：SSE、Streamable HTTP、stdio | 2 加 1 拒绝 | **不建接口。** SDK 提供前两种的传输类，`McpClientFactory` 用一个 `switch`。stdio 由 `McpTransportType.isSupported()` 在保存时拒绝 |
| 审批种类：工具调用、OAuth 授权 | 2 | 枚举 `McpApprovalKind`，不建接口 |
| 记忆种类：profile、preference、fact、task、interest | 5 种数据 | 枚举 `MemoryKind`，字段 `resident`、`renderOrder`，不建接口。它们差别在数据而不在行为 |
| 记忆召回排序：字面匹配、向量匹配，用 RRF 融合 | 2 | 不建接口。两个排序在 `MemoryRecallService` 里顺序调用，只有两路，没有第三个分支 |
| 推荐问题来源：智能体配置的精选、知识库的 FAQ 与生成问题 | 2 | 不建接口，`SuggestedQuestionMixer` 合并两路 |
| 收藏资源类型：kb、agent | 2 | 枚举 `FavoriteResourceType` |
| 记忆任务队列：进程内、Redis | 删除 | JobRunr 取代 |

## 1.3 旧接口到新接口

规则：旧前缀 `/api/v1` 一律改 `/api/admin/v1`。下表只标需要说明的行，其余是机械换前缀。“两前缀”表示同时挂 `/api/admin/v1` 与 `/api/open/v1`。

| 资源 | 旧接口 | 新接口与说明 |
|---|---|---|
| agent | `GET /agents`、`GET /agents/placeholders`、`GET /agents/type-presets`、`POST /agents`、`GET/PUT/DELETE /agents/{id}`、`POST /agents/{id}/copy`、`GET /agents/{id}/suggested-questions` | 两前缀，机械换前缀。`{id}` 接受 `builtin-*` 与雪花字符串 |
| skills | `GET /skills`、`GET/POST /skills/catalog`、`GET/PUT/DELETE /skills/catalog/{id}`、`GET /skills/catalog/{id}/files`、`GET /skills/catalog/{id}/files/content` | 仅 admin，机械换前缀。路径沿用旧的 `catalog`，前端 API 层不变 |
| favorites | `GET /user/favorites?type=`、`POST /user/favorites`、`DELETE /user/favorites/{type}/{id}` | 仅 admin，机械换前缀。创建返回 201 改为 200 加外壳，API 层不依赖状态码 |
| mcp 服务 | `GET/POST /mcp-services`、`GET/PUT/DELETE /mcp-services/{id}`、`POST /{id}/test`、`GET /{id}/tools`、`GET /{id}/resources`、`GET /{id}/metadata`、`POST /{id}/metadata/refresh`、`POST /{id}/usage-instructions/generate` | 两前缀，机械换前缀 |
| mcp 凭据 | `PUT /mcp-services/{id}/credentials`、`DELETE /mcp-services/{id}/credentials/{field}` | 两前缀，机械换前缀 |
| mcp 工具策略 | `GET /mcp-services/{id}/tool-approvals`、`PUT /mcp-services/{id}/tool-approvals/{toolName}` | **非机械**：`GET /mcp-services/{id}/tool-policies`、`PUT /mcp-services/{id}/tools/{toolName}/policy`，见决定二第 2 条 |
| mcp OAuth | `POST /mcp-services/{id}/oauth/authorize-url`、`GET /{id}/oauth/status`、`DELETE /{id}/oauth/token`、`GET /mcp-oauth/callback` | 前三条两前缀机械换前缀。回调 **非机械**，见决定二第 3 条 |
| mcp 待审批 | `POST /agent/tool-approvals/{pendingId}`、`POST /agent/mcp-oauth-resolutions/{pendingId}`、`POST .../cancel` | **非机械**：仅 admin，`POST /mcp-approvals/{id}/resolution`，见决定二第 1 条 |
| memory | `GET/PUT /memory/settings`、`GET/POST/DELETE /memory/items`、`PUT/DELETE /memory/items/{id}`、`POST /memory/items/{id}/confirm`、`.../reject`、`GET /memory/topics`、`POST /memory/topics/{id}/promote`、`DELETE /memory/topics/{id}`、`GET /memory/documents`、`DELETE /memory/documents/{id}`、`GET /memory/export`、`POST /memory/consolidate` | 两前缀，机械换前缀。列表分页形状 `{items, page, pageSize, total}` 沿用 `PageData` 之外的原形，由 VO `MemoryPageVo` 保持 |
| memory 配置 | `GET/PUT /tenants/kv/memory-config` | 归 tenant 域，计划 03 的租户配置接口。本计划只在前端 API 层改指向 |

## 1.4 旧表到新表

通用规则：旧 `id varchar(36)` 改 bigint 雪花，`tenant_id integer` 改 bigint，`timestamp` 改 `timestamptz`，软删改哨兵值，租户表都开行级安全。下面只写差异与新增。

**agent 域**

| 新表 | 旧表 | 列与约束 | 写属主 |
|---|---|---|---|
| `agents` | `custom_agents` | `id`、`tenant_id`、`builtin_code varchar(64) NULL`、`name varchar(255) NOT NULL`、`description text`、`avatar varchar(64)`、`config jsonb NOT NULL DEFAULT '{}'`、`runnable_by_viewer boolean NOT NULL DEFAULT true`、`created_by bigint`、`created_at`、`updated_at`、`deleted_at`。旧 `is_builtin` 由 `builtin_code IS NOT NULL` 表达，删列。部分唯一索引 `(tenant_id, builtin_code, deleted_at) WHERE builtin_code IS NOT NULL`，索引 `(tenant_id, created_at DESC)` | `AgentRepository` |
| `skills` | `skills` | `id`、`tenant_id bigint NOT NULL`、`slug varchar(64)`、`name varchar(128)`、`description text`、`content text`、`version int`、`created_by bigint`、`created_at`、`updated_at`、`deleted_at`。唯一索引 `(tenant_id, slug, deleted_at)` | `SkillRepository` |
| `platform_skills` | `skills` 中 `tenant_id IS NULL` 的行 | 同 `skills` 去掉 `tenant_id`，`slug` 唯一。写入只经 Flyway DML。**不开行级安全**，加入 `gac.furnace.tenant.ignore-tables` | `PlatformSkillRepository`，应用只读 |

旧 `custom_agents.created_by` 是用户 UUID 字符串，新列存计划 03 的用户 bigint ID。平台技能的种子数据来源：旧库里 `tenant_id IS NULL` 的行，仓库内未找到插入脚本，执行方先在 `docs/migration/progress.md` 记录来源。**待执行方核对**：`grep -rn "INSERT INTO skills" migrations domains` 无结果时，种子 DML 从冻结点开发库导出。

**mcp 域**

| 新表 | 旧表 | 列与约束 | 写属主 |
|---|---|---|---|
| `mcp_services` | `mcp_services` | `name`、`description`、`enabled`、`transport_type`、`url`、`headers jsonb`、`auth_config jsonb`、`advanced_config jsonb`、`stdio_config jsonb`、`env_vars jsonb`、`builtin boolean`、`usage_instructions text`、`credential_api_key text`、`credential_token text`（后两列 `@Encrypted`，取代旧 `McpSecretTypeHandler`）。`headers` 与 `auth_config.customHeaders` 中的敏感值在落库前加密，加密字段范围**待执行方核对** `McpAuthConfigTypeHandler`（125 行）后在 DDL 任务里写死 | `McpServiceRepository` |
| `mcp_tool_policies` | `mcp_tool_approvals` | `service_id bigint`、`tool_name varchar(512)`、`require_approval boolean`、`enabled boolean`。唯一 `(tenant_id, service_id, tool_name)` | `McpToolPolicyRepository` |
| `mcp_metadata` | `mcp_metadata` | `service_id`、`principal_type`、`principal_id`、`config_fingerprint varchar(64)`、`tools jsonb`、`instructions`、`server_name`、`server_version`、`server_description`、`synced_at`。主键 `(tenant_id, service_id, principal_type, principal_id)` 的新代理键仍用雪花 `id`，上述四列唯一 | `McpCatalogSnapshotRepository` |
| `mcp_oauth_clients` | `mcp_oauth_clients` | `service_id`、`client_id`、`client_secret`（`@Encrypted`）、`redirect_uri`。唯一 `(tenant_id, service_id)` | `McpOAuthClientRepository` |
| `mcp_oauth_tokens` | `mcp_oauth_tokens` | `principal_type`、`principal_id`、`service_id`、`access_token`、`refresh_token`（两列 `@Encrypted`）、`token_type`、`expires_at timestamptz`、`refresh_lease_id`、`refresh_lease_until`。删旧 `user_id` 列，它与 `principal_id` 重复。唯一 `(tenant_id, principal_type, principal_id, service_id)` | `McpOAuthTokenRepository` |
| `mcp_tool_approvals` | 新表 | `kind varchar(16)`、`status varchar(16)`（`PENDING`、`APPROVED`、`REJECTED`、`EXECUTING`、`DONE`、`EXPIRED`、`CANCELLED`）、`service_id`、`service_name`、`tool_name`、`registered_tool_name`、`tool_call_id`、`args jsonb`、`modified_args jsonb`、`reason`、`session_id bigint`、`assistant_message_id bigint`、`request_id`、`requester_principal_type`、`requester_principal_id`、`expires_at timestamptz`、`decided_at`、`decided_by`。索引 `(status, expires_at)`、`(tenant_id, session_id)` | `McpToolApprovalRepository` |

OAuth 一次性 state 与 PKCE 校验码继续放 Redis，键 `mcp:oauth:state:<state>`，TTL 与旧实现一致，由 `OAuthStateCacheRepository` 经 Redisson `RBucket` 读写，**不进表**。旧 `OAuthStateStore` 的 GC 线程随 TTL 删除。

**memory 域**（列语义与旧表一致，只写差异）

| 新表 | 旧表 | 差异 | 写属主 |
|---|---|---|---|
| `memory_items` | `memory_items` | `principal_type`、`principal_id` 取代 `subject_id`。`source_session_id`、`source_message_id`、`superseded_by`、`replaces_id` 改 bigint。`kind`、`origin`、`status` 保持 varchar，Java 侧用枚举。索引 `(tenant_id, principal_type, principal_id, status)`、`(tenant_id, principal_type, principal_id, normalized_key)` | `MemoryItemRepository` |
| `memory_item_embeddings` | 同名 | `item_id bigint`，保留 `embedding halfvec`，丢弃旧 `vector bytea` 列。向量检索 SQL 写 Mapper XML，使用 pgvector 操作符 | `MemoryItemRepository` |
| `memory_tombstones` | 同名 | `fingerprint varchar(64)`，其余同旧 | `MemoryItemRepository` |
| `memory_subjects` | 同名 | 唯一 `(tenant_id, principal_type, principal_id)`。丢弃 `extract_cursor`、`pending_sessions`、`extraction_state` 三个遗留列，旧代码注释说明新 worker 从不推进它们，系统未上线无存量需要导入 | `MemorySubjectRepository` |
| `memory_extraction_sessions` | 同名 | 主键改雪花 `id`，唯一 `(tenant_id, principal_type, principal_id, session_id)` | `MemorySubjectRepository` |
| `memory_topic_stats` | 同名 | 唯一 `(tenant_id, principal_type, principal_id, normalized_key)` | `MemoryTopicRepository` |
| `memory_doc_affinity` | 同名 | `knowledge_id`、`knowledge_base_id` 改 bigint。唯一 `(tenant_id, principal_type, principal_id, knowledge_id)` | `MemoryDocAffinityRepository` |

**favorite 域**：`user_resource_favorites`，`user_id bigint`、`tenant_id`、`resource_type varchar(16)`、`resource_id varchar(64)`、`created_at`。主键用雪花 `id`，唯一 `(tenant_id, user_id, resource_type, resource_id)`。旧表是复合主键，改单列代理键以符合 5.7.1。写属主 `FavoriteRepository`。

## 1.5 旧类到新类

大类按“变化原因”拆分，不换名搬运。行数栏是新类预算上限。

**agent**

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `CustomAgentService` 434 | `AgentService` ≤300（创建、更新、删除、复制、归属校验）、`AgentListAssembler` ≤200（内置模板与行合并、本地化、creator 筛选与名称回填） | 用例与列表组装是两个变化原因 |
| `BuiltinAgentRegistry` 259 | `BuiltinAgentTemplates` ≤250（装载 `builtin_agents.yaml`、提示词引用解析、按语言选择） | 保持一个类，同一变化原因 |
| `AgentConfigJson` 212、`AgentPlaceholders` 67、`AgentTypePresets` 135 | `AgentConfigNormalizer` ≤200、`AgentPromptPlaceholders` ≤80、`AgentTypePresetCatalog` ≤150 | 仅改名与去 Go 语义 |
| `AgentSuggestedQuestions` 527 | `SuggestedQuestionService` ≤250、`SuggestedQuestionMixer` ≤150（轮询与混合合并）、`TagScopeResolver` ≤150（标签范围转知识 ID，调 `KnowledgeQuestionApi`） | 流程、合并算法、范围解析 |
| `AgentQuestionMapper` 105、`AgentQuestionRepository` 42 | 删除 | 数据改由 `KnowledgeQuestionApi` 提供 |
| `CustomAgentMapper` 53、`CustomAgentRepository` 37、`CustomAgentEntity` 74 | `AgentMapper` 加 XML、`AgentRepository extends ServiceImpl`、`Agent` | 注解 SQL 清零 |
| `AgentLookupAdapter` 111 | `AgentQueryService implements AgentQueryApi` ≤200 | 直接实现 api，不做转发层 |
| `AgentController` 301、`AgentResponses` 282、`AgentView`、`CustomAgentResult` | `AgentController` ≤200、`AgentVo`、`AgentConfigVo`、`CreateAgentReqVo`、`UpdateAgentReqVo`、`AgentAssembler`（MapStruct） | `AgentResponses` 的 282 行 Go 形状手写序列化丢弃 |
| `SkillController` 333 | `SkillController` ≤150、`SkillSummaryVo`、`SkillDetailVo`、`SkillReferenceVo`、`CreateSkillReqVo`、`UpdateSkillReqVo` | 内嵌 record 移到 `vo` |
| `SkillCatalogService` 376（agent-engine） | `SkillService` ≤250、`SkillReferenceChecker` ≤120、`VisibleSkillCatalog` ≤80 | 管理用例、引用保护、给引擎的只读目录 |
| `SkillEntity`、`SkillMapper`、`SkillCatalogPort` 102 | `Skill`、`PlatformSkill`、`SkillMapper` 加 XML；`SkillCatalogPort` 解散，5 个异常换成 `AgentCode` 加 `BusinessRuntimeException` | contract 解散 |

**mcp**

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `McpServiceManagement` 631 | `McpServiceService` ≤320（增删改查、内置保护、URL 校验）、`McpCredentialService` ≤150、`McpServiceProbe` ≤200（测试连接、列工具与资源） | 配置生命周期、凭据、探测 |
| `McpServiceController` 346、`McpServiceCrudOps` 401、`McpUsageInstructionsOps` 288 | `McpServiceController` ≤200、`McpServiceToolController` ≤200（test、tools、resources、metadata、tool policy 接口）、`McpUsageInstructionWriter` ≤250 | 控制器按资源拆，LLM 生成独立成类 |
| `McpCredentialsController` 148、`McpOAuthController` 229、`AgentToolApprovalController` 45 | `McpCredentialController` ≤120、`McpOAuthController` ≤200、`McpApprovalController` ≤120 | 机械 |
| `McpMetadataService` 372、`McpMetadataException` 112、`McpConfigFingerprint` 207 | `McpCatalogSnapshotService` ≤300、异常并入 `McpCode`、`McpConfigFingerprint` ≤120 | 快照读写与同步 |
| `McpToolApprovalService` 80、`McpToolPolicySource`、`ToolPolicyChecker`、`ToolEnabledChecks` 等 | `McpToolPolicyService implements McpToolPolicyApi` ≤200 | 一个类承担全部策略读写 |
| `McpOAuthFlowService` 269、`AgentToolApprovalService` 129 | `McpOAuthFlowService implements McpOAuthApi` ≤250；`McpApprovalService implements McpApprovalApi` ≤300 | 授权流程与审批状态机 |
| `McpToolRegistrationAdapter` 107 中的列服务、读目录、调用逻辑 | `McpToolGatewayService implements McpToolGatewayApi` ≤300（新类，承接列启用服务、读目录快照、经连接池调用工具） | 注册编排迁出，数据面留在 mcp |
| `Gate` 647 与 `approval` 包其余 15 个类共 1,622 行 | 删除。由 `McpApprovalService`、`McpToolApprovalRepository`、`ApprovalResolutionListener`、`McpApprovalExpiryJob`（≤100）取代 | 线程阻塞模型整体替换 |
| `protocol` 包 3,413 行，含 `DefaultMcpClient` 482、`SseTransport` 341、`StreamableHttpTransport` 322、`McpClientManager` 516、`McpHttp` 165、`McpAuthHeaders` 194、`McpCancellation` 119 | 官方 SDK 取代。保留的职责：`McpClientFactory` ≤150、`McpConnectionPool` ≤200（Caffeine，键为服务 ID、主体、配置版本，`removalListener` 里关闭连接）、`McpCancellationNotifier` ≤100（发 `notifications/cancelled`）、`McpAuthStrategy` 与 4 个实现各 ≤60 | 协议实现删除，只留适配 |
| `oauth` 包 3,647 行，含 `OAuthDiscovery` 438、`OAuthRuntime` 399、`OAuthManager` 361、`OAuthTokenOps` 294、`OAuthStateStore` 241、`OAuthHandler` 215 | Nimbus 取代协议步骤。保留：`OAuthDiscoveryClient` ≤300（RFC 8414 与 RFC 9728 发现，缺口按计划 01 Task 17 结论补）、`OAuthTokenExchanger` ≤250（授权码换令牌与刷新）、`OAuthRefreshCoordinator` ≤250（刷新租约，沿用 `refresh_lease_*` 列）、`OAuthStateCacheRepository` ≤150、`GuardedHttpSender` ≤80（把 `SsrfGuard` 接到 Nimbus 的 HTTP 层） | 发现、令牌交换、刷新并发、状态存取是四个变化原因 |
| `McpOAuthRepository` 169、`DbTokenStore`、`ManagedTokenStore`、`OAuthRepository`、`McpOAuthRepositoryAdapter` | `McpOAuthTokenRepository`、`McpOAuthClientRepository` | 去掉三层包装 |
| `McpPrincipal` 126 | 删除，用 `auth.api` 的 `Principal` | 计划 03 提供 |
| `McpAuthConfigTypeHandler` 125、`McpSecretTypeHandler` 78、`McpToolListTypeHandler` 67 | 删除。`@Encrypted` 加 Jackson jsonb 处理器 | framework 提供 |
| `tool` 包 13 个类 3,308 行、`McpToolRegistrationAdapter` 107、`McpApprovalOpsAdapter` 72 | 迁出给 agenttools，见“对外承诺”。两个 Adapter 删除 | 职责错位，spec 5.12 第 4 条 |
| `McpWiring`、`McpOAuthWiring` | 并入 `com.ragagent.config.McpAssemblyConfig` | 装配只在 config 包 |

**memory**

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `MemoryService` 440 + `MemoryCatalogOps` 516 | `MemoryItemService` ≤350（列表、创建、更新、删除、确认、拒绝、清空、导出）、`MemorySettingsService` ≤150（设置与主体开关，吸收 `MemoryScopeGate`） | 条目生命周期与设置 |
| `MemoryInsightOps` 414 | `MemoryTopicService implements MemoryTopicApi` ≤250、`MemoryDocumentService implements MemoryDocumentApi` ≤200、`MemoryRecallService` 中的 `retrievalContext` 与 `search` | 话题、文档亲和、召回上下文三个变化原因 |
| `MemoryRecallOps` 253 + `MemoryRecallSelector` 284 + `MemoryLexical` 318 | `MemoryRecallService implements MemoryRecallApi` ≤350、`LexicalScorer` ≤250、`ResidentBlockRenderer` ≤150 | 召回流程、字面打分、常驻块渲染 |
| `MemoryExtractionService` 727 + `MemoryTranscriptOps` 159 + `MemoryWritePipeline` 247 | `MemoryExtractionScheduler implements MemoryExtractionApi` ≤200、`MemoryExtractionJob` ≤250（JobRunr 任务体）、`TranscriptSegmenter` ≤250、`MemoryWriter` ≤250（去重、取代、待确认状态） | 调度、执行、转写切段、写入规则 |
| `MemoryExtractionLlm` 396 + `MemoryExtractionModelResolver`、`DefaultMemoryModelResolver`、`MemoryModelResolver` | `MemoryExtractionClient` ≤350（提示词、调用、输出解析与修复）、`MemoryExtractionPrompt` ≤200（系统提示词与 JSON schema 常量）、`MemoryModelResolver` ≤120 | 提示词与调用分开；三个解析类合一 |
| `MemoryConsolidationService` 529 | `MemoryConsolidationService` ≤350、`MemoryClusterer` ≤200 | 流程与聚类算法 |
| `MemoryVectorService` 386 + `MemoryVectorStore` 327 | `MemoryEmbeddingService` ≤300、向量 SQL 进 `MemoryItemMapper.xml` | 服务与存取 |
| `MemoryRepository` 461 + `MemoryIndexStore` 627 + `MemoryItemStore` 457 + `MemoryTxTemplate` 52 | 四个 Repository，各 ≤300，见 1.4 | 按聚合根；事务放 Service，`MemoryTxTemplate` 删除 |
| `MemoryTopicResolver` 451、`MemoryText` 296、`MemoryRender` 145 | `MemoryTopicNormalizer` ≤300、`MemoryTextNormalizer` ≤250 | 去 Go 语义工具 |
| `MemoryController` 531、`MemoryDocView` 等 DTO | `MemoryItemController` ≤200（items 与 settings）、`MemoryInsightController` ≤200（topics、documents、export、consolidate）、各 VO record | 控制器超限，按资源拆 |
| `runtime` 包：两个队列实现、`MemoryTaskQueueWiring`、`MemoryTrace` 156 | 删除。追踪上下文由 JobRunr 过滤器传播，**待执行方核对**计划 04 的 JobRunr 追踪方案 | 队列由 JobRunr 取代 |
| 契约类 `MemoryConfig` 289、`MemoryKeys` 322、`MemoryKinds` 127、`MemoryItemView` 等 | `MemoryConfig` record 进 `memory.api`；`MemoryKinds` 变枚举 `MemoryKind`；`MemoryKeys` 的键常量收入 `MemoryConfig` 与 `MemoryTextNormalizer` | contract 解散 |

**favorite**：`UserFavoriteController` 122 → `FavoriteController` ≤100；`UserResourceFavoriteService` 74 → `FavoriteService` ≤80；Repository、Mapper、Entity 各一个。无拆分。

## 1.6 后台任务与队列去向

| 旧机制 | 去向 |
|---|---|
| `MemoryExtractTaskQueue`（进程内与 Redis 两实现，延迟投递，重试 2 次） | JobRunr 延迟任务 `MemoryExtractionJob`，重试 2 次，`jobId` 用 `(tenantId, principal, sessionId)` 的确定性 UUID，重复投递被 JobRunr 去重。“在途槽位”仍由 `memory_subjects` 行串行化，投递失败时释放槽位的逻辑保留 |
| `memory.redis-enabled` 开关与 `MemoryTaskQueueWiring` | 删除 |
| 记忆每日整理（在蒸馏任务内 `consolidateIfDue`） | 保持在 `MemoryExtractionJob` 内触发，不另建定时任务 |
| `Gate` 订阅线程、`ApprovalCancellations` 监视线程、Redis Pub/Sub | 删除 |
| 审批超时 | 新增 JobRunr 周期任务 `McpApprovalExpiryJob`，每分钟把 `expires_at` 已过的 `PENDING` 置 `EXPIRED` 并调用 `ApprovalResolutionListener` |
| `OAuthStateStore` 的 GC 线程 | 删除，Redis TTL |
| `McpClientManager` 的空闲清理线程 | 删除，Caffeine `expireAfterAccess` 加 `removalListener` |
| `McpUsageInstructionsOps` 的并发摘要 | 保留虚拟线程执行器，局部使用 |
| `McpTooling` 后台线程 | 随工具类迁出给 agenttools |

## 1.7 跨域依赖

| 本域 | 依赖 | 方向与方式 |
|---|---|---|
| agent | knowledge | 业务域同级，调用 `KnowledgeQuestionApi`，无反向依赖 |
| agent | auth | 调用 `UserQueryApi` 批量取用户名做 creator 回填（计划 03，名称待核对） |
| agent | audit | 调用 `audit.api` 写审计（技能增改删） |
| agent | agent-engine | 实现 `SkillCatalog` spi，使用 `SkillFrontmatter` 组装与校验 |
| mcp | model、model-runtime | 生成使用说明时取聊天模型并调用 `LlmChatClient` |
| mcp | auth | 当前主体 |
| mcp | 框架 | `SsrfGuard` Bean、Redisson、JobRunr |
| memory | tenant | 读 `memory-config`，计划 03 |
| memory | model、model-runtime | 抽取模型、嵌入模型、`EmbeddingPort` |
| memory | session | 反向：session 实现 `MemoryTranscriptProvider`，调用 memory 四个 api |
| favorite | auth | 当前用户 |
| 被依赖 | channel（07）、session（08）、agenttools（08）、model（03） | 见交接表 |

## 1.8 旧测试处置

处置只有三种：移植业务断言、改写为新规范、丢弃。丢弃的理由写在行里。

| 域 | 旧测试 | 处置 |
|---|---|---|
| agent | `AgentSuggestedQuestionsTest`、`AgentPlaceholdersTest` | 移植 |
| agent | `AgentLookupAdapterTest` | 改写为 `AgentQueryServiceTest` |
| agent | `SkillCatalogServiceTest`（engine）、`SkillFrontmatterTest`、`DbSkillSourceTest`、`SkillReadFileToolTest`、`SkillPinnedInstructionsTest` | 第一个改写为 `SkillServiceTest` 进 app；其余留在引擎，归计划 02 |
| mcp | `ToolPolicyTest`、`McpUsageInputTest`、`McpConfigFingerprintTest`、`McpMetadataOAuthSignalTest` | 移植 |
| mcp | `GateTest` | 改写为 `McpApprovalServiceTest`，保留状态机断言：重复决定幂等、用户不匹配拒绝、租户不匹配拒绝、过期 |
| mcp | `AgentToolApprovalControllerTest`、`McpOAuthControllerTest`、`McpMetadataErrorMappingTest`、`McpServiceResponseTest`、`McpAuthTypeTest` | 改写为 MockMvc 与 VO 断言，去掉 Go 外壳字节形状 |
| mcp | `OAuthHandlerTest`、`OAuthLifecycleTest`、`OAuthManagerTest`、`OAuthPrincipalTest`、`OAuthStateStoreTest` | 改写到 Nimbus 版新类，保留生命周期断言：令牌过期可刷新、刷新失败要求重新授权、刷新租约互斥、状态一次性使用 |
| mcp | `McpAuthHeadersTest`、`McpClientManagerTest`、`McpToolListingTest` | 改写为 `McpAuthStrategyTest`、`McpConnectionPoolTest`、`McpToolGatewayTest`；`McpServerStub` 保留作 SDK 集成测桩 |
| mcp | `ApprovalWireFormatTest`、`GateCrossInstanceTest`、`SpringRedisPubSubTest`、`FakeRedisPubSub`、`ApprovalEventBusesTest`、`TestCancellation` | 丢弃：钉 Go 版线格式与 Redis 广播，机制已删除 |
| mcp | `McpClientProtocolTest`、`McpSseTransportTest`、`McpServiceCrudOpsBindingTest`、`FakeOAuthStateRedis` | 丢弃：自研协议由 SDK 自带测试覆盖；rawBody 绑定已删除；Fake 随新测试重建 |
| memory | `MemoryTextTest`、`MemoryVectorsTest`、`MemoryExtractionHelpersTest`、`MemoryLexicalTest`、`MemoryRecallSelectorTest`、`MemoryTopicResolverTest`、`MemoryVectorLogicTest` | 移植，类名随新类改 |
| memory | `MemoryExtractionServiceTest` | 改写为 `MemoryExtractionSchedulerTest` 与 `MemoryExtractionJobTest`，保留防抖、最小间隔、槽位释放、输出解析断言 |
| memory | `MemoryContractTest`、`MemoryEntityJsonTest`、`RedisMemoryExtractTaskQueueTest` | 丢弃：钉 Go 零值与 JSON 键序，队列已删除 |
| favorite | 无 | 新写 |

## 1.9 错误码

模块名称写 `ragagent`。对象不存在、参数校验、未登录一律用 `CommCode`，不重复定义。

**agent 013**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001013001` agent.builtin.undeletable | 删除内置智能体 | 内置智能体不可删除 | Built-in agents cannot be deleted |
| ragagent | `1001013002` agent.not.owner | 非创建者且非管理员修改或删除智能体 | 只有创建者或管理员可以操作该智能体 | Only the creator or an administrator can operate this agent |
| ragagent | `1001013003` agent.config.invalid | 智能体配置校验不通过 | 智能体配置不合法：{0} | Agent configuration is invalid: {0} |
| ragagent | `1001013004` skill.slug.duplicate | 新建技能时标识已存在 | 技能标识已存在：{0} | Skill slug already exists: {0} |
| ragagent | `1001013005` skill.name.conflict | 技能名称与平台内置或本空间技能重名 | 技能名称已被占用：{0} | Skill name is already taken: {0} |
| ragagent | `1001013006` skill.platform.readonly | 修改或删除平台内置技能 | 平台内置技能只读 | Platform built-in skills are read-only |
| ragagent | `1001013007` skill.rename.blocked | 技能被智能体引用时改名 | 技能正被 {0} 个智能体引用，不能改名 | The skill is referenced by {0} agents and cannot be renamed |
| ragagent | `1001013008` skill.delete.blocked | 技能被智能体引用时未带强制标志删除 | 技能正被 {0} 个智能体引用，确认后可强制删除 | The skill is referenced by {0} agents. Confirm to force deletion |
| ragagent | `1001013009` skill.content.invalid | 技能正文或头信息校验不通过 | 技能内容不合法：{0} | Skill content is invalid: {0} |
| ragagent | `1001013010` skill.file.notfound | 读取技能中不存在的文件 | 技能中没有该文件：{0} | The skill has no such file: {0} |

**mcp 014**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001014001` mcp.builtin.immutable | 修改、删除内置服务或其凭据 | 内置 MCP 服务不可修改 | Built-in MCP services cannot be changed |
| ragagent | `1001014002` mcp.transport.unsupported | 保存或连接 stdio 类型服务 | 不支持 stdio 传输 | The stdio transport is not supported |
| ragagent | `1001014003` mcp.oauth.notconfigured | 对未启用 OAuth 的服务发起授权 | 该服务未配置 OAuth | The service is not configured for OAuth |
| ragagent | `1001014004` mcp.oauth.required | 测试或同步时服务端要求 OAuth 授权 | 服务端要求 OAuth 授权，请改用 OAuth 2.0 | The server requires OAuth. Switch the service to OAuth 2.0 |
| ragagent | `1001014005` mcp.oauth.incomplete | 授权尚未完成就查询或使用 | 授权尚未完成 | Authorization has not been completed |
| ragagent | `1001014006` mcp.connection.changed | 刷新目录期间连接配置被改动 | 刷新期间连接配置已变化，请重试 | The connection changed during refresh. Try again |
| ragagent | `1001014007` mcp.catalog.invalid | 服务端返回的工具目录非法或过大 | MCP 目录不合法或过大 | The MCP directory is invalid or too large |
| ragagent | `1001014008` mcp.refresh.failed | 刷新工具目录失败 | 刷新工具失败，请检查连接后重试 | Failed to refresh tools. Check the connection and try again |
| ragagent | `1001014009` mcp.catalog.storage | 目录存储不可用 | MCP 目录存储暂不可用 | MCP directory storage is unavailable |
| ragagent | `1001014010` mcp.url.rejected | 服务地址未通过出站校验 | 服务地址不允许访问 | The service URL is not allowed |
| ragagent | `1001014011` mcp.approval.notpending | 对已决定、已过期或不存在的待审批再次决定 | 该审批已处理或已过期 | The approval was already handled or has expired |
| ragagent | `1001014012` mcp.approval.forbidden | 非发起人或跨租户决定审批 | 无权处理该审批 | You are not allowed to handle this approval |
| ragagent | `1001014013` mcp.model.unavailable | 生成使用说明时没有可用聊天模型 | 聊天模型不可用 | The chat model is unavailable |
| ragagent | `1001014014` mcp.tools.empty | 没有启用的工具可汇总 | 没有可用的启用工具 | No enabled tools are available |
| ragagent | `1001014015` mcp.client.failed | 建立 MCP 连接失败 | 连接 MCP 服务失败：{0} | Failed to connect to the MCP service: {0} |

**memory 015**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001015001` memory.disabled | 记忆在工作区或个人层被关闭时操作记忆 | 记忆功能已关闭 | Memory is disabled |
| ragagent | `1001015002` memory.conflict | 写入与已有记忆冲突 | 记忆内容与已有条目冲突 | The memory conflicts with an existing entry |
| ragagent | `1001015003` memory.capacity | 条目数达到工作区上限 | 记忆条目已达上限 | The memory item limit has been reached |

`1001015003` **待执行方核对**：`MemoryConfig.effectiveMaxItems` 到达上限时旧代码的行为（报错还是淘汰最旧），核对后若旧行为是淘汰则删除该码。

**favorite 016**：无自定义码。类型非法、资源 ID 为空用 `CommCode.PARAM_ERROR`。

## 1.10 `@IgnoreTenant` 白名单

| 位置 | 原因 |
|---|---|
| `McpApprovalExpiryJob.expireDue` | 周期任务跨租户扫描过期待审批，属于后台调度系统通道 |

`platform_skills` 不是 `@IgnoreTenant`，而是全局表，登记在 `gac.furnace.tenant.ignore-tables`。蒸馏任务与向量回填在任务参数里带租户，由计划 04 的 JobRunr 过滤器设置租户上下文，不使用 `@IgnoreTenant`。OAuth 回调从 Redis 状态里取租户并显式设置上下文，同样不使用。

## 1.11 权限点

权限点命名以计划 03 为准。本计划使用下列逻辑名，计划 03 没有对应项时按偏离登记。

| 逻辑名 | 旧角色要求 | 用于 |
|---|---|---|
| `agent:read`、`agent:write` | 读 Viewer 及以上，写创建者或 Admin | agents 全部接口，写操作再叠加归属校验 |
| `skill:read`、`skill:manage` | 选择器 Viewer，管理 Admin | skills |
| `mcp:manage` | Admin，Key 能力 `manage_mcp_services` | mcp-services 全部接口 |
| `mcp:approve` | 已登录用户，且为发起会话的属主 | mcp-approvals 的 resolution |
| `memory:use` | 已登录用户，Key 需 full-access | memory 全部接口 |
| `favorite:use` | Viewer 及以上 | favorites |

## 1.12 前端 API 层切换表

形状栏“是”表示适配层只改路径与解包。

**`frontend/src/api/agent/index.ts`**（只改智能体与推荐问题函数，`listIMChannels`、`createIMChannel` 等 IM 与微信二维码函数归计划 07）

| 函数 | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|
| `listAgents` | `GET /agents?creator=` | `GET /api/admin/v1/agents?creator=` | 是 | `tenantId` 字符串转 number |
| `getAgentById`、`createAgent`、`updateAgent`、`deleteAgent`、`copyAgent` | `/agents/{id}` 等 | 换前缀 | 是 | 内置智能体 `id` 保持 `builtin-*` |
| `getPlaceholders`、`getAgentTypePresets` | `/agents/placeholders`、`/agents/type-presets` | 换前缀 | 是 | |
| `getSuggestedQuestions` | `/agents/{id}/suggested-questions` | 换前缀 | 是 | `knowledgeIds` 逗号串保持 |

**`mcp-service.ts`**

| 函数 | 新路径 | 形状 | 适配点 |
|---|---|---|---|
| `listMCPServices`、`getMCPService`、`createMCPService`、`updateMCPService`、`deleteMCPService`、`testMCPService`、`getMCPServiceTools`、`getMCPServiceResources`、`getMCPMetadata`、`refreshMCPMetadata`、`generateMCPUsageInstructions` | 换前缀 | 是 | `getMCPMetadata` 从未同步时 `data` 为 `null`，保持 |
| `getMCPToolApprovals` | `GET /mcp-services/{id}/tool-policies` | 字段改名 | 映射回 `MCPToolApprovalRow` |
| `setMCPToolApproval`、`setMCPToolEnabled` | `PUT /mcp-services/{id}/tools/{toolName}/policy` | 是 | |
| `putMCPCredentials`、`deleteMCPCredentialField` | 换前缀 | 是 | |
| `getMCPOAuthAuthorizeURL`、`getMCPOAuthStatus`、`getMCPOAuthAuthorizationStatus`、`revokeMCPOAuthToken` | 换前缀 | 是 | `MCP_OAUTH_CALLBACK_PATH` 改新回调路径 |
| `resolveToolApproval`、`resolveMCPOAuth`、`cancelMCPOAuth` | `POST /mcp-approvals/{id}/resolution` | 请求体改 | `decision` 在函数内映射为 `APPROVE`、`REJECT`、`AUTHORIZE`、`CANCEL` |

**`memory.ts`**：函数全部是换前缀，形状一致。`MemoryItem.tenantId` 字符串转 number。`exportMemoryItems` 返回 `items: []`，类型保持 `| null`。`getTenantMemoryConfig`、`updateTenantMemoryConfig` 改指向计划 03 的租户配置接口，路径以计划 03 为准。

**`skills.ts`**：函数全部换前缀，形状一致。`deleteSkill(id, force)` 的 `?force=true` 保持。

**`user-favorites.ts`**：函数全部换前缀。`FavoriteEntry.tenantId` 字符串转 number，`userId` 保持字符串。

## 1.13 界面端到端域标签

计划 01 的 `tests/e2e/PAGES.md` 给每个页面标域。本计划负责转绿的行：

| 域标签 | 页面与流程 |
|---|---|
| `@agent` | 智能体列表 `/platform/agents`、编辑弹窗（含模型选择、知识库选择、技能选择区、MCP 选择区）、复制、删除、推荐问题展示 |
| `@skill` | 设置里的技能管理面板：新建、编辑、引用提示、强制删除 |
| `@mcp` | 设置里的 MCP 服务：新增、编辑、测试连接、工具列表、元数据面板、凭据卡片、OAuth 授权状态、工具审批开关 |
| `@mcp-approval` | 对话里的 `ToolApprovalCard` 与 `McpOAuthCard`，依赖计划 08 的问答主链路，本计划只做到接口级验证，界面用例留待计划 08 转绿 |
| `@memory` | 设置里的个人记忆（条目、待确认、话题、文档、导出、整理）与工作区记忆配置 |
| `@favorite` | 知识库列表与智能体列表的收藏星标 |

## Review Focus

下列五类输入与场景，spec 没有逐条写测试，但最容易出错。每条都在对应任务里有断言。

1. 审批记录在服务重启后仍可被决定，同一审批被并发决定或重复决定时只成功一次，批准后的调用只能被领取一次。归属 Task 26。
2. 两个线程同时遇到过期的 MCP 令牌，只有一个去刷新，另一个等待结果。刷新失败要求重新授权，不会把过期令牌当有效令牌发出。归属 Task 24。
3. 蒸馏任务在没有请求上下文的线程里运行，任务参数缺租户时立即失败，不查全库。归属 Task 16。
4. 智能体 ID 的边界输入：`builtin-` 前缀但不在 7 个之内、空串、非数字、超过 2^53 的雪花 ID。期望参数错误或正确往返，不得 500。归属 Task 5。
5. OAuth 回调带过期、重放或伪造的 state。期望返回可读的失败页面与错误码，不泄露内部异常，也不把令牌写给错误的主体。归属 Task 27。

## 路径缩写

`M` = `backend/app/src/main/java/com/ragagent`，`T` = `backend/app/src/test/java/com/ragagent`，`R` = `backend/app/src/main/resources`，`DB` = `R/db/migration/allenv`。脚本文件名里的 `<D>` 是写脚本当天的 `yyyyMMdd`，序号取 `ls DB | grep <D>` 的下一个。

每个任务的步骤固定五步：写失败测试、跑红、实现、跑绿、提交。跑红与跑绿都用 `MVN test -Dtest=<该任务测试类>`，跑红预期 FAIL，原因是类不存在或断言不成立，跑绿预期 PASS。提交前再跑一次 `MVN checkstyle:check`，预期无违规。提交后在 `docs/migration/progress.md` 追加一行。

# 2. 任务

## 批次 A：favorite

### Task 1: favorite 域后端

**Files:**
- Create: `DB/V<D>_NN_DDL_rebuild_favorite_create_user_resource_favorites_jiangguifan.sql`
- Create: `M/favorite/{entity/UserResourceFavorite,entity/FavoriteResourceType,mapper/FavoriteMapper,repository/FavoriteRepository,service/FavoriteService,controller/FavoriteController,vo/FavoriteVo,vo/AddFavoriteReqVo,assembler/FavoriteAssembler}.java`
- Create: `R/mapper/favorite/FavoriteMapper.xml`
- Create: `T/favorite/{FavoriteRowLevelSecurityTest,FavoriteServiceTest,FavoriteControllerTest}.java`

**Interfaces:**
- Consumes: `auth.api` 的 `CurrentPrincipal`，framework 的 `PostgresContainerTest`、`RestResponse`
- Produces: `FavoriteService`：`List<UserResourceFavorite> list(long userId, FavoriteResourceType type)`、`void add(long userId, FavoriteResourceType type, String resourceId)`、`void remove(long userId, FavoriteResourceType type, String resourceId)`。`FavoriteResourceType { KB, AGENT }`，JSON 值小写 `kb`、`agent`

- [x] **Step 1: 写失败测试**
  - `FavoriteRowLevelSecurityTest.tenant2CannotReadTenant1Rows`、`tenant2CannotInsertRowForTenant1`。
  - `FavoriteServiceTest.addTwiceIsIdempotent`、`removeMissingRowSucceeds`、`listReturnsOnlyCallersRowsOfThatType`。
  - `FavoriteControllerTest.invalidTypeReturns4400`、`blankResourceIdReturns4400`、`anonymousReturns4402`、`otherUsersFavoritesAreInvisible`，路径 `/api/admin/v1/user/favorites`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='Favorite*Test'`。
- [x] **Step 3: 实现**：唯一索引 `(tenant_id, user_id, resource_type, resource_id, deleted_at)`；`add` 用唯一索引冲突当作已存在，不先查后写；`FavoriteRepository extends ServiceImpl<FavoriteMapper, UserResourceFavorite>`；类型与 ID 校验在 `FavoriteController` 的 VO 校验注解里，Service 信任入参。
- [x] **Step 4: 跑绿**：同命令。
- [x] **Step 5: 提交**：`git add DB/...favorite... M/favorite R/mapper/favorite T/favorite`，`git commit -m "feat(favorite): 收藏域迁入新架构"`。

### Task 2: favorite 前端与界面验收

**Files:**
- Modify: `frontend/src/api/user-favorites.ts`
- Modify: `tests/e2e/PAGES.md`（标记 `@favorite` 行已转绿）

- [x] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep @favorite`，在新栈上运行，当前预期失败。新栈启动方式见计划 01 的 `backend/README.md`，前端 dev 代理指向 8080。
- [x] **Step 2: 跑红**：同上，预期 FAIL，原因是前端仍指向旧路径。
- [x] **Step 3: 实现**：按 1.12 的表改 `user-favorites.ts`，三个函数保持签名，传输用 `createApiClient`，`FavoriteEntry.tenantId` 转 number。
- [x] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit`，预期无错误；再跑 Step 1 的命令，预期全部通过；最后 `MVN verify`，预期全绿。
- [x] **Step 5: 提交**：`git add frontend/src/api/user-favorites.ts tests/e2e/PAGES.md`，`git commit -m "feat(favorite): 前端 API 层切换"`。

## 批次 B：agent 与技能

### Task 3: agent 盘点核对、DDL 与行级安全

**Files:**
- Create: `DB/V<D>_NN_DDL_rebuild_agent_create_agents_skills_jiangguifan.sql`
- Create: `DB/V<D>_NN_DML_rebuild_agent_seed_platform_skills_jiangguifan.sql`
- Modify: `R/application.yml`（`gac.furnace.tenant.ignore-tables` 追加 `platform_skills`）
- Create: `T/agent/{AgentRowLevelSecurityTest,PlatformSkillsAreGlobalTest,BuiltinAgentUniquenessTest}.java`

**Interfaces:**
- Consumes: 计划 01 Task 5 的 RLS 策略模板
- Produces: 表 `agents`、`skills`、`platform_skills`，列定义见 1.4

- [x] **Step 1: 写失败测试**
  - 先读 `docs/migration/frozen-bugs.md` 中涉及 agentmanagement 与 skills 的条目，逐条写进 `docs/migration/progress.md` 的本任务行并决定是否影响本计划；再用 `find domains/src/main/java/com/ragagent/agentmanagement -name '*.java' | wc -l` 核对文件数为 27，有差异先改 1.1。
  - `AgentRowLevelSecurityTest.tenant2CannotReadTenant1Rows_agents`、`..._skills`，`tenant2CannotInsertRowForTenant1_agents`、`..._skills`。
  - `PlatformSkillsAreGlobalTest.readableUnderAnyTenantContext`、`applicationRoleCannotInsert`（应用角色只有 SELECT）。
  - `BuiltinAgentUniquenessTest.secondBuiltinRowInSameTenantRejected`、`recreateAfterSoftDeleteAccepted`、`sameCodeInOtherTenantAccepted`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='AgentRowLevelSecurityTest,PlatformSkillsAreGlobalTest,BuiltinAgentUniquenessTest'`。
- [x] **Step 3: 实现**：两个 DDL 脚本按 1.4 写，`agents` 带部分唯一索引与行级安全，`skills` 带行级安全，`platform_skills` 不开。DML 脚本的种子数据来源见 1.4 末段，来源未定时脚本先建空文件并在 `deviations.md` 登记。
- [x] **Step 4: 跑绿**。反向验证：在测试容器里对 `agents` 执行 `ALTER TABLE agents DISABLE ROW LEVEL SECURITY`，`AgentRowLevelSecurityTest` 预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add DB/...agent... R/application.yml T/agent`，`git commit -m "feat(agent): 智能体与技能表及行级安全"`。

### Task 4: agent Entity、Mapper 与 Repository

**Files:**
- Create: `M/agent/entity/{Agent,Skill,PlatformSkill}.java`
- Create: `M/agent/mapper/{AgentMapper,SkillMapper,PlatformSkillMapper}.java`，`R/mapper/agent/{AgentMapper,SkillMapper}.xml`
- Create: `M/agent/repository/{AgentRepository,SkillRepository,PlatformSkillRepository}.java`
- Create: `T/agent/{AgentRepositoryTest,SkillRepositoryTest,AgentReferenceQueryTest}.java`

**Interfaces:**
- Produces: `AgentRepository.listByCreatedDesc()`、`Optional<Agent> findBuiltin(String builtinCode)`、`List<Agent> listReferencingSkill(String name, String slug)`、`List<Agent> listReferencingModel(long modelId)`。`SkillRepository.findVisibleByName(String name)`。`PlatformSkillRepository.listAll()`

- [x] **Step 1: 写失败测试**
  - `AgentRepositoryTest.configJsonbRoundTrips`、`softDeleteHidesRow`、`listIsOrderedByCreatedAtDesc`、`updateByIdKeepsUntouchedColumns`。
  - `SkillRepositoryTest.liveDuplicateSlugRejected`、`recreateAfterSoftDeleteAccepted`、`updateBumpsVersion`。
  - `AgentReferenceQueryTest.finds_selectedSkills_by_name`、`finds_all_mode_agents`、`finds_model_in_each_of_six_config_keys`（`modelId`、`rerankModelId`、`vlmModelId`、`asrModelId`、`queryUnderstandModelId`、`questionSuggestions.followUps.modelId` 各一例）、`ignores_soft_deleted_agents`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='AgentRepositoryTest,SkillRepositoryTest,AgentReferenceQueryTest'`。
- [x] **Step 3: 实现**：实体开 `autoResultMap`，`config` 用 `JacksonTypeHandler`；jsonb 引用查询写 Mapper XML，使用 `config -> 'selectedSkills' ? #{name}` 一类 jsonb 操作符；`skillsSelectionMode = 'all'` 的内置与自建智能体都算引用，不再有 `OR is_builtin` 兜底。主码不出现 `@Select` 等注解 SQL。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agent/entity M/agent/mapper M/agent/repository R/mapper/agent T/agent`，`git commit -m "feat(agent): 智能体与技能的持久层"`。

### Task 5: agent 的 api 包、错误码与文案

**Files:**
- Create: `M/agent/api/{AgentIds,BuiltinAgentCode,AgentCode,AgentQueryApi,AgentDeletedEvent}.java`，`M/agent/bo/{AgentBo,AgentConfigBo,SuggestedQuestionBo}.java`
- Modify: `R/i18n/app-messages{,_zh_CN,_zh_HK,_en_US,_es_ES,_pt_BR,_ru_RU,_th_TH}.properties`（追加 1.9 的 10 个 agent 错误码与 `agent.copy.suffix`）
- Create: `T/agent/{AgentIdsTest,AgentCodeMessagesTest}.java`

**Interfaces:**
- Produces: `AgentIds.decode(String id)` 返回 `long`、`AgentIds.encode(long id)` 返回 `String`；`BuiltinAgentCode`：`QUICK_ANSWER(1,"builtin-quick-answer")`、`SMART_REASONING(2,"builtin-smart-reasoning")`、`WIKI_RESEARCHER(3,"builtin-wiki-researcher")`、`DEEP_RESEARCHER(4,"builtin-deep-researcher")`、`DATA_ANALYST(5,"builtin-data-analyst")`、`KNOWLEDGE_GRAPH_EXPERT(6,"builtin-knowledge-graph-expert")`、`DOCUMENT_ASSISTANT(7,"builtin-document-assistant")`，另有旧代码认得但不展示的 `builtin-wiki-fixer` 取 8。`AgentQueryApi` 与 `AgentDeletedEvent` 签名见决定三

- [x] **Step 1: 写失败测试**
  - `AgentIdsTest.decodesEachBuiltinCodeAndEncodesBack`（8 个往返）、`decodesSnowflakeString`、`roundTripsIdAbove2Pow53`、`rejectsUnknownBuiltinPrefix`、`rejectsEmptyAndNonNumeric`，后两项断言抛 `BusinessRuntimeException` 且错误码为 `CommCode.PARAM_ERROR`。
  - `AgentCodeMessagesTest.everyCodeHasMessageInSevenLanguages`：遍历 `AgentCode.values()`，七种语言各取一次，断言不等于 key 本身；`copySuffixExistsInSevenLanguages`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='AgentIdsTest,AgentCodeMessagesTest'`。
- [x] **Step 3: 实现**：`AgentCode` 10 个枚举值对应 1.9，`httpStatus` 用默认；非中英文的五种语言由机器翻译出初稿并标注待人工校对；`CodeCache.register(AgentCode.values())` 放在 `com.ragagent.config.AgentAssemblyConfig`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agent/api M/agent/bo M/config/AgentAssemblyConfig.java R/i18n T/agent`，`git commit -m "feat(agent): api 包、错误码与七语种文案"`。

### Task 6: agent 核心用例

**Files:**
- Create: `M/agent/service/{AgentService,AgentListAssembler,BuiltinAgentTemplates,AgentConfigNormalizer,AgentPromptPlaceholders,AgentTypePresetCatalog,AgentQueryService,AgentModelReferenceProvider}.java`
- Create: `R/agent/builtin_agents.yaml`、`R/agent/agent_type_presets.yaml`（从 `domains/src/main/resources/agent/management/` 原样复制）
- Create: `T/agent/{AgentPlaceholdersTest,AgentServiceTest,AgentListAssemblerTest,AgentQueryServiceTest,AgentModelReferenceProviderTest}.java`

**Interfaces:**
- Consumes: Task 4 的 Repository，计划 03 的 `UserQueryApi`、`ModelReferenceProvider`，`CurrentPrincipal`
- Produces: `AgentService`：`AgentBo create(CreateAgentBo in)`、`AgentBo update(long id, UpdateAgentBo in)`、`void delete(long id)`、`AgentBo copy(long id, Locale locale)`、`void requireOwnedOrAdmin(long id)`。`AgentListAssembler.list(CreatorFilter filter, Locale locale)`。`AgentQueryService implements AgentQueryApi`。`AgentModelReferenceProvider implements ModelReferenceProvider`

- [ ] **Step 1: 写失败测试**
  - `AgentPlaceholdersTest`：从旧 `AgentPlaceholdersTest` 移植全部断言。
  - `AgentServiceTest`：`createRequiresName`、`createDefaultsAgentModeToQuickAnswer`、`createAsOpenCallerLeavesCreatedByEmpty`、`updateBuiltinWritesConfigOnlyAndCreatesRowOnFirstUpdate`、`updateBuiltinIgnoresNameDescriptionAvatar`、`deleteBuiltinRejectedWithBuiltinUndeletableCode`、`deletePublishesAgentDeletedEventAfterCommit`、`copyAppendsLocalizedSuffix`（zh-CN 为 ` (副本)`，en-US 为 ` (Copy)`）、`nonCreatorNonAdminCannotUpdate`。
  - `AgentListAssemblerTest`：`builtinsComeFirstInFixedOrder`、`dbRowOverridesTemplateButNameIsLocalized`、`creatorMineKeepsBuiltinsAndOwnRows`、`creatorNamesAreBackfilled`、`userLookupFailureSkipsBackfillSilently`。
  - `AgentQueryServiceTest`：从旧 `AgentLookupAdapterTest` 改写，加 `isOwnedTrueForBuiltinWithoutRow`、`hasRowFalseForBuiltinWithoutRow`、`findReturnsSyntheticViewForBuiltinWithoutRow`、`normalizeConfigReturnsNullForNonObject`。
  - `AgentModelReferenceProviderTest.reportsAgentsForEachConfigKey`。
- [ ] **Step 2: 跑红**：`MVN test -Dtest='AgentPlaceholdersTest,AgentServiceTest,AgentListAssemblerTest,AgentQueryServiceTest,AgentModelReferenceProviderTest'`。
- [ ] **Step 3: 实现**：内置智能体模板的 YAML 装载、未知键过滤、提示词引用解析、语言选择沿用旧 `BuiltinAgentRegistry` 的规则，只改写法；语言取值改用请求 `Locale`，不再读 `RAGAGENT_LANGUAGE` 环境变量；`AgentService` 的每个用例拆成“校验、构造实体、保存、提交后动作”四个私有方法；事件用 `ApplicationEventPublisher` 配 `@TransactionalEventListener(AFTER_COMMIT)` 由 Spring Modulith 事件登记表保证投递。
- [ ] **Step 4: 跑绿**。
- [ ] **Step 5: 提交**：`git add M/agent/service R/agent T/agent`，`git commit -m "feat(agent): 智能体核心用例与内置模板"`。

### Task 7: 技能库用例与引擎目录实现

**Files:**
- Create: `M/agent/service/skill/{SkillService,SkillReferenceChecker,VisibleSkillCatalog}.java`
- Create: `T/agent/skill/{SkillServiceTest,SkillReferenceCheckerTest,VisibleSkillCatalogTest}.java`

**Interfaces:**
- Consumes: 计划 02 的 `agentengine.spi.SkillCatalog`、`SkillRecord` 与 `SkillFrontmatter` 的 api 封装，Task 4 的 Repository，`audit.api`
- Produces: `SkillService`：`SkillBo create(CreateSkillBo in)`、`SkillBo update(long id, UpdateSkillBo in)`、`void delete(long id, boolean force)`、`List<SkillBo> listCatalog()`、`SkillDetailBo detail(long id)`。`SkillReferenceChecker.references(String slug, String name)`。`VisibleSkillCatalog implements SkillCatalog`

- [x] **Step 1: 写失败测试**
  - `SkillServiceTest`：从旧 `SkillCatalogServiceTest` 改写。`createAssemblesFrontmatterAndValidates`、`duplicateSlugRejected`、`nameTakenByPlatformSkillRejected`、`updateCannotChangeSlug`、`renameWhileReferencedRejectedWithReferenceList`、`updatePlatformSkillRejectedAsReadOnly`、`deleteReferencedWithoutForceRejected`、`deleteReferencedWithForceSucceedsAndAudits`、`softDeleteKeepsRowForAudit`。
  - `SkillReferenceCheckerTest`：`counts_selected_and_all_mode_agents`、`ignores_other_tenants`。
  - `VisibleSkillCatalogTest.mergesTenantAndPlatformSkills`、`tenantSkillWithSameSlugAsPlatformSkillBothVisible`、`emptyTenantStillListsPlatformSkills`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='SkillServiceTest,SkillReferenceCheckerTest,VisibleSkillCatalogTest'`。
- [x] **Step 3: 实现**：frontmatter 的组装、校验与正文还原调用计划 02 提供的引擎 api，不在 app 里重写；`create`、`update`、`delete` 各写一条审计；5 个旧异常换成 `AgentCode` 加 `BusinessRuntimeException`，`SkillCode` 对应 1.9 的 `skill.*` 键。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agent/service/skill T/agent/skill`，`git commit -m "feat(agent): 技能库用例与引擎技能目录实现"`。

### Task 8: 推荐问题

**Files:**
- Create: `M/agent/service/suggestion/{SuggestedQuestionService,SuggestedQuestionMixer,TagScopeResolver}.java`
- Create: `T/agent/suggestion/{SuggestedQuestionServiceTest,SuggestedQuestionMixerTest,TagScopeResolverTest}.java`

**Interfaces:**
- Consumes: 计划 04 的 `KnowledgeQuestionApi`。动手前先在 `backend/app/src/main/java/com/ragagent/knowledge/api` 里确认该接口存在，缺失就停下，在 `deviations.md` 登记并通知用户，不要自己在 knowledge 域里建
- Produces: `SuggestedQuestionService.suggest(long agentId, SuggestionQuery query)` 返回 `List<SuggestedQuestionBo>`

- [x] **Step 1: 写失败测试**
  - 从旧 `AgentSuggestedQuestionsTest` 移植全部断言，再加：`curatedModeUsesConfigItemsOnly`、`knowledgeModeUsesFaqAndGeneratedQuestions`、`hybridModeMixesBothUpToLimit`、`roundRobinSpreadsAcrossKnowledgeBases`、`unknownKbIdsAreFilteredOut`、`tagScopesNarrowKnowledgeIds`、`invalidTagScopesJsonRejected`（`CommCode.PARAM_ERROR`）。
- [x] **Step 2: 跑红**：`MVN test -Dtest='SuggestedQuestionServiceTest,SuggestedQuestionMixerTest,TagScopeResolverTest'`。
- [x] **Step 3: 实现**：旧代码的 wiki 兜底本来就未实现，不补；轮询与混合合并的算法沿用旧 `roundRobin`、`mergeHybrid`，放进 `SuggestedQuestionMixer`；不再直接查任何知识库表。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agent/service/suggestion T/agent/suggestion`，`git commit -m "feat(agent): 推荐问题改走知识库 api"`。

### Task 9: agent 与技能的控制器、VO

**Files:**
- Create: `M/agent/controller/{AgentController,SkillController}.java`
- Create: `M/agent/vo/{AgentVo,AgentConfigVo,CreateAgentReqVo,UpdateAgentReqVo,SkillSummaryVo,SkillDetailVo,SkillReferenceVo,CreateSkillReqVo,UpdateSkillReqVo,SkillFileVo}.java`
- Create: `M/agent/assembler/{AgentAssembler,SkillAssembler}.java`
- Create: `T/agent/{AgentControllerTest,SkillControllerTest}.java`

**Interfaces:**
- Produces: `AgentController` 映射 `{"/api/admin/v1/agents", "/api/open/v1/agents"}`；`SkillController` 映射 `/api/admin/v1/skills`。接口清单见 1.3

- [x] **Step 1: 写失败测试**
  - `AgentControllerTest`：`listReturnsBuiltinsFirstWithBuiltinCodeIds`、`getAcceptsBuiltinCodeAndSnowflake`、`getUnknownBuiltinPrefixReturns4400`、`createWithBlankNameReturns4400`、`deleteBuiltinReturnsUndeletableCode`、`openPrefixRequiresOpenAccountWithManageAgentsCapability`、`viewerCannotWrite`、`suggestedQuestionsLimitIsHonored`。
  - `SkillControllerTest`：`selectorListShowsNameAndDescriptionOnly`、`catalogListIncludesReferencedByAndReadOnlyFlag`、`updateReferencedRenameReturnsReferenceListInDetails`、`deleteWithForceQueryParam`、`filesContentReturnsFullSkillMd`、`openPrefixIsNotMapped`（`/api/open/v1/skills` 返回 404）。
- [x] **Step 2: 跑红**：`MVN test -Dtest='AgentControllerTest,SkillControllerTest'`。
- [x] **Step 3: 实现**：控制器只做绑定、`@Valid`、调用一个 Service；VO 用 `record`；`AgentAssembler` 用 MapStruct，内置智能体 `id` 经 `AgentIds.encode` 转成 `builtin-*`；springdoc 注解 `@Tag`、`@Operation` 齐全。
- [x] **Step 4: 跑绿**，再跑 `MVN test -Dtest=OpenApiExportTest` 导出文档，预期通过，然后 `cd frontend/packages/api-client && npm run gen:api`，预期 `schema.d.ts` 出现 agents 与 skills 的路径。
- [x] **Step 5: 提交**：`git add M/agent/controller M/agent/vo M/agent/assembler T/agent frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(agent): 智能体与技能的接口层"`。

### Task 10: agent 前端与界面验收

**Files:**
- Modify: `frontend/src/api/agent/index.ts`（只改智能体、占位符、预设、推荐问题函数）
- Modify: `frontend/src/api/skills.ts`
- Modify: `tests/e2e/PAGES.md`

- [x] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep '@agent|@skill'`，预期当前失败。
- [x] **Step 2: 跑红**：同上。
- [x] **Step 3: 实现**：按 1.12 的表改两个文件，函数名与返回形状不变；IM 与微信二维码函数留给计划 07，不动。
- [x] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit && npx vitest run src/views/agent src/views/settings/panels/skillManagementForm.test.ts`，预期无错误；再跑 Step 1 命令，预期全部通过。最后跑 `MVN verify`，预期全绿，其中 Modulith 与 ArchUnit 与 Checkstyle 通过。
- [x] **Step 5: 提交**：`git add frontend/src/api/agent/index.ts frontend/src/api/skills.ts tests/e2e/PAGES.md`，`git commit -m "feat(agent): 前端 API 层切换与界面验收"`。

## 批次 C：memory

### Task 11: memory 盘点核对、DDL 与行级安全

**Files:**
- Create: `DB/V<D>_NN_DDL_rebuild_memory_create_memory_tables_jiangguifan.sql`
- Create: `T/memory/{MemoryRowLevelSecurityTest,MemoryConstraintsTest}.java`

**Interfaces:**
- Consumes: 计划 01 的自建数据库镜像，含 pgvector
- Produces: 7 张表，列与约束见 1.4

- [x] **Step 1: 写失败测试**
  - 先读 `frozen-bugs.md` 中涉及 memory 的条目，写入 `progress.md`；核对 `find domains/src/main/java/com/ragagent/memory -name '*.java' | wc -l` 为 91。
  - `MemoryRowLevelSecurityTest`：对 7 张表各一对 `tenant2CannotReadTenant1Rows_<表名>`、`tenant2CannotInsertRowForTenant1_<表名>`。
  - `MemoryConstraintsTest`：`onePrincipalHasOneSubjectRow`、`topicStatKeyIsUniquePerPrincipal`、`docAffinityKeyIsUniquePerPrincipal`、`halfvecColumnAcceptsVectorsAndOrdersByCosineDistance`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='MemoryRowLevelSecurityTest,MemoryConstraintsTest'`。
- [x] **Step 3: 实现**：按 1.4 写 DDL，每张租户表开行级安全，`memory_item_embeddings.embedding` 用 `halfvec`，向量索引沿用旧 V1 里对该列的索引定义，维度处理沿用旧列。
- [x] **Step 4: 跑绿**；反向验证：对 `memory_items` 关闭行级安全，`MemoryRowLevelSecurityTest` 预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add DB/...memory... T/memory`，`git commit -m "feat(memory): 记忆表与行级安全"`。

### Task 12: memory 持久层

**Files:**
- Create: `M/memory/entity/{MemoryItem,MemoryItemEmbedding,MemoryTombstone,MemorySubject,MemoryExtractionSession,MemoryTopicStat,MemoryDocAffinity}.java`
- Create: `M/memory/mapper/*Mapper.java`（7 个，对应实体），`R/mapper/memory/*.xml`
- Create: `M/memory/repository/{MemoryItemRepository,MemorySubjectRepository,MemoryTopicRepository,MemoryDocAffinityRepository}.java`
- Create: `T/memory/{MemoryItemRepositoryTest,MemorySubjectRepositoryTest,MemoryTopicRepositoryTest,MemoryDocAffinityRepositoryTest}.java`

**Interfaces:**
- Produces: `MemoryItemRepository`：`MemoryItem save(MemoryItem)`、`PageData<MemoryItem> pageByStatus(Principal, MemoryStatus, int limit, int offset)`、`Optional<MemoryItem> findActiveByKey(Principal, String normalizedKey)`、`void reject(MemoryItem, MemoryTombstone)`、`List<MemoryVectorHit> vectorSearch(Principal, long modelId, float[] query, int limit)`、`void upsertEmbedding(MemoryItemEmbedding)`。`MemorySubjectRepository`：`MemorySubject ensureSubject(Principal)`、`MemorySubject lockSubject(Principal)`、`EnqueueResult enqueuePendingSession(Principal, long sessionId, Duration lease)`、`void releaseExtractionSlot(Principal, long sessionId)`。其余两个 Repository 用 `upsertHit`、`listPage`、`deleteById` 一类业务动词

[x] **Step 1: 写失败测试**
  - `MemoryItemRepositoryTest`：`saveAndPageByStatus`、`vectorSearchOrdersByCosineAndStaysWithinPrincipal`、`rejectWritesTombstoneAndMarksItemInOneTransaction`、`updateByIdKeepsNullableColumns`。
  - `MemorySubjectRepositoryTest`：`ensureSubjectIsIdempotentUnderEightConcurrentCallers`（最终一行）、`enqueueFirstSendsSecondWithinLeaseDoesNot`、`releaseSlotAllowsNextSend`、`expiredLeaseAllowsNextSend`。
  - `MemoryTopicRepositoryTest.upsertHitAccumulatesAndKeepsAliases`、`MemoryDocAffinityRepositoryTest.upsertHitIncrementsHits`。
[x] **Step 2: 跑红**：`MVN test -Dtest='Memory*RepositoryTest'`。
[x] **Step 3: 实现**：`ON CONFLICT` 写入、`FOR UPDATE` 行锁、`halfvec` 检索写 Mapper XML；`lockSubject` 由 Service 在事务内调用，Repository 不开事务；jsonb 列用 Jackson 处理器；写法遵守 5.11：默认 `ServiceImpl` 的 `save`、`updateById`。
[x] **Step 4: 跑绿**。
[x] **Step 5: 提交**：`git add M/memory/entity M/memory/mapper M/memory/repository R/mapper/memory T/memory`，`git commit -m "feat(memory): 记忆持久层，四个聚合根"`。

### Task 13: memory 的 api 包、错误码与文案

**Files:**
- Create: `M/memory/api/{MemoryKind,MemoryStatus,MemoryOrigin,MemoryConfig,MemoryCode,MemoryRecallApi,MemoryExtractionApi,MemoryDocumentApi,MemoryTopicApi,MemoryTranscriptProvider}.java`，`M/memory/bo/*.java`
- Modify: `R/i18n/app-messages*.properties`（3 个 memory 错误码）
- Create: `T/memory/{MemoryKindTest,MemoryConfigTest,MemoryCodeMessagesTest}.java`

**Interfaces:**
- Produces: `MemoryKind`：`PROFILE`、`PREFERENCE`、`FACT`、`TASK`、`INTEREST`，字段 `resident`、`renderOrder`，常驻种类为 `PROFILE`、`PREFERENCE`、`INTEREST`，`FACT` 与 `TASK` 只在提问匹配时召回。`MemoryConfig` record 保留旧 `contract.memory.MemoryConfig` 的全部字段与默认值，含 `effectiveMaxItems()`、`extractDelay()`、`extractMinInterval()`、`autoExtractEnabled()`。四个 api 与 `MemoryTranscriptProvider` 签名见“对外承诺”和决定三

- [x] **Step 1: 写失败测试**
  - `MemoryKindTest.residentKindsAreProfilePreferenceInterest`、`renderOrderMatchesLegacyAll`（`profile, preference, fact, task, interest`）、`wireValuesAreLowercase`。
  - `MemoryConfigTest`：从旧 `MemoryContractTest` 移植默认值与边界断言，丢弃 JSON 形状断言；加 `blankExtractModelFallsBackToChatModel`（若旧语义如此，**待执行方核对** `MemoryConfig` 289 行）。
  - `MemoryCodeMessagesTest.everyCodeHasMessageInSevenLanguages`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='MemoryKindTest,MemoryConfigTest,MemoryCodeMessagesTest'`。
- [x] **Step 3: 实现**：`MemoryKeys` 的键常量按用途并入 `MemoryConfig` 与 `MemoryTextNormalizer`，不另留 `*Keys` 类；注册 `MemoryCode` 到 `CodeCache`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/memory/api M/memory/bo R/i18n T/memory`，`git commit -m "feat(memory): api 包、枚举、配置与错误码"`。

### Task 14: memory 设置、条目、话题、文档用例

**Files:**
- Create: `M/memory/service/{MemoryScopeResolver,MemoryItemService,MemorySettingsService,MemoryTopicService,MemoryDocumentService,MemoryTextNormalizer,MemoryTopicNormalizer}.java`
- Create: `T/memory/{MemoryTextNormalizerTest,MemoryTopicNormalizerTest,MemoryItemServiceTest,MemorySettingsServiceTest,MemoryTopicServiceTest,MemoryDocumentServiceTest}.java`

**Interfaces:**
- Consumes: Task 12 的 Repository，计划 03 的 `CurrentPrincipal` 与租户配置读取
- Produces: `MemoryScope(long tenantId, Principal principal)` 与 `MemoryScopeResolver.current()`；`MemoryItemService`：`list`、`create`、`update`、`delete`、`confirm`、`reject`、`clear`（返回删除条数）、`export`；`MemoryTopicService implements MemoryTopicApi`；`MemoryDocumentService implements MemoryDocumentApi`；`MemorySettingsService.get()`、`setEnabled(boolean)`

- [x] **Step 1: 写失败测试**
  - `MemoryTextNormalizerTest`、`MemoryTopicNormalizerTest`：分别从旧 `MemoryTextTest`、`MemoryTopicResolverTest` 全量移植。
  - `MemoryItemServiceTest`：`manualCreateIsActiveWithManualOrigin`、`confirmMovesPendingToActive`、`rejectWritesTombstoneAndSameFingerprintIsNotReproposedWithinWindow`、`pendingItemsAreNeverReturnedByRecallQuery`、`unsupportedStatusRejectedAsParamError`、`disabledMemoryRejectedWithDisabledCode`、`clearReturnsRemovedCount`、`exportOfEmptyMemoryReturnsEmptyList`、`noPrincipalReturnsNoLoginCode`。
  - `MemorySettingsServiceTest`：`effectiveIsWorkspaceAndPersonal`、`workspaceOffOverridesPersonalOn`、`itemCountAndMaxItemsReported`。
  - `MemoryTopicServiceTest`：`observeBelowThresholdOnlyCounts`、`reachingThresholdCreatesPendingInterest`、`deleteRemembersRefusalAndSkipsAutoPromotion`、`promoteCreatesActiveInterest`。
  - `MemoryDocumentServiceTest`：`recordAnswerSourcesIncrementsHits`、`affinityOfReturnsOnlyAskedIds`、`deleteStopsUsingDocument`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='Memory*ServiceTest,MemoryTextNormalizerTest,MemoryTopicNormalizerTest'`。
- [x] **Step 3: 实现**：事务在 Service 公开方法上，行锁由 `lockSubject` 在事务内取；写入容量上限的处理按 Task 13 核对的旧行为；所有 Service 不再出现 `new XxxOps(this)`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/memory/service T/memory`，`git commit -m "feat(memory): 设置、条目、话题与文档用例"`。

### Task 15: memory 召回与向量

**Files:**
- Create: `M/memory/service/{MemoryRecallService,LexicalScorer,ResidentBlockRenderer,MemoryEmbeddingService,MemoryModelResolver}.java`
- Create: `T/memory/{LexicalScorerTest,ResidentBlockRendererTest,MemoryRecallServiceTest,MemoryEmbeddingServiceTest}.java`

**Interfaces:**
- Consumes: Task 12、14，计划 02 的 `EmbeddingPort`
- Produces: `MemoryRecallService implements MemoryRecallApi`；`MemoryEmbeddingService`：`void storeItemEmbedding(MemoryScope, MemoryConfig, MemoryItem)`、`VectorSearchOutcome vectorSearch(MemoryScope, MemoryConfig, String query, int limit)`、`int backfillEmbeddings(MemoryScope, MemoryConfig)`

- [x] **Step 1: 写失败测试**
  - `LexicalScorerTest`、`MemoryRecallServiceTest`、`MemoryEmbeddingServiceTest`：分别从旧 `MemoryLexicalTest`、`MemoryRecallSelectorTest`、`MemoryVectorLogicTest` 与 `MemoryVectorsTest` 全量移植。
  - 新增：`ResidentBlockRendererTest.blockStaysWithin900Runes`；`MemoryRecallServiceTest.recallStaysWithin600RunesAndFiveItems`、`searchStaysWithin20ItemsAnd2000Runes`、`fusionUsesRrfWithK60`、`vectorHitsOutsideLexicalPoolAreReportedAsSuch`、`recallIsEmptyWhenMemoryDisabled`、`embeddingTimeoutDegradesToLexicalOnly`（嵌入调用超过 2 秒，召回仍返回字面结果并记录 `vectorSkipReason`）。
- [x] **Step 2: 跑红**：`MVN test -Dtest='LexicalScorerTest,ResidentBlockRendererTest,MemoryRecallServiceTest,MemoryEmbeddingServiceTest'`。
- [x] **Step 3: 实现**：字面池大小取 `MemoryConfig.effectiveMaxItems()`；两路排序顺序调用后 RRF 融合，没有策略接口，见 1.2；嵌入超时常量 2 秒、写入超时 10 秒、回填每次 200 条、同步每次 2000 条沿用旧值，收进 `MemoryLimits` 常量类。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/memory/service T/memory`，`git commit -m "feat(memory): 召回与向量检索"`。

### Task 16: memory 蒸馏调度与任务

**Files:**
- Create: `M/memory/service/{MemoryExtractionScheduler,MemoryExtractionJob,TranscriptSegmenter,MemoryWriter,MemoryExtractionClient,MemoryExtractionPrompt}.java`
- Create: `T/memory/{MemoryExtractionSchedulerTest,MemoryExtractionJobTest,TranscriptSegmenterTest,MemoryWriterTest,MemoryExtractionClientTest}.java`

**Interfaces:**
- Consumes: 计划 04 的 `JobScheduler` 与测试支持，`MemoryTranscriptProvider`（测试里用桩实现），计划 02 的 `LlmChatClient`
- Produces: `MemoryExtractionScheduler implements MemoryExtractionApi`；`MemoryExtractionJob.run(MemoryExtractionJobParams params)`，`MemoryExtractionJobParams(long tenantId, Principal principal, long sessionId, long messageId, long chatModelId)`

- [x] **Step 1: 写失败测试**
  - `MemoryExtractionSchedulerTest`：`disabledMemoryDoesNotSchedule`、`autoExtractOffDoesNotSchedule`、`firstCallSchedulesJobWithConfiguredDelay`、`secondCallWithinLeaseSchedulesNothing`、`recentRunPushesDelayToMinInterval`、`scheduleFailureReleasesInFlightSlotAndRethrows`。
  - `MemoryExtractionJobTest`：**`missingTenantParamFailsImmediatelyWithoutQuerying`**（任务参数 `tenantId` 为 0 或空，断言抛 `TenantContextMissingException` 且没有任何 SQL 被执行）、`runWritesExtractedItemsAndAdvancesCursor`、`failureIncrementsFailureCountAndKeepsCursor`、`runRetriesTwiceThenGivesUp`、`runTriggersConsolidationWhenDue`。
  - `TranscriptSegmenterTest`：从旧 `MemoryExtractionHelpersTest` 全量移植。
  - `MemoryWriterTest`：`contradictoryStatementSupersedesOldItem`、`inferredItemIsPendingAndNotInjected`、`tombstonedFingerprintIsSkipped`、`explicitUserRequestIsActive`。
  - `MemoryExtractionClientTest`：`invalidJsonRaisesInvalidExtractionOutput`、`tokenBudgetRetriesFrom1200To4000`、`systemPromptAndSchemaMatchLegacyConstants`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='MemoryExtraction*Test,TranscriptSegmenterTest,MemoryWriterTest'`。
- [x] **Step 3: 实现**：调度用确定性 `jobId`，防抖与最小间隔语义沿用旧 `scheduleExtraction`；`MemoryExtractionJob` 先校验参数里的租户再进入租户上下文，上下文由计划 04 的 JobRunr 过滤器设置，缺失即失败；提示词与 JSON schema 原文沿用旧 `EXTRACTION_SYSTEM_PROMPT`、`EXTRACTION_SCHEMA`，放进 `MemoryExtractionPrompt`；抽取跑在虚拟线程，由 JobRunr 管理。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/memory/service T/memory`，`git commit -m "feat(memory): 蒸馏调度与 JobRunr 任务"`。

### Task 17: memory 整理

**Files:**
- Create: `M/memory/service/{MemoryConsolidationService,MemoryClusterer}.java`
- Create: `T/memory/{MemoryClustererTest,MemoryConsolidationServiceTest}.java`

**Interfaces:**
- Produces: `MemoryConsolidationService.consolidateNow()` 返回 `MemoryConsolidationResult`；`consolidateIfDue(MemoryScope, MemoryConfig, long modelId)`，供 Task 16 的任务调用

- [x] **Step 1: 写失败测试**
  - `MemoryClustererTest`：`lexicalOverlapThresholdIs055Normal030Forced`、`cosineThresholdIs086Normal075Forced`、`maxClustersIs3Normal8Forced`。
  - `MemoryConsolidationServiceTest`：`skipsWithTooFewItemsBelowSix`、`skipsWithNoCandidates`、`skipsWhenModelUnavailable`、`skipsWhenModelDeclines`、`skipsTooSoonWithin24hUnlessForcedAfterOneMinute`、`mergesNearDuplicatesIntoOneItem`、`demotesTasksOlderThan45Days`、`resultCountsMergedDemotedExpiredReviewedCandidates`、`consolidateNowBackfillsEmbeddings`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='MemoryClustererTest,MemoryConsolidationServiceTest'`。
- [x] **Step 3: 实现**：常量 24 小时、1 分钟、6 条、3 与 8 簇、0.55、0.3、0.86、0.75、45 天沿用旧值，收进 `MemoryLimits`；聚类算法放 `MemoryClusterer`，流程放 Service。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/memory/service T/memory`，`git commit -m "feat(memory): 记忆整理"`。

### Task 18: memory 控制器

**Files:**
- Create: `M/memory/controller/{MemoryItemController,MemoryInsightController}.java`
- Create: `M/memory/vo/{MemoryItemVo,MemoryPageVo,MemorySettingsVo,MemoryTopicVo,MemoryDocVo,MemoryExportVo,MemoryConsolidationVo,CreateMemoryItemReqVo,UpdateMemoryItemReqVo,UpdateMemorySettingsReqVo}.java`，`M/memory/assembler/MemoryAssembler.java`
- Create: `T/memory/{MemoryItemControllerTest,MemoryInsightControllerTest}.java`

**Interfaces:**
- Produces: 两个控制器映射 `{"/api/admin/v1/memory", "/api/open/v1/memory"}`，接口清单见 1.3。分页形状 `{items, page, pageSize, total}`

- [x] **Step 1: 写失败测试**
  - `MemoryItemControllerTest`：`listReturnsPageShapeWithStringIds`、`createReturnsItemWithManualOrigin`、`confirmThenListShowsActive`、`rejectReturnsOkAndHidesItem`、`clearDeletesAllAndKeepsOthersPrincipalsRows`、`anonymousReturns4402`。
  - `MemoryInsightControllerTest`：`topicsPageShape`、`documentsPageShape`、`exportOfEmptyReturnsEmptyArray`、`consolidateReturnsCountsAndSkippedReason`、`openPrefixRequiresFullAccessKey`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='MemoryItemControllerTest,MemoryInsightControllerTest'`。
- [x] **Step 3: 实现**：控制器只绑定、校验、调一个 Service；VO 用 `record`；`MemoryAssembler` 用 MapStruct；`ZeroTimeSerializer` 一类零值时间工具不带入，可空时间输出 `null`。
- [x] **Step 4: 跑绿**；再 `MVN test -Dtest=OpenApiExportTest` 并 `npm run gen:api`，预期 memory 路径出现在 `schema.d.ts`。
- [x] **Step 5: 提交**：`git add M/memory/controller M/memory/vo M/memory/assembler T/memory frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(memory): 记忆接口层"`。

### Task 19: memory 前端与界面验收

**Files:**
- Modify: `frontend/src/api/memory.ts`
- Modify: `tests/e2e/PAGES.md`

- [x] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep @memory`，预期当前失败。
- [x] **Step 2: 跑红**：同上。
- [x] **Step 3: 实现**：按 1.12 改 `memory.ts`；`MemoryItem.tenantId` 转 number；`getTenantMemoryConfig`、`updateTenantMemoryConfig` 指向计划 03 的租户配置路径。
- [x] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit`，预期无错误；Step 1 命令全部通过；`MVN verify` 全绿。
- [x] **Step 5: 提交**：`git add frontend/src/api/memory.ts tests/e2e/PAGES.md`，`git commit -m "feat(memory): 前端 API 层切换与界面验收"`。

## 批次 D：mcp

### Task 20: mcp 盘点核对、DDL 与行级安全

**Files:**
- Create: `DB/V<D>_NN_DDL_rebuild_mcp_create_mcp_tables_jiangguifan.sql`
- Create: `T/mcp/{McpRowLevelSecurityTest,McpConstraintsTest}.java`

**Interfaces:**
- Produces: `mcp_services`、`mcp_tool_policies`、`mcp_metadata`、`mcp_oauth_clients`、`mcp_oauth_tokens`、`mcp_tool_approvals`，列与约束见 1.4

- [x] **Step 1: 写失败测试**
  - 先读 `frozen-bugs.md` 中涉及 mcp 的条目；核对 `find domains/src/main/java/com/ragagent/mcp -name '*.java' | wc -l` 为 171。
  - `McpRowLevelSecurityTest`：6 张表各一对读与插入的跨租户被拒测试。
  - `McpConstraintsTest`：`toolPolicyIsUniquePerServiceAndTool`、`oauthTokenIsUniquePerPrincipalAndService`、`oauthClientIsUniquePerService`、`snapshotIsUniquePerPrincipalAndService`、`approvalStatusRejectsUnknownValue`、`approvalKindRejectsUnknownValue`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='McpRowLevelSecurityTest,McpConstraintsTest'`。
- [x] **Step 3: 实现**：按 1.4 写 DDL，`status` 与 `kind` 加 CHECK 约束，`mcp_tool_approvals` 建 `(status, expires_at)` 与 `(tenant_id, session_id)` 索引。
- [x] **Step 4: 跑绿**；反向验证：对 `mcp_oauth_tokens` 关闭行级安全，跨租户测试预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add DB/...mcp... T/mcp`，`git commit -m "feat(mcp): MCP 表与行级安全"`。

### Task 21: mcp 持久层

**Files:**
- Create: `M/mcp/entity/{McpService,McpToolPolicy,McpCatalogSnapshot,McpOAuthClient,McpOAuthToken,McpToolApproval}.java`
- Create: `M/mcp/mapper/*Mapper.java`（6 个），`R/mapper/mcp/*.xml`
- Create: `M/mcp/repository/{McpServiceRepository,McpToolPolicyRepository,McpCatalogSnapshotRepository,McpOAuthClientRepository,McpOAuthTokenRepository,McpToolApprovalRepository}.java`
- Create: `T/mcp/*RepositoryTest.java`（6 个）

**Interfaces:**
- Produces: `McpToolPolicyRepository.upsertPolicy(long serviceId, String toolName, Boolean requireApproval, Boolean enabled)`（单条 `ON CONFLICT DO UPDATE`，只改传入的非空字段）。`McpOAuthTokenRepository.claimRefreshLease(long tokenId, String leaseId, Duration ttl)` 返回 `boolean`。`McpToolApprovalRepository`：`boolean decide(long id, McpApprovalStatus to, ...)` 仅当当前为 `PENDING` 才成功，`Optional<McpToolApproval> claimApproved(long id)` 把 `APPROVED` 原子置 `EXECUTING`，`List<McpToolApproval> listExpiredPending(OffsetDateTime now)`，后者带 `@IgnoreTenant`

- [x] **Step 1: 写失败测试**
  - `McpServiceRepositoryTest.credentialColumnsAreEncryptedAtRest`（原生 SQL 读出的值不等于明文）、`jsonbConfigsRoundTrip`、`softDeletedRowsAreHidden`。
  - `McpToolPolicyRepositoryTest.upsertWithOnlyEnabledKeepsRequireApproval`、`concurrentPatchesBothSurvive`（两线程分别改两个字段，最终两个字段都生效，不再需要旧代码的重试循环）。
  - `McpOAuthTokenRepositoryTest.tokensAreEncryptedAtRest`、`refreshLeaseIsExclusive`（两个并发申请者只有一个成功）、`expiredLeaseCanBeReclaimed`。
  - `McpToolApprovalRepositoryTest.decideSucceedsOnceThenFails`、`claimApprovedSucceedsOnce`、`rejectedAndExpiredCannotBeClaimed`、`listExpiredPendingSpansTenants`。
  - `McpCatalogSnapshotRepositoryTest.upsertKeepsOneRowPerPrincipalAndService`、`McpOAuthClientRepositoryTest.secretIsEncryptedAtRest`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='Mcp*RepositoryTest'`。
- [x] **Step 3: 实现**：加密列用 `@Encrypted`，jsonb 列用 Jackson 处理器；`ON CONFLICT` 与条件 `UPDATE` 写 Mapper XML；`McpToolApprovalRepository.listExpiredPending` 的 `@IgnoreTenant` 在代码里写一行中文原因，并登记到 `progress.md` 白名单。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/mcp/entity M/mcp/mapper M/mcp/repository R/mapper/mcp T/mcp`，`git commit -m "feat(mcp): MCP 持久层"`。

### Task 22: mcp 的 api 包、枚举、错误码与文案

**Files:**
- Create: `M/mcp/api/{McpCode,McpAuthType,McpTransportType,McpApprovalKind,McpApprovalStatus,McpToolGatewayApi,McpToolPolicyApi,McpApprovalApi,McpOAuthApi,ApprovalResolutionListener}.java`，`M/mcp/bo/*.java`
- Modify: `R/i18n/app-messages*.properties`（15 个 mcp 错误码）
- Create: `T/mcp/{McpAuthTypeTest,McpTransportTypeTest,McpCodeMessagesTest}.java`

**Interfaces:**
- Produces: `McpAuthType`：`NONE("")`、`API_KEY("api_key")`、`BEARER("bearer")`、`OAUTH("oauth")`，`fromValue` 宽松读（未知值得 `null`，空串得 `NONE`），`parseStrict` 写侧用，未知值抛 `CommCode.PARAM_ERROR`。`McpTransportType`：`SSE("sse")`、`HTTP_STREAMABLE("http-streamable")`、`STDIO("stdio")`，`isSupported()` 对 `STDIO` 为假。四个 api 签名见“对外承诺”

- [x] **Step 1: 写失败测试**
  - `McpAuthTypeTest`：从旧测试改写。`emptyStringIsNone`、`lenientReadOfUnknownIsNull`、`strictParseOfApiKeyCamelCaseIsRejectedWithHint`（前端曾发 `apiKey` 导致策略被静默写成 null，写侧必须报错）。
  - `McpTransportTypeTest.stdioIsNotSupported`、`sseAndStreamableAreSupported`。
  - `McpCodeMessagesTest.everyCodeHasMessageInSevenLanguages`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='McpAuthTypeTest,McpTransportTypeTest,McpCodeMessagesTest'`。
- [x] **Step 3: 实现**：`McpCode` 15 个枚举对应 1.9；`CodeCache.register(McpCode.values())` 放 `com.ragagent.config.McpAssemblyConfig`；`ApprovalResolutionListener.onResolved(ApprovalResolvedBo)` 的 BO 含 `approvalId`、`kind`、`decision`、`modifiedArgs`、`sessionId`、`assistantMessageId`、`toolCallId`、`serviceId`、`toolName`、`timedOut`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/mcp/api M/mcp/bo M/config/McpAssemblyConfig.java R/i18n T/mcp`，`git commit -m "feat(mcp): api 包、枚举与错误码"`。

### Task 23: mcp 协议适配（官方 SDK）

**Files:**
- Create: `M/mcp/adapter/{McpClientFactory,McpConnectionPool,McpCancellationNotifier,McpAuthStrategy,NoAuthStrategy,ApiKeyAuthStrategy,BearerAuthStrategy,OAuthAuthStrategy}.java`
- Modify: `backend/app/pom.xml`（加 `io.modelcontextprotocol.sdk:mcp-core`，版本取计划 01 Task 16 文档里记录的实际版本）
- Create: `T/mcp/adapter/{McpAuthStrategyTest,McpClientFactoryTest,McpConnectionPoolTest,McpCancellationNotifierTest}.java`，`T/mcp/adapter/McpServerStub.java`（沿用旧 `McpServerStub`）

**Interfaces:**
- Consumes: `docs/optimization/spikes/2026-11-11-mcp-sdk-verification.md` 的结论；Spring Bean 形式的 `SsrfGuard`
- Produces: `McpAuthStrategy.apply(McpRequestHeaders headers, McpAuthContext context)`，`McpAuthContext(McpService service, Principal principal)`。`McpClientFactory.create(McpService service, Principal principal)` 返回 SDK 的同步客户端。`McpConnectionPool.get(long serviceId, Principal principal, String configVersion)`，键为这三者。`McpCancellationNotifier.cancel(McpSyncClient client, Object requestId)`

**开工前检查。** 读 Task 16 的结论文档。结论是“采用”则按下面步骤做。结论是“退回自研”则改做末尾的备选任务，不要先写 SDK 代码。

- [x] **Step 1: 写失败测试**
  - `McpAuthStrategyTest`：从旧 `McpAuthHeadersTest` 改写。`noneAddsOnlyCustomHeaders`、`apiKeyUsesDefaultHeaderNameXApiKey`、`apiKeyUsesConfiguredHeaderName`、`bearerSendsAuthorizationBearer`、`oauthUsesCallersOwnToken`、`oauthWithoutTokenSignalsAuthorizationRequired`、`registryContainsAllFourTypes`（`Map<McpAuthType, McpAuthStrategy>` 含 4 项）。
  - `McpClientFactoryTest`（对 `McpServerStub`）：`sseTransportListsTools`、`streamableHttpTransportListsTools`、`stdioIsRejectedWithTransportCode`、`loopbackTargetRejectedBySsrfGuard`、**`everyRedirectHopIsValidated`**（三跳重定向，第二跳指向内网，断言被拒）、`authorizationHeaderIsNotForwardedAcrossHosts`。
  - `McpConnectionPoolTest`：`sameKeyReusesConnection`、`configVersionChangeBuildsNewAndClosesOld`、`idleConnectionIsClosedAfterExpiry`、`differentPrincipalsGetSeparateConnections`。
  - `McpCancellationNotifierTest.cancelSendsNotificationsCancelledWithRequestId`（桩服务端收到 `notifications/cancelled`）。
- [x] **Step 2: 跑红**：`MVN test -Dtest='McpAuthStrategyTest,McpClientFactoryTest,McpConnectionPoolTest,McpCancellationNotifierTest'`。
- [x] **Step 3: 实现**：`McpClientFactory` 用一个 `switch` 选 SDK 的 SSE 或 Streamable HTTP 传输，HTTP 客户端换成经 `SsrfGuard` 校验每一跳的实现；鉴权用注入的 `Map<McpAuthType, McpAuthStrategy>`；`McpConnectionPool` 用 Caffeine，`expireAfterAccess` 沿用旧空闲时间，`removalListener` 里关闭连接；SDK 内部依赖 Reactor，对外只用阻塞 API。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/mcp/adapter backend/app/pom.xml T/mcp/adapter`，`git commit -m "feat(mcp): 基于官方 SDK 的协议适配"`。

**备选：Task 23'（仅当 Task 16 结论为退回自研）。** 保留旧 `protocol` 包的传输与客户端，改名后按路线图 3.11 拆分，并补齐 spec 5.12 点名的缺口：1. 用户停止时发 `notifications/cancelled`；2. 处理 `notifications/tools/list_changed` 并使目录快照过期；3. 处理进度通知；4. 解析结构化输出；5. Streamable HTTP 断线续传。每项一个测试加一个实现，仍用上面的 `McpAuthStrategy` 登记表与 `McpConnectionPool`。开工前在 `deviations.md` 登记并通知用户，同时把 spec D26 改为“自研并补齐上述 5 项”。

### Task 24: mcp OAuth（Nimbus）

**Files:**
- Create: `M/mcp/adapter/oauth/{OAuthDiscoveryClient,OAuthTokenExchanger,OAuthRefreshCoordinator,OAuthStateCacheRepository,GuardedHttpSender}.java`
- Modify: `backend/app/pom.xml`（加 `com.nimbusds:oauth2-oidc-sdk`，版本取计划 01 Task 17 文档）
- Create: `T/mcp/adapter/oauth/{OAuthDiscoveryClientTest,OAuthTokenExchangerTest,OAuthRefreshCoordinatorTest,OAuthStateCacheRepositoryTest}.java`

**Interfaces:**
- Consumes: Task 21 的 `McpOAuthTokenRepository`、`McpOAuthClientRepository`；`docs/optimization/spikes/2026-11-11-nimbus-rfc9728.md`
- Produces: `OAuthDiscoveryClient.discover(String resourceUrl, String wwwAuthenticateHeader)` 返回 `OAuthServerInfo`；`OAuthTokenExchanger.exchange(...)`、`refresh(...)`；`OAuthRefreshCoordinator.validTokenFor(McpService, Principal)` 返回令牌或抛 `OAuthReauthorizationRequiredException`；`OAuthStateCacheRepository.save(OAuthState)`、`consume(String state)` 返回 `Optional<OAuthState>`

- [x] **Step 1: 写失败测试**
  - `OAuthDiscoveryClientTest`：`parsesResourceMetadataFromWwwAuthenticate`、`fallsBackToAuthorizationServerMetadata`、`registersClientDynamically`、`everyFetchPassesSsrfGuard`；RFC 9728 缺口的补齐按 Task 17 结论，用本地桩服务验证。
  - `OAuthTokenExchangerTest`：`authorizationCodeExchangeSendsPkceVerifier`、`refreshExchangesRefreshToken`。
  - `OAuthRefreshCoordinatorTest`：从旧 `OAuthLifecycleTest`、`OAuthManagerTest` 改写，保留断言：`unexpiredTokenIsReturnedWithoutNetwork`、`expiredTokenWithRefreshIsRefreshed`、**`twoConcurrentCallersCauseExactlyOneRefreshRequest`**（桩令牌端点计数为 1，另一个线程拿到同一结果）、**`refreshRejectedMarksReauthRequiredAndNeverReturnsExpiredToken`**、`temporaryRefreshFailureKeepsTokenAndThrowsRetryable`、`leaseHolderCrashLetsAnotherCallerRefreshAfterLeaseExpiry`。
  - `OAuthStateCacheRepositoryTest`：`stateIsSingleUse`、`stateExpiresAfterTtl`、`replayReturnsEmpty`，用 Redis Testcontainers。
- [x] **Step 2: 跑红**：`MVN test -Dtest='OAuth*Test'`。
- [x] **Step 3: 实现**：协议步骤全部用 Nimbus；`GuardedHttpSender` 把 `SsrfGuard` 接到 Nimbus 的 HTTP 层；刷新并发用 `refresh_lease_*` 列做跨实例互斥，进程内再加一把按 `(serviceId, principal)` 的锁避免本机重复请求；状态用 Redisson `RBucket`，键 `mcp:oauth:state:<state>`，值含租户、主体、服务 ID、PKCE 校验码、回调地址。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/mcp/adapter/oauth backend/app/pom.xml T/mcp/adapter/oauth`，`git commit -m "feat(mcp): 基于 Nimbus 的 OAuth 客户端"`。

### Task 25: mcp 服务用例与工具网关

**Files:**
- Create: `M/mcp/service/{McpServiceService,McpCredentialService,McpServiceProbe,McpToolPolicyService,McpCatalogSnapshotService,McpUsageInstructionWriter,McpToolGatewayService,McpOAuthFlowService}.java`，`M/mcp/service/McpConfigFingerprint.java`
- Create: `T/mcp/service/*Test.java`（与上述类一一对应，加 `McpConfigFingerprintTest`）

**Interfaces:**
- Consumes: Task 21、23、24，计划 03 的 `model.api`，计划 02 的 `LlmChatClient`
- Produces: `McpToolGatewayService implements McpToolGatewayApi`；`McpToolPolicyService implements McpToolPolicyApi`；`McpOAuthFlowService implements McpOAuthApi`；`McpServiceService`：`list`、`get`、`create`、`update`、`delete`；`McpCredentialService`：`put`、`clear`；`McpServiceProbe`：`test`、`listTools`、`listResources`；`McpCatalogSnapshotService`：`read`、`refresh`；`McpUsageInstructionWriter.generate(long serviceId, String language)`

- [x] **Step 1: 写失败测试**
  - `McpServiceServiceTest`：`createRejectsUrlBlockedBySsrfGuard`、`createRejectsStdio`、`builtinCannotBeUpdatedOrDeleted`、`updateChangesFingerprintAndClosesPooledConnections`、`deleteIsSoftAndClosesConnections`。
  - `McpCredentialServiceTest`：`putThenClearTriggersReconnect`、`unknownFieldRejectedAsParamError`、`builtinCredentialsImmutable`、`responseCarriesConfiguredFlagsOnly`。
  - `McpServiceProbeTest`：`oauthRequiredServerYieldsOauthRequiredCode`、`successfulTestListsToolsAndResources`。
  - `McpToolPolicyServiceTest`：从旧 `ToolPolicyTest` 移植；加 **`policyReadFailureRequiresApproval`**（查询失败时 `needsApproval` 为真，没有 fail-open 开关）、`isEnabledThrowsOnQueryFailure`、`enabledToolsBatchMatchesSingleCalls`。
  - `McpCatalogSnapshotServiceTest`：从旧 `McpMetadataOAuthSignalTest` 移植；`refreshStoresSnapshotPerPrincipalAndFingerprint`、`connectionChangedDuringRefreshReturnsConflictCode`、`oversizedCatalogRejected`、`readNeverOpensNetworkConnection`。
  - `McpUsageInstructionWriterTest`：从旧 `McpUsageInputTest` 移植；`noEnabledToolsReturnsToolsEmptyCode`、`missingChatModelReturnsModelUnavailableCode`。
  - `McpToolGatewayServiceTest`：`listEnabledServicesHonorsSelectedAndAll`、`loadCatalogUsesSnapshotWithoutNetwork`、`callToolPropagatesCancellationToNotifier`、`callToolAppliesAuthStrategyOfService`。
  - `McpConfigFingerprintTest`：从旧测试移植。
- [x] **Step 2: 跑红**：`MVN test -Dtest='Mcp*ServiceTest,McpServiceProbeTest,McpToolPolicyServiceTest,McpCatalogSnapshotServiceTest,McpUsageInstructionWriterTest,McpToolGatewayServiceTest,McpConfigFingerprintTest'`。
- [x] **Step 3: 实现**：Service 方法每个对应一个用例，拆成校验、构造、保存、提交后动作；探测与同步失败按错误码映射，不再在消息里拼 `rawMessage(e)`；`McpConfigFingerprint` 用 record 加静态工厂；使用说明生成的并发摘要用局部虚拟线程执行器。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/mcp/service T/mcp/service`，`git commit -m "feat(mcp): 服务用例、目录快照与工具网关"`。

### Task 26: mcp 审批持久化与事件驱动

**Files:**
- Create: `M/mcp/service/{McpApprovalService,McpApprovalExpiryJob}.java`
- Create: `T/mcp/service/{McpApprovalServiceTest,ApprovalSurvivesRestartTest,ConcurrentResolveTest,ClaimApprovedCallTest,McpApprovalExpiryJobTest}.java`

**Interfaces:**
- Consumes: Task 21 的 `McpToolApprovalRepository`，Task 22 的 `ApprovalResolutionListener`，计划 04 的 `JobScheduler`
- Produces: `McpApprovalService implements McpApprovalApi`；`McpApprovalExpiryJob.expireDue()`，注册为每分钟的 JobRunr 周期任务

- [x] **Step 1: 写失败测试**
  - `McpApprovalServiceTest`：从旧 `GateTest` 改写。`openToolApprovalPersistsPendingRowWithExpiry`（默认 10 分钟）、`sameUserCanResolve`、`otherUserForbidden`、`otherTenantForbidden`、`emptyCallerWhileRequesterHasUserIsForbidden`（fail-closed）、`modifiedArgsMustBeJsonObject`、`oauthKindAuthorizeRequiresTokenPresent`、`cancelMarksCancelled`、`resolveCallsListenerAfterCommit`。
  - `ApprovalSurvivesRestartTest.pendingApprovalCanBeResolvedByFreshServiceInstance`（新建 `McpApprovalService` 实例，不共享内存）。
  - `ConcurrentResolveTest.exactlyOneOfTwoConcurrentResolvesSucceeds`（另一个得到 `mcp.approval.notpending`，监听器调用次数为 1）。
  - `ClaimApprovedCallTest.approvedCallCanBeClaimedOnce`、`rejectedAndExpiredAreNotClaimable`、`claimedCallCannotBeResolvedAgain`。
  - `McpApprovalExpiryJobTest.expiresDueRowsAcrossTenantsAndNotifiesListenerWithTimedOut`、`futureRowsUntouched`、`jobTouchesOnlyPendingRows`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='McpApprovalServiceTest,ApprovalSurvivesRestartTest,ConcurrentResolveTest,ClaimApprovedCallTest,McpApprovalExpiryJobTest'`。
- [x] **Step 3: 实现**：`resolve` 在事务里调 `decide`，事务提交后调用 `ObjectProvider<ApprovalResolutionListener>`，无实现时只记日志，计划 08 之前这是常态；监听器调用失败不回滚决定，由监听器自身重试；`McpApprovalExpiryJob.expireDue` 带 `@IgnoreTenant` 并逐条按各自租户上下文通知监听器。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/mcp/service T/mcp/service`，`git commit -m "feat(mcp): 审批持久化与事件驱动"`。

### Task 27: mcp 控制器

**Files:**
- Create: `M/mcp/controller/{McpServiceController,McpServiceToolController,McpCredentialController,McpOAuthController,McpApprovalController}.java`
- Create: `M/mcp/vo/*.java`（服务、凭据、元数据、策略、OAuth、审批的请求与响应 VO，均为 `record`），`M/mcp/assembler/McpAssembler.java`
- Modify: `M/config/McpAssemblyConfig.java`（Sa-Token 放行 `/api/admin/v1/mcp-oauth/callback`）
- Create: `T/mcp/controller/{McpServiceControllerTest,McpServiceToolControllerTest,McpCredentialControllerTest,McpOAuthControllerTest,McpApprovalControllerTest,OAuthCallbackTest}.java`

**Interfaces:**
- Produces: `McpServiceController`、`McpServiceToolController`、`McpCredentialController`、`McpOAuthController` 的前三个授权接口映射 `{"/api/admin/v1/mcp-services", "/api/open/v1/mcp-services"}`；回调接口与 `McpApprovalController` 只挂 admin。路径清单见 1.3

- [x] **Step 1: 写失败测试**
  - `McpServiceControllerTest`：`createReturnsVoWithoutSecrets`（响应里没有 `apiKey`、`token`，有 `credentials.{apiKey,token}.configured`）、`authTypeWireValuesAreEmptyApiKeyBearerOauth`、`stdioCreateReturnsTransportCode`、`openPrefixNeedsManageMcpCapability`、`viewerCannotWrite`。
  - `McpServiceToolControllerTest`：`toolPolicyEndpointsRoundTrip`、`metadataBeforeFirstSyncReturnsNullData`、`refreshOfOauthServiceWithoutTokenReturnsOauthRequiredCode`、`usageInstructionsEndpointReturnsText`。
  - `McpCredentialControllerTest`：`putAndDeleteReturnConfiguredFlags`、`unknownFieldReturns4400`。
  - `McpOAuthControllerTest`：从旧测试改写。`authorizeUrlReturnsUrlAndAttempt`、`statusReportsAuthorizedRefreshableReauthPending`、`revokeRemovesToken`。
  - `McpApprovalControllerTest`：`approveRejectAuthorizeCancelAreAccepted`、`otherUserGetsForbiddenCode`、`secondDecisionReturnsNotPendingCode`、`openPrefixIsNotMapped`。
  - **`OAuthCallbackTest`**：`unknownStateShowsFailurePageWithCode`、`expiredStateShowsFailurePage`、`replayedStateFailsSecondTime`、`successStoresTokenForStatePrincipalOnly`、`failurePageContainsNoStackTraceOrInternalMessage`、`callbackIsReachableWithoutToken`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='Mcp*ControllerTest,OAuthCallbackTest'`。
- [x] **Step 3: 实现**：回调从 Redis 状态里取租户并显式设置租户上下文再写令牌；控制器只做绑定、`@Valid`、调一个 Service；`McpServiceResponse` 的 185 行手写序列化不带入，用 MapStruct 装配；springdoc 注解齐全。
- [x] **Step 4: 跑绿**；再 `MVN test -Dtest=OpenApiExportTest` 并 `npm run gen:api`。
- [x] **Step 5: 提交**：`git add M/mcp/controller M/mcp/vo M/mcp/assembler M/config/McpAssemblyConfig.java T/mcp/controller frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(mcp): MCP 接口层"`。

### Task 28: mcp 前端与界面验收

**Files:**
- Modify: `frontend/src/api/mcp-service.ts`
- Modify: `tests/e2e/PAGES.md`

- [x] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep '@mcp' --grep-invert @mcp-approval`，预期当前失败。
- [x] **Step 2: 跑红**：同上。
- [x] **Step 3: 实现**：按 1.12 改 `mcp-service.ts`；`resolveToolApproval`、`resolveMCPOAuth`、`cancelMCPOAuth` 保持签名，内部映射到 `POST /mcp-approvals/{id}/resolution`；`MCP_OAUTH_CALLBACK_PATH` 改新路径。
- [x] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit && npx vitest run src/views/settings/McpSettings.test.ts src/views/settings/components`，预期无错误；Step 1 命令全部通过。
- [x] **Step 5: 提交**：`git add frontend/src/api/mcp-service.ts tests/e2e/PAGES.md`，`git commit -m "feat(mcp): 前端 API 层切换与界面验收"`。

### Task 29: 迁出交接与闸门

**Files:**
- Create: `docs/migration/handoff-mcp-to-agenttools.md`
- Create: `T/mcp/McpApprovalFlowSmokeTest.java`
- Modify: `docs/migration/progress.md`

- [x] **Step 1: 写失败测试**：`McpApprovalFlowSmokeTest.openResolveClaimExecuteFlowWorksEndToEndAtApiLevel`：用 `McpApprovalApi` 打开工具审批、经 `McpApprovalController` 决定、监听桩收到一次回调、`claimApprovedCall` 领取一次、再次领取为空。
- [x] **Step 2: 跑红**：`MVN test -Dtest=McpApprovalFlowSmokeTest`，若 Task 26 已完成，此测试一开始可能直接通过，此时把它当回归测试保留，跳过红灯。
- [x] **Step 3: 实现**：写交接文档，包含“对外承诺”里的迁出表、四个 mcp api 与 `ApprovalResolutionListener` 的签名与语义、审批流的状态机图（文字版即可）、`mcp_tool_approvals.status` 取值与转换规则、对回归对拍的影响说明。
- [x] **Step 4: 跑绿**：`MVN verify`，预期全绿，其中 Modulith、ArchUnit、Checkstyle 通过；再对整条分支做一次 `code-review-mr` 审查，高置信问题修完后把结论写进 `progress.md`。
- [x] **Step 5: 提交**：`git add docs/migration/handoff-mcp-to-agenttools.md T/mcp/McpApprovalFlowSmokeTest.java docs/migration/progress.md`，`git commit -m "docs(mcp): 迁出 agenttools 的交接与本计划闸门"`。

# 3. 完成判据

以下命令全部给出预期输出才算本计划完成：

```bash
# 全量构建，含 Modulith、ArchUnit、Checkstyle
MVN verify
# 预期：BUILD SUCCESS，无 Checkstyle 违规

# 四域相关界面端到端（新栈），mcp-approval 除外
cd tests/e2e && npx playwright test --grep '@agent|@skill|@mcp|@memory|@favorite' --grep-invert @mcp-approval
# 预期：全部通过，无 skipped 的 @agent、@skill、@mcp、@memory、@favorite 用例

# OpenAPI 类型无漂移
cd frontend/packages/api-client && npm run check:api
# 预期：退出码 0

# 前端类型检查与相关单测
cd frontend && npx vue-tsc --noEmit && npx vitest run src/views/agent src/views/settings
# 预期：无错误

# 四域主码无手写租户条件、无注解 SQL、无禁用后缀类
grep -rEn "tenant_id" backend/app/src/main/java/com/ragagent/{agent,mcp,memory,favorite}
grep -rEn "@(Select|Update|Insert|Delete)\b" backend/app/src/main/java/com/ragagent/{agent,mcp,memory,favorite}
find backend/app/src/main/java/com/ragagent/{agent,mcp,memory,favorite} -name '*Ops.java' -o -name '*Support.java' -o -name '*Helper.java' -o -name '*Facade.java' -o -name '*Manager.java'
# 预期：三条命令均无输出
```

逐条对应路线图 06 号闸门与 spec 第 7 节：

| 闸门项 | 覆盖方式 |
|---|---|
| 四域相关页面转绿 | Task 2、10、19、28 的 e2e 命令 |
| mcp 通过 D26 结论落地 | Task 23，结论为退回自研时走 Task 23' |
| 验收 3：无手写租户条件，租户表开行级安全并有跨租户被拒测试 | Task 1、3、11、20 的 `*RowLevelSecurityTest` 与上面的 grep |
| 验收 4：业务异常带错误码与 i18n key，覆盖七语种 | Task 5、13、22 的 `*CodeMessagesTest` |
| 验收 5：前端类型由 OpenAPI 生成 | Task 9、18、27 的 `gen:api` 与 `check:api` |
| 验收 6：架构规则由 Java 测试表达 | 每个域最后一个任务的 `MVN verify` |

本计划明确不覆盖：`@mcp-approval` 界面用例与工具审批的整链路回归对拍，依赖计划 08 的问答主链路；聊天里 `McpOAuthCard`、`ToolApprovalCard` 的界面验收同样在计划 08 转绿。




