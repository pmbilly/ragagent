# RagAgent 重建：session 与 agenttools 迁入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `session`（31,716 行）与 `agenttools`（11,726 行）按新规范重写进 `backend/app`，用 Spring MVC `SseEmitter` 重建 SSE 通道，打通控制台问答、挂件对话、渠道问答、开放接口、Agent 工具五个入口的问答主链路，并让整链路的 SSE 对拍与检索回归通过。

**Architecture:** session 是编排域，按阶段拆类：轮次准入、请求解析、管线规划、管线执行、流通道、终态提交、会话持久化各自独立。多入口问答用 `session.bo.QaTurnCommand` 承载，控制台、挂件、开放接口、渠道、Agent 工具共用同一个 `QaTurnService`。流分发存储下沉到 framework，渠道域只依赖 framework 与自身声明的 Provider，不依赖编排域。agenttools 是工具实现层，用“窄接口加 Spring 登记表”：一个工具一个类，工具注册、白名单、并发策略由类自身声明，新增工具不改注册中心。MCP 工具包装从 mcp 域迁入 agenttools。

**Tech Stack:** JDK 21、Spring Boot 3.5.16、`SseEmitter` 与虚拟线程、MyBatis-Plus、Flyway、Spring Modulith、Sa-Token、Redisson、JobRunr、DuckDB JDBC、Testcontainers PostgreSQL、Playwright。

**规格来源**：`docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图：`docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 08 号计划。

## Global Constraints

继承路线图第 3 节全部约定。下列为本计划最常用的几条，原文以路线图为准。

- JDK 21、Spring Boot 3.5.16。Java 缩进 tab、行宽 140、中文 Javadoc、`@author jiangguifan`、魔法字符串收口常量或枚举。
- 业务错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号：session 018、agenttools 019。枚举放各域 `api` 包，命名 `SessionCode`、`AgentToolCode`。
- 文案 key `code_<错误码>`，业务域文案放 `backend/app/src/main/resources/i18n/app-messages*.properties`，七语种加默认文件齐全。
- Flyway 脚本放 `backend/app/src/main/resources/db/migration/allenv/`，命名 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_{域}_{描述}_jiangguifan.sql`。版本号与描述之间两个下划线。
- 主键 bigint 雪花 ID，序列化为字符串。租户表 `tenant_id bigint NOT NULL` 加行级安全，策略用计划 01 Task 5 的 fail-closed 模板。软删 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
- 路径三组：`/api/admin/v1`、`/api/v1`、`/api/open/v1`。后台用户用到的会话与问答接口归 admin，挂件访客归 `/api/v1`，同一资源由同一个 Service 服务。
- 代码质量按路线图 3.11：方法 40 行、类 400 行、参数 5 个、圈复杂度 10、嵌套 3 层、构造器依赖 7 个。类名不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common`。单实现不建接口。构造器注入用 `@RequiredArgsConstructor` 或显式构造器。
- 事务内不做外部调用。LLM、Embedding、向量库、网页抓取、MCP、渠道都在事务外或提交后执行。
- Maven 命令统一带 `-s C:/Users/jiangguifan/.m2/settings-coding-uat.xml`。`-pl` 必带 `-am`。下文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml`，`MU` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`。
- `git add` 只写显式路径。每个任务一次提交，提交后在 `docs/migration/progress.md` 追加一行。与计划不一致写进 `docs/migration/deviations.md`。

## Review Focus

下列五类输入与场景 spec 没有逐条写测试，但最容易出错。每条都在对应任务里有断言。

1. 会话不可见与不存在要返回同一个错误码，跨租户与跨属主都读不到。归属 Task 4、Task 13。
2. 同一会话已有生成中的轮次时，第二次提问返回 409，不覆盖 live-run 标记；取消后迟到的成功帧不能把消息改成成功。归属 Task 11、Task 13。
3. SSE 客户端在第 3 条事件后断开。期望：引擎继续跑完并落库，流泵降级为无头监控，取消事件与终态各只发一次，没有连接泄漏。归属 Task 10、Task 11。
4. `database_query` 跨租户读不到任何行，写语句被数据库拒绝，超过 `statement_timeout` 的查询被数据库杀掉并返回可读失败。归属 Task 17。
5. 新增一个工具只改一个文件。期望：工具名、schema、读写性质、并发策略、默认白名单都来自该类自身。归属 Task 15。

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 | framework 的 `RestResponse`、`BusinessRuntimeException`、`CodeCache`、`I18nUtil`、`@IgnoreTenant`、`TenantAwareJdbcTemplate`、`PostgresContainerTest`；Task 11d 的 `framework.stream` 与 `framework.sql`；`@ragagent/api-client`；`tests/e2e/PAGES.md`；`tests/api-smoke` 六条冒烟；`tests/regression` 的 `compare.mjs` 与 `baseline.json`；`deploy/db` 镜像 |
| 计划 02 | `agentengine.api.AgentEngineFactory`、`AgentEngineHandle`、`AgentEngineRequest`、`AgentTool`、`ToolDefinitions`、`ToolCancellation`；`agentengine.spi.AgentEventSink`、`SteerSink`、`SteerMessage`、`McpToolProvider`、`McpToolSession`、`McpSelection`；`modelruntime.api.LlmChatClient`、`ChatStream`、`Reranker`；`retrieval.api.HybridSearchApi` |
| 计划 03 | `auth.api` 当前主体与当前用户；`model.api` 的模型查询；`audit.api`；`storage.api` 的附件落盘与文件签名；`docs/migration/golden-sample.md`；权限点命名 |
| 计划 04 | `knowledge.api` 的知识库、文档、分块读取，grep 检索，标签范围解析；`docs/migration/handoff-04` 若存在以它为准 |
| 计划 05 | `wiki.api` 的页面读写与问题单接口 |
| 计划 06 | `agent.api` 的 `AgentQueryApi`；`memory.api` 四个 api 与 `MemoryTranscriptProvider` 声明；`mcp.api` 四个 api 与 `ApprovalResolutionListener` 声明；`docs/migration/handoff-mcp-to-agenttools.md` |
| 计划 07 | `channel.api` 的 `ChannelTurnProvider`、`ChannelTurn`、`ChannelTurnListener`、`ChannelTurnRequest`、`ChannelTurnRef`、`ChannelTurnOutcome`、`ChannelConversationSpec`、`ChannelVisitorApi`、`VisitorScope`、`VisitorSessionBo`、`ChannelMessageBo`、`SuggestionSetBo`、`SuggestionEventBo`，签名见“对外承诺” |

### 交付

1. session 与 agenttools 的表、接口、Service、Repository、前端 API 层。
2. session 与 agenttools 对 `framework.stream` 与 `framework.sql` 的消费接线。两个包由计划 01 Task 11d 交付，本计划不建。
3. agenttools 的工具登记表与 22 个工具，MCP 工具包装迁入完成。
4. 整链路对拍脚本 `tests/regression/sse/`，检索回归在整链路复跑的能力。
5. 路线图第 6 节交接表的增补，见下节。

### 对外承诺

**session 实现的 Provider**（下级声明，本域实现）：

| 声明方 | 接口 | 实现类 | 语义 |
|---|---|---|---|
| channel（07） | `ChannelTurnProvider` | `ChannelConversationService` | 渠道消息触发问答，无 SSE，结果经事件总线回流 |
| channel（07） | `ChannelVisitorApi` | `ChannelConversationService` | 挂件访客会话、消息、停止与建议，实现内部落 `EmbedSessionService` 与 `MessageSuggestionService` |
| mcp（06） | `ApprovalResolutionListener` | `ApprovalTurnResolver` | 审批决定后注入运行中的轮次，轮次已结束则开后续轮次 |
| memory（06） | `MemoryTranscriptProvider` | `SessionTranscriptReader` | 按游标增量读会话消息 |
| agent-engine（02） | `AgentEventSink` | `AgentEventBridge` | 引擎事件落 `StreamEventLog` |
| agent-engine（02） | `SteerSink` | `TurnSteerSink` | 引擎每轮边界排空 steer 队列并落 user 行 |
| agent-engine（02） | `McpToolProvider`（agenttools 实现，Task 19） | `McpToolProviderBridge` | 按租户与本轮选择开 MCP 工具会话 |
| agenttools（08，本域） | `ConversationSearchProvider` | `ConversationSearchService` | `search_conversations` 工具的取数 |

**channel（07）声明的两个接口**，包 `com.ragagent.channel.api`，签名以计划 07 的对外承诺为准，`ChannelVisitorApi` 的九个方法逐个实现，不得抽掉任一个：

| 接口 | 方法签名 |
|---|---|
| `ChannelTurnProvider` | `String openConversation(ChannelConversationSpec spec)`；`ChannelTurn startTurn(ChannelTurnRequest request)`；`void cancelTurn(ChannelTurnRef ref)` |
| `ChannelVisitorApi` | `void attachVisitor(VisitorScope scope, String sessionId)`；`List<VisitorSessionBo> listVisitorSessions(VisitorScope scope, int limit)`；`boolean deleteVisitorSession(VisitorScope scope, String sessionId)`；`long countSessions(long tenantId, long channelId)`；`List<ChannelMessageBo> loadMessages(VisitorScope scope, String sessionId, int limit, String before)`；`void stopTurn(VisitorScope scope, String sessionId, String assistantMessageId)`；`SuggestionSetBo readSuggestions(VisitorScope scope, String sessionId, String messageId)`；`SuggestionSetBo ensureSuggestions(VisitorScope scope, String sessionId, String messageId)`；`void recordSuggestionEvent(VisitorScope scope, String sessionId, SuggestionEventBo event)` |

`ChannelTurn` 是 `startTurn` 的返回值，接口本身在 channel 声明（`sessionId()`、`assistantMessageId()`、`onEvent(ChannelTurnListener)`、`awaitCompletion(Duration)`、`close()`），实现类在 session（Task 20 的 `ChannelTurnHandle`）。`ChannelTurnListener` 四个回调 `onReasoningDelta`、`onAnswerDelta`、`onTool`、`onCompleted`，`onCompleted` 恰好一次。

**session 的 api**：`SessionQueryApi`（`Optional<SessionBo> findOwned(long tenantId, String ownerId, long sessionId)`、`boolean exists(long tenantId, long sessionId)`），供 app 内装配与测试使用。

**agenttools 的 spi 与登记表**：`ToolBuildContext` 放在 `agenttools.api`；`SearchTargetsBo` 与 `KnowledgeScopeBo` 由 `knowledge.api` 提供，两个编排域共用。agenttools 不 import session 的任何包，否则 session 依赖 agenttools、agenttools 反向依赖 session，Modulith 报环。

```java
package com.ragagent.agenttools.api;

import com.ragagent.agentengine.api.AgentTool;
import com.ragagent.knowledge.api.SearchTargetsBo;
import com.ragagent.modelruntime.api.Reranker;

/**
 * Agent 工具工厂。一个实现类对应一个工具，注册为 Spring Bean 即完成注册。
 *
 * @author jiangguifan
 */
public interface AgentToolFactory {

	/** 工具名，与 ToolDefinitions 常量同值。 */
	String toolName();

	/** 默认是否进入智能体可选工具集。 */
	boolean isDefaultAllowed();

	/** 并发策略：只读且无会话级可变状态才算安全。 */
	boolean isConcurrencySafe();

	/** 构建本轮工具实例。 */
	AgentTool create(ToolBuildContext context);
}

/**
 * 工具构建上下文，随轮次创建。
 *
 * @param tenantId 本轮执行租户，共享智能体时取智能体所属租户
 * @param ownerId 会话属主，历史会话检索据此限定
 * @param sessionId 当前会话，用于剔除本轮自身
 * @param searchTargets 本轮检索范围
 * @param reranker 本轮重排模型，无重排时为 null
 * @author jiangguifan
 */
public record ToolBuildContext(long tenantId, String ownerId, long sessionId,
		SearchTargetsBo searchTargets, Reranker reranker) {
}
```

`ToolCatalog` 注入 `List<AgentToolFactory>`，构造时按 `toolName()` 建立 `Map`，重复名启动失败。允许清单仍由智能体配置与运行时开关裁剪，裁剪规则留在 `AgentToolSelector`。计划 02 没有“工具目录提供方 spi”，工具实例经 `AgentEngineRequest.tools`（`List<AgentTool>`）传给引擎，所以 `ToolCatalog` 是 app 内 Bean，只有 session 的 `AgentTurnAssembler` 调用它；唯一对引擎暴露的 spi 是 `McpToolProvider`，由 Task 19 的 `McpToolProviderBridge` 实现。

`Reranker` 在计划 02 的 `modelruntime.api`，`ToolBuildContext` 按该包名 import。

**对路线图第 6 节的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| channel（07） | `ChannelTurnProvider` | 渠道调用问答 | session（08） |
| channel（07） | `ChannelVisitorApi` | 挂件访客会话、消息、停止、建议 | session（08） |
| mcp（06） | `ApprovalResolutionListener` | 审批后恢复轮次 | session（08） |
| memory（06） | `MemoryTranscriptProvider` | 蒸馏读会话消息 | session（08） |
| agent-engine（02） | `AgentEventSink`、`SteerSink` | 事件流与运行中插话 | session（08） |
| agent-engine（02） | `McpToolProvider` | MCP 工具注册与调用 | agenttools（08） |
| agenttools（08） | `ConversationSearchProvider` | 历史会话检索工具 | session（08） |
| session（08） | `SessionQueryApi` | 会话存在性与归属查询 | agenttools（08）、channel（07）若需要 |

### 对前置计划的新增需求

1. **计划 02：工具 spi 要能承载本计划的显示与并发声明。** `agentengine.spi` 至少需要：`ToolDescriptor`（`name`、`descriptionKey` 或 `description`、`schema`、`isConcurrencySafe`）；`ExecutionPolicy` 改为查询 `ToolDescriptor.isConcurrencySafe()`，不再维护工具名 switch；`ToolDefinitions.defaultAllowedTools()` 与可选工具元数据表改为从工具目录汇总，不在引擎里写死名单。缺任何一项时，Task 15 先停下登记。
2. **计划 04：grep 检索与分块读取的 api。** 需要 `List<GrepHitBo> grepChunks(GrepQueryBo query)`（内容正则、知识库或标签范围、上限 500、返回命中数与分块所在文档标题）、`List<Long> countEnabledChunksByKnowledgeIds(List<Long> knowledgeIds)`、`Optional<KnowledgeFactsBo> findKnowledgeFacts(long knowledgeId)`、`ChunkPageBo listChunks(ChunkPageQueryBo query)`。名称以计划 04 为准。
3. **计划 05：wiki api 需要覆盖页面读写、搜索、问题单与索引页**，供 agenttools 的 10 个 wiki 工具使用。名称以计划 05 为准。
4. **计划 06：审批过期的 JobRunr 任务要逐租户回调。** `McpApprovalExpiryJob` 跨租户扫描后，必须按每一行的租户设置上下文再调用 `ApprovalResolutionListener`，否则 session 无法写回会话。
5. **计划 07：对话入口 Provider 需覆盖会话映射与问答触发**，渠道侧只传载荷与事件总线，不传 SSE。
6. **计划 01 Task 11d 交付流分发存储，本计划只消费。** 包 `com.ragagent.framework.stream`。签名见 Task 1 的 Consumes 原文，本计划不新建这些类，实现细节（Redis List 键 `{prefix}:{sessionId}:{messageId}` 与 `...:steer`、live-run 键、启动期 Ping）由 Task 11d 负责。
7. **计划 01 Task 11d 交付只读 SQL 执行器，并把 `jdbcTemplateAllowedOnlyInTenantAndRetrieval` 的白名单追加 `com.ragagent.framework.sql..`。** 签名见 Task 1 的 Consumes 原文：`interface ReadOnlySqlExecutor { List<Map<String, Object>> query(String sql, int maxRows); }`，实现用独立 Hikari 数据源与只读角色，每查询一个事务并先 `set_config('app.tenant_id', ?, true)`。现有规则只放行 `com.ragagent.framework.tenant..` 与 `com.ragagent.retrieval..`，`framework.sql` 未被覆盖，Task 11d 必须同步改这一行，Task 1 只复核白名单包含它。

## 本计划新做的设计决定（待用户核对）

### 一、新表结构与聚合根划分

1. **流分发存储放 framework，不进任何域。** 决定：`com.ragagent.framework.stream` 提供 `StreamEventLog`、`MemoryStreamEventLog`、`RedisStreamEventLog`、`StreamEvent`、`StreamBatch`、`LiveRun`。理由：渠道域（业务）要写停止事件，而业务域不得依赖编排域 session，framework 是唯一合法的共同下层。否决：`session.api.StreamStopApi`，渠道要依赖编排域，ArchUnit 直接红。
2. **`messages` 增加 `tenant_id` 并开启行级安全。** 决定：写入时取会话的租户，读路径由租户插件与行级安全双重约束。理由：消息表是数据量最大的表，隔离不能只靠 join 到会话表；行级安全要求每张租户表自带列。否决：保持无隔离列，所有消息读写必须 join 会话，行级安全覆盖不到。
3. **`sessions.user_id` 拆成 `owner_type` 加 `owner_id`。** 决定：`owner_type` 取 `USER`、`API_TENANT_KEY`、`API_EXTERNAL_USER`、`EMBED_SESSION`，`owner_id` 保留派生串原文。理由：四种主体共用一个字符串列时，列表查询靠 `LIKE '前缀%'` 分桶，改一个字符历史行就落错桶。否决：`owner_id bigint` 并关联用户表，表达不了 API Key 与访客主体。
4. **`sessions.description` 不再兼作渠道标记，新增 `channel_marker`。** 决定：`description` 回归用户可编辑字段，挂件 marker 单独一列。理由：旧代码把 embed 渠道标记塞进描述列，`ownership` 接口把两者一起返回，语义混叠。否决：保留混用，前端描述框会显示内部标记。
5. **`sessions` 的 14 个遗留检索策略列全部删除。** 决定：`knowledge_base_id`、`max_rounds`、`enable_rewrite`、`fallback_*`、`*_threshold`、`embedding_top_k`、`rerank_*`、`summary_*`、`context_config` 不迁移。理由：旧实体注释写明未映射，检索参数来自 `conversation.*` 配置与智能体配置，本域代码从不读写这些列。否决：原样保留，新表带一堆无人写入的列。

