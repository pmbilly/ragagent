<!-- 由 scripts/gen-arch-index.py 生成；不要手改。 -->
# 包索引

322 份 `package-info.java` 的首句摘要，按模块分组。找代码时先按模块，再按包名。

| 模块 | 包 | 职责 |
|---|---|---|
| `:agent-engine` | `com.ragagent.agent` | ReAct 引擎域：AgentEngine 门面与八个段协作者（think/act/observe/prompt/finalize/steer/context-debug/ReAct 迭代）、 提示词装配、token 预算与取消探测。 |
| `:agent-engine` | `com.ragagent.agent.budget` | token 预算与上下文占用：TokenEstimator（token 估算，压缩与裁剪共用）、 AgentBudgets（轮次 / completion / 上下文预留等预算常量与换算）、 Cont |
| `:agent-engine` | `com.ragagent.agent.compaction` | 上下文压缩：Compactor、溢出判定、截断点选择与压缩结果/原因类型。 |
| `:agent-engine` | `com.ragagent.agent.domain` | 引擎状态类型：AgentState/AgentStep/AgentToolCall/ToolResult 等，落库 jsonb 与消息响应共用（键名改动须连带前端）。 |
| `:agent-engine` | `com.ragagent.agent.prompt` | agent 侧提示词构件：AgentPromptTemplates（系统提示词模板按 mode 选取，经 common.prompt.PromptSource 装载）、GroundingPrompt（按当前工具清单生成 内容接地指引）。 |
| `:agent-engine` | `com.ragagent.agent.skills` | 指令型技能：SKILL.md 解析（Skill/Frontmatter）、宿主目录加载与技能管理器（提示词注入路径）。 |
| `:agent-engine` | `com.ragagent.agent.skills.mapper` | 技能 · 映射：SkillMapper。 |
| `:agent-engine` | `com.ragagent.agent.tool` | Agent 运行面的**工具框架**（`agent.tool/`）：工具契约与基类、定义与 schema、注册表、 参数校验与投射、执行上下文与预算、输出持久化、取消与能力标记、通用单件工具。 |
| `:agent-engine` | `com.ragagent.agent.tool.runtime` | 工具运行时横切件（不放具体工具）：SearchAuth（检索范围鉴权）、 ToolResultPersist（工具结果的客户端 / 存储投影）、MessageSanitizer 与 ThinkBloc |
| `:boot` | `com.ragagent` | com · ragagent：RagAgentApplication。 |
| `:boot` | `com.ragagent.config` | Spring 装配层：Web/MyBatis/检索引擎/会话/IM 等的 Bean 定义与 @ConfigurationProperties 属性绑定， 不含业务逻辑。 |
| `:contract` | `com.ragagent.contract` | 契约面（contracts，阶段 6 起逐步收纳）：跨模块公开的 API / SPI 与不可变载荷。 |
| `:contract` | `com.ragagent.contract.agent` | 代理契约面：代理目录/定义/执行/进度/运行请求 + 渠道清理端口。 |
| `:contract` | `com.ragagent.contract.apikey` | API Key 契约面：能力/作用域上下文与类型、管理端口、响应载荷。 |
| `:contract` | `com.ragagent.contract.audit` | 审计契约面：wiki 活动审计 + 审计写入端口。 |
| `:contract` | `com.ragagent.contract.auth` | 认证契约面：用户查找端口（跨域只经契约）。 |
| `:contract` | `com.ragagent.contract.doc` | 文档解析服务（DocReader）的跨域端口面。 |
| `:contract` | `com.ragagent.contract.error` | 契约 · 错误：ErrorCode / BizException / AppError。 |
| `:contract` | `com.ragagent.contract.event` | 契约 · 事件：EventBusInterface / EventBusFactory / EventHandler…。 |
| `:contract` | `com.ragagent.contract.event.payload` | 事件载荷契约面：SSE 事件载荷（线格式由 common.web.ContractJsonMixinsEvent 承载）。 |
| `:contract` | `com.ragagent.contract.graph` | 图谱契约面：图数据/节点/关系/命名空间。 |
| `:contract` | `com.ragagent.contract.json` | 契约 · JSON：ByteKeyOrder / JsonValues / ZeroTimes。 |
| `:contract` | `com.ragagent.contract.knowledge` | 知识契约面：分块/库/文档的事实、视图、网关与端口。 |
| `:contract` | `com.ragagent.contract.knowledge.chunker` | 知识库 · 分块：CodePoints / Tokens。 |
| `:contract` | `com.ragagent.contract.knowledge.domain` | 知识库 · 领域模型：StorageProviderConfigView / ImageProcessingConfigView / IndexingStrategyView…。 |
| `:contract` | `com.ragagent.contract.knowledge.service` | 知识库 · 服务：DuplicateKnowledgeDetails / DuplicateKnowledgeException。 |
| `:contract` | `com.ragagent.contract.knowledge.support` | 知识库 · 支撑：KnowledgeProjectionAdapter / ChunkProjectionAdapter / KnowledgeBaseViews…。 |
| `:contract` | `com.ragagent.contract.memory` | 记忆契约面：配置/条目视图/键与类目/召回与检索上下文。 |
| `:contract` | `com.ragagent.contract.model` | 模型契约面：模型事实与运行时而面载荷。 |
| `:contract` | `com.ragagent.contract.model.chat` | 模型聊天契约面：聊天消息/选项/响应与工具定义等跨域共享形状。 |
| `:contract` | `com.ragagent.contract.pipeline` | 管线 SPI（契约化）：`chatpipeline` 消费、业务域实现的端口（MCP 工具网关/按需注册、 工具注册表、网页检索执行器）。 |
| `:contract` | `com.ragagent.contract.retrieval` | 检索契约面：检索类型/命中合并/目标。 |
| `:contract` | `com.ragagent.contract.session` | 会话契约面：管线消息视图与消息端口。 |
| `:contract` | `com.ragagent.contract.settings` | 契约 · 设置：SystemSettingGateway。 |
| `:contract` | `com.ragagent.contract.storage` | 存储契约面：后端供给端口。 |
| `:contract` | `com.ragagent.contract.storage.fileserve` | 存储 · 文件服务：WritableFileContentService / FileContentService / GlobalFileServicePort…。 |
| `:contract` | `com.ragagent.contract.storage.provider` | 存储 · 提供方：SeekableSource。 |
| `:contract` | `com.ragagent.contract.storage.support` | 存储 · 支撑：StorageBackendResolver / StorageUrlContext / FileUrlPort…。 |
| `:contract` | `com.ragagent.contract.tenant` | 租户契约面：租户角色等跨域值对象。 |
| `:contract` | `com.ragagent.contract.text` | 契约文本工具面：契约载荷（wiki 标记/语言判定）共用的纯 JDK 文本原语。 |
| `:contract` | `com.ragagent.contract.tool` | 工具契约面：工具执行结果载荷与工具取消/能力词汇。 |
| `:contract` | `com.ragagent.contract.vectorstore` | 向量库契约面：环境 store id、查找端口与视图。 |
| `:contract` | `com.ragagent.contract.websearch` | 契约 · 网页搜索：WebSearchTempStatePort / WebSearchProviderPort / PipelinePageFetcher…。 |
| `:contract` | `com.ragagent.contract.wiki` | Wiki 契约面：摄取/收尾端口与图片标记。 |
| `:domains` | `com.ragagent.agentmanagement` | 智能体管理面（agent 域子包， 由顶层 agentm 并入）：内建智能体注册表与类型预设、 自定义智能体 CRUD（custom_agents 表）、技能目录、推荐问题流与 agent config jsonb。 |
| `:domains` | `com.ragagent.agentmanagement.api` | Agent 管理 · 端口实现：AgentLookupAdapter。 |
| `:domains` | `com.ragagent.agentmanagement.controller` | agent.management 的 @RestController 薄层：智能体 CRUD（com.ragagent.agentmanagement.controller.AgentControll |
| `:domains` | `com.ragagent.agentmanagement.domain` | agent.management 实体（@TableName 跟随表名）。 |
| `:domains` | `com.ragagent.agentmanagement.dto` | agent.management 的响应装配类型（com.ragagent.agentmanagement.dto.AgentResponses）。 |
| `:domains` | `com.ragagent.agentmanagement.mapper` | agent.management 的 MyBatis-Plus 接口与 jsonb 原串 TypeHandler。 |
| `:domains` | `com.ragagent.agentmanagement.service` | 智能体管理用例实现：自定义智能体 CRUD（com.ragagent.agentmanagement.service.CustomAgentService）。 |
| `:domains` | `com.ragagent.agentmanagement.service.builtin` | 内置智能体面（从 agentmanagement.service 切出，对齐方案）： BuiltinAgentRegistry——内置智能体定义、类型与提示词的注册表。 |
| `:domains` | `com.ragagent.agentmanagement.service.config` | 智能体配置面（从 agentmanagement.service 切出，对齐方案）： agent config jsonb 读写（AgentConfigJson）、占位符（AgentPlacehold |
| `:domains` | `com.ragagent.agenttools` | agent 工具**实现**面：把 Agent 的工具调用翻译为业务域公开用例，并把结果格式化为模型可读输出。 |
| `:domains` | `com.ragagent.agenttools.datasource` | agent 数据分析工具族：data_analysis / data_schema 两工具 + DuckDB 生产实现 （AnalysisDuckDbJdbc）与 chatpipeline 会话桥（D |
| `:domains` | `com.ragagent.agenttools.knowledge` | agent 知识检索工具族：knowledge_search / grep_chunks / list_knowledge_chunks / query_knowledge_graph / get_d |
| `:domains` | `com.ragagent.agenttools.sql` | agent SQL 工具族：database_query 工具与手写 SQL 安全守卫（SqlGuard + tokenizer / 深检查 / 注入分析协作者）。 |
| `:domains` | `com.ragagent.agenttools.web` | agent 网页工具族：web_search / web_fetch 两件。 |
| `:domains` | `com.ragagent.agenttools.wiki` | agent wiki 工具族：页面工具十件（读/写/改名/替换/删除/搜索/索引概览/读源文档/标记与更新 issue）+ 内容重写与路由解析（WikiContentRewrite / WikiRou |
| `:domains` | `com.ragagent.approval` | 审批机制域：审批门（Gate）、待审请求/决议消息、Redis pubsub 适配与工具策略。 |
| `:domains` | `com.ragagent.audit` | 审计日志域：审计条目的写入面（com.ragagent.audit.service.AuditLogService）、查询面与保留期清理 （com.ragagent.audit.service.Aud |
| `:domains` | `com.ragagent.audit.api` | 审计 · 端口实现：AuditWriteAdapter。 |
| `:domains` | `com.ragagent.audit.controller` | 审计 · HTTP 层：AuditLogController。 |
| `:domains` | `com.ragagent.audit.domain` | 审计 · 领域模型：AuditLogQuery / AuditLog。 |
| `:domains` | `com.ragagent.audit.dto` | 审计 · 数据传输：AuditLogListResponse。 |
| `:domains` | `com.ragagent.audit.mapper` | 审计 · 映射：AuditLogRepository / AuditLogMapper。 |
| `:domains` | `com.ragagent.audit.service` | 审计 · 服务：AuditLogService / RbacDeniedAuditorRegistrar / WikiActivityAuditRecorder…。 |
| `:domains` | `com.ragagent.auth` | 认证与租户域：登录/OIDC/令牌、用户与租户目录、RBAC 与会话身份。 |
| `:domains` | `com.ragagent.auth.api` | 认证 · 端口实现：AuthTokenAdminAdapter / UserLookupAdapter / AdminUserAdapter。 |
| `:domains` | `com.ragagent.auth.apikey` | API 密钥子域（auth 之下，机器调用凭据面）：租户级 API Key 的签发、校验与访问控制——路由策略（com.ragagent.auth.apikey.domain.APIKeyRouteP |
| `:domains` | `com.ragagent.auth.apikey.controller` | apikey · HTTP 层：TenantAPIKeyController。 |
| `:domains` | `com.ragagent.auth.apikey.controller.filter` | API Key 过滤器面（由 auth.apikey.filter 迁入 apikey.controller.filter，对齐方案）： 鉴权通道与网关拦截、路由授权与策略、范围清理与文件服务豁免、主体拒绝（9 类）。 |
| `:domains` | `com.ragagent.auth.apikey.domain` | apikey · 领域模型：APIKeyStringListTypeHandler / APIKeyRawJsonbTypeHandler / TenantAPIKeyProjections…。 |
| `:domains` | `com.ragagent.auth.apikey.mapper` | apikey · 映射：TenantAPIKeyRepository / TenantAPIKeyMapper / TenantAPIKeyNotFoundException。 |
| `:domains` | `com.ragagent.auth.apikey.service` | apikey · 服务：TenantAPIKeyService / ApiKeyAdminAdapter / TenantAPIKeyBootstrap…。 |
| `:domains` | `com.ragagent.auth.config` | 认证 · 配置：JwtProperties。 |
| `:domains` | `com.ragagent.auth.controller` | auth 域控制器的 HTTP 端点与协作类（7 个协作类）。 |
| `:domains` | `com.ragagent.auth.controller.filter` | 认证过滤器面（由 auth.filter 迁入 controller.filter，对齐方案）： AuthFilter（请求鉴权入口）与 WsAuthSupport（WebSocket 握手鉴权）。 |
| `:domains` | `com.ragagent.auth.domain` | 认证 · 领域模型：TenantConfigRedaction / TenantInvitation / TenantMember…。 |
| `:domains` | `com.ragagent.auth.dto` | 认证 · 数据传输：UpdatePreferencesRequest / TenantInvitationResponse / InvitationLookupResponse…。 |
| `:domains` | `com.ragagent.auth.mapper` | 认证 · 映射：TenantInvitationMapper / TenantMemberMapper / AuthTokenMapper…。 |
| `:domains` | `com.ragagent.auth.service` | 认证 · 服务：TenantInvitationService / TenantMemberService / TenantService…。 |
| `:domains` | `com.ragagent.datasource` | 数据源连接器域：外部知识源（Notion/Feishu/Yuque/RSS/IMA/GitLab 等，见 connector/）的抓取、游标推进、 同步编排（com.ragagent.datasour |
| `:domains` | `com.ragagent.datasource.connector.feishu.core` | feishu · core：FeishuCursorCodec / FeishuWikiTreeOps / FeishuTransport…。 |
| `:domains` | `com.ragagent.datasource.connector.feishu.drive` | feishu · drive：DriveConnector。 |
| `:domains` | `com.ragagent.datasource.connector.feishu.wiki` | feishu · Wiki：WikiConnector。 |
| `:domains` | `com.ragagent.datasource.connector.gitlab` | 连接器 · gitlab：GitLabConnector / GitLabClient / GitLabConfig…。 |
| `:domains` | `com.ragagent.datasource.connector.ima` | 连接器 · ima：ImaRetryPolicy / ImaConnector / ImaApiTypes…。 |
| `:domains` | `com.ragagent.datasource.connector.notion` | 连接器 · notion：NotionPaginatedResponse / NotionDatabaseInfo / NotionDateMention…。 |
| `:domains` | `com.ragagent.datasource.connector.rss` | 连接器 · rss：UnavailableArticleExtractor / ArticleExtractor / JdkXmlFeedParser…。 |
| `:domains` | `com.ragagent.datasource.connector.yuque` | 连接器 · yuque：YuqueRetryPolicy / YuqueConnector / YuqueApiTypes…。 |
| `:domains` | `com.ragagent.datasource.controller` | 数据源 · HTTP 层：DataSourceCredentialsController / DataSourceController。 |
| `:domains` | `com.ragagent.datasource.domain` | 数据源 · 领域模型：DataSourceMapSerializer / DataSourceSyncPayload / DataSourceConstants…。 |
| `:domains` | `com.ragagent.datasource.domain.connector` | 连接器契约面（方案 的 domain.connector/）：连接器 SPI（Connector / StreamingConnector）、流式回调（StreamHandler）、目录与注册表 （C |
| `:domains` | `com.ragagent.datasource.dto` | 数据源 · 数据传输：DatasourceCredentialFieldMetadata / DatasourceCredentialsResponse / DataSourceConfigDto…。 |
| `:domains` | `com.ragagent.datasource.mapper` | 数据源 · 映射：DataSourceRepository / SyncLogRepository / DataSourceMapper…。 |
| `:domains` | `com.ragagent.datasource.service` | 数据源 · 服务：DataSourceService / KnowledgeTagAutoTagProvider / DataSourceSyncResultOps…。 |
| `:domains` | `com.ragagent.datasource.task` | 同步任务面（方案 的 task/）：同步任务队列（DataSourceSyncTaskQueue + InProcess/Redis 双实现）、任务处理器（DataSourceSyncHandler） |
| `:domains` | `com.ragagent.embedchannel` | 嵌入渠道域：对外暴露的嵌入问答渠道（com.ragagent.embedchannel.controller.EmbedChannelController）及其鉴权过滤器、 渠道配置与令牌。 |
| `:domains` | `com.ragagent.embedchannel.controller` | embedchannel 域控制器的 HTTP 端点与协作类（3 个协作类）。 |
| `:domains` | `com.ragagent.embedchannel.controller.filter` | 嵌入渠道鉴权过滤器面（由 embedchannel.filter 迁入 controller.filter，对齐方案）： EmbedAuthFilter——对外嵌入问答的访客鉴权。 |
| `:domains` | `com.ragagent.embedchannel.domain` | 嵌入渠道 · 领域模型：EmbedChannelEntity。 |
| `:domains` | `com.ragagent.embedchannel.mapper` | 嵌入渠道 · 映射：EmbedChannelMapper。 |
| `:domains` | `com.ragagent.embedchannel.service` | 嵌入渠道 · 服务：EmbedChannelService / RedisEmbedTokenStore / EmbedTokenStore。 |
| `:domains` | `com.ragagent.evaluation` | 评估域：评估任务与指标计算（Rouge 等，见 metric/），对外入口 /api/v1/evaluation。 |
| `:domains` | `com.ragagent.evaluation.controller` | 评测 · HTTP 层：EvaluationController。 |
| `:domains` | `com.ragagent.evaluation.domain` | 评测 · 领域模型：QaPair。 |
| `:domains` | `com.ragagent.evaluation.dto` | 评测 · 数据传输：EvaluationRequest / EvaluationDtos。 |
| `:domains` | `com.ragagent.evaluation.metric` | 评测 · 指标：MetricSegmenter / PrecisionMetric / MetricCommon…。 |
| `:domains` | `com.ragagent.evaluation.service` | 评测 · 服务：EvaluationService / DatasetService / EvaluationPromptDefaults…。 |
| `:domains` | `com.ragagent.favorite` | 收藏域：用户对资源的收藏闭环（controller/service/mapper/domain 各一，最小完整域）。 |
| `:domains` | `com.ragagent.favorite.controller` | 收藏 · HTTP 层：UserFavoriteController。 |
| `:domains` | `com.ragagent.favorite.domain` | 收藏 · 领域模型：UserResourceFavorite。 |
| `:domains` | `com.ragagent.favorite.mapper` | 收藏 · 映射：UserResourceFavoriteMapper。 |
| `:domains` | `com.ragagent.favorite.service` | 收藏 · 服务：UserResourceFavoriteService。 |
| `:domains` | `com.ragagent.im` | 即时通讯渠道域：九渠道适配（feishu/wechat/wecom/dingtalk/slack/mattermost/telegram/qqbot/yunzhijia，一渠道一子包） 与运行时（ru |
| `:domains` | `com.ragagent.im.channel.dingtalk` | 渠道 · dingtalk：DingtalkAdapter / DingtalkAdapterFactory / DingtalkStreamClient。 |
| `:domains` | `com.ragagent.im.channel.feishu` | 渠道 · feishu：FeishuAdapter / FeishuLongConnClient / FeishuAdapterFactory…。 |
| `:domains` | `com.ragagent.im.channel.mattermost` | 渠道 · mattermost：MattermostAdapter / MattermostAdapterFactory / MattermostClient。 |
| `:domains` | `com.ragagent.im.channel.qqbot` | 渠道 · qqbot：QqBotAdapter / QqBotAdapterFactory / QqBotGatewayClient…。 |
| `:domains` | `com.ragagent.im.channel.slack` | 渠道 · slack：SlackAdapter / SlackSocketModeClient / SlackAdapterFactory。 |
| `:domains` | `com.ragagent.im.channel.telegram` | 渠道 · telegram：TelegramAdapter / TelegramLongPollingClient / TelegramAdapterFactory。 |
| `:domains` | `com.ragagent.im.channel.wechat` | 渠道 · wechat：WechatQRCodeService / WechatAdapter / WechatAdapterFactory…。 |
| `:domains` | `com.ragagent.im.channel.wecom` | 渠道 · wecom：WecomWebhookAdapter / WecomWSAdapter / WecomLongConnClient…。 |
| `:domains` | `com.ragagent.im.channel.yunzhijia` | 渠道 · yunzhijia：YunzhijiaAdapter / YunzhijiaLongConnClient / YunzhijiaAdapterFactory…。 |
| `:domains` | `com.ragagent.im.controller` | IM · HTTP 层：ImCallbackController / ImChannelController。 |
| `:domains` | `com.ragagent.im.domain` | IM · 领域模型：ChannelSessionEntity / ImChannelEntity。 |
| `:domains` | `com.ragagent.im.mapper` | IM · 映射：ChannelSessionMapper / ImChannelMapper。 |
| `:domains` | `com.ragagent.im.runtime` | IM · 运行时：AdapterInterfaces / FeishuWecomCrypt / SlackAdapterCore…。 |
| `:domains` | `com.ragagent.im.service` | IM · 服务：ImChannelService / ImAttachmentPreparer / ImKnowledgeBridgeOps…。 |
| `:domains` | `com.ragagent.initialization` | 系统初始化与模型能力域：/api/v1/initialization/* 的初始化配置、Ollama 管理、模型连通性测试、 文本抽取试验与语音识别（ASR）。 |
| `:domains` | `com.ragagent.initialization.controller` | initialization 的 @RestController 薄层：端点注解与委托，逻辑在 service 协作者 （com.ragagent.initialization.controller. |
| `:domains` | `com.ragagent.initialization.dto` | initialization 的响应装配类型（com.ragagent.initialization.dto.InitResponses）。 |
| `:domains` | `com.ragagent.initialization.service` | initialization 用例实现：初始化配置（com.ragagent.initialization.service.InitializationConfigService）、 模型连通性测试、 |
| `:domains` | `com.ragagent.knowledge` | 知识库域：知识的存储、处理管线与检索面。 |
| `:domains` | `com.ragagent.knowledge.api` | 知识库 · 端口实现：KnowledgeChunkPipelineAdapter / KnowledgeBaseCatalogAdapter / KnowledgeFinalizeAdapter…。 |
| `:domains` | `com.ragagent.knowledge.chunker` | 知识库 · 分块：HeuristicSplitter / DocumentProfiler / HeadingHierarchy…。 |
| `:domains` | `com.ragagent.knowledge.client` | 知识库出站客户端：DocReaderClient（文档解析服务）、EmbedderClient（向量化服务）。 |
| `:domains` | `com.ragagent.knowledge.config` | 知识库 · 配置：HousekeepingProperties / BatchEmbedProperties / DocReaderProperties。 |
| `:domains` | `com.ragagent.knowledge.controller` | 知识库 HTTP 面：薄绑定层（@Valid DTO → service → 裸资源视图），线格式 camelCase、无信封； 无手搓解析与信封拼装。 |
| `:domains` | `com.ragagent.knowledge.domain` | 知识库持久化实体与 JSON 结构（MyBatis-Plus 注解 + Jackson 序列化注解）。 |
| `:domains` | `com.ragagent.knowledge.domain.access` | 知识库访问守卫：KnowledgeRouteGuards（路由级租户/成员校验）+ KnowledgeAccessGuard（知识库读写权限）+ ChunkAccessGuard（chunk 写守卫） |
| `:domains` | `com.ragagent.knowledge.domain.span` | 处理轨迹（span）追踪：SpanTracker 维护 attempt/stage/sub-span 的开启与收口， 落库经 KnowledgeSpanRepository。 |
| `:domains` | `com.ragagent.knowledge.dto` | 知识库域的**请求/响应载荷**（HTTP 面 DTO，无逻辑）。 |
| `:domains` | `com.ragagent.knowledge.dto.chunk` | chunk 面载荷：chunk 读写与修订、重解析、内容预览（Chunk* / *Reparse* / Preview*）。 |
| `:domains` | `com.ragagent.knowledge.dto.doc` | 文档（Knowledge）面载荷：列表/详情/搜索/移动与文件夹、重新索引、清空内容、最后导入状态。 |
| `:domains` | `com.ragagent.knowledge.dto.faq` | FAQ 面载荷：条目/导入/搜索/合并（Faq*）与生成问题（*GeneratedQuestion*、AddSimilarQuestions*）。 |
| `:domains` | `com.ragagent.knowledge.dto.kb` | 知识库本体载荷：增删改查/复制/副本，以及它的配置视图（ASR / VLM / 分块 / 索引策略 / 解析引擎规则）。 |
| `:domains` | `com.ragagent.knowledge.dto.tag` | 标签面载荷：标签增删改查分页，以及知识库标签批量绑定与统计。 |
| `:domains` | `com.ragagent.knowledge.index` | 索引面（`index/`）：知识条目与向量/检索引擎的绑定与写入适配。 |
| `:domains` | `com.ragagent.knowledge.mapper` | MyBatis-Plus mapper 接口层：每张表一个 *Mapper，只声明 SQL/条件构造， 不承载业务规则。 |
| `:domains` | `com.ragagent.knowledge.repository` | 仓储门面层：把 mapper 的原子操作组合成领域可用的读写契约，并集中承载 软删三面孔、全字段 UPDATE、乐观锁冲突与 PG/H2 方言分支。 |
| `:domains` | `com.ragagent.knowledge.service` | 知识库用例层（顶层残部）。 |
| `:domains` | `com.ragagent.knowledge.service.chunk` | 分块面（从 service 切出的第二个子面）：分块的编辑、抽取与读取。 |
| `:domains` | `com.ragagent.knowledge.service.enrichment` | 富化面（从 service 切出的子面）：处理管线完成后的 LLM 富化——摘要与问题生成。 |
| `:domains` | `com.ragagent.knowledge.service.faq` | FAQ 面（从 service 整簇切出的第一个子面）。 |
| `:domains` | `com.ragagent.knowledge.service.image` | 服务 · 图片：ExtractedImageStore。 |
| `:domains` | `com.ragagent.knowledge.service.management` | 知识域**应用/门面面**（起）：对外的门面 KnowledgeService，以及它向下编排的 用例服务（知识库/文件夹、文件与手工内容、解析生命周期、克隆/移动/标签/批处理）。 |
| `:domains` | `com.ragagent.knowledge.service.query` | 查询面（从 service 切出，按 命名）：检索与 span 读取。 |
| `:domains` | `com.ragagent.knowledge.storage` | 知识库文件与对象存储：LocalStorageService（本地卷）+ TenantFileStorage（租户感知门面， 知识上传/读取/删除的唯一入口）。 |
| `:domains` | `com.ragagent.knowledge.support` | 知识库的纯逻辑支撑：无状态、零依赖的算法与规则，不作为 Spring bean。 |
| `:domains` | `com.ragagent.knowledge.task` | 知识库异步任务：队列与执行。 |
| `:domains` | `com.ragagent.mcp` | MCP 域：Model Context Protocol 的仓内实现——协议（protocol/）、OAuth（oauth/，含回调与令牌存储）、 服务注册与调用面；对外 /api/v1/mcp-services。 |
| `:domains` | `com.ragagent.mcp.api` | MCP · 端口实现：McpToolRegistrationAdapter / McpApprovalOpsAdapter。 |
| `:domains` | `com.ragagent.mcp.controller` | mcp 域控制器的 HTTP 端点与协作类（2 个协作类）。 |
| `:domains` | `com.ragagent.mcp.domain` | MCP · 领域模型：McpService / McpAuthConfigTypeHandler / McpToolListTypeHandler…。 |
| `:domains` | `com.ragagent.mcp.dto` | MCP · 数据传输：McpToolApprovalPolicyRequest / McpCredentialFieldMetadata / ResolveToolApprovalRequest…。 |
| `:domains` | `com.ragagent.mcp.mapper` | MCP · 映射：McpToolApprovalMapper / McpOAuthClientMapper / McpOAuthTokenMapper…。 |
| `:domains` | `com.ragagent.mcp.oauth` | MCP · OAuth：McpOAuthRepositoryAdapter / OAuthRepository / OAuthAuthorizationStatus…。 |
| `:domains` | `com.ragagent.mcp.protocol` | MCP · 协议：StreamableHttpTransport / ReadResourceResult / ServerCapabilities…。 |
| `:domains` | `com.ragagent.mcp.repository` | MCP · 仓储：McpToolApprovalRepository / McpMetadataRepository / McpOAuthRepository。 |
| `:domains` | `com.ragagent.mcp.service` | MCP · 服务：AgentToolApprovalService / McpToolApprovalService / McpOAuthFlowService…。 |
| `:domains` | `com.ragagent.mcp.tool` | MCP 工具实现族（方案 的 tool/）：把 MCP 服务暴露的工具翻译为 Agent 工具—— 调用（McpCallTool）、发现与目录（McpDiscoverTool / McpCatalog |
| `:domains` | `com.ragagent.memory` | 记忆域：用户长期记忆的抽取（com.ragagent.memory.service.MemoryExtractionService）、去重与合并、 检索与生命周期（租约、墓碑、容量； |
| `:domains` | `com.ragagent.memory.api` | 记忆 · 端口实现：MemoryPipelineAdapter / MemoryAccessAdapter。 |
| `:domains` | `com.ragagent.memory.controller` | 记忆 · HTTP 层：MemoryController。 |
| `:domains` | `com.ragagent.memory.domain` | 记忆域实体（留存面）：抽取会话/批次/失败/状态（MemoryExtraction*）、 渲染与投影（MemoryRender / MemoryDocView / MemoryTopicView / |
| `:domains` | `com.ragagent.memory.domain.item` | 记忆条目面（拆自 memory.domain，对齐方案 的 domain.item/）： MemoryItem（可检索的条目）、MemoryItemEmbedding（其向量）、 MemoryTombstone（删除墓碑）。 |
| `:domains` | `com.ragagent.memory.domain.scope` | 记忆范围面（拆自 memory.domain，对齐方案 的 domain.scope/）： MemoryScope（作用域）、MemorySettings（开关与阈值）、 MemorySubject 与缺失异常。 |
| `:domains` | `com.ragagent.memory.domain.vector` | 记忆向量检索面（拆自 memory.domain，对齐方案 的 domain.vector/）： MemoryVectors（向量读写）、MemoryVectorQuery（检索请求）、 MemoryVectorHit（命中行）。 |
| `:domains` | `com.ragagent.memory.dto` | 记忆模块对外 HTTP 响应/请求的 DTO。 |
| `:domains` | `com.ragagent.memory.mapper` | 记忆 · 映射：MemoryExtractionSessionMapper / MemoryItemEmbeddingMapper / MemoryDocAffinityMapper…。 |
| `:domains` | `com.ragagent.memory.repository` | 仓储门面层：记忆域的持久化门面与其协作者。 |
| `:domains` | `com.ragagent.memory.service` | 记忆 · 服务：MemoryConsolidationService / MemoryExtractionService / MemoryVectorService…。 |
| `:domains` | `com.ragagent.model` | 模型配置域：模型条目 CRUD（com.ragagent.model.controller.ModelController）与调试面 （com.ragagent.model.controller.Mo |
| `:domains` | `com.ragagent.model.api` | 模型 · 端口实现：ModelCatalogAdapter。 |
| `:domains` | `com.ragagent.model.controller` | 模型 · HTTP 层：ModelCredentialsController / ModelDebugController / ModelController。 |
| `:domains` | `com.ragagent.model.domain` | 模型 · 领域模型：ModelParametersTypeHandler / Model。 |
| `:domains` | `com.ragagent.model.dto` | 模型 · 数据传输：ModelCredentialFieldMetadata / ModelCredentialsPutRequest / ModelCredentialsResponse…。 |
| `:domains` | `com.ragagent.model.mapper` | 模型 · 映射：ModelUsageMapper / ModelMapper。 |
| `:domains` | `com.ragagent.model.service` | 模型 · 服务：ModelService / BuiltinModelsReconciler / ModelProviderCatalog…。 |
| `:domains` | `com.ragagent.session` | 会话域：会话与消息的读写（com.ragagent.session.service.SessionService / com.ragagent.session.service.MessageServi |
| `:domains` | `com.ragagent.session.api` | 会话 · 端口实现：KnowledgeQaEventAdapter / EmbedSessionOpsAdapter / EmbedSessionAdapter…。 |
| `:domains` | `com.ragagent.session.controller` | session 域控制器的 HTTP 端点（协议适配层）。 |
| `:domains` | `com.ragagent.session.controller.sse` | session 的 SSE 传输面（由 session.sse 迁入 controller.sse，对齐方案）： SseContract（帧契约）、SseFrameWriter（帧写出）、Stream |
| `:domains` | `com.ragagent.session.domain` | 会话域实体（留存面；已切出三簇：conversation/（会话与分页）、 document/（临时文档）、message/（消息族 + artifact 版本化））。 |
| `:domains` | `com.ragagent.session.domain.conversation` | 会话实体面（由 session.domain 切出，对齐方案）： Session 及其列表/分页/状态（SessionListItem / SessionListQuery / SessionPage |
| `:domains` | `com.ragagent.session.domain.document` | 会话文档面（由 session.domain 切出，对齐方案）：TemporaryDocument。 |
| `:domains` | `com.ragagent.session.domain.message` | 消息实体面（由 session.domain 切出，对齐方案）：Message 与其 附件/图片/工件/搜索/建议集（MessageArtifact / MessageSuggestionSet …） |
| `:domains` | `com.ragagent.session.dto` | 会话 · 数据传输：BatchDeleteSessionsRequest / GenerateTitleResponse / SearchMessagesRequest…。 |
| `:domains` | `com.ragagent.session.mapper` | 会话 · 映射：TemporaryDocumentMapper / MessageSuggestionMapper / MessageMapper…。 |
| `:domains` | `com.ragagent.session.qa` | QA 轮的编排簇（阶段 5 从 session.controller 剥离）。 |
| `:domains` | `com.ragagent.session.repository` | 仓储门面层：把 mapper 的原子操作组合成领域可用的读写契约，并集中承载软删、全字段 UPDATE 与 PG/H2 方言分支。 |
| `:domains` | `com.ragagent.session.service` | 会话 · 服务：SessionKnowledgeQaService / TemporaryDocumentService / SessionAgentQaService…。 |
| `:domains` | `com.ragagent.session.service.rag` | 聊天管线（库式域，无 HTTP 面）：可插拔的 Plugin 链——检索、合并、重排、实体抽取、数据分析、历史装载等， 由会话域按顺序驱动。 |
| `:domains` | `com.ragagent.session.service.rag.plugin` | chat 管线的**插件实现**：Plugin 接口 + 18 个步骤实现 （query_understand → search → merge → rerank → into_chat_messag |
| `:domains` | `com.ragagent.session.service.rag.spi` | 管线 · spi：KnowledgeQaEventPort。 |
| `:domains` | `com.ragagent.session.service.rag.support` | chat 管线的**纯逻辑辅助**：检索结果去重/去包含（com.ragagent.session.service.rag.support.SearchSupport）、 查询分词与关键词抽取（com |
| `:domains` | `com.ragagent.session.service.suggestion` | 消息建议面（由 session.service 切出，对齐方案 的 service.suggestion/）： MessageSuggestionService（建议生成的编排与落库）与 Messag |
| `:domains` | `com.ragagent.session.support` | 会话 · 支撑：MessageReferenceRewriter / PipelineViews。 |
| `:domains` | `com.ragagent.storage` | 存储后端域：存储后端配置与凭据（com.ragagent.storage.service.StorageBackendService，对外 /api/v1/storage-backends） 与文件服 |
| `:domains` | `com.ragagent.storage.api` | 存储 · 端口实现：StorageBackendPortAdapter / ProviderFileStoreAdapter / GlobalFileServiceAdapter…。 |
| `:domains` | `com.ragagent.storage.config` | 存储 · 配置：LocalStorageEnvProperties / ResourceUrlModeProperties / StorageProviderEnv。 |
| `:domains` | `com.ragagent.storage.controller` | 存储 · HTTP 层：StorageBackendController / FileProxyController。 |
| `:domains` | `com.ragagent.storage.domain` | 存储 · 领域模型：StoredResource / StorageBackend。 |
| `:domains` | `com.ragagent.storage.dto` | 存储 · 数据传输：StorageBackendResponse / StorageConfig。 |
| `:domains` | `com.ragagent.storage.fileserve` | 存储 · 文件服务：ProviderFileContentService / BackendScopedFileService / LocalFileContentService…。 |
| `:domains` | `com.ragagent.storage.mapper` | 存储 · 映射：StorageBackendRepository / ResourceRepository。 |
| `:domains` | `com.ragagent.storage.provider` | 存储 · 提供方：S3CompatibleFileService / SeekableFileService / LocalFileService…。 |
| `:domains` | `com.ragagent.storage.service` | 存储 · 服务：ResourceCatalogService / StorageBackendService / DefaultStorageBackendProvisioner…。 |
| `:domains` | `com.ragagent.storage.support` | 存储域的支撑层：存储 URL 解析与重写——签名 URL、流式重写与文件服务解析 （原顶层包 storageurl， 并入）。 |
| `:domains` | `com.ragagent.system` | 系统设置域：系统设置的读写（com.ragagent.system.service.SystemSettingService）与管理员面 （/api/v1/system、/api/v1/system/admin）。 |
| `:domains` | `com.ragagent.system.controller` | 系统 · HTTP 层：SystemAdminController / SystemController。 |
| `:domains` | `com.ragagent.system.domain` | 系统 · 领域模型：SystemSetting。 |
| `:domains` | `com.ragagent.system.dto` | 系统 · 数据传输：SystemDtos。 |
| `:domains` | `com.ragagent.system.mapper` | 系统 · 映射：SystemSettingMapper。 |
| `:domains` | `com.ragagent.system.service` | 系统 · 服务：SystemAdminUserService / SystemSettingService / SystemInfoService…。 |
| `:domains` | `com.ragagent.tenant` | 租户域（由 common.tenant 搬出）：租户实体 com.ragagent.tenant.domain.Tenant、 TenantMapper、租户级配置 VO（APIPrincipalCo |
| `:domains` | `com.ragagent.tenant.api` | 租户 · 端口实现：TenantStorageQuotaAdapter。 |
| `:domains` | `com.ragagent.tenant.domain` | 租户 · 领域模型：APIPrincipalConfigTypeHandler / Tenant。 |
| `:domains` | `com.ragagent.tenant.mapper` | 租户 · 映射：TenantMapper。 |
| `:domains` | `com.ragagent.tenant.repository` | 租户仓储面（由 tenant 根包迁出，对齐方案 的 repository/）： Tenant（租户实体）、TenantLookup（按 id/域名查找）与 TenantConfigRedaction（配置脱敏，供查询面复用）。 |
| `:domains` | `com.ragagent.vectorstore` | 向量库配置域：向量库类型目录（com.ragagent.vectorstore.dto.VectorStoreTypes）与配置校验/持久化 （com.ragagent.vectorstore.ser |
| `:domains` | `com.ragagent.vectorstore.controller` | 向量库 · HTTP 层：VectorStoreController。 |
| `:domains` | `com.ragagent.vectorstore.domain` | 向量库 · 领域模型：ConnectionConfigTypeHandler / IndexConfigTypeHandler / VectorStoreEngines…。 |
| `:domains` | `com.ragagent.vectorstore.dto` | 向量库 · 数据传输：VectorStoreResponse / VectorStoreTypes。 |
| `:domains` | `com.ragagent.vectorstore.mapper` | 向量库 · 映射：VectorStoreRepository。 |
| `:domains` | `com.ragagent.vectorstore.service` | 向量库 · 服务：VectorStoreConfigService / VectorStoreLookupAdapter。 |
| `:domains` | `com.ragagent.websearch` | 联网搜索域：搜索 provider 目录与凭据（对外 /api/v1/web-search-providers）、搜索执行 （com.ragagent.websearch.service.WebSea |
| `:domains` | `com.ragagent.websearch.api` | 网页搜索 · 端口实现：WebSearchBackendsAdapter / WebSearchSupportAdapter。 |
| `:domains` | `com.ragagent.websearch.controller` | 网页搜索 · HTTP 层：WebSearchProviderCredentialsController / WebSearchProviderController / WebSearchController。 |
| `:domains` | `com.ragagent.websearch.domain` | 网页搜索 · 领域模型：WebSearchParamsTypeHandler / WebSearchProviderParams / WebSearchProviderEntity。 |
| `:domains` | `com.ragagent.websearch.dto` | 网页搜索 · 数据传输：WebSearchProviderResponse / WebSearchProviderTypes。 |
| `:domains` | `com.ragagent.websearch.fetch` | 网页搜索 · 抓取：PipelinePageFetcherImpl / ToolPageFetcherImpl / BrowserRenderer…。 |
| `:domains` | `com.ragagent.websearch.mapper` | 网页搜索 · 映射：WebSearchProviderRepository。 |
| `:domains` | `com.ragagent.websearch.provider` | 网页搜索 · 提供方：WebSearchProviderRegistry / ZhipuWebSearchProvider / DuckDuckGoProvider…。 |
| `:domains` | `com.ragagent.websearch.service` | 网页搜索 · 服务：WebSearchTempKbStateService / WebSearchProviderService / WebSearchService。 |
| `:domains` | `com.ragagent.wiki` | wiki 域:知识库内联百科(页面/修订/文件夹树/链接图/摄取管线)。 |
| `:domains` | `com.ragagent.wiki.api` | Wiki · 端口实现：WikiPageOpsAdapter。 |
| `:domains` | `com.ragagent.wiki.controller` | wiki 的 @RestController:页面 CRUD/修订/文件夹/索引/图谱/统计/问题端点(raw-JSON 对齐形态,契约换锚时整体重写为 DTO)。 |
| `:domains` | `com.ragagent.wiki.domain` | wiki 域留存面（拆簇后）：WikiConfig / WikiConstants / WikiException / WikiExtractionGranularity / WikiSourceRe |
| `:domains` | `com.ragagent.wiki.domain.folder` | wiki 文件夹面（拆自 wiki.domain，对齐方案）：WikiFolder 与其创建/更新/列表请求-响应、冲突与"非空""不存在"异常。 |
| `:domains` | `com.ragagent.wiki.domain.graph` | wiki 链接图面（拆自 wiki.domain）：WikiGraph（页面入链/出链视图）。 |
| `:domains` | `com.ragagent.wiki.domain.index` | wiki 索引面（拆自 wiki.domain）：WikiIndex / WikiIndexEntry（目录树条目）与 WikiCategoryPaths（分类路径规范化）。 |
| `:domains` | `com.ragagent.wiki.domain.issue` | wiki 巡检问题面（拆自 wiki.domain）：WikiLintIssue / WikiLintReport（lint 单条问题与汇总报告）。 |
| `:domains` | `com.ragagent.wiki.domain.page` | wiki 页面面（拆自 wiki.domain）：WikiPage 与页面修订、列表/移动/回滚/裁剪请求-响应、冲突与"不存在"异常，以及页面统计 WikiStats。 |
| `:domains` | `com.ragagent.wiki.mapper` | wiki 的 MyBatis-Plus 接口（@MapperScan 扫描范围内）。 |
| `:domains` | `com.ragagent.wiki.prompt` | wiki 的提示词模板目录与渲染支撑。 |
| `:domains` | `com.ragagent.wiki.repository` | 仓储门面层：把 mapper 的原子操作组合成页面/文件夹级的读写契约，并集中承载软删、 版本冲突与 PG 方言分支。 |
| `:domains` | `com.ragagent.wiki.service` | wiki 用例层根包：**跨切面端口与 LLM/提示词适配**——分块清洗（ com.ragagent.wiki.service.WikiChunkCleaner + 缺省实现）、图片富化、知识收尾、 |
| `:domains` | `com.ragagent.wiki.service.ingest` | wiki **摄取（ingest）管线**：批量/单篇入库的门面与协作者 （com.ragagent.wiki.service.ingest.WikiIngestService、 com.ragage |
| `:domains` | `com.ragagent.wiki.service.ingest.graph` | wiki 摄取·链接图面（拆自 wiki.service.ingest，对齐方案 的 service.ingest.graph/）： WikiGraphCalculator——从页面/引用计算链接图（入链、出链、孤儿）。 |
| `:domains` | `com.ragagent.wiki.service.page` | wiki **页面**面：页面服务（接口门面 + 实现）、文件夹/视图支撑、链接与 lint （com.ragagent.wiki.service.page.WikiLinkify、WikiCross |
| `:model-runtime` | `com.ragagent.embedding` | 嵌入向量 provider 客户端（库式域，无 HTTP 面）：com.ragagent.embedding.EmbedderFactory 选择器、 EmbeddingHttp/ProviderJson 传输与编码、池化与失败降级。 |
| `:model-runtime` | `com.ragagent.embedding.provider` | 嵌入 provider 的**具体实现**（各家 HTTP 调用与响应解析）：OpenAI / AzureOpenAI / Gemini / Jina / Nvidia / Ollama / Aliy |
| `:model-runtime` | `com.ragagent.llm` | LLM 客户端（库式域，无 HTTP 面）：provider 适配（provider/）、对话流转与流式（chat/， 含 com.ragagent.modelruntime.adapter.prov |
| `:model-runtime` | `com.ragagent.llm.domain` | LLM · 领域模型：ProviderMetadataMaps。 |
| `:model-runtime` | `com.ragagent.llm.extract` | LLM · extract：EntityExtractionAdapter / EntityExtraction。 |
| `:model-runtime` | `com.ragagent.llm.limiter` | LLM · limiter：ModelConcurrencyLimiter / BackgroundTaskContext / ConcurrencyGovernor…。 |
| `:model-runtime` | `com.ragagent.llm.ollama` | LLM · ollama：OllamaService / OllamaChatResponse / OllamaChatRequest…。 |
| `:model-runtime` | `com.ragagent.llm.provider` | LLM · 提供方：AzureOpenAIProvider / SiliconFlowProvider / OpenRouterProvider…。 |
| `:model-runtime` | `com.ragagent.modelcontext` | 模型上下文装配（库式域）：来源注册表（com.ragagent.modelcontext.SourceRegistry）与模型输出封装 （com.ragagent.modelcontext.Model |
| `:model-runtime` | `com.ragagent.modelruntime` | 模型运行时：模型调用与 provider 适配、流生命周期与限流。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter` | 模型运行时的适配面：按协议家族归类的 provider 实现与访问/凭据适配。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter.provider` | provider 适配实现（chat/embedding/asr 等按能力分面）。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter.provider.asr` | ASR（语音转写）适配：AsrTranscriber。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter.provider.chat` | 对话通道：远端 API 家族（RemoteApi* / RemoteHttpOps / SSE 与流状态 / 缓存 / 预算 / 图片解析）与本地 Ollama。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter.provider.chat.anthropic` | Anthropic 协议家族：请求/响应/内容块/工具与工具流事件（由 `llm.chat` 归位）。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter.provider.chat.common` | 跨 provider 共享的对话底座：装配（ProviderAdapter(s)）、并发客户端、思考策略与流式修正，（流句柄三件已迁 `platform.stream`——契约 ChatStream 的队列实现与追踪装饰器共用）。 |
| `:model-runtime` | `com.ragagent.modelruntime.adapter.provider.vlm` | 提供方 · 视觉：VlmHttpTransport / VlmClient。 |
| `:model-runtime` | `com.ragagent.modelruntime.application` | 模型运行时 · application：ModelRuntimeAssembler / ModelRuntimeConfigs / LlmChatClients…。 |
| `:model-runtime` | `com.ragagent.rerank` | 重排 provider 客户端（库式域，无 HTTP 面）：RerankerFactory 选择器、RerankHttp/ProviderJson 传输与 RankResult 形态，只做"候选 → 重排分"。 |
| `:model-runtime` | `com.ragagent.rerank.provider` | 重排 provider 的**具体实现**：OpenAI / Jina / Nvidia / Aliyun / Volcengine / Zhipu / Lkeap / 各家 HTTP 调用与响应解析。 |
| `:platform` | `com.ragagent.platform` | 平台面（platform，阶段 6 起逐步收纳）：框架适配、可靠执行与通用技术能力。 |
| `:platform` | `com.ragagent.platform.access` | 平台访问上下文面（方案 的 platform.access/）：请求级会话与追踪载体。 |
| `:platform` | `com.ragagent.platform.config` | 平台配置面：部署属性与环境查找面。 |
| `:platform` | `com.ragagent.platform.event` | 平台事件总线面（由 com.ragagent.event 迁入，对齐方案 的 platform.event/）： Event / EventBus / EventJson 等 12 类——进程内事件总线与载荷编解码。 |
| `:platform` | `com.ragagent.platform.http` | 平台 HTTP 面：SSRF 策略下的安全抓取工具，以及全进程共享的裸 HTTP 传输层 （com.ragagent.platform.http.LlmTransport：连接级 SSRF 校验 + |
| `:platform` | `com.ragagent.platform.knowledge` | 平台 · 知识库：FaqChunkMetadataCodec / ImageInfoMatchUtil / GrepChunksScoring…。 |
| `:platform` | `com.ragagent.platform.model` | 平台模型网关面：模型/向量网关（provider 归 model-runtime）。 |
| `:platform` | `com.ragagent.platform.persistence` | 平台持久化面：MyBatis 拦截器（全表写守卫 / 租户过滤）、分页请求、JSONB / 时间类型处理器、方言与 LIKE 转义。 |
| `:platform` | `com.ragagent.platform.prompt` | 平台提示词面：模板目录、常量、占位符与组装。 |
| `:platform` | `com.ragagent.platform.redis` | 平台 · Redis：SpringRedisPubSub / RedisPubSub。 |
| `:platform` | `com.ragagent.platform.security` | 平台安全面：加密（AES）、SSRF 守卫/白名单、输入与日志净化、IP 分类、用户名查找。 |
| `:platform` | `com.ragagent.platform.settings` | 平台 · 设置：ConversationProperties / SystemSettingRegistry。 |
| `:platform` | `com.ragagent.platform.storage.config` | 平台存储配置面：允许清单、上传上限与存储运行时环境。 |
| `:platform` | `com.ragagent.platform.storage.fileserve` | 存储 · 文件服务：FileTransport / FileProxyPort / FileServeHttp…。 |
| `:platform` | `com.ragagent.platform.storage.support` | 存储 · 支撑：FileResolverFactory / ResourceUrlMode / StreamRewriter…。 |
| `:platform` | `com.ragagent.platform.stream` | 流事件基础设施：Redis / 内存两种流管理器（com.ragagent.platform.stream.RedisStreamManager）与事件载体 （com.ragagent.platfor |
| `:platform` | `com.ragagent.platform.task` | 平台任务面：Redis 任务队列核心。 |
| `:platform` | `com.ragagent.platform.text` | 平台文本面（的 `platform.text/`）：HTML→Markdown、实体/空白/代码点处理、路径与文件类型推断。 |
| `:platform` | `com.ragagent.platform.tool` | 平台 · 工具：BaseTool。 |
| `:platform` | `com.ragagent.platform.tracing.decorators` | 追踪 · decorators：LangfuseChatClient / LangfuseEmbedder / LangfusePayloads…。 |
| `:platform` | `com.ragagent.platform.tracing.langfuse` | 平台追踪面（Langfuse OTel 适配； |
| `:platform` | `com.ragagent.platform.web` | 平台 Web 面：统一响应外壳、JSON 映射、分页/字段参数、请求 ID 过滤器、非空体/RBAC 拦截器与序列化器。 |
| `:platform` | `com.ragagent.platform.web.error` | 平台错误面（的 `platform.web.error/`）：`BizException` 家族、`GuardForbiddenException`、`PlainErrorException` 与全局异常处理器。 |
| `:platform` | `com.ragagent.platform.web.health` | 平台健康检查面。 |
| `:retrieval-engine` | `com.ragagent.retrieval` | 检索引擎域（库式域，**无 HTTP 面**）：多引擎仓储（engine/：OpenSearch/Doris/Milvus/Qdrant/Tencent…， 含 postgres 引擎的 embedd |
| `:retrieval-engine` | `com.ragagent.retrieval.artifact` | 检索 · artifact：ArtifactReferenceRewriter。 |
| `:retrieval-engine` | `com.ragagent.retrieval.config` | 检索 · 配置：RetrievalEnvLookup。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine` | 检索 · 引擎：KeywordsVectorHybridRetrieveEngineService / PgVectorRetrieveRepository / RetrieveEngineRepository…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.doris` | 引擎 · doris：DorisRetrieveRepository / DorisStreamLoadClient / DorisVectorEmbedding…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.elasticsearch` | 引擎 · elasticsearch：ElasticsearchV8RetrieveRepository / ElasticsearchV7RetrieveRepository / ElasticsearchV8SearchOps…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.milvus` | 引擎 · milvus：MilvusRetrieveRepository / MilvusVectorEmbedding / MilvusRestClient…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.opensearch` | 引擎 · opensearch：OpenSearchRetrieveRepository / OpenSearchSearchOps / OpenSearchWriteOps…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.qdrant` | 引擎 · qdrant：QdrantRetrieveRepository / QdrantVectorEmbedding / QdrantRestClient…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.sqlite` | 引擎 · sqlite：SqliteRetrieveRepository / SqliteCjkBigram / SqliteSearchOps…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.tencentvectordb` | 引擎 · tencentvectordb：TencentVectorDbRetrieveRepository / TencentVectorDbRestClient / TencentVectorDbSearchOps…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.engine.weaviate` | 引擎 · weaviate：WeaviateRetrieveRepository / WeaviateVectorEmbedding / WeaviateRestClient…。 |
| `:retrieval-engine` | `com.ragagent.retrieval.graph` | 检索 · 图谱：Neo4jGraphRepository / Neo4jGraphConfig / Neo4jProperties。 |
| `:retrieval-engine` | `com.ragagent.retrieval.support` | 检索引擎的支撑层：检索文本归一/分词/哈希、chunk 内容拼接、grep 结果视图与打分等无状态算法 （原顶层包 searchutil， 并入）。 |
| `:retrieval-engine` | `com.ragagent.retrieval.telemetry` | 检索 · telemetry：RetrievalObs。 |
