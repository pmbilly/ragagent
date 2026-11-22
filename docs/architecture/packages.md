<!-- 由 scripts/gen-arch-index.py 生成；不要手改。 -->
# 包索引

348 份 `package-info.java` 的首句摘要，按模块分组。找代码时先按模块，再按包名。

| 模块 | 包 | 职责 |
|---|---|---|
| `:backend` | `com.ragagent` | com · ragagent：RagAgentApplication。 |
| `:backend` | `com.ragagent.agent` | ReAct 引擎域：AgentEngine 门面与段协作者（think/act/observe/prompt/finalize/steer/context-debug/ReAct 迭代）、 提示词装配、token 预算与取消探测。 |
| `:backend` | `com.ragagent.agent.budget` | token 预算与上下文占用：TokenEstimator（token 估算，压缩与裁剪共用）、 AgentBudgets（轮次 / completion / 上下文预留等预算常量与换算）、 Cont |
| `:backend` | `com.ragagent.agent.compaction` | 上下文压缩：Compactor、溢出判定、截断点选择与压缩结果/原因类型。 |
| `:backend` | `com.ragagent.agent.domain` | 引擎状态类型：AgentState/AgentStep/AgentToolCall/ToolResult 等，落库 jsonb 与消息响应共用（键名改动须连带前端）。 |
| `:backend` | `com.ragagent.agent.prompt` | agent 侧提示词构件：AgentPromptTemplates（系统提示词模板按 mode 选取，经 platform.prompt.PromptSource 装载）、GroundingPrompt（按当前工具清单生成 内容接地指引）。 |
| `:backend` | `com.ragagent.agent.skills` | 指令型技能：SKILL.md 解析（Skill/Frontmatter）、宿主目录加载与技能管理器（提示词注入路径）。 |
| `:backend` | `com.ragagent.agent.skills.mapper` | 技能 · 映射：SkillMapper。 |
| `:backend` | `com.ragagent.agent.tool` | Agent 运行面的**工具框架**（`agent.tool/`）：工具契约与基类、定义与 schema、注册表、 参数校验与投射、执行上下文与预算、输出持久化、取消与能力标记、通用单件工具。 |
| `:backend` | `com.ragagent.agent.tool.runtime` | 工具运行时横切件（不放具体工具）：ToolResultPersist（工具结果的客户端 / 存储投影）、 MessageSanitizer 与 ThinkBlocks / ThinkStreamSpl |
| `:backend` | `com.ragagent.agentengine.api` | Agent 引擎的对外契约：执行请求/句柄/工厂、工具能力、技能目录、预算与流式信号。 |
| `:backend` | `com.ragagent.agentengine.json` | Agent 面契约载荷的 Jackson 装配（mixin 与注册表）。 |
| `:backend` | `com.ragagent.agentmanagement` | 智能体管理面：内建智能体注册表与类型预设、自定义智能体 CRUD（custom_agents）、 技能目录、推荐问题流与 agent config jsonb。 |
| `:backend` | `com.ragagent.agentmanagement.adapter` | Agent 管理 · 端口实现：AgentLookupAdapter。 |
| `:backend` | `com.ragagent.agentmanagement.controller` | 智能体管理面的 HTTP 薄层：智能体 CRUD（com.ragagent.agentmanagement.controller.AgentController） 与技能目录（com.ragagent |
| `:backend` | `com.ragagent.agentmanagement.domain` | 智能体管理实体（@TableName 跟随表名）。 |
| `:backend` | `com.ragagent.agentmanagement.dto` | 智能体管理面的响应装配类型（com.ragagent.agentmanagement.dto.AgentResponses）。 |
| `:backend` | `com.ragagent.agentmanagement.mapper` | 智能体管理面的 MyBatis-Plus 接口与 jsonb 原串 TypeHandler。 |
| `:backend` | `com.ragagent.agentmanagement.repository` | Agent 定义与推荐问题的仓储门面：自定义 Agent 行与跨表取样的只读查询。 |
| `:backend` | `com.ragagent.agentmanagement.service` | 智能体管理用例实现：自定义智能体 CRUD（com.ragagent.agentmanagement.service.CustomAgentService）。 |
| `:backend` | `com.ragagent.agentmanagement.service.builtin` | 内置智能体面（从 agentmanagement.service 切出，对齐方案）： BuiltinAgentRegistry——内置智能体定义、类型与提示词的注册表。 |
| `:backend` | `com.ragagent.agentmanagement.service.config` | 智能体配置面（从 agentmanagement.service 切出，对齐方案）： agent config jsonb 读写（AgentConfigJson）、占位符（AgentPlacehold |
| `:backend` | `com.ragagent.agenttools` | Agent 工具实现面：把工具调用翻译为业务域公开用例，并把结果格式化为模型可读输出。 |
| `:backend` | `com.ragagent.agenttools.api` | Agent 工具域对外契约。 |
| `:backend` | `com.ragagent.agenttools.datasource` | agent 数据分析工具族：data_analysis / data_schema 两工具 + DuckDB 生产实现 （AnalysisDuckDbJdbc）与数据分析会话桥（DataAnalysisSessionBridge）。 |
| `:backend` | `com.ragagent.agenttools.knowledge` | agent 知识检索工具族：knowledge_search / grep_chunks / list_knowledge_chunks / query_knowledge_graph / get_d |
| `:backend` | `com.ragagent.agenttools.sql` | agent SQL 工具族：database_query 工具与手写 SQL 安全守卫（SqlGuard + tokenizer / 深检查 / 注入分析协作者）。 |
| `:backend` | `com.ragagent.agenttools.web` | agent 网页工具族：web_search / web_fetch 两件。 |
| `:backend` | `com.ragagent.agenttools.wiki` | agent wiki 工具族：页面工具十件（读/写/改名/替换/删除/搜索/索引概览/读源文档/标记与更新 issue）+ 内容重写与路由解析（WikiContentRewrite / WikiRou |
| `:backend` | `com.ragagent.audit` | 审计日志域：审计条目的写入、查询与保留期清理。 |
| `:backend` | `com.ragagent.audit.adapter` | 审计 · 端口实现：AuditWriteAdapter。 |
| `:backend` | `com.ragagent.audit.api` | audit 域对外契约。 |
| `:backend` | `com.ragagent.audit.controller` | 审计 · HTTP 层：AuditLogController。 |
| `:backend` | `com.ragagent.audit.domain` | 审计 · 领域模型：AuditLog。 |
| `:backend` | `com.ragagent.audit.dto` | 审计 · 数据传输：AuditLogListResponse / AuditLogQuery。 |
| `:backend` | `com.ragagent.audit.mapper` | 审计 · 映射：AuditLogRepository / AuditLogMapper。 |
| `:backend` | `com.ragagent.audit.repository` | 审计日志仓储门面：对 audit.mapper.AuditLogMapper 的查询与写入封装。 |
| `:backend` | `com.ragagent.audit.service` | 审计 · 服务：AuditLogService / RbacDeniedAuditorRegistrar / WikiActivityAuditRecorder…。 |
| `:backend` | `com.ragagent.auth` | 认证与租户控制面：登录/OIDC/令牌、用户与租户目录、RBAC 与会话身份。 |
| `:backend` | `com.ragagent.auth.adapter` | 认证 · 端口实现：UserLookupAdapter（contract.auth.UserLookupPort）、 AdminUserAdapter、TenantStorageQuotaAdapter。 |
| `:backend` | `com.ragagent.auth.api` | 认证 · 域内视图：com.ragagent.auth.api.AuthUserErrors（认证错误码与文案）。 |
| `:backend` | `com.ragagent.auth.apikey` | API 密钥子域（auth 之下，机器调用凭据面）：租户级 API Key 的签发、校验与访问控制。 |
| `:backend` | `com.ragagent.auth.apikey.controller` | apikey · HTTP 层：TenantAPIKeyController。 |
| `:backend` | `com.ragagent.auth.apikey.controller.filter` | API Key 过滤器面（由 auth.apikey.filter 迁入 apikey.controller.filter，对齐方案）： 鉴权通道与网关拦截、路由授权与策略、范围清理与文件服务豁免、主体拒绝（9 类）。 |
| `:backend` | `com.ragagent.auth.apikey.domain` | apikey · 领域模型：APIKeyStringListTypeHandler / APIKeyRawJsonbTypeHandler / TenantAPIKeyProjections…。 |
| `:backend` | `com.ragagent.auth.apikey.dto` | 租户 API Key · 传输形状：请求体与实体到响应的投影。 |
| `:backend` | `com.ragagent.auth.apikey.mapper` | apikey · 映射：TenantAPIKeyMapper。 |
| `:backend` | `com.ragagent.auth.apikey.repository` | API Key 仓储门面：对 auth.apikey.mapper 的读写封装与不存在异常转换。 |
| `:backend` | `com.ragagent.auth.apikey.service` | apikey · 服务：TenantAPIKeyService / ApiKeyAdminAdapter / TenantAPIKeyBootstrap…。 |
| `:backend` | `com.ragagent.auth.config` | 认证 · 配置：JwtProperties。 |
| `:backend` | `com.ragagent.auth.controller` | auth 域控制器的 HTTP 端点与协作类（7 个协作类）。 |
| `:backend` | `com.ragagent.auth.controller.filter` | 认证过滤器面（由 auth.filter 迁入 controller.filter，对齐方案）： AuthFilter（请求鉴权入口）与 WsAuthSupport（WebSocket 握手鉴权）。 |
| `:backend` | `com.ragagent.auth.domain` | 认证 · 领域模型：TenantConfigRedaction / TenantInvitation / TenantMember…。 |
| `:backend` | `com.ragagent.auth.dto` | 认证 · 数据传输：UpdatePreferencesRequest / TenantInvitationResponse / InvitationLookupResponse…。 |
| `:backend` | `com.ragagent.auth.mapper` | 认证 · 映射：TenantInvitationMapper / TenantMemberMapper / AuthTokenMapper…。 |
| `:backend` | `com.ragagent.auth.repository` | 认证域仓储门面：会话令牌的查询、签发落库与吊销。 |
| `:backend` | `com.ragagent.auth.service` | 认证 · 服务：TenantInvitationService / TenantMemberService / TenantService…。 |
| `:backend` | `com.ragagent.auth.system` | 系统设置与管理员面：系统设置读写（/api/v1/system）与管理员操作 （/api/v1/system/admin）。 |
| `:backend` | `com.ragagent.auth.system.controller` | 系统 · HTTP 层：SystemAdminController / SystemController。 |
| `:backend` | `com.ragagent.auth.system.domain` | 系统 · 领域模型：SystemSetting。 |
| `:backend` | `com.ragagent.auth.system.dto` | 系统管理 · 传输形状：系统设置响应视图。 |
| `:backend` | `com.ragagent.auth.system.mapper` | 系统 · 映射：SystemSettingMapper。 |
| `:backend` | `com.ragagent.auth.system.repository` | 系统设置仓储门面：全量读取、按键读写与删除。 |
| `:backend` | `com.ragagent.auth.system.service` | 系统 · 服务：SystemAdminUserService / SystemSettingService / SystemInfoService…。 |
| `:backend` | `com.ragagent.channel` | 种类：集成适配 入口：渠道回调适配与挂件会话端口（channel. |
| `:backend` | `com.ragagent.channel.api` | embedchannel 域对外契约。 |
| `:backend` | `com.ragagent.channel.callback` | IM · 平台回调处理：渠道校验、适配器验签/解析与 ACK 直写。 |
| `:backend` | `com.ragagent.channel.channel.dingtalk` | 渠道 · dingtalk：DingtalkAdapter / DingtalkAdapterFactory / DingtalkStreamClient。 |
| `:backend` | `com.ragagent.channel.channel.feishu` | 渠道 · feishu：FeishuAdapter / FeishuLongConnClient / FeishuAdapterFactory…。 |
| `:backend` | `com.ragagent.channel.channel.mattermost` | 渠道 · mattermost：MattermostAdapter / MattermostAdapterFactory / MattermostClient。 |
| `:backend` | `com.ragagent.channel.channel.qqbot` | 渠道 · qqbot：QqBotAdapter / QqBotAdapterFactory / QqBotGatewayClient…。 |
| `:backend` | `com.ragagent.channel.channel.slack` | 渠道 · slack：SlackAdapter / SlackSocketModeClient / SlackAdapterFactory。 |
| `:backend` | `com.ragagent.channel.channel.telegram` | 渠道 · telegram：TelegramAdapter / TelegramLongPollingClient / TelegramAdapterFactory。 |
| `:backend` | `com.ragagent.channel.channel.wechat` | 渠道 · wechat：WechatQRCodeService / WechatAdapter / WechatAdapterFactory…。 |
| `:backend` | `com.ragagent.channel.channel.wecom` | 渠道 · wecom：WecomWebhookAdapter / WecomWSAdapter / WecomLongConnClient…。 |
| `:backend` | `com.ragagent.channel.channel.yunzhijia` | 渠道 · yunzhijia：YunzhijiaAdapter / YunzhijiaLongConnClient / YunzhijiaAdapterFactory…。 |
| `:backend` | `com.ragagent.channel.controller` | IM · HTTP 层：ImCallbackController / ImChannelController。 |
| `:backend` | `com.ragagent.channel.controller.filter` | 渠道域过滤器：挂件令牌交换与 API-Key 作用域清理（EmbedAuthFilter）。 |
| `:backend` | `com.ragagent.channel.domain` | IM · 领域模型：ChannelSessionEntity / ImChannelEntity。 |
| `:backend` | `com.ragagent.channel.dto` | IM · 传输形状：渠道响应行。 |
| `:backend` | `com.ragagent.channel.mapper` | IM · 映射：ChannelSessionMapper / ImChannelMapper。 |
| `:backend` | `com.ragagent.channel.repository` | IM 仓储门面：渠道配置行与渠道会话映射的读写。 |
| `:backend` | `com.ragagent.channel.runtime` | IM · 运行时：AdapterInterfaces / FeishuWecomCrypt / SlackAdapterCore…。 |
| `:backend` | `com.ragagent.channel.service` | IM · 服务：ImChannelService / ImAttachmentPreparer / ImKnowledgeBridgeOps…。 |
| `:backend` | `com.ragagent.channel.support` | Embed · 支撑类型：错误、令牌与限流。 |
| `:backend` | `com.ragagent.config` | Spring 装配层：Web/MyBatis/检索引擎/会话/IM 等的 Bean 定义与 @ConfigurationProperties 属性绑定， 不含业务逻辑。 |
| `:backend` | `com.ragagent.config.json` | 业务域面载荷的 Jackson 装配（mixin 与注册表）。 |
| `:backend` | `com.ragagent.datasource` | 数据源连接器域：外部知识源（Notion/Feishu/Yuque/RSS/IMA/GitLab 等）的抓取、游标推进、 同步编排与数据源配置面。 |
| `:backend` | `com.ragagent.datasource.catalog` | 数据源 · 连接器目录：类型元数据与可用连接器清单（不参与持久化）。 |
| `:backend` | `com.ragagent.datasource.config` | 数据源装配：连接器注册表、调度器与生命周期 Bean。 |
| `:backend` | `com.ragagent.datasource.connector.feishu.core` | feishu · core：FeishuCursorCodec / FeishuWikiTreeOps / FeishuTransport…。 |
| `:backend` | `com.ragagent.datasource.connector.feishu.drive` | feishu · drive：DriveConnector。 |
| `:backend` | `com.ragagent.datasource.connector.feishu.wiki` | feishu · Wiki：WikiConnector。 |
| `:backend` | `com.ragagent.datasource.connector.gitlab` | 连接器 · gitlab：GitLabConnector / GitLabClient / GitLabConfig…。 |
| `:backend` | `com.ragagent.datasource.connector.ima` | 连接器 · ima：ImaRetryPolicy / ImaConnector / ImaApiTypes…。 |
| `:backend` | `com.ragagent.datasource.connector.notion` | 连接器 · notion：NotionPaginatedResponse / NotionDatabaseInfo / NotionDateMention…。 |
| `:backend` | `com.ragagent.datasource.connector.rss` | 连接器 · rss：UnavailableArticleExtractor / ArticleExtractor / JdkXmlFeedParser…。 |
| `:backend` | `com.ragagent.datasource.connector.yuque` | 连接器 · yuque：YuqueRetryPolicy / YuqueConnector / YuqueApiTypes…。 |
| `:backend` | `com.ragagent.datasource.controller` | 数据源 · HTTP 层：DataSourceCredentialsController / DataSourceController。 |
| `:backend` | `com.ragagent.datasource.domain` | 数据源 · 领域模型：DataSourceMapSerializer / DataSourceSyncPayload / DataSourceConstants…。 |
| `:backend` | `com.ragagent.datasource.domain.connector` | 连接器契约面（方案 的 domain.connector/）：连接器 SPI（Connector / StreamingConnector）、流式回调（StreamHandler）、目录与注册表 （C |
| `:backend` | `com.ragagent.datasource.dto` | 数据源 · 数据传输：DatasourceCredentialFieldMetadata / DatasourceCredentialsResponse / DataSourceConfigDto…。 |
| `:backend` | `com.ragagent.datasource.mapper` | 数据源 · 映射：DataSourceRepository / SyncLogRepository / DataSourceMapper…。 |
| `:backend` | `com.ragagent.datasource.repository` | 数据源与同步日志仓储门面：对 datasource.mapper 的读写封装。 |
| `:backend` | `com.ragagent.datasource.service` | 数据源 · 服务：DataSourceService / KnowledgeTagAutoTagProvider / DataSourceSyncResultOps…。 |
| `:backend` | `com.ragagent.datasource.task` | 同步任务面（方案 的 task/）：同步任务队列（DataSourceSyncTaskQueue + InProcess/Redis 双实现）、任务处理器（DataSourceSyncHandler） |
| `:backend` | `com.ragagent.embedding` | 嵌入向量 provider 客户端（库式域，无 HTTP 面）：com.ragagent.embedding.EmbedderFactory 选择器、 EmbeddingHttp/ProviderJson 传输与编码、池化与失败降级。 |
| `:backend` | `com.ragagent.embedding.provider` | 嵌入 provider 的**具体实现**（各家 HTTP 调用与响应解析）：OpenAI / AzureOpenAI / Gemini / Jina / Nvidia / Ollama / Aliy |
| `:backend` | `com.ragagent.favorite` | 收藏域：用户对资源的收藏闭环（controller/service/mapper/domain 各一，最小完整域）。 |
| `:backend` | `com.ragagent.favorite.controller` | 收藏 · HTTP 层：UserFavoriteController。 |
| `:backend` | `com.ragagent.favorite.domain` | 收藏 · 领域模型：UserResourceFavorite。 |
| `:backend` | `com.ragagent.favorite.dto` | 收藏 · 传输形状：用户资源收藏的响应视图。 |
| `:backend` | `com.ragagent.favorite.mapper` | 收藏 · 映射：UserResourceFavoriteMapper。 |
| `:backend` | `com.ragagent.favorite.repository` | 收藏仓储门面：四键复合主键的查询、插入与删除。 |
| `:backend` | `com.ragagent.favorite.service` | 收藏 · 服务：UserResourceFavoriteService。 |
| `:backend` | `com.ragagent.framework` | 平台面（platform，阶段 6 起逐步收纳）：框架适配、可靠执行与通用技术能力。 |
| `:backend` | `com.ragagent.framework.concurrent` | 平台并发原语：进程内 in-flight 去重（SingleFlight）等与业务无关的并发工具。 |
| `:backend` | `com.ragagent.framework.config` | 平台配置面：部署属性与环境查找面。 |
| `:backend` | `com.ragagent.framework.error` | 契约 · 错误：ErrorCode / BizException / AppError。 |
| `:backend` | `com.ragagent.framework.event` | 契约 · 事件：EventBusInterface / EventBusFactory / EventHandler…。 |
| `:backend` | `com.ragagent.framework.eventbus` | 平台事件总线面（platform.event/）： Event / EventBus / EventJson 等 9 类——进程内事件总线与载荷编解码。 |
| `:backend` | `com.ragagent.framework.http` | 平台 HTTP 面：SSRF 策略下的安全抓取工具，以及全进程共享的裸 HTTP 传输层 （com.ragagent.framework.http.LlmTransport：连接级 SSRF 校验 + |
| `:backend` | `com.ragagent.framework.json` | 契约 · JSON：ByteKeyOrder / JsonValues / ZeroTimes。 |
| `:backend` | `com.ragagent.framework.jsonbind` | JSON 绑定：Jackson 的 mapper 工厂、契约 mixin 注册表与各模块贡献的模块 SPI。 |
| `:backend` | `com.ragagent.framework.pagination` | 分页契约：统一的 com.ragagent.framework.pagination.PageResult。 |
| `:backend` | `com.ragagent.framework.persistence` | 平台持久化面：MyBatis 拦截器（全表写守卫 / 租户过滤）、分页请求、JSONB / 时间类型处理器、方言与 LIKE 转义。 |
| `:backend` | `com.ragagent.framework.redis` | 平台 · Redis：SpringRedisPubSub / RedisPubSub。 |
| `:backend` | `com.ragagent.framework.security` | 平台安全面：加密（AES）、SSRF 守卫/白名单、输入与日志净化、IP 分类、用户名查找。 |
| `:backend` | `com.ragagent.framework.storage.config` | 存储启动期环境快照与上传上限：framework 侧消费（llm 图片解析兜底、URL 抓取限额）与 app 侧装配共用。 |
| `:backend` | `com.ragagent.framework.storage.fileserve` | 存储 · 文件服务：FileTransport / FileProxyPort / FileServeHttp…。 |
| `:backend` | `com.ragagent.framework.storage.support` | 存储 · 支撑：FileResolverFactory / ResourceUrlMode / StreamRewriter…。 |
| `:backend` | `com.ragagent.framework.task` | 平台任务面：Redis 任务队列核心（RedisTaskQueueCore）、 Redis 重试执行循环（RedisRetryingTaskQueue）、进程内执行队列 （InProcessTaskQ |
| `:backend` | `com.ragagent.framework.tenant` | 多租户与访问控制（基础设施）：请求级租户上下文（TenantContext 及其跨线程作用域）、租户角色、租户配置读取面、API Key 作用域。 |
| `:backend` | `com.ragagent.framework.text` | 平台文本与标记卫生面（原 text + html 两包合并）：契约文本原语、繁简转换、 请求语言解析、HTML→Markdown 转换、实体表与转义、路径/文件名/无效字符清洗、 退化检测与码点序比较。 |
| `:backend` | `com.ragagent.framework.tracing` | 观测技术面：Langfuse/OTLP 适配与 trace 载体注入。 |
| `:backend` | `com.ragagent.framework.tracing.langfuse` | 平台追踪面（Langfuse OTel 适配； |
| `:backend` | `com.ragagent.framework.vocabulary` | 跨引擎形状的最后一站（终态 1 类）：SseSink——SSE 帧出口接口，被 framework/web/ServletSseSink 实现；随 SseEmitter 替换批（蓝图 5.6.6）一并消亡。 |
| `:backend` | `com.ragagent.framework.web` | 平台 Web 面：统一响应外壳、JSON 映射、分页/字段参数、请求 ID 过滤器、非空体/RBAC 拦截器与序列化器。 |
| `:backend` | `com.ragagent.framework.web.error` | 平台错误面：`BizException` 家族（含 `GuardForbiddenException`、`PlainErrorException`）、统一响应外壳的错误分派与全局异常处理器。 |
| `:backend` | `com.ragagent.framework.web.health` | 平台健康检查面。 |
| `:backend` | `com.ragagent.initialization` | 初始化与能力自检域：/api/v1/initialization/* 的初始化配置、未保存模型的 连通性预检、文本抽取试验与语音识别（ASR）。 |
| `:backend` | `com.ragagent.initialization.controller` | initialization 的 @RestController 薄层：端点注解与委托，逻辑在 service 协作者 （com.ragagent.initialization.controller. |
| `:backend` | `com.ragagent.initialization.dto` | initialization 的模型视图投影（com.ragagent.initialization.dto.ModelViews）。 |
| `:backend` | `com.ragagent.initialization.service` | initialization 用例实现：初始化配置（com.ragagent.initialization.service.InitializationConfigService）、 模型连通性测试、 |
| `:backend` | `com.ragagent.knowledge` | 知识库域：知识的存储、处理管线与检索面。 |
| `:backend` | `com.ragagent.knowledge.adapter` | 知识库 · 端口实现（11 个）：ChunkPort / KnowledgeCatalog / KnowledgeBaseCatalog / KnowledgeBaseLookup / Knowled |
| `:backend` | `com.ragagent.knowledge.api` | knowledge 域对外契约。 |
| `:backend` | `com.ragagent.knowledge.api.catalog` | 知识目录复合端口及其能力分面。 |
| `:backend` | `com.ragagent.knowledge.api.chunk` | chunk 视图、载荷与读取端口。 |
| `:backend` | `com.ragagent.knowledge.api.config` | 知识库配置形状（入库 / 解析 / 存储 / VLM 的 jsonb 配置列）。 |
| `:backend` | `com.ragagent.knowledge.api.error` | 知识域错误载荷。 |
| `:backend` | `com.ragagent.knowledge.api.faq` | FAQ 条目在 chunks.metadata（jsonb）中的结构与其 Jackson 读写（映射面）。 |
| `:backend` | `com.ragagent.knowledge.api.kb` | 知识库视图、批量取与行投影。 |
| `:backend` | `com.ragagent.knowledge.api.projection` | 实体 → 视图的投影助手。 |
| `:backend` | `com.ragagent.knowledge.api.wiki` | wiki 词汇（页面、抽取项、语言）。 |
| `:backend` | `com.ragagent.knowledge.assembler` | 领域对象与 HTTP 出入参之间的装配：只做字段搬运与空值处理，不含业务判断。 |
| `:backend` | `com.ragagent.knowledge.chunker` | 知识库 · 分块：HeuristicSplitter / DocumentProfiler / HeadingHierarchy…。 |
| `:backend` | `com.ragagent.knowledge.client` | 知识库出站客户端：DocReaderClient（文档解析服务）。 |
| `:backend` | `com.ragagent.knowledge.config` | 知识库 · 配置：HousekeepingProperties / BatchEmbedProperties / DocReaderProperties。 |
| `:backend` | `com.ragagent.knowledge.controller` | 知识库 HTTP 面：薄绑定层（@Valid DTO → service → 裸资源视图），线格式 camelCase、无信封； 无手搓解析与信封拼装。 |
| `:backend` | `com.ragagent.knowledge.controller.vo` | FAQ 模块的 HTTP 出入参：只承载传输形状，不参与业务判断。 |
| `:backend` | `com.ragagent.knowledge.domain` | 知识库持久化实体与 JSON 结构（MyBatis-Plus 注解 + Jackson 序列化注解）。 |
| `:backend` | `com.ragagent.knowledge.domain.access` | 知识库访问守卫：KnowledgeRouteGuards（路由级租户/成员校验）+ KnowledgeAccessGuard（知识库读写权限）+ ChunkAccessGuard（chunk 写守卫） |
| `:backend` | `com.ragagent.knowledge.domain.ingest` | 知识 · 入库状态：5 个 canonical 阶段（IngestStage）与一次尝试的内存态 （IngestJob）。 |
| `:backend` | `com.ragagent.knowledge.domain.span` | 处理轨迹（span）追踪：SpanTracker 维护 attempt/stage/sub-span 的开启与收口， 落库经 KnowledgeSpanRepository。 |
| `:backend` | `com.ragagent.knowledge.dto` | 知识库域的**请求/响应载荷**（HTTP 面 DTO，无逻辑）。 |
| `:backend` | `com.ragagent.knowledge.dto.chunk` | chunk 面载荷：chunk 读写与修订、重解析、内容预览（Chunk* / *Reparse* / Preview*）。 |
| `:backend` | `com.ragagent.knowledge.dto.doc` | 文档（Knowledge）面载荷：列表/详情/搜索/移动与文件夹、重新索引、清空内容、最后导入状态。 |
| `:backend` | `com.ragagent.knowledge.dto.faq` | FAQ 面载荷：条目/导入/搜索/合并（Faq*）与生成问题（*GeneratedQuestion*、AddSimilarQuestions*）。 |
| `:backend` | `com.ragagent.knowledge.dto.kb` | 知识库本体载荷：增删改查/复制/副本，以及它的配置视图（ASR / VLM / 分块 / 索引策略 / 解析引擎规则）。 |
| `:backend` | `com.ragagent.knowledge.dto.tag` | 标签面载荷：标签增删改查分页，以及知识库标签批量绑定与统计。 |
| `:backend` | `com.ragagent.knowledge.index` | 索引面：知识条目与向量/检索引擎的绑定、写入与回收适配。 |
| `:backend` | `com.ragagent.knowledge.mapper` | MyBatis-Plus mapper 接口层：每张表一个 *Mapper，只声明 SQL/条件构造， 不承载业务规则。 |
| `:backend` | `com.ragagent.knowledge.recovery` | 知识侧启动恢复：重启后把卡在 pending/processing/finalizing/deleting 的解析行复位为 failed 并取消孤儿 span，summary 的 pending/processing 同样复位。 |
| `:backend` | `com.ragagent.knowledge.repository` | 仓储门面层：把 mapper 的原子操作组合成领域可用的读写契约，并集中承载 软删三面孔、全字段 UPDATE、乐观锁冲突与 PG/H2 方言分支。 |
| `:backend` | `com.ragagent.knowledge.runtime` | 知识库运行面：任务观测门面。 |
| `:backend` | `com.ragagent.knowledge.service` | 知识库用例层（顶层残部）。 |
| `:backend` | `com.ragagent.knowledge.service.access` | 知识 · 访问守卫：chunk 路由的 KB/所有权判定。 |
| `:backend` | `com.ragagent.knowledge.service.chunk` | 分块面（从 service 切出的第二个子面）：分块的编辑、抽取与读取。 |
| `:backend` | `com.ragagent.knowledge.service.enrichment` | 富化面（从 service 切出的子面）：处理管线完成后的 LLM 富化——摘要与问题生成。 |
| `:backend` | `com.ragagent.knowledge.service.faq` | FAQ 面（从 service 整簇切出的第一个子面）。 |
| `:backend` | `com.ragagent.knowledge.service.image` | 服务 · 图片：ExtractedImageStore。 |
| `:backend` | `com.ragagent.knowledge.service.management` | 知识域**应用/门面面**（起）：对外的门面 KnowledgeService，以及它向下编排的 用例服务（知识库/文件夹、文件与手工内容、解析生命周期、克隆/移动/标签/批处理）。 |
| `:backend` | `com.ragagent.knowledge.service.parser` | 解析引擎目录：本地静态注册表 + docreader 远端发现的合并（ParserEngineCatalog）。 |
| `:backend` | `com.ragagent.knowledge.service.query` | 查询面（从 service 切出，按 命名）：检索与 span 读取。 |
| `:backend` | `com.ragagent.knowledge.storage` | 知识库文件与对象存储：LocalStorageService（本地卷）+ TenantFileStorage（租户感知门面， 知识上传/读取/删除的唯一入口）。 |
| `:backend` | `com.ragagent.knowledge.support` | 知识库任务面领域类型：任务提交端口、进度存储、导入任务存储与任务 id/事件。 |
| `:backend` | `com.ragagent.knowledge.support.access` | 知识 · 路由守卫支撑：批量访问检查与任务 id 解析（非 service 层）。 |
| `:backend` | `com.ragagent.knowledge.task` | 知识库异步任务：队列、进度与执行。 |
| `:backend` | `com.ragagent.knowledge.wiki` | wiki 域：知识库内联百科（页面/修订/文件夹树/链接图/摄取管线）。 |
| `:backend` | `com.ragagent.knowledge.wiki.adapter` | Wiki · 端口实现：WikiPageOpsAdapter。 |
| `:backend` | `com.ragagent.knowledge.wiki.api` | Wiki 域对外契约。 |
| `:backend` | `com.ragagent.knowledge.wiki.controller` | wiki 的 @RestController:页面 CRUD/修订/文件夹/索引/图谱/统计/问题端点(raw-JSON 对齐形态,契约换锚时整体重写为 DTO)。 |
| `:backend` | `com.ragagent.knowledge.wiki.domain` | wiki 域留存面（拆簇后）：WikiConfig / WikiConstants / WikiException / WikiExtractionGranularity / WikiSourceRe |
| `:backend` | `com.ragagent.knowledge.wiki.domain.folder` | wiki 文件夹面（拆自 wiki.domain，对齐方案）：WikiFolder 与其创建/更新/列表请求-响应、冲突与"非空""不存在"异常。 |
| `:backend` | `com.ragagent.knowledge.wiki.domain.graph` | wiki 链接图面（拆自 wiki.domain）：WikiGraph（页面入链/出链视图）。 |
| `:backend` | `com.ragagent.knowledge.wiki.domain.index` | wiki 索引面（拆自 wiki.domain）：WikiIndex / WikiIndexEntry（目录树条目）与 WikiCategoryPaths（分类路径规范化）。 |
| `:backend` | `com.ragagent.knowledge.wiki.domain.ingest` | ingest 领域形状与跨层端口：任务载荷（WikiIngestTask / WikiIngestPayload）、 finalize 变更行、超时常量，以及队列侧需要的槽位收尾端口。 |
| `:backend` | `com.ragagent.knowledge.wiki.domain.issue` | wiki 巡检问题面（拆自 wiki.domain）：WikiLintIssue / WikiLintReport（lint 单条问题与汇总报告）。 |
| `:backend` | `com.ragagent.knowledge.wiki.domain.page` | wiki 页面面（拆自 wiki.domain）：WikiPage 与页面修订、列表/移动/回滚/裁剪请求-响应、冲突与"不存在"异常，以及页面统计 WikiStats。 |
| `:backend` | `com.ragagent.knowledge.wiki.mapper` | wiki 的 MyBatis-Plus 接口（@MapperScan 扫描范围内）。 |
| `:backend` | `com.ragagent.knowledge.wiki.prompt` | wiki 的提示词模板目录与渲染支撑。 |
| `:backend` | `com.ragagent.knowledge.wiki.repository` | 仓储门面层：把 mapper 的原子操作组合成页面/文件夹级的读写契约，并集中承载软删、 版本冲突与 PG 方言分支。 |
| `:backend` | `com.ragagent.knowledge.wiki.repository.ingest` | wiki 摄取的存储与并发协调：任务队列、finalize 锁、身份认领、在途限流、删除墓碑。 |
| `:backend` | `com.ragagent.knowledge.wiki.repository.page` | wiki 页面仓储面：slug 锁的进程内/Redis 实现。 |
| `:backend` | `com.ragagent.knowledge.wiki.service` | wiki 用例层根包：**跨切面端口与 LLM/提示词适配**——分块清洗（ com.ragagent.knowledge.wiki.service.WikiChunkCleaner + 缺省实现）、 |
| `:backend` | `com.ragagent.knowledge.wiki.service.ingest` | wiki 摄取管线：批量/单篇入库的门面与协作者 （com.ragagent.knowledge.wiki.service.ingest.WikiIngestService、 com.ragagent |
| `:backend` | `com.ragagent.knowledge.wiki.service.ingest.graph` | wiki 摄取·链接图面（拆自 wiki.service.ingest，对齐方案 的 service.ingest.graph/）： WikiGraphCalculator——从页面/引用计算链接图（入链、出链、孤儿）。 |
| `:backend` | `com.ragagent.knowledge.wiki.service.ingest.support` | wiki 摄取 · 支撑：批上下文、去重工具与预览等无状态叶子类型 （WikiBatchContext、WikiCleanupScope、WikiIngestPreviews 等）。 |
| `:backend` | `com.ragagent.knowledge.wiki.service.page` | wiki **页面**面：页面服务（接口门面 + 实现）、文件夹/视图支撑、链接与 lint （com.ragagent.knowledge.wiki.service.page.WikiLinkify |
| `:backend` | `com.ragagent.llm` | LLM 客户端（库式域，无 HTTP 面）：provider 适配（provider/）、对话流转与流式（chat/， 含 com.ragagent.modelruntime.adapter.prov |
| `:backend` | `com.ragagent.llm.api` | 模型运行时的对外契约：Ollama 本地模型管理端口与进度载荷。 |
| `:backend` | `com.ragagent.llm.domain` | LLM · 领域模型：ProviderMetadataMaps。 |
| `:backend` | `com.ragagent.llm.extract` | LLM · extract：EntityExtractionAdapter / EntityExtraction。 |
| `:backend` | `com.ragagent.llm.json` | 模型面契约载荷的 Jackson 装配（mixin 与注册表）。 |
| `:backend` | `com.ragagent.llm.limiter` | LLM · limiter：ModelConcurrencyLimiter / BackgroundTaskContext / ConcurrencyGovernor…。 |
| `:backend` | `com.ragagent.llm.ollama` | LLM · ollama：OllamaService / OllamaChatResponse / OllamaChatRequest…。 |
| `:backend` | `com.ragagent.llm.provider` | LLM · 提供方：AzureOpenAIProvider / SiliconFlowProvider / OpenRouterProvider…。 |
| `:backend` | `com.ragagent.mcp` | MCP 域：Model Context Protocol 的仓内实现——协议、OAuth（含回调与令牌存储）、 服务注册与调用面；对外 /api/v1/mcp-services。 |
| `:backend` | `com.ragagent.mcp.adapter` | MCP · 端口实现：McpToolRegistrationAdapter / McpApprovalOpsAdapter。 |
| `:backend` | `com.ragagent.mcp.api` | 种类：集成适配 入口：MCP 审批与 OAuth 的事件载荷（com.ragagent.mcp.api.ToolApprovalRequiredData 等） 对外契约面：MCP 危险工具审批 / 会 |
| `:backend` | `com.ragagent.mcp.approval` | 工具审批域：审批门（Gate）、待审请求/决议消息、Redis pub/sub 适配与工具策略。 |
| `:backend` | `com.ragagent.mcp.controller` | mcp 域控制器的 HTTP 端点与协作类（2 个协作类）。 |
| `:backend` | `com.ragagent.mcp.domain` | MCP · 领域模型：McpService / McpAuthConfigTypeHandler / McpToolListTypeHandler…。 |
| `:backend` | `com.ragagent.mcp.dto` | MCP · 数据传输：McpToolApprovalPolicyRequest / McpCredentialFieldMetadata / ResolveToolApprovalRequest…。 |
| `:backend` | `com.ragagent.mcp.mapper` | MCP · 映射：McpToolApprovalMapper / McpOAuthClientMapper / McpOAuthTokenMapper…。 |
| `:backend` | `com.ragagent.mcp.oauth` | MCP · OAuth 流程：授权发现、令牌获取/刷新、传输与运行时，以及配置 （OAuthConfig）与「需要授权」异常（OAuthAuthorizationRequiredException）。 |
| `:backend` | `com.ragagent.mcp.oauth.error` | OAuth 异常族与标准错误体：协议错误、需要授权/重新授权、无令牌、状态缺失。 |
| `:backend` | `com.ragagent.mcp.oauth.model` | OAuth 协议模型：授权服务器元数据、发现结果、令牌、授权状态与 PKCE。 |
| `:backend` | `com.ragagent.mcp.oauth.store` | OAuth 存储：令牌仓库与授权状态的端口，以及数据库/Redis 实现。 |
| `:backend` | `com.ragagent.mcp.oauth.wiring` | OAuth 装配：把配置、存储与流程组装成对 MCP 侧暴露的支持面。 |
| `:backend` | `com.ragagent.mcp.protocol` | MCP · 协议：StreamableHttpTransport / ReadResourceResult / ServerCapabilities…。 |
| `:backend` | `com.ragagent.mcp.repository` | MCP · 仓储：McpToolApprovalRepository / McpMetadataRepository / McpOAuthRepository。 |
| `:backend` | `com.ragagent.mcp.service` | MCP · 服务：AgentToolApprovalService / McpToolApprovalService / McpOAuthFlowService…。 |
| `:backend` | `com.ragagent.mcp.tool` | MCP 工具实现族（方案 的 tool/）：把 MCP 服务暴露的工具翻译为 Agent 工具—— 调用（McpCallTool）、发现与目录（McpDiscoverTool / McpCatalog |
| `:backend` | `com.ragagent.memory` | 记忆域：用户长期记忆的抽取、去重与合并、检索与生命周期（租约、墓碑、容量）。 |
| `:backend` | `com.ragagent.memory.adapter` | 记忆 · 端口实现：MemoryPipelineAdapter。 |
| `:backend` | `com.ragagent.memory.api` | 记忆 · 对外入口：MemoryAccess（session 等跨域消费方经此读记忆）。 |
| `:backend` | `com.ragagent.memory.config` | 记忆 · 配置值：MemorySettings（记忆开关、阈值与策略）。 |
| `:backend` | `com.ragagent.memory.controller` | 记忆 · HTTP 层：MemoryController。 |
| `:backend` | `com.ragagent.memory.domain` | 记忆域实体（留存面）：抽取会话/批次/失败/状态（MemoryExtraction*）、 渲染与投影（MemoryRender / MemoryDocView / MemoryTopicView / |
| `:backend` | `com.ragagent.memory.domain.item` | 记忆条目面（拆自 memory.domain，对齐方案 的 domain.item/）： MemoryItem（可检索的条目）、MemoryItemEmbedding（其向量）、 MemoryTombstone（删除墓碑）。 |
| `:backend` | `com.ragagent.memory.domain.scope` | 记忆范围面（拆自 memory.domain，对齐方案 的 domain.scope/）： MemoryScope（作用域）、MemorySettings（开关与阈值）、 MemorySubject 与缺失异常。 |
| `:backend` | `com.ragagent.memory.domain.vector` | 记忆向量检索面（拆自 memory.domain，对齐方案 的 domain.vector/）： MemoryVectors（向量读写）、MemoryVectorQuery（检索请求）、 MemoryVectorHit（命中行）。 |
| `:backend` | `com.ragagent.memory.dto` | 记忆模块对外 HTTP 响应/请求的 DTO。 |
| `:backend` | `com.ragagent.memory.mapper` | 记忆 · 映射：MemoryExtractionSessionMapper / MemoryItemEmbeddingMapper / MemoryDocAffinityMapper…。 |
| `:backend` | `com.ragagent.memory.repository` | 仓储门面层：记忆域的持久化门面与其协作者。 |
| `:backend` | `com.ragagent.memory.runtime` | 长期记忆运行面：langfuse 观测门面、蒸馏队列的进程内/Redis 实现与装配。 |
| `:backend` | `com.ragagent.memory.service` | 记忆 · 服务：MemoryConsolidationService / MemoryExtractionService / MemoryVectorService…。 |
| `:backend` | `com.ragagent.memory.support` | 记忆 · 支撑类型：冲突异常等跨层共享但不属于实体的词汇。 |
| `:backend` | `com.ragagent.model` | 模型配置域：模型条目 CRUD、凭据子资源、Ollama 管理与调试面（embedding 原始响应等）。 |
| `:backend` | `com.ragagent.model.adapter` | 模型 · 端口实现：ModelCatalogAdapter。 |
| `:backend` | `com.ragagent.model.controller` | 模型 · HTTP 层：ModelCredentialsController / ModelDebugController / ModelController。 |
| `:backend` | `com.ragagent.model.domain` | 模型 · 领域模型：ModelParametersTypeHandler / Model。 |
| `:backend` | `com.ragagent.model.dto` | 模型 · 数据传输：ModelCredentialFieldMetadata / ModelCredentialsPutRequest / ModelCredentialsResponse…。 |
| `:backend` | `com.ragagent.model.mapper` | 模型 · 映射：ModelUsageMapper / ModelMapper。 |
| `:backend` | `com.ragagent.model.repository` | 模型仓储门面：可见性查询、写入、软删与内置模型对账；另有使用关系只读仓储。 |
| `:backend` | `com.ragagent.model.service` | 模型 · 服务：ModelService / BuiltinModelsReconciler / ModelProviderCatalog…。 |
| `:backend` | `com.ragagent.modelcontext` | 模型上下文装配（库式域）：来源注册表（com.ragagent.modelcontext.SourceRegistry）与模型输出封装 （com.ragagent.modelcontext.Model |
| `:backend` | `com.ragagent.modelruntime` | 模型运行时：模型调用与 provider 适配、流生命周期与限流。 |
| `:backend` | `com.ragagent.modelruntime.adapter` | 模型运行时的适配面：按协议家族归类的 provider 实现与访问/凭据适配。 |
| `:backend` | `com.ragagent.modelruntime.adapter.provider` | provider 适配实现（chat/embedding/asr 等按能力分面）。 |
| `:backend` | `com.ragagent.modelruntime.adapter.provider.asr` | ASR（语音转写）适配：AsrTranscriber。 |
| `:backend` | `com.ragagent.modelruntime.adapter.provider.chat` | 对话通道：远端 API 家族（RemoteApi* / RemoteHttpOps / SSE 与流状态 / 缓存 / 预算 / 图片解析）与本地 Ollama。 |
| `:backend` | `com.ragagent.modelruntime.adapter.provider.chat.anthropic` | Anthropic 协议家族：请求/响应/内容块/工具与工具流事件（由 `llm.chat` 归位）。 |
| `:backend` | `com.ragagent.modelruntime.adapter.provider.chat.common` | 跨 provider 共享的对话底座：装配（ProviderAdapter(s)）、并发客户端、思考策略与流式修正，（流句柄三件已迁 `platform.stream`——契约 ChatStream 的队列实现与追踪装饰器共用）。 |
| `:backend` | `com.ragagent.modelruntime.adapter.provider.vlm` | 提供方 · 视觉：VlmHttpTransport / VlmClient。 |
| `:backend` | `com.ragagent.modelruntime.application` | 模型运行时 · application：ModelRuntimeAssembler / ModelRuntimeConfigs / LlmChatClients…。 |
| `:backend` | `com.ragagent.modelruntime.prompt` | 提示词指纹与缓存键计算。 |
| `:backend` | `com.ragagent.modelruntime.stream` | 流式会话的存储与泵：StreamManager 的 Redis / 内存两种实现与帧编解码。 |
| `:backend` | `com.ragagent.modelruntime.tracing` | Langfuse 追踪装饰器：聊天 / 嵌入 / 重排 / VLM 的观测包装。 |
| `:backend` | `com.ragagent.rerank` | 重排 provider 客户端（库式域，无 HTTP 面）：RerankerFactory 选择器、RerankHttp/ProviderJson 传输与 RankResult 形态，只做"候选 → 重排分"。 |
| `:backend` | `com.ragagent.rerank.provider` | 重排 provider 的**具体实现**：OpenAI / Jina / Nvidia / Aliyun / Volcengine / Zhipu / Lkeap / 各家 HTTP 调用与响应解析。 |
| `:backend` | `com.ragagent.retrieval` | 检索引擎域（库式域，**无 HTTP 面**）：多引擎仓储（engine/：OpenSearch/Doris/Milvus/Qdrant/Tencent…， 含 postgres 引擎的 embedd |
| `:backend` | `com.ragagent.retrieval.api` | 检索引擎的对外契约：向量库查找端口、视图、必填字段与引擎类型。 |
| `:backend` | `com.ragagent.retrieval.artifact` | 检索 · artifact：ArtifactReferenceRewriter。 |
| `:backend` | `com.ragagent.retrieval.config` | 检索 · 配置：RetrievalEnvLookup。 |
| `:backend` | `com.ragagent.retrieval.engine` | 检索 · 引擎：KeywordsVectorHybridRetrieveEngineService / PgVectorRetrieveRepository / RetrieveEngineRepository…。 |
| `:backend` | `com.ragagent.retrieval.engine.doris` | 引擎 · doris：DorisRetrieveRepository / DorisStreamLoadClient / DorisVectorEmbedding…。 |
| `:backend` | `com.ragagent.retrieval.engine.elasticsearch` | 引擎 · elasticsearch：ElasticsearchV8RetrieveRepository / ElasticsearchV7RetrieveRepository / ElasticsearchV8SearchOps…。 |
| `:backend` | `com.ragagent.retrieval.engine.milvus` | 引擎 · milvus：MilvusRetrieveRepository / MilvusVectorEmbedding / MilvusRestClient…。 |
| `:backend` | `com.ragagent.retrieval.engine.opensearch` | 引擎 · opensearch：OpenSearchRetrieveRepository / OpenSearchSearchOps / OpenSearchWriteOps…。 |
| `:backend` | `com.ragagent.retrieval.engine.qdrant` | 引擎 · qdrant：QdrantRetrieveRepository / QdrantVectorEmbedding / QdrantRestClient…。 |
| `:backend` | `com.ragagent.retrieval.engine.sqlite` | 引擎 · sqlite：SqliteRetrieveRepository / SqliteCjkBigram / SqliteSearchOps…。 |
| `:backend` | `com.ragagent.retrieval.engine.tencentvectordb` | 引擎 · tencentvectordb：TencentVectorDbRetrieveRepository / TencentVectorDbRestClient / TencentVectorDbSearchOps…。 |
| `:backend` | `com.ragagent.retrieval.engine.weaviate` | 引擎 · weaviate：WeaviateRetrieveRepository / WeaviateVectorEmbedding / WeaviateRestClient…。 |
| `:backend` | `com.ragagent.retrieval.graph` | 检索 · 图谱：Neo4jGraphRepository / Neo4jGraphConfig / Neo4jProperties。 |
| `:backend` | `com.ragagent.retrieval.json` | 检索与向量库面载荷的 Jackson 装配（mixin 与注册表）。 |
| `:backend` | `com.ragagent.retrieval.support` | 检索引擎的支撑层：检索文本归一/分词/哈希、chunk 内容拼接、grep 结果打分等无状态算法 （原顶层包 searchutil， 并入）。 |
| `:backend` | `com.ragagent.retrieval.telemetry` | 检索 · telemetry：RetrievalObs。 |
| `:backend` | `com.ragagent.session` | 会话域：会话与消息读写（conversation/）、两条问答入口（知识库问答 / Agent 问答）、 SSE 流桥、附件（attachment/）与会话侧管线（rag/ + assembly/）。 |
| `:backend` | `com.ragagent.session.adapter` | 会话 · 端口实现：SessionQaAdapter / EmbedSessionAdapter / EmbedSessionOpsAdapter / KnowledgeQaEventAdapter |
| `:backend` | `com.ragagent.session.api` | 种类：产品能力（编排域） 入口：会话域跨域形状（com.ragagent.session.api.StopData） 对外契约面：会话编排对外暴露的事件载荷（continue-stream 的停止请求 |
| `:backend` | `com.ragagent.session.assembly` | 会话侧装配：问答管线注册（QaWiring）与跨域协作件的接线点。 |
| `:backend` | `com.ragagent.session.attachment` | 会话附件：临时文档的落盘、解析、提示词渲染与生命周期。 |
| `:backend` | `com.ragagent.session.controller` | session 域控制器的 HTTP 端点（协议适配层）。 |
| `:backend` | `com.ragagent.session.controller.sse` | 会话 · SSE 传输面：SseContract（头部契约）与 SseFrameWriter （Servlet 直写 + 帧组装）。 |
| `:backend` | `com.ragagent.session.conversation` | 会话与消息生命周期：会话/消息读写、消息检索、停止会话与 SSE 流桥 （AgentStreamBridge / AgentStreamEmitter）。 |
| `:backend` | `com.ragagent.session.domain` | 会话域实体（留存面；已切出三簇：conversation/（会话与分页）、 document/（临时文档）、message/（消息族 + artifact 版本化））。 |
| `:backend` | `com.ragagent.session.domain.conversation` | 会话实体面（由 session.domain 切出，对齐方案）： Session 及其列表/分页/状态（SessionListItem / SessionListQuery / SessionPage |
| `:backend` | `com.ragagent.session.domain.document` | 会话文档面（由 session.domain 切出，对齐方案）：TemporaryDocument。 |
| `:backend` | `com.ragagent.session.domain.message` | 消息实体面（由 session.domain 切出，对齐方案）：Message 与其 附件/图片/工件/搜索/建议集（MessageArtifact / MessageSuggestionSet …） |
| `:backend` | `com.ragagent.session.dto` | 会话 · 数据传输：BatchDeleteSessionsRequest / GenerateTitleResponse / SearchMessagesRequest…。 |
| `:backend` | `com.ragagent.session.mapper` | 会话 · 映射：TemporaryDocumentMapper / MessageSuggestionMapper / MessageMapper…。 |
| `:backend` | `com.ragagent.session.qa` | QA 轮的编排簇。 |
| `:backend` | `com.ragagent.session.rag` | 聊天管线（库式域，无 HTTP 面）：可插拔的 Plugin 链——检索、合并、重排、实体抽取、数据分析、历史装载等， 由会话域按顺序驱动。 |
| `:backend` | `com.ragagent.session.rag.plugin` | chat 管线的**插件实现**：Plugin 接口 + 18 个步骤实现 （query_understand → search → merge → rerank → into_chat_messag |
| `:backend` | `com.ragagent.session.rag.spi` | 管线 · spi：KnowledgeQaEventPort。 |
| `:backend` | `com.ragagent.session.rag.support` | chat 管线的**纯逻辑辅助**：检索结果去重/去包含（com.ragagent.session.rag.support.SearchSupport）、 查询分词与关键词抽取（com.ragagen |
| `:backend` | `com.ragagent.session.repository` | 仓储门面层：把 mapper 的原子操作组合成领域可用的读写契约，并集中承载软删、全字段 UPDATE 与 PG/H2 方言分支。 |
| `:backend` | `com.ragagent.session.runtime` | 会话运行面：模型观测装饰入口与 span 门面。 |
| `:backend` | `com.ragagent.session.service` | 会话 · 编排核心：知识/Agent 两条问答服务、QA 上下文解析（ChatManageFactory / QaSupport）、Agent 工具后端与编排协作件。 |
| `:backend` | `com.ragagent.session.service.sse` | 会话 · SSE 组装（传输无关）：事件 → 响应体（StreamResponseBuilder）、 重写与写出（StreamEventEmitter）、帧写出端口（SseFrameSink）。 |
| `:backend` | `com.ragagent.session.service.suggestion` | 消息建议面（由 session.service 切出，对齐方案 的 service.suggestion/）： MessageSuggestionService（建议生成的编排与落库）与 Messag |
| `:backend` | `com.ragagent.session.support` | 会话 · 支撑：MessageReferenceRewriter / PipelineViews。 |
| `:backend` | `com.ragagent.storage` | 存储后端域：存储后端配置与凭据（对外 /api/v1/storage-backends）与文件服务 （传输、解析、文件解析器）。 |
| `:backend` | `com.ragagent.storage.adapter` | 存储 · 端口实现：StorageBackendPortAdapter / ProviderFileStoreAdapter / GlobalFileServiceAdapter / ResourceCatalogAdapter。 |
| `:backend` | `com.ragagent.storage.api` | storage 域对外契约。 |
| `:backend` | `com.ragagent.storage.config` | 存储 · 配置：LocalStorageEnvProperties / ResourceUrlModeProperties / StorageProviderEnv。 |
| `:backend` | `com.ragagent.storage.controller` | 存储 · HTTP 层：StorageBackendController / FileProxyController。 |
| `:backend` | `com.ragagent.storage.domain` | 存储 · 领域模型：StoredResource / StorageBackend。 |
| `:backend` | `com.ragagent.storage.dto` | 存储 · 数据传输：StorageBackendResponse / StorageConfig。 |
| `:backend` | `com.ragagent.storage.fileserve` | 存储 · 文件服务：ProviderFileContentService / BackendScopedFileService / LocalFileContentService…。 |
| `:backend` | `com.ragagent.storage.provider` | 存储 · 提供方：S3CompatibleFileService / SeekableFileService / LocalFileService…。 |
| `:backend` | `com.ragagent.storage.repository` | 存储后端与资源仓储门面：存储元数据与资源行的查询（JdbcClient 方言 SQL）。 |
| `:backend` | `com.ragagent.storage.service` | 存储 · 服务：ResourceCatalogService / StorageBackendService / DefaultStorageBackendProvisioner…。 |
| `:backend` | `com.ragagent.storage.support` | 存储域的支撑层：存储 URL 解析与重写——签名 URL、流式重写与文件服务解析 （原顶层包 storageurl， 并入）。 |
| `:backend` | `com.ragagent.vectorstore` | 向量库配置域：向量库类型目录与配置校验/持久化，对外 /api/v1/vector-stores。 |
| `:backend` | `com.ragagent.vectorstore.config` | 向量存储 · 环境配置：EnvVectorStores 的读取与查询视图。 |
| `:backend` | `com.ragagent.vectorstore.controller` | 向量库 · HTTP 层：VectorStoreController。 |
| `:backend` | `com.ragagent.vectorstore.domain` | 向量库 · 领域模型：ConnectionConfigTypeHandler / IndexConfigTypeHandler / VectorStoreEngines…。 |
| `:backend` | `com.ragagent.vectorstore.dto` | 向量库 · 数据传输：VectorStoreResponse / VectorStoreTypes。 |
| `:backend` | `com.ragagent.vectorstore.mapper` | 向量库 · 映射：com.ragagent.vectorstore.mapper.VectorStoreMapper。 |
| `:backend` | `com.ragagent.vectorstore.repository` | 向量库仓储门面：VectorStoreMapper 显式 SQL 方法的收口点。 |
| `:backend` | `com.ragagent.vectorstore.service` | 向量库 · 服务：VectorStoreConfigService / VectorStoreLookupAdapter。 |
| `:backend` | `com.ragagent.websearch` | 联网搜索域：搜索 provider 目录与凭据（对外 /api/v1/web-search-providers）、 搜索执行与各家 provider 适配。 |
| `:backend` | `com.ragagent.websearch.adapter` | 网页搜索 · 端口实现：WebSearchBackendsAdapter / WebSearchSupportAdapter。 |
| `:backend` | `com.ragagent.websearch.api` | websearch 域对外契约。 |
| `:backend` | `com.ragagent.websearch.config` | 网页搜索 · 配置值：WebSearchProviderParams（provider 的 parameters 载荷）。 |
| `:backend` | `com.ragagent.websearch.controller` | 网页搜索 · HTTP 层：WebSearchProviderCredentialsController / WebSearchProviderController / WebSearchController。 |
| `:backend` | `com.ragagent.websearch.domain` | 网页搜索 · 领域模型：WebSearchParamsTypeHandler / WebSearchProviderEntity。 |
| `:backend` | `com.ragagent.websearch.dto` | 网页搜索 · 数据传输：WebSearchProviderResponse / WebSearchProviderTypes。 |
| `:backend` | `com.ragagent.websearch.fetch` | 网页搜索 · 抓取：PipelinePageFetcherImpl / ToolPageFetcherImpl / BrowserRenderer…。 |
| `:backend` | `com.ragagent.websearch.mapper` | 网页搜索 · 映射：WebSearchProviderRepository。 |
| `:backend` | `com.ragagent.websearch.provider` | 网页搜索 · 提供方：WebSearchProviderRegistry / ZhipuWebSearchProvider / DuckDuckGoProvider…。 |
| `:backend` | `com.ragagent.websearch.repository` | 联网搜索仓储门面：提供方配置的查询与写入。 |
| `:backend` | `com.ragagent.websearch.runtime` | 联网搜索运行面：临时知识库的 Redis 状态。 |
| `:backend` | `com.ragagent.websearch.service` | 网页搜索 · 服务：WebSearchTempKbStateService / WebSearchProviderService / WebSearchService。 |