### 二、新接口路径与 VO 形状

1. **SSE 端点保持两个前缀各一份，由同一控制器映射两条路径。** 决定：`POST /api/admin/v1/sessions/{id}/knowledge-chat`、`/api/admin/v1/sessions/{id}/agent-chat`、`POST /api/v1/sessions/{id}/knowledge-chat`、`/api/v1/sessions/{id}/agent-chat`，四个映射指向同一方法。理由：挂件访客用 `/api/v1`，控制台用 `/api/admin/v1`，路线图 3.6 要求同一资源不重复实现。否决：挂件走 embed 渠道转发到 admin，访客身份无法通过 admin 鉴权。
2. **续流端点改为 `GET /api/{admin/}v1/sessions/{id}/stream?messageId=`。** 旧路径把 `continue-stream` 作为集合动作，不符合资源路径规范。前端 `streame.ts` 里的续流拼接随之更新。
3. **会话创建与更新拆成明确的两个 VO，`description` 不再接受渠道标记。** `CreateSessionReqVo` 只有 `title`、`description`、`agentId`；`UpdateSessionReqVo` 只允许 `title`、`description`。
4. **建议三端点合并为一个资源。** `GET/POST /api/admin/v1/sessions/{id}/messages/{messageId}/suggestions` 加 `POST /api/admin/v1/sessions/{id}/suggestion-events`，旧的三条路径机械换前缀，形状不变。
5. **附件列表与预览并入会话资源。** `GET/POST /api/admin/v1/sessions/{id}/attachments`，`GET/DELETE /api/admin/v1/sessions/{id}/attachments/{attachmentId}`，`GET .../preview`。旧的双 pattern（`{id}` 与 `{sessionId}`）只保留一个路径变量名 `{id}`。
6. **`SearchTargetsBo` 取代 `contract.retrieval.SearchTarget` 在本域与 agenttools 的传递。** 决定：`knowledge.api` 定义 `KnowledgeScopeBo`（知识库、文档、标签三元组），session 与 agenttools 一律使用它。理由：旧 `SearchTarget` 是 Go 形状的可变类，携带 `authObjects` 之类字段。否决：复制旧类到 framework，两个域各持一份。

### 三、Provider 与 spi 签名

1. **`ConversationSearchProvider` 声明在 agenttools，session 实现。** 签名：`List<ConversationExchangeBo> search(ConversationSearchQueryBo query)`，查询含 `ownerId`、`sessionId`（用于剔除本轮）、`query`、`limit`、`language`。理由：工具在 agenttools，数据在 session，按 D25 由上级 session 实现下级声明的接口。否决：agenttools 依赖 `session.api`，两个编排域成环。
2. **`AgentToolFactory` 与 `ToolBuildContext`**，见“对外承诺”。
3. **`SessionTranscriptReader`** 实现 memory 的 `MemoryTranscriptProvider`，游标为 `(createdAt, messageId)` 复合值。
4. **`ApprovalTurnResolver`** 实现 mcp 的 `ApprovalResolutionListener`，决定注入走 steer 通道，轮次已结束则开后续轮次。
5. **`ReadOnlySqlExecutor`** 由 framework 提供，agenttools 只声明使用。签名 `List<Map<String, Object>> query(String sql, int maxRows)`，入参是已通过 `SqlGuard` 校验的 SQL，框架只负责租户会话变量、只读事务、`statement_timeout` 与行数上限。理由：`JdbcTemplate` 的 ArchUnit 白名单只放 framework 与 retrieval-engine，agenttools 不能自建模板；把连接、账号与超时收进框架，工具类只关心 SQL 校验。

### 四、与旧行为不一致的地方

1. **SSE 帧不再逐字节对齐旧版。** 决定：帧由 `SseEmitter` 生成，按 SSE 规范解析，事件类型、顺序、载荷键序保持不变。理由：spec 5.6 第 6 条要求删除手工拼帧。前端的 `fetch-event-source`、mcp-server 与接口调试页都按规范解析。否决：保留 `SseFrameWriter` 手拼帧绕过 `SseEmitter`，等于没迁移。
2. **不再有全局 HTML 转义。** 决定：确认新 `ObjectMapper` 不启用 `CharacterEscapes`，并清理 `SseFrameWriter` 类注释与 `SseFrameWriterTest` 里“由 `JacksonConfig` 全局装满”的过时说明。理由：盘点已核实仓库不存在 `JacksonConfig` 类，也不存在任何 `CharacterEscapes` 配置，全仓只有两处过时注释。spec 写的“删除 `JacksonConfig` 中的全局 HTML 转义”与实际代码不符，本计划按“确认不开启并清理注释”落地。这条与 spec 的差异在此登记。否决：新增一个 `JacksonConfig` 再删掉转义，凭空造一个类。
3. **工具失败不再进用户可见错误码。** 工具错误文案给模型看，保留在 `ToolResult.error` 里；`agenttools` 的 019 号错误码只覆盖注册期与配置期异常。旧代码同样零 `BizException` 抛出，这里保持。
4. **数据库查询工具换只读账号，租户条件由行级安全与 `SET LOCAL` 承担。** 失败文案从“校验器拼出来的租户条件”变为数据库拒绝或超时，模型会看到新的失败措辞。
5. **审批挂起与恢复会多一个轮次边界。** 旧 Gate 在线程内阻塞等待，新实现本轮挂起、决定后注入或续跑，SSE 序列在审批前后多一次挂起与恢复。对拍时审批场景单列。
6. **临时附件清理与解析由 JobRunr 承担。** 旧实现是守护虚拟线程加单线程执行器，新实现是周期任务加按文档任务，语义（10 分钟周期、每批 100、按状态推进）保留。
7. **会话标题生成保留虚拟线程，不进 JobRunr。** 理由：标题事件要写进当前轮次的流，跨实例的 JobRunr 任务拿不到那条流；保留租户快照补齐身份。

# 1. 盘点

数据取自冻结点工作区。执行方在 Task 1 核对文件数与行数，有差异先改计划并登记。

## 1.1 规模与包去向

抽查确认：`domains/src/main/java/com/ragagent/session` 212 个文件 31,716 行，`agenttools` 59 个文件 11,726 行，测试 47 与 22 个。与盘点一致。

| 旧子包 | 文件 | 行数 | 新位置 |
|---|---|---|---|
| `session/api` | 6 | 576 | `session.api`（BO 与对外接口），Adapter 删除 |
| `session/assembly` | 2 | 399 | `com.ragagent.config.SessionAssemblyConfig` |
| `session/attachment` | 5 | 1,211 | `session.attachment`（服务、处理器、文件存储、提示词解析） |
| `session/controller` | 9 | 2,249 | 8 个控制器加 1 个 package-info，`session.controller` 按资源拆成 9 个控制器（含新增的 `MessageSuggestionController`） |
| `session/controller/sse` | 3 | 180 | 删除。帧由 `SseEmitter` 生成，四个头进 framework |
| `session/conversation` | 10 | 2,903 | `session.service`（会话、消息、检索、终态）与 `session.stream` |
| `session/domain` 三组 | 22 | 1,807 | `session.entity` 与 `session.bo`（旧 `domain` 顶层 7 个 jsonb TypeHandler 一并迁 `session.mapper`） |
| `session/dto` | 28 | 1,153 | `session.vo`（入出参）与 `session.bo`（用例载荷），无用 DTO 删除 |
| `session/mapper` | 5 | 519 | `session.mapper` 加 XML |
| `session/qa` | 13 | 2,111 | `session.service.qa`（准入、解析、执行、终态） |
| `session/rag` | 22 | 1,920 | `session.pipeline`（管线与阶段接口） |
| `session/rag/plugin` | 24 | 5,604 | `session.pipeline.stage`，一个阶段一个类，`Ops` 后缀去掉 |
| `session/rag/spi` 与 `support` | 9 | 564 | 阶段接口进 `session.pipeline`，工具函数消亡或并入用到的阶段类 |
| `session/repository` | 5 | 1,274 | `session.repository`，按聚合根 |
| `session/runtime` | 3 | 72 | 删除，虚拟线程执行器由调用点自建 |
| `session/service` | 29 | 6,447 | `session.service` 与 `session.service.suggestion`、`session.service.agent` |
| `session/service/sse` | 4 | 377 | `session.stream`（事件发射、响应组装、写出口） |
| `session/service/suggestion` | 4 | 1,259 | `session.service.suggestion` |
| `session/support` | 8 | 1,078 | 并入调用方：`ToolResultPersist` 进 `session.stream`，其余静态工具随阶段类 |
| `agenttools/datasource` | 6 | 934 | `agenttools.datasource` |
| `agenttools/knowledge` | 12 | 4,422 | `agenttools.knowledge` |
| `agenttools/sql` | 6 | 1,917 | `agenttools.sql`，`SqlInjectionAnalyzer` 删除 |
| `agenttools/web` | 3 | 1,155 | `agenttools.web` |
| `agenttools/wiki` | 31 | 3,285 | `agenttools.wiki` |

**超出 400 行的类及拆分归宿。** session 21 个、agenttools 12 个超过 400 行。下表除这 33 个外，还收了与拆分直接相邻、同一变化原因的类（如 300 行级的 `SessionRepository`、`QaRequestParser`，以及 200 行级的阶段类），保证一次拆到底。`TurnStreamPump`、`StreamEventEmitter`、`MessageService`、`SessionService`、`SessionRepository`、`QaRequestParser`、`MessageSuggestionService`、`SteerController`、`SessionController`、`SessionStreamController` 保留原名，其余改造后改名或删除。

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `SessionKnowledgeQaService` 680 | `QuestionAnswerService` ≤250（用例入口）、`RetrievalPlanBuilder` ≤200（阶段序列选择）、`KnowledgeScopeResolver` ≤200（检索范围与租户） | 用例、计划、范围三个变化原因 |
| `QaTurnExecutor` 537 | `QaTurnService` ≤250（准入与提交）、`QaTurnRunner` ≤200（异步执行体）、`LiveRunGate` ≤120（并发守卫） | 编排、执行、互斥 |
| `QaSseOrchestrator` 260 | 删除。职责分给 `QaTurnRunner`、`TurnStreamPump`、`AgentEventBridge` | 编排职责拆到底 |
| `TurnStreamPump` 217 | `TurnStreamPump` ≤200，只负责增量读与写帧；无头模式与 SSE 模式共用 | 单一读循环 |
| `AgentStreamBridge` 671 | `AgentEventBridge` ≤200（16 类事件映射）、`AssistantMessageAccumulator` ≤200（正文与步骤累积） | 事件映射与状态累积 |
| `StreamEventEmitter` 176 | `StreamEventEmitter` ≤180，接口改为面向 `SseEmitter` | 单一写出点 |
| `StreamResponseBuilder` 172 | `StreamResponseAssembler` ≤180 | 改名，逻辑不变 |
| `SessionService` 667 | `SessionService` ≤250（增删改）、`SessionReadService` ≤200（可见性与列表）、`SessionTitleService` ≤150（标题生成） | 写、读、标题 |
| `MessageService` 522 | `MessageService` ≤250、`MessageSearchService` ≤200（关键词与向量融合检索） | 消息读写与历史检索 |
| `MessageSearch` 582 | 同上，合并进 `MessageSearchService`，RRF 抽成 `ReciprocalRankFusion` ≤100 | 融合算法独立 |
| `MessageRepository` 589 | `MessageRepository` ≤300（单表）、`MessageArtifactReader` ≤120（产物与附件投影） | 单表与跨 jsonb 投影 |
| `SessionRepository` 347 | `SessionRepository` ≤250 | 保持单类 |
| `SteerController` 522 | `SteerController` ≤150、`SteerService` ≤250（排队、提升、删除、live-run 重绑） | 控制器只做绑定 |
| `SteerSinkBridge` 262 | `TurnSteerSink` ≤200（引擎侧 `SteerSink` 实现与排空）、`SteerService` ≤250（HTTP 侧读写） | 生产与消费分开 |
| `SteerRunCoordinator` 254 | 并入 `QaTurnRunner` 的收尾阶段与 `SteerService` | 交接属于轮次生命周期 |
| `SessionController` 509 | `SessionController` ≤200、`SessionArtifactController` ≤120、`MessageController` ≤150、`MessageFileController` ≤80 | 按资源拆 |
| `SessionStreamController` 378 | `SessionStreamController` ≤120（只建 emitter 并交泵） | 续流并入同一控制器 |
| `KnowledgeQaController` 144 | `QaController` ≤150，一条路径映射两个前缀 | 一控制器一资源 |
| `TemporaryDocumentController` 341 | `AttachmentController` ≤200 | 改名 |
| `TemporaryDocumentService` 477 | `TemporaryDocumentService` ≤250、`TemporaryDocumentCleanupJob` ≤100、`TemporaryDocumentProcessor` ≤200 | 服务、清理、解析 |
| `MessageSuggestionService` 592 | `MessageSuggestionService` ≤250、`MessageSuggestionGenerator` ≤200（模型与知识两路）、`MessageSuggestionCache` ≤120 | 用例、生成、缓存 |
| `MessageSuggestionPipeline` 510 | 并入 `MessageSuggestionGenerator` | 生成算法 |
| `MessageSuggestionFacade` 146 | `MessageSuggestionController` 内的错误映射私有方法 | 门面是转发层 |
| `AgentToolBackends` 552 | 删除。工具工厂在 agenttools，取数 Provider 在 `session.agent` 与各域 api | 装配集中在工具类 |
| `AgentToolKbBackends` 544 | 删除。分块读取走 `knowledge.api`，grep 走 `knowledge.api` | 消除跨域 SQL |
| `AgentToolWikiBackends` 273 | 删除。wiki 工具直接注入 `wiki.api` | 同上 |
| `AgentEngineAssembler` 466 | `AgentTurnAssembler` ≤250（引擎参数、历史、记忆）、`AgentToolSelector` ≤200（允许清单与开关裁剪） | 装配与选择 |
| `AgentConfigAssembler` 314、`AgentHistoryAssembler`、`AgentResolver`、`AgentConfigSnapshot` | `AgentTurnAssembler` 的私有阶段方法，配置归一调 `AgentQueryApi` | 收敛 |
| `QaSupport` 538 | 删除。容器类拆成 `session.bo` 的 `QaTurnCommand`、`QaTurnContext`、`TurnStreamContext`、`TagScope`，常量进 `SessionConstants` | 静态杂物类 |
| `QaRequestParser` 347 | `QaRequestParser` ≤250（校验、附件、图片） | 单类 |
| `QaTurnFinalizer` 221 | `TurnFinalizer` ≤200（幂等提交与提交后动作） | 改名 |
| `ToolResultPersist` 435 | `session.stream.ToolPayloadSanitizer` ≤200（落库裁剪）、`session.stream.ToolResultRenderer` ≤150（下发形状） | 落库与下发 |
| `MessageReferenceRewriter` 322 | `session.vo.ReferenceRewriter` ≤200 | 并入引用重写 |
| `rag/plugin/PluginRerank` 679 | `RerankStage` ≤250、`RerankScoring` ≤150（MMR 与复合分）、`PassageEnricher` ≤150 | 流程、打分、富化 |
| `rag/plugin/PluginMerge` 654 | `MergeStage` ≤250、`ParentChunkResolver` ≤150、`FaqAnswerAppender` ≤150、`SequentialChunkMerger` ≤150 | 四件独立规则 |
| `MergeParentOps` 523 | 合并进 `ParentChunkResolver` 与 `SequentialChunkMerger` | Ops 回引取消 |
| `PluginSearch` 232 与 `PluginSearchOps` 382、`PluginExpansionOps` 211，共 825 | `ChunkSearchStage` ≤250、`QueryExpansion` ≤150、`SearchTargetExecutor` ≤200 | 三阶段 |
| `PluginQueryUnderstand` 525 | `QueryUnderstandStage` ≤250、`QueryRewriteService` ≤150 | 流程与改写 |
| `PluginIntoChatMessage` 381 | `IntoChatMessageStage` ≤250、`ImageDescriber` ≤150 | 组装与视觉 |
| `PluginChatCompletionStream` 367 | `ChatCompletionStage` ≤250，流消费进 `session.stream.ModelStreamConsumer` ≤150 | 阶段与流消费 |
| `session/support/*` 8 个共 1,078 | `SessionConstants`、`ReferenceRewriter`、`OwnerIdentity` ≤150 | 常量、引用、主体 |
| `agenttools/web/WebFetchTool` 695 | `WebFetchTool` ≤250、`FetchedPageTrimmer` ≤200（预算与截断）、`PageSnapshotWriter` ≤150 | 抓取、裁剪、落页 |
| `agenttools/knowledge/GrepChunksTool` 636 | `GrepChunksTool` ≤250、`GrepRegexBuilder` ≤150、`GrepOutputFormatter` ≤150 | 入参、执行、输出 |
| `agenttools/sql/SqlSelectDeepChecker` 596 | `SqlSelectChecker` ≤250、`SqlTableCollector` ≤200（表与 CTE 收集） | 校验与收集 |
| `agenttools/knowledge/KnowledgeSearchTool` 585 | `KnowledgeSearchTool` ≤250、`KnowledgeSearchExecutor` ≤200、`KnowledgeSearchMerger` ≤150 | 入口、检索、合并 |
| `agenttools/datasource/DataAnalysisTool` 574 | `DataAnalysisTool` ≤250、`DuckDbTableLoader` ≤200 | 工具与装载 |
| `agenttools/knowledge/QueryKnowledgeGraphTool` 507 | `KnowledgeGraphTool` ≤250、`GraphResultFormatter` ≤150 | 查询与输出 |
| `agenttools/knowledge/KnowledgeSearchOutputFormatter` 466 | `KnowledgeSearchFormatter` ≤250 | 改名 |
| `agenttools/web/WebSearchTool` 456 | `WebSearchTool` ≤250、`WebSearchResultFetcher` ≤150 | 搜索与正文抓取 |
| `agenttools/knowledge/ListKnowledgeChunksTool` 443 | `ListChunksTool` ≤200 | 依赖 knowledge.api |
| `agenttools/sql/SqlGuard` 432 | `SqlGuard` ≤200（只做语法与表白名单）、`SqlScopeAppender` ≤150 | 校验与范围 |
| `agenttools/knowledge/GetDocumentInfoTool` 427 | `DocumentInfoTool` ≤200 | 改名 |
| `agenttools/knowledge/FaqSnippet` 416 | `FaqSnippetProjector` ≤200 | 改名 |

