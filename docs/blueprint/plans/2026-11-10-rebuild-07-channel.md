# RagAgent 重建：channel 域迁入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `im` 与 `embedchannel` 两域合并重写为 `backend/app` 的 `channel` 域，交付十个 IM 平台适配器、渠道回调、渠道生命周期与长连接、嵌入渠道管理面与挂件访客面，并在 `channel.api` 声明由计划 08 实现的对话入口 Provider。渠道相关页面在新栈上与旧系统一致。

**Architecture:** 一个业务域 `com.ragagent.channel`，两条聚合：`ImChannel`（`channel_im`）与 `EmbedChannel`（`channel_embed`）。IM 会话映射单独成 `ChannelConversation`（`channel_conversations`）。渠道平台差异用窄接口 `ChannelAdapter` 加 Spring 登记表消解，每个平台一个 `channel/adapter/<platform>` 子包。渠道回调由各平台适配器自行验签。长连接与后台任务只在 worker 角色运行。channel 是业务域，不依赖编排域 session；问答入口由 channel 在 `api` 声明 `ChannelTurnProvider`，session 实现。

**Tech Stack:** Spring Boot 3.5.16、MyBatis-Plus、Flyway、Spring Modulith、Sa-Token、Redisson、JobRunr、springdoc、Testcontainers PostgreSQL、Playwright。

**规格来源**: `docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图 `docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 07 号计划。

## Global Constraints

继承路线图第 3 节全部约定，下列为本计划最常用的几条，原文以路线图为准：

- JDK 21、Spring Boot 3.5.16。Java 缩进 tab、行宽 140、中文 Javadoc、`@author jiangguifan`、魔法字符串收口常量或枚举。
- 业务错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号 **017**，枚举 `ChannelCode` 放 `channel/api`。
- 文案 key `code_<错误码>`，业务域文案放 `backend/app/src/main/resources/i18n/app-messages*.properties`，七语种加默认文件齐全。
- Flyway 脚本放 `backend/app/src/main/resources/db/migration/allenv/`，命名 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_channel_{描述}_jiangguifan.sql`，版本号与描述之间双下划线。
- 主键 bigint 雪花 ID，序列化为字符串。租户表 `tenant_id bigint NOT NULL` 加行级安全，策略用计划 01 Task 5 的 fail-closed 模板。软删 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
- 路径三组：`/api/admin/v1`、`/api/v1`、`/api/open/v1`。请求体一律 VO 加 `@Valid`，响应 `RestResponse`。流式接口与渠道回调除外。
- 代码质量按路线图 3.11：方法 40 行、类 400 行、参数 5 个、构造器依赖 7 个、圈复杂度 10、嵌套 3 层。类名不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common`，单实现不建接口。构造器注入用 `@RequiredArgsConstructor`。
- 事务内不做外部调用。渠道出站、模型、向量库、文档解析都算外部调用。
- `git add` 只写显式路径。每个任务一次提交，提交后在 `docs/migration/progress.md` 追加一行。
- Maven 命令统一带 `-s C:/Users/jiangguifan/.m2/settings-coding-uat.xml`，`-pl` 必带 `-am`。下文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`。

## Review Focus

下列五类输入与场景 spec 没有逐条写测试，但最容易出错。每条都在对应任务的测试里有断言。

1. 渠道回调的三种失败：未知渠道返回 404，渠道已禁用返回 503，平台没有适配器返回 503，三者都不泄露租户信息，且回包仍是该平台要求的纯 JSON 形状。归属 Task 11。
2. 同一机器人凭据绑定第二个渠道被拒，软删第一个渠道后可以重新绑定；两个租户各自绑定不同机器人互不影响。唯一性由 Task 1 的全局索引与 Task 10 的用例共同保证。
3. 两个实例同时启动同一 websocket 渠道，只有一个实例拿到领导权；持锁实例停止后续期线程，另一实例在 10 秒内接管。归属 Task 8。
4. 挂件鉴权判定顺序固定为：路径渠道 ID、`Authorization: Embed <token>` 存在性、令牌形态、渠道禁用先于令牌比对、Origin 白名单、三段限流、租户存在性。任一步失败都不得进入后续步骤。归属 Task 13。
5. 非流式（full）模式整轮只发一条最终消息，流式（stream）模式下已发送的分片只能变长不能回退；在途轮次收到停止请求后不再发出新分片。归属 Task 9。

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 | framework 的 `RestResponse`、`BusinessRuntimeException`、`CodeCache`、`@IgnoreTenant`、`@Encrypted`、`PostgresContainerTest`、`SaRouteRules.loginTypeOf`、`permit-paths` 配置、`@ragagent/api-client`、`tests/e2e/PAGES.md` |
| 计划 02 | `agentengine.api.ToolCancellation`；model-runtime 的 `LlmChatClient`（本计划不直接用，渠道只经 Provider 问答） |
| 计划 03 | `auth.api` 当前主体；`tenant.api` 租户存在性；`storage.api` 按 `resource://` 句柄读文件内容；`system.api` 部署形态（是否生产）；`docs/migration/golden-sample.md`；权限点命名 |
| 计划 04 | `knowledge.api` 的分块按 ID 读取、知识库存在性、附件异步入库入口；JobRunr 的 `JobScheduler` Bean 与 `ragagent.role` 开关 |
| 计划 06 | `agent.api.AgentQueryApi`（归属校验、按 ID 取视图、归一化配置）、`AgentDeletedEvent`；`mcp.api.McpApprovalApi`、`McpOAuthApi` |
| 计划 08 | `ChannelTurnProvider` 与 `ChannelVisitorApi` 的实现方，见“对外承诺”。本计划只声明接口并按接口写测试替身 |

### 交付

1. `channel` 域三张表、错误码与七语文案、持久层、适配器层、用例层、控制器层。
2. 十个 IM 平台适配器与回调面。
3. 嵌入渠道管理面、访客鉴权过滤链、挂件公开面。
4. `channel.api` 的两个 Provider 声明与对应 BO，供计划 08 实现。
5. 前端 `frontend/src/api/agent/index.ts` 的 IM 部分、`frontend/src/api/embed/index.ts`、`frontend/src/composables/useEmbedChatSession.ts` 的路径切换。
6. 对路线图第 6 节交接表的增补。

### 对外承诺

**对话入口 Provider**，包 `com.ragagent.channel.api`，由计划 08 的 session 实现：

```java
/**
 * 渠道对话入口：接收一条渠道消息，开一轮问答并返回可订阅的轮次。
 *
 * @author jiangguifan
 */
public interface ChannelTurnProvider {

	/**
	 * 为一条渠道会话建底层会话行，已存在同键会话时不复用，由调用方决定是否调用。
	 *
	 * @param spec 会话规格，含租户、渠道、受众与外部主体
	 * @return 新会话 ID
	 */
	String openConversation(ChannelConversationSpec spec);

	/**
	 * 在指定会话内开一轮问答。返回即表示消息行已落库、管线已启动。
	 *
	 * @param request 本轮请求
	 * @return 轮次句柄，调用方订阅事件或等待完成
	 */
	ChannelTurn startTurn(ChannelTurnRequest request);

	/**
	 * 取消指定会话内的在途轮次，跨实例有效。轮次不存在或已结束时不报错。
	 *
	 * @param ref 会话与助手消息定位
	 */
	void cancelTurn(ChannelTurnRef ref);
}
```

`ChannelTurn` 语义：

```java
/**
 * 一轮渠道问答的句柄。
 *
 * @author jiangguifan
 */
public interface ChannelTurn extends AutoCloseable {

	/** 本轮会话 ID。 */
	String sessionId();

	/** 本轮助手消息 ID，取消时用。 */
	String assistantMessageId();

	/** 注册事件监听，只允许注册一次，重复注册抛 IllegalStateException。 */
	void onEvent(ChannelTurnListener listener);

	/** 阻塞等待本轮结束，返回最终答案与失败标记。 */
	Optional<ChannelTurnOutcome> awaitCompletion(Duration timeout);

	/** 释放句柄，不取消轮次。 */
	@Override
	void close();
}
```

`ChannelTurnListener` 四个回调：`onReasoningDelta(String)`、`onAnswerDelta(String)`、`onTool(ChannelToolEvent)`、`onCompleted(ChannelTurnOutcome)`。`onAnswerDelta` 只传增量，累积由渠道侧负责。`onCompleted` 恰好调用一次，失败时 `ChannelTurnOutcome.isFailed()` 为 true 并带可对用户展示的文案。

`ChannelTurnRequest` 字段：`tenantId`、`sessionId`、`channelType`（`ChannelType`）、`channelId`、`externalUserId`、`audience`（`END_USER` 或 `VISITOR`）、`query`、`quotedContext`、`attachments`（`List<ChannelAttachment>`）、`requestId`。租户必须显式传入，Provider 不用请求上下文推导，因为渠道回调线程与长连接线程都没有登录会话。

`ChannelConversationSpec` 字段：`tenantId`、`channelType`、`channelId`、`externalUserId`、`audience`、`title`、`description`。`description` 由渠道侧给出，格式 `im_channel:<channelId>` 或 `embed_channel:<channelId>`，会话侧原样存储。

**访客会话 Provider**，同一包，由计划 08 实现：

```java
/**
 * 挂件访客会话语义：会话归属、历史列表、消息读取、停止、建议与事件。
 *
 * @author jiangguifan
 */
public interface ChannelVisitorApi {

	/** 把渠道与访客标记写进会话的归属字段，失败不抛。 */
	void attachVisitor(VisitorScope scope, String sessionId);

	/** 本访客在本渠道下的历史会话，按最后活动倒序。 */
	List<VisitorSessionBo> listVisitorSessions(VisitorScope scope, int limit);

	/** 删除本访客的会话，返回 false 表示不属于该访客或已删除。 */
	boolean deleteVisitorSession(VisitorScope scope, String sessionId);

	/** 本渠道来源的会话数，供管理面统计。 */
	long countSessions(long tenantId, long channelId);

	/** 读取会话消息，含资源 URL 重写。 */
	List<ChannelMessageBo> loadMessages(VisitorScope scope, String sessionId, int limit, String before);

	/** 停止指定助手消息。 */
	void stopTurn(VisitorScope scope, String sessionId, String assistantMessageId);

	/** 读建议集，未生成返回空集。 */
	SuggestionSetBo readSuggestions(VisitorScope scope, String sessionId, String messageId);

	/** 保证建议集存在，生成中时 `status` 为 `GENERATING`。 */
	SuggestionSetBo ensureSuggestions(VisitorScope scope, String sessionId, String messageId);

	/** 记录建议点击事件。 */
	void recordSuggestionEvent(VisitorScope scope, String sessionId, SuggestionEventBo event);
}
```

`VisitorScope(long tenantId, long channelId, String visitorId)`。

**对路线图第 6 节的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| channel（07） | `ChannelTurnProvider` | 渠道与挂件调用问答 | session（08） |
| channel（07） | `ChannelVisitorApi` | 挂件访客会话、消息、停止、建议 | session（08） |
| channel（07） | 实现 `AgentDeletedEvent` 监听 | 删除智能体后软删其渠道 | 本域 |

路线图中已有的“channel（07）对话入口 Provider”一行以本节为准，接口名由本计划钉死。

### 对前置计划的新增需求

| 前置 | 需求 |
|---|---|
| 计划 03 | `storage.api` 提供 `Optional<byte[]> readResource(String resourceUrl)` 与 `long storeResource(InputStream, String fileName, String mediaType)`，供挂件附件代理与渠道附件入知识库使用。计划 03 若已有等价方法，以它为准并登记偏离 |
| 计划 03 | 权限点 `channel:read`、`channel:write`、`embed-channel:manage` 三个逻辑名，没有时按偏离登记 |
| 计划 04 | 附件入知识库入口需要同时返回“已受理”标识，供渠道侧回复用户“文件已收到”。以计划 04 的 `knowledge.api` 为准 |
| 计划 04 | `ragagent.role` 取值 `api`、`worker`、`all`，`ChannelRuntime` 只在含 worker 的角色下启动长连接。JobRunr 的 `JobScheduler` 由计划 04 提供 |
| 计划 06 | `AgentQueryApi.find(long agentId, Locale locale)` 会被渠道列表逐行调用。渠道数量少，不要求批量接口。若计划 06 已声明批量方法，改用批量 |

## 本计划新做的设计决定（待用户核对）

只列 spec 与路线图没有定、由本计划新定的决定，按重要度排序。纯机械翻译的不列。

### 一、新表结构与聚合根划分

1. **IM 与嵌入渠道分两张表，不合表。** 决定：`channel_im` 与 `channel_embed` 各自成聚合，写属主分别是 `ImChannelRepository` 与 `EmbedChannelRepository`。理由：两域字段几乎不相交，合表会出现一半列长期为 null，且 IM 的 `credentials` 与嵌入的 `publish_token` 生命周期不同。否决：单表加 `channel_kind` 判别列，所有查询都要带判别条件，新增渠道类型时列继续膨胀。
2. **IM 会话映射表改名 `channel_conversations`，只服务 IM。** 决定：旧 `im_channel_sessions` 改名，列改 bigint，去掉到 `sessions` 的外键。嵌入渠道的会话归属由会话行自身的 `embed_visitor_id` 与描述标记表达，不再有一张独立映射表。理由：spec 5.7 要求域间只存 ID 不建外键，删会话后的孤儿映射沿用旧行为在读取时清理。否决：嵌入也建一张映射表，与 `sessions.embed_visitor_id` 重复存同一事实。
3. **渠道凭据整列加密，嵌入发布令牌加哈希列。** 决定：`channel_im.credentials_ciphertext text NOT NULL` 与 `channel_embed.webhook_secret_ciphertext text` 用 framework 的 `@Encrypted` 类型处理器。`channel_embed` 的发布令牌拆两列：`publish_token_ciphertext text NOT NULL`（`@Encrypted`）与 `publish_token_sha256 char(64) NOT NULL`。理由：spec 3.7 第 4 条要求凭据与令牌一律 `@Encrypted`；发布令牌要建唯一索引并支持等值查询，密文列不能承担这两件事。旧实现对整块凭据 JSON 再套一层 AES，新实现由类型处理器逐列加密，语义等价。否决：整块 jsonb 加密后不可索引，发布令牌唯一性要在应用层扫全表。
4. **三个聚合根，按生命周期划分。** 决定：`ImChannelRepository` 拥有 `channel_im`；`EmbedChannelRepository` 拥有 `channel_embed`；`ChannelConversationRepository` 拥有 `channel_conversations`。理由：会话映射与渠道配置的写入时机与保留策略不同，渠道删除后映射需要清理但不能与渠道同行。否决：把映射并入渠道聚合，删除渠道要连带级联，事务边界变大。

### 二、新接口路径与 VO 形状