**超过 100 行的方法与归宿。** 全部随上表拆类消失。重点三条：`SqlSelectDeepChecker.deepCheck` 275 行拆成 `checkStatement`、`checkSubqueries`、`checkSystemColumns` 三个方法；`WikiReadSourceDocTool.execute` 246 行拆成 `resolveRoute`、`readChunks`、`render`；`QueryKnowledgeGraphTool.execute` 240 行拆成 `parseQuery`、`runGraphSearch`、`render`。其余方法按同法拆到 40 行内。

## 1.2 旧接口到新接口

旧前缀 `/api/v1` 按资源归属换前缀。后台用户接口进 `/api/admin/v1`，访客接口进 `/api/v1`，问答与续流两个前缀都有。表只列需要说明的行。

| 资源 | 旧接口 | 新接口与说明 |
|---|---|---|
| 问答 | `POST /knowledge-chat/{sessionId}`、`POST /agent-chat/{sessionId}` | `/api/admin/v1/sessions/{id}/knowledge-chat`、`.../agent-chat`；`/api/v1/sessions/{id}/...` 同一方法映射 |
| 检索（无 LLM） | `POST /knowledge-search` | `/api/admin/v1/sessions/{id}/knowledge-search`，会话 ID 从 body 移到路径 |
| 续流 | `GET /sessions/continue-stream/{sessionId}?messageId=` | `GET /api/admin/v1/sessions/{id}/stream?messageId=`，挂件走 `/api/v1` |
| 会话 | `GET/POST /sessions`、`GET/PUT/DELETE /sessions/{id}` | 机械换前缀。创建与更新的 VO 见决定二第 3 条 |
| 批量删除 | `DELETE /sessions/batch` | `POST /api/admin/v1/sessions/batch-delete`，动作接口用 `POST /<资源>/<动词>` |
| 清空消息 | `DELETE /sessions/{id}/messages` | 保留 |
| 置顶 | `POST/DELETE /sessions/{id}/pin` | 保留 |
| 标题 | `POST /sessions/{sessionId}/generate_title` | `POST /api/admin/v1/sessions/{id}/title` |
| 停止 | `POST /sessions/{sessionId}/stop` | `POST /api/admin/v1/sessions/{id}/stop` |
| steer | `POST /sessions/{sessionId}/steer`、`.../steer/{steerId}/inject`、`GET /sessions/{id}/steer`、`DELETE /sessions/{id}/steer/{steerId}` | 机械换前缀，路径变量统一 `{id}` |
| 消息 | `GET /messages/{sessionId}/load`、`DELETE /messages/{sessionId}/{id}`、`POST /messages/search`、`GET /messages/chat-history-stats` | `/api/admin/v1/sessions/{id}/messages`、`.../messages/{messageId}`、`/api/admin/v1/messages/search`、`/api/admin/v1/messages/statistics` |
| 消息文件 | `GET/HEAD /sessions/{id}/messages/{messageId}/files` | `/api/admin/v1/sessions/{id}/messages/{messageId}/files`，HEAD 与 GET 同一方法加 `@RequestMapping(method = {GET, HEAD})` |
| 产物 | `GET /sessions/{id}/artifacts`、`.../messages/{messageId}/artifacts`、`.../artifacts/{index}/download` | 机械换前缀 |
| 建议 | 三条路径 | 机械换前缀，见决定二第 4 条 |
| 附件 | `POST/GET /sessions/{id}/attachments`、`GET/DELETE .../{attachmentId}`、`.../preview` | 机械换前缀，路径变量统一 `{id}` 与 `{attachmentId}` |

## 1.3 旧表到新表

通用规则：旧 `varchar(36)` 主键改 bigint 雪花，`tenant_id integer` 改 bigint，`timestamp` 改 `timestamptz`，软删改哨兵值，租户表开行级安全。

| 新表 | 旧表 | 列与约束 | 写属主 |
|---|---|---|---|
| `sessions` | `sessions` | `id`、`tenant_id`、`title varchar(255)`、`description text`、`agent_id bigint`、`owner_type varchar(32)`、`owner_id varchar(512)`、`channel_marker varchar(128)`、`embed_visitor_id varchar(128)`、`is_pinned boolean`、`pinned_at`、`last_request_state jsonb`、`created_at`、`updated_at`、`deleted_at`。删 14 个遗留检索策略列与 `context_config`。索引 `(tenant_id, owner_type, owner_id, is_pinned DESC, pinned_at DESC, updated_at DESC) WHERE deleted_at = 哨兵`、`(tenant_id, channel_marker, updated_at DESC)`、`(tenant_id, embed_visitor_id, updated_at DESC) WHERE embed_visitor_id IS NOT NULL` | `SessionRepository` |
| `messages` | `messages` | `id`、`tenant_id`（新增）、`request_id varchar(64)`、`session_id bigint`、`role varchar(16)`、`content text`、`rendered_content text`、`knowledge_references jsonb`、`agent_steps jsonb`、`mentioned_items jsonb`、`images jsonb`、`attachments jsonb`、`artifacts jsonb`、`used_memories jsonb`、`usage jsonb`、`execution_context jsonb`、`is_completed boolean`、`is_fallback boolean`、`agent_id bigint`、`agent_tenant_id bigint`、`model_id varchar(64)`、`knowledge_id bigint`、`channel varchar(32)`、`agent_duration_ms bigint`、`created_at`、`updated_at`、`deleted_at`。索引 `(tenant_id, session_id, created_at)`、`(tenant_id, request_id)`、gin `agent_steps` | `MessageRepository` |
| `temporary_documents` | `temporary_documents` | `id`、`tenant_id`、`session_id bigint`、`resource_ref text`、`file_name varchar(1024)`、`file_type varchar(32)`、`mime_type varchar(255)`、`file_size bigint`、`status varchar(16)`、`content text`、`chunks jsonb`、`image_refs jsonb`、`metadata jsonb`、`processing_options jsonb`、`token_count int`、`chunk_count int`、`error_message text`、`expires_at timestamptz`、`started_at`、`ready_at`、`created_at`、`updated_at`、`deleted_at`。索引 `(expires_at)`、`(tenant_id, session_id)`、`(status)` | `TemporaryDocumentRepository` |
| `message_suggestion_sets` | 同名 | `id`、`tenant_id`、`session_id bigint`、`assistant_message_id bigint`、`agent_id bigint`、`agent_tenant_id`、`placement`、`config_hash`、`locale`、`status`、`allow_regenerate`、`suppression_reason`、`questions jsonb`、`model_id`、`prompt_tokens`、`completion_tokens`、`latency_ms`、`error_code`、`lease_until`、`generated_at`、`created_at`、`updated_at`、`deleted_at`。唯一 `(tenant_id, assistant_message_id, placement, config_hash, locale, deleted_at)` | `MessageSuggestionRepository` |
| `message_suggestion_events` | 同名 | `id`、`tenant_id`、`session_id bigint`、`suggestion_set_id bigint`、`question_id`、`event_type`、`actor_id`、`created_at`。索引 `(tenant_id, session_id)`、`(suggestion_set_id)` | `MessageSuggestionRepository` |

`sessions.agent_config` 改名 `last_request_state`，它实际存的是 `SessionLastRequestState`（旧实体注释已写明）；字段映射按旧 record 十个字段一一对应，`agentConfig` 键改名 `lastRequestState` 属前端可见变化，前端 API 层不改字段名，适配在服务端读旧键名。

`messages.tenant_id` 是新增列，而 spec 5.7.1 第 5 条写明系统未上线、无旧数据迁移，所以只建列与行级安全，不写回填 DML。系统若在某个环境下已有数据，Task 1 的 DDL 脚本改为先建可空列、再单独提交一份 DML 回填（`UPDATE messages m SET tenant_id = s.tenant_id FROM sessions s WHERE s.id = m.session_id AND m.tenant_id IS NULL`）、最后 DDL 收紧 `NOT NULL`，三份脚本分文件，并登记 `deviations.md`。

写入规则：消息行的 `tenant_id` 取自会话行，同一事务内先取会话再写消息；`MessageRepository` 与 Mapper XML 都不写 `tenant_id` 条件，由租户插件注入，行级安全兜底。跨租户读取由行级安全拦下。

## 1.4 旧类到新类的阶段拆分

问答编排按阶段拆，流泵、流分发、会话持久化各自独立成类。这是本计划最重要的拆分规则。

**问答主链路阶段串（新）**：

```
QaController → QaTurnService（准入、落库、开流）
                 ├─ LiveRunGate（并发守卫）
                 ├─ QaTurnRunner（异步执行体，无 SSE 依赖）
                 │    ├─ QaRequestParser（校验、附件、图片）
                 │    ├─ RetrievalPlanBuilder（阶段序列）
                 │    ├─ QuestionAnswerService（NORMAL）或 AgentTurnService（AGENT）
                 │    └─ TurnFinalizer（幂等提交与提交后动作）
                 └─ TurnStreamPump（增量读流、写 SseEmitter）
```

| 旧类 | 新类 | 归属 |
|---|---|---|
| `QaEntryRunner`、`QaRequestBinder`、`ParsedRequest` | `QaTurnService`、`QaRequestParser`、`QaTurnCommand` | `session.service.qa` |
| `QaTurnExecutor` | `QaTurnService`、`QaTurnRunner`、`LiveRunGate` | 同上 |
| `QaSseOrchestrator` | 删除，职责分给 `QaTurnService` 与 `TurnStreamPump` | 同上 |
| `TurnStreamPump` | `TurnStreamPump` | `session.stream` |
| `AgentStreamBridge` | `AgentEventBridge`、`AssistantMessageAccumulator` | `session.stream` |
| `StreamEventEmitter`、`StreamResponseBuilder`、`SseFrameSink`、`SseFrameWriter`、`SseContract`、`ServletSseSink` | `StreamEventEmitter`、`StreamResponseAssembler`、`SseEmitterFactory` | `session.stream`，后两个旧类删除 |
| `QaTurnFinalizer`、`QaTurnSupport`、`TurnLifecycle`、`TurnMessageGate`、`TurnMessageHandle` | `TurnFinalizer`、`TurnLifecycle`、`TurnMessageGate` | `session.service.qa` |
| `SteerController`、`SteerSinkBridge`、`SteerRunCoordinator`、`QaSupport` 的 steer 常量 | `SteerController`、`SteerService`、`SteerInbox` | `session.controller` 与 `session.service.steer` |
| `SessionService`、`SessionReadService`、`SessionLookupScope`、`SessionStopService` | `SessionService`、`SessionReadService`、`SessionTitleService`、`SessionStopService` | `session.service` |
| `MessageService`、`MessageHistoryService`、`MessageSearch` | `MessageService`、`MessageSearchService`、`ReciprocalRankFusion` | `session.service` |
| `SessionRepository`、`MessageRepository` | `SessionRepository`、`MessageRepository`、`MessageArtifactReader` | `session.repository` |
| `api/SessionQaAdapter`、`EmbedSessionAdapter`、`EmbedSessionOpsAdapter`、`KnowledgeQaEventAdapter`、`MessageTranscriptAdapter` | `ChannelConversationService`（实现 channel 的 `ChannelTurnProvider` 与 `ChannelVisitorApi`）、`EmbedSessionService`（访客会话读写，被前者调用）、`SessionTranscriptReader`；`KnowledgeQaEventAdapter` 无消费方，删除 | `session.service` |
| `service/AgentToolBackends`、`AgentToolKbBackends`、`AgentToolWikiBackends` | 删除，工具工厂在 agenttools，取数调用下级 `api` | 无 |
| `service/AgentEngineAssembler`、`AgentConfigAssembler`、`AgentHistoryAssembler`、`AgentResolver`、`AgentWebPages` | `AgentTurnAssembler`、`AgentToolSelector`、`AgentWebPageStore` | `session.service.agent` |
| `rag/Pipeline*`、`ChatManage*`、`QaWiring` | `session.pipeline` 的 `Pipeline`、`PipelineStage`、`PipelineExecutor`、`ChatSettings`；装配类另见 `com.ragagent.config.SessionAssemblyConfig` | `session.pipeline` |
| `rag/plugin` 24 个类 | `session.pipeline.stage` 下 15 个阶段类，见 1.1 拆分表 | `session.pipeline.stage` |
| `rag/plugin/McpToolWrapper` 等 MCP 工具类（实际在旧 `mcp/tool` 包，不在 session） | `agenttools.mcp` 下 13 个类，见 1.5 | `agenttools.mcp` |


**agenttools 注册结构（新）**：

```
AgentToolFactory（窄接口）─ 22 个内建工具实现（knowledge 与 web 9、wiki 10、datasource 2、sql 1）
ToolCatalog（Spring 登记表，按 toolName 索引内建工厂）
AgentToolSelector（按智能体配置与运行时开关裁剪）
ExecutionPolicy（查询每个 AgentToolFactory.isConcurrencySafe()）
```

MCP 工具不进 `AgentToolFactory`：工具数由 MCP 服务端决定，走 `McpToolProvider` 与 `McpToolSession`（Task 19）。

新增工具只需新增一个 `@Component` 实现类，名称、schema、默认白名单、并发性质随类声明；`ToolCatalog` 与并发策略不改代码。

**事件映射表（复制自旧 `AgentStreamBridge.subscribe`，字段可改类型名不可改）。** 16 类 `EventType` 到 `ResponseType`：

| 旧 `EventType` | 帧 `ResponseType` |
|---|---|
| `EVENT_AGENT_THOUGHT` | `thinking` |
| `EVENT_AGENT_TOOL_CALL` | `toolCall` |
| `EVENT_AGENT_TOOL_RESULT` | `toolResult`（失败时可能额外发 `error`） |
| `EVENT_AGENT_REFERENCES` | `references` |
| `EVENT_MEMORY_RECALLED` | `memoryRecalled` |
| `EVENT_CONTEXT_COMPACTED` | `contextCompacted` |
| `EVENT_USER_MESSAGE_INJECTED` | `userMessageInjected` |
| `EVENT_AGENT_FINAL_ANSWER` | `answer` |
| `EVENT_AGENT_REFLECTION` | `reflection` |
| `EVENT_ERROR` | `error` |
| `EVENT_SESSION_TITLE` | `sessionTitle` |
| `EVENT_AGENT_COMPLETE` | `complete` |
| `EVENT_TOOL_APPROVAL_REQUIRED` | `toolApprovalRequired` |
| `EVENT_TOOL_APPROVAL_RESOLVED` | `toolApprovalResolved` |
| `EVENT_MCP_OAUTH_REQUIRED` | `mcpOauthRequired` |
| `EVENT_MCP_OAUTH_RESOLVED` | `mcpOauthResolved` |

另有两类不由事件桥产出：`agentQuery` 由 `QaTurnService` 开流时直写，`stop` 由 `TurnStreamPump` 在收到 STOP 事件时写。`ResponseType` 的 23 个枚举字面量（`answer`、`references`、`thinking`、`toolCall`、`toolResult`、`installOutput`、`commandOutput`、`error`、`reflection`、`sessionTitle`、`agentQuery`、`complete`、`artifactsPending`、`toolApprovalRequired`、`toolApprovalResolved`、`mcpOauthRequired`、`mcpOauthResolved`、`memoryRecalled`、`steer`、`userMessageInjected`、`contextCompacted`、`installPrompt`、`stop`）不可改，前端与 mcp-server 按它们分派。

## 1.5 MCP 工具包装迁入

按计划 06 的「对外承诺」清单接收，逐个改为调用 `mcp.api`。

| 旧类（行） | 新类（预算） | 说明 |
|---|---|---|
| `McpToolWrapper` 497 | `McpToolWrapperTool` ≤250 | 调用 `McpToolGatewayApi.callTool`，审批走 `openToolApproval` 后挂起 |
| `McpCatalog` 543 | `McpToolCatalog` ≤250 | 数据来自 `McpToolGatewayApi.loadCatalog` |
| `McpCatalogPagination` 215 | `McpCatalogPaginator` ≤150 | 分页 |
| `McpDiscoverTool` 276、`McpCallTool` 132 | `McpDiscoverTool` ≤150、`McpCallTool` ≤120 | 两个代理工具 |
| `McpExposure` 314、`McpTooling` 309 | `McpToolExposure` ≤200、`McpToolingBridge` ≤200（实现 `agentengine.spi.McpToolSession`） | 注册编排 |
| `McpInputSchemaValidator` 440 | `McpInputSchemaValidator` ≤250 | 保持 |
| `McpJsonCodec` 108、`McpToolNaming` 84、`McpRegisteredTool` 80 | `McpJsonCodec` ≤100、`McpToolNaming` ≤80、`McpRegisteredTool` ≤100 | 保持 |
| `McpOAuthPromptSupport` 271 | `McpOAuthCoordinator` ≤200 | 改为 `openOAuthAuthorization` 后挂起 |
| `ApprovalEventBuses` | 删除 | 事件由 session 的事件流发射 |

## 1.6 后台任务与队列去向

| 旧机制 | 去向 |
|---|---|
| `TemporaryDocumentService` 守护虚拟线程，10 分钟周期，每批 100 | JobRunr 周期任务 `TemporaryDocumentCleanupJob`，`@IgnoreTenant` |
| `TemporaryDocumentProcessor` 单线程执行器 | JobRunr 按文档任务 `TemporaryDocumentParseJob`，任务参数带租户 |
| `SessionService.generateTitleAsync` 虚拟线程 | 保留虚拟线程，原因见设计决定四第 7 条 |
| `QaTurnExecutor.startAsyncRunner` 虚拟线程 | 保留虚拟线程，线程名 `qa-turn-<messageId>` |
| `QaSseOrchestrator` STOP 兜底 3000ms | 保留在 `QaTurnRunner`，常量 `SessionConstants.STOP_FALLBACK_MILLIS` |
| `TurnStreamPump` 100ms 或 300ms 轮询，2 小时上限 | 保留，泵仍在虚拟线程 |
| `StreamManager`（memory 与 redis 两实现） | 迁到 `framework.stream`，键前缀与 TTL 保留，实现名 `MemoryStreamEventLog` 与 `RedisStreamEventLog` |
| `MessageSuggestion*` 无自有调度 | 保留，终态提交后异步触发，租约列保留 |
| `PluginIntoChatMessage`、`PluginQueryUnderstand` 的图片回写虚拟线程 | 保留 |
| `PluginSearch`、`PluginExpansionOps`、`PluginWebFetch` 的并发虚拟线程 | 收敛为 `session.pipeline.PipelineExecutor` 内的受限并发，上限 16 |
| `AnalysisDuckDbJdbc` 进程内 DuckDB 连接 | 保留，工具实例按轮创建，`Cleanable` 清理 |
| `agenttools/web` 并行抓取 | 保留虚拟线程加 `join`，上限 3 |

无 `@Scheduled` 迁移；除临时附件清理外无数据库轮询队列。

## 1.7 跨域依赖

| 本域 | 依赖 | 新做法 |
|---|---|---|
| session | knowledge | `knowledge.api`：知识库与文档读取、分块分页、grep 检索、标签范围解析。**不再跨域直接查 `chunks` 与 `knowledges`** |
| session | retrieval | `retrieval.api.HybridSearchApi` 做向量与关键词检索、取查询向量 |
| session | model-runtime | `LlmChatClient`、`ChatStream`、`Reranker`、上下文预算 |
| session | model | `model.api` 查模型参数与视觉能力 |
| session | agent | `AgentQueryApi` 取智能体配置、内置智能体判定、推荐问题 |
| session | memory | 四个 api；本域实现 `MemoryTranscriptProvider` |
| session | mcp | 四个 api；本域实现 `ApprovalResolutionListener` |
| session | storage | 附件落盘与文件签名 |
| session | audit | 会话删除与导出审计 |
| session | channel | 无。渠道调本域，方向不反转 |
| agenttools | knowledge | `knowledge.api` 分块与 grep |
| agenttools | wiki | `wiki.api` 页面读写与问题单 |
| agenttools | datasource | 无其他域，DuckDB 进程内 |
| agenttools | websearch | `websearch.api` 搜索与抓取 |
| agenttools | memory | `memory.api.MemorySearchApi` |
| agenttools | session | **无反向依赖**。历史会话检索经本域声明的 `ConversationSearchProvider`，由 session 实现 |
| agenttools | mcp | 四个 api |
| agenttools | model-runtime | 重排与嵌入 |
| 被依赖 | channel（07）、装配 | 见交接表 |

`agenttools` 不再出现任何 SQL；三张表直查全部换成 `knowledge.api`。

## 1.8 旧测试处置

处置只有三种：移植业务断言、改写为新规范、丢弃。丢弃理由只能在行里写明是钉旧错误文案、零值或字节形状。

**session（47 个）**

| 旧测试 | 测什么 | 处置 |
|---|---|---|
| `KnowledgeQaContractTest` | 三入口回放与错误信封 | 改写为 `QaControllerTest`（Task 13），保留业务断言（缺 query、知识库不存在、会话不存在、agent 模式缺 agent），丢弃逐字错误文案与帧骨架 |
| `MessageJsonContractTest`、`SessionJsonContractTest` | 键序与键集 | 丢弃：钉旧 JSON 键序，新 VO 由 OpenAPI 生成 |
| `SessionControllerPackagingTest` | 包内无编排类 | 改写为 ArchUnit 规则 `controllersAreThin`（Task 22 的 `MVN verify` 里生效，规则加进 `ArchitectureRulesTest`） |
| `QaRequestBindingTest` | camelCase 绑定与旧 snake 键静默失效 | 移植为 `QaRequestParserTest`，旧键不再静默失效，改为 400 |
| `QaSseOrchestratorTest` | error 加 done 为终态 | 改写为 `TurnStreamPumpTest` |
| `QaTurnExecutorNormalFailureTest`、`QaTurnFinalizerCommitGateTest`、`QaTurnFinalizerSnapshotTest`、`QaTurnFinalizerTenantContextTest` | 失败必须提交完成态、幂等提交、快照、租户补齐 | 移植为 `TurnFinalizerTest` 与 `QaTurnRunnerTest` |
| `rag/*RecordingTest` 7 个、`MergeRecordingTest` | 管线五件录制回放 | 改写为阶段级用例：`ChunkSearchStageTest`、`MergeStageTest`、`RerankStageTest`、`QueryUnderstandStageTest`、`IntoChatMessageStageTest`。金片丢弃，改为显式输入输出断言 |
| `SearchGradingTest` | 硬错与降级分级 | 移植为 `SearchFailureGraderTest` |
| `PluginChatCompletionStreamIdentityTest`、`...TerminalTest`、`...EndStreamTest` | 流消费身份与终态 | 移植为 `ModelStreamConsumerTest`（Task 10 的 `M/session/stream/ModelStreamConsumer.java`，测试类加进 Task 10 的 Files） |
| `AgentStreamBridgeTest` | SSE 载荷契约 | 改写为 `AgentEventBridgeTest`，断言事件映射，不断言字节 |
| `service/sse/StreamResponseBuilderTest` | 逐字节帧 | 丢弃：钉帧字节，帧由 `SseEmitter` 生成 |
| `MessageServiceChatHistoryIndexTest`、`MessageServiceVectorSearchTest` | 历史索引与向量检索 | 移植为 `MessageSearchServiceTest` |
| `SessionKnowledgeQaServiceAgentModeTest`、`SessionQaResolutionSearchTargetsTest`、`SessionQaResolutionOverridesTest`、`SessionKnowledgeQaKbScopeTest`、`SessionLookupScopeTest`、`SessionServiceReadTest`、`SessionServiceDeleteTest`、`TurnLifecycleTest`、`TurnTerminalMatrixTest`、`AgentToolBackendsKbToolTest` | 范围、可见性、终态矩阵、工具接线 | 前九项移植；`AgentToolBackendsKbToolTest` 改写为 `ToolCatalogTest` 与各工具工厂用例 |
| `attachment/*`、`controller/*`、`domain/message/ArtifactVersionsTest`、`service/TemporaryDocument*Test`、`service/VlmDescriberWiringTest`、`support/*`、`suggestion/*` | 附件、控制器错误映射、建议生成 | 移植，控制器断言改 MockMvc 加错误码 |

**agenttools（22 个）**

| 旧测试 | 处置 |
|---|---|
| `OutputBudgetsRecordingTest`、`ToolCapabilitiesRecordingTest`、`KnowledgeSearchRankingDedupTest`、`ListKnowledgeChunksRangeTest`、`KnowledgeSearchOutputFormatterTest` | 移植，金片改为内联期望值 |
| 各类 `*RecordingTest`（12 个）与 `ToolContractGoldenC`、`Tools45cFakes` | 改写：回放数据改为显式构造的 fake Provider 返回值；`ToolContractGoldenC` 与 `GoldenResources.get` 的 265 处调用丢弃，理由是需要 Go 版逐字节输出 |
| `McpStubABTest` | 丢弃：钉双端 JSON-RPC 字节与 agent prompt 金片，协议客户端已换成官方 SDK |
| `web/WebFetchToolTest`、`WebSearchToolTest`、`web/WebToolsRecordingTest` | 移植文案断言（入参错误、`empty_content`），字节形状丢弃 |
| `sql/DatabaseQueryRecordingTest` | 改写为 `DatabaseQueryToolTest` 与只读账号集成测试，见 Task 17 |
| `datasource/DataAnalysisRecordingTest` | 改写为 `DataAnalysisToolTest`，DuckDB 内存库，去掉 Excel 与 spatial 扩展的硬依赖 |

## 1.9 错误码

模块名称写 `ragagent`。对象不存在、参数校验、未登录一律用 `CommCode`。会话可见性错误统一 `1001018001`，不区分不存在与无权访问，避免探测。

**session 018**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001018001` session.not.found | 会话不存在、已删除或不在当前租户与属主范围内 | 会话不存在 | The session does not exist |
| ragagent | `1001018002` session.turn.running | 同一会话已有生成中的轮次又发起提问 | 该会话正在生成回答，请稍后再试 | A response is already being generated in this session |
| ragagent | `1001018003` session.query.empty | 提问内容为空 | 提问内容不能为空 | The question cannot be empty |
| ragagent | `1001018004` session.query.too.long | 提问或插话超过 10000 码点 | 提问内容过长 | The question is too long |
| ragagent | `1001018005` session.agent.required | 开启智能体模式但未指定智能体或智能体不可用 | 智能体不可用，请重新选择 | The agent is unavailable. Select another agent |
| ragagent | `1001018006` session.attachment.limit | 单次消息附件超过 5 个或单请求超过 100MB | 附件数量或大小超出限制 | Attachments exceed the allowed count or size |
| ragagent | `1001018007` session.attachment.type.unsupported | 智能体不支持该附件类型或未启用语音 | 该智能体不支持这种附件 | This agent does not support this attachment type |
| ragagent | `1001018008` session.image.unsupported | 智能体未启用图片理解却传入图片 | 该智能体未启用图片理解 | Image understanding is not enabled for this agent |
| ragagent | `1001018009` session.steer.queue.full | 排队插话超过 10 条 | 待发送消息已达上限 | The pending message queue is full |
| ragagent | `1001018010` session.steer.not.found | 提升或删除不存在的排队消息 | 待发送消息不存在 | The pending message does not exist |
| ragagent | `1001018011` session.stream.not.found | 续流时流事件为空 | 没有可续传的流数据 | No stream data to resume |
| ragagent | `1001018012` session.artifact.index.invalid | 产物下标非整数、负数或越界 | 产物编号无效 | The artifact index is invalid |
| ragagent | `1001018013` session.message.not.found | 消息不存在或不属于该会话 | 消息不存在 | The message does not exist |
| ragagent | `1001018014` session.title.generation.failed | 标题生成调模型失败 | 标题生成失败 | Failed to generate the title |
| ragagent | `1001018015` session.suggestion.not.found | 读取尚不存在的追问建议 | 追问建议尚未生成 | Suggestions have not been generated yet |

**agenttools 019**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001019001` tool.name.duplicate | 两个工具工厂声明同名工具，启动期校验 | 工具名称重复：{0} | Duplicate tool name: {0} |
| ragagent | `1001019002` tool.not.registered | 智能体允许清单里的工具没有任何工厂 | 工具未注册：{0} | Tool is not registered: {0} |
| ragagent | `1001019003` tool.scope.unavailable | 需要知识范围的工具在没有有效范围时被调用 | 没有可用的知识范围 | No effective knowledge scope is available |
| ragagent | `1001019004` tool.sql.readonly.denied | 数据库查询被只读角色拒绝 | 该查询需要写权限，不允许执行 | The query needs write access and is not allowed |
| ragagent | `1001019005` tool.sql.timeout | 数据库查询超过执行时限 | 查询执行超时 | The query timed out |
| ragagent | `1001019006` tool.build.failed | 工具构建时缺少必要依赖或配置 | 工具暂不可用：{0} | The tool is temporarily unavailable: {0} |

## 1.10 `@IgnoreTenant` 白名单

| 位置 | 原因 |
|---|---|
| `TemporaryDocumentCleanupJob.cleanExpired` | 周期任务跨租户扫描过期附件，属后台调度系统通道 |

`message_suggestion_*` 与 `messages` 都不需要豁免：消息写入时显式取会话租户，读取由行级安全兜底。附件解析任务从任务参数取租户，由 JobRunr 过滤器设置上下文，不用 `@IgnoreTenant`。

## 1.11 前端 API 层切换

形状栏“是”表示只改路径与解包。

**`frontend/src/api/chat/index.ts`**

| 函数 | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|
| `createSessions` | `POST /api/v1/sessions` | `POST /api/admin/v1/sessions` | 是 | `tenantId` 字符串转 number |
| `getSessionsList` | `GET /api/v1/sessions?page&pageSize&source` | 换前缀 | 是 | 来源参数改 `ownerType`，函数内部映射 |
| `getSession`、`updateSession`、`delSession` | `/api/v1/sessions/{id}` | 换前缀 | 是 | |
| `batchDelSessions`、`deleteAllSessions` | `DELETE /api/v1/sessions/batch` | `POST /api/admin/v1/sessions/batch-delete` | 是 | 请求体 `{ids}` 或 `{deleteAll:true}` 不变 |
| `clearSessionMessages` | `DELETE /api/v1/sessions/{id}/messages` | 换前缀 | 是 | |
| `pinSession`、`unpinSession` | `/pin` | 换前缀 | 是 | |
| `generateSessionsTitle` | `/generate_title` | `/title` | 是 | |
| `stopSession` | `/stop` | 换前缀 | 是 | |
| `knowledgeChat`、`agentChat` | `/api/v1/knowledge-chat/{sid}` | `/api/admin/v1/sessions/{sid}/knowledge-chat` | 帧格式变化 | 传输改 `createApiClient` 的流式方法，见 Task 14 |
| `getMessageList` | `/api/v1/messages/{sid}/load` | `/api/admin/v1/sessions/{sid}/messages` | 是 | |
| `listMessageArtifacts`、`listSessionArtifacts`、`downloadArtifact` | 产物三条 | 换前缀 | 是 | 下载仍走 blob |
| `listMessageFiles`（新增或用内部调用） | `/files` | 换前缀 | 是 | HEAD 由 GET 端点承接 |

**`frontend/src/api/chat/streame.ts`**：`useStream` 保持导出名与回调集合。续流 URL 由 `continue-stream/{sid}?messageId=` 改为 `sessions/{sid}/stream?messageId=`；`onopen` 与 `onmessage` 逻辑不变，由 `fetch-event-source` 按规范解析帧。

**`frontend/src/api/chat/steer.ts`**：四个函数换前缀，`SteerMutationResponse` 形状不变，`status` 取值不变。

**`frontend/src/api/chat/temporary-attachments.ts`**：四个函数换前缀，双 pattern 收敛为单路径变量，形状不变。

**`frontend/src/api/message-suggestion.ts`**：三个函数换前缀，形状不变。

**`frontend/src/api/chat-history.ts`**：`getChatHistoryKBStats` 改 `/api/admin/v1/messages/statistics`，`searchMessages` 改 `/api/admin/v1/messages/search`，形状不变。

## 1.12 e2e 域标签与冒烟

| 域标签 | 页面与流程 | 本计划责任 |
|---|---|---|
| `@session` | `/platform/creatChat`、`/platform/chat/:chatid`、对话历史设置、挂件对话 | 全部转绿 |
| `@agenttools` | 对话内的工具审批卡片 | 转绿 |
| `@channel` | 挂件首屏与嵌入渠道 | 首屏由计划 07 转绿；挂件访客对话经计划 07 的 `EmbedVisitorChatService` 调本计划的 `ChannelTurnProvider`，在本计划转绿。渠道管理页归计划 07 |

冒烟六条（计划 01 Task 15 钉的端点）在计划 08 转绿：登录、建知识库、上传文档、入库状态、控制台问答、挂件问答。前四条在计划 03 与 04 完成时转绿，本计划负责后两条并复核全六条。

## 1.13 路径缩写

`M` = `backend/app/src/main/java/com/ragagent`，`T` = `backend/app/src/test/java/com/ragagent`，`R` = `backend/app/src/main/resources`，`DB` = `R/db/migration/allenv`。脚本文件名的 `<D>` 是写脚本当天的 `yyyyMMdd`，序号取 `ls DB | grep <D>` 的下一个。

每个任务固定五步：写失败测试、跑红、实现、跑绿、提交。跑红与跑绿都用 `MU test -Dtest=<测试类>`，跑红预期 FAIL（类不存在或断言不成立），跑绿预期 PASS。提交前 `MU checkstyle:check` 无违规，提交后在 `docs/migration/progress.md` 追加一行。

# 2. 任务

## 批次 A：session 基座

### Task 1: 盘点核对、session DDL 与框架能力接入