1. **平台回调迁到 `/api/open/v1/channels/{channelId}/callbacks`。** 决定：旧 `/api/v1/im/callback/{channelId}` 的 GET 与 POST 合并到开放接口组，响应形状按平台原样输出，不走 `RestResponse`。理由：回调是平台服务器直调，属于开放接口组；`/api/v1` 按路线图 3.6 只给访客身份，回调没有访客令牌。系统未上线，各平台控制台里的回调地址随部署更新，无历史地址需要兼容。否决：保留旧路径另加新路径，两套入口要同时维护验签与 ACK 逻辑。
2. **挂件问答两条路径合并为 `POST /api/v1/embed/{channelId}/chat`。** 决定：旧 `knowledge-chat/{sessionId}` 与 `agent-chat/{sessionId}` 合并，请求体 `{sessionId, query, mode, resourceUrls}`，`mode` 取 `KNOWLEDGE`、`AGENT`，缺省 `KNOWLEDGE`。响应 `text/event-stream`。理由：两条路径的鉴权、限流、渠道约束、事件泵完全相同，只有会话侧管线不同；计划 01 的冒烟用例已按 `/chat` 记账。否决：保留两条路径并在 API 层各写一份 SSE 处理。
3. **挂件待审批两条路径合并为 `.../mcp-approvals/{approvalId}/resolution`。** 决定：旧 `mcp-oauth-resolutions/{pendingId}`、其 `/cancel` 与 `tool-approvals/{pendingId}` 三条路径合并，请求体 `{decision}`，`decision` 取 `APPROVE`、`REJECT`、`AUTHORIZE`、`CANCEL` 四个值，与计划 06 设计决定二第 1 条完全一致，挂件与管理面共用同一个 `ApprovalDecision` 枚举。理由：沿用计划 06 的审批合并决定，两边的待审批共用一张表与一套状态机。前端三个函数保持签名，内部映射。
4. **微信扫码归管理面 `POST /api/admin/v1/wechat/qrcodes` 与 `GET /api/admin/v1/wechat/qrcodes/{qrcode}`。** 决定：旧 `POST /api/v1/wechat/qrcode` 与 `POST /api/v1/wechat/qrcode/status` 改为资源式路径。理由：扫码是后台用户绑定渠道的动作，属管理面；状态查询是幂等读，用 GET。否决：保留 POST 状态查询，路径与动作语义不符且无法缓存。
5. **管理面资源名保持 `im-channels` 与 `embed-channels`。** 决定：`/api/admin/v1/im-channels`、`/api/admin/v1/agents/{agentId}/im-channels`、`/api/admin/v1/embed-channels`、`/api/admin/v1/agents/{agentId}/embed-channels`。理由：前端两个面板按资源分文件，路径改名换不来收益；合成单一 `channels` 资源后，两类渠道的字段与校验差异要全部塞进一个 VO。否决：统一 `channels` 资源加 `type` 判别。
6. **挂件公开配置与访客会话路径保持 `/api/v1/embed/{channelId}/...` 的层级。** 决定：`config`、`exchange`、`suggested-questions`、`chunks/{chunkId}`、`files`、`sessions`、`messages/{sessionId}/load`、`stop`、`suggestions`、`suggestion-events`、`events` 沿用旧层级，仅把 `mode` 与合并路径按上面两条调整。理由：这些路径被宿主页的 `ragagent-widget.js` 与 iframe 内的前端直接调用，层级变化只有一处收益，即与 REST 规范一致，但要同步改前端与已嵌入的示例代码。否决：全部重排为 `/api/v1/channels/{channelId}/...`。
7. **会话创建与轮次的先后固定为先开会话，再起轮次。** 决定：`ChannelConversationResolver` 先按 `(tenantId, channelType, channelId, externalUserId)` 查 `channel_conversations`，命中则复用其 `sessionId`，未命中调 `ChannelTurnProvider.openConversation(ChannelConversationSpec)` 拿到新 `sessionId` 并落映射，之后才调 `startTurn`。`openConversation` 不做幂等去重，同键重复调用会建出两个会话，去重责任在 `ChannelConversationResolver`，理由是只有渠道侧有映射表可查。理由：会话映射归 `ChannelConversationRepository`，映射未建立时没有会话可写；顺序写反会把轮次挂在空 ID 上，或每条消息都新建会话。否决：`openConversation` 内部按 `description` 幂等，会话域要反向读渠道映射，上下级倒挂。

### 三、Provider 与 spi 签名

1. **`ChannelTurnProvider` 与 `ChannelVisitorApi` 由 channel 声明、session 实现。** 决定：签名见“对外承诺”。理由：channel 是业务域，session 是编排域，业务域不能依赖编排域；下级声明、上级实现是路线图 D25 的既定写法。否决：channel 直接注入 session 的控制器或服务，Modulith 报环。
2. **`ChannelAdapter` 窄接口加 Spring 登记表。** 决定：必需方法 4 个，可选能力用两个窄接口，见 1.2。登记表 `ChannelAdapterRegistry` 由构造器注入 `List<ChannelAdapterFactory>`，按 `platform()` 建 `Map<ChannelType, ChannelAdapterFactory>`。理由：十个平台各有独立协议与凭据，是真实变化点；新增公众号、小程序客服、微信客服时只加一个适配器子包与一个工厂 Bean，登记表与入站管线不动。否决：一个 `switch (platform)` 分支堆十个平台。
3. **回调请求用值对象，适配器不碰 servlet。** 决定：`ChannelCallbackRequest(String method, Map<String,String> headers, Map<String,String> query, byte[] body)` 与 `ChannelCallbackResponder` 两个小接口。理由：适配器单测不启动容器；渠道回包是平台私有的，必须在适配器内决定状态码与形状。否决：适配器方法直接收 `HttpServletRequest`，单测要起 MockMvc，且 servlet 依赖渗入适配器。

### 四、与旧行为不一致的地方

1. **渠道回调地址变化。** 各平台控制台登记的回调地址要从 `/api/v1/im/callback/{channelId}` 换成 `/api/open/v1/channels/{channelId}/callbacks`。系统未上线，部署时一次改完。
2. **嵌入渠道删除未用字段。** `embed_channels.allow_memory` 旧实体有字段但写入路径从不使用，界面也没有开关，新表不建该列。`embed_channels.metadata` 同理删除。控制台界面不变。
3. **删除纯文本错误页配置。** 旧 `EmbedWiring` 把 Jetty 的 `ErrorReportValveClass` 换成 `PlainTextErrorReportValve`，是复刻 Go 版错误页的产物。新实现用 framework 的统一异常处理器，未捕获容器错误的响应体变为标准错误页。影响面只有 404 与 500 页面的内容类型。
4. **渠道列表的智能体名字按请求语言本地化。** 旧实现固定按 `zh-CN` 取内置智能体名字。新实现按 `Accept-Language`，与 spec D2 一致。
5. **IM 并发闸门改用 Redisson 信号量。** 旧实现用 Redis 计数器加 TTL 协调全局与单用户并发。新实现用 Redisson `RSemaphore`，键与阈值不变，配置前缀改为 `ragagent.channel.turn.*`。语义保持：超限时回复“当前排队较多，请稍后再试”，不丢消息。
6. **渠道配置热更新不再广播后重载整渠道，改为只重启受影响渠道。** 旧实现发布 `im:channel:config` 后各实例重载。新实现用 Redisson `RTopic` 发布渠道 ID，持有该渠道领导权的实例停旧连新。语义等价，日志与重连行为更可预测。

# 1. 盘点

数据取自冻结点 `rebuild-freeze`，关键数字已用 `find` 与 `wc -l` 复核。im 主码 `find -name '*.java'` 实测 106 个文件（早期盘点表的合计行多算 1），行数 18,308 与测试侧 im 29 个类 6,661 行、embedchannel 2 个类 215 行均一致。

## 1.1 规模

| 域 | 旧包 | 文件 | 行数 | 旧测试 |
|---|---|---:|---:|---|
| im | `com.ragagent.im` | 106 | 18,308 | 29 个类，6,661 行 |
| embedchannel | `com.ragagent.embedchannel` | 25 | 3,070 | 2 个类，215 行 |

**包规模**（新包预算，含括号内拆分依据）：

```
com.ragagent.channel
├── api/           错误码、渠道类型枚举、三个 Provider 与 BO，约 18 个类型，≤ 900 行
├── adapter/       窄接口 3 个、登记表 2 个、回调请求/回包 2 个，约 900 行
│   ├── dingtalk/ feishu/ mattermost/ qqbot/ slack/ telegram/ wechat/ wecom/ yunzhijia/
├── controller/    5 个控制器，≤ 900 行
├── service/
│   ├── inbound/   入站校验、去重、限流、队列、会话解析、命令，约 1,400 行
│   ├── outbound/  出站格式化、流式渲染、工具展示，约 1,100 行
│   ├── runtime/   生命周期、领导权、配置广播、连接守护，约 900 行
│   ├── admin/     渠道管理用例、凭据校验、机器人标识，约 900 行
│   └── embed/     嵌入渠道管理、鉴权、公开面、限流、令牌，约 1,700 行
├── repository/    3 个，≤ 450 行
├── mapper/        3 个，≤ 200 行
├── entity/        3 个，≤ 400 行
├── bo/            约 10 个 record，≤ 400 行
├── vo/            约 20 个 record，≤ 700 行
└── assembler/     3 个 MapStruct 接口，≤ 250 行
```

主码合计约 9,000 行，低于旧域 21,378 行。差额来自删除 Go 兼容层、`Ops` 回引结构、`Map<String, Object>` 数据传递、`rawBody` 手写绑定与重复的适配器样板。

**超过 400 行的旧类与拆分归宿**（12 个）：

| 旧类（行） | 拆分去向 |
|---|---|
| `im.runtime.ToolDisplay` 761 | `outbound/ChannelToolStepRenderer` ≤300（渲染）、`outbound/ChannelToolStepFormatter` ≤200（参数与结果摘要）、`bo/ChannelToolStep` record |
| `embedchannel.service.EmbedChannelService` 732 | `embed/EmbedChannelAdminService` ≤200、`embed/EmbedChannelPolicy` ≤180、`embed/EmbedChannelLookup` ≤100、`embed/EmbedChannelTokenService` ≤120、`embed/EmbedPublicConfigAssembler` ≤150 |
| `im.channel.wecom.WecomLongConnClient` 660 | `adapter/wecom/WecomBotSocketClient` ≤350、`adapter/wecom/WecomSocketFrames` ≤150 |
| `im.channel.yunzhijia.YunzhijiaAdapter` 642 | `adapter/yunzhijia/YunzhijiaAdapter` ≤250、`adapter/yunzhijia/YunzhijiaCardBuilder` ≤200、`adapter/yunzhijia/YunzhijiaFileClient` ≤150 |
| `im.channel.feishu.FeishuLongConnClient` 541 | `adapter/feishu/FeishuSocketClient` ≤320、`adapter/feishu/FeishuFrameCodec` ≤150 |
| `im.service.ImTurnOrchestrator` 540 | `inbound/ChannelInboundPipeline` ≤220、`inbound/ChannelTurnDispatcher` ≤180、`runtime/ChannelRuntime` ≤200 |
| `im.channel.dingtalk.DingtalkStreamClient` 475 | `adapter/dingtalk/DingtalkSocketClient` ≤300、`adapter/dingtalk/DingtalkFrameCodec` ≤120 |
| `im.channel.wecom.WecomWebhookAdapter` 469 | `adapter/wecom/WecomWebhookAdapter` ≤240、`adapter/wecom/WecomPayloadParser` ≤180 |
| `im.runtime.ImCommandSet` 463 | `inbound/command/SlashCommandCatalog` ≤120、`HelpCommand`、`InfoCommand`、`SearchCommand`、`StopCommand`、`ClearCommand` 各 ≤90 |
| `embedchannel.controller.EmbedChannelController` 447 | `controller/EmbedChannelAdminController` ≤140、`controller/EmbedWidgetBootstrapController` ≤180、`controller/EmbedVisitorSessionController` ≤180、`controller/EmbedVisitorInteractionController` ≤150 |
| `im.service.ImStreamPipeline` 436 | `outbound/ChannelStreamTurn` ≤240、`outbound/ChannelStreamEventReducer` ≤160 |
| `im.controller.ImChannelController` 409 | `controller/ImChannelAdminController` ≤180、`controller/WechatQrcodeAdminController` ≤120 |

**超过 100 行的旧方法与拆分归宿**（6 个）：

| 旧类.方法（行） | 拆分去向 |
|---|---|
| `ImStreamPipeline.subscribeStreamEvents` 136 | `ChannelStreamEventReducer` 按事件族拆成 `onReasoning`、`onAnswer`、`onTool`、`onError` 四个方法，每个 ≤40 行 |
| `ImStreamPipeline.handleMessageStream` 132 | `ChannelStreamTurn.run` 分阶段：准备消息、订阅、等待终态、收尾，各 ≤40 行 |
| `ImQaRunner.runQA` 124 | `ChannelNonStreamTurn.run` 拆成 `subscribe`、`collect`、`finish`，各 ≤40 行 |
| `LarkEventConverter.convert` 113 | `adapter/feishu/LarkEventParser` 按事件类型拆成 `parseMessage`、`parseFile`、`parseQuote` |
| `ImTurnOrchestrator.runHandleMessage` 109 | `ChannelInboundPipeline.handle` 拆成 `ensureTenant`、`guardInbound`、`resolveConversation`、`dispatch` |
| `FeishuCallbackOps.parseCallback` 101 | `FeishuPayloadParser` 拆成 `decode`、`readEvent`、`readMessage` |

## 1.2 多实现点盘点

路线图 3.11 要求只在真实多实现处建窄接口。本计划结论：

| 位置 | 实现数 | 结论 |
|---|---|---|
| 渠道平台：钉钉、飞书、Lark、Mattermost、QQBot、Slack、Telegram、微信个人号、企业微信、云之家 | 10 | **建接口。** 第二个阶段还要新增公众号、小程序客服、微信客服，变化点确定。窄接口 `ChannelAdapter` 见下 |
| 嵌入渠道 | 1 | **不实现 `ChannelAdapter`。** 嵌入没有出站推送、没有平台回调、没有长连接，入站是 HTTP 请求加 SSE 回包，回包通道由 session 持有。它由 `EmbedVisitorAuthFilter` 与四个控制器直接承载，理由见设计决定三.2 |
| 渠道连接模式：webhook、websocket、longpoll | 3 | 不建接口，`ConnectionMode` 枚举，适配器工厂内部按模式分支 |
| 渠道回复模式：stream、full | 2 | 不建接口，`ReplyMode` 枚举，两个用例类 `ChannelStreamTurn`、`ChannelNonStreamTurn` 按模式取用 |
| 会话模式：user、thread | 2 | 不建接口，`SessionMode` 枚举 |
| 渠道平台验签：HMAC 头、AES 解密后比对、常量子串、无验签（QQBot） | 4 类 | 不建接口，各适配器在自己的 `verifyAndParse` 内实现，算法只有本平台用 |
| 斜杠命令：help、info、search、stop、clear | 5 | **建窄接口** `SlashCommand` 加 `SlashCommandCatalog` 登记表。变化点是新增命令，与平台无关 |
| 限流存储：Redis 滑窗、本地滑窗 | 2 | 不建接口，`EmbedRateLimiter` 内按 `redis-enabled` 分支，与旧行为一致 |
| 渠道凭据字段解析：各平台字段名不同 | 10 | 不建接口，凭据字段名常量放各适配器子包 |

**`ChannelAdapter` 方法清单**，包 `com.ragagent.channel.adapter`：

```java
/**
 * 一个即时通讯平台的适配器：验签、解析、回包、可选能力。
 *
 * @author jiangguifan
 */
public interface ChannelAdapter {

	/** 本适配器负责的平台。 */
	ChannelType type();

	/**
	 * 处理一次平台回调：先处理平台握手挑战，再验签、解析并写出 ACK，最后把消息交给入站接收器。
	 * ACK 必须先于入站处理写出，平台有超时。
	 *
	 * @param request   回调请求快照
	 * @param responder 响应写出器，只允许写一次
	 * @param sink      入站消息接收器，ACK 后异步投递
	 */
	void handleCallback(ChannelCallbackRequest request, ChannelCallbackResponder responder, ChannelInboundSink sink);

	/** 回一条完整消息。 */
	void sendReply(ChannelReplyTarget target, ChannelReply reply);

	/** 能否渲染 markdown 图片，不能时出站把图片降级为链接。 */
	boolean rendersMarkdownImages();
}
```

两个可选能力各是一个接口，适配器按需实现，使用方 `instanceof` 判定：

```java
/** 流式回复能力：分片替换同一条用户可见消息。 */
public interface ChannelStreamSender {
	String startStream(ChannelReplyTarget target);
	void updateStream(String streamId, String fullContent);
	void finalizeStream(String streamId, String finalContent);
	void endStream(String streamId);
}

/** 附件下载能力：把平台文件拉成字节与文件名。 */
public interface ChannelFileDownloader {
	ChannelDownloadedFile download(ChannelInboundMessage message);
}
```

`ChannelInboundSink` 是一个函数式接口 `void accept(ChannelInboundMessage message, ChannelContext context)`，由入站管线实现，适配器不依赖管线。`ChannelReplyTarget` 含租户、渠道 ID、平台消息定位字段（用户、会话、线程、额外键）。

**登记表按渠道类型取用**：

```java
/**
 * 渠道适配器登记表：构造器注入全部工厂，按平台建索引。
 *
 * @author jiangguifan
 */
@Configuration
@RequiredArgsConstructor
public class ChannelAdapterConfig {

	private final List<ChannelAdapterFactory> factories;

	@Bean
	public ChannelAdapterRegistry channelAdapterRegistry() {
		return ChannelAdapterRegistry.of(factories);
	}
}
```

`ChannelAdapterFactory` 一个方法：`ConfiguredChannel create(ImChannel channel, ChannelInboundSink sink)`，返回 `ConfiguredChannel(ChannelAdapter adapter, Runnable stopAction)`。`stopAction` 为 null 表示该模式没有长连接。`ChannelAdapterRegistry` 提供 `Optional<ChannelAdapter> adapterOf(ChannelType type)` 与 `Optional<ChannelAdapterFactory> factoryOf(ChannelType type)`。

**第二阶段新增渠道的落地成本**：新增 `adapter/officialaccount/` 一个子包，内含适配器类、工厂类（`@Component`）、凭据字段常量类各一个，`ChannelType` 加一个枚举值，`ChannelCode` 不加码。登记表、入站管线、出站格式化、管理面控制器、前端面板的字段映射表各改一处，都是加行不改结构。

## 1.3 旧接口到新接口

`/api/v1` 下的管理面接口一律改 `/api/admin/v1`。下表逐条给出归类与理由。

**IM 管理面**