**Files:**
- Modify: `backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（`jdbcTemplateAllowedOnlyInTenantAndRetrieval` 白名单追加 `com.ragagent.framework.sql..`）
- Create: `DB/V<D>_NN__DDL_rebuild_session_create_tables_jiangguifan.sql`
- Create: `T/session/{SessionRowLevelSecurityTest,SessionConstraintsTest}.java`

**Interfaces:**
- Consumes:
  - 计划 01 的 `TenantAwareJdbcTemplate`、`PostgresContainerTest`、`TenantProperties`、`deploy/db/init/10-roles.sql`、`@IgnoreTenant`
  - 计划 01 Task 11d 的 `com.ragagent.framework.stream`：`record StreamEvent(String id, ResponseType type, String content, boolean done, Map<String,Object> data, TokenUsage usage, OffsetDateTime at)`、`record StreamBatch(List<StreamEvent> events, int nextOffset)`
  - 计划 01 Task 11d 的 `interface StreamEventLog`：`void append(long sessionId, long messageId, StreamEvent event)`、`StreamBatch read(long sessionId, long messageId, int fromOffset)`、`void appendSteer(long sessionId, long messageId, List<StreamEvent> events)`、`StreamBatch readSteer(long sessionId, long messageId, int fromOffset)`、`boolean updateSteerData(long sessionId, long messageId, String eventId, Map<String,Object> data)`、`boolean deleteSteer(long sessionId, long messageId, String eventId)`、`void setLiveRun(long sessionId, long assistantMessageId, String requestId)`、`void claimLiveRun(long sessionId, long assistantMessageId, String requestId)`、`LiveRun getLiveRun(long sessionId)`、`void clearLiveRun(long sessionId, long assistantMessageId)`
  - 计划 01 Task 11d 的 `record LiveRun(long assistantMessageId, String requestId, OffsetDateTime startedAt)`，静态 `NONE`；配置项 `gac.furnace.stream.type`（`memory` 默认、`redis`）、`gac.furnace.stream.prefix`（默认 `stream:events`）、`gac.furnace.stream.ttl`（默认 1 小时）
  - 计划 01 Task 11d 的 `com.ragagent.framework.sql`：`interface ReadOnlySqlExecutor { List<Map<String, Object>> query(String sql, int maxRows); }`、`PostgresReadOnlySqlExecutor`，配置项 `gac.furnace.readonly-datasource.{url,username,password}`、`gac.furnace.sql.statement-timeout`（默认 5 秒）、`gac.furnace.sql.max-rows`（默认 2000）
  - 计划 01 Task 11d 的只读角色脚本；Task 11d 未含时由 Task 17 的 `deploy/db/readonly/README.md` 承接并登记偏离
- Produces: 1.3 的 5 张表，列、索引与写属主见表

- [x] **Step 1: 写失败测试**
  - 先读 `docs/migration/frozen-bugs.md` 中涉及 session 与 agenttools 的条目，逐条对照本计划 1.1 到 1.3 是否已覆盖，未覆盖的补进对应 Task。再核对 `find domains/src/main/java/com/ragagent/session -name '*.java' | wc -l` 为 212、`agenttools` 为 59，与 1.1 一致。
  - `SessionRowLevelSecurityTest`：`sessions`、`messages`、`temporary_documents`、`message_suggestion_sets`、`message_suggestion_events` 五张表各一对读取与插入的跨租户被拒测试。
  - `SessionConstraintsTest.suggestionSetIsUniquePerCacheKey`、`sessionOwnerTypeRejectsUnknownValue`、`messageSoftDeleteKeepsLiveRowUnique`。
- [x] **Step 2: 跑红**：`MU test -Dtest='SessionRowLevelSecurityTest,SessionConstraintsTest'`。
- [x] **Step 3: 实现**：DDL 按 1.3 建 5 张表，租户表 `ENABLE` 与 `FORCE` 行级安全，策略用计划 01 Task 5 模板。
- [x] **Step 4: 跑绿**；反向验证：对 `messages` 关闭行级安全，跨租户测试预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java DB T/session`，`git commit -m "feat(session): session 表与行级安全接入"`。

### Task 2: session 持久层

**Files:**
- Create: `M/session/entity/{Session,Message,TemporaryDocument,MessageSuggestionSet,MessageSuggestionEvent,OwnerType,MessageRole,DocumentStatus}.java`
- Create: `M/session/mapper/{SessionMapper,MessageMapper,TemporaryDocumentMapper,MessageSuggestionMapper}.java`
- Create: `R/mapper/session/{SessionMapper,MessageMapper,TemporaryDocumentMapper,MessageSuggestionMapper}.xml`
- Create: `M/session/repository/{SessionRepository,MessageRepository,TemporaryDocumentRepository,MessageSuggestionRepository,MessageArtifactReader}.java`
- Create: `T/session/{SessionRepositoryTest,MessageRepositoryTest,TemporaryDocumentRepositoryTest,MessageSuggestionRepositoryTest}.java`

**Interfaces:**
- Consumes: Task 1 的表与 `@IgnoreTenant`
- Produces:
  - `SessionRepository extends ServiceImpl<SessionMapper, Session>`：`Optional<Session> findVisible(long tenantId, String ownerType, String ownerId, long sessionId)`、`long countByOwner(long tenantId, String ownerType, String ownerId)`、`long softDelete(long tenantId, Collection<Long> sessionIds)`、`void updateLastRequestState(long tenantId, long sessionId, String json)`
  - `MessageRepository extends ServiceImpl<MessageMapper, Message>`：`List<Message> listBySession(long tenantId, long sessionId, int limit, OffsetDateTime before)`、`Optional<Message> findByRequestId(long tenantId, long sessionId, String requestId)`、`boolean completeOnce(long tenantId, long messageId, OffsetDateTime now)`（CAS 置 `is_completed`）
  - `TemporaryDocumentRepository`：`List<TemporaryDocument> listExpired(OffsetDateTime now, int limit)`（`@IgnoreTenant`）、`List<TemporaryDocument> claimPending(int limit)`
  - `MessageSuggestionRepository`：`Optional<MessageSuggestionSet> findLive(long tenantId, long assistantMessageId, String placement, String configHash, String locale)`、`boolean claimLease(long id, String leaseId, OffsetDateTime until)`、`int insertEvent(MessageSuggestionEvent event)`
- [x] **Step 1: 写失败测试**
  - `SessionRepositoryTest.softDeletedRowIsInvisible`、`lastRequestStateRoundTripsJsonb`、`ownerTypeAndOwnerIdFilterList`。
  - `MessageRepositoryTest.jsonbColumnsRoundTrip`、`completeOnceWinsExactlyOnce`、`listBySessionHonoursBeforeCursorAndLimit`。
  - `TemporaryDocumentRepositoryTest.claimPendingSkipsLockedRows`（两线程并发领取，同一条只被领一次）。
  - `MessageSuggestionRepositoryTest.leaseIsExclusive`、`eventInsertAppends`。
- [x] **Step 2: 跑红**：`MU test -Dtest='*RepositoryTest'`。
- [x] **Step 3: 实现**：jsonb 列用 Jackson `TypeHandler` 加 `autoResultMap`；`ON CONFLICT`、`FOR UPDATE SKIP LOCKED`、条件 `UPDATE` 写 XML；所有查询不带 `tenant_id` 条件，由插件注入。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/entity M/session/mapper M/session/repository R/mapper/session T/session`，`git commit -m "feat(session): 持久层"`。

### Task 3: session api 包、错误码与文案

**Files:**
- Create: `M/session/api/{SessionCode,SessionQueryApi}.java`
- Create: `M/session/bo/{SessionBo,SessionOwnerBo,QaTurnCommand,QaTurnContext,TurnStreamContext,TagScope,SearchTargetsBo,AttachmentRefBo,SteerCommandBo,EventSink}.java`。`TranscriptLineBo` 与 `TranscriptCursor` 由 memory 的 `memory.api` 或 `memory.bo` 声明，本计划只在 `SessionTranscriptReader` 的签名里引用，不另建（见 Task 20）
- Create: `M/session/service/{SessionQueryService,SessionConstants}.java`
- Create: `M/config/SessionAssemblyConfig.java`
- Modify: `R/i18n/app-messages*.properties`（15 个 session 错误码）
- Create: `T/session/{SessionCodeMessagesTest,SessionQueryServiceTest}.java`

**Interfaces:**
- Produces:
  - `SessionCode` 15 个枚举对应 1.9，`CodeCache.register(SessionCode.values())` 放 `com.ragagent.config.SessionAssemblyConfig`
  - `SessionQueryService implements SessionQueryApi`：`Optional<SessionBo> findOwned(long tenantId, String ownerId, long sessionId)`、`boolean exists(long tenantId, long sessionId)`
  - `record QaTurnCommand(long sessionId, String query, boolean agentEnabled, Long agentId, List<Long> knowledgeBaseIds, List<Long> knowledgeIds, List<TagScope> tagScopes, List<AttachmentRefBo> attachments, boolean webSearchEnabled, String channel, String suggestionAttribution)`。五个入口共用这一个 record，入口差异只在 `channel` 与 `attachments`。
  - `record SearchTargetsBo(List<KnowledgeScopeBo> scopes)`（`KnowledgeScopeBo` 来自 `knowledge.api`）。`knowledge.api` 若未提供 `SearchTargetsBo`，本计划在 `session.bo` 声明它，并以本计划为唯一声明方；agenttools 经 `ToolBuildContext` 消费，不重复声明（见“对外承诺”）。
  - `EventSink` 声明在本计划 `session.bo`：`void emit(String type, Map<String, Object> data)`，用于无 SSE 的入口（渠道、steer 后续轮次）接收与 SSE 同构的事件，实现由 Task 11 的 `HeadlessEventSink` 落在 `StreamEventLog`
  - `SessionConstants`：`MAX_ATTACHMENTS_PER_MESSAGE = 5`、`MAX_STEER_QUEUE_DEPTH = 10`、`MAX_STEER_QUERY_CODE_POINTS = 10000`、`STEER_DRAIN_BATCH_LIMIT = 20`、`STOP_FALLBACK_MILLIS = 3000`、`STREAM_POLL_MILLIS = 100`、`HEADLESS_POLL_MILLIS = 300`、`MAX_STREAM_MILLIS = 7_200_000`、`MAX_TITLE_CODE_POINTS = 100`
- [x] **Step 1: 写失败测试**
  - `SessionCodeMessagesTest.everyCodeHasMessageInSevenLanguages`。
  - `SessionQueryServiceTest.crossTenantLookupReturnsEmpty`、`softDeletedSessionIsNotFound`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：文案 key `code_1001018xxx`，七语种加默认文件；其余六种语言标注待人工校对。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/api M/session/bo M/session/service/SessionQueryService.java M/session/service/SessionConstants.java M/config/SessionAssemblyConfig.java R/i18n T/session`，`git commit -m "feat(session): api 包、错误码与文案"`。

### Task 4: 会话用例与标题生成

**Files:**
- Create: `M/session/service/{SessionService,SessionReadService,SessionTitleService,SessionStopService}.java`
- Create: `T/session/{SessionServiceTest,SessionReadServiceTest,SessionTitleServiceTest,SessionStopServiceTest}.java`

**Interfaces:**
- Consumes: Task 2 的 Repository、Task 3 的 `SessionCode`、`model.api` 与 `LlmChatClient`、计划 01 Task 11d 的 `StreamEventLog`
- Produces:
  - `SessionService.create(SessionBo)` 返回 `SessionBo`；`rename(long sessionId, String title, String description)`；`pin(long sessionId, boolean pinned)`；`delete(long sessionId)`；`batchDelete(Collection<Long> ids, boolean deleteAll)`；`clearMessages(long sessionId)`
  - `SessionReadService.page(String keyword, OwnerType ownerType, int page, int pageSize)` 返回 `PageData<SessionBo>`；`load(long sessionId)` 返回会话加最近 N 条消息
  - `SessionTitleService.generateAsync(SessionBo session, String userQuery, long modelId, Consumer<String> onTitle)`：虚拟线程，租户快照补齐身份，标题经 `sanitizeTitle`（剥 `<think>` 前缀、Unicode 空白 trim、100 码点截断）
  - `SessionStopService.stop(long sessionId, long messageId)`：校验消息属于会话，往 `StreamEventLog` 追加 `STOP` 事件
- [x] **Step 1: 写失败测试**
  - `SessionServiceTest.deleteIsSoftAndIdempotent`、`batchDeleteWithoutIdsFails`、`clearMessagesKeepsSession`。
  - `SessionReadServiceTest.listReturnsOnlyCallersSessions`、`keywordMatchesTitle`。
  - `SessionTitleServiceTest.sanitizesThinkPrefixAndTruncatesTo100CodePoints`、`blankTitleIsNotWritten`、`keepsTenantContextInsideVirtualThread`。
  - `SessionStopServiceTest.rejectsMessageFromAnotherSession`、`appendsStopEventOnce`。
- [x] **Step 2: 跑红**：`MU test -Dtest='Session*Test'`。
- [x] **Step 3: 实现**：可见性判定集中在 `SessionReadService.requireVisible`，不存在与无权都抛 `SessionCode.SESSION_NOT_FOUND`。标题生成的模型调用在事务外。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/service/SessionService.java M/session/service/SessionReadService.java M/session/service/SessionTitleService.java M/session/service/SessionStopService.java T/session`，`git commit -m "feat(session): 会话、读、标题与停止用例"`。

### Task 5: 消息与历史检索

**Files:**
- Create: `M/session/service/{MessageService,MessageSearchService,ReciprocalRankFusion}.java`
- Create: `M/session/service/ConversationSearchService.java`
- Create: `T/session/{MessageServiceTest,MessageSearchServiceTest,ReciprocalRankFusionTest,ConversationSearchServiceTest}.java`

**Interfaces:**
- Consumes: Task 2 的 `MessageRepository`、`MessageArtifactReader`；`retrieval.api.HybridSearchApi`；本域 `ConversationSearchProvider`
- Produces:
  - `MessageService.createUserTurn`、`createAssistantTurn`（同一事务双行）、`appendContent`、`delete`、`list`、`artifactsOf`
  - `MessageSearchService.search(String query, SearchMode mode, int limit, Long sessionId)` 返回分组结果；`statistics()` 返回按知识库聚合的历史统计
  - `ConversationSearchService implements ConversationSearchProvider.search(ConversationSearchQueryBo)`
  - `ReciprocalRankFusion.fuse(List<List<String>> rankedIds, int k)`，`k` 默认 60
- [x] **Step 1: 写失败测试**
  - `MessageServiceTest.createsUserAndAssistantInOneTransaction`、`deleteHidesRowFromLists`。
  - `MessageSearchServiceTest.hybridFusesKeywordAndVector`、`restrictsToOwnedSessions`、`stripsThinkFromIndexedText`。
  - `ReciprocalRankFusionTest.ranksItemsAppearingInBothListsFirst`、`kOfSixtyMatchesLegacyOrdering`。
  - `ConversationSearchServiceTest.excludesCurrentSession`、`returnsOwnerScopedExchanges`。
- [x] **Step 2: 跑红**：`MU test -Dtest='Message*Test,ReciprocalRankFusionTest,ConversationSearchServiceTest'`。
- [x] **Step 3: 实现**：向量检索走 `HybridSearchApi`，关键词走 `MessageMapper` 的 XML，融合按名次不按分数。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/service/MessageService.java M/session/service/MessageSearchService.java M/session/service/ReciprocalRankFusion.java M/session/service/ConversationSearchService.java T/session`，`git commit -m "feat(session): 消息与历史检索"`。

### Task 6: steer 会话侧与引擎 spi

**Files:**
- Create: `M/session/service/steer/{SteerService,SteerInbox,TurnSteerSink}.java`
- Create: `T/session/{SteerServiceTest,SteerInboxTest,TurnSteerSinkTest}.java`

**Interfaces:**
- Consumes: 计划 01 Task 11d 的 `StreamEventLog`、计划 02 的 `agentengine.spi.SteerSink` 与 `agentengine.spi.SteerMessage`、`mcp.api.ApprovalResolutionListener` 的语义务（steer 是审批恢复的注入通道）
- Produces:
  - `SteerService.queue(SteerCommandBo command)` 返回 `SteerMutationResponse`（`status` 取 `queued`、`already_injected`、`new_run`、`gone`）；`promote`、`list`、`remove`
  - `SteerInbox`：`List<SteerMessageBo> drain(int limit)`，按 `injectedIds` 过滤，不重复注入
  - `TurnSteerSink implements SteerSink`，构造参数为 `(String sessionId, String messageId, SteerInbox inbox, MessageService messageService, TenantSnapshot tenant)`：
    - `List<SteerMessage> poll(String sessionId, String messageId, int lastOffset)`：在构造期捕获的租户快照内调用 `SteerInbox.drain`，返回未注入条目，`lastOffset` 不参与跳过判断（消费标记挂在事件 data 上，与旧 `SteerSinkBridge` 一致）
    - `String persist(SteerMessage message)`：把接受的消息落成该轮 requestId 下的 user 行，返回新行 ID；落库失败或空 ID 时返回空串，调用方不得把文本并入消息
- [x] **Step 1: 写失败测试**
  - `SteerServiceTest.rejectsQueueWhenFull`、`promoteMarksInject`、`removeDeletesEntry`、`noLiveRunReturnsNewRun`、`liveRunLookupFailureReturnsError`。
  - `SteerInboxTest.drainsUpToBatchLimit`、`doesNotReplayInjectedMessages`、`appendsUserMessageInjectedEvent`。
  - `TurnSteerSinkTest.pollUsesCapturedTenantSnapshot`、`pollSkipsInjectedRegardlessOfOffset`、`persistReturnsEmptyStringOnFailure`。
- [x] **Step 2: 跑红**：`MU test -Dtest='Steer*Test,TurnSteerSinkTest'`。
- [x] **Step 3: 实现**：队列条目放 `StreamEventLog` 的 steer 子列表；`consumed` 标记写在事件 data 上，跨副本一致。`query` 长度按码点计。`TurnSteerSink` 每轮创建一个实例，经 `AgentEngineHandle.setSteerSink` 交给引擎，Bean 不单例（引擎持有的是实例而非 Bean）。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/service/steer T/session`，`git commit -m "feat(session): steer 排队、排空与引擎 SteerSink"`。

### Task 7: 附件与临时文档

**Files:**
- Create: `M/session/attachment/{TemporaryDocumentService,TemporaryDocumentProcessor,TemporaryDocumentCleanupJob,AttachmentFileStore,TemporaryDocumentPromptResolver}.java`
- Create: `M/session/attachment/AttachmentKind.java`
- Create: `T/session/{TemporaryDocumentServiceTest,TemporaryDocumentCleanupJobTest,AttachmentPromptResolverTest}.java`

**Interfaces:**
- Consumes: Task 2 的 `TemporaryDocumentRepository`、`storage.api` 的落盘与签名、`agent.api.AgentQueryApi` 取附件类型门控、JobRunr 的 `JobScheduler`
- Produces:
  - `TemporaryDocumentService.upload(...)` 返回 `AttachmentBo`，校验 5 个上限、文件类型、语音与图片开关
  - `TemporaryDocumentProcessor.parse(long documentId)`：解析、分块、图片落盘、状态推进
  - `TemporaryDocumentCleanupJob.cleanExpired`：周期 10 分钟，每批 100，带 `@IgnoreTenant`
  - `TemporaryDocumentPromptResolver.resolve(List<AttachmentRefBo>)` 返回本轮提示词片段
- [x] **Step 1: 写失败测试**
  - `TemporaryDocumentServiceTest.rejectsSixthAttachment`、`rejectsTypeNotAllowedByAgent`、`rejectsAudioWithoutAsrModel`、`uploadReturnsUploadedStatus`。
  - `TemporaryDocumentCleanupJobTest.deletesExpiredRowsInBatches`、`scansAcrossTenants`。
  - `AttachmentPromptResolverTest.rendersTextAndImageSectionsInOrder`、`audioUsesTranscribedText`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：上传受理后投递 JobRunr 任务，任务参数带租户与文档 ID；清理任务走系统通道。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/attachment T/session`，`git commit -m "feat(session): 临时附件上传、解析与清理"`。

### Task 8: 追问建议

**Files:**
- Create: `M/session/service/suggestion/{MessageSuggestionService,MessageSuggestionGenerator,MessageSuggestionCache}.java`
- Create: `T/session/{MessageSuggestionServiceTest,MessageSuggestionGeneratorTest}.java`

**Interfaces:**
- Consumes: Task 2 的 `MessageSuggestionRepository`；`agent.api.AgentQueryApi.suggestedQuestions`；`knowledge.api` 的 FAQ 与生成问题；`LlmChatClient`
- Produces:
  - `MessageSuggestionService.ensure(long sessionId, long messageId, boolean regenerate)` 返回建议集；`get`、`recordEvent`
  - `MessageSuggestionGenerator.generate(SuggestionContext)` 返回问题列表，三种模式：知识、生成、混合
- [x] **Step 1: 写失败测试**
  - `MessageSuggestionServiceTest.ensureIsIdempotentWithinLease`、`suppressedWhenAgentDisallows`、`regenerateBypassesCache`、`eventTypesAreImpressionClickDismiss`。
  - `MessageSuggestionGeneratorTest.knowledgeModeUsesFaqAndDocument`、`generationModeUsesModelPrompt`、`capsHistoryBudget`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：租约用 `claimLease` 条件更新，抢不到时返回 `generating` 状态；生成在事务外。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/service/suggestion T/session`，`git commit -m "feat(session): 追问建议"`。

## 批次 B：问答编排与 SSE

### Task 9: NORMAL 管线阶段

**Files:**
- Create: `M/session/pipeline/{Pipeline,PipelineStage,ChatSettings,PipelineExecutor,RetrievalPlanBuilder}.java`
- Create: `M/session/pipeline/stage/{LoadHistoryStage,MemoryRecallStage,QueryUnderstandStage,ChunkSearchStage,QueryExpansion,SearchTargetExecutor,RerankStage,RerankScoring,PassageEnricher,WebFetchStage,MergeStage,ParentChunkResolver,FaqAnswerAppender,SequentialChunkMerger,FilterTopKStage,DataAnalysisStage,IntoChatMessageStage,ImageDescriber,ChatCompletionStage}.java`
- Create: `M/session/service/{QuestionAnswerService,KnowledgeScopeResolver,SearchFailureGrader}.java`
- Create: `T/session/{PipelineTest,RetrievalPlanBuilderTest,KnowledgeScopeResolverTest,SearchFailureGraderTest,RerankScoringTest,MergeStageTest,IntoChatMessageStageTest}.java`

**Interfaces:**
- Consumes: 计划 02 的 `retrieval.api.HybridSearchApi`、`modelruntime.api.LlmChatClient`、`ChatStream`、`Reranker`；计划 04 的 `knowledge.api`；`memory.api`；Task 3 的 `SearchTargetsBo`
- Produces:
  - `interface PipelineStage { String name(); void run(PipelineContext context); }`，`Pipeline` 按名称序列执行
  - `RetrievalPlanBuilder.plan(boolean needsRag, boolean needsWeb, boolean needsAnalysis)` 返回阶段名列表，顺序与 1.4 的阶段序列一致
  - `ChatSettings` record，字段取旧 `ChatManage` 的检索、模型、生成、网页四组参数，`conversation.*` 配置提供默认值
  - `QuestionAnswerService.answer(QaTurnContext context, EventSink sink)`：NORMAL 用例入口
  - `SearchFailureGrader.grade(RuntimeException e)` 返回 `HARD` 或 `DEGRADED`
- [x] **Step 1: 写失败测试**
  - `PipelineTest.runsStagesInDeclaredOrder`、`stopsAtFirstHardFailure`、`degradedFailureContinues`。
  - `RetrievalPlanBuilderTest.pureChatSkipsRetrievalStages`、`ragRunsFullSequence`、`webAndAnalysisAreOptional`。
  - `KnowledgeScopeResolverTest.sharedAgentUsesAgentTenant`、`tagScopesExpandToKnowledgeIds`。
  - `RerankScoringTest.mmrPenalisesSimilarChunks`、`compositeScoreWeightsRerankAboveVector`。
  - `MergeStageTest.dedupKeepsHighestScore`、`parentChunksReplaceChildren`、`faqAnswerAppendsUnderChunk`、`sequentialChunksMergeInOrder`。
  - `IntoChatMessageStageTest.mediaSectionListsTypesOnce`。
- [x] **Step 2: 跑红**：`MU test -Dtest='PipelineTest,RetrievalPlanBuilderTest,KnowledgeScopeResolverTest,SearchFailureGraderTest,RerankScoringTest,MergeStageTest,IntoChatMessageStageTest'`。
- [x] **Step 3: 实现**：阶段之间只传 `PipelineContext`（本轮检索结果、引用、进度）；并发只在检索与网页抓取两处，用 `PipelineExecutor` 的统一上限 16。事务边界只在 `QuestionAnswerService` 外层，阶段内无事务。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/pipeline M/session/service/QuestionAnswerService.java M/session/service/KnowledgeScopeResolver.java M/session/service/SearchFailureGrader.java T/session`，`git commit -m "feat(session): NORMAL 管线与阶段"`。

### Task 10: SSE 通道

**Files:**
- Create: `M/session/stream/{SseEmitterFactory,StreamEventEmitter,StreamResponseAssembler,TurnStreamPump,AgentEventBridge,AssistantMessageAccumulator,ToolPayloadSanitizer}.java`
- Create: `M/session/stream/ModelStreamConsumer.java`
- Create: `T/session/{SseEmitterFactoryTest,StreamEventEmitterTest,TurnStreamPumpTest,AgentEventBridgeTest,AssistantMessageAccumulatorTest,JacksonEscapingTest,ModelStreamConsumerTest}.java`

**Interfaces:**
- Consumes: 计划 01 Task 11d 的 `StreamEventLog`；计划 02 的 `agentengine.api.AgentEvent`、`AgentEventType` 与 `agentengine.spi.AgentEventSink`；`SseEmitter`
- Produces:
  - `SseEmitterFactory.create()`：构造时统一配置超时（默认 2 小时，配置项 `gac.furnace.stream.sse-timeout`），注册 `onCompletion`、`onError`、`onTimeout` 三个回调，回调里置 `clientGone` 并让泵停止写
  - `TurnStreamPump.pump(long sessionId, long assistantMessageId, String requestId, SseEmitter emitter, TurnStreamContext context)`：独立虚拟线程，串行 `send`，按 `STREAM_POLL_MILLIS` 增量读；写失败则降级无头，按 `HEADLESS_POLL_MILLIS` 继续读到终态
  - `AgentEventBridge implements AgentEventSink`：`void emit(AgentEvent event)` 按 16 类 `AgentEventType` 映射到 `ResponseType`，映射表与 1.4 的事件映射表逐项一致，映射结果 append 进 `StreamEventLog`。每轮一个实例，构造参数 `(long sessionId, long assistantMessageId, String requestId, StreamEventLog log, AssistantMessageAccumulator accumulator, ToolPayloadSanitizer sanitizer)`，不注册为单例 Bean
  - `AssistantMessageAccumulator`：累积正文、reasoning、步骤、用量，供终态落库
- [x] **Step 1: 写失败测试**
  - `SseEmitterFactoryTest.setsTimeoutAndRegistersThreeCallbacks`、`completionCallbackMarksClientGone`。
  - `StreamEventEmitterTest.holdsBackDeltaContentUntilTerminal`、`flushesHeldContentBeforeComplete`、`sendsEachEventOnce`。
  - `TurnStreamPumpTest.stopsOnCompleteEvent`、`emitsStopEventOnceOnStop`、`degradesToHeadlessAfterWriteFailure`、`exitsAtMaxDuration`。
  - `AgentEventBridgeTest.mapsAllSixteenEventTypes`、`errorFramePrecedesTerminalFrame`、`emitAppendsExactlyOneEventPerAgentEvent`、`implementsAgentEventSink`（断言 `AgentEventSink.class.isAssignableFrom(AgentEventBridge.class)`）。
  - `AssistantMessageAccumulatorTest.concatenatesAnswerChunksInOrder`、`keepsReasoningSeparate`。
  - `ModelStreamConsumerTest.keepsIdentityAcrossReconnects`、`stopsAtTerminalFrames`、`closesUnderlyingStream`。
  - `JacksonEscapingTest.angleBracketsAndAmpersandsAreNotEscaped`：应用 `ObjectMapper` 序列化含 `<script>&` 的字符串，断言输出里有字面的尖括号与 `&`，且不出现 `<`、`>`、`&` 三种转义序列。
- [x] **Step 2: 跑红**：`MU test -Dtest='SseEmitterFactoryTest,StreamEventEmitterTest,TurnStreamPumpTest,AgentEventBridgeTest,AssistantMessageAccumulatorTest,JacksonEscapingTest,ModelStreamConsumerTest'`。
- [x] **Step 3: 实现**
  - 帧由 `SseEmitter.send(SseEmitter.event().name("message").data(payload))` 生成，内容用新 `ObjectMapper`，**不配置 `CharacterEscapes`**，不引入 HTML 转义。本任务同时加 `T/session/JacksonEscapingTest.angleBracketsAndAmpersandsAreNotEscaped`，断言 `< > &` 原样输出。
  - `event:message` 的事件名与旧帧一致，前端 `fetch-event-source` 不依赖冒号后的空格。
  - 删除 `SseFrameWriter`、`ServletSseSink`、`SseFrameSink`、`SseContract` 的新实现；旧类只在旧目录保留，不改。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/stream T/session`，`git commit -m "feat(session): SseEmitter 流通道与事件桥"`。

### Task 11: 问答用例编排与终态

**Files:**
- Create: `M/session/service/qa/{QaTurnService,QaTurnRunner,QaRequestParser,LiveRunGate,TurnFinalizer,TurnLifecycle,TurnMessageGate,HeadlessEventSink}.java`
- Create: `T/session/{QaTurnServiceTest,QaRequestParserTest,QaTurnRunnerTest,LiveRunGateTest,TurnFinalizerTest,MultiEntryQaTest}.java`

**Interfaces:**
- Consumes: Task 3 的 `EventSink`；Task 4 到 Task 10 全部产物
- Produces:
  - `QaTurnService.execute(QaTurnCommand command, SseEmitter emitter)`：校验、落库双行、写 `agentQuery` 帧、起泵、起执行体
  - `QaTurnService.executeHeadless(QaTurnCommand command, EventSink sink)`：渠道与 steer 后续轮次用，无 emitter
  - `QaTurnRunner.run(QaTurnContext context)`：解析附件、选管线、调 `QuestionAnswerService` 或 `AgentTurnService`、finally 里走终态
  - `TurnFinalizer.complete(QaTurnContext context, TurnOutcome outcome)`：CAS 幂等，赢家异步索引历史、生成建议、调度记忆蒸馏
  - `LiveRunGate.acquire(long sessionId, long assistantMessageId, String requestId)`：冲突抛 `SessionCode.SESSION_TURN_RUNNING`
  - `HeadlessEventSink implements EventSink`：构造参数 `(long sessionId, long assistantMessageId, String requestId, StreamEventLog log)`，`emit` 把事件 append 进 `StreamEventLog`，与 SSE 泵读同一个日志，保证无头入口的事件序列与 SSE 入口同构
- [x] **Step 1: 写失败测试**
  - `QaRequestParserTest.rejectsBlankQuery`、`rejectsQueryOverLimit`、`requiresAgentWhenAgentMode`、`rejectsSixAttachments`、`rejectsImageWhenAgentDisablesVision`。
  - `LiveRunGateTest.secondTurnIsRejectedWithConflict`、`sameAssistantIsIdempotent`、`releaseOnlyWhenStillOwner`。
  - `QaTurnRunnerTest.failureStillCommitsTerminalState`、`cancelThenLateSuccessKeepsCancelled`、`restoresTenantContextInsideVirtualThread`。
  - `TurnFinalizerTest.commitIsIdempotent`、`winnerTriggersIndexSuggestionAndMemoryOnce`。
  - `HeadlessEventSink` 的断言并入 `QaTurnServiceTest.headlessWritesSameEventSequenceAsSse`：同一输入分别走 `execute` 与 `executeHeadless`，断言 `StreamEventLog` 里两串事件类型序列一致。
  - `MultiEntryQaTest`：五个入口各调一次，断言最终落到同一 `QaTurnService.execute` 或 `executeHeadless`，且 `messages` 每个入口各写一对 user 与 assistant 行、终态只提交一次。落点：控制台走 `QaController`（admin 前缀，`execute`），挂件与渠道走 `ChannelTurnProvider.startTurn`（`executeHeadless`），开放接口（API Key 集成）走同一 `QaController` 方法（`execute`，前缀待 Task 13 定），Agent 工具 `search_conversations` 是只读历史、不新开轮次，断言它不写消息行。这是路线图 3.9 第 3 条要求的合并用例，用例写在 session 域，不在各入口域重复。
- [x] **Step 2: 跑红**：`MU test -Dtest='QaTurn*Test,QaRequestParserTest,LiveRunGateTest,TurnFinalizerTest,MultiEntryQaTest'`。
- [x] **Step 3: 实现**：`QaTurnRunner` 在 `finally` 里调用 `TurnFinalizer`，`LiveRunGate` 在 `finally` 里释放；`SseEmitter` 的三个回调只置位，不直接写库。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/service/qa T/session`，`git commit -m "feat(session): 问答编排、并发守卫与终态"`。

### Task 12: Agent 模式装配

**Files:**
- Create: `M/session/service/agent/{AgentTurnService,AgentTurnAssembler,AgentToolSelector,AgentWebPageStore}.java`
- Create: `T/session/{AgentTurnServiceTest,AgentToolSelectorTest,AgentTurnAssemblerTest}.java`

**Interfaces:**
- Consumes: 计划 02 的 `agentengine.api.AgentEngineFactory`、`AgentEngineRequest`、`AgentEngineHandle`、`AgentTool`；计划 06 的 `agent.api.AgentQueryApi`、`memory.api`；Task 3 的 `EventSink`；`agenttools.api.ToolBuildContext`、`agenttools.ToolCatalog`
- Produces:
  - `AgentTurnService.answer(QaTurnContext context, EventSink sink)`：AGENT 用例入口，返回 `TurnLifecycle`。内部把 `AgentEventBridge` 作为 `AgentEventSink` 交给 `AgentEngineFactory.create`，把 `TurnSteerSink` 经 `AgentEngineHandle.setSteerSink` 交给引擎
  - `AgentTurnAssembler.assemble(QaTurnContext context)`：模型面、历史、技能、MCP、记忆召回、runtimeContext 块
  - `AgentToolSelector.select(List<String> allowedTools, boolean hasKnowledgeScope, boolean hasWikiKb, boolean hasVectorKb, boolean webEnabled, boolean memoryAvailable)` 返回最终工具名列表
- [x] **Step 1: 写失败测试**
  - `AgentToolSelectorTest.filtersKnowledgeToolsWithoutScope`、`filtersWikiToolsWithoutWikiKb`、`webToolsFollowRuntimeSwitch`、`memoryToolFollowsAvailability`、`keepsOrderAfterFiltering`。
  - `AgentTurnServiceTest.usesAgentTenantForSharedAgent`、`requiresAgentConfig`、`injectsSteerDrainAtRoundBoundary`。
  - `AgentTurnAssemblerTest.buildsRuntimeContextWithLocalAndUtcTime`、`replacesOldToolResultsWithOmissionNote`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：工具实例从 `ToolCatalog` 取，装配期不再有 switch；MCP 目录由 mcp 适配层注入引擎，session 不直接构造 MCP 工具。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/session/service/agent T/session`，`git commit -m "feat(session): Agent 模式装配与工具选择"`。

### Task 13: 控制器、VO 与 OpenAPI

**Files:**
- Create: `M/session/controller/{QaController,SessionController,SessionStreamController,MessageController,SteerController,AttachmentController,SessionArtifactController,MessageFileController,MessageSuggestionController}.java`
- Create: `M/session/vo/`（按 1.11 的请求与响应，含 `KnowledgeChatReqVo`、`SessionRespVo`、`SessionListQueryVo`、`SteerQueueItemVo`、`MessageRespVo`、`SuggestionRespVo`、`AttachmentRespVo`、`ArtifactRespVo`）
- Create: `M/session/assembler/`（MapStruct：`SessionAssembler`、`MessageAssembler`、`AttachmentAssembler`、`SuggestionAssembler`）
- Create: `T/session/{QaControllerTest,SessionControllerTest,SessionStreamControllerTest,SteerControllerTest,AttachmentControllerTest,MessageSuggestionControllerTest}.java`
- Modify: `frontend/packages/api-client/src/schema.d.ts`（`npm run gen:api` 产物）

**Interfaces:**
- Consumes: Task 11 到 Task 12 的 Service
- Produces: 1.2 的路径全部落地；两个前缀问答端点由同一方法映射；同名资源不重复实现
- [x] **Step 1: 写失败测试**
  - `QaControllerTest.adminAndVisitorPrefixesHitSameHandler`、`blankQueryReturns4400`、`runningTurnReturns409WithSessionCode`、`sseResponseHasEventStreamContentType`。
  - `SessionControllerTest.invisibleSessionReturnsSessionNotFoundCode`、`batchDeleteWithoutIdsReturns4400`。
  - `SessionStreamControllerTest.unknownMessageReturnsStreamNotFound`、`resumesFromOffsetZero`。
  - `SteerControllerTest.queueFullReturnsSessionCode`、`promoteMissingReturnsSteerNotFound`。
  - `AttachmentControllerTest.sixthAttachmentReturnsLimitCode`、`previewStreamsBytes`。
  - `MessageSuggestionControllerTest.unknownMessageReturnsMessageNotFound`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：控制器只做绑定、`@Valid`、调一个 Service；流式方法返回 `SseEmitter`，方法体只建 emitter 并交给 `QaTurnService`。四个 SSE 头由 `SseEmitterFactory` 设（`Cache-Control: no-cache`、`Connection: keep-alive`、`X-Accel-Buffering: no`，`Content-Type` 由 emitter 自己给）。
- [x] **Step 4: 跑绿**，再跑 `MU test -Dtest=OpenApiExportTest` 并 `cd frontend/packages/api-client && npm run gen:api`。
- [x] **Step 5: 提交**：`git add M/session/controller M/session/vo M/session/assembler T/session frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(session): 控制器、VO 与 OpenAPI"`。