| 旧接口 | 新接口 | 归类理由 |
|---|---|---|
| `POST /api/v1/agents/{id}/im-channels` | `POST /api/admin/v1/agents/{agentId}/im-channels` | 后台用户建渠道，管理面 |
| `GET /api/v1/agents/{id}/im-channels` | `GET /api/admin/v1/agents/{agentId}/im-channels` | 同上 |
| `GET /api/v1/im-channels` | `GET /api/admin/v1/im-channels` | 租户级渠道总览，管理面 |
| `PUT /api/v1/im-channels/{id}` | `PUT /api/admin/v1/im-channels/{id}` | 管理面 |
| `DELETE /api/v1/im-channels/{id}` | `DELETE /api/admin/v1/im-channels/{id}` | 管理面 |
| `POST /api/v1/im-channels/{id}/toggle` | `POST /api/admin/v1/im-channels/{id}/toggle` | 管理面，动作式接口 |
| `POST /api/v1/wechat/qrcode` | `POST /api/admin/v1/wechat/qrcodes` | 管理面，资源式建扫码会话 |
| `POST /api/v1/wechat/qrcode/status` | `GET /api/admin/v1/wechat/qrcodes/{qrcode}` | 管理面，幂等查询改 GET |

**渠道平台回调**

| 旧接口 | 新接口 | 归类理由 |
|---|---|---|
| `GET/POST /api/v1/im/callback/{channelId}` | `GET/POST /api/open/v1/channels/{channelId}/callbacks` | 平台服务器直调，不持访客身份，属开放接口组 |

**嵌入渠道管理面**

| 旧接口 | 新接口 | 归类理由 |
|---|---|---|
| `POST /api/v1/agents/{id}/embed-channels` | `POST /api/admin/v1/agents/{agentId}/embed-channels` | 管理面 |
| `GET /api/v1/agents/{id}/embed-channels` | `GET /api/admin/v1/agents/{agentId}/embed-channels` | 管理面 |
| `GET /api/v1/embed-channels` | `GET /api/admin/v1/embed-channels` | 管理面 |
| `GET /api/v1/embed-channels/{channelId}` | `GET /api/admin/v1/embed-channels/{channelId}` | 管理面 |
| `PUT /api/v1/embed-channels/{channelId}` | `PUT /api/admin/v1/embed-channels/{channelId}` | 管理面 |
| `DELETE /api/v1/embed-channels/{channelId}` | `DELETE /api/admin/v1/embed-channels/{channelId}` | 管理面 |
| `POST /api/v1/embed-channels/{channelId}/rotate-token` | `POST /api/admin/v1/embed-channels/{channelId}/rotate-token` | 管理面 |
| `POST /api/v1/embed-channels/{channelId}/preview-session` | `POST /api/admin/v1/embed-channels/{channelId}/preview-session` | 管理面，建预览用访客令牌 |
| `GET /api/v1/embed-channels/{channelId}/stats` | `GET /api/admin/v1/embed-channels/{channelId}/stats` | 管理面 |

**挂件访客面**（保持 `/api/v1` 前缀与层级，访客账号体系）

| 旧接口 | 新接口 | 归类理由 |
|---|---|---|
| `POST /api/v1/embed/{channelId}/exchange` | `POST /api/v1/embed/{channelId}/exchange` | 访客用发布令牌换会话令牌 |
| `GET /api/v1/embed/{channelId}/config` | `GET /api/v1/embed/{channelId}/config` | 访客读公开配置 |
| `GET /api/v1/embed/{channelId}/suggested-questions` | `GET /api/v1/embed/{channelId}/suggested-questions` | 访客读推荐问题 |
| `GET /api/v1/embed/{channelId}/chunks/{chunkId}` | `GET /api/v1/embed/{channelId}/chunks/{chunkId}` | 访客读分块 |
| `GET /api/v1/embed/{channelId}/files` | `GET /api/v1/embed/{channelId}/files` | 访客读附件 |
| `GET/POST /api/v1/embed/{channelId}/sessions` | 同路径 | 访客会话列表与创建 |
| `DELETE /api/v1/embed/{channelId}/sessions/{sessionId}` | 同路径 | 访客删本访客会话 |
| `GET /api/v1/embed/{channelId}/messages/{sessionId}/load` | 同路径 | 访客读历史消息 |
| `POST /api/v1/embed/{channelId}/sessions/{sessionId}/stop` | 同路径 | 访客停止本轮 |
| `GET/POST .../messages/{messageId}/suggestions` | 同路径 | 访客读与生成建议 |
| `POST .../suggestion-events` | 同路径 | 访客记录建议点击 |
| `POST .../events` | 同路径 | 宿主页 webhook 中继 |
| `POST /api/v1/embed/{channelId}/knowledge-chat/{sessionId}` | `POST /api/v1/embed/{channelId}/chat` | 与 agent 通道合并，请求体带 `mode`，见设计决定二.2 |
| `POST /api/v1/embed/{channelId}/agent-chat/{sessionId}` | 同上 | 同上 |
| `POST .../mcp-services/{svcId}/oauth/authorize-url` | 同路径 | 访客发起授权 |
| `GET .../mcp-services/{svcId}/oauth/status` | 同路径 | 访客读授权状态 |
| `POST .../mcp-oauth-resolutions/{pendingId}` | `POST .../mcp-approvals/{approvalId}/resolution` | 与工具审批合并，见设计决定二.3 |
| `POST .../mcp-oauth-resolutions/{pendingId}/cancel` | 同上，`decision=CANCEL` | 同上 |
| `POST .../tool-approvals/{pendingId}` | 同上 | 同上 |

## 1.4 旧表到新表

通用规则：旧 `id varchar(36)` 改 bigint 雪花，`tenant_id integer` 改 bigint，时间改 `timestamptz`，软删改哨兵值，租户表开行级安全。三张表都是租户表。

**`channel_im`**，写属主 `ImChannelRepository`，旧表 `im_channels`：

| 新列 | 旧列 | 说明 |
|---|---|---|
| `id bigint` | `id varchar(36)` | 雪花 |
| `tenant_id bigint NOT NULL` | 同名 | |
| `agent_id bigint NOT NULL` | `agent_id varchar(36)` | 内置智能体用计划 06 的预留 ID |
| `channel_type varchar(32) NOT NULL` | `platform varchar(20)` | `ChannelType` 枚举名 |
| `name varchar(255) NOT NULL DEFAULT ''` | 同名 | |
| `enabled boolean NOT NULL DEFAULT true` | 同名 | |
| `connection_mode varchar(20) NOT NULL` | `mode varchar(20)` | `WEBHOOK`、`WEBSOCKET`、`LONGPOLL` |
| `reply_mode varchar(20) NOT NULL` | `output_mode varchar(20)` | `STREAM`、`FULL` |
| `session_mode varchar(20) NOT NULL DEFAULT 'USER'` | `session_mode varchar(20)` | `USER`、`THREAD` |
| `knowledge_base_id bigint NULL` | `knowledge_base_id varchar(36) DEFAULT ''` | 空串改 NULL |
| `bot_identity varchar(255) NULL` | `bot_identity varchar(255) DEFAULT ''` | 空串改 NULL |
| `credentials_ciphertext text NOT NULL` | `credentials jsonb NOT NULL DEFAULT '{}'` | `@Encrypted`，明文是凭据 JSON |
| `created_at`、`updated_at`、`deleted_at` | 同名 | |

索引：主键；`(tenant_id, agent_id, deleted_at)`；`(tenant_id, created_at DESC)`；**全局唯一** `(bot_identity, deleted_at) WHERE bot_identity IS NOT NULL`。全局唯一是保留旧语义：机器人凭据属于平台侧唯一资源，同一机器人不能同时挂在两个租户的渠道上。旧索引即为全局，新表不按租户收窄。`(bot_identity, deleted_at)` 的写法与路线图 3.5 第 3 条一致，同 `bot_identity` 的多条历史软删行因 `deleted_at` 不同不冲突，只有活着的行受约束。前提：软删时 `deleted_at` 写当前时间戳而不是同一哨兵值，`duplicateBotAllowedAfterSoftDelete` 用例钉住这一点。

**`channel_conversations`**，写属主 `ChannelConversationRepository`，旧表 `im_channel_sessions`：

| 新列 | 旧列 | 说明 |
|---|---|---|
| `id bigint` | `id varchar(36)` | |
| `tenant_id bigint NOT NULL` | 同名 | |
| `channel_id bigint NOT NULL` | `im_channel_id varchar(36) DEFAULT ''` | 空串改 NOT NULL，映射必须指向渠道 |
| `agent_id bigint NOT NULL` | `agent_id varchar(36) DEFAULT ''` | |
| `channel_type varchar(32) NOT NULL` | `platform varchar(20)` | |
| `external_user_id varchar(128) NOT NULL` | `user_id` | |
| `external_chat_id varchar(128) NOT NULL DEFAULT ''` | `chat_id` | 私聊为空串 |
| `external_thread_id varchar(128) NOT NULL DEFAULT ''` | `thread_id` | |
| `session_id bigint NOT NULL` | `session_id varchar(36)` | 不建外键 |
| `status varchar(16) NOT NULL DEFAULT 'ACTIVE'` | `status varchar(20)` | `ACTIVE`、`CLOSED` |
| `created_at`、`updated_at`、`deleted_at` | 同名 | |

丢弃 `metadata jsonb`：旧表有列，代码从未写非空值。索引：唯一 `(channel_type, external_user_id, external_chat_id, tenant_id, agent_id, deleted_at)`；唯一 `(channel_type, external_chat_id, external_thread_id, tenant_id, agent_id, deleted_at) WHERE external_thread_id <> ''`；`(tenant_id, session_id, deleted_at)`；`(tenant_id, channel_id, deleted_at)`。

**`channel_embed`**，写属主 `EmbedChannelRepository`，旧表 `embed_channels`：

| 新列 | 旧列 | 说明 |
|---|---|---|
| `id bigint` | `id varchar(36)` | |
| `tenant_id bigint NOT NULL` | 同名 | |
| `agent_id bigint NOT NULL` | `agent_id varchar(36) DEFAULT 'builtin-quick-answer'` | 默认值改为计划 06 的 `BuiltinAgentCode.QUICK_ANSWER` 预留 ID |
| `name varchar(255) NOT NULL` | 同名 | |
| `enabled boolean NOT NULL DEFAULT true` | 同名 | |
| `publish_token_ciphertext text NOT NULL` | `publish_token varchar(64)` | `@Encrypted` |
| `publish_token_sha256 char(64) NOT NULL` | 同上 | 十六进制小写，建唯一索引与等值查询 |
| `allowed_origins jsonb NOT NULL DEFAULT '[]'` | 同名 | `JacksonTypeHandler` |
| `welcome_message text`、`primary_color varchar(32)`、`page_title varchar(255)` | 同名 | |
| `header_title_mode varchar(32) NOT NULL DEFAULT 'CHANNEL'` | `varchar(32) DEFAULT 'channel'` | 枚举名 |
| `show_suggested_questions boolean NOT NULL DEFAULT true`、`show_thinking boolean NOT NULL DEFAULT false` | 同名 | |
| `widget_position varchar(32) NOT NULL DEFAULT 'BOTTOM_RIGHT'` | `varchar(32)` | 枚举名 |
| `allow_web_search boolean NOT NULL DEFAULT false`、`allow_file_upload boolean NOT NULL DEFAULT false` | 同名 | |
| `default_locale varchar(16) NOT NULL DEFAULT ''` | 同名 | 空串表示跟随请求 |
| `rate_limit_per_minute int NOT NULL DEFAULT 30`、`rate_limit_per_day int NOT NULL DEFAULT 10000` | 同名 | |
| `webhook_url varchar(512)` | 同名 | |
| `webhook_secret_ciphertext text` | `webhook_secret varchar(128)` | `@Encrypted` |
| `launcher_icon text` | 同名 | data URL |
| `created_at`、`updated_at`、`deleted_at` | 同名 | |

丢弃 `allow_memory` 与 `metadata`：旧实体有字段，写入路径从不使用，界面无开关。索引：主键；`(tenant_id, agent_id, deleted_at)`；`(tenant_id, created_at DESC)`；唯一 `(publish_token_sha256, deleted_at)`。

**跨租户访问被拒测试**：三张表各一条 `tenant2CannotReadTenant1Rows` 与 `tenant2CannotInsertRowForTenant1`，共六条，在 Task 1 里写。

## 1.5 旧类到新类

**Ops、Support、Facade 类与外提协作者**（15 个，全部拆解，不换名搬运）：

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `ImChannelRuntimeOps` 394 | `runtime/ChannelRuntime` ≤200（启动停止与运行态表）、`runtime/ChannelLeaderLease` ≤120（领导权续期与接管）、`runtime/ChannelConfigBroadcast` ≤90（配置变更广播与订阅） | 生命周期、选主、广播三个变化原因，且分发与收发不相关；连接守护见第二张表 |
| `im.channel.wecom.WecomSupport` 274 | `adapter/wecom/WecomSignature` ≤140（签名与解密）、`adapter/wecom/WecomApiClient` ≤150（出站 API） | 密码学与 HTTP 出站分开 |
| `embedchannel.controller.EmbedChannelDelegateOps` 268 | `embed/EmbedVisitorChatService` ≤150（问答委托）、`embed/EmbedVisitorApprovalService` ≤120（MCP 授权与工具审批） | 问答与会话内审批是两条用例 |
| `im.channel.feishu.FeishuCardStreamOps` 253 | `adapter/feishu/FeishuCardStream` ≤250 | 单一变化原因，去后缀 |
| `im.channel.feishu.FeishuCallbackOps` 234 | `adapter/feishu/FeishuCallbackVerifier` ≤110、`adapter/feishu/FeishuPayloadParser` ≤150 | 验签与解析分开，且解析方法 101 行必须拆 |
| `embedchannel.controller.EmbedChannelPublicOps` 214 | `embed/EmbedWidgetBootstrapService` ≤180 | 单一变化原因 |
| `im.channel.feishu.FeishuMediaOps` 210 | `adapter/feishu/FeishuMediaClient` ≤210 | 去后缀 |
| `embedchannel.controller.EmbedChannelMgmtOps` 197 | `embed/EmbedChannelAdminService` ≤200 | 去后缀 |
| `im.service.ImStopOps` 168 | `inbound/ChannelTurnCanceller` ≤140 | 去后缀，职责是取消在途轮次 |
| `im.channel.feishu.FeishuSendOps` 148 | `adapter/feishu/FeishuMessageSender` ≤150 | 去后缀 |
| `embedchannel.controller.EmbedChannelRows` 135 | `assembler/EmbedChannelAssembler` ≤140 | 去后缀，MapStruct 接口 |
| `im.service.ImInboundGuardOps` 121 | `inbound/ChannelInboundGuard` ≤140 | 去后缀 |
| `im.service.ImKnowledgeBridgeOps` 114 | `inbound/ChannelAttachmentIngest` ≤110 | 去后缀，改为调 `knowledge.api` |
| `im.dto.ImChannelRows` 112 | `assembler/ImChannelAssembler` ≤140 | 去后缀 |
| `embedchannel.PlainTextErrorReportValve` 55 | 删除 | 复刻 Go 错误页的产物，由 framework 异常处理器取代 |

**外提协作者与其余大类**（按变化原因拆，括号内为旧行数）：