### Task 14: 前端 API 层与 session 界面验收

**Files:**
- Modify: `frontend/src/api/chat/{index.ts,streame.ts,steer.ts,temporary-attachments.ts}`
- Modify: `frontend/src/api/{message-suggestion.ts,chat-history.ts}`
- Create: `frontend/src/api/chat/{streame.sse.test.ts,chat.test.ts}`
- Modify: `tests/e2e/PAGES.md`

**Interfaces:**
- Consumes: Task 13 生成的类型
- Produces: 1.11 的切换表全部落地；`streame.ts` 用 `fetch-event-source` 解析，验证 `event:message` 与 `data:` 多字段组合
- [x] **Step 1: 写失败测试**
  - `streame.sse.test.ts`：起一个本地 SSE 桩，依次发 `event:message` 两帧、一条注释行、一条空行，断言 `onmessage` 收到两帧且顺序正确、注释行被忽略。
  - `chat.test.ts`：断言 `useStream` 的续流 URL 已改为 `sessions/{id}/stream`。
  - `cd tests/e2e && npx playwright test --grep '@session'`，当前预期失败（前端仍指向旧路径）。`@session` 的用例文件由计划 01 Task 13 录制，本任务只切 API 层，不改用例。
- [x] **Step 2: 跑红**：`cd frontend && npx vitest run src/api/chat`。
- [x] **Step 3: 实现**：按 1.11 改函数，`tenantId` 转 number，错误走 `ApiError`；`useStream` 导出名与回调不变。
- [x] **Step 4: 跑绿**：`npx vitest run src/api/chat`、`npx vue-tsc --noEmit`、`npx playwright test --grep '@session'` 全绿。
- [x] **Step 5: 提交**：`git add frontend/src/api/chat frontend/src/api/message-suggestion.ts frontend/src/api/chat-history.ts tests/e2e/PAGES.md`，`git commit -m "feat(session): 前端 API 层切换与流式解析验证"`。

### Task 14b: 挂件与渠道入口前端

**Files:**
- Modify: `frontend/src/api/embed/`（挂件侧问答与续流入口）
- Create: `frontend/src/api/embed/embed-chat.test.ts`

**Interfaces:**
- Consumes: Task 13 的 `/api/v1/sessions/{id}/knowledge-chat`、`/agent-chat`、`/stream`
- Produces: 挂件（`/api/v1`）与控制台（`/api/admin/v1`）共用同一个 `useStream`，只有基址不同
- [x] **Step 1: 写失败测试**：`embed-chat.test.ts.chatUrlUsesVisitorPrefix`、`streamUrlUsesMessageIdQuery`。
- [x] **Step 2: 跑红**：`cd frontend && npx vitest run src/api/embed`。
- [x] **Step 3: 实现**：按任务要求的前缀与路径改，认证头 `Embed` 前缀与 `X-Embed-Session`、`X-Embed-Visitor` 头保持不变。
- [x] **Step 4: 跑绿**：`npx vitest run src/api/embed` 与 `npx vue-tsc --noEmit`。
- [x] **Step 5: 提交**：`git add frontend/src/api/embed`，`git commit -m "feat(session): 挂件问答入口切换"`。

## 批次 C：agenttools

### Task 15: 工具登记表与 spi

**Files:**
- Create: `M/agenttools/api/{AgentToolFactory,ToolBuildContext,AgentToolCode}.java`
- Create: `M/agenttools/{ToolCatalog,AgentToolSelector}.java`
- Create: `M/config/AgentToolAssemblyConfig.java`
- Modify: `R/i18n/app-messages*.properties`（6 个 agenttools 错误码）
- Create: `T/agenttools/{ToolCatalogTest,AgentToolCodeMessagesTest,AgentToolSelectorTest}.java`

**Interfaces:**
- Consumes: 计划 02 的 `agentengine.api.AgentTool`、`ToolDefinitions`；计划 03 的子任务（无）
- Produces:
  - `AgentToolFactory` 四方法（见“对外承诺”）
  - `ToolCatalog`：构造注入 `List<AgentToolFactory>`，同名抛 `AgentToolCode.TOOL_NAME_DUPLICATE`；`Optional<AgentToolFactory> find(String toolName)`、`Set<String> defaultAllowed()`、`boolean isConcurrencySafe(String toolName)`
  - `AgentToolSelector`：从 `ToolCatalog` 取默认集合并按运行时开关裁剪，输出名列表
  - `AgentToolCode` 6 个枚举，`CodeCache.register` 放 `com.ragagent.config.AgentToolAssemblyConfig`
- [x] **Step 1: 写失败测试**
  - `ToolCatalogTest.duplicateNameFailsFastWithCode`、`defaultAllowedUnionsFactories`、`concurrencySafetyComesFromFactory`。
  - `AgentToolCodeMessagesTest.everyCodeHasMessageInSevenLanguages`。
  - `AgentToolSelectorTest.filtersByRuntimeSwitches`、`unknownAllowedToolRaisesToolNotRegistered`。
  - 一致性测试：`allToolsAreRegisteredExactlyOnce`，用 `ToolDefinitions` 常量集合与 `ToolCatalog` 的 key 集合比对，替代旧 `scripts/check-tool-registry.py`。
- [x] **Step 2: 跑红**：`MU test -Dtest='ToolCatalogTest,AgentToolCodeMessagesTest,AgentToolSelectorTest'`。
- [x] **Step 3: 实现**：并发性质从引擎的 switch 改为查询工厂声明；引擎侧 `ExecutionPolicy` 的改造按“对前置计划的新增需求”第 1 条在计划 02 完成，本任务只提供数据。`ToolCatalog` 是 app 内 Bean，不实现任何引擎 spi：计划 02 只用 `AgentEngineRequest.tools` 接收工具实例，工具目录本身没有 spi。对引擎暴露的 spi 只有 `McpToolProvider`，见 Task 19。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agenttools/api M/agenttools/ToolCatalog.java M/agenttools/AgentToolSelector.java M/config/AgentToolAssemblyConfig.java R/i18n T/agenttools`，`git commit -m "feat(agenttools): 工具登记表与 spi"`。

### Task 16: knowledge 与 web 工具

**Files:**
- Create: `M/agenttools/knowledge/{KnowledgeSearchTool,KnowledgeSearchExecutor,KnowledgeSearchMerger,KnowledgeSearchFormatter,GrepChunksTool,GrepRegexBuilder,GrepOutputFormatter,ListChunksTool,DocumentInfoTool,KnowledgeGraphTool,GraphResultFormatter,SearchConversationsTool,SearchMemoryTool,FaqSnippetProjector,KnowledgeSearchRanking,ChunkMetadataReader}.java`
- Create: `M/agenttools/web/{WebSearchTool,WebSearchResultFetcher,WebFetchTool,FetchedPageTrimmer,PageSnapshotWriter}.java`
- Create: `T/agenttools/{KnowledgeSearchToolTest,GrepChunksToolTest,ListChunksToolTest,DocumentInfoToolTest,KnowledgeGraphToolTest,SearchConversationsToolTest,SearchMemoryToolTest,WebSearchToolTest,WebFetchToolTest}.java`

**Interfaces:**
- Consumes: 计划 04 的 `knowledge.api`（1.4 的“对前置计划的新增需求”第 2 条）、`websearch.api`、`memory.api.MemorySearchApi`、本域 `ConversationSearchProvider`、`rerank`
- Produces: `knowledge_search`、`grep_chunks`、`list_knowledge_chunks`、`get_document_info`、`query_knowledge_graph`、`search_conversations`、`search_memory`、`web_search`、`web_fetch` 九个工厂。默认白名单按工厂自身声明取并集，旧 `ToolDefinitions.defaultAllowedTools()` 只给五个内建工具（knowledge_search、grep_chunks、list_knowledge_chunks、get_document_info、search_conversations），其余工具的 `isDefaultAllowed()` 一律为假，由智能体配置与运行时开关打开。wiki 十件与 web 两件不进默认集。并发安全（`isConcurrencySafe()` 为真）只有 list_knowledge_chunks、query_knowledge_graph、get_document_info、search_conversations、search_memory、data_schema、web_search、web_fetch、wiki_search、wiki_read_page、wiki_read_source_doc、wiki_read_issue 十二个（与旧 `ExecutionPolicy.canRunConcurrently` 一致）。`knowledge_search` 与 `grep_chunks` 带会话级已见集合，返回 false。
- [x] **Step 1: 写失败测试**
  - 每个工具一条入参与输出用例，断言 schema 键序（`properties` < `required` < `type`）、失败返回 `success=false` 加可读 `error`、不抛异常。
  - `KnowledgeSearchToolTest.fusesTargetsAndDedupsByKnowledgeAndIndex`、`appliesMmrWhenConfigured`。
  - `GrepChunksToolTest.buildsPostgresRegexFromKeywords`、`limitsTo500Rows`、`filtersByTagScope`。
  - `WebFetchToolTest.trimToBudgetAndReportTruncation`、`rejectsInvalidItemsParam`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：取数只经下级 `api`，本域不再出现 SQL；`search_conversations` 调 `ConversationSearchProvider`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agenttools/knowledge M/agenttools/web T/agenttools`，`git commit -m "feat(agenttools): knowledge 与 web 工具"`。

### Task 17: DatabaseQueryTool 与只读账号

**Files:**
- Create: `M/agenttools/sql/{DatabaseQueryTool,SqlGuard,SqlSelectChecker,SqlTableCollector,SqlTokenizer,SqlScopeAppender}.java`
- Create: `deploy/db/readonly/README.md`
- Create: `T/agenttools/{SqlGuardTest,SqlSelectCheckerTest,DatabaseQueryToolTest,DatabaseQueryReadOnlyIT}.java`

**Interfaces:**
- Consumes: 计划 01 Task 11d 的 `ReadOnlySqlExecutor`；`knowledge.api` 的检索范围
- Produces:
  - `DatabaseQueryTool`：schema 仍只有 `sql` 一个必填字符串；执行前 `SqlGuard.validate(sql)` 校验单条 SELECT、表白名单（`knowledge_bases`、`knowledges`、`chunks`）、函数白名单；再 `SqlScopeAppender.append(sql, SearchTargetsBo)` 只追加检索范围（知识库、文档、标签），**不追加租户条件**
  - `SqlGuard.validate` 返回 `ValidationResult`（`valid` 与首条 `message`）
- [x] **Step 1: 写失败测试**
  - `SqlGuardTest.rejectsNonSelect`、`rejectsUnknownTable`、`rejectsMultiStatement`、`rejectsForbiddenFunction`、`rejectsTooLongOrTooShort`。
  - `SqlSelectCheckerTest.rejectsSubqueryOnSystemSchema`、`rejectsSystemColumns`、`rejectsCteHidingTable`。
  - `DatabaseQueryToolTest.failsWithoutScope`、`reportsValidationMessage`、`formatsRowsAndTruncationNote`。
  - `DatabaseQueryReadOnlyIT`：真实 PostgreSQL，用 `ragagent_ro` 账号执行三类断言。
    1. `crossTenantRowsAreInvisible`：租户 1 有行、租户 2 无行，同一 SQL 以租户 2 执行返回空。
    2. `insertIsRejected`：执行 `INSERT` 被 `SqlGuard` 拦下；绕过校验直接走执行器时数据库报 `insufficient_privilege`，映射为 `AgentToolCode.TOOL_SQL_READONLY_DENIED`。
    3. `longQueryIsKilledByStatementTimeout`：`SELECT pg_sleep(10)` 在 5 秒内被取消，映射为 `AgentToolCode.TOOL_SQL_TIMEOUT`。
- [x] **Step 2: 跑红**：`MU test -Dtest='SqlGuardTest,SqlSelectCheckerTest,DatabaseQueryToolTest,DatabaseQueryReadOnlyIT'`。
- [x] **Step 3: 实现**
  - 只读账号创建（`deploy/db/readonly/README.md`）：`CREATE ROLE ragagent_ro LOGIN PASSWORD '<由 DBA 设置>' NOSUPERUSER NOCREATEROLE NOCREATEDB`；`GRANT CONNECT ON DATABASE ragagent_rebuild`；`GRANT USAGE ON SCHEMA public`；`GRANT SELECT ON knowledge_bases, knowledges, chunks`；`ALTER ROLE ragagent_ro SET statement_timeout = '5s'`；`ALTER ROLE ragagent_ro SET default_transaction_read_only = on`。账号可用性由计划 01 Task 11d 的 `ReadOnlySqlExecutorTest` 复核，本计划不另建用例。
  - 执行器每查询前 `set_config('app.tenant_id', <当前租户>, true)`，行级安全按会话租户过滤；`maxRows` 由 `JdbcTemplate.setMaxRows` 设置。
  - 删除 `SqlInjectionAnalyzer`（旧类名 `SqlInjectionAnalyzer` 现为 `SqlScopeAppender`，只追加检索范围）。
- [x] **Step 4: 跑绿**；再跑 `bash deploy/db/smoke.sh` 与计划 01 Task 11d 的 `ReadOnlySqlExecutorTest` 复核账号可用。
- [x] **Step 5: 提交**：`git add M/agenttools/sql deploy/db/readonly T/agenttools`，`git commit -m "feat(agenttools): 数据库查询工具换只读账号与行级安全"`。

### Task 18: wiki 与 datasource 工具

**Files:**
- Create: `M/agenttools/wiki/{WikiReadPageTool,WikiSearchTool,WikiReadSourceDocTool,WikiFlagIssueTool,WikiWritePageTool,WikiReplaceTextTool,WikiRenamePageTool,WikiDeletePageTool,WikiReadIssueTool,WikiUpdateIssueTool,WikiRouteResolver,WikiScope,WikiPageTexts,WikiPageRendering,WikiContentRewrite}.java`
- Create: `M/agenttools/wiki/{PageView,IssueView,IndexEntryView,IndexGroupView,PendingWikiPage,WikiSlugs,WikiRewriteException}.java`
- Create: `M/agenttools/datasource/{DataSchemaTool,DataAnalysisTool,DuckDbTableLoader,AnalysisDuckDbConnection}.java`
- Create: `T/agenttools/{WikiReadPageToolTest,WikiWritePageToolTest,WikiSearchToolTest,DataSchemaToolTest,DataAnalysisToolTest}.java`

**Interfaces:**
- Consumes: 计划 05 的 `wiki.api`、计划 04 的 `knowledge.api`、`Cleanable`
- Produces: 十个 wiki 工厂与两个 datasource 工厂；`data_analysis` 实现 `Cleanable`，轮末清理内存表
- [x] **Step 1: 写失败测试**
  - `WikiReadPageToolTest.resolvesBySlugThenTitle`、`unknownPageReturnsFailureResult`。
  - `WikiWritePageToolTest.createsPageAndReportsAppliedChange`、`renameUpdatesLinks`。
  - `WikiSearchToolTest.matchesLowercaseSubstringAcrossSlugTitleContent`。
  - `DataSchemaToolTest.listsColumnsFromChunks`、`deniesWhenScopeAuthorizerRejects`。
  - `DataAnalysisToolTest.loadsCsvIntoDuckDbAndQueries`、`cleanupDropsTables`。