| 旧类 | 新类 | 拆分依据 |
|---|---|---|
| `ImTurnOrchestrator` 540 | `inbound/ChannelInboundPipeline`、`inbound/ChannelTurnDispatcher`、`runtime/ChannelRuntime` | 入站校验、轮次派发、运行态 |
| `ImChannelService` 368 | `admin/ImChannelAdminService`（增删改查）、`admin/ChannelBotIdentity`（机器人标识与查重）、`admin/ChannelCallbackGuard`（回调前的渠道三态判定） | 三个变化原因；回调判定不能与后台写用例混在一起 |
| `ImChannelRuntimeOps`、`ImSupervisor` 91 | `runtime/ChannelConnectionSupervisor` | 守护循环，6 小时重建周期与 5 秒退避迁自 `ImSupervisor`；`ImChannelRuntimeOps` 的守护部分（连接存活探测与重建判定）一并并入本类，其余部分见上一行 |
| `QaQueue` 378 | `inbound/ChannelTurnQueue` ≤260、`config/ChannelTurnProperties` | 队列算法与配置 |
| `ImRedisStore` 274 | `inbound/ChannelDedupCache` ≤90、`inbound/ChannelRateWindow` ≤110、`runtime/ChannelLeaderLock` ≤90 | 去重、限流、选主三个键族，生命周期不同 |
| `ImFormat` 391 | `inbound/ChannelUserKey` ≤70、`inbound/ChannelTextLimit` ≤90、`inbound/ChannelFileMessage` ≤80、`outbound/ChannelFailureReply` ≤70 | 每种格式规则一个类 |
| `ImCommandSet` 463 | `SlashCommandCatalog` 加五个命令类 | 一个命令一个变化原因 |
| `Commands` 159 | `SlashCommand` 接口、`SlashCommandParser` ≤120 | 解析与执行分开 |
| `ImQaRequests` 92 | `inbound/ChannelTurnRequestFactory` ≤120 | 去后缀，构造 `ChannelTurnRequest` |
| `ImSessionResolver` 129 | `inbound/ChannelConversationResolver` ≤150 | 去后缀 |
| `ImAttachmentPreparer` 227 | `inbound/ChannelAttachmentPreparer` ≤200 | 去后缀内容不变 |
| `ImOutboundFormatter` 86 | `outbound/ChannelOutboundFormatter` ≤120 | 去后缀 |
| `ImQaRunner` 299 | `outbound/ChannelNonStreamTurn` ≤200 | 全量回复一个用例 |
| `ImStreamPipeline` 436 | `outbound/ChannelStreamTurn`、`outbound/ChannelStreamEventReducer`、`bo/ChannelStreamBuffer` | 轮次编排、事件归约、缓冲 |
| `ToolDisplay` 761 | `outbound/ChannelToolStepRenderer`、`outbound/ChannelToolStepFormatter`、`bo/ChannelToolStep` | 渲染、摘要、数据结构 |
| `ThinkDisplay` 275 | `outbound/ChannelThinkSplitter` ≤220 | 去后缀 |
| `AdapterInterfaces` 101 | `adapter/ChannelAdapter`、`adapter/ChannelStreamSender`、`adapter/ChannelFileDownloader` | 必需能力与两个可选能力分离 |
| `CallbackExchange` 151 | `adapter/ChannelCallbackRequest` record、`adapter/ChannelCallbackResponder` 接口 | 值对象取代 servlet 包装 |
| `IncomingMessage` 67 | `adapter/ChannelInboundMessage` record | 改用 record，字段不变 |
| `ReplyMessage` 29 | `adapter/ChannelReply` record | 同上 |
| `ImCredentials` | `admin/ChannelCredentialReader` ≤110、`admin/ChannelCredentialColumn` ≤40 | 读字段与拼存储列分开 |
| `ImTypes` 47 | `api/ChannelType`、`api/ConnectionMode`、`api/ReplyMode`、`api/SessionMode`、`api/ChannelMessageKind` | 字符串常量改枚举 |
| `ImAdapterVerify` 90 | 删除 | 验签向量已按平台下沉到各适配器的测试 |
| `FeishuWecomCrypt` 185 | `adapter/feishu/FeishuAesCipher`、`adapter/wecom/WecomAesCipher` | 两种算法各自独立，共用类只是行数巧合 |
| `SlackAdapterCore` 155 | `adapter/slack/SlackEventParser` | 去核心后缀 |
| `StreamSection` | `bo/ChannelStreamBuffer` 的组成部分 | 并回使用方 |
| `EmbedChannelService` 732 | 五个类，见 1.1 | 管理用例、策略校验、公开查询、令牌、公开配置视图 |
| `EmbedAuthFilter` 246 | `embed/EmbedVisitorAuthFilter` ≤180、`embed/EmbedOriginMatcher` ≤80 | 过滤链与 Origin 匹配分开 |
| `EmbedChannelController` 447 | 四个控制器，见 1.1 | 按资源与账号面拆 |
| `support/EmbedTokens` 97 | `embed/EmbedTokenCodec` ≤110 | 去后缀 |
| `support/EmbedError` 73 | 删除 | 改 `ChannelCode` 与业务异常 |
| `EmbedWiring` 49 | 删除 | 错误页配置不再需要 |
| `repository/RedisEmbedTokenStore` | `embed/EmbedSessionTokenCache` ≤90 | 名字表达用途 |

**每个平台一个 adapter 子包的后缀对照**：`FeishuCallbackOps` → `FeishuCallbackVerifier` + `FeishuPayloadParser`；`FeishuSendOps` → `FeishuMessageSender`；`FeishuMediaOps` → `FeishuMediaClient`；`FeishuCardStreamOps` → `FeishuCardStream`；`WecomSupport` → `WecomSignature` + `WecomApiClient`；`SlackAdapterCore` → `SlackEventParser`。

## 1.6 旧队列与后台任务去向

| 旧机制 | 新做法 | 角色 |
|---|---|---|
| `ImCallbackHandler` 的虚拟线程异步处理 | 回调 ACK 后投给 `ChannelTurnQueue`，不新建裸线程 | worker |
| `QaQueue` 有界队列加 Redis 计数 | `ChannelTurnQueue`：Redisson 信号量控全局与单用户并发，队列上限与超时语义保留，配置前缀 `ragagent.channel.turn.*` | worker |
| `ImChannelRuntimeOps` 领导权续期线程（5 秒） | `ChannelLeaderLease` 用 Spring `TaskScheduler` 固定频率，只在该渠道持锁实例上运行 | worker |
| 领导权抢锁重试线程（10 秒） | 同上，`ChannelLeaderLease` 的接管任务 | worker |
| Redis pub/sub `im:channel:config` 订阅 | `ChannelConfigBroadcast` 用 Redisson `RTopic`，载荷 `{channelId, sourceInstance}` 保留，忽略自身来源 | worker |
| 启动时加载启用渠道 | `ChannelRuntime.startEnabledChannels` 在 `ApplicationReadyEvent` 触发，仅 worker 角色执行 | worker |
| 渠道长连接守护线程 | `ChannelConnectionSupervisor` 周期性重建连接，取消时先拆连接 | worker |
| `/stop` 跨实例停止 | `ChannelTurnCanceller`：Redisson 停止标记加 `ChannelTurnProvider.cancelTurn` | worker |
| 附件入知识库虚拟线程 | `ChannelAttachmentIngest` 投 JobRunr 任务，任务参数带租户 | worker |
| `EmbedRateLimiter` 三段滑窗 | 保留同一算法，Redis 用 `RScoredSortedSet`，关闭时回落本地滑窗 | api 与 worker 均可 |
| `ImRedisRuntime` 的 null 对象 | 删除，改为条件装配的 `ChannelCacheConfig` | 装配 |

## 1.7 跨域依赖与新做法

| 旧依赖 | 新做法 |
|---|---|
| `contract.agent.AgentLookup` | `agent.api.AgentQueryApi`（计划 06） |
| `contract.session.SessionQaPort`、`EmbedSessionPort`、`EmbedSessionOps`、`SessionView`、`MessageView`、`QaRequestPayload` | 删除。channel 在 `channel.api` 声明 `ChannelTurnProvider` 与 `ChannelVisitorApi`，计划 08 的 session 实现 |
| `contract.session.SessionView`、`MessageView` 作为跨域传输 | 渠道侧只用自己 `api` 包里的 `VisitorSessionBo`、`ChannelMessageBo`、`ChannelTurnOutcome`，不出现会话域类型。流分发由 framework.stream 承接，见计划 08 |
| `contract.knowledge.KnowledgeCatalogPort`、`ChunkRowView` | `knowledge.api` 的分块读取、知识库校验、附件入库 |
| `contract.doc.DocReaderParsePort` | 删除。渠道附件统一经 `knowledge.api` 入库，解析在 knowledge 域内 |
| `contract.tool.McpApprovalOps` | `mcp.api.McpApprovalApi`、`McpOAuthApi`（计划 06） |
| `contract.tenant.TenantConfigLookup` | `tenant.api` 租户存在性 |
| `contract.storage.support.StorageUrlContext`、`platform.storage.fileserve.FileProxyPort` | `storage.api` 的资源句柄与文件读取 |
| `platform.stream.StreamManager`、`contract.event.EventBusFactory` | 删除。流分发由 framework.stream 承接，见计划 08；渠道侧流式反馈改由 `ChannelTurn.onEvent` 与 `ChannelVisitorApi` 的 SSE 承载 |
| `platform.access.TenantContext` | `framework.tenant.TenantContextHolder`，回调线程用显式租户设置 |
| `platform.redis.RedisPubSub` | Redisson `RTopic` |
| `platform.security.SsrfGuard` | framework 的 `SsrfGuard` Bean |
| `platform.security.CryptoService`、`SYSTEM_AES_KEY` | `@Encrypted`，密钥配置 `gac.furnace.mybatis.encrypt-key` |
| `platform.config.DeploymentProperties.isProduction()` | `system.api` 的部署形态查询 |
| `platform.security.LogSanitizer` | framework 日志脱敏 Appender |
| `platform.web.ApiResponse`、`JsonMappers` | `RestResponse`、framework Jackson 配置 |
| `boot/config/ImAdapterWiringConfig` 注册十个工厂 | `ChannelAdapterConfig` 构造器注入全部工厂，不再手动逐个 `registerAdapterFactory` |

## 1.8 旧测试处置

处置只取三种：移植业务断言、改写为新规范、丢弃。丢弃理由只写“钉 Go 语义”。

| 旧测试类（行） | 测什么 | 处置 |
|---|---|---|
| `channel.dingtalk.DingtalkAdapterTest` 596 | 解析、验签、卡片、Go 文案 | 移植验签与解析断言到 `DingtalkAdapterTest`；Go 文案行丢弃，改为错误码断言 |
| `channel.dingtalk.DingtalkStreamClientTest` 235 | WS 帧与重连 | 移植帧解析到 `DingtalkFrameCodecTest`，重连语义改写为 `ChannelConnectionSupervisorTest` 一个用例 |
| `channel.feishu.FeishuAdapterTest` 551 | 事件、验签、卡片 | 移植到 `FeishuAdapterTest` 与 `FeishuAesCipherTest` |
| `channel.feishu.FeishuLongConnClientTest` 277 | WS 分片与重连 | 移植分片到 `FeishuFrameCodecTest`，Go 默认值断言丢弃 |
| `channel.feishu.LarkFrameTest` 113 | Lark 帧逐字节相等 | 丢弃，字节形状钉 Go 语义；新测试断言解出的字段值 |
| `channel.mattermost.MattermostAdapterTest` 408 | webhook 与线程 | 移植到 `MattermostAdapterTest` |
| `channel.qqbot.QqBotAdapterTest` 247 | 网关与消息 | 移植到 `QqBotAdapterTest`；Go 文案行丢弃 |
| `channel.slack.SlackAdapterTest` 278 | 事件与签名 | 移植到 `SlackAdapterTest` |
| `channel.telegram.TelegramAdapterTest` 318 | 更新解析与长轮询 | 移植到 `TelegramAdapterTest` |
| `channel.wechat.WechatAdapterTest` 396 | iLink 长轮询、AES 附件 | 移植到 `WechatAdapterTest`；Go 文案行丢弃 |
| `channel.wechat.WechatQRCodeServiceTest` 125 | 扫码状态机 | 移植到 `WechatQrcodeServiceTest` |
| `channel.wecom.WecomLongConnTest` 268 | WS 帧与心跳 | 移植帧到 `WecomSocketFramesTest` |
| `channel.wecom.WecomWebhookAdapterTest` 406 | 解密与验签 | 移植到 `WecomWebhookAdapterTest`；Go 文案行丢弃 |
| `channel.yunzhijia.YunzhijiaAdapterTest` 580 | 验签、ACK、Go 向量 | 移植验签到 `YunzhijiaAdapterTest`；独立 Go 程序录的向量丢弃 |
| `controller.ImChannelWechatQrcodeTest` 125 | 扫码接口与租户 | 改写为 `WechatQrcodeAdminControllerTest`，MockMvc |
| `domain.ImCredentialsTypeHandlerTest` 89 | AES-GCM 与明文降级 | 丢弃，`@Encrypted` 由 framework 测试覆盖；新增 `ImChannelCredentialRoundTripTest` 断言密文不等于明文 |
| `runtime.FeishuWecomCryptTest` 91 | 飞书与企微 AES | 移植断言到 `FeishuAesCipherTest`、`WecomAesCipherTest`，fixture 复用 `contracts/w5g3b-im-crypt.tsv` |
| `runtime.ImAdapterVerifyTest` 75 | 各平台验签向量 | 移植到各适配器测试，fixture 复用 `contracts/w5g3-im-adapter-signatures.tsv` |
| `runtime.ImFoundationContractTest` 349 | 基础契约金片 | 丢弃，契约面已重设计，金片钉 Go 语义 |
| `runtime.ImFormatMediaTest` 81 | 媒体格式与 URL | 移植到 `ChannelFileMessageTest` |
| `runtime.ImFormatPlatformChannelTest` 33 | 平台到渠道映射 | 移植到 `ChannelTypeTest`，断言十个枚举值与平台名一一对应 |
| `runtime.ImRedisStoreTest` 116 | 限流、选主、去重 | 改写为 `ChannelDedupCacheTest`、`ChannelRateWindowTest`、`ChannelLeaderLockTest`，用 Testcontainers Redis |
| `runtime.QaQueueTest` 234 | 队列限额与全局闸门 | 移植到 `ChannelTurnQueueTest` |
| `runtime.SlackAdapterCoreTest` 109 | Slack 事件核心 | 移植到 `SlackEventParserTest` |
| `service.ImAttachmentPreparerTest` 255 | 附件下载与解析 | 移植到 `ChannelAttachmentPreparerTest` |
| `service.ImChannelServiceTest` 58 | 渠道服务 | 改写为 `ImChannelAdminServiceTest` |
| `service.ImInboundGuardOpsTest` 75 | 去重与限流 | 并入 `ChannelInboundGuardTest` |
| `service.ImLifecycleWiringTest` 51 | 生命周期装配 | 改写为 `ChannelRuntimeTest` |
| `service.ImStopOpsTest` 122 | 跨实例停止链路 | 改写为 `ChannelTurnCancellerTest` |
| `support.EmbedRateLimiterTest` | 三段限流双路径 | 移植到 `EmbedRateLimiterTest`，Redis 不可用时跳过 |
| `controller.EmbedChatPayloadPatchTest` | 渠道约束注入聊天载荷 | 改写为 `EmbedVisitorChatServiceTest`，断言 `ChannelTurnRequest` 字段 |

## 1.9 错误码

模块名 `ragagent`，模块号 017。`ChannelCode` 放 `channel/api`。

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001017001` channel.not_found | 操作或回调引用不存在的渠道 | 渠道不存在 | Channel not found |
| ragagent | `1001017002` channel.disabled | 向已禁用的渠道发消息或访问其挂件 | 渠道已禁用 | Channel is disabled |
| ragagent | `1001017003` channel.adapter_unavailable | 平台没有可用适配器，或适配器启动失败 | 渠道平台不可用 | Channel platform is unavailable |
| ragagent | `1001017004` channel.invalid_platform | 建渠道时平台不在白名单 | 不支持的渠道平台 | Unsupported channel platform |
| ragagent | `1001017005` channel.duplicate_bot | 同一机器人凭据已绑定其他渠道 | 该机器人已绑定其他渠道 | This bot is already bound to another channel |
| ragagent | `1001017006` channel.invalid_session_mode | 会话模式取值不在枚举内 | 会话模式不合法 | Invalid session mode |
| ragagent | `1001017007` channel.callback_parse_failed | 回调体无法解析成消息 | 回调内容无法解析 | Callback content cannot be parsed |
| ragagent | `1001017008` channel.callback_verify_failed | 回调验签失败 | 回调验签失败 | Callback verification failed |
| ragagent | `1001017009` channel.agent_not_found | 建或改渠道时智能体不存在 | 智能体不存在 | Agent not found |
| ragagent | `1001017010` channel.queue_rejected | 并发与排队额度用尽 | 当前排队较多，请稍后再试 | Too many requests, please try again later |
| ragagent | `1001017011` channel.turn_provider_missing | 部署未装配对话入口实现 | 对话能力尚未就绪 | Conversation capability is not ready |
| ragagent | `1001017012` channel.qrcode_invalid | 扫码状态查询缺参数或已过期 | 扫码状态无效 | Invalid QR code status |
| ragagent | `1001017013` embed.not_found | 挂件渠道不存在 | 嵌入渠道不存在 | Embed channel not found |
| ragagent | `1001017014` embed.disabled | 挂件渠道已禁用 | 嵌入渠道已禁用 | Embed channel is disabled |
| ragagent | `1001017015` embed.token_invalid | 发布令牌或会话令牌无效 | 嵌入渠道或令牌无效 | Invalid embed channel or token |
| ragagent | `1001017016` embed.token_unavailable | 会话令牌存储不可用 | 会话令牌暂不可用 | Session tokens are unavailable |
| ragagent | `1001017017` embed.origin_denied | 请求来源不在白名单 | 来源不在白名单 | Origin is not allowed |
| ragagent | `1001017018` embed.rate_limited | 超过每分钟限额 | 请求过于频繁，请稍后再试 | Rate limit exceeded, please try again later |
| ragagent | `1001017019` embed.daily_quota_exceeded | 超过每日限额 | 今日请求已达上限 | Daily request limit exceeded |
| ragagent | `1001017020` embed.session_not_owned | 会话不属于该访客或渠道 | 会话不存在 | Session not found |
| ragagent | `1001017021` embed.session_signature_invalid | 会话签名校验失败 | 会话签名无效 | Session signature is invalid |
| ragagent | `1001017022` embed.invalid_visitor | 访客标识为空、含控制字符或过长 | 访客标识不合法 | Invalid embed visitor id |
| ragagent | `1001017023` embed.invalid_origin_config | 生产环境 Origin 白名单为空或含通配 | 来源白名单配置不合法 | Invalid origin configuration |
| ragagent | `1001017024` embed.invalid_webhook | webhook 地址非法或指向内网 | webhook 地址不合法 | Invalid webhook URL |
| ragagent | `1001017025` embed.invalid_launcher_icon | 启动图标类型或大小不合规 | 启动图标不合法 | Invalid launcher icon |
| ragagent | `1001017026` embed.publish_token_required | 兑换会话令牌时未带发布令牌 | 需要发布令牌 | Publish token is required |
| ragagent | `1001017027` embed.chunk_not_found | 分块不存在 | 分块不存在 | Chunk not found |
| ragagent | `1001017028` embed.chunk_forbidden | 分块不在渠道可读范围 | 分块不可访问 | Chunk is not accessible |
| ragagent | `1001017029` embed.event_unsupported | 宿主页事件类型不在白名单 | 不支持的事件类型 | Unsupported event type |
| ragagent | `1001017030` embed.workspace_unavailable | 渠道所属租户不可用 | 工作区不可用 | Workspace is unavailable |

七语种由机器翻译出初稿，同一提交入库，标注待人工校对。`app-messages*.properties` 追加这 30 个 key。

## 1.10 `@IgnoreTenant` 白名单

| 位置 | 原因 |
|---|---|
| `ImChannelRepository.findByIdAcrossTenants(long)` | 平台回调只带渠道 ID，必须先跨租户反查渠道才能得到租户。系统通道 |
| `EmbedChannelRepository.findByIdAcrossTenants(long)` | 挂件公开面只带渠道 ID，同上。系统通道 |
| `ImChannelRepository.listEnabledAcrossTenants()` | worker 启动时跨租户加载全部启用渠道。后台调度系统通道 |
| `ChannelConversationRepository.deleteOrphansAcrossTenants()` | 每日清理指向已删会话的映射，全局扫。后台调度系统通道 |

四处都在代码里写一行中文注释说明原因，并在 `docs/migration/progress.md` 的 `## IgnoreTenant 白名单` 下登记。`AgentDeletedEvent` 监听器不使用 `@IgnoreTenant`：事件带 `tenantId`，监听器显式设置租户上下文。

## 1.11 权限点

权限点命名以计划 03 为准，本计划使用下列逻辑名，计划 03 没有对应项时登记偏离。

| 逻辑名 | 旧要求 | 用于 |
|---|---|---|
| `channel:read` | 登录用户可读本租户渠道 | `GET /api/admin/v1/im-channels`、`GET /api/admin/v1/agents/{agentId}/im-channels` |
| `channel:write` | Admin | IM 渠道增删改、切换、扫码 |
| `embed-channel:manage` | Admin，API Key 能力 `manage_channels` | 嵌入渠道全部管理面接口 |

挂件公开面不走权限点，走 `EmbedVisitorAuthFilter` 的令牌链。

## 1.12 前端 API 层切换表

**`frontend/src/api/agent/index.ts`**（只改 IM 与扫码函数；智能体函数归计划 06）

| 旧函数 | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|
| `listIMChannels` | `GET /api/v1/agents/{agentId}/im-channels` | `GET /api/admin/v1/agents/{agentId}/im-channels` | 是 | `tenantId` 转 number，`agentId` 字符串 |
| `listAllIMChannels` | `GET /api/v1/im-channels` | `GET /api/admin/v1/im-channels` | 是 | 同上 |
| `createIMChannel` | `POST /api/v1/agents/{agentId}/im-channels` | `POST /api/admin/v1/agents/{agentId}/im-channels` | 是 | 请求体字段名不变，枚举值改大写 |
| `updateIMChannel` | `PUT /api/v1/im-channels/{id}` | `PUT /api/admin/v1/im-channels/{id}` | 是 | 同上 |
| `deleteIMChannel` | `DELETE /api/v1/im-channels/{id}` | `DELETE /api/admin/v1/im-channels/{id}` | 是 | 旧返回 `{success:true}`，新返回 `RestResponse`，外壳由 API 客户端解包，调用方拿 `undefined` |
| `toggleIMChannel` | `POST /api/v1/im-channels/{id}/toggle` | `POST /api/admin/v1/im-channels/{id}/toggle` | 是 | |
| `getWeChatQRCode` | `POST /api/v1/wechat/qrcode` | `POST /api/admin/v1/wechat/qrcodes` | 是 | |
| `pollWeChatQRCodeStatus` | `POST /api/v1/wechat/qrcode/status` | `GET /api/admin/v1/wechat/qrcodes/{qrcode}` | 是 | 参数从请求体改路径变量 |

**`frontend/src/api/embed/index.ts`**

| 旧函数 | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|
| `listEmbedChannels`、`listAllEmbedChannels`、`createEmbedChannel`、`getEmbedChannel`、`updateEmbedChannel`、`deleteEmbedChannel`、`rotateEmbedToken`、`getEmbedChannelStats`、`issueEmbedPreviewSession` | `/api/v1/embed-channels...`、`/api/v1/agents/{agentId}/embed-channels` | 换 `/api/admin/v1` 前缀 | 是 | `tenantId` 转 number，`agentId` 字符串；`deleteEmbedChannel` 返回 `undefined` |
| `exchangeEmbedSession`、`getEmbedConfig`、`getEmbedSuggestedQuestions`、`getEmbedChunkById`、`createEmbedSession`、`listEmbedVisitorSessions`、`deleteEmbedVisitorSession`、`getEmbedMessageList`、`stopEmbedSession`、`getEmbedMessageSuggestions`、`ensureEmbedMessageSuggestions`、`recordEmbedMessageSuggestionEvent`、`relayEmbedWebhookEvent` | `/api/v1/embed/{channelId}/...` | 路径不变，只换传输为 `createApiClient` | 是 | 流式与 `Authorization: Embed <token>` 的请求仍走原 `fetch`，不进 `createApiClient` |
| `resolveEmbedMCPOAuth`、`cancelEmbedMCPOAuth`、`resolveEmbedToolApproval` | 两条路径加一条 cancel | `POST /api/v1/embed/{channelId}/sessions/{sessionId}/mcp-approvals/{approvalId}/resolution` | 请求体改 | 三个函数保持签名，内部映射 `decision` 为 `AUTHORIZE`、`REJECT`、`CANCEL` |
| `getEmbedMCPOAuthAuthorizeURL`、`getEmbedMCPOAuthStatus` | `/mcp-services/{svcId}/oauth/...` | 路径不变 | 是 | |

**两处非 API 层的路径字面量**，登记为偏离：

| 文件 | 内容 | 处理 |
|---|---|---|
| `frontend/src/components/IMChannelPanel.vue:942` | `` `${base}/api/v1/im/callback/${channel.id}` `` | 改为 `/api/open/v1/channels/${channel.id}/callbacks`。这是给用户复制到平台控制台的地址，不是接口调用，API 层封装不到 |
| `frontend/src/composables/useEmbedChatSession.ts:347-348` | SSE 路径两条 | 改为调用 `api/embed` 新增的 `embedChatUrl(channelId)`，两条路径合一，另传 `mode` |

**`frontend/src/api/embed/index.ts` 新增导出**：`embedChatUrl(channelId: string): string` 返回 `/api/v1/embed/{channelId}/chat`，供 composable 使用。`frontend/embed-main.ts` 本身不含接口路径，不需要改。

## 1.13 界面端到端域标签

计划 01 的 `tests/e2e/PAGES.md` 已给每个页面标域。本计划负责转绿的行：

| 域标签 | 页面与流程 | 用例 |
|---|---|---|
| `@channel` | IM 渠道列表（设置 `?section=integration-im`）：打开新建对话框，选平台后取消 | `channel/integration-im.spec.ts` |
| `@channel` | 嵌入渠道列表（设置 `?section=integration-embed`）：新建嵌入渠道，嵌入代码出现，记录渠道 ID 回填 `E2E_CHANNEL_ID` | `channel/integration-embed.spec.ts` |
| `@channel` | 挂件首屏（`/embed/:channelId`）：欢迎语与输入框可见，无报错 | `channel/widget-shell.spec.ts` |
| `@channel` | 智能体编辑弹窗内的两个渠道面板：切换标签页、打开新建对话框再取消 | `channel/agent-channel-panels.spec.ts` |
| `@session` | 挂件对话（输入问题、回答气泡、历史抽屉） | `session/widget-chat.spec.ts`，**本计划只做接口级验证，用例在计划 08 转绿** |

冒烟用例：计划 01 的 `tests/api-smoke` 里挂件问答一条（`POST /api/v1/embed/{channelId}/chat`）由计划 01 标 `test.fixme`，**在计划 08 转绿**。本计划转绿的是渠道回调一条，新增在 Task 16：`POST /api/open/v1/channels/{channelId}/callbacks` 带伪造签名的请求返回验签失败，不带渠道 ID 返回 404，回包为纯 JSON。

## 路径缩写

`M` = `backend/app/src/main/java/com/ragagent`，`T` = `backend/app/src/test/java/com/ragagent`，`R` = `backend/app/src/main/resources`，`DB` = `R/db/migration/allenv`。脚本文件名里的 `<D>` 是写脚本当天的 `yyyyMMdd`，序号取 `ls DB | grep <D>` 的下一个。`MVN` 见 Global Constraints。

每个任务固定五步：写失败测试、跑红、实现、跑绿、提交。跑红与跑绿都用 `MVN test -Dtest=<该任务测试类>`，跑红预期 FAIL（类不存在或断言不成立），跑绿预期 PASS。提交前跑一次 `MVN checkstyle:check`，预期无违规。提交后在 `docs/migration/progress.md` 追加一行。

# 2. 任务

## 批次 A：盘点核对与表结构

### Task 1: 盘点核对、DDL 与行级安全

**Files:**
- Create: `DB/V<D>_01__DDL_rebuild_channel_create_tables_jiangguifan.sql`
- Create: `T/channel/{ChannelRowLevelSecurityTest,ChannelSchemaTest}.java`

**Interfaces:**
- Consumes: 计划 01 Task 5 的 fail-closed 行级安全策略模板
- Produces: `channel_im`、`channel_conversations`、`channel_embed` 三张表，列定义见 1.4

- [x] **Step 1: 写失败测试**
  - 先读 `docs/migration/frozen-bugs.md` 中涉及 im 与 embedchannel 的条目，逐条写进 `docs/migration/progress.md` 本任务行并判断是否影响本计划。
  - 核对：`find domains/src/main/java/com/ragagent/im -name '*.java' | wc -l` 预期 106，`find domains/src/main/java/com/ragagent/embedchannel -name '*.java' | wc -l` 预期 25。有差异先改 1.1。
  - `ChannelRowLevelSecurityTest`：`tenant2CannotReadTenant1Rows_channelIm`、`..._channelConversations`、`..._channelEmbed` 与对应的 `tenant2CannotInsertRowForTenant1_*`，共六条。
  - `ChannelSchemaTest.liveDuplicatePublishTokenRejected`、`recreatePublishTokenAfterSoftDeleteAccepted`、`globalBotIdentityUniquenessSpansTenants`、`threadLookupIndexOnlyAppliesWhenThreadPresent`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ChannelRowLevelSecurityTest,ChannelSchemaTest'`。
- [x] **Step 3: 实现**：单份 DDL 脚本建三张表，每张 `ENABLE ROW LEVEL SECURITY` 并 `FORCE`，策略用 `tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint OR current_setting('app.tenant_id', true) = 'system'`。`channel_conversations` 不建到 `sessions` 的外键。三张表的 `deleted_at` 默认 `'1970-01-01 00:00:00+00'`。
- [x] **Step 4: 跑绿**。反向验证：测试容器里对 `channel_im` 执行 `ALTER TABLE channel_im DISABLE ROW LEVEL SECURITY`，`ChannelRowLevelSecurityTest` 预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add DB/...channel... T/channel/ChannelRowLevelSecurityTest.java T/channel/ChannelSchemaTest.java`，`git commit -m "feat(channel): 渠道三张表与行级安全"`。

## 批次 B：api 包

### Task 2: api 包、错误码、i18n 与 Provider 声明

**Files:**
- Create: `M/channel/api/{ChannelCode,ChannelType,ConnectionMode,ReplyMode,SessionMode,ChannelMessageKind,ChannelAudience,ChannelConversationStatus}.java`
- Create: `M/channel/api/{ChannelTurnProvider,ChannelTurn,ChannelTurnListener,ChannelVisitorApi}.java`
- Create: `M/channel/api/{ChannelTurnRequest,ChannelTurnRef,ChannelTurnOutcome,ChannelToolEvent,ChannelConversationSpec,ChannelAttachment,ChannelMessageBo,VisitorScope,VisitorSessionBo,SuggestionSetBo,SuggestionEventBo,ChannelPlatformBo}.java`
- Create: `M/config/ChannelCodeRegistration.java`
- Create: `R/i18n/app-messages*.properties`（八个文件追加 30 个 key）
- Create: `T/channel/ChannelCodeMessagesTest.java`

**Interfaces:**
- Produces: “对外承诺”里的四个 Provider 接口与全部 BO；`ChannelCode` 三十个枚举值，码值见 1.9；五个渠道枚举，`ChannelType` 十个值各带 `wireName()` 返回旧平台名（`"wechat"` 等）
- 语义补充：`ChannelTurnRequest.attachments` 为空表表示无附件；`ChannelConversationSpec.description` 由渠道侧给出且不被 Provider 改写；`ChannelTurn.awaitCompletion` 超时后仍可继续收事件，返回 `Optional.empty()`

- [x] **Step 1: 写失败测试**
  - `ChannelCodeMessagesTest.everyCodeHasTextInSevenLocales`：遍历 `ChannelCode.values()`，七个语种与默认文件各取一次文案，断言不等于 key。
- [x] **Step 2: 跑红**：`MVN test -Dtest=ChannelCodeMessagesTest`。
- [x] **Step 3: 实现**：`ChannelCode` 实现 framework 的 `Code` 接口，`httpStatus()` 按语义给默认值；`ChannelCodeRegistration` 用 `@PostConstruct` 调 `CodeCache.register(ChannelCode.values())`。BO 全部用 `record`，带中文 Javadoc 与 `@author jiangguifan`。方法 Javadoc 写明失败语义与租户来源。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/api M/config/ChannelCodeRegistration.java R/i18n T/channel/ChannelCodeMessagesTest.java`，`git commit -m "feat(channel): api 包、错误码与对话入口 Provider 声明"`。

## 批次 C：持久层

### Task 3: Entity、Mapper 与 Repository

**Files:**
- Create: `M/channel/entity/{ImChannel,ChannelConversation,EmbedChannel}.java`
- Create: `M/channel/mapper/{ImChannelMapper,ChannelConversationMapper,EmbedChannelMapper}.java`
- Create: `R/mapper/channel/{ImChannelMapper,ChannelConversationMapper,EmbedChannelMapper}.xml`
- Create: `M/channel/repository/{ImChannelRepository,ChannelConversationRepository,EmbedChannelRepository}.java`
- Create: `T/channel/{ImChannelRepositoryTest,ChannelConversationRepositoryTest,EmbedChannelRepositoryTest}.java`

**Interfaces:**
- Consumes: Task 2 的五个渠道枚举。枚举归 `channel/api`，本任务只引用，不在 `entity` 包重复定义
- Produces:
  - `ImChannelRepository`：`Optional<ImChannel> findByIdAcrossTenants(long id)`（`@IgnoreTenant`）、`List<ImChannel> listEnabledAcrossTenants()`（`@IgnoreTenant`）、`Optional<ImChannel> findByBotIdentity(String identity)`、`List<ImChannel> listByAgent(long agentId)`、`long softDeleteByAgent(long agentId)`
  - `ChannelConversationRepository`：`Optional<ChannelConversation> findUserConversation(ChannelType type, String userId, String chatId, long agentId)`、`Optional<ChannelConversation> findThreadConversation(ChannelType type, String chatId, String threadId, long agentId)`、`int deleteOrphansAcrossTenants(int limit)`（`@IgnoreTenant`）
  - `EmbedChannelRepository`：`Optional<EmbedChannel> findByIdAcrossTenants(long id)`（`@IgnoreTenant`）、`Optional<EmbedChannel> findByPublishTokenSha256(String sha256)`、`List<EmbedChannel> listByAgent(long agentId)`