- [x] **Step 2: 跑红**。
- [x] **Step 3: 实现**：wiki 工具统一注入 `wiki.api`，不再有 `WikiPages` 端口壳；`DataAnalysisTool` 用进程内 DuckDB，不依赖 Excel 与 spatial 扩展。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agenttools/wiki M/agenttools/datasource T/agenttools`，`git commit -m "feat(agenttools): wiki 与 datasource 工具"`。

### Task 19: MCP 工具包装迁入

**Files:**
- Create: `M/agenttools/mcp/{McpToolWrapperTool,McpToolCatalog,McpCatalogPaginator,McpDiscoverTool,McpCallTool,McpToolExposure,McpToolingBridge,McpInputSchemaValidator,McpJsonCodec,McpToolNaming,McpRegisteredTool,McpOAuthCoordinator,McpToolProviderBridge}.java`
- Create: `T/agenttools/{McpToolWrapperToolTest,McpCatalogTest,McpDiscoverToolTest,McpInputSchemaValidatorTest,McpToolNamingTest,McpToolProviderBridgeTest}.java`

**Interfaces:**
- Consumes: 计划 06 的 `McpToolGatewayApi`、`McpToolPolicyApi`、`McpApprovalApi`、`McpOAuthApi`；计划 02 的 `agentengine.api.ToolCancellation`、`agentengine.spi.McpToolProvider`、`agentengine.spi.McpToolSession`、`agentengine.spi.McpSelection`
- Produces:
  - `McpToolWrapperTool`：每次调用先问 `McpToolPolicyApi.needsApproval`，需要则 `McpApprovalApi.openToolApproval` 后返回挂起结果，轮次由 session 的审批恢复接线续跑
  - `McpToolProviderBridge implements McpToolProvider`：`McpToolSession open(long tenantId, McpSelection selection)`。返回 `McpToolingBridge`，即 `McpToolSession` 的实现。这是本计划对引擎暴露的唯一 MCP spi，注册为 Bean，缺 `McpToolGatewayApi` 时启动失败
  - `McpToolingBridge implements McpToolSession`，六个方法签名照抄计划 02：`void prepare(boolean direct)`、`void refresh()`、`void rememberHistory(List<ChatMessage>)`、`ToolCallTarget callTarget(String name, Object raw)`、`boolean isPrepared()`、`boolean hasServer(String serviceId)`。每次 `open` 一个实例，不是单例 Bean。旧 `McpTooling` 的注册编排并入本类，旧 `McpExposure` 的静态注册改为 `McpToolExposure` 的实例方法
  - `McpDiscoverTool`、`McpCallTool` 两个代理工具，`isDefaultAllowed()` 为假
- [x] **Step 1: 写失败测试**
  - `McpToolWrapperToolTest.opensApprovalWhenPolicyRequires`、`callsGatewayWhenApprovalNotRequired`、`mapsGatewayFailureToToolResultError`、`sendsCancellationOnInterrupt`。
  - `McpToolProviderBridgeTest.openBuildsSessionForSelection`、`sessionPrepareAndRefreshDelegateToCatalog`、`callTargetRejectsUnknownTool`、`hasServerReflectsSelection`、`bridgeImplementsMcpToolSession`（断言 `McpToolSession.class.isAssignableFrom(McpToolingBridge.class)`）。
  - `McpCatalogTest.paginatesAndReportsHasMore`、`authorizeRejectsTenantMismatch`。
  - `McpInputSchemaValidatorTest.rejectsExtraProperties`、`acceptsCoercedPrimitiveTypes`。
  - `McpToolNamingTest.prefixesServerAndKeepsNameWithinSixtyFourChars`。
  - 一致性测试 `mcpToolsAreRegisteredExactlyOnce`。
- [x] **Step 2: 跑红**：`MU test -Dtest='McpTool*Test,McpToolProviderBridgeTest,McpCatalogTest,McpInputSchemaValidatorTest,McpToolNamingTest'`。
- [x] **Step 3: 实现**：不保留旧 `Gate` 的线程阻塞；所有等待表现为挂起结果与事件帧。`McpToolProviderBridge` 只做租户与选择项的边界校验，目录读取与调用全部转给 `McpToolGatewayApi`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/agenttools/mcp T/agenttools`，`git commit -m "feat(agenttools): MCP 工具包装迁入"`。

## 批次 D：多入口与整链路

### Task 20: 多入口 Provider

**Files:**
- Create: `M/session/service/{ChannelConversationService,ChannelTurnHandle,EmbedSessionService,ApprovalTurnResolver,SessionTranscriptReader}.java`
- Create: `T/session/{ChannelConversationServiceTest,ChannelTurnHandleTest,EmbedSessionServiceTest,ApprovalTurnResolverTest,SessionTranscriptReaderTest}.java`

**Interfaces:**
- Consumes: 计划 07 的 `com.ragagent.channel.api.ChannelTurnProvider`、`ChannelTurn`、`ChannelTurnListener`、`ChannelTurnRequest`、`ChannelTurnRef`、`ChannelTurnOutcome`、`ChannelConversationSpec`、`ChannelVisitorApi`、`VisitorScope`、`VisitorSessionBo`、`ChannelMessageBo`、`SuggestionSetBo`、`SuggestionEventBo`；`mcp.api.ApprovalResolutionListener`；`memory.api.MemoryTranscriptProvider`
- Produces:
  - `ChannelConversationService implements ChannelTurnProvider`（3 个方法）与 `ChannelVisitorApi`（9 个方法）。两个接口都在 `com.ragagent.channel.api`，session 只实现不改声明，不 import channel 的 `service` 或 `repository`
  - `ChannelConversationService.openConversation(ChannelConversationSpec spec)` 返回 `String`：按 `spec.tenantId` 显式设租户上下文，建 `sessions` 行，`owner_type` 取 `spec.audience`（`END_USER` 时 `API_EXTERNAL_USER`，`VISITOR` 时 `EMBED_SESSION`），`owner_id` 存 `spec.externalUserId`，`channel_marker` 存 `spec.description` 原文
  - `ChannelConversationService.startTurn(ChannelTurnRequest request)` 返回 `ChannelTurn`：调 `QaTurnService.executeHeadless`，返回 `ChannelTurnHandle`（`sessionId()`、`assistantMessageId()` 返回字符串化 bigint；`onEvent` 只允许注册一次，重复抛 `IllegalStateException`；`awaitCompletion(Duration)` 超时返回 `Optional.empty()`；`close()` 释放句柄不取消轮次）
  - `ChannelConversationService.cancelTurn(ChannelTurnRef ref)`：调 `SessionStopService.stop`，轮次不存在或已结束不报错
  - `ChannelConversationService.attachVisitor`、`listVisitorSessions`、`deleteVisitorSession`、`countSessions`、`loadMessages`、`stopTurn`、`readSuggestions`、`ensureSuggestions`、`recordSuggestionEvent`：落 `EmbedSessionService` 与 `MessageSuggestionService`，会话查询按 `VisitorScope` 的租户加渠道加访客三元组限定
  - `ApprovalTurnResolver implements ApprovalResolutionListener.onResolved(ApprovalResolvedBo)`：决定到达后，若该 `assistantMessageId` 仍有 live-run，则经 `SteerService` 注入；否则用原会话开后续轮次
  - `SessionTranscriptReader implements MemoryTranscriptProvider.readSince(long sessionId, TranscriptCursor cursor, int limit)`：按 `(createdAt, messageId)` 游标增量读，返回 `List<TranscriptLineBo>`
- [x] **Step 1: 写失败测试**
  - `ChannelConversationServiceTest.openConversationWritesOwnerTypeFromAudience`、`startTurnWritesEventsAndCommitsOnce`、`startTurnDoesNotCreateEmitter`、`cancelTurnOnFinishedTurnIsSilent`、`visitorMethodsScopeByTenantChannelAndVisitor`、`attachVisitorUsesDedicatedColumn`、`visitorMethodCountMatchesContract`（反射断言 `ChannelVisitorApi` 的抽象方法数与本类实现数都是 9）。
  - `ChannelTurnHandleTest.onEventRegistersOnce`、`secondRegistrationThrows`、`awaitCompletionTimeoutReturnsEmpty`、`completedFiresExactlyOnce`。
  - `EmbedSessionServiceTest.ownerMarkerUsesDedicatedColumn`、`crossChannelSessionIsInvisible`、`deleteVisitorSessionRequiresOwnership`。
  - `ApprovalTurnResolverTest.injectsWhenTurnStillRunning`、`startsFollowUpWhenTurnEnded`、`ignoresResolutionForOtherSession`。
  - `SessionTranscriptReaderTest.cursorAdvancesWithoutDuplicates`、`stopsAtLimit`。
- [x] **Step 2: 跑红**：`MU test -Dtest='ChannelConversationServiceTest,ChannelTurnHandleTest,EmbedSessionServiceTest,ApprovalTurnResolverTest,SessionTranscriptReaderTest'`。
- [x] **Step 3: 实现**：Provider 实现放在 `session.service`，由 `SessionAssemblyConfig` 声明 Bean。渠道回调线程没有登录会话，所以每个入口先从 `request` 或 `scope` 取租户显式设置上下文，`finally` 恢复。`startTurn` 内部把 `ChannelTurnListener` 适配到 `StreamEventLog` 的读侧，不新增第二套事件通道。
- [x] **Step 4: 跑绿**。核对 `ChannelVisitorApi` 九个方法与计划 07 声明逐个一致，缺一个时 `implements` 编译失败即视为通过该检查。
- [x] **Step 5: 提交**：`git add M/session/service/ChannelConversationService.java M/session/service/ChannelTurnHandle.java M/session/service/EmbedSessionService.java M/session/service/ApprovalTurnResolver.java M/session/service/SessionTranscriptReader.java M/config/SessionAssemblyConfig.java T/session`，`git commit -m "feat(session): 渠道、挂件、审批恢复与记忆转写的 Provider"`。

### Task 21: 整链路回归与对拍

**Files:**
- Create: `tests/regression/sse/{package.json,record-sse.mjs,compare-sse.mjs,compare-sse.test.mjs,README.md}`
- Create: `tests/regression/sse/cases.json`
- Create: `tests/regression/sse/baseline-legacy.json`（录制产物，提交）
- Modify: `tests/e2e/tests/agenttools/tool-approval.spec.ts`（计划 01 Task 13 已录制，本任务去掉条件页标注并补审批恢复步骤）
- Modify: `tests/e2e/PAGES.md`（`@session`、`@agenttools`、挂件两条状态改 `已录制`）
- Modify: `tests/api-smoke/tests/chat-flow.spec.ts`（去掉 `test.fixme`）

**Interfaces:**
- Consumes: 计划 01 的 `tests/regression` 适配器契约、`legacy.mjs`；`tests/e2e` 的 `PAGES.md`；Task 20 的四类 Provider 入口（`ChannelTurnProvider`、`ChannelVisitorApi`、`ApprovalResolutionListener`、`MemoryTranscriptProvider`）
- Produces:
  - `record-sse.mjs --adapter legacy|rebuild --out <file>`：对同一批问题逐个发问答请求，抓取 SSE 帧，产出 `[{id, events: [{type, keys, done}], frames}]`
  - `compare-sse.mjs --baseline a.json --candidate b.json [--approval-excluded]`：比对维度为事件类型序列、顺序、每个事件 `data` 的键集合与键序；`--approval-excluded` 时跳过审批挂起与恢复的两个事件
  - 判据：`typeSequenceEqual`（类型序列完全一致，审批场景除外）、`keyOrderEqual`（键序一致）、`explainableDiffs` 列表为空。任一非空则退出码 1。
- [x] **Step 1: 写失败测试**
  - `compare-sse.test.mjs` 四条：自比对通过；缺少一个 `answer` 帧时类型序列不一致并失败；交换两个事件顺序时失败；审批场景在两帧恢复事件被排除后通过。
  - 两端各录一次：`node record-sse.mjs --adapter legacy --out baseline-legacy.json`（旧系统在 `../ragagent-legacy`，8083）与 `--adapter rebuild`（新栈 8080）。
- [x] **Step 2: 跑红**：`cd tests/regression/sse && node --test compare-sse.test.mjs` 首条即失败（脚本不存在）。
- [x] **Step 3: 实现**
  - 问题集 10 条，覆盖纯聊天、KB 检索、Agent 工具调用、插话（steer）、停止、追问建议触发、审批挂起与恢复（单列）。每条带 `expectedTypes` 供报告。
  - 对拍差异必须写进 `README.md` 的“可解释差异”清单：HTML 转义不再生效导致的字节差异、`SseEmitter` 分帧导致的合帧差异，以及断线续流从 offset 0 重放导致的重复帧（这些不参与类型序列比对，比对只取首轮原始流）。
  - 检索回归在整链路复跑：`cd tests/regression && node record.mjs --adapter rebuild --out candidate.json && node compare.mjs --baseline baseline.json --candidate candidate.json --min-expected 1.0`。
- [x] **Step 4: 跑绿**
  - `node --test compare-sse.test.mjs` 全绿。
  - `node compare-sse.mjs --baseline baseline-legacy.json --candidate baseline-rebuild.json` 退出 0，`explainableDiffs` 为空。
  - `cd tests/api-smoke && npx playwright test` 六条全绿。
  - `cd tests/e2e && npx playwright test --grep '@session|@agenttools'` 全绿；`--grep '@channel'` 只跑挂件首屏与访客对话两条，同样全绿。
- [x] **Step 5: 提交**：`git add tests/regression/sse tests/e2e/tests/agenttools tests/e2e/PAGES.md tests/api-smoke/tests/chat-flow.spec.ts`，`git commit -m "test(session): 整链路 SSE 对拍、检索回归与界面验收"`。

### Task 22: 闸门

**Files:**
- Modify: `docs/migration/progress.md`

- [x] **Step 1: 全量构建**：`MVN verify`，预期 BUILD SUCCESS，Modulith、ArchUnit、Checkstyle 全绿。
- [x] **Step 2: 结构检查**
  ```bash
  grep -rEn "tenant_id" backend/app/src/main/java/com/ragagent/{session,agenttools}
  grep -rEn "@(Select|Update|Insert|Delete)\b" backend/app/src/main/java/com/ragagent/{session,agenttools}
  find backend/app/src/main/java/com/ragagent/{session,agenttools} -name '*Ops.java' -o -name '*Support.java' -o -name '*Helper.java' -o -name '*Facade.java' -o -name '*Manager.java'
  grep -rEn "CharacterEscapes|ESCAPE_HTML|JsonWriteFeature" backend/
  ```
  预期四条命令均无输出。
- [x] **Step 3: 注释清理**：旧目录 `domains/.../session/controller/sse/SseFrameWriter.java` 与 `boot/src/test/java/com/ragagent/session/controller/sse/SseFrameWriterTest.java` 的过时注释在计划 09 删旧模块时一并消失，本计划不改旧模块。在新代码 `session.stream` 的类 Javadoc 里写一句“不启用 HTML 转义”，并由 Task 10 的 `JacksonEscapingTest` 断言。
- [x] **Step 4: 代码审查**：对整条分支跑 `code-review-mr`，高置信问题修完，结论写进 `progress.md`。
- [x] **Step 5: 提交**：`git add docs/migration/progress.md`，`git commit -m "chore(session): 计划 08 闸门"`。

# 3. 完成判据

```bash
# 全量构建，含 Modulith、ArchUnit、Checkstyle
MVN verify
# 预期：BUILD SUCCESS，无 Checkstyle 违规

# session 与 agenttools 界面端到端
cd tests/e2e && npx playwright test --grep '@session|@agenttools'
# 预期：全部通过，无 skipped

# 挂件访客对话
cd tests/e2e && npx playwright test --grep '@channel'
# 预期：挂件首屏与访客对话用例通过

# 冒烟六条
cd tests/api-smoke && npx playwright test
# 预期：6 passed，0 skipped

# SSE 整链路对拍
cd tests/regression/sse && node compare-sse.mjs --baseline baseline-legacy.json --candidate baseline-rebuild.json
# 预期：退出码 0，explainableDiffs 为空

# 检索回归在整链路复跑
cd tests/regression && node compare.mjs --baseline baseline.json --candidate candidate.json --min-expected 1.0
# 预期：expectedRecall = 1.000，退出码 0

# OpenAPI 类型无漂移
cd frontend/packages/api-client && npm run check:api
# 预期：退出码 0

# 前端类型检查与相关单测
cd frontend && npx vue-tsc --noEmit && npx vitest run src/api/chat
# 预期：无错误

# 结构检查
grep -rEn "tenant_id" backend/app/src/main/java/com/ragagent/{session,agenttools}
grep -rEn "@(Select|Update|Insert|Delete)\b" backend/app/src/main/java/com/ragagent/{session,agenttools}
find backend/app/src/main/java/com/ragagent/{session,agenttools} -name '*Ops.java' -o -name '*Support.java' -o -name '*Helper.java' -o -name '*Facade.java' -o -name '*Manager.java'
grep -rEn "CharacterEscapes|ESCAPE_HTML|JsonWriteFeature" backend/
# 预期：四条命令均无输出
```

逐条对应路线图 08 号闸门与 spec 第 7 节：

| 闸门项 | 覆盖方式 |
|---|---|
| 控制台问答、挂件访客对话转绿 | Task 14、Task 21 的 `@session` 与 `@channel` 用例 |
| 冒烟六条全绿 | Task 21 的 `tests/api-smoke` |
| 检索回归与引擎对拍在整链路上通过 | Task 21 的 `compare.mjs` 与 `compare-sse.mjs` |
| 验收 2：无 rawBody 与 Go 兼容工具 | Task 13 的 VO 加 `@Valid`，结构检查 |
| 验收 3：无手写租户条件、租户表开行级安全 | Task 1 的 `SessionRowLevelSecurityTest` 与结构检查第一条 |
| 验收 4：业务异常带错误码与 i18n key | Task 3、Task 15 的 `*CodeMessagesTest` |
| 验收 5：前端类型由 OpenAPI 生成 | Task 13、Task 14 的 `gen:api` 与 `check:api` |
| 验收 6：架构规则由 Java 测试表达 | Task 15 的工具注册一致性测试替代 `check-tool-registry.py`，Task 22 的 `MVN verify` |

## 本计划明确不覆盖

1. `@channel` 的渠道管理页（IM 渠道、嵌入渠道配置）归计划 07；本计划只转绿挂件首屏与访客对话。
2. 前端拆 `apps/console` 与 `apps/widget`、pnpm 迁移归计划 09。
3. mcp-server 改新 API 与 SSE 规范解析归计划 09，本计划只提供事件表与路径。
4. 计划 02 的工具 spi 若未按“对前置计划的新增需求”第 1 条落地，Task 15 先停并登记，不在本计划内改引擎内部实现。
5. 计划 01 Task 11d 若未交付 `framework.stream` 与 `framework.sql`，本计划 Task 1、Task 4、Task 6、Task 10、Task 17 全部先停，等 Task 11d 落盘并把签名补进 Task 1 的 Consumes 后再开工。