- [x] **Step 1: 写失败测试**
  - `ImChannelRepositoryTest.credentialsCiphertextDiffersFromPlaintext`、`unknownBotIdentityReturnsEmpty`、`softDeleteHidesFromAgentList`、`listEnabledAcrossTenantsIgnoresTenantFilter`。
  - `ChannelConversationRepositoryTest.userLookupIsTenantAndAgentScoped`、`threadLookupFallsBackToUserWhenThreadEmpty`、`deleteOrphansRemovesRowsWithoutSession`。
  - `EmbedChannelRepositoryTest.publishTokenLookupByHash`、`updateByIdKeepsUntouchedColumns`、`allowedOriginsJsonbRoundTrips`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ImChannelRepositoryTest,ChannelConversationRepositoryTest,EmbedChannelRepositoryTest'`。
- [x] **Step 3: 实现**：实体开 `autoResultMap`，`credentialsCiphertext`、`webhookSecretCiphertext`、`publishTokenCiphertext` 标 `@Encrypted`；`allowedOrigins` 用 `JacksonTypeHandler`；`deletedAt` 用 `@TableLogic(value = "'1970-01-01 00:00:00+00'", delval = "now()")`。枚举列用 `IEnum` 写 `wireName`，读时大小写不敏感。类名不带 `Ops`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/entity M/channel/mapper M/channel/repository R/mapper/channel T/channel/ImChannelRepositoryTest.java T/channel/ChannelConversationRepositoryTest.java T/channel/EmbedChannelRepositoryTest.java`，`git commit -m "feat(channel): 渠道持久层"`。

## 批次 D：适配器层

### Task 4: 适配器窄接口、回调值对象与登记表

**Files:**
- Create: `M/channel/adapter/{ChannelAdapter,ChannelStreamSender,ChannelFileDownloader,ChannelAdapterFactory,ChannelInboundSink}.java`
- Create: `M/channel/adapter/{ChannelCallbackRequest,ChannelCallbackResponder,ChannelInboundMessage,QuotedMessage,ChannelReply,ChannelReplyTarget,ChannelDownloadedFile,ConfiguredChannel,ChannelContext}.java`
- Create: `M/channel/adapter/ChannelAdapterRegistry.java`
- Create: `M/config/ChannelAdapterConfig.java`
- Create: `T/channel/adapter/{ChannelAdapterRegistryTest,ChannelCallbackResponderTest}.java`

**Interfaces:**
- Produces: 见 1.2 的接口与登记表。`ChannelCallbackRequest` 为 record，`headers` 的键小写。`ChannelCallbackResponder` 三个方法 `json(int, Object)`、`plain(int, String, String)`、`boolean isCommitted()`，第二次写出抛 `IllegalStateException`
- `ChannelInboundMessage` record 字段：`ChannelType type`、`ChannelMessageKind kind`、`externalUserId`、`userName`、`chatId`、`chatType`、`content`、`messageId`、`fileKey`、`fileName`、`fileSize`、`threadId`、`quote`（`QuotedMessage` record，字段 `messageId`、`senderName`、`content`）、`extra`（`Map<String,String>` 不可变副本）
- `ChannelContext` record 字段：`long tenantId`、`long channelId`、`ChannelType type`；由回调控制器在验签后构造，下游不读请求上下文
- `ChannelReplyTarget` record 字段：`long tenantId`、`long channelId`、`ChannelType type`、`externalUserId`、`chatId`、`threadId`、`Map<String,String> extra`

- [x] **Step 1: 写失败测试**
  - `ChannelAdapterRegistryTest.lookupByTypeReturnsRegisteredAdapter`、`unknownTypeReturnsEmpty`、`duplicateTypeAtStartupThrows`。
  - `ChannelCallbackResponderTest.secondWriteThrows`、`committedFlagReflectsFirstWrite`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ChannelAdapterRegistryTest,ChannelCallbackResponderTest'`。
- [x] **Step 3: 实现**：`ChannelAdapterRegistry.of(List<ChannelAdapterFactory>)` 用 `Collectors.toMap` 并在重复键时抛 `IllegalStateException`（启动即失败，不静默取后者）。`ChannelAdapterConfig` 放在 `com.ragagent.config`，不放在 `channel` 包内，遵守路线图 3.2 第 3 条。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/adapter M/config/ChannelAdapterConfig.java T/channel/adapter`，`git commit -m "feat(channel): 渠道适配器窄接口与登记表"`。

### Task 5: 平台适配器第一批（Telegram、Slack、QQBot、Mattermost）

**Files:**
- Create: `M/channel/adapter/telegram/{TelegramAdapter,TelegramAdapterFactory,TelegramLongPollClient,TelegramCredentials}.java`
- Create: `M/channel/adapter/slack/{SlackAdapter,SlackAdapterFactory,SlackSocketModeClient,SlackEventParser,SlackSignature,SlackCredentials}.java`
- Create: `M/channel/adapter/qqbot/{QqBotAdapter,QqBotAdapterFactory,QqBotGatewayClient,QqBotApiClient,QqBotCredentials}.java`
- Create: `M/channel/adapter/mattermost/{MattermostAdapter,MattermostAdapterFactory,MattermostApiClient,MattermostCredentials}.java`
- Create: `T/channel/adapter/{TelegramAdapterTest,SlackAdapterTest,SlackEventParserTest,QqBotAdapterTest,MattermostAdapterTest}.java`

**Interfaces:**
- Produces: 四个 `ChannelAdapter` 与四个 `ChannelAdapterFactory` Bean（`@Component`）
- Telegram：`ChannelAdapter` + `ChannelStreamSender`（`sendMessage` 占位加 `editMessageText` 原地替换）+ `ChannelFileDownloader`（`getFile`）；验签用 `X-Telegram-Bot-Api-Secret-Token` 常量时间比较，未配置密钥时放行
- Slack：`ChannelAdapter`；验签用 `X-Slack-Request-Timestamp` 与 `X-Slack-Signature`，五分钟窗口；`url_verification` 挑战回显
- QQBot：`ChannelAdapter`；不验签（网关鉴权在连接建立时完成），`handleCallback` 恒回 200
- Mattermost：`ChannelAdapter`；比 outgoing payload 里的 `token`，忽略机器人自发消息

- [x] **Step 1: 写失败测试**
  - 每个适配器测：`verifyRejectsTamperedSignature`、`parseMessageFillsCanonicalFields`、`urlVerificationIsHandledBeforeVerification`（QQBot 无此项，改为 `callbackAlwaysAcknowledges`）、`sendReplyPostsToPlatformEndpoint`（用本地 stub server，注入基址）。
  - Slack 额外：`timestampOutsideFiveMinuteWindowRejected`、`botSubtypeEventsAreIgnored`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='TelegramAdapterTest,SlackAdapterTest,SlackEventParserTest,QqBotAdapterTest,MattermostAdapterTest'`。
- [x] **Step 3: 实现**：复用 `contracts/w5g3-im-adapter-signatures.tsv` 的验签向量。流式发送的孤儿流清理沿用旧行为（`startStream` 时顺带清理 5 分钟前的孤儿）。HTTP 客户端用 `java.net.http.HttpClient`，基址可注入。出站失败抛 `BusinessRuntimeException(ChannelCode.CHANNEL_ADAPTER_UNAVAILABLE)`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/adapter/telegram M/channel/adapter/slack M/channel/adapter/qqbot M/channel/adapter/mattermost T/channel/adapter/TelegramAdapterTest.java T/channel/adapter/SlackAdapterTest.java T/channel/adapter/SlackEventParserTest.java T/channel/adapter/QqBotAdapterTest.java T/channel/adapter/MattermostAdapterTest.java`，`git commit -m "feat(channel): Telegram、Slack、QQBot、Mattermost 适配器"`。

### Task 6: 平台适配器第二批（飞书与 Lark、企业微信、钉钉、微信个人号、云之家）

**Files:**
- Create: `M/channel/adapter/feishu/{FeishuAdapter,FeishuAdapterFactory,FeishuSocketClient,FeishuFrameCodec,FeishuCallbackVerifier,FeishuPayloadParser,FeishuAesCipher,FeishuMessageSender,FeishuMediaClient,FeishuCardStream,FeishuRegion,FeishuCredentials}.java`
- Create: `M/channel/adapter/wecom/{WecomWebhookAdapter,WecomBotSocketClient,WecomSocketFrames,WecomSignature,WecomApiClient,WecomAdapterFactory,WecomCredentials}.java`
- Create: `M/channel/adapter/dingtalk/{DingtalkAdapter,DingtalkAdapterFactory,DingtalkSocketClient,DingtalkFrameCodec,DingtalkApiClient,DingtalkCallbackParser,DingtalkCardStream,DingtalkCredentials}.java`
- Create: `M/channel/adapter/wechat/{WechatAdapter,WechatAdapterFactory,WechatLongPollClient,WechatAesCipher,WechatQrcodeService,WechatCredentials}.java`
- Create: `M/channel/adapter/yunzhijia/{YunzhijiaAdapter,YunzhijiaAdapterFactory,YunzhijiaSocketClient,YunzhijiaSignature,YunzhijiaUrl,YunzhijiaCardBuilder,YunzhijiaFileClient,YunzhijiaCredentials}.java`
- Create: `T/channel/adapter/{FeishuAdapterTest,FeishuFrameCodecTest,FeishuAesCipherTest,WecomWebhookAdapterTest,WecomSocketFramesTest,WecomAesCipherTest,DingtalkAdapterTest,DingtalkFrameCodecTest,WechatAdapterTest,WechatQrcodeServiceTest,YunzhijiaAdapterTest}.java`

**Interfaces:**
- Produces: 六个工厂 Bean；`FeishuAdapterFactory` 用两个 Bean 实例分别注册 `FEISHU` 与 `LARK`，共用实现、只换基址
- 微信个人号：基址 `https://ilinkai.weixin.qq.com`、CDN `https://novac2c.cdn.weixin.qq.com/c2c`，认证头 `ilink_bot_token` 加 `Bearer` 加随机 `X-WECHAT-UIN`，只支持长轮询。凭据键 `bot_token`、`ilink_bot_id`，缺任一抛 `CHANNEL_ADAPTER_UNAVAILABLE`。**保持现状，不用于营销**
- `WechatQrcodeService`：`QrcodeResult open(long tenantId)`、`LoginResult poll(String qrcode)`，长轮询超时 38 秒，客户端超时按“仍在等待”返回
- 企业微信两种模式：webhook（`corp_id`、`agent_secret`、`token`、`encoding_aes_key`、`corp_agent_id`）与机器人 WS（`bot_id`、`bot_secret`、`ws_endpoint`、`bot_name`）

- [x] **Step 1: 写失败测试**
  - 每平台至少：`verifyRejectsTamperedSignature`、`parseMessageFillsCanonicalFields`、`sendReplyPostsToPlatformEndpoint`。
  - 飞书：`urlVerificationEchoesChallenge`、`encryptedCallbackDecryptedBeforeTokenCompare`、`cardStreamReplacesSameMessage`。
  - 企业微信：`echostrIsDecryptedAndEchoed`、`webhookModeRejectsWsOnlyCredentials`。
  - 微信：`qrCodeStatusMachineTransitions`（`wait`、`scaned`、`confirmed`、`expired`）、`attachmentUsesAes128EcbWithMessageKey`、`missingCredentialRejectsChannelStart`。
  - 云之家：`ackPayloadHasDataEnvelope`、`signatureUsesHmacSha1`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='Feishu*Test,Wecom*Test,Dingtalk*Test,Wechat*Test,Yunzhijia*Test'`。
- [x] **Step 3: 实现**：验签向量复用 `contracts/w5g3-im-adapter-signatures.tsv` 与 `contracts/w5g3b-im-crypt.tsv`。长连接参数（心跳、重连退避、读超时）按盘点 D 节的表格值，收口为各客户端的常量。卡片流式更新只在 `ReplyMode.STREAM` 下启用。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/adapter/feishu M/channel/adapter/wecom M/channel/adapter/dingtalk M/channel/adapter/wechat M/channel/adapter/yunzhijia T/channel/adapter`，`git commit -m "feat(channel): 飞书、企微、钉钉、微信、云之家适配器"`。

## 批次 E：入站管线与运行态

### Task 7: 入站管线（校验、去重、限流、队列、会话解析、附件、命令）

**Files:**
- Create: `M/channel/service/inbound/{ChannelInboundPipeline,ChannelInboundGuard,ChannelTurnQueue,ChannelTurnTask,ChannelTurnDispatcher,ChannelReplyHandler,ChannelConversationResolver,ChannelAttachmentPreparer,ChannelAttachmentIngest,ChannelTurnCanceller,ChannelTurnRequestFactory,ChannelUserKey,ChannelTextLimit,ChannelFileMessage,ChannelDedupCache,ChannelRateWindow}.java`
- Create: `M/channel/service/inbound/command/{SlashCommand,SlashCommandCatalog,SlashCommandParser,HelpCommand,InfoCommand,SearchCommand,StopCommand,ClearCommand}.java`
- Create: `M/config/ChannelTurnProperties.java`
- Create: `T/channel/{ChannelInboundPipelineTest,ChannelInboundGuardTest,ChannelTurnQueueTest,ChannelConversationResolverTest,ChannelAttachmentPreparerTest,ChannelDedupCacheTest,ChannelRateWindowTest,SlashCommandCatalogTest,ChannelTurnCancellerTest,ChannelFileMessageTest,ChannelTypeTest}.java`

**Interfaces:**
- Produces: `ChannelInboundPipeline.handle(ChannelInboundMessage message, ChannelContext context)`，`ChannelContext` 含租户、渠道 ID、渠道类型
- `ChannelTurnQueue.enqueue(ChannelTurnTask task)`，满队列抛 `BusinessRuntimeException(ChannelCode.CHANNEL_QUEUE_REJECTED)`；Redisson 信号量键 `channel:turn:global` 与 `channel:turn:user:<userKey>`，阈值读 `ChannelTurnProperties`（`maxQueue` 默认 50、`maxPerUser` 默认 3、`workers` 默认 5、`globalMaxWorkers` 默认 0 表示不限、`queueTimeout` 默认 60 秒）
- `ChannelReplyHandler` 是本任务新声明的窄接口：`ReplyMode mode()`、`void run(ChannelTurnTask task)`。`ChannelTurnDispatcher` 构造器注入 `List<ChannelReplyHandler>` 建 `Map<ReplyMode, ChannelReplyHandler>`，两个真实实现 `ChannelStreamTurn`、`ChannelNonStreamTurn` 由 Task 9 提供。Task 7 的测试用 `ChannelReplyHandler` 替身，验证只依赖本任务与之前的产物
- 命令不参与限流（保留旧行为），命令描述表按 1.6 拆成五个类
- `ChannelAttachmentIngest` 投 JobRunr 任务，任务参数带租户；事务外执行

- [x] **Step 1: 写失败测试**
  - `ChannelInboundPipelineTest.duplicateMessageIdIsSkipped`、`duplicateContentWithinFiveSecondsSkipped`、`overlongContentTruncatedByCodePoints`、`emptyMessageRepliesWithHint`、`rateLimitedUserGetsReplyInsteadOfDrop`、`commandMessagesBypassRateLimit`。
  - `ChannelTurnQueueTest.rejectsWhenQueueFull`、`perUserLimitApplies`、`globalGateReleasesOnCompletion`。
  - `ChannelConversationResolverTest.userModeCreatesMappingThenReuses`、`threadModeUsesThreadId`、`emptyThreadFallsBackToUserMode`、`staleMappingSoftDeletedAndResolvedAgain`、`openBeforeStartIsEnforced`。
  - `SlashCommandCatalogTest`：五个命令各一条解析与执行断言，`unknownCommandReturnsHelpHint`。
  - `ChannelFileMessageTest`：媒体格式识别与 URL 拼装，按 1.7 的 `runtime.ImFormatMediaTest` 移植。
  - `ChannelTypeTest`：十个枚举值与平台名一一对应，按 1.7 的 `runtime.ImFormatPlatformChannelTest` 移植。
  - `ChannelTurnCancellerTest.cancelMarksStopAndCallsProviderOnce`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ChannelInboundPipelineTest,ChannelTurnQueueTest,ChannelConversationResolverTest,SlashCommandCatalogTest,ChannelTurnCancellerTest,ChannelFileMessageTest,ChannelTypeTest'`。
- [x] **Step 3: 实现**：`ChannelInboundPipeline.handle` 拆成 `ensureTenant`、`guardInbound`、`resolveConversation`、`dispatch` 四个私有方法，各 ≤40 行。`resolveConversation` 必须先查 `channel_conversations` 的 `(tenant_id, channel_type, channel_id, external_user_id)`，命中复用 `sessionId`，未命中才调 `ChannelTurnProvider.openConversation` 并落映射，之后 `dispatch` 才调 `startTurn`。顺序见设计决定二第 7 条，测试 `ChannelConversationResolverTest.openBeforeStartIsEnforced` 用 Mockito 的 `InOrder` 钉住两次调用的先后。回调线程无租户时用渠道租户显式设置，主体类型沿用系统合成主体。`ChannelTurnRequestFactory` 构造 `ChannelTurnRequest` 并从 `ObjectProvider<ChannelTurnProvider>` 取实现，缺失时抛 `CHANNEL_TURN_PROVIDER_MISSING`（计划 08 之前这是常态，日志一次 WARN）。
- [x] **Step 4: 跑绿**。反向验证：把 `ChannelDedupCache` 的 TTL 临时改成 0，`duplicateMessageIdIsSkipped` 预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add M/channel/service/inbound M/config/ChannelTurnProperties.java T/channel/ChannelInboundPipelineTest.java T/channel/ChannelInboundGuardTest.java T/channel/ChannelTurnQueueTest.java T/channel/ChannelConversationResolverTest.java T/channel/ChannelAttachmentPreparerTest.java T/channel/ChannelDedupCacheTest.java T/channel/ChannelRateWindowTest.java T/channel/SlashCommandCatalogTest.java T/channel/ChannelTurnCancellerTest.java T/channel/ChannelFileMessageTest.java T/channel/ChannelTypeTest.java`，`git commit -m "feat(channel): 入站管线、队列、会话解析与斜杠命令"`。

### Task 8: 渠道运行态、领导权与生命周期

**Files:**
- Create: `M/channel/service/runtime/{ChannelRuntime,ChannelLeaderLease,ChannelConfigBroadcast,ChannelConnectionSupervisor,ChannelLeaderLock}.java`
- Create: `M/channel/service/runtime/AgentChannelsRemovalListener.java`
- Modify: `R/application.yml`（追加 `ragagent.channel.turn.*` 与 `ragagent.channel.runtime.*` 两组默认值）
- Create: `T/channel/{ChannelRuntimeTest,ChannelLeaderLeaseTest,ChannelConfigBroadcastTest,AgentChannelsRemovalListenerTest,ChannelLeaderLockTest}.java`

**Interfaces:**
- Produces:
  - `ChannelRuntime.startEnabledChannels()`：读 `listEnabledAcrossTenants`，逐渠道启动。只在 `ragagent.role` 含 `worker` 时执行，用 `@ConditionalOnProperty` 或运行期判定
  - `ChannelRuntime.startChannel(ImChannel channel)`：经登记表取工厂，`WEBSOCKET` 模式先抢领导权（键 `channel:leader:<channelId>`，TTL 15 秒），失败则调度接管
  - `ChannelRuntime.stopChannel(long channelId)`、`ChannelRuntime.reloadChannel(long channelId)`
  - `ChannelLeaderLease`：`TaskScheduler` 固定 5 秒续期、失锁停渠道、10 秒后重试接管
  - `ChannelConfigBroadcast.publish(long channelId)` 与订阅回调 `reloadChannel`
  - `AgentChannelsRemovalListener`：`@TransactionalEventListener(phase = AFTER_COMMIT)` 监听 `AgentDeletedEvent`，显式设置租户后软删该智能体的两个渠道聚合

- [x] **Step 1: 写失败测试**
  - `ChannelRuntimeTest.startChannelRegistersAdapter`、`factoryFailureLeavesChannelInactive`、`stopChannelReleasesLeader`、`reloadRestartsOnlyThatChannel`、`apiRoleDoesNotStartLongConnections`。
  - `ChannelLeaderLeaseTest.onlyOneInstanceHoldsLease`、`leaseRenewedBeforeTtl`、`lostLeaseStopsChannelAndRetries`。
  - `AgentChannelsRemovalListenerTest.softDeletesBothChannelKindsForAgent`、`otherTenantChannelsUntouched`。
  - `ChannelLeaderLockTest`：Testcontainers Redis 下选主互斥、失去租约后停止渠道，按 1.7 的 `runtime.ImRedisStoreTest` 选主部分移植。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ChannelRuntimeTest,ChannelLeaderLeaseTest,ChannelConfigBroadcastTest,AgentChannelsRemovalListenerTest,ChannelLeaderLockTest'`。
- [x] **Step 3: 实现**：领导权用 `RLock.tryLock(0, 15, SECONDS)` 加 `renewExpiration` 不适用（不是 watchdog 场景），改为每 5 秒 `tryLock` 续期并在续期前查库确认渠道仍启用。`ChannelConnectionSupervisor` 沿用旧守护语义：连接对象活着但收不到消息时按 6 小时周期重建，取消时先拆活动连接。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/service/runtime R/application.yml T/channel/ChannelRuntimeTest.java T/channel/ChannelLeaderLeaseTest.java T/channel/ChannelConfigBroadcastTest.java T/channel/AgentChannelsRemovalListenerTest.java T/channel/ChannelLeaderLockTest.java`，`git commit -m "feat(channel): 渠道运行态、领导权与智能体删除联动"`。

## 批次 F：出站

### Task 9: 出站格式化、流式轮次与非流式轮次

**Files:**
- Create: `M/channel/service/outbound/{ChannelOutboundFormatter,ChannelStreamTurn,ChannelStreamEventReducer,ChannelNonStreamTurn,ChannelToolStepRenderer,ChannelToolStepFormatter,ChannelThinkSplitter,ChannelFailureReply}.java`
- Create: `M/channel/bo/{ChannelStreamBuffer,ChannelToolStep,ChannelStreamSection}.java`
- Create: `T/channel/{ChannelStreamTurnTest,ChannelNonStreamTurnTest,ChannelOutboundFormatterTest,ChannelThinkSplitterTest,ChannelToolStepRendererTest}.java`

**Interfaces:**
- Produces:
  - `ChannelStreamTurn implements ChannelReplyHandler`（Task 7 声明的窄接口），`mode()` 返回 `ReplyMode.STREAM`，`run(ChannelTurnTask task)` 注册监听、按事件更新缓冲、节流推送分片、终态只发一次
  - `ChannelNonStreamTurn implements ChannelReplyHandler`，`mode()` 返回 `ReplyMode.FULL`，不开流，等 `awaitCompletion` 后发一条完整消息
  - `ChannelStreamEventReducer`：`onReasoning`、`onAnswer`、`onTool`、`onError` 四个方法，各自 ≤40 行
  - `ChannelThinkSplitter.formatIntermediate(List<ChannelStreamSection>, boolean agentRunning)` 与 `formatFinal(String raw, boolean rendersImages)`
  - `ChannelOutboundFormatter` 把不能渲染 markdown 图片的平台里的图片降级为链接

- [x] **Step 1: 写失败测试**
  - `ChannelStreamTurnTest.deltasAreAccumulatedAndPushedInOrder`、`contentNeverShrinks`、`completedEventSentExactlyOnce`、`cancelledTurnStopsPushing`、`toolStepsRenderedInCallOrder`。
  - `ChannelNonStreamTurnTest.sendsSingleMessageAfterCompletion`、`failedTurnSendsReadableFallback`。
  - `ChannelOutboundFormatterTest.imagesDegradedToLinksWhenPlatformCannotRender`。
  - `ChannelThinkSplitterTest.splitAcrossChunkBoundary`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ChannelStreamTurnTest,ChannelNonStreamTurnTest,ChannelOutboundFormatterTest,ChannelThinkSplitterTest,ChannelToolStepRendererTest'`。
- [x] **Step 3: 实现**：两个实现注册为 Bean，`ChannelTurnDispatcher` 在启动时自动收集，Task 7 的替身不再装配。适配器不实现 `ChannelStreamSender` 时，流式模式退回只发最终消息（与旧行为一致）。节流间隔 500 毫秒，与旧值一致。停止请求到达后不再推送，但仍写终态。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/service/outbound M/channel/bo T/channel/ChannelStreamTurnTest.java T/channel/ChannelNonStreamTurnTest.java T/channel/ChannelOutboundFormatterTest.java T/channel/ChannelThinkSplitterTest.java T/channel/ChannelToolStepRendererTest.java`，`git commit -m "feat(channel): 渠道出站格式化与流式轮次"`。

## 批次 G：控制器

### Task 10: IM 渠道管理面与微信扫码

**Files:**
- Create: `M/channel/service/admin/{ImChannelAdminService,ChannelBotIdentity,ChannelCallbackGuard,ChannelCredentialReader,ChannelCredentialColumn,ChannelPolicyValidator}.java`
- Create: `M/channel/controller/{ImChannelAdminController,WechatQrcodeAdminController}.java`
- Create: `M/channel/vo/{ImChannelReqVo,ImChannelUpdateReqVo,ImChannelRespVo,ImChannelOverviewRespVo,WechatQrcodeRespVo,WechatQrcodeStatusRespVo}.java`
- Create: `M/channel/assembler/{ImChannelAssembler,ChannelEnumMapper}.java`
- Create: `T/channel/{ImChannelAdminServiceTest,ImChannelAdminControllerTest,WechatQrcodeAdminControllerTest,ChannelBotIdentityTest}.java`

**Interfaces:**
- Produces: `/api/admin/v1` 下六条 IM 渠道接口与两条扫码接口，路径见 1.3
- `ImChannelAdminService`：`ImChannel create(long agentId, ImChannelReqVo vo)`、`ImChannel update(long id, ImChannelUpdateReqVo vo)`、`void delete(long id)`、`ImChannel toggle(long id)`、`List<ImChannel> listByAgent(long agentId)`、`List<ChannelPlatformBo> listOverview()`
- 平台白名单改为 `ChannelType` 全集判定，非法平台抛 `CHANNEL_INVALID_PLATFORM`
- 建渠道时的默认值规则保留：微信个人号强制 `LONGPOLL` 加 `FULL`；Mattermost 与云之家默认 `WEBHOOK`；其余默认 `WEBSOCKET` 加 `STREAM`
- `ChannelBotIdentity.compute(ImChannel)` 保留旧规则（按平台取不同凭据字段做十六进制 SHA-256），冲突抛 `CHANNEL_DUPLICATE_BOT`
- `ApiResult` 不再使用，改 `RestResponse`；删除返回 `RestResponse<Void>`

- [x] **Step 1: 写失败测试**
  - `ImChannelAdminServiceTest.createRejectsUnknownPlatform`、`createEnforcesWechatModeDefaults`、`createDefaultsOtherPlatformsToWebsocketStream`、`duplicateBotRejectedAcrossChannels`、`duplicateBotAllowedAfterSoftDelete`、`switchAgentRejectsMissingAgent`、`deleteSoftDeletesAndNotifiesRuntime`。
  - `ImChannelAdminControllerTest.unknownFieldsDoNotLeakCredentials`、`invalidPlatformReturns4400`、`anonymousReturns4402`、`listOverviewLocalizesBuiltinAgentNameByAcceptLanguage`。
  - `WechatQrcodeAdminControllerTest.openReturnsQrcodeUrl`、`statusWithoutQrcodeReturnsParamError`、`confirmedReturnsCredentials`。
  - `ChannelBotIdentityTest`：九个平台各一条标识生成断言，同一凭据在不同租户得到相同标识（全局唯一的前提）。
- [x] **Step 2: 跑红**：`MVN test -Dtest='ImChannelAdminServiceTest,ImChannelAdminControllerTest,WechatQrcodeAdminControllerTest,ChannelBotIdentityTest'`。
- [x] **Step 3: 实现**：控制器只做绑定、`@Valid` 与一次 Service 调用。凭据 VO 用 `Map<String,String>` 且标 `@JsonDeserialize` 保留字段序不必要，但序列化时按白名单输出，不泄露未登记键。`ChannelPolicyValidator` 统一校验会话模式与连接模式与平台的组合。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/service/admin M/channel/controller/ImChannelAdminController.java M/channel/controller/WechatQrcodeAdminController.java M/channel/vo M/channel/assembler T/channel/ImChannelAdminServiceTest.java T/channel/ImChannelAdminControllerTest.java T/channel/WechatQrcodeAdminControllerTest.java T/channel/ChannelBotIdentityTest.java`，`git commit -m "feat(channel): IM 渠道管理面与微信扫码"`。

### Task 11: 渠道平台回调控制器

**Files:**
- Create: `M/channel/controller/ChannelCallbackController.java`
- Create: `M/channel/adapter/ServletCallbackResponder.java`
- Create: `T/channel/ChannelCallbackControllerTest.java`

**Interfaces:**
- Produces: `GET|POST /api/open/v1/channels/{channelId}/callbacks`。控制器把 `HttpServletRequest` 与 `HttpServletResponse` 包成 `ChannelCallbackRequest` 与 `ServletCallbackResponder`，按渠道类型取适配器，交给 `adapter.handleCallback`
- 渠道三态：渠道不存在 404、禁用 503、平台无适配器 503，回包为该平台的纯 JSON，不走 `RestResponse`
- 回调路径的 Sa-Token 放行由计划 01 的 `SaRouteRules` 内置例外 `SecurityConstants.CHANNEL_CALLBACK_PATH`（`/api/open/v1/channels/*/callbacks`）承担，不依赖 `gac.furnace.security.permit-paths`，本计划不追加配置项。开放接口组的 Sa-Token 路由规则对该路径仍判为 `open`，验签由适配器负责

- [x] **Step 1: 写失败测试**
  - `ChannelCallbackControllerTest.unknownChannelReturns404`、`disabledChannelReturns503`、`missingAdapterReturns503`、`verificationFailureReturns403`、`parseFailureReturns400`、`ackIsWrittenBeforeInboundProcessing`、`yunzhijiaAckHasDataEnvelope`、`noRestResponseEnvelopeOnCallbackRoutes`。
- [x] **Step 2: 跑红**：`MVN test -Dtest=ChannelCallbackControllerTest`。
- [x] **Step 3: 实现**：控制器只做三件事：包请求、取适配器、委托。ACK 之后的入站处理交 `ChannelTurnQueue`，不在控制器里起线程。三态判定在 `ChannelCallbackGuard`，抛带 `ChannelCode` 的业务异常，控制器把它映射成对应状态码与纯 JSON。
- [x] **Step 4: 跑绿**：同一命令。
- [x] **Step 5: 提交**：`git add M/channel/controller/ChannelCallbackController.java M/channel/adapter/ServletCallbackResponder.java T/channel/ChannelCallbackControllerTest.java`，`git commit -m "feat(channel): 渠道平台回调面"`。

## 批次 H：嵌入渠道

### Task 12: 嵌入渠道管理面

**Files:**
- Create: `M/channel/service/embed/{EmbedChannelAdminService,EmbedChannelPolicy,EmbedChannelLookup,EmbedChannelTokenService,EmbedPublicConfigAssembler,EmbedTokenCodec,EmbedSessionTokenCache,EmbedChannelStatsService}.java`
- Create: `M/channel/controller/EmbedChannelAdminController.java`
- Create: `M/channel/vo/{EmbedChannelReqVo,EmbedChannelRespVo,EmbedChannelStatsRespVo,EmbedPreviewSessionRespVo}.java`
- Create: `M/channel/assembler/EmbedChannelAssembler.java`
- Create: `T/channel/{EmbedChannelAdminServiceTest,EmbedChannelAdminControllerTest,EmbedTokenCodecTest,EmbedChannelPolicyTest}.java`

**Interfaces:**
- Produces: `/api/admin/v1` 下九条嵌入渠道接口，路径见 1.3
- `EmbedChannelPolicy.validateOrigins(List<String>)`：生产环境空清单与通配符 `*` 拒绝，格式非法拒绝；`validateWebhookUrl(String)`：http 或 https，SSRF 内网段拒绝；`validateLauncherIcon(String)`：只接受 `data:image/(png|jpeg|svg+xml|webp);base64,`，解码后不超过 2 MB
- `EmbedChannelTokenService.issuePublish()`、`issueSession(long channelId)` 返回 30 分钟令牌，Redis 键 `embed:session:<token>`
- `EmbedTokenCodec.signHandle(long channelId, String publishToken, String sessionId)` 用 HMAC-SHA256，`verifyHandle` 常量时间比较
- 响应形状：响应 VO 的 `publishToken` 只在创建、详情、轮换三处输出

- [x] **Step 1: 写失败测试**
  - `EmbedChannelAdminServiceTest.createGeneratesPublishToken`、`rotateInvalidatesOldToken`、`productionRejectsEmptyOrigins`、`productionRejectsWildcardOrigin`、`webhookToPrivateAddressRejected`、`launcherIconOver2MbRejected`、`statsCountsOnlyThisChannel`、`crossTenantGetReturns404`。
  - `EmbedChannelAdminControllerTest.createReturns201AndToken`、`updateDoesNotEchoPublishToken`、`deleteReturnsRestResponse`。
  - `EmbedTokenCodecTest.handleRoundTripsAndRejectsTamperedSignature`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='EmbedChannelAdminServiceTest,EmbedChannelAdminControllerTest,EmbedTokenCodecTest,EmbedChannelPolicyTest'`。
- [x] **Step 3: 实现**：发布令牌明文只在生成时返回一次，落库为密文加 SHA-256；查表用哈希列。`listAll` 与 `listByAgent` 不输出 `publishToken`。`agentId` 用 `AgentQueryApi.isOwned` 校验，失败抛 `CHANNEL_AGENT_NOT_FOUND`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/channel/service/embed M/channel/controller/EmbedChannelAdminController.java M/channel/vo M/channel/assembler/EmbedChannelAssembler.java T/channel/EmbedChannelAdminServiceTest.java T/channel/EmbedChannelAdminControllerTest.java T/channel/EmbedTokenCodecTest.java T/channel/EmbedChannelPolicyTest.java`，`git commit -m "feat(channel): 嵌入渠道管理面"`。

### Task 13: 挂件访客鉴权与公开面

**Files:**
- Create: `M/channel/service/embed/{EmbedVisitorAuthFilter,EmbedOriginMatcher,EmbedRateLimiter,EmbedVisitorChatService,EmbedVisitorApprovalService,EmbedWidgetBootstrapService,EmbedVisitorSessionService}.java`
- Create: `M/channel/controller/{EmbedWidgetBootstrapController,EmbedVisitorSessionController,EmbedVisitorInteractionController}.java`
- Create: `M/channel/vo/{EmbedChatReqVo,EmbedExchangeRespVo,EmbedSessionTokenRespVo,EmbedVisitorSessionRespVo,EmbedApprovalReqVo}.java`
- Create: `T/channel/{EmbedVisitorAuthFilterTest,EmbedOriginMatcherTest,EmbedRateLimiterTest,EmbedWidgetBootstrapControllerTest,EmbedVisitorSessionControllerTest,EmbedVisitorChatServiceTest}.java`

**Interfaces:**
- Produces: `/api/v1/embed/{channelId}/...` 全部挂件接口，路径见 1.3；`EmbedVisitorAuthFilter` 作为 `FilterRegistrationBean` 显式注册在 `Ordered.HIGHEST_PRECEDENCE + 30`，不用 `@Component` 加 `@Order`，因为过滤器顺序要和 Sa-Token 的登录拦截区分开：挂件访客没有 Sa-Token 主体，鉴权必须在安全过滤器之前跑完并把访客主体塞进上下文。登记到 `docs/migration/deviations.md` 一条：计划 01 的 `FilterOrders` 没有 `EMBED_AUTH`，本计划用字面量加注释，计划 01 补常量后改引用
- `/api/v1/embed/**` 必须登记进 app 的 `gac.furnace.security.permit-paths`：计划 01 的 token 前缀是全局 `Bearer`，Sa-Token 读不到 `Embed <token>`，这段路由不能进 visitor 组的 `checkLogin()`，验签只由 `EmbedVisitorAuthFilter` 负责（偏离登记见 `docs/migration/deviations.md` 计划 01 Task 8 行）
- 鉴权顺序固定七步：路径渠道 ID、`Authorization: Embed <token>`、令牌形态（`ems_` 走 Redis 反查、`em_` 走渠道比对）、渠道禁用先于令牌比对、Origin 白名单、三段限流、租户存在性。任一步失败立即返回，不继续
- `EmbedOriginMatcher.matches(List<String> allowed, String origin, String referer)`：`Origin` 头优先，回落 `Referer` 的 `scheme://host[:port]`；空清单全拒，`*` 全放，`*.` 后缀匹配，其余大小写不敏感精确匹配
- `EmbedRateLimiter` 三段：`perIp` 每分钟读渠道 `rateLimitPerMinute`；全局每分钟为 `perIp × 20` 且不低于 120；每日读 `rateLimitPerDay`。Redis 键 `embed:ratelimit:<windowMs>:<key>`
- `EmbedVisitorChatService.start(...)` 调 `ChannelTurnProvider.startTurn`，把 `ChannelTurn` 事件映射成 SSE 帧，`mode` 只影响请求里的 `audience` 与渠道约束注入，管线选择在 Provider 内部
- `EmbedVisitorApprovalService` 调 `McpApprovalApi.resolve(long approvalId, ApprovalDecisionBo decision)`，替换旧 `McpApprovalOps`。访客的审批与 OAuth 恢复走同一个入口：计划 06 把两类待审批合并进 `mcp_tool_approvals`，`decision` 取 `APPROVE`、`REJECT`、`AUTHORIZE`、`CANCEL` 四值，与计划 06 设计决定二第 1 条一致。挂件侧的 `EmbedApprovalReqVo.decision` 用同一个枚举 `com.ragagent.mcp.api.ApprovalDecision`，不另定义三值枚举
- 挂件 `oauth/authorize-url` 与 `oauth/status` 两个接口服务端不发请求，映射为 `McpApprovalApi.openOAuthAuthorization(OpenOAuthBo)` 与 `McpApprovalApi.claimApprovedCall(long)` 两条既有方法：授权 URL 以 `kind=OAUTH` 的待审批行承载，`attemptId` 即待审批行 ID。计划 06 的 `McpOAuthApi` 保留给管理面，挂件不复用它，避免为访客另开一套状态机

- [x] **Step 1: 写失败测试**
  - `EmbedVisitorAuthFilterTest.missingTokenReturns401`、`bearerTokenNotAccepted`、`sessionTokenResolvesChannelFromRedis`、`disabledChannelRejectedBeforeTokenCompare`、`originNotAllowedReturns403`、`rateLimitExceededReturns429`、`dailyQuotaExceededReturns429`、`tenantMissingReturns500`、`contextClearedAfterRequest`。
  - `EmbedOriginMatcherTest.emptyListRejectsAll`、`wildcardAllowsAll`、`suffixWildcardMatchesSubdomain`、`refererFallbackParsesOrigin`。
  - `EmbedRateLimiterTest.threeWindowsApplyIndependently`、`redisDisabledFallsBackToLocalWindow`。
  - `EmbedVisitorChatServiceTest.modeDefaultsToKnowledge`、`modeAgentPassesAudienceEndUser`、`approvalResolutionMapsDecision`、`sseFramesFollowChannelTurnEvents`。
  - `EmbedVisitorSessionControllerTest.visitorCannotReadAnotherVisitorsSession`、`sessionSignatureInvalidReturns4400`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='EmbedVisitorAuthFilterTest,EmbedOriginMatcherTest,EmbedRateLimiterTest,EmbedWidgetBootstrapControllerTest,EmbedVisitorSessionControllerTest,EmbedVisitorChatServiceTest'`。
- [x] **Step 3: 实现**：过滤器通过后写入框架租户上下文与一个挂件访客主体，请求结束清理。`ChannelVisitorApi` 从 `ObjectProvider` 取，缺失时公开面问答返回 `CHANNEL_TURN_PROVIDER_MISSING`（计划 08 之前是常态）。附件走 `storage.api` 的资源句柄，`resource://` 前缀保留。`relayEmbedWebhookEvent` 只接受白名单事件类型，其余抛 `EMBED_EVENT_UNSUPPORTED`。
- [x] **Step 4: 跑绿**。反向验证：把 `EmbedVisitorAuthFilter` 的第三与第四步顺序对调，`disabledChannelRejectedBeforeTokenCompare` 预期变红，确认后恢复。
- [x] **Step 5: 提交**：`git add M/channel/service/embed/EmbedVisitorAuthFilter.java M/channel/service/embed/EmbedOriginMatcher.java M/channel/service/embed/EmbedRateLimiter.java M/channel/service/embed/EmbedVisitorChatService.java M/channel/service/embed/EmbedVisitorApprovalService.java M/channel/service/embed/EmbedWidgetBootstrapService.java M/channel/service/embed/EmbedVisitorSessionService.java M/channel/controller/EmbedWidgetBootstrapController.java M/channel/controller/EmbedVisitorSessionController.java M/channel/controller/EmbedVisitorInteractionController.java M/channel/vo T/channel/EmbedVisitorAuthFilterTest.java T/channel/EmbedOriginMatcherTest.java T/channel/EmbedRateLimiterTest.java T/channel/EmbedWidgetBootstrapControllerTest.java T/channel/EmbedVisitorSessionControllerTest.java T/channel/EmbedVisitorChatServiceTest.java`，`git commit -m "feat(channel): 挂件访客鉴权与公开面"`。

## 批次 I：前端与验收

### Task 14: 前端 API 层切换与类型再生成

**Files:**
- Modify: `frontend/src/api/agent/index.ts`
- Modify: `frontend/src/api/embed/index.ts`
- Modify: `frontend/src/composables/useEmbedChatSession.ts`
- Modify: `frontend/src/components/IMChannelPanel.vue`（只改第 942 行的回调地址字面量）
- Modify: `frontend/packages/api-client/src/schema.d.ts`（`npm run gen:api` 产物）

- [x] **Step 1: 写失败测试**：`cd frontend && npx vue-tsc --noEmit`，当前预期失败（旧路径不被新 `paths` 类型接受）或至少在新路径接入后暴露类型不一致。旧路径字面量用 `grep -rn "api/v1/im-channels\|api/v1/embed-channels\|api/v1/im/callback\|api/v1/wechat/qrcode\|knowledge-chat\|agent-chat" frontend/src/api/agent/index.ts frontend/src/api/embed/index.ts frontend/src/composables/useEmbedChatSession.ts frontend/src/components/IMChannelPanel.vue` 盘点，预期当前有多处命中。控制台对话路径 `/api/v1/knowledge-chat`、`/api/v1/agent-chat` 归计划 08，不在本任务的清扫范围。
- [x] **Step 2: 跑红**：同命令。
- [x] **Step 3: 实现**：按 1.12 的三张表改。传输统一用 `@ragagent/api-client` 的 `createApiClient`；带 `Authorization: Embed <token>` 与 SSE 的请求保留原 `fetch`，只改路径。`agentId` 由后端字符串化返回，前端 `IMChannel.agentId` 类型保持 `string`；`tenantId` 在 API 层 `Number(...)`。`embedChatUrl(channelId)` 新增导出。

  挂件独立入口 `frontend/embed-main.ts` 本身不含接口路径，它引用的两个 API 层文件就是本任务改的 `api/embed/index.ts` 与 `composables/useEmbedChatSession.ts`，所以挂件对话页的 API 层切换与主控制台在同一任务完成。挂件访客对话的界面用例依赖计划 08 的 Provider 实现，见 Task 15 与完成判据。
- [x] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit` 无错误；`cd frontend/packages/api-client && npm run gen:api && npm run check:api` 退出码 0；`grep` 命令无旧路径命中（`IMChannelPanel.vue` 的回调地址已改）。
- [x] **Step 5: 提交**：`git add frontend/src/api/agent/index.ts frontend/src/api/embed/index.ts frontend/src/composables/useEmbedChatSession.ts frontend/src/components/IMChannelPanel.vue frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(channel): 前端 API 层切换与类型再生成"`。

### Task 15: 界面端到端转绿

**Files:**
- Modify: `tests/e2e/PAGES.md`（标记 `@channel` 行已转绿）
- Create: `tests/e2e/tests/channel/agent-channel-panels.spec.ts`
- Modify: `tests/e2e/tests/channel/{integration-im,integration-embed,widget-shell}.spec.ts`（去掉 `test.fixme`，路径与选择器按新栈校正）

- [x] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep @channel`，在新栈上运行（后端 8080、前端 5173 代理到 8080），当前预期失败。
- [x] **Step 2: 跑红**：同上，预期 FAIL，原因是前端仍指向旧路径或页面未就绪。
- [x] **Step 3: 实现**：只改测试与 `PAGES.md`，不改组件。`@channel` 的四条用例按 1.13 的清单跑通；`integration-embed` 用例产出的渠道 ID 写进 `E2E_CHANNEL_ID` 供后续批次使用。`widget-shell` 只断言首屏欢迎语与输入框可见，不发消息，因此不依赖计划 08。新增 `agent-channel-panels.spec.ts` 覆盖智能体编辑弹窗内两个频道面板的标签切换与新建对话框打开后取消。
- [x] **Step 4: 跑绿**：同命令，预期全部通过，无 skipped 的 `@channel` 用例。再跑 `npx vue-tsc --noEmit` 与 `MVN verify`。
- [x] **Step 5: 提交**：`git add tests/e2e/PAGES.md tests/e2e/tests/channel`，`git commit -m "test(channel): 渠道页面端到端转绿"`。

### Task 16: 冒烟回调用例与闸门

**Files:**
- Create: `tests/api-smoke/tests/channel-callback.spec.ts`
- Modify: `docs/migration/progress.md`（登记四处 `@IgnoreTenant` 与 `code-review-mr` 结论）
- Modify: `docs/migration/deviations.md`（登记三件事：回调路径变化、`IMChannelPanel.vue` 的路径字面量、`embedChatUrl` 新增导出）

- [x] **Step 1: 写失败测试**：新建冒烟用例，四条断言：缺渠道返回 404 且响应体是纯 JSON（无 `success` 字段）；伪造签名返回 403；合法 GET 握手请求返回 200 且 `Content-Type` 为 `application/json; charset=utf-8`；渠道已禁用返回 503。
- [x] **Step 2: 跑红**：`cd tests/api-smoke && npx playwright test channel-callback`，当前预期部分失败。
- [x] **Step 3: 实现**：只改测试。运行前确认计划 01 的 `SaRouteRules` 内置回调例外已生效，否则用例被 Sa-Token 拦成 401。
- [x] **Step 4: 跑绿**：同命令全绿；随后跑下面的完成判据。
- [x] **Step 5: 提交**：`git add tests/api-smoke/tests/channel-callback.spec.ts docs/migration/progress.md docs/migration/deviations.md`，`git commit -m "test(channel): 回调冒烟与计划闸门登记"`。

# 3. 完成判据

以下命令全部给出预期输出才算本计划完成：

```bash
# 全量构建，含 Modulith、ArchUnit、Checkstyle
MVN verify
# 预期：BUILD SUCCESS，无 Checkstyle 违规

# 渠道相关界面端到端（新栈），不含挂件对话
cd tests/e2e && npx playwright test --grep '@channel' --grep-invert 'widget-chat'
# 预期：全部通过，无 skipped 的 @channel 用例

# 渠道回调冒烟
cd tests/api-smoke && npx playwright test channel-callback
# 预期：全部通过

# OpenAPI 类型无漂移
cd frontend/packages/api-client && npm run check:api
# 预期：退出码 0

# 前端类型检查
cd frontend && npx vue-tsc --noEmit
# 预期：无错误

# 主码无手写租户条件、无注解 SQL、无禁用后缀
grep -rEn "tenant_id" backend/app/src/main/java/com/ragagent/channel
grep -rEn "@(Select|Update|Insert|Delete)\b" backend/app/src/main/java/com/ragagent/channel
find backend/app/src/main/java/com/ragagent/channel -name '*Ops.java' -o -name '*Support.java' -o -name '*Helper.java' -o -name '*Facade.java' -o -name '*Manager.java' -o -name '*Common.java'
# 预期：三条命令均无输出

# channel 不依赖 session（ArchUnit 已有规则，这里显式确认一次）
grep -rn "com.ragagent.session" backend/app/src/main/java/com/ragagent/channel
# 预期：无输出
```

逐条对应路线图 07 号闸门与 spec 第 7 节：

| 闸门项 | 覆盖方式 |
|---|---|
| 渠道相关页面转绿 | Task 15 的 e2e 命令 |
| 验收 3：无手写租户条件，租户表开行级安全并有跨租户被拒测试 | Task 1 的 `ChannelRowLevelSecurityTest` 与上面的 grep |
| 验收 4：业务异常带错误码与 i18n key，覆盖七语种 | Task 2 的 `ChannelCodeMessagesTest` |
| 验收 5：前端类型由 OpenAPI 生成 | Task 14 的 `gen:api` 与 `check:api` |
| 验收 6：架构规则由 Java 测试表达 | Task 15、16 的 `MVN verify` |
| 路线图第 6 节交接表 | “对外承诺”增补的两行以本计划为准，路线图维护者回填 |

本计划明确不覆盖：

1. `session/widget-chat.spec.ts` 与挂件问答冒烟用例。它们依赖计划 08 的 `ChannelTurnProvider` 与 `ChannelVisitorApi` 实现，**在计划 08 转绿**。本计划内这两条用例保持 `test.fixme`，Task 13 只到接口级验证，用测试替身断言 SSE 帧形状。
2. 各平台真实网关的回调抓包校验，沿用盘点的未确认项。执行时用各平台官方文档的示例载荷作为 fixture。
3. 挂件的手机端优先改版，属第二阶段。

