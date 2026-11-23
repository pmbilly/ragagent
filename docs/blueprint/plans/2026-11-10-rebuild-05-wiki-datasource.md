# RagAgent 重建：wiki、datasource 迁入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `domains/.../wiki`（165 文件、23545 行）与 `domains/.../datasource`（147 文件、26356 行）按新规范重写进 `backend/app`。wiki 侧迁入页面、修订、文件夹、问题、图谱、lint 与三阶段摄取管线；datasource 侧迁入六类外部系统的连接器、凭据管理、同步调度与同步日志。两域的自研队列全部换成 JobRunr，slug 互斥改用 framework 的 `@DistributedLock`，凭据字段用 `@Encrypted`，连接器改为窄接口加 Spring 登记表。

**Architecture:** 两域属于同一级业务域，互不依赖，可并行执行。wiki 对 knowledge 只走 `knowledge.api`，knowledge 入库完成后通过事件通知 wiki，wiki 反向调用 `KnowledgeIngestApi.finalizeSubtask` 收尾。datasource 对 knowledge 只走 `knowledge.api`，写入知识条目不再直连 Mapper。后台任务全部走计划 04 定义的 JobRunr 接入与 `ragagent.role` 角色开关，`worker` 角色执行同步与摄取，`api` 角色只处理 HTTP。

**Tech Stack:** Spring Boot 3.5.16、Spring Modulith 1.4、MyBatis-Plus 3.5.x、Flyway、JobRunr 7.x（开源版，存储 PostgreSQL）、Redisson（分布式锁与缓存）、Jackson 2、MapStruct、Testcontainers PostgreSQL、Playwright。

**规格来源**: `docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图：`docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 05 号计划，第 3 节约定全部继承。

## Global Constraints

继承路线图第 3 节全部约定。本计划最常用的几条：

- JDK 21、Spring Boot 3.5.16。Java 缩进 tab、行宽 140、中文 Javadoc、`@author jiangguifan`、魔法字符串收口为常量或枚举。
- 业务错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号 wiki `011`、datasource `012`。枚举 `WikiCode`、`DataSourceCode` 放各域 `api` 包，启动时 `CodeCache.register`。
- 文案 key `code_<错误码>`，放 `backend/app/src/main/resources/i18n/app-messages*.properties`，七语种加默认文件齐全。
- Flyway 脚本放 `backend/app/src/main/resources/db/migration/allenv/`，命名 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_{域名}_{描述}_jiangguifan.sql`，版本与描述之间是**双下划线**。JobRunr 自建的表不进本域脚本。
- 主键 bigint 雪花 ID，对外序列化为字符串。租户表 `tenant_id bigint NOT NULL` 加行级安全，策略用计划 01 Task 5 的 fail-closed 模板。软删 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
- 路径三组：`/api/admin/v1`、`/api/v1`、`/api/open/v1`。wiki 与 datasource 都是后台功能，只挂 `/api/admin/v1`。请求体 VO 加 `@Valid`，响应 `RestResponse`，ID 为字符串。
- 代码质量按路线图 3.11：方法 40 行、类 400 行、参数 5 个、圈复杂度 10、嵌套 3 层、构造器依赖 7 个，Checkstyle 拦截。类名不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common`。单实现不建接口。构造器注入用 `@RequiredArgsConstructor`。
- 事务内不做外部调用。LLM、Embedding、外部数据源 HTTP、SSRF 校验后的出网请求都算外部调用，不进事务。
- 下文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`。`-pl` 必带 `-am`。命令前确认 `mvn -v` 显示 Java 21。
- 路径缩写：`M` = `backend/app/src/main/java/com/ragagent`，`T` = `backend/app/src/test/java/com/ragagent`，`R` = `backend/app/src/main/resources`，`DB` = `R/db/migration/allenv`。脚本名里的 `<D>` 是写脚本当天的 `yyyyMMdd`，序号取 `ls DB | grep <D>` 的下一个。
- `git add` 只写显式路径。每个任务一次提交，提交后在 `docs/migration/progress.md` 追加一行。与计划不一致的决定写 `docs/migration/deviations.md`。
- 旧代码只读。旧系统副本在 `../ragagent-legacy`（计划 01 Task 0）。

## Review Focus

以下五类最容易出错，每条都在对应任务的测试里有断言。

1. **后台同步丢租户。** datasource 同步作业没有 HTTP 请求，租户只能来自作业参数。期望：参数缺租户时作业立刻失败且不重试，不出现全租户扫描。归属 Task 15。
2. **凭据泄漏或明文落库。** datasource 的 `appSecret`、`apiToken`、`access_token` 必须加密存储，任何响应与日志都不出现明文。期望：入库后列值不等于原文，响应体只返回 `configured` 布尔。归属 Task 11、Task 14、Task 16。
3. **外部调用在事务内。** 连接器校验、列举资源、抓取条目都不能包在数据库事务里，否则慢外部系统会拖住连接池。期望：同步作业按「抓取一批、逐条开事务落库」执行，事务外可观测。归属 Task 14、Task 15。
4. **同一 slug 并发丢更新。** 两个摄取批次同时写同一页时必须有互斥，租户配置变更后下一批次立即生效且没有广播通道。期望：并发用例只有一个写入者成功；管理面写超时报 `wiki.slug.lockTimeout`；摄取侧拿不到锁直接跳过该 slug 的更新，既不等待也不报错。归属 Task 6、Task 7。
5. **wiki 页面 ID 与 slug 的边界。** 层级 slug 带斜杠、空 slug、超长 slug、已软删后重建同名页面。期望：路径解析正确，重建同名页面被接受，重复活行被拒。归属 Task 2、Task 7。

---

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 | framework 的 `RestResponse`、`BusinessRuntimeException`、`CodeCache`、`@IgnoreTenant`、`@Encrypted`、`@DistributedLock`、`DistributedLockKeyResolver`、`TenantClientRegistry`、`TenantConfigSource`、`TenantAwareJdbcTemplate`、`PostgresContainerTest`、`OpenApiExportTest`；`@ragagent/api-client`；`tests/e2e/PAGES.md` 与 `wiki`、`datasource` 两批用例 |
| 计划 02 | model-runtime 的 `LlmChatClient`、`EmbeddingPort`、`ModelRuntime`；签名以计划 02 的 `api` 为准 |
| 计划 03 | `auth.api` 的当前主体与权限点；`audit.api` 的审计写入；`tenant.api.TenantConfigApi.wiki(long)` 与 `sectionVersion(long, String)`（wiki 运行配置与版本）；`model.api` 的模型查询；`docs/migration/golden-sample.md` |
| 计划 04 | `JobRunrConfig`、`TenantJobFilter`、`TenantJobEnqueuer`、`RagAgentRole` 与 `RoleGatedWebConfig`；`KnowledgeBaseQueryApi`、`KnowledgeContentApi`、`KnowledgeIngestApi`；`docs/migration/knowledge-job-map.md` |

计划 02、03 的接口名以它们的 `api` 为准，本文不猜内部方法。计划 04 已落盘，本文按它「对外承诺」里的五个 api 使用；缺的能力写进下面「对前置计划的新增需求」。

### 对前置计划的新增需求

| 声明方 | 需要的接口或机制 | 用途 | 缺失时的处置 |
|---|---|---|---|
| 计划 04 | `KnowledgeContentApi.listTextChunks(long knowledgeId)` 返回 `List<ChunkTextBo>`（`id`、`content`、`chunkType`、`chunkIndex`、`startAt`、`endAt`） | wiki 摄取 Map 阶段读正文分块 | Task 6 第一步停，登记 `deviations.md` 并问用户 |
| 计划 04 | `KnowledgeContentApi.findChunks(List<Long> chunkIds)` 返回 `List<ChunkTextBo>` | 引用式抽取按 chunk 引用回读 | 同上 |
| 计划 04 | `KnowledgeContentApi.enrichImageInfo(String content, List<Long> chunkIds)` 返回补全后的正文 | 页面正文里的图片信息补全 | 同上 |
| 计划 04 | `KnowledgeContentApi.deleteChunk(long knowledgeId, long chunkId)` | 页面删除后清理孤儿分块 | 同上 |
| 计划 04 | `KnowledgeIngestApi.finalizeSubtask(long knowledgeId)` 返回 `FinalizeSubtaskBo(boolean decremented, boolean promoted)` | wiki 子任务收尾时递减 `pending_subtasks_count` 并在归零时晋升 | 同上 |
| 计划 04 | `KnowledgeContentApi.findByExternalRefs(long kbId, long dataSourceId, List<String> externalIds)` 返回 `List<KnowledgeDocumentBo>`（含 `metadata`） | datasource 按外部 ID 判断条目是否已存在 | 同上 |
| 计划 04 | `KnowledgeContentApi.findByExternalRefPrefix(long kbId, long dataSourceId, String prefix)` 返回 `List<KnowledgeDocumentBo>` | datasource 子树清理时按前缀取子条目 | 同上 |
| 计划 04 | `KnowledgeContentApi.updateMetadata(long knowledgeId, Map<String,String> metadata)` | datasource 刷新条目标题、来源链接 | 同上 |
| 计划 04 | `KnowledgeContentApi.createFromExternalRef(long kbId, ExternalRefDocumentBo doc)` 返回 `long`，`ExternalRefDocumentBo(String title, String sourceUrl, String content, String externalId, long dataSourceId, Map<String,String> metadata)` | datasource 建条目时同时写入外部 ID 与元数据，消除「先建后补元数据」期间 `findByExternalRefs` 查不到导致的重复建 | 计划 04 已补进 `KnowledgeContentApi`，Task 10 第一步核对方法存在 |
| 计划 04 | `KnowledgeBaseQueryApi.resolveAutoTagIds(long kbId, String dataSourceType)` 返回 `List<Long>` | 同步条目按知识库自动标签配置打标 | 同上 |
| 计划 04 | 发布 `KnowledgeIngestedEvent(long tenantId, long kbId, long knowledgeId, String language)`，在入库收尾事务提交后发出 | wiki 收到后入队摄取，取代旧代码里 knowledge 直接调用 `WikiIngestService` | 同上 |
| 计划 01 | `DistributedLockKeyResolver` 的 `@Bean` 加 `@ConditionalOnMissingBean`，允许 app 声明自己的解析器 | wiki 需要按 `kbId` 与 `slug` 生成锁键，默认解析器只支持字面量 | 已由计划 01 Task 10 提供，无需登记偏离 |

`KnowledgeDocumentBo` 与 `ChunkTextBo` 由计划 04 定义，本文只消费，不在 wiki 或 datasource 重复建类。`KnowledgeDocumentBo` 需含 `id`、`knowledgeBaseId`、`title`、`lang`、`parseStatus`、`metadata`，缺字段时按同样方式登记。

### 交付

1. wiki 域 5 张表、`api` 包、Service、4 个 Controller、前端 API 层。wiki 摄取改为 JobRunr 作业，slug 互斥走 `@DistributedLock`。
2. datasource 域 3 张表、`api` 包、连接器 SPI 与七个连接器类、Service、4 个 Controller、前端 API 层。同步改为 JobRunr 周期作业与一次性作业。
3. `docs/migration/handoff-wiki-datasource.md`：旧队列与旧后台任务的一行一去向，供计划 08 与计划 09 核对。
4. 对路线图第 6 节交接表的增补，见下节。

### 对外承诺

**`wiki.api` 对 Agent 工具暴露的接口**，包 `com.ragagent.wiki.api`：

| 接口 | 方法 | 消费方 |
|---|---|---|
| `WikiPageApi` | `Optional<WikiPageBo> findBySlug(long kbId, String slug)`；`WikiPageBo create(WikiPageWriteBo page, WikiEditSource source)`；`void update(WikiPageWriteBo page, WikiEditSource source)`；`void updateAutoLinkedContent(WikiPageBo page, WikiEditSource source)`；`void delete(long kbId, String slug, WikiEditSource source)`；`Optional<LinkRepairBo> repairContentLinks(long kbId, String slug, String content)`；`void injectCrossLinks(long kbId, List<String> affectedSlugs)`；`List<WikiPageBo> search(long kbId, String query, int limit)` | agenttools（计划 08） |
| `WikiIssueApi` | `List<WikiIssueBo> list(long kbId, String slug, String status)`；`WikiIssueBo create(WikiIssueWriteBo issue)`；`void updateStatus(long issueId, String status)` | agenttools（计划 08） |
| `WikiIndexApi` | `Optional<WikiIndexBo> indexView(long kbId, int topK)` | agenttools（计划 08） |

`WikiEditSource` 为枚举 `PIPELINE`、`AGENT`、`USER`、`REVERT`，JSON 值为小写。`WikiPageBo` 字段与旧 `WikiPageView` 一一对应，`pageMetadata` 用 `JsonNode`，其余字段为 JDK 类型。三组 api 的方法参数用 `long`，ID 在 JSON 边界由 framework 的 Long 转字符串序列化器处理，与路线图 3.6 第 3 条一致。

**datasource 不对外提供 api。** 旧主码中没有其他域引用 datasource 类，本域只对外提供错误码与 BO 供自身 Controller 使用。

**对路线图第 6 节的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| wiki（05） | `WikiPageApi`、`WikiIssueApi`、`WikiIndexApi` | Agent 工具读写 wiki | 本域 |
| knowledge（04） | `KnowledgeIngestedEvent` | 入库完成后触发 wiki 摄取 | knowledge 发布，wiki 监听 |
| knowledge（04） | `finalizeSubtask` 等新增需求 | wiki 收尾、分块读写、datasource 条目读写 | knowledge 提供 |

## 本计划新做的设计决定（待用户核对）

只列 spec 与路线图没有定、由本计划新定的决定，按重要度排序。纯机械翻译的不列。

### 一、新表结构与聚合根划分

1. **wiki 摄取待办保留一张业务表 `wiki_ingest_ops`，JobRunr 只承担触发与重试。** 决定：`task_pending_ops` 与 `task_dead_letters` 合并为 `wiki_ingest_ops`，保留按知识库去重、批量认领两件旧能力；触发、后退重试、失败列表交给 JobRunr。理由：旧管线把 5 篇文档合成一批做 Reduce，按 slug 合并更新能显著减少 LLM 调用；JobRunr 作业是单文档粒度，直接拆到文档会让同一 slug 被重复生成。否决：完全删表，一个文档一个作业，成本随文档数线性上升。
2. **`wiki_folders` 的根父节点用 `0` 哨兵，不用 `NULL`。** 决定：`parent_id bigint NOT NULL DEFAULT 0`，唯一索引 `(tenant_id, knowledge_base_id, parent_id, name, deleted_at)`。理由：PostgreSQL 唯一索引把 `NULL` 视为互不相同，根目录同名文件夹会重复。否决：部分唯一索引加 `COALESCE`，可读性差且索引无法命中等值查询。
3. **datasource 凭据独立成表 `data_source_credentials`，字段级加密。** 决定：`data_sources.config` 只留 `resourceIds` 与 `settings`，凭据按 `field_name` 一行一个字段存 `data_source_credentials`，值列标 `@Encrypted`。理由：`@Encrypted` 是字段级 TypeHandler，旧实现把它塞进 jsonb 后手工逐项加密，等于绕开了框架能力；独立成表还能让凭据子资源的 PUT、DELETE 一字段一行自然落地。否决：保留 `config.credentials` jsonb 手工加密，与路线图 3.7 第 4 条冲突。
4. **连接器目录随连接器实现走，未实现的 8 类单独放 `PlannedConnectorType` 枚举。** 决定：已实现的连接器在类里声明 `ConnectorDescriptor`，`SourceConnectorRegistry` 汇总生成目录；目录里只剩规划中的 8 类从枚举读取。理由：新增连接器只需新增一个类，目录自动出现。否决：静态目录表加手工登记，新类忘记登记时界面看不到类型。
5. **`sync_logs` 不加密、不软删、保留清理任务。** 决定：同步日志按 `sync_log_retention_days` 由 JobRunr 每日周期任务物理删除，表不带 `deleted_at`。理由：旧表就没有软删，日志是可重建的观测数据。否决：加软删列，清理任务还要多一个索引。

### 二、新接口路径与 VO 形状

1. **wiki 资源路径改为复数名词，动作接口保留动词。** 决定：`/knowledge-bases/{kbId}/wiki/pages`、`/pages/{*slug}`、`/pages/{*slug}/revisions`、`/revisions/{slug}/{version}`、`/pages/{*slug}/revert`、`/folders`、`/folders/{id}`、`/pages/{*slug}/move`、`/index`、`/graph`、`/stats`、`/pages/search`、`/issues`、`/issues/{id}/status`、`/links/rebuild`、`/lint`、`/lint/auto-fix`。理由：旧路径把 21 个动作塞进一个控制器，读的人要记住哪些是资源、哪些是动作。否决：保留旧路径只换前缀，OpenAPI 里动词路径继续当资源。
2. **层级 slug 用通配段 `{*slug}` 承载，不再要求调用方编码斜杠。** 决定：Controller 用 `@PathVariable("slug")` 取通配值，统一去掉前导斜杠再清洗；前端 `encodeSlugPath` 保留，行为不变。理由：旧 Java 侧已经这么实现，前端也依赖它，改成单段编码会破坏层级页面。否决：把 slug 拆成路径段，OpenAPI 无法表达可变深度。
3. **wiki 响应统一包 `RestResponse`，裸实体、raw map、裸数组三种形态消失。** 决定：页面、文件夹、索引、图谱、统计、问题全部走 VO 加 `RestResponse`；`updatePage` 的乐观锁冲突从 200 加 `current_version` 改为 409 加错误码 `wiki.page.conflict`，详情带当前版本。理由：路线图 3.6 要求统一外壳。否决：保留旧三种形态，前端 API 层要按端点分支解包。
4. **datasource 路径改为 `/data-sources`，动作接口用子资源动词。** 决定：`/data-sources`、`/data-sources/{id}`、`/data-sources/{id}/validation`、`/data-sources/credential-previews`、`/data-sources/{id}/resources`、`/data-sources/{id}/resource-ancestors`、`/data-sources/{id}/syncs`、`/data-sources/{id}/pause`、`/data-sources/{id}/resume`、`/data-sources/{id}/credentials`、`/data-sources/{id}/credentials/{field}`、`/data-sources/{id}/sync-logs`、`/data-sources/sync-logs/{logId}`、`/data-sources/types`。理由：旧路径 `datasource` 是单数且 `/logs` 与 `/sync` 混用动词与名词。否决：只换前缀，路径里继续出现 `validate-credentials` 这类动作名。
5. **同步日志列表返回 `PageData`，`limit` 越界用 `CommCode.PARAM_ERROR`。** 决定：旧 `limit` 在 1 到 100 之外返回 400 硬编码文案，新实现由 VO 校验注解表达。理由：参数校验只放边界。否决：Service 里手写范围判断。

### 三、Provider 与 spi 的签名

1. **`SourceConnector` 窄接口加 Spring 登记表。** 决定：接口方法 `ConnectorDescriptor descriptor()`、`void validate(ConnectorConfig config)`、`List<SourceResourceBo> listResources(ConnectorConfig config, String parentExternalId)`、`List<String> resolveResourceAncestors(ConnectorConfig config, List<String> externalIds)`、`List<FetchedItemBo> fetchAll(ConnectorConfig config, List<String> resourceIds)`、`FetchResultBo fetchIncremental(ConnectorConfig config, SyncCursorBo cursor)`。流式能力单独一个 `StreamingSourceConnector` 接口继承它。实现类标 `@Component`，`SourceConnectorRegistry` 构造器注入 `List<SourceConnector>` 按 `descriptor().types()` 建 `Map<String, SourceConnector>`。理由：连接器是真实多实现点，旧 `ConnectorRegistry` 已经在做同样的事，只是靠手工装配。否决：保留手工 `DataSourceWiring` 注册，新连接器要在两个文件里各改一处。
2. **`ConnectorDescriptor` 同时携带目录元数据与凭据字段声明。** 决定：`record ConnectorDescriptor(Set<String> types, String displayNameKey, String descriptionKey, int priority, ConnectorAuthType authType, Set<ConnectorCapability> capabilities, List<CredentialField> credentialFields)`，`CredentialField` 含字段名、是否必填、是否密钥。理由：目录与实现同源，界面类型列表不会再与实现脱节；凭据子资源的字段校验与前端「已配置」判定也由同一份声明驱动，旧实现把整张凭据 map 当一个原子字段，清除某个字段只能靠 `unknown credential field` 兜底。否决：目录与凭据字段各自维护一份静态清单，旧代码里 17 项目录与 9 个实现已经在语义上分叉。
3. **datasource 的同步作业签名。** 决定：`DataSourceSyncJob.run(Long tenantId, Long dataSourceId, Long syncLogId, boolean forceFull)`，由 `TenantJobEnqueuer` 入队，`jobId` 规则见 1.5。理由：作业只接参数不接对象，JobRunr 持久化参数时不会把实体快照写进作业表。
4. **wiki 运行配置走 framework 的 `TenantClientRegistry`，不做广播。** 决定：声明 `WikiRuntimeConfigSource implements TenantConfigSource<WikiRuntimeConfig>`，`version(tenantId)` 只查该租户配置行的 `updated_at`，`load(tenantId)` 读整份配置；`WikiRuntimeConfigClients` 持有一个 `TenantClientRegistry<WikiRuntimeConfig, WikiRuntimeConfigSnapshot>`，`factory` 把原始配置解析成不可变快照，`closer` 为空实现。摄取作业每次进入时调 `forTenant(tenantId)`，不长期持有引用。决定的口径与计划 01 Task 9 一致：配置变更后下一个请求或下一个作业立即生效，不依赖 Redis 广播总线。理由：旧代码每次摄取都从 `knowledge_bases.wiki_config` 现读，没有跨实例推送，改成注册表后拿到同一语义，还省掉重复解析；租户级开关（是否允许 wiki、默认语言、并发上限）此前散落在 `RAGAGENT_LANGUAGE` 环境变量与常量里，现在统一进这份配置。否决：引入 Redis 失效广播，需要额外一条消息通道且与计划 01 的取舍冲突。

### 四、与旧行为不一致的地方

1. **wiki 摄取由「进程内虚拟线程队列」改为「JobRunr 作业加业务待办表」。** 多实例下不再出现「上传落到哪个副本就由哪个处理」，重启不丢任务。界面上的 wiki 页面与本批次进度表现不变。
2. **wiki 页面乐观锁冲突从 200 改为 409。** 前端 API 层在函数内部把它转成旧的返回形状，页面不改。
3. **datasource 同步的 cron 由进程内 `ThreadPoolTaskScheduler` 改为 JobRunr 周期作业。** 暂停、恢复、改 cron 都变成「重建周期作业」，多实例只有一个实例触发。旧实现靠 `hasRunningSync` 加确定性 TaskID 去重，新实现靠作业 `jobId` 去重加同步日志的 running 行。
4. **datasource 连接器 HTTP 统一注入 `SsrfGuard` Bean。** 旧实现静态持有守卫并在测试里 setter 替换，新实现构造器注入，测试用 `@MockBean`。
5. **wiki 语言不再读 `RAGAGENT_LANGUAGE` 环境变量。** 语言取自请求 `Accept-Language`，后台作业语言在入队时写进待办行，与旧行为等价但去掉了静态环境快照。

# 1. 盘点（执行方核对，不重新盘点）

数据取自冻结点工作区。Task 2、Task 11 第一步用 `find` 与 `wc -l` 抽查文件数与行数，不符以源码为准并在本文标注。

## 1.1 规模与目标包

旧规模复核结果：wiki 主码 165 文件 23,545 行，测试 32 文件 11,240 行；datasource 主码 147 文件 26,356 行，测试 38 文件 18,015 行。

**wiki 目标包规模（新建后预估）**

| 目标包 | 文件数 | 行数 | 职责 |
|---|---:|---:|---|
| `wiki/api` | 13 | 950 | 三个 api、BO、`WikiCode`、`WikiEditSource`、`WikiPageType`、`WikiPageStatus` |
| `wiki/controller` | 4 | 720 | 页面与修订、文件夹与移动、索引与图谱与统计与搜索、维护与问题 |
| `wiki/service/page` | 8 | 1350 | 页面用例、修订、链接图、视图组装、slug 修复、文本处理 |
| `wiki/service/folder` | 2 | 380 | 文件夹树与移动校验 |
| `wiki/service/ingest` | 12 | 2500 | 摄取作业、待办表、三阶段、LLM 调用、分类、索引页、收尾 |
| `wiki/service/ingest/graph` | 2 | 400 | 图谱计算 |
| `wiki/service/lint` | 2 | 620 | lint 规则与 auto-fix |
| `wiki/prompt` | 3 | 820 | 提示词常量与渲染 |
| `wiki/repository` | 5 | 750 | 按聚合根划分 |
| `wiki/entity`、`mapper`、`vo`、`assembler` | 28 | 1650 | 实体、Mapper 加 XML、出入参、MapStruct |

**datasource 目标包规模（新建后预估）**

| 目标包 | 文件数 | 行数 | 职责 |
|---|---:|---:|---|
| `datasource/api` | 10 | 700 | `SourceConnector` spi 类型的公开面、`DataSourceCode`、BO |
| `datasource/controller` | 4 | 820 | 数据源、凭据、资源与校验、同步日志 |
| `datasource/service` | 8 | 1500 | 数据源用例、凭据、同步执行、条目落库、自动标签、子资源清理 |
| `datasource/connector` | 81 | 17965 | 七个连接器类与飞书公共客户端 |
| `datasource/catalog` | 2 | 260 | 已实现类型汇总、规划类型枚举 |
| `datasource/repository` | 3 | 520 | 按聚合根划分 |
| `datasource/entity`、`mapper`、`vo`、`assembler` | 22 | 1200 | 实体、Mapper 加 XML、出入参、MapStruct |

**wiki 超出 3.11 尺寸上限的旧类与拆分归宿**

| 旧类 | 行 | 拆成 |
|---|---:|---|
| `service/ingest/WikiIngestCitePipeline` | 746 | `WikiCitationExtractor`（≤300，候选 slug 抽取）、`WikiCitationPrompt`（≤200，提示词与 JSON 协议）、`WikiCitationParser`（≤200，输出解析与校验） |
| `service/page/DefaultWikiPageService` | 729 | `WikiPageService`（≤300，增删改与版本策略）、`WikiPageLinkService`（≤200，链接图维护）、`WikiPageRevisionService`（≤180，快照与回滚）、`WikiPageViewService`（≤200，列表与索引视图） |
| `repository/WikiPageRepository` | 708 | `WikiPageRepository`（≤350，页面单表）、`WikiPageIssueRepository`（≤180，问题单表）、`WikiPageSearchRepository`（≤180，全文与似语建议） |
| `service/ingest/WikiIngestRunSupport` | 660 | `WikiIngestBatchRunner`（≤250，三阶段编排）、`WikiIngestMapReducer`（≤250，Map 与 Reduce 调度）、`WikiIngestBatchContextFactory`（≤180，批次上下文） |
| `prompt/WikiPrompts` | 644 | `WikiPromptCatalog`（≤300，系统提示词常量）、`WikiPromptRenderer`（≤200，模板占位符与 JSON 协议）、`WikiPromptHashing`（≤80，指纹） |
| `service/page/WikiLinkify` | 639 | `WikiLinkifier`（≤300，链接识别）、`WikiLinkSpanGuard`（≤180，代码块与转义区跳过）、`WikiSlugMatcher`（≤180，模糊匹配） |
| `mapper/WikiPageMapper` | 636 | 拆成 `WikiPageMapper` 加 XML、`WikiPageSearchMapper` 加 XML，注解 SQL 清零 |
| `service/ingest/WikiIngestMapPhase` | 590 | `WikiIngestMapPhase`（≤300）、`WikiIngestUpdateBuilder`（≤200，更新构造）、`WikiIngestMergePolicy`（≤150，合并规则） |
| `service/ingest/WikiIngestReducePhase` | 544 | `WikiIngestReducePhase`（≤280）、`WikiIngestPageWriter`（≤250，页面写入与 slug 锁） |
| `service/ingest/WikiIngestService` | 530 | `WikiIngestService`（≤300，门面用例）、`WikiIngestScheduler`（≤200，JobRunr 投递与防抖） |
| `service/ingest/WikiIngestBatchHandler` | 490 | `WikiIngestJob`（≤250，JobRunr 任务体）、`WikiIngestBatchLoader`（≤200，KB 与分块批量读取） |
| `service/page/WikiLintService` | 471 | `WikiLintService`（≤280）、`WikiLintRules`（≤250，规则集） |
| `service/ingest/WikiIngestDedupService` | 458 | `WikiEntityDedup`（≤250）、`WikiSlugSimilarity`（≤180） |
| `service/ingest/WikiIngestTaxonomy` | 437 | `WikiTaxonomyPlanner`（≤300，分类与文件夹规划）、`WikiCategoryPathNormalizer`（≤120） |
| `service/ingest/WikiIngestEnqueueOps` | 429 | `WikiIngestEnqueuer`（≤250，待办写入与去重）、`WikiFinalizeEnqueuer`（≤150，finalize 通道） |
| `controller/WikiPageOps` | 406 | 删除，逻辑进 `WikiPageController`（≤200）、`WikiPageRevisionController`（≤150）、`WikiPageService` |
| `service/ingest/WikiIdentityDedup` | 401 | `WikiIdentityClaimer`（≤250，认领）、`WikiIdentityKey`（≤120，键构造） |
| `service/page/WikiPageFolderSupport` | 382 | 删除，逻辑进 `WikiFolderService`（≤300） |
| `service/ingest/WikiIngestPageOps` | 246 | `WikiPageWriter`（≤250） |
| `service/ingest/WikiIngestIndexOps` | 274 | `WikiIndexPageBuilder`（≤250） |
| `service/ingest/WikiIngestLlmSupport` | 271 | `WikiLlmClient`（≤250，调用与重试）、`WikiLlmCallMetadata`（≤80） |
| `controller/WikiRequestSupport` | 279 | 删除，请求解析交给 VO 加 `@Valid`，错误映射交给 `GlobalExceptionHandler` |
| `api/WikiPageOpsAdapter` | 283 | 删除，改为 `WikiPageService implements WikiPageApi`，不做转发层 |
| `service/ingest/WikiIngestSettleOps` | 203 | `WikiIngestSettler`（≤200，槽位与终态） |
| `service/page/WikiPageViewsSupport` | 205 | `WikiPageViewAssembler`（≤200） |
| `controller/WikiStatsOps` | 194 | 逻辑进 `WikiInsightController`（≤200） |
| `service/ingest/WikiIngestQueueOps` | 156 | 删除，队列操作由 JobRunr 与待办表承担 |
| `service/page/WikiPageRevisionOps` | 163 | `WikiPageRevisionService`（见上） |
| `controller/WikiFolderOps` | 166 | 逻辑进 `WikiFolderController`（≤180） |
| `service/ingest/WikiIngestContentSupport` | 136 | `WikiContentGenerator`（≤250） |
| `controller/WikiMaintenanceOps` | 137 | 逻辑进 `WikiMaintenanceController`（≤150） |
| `service/page/WikiPageLinkOps` | 246 | `WikiPageLinkService`（见上） |
| `service/ingest/support/WikiDedupSupport` 等 | 全部 ≤250 | 按用途并入上面的新类，不单独保留 `Support` 后缀 |

**wiki 超过 100 行的旧方法与处置**

| 旧方法 | 行 | 处置 |
|---|---:|---|
| `WikiIngestRunSupport.runIngestPhases` | 372 | 归入 `WikiIngestBatchRunner`，拆成 `runMapPhase`、`runReducePhase`、`runFinalizePhase` 三个方法，每个 ≤40 行 |
| `WikiLintService.runLint` | 253 | 归入 `WikiLintRules`，每个规则一个方法，`runLint` 只做聚合 |
| `WikiIngestFinalizePhase.runFinalize` | 192 | 归入 `WikiFinalizeSettler`，拆成「排空待办」「聚合变更」「重建索引页」「清死链」四步 |
| `WikiGraphCalculator.compute` | 153 | 拆成 `buildAdjacency`、`pickOverview`、`expandEgo` |
| `WikiIngestMapPhase.buildUpdates` | 133 | 归入 `WikiIngestUpdateBuilder`，按字段分方法 |
| `WikiIngestMapPhase.classifyAndMerge` | 128 | 归入 `WikiIngestMergePolicy`，按合并动作分方法 |
| `WikiIngestExtractDedup.deduplicateExtractedBatch` | 128 | 归入 `WikiEntityDedup`，拆成「按身份分组」「组内择优」两步 |
| `WikiIngestBatchHandler.newWikiBatchContext` | 123 | 归入 `WikiIngestBatchContextFactory`，参数校验与装配分开 |
| `WikiIngestLlmSupport.generateWithTemplate` | 120 | 归入 `WikiLlmClient`，重试与调用分开 |
| `WikiLintService.autoFix` | 111 | 归入 `WikiLintRules`，每条修复规则一个方法 |
| `WikiLinkify.computeForbiddenSpans` | 103 | 归入 `WikiLinkSpanGuard` |
| `WikiIngestMapPhase.mapOneDocument` | 102 | 归入 `WikiIngestMapPhase`，拆成「取分块」「调 LLM」「解析结果」三步 |

**datasource 超出 3.11 尺寸上限的旧类与拆分归宿**

| 旧类 | 行 | 拆成 |
|---|---:|---|
| `connector/notion/NotionClient` | 707 | `NotionClient`（≤350，请求与重试）、`NotionPagination`（≤180，游标分页）、`NotionErrors`（≤150） |
| `connector/feishu/core/DocxBlocks` | 698 | `DocxBlockParser`（≤350）、`DocxBlockMarkdown`（≤250） |
| `connector/yuque/YuqueApiTypes` | 696 | `YuqueApiModels`（≤300）、`YuqueJsonCodec`（≤250） |
| `connector/feishu/drive/DriveConnector` | 693 | `DriveConnector`（≤300）、`DriveItemFetcher`（≤250） |
| `connector/notion/NotionMarkdown` | 686 | `NotionBlockRenderer`（≤300）、`NotionRichText`（≤250）、`NotionTableRenderer`（≤150） |
| `connector/ima/ImaApiTypes` | 676 | `ImaApiModels`（≤300）、`ImaJsonCodec`（≤250） |
| `connector/ima/ImaConnector` | 656 | `ImaConnector`（≤280）、`ImaTreeWalker`（≤250）、`ImaContentFetcher`（≤200） |
| `service/DataSourceService` | 651 | `DataSourceService`（≤300，增删改查）、`DataSourceCredentialService`（≤200）、`DataSourceSyncService`（≤250，触发与暂停恢复） |
| `connector/rss/JdkXmlFeedParser` | 650 | `FeedParser`（≤300）、`FeedItemExtractor`（≤250） |
| `connector/feishu/core/FeishuClient` | 649 | `FeishuClient`（≤300，传输与鉴权）、`FeishuDocxClient`（≤250）、`FeishuBitableClient`（≤200） |
| `connector/feishu/core/FeishuApiTypes` | 619 | `FeishuApiModels`（≤300）、`FeishuJsonCodec`（≤250） |
| `connector/notion/NotionConnector` | 607 | `NotionConnector`（≤300）、`NotionResourceWalker`（≤250） |
| `connector/gitlab/GitLabConnector` | 590 | `GitLabConnector`（≤300）、`GitLabTreeWalker`（≤250） |
| `connector/feishu/wiki/WikiConnector` | 590 | `FeishuWikiConnector`（≤300）、`FeishuWikiTreeWalker`（≤250） |
| `controller/DataSourceController` | 546 | `DataSourceController`（≤200）、`DataSourceResourceController`（≤180）、`DataSourceSyncLogController`（≤150） |
| `connector/rss/RssConnector` | 540 | `RssConnector`（≤300）、`RssCursorCodec`（≤150） |
| `connector/notion/NotionFetchOps` | 490 | `NotionPageFetcher`（≤280）、`NotionChildResolver`（≤180） |
| `service/DataSourceSyncExecutor` | 488 | `DataSourceSyncJob`（≤250，JobRunr 任务体）、`DataSourceSyncPipeline`（≤280，抓取与落库交错） |
| `connector/yuque/YuqueConnector` | 472 | `YuqueConnector`（≤300）、`YuqueTreeWalker`（≤200） |
| `connector/feishu/core/FeishuSupport` | 468 | `FeishuConfig`（≤200）、`FeishuRetryPolicy`（≤150）、`FeishuResponseCodec`（≤150） |
| `connector/gitlab/GitLabClient` | 431 | `GitLabClient`（≤300）、`GitLabPagination`（≤150） |
| `connector/ima/ImaClient` | 402 | `ImaClient`（≤300）、`ImaAuth`（≤120） |
| `service/DataSourceItemOps` | 343 | `DataSourceItemWriter`（≤300，逐条落库与删除） |
| `service/DataSourceSupport` | 233 | 删除，校验进 VO，KB 查询与 DTO 转换进 `DataSourceAssembler` 与 `DataSourceService` |
| `service/DataSourceSyncResultOps` | 184 | `DataSourceSyncResultRecorder`（≤180，统计与审计） |
| `catalog/ConnectorCatalog` | 162 | `SourceConnectorRegistry`（≤200，已实现汇总）、`PlannedConnectorType`（枚举，规划项） |
| `task/Scheduler` | 351 | 删除，改为 JobRunr 周期作业 `DataSourceScheduleJob`（≤250） |

**datasource 超过 100 行的旧方法与处置**

| 旧方法 | 行 | 处置 |
|---|---:|---|
| `DataSourceSyncExecutor.handle` | 225 | 归入 `DataSourceSyncJob.run`，拆成「加载与校验」「抓取」「落库」「收口」四步 |
| `NotionMarkdown.renderBlock` | 210 | 归入 `NotionBlockRenderer`，按块类型分方法 |
| `YuqueConnector.walk` | 182 | 归入 `YuqueTreeWalker`，深度优先遍历抽成 `visitNode` |
| `DocxFetcher.fetchDocxWithBlocks` | 143 | 归入 `FeishuDocxClient`，拉取与解析分开 |
| `SyncEngine.runSync` | 138 | 归入 `FeishuSyncEngine`，检查点与遍历分开 |
| `NotionConnector.fetchIncremental` | 122 | 归入 `NotionResourceWalker` |
| `NotionFetchOps.fetchPage` | 120 | 归入 `NotionPageFetcher` |
| `ImaClient.callApi` | 113 | 归入 `ImaClient`，鉴权、请求、错误映射分开 |
| `FeishuClient.readBitableRecords` | 113 | 归入 `FeishuBitableClient`，分页与记录映射分开 |
| `ImaConnector.walk` | 106 | 归入 `ImaTreeWalker` |

## 1.2 真实多实现点

路线图 3.11 要求只在真实多实现处建「窄接口加 Spring 登记表」。本计划结论：

| 位置 | 实现数 | 结论 |
|---|---|---|
| 外部数据源连接器 | 7 个类覆盖 9 个类型（飞书与 Lark 共用一个 wiki 连接器，飞书云盘与 Lark 云盘共用一个云盘连接器，其余各 1 个） | **建接口。** `SourceConnector` 加 `SourceConnectorRegistry`，实现类 `@Component` 自登记。新增连接器只需新增一个类 |
| 流式抓取能力 | 3 个连接器实现（飞书 Wiki、飞书云盘、GitLab） | **建子接口** `StreamingSourceConnector`。旧代码用 `instanceof`，新实现用注册表按类型查能力 |
| wiki slug 锁 | 旧 2 实现（进程内、Redis） | **不建接口。** 统一用 framework 的 `@DistributedLock` 注解，进程内与 Redis 的差异由 `gac.furnace.redis.enabled` 开关决定 |
| wiki 页面视图形态 | 列表、索引、图谱邻域、统计 4 种读出参 | 不建接口，4 个 VO |
| wiki 摄取阶段 | Map、Reduce、Finalize 3 段 | 不建接口，3 个私有方法 |
| datasource 同步模式 | 全量、增量 2 种 | 不建接口，`DataSourceSyncPipeline` 内 2 个方法 |

## 1.3 旧接口到新接口

规则：旧前缀 `/api/v1` 一律改 `/api/admin/v1`。下表逐行给出新路径与形状差异，不标「机械换前缀」的也按新路径重建。

**wiki（21 个端点，旧前缀 `/api/v1/knowledgebase/{kbId}/wiki`）**

| 旧路径 | 新路径 | 形状 |
|---|---|---|
| `GET /pages` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/pages` | 裸数组改 `RestResponse<PageData<WikiPageVo>>` |
| `POST /pages` | `POST /api/admin/v1/knowledge-bases/{kbId}/wiki/pages` | 裸实体改 `RestResponse<WikiPageVo>`，请求体 `CreateWikiPageReqVo` |
| `GET /pages/{*slug}` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/{*slug}` | 裸实体改外壳 |
| `PUT /pages/{*slug}` | `PUT /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/{*slug}` | 乐观锁冲突 200 加 `current_version` 改为 409 加 `wiki.page.conflict` |
| `DELETE /pages/{*slug}` | `DELETE /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/{*slug}` | 200 加外壳 |
| `GET /revisions/{*slug}` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/{*slug}/revisions` | 裸数组改外壳加 `PageData` |
| 旧列表内联修订详情 | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/revisions/{slug}/{version}` | 新增独立读数，前端 `getWikiRevision` 改指向 |
| `POST /revert` | `POST /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/{*slug}/revert` | 请求体 `RevertWikiPageReqVo` 含 `version` |
| `GET /folders` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/folders` | 外壳 |
| `POST /folders` | `POST /api/admin/v1/knowledge-bases/{kbId}/wiki/folders` | 请求体 `CreateWikiFolderReqVo` |
| `PUT /folders/{folderId}` | `PUT /api/admin/v1/knowledge-bases/{kbId}/wiki/folders/{id}` | `id` 为雪花字符串 |
| `DELETE /folders/{folderId}` | `DELETE /api/admin/v1/knowledge-bases/{kbId}/wiki/folders/{id}` | 非空返回 `wiki.folder.notEmpty` |
| `PUT /move-page` | `PUT /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/{*slug}/move` | slug 进路径，请求体 `{folderId}` |
| `GET /index` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/index` | 外壳 |
| `GET /graph` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/graph` | 参数校验失败从 400 硬编码改 `CommCode.PARAM_ERROR` |
| `GET /stats` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/stats` | 外壳 |
| `GET /search` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/pages/search?q=` | 外壳，`q` 必填由 VO 校验 |
| `POST /rebuild-links` | `POST /api/admin/v1/knowledge-bases/{kbId}/wiki/links/rebuild` | 返回 `{message}` 改 `RestResponse<Void>` |
| `GET /lint` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/lint` | 新增前端调用（旧无调用方） |
| `POST /auto-fix` | `POST /api/admin/v1/knowledge-bases/{kbId}/wiki/lint/auto-fix` | 返回 `{fixed,message}` 改 VO |
| `GET /issues` | `GET /api/admin/v1/knowledge-bases/{kbId}/wiki/issues` | 裸数组改外壳 |
| `PUT /issues/{issueId}/status` | `PUT /api/admin/v1/knowledge-bases/{kbId}/wiki/issues/{id}/status` | 状态非法改 `wiki.issue.status.invalid` |

**datasource（17 个端点，旧前缀 `/api/v1/datasource`）**

| 旧路径 | 新路径 | 形状 |
|---|---|---|
| `GET /types` | `GET /api/admin/v1/data-sources/types` | 裸数组改 `RestResponse<List<ConnectorTypeVo>>` |
| `POST /` | `POST /api/admin/v1/data-sources` | 请求体 `CreateDataSourceReqVo` |
| `GET /{id}` | `GET /api/admin/v1/data-sources/{id}` | 外壳，凭据只返回 `configured` |
| `GET /?kbId=` | `GET /api/admin/v1/data-sources?kbId=` | 外壳加 `PageData` |
| `PUT /{id}` | `PUT /api/admin/v1/data-sources/{id}` | 请求体 `UpdateDataSourceReqVo` |
| `DELETE /{id}` | `DELETE /api/admin/v1/data-sources/{id}` | 级联软删同步日志 |
| `POST /{id}/validate` | `POST /api/admin/v1/data-sources/{id}/validation` | 动作改子资源名词 |
| `POST /validate-credentials` | `POST /api/admin/v1/data-sources/credential-previews` | 动作改名词，请求体 `ValidateCredentialsReqVo` |
| `GET /{id}/resources` | `GET /api/admin/v1/data-sources/{id}/resources?parentId=` | 外壳 |
| `POST /{id}/resource-ancestors` | `POST /api/admin/v1/data-sources/{id}/resource-ancestors` | 外壳 |
| `POST /{id}/sync` | `POST /api/admin/v1/data-sources/{id}/syncs` | 返回 `SyncLogVo`，已在同步返回 `datasource.sync.running` |
| `POST /{id}/pause` | `POST /api/admin/v1/data-sources/{id}/pause` | 外壳 |
| `POST /{id}/resume` | `POST /api/admin/v1/data-sources/{id}/resume` | 外壳 |
| `GET /{id}/logs` | `GET /api/admin/v1/data-sources/{id}/sync-logs` | `limit` 越界改 `CommCode.PARAM_ERROR` |
| `GET /logs/{logId}` | `GET /api/admin/v1/data-sources/sync-logs/{logId}` | 外壳 |
| `PUT /{id}/credentials` | `PUT /api/admin/v1/data-sources/{id}/credentials` | 请求体 `UpdateCredentialsReqVo` |
| `DELETE /{id}/credentials/{field}` | `DELETE /api/admin/v1/data-sources/{id}/credentials/{field}` | 未知字段改 `datasource.credentials.field.unknown` |

## 1.4 旧表到新表

通用规则：旧 `id varchar(36)` 改 bigint 雪花，`tenant_id integer` 改 bigint，`timestamp` 改 `timestamptz`，软删改哨兵值，唯一索引含 `deleted_at`，租户表全部开行级安全。

**wiki 域**

| 新表 | 旧表 | 聚合根与写属主 | 列与映射 |
|---|---|---|---|
| `wiki_pages` | `wiki_pages` | `WikiPage`，`WikiPageRepository` | 保留 `knowledge_base_id`、`slug`、`title`、`page_type`、`status`、`content`、`summary`、`parent_slug`、`folder_id`、`category_path jsonb`、`wiki_path`、`depth`、`sort_order`、`source_refs jsonb`、`chunk_refs jsonb`、`in_links jsonb`、`out_links jsonb`、`page_metadata jsonb`、`aliases jsonb`、`version`、`last_edit_source`、`last_editor_id`。`knowledge_base_id` 与 `folder_id` 改 bigint，`parent_slug` 保留字符串（slug 是业务键）。唯一 `(tenant_id, knowledge_base_id, slug, deleted_at)` |
| `wiki_page_revisions` | 同名 | `WikiPage`，`WikiPageRepository` | `page_id bigint`、`slug`、`version`、`title`、`page_type`、`status`、`content`、`summary`、`aliases jsonb`、`edit_source`、`editor_id bigint`、`edited_at`。唯一 `(page_id, version)`，索引 `(tenant_id, knowledge_base_id, slug)` |
| `wiki_folders` | 同名 | `WikiFolder`，`WikiFolderRepository` | `parent_id bigint NOT NULL DEFAULT 0`、`name`、`path`、`depth`、`sort_order`。唯一 `(tenant_id, knowledge_base_id, parent_id, name, deleted_at)` |
| `wiki_page_issues` | 同名 | `WikiPageIssue`，`WikiPageIssueRepository` | `knowledge_base_id bigint`、`slug`、`issue_type`、`description`、`suspected_knowledge_ids jsonb`、`status`、`reported_by bigint`。索引 `(tenant_id, knowledge_base_id, slug, status)` |
| `wiki_ingest_ops` | `task_pending_ops` 与 `task_dead_letters` 合并 | `WikiIngestOp`，`WikiIngestOpRepository` | 见 1.5 |

旧 `wiki_pages` 的 `idx_wiki_pages_fulltext` gin 索引改到 `WikiPageSearchMapper.xml` 的 `to_tsvector` 表达式上，使用 PGroonga 的 `&@~` 运算符，随计划 01 的数据库镜像可用。

**datasource 域**

| 新表 | 旧表 | 聚合根与写属主 | 列与映射 |
|---|---|---|---|
| `data_sources` | 同名 | `DataSource`，`DataSourceRepository` | `knowledge_base_id bigint`、`name`、`type varchar(50)`、`resource_ids jsonb`、`settings jsonb`、`sync_schedule varchar(100)`、`sync_mode varchar(20)`、`status varchar(32)`、`conflict_strategy varchar(32)`、`sync_deletions boolean`、`last_sync_at timestamptz`、`last_sync_cursor jsonb`、`last_sync_result jsonb`、`error_message text`、`sync_log_retention_days int`。旧 `config jsonb` 拆成 `resource_ids` 与 `settings`，凭据列迁出 |
| `data_source_credentials` | `data_sources.config.credentials` 内的键值 | `DataSource`，`DataSourceCredentialRepository` | `data_source_id bigint`、`field_name varchar(64)`、`field_value text`（`@Encrypted`）。唯一 `(tenant_id, data_source_id, field_name)`，跟随数据源级联删除 |
| `sync_logs` | 同名 | `SyncLog`，`SyncLogRepository` | `data_source_id bigint`、`status`、`started_at`、`finished_at`、`items_total`、`items_created`、`items_updated`、`items_deleted`、`items_skipped`、`items_failed`、`error_message text`、`result jsonb`。无软删，按保留天数物理删除 |

`data_sources.last_sync_result` 是同步结果快照，与 `sync_logs.result` 同形状，两列都保留。旧表没有额外索引，新表按查询补 `(tenant_id, knowledge_base_id)`、`(tenant_id, status)`、`(tenant_id, type)`；`sync_logs` 补 `(tenant_id, data_source_id, started_at DESC)`。

## 1.5 旧队列与后台任务去向

| 旧机制 | 域 | 去向 |
|---|---|---|
| `WikiIngestTaskQueue`（进程内实现 `InProcessWikiIngestTaskQueue`） | wiki | 删除。触发改为 JobRunr 作业 `WikiIngestJob`，待办仍在业务表 `wiki_ingest_ops` |
| `RedisWikiIngestTaskQueue` 与 `wiki.task:proc`、`wiki.task:id:` 键、租约续期 | wiki | 删除。多实例协调由 JobRunr 的作业去重与 `wiki_ingest_ops` 的认领列承担 |
| `WikiIngestTaskRunner` 的重试延迟公式 `n^4+15+rand(30)*(n+1)` | wiki | 删除。JobRunr 指数退避，`retries` 取旧上限 10 |
| `task_dead_letters` 死信表 | wiki | 合并进 `wiki_ingest_ops.status='DEAD'`，保留载荷与错误原文，供后续人工重投 |
| `RedisWikiFinalizeLock`、`wiki.finalize:active:` | wiki | 改为 `@DistributedLock(lockName = "wiki-finalize")` 加 SpEL 键，见 Task 6 |
| `RedisWikiInflightLimiter`、`wiki.inflight:` | wiki | 改为 `@DistributedLock(lockName = "wiki-inflight")` 的并发计数改用 `wiki_ingest_ops` 按 KB 的进行中行数，超过阈值回退重排 |
| `RedisWikiIdentityClaimStore`、`wiki.identity:` | wiki | 改为 JobRunr 作业级互斥加 `wiki_ingest_ops` 的唯一 `(tenant_id, knowledge_base_id, dedup_key, status)` 索引 |
| `RedisWikiDeletedTombstoneStore`、`wiki.deleted:` | wiki | 改为 `wiki_ingest_ops` 的 `RETRACT` 待办行，TTL 语义换成「收尾后删除」 |
| `RedisWikiSlugLock`、`wiki.slug:` 与 `InProcessWikiSlugLock` | wiki | 改为 `@DistributedLock`，键 `wiki:slug:{kbId}:{slug}`，`waitTime=120`、`leaseTime=300`，见 Task 6 |
| `Scheduler`（cron 线程池，每个数据源一条 `CronTrigger`） | datasource | 删除。改为 JobRunr 周期作业 `DataSourceScheduleJob`，每个数据源一个周期作业，暂停即删除作业 |
| `RedisDataSourceSyncTaskQueue`、`datasource.sync:id:` | datasource | 删除。一次性同步改为 JobRunr 作业 `DataSourceSyncJob`，`jobId` 规则见下 |
| `InProcessDataSourceSyncTaskQueue` | datasource | 删除 |
| `dssync:<dsId>:<yyyyMMddHHmm>` 确定性 TaskID | datasource | 改为 `ds-sync:<tenantId>:<dataSourceId>` 作为 JobRunr `jobId`，同一数据源同时只有一个待执行作业 |
| `SyncEngine` 检查点（50 条或 30 秒） | datasource | 保留，写入 `data_sources.last_sync_cursor` |
| 同步日志保留清理（旧在 `DataSourceService` 内按需触发） | datasource | 新增 JobRunr 周期作业 `SyncLogRetentionJob`，每日一次，租户级循环 |

JobRunr 接入方式照抄计划 04：作业类以租户 ID 为首个参数，`TenantJobFilter` 在 `onProcessing` 设置租户上下文，缺参数抛不可重试异常；`TenantJobEnqueuer` 是唯一入队入口；`ragagent.role` 控制 `worker` 角色是否启动 `BackgroundJobServer`，`api` 角色只入队不执行。行级安全在作业内生效，跨租户清扫类作业用 `@IgnoreTenant` 并显式循环租户。

## 1.6 跨域依赖

| 本域 | 依赖方 | 旧做法 | 新做法 |
|---|---|---|---|
| wiki | knowledge | 直接 import `knowledge.api.ChunkPortAdapter`、`KnowledgeFinalizeAdapter`、`KnowledgeBaseLookup`，knowledge 的 `KnowledgeFinalizingStage` 反向 import `WikiIngestService` | 双向都走 `knowledge.api`。wiki 调 `KnowledgeBaseQueryApi`、`KnowledgeContentApi`、`KnowledgeIngestApi`；knowledge 在收尾提交后发 `KnowledgeIngestedEvent`，wiki 用 `@ApplicationModuleListener` 接收并入队，不再反向依赖 |
| wiki | model、model-runtime | `ModelCatalogPort`、`ModelRuntime`、`EmbeddingModelPort` | 以计划 03、02 的 api 为准 |
| wiki | audit | `WikiActivityAudit` 端口 | `audit.api` 的 `AuditApi` |
| wiki | agenttools | 反向：`contract.wiki.WikiPageOpsPort` | 本域 `WikiPageApi`，agenttools 在计划 08 实现调用 |
| wiki | session | `WikiLanguageSupport` 静态线程本地 | 删除。语言只在本域内以参数传递，session 不再依赖 wiki |
| datasource | knowledge | `KnowledgeCatalogPort`、`KnowledgeIngest` 直连 Mapper 语义 | `KnowledgeBaseQueryApi`、`KnowledgeContentApi`、`KnowledgeIngestApi` |
| datasource | model、model-runtime | 自动标签用 LLM | 以计划 02 的 `LlmChatClient` 为准，`KnowledgeContentApi` 提供标签解析 |
| datasource | audit | `AuditWritePort` | `audit.api` |
| datasource | tenant | `TenantConfigLookup`（旧代码注入但未使用） | 删除注入 |
| datasource | auth | API Key 能力校验在控制器 | 由 Sa-Token 权限点表达，见 1.11 |

## 1.7 旧类到新类

大类按「变化原因」拆分，不换名搬运。行数栏是新类预算上限。

**wiki 的 Ops 与 Support 类**

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `controller/WikiPageOps` 406 | `WikiPageController` ≤200、`WikiPageRevisionController` ≤150，逻辑落 `WikiPageService` | HTTP 绑定与用例分开 |
| `controller/WikiFolderOps` 166 | `WikiFolderController` ≤180 | 单资源控制器 |
| `controller/WikiStatsOps` 194 | `WikiInsightController` ≤200（index、graph、stats、search） | 读视图一组 |
| `controller/WikiMaintenanceOps` 137 | `WikiMaintenanceController` ≤150（links、lint、issues） | 维护动作一组 |
| `controller/WikiRequestSupport` 279 | 删除 | VO 加 `@Valid` 取代手写解析，错误映射归 `GlobalExceptionHandler` |
| `controller/WikiKbAccessGuard` 95 | `WikiKbAccessGuard` ≤100（保留，改名 `WikiKnowledgeBaseGuard`） | KB 访问解析无法用权限注解表达 |
| `controller/WikiActivityRecorder` 51 | `WikiActivityRecorder` ≤80 | 审计埋点适配 |
| `api/WikiPageOpsAdapter` 283 | 删除，`WikiPageService implements WikiPageApi` | 不做转发层 |
| `service/page/DefaultWikiPageService` 729 | 见 1.1 拆分表 | 4 个变化原因 |
| `service/page/WikiPageFolderSupport` 382 | `WikiFolderService` ≤300 | 文件夹树是独立变化原因 |
| `service/page/WikiPageLinkOps` 246 | `WikiPageLinkService` ≤200 | 链接图维护 |
| `service/page/WikiPageViewsSupport` 205 | `WikiPageViewAssembler` ≤200 | 视图组装 |
| `service/page/WikiPageRevisionOps` 163 | `WikiPageRevisionService` ≤180 | 修订与回滚 |
| `service/page/WikiLintService` 471 | `WikiLintService` ≤280、`WikiLintRules` ≤250 | 用例与规则集 |
| `service/page/WikiLinkify` 639 | 见 1.1 拆分表 | 3 个变化原因 |
| `service/page/SlugFuzzy`、`WikiSlugHandles`、`WikiTextUtils` | `WikiSlugMatcher` ≤180、`WikiTextNormalizer` ≤150 | 去 Go 语义工具 |
| `service/page/WikiCrossLinker`、`WikiDeadLinks`、`WikiPageLinkRepair` | `WikiCrossLinker` ≤250、`WikiDeadLinkSweeper` ≤150 | 交叉链接与死链清理 |
| `service/ingest/*` 全部 Ops 与 Support | 见 1.1 拆分表 | 按阶段与基础设施拆分 |
| `repository/TaskPendingOpsRepository`、`TaskDeadLetterRepository` | `WikiIngestOpRepository` ≤250 | 两表合一 |
| `repository/ingest/*Queue` 与 6 个 Redis 存储 | 删除 | JobRunr 与 `@DistributedLock` 取代 |

**wiki 的其余类**

| 旧类（行） | 新类（预算） | 说明 |
|---|---|---|
| `domain/WikiPage` | `WikiPage` 实体 | 主键改 bigint，去零值归一 |
| `domain/WikiPageIssue`、`domain/folder/WikiFolder` | 同名实体 | 同上 |
| `domain/TaskPendingOp`、`TaskDeadLetter` | `WikiIngestOp` 实体 | 状态列加 `status` |
| `domain/WikiConfig` | `WikiConfig` record 进 `wiki.api` | 保留字段与默认值方法 |
| `domain/WikiStringListTypeHandler`、`EmptyListAsNullSerializer`、`SourceRefNeedle` | 删除 | Jackson 与 MyBatis 处理器由 framework 提供，零值语义不带入 |
| `service/WikiChunkCleaner`、`WikiImageEnricher`、`WikiKnowledgeFinalizer`、`WikiModelResolver` 及其 `Default*` 实现 | `WikiChunkGateway` ≤150、`WikiImageEnricher` ≤150、`WikiFinalizeSettler` ≤150、`WikiModelResolver` ≤120，四个都只在 `WikiIngestJob` 内使用，不建接口 | 单实现不建接口，直接类 |

**datasource 的 Ops 与 Support 类**

| 旧类（行） | 新类（预算） | 拆分依据 |
|---|---|---|
| `service/DataSourceService` 651 | `DataSourceService` ≤300、`DataSourceCredentialService` ≤200、`DataSourceSyncService` ≤250 | 配置、凭据、同步三类用例 |
| `service/DataSourceSupport` 233 | 删除 | 校验进 VO，转换进 `DataSourceAssembler` |
| `service/DataSourceItemOps` 343 | `DataSourceItemWriter` ≤300 | 逐条落库 |
| `service/DataSourceSyncResultOps` 184 | `DataSourceSyncResultRecorder` ≤180 | 统计与审计 |
| `service/DataSourceSyncExecutor` 488 | `DataSourceSyncJob` ≤250、`DataSourceSyncPipeline` ≤280 | 作业壳与执行体分开 |
| `connector/feishu/core/FeishuSupport` 468 | `FeishuConfig` ≤200、`FeishuRetryPolicy` ≤150、`FeishuResponseCodec` ≤150 | 配置、重试、编解码 |
| `connector/feishu/core/FeishuDriveOps` 164 | `FeishuDriveLister` ≤180 | 云盘列举 |
| `connector/feishu/core/FeishuWikiTreeOps` 230 | `FeishuWikiTreeLister` ≤230 | wiki 树 |
| `connector/notion/NotionFetchOps` 490 | `NotionPageFetcher` ≤280、`NotionChildResolver` ≤180 | 抓取与子项解析 |
| `service/KnowledgeTagAutoTagProvider` | `DataSourceAutoTagResolver` ≤150 | 自动标签改走 knowledge api |
| `domain/connector/ConnectorRegistry`、`ConnectorHttpWiring`、`config/DataSourceWiring` | `SourceConnectorRegistry` ≤200、`com.ragagent.config.DataSourceAssemblyConfig` | 登记表与装配分开 |

**datasource 的其余类**

| 旧类（行） | 新类（预算） | 说明 |
|---|---|---|
| `domain/DataSource`、`SyncLog` | 同名实体 | 主键改 bigint，`config` 拆列 |
| `domain/DataSourceConfig` | `ConnectorConfig` record（`type`、`resourceIds`、`settings`、`credentials`），凭据在服务端装配时注入 | 不再承担加密职责 |
| `domain/DataSourceConstants` | 枚举 `SyncMode`、`DataSourceStatus`、`SyncLogStatus`、`ConflictStrategy` 与 `ConnectorType` 常量 | 魔法字符串收口 |
| `domain/connector/Connector`、`StreamingConnector` | `SourceConnector`、`StreamingSourceConnector` | 见决定三 |
| `domain/connector/ConnectorHttp` | `ConnectorHttpClient` ≤250，构造器注入 `SsrfGuard` | 去静态持有 |
| `domain/DataSourceSyncPayload`、`TaskInitiator` | 删除，作业参数直接传 `tenantId` 与 ID | JobRunr 持久化参数 |
| `domain/connector/ConnectorException` | 删除，改 `DataSourceCode` 加 `BusinessRuntimeException` | 153 处抛点按 1.9 归类 |
| `dto/*` 7 个 | `vo/*` record 7 个加 `assembler/DataSourceAssembler` | 去 Go 形状 |
| `task/*` 4 个 | 删除 | JobRunr 取代 |

## 1.8 旧测试处置

处置只有三种：移植业务断言、改写为新规范、丢弃。丢弃的理由写在行里。

**wiki（32 个测试类）**

| 旧测试 | 处置 |
|---|---|
| `WikiPageRepositoryTest`、`TaskQueueRepositoryTest` | 移植为 `WikiPageRepositoryTest`、`WikiIngestOpRepositoryTest`，断言改成新表与新主键 |
| `WikiLintServiceTest`、`WikiPageRevisionServiceTest`、`WikiPageServiceTest` | 移植，类名随新类改 |
| `WikiGraphCalculatorTest`、`WikiLinkifyTest`、`SlugFuzzyTest`、`WikiSlugHandlesTest` | 移植 |
| `WikiIngestBatchHandlerTest`、`WikiIngestCitePipelineTest`、`WikiIngestDedupServiceTest`、`WikiIngestLanguageTest`、`WikiIngestRetryTest`、`WikiIngestServiceTest`、`WikiIngestTaxonomyTest`、`WikiIngestTextUtilsTest` | 移植业务断言，断言对象换成新类；重试断言改由 JobRunr 作业行为验证 |
| `WikiKnowledgeFinalizerTest` | 改写为 `WikiFinalizeSettlerTest`，断言 `finalizeSubtask` 调用与归零晋升 |
| `RedisWikiSlugLockTest`、`WikiSlugLockTest` | 丢弃：锁实现删除，改为 `@DistributedLock`，新写 `WikiSlugLockTest` 用真实 Redis 验证互斥 |
| `InProcessWikiIngestTaskQueueTest`、`RedisWikiIngestTaskQueueTest`、`RedisWikiFinalizeLockTest`、`RedisWikiIdentityClaimStoreTest`、`RedisWikiInflightLimiterTest`、`RedisWikiDeletedTombstoneStoreTest` | 丢弃：机制删除，新写 `WikiIngestOpRepositoryTest` 与 `WikiIngestJobTest` 覆盖去重、认领、终态 |
| `WikiRedisWiringTest` | 丢弃：`wiki.redis-enabled` 开关删除 |
| `WikiContractTest`、`WikiHttpContractTest`、`WikiRequestSupportTest` | 丢弃：钉 Go 逐字节载荷与 rawBody 绑定，两者均已删除；业务断言移植到 Controller 与 Service 测试 |
| `WikiDomainTest`、`WikiPromptsTest` | 移植，去掉零值与键序断言 |

**datasource（38 个测试类）**

| 旧测试 | 处置 |
|---|---|
| `DataSourceRepositoryTest`、`SyncLogRepositoryTest`、`DataSourceServiceTest` | 移植，断言改新表与凭据表 |
| `ConnectorFrameworkTest`、`ConnectorHttpTest`、`KnowledgeTagAutoTagProviderTest`、`MapperKnowledgeBridgeUrlIngestTest` | 改写为 `SourceConnectorRegistryTest`、`ConnectorHttpClientTest`、`DataSourceAutoTagResolverTest`、`DataSourceItemWriterTest` |
| `SchedulerTest` | 丢弃：线程池调度删除；新写 `DataSourceScheduleJobTest` 覆盖周期作业重建与暂停 |
| `InProcessDataSourceSyncTaskQueueTest`、`RedisDataSourceSyncTaskQueueTest` | 丢弃：队列删除；新写 `DataSourceSyncJobTest` 覆盖去重、租户缺失失败、部分失败 |
| 七个连接器类的 `*ClientTest`、`*ConnectorTest`、`*FormatsTest`、`JdkXmlFeedParserTest`、`DocxBlocksClientTest`、`DocxMarkdownTest`、`FeishuClientRetryTest`、`FeishuErrorsTest`、`FeishuSupportTest`、`DriveConnectorTest`、`WikiConnectorStreamTest`、`GitLabCompatTest`、`GitLabConfigTest` | 移植，按新类名改；`FeishuSupportTest` 拆成 `FeishuConfigTest`、`FeishuRetryPolicyTest` |
| `DataSourceHttpContractTest`、`DataSourceJsonTest` | 丢弃：钉 Go 零值 `0001-01-01T00:00:00Z`、空串与 `null` 的区分、字节形状；业务断言移植到 `DataSourceControllerTest` 与 VO 测试 |
| 连接器测试里的零时间断言（`WikiConnectorTest`、`RssConnectorTest`、`RssPureFunctionsTest`） | 丢弃零值部分，其余断言移植 |

## 1.9 错误码

模块名称写 `ragagent`。对象不存在、参数校验、未登录一律用 `CommCode`，不重复定义。

**wiki 011**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001011001` wiki.kb.notfound | 知识库不存在 | 知识库不存在 | Knowledge base not found |
| ragagent | `1001011002` wiki.kb.forbidden | 知识库不属于当前租户 | 无权访问该知识库 | You are not allowed to access this knowledge base |
| ragagent | `1001011003` wiki.kb.disabled | 知识库未启用 wiki | 该知识库未启用 wiki 功能 | The wiki feature is not enabled for this knowledge base |
| ragagent | `1001011004` wiki.page.notfound | 页面不存在 | wiki 页面不存在 | Wiki page not found |
| ragagent | `1001011005` wiki.page.conflict | 更新时版本落后 | 页面已被他人修改，请刷新后重试 | The page was modified by someone else. Refresh and try again |
| ragagent | `1001011006` wiki.page.forbidden | 非创建者且非管理员写入 | 只有创建者或管理员可以修改该页面 | Only the creator or an administrator can modify this page |
| ragagent | `1001011007` wiki.revision.notfound | 修订不存在 | wiki 修订不存在 | Wiki page revision not found |
| ragagent | `1001011008` wiki.revert.targetMissing | 回滚目标页面或修订不存在 | 回滚目标不存在 | The revert target does not exist |
| ragagent | `1001011009` wiki.folder.conflict | 同一父目录下同名文件夹 | 该目录下已存在同名文件夹 | A folder with this name already exists here |
| ragagent | `1001011010` wiki.folder.notEmpty | 删除含页面或子目录的文件夹 | 文件夹非空，不能删除 | The folder is not empty and cannot be deleted |
| ragagent | `1001011011` wiki.folder.cycle | 把文件夹移到自身或后代 | 不能把文件夹移到自身或其子目录下 | A folder cannot be moved into itself or its descendants |
| ragagent | `1001011012` wiki.issue.status.invalid | 问题状态取值非法 | 问题状态不合法 | The issue status is invalid |
| ragagent | `1001011013` wiki.pageType.invalid | 页面类型取值非法 | 页面类型不合法 | The page type is invalid |
| ragagent | `1001011014` wiki.status.invalid | 页面状态取值非法 | 页面状态不合法 | The page status is invalid |
| ragagent | `1001011015` wiki.graph.param.invalid | 图谱参数组合非法 | 图谱参数不合法 | The graph parameters are invalid |
| ragagent | `1001011016` wiki.ingest.modelMissing | 知识库未配置合成模型 | 该知识库未配置 wiki 合成模型 | No wiki synthesis model is configured for this knowledge base |
| ragagent | `1001011017` wiki.slug.lockTimeout | 同一页面并发写入超过等待上限 | 页面正被其他任务更新，请稍后重试 | The page is being updated by another task. Try again later |
| ragagent | `1001011018` wiki.ingest.dead | 摄取待办进入死信后重投 | 摄取任务已失败，请检查模型配置后重试 | The ingest task failed. Check the model configuration and retry |
| ragagent | `1001011019` wiki.config.invalid | 租户 wiki 运行配置解析失败 | wiki 配置不合法：{0} | The wiki configuration is invalid: {0} |

**datasource 012**

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `1001012001` datasource.notfound | 数据源不存在 | 数据源不存在 | Data source not found |
| ragagent | `1001012002` datasource.kb.notfound | 目标知识库不存在 | 知识库不存在 | Knowledge base not found |
| ragagent | `1001012003` datasource.kb.forbidden | 目标知识库不属于当前租户 | 无权访问该知识库 | You are not allowed to access this knowledge base |
| ragagent | `1001012004` datasource.type.unsupported | 连接器类型未实现 | 不支持该数据源类型 | This data source type is not supported |
| ragagent | `1001012005` datasource.config.invalid | 连接器配置校验不通过 | 数据源配置不合法：{0} | The data source configuration is invalid: {0} |
| ragagent | `1001012006` datasource.kb.immutable | 更新时尝试改目标知识库 | 不允许修改数据源关联的知识库 | Changing the knowledge base of a data source is not allowed |
| ragagent | `1001012007` datasource.inactive | 对非 active 状态触发校验或列举 | 数据源当前不可用 | The data source is not active |
| ragagent | `1001012008` datasource.credentials.field.unknown | 清除连接器未声明的凭据字段 | 未知的凭据字段：{0} | Unknown credential field: {0} |
| ragagent | `1001012009` datasource.credentials.invalid | 凭据为空或校验失败 | 凭据无效，请检查后重试 | The credentials are invalid. Check them and try again |
| ragagent | `1001012010` datasource.synclog.notfound | 同步日志不存在 | 同步日志不存在 | Sync log not found |
| ragagent | `1001012011` datasource.connector.auth.failed | 外部系统拒绝鉴权 | 连接外部系统失败，请检查凭据 | Failed to authenticate with the external system. Check the credentials |
| ragagent | `1001012012` datasource.connector.fetch.failed | 抓取过程中外部调用失败 | 抓取数据失败：{0} | Failed to fetch data: {0} |
| ragagent | `1001012013` datasource.connector.partial | 部分资源抓取失败 | 部分资源抓取失败，已同步其余条目 | Some resources failed to fetch. The rest were synced |
| ragagent | `1001012014` datasource.sync.running | 同一数据源已有同步在执行 | 该数据源正在同步中 | A sync is already running for this data source |
| ragagent | `1001012015` datasource.sync.enqueue.failed | 同步作业投递失败 | 触发同步失败，请稍后重试 | Failed to start the sync. Try again later |
| ragagent | `1001012016` datasource.connector.item.invalid | 抓取条目既无正文也无 URL | 抓取条目缺少正文或链接 | The fetched item has neither content nor URL |
| ragagent | `1001012017` datasource.synclog.limit.invalid | 同步日志分页 limit 越界 | 分页大小必须在 1 到 100 之间 | The page size must be between 1 and 100 |

## 1.10 `@IgnoreTenant` 白名单

| 位置 | 原因 |
|---|---|
| `SyncLogRetentionJob.purgeExpired` | 周期任务按租户循环物理删除过期日志，循环体外的租户枚举属于后台调度系统通道 |
| `DataSourceScheduleJob.reconcile` | 周期任务枚举全部租户的 active 数据源重建周期作业，属于后台调度系统通道 |
| `WikiIngestDeadLetterRescanner.rescan` | 周期任务扫跨租户的失败待办，属于后台调度系统通道 |

其余路径一律走当前上下文，不用 `@IgnoreTenant`：JobRunr 作业由 `TenantJobFilter` 从参数设置租户；`WikiIngestedEvent` 的监听在发布事务的租户上下文内执行；连接器出网不涉及数据库。三处使用都按路线图 3.7 第 3 条写一行中文注释，并登记到 `progress.md` 的 `IgnoreTenant 白名单`。

三个白名单类都要有对应的 Create 条目：`WikiIngestDeadLetterRescanner` 进 Task 7 的 Files，`DataSourceScheduleJob` 与 `SyncLogRetentionJob` 进 Task 16 的 Files。`WikiIngestDeadLetterRescanner` 与 `DataSourceScheduleJob` 的周期注册在各自的 Task 里写出，不依赖计划 04 的 `RecurrentJobRegistration`（那个只注册 knowledge 的五个周期作业）。注册方式是 `TenantJobEnqueuer.scheduleRecurrent(0L, jobId, cron, job)`，`tenantId` 传 `0` 表示跨租户系统作业，作业体内自行枚举租户。

## 1.11 权限点

权限点命名以计划 03 为准。本计划使用下列逻辑名，计划 03 没有对应项时按偏离登记。

| 逻辑名 | 旧角色要求 | 用于 |
|---|---|---|
| `kb:wiki:read` | 知识库 Viewer 及以上 | wiki 全部读接口 |
| `kb:wiki:write` | 知识库 Editor 及以上，页面写入再叠加创建者或 Admin 校验 | wiki 写接口 |
| `kb:wiki:maintain` | Admin | `links/rebuild`、`lint/auto-fix`、问题状态更新 |
| `kb:datasource:read` | 知识库 Viewer 及以上 | 数据源读、类型目录、资源列举、同步日志 |
| `kb:datasource:write` | 知识库 Editor 及以上 | 数据源增删改、凭据、同步触发、暂停恢复 |

跨租户一律拒绝：知识库归属由 `WikiKnowledgeBaseGuard` 与 `DataSourceKnowledgeBaseGuard` 用 `KnowledgeBaseQueryApi.isOwned` 判定，返回 `wiki.kb.forbidden` 或 `datasource.kb.forbidden`。

## 1.12 前端 API 层切换表

形状栏「是」表示适配层只改路径与解包。

**`frontend/src/api/wiki/index.ts`**

| 函数 | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|
| `listWikiPages` | `GET /knowledgebase/{kbId}/wiki/pages` | `GET /knowledge-bases/{kbId}/wiki/pages` | 是 | 外壳解包，`tenantId` 字符串转 number |
| `createWikiPage`、`getWikiPage` | `/wiki/pages` 系列 | 同上系列 | 是 | 层级 slug 仍走 `encodeSlugPath` |
| `updateWikiPage` | `PUT /wiki/pages/{*slug}` | 同路径新前缀 | 否 | 409 响应转成旧的 `{currentVersion}` 返回形状 |
| `deleteWikiPage` | `DELETE /wiki/pages/{*slug}` | 同路径新前缀 | 是 | |
| `listWikiRevisions`、`getWikiRevision` | `GET /wiki/revisions/{*slug}` | `GET /wiki/pages/{*slug}/revisions`、`GET /wiki/revisions/{slug}/{version}` | 否 | 列表外壳解包；`getWikiRevision` 从列表结果里按 version 取，改为直连新端点 |
| `revertWikiPage` | `POST /wiki/revert` | `POST /wiki/pages/{*slug}/revert` | 是 | slug 进路径 |
| `listWikiFolders`、`createWikiFolder`、`updateWikiFolder`、`deleteWikiFolder` | `/wiki/folders` | 同路径新前缀 | 是 | `folderId` 字符串 |
| `moveWikiPage` | `PUT /wiki/move-page` | `PUT /wiki/pages/{*slug}/move` | 是 | slug 进路径 |
| `getWikiIndex`、`getWikiGraph`、`getWikiStats`、`searchWikiPages` | `/wiki/index`、`/graph`、`/stats`、`/search` | 同路径新前缀 | 是 | `searchWikiPages` 路径改 `/pages/search` |
| `listWikiIssues`、`updateWikiIssueStatus` | `/wiki/issues` | 同路径新前缀 | 是 | |
| `rebuildWikiLinks` | `POST /wiki/rebuild-links` | `POST /wiki/links/rebuild` | 是 | |

**`frontend/src/api/datasource/index.ts`**

| 函数 | 旧路径 | 新路径 | 形状 | 适配点 |
|---|---|---|---|---|
| `getConnectorTypes` | `GET /datasource/types` | `GET /data-sources/types` | 是 | `capabilities` 仍为字符串数组 |
| `listDataSources`、`getDataSource`、`createDataSource`、`updateDataSource`、`deleteDataSource` | `/datasource` 系列 | `/data-sources` 系列 | 是 | `credentials.configured` 由 `configuredFields` 组装 |
| `validateConnection` | `POST /datasource/{id}/validate` | `POST /data-sources/{id}/validation` | 是 | |
| `validateCredentials` | `POST /datasource/validate-credentials` | `POST /data-sources/credential-previews` | 是 | |
| `listResources`、`resolveResourceAncestors` | `/datasource/{id}/resources`、`/resource-ancestors` | 同路径新前缀 | 是 | 保留 120 秒超时 |
| `triggerSync` | `POST /datasource/{id}/sync` | `POST /data-sources/{id}/syncs` | 是 | 400 `datasource.sync.running` 转成提示文案 |
| `pauseDataSource`、`resumeDataSource` | `/{id}/pause`、`/{id}/resume` | 同路径新前缀 | 是 | |
| `getSyncLogs` | `GET /datasource/{id}/logs` | `GET /data-sources/{id}/sync-logs` | 是 | 分页形状改 `PageData`，函数内部转回 `{items,total}` |
| `putDataSourceCredentials` | `PUT /datasource/{id}/credentials` | 同路径新前缀 | 是 | 返回 `{fields:{credentials:{configured}}}` 形状保持 |
| `deleteDataSourceCredentials` | `DELETE /datasource/{id}/credentials/credentials` | `DELETE /data-sources/{id}/credentials/{field}` | 否 | 未知字段从 400 硬编码改错误码，函数签名不变 |

## 1.13 界面端到端域标签

计划 01 的 `tests/e2e/PAGES.md` 给每个页面标域。本计划负责转绿的行：

| 域标签 | 页面与流程 | 用例文件 |
|---|---|---|
| `@wiki` | 知识库详情 Wiki 页签：新建页面、打开版本抽屉、回滚、文件夹树展开、索引页、图谱两种模式（overview 与 ego）、搜索、问题列表改状态。前置：在知识库设置里开启 Wiki 并配置合成模型 | `tests/e2e/tests/wiki/wiki-browser.spec.ts` |
| `@wiki-lint` | Wiki 维护入口：lint 结果列表、auto-fix 后列表刷新、rebuild-links 后的提示 | 同上文件内追加用例，标签独立 |
| `@datasource` | 知识库设置数据源页签：类型下拉、新建数据源对话框（选类型后取消与人脸校验）、凭据卡片、资源选择器懒加载、手动同步、暂停与恢复、同步日志抽屉快照 | `tests/e2e/tests/datasource/datasource-settings.spec.ts` |

冒烟清单（本计划负责转绿的条目）：`wiki-page-create`（建页成功并出现在列表）、`wiki-revision-revert`（回滚后正文回到旧版本）、`datasource-sync-manual`（新建数据源后手动同步，日志状态从 running 变 success）。

## 1.14 与计划 04 和计划 08 的边界

| 事项 | 归属 |
|---|---|
| 知识库入库完成后的 wiki 摄取触发 | 计划 04 发 `KnowledgeIngestedEvent`，本计划监听 |
| Agent 工具的 wiki 读写实现 | 本计划只给 `wiki.api`，工具包装与目录在计划 08 |
| 会话里的 wiki 语言判定 | 计划 08 负责；本计划删除 `WikiLanguageSupport` |
| MCP 与 wiki 的交互 | 无 |
| 数据源同步产生的新知识条目 | 本计划只写入，入库与分块由计划 04 的作业链负责 |

# 2. 任务

两批可并行：批次 A 是 wiki，批次 B 是 datasource，互不依赖，两个执行方在各批内部按编号顺序执行，Task 18 在两者都完成后做。

每个任务的步骤固定五步：写失败测试、跑红、实现、跑绿、提交。跑红与跑绿都用 `MVN test -Dtest=<该任务测试类>`，跑红预期 FAIL 且原因是类不存在或断言不成立，跑绿预期 PASS。提交前跑 `MVN checkstyle:check`，预期无违规。提交后在 `docs/migration/progress.md` 追加一行。

## 批次 A：wiki

### Task 1: wiki 盘点核对、DDL 与行级安全

**Files:**
- Create: `DB/V<D>_NN__DDL_rebuild_wiki_create_wiki_tables_jiangguifan.sql`
- Create: `T/wiki/{WikiRowLevelSecurityTest,WikiConstraintsTest}.java`

**Interfaces:**
- Consumes: 计划 01 Task 5 的 fail-closed RLS 策略模板，计划 01 Task 18 的数据库镜像
- Produces: 5 张表 `wiki_pages`、`wiki_page_revisions`、`wiki_folders`、`wiki_page_issues`、`wiki_ingest_ops`

- [x] **Step 1: 核对盘点**
  - 读 `docs/migration/frozen-bugs.md` 中涉及 wiki 的条目，逐条写进 `progress.md` 并判断是否影响本计划。
  - 跑 `find domains/src/main/java/com/ragagent/wiki -name '*.java' | wc -l` 与 `find domains/src/main/java/com/ragagent/wiki -name '*.java' -exec cat {} + | wc -l`，预期 `165` 与 `23545`。不符先改 1.1。
  - 跑 `grep -c "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping" domains/src/main/java/com/ragagent/wiki/controller/WikiPageController.java`，预期 `21`。
- [x] **Step 2: 写失败测试**
  - `WikiRowLevelSecurityTest`：5 张表各一对 `tenant2CannotReadTenant1Rows_<表名>`、`tenant2CannotInsertRowForTenant1_<表名>`，跨租户读取断言为空列表，跨租户插入断言被策略拒绝。
  - `WikiConstraintsTest.liveDuplicateSlugRejected`、`recreateAfterSoftDeleteAccepted`、`siblingFolderNameUnique`、`revisionVersionUniquePerPage`、`ingestOpDedupKeyUniquePerStatus`。
- [x] **Step 3: 跑红**：`MVN test -Dtest='WikiRowLevelSecurityTest,WikiConstraintsTest'`。预期 FAIL，表不存在。
- [x] **Step 4: 实现**
  - 按 1.4 写 DDL。`wiki_pages` 唯一索引 `(tenant_id, knowledge_base_id, slug, deleted_at)`；`wiki_folders` 唯一索引 `(tenant_id, knowledge_base_id, parent_id, name, deleted_at)`；`wiki_page_revisions` 唯一 `(page_id, version)`；`wiki_page_issues` 索引 `(tenant_id, knowledge_base_id, slug, status)`。
  - `wiki_ingest_ops`：`id bigint`、`tenant_id`、`knowledge_base_id`、`op varchar(32)`、`dedup_key varchar(128)`、`status varchar(16)`（`PENDING`、`CLAIMED`、`DONE`、`DEAD`）、`payload jsonb`、`language varchar(16)`、`fail_count int NOT NULL DEFAULT 0`、`claimed_at timestamptz`、`enqueued_at timestamptz NOT NULL DEFAULT now()`。唯一 `(tenant_id, knowledge_base_id, dedup_key, status) WHERE status IN ('PENDING','CLAIMED')`，索引 `(tenant_id, knowledge_base_id, status, id)`。
  - 5 张表全部开行级安全，策略用计划 01 的 fail-closed 模板。
  - `wiki_pages` 的 gin 全文索引改为 `CREATE INDEX ... USING pgroonga (content)`，与计划 01 数据库镜像一致。
- [x] **Step 5: 跑绿**，然后反向验证：对 `wiki_pages` 执行 `ALTER TABLE wiki_pages DISABLE ROW LEVEL SECURITY`，`WikiRowLevelSecurityTest` 预期变红，确认后恢复。
- [x] **Step 6: 提交**：`git add DB/V<D>_NN__DDL_rebuild_wiki_create_wiki_tables_jiangguifan.sql T/wiki`，`git commit -m "feat(wiki): wiki 表与行级安全"`。

### Task 2: wiki Entity、Mapper 与 Repository

**Files:**
- Create: `M/wiki/entity/{WikiPage,WikiPageRevision,WikiFolder,WikiPageIssue,WikiIngestOp}.java`
- Create: `M/wiki/mapper/{WikiPageMapper,WikiPageSearchMapper,WikiPageRevisionMapper,WikiFolderMapper,WikiPageIssueMapper,WikiIngestOpMapper}.java`
- Create: `R/mapper/wiki/{WikiPageMapper,WikiPageSearchMapper,WikiIngestOpMapper}.xml`
- Create: `M/wiki/repository/{WikiPageRepository,WikiFolderRepository,WikiPageIssueRepository,WikiIngestOpRepository}.java`
- Create: `T/wiki/{WikiPageRepositoryTest,WikiFolderRepositoryTest,WikiIngestOpRepositoryTest}.java`

**Interfaces:**
- Produces:
  - `WikiPageRepository extends ServiceImpl<WikiPageMapper, WikiPage>`：`Optional<WikiPage> findBySlug(long kbId, String slug)`、`Optional<WikiPage> findByIdForUpdate(long id)`、`List<WikiPage> listByKb(WikiPageCondition condition)`、`List<WikiPage> search(long kbId, String query, int limit)`、`List<String> listSlugsByPrefix(long kbId, String prefix)`、`boolean markDeleted(long id)`、`int detachOutLink(long kbId, String slug)`（把 `out_links` 数组里等于该 slug 的元素摘掉，写 Mapper XML）
  - `WikiFolderRepository`：`List<WikiFolder> listTree(long kbId)`、`Optional<WikiFolder> findSibling(long kbId, long parentId, String name)`、`boolean hasChildren(long folderId)`
  - `WikiPageIssueRepository`：`List<WikiPageIssue> list(long kbId, String slug, String status)`、`boolean updateStatus(long id, String status)`
  - `WikiIngestOpRepository`：`boolean enqueue(WikiIngestOp op)`、`List<WikiIngestOp> claimBatch(long kbId, int limit, OffsetDateTime staleBefore)`、`void markDone(List<Long> ids)`、`void markDead(long id, String error)`、`long countClaimed(long kbId)`、`List<WikiIngestOp> listDead(long kbId, int limit)`
- [x] **Step 1: 写失败测试**
  - `WikiPageRepositoryTest.jsonbColumnsRoundTrip`、`softDeleteHidesRow`、`findBySlugScopedByKb`、`listByKbFiltersPageTypeStatusAndCategory`、`searchMatchesContentAndTitle`、`updateByIdKeepsUntouchedColumns`、`detachOutLinkRemovesElementFromEveryLiveRow`。
  - `WikiFolderRepositoryTest.listTreeReturnsDepthFirst`、`findSiblingIgnoresSoftDeleted`。
  - `WikiIngestOpRepositoryTest.enqueueTwiceSameDedupKeyKeepsOnePendingRow`、`claimBatchMarksClaimedAndCounts`、`claimBatchSkipsFreshClaims`、`markDoneFreesDedupKey`、`markDeadKeepsPayloadAndError`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiPageRepositoryTest,WikiFolderRepositoryTest,WikiIngestOpRepositoryTest'`。
- [x] **Step 3: 实现**：实体 `autoResultMap = true`，jsonb 列用 `JacksonTypeHandler`，`pageMetadata` 用 `FieldStrategy.ALWAYS`（旧代码的 `page_metadata` 带 DEFAULT）。`findByIdForUpdate` 与 `claimBatch` 写 Mapper XML，使用 `FOR UPDATE` 与 `UPDATE ... WHERE status='PENDING'` 条件更新。主码不出现 `@Select` 等注解 SQL。`WikiPageSearchMapper` 的全文查询用 PGroonga `&@~` 运算符。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/wiki/entity M/wiki/mapper M/wiki/repository R/mapper/wiki T/wiki`，`git commit -m "feat(wiki): wiki 持久层，四个聚合根"`。

### Task 3: wiki api 包、错误码与七语文案

**Files:**
- Create: `M/wiki/api/{WikiCode,WikiEditSource,WikiPageType,WikiPageStatus,WikiConfig,WikiPageApi,WikiIssueApi,WikiIndexApi}.java`
- Create: `M/wiki/bo/{WikiPageBo,WikiPageWriteBo,WikiIssueBo,WikiIssueWriteBo,LinkRepairBo,WikiIndexBo,WikiPageCondition,WikiPageRevisionBo,WikiFolderBo,WikiFolderNodeBo,WikiGraphBo,GraphMode,WikiStatsBo,WikiLintReportBo,WikiAutoFixBo,ItemOutcome}.java`
- Create: `M/wiki/vo/{CreateWikiPageBo,UpdateWikiPageBo,WikiBudgetBo,WikiRuntimeConfig,WikiRuntimeConfigSnapshot}.java`（前两个由 Controller 用的 VO 是 `CreateWikiPageReqVo` 与 `UpdateWikiPageReqVo`，任务与测试用的 BO 是这两个；后三个是 wiki 运行配置的数据形状）
- Modify: `R/i18n/app-messages{,_zh_CN,_zh_HK,_en_US,_es_ES,_pt_BR,_ru_RU,_th_TH}.properties`（追加 1.9 的 19 个 wiki 错误码）
- Create: `M/config/WikiAssemblyConfig.java`
- Create: `T/wiki/{WikiCodeMessagesTest,WikiConfigTest,WikiPageTypeTest,WikiApiTypesTest}.java`

**Interfaces:**
- Produces: `WikiCode` 19 个枚举值对应 1.9；`WikiEditSource { PIPELINE, AGENT, USER, REVERT }` 与 `WikiPageType { SUMMARY, ENTITY, CONCEPT, INDEX, SYNTHESIS, COMPARISON }`、`WikiPageStatus { DRAFT, PUBLISHED, ARCHIVED }`，JSON 值为小写。`WikiConfig` record 保留旧字段与 `ingestBatchSizeOrDefault` 等四个回落方法。三个 api 的签名见「对外承诺」
- [x] **Step 1: 写失败测试**
  - `WikiCodeMessagesTest.everyCodeHasMessageInSevenLanguages`：遍历 `WikiCode.values()`，七种语言各取一次，断言不等于 key 本身。
  - `WikiConfigTest.fromJsonIgnoresUnknownKeys`、`toJsonOmitsEmptyFields`、`defaultsApplyWhenZero`。
  - `WikiPageTypeTest.fromValueRejectsUnknown`、`toValueIsLowercase`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiCodeMessagesTest,WikiConfigTest,WikiPageTypeTest'`。
- [x] **Step 3: 实现**：非中英文五种语言的文案由机器翻译出初稿，同一提交入库并标注待人工校对。`CodeCache.register(WikiCode.values())` 放在 `WikiAssemblyConfig`。`WikiConfig.fromJson` 用宽容 mapper，`extractionGranularity` 为 `String`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/wiki/api M/wiki/bo M/config/WikiAssemblyConfig.java R/i18n T/wiki`，`git commit -m "feat(wiki): api 包、错误码与七语种文案"`。

### Task 4: wiki 页面用例与修订

**Files:**
- Create: `M/wiki/service/page/{WikiPageService,WikiPageRevisionService,WikiPageViewAssembler,WikiSlugNormalizer}.java`
- Create: `T/wiki/page/{WikiPageServiceTest,WikiPageRevisionServiceTest,WikiSlugNormalizerTest}.java`

**Interfaces:**
- Consumes: Task 2 的 Repository，`KnowledgeBaseQueryApi` 与 `audit.api.AuditApi`
- Produces:
  - `WikiPageService implements WikiPageApi`：签名见「对外承诺」，另加 `WikiPageBo createFromRequest(long kbId, CreateWikiPageBo in, WikiEditSource source)`、`WikiPageBo updateFromRequest(long kbId, String slug, UpdateWikiPageBo in, WikiEditSource source)`、`PageData<WikiPageBo> list(long kbId, WikiPageCondition condition)`、`void revertToVersion(long kbId, String slug, int version)`
  - `WikiPageRevisionService`：`void snapshot(WikiPage page, WikiEditSource source, Long editorId)`、`List<WikiPageRevisionBo> list(long kbId, String slug, int limit, int offset)`、`Optional<WikiPageRevisionBo> find(long kbId, String slug, int version)`、`WikiPage restore(WikiPageRevisionBo revision)`
  - `WikiSlugNormalizer.normalize(String rawSlug)` 返回去重前导斜杠、折叠连续斜杠、裁剪首尾空白的 slug
- [x] **Step 1: 写失败测试**
  - `WikiPageServiceTest`：`createRejectsBlankSlug`（`CommCode.PARAM_ERROR`）、`createRequiresWikiEnabledKb`（`wiki.kb.disabled`）、`createRejectsUnknownPageType`（`wiki.pageType.invalid`）、`createRejectsUnknownStatus`（`wiki.status.invalid`）、`updateWithStaleVersionThrowsConflictWithCurrentVersion`（`wiki.page.conflict`，details 含 `currentVersion`）、`updateBumpsVersionOnlyWhenContentFieldsChange`、`updateMetaDoesNotBumpVersion`、`deleteSoftDeletesAndRemovesInboundLinks`、`deleteOnMissingPageThrowsNotFound`、`listFiltersByPageTypeAndStatus`、`listSupportsCursorPagination`、`nonCreatorNonAdminCannotWrite`（`wiki.page.forbidden`）、`otherTenantKbRejected`（`wiki.kb.forbidden`）。
  - `WikiPageRevisionServiceTest.snapshotKeepsMachineRevisionsAt50AndHardCapAt200`、`restoreReturnsContentOfTargetVersion`、`revertToCurrentVersionRejected`（`wiki.revert.targetMissing`）、`revisionNotFoundThrows`。
  - `WikiSlugNormalizerTest.stripsLeadingSlash`、`collapsesDuplicateSlashes`、`keepsHierarchySegments`、`rejectsBlankAfterNormalize`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiPageServiceTest,WikiPageRevisionServiceTest,WikiSlugNormalizerTest'`。
- [x] **Step 3: 实现**：每个用例拆成「前置校验」「构造实体」「保存」「提交后动作」四个私有方法，各 ≤40 行。校验只读 `KnowledgeBaseQueryApi` 与入参，写库走 Repository。删除页面时调 `WikiPageRepository.detachOutLink(kbId, slug)` 把指向它的 `[[slug]]` 从其他页面的 `out_links` 摘掉，只维护已存链接图，不解析正文。审计写 `wiki.content_changed`，动作名为 `create`、`update`、`delete`、`revert`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/wiki/service/page T/wiki/page`，`git commit -m "feat(wiki): 页面用例与修订回滚"`。

### Task 5: wiki 文件夹、视图、搜索、索引、图谱与统计

**Files:**
- Create: `M/wiki/service/folder/{WikiFolderService,WikiFolderTreeBuilder}.java`
- Create: `M/wiki/service/page/{WikiIndexViewBuilder,WikiPageSearchService}.java`
- Create: `M/wiki/service/graph/WikiGraphCalculator.java`
- Create: `M/wiki/service/page/WikiStatsService.java`
- Create: `T/wiki/{WikiFolderServiceTest,WikiFolderTreeBuilderTest,WikiIndexViewBuilderTest,WikiPageSearchServiceTest,WikiGraphCalculatorTest,WikiStatsServiceTest}.java`

**Interfaces:**
- Consumes: Task 2 的 Repository，Task 4 的 `WikiSlugNormalizer`
- Produces:
  - `WikiFolderService`：`List<WikiFolderNodeBo> list(long kbId, long parentId, Set<WikiPageType> pageTypes)`、`WikiFolderBo create(long kbId, long parentId, String name)`、`WikiFolderBo rename(long kbId, long folderId, String name)`、`WikiFolderBo move(long kbId, long folderId, long newParentId)`、`void delete(long kbId, long folderId)`、`void movePage(long kbId, String slug, long folderId)`
  - `WikiIndexViewBuilder.build(long kbId, Set<WikiPageType> pageTypes, int limit, String cursor)` 返回 `WikiIndexBo`
  - `WikiGraphCalculator.compute(List<WikiPage> pages, GraphMode mode, String centerSlug, int depth, int limit)` 返回 `WikiGraphBo`
  - `WikiStatsService.stats(long kbId)` 返回 `WikiStatsBo`
- [x] **Step 1: 写失败测试**
  - `WikiFolderServiceTest.createUnderRootUsesZeroParent`、`siblingNameConflictThrows`、`moveIntoOwnDescendantThrows`（`wiki.folder.cycle`）、`deleteNonEmptyFolderThrows`（`wiki.folder.notEmpty`）、`movePageIntoUnknownFolderThrows`、`listScopesByPageTypes`。
  - `WikiFolderTreeBuilderTest.pageCountIsRecursive`、`hasChildrenFlagIsAccurate`、`emptyFolderStillListed`。
  - `WikiIndexViewBuilderTest.groupsByPageType`、`cursorAdvancesWithinGroup`、`introEmptyWhenNoIndexPage`。
  - `WikiPageSearchServiceTest.matchesTitleAndContent`、`respectsLimit`、`escapesSpecialCharacters`。
  - `WikiGraphCalculatorTest.overviewReturnsTopConnected`、`egoReturnsNeighborhoodWithinDepth`、`egoWithoutCenterThrows`（`wiki.graph.param.invalid`）、`truncatesAtLimitAndSetsFlag`。
  - `WikiStatsServiceTest.countsByTypeAndOrphans`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiFolderServiceTest,WikiFolderTreeBuilderTest,WikiIndexViewBuilderTest,WikiPageSearchServiceTest,WikiGraphCalculatorTest,WikiStatsServiceTest'`。
- [x] **Step 3: 实现**：文件夹 `parent_id` 用 `0` 表示根。移动环检测沿 `parent_id` 向上走，深度上限 32 次防数据成环。图谱邻域用迭代 BFS，不递归。检索走 `WikiPageSearchMapper`，用户输入转义后作为 PGroonga 查询串。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/wiki/service/folder M/wiki/service/graph M/wiki/service/page T/wiki`，`git commit -m "feat(wiki): 文件夹、索引、图谱与统计"`。

### Task 6: wiki 链接图、lint 与 slug 互斥

**Files:**
- Create: `M/wiki/service/page/{WikiPageLinkService,WikiLinkifier,WikiLinkSpanGuard,WikiSlugMatcher,WikiCrossLinker,WikiDeadLinkSweeper,WikiSlugLockGuard}.java`
- Create: `M/wiki/service/lint/{WikiLintService,WikiLintRules}.java`
- Create: `M/config/WikiDistributedLockKeyResolver.java`
- Create: `T/wiki/page/{WikiPageLinkServiceTest,WikiLinkifierTest,WikiSlugMatcherTest}.java`
- Create: `T/wiki/lint/{WikiLintServiceTest,WikiLintRulesTest}.java`
- Create: `T/wiki/WikiSlugLockTest.java`
- Modify: `M/config/WikiAssemblyConfig.java`（声明 `@Primary` 的 `DistributedLockKeyResolver`）

**Interfaces:**
- Consumes: 计划 01 的 `@DistributedLock`、`DistributedLockKeyResolver`、`DistributedLockAspect`
- Produces:
  - `WikiPageLinkService`：`void rebuildAll(long kbId)`、`void injectCrossLinks(long kbId, List<String> affectedSlugs)`、`void removeInboundLinks(long kbId, String slug)`、`Set<String> deadLinks(long kbId)`、`String repairContentLinks(long kbId, String selfSlug, String content)`
  - `WikiLintService.lint(long kbId)` 返回 `WikiLintReportBo`；`autoFix(long kbId)` 返回 `WikiAutoFixBo`
  - `WikiDistributedLockKeyResolver implements DistributedLockKeyResolver`，`lockName()` 为 `wiki-slug` 时把参数拼成 `wiki:slug:<kbId>:<slug>`
  - `WikiSlugLockGuard.tryWithSlugLock(long kbId, String slug, Runnable body)` 返回 `boolean`，内部用 `RedissonClient.tryLock(0, 300, SECONDS)`，键与注解路径一致
- [x] **Step 1: 写失败测试**
  - `WikiPageLinkServiceTest.rebuildRecomputesOutAndInLinks`、`crossLinksSkipSelfAndExisting`、`repairRewritesOnlyToExistingSlug`、`deadLinksListsMissingSlugs`。
  - `WikiLinkifierTest.detectsBracketLinks`、`ignoresCodeBlocksAndInlineCode`、`handlesEscapedBrackets`、`keepsOriginalWhenNoMatch`。
  - `WikiSlugMatcherTest.matchesExactBeforeFuzzy`、`fuzzyIsCaseInsensitive`、`rejectsAmbiguousMatch`。
  - `WikiLintRulesTest.orphanPageReported`、`brokenLinkReported`、`emptySummaryReported`、`autoFixFillsSummaryOnly`。
  - `WikiSlugLockTest.concurrentWritesToSameSlugSerializeToTwoSequentialVersions`：两个虚拟线程同时进入 `@DistributedLock` 方法，断言版本号最终为 2 且两次写入内容都在。另加 `adminWriteTimesOutWithCode`（`@DistributedLock` 路径超时抛 `wiki.slug.lockTimeout`）与 `ingestGuardSkipsWithoutWaiting`（`WikiSlugLockGuard` 路径返回 false，不抛）。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiPageLinkServiceTest,WikiLinkifierTest,WikiSlugMatcherTest,WikiLintRulesTest,WikiSlugLockTest'`。
- [x] **Step 3: 实现**
  - `WikiPageService` 的写方法标 `@DistributedLock(lockName = "wiki-slug", waitTime = 120, leaseTime = 300)`，参数顺序为 `(long kbId, String slug, ...)`，由 `WikiDistributedLockKeyResolver` 生成键。拿不到锁时抛 `wiki.slug.lockTimeout`，这是管理面主动写页面的语义：用户点了保存就要得到明确反馈。
  - 摄取侧要「拿不到锁就跳过该 slug 而不是失败」，交给 `WikiSlugLockGuard.tryWithSlugLock`，内部用 `RLock.tryLock(0, 300, SECONDS)`，锁键由同一个 `WikiDistributedLockKeyResolver` 拼装。两套语义是刻意的：管理面阻塞等待并超时报错，摄取侧不等待并跳过。`WikiSlugLockGuard` 不复用 `@DistributedLock` 注解，因为注解的切面只能抛异常，表达不了「跳过」。
  - `WikiLintRules` 每条规则一个方法，`WikiLintService.lint` 只做聚合与排序。
  - 补 Task 4 里留空的摘链接调用的真实实现。
- [x] **Step 4: 跑绿**。`WikiSlugLockTest` 需要真实 Redis，继承计划 01 的 `RedisContainerTest`（计划 01 无此基类时用 Testcontainers Redis 在本测试内启动）。
- [x] **Step 5: 提交**：`git add M/wiki/service/page M/wiki/service/lint M/config/WikiDistributedLockKeyResolver.java M/config/WikiAssemblyConfig.java T/wiki`，`git commit -m "feat(wiki): 链接图、lint 与分布式 slug 锁"`。

### Task 7: wiki 摄取作业

**Files:**
- Create: `M/wiki/service/ingest/{WikiIngestJob,WikiIngestService,WikiIngestScheduler,WikiIngestEnqueuer,WikiIngestBatchRunner,WikiIngestBatchLoader,WikiIngestBatchContextFactory,WikiIngestMapPhase,WikiIngestUpdateBuilder,WikiIngestMergePolicy,WikiIngestReducePhase,WikiIngestPageWriter,WikiIngestSettler}.java`
- Create: `M/wiki/service/ingest/{WikiCitationExtractor,WikiCitationPrompt,WikiCitationParser,WikiEntityDedup,WikiSlugSimilarity,WikiTaxonomyPlanner,WikiIdentityClaimer,WikiIndexPageBuilder,WikiContentGenerator,WikiLlmClient}.java`
- Create: `M/wiki/service/ingest/{WikiChunkGateway,WikiImageEnricher,WikiFinalizeSettler,WikiModelResolver,WikiKnowledgeBaseConfigReader,WikiIngestDeadLetterRescanner}.java`
- Create: `M/wiki/prompt/{WikiPromptCatalog,WikiPromptRenderer,WikiPromptHashing}.java`
- Create: `M/wiki/listener/WikiIngestedEventListener.java`
- Create: `M/wiki/config/{WikiRuntimeConfigSource,WikiRuntimeConfigClients}.java`
- Create: `T/wiki/ingest/{WikiIngestJobTest,WikiIngestBatchRunnerTest,WikiIngestEnqueuerTest,WikiIngestSettlerTest,WikiCitationParserTest,WikiEntityDedupTest,WikiTaxonomyPlannerTest,WikiContentGeneratorTest,WikiIngestDeadLetterRescannerTest}.java`
- Create: `T/wiki/config/WikiRuntimeConfigClientsTest.java`

**Interfaces:**
- Consumes: 计划 04 的 `TenantJobEnqueuer`、`JobScheduler`、`KnowledgeIngestedEvent`、`KnowledgeContentApi`、`KnowledgeIngestApi`、`KnowledgeBaseQueryApi`；计划 02 的 `LlmChatClient`、`EmbeddingPort`；计划 01 的 `TenantClientRegistry`、`TenantConfigSource`
- Produces:
  - `WikiIngestService`：`void enqueueIngest(long kbId, long knowledgeId, String language)`、`void enqueueRetract(long kbId, long knowledgeId, String docTitle, String docSummary)`、`void enqueueFinalize(long kbId)`、`void clearPendingOpsOfDeletedKb(long kbId)`
  - `WikiIngestJob implements TenantScopedJob`：签名 `long tenantId()` 返回构造时传入的租户，`void run()` 执行主体，业务 ID（`kbId`）存构造器字段。不写 `@Job(retries=0)`，也不写成 `run(Long tenantId, Long kbId)` 的双参形式，那两种都与计划 04 的 `TenantScopedJob{long tenantId(); void run();}` 不符。重试次数由 `TenantJobEnqueuer.enqueue` 的调用方用 JobRunr 的 `RetryPolicy` 指定，默认沿用计划 04 的全局配置
  - `WikiIngestScheduler.trigger(long kbId, long ingestOpId)`：`jobId = "wiki-ingest-" + kbId + "-" + ingestOpId`，每次触发带入本次认领到的操作 ID，避免同一个 `kbId` 的后续批次被已完成的作业 ID 静默吞掉；防抖窗口靠 `wiki_ingest_ops` 的 `dedup_key` 唯一约束实现，不靠 jobId
  - `WikiIngestEnqueuer`：写 `wiki_ingest_ops`，`dedup_key` 为 knowledgeId（retract 为 `retract:<id>`），finalize 行为 `op='FINALIZE'`
  - `WikiBudgetBo`（进程内）：`record WikiBudgetBo(int batchSize, int mapParallel, int reduceParallel, int maxInflight)`
  - `WikiRuntimeConfigSource implements TenantConfigSource<WikiRuntimeConfig>`：`version(long tenantId)` 调计划 03 的 `TenantConfigApi.sectionVersion(tenantId, "wiki")`（只查 `tenant_settings` 该 section 行的 `updated_at` 一列，表属于 tenant 域）；`load(long tenantId)` 调 `TenantConfigApi.wiki(tenantId)` 拿 `WikiTenantConfigBo` 并转成 `WikiRuntimeConfig`。本计划不直连 `tenant_settings` 表，只用 `TenantConfigApi`
  - `WikiRuntimeConfigClients.forTenant(long tenantId)` 返回 `WikiRuntimeConfigSnapshot`，内部持 `TenantClientRegistry<WikiRuntimeConfig, WikiRuntimeConfigSnapshot>`
- [x] **Step 1: 写失败测试**
  - `WikiIngestEnqueuerTest.enqueueTwiceSameKnowledgeKeepsOnePendingOp`、`finalizeOpCoalescesPerKb`、`retractOpDedupKeyDiffersFromIngest`。
  - `WikiIngestJobTest`：`missingTenantFailsWithoutRetry`、`claimsBatchAndMarksDone`、`deadLetterKeptWhenLlmRepeatedlyFails`（重试 10 次后 `status='DEAD'`）、`skipsSlugWhenLockBusy`、`writesPageWithPipelineEditSource`。
  - `WikiIngestBatchRunnerTest.mapPhaseReadsTextChunksInOrder`、`reducePhaseMergesUpdatesForSameSlug`、`finalizeRebuildsIndexOnceForBatch`。
  - `WikiIngestSettlerTest.decrementsPendingSubtaskAndPromotesWhenZero`。
  - `WikiCitationParserTest.parsesSlugAndFactsAndIgnoresUnknownKeys`、`truncatedJsonIsRejected`。
  - `WikiEntityDedupTest.groupsByNormalizedIdentity`、`prefersLongerContent`。
  - `WikiTaxonomyPlannerTest.alignsToExistingFolders`、`createsAtMostTwoLevels`。
  - `WikiContentGeneratorTest.usesSynthesisModelFromKbConfig`、`missingModelThrowsIngestModelMissing`。
  - `WikiIngestDeadLetterRescannerTest.rescansDeadOpsOlderThanWindow`、`skipsOpsStillUnderRetry`、`recurrentRegistrationUsesSystemTenant`。
  - `WikiRuntimeConfigClientsTest.reusesSnapshotWithinSameVersion`、`rebuildsAfterVersionBumps`、`loadsFreshWhenTenantMissing`、`doesNotCacheAcrossTenants`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiIngest*Test,WikiCitationParserTest,WikiEntityDedupTest,WikiTaxonomyPlannerTest,WikiContentGeneratorTest,WikiRuntimeConfigClientsTest'`。
- [x] **Step 3: 实现**
  - `WikiIngestedEventListener` 用 `@ApplicationModuleListener` 监听 `KnowledgeIngestedEvent`，在事务提交后执行，调用 `enqueueIngest` 与 `enqueueFinalize`。这是 knowledge 与 wiki 的唯一正向耦合点。
  - `WikiIngestJob` 拆分：作业壳负责租户、KB 校验、认领批次；`WikiIngestBatchRunner` 负责 Map、Reduce、Finalize 三段；`WikiIngestBatchLoader` 批量读分块与知识行；`WikiIngestPageWriter` 单 slug 写入，包在 `WikiSlugLockGuard.tryWithSlugLock` 内。
  - 语言取自 `wiki_ingest_ops.language`，缺省回落 `zh-CN`。
  - 批次预算从 `WikiRuntimeConfigClients.forTenant(tenantId)` 取，知识库级的 `WikiConfig` 只覆盖文档批次与并发两项；`maxInflight` 用 `countClaimed(kbId)` 判断，超限时 `WikiIngestScheduler.trigger(kbId, Duration.ofSeconds(30))` 重排。作业每次进入都重新 `forTenant`，不把快照存进作业参数，租户配置变更后下一个作业即生效。
  - `WikiRuntimeConfigSource.version` 只查一行一列，不做整份配置反序列化；快照是不可变 record，`factory` 解析失败时抛 `BusinessRuntimeException` 加 `wiki.config.invalid`，不要静默回落默认值。
  - Finalize 阶段标 `@DistributedLock(lockName = "wiki-finalize")`，键 `wiki:finalize:<kbId>`；拿不到锁时 `WikiIngestJob` 抛可重试异常，由 JobRunr 退避。
  - `WikiFinalizeSettler` 收尾时调用 `KnowledgeIngestApi.finalizeSubtask`，不再直接写 knowledge 的表。
  - 提示词从旧 `WikiPrompts` 原样搬进 `WikiPromptCatalog`，去 `StringBuilder` 拼接，改 `String.formatted`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/wiki/service/ingest M/wiki/prompt M/wiki/listener M/wiki/config T/wiki/ingest T/wiki/config`，`git commit -m "feat(wiki): 摄取作业迁入 JobRunr 与租户配置注册表"`。

### Task 8: wiki Controller、VO 与 Assembler

**Files:**
- Create: `M/wiki/controller/{WikiPageController,WikiPageRevisionController,WikiFolderController,WikiInsightController,WikiMaintenanceController,WikiKnowledgeBaseGuard,WikiActivityRecorder}.java`
- Create: `M/wiki/vo/{WikiPageVo,WikiPageSummaryVo,CreateWikiPageReqVo,UpdateWikiPageReqVo,RevertWikiPageReqVo,WikiRevisionVo,WikiFolderVo,WikiFolderNodeVo,CreateWikiFolderReqVo,UpdateWikiFolderReqVo,MoveWikiPageReqVo,WikiIndexVo,WikiGraphVo,WikiStatsVo,WikiIssueVo,UpdateIssueStatusReqVo,WikiLintReportVo,WikiAutoFixVo}.java`
- Create: `M/wiki/assembler/WikiAssembler.java`
- Create: `T/wiki/{WikiPageControllerTest,WikiFolderControllerTest,WikiInsightControllerTest,WikiMaintenanceControllerTest}.java`

**Interfaces:**
- Consumes: Task 4 到 Task 7 的 Service
- Produces: 4 个控制器映射 `/api/admin/v1/knowledge-bases/{kbId}/wiki`，端点清单见 1.3
- [x] **Step 1: 写失败测试**（MockMvc）
  - `WikiPageControllerTest.listReturnsPagedEnvelope`、`createWithBlankSlugReturns4400`、`getRendersHierarchicalSlug`、`updateStaleVersionReturns409WithCurrentVersion`、`deleteUnknownReturnsWikiPageNotFound`、`otherTenantKbReturnsForbiddenCode`、`viewerCannotWrite`。
  - `WikiFolderControllerTest.createUnderRoot`、`moveIntoDescendantReturnsCycleCode`、`deleteNonEmptyReturnsFolderNotEmpty`。
  - `WikiInsightControllerTest.graphEgoWithoutCenterReturnsParamError`、`searchWithoutQueryReturnsParamError`、`statsCountsMatchSeededRows`。
  - `WikiMaintenanceControllerTest.lintReturnsRulesWithCounts`、`autoFixReturnsFixedCount`、`updateIssueStatusRejectsUnknownStatus`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='WikiPageControllerTest,WikiFolderControllerTest,WikiInsightControllerTest,WikiMaintenanceControllerTest'`。
- [x] **Step 3: 实现**：控制器只做绑定、`@Valid`、调用一个 Service。用 `@SaCheckPermission` 标权限点，见 1.11。`WikiKnowledgeBaseGuard` 用 `KnowledgeBaseQueryApi.isOwned` 与 `find` 判断归属与 wiki 开关，返回 `wiki.kb.*` 错误码。通配 slug 用 `@PathVariable("slug")` 并调用 `WikiSlugNormalizer.normalize`。springdoc 注解齐全。
- [x] **Step 4: 跑绿**，再跑 `MVN test -Dtest=OpenApiExportTest`，然后 `cd frontend/packages/api-client && npm run gen:api`，预期 `schema.d.ts` 出现 wiki 路径。
- [x] **Step 5: 提交**：`git add M/wiki/controller M/wiki/vo M/wiki/assembler T/wiki frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(wiki): 接口层与 VO"`。

### Task 9: wiki 前端 API 层与界面验收

**Files:**
- Modify: `frontend/src/api/wiki/index.ts`
- Modify: `tests/e2e/PAGES.md`

**Interfaces:**
- Consumes: Task 8 的 OpenAPI 产物 `frontend/packages/api-client/src/schema.d.ts`
- Produces: `frontend/src/api/wiki/index.ts` 的 20 个导出函数保持现有签名

- [ ] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep '@wiki'`，在新栈上运行，预期失败，原因是前端仍指向旧路径。
- [ ] **Step 2: 跑红**：同上。
- [ ] **Step 3: 实现**：按 1.12 的表改 `frontend/src/api/wiki/index.ts`。传输用 `createApiClient`，`tenantId` 转 number，`updateWikiPage` 在函数内部把 409 转成旧返回形状 `{currentVersion}`。
- [ ] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit`，预期无错误；再跑 `npx playwright test --grep '@wiki'`，预期全绿；再跑受影响模块 `MVN test -pl app -am`，预期全绿。全量 `MVN verify` 不在本任务跑，留到 Task 18 统一做，避免多批之间互相耦合。
- [ ] **Step 5: 提交**：`git add frontend/src/api/wiki/index.ts tests/e2e/PAGES.md`，`git commit -m "feat(wiki): 前端 API 层切换与界面验收"`。

## 批次 B：datasource

### Task 10: datasource 盘点核对、DDL 与行级安全

**Files:**
- Create: `DB/V<D>_NN__DDL_rebuild_datasource_create_data_sources_jiangguifan.sql`
- Create: `T/datasource/{DataSourceRowLevelSecurityTest,DataSourceConstraintsTest}.java`

**Interfaces:**
- Consumes: 计划 01 Task 5 的 RLS 策略模板
- Produces: `data_sources`、`data_source_credentials`、`sync_logs` 三张表
- [x] **Step 1: 核对盘点**
  - 读 `frozen-bugs.md` 中涉及 datasource 的条目并登记到 `progress.md`。
  - 跑 `find domains/src/main/java/com/ragagent/datasource -name '*.java' | wc -l` 与行数统计，预期 `147` 与 `26356`。
  - 跑 `grep -rcE "@(Get|Post|Put|Delete|Patch)Mapping" domains/src/main/java/com/ragagent/datasource/controller`，两个控制器合计预期 `17`，与盘点 2.2 及 1.3 的 17 行一致。冻结点已由主会话实测为 17，不符以源码为准并改 1.3。
- [x] **Step 2: 写失败测试**
  - `DataSourceRowLevelSecurityTest`：三张表各一对跨租户读与写用例。
  - `DataSourceConstraintsTest.credentialFieldUniquePerDataSource`、`recreateAfterDeleteAccepted`、`syncLogsHaveNoSoftDeleteColumn`。
- [x] **Step 3: 跑红**：`MVN test -Dtest='DataSourceRowLevelSecurityTest,DataSourceConstraintsTest'`。
- [x] **Step 4: 实现**：按 1.4 写 DDL。`data_source_credentials` 唯一 `(tenant_id, data_source_id, field_name)`，`data_source_id` 带 `ON DELETE CASCADE` 需要同库外键，域内允许。`sync_logs` 索引 `(tenant_id, data_source_id, started_at DESC)`。三张表全部开行级安全。
- [x] **Step 5: 跑绿**，反向验证同 Task 1。
- [x] **Step 6: 提交**：`git add DB/V<D>_NN__DDL_rebuild_datasource_create_data_sources_jiangguifan.sql T/datasource`，`git commit -m "feat(datasource): 数据源表与行级安全"`。

### Task 11: datasource Entity、Mapper 与 Repository

**Files:**
- Create: `M/datasource/entity/{DataSource,DataSourceCredential,SyncLog,SyncMode,DataSourceStatus,SyncLogStatus,ConflictStrategy}.java`
- Create: `M/datasource/mapper/{DataSourceMapper,DataSourceCredentialMapper,SyncLogMapper}.java`
- Create: `R/mapper/datasource/{DataSourceMapper,SyncLogMapper}.xml`
- Create: `M/datasource/repository/{DataSourceRepository,DataSourceCredentialRepository,SyncLogRepository}.java`
- Create: `T/datasource/{DataSourceRepositoryTest,DataSourceCredentialRepositoryTest,SyncLogRepositoryTest}.java`

**Interfaces:**
- Produces:
  - `DataSourceRepository extends ServiceImpl<DataSourceMapper, DataSource>`：`List<DataSource> listByKb(long kbId)`、`List<DataSource> listActive()`、`List<DataSource> listAllTenantsActive()`（标 `@IgnoreTenant`）、`Optional<DataSource> findByIdForUpdate(long id)`、`boolean updateLastSync(long id, OffsetDateTime at, SyncCursorBo cursor, SyncResultBo result)`
  - `DataSourceCredentialRepository`：`Map<String,String> loadDecrypted(long dataSourceId)`、`boolean put(long dataSourceId, String field, String value)`、`boolean clear(long dataSourceId, String field)`、`Set<String> configuredFields(long dataSourceId)`
  - `SyncLogRepository`：`SyncLog start(SyncLog log)`、`void finish(long id, SyncResultBo result, SyncLogStatus status, String error)`、`PageData<SyncLog> pageByDataSource(long dataSourceId, int limit, int offset)`、`Optional<SyncLog> findById(long id)`、`long countRunning(long dataSourceId)`、`int purgeOlderThan(long tenantId, OffsetDateTime cutoff)`
- [x] **Step 1: 写失败测试**
  - `DataSourceRepositoryTest.jsonbRoundTrips`、`lastSyncUpdateKeepsOtherColumns`、`listActiveSkipsPausedAndError`、`listAllTenantsActiveSeesAllTenants`。
  - `DataSourceCredentialRepositoryTest.storesCiphertextDifferentFromPlaintext`、`putTwiceUpdatesInPlace`、`clearRemovesRow`、`configuredFieldsListsNamesOnly`。
  - `SyncLogRepositoryTest.pageIsOrderedByStartedAtDesc`、`countRunningCountsOnlyRunning`、`purgeOlderThanDeletesOnlyExpired`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='DataSource*RepositoryTest,SyncLogRepositoryTest'`。
- [x] **Step 3: 实现**：`DataSourceCredential.fieldValue` 标 `@Encrypted`，`@TableName(autoResultMap = true)`。枚举用 MyBatis-Plus `EnumValue` 存小写值。`listAllTenantsActive` 标 `@IgnoreTenant` 并写一行中文注释说明是周期作业的系统通道。跨表 join 只在只读 QueryRepository 里做，本任务没有跨表 join。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/datasource/entity M/datasource/mapper M/datasource/repository R/mapper/datasource T/datasource`，`git commit -m "feat(datasource): 数据源持久层"`。

### Task 12: 连接器 SPI、登记表与错误码

**Files:**
- Create: `M/datasource/api/{DataSourceCode}.java`
- Create: `M/datasource/connector/{SourceConnector,StreamingSourceConnector,ConnectorDescriptor,ConnectorAuthType,ConnectorCapability,CredentialField,SourceConnectorRegistry,ConnectorConfig,SyncCursorBo,SyncResultBo,FetchedItemBo,SourceResourceBo,FetchResultBo,ConnectorHttpClient,ConnectorTypeBo}.java`
- Create: `M/datasource/catalog/PlannedConnectorType.java`
- Create: `M/datasource/connector/PlannedConnectorTypeCatalog.java`
- Modify: `R/i18n/app-messages*.properties`（追加 1.9 的 17 个 datasource 错误码）
- Create: `M/config/DataSourceAssemblyConfig.java`
- Create: `T/datasource/{SourceConnectorRegistryTest,DataSourceCodeMessagesTest,ConnectorHttpClientTest}.java`

**Interfaces:**
- Consumes: 计划 03 Task 13 的 `SsrfGuard` Spring Bean，计划 01 的 `@Encrypted`
- Produces:
  - `interface SourceConnector { ConnectorDescriptor descriptor(); void validate(ConnectorConfig config); List<SourceResourceBo> listResources(ConnectorConfig config, String parentExternalId); List<String> resolveResourceAncestors(ConnectorConfig config, List<String> externalIds); List<FetchedItemBo> fetchAll(ConnectorConfig config, List<String> resourceIds); FetchResultBo fetchIncremental(ConnectorConfig config, SyncCursorBo cursor); }`
  - `interface StreamingSourceConnector extends SourceConnector { SyncCursorBo fetchStream(ConnectorConfig config, SyncCursorBo cursor, StreamSink sink); }`，`interface StreamSink { void emit(FetchedItemBo item); void checkpoint(SyncCursorBo cursor); }`
  - `record ConnectorDescriptor(Set<String> types, String displayNameKey, String descriptionKey, int priority, ConnectorAuthType authType, Set<ConnectorCapability> capabilities, List<CredentialField> credentialFields)`
  - `class SourceConnectorRegistry`：构造器 `(List<SourceConnector> connectors)`，方法 `SourceConnector require(String type)`（未实现时抛 `datasource.type.unsupported`）、`List<ConnectorTypeBo> catalog()`（已实现按 priority 稳定排序，规划项追加在后）
  - `ConnectorHttpClient`：`(SsrfGuard guard, Duration connectTimeout, Duration readTimeout)`，方法 `HttpResponse<String> send(HttpRequest request)`，出网前调 `guard.validateURLForSSRF(uri.toString())`。方法名照旧 `SsrfGuard.validateURLForSSRF(String)`，不新造 `check(URI)`
  - 重定向防护照搬旧 `ConnectorHttp`（`domains/src/main/java/com/ragagent/datasource/domain/connector/ConnectorHttp.java` 第 178 到 220 行）：最多 5 跳，每跳先校验 scheme 只能是 http 或 https，再对解析后的目标 URI 调一次 `validateURLForSSRF`，跨源时剥掉 `Authorization`、`Cookie`、`X-Api-Key` 一类凭据头，301、302、303 与非 GET 或 HEAD 的 307 转 GET 并丢 body
- [x] **Step 1: 写失败测试**
  - `SourceConnectorRegistryTest.registersNineTypesFromSixBeans`、`catalogOrdersImplementedBeforePlanned`、`unknownTypeThrowsUnsupported`、`duplicateTypeAtStartupFails`。
  - `DataSourceCodeMessagesTest.everyCodeHasMessageInSevenLanguages`。
  - `ConnectorHttpClientTest.rejectsPrivateAddress`（断言 `SsrfGuard.validateURLForSSRF` 抛出的异常被包成 `datasource.url.blocked`）、`rejectsRedirectToInternalHost`、`stripsCredentialsOnCrossOriginRedirect`、`capsRedirectsAtFive`、`downgradeToGetOnPostRedirect`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='SourceConnectorRegistryTest,DataSourceCodeMessagesTest,ConnectorHttpClientTest'`。
- [x] **Step 3: 实现**：`SourceConnectorRegistry` 构造器遍历 `List<SourceConnector>`，按 `descriptor().types()` 逐个放入 `Map<String,SourceConnector>`，同一类型出现两次抛 `IllegalStateException` 让启动失败。`PlannedConnectorType` 枚举含 `CONFLUENCE`、`GITHUB`、`GOOGLE_DRIVE`、`ONEDRIVE`、`DINGTALK`、`WEB_CRAWLER`、`SLACK`、`IMAP` 与各自的显示名 key、描述 key、优先级、鉴权方式、能力集合。`CodeCache.register(DataSourceCode.values())` 放 `DataSourceAssemblyConfig`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/datasource/api M/datasource/connector M/datasource/catalog M/config/DataSourceAssemblyConfig.java R/i18n T/datasource`，`git commit -m "feat(datasource): 连接器 SPI、登记表与错误码"`。

### Task 13: 七个连接器类迁入

**Files:**
- Create: `M/datasource/connector/feishu/{FeishuWikiConnector,FeishuWikiTreeWalker,FeishuDriveConnector,FeishuConfig,FeishuClient,FeishuDocxClient,FeishuBitableClient,FeishuRetryPolicy,FeishuResponseCodec,FeishuApiModels,FeishuSyncEngine,DocxBlockParser,DocxBlockMarkdown}.java`
- Create: `M/datasource/connector/notion/{NotionConnector,NotionResourceWalker,NotionClient,NotionPagination,NotionPageFetcher,NotionChildResolver,NotionBlockRenderer,NotionRichText,NotionTableRenderer,NotionApiModels}.java`
- Create: `M/datasource/connector/yuque/{YuqueConnector,YuqueTreeWalker,YuqueClient,YuqueApiModels,YuqueJsonCodec}.java`
- Create: `M/datasource/connector/ima/{ImaConnector,ImaTreeWalker,ImaContentFetcher,ImaClient,ImaAuth,ImaApiModels,ImaJsonCodec}.java`
- Create: `M/datasource/connector/rss/{RssConnector,FeedParser,FeedItemExtractor,RssCursorCodec}.java`
- Create: `M/datasource/connector/gitlab/{GitLabConnector,GitLabTreeWalker,GitLabClient,GitLabPagination}.java`
- Create: `T/datasource/connector/**`（按 1.8 表移植的测试类）
- Modify: `M/config/DataSourceAssemblyConfig.java`（如连接器需要注入 `ConnectorHttpClient`）

**Interfaces:**
- Consumes: Task 12 的 `SourceConnector`、`ConnectorHttpClient`、`ConnectorConfig`
- Produces: 7 个 `@Component` 连接器类，`descriptor().types()` 分别为 `{feishu, lark}`、`{feishu_drive, lark_drive}`、`{notion}`、`{yuque}`、`{ima}`、`{rss}`、`{gitlab}`
- [x] **Step 1: 按旧测试的断言重写测试并跑红**：不复制旧测试文件，旧测试 import 了已删的 `DataSourceConfig`、`ConnectorException`，复制过来编译不过。做法是读旧 `domains/src/test/java/com/ragagent/datasource/connector/**` 的每个断言，在 `T/datasource/connector/**` 里按新类型重写：`ConnectorConfig` 替 `DataSourceConfig`，断言业务结果而不是异常类型，钉 Go 序列化字节形状的断言丢弃。逐类搬迁清单见 1.7。跑 `MVN test -Dtest='com.ragagent.datasource.connector.*Test'`，预期 FAIL，类不存在。
- [x] **Step 2: 实现**：每个连接器沿用旧业务规则，只改四处：实现 `SourceConnector`；`DataSourceConfig` 换成 `ConnectorConfig`；`ConnectorException` 换成 `BusinessRuntimeException` 加 `DataSourceCode`；HTTP 走注入的 `ConnectorHttpClient`。飞书与 Lark 用同一个类加 `FeishuRegion` 参数，云盘模式用独立类。
- [x] **Step 3: 跑绿**：同 Step 1 命令，预期全部 PASS。
- [x] **Step 4: 补一条新用例**：`SourceConnectorRegistryTest` 断言 `catalog()` 有 9 个已实现类型与 8 个规划类型，共 17 项，与旧目录一致。
- [x] **Step 5: 提交**：`git add M/datasource/connector M/config/DataSourceAssemblyConfig.java T/datasource/connector`，`git commit -m "feat(datasource): 七个连接器类迁入窄接口"`。

### Task 14: datasource Service 与条目落库

**Files:**
- Create: `M/datasource/service/{DataSourceService,DataSourceCredentialService,DataSourceSyncService,DataSourceItemWriter,DataSourceSyncResultRecorder,DataSourceAutoTagResolver,DataSourceKnowledgeBaseGuard,DataSourceAssembler}.java`
- Create: `T/datasource/{DataSourceServiceTest,DataSourceCredentialServiceTest,DataSourceItemWriterTest,DataSourceAutoTagResolverTest}.java`

**Interfaces:**
- Consumes: Task 11 的 Repository，Task 12 的 `SourceConnectorRegistry`，计划 04 的 `KnowledgeBaseQueryApi`、`KnowledgeContentApi`、`KnowledgeIngestApi`
- Produces:
  - `DataSourceService`：`DataSourceBo create(CreateDataSourceBo in)`、`DataSourceBo update(long id, UpdateDataSourceBo in)`、`void delete(long id)`、`DataSourceBo get(long id)`、`PageData<DataSourceBo> list(long kbId, int limit, int offset)`、`void validateConnection(long id)`、`void validatePreview(String type, Map<String,String> credentials)`、`List<SourceResourceBo> listResources(long id, String parentId)`、`List<String> resolveAncestors(long id, List<String> externalIds)`
  - `DataSourceCredentialService`：`Map<String, CredentialFieldStateBo> put(long id, Map<String,String> credentials)`、`Map<String, CredentialFieldStateBo> clear(long id, String field)`、`CredentialFieldStateBo state(long id)`
  - `DataSourceSyncService`：`SyncLogBo triggerManual(long id)`、`void pause(long id)`、`void resume(long id)`、`PageData<SyncLogBo> syncLogs(long id, int limit, int offset)`、`SyncLogBo syncLog(long logId)`
  - `DataSourceItemWriter.apply(ConnectorConfig config, long dataSourceId, long syncLogId, FetchedItemBo item, ConflictStrategy strategy, boolean syncDeletions)` 返回 `ItemOutcome`（`CREATED`、`UPDATED`、`SKIPPED`、`FAILED`）
- [x] **Step 1: 写失败测试**
  - `DataSourceServiceTest.createRequiresKbOwnership`（`datasource.kb.forbidden`）、`createRejectsUnsupportedType`、`updateCannotChangeKb`（`datasource.kb.immutable`）、`updateValidatesConnectorBeforeSave`、`createDoesNotCallConnectorInsideTransaction`（用计数桩断言事务提交后才调连接器，见下）、`deleteCascadesCredentialsAndSoftDeletesLogs`。
  - `DataSourceCredentialServiceTest.putEncryptsFieldsAndReturnsConfiguredFlags`、`clearUnknownFieldThrows`（`datasource.credentials.field.unknown`）、`emptyCredentialsThrows`（`datasource.credentials.invalid`）、`responsesNeverContainPlaintext`（断言 JSON 序列化后不含原文）。
  - `DataSourceItemWriterTest.createsWhenAbsent`、`skipsWhenPresentAndStrategySkip`、`updatesMetadataWhenStrategyOverwrite`、`deletesWhenMarkedDeletedAndSyncDeletionsOn`、`itemWithoutContentAndUrlThrows`（`datasource.connector.item.invalid`）。
  - `DataSourceAutoTagResolverTest.delegatesToKnowledgeApi`、`fallsBackToEmptyWhenKbHasNoAutoTags`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='DataSourceServiceTest,DataSourceCredentialServiceTest,DataSourceItemWriterTest,DataSourceAutoTagResolverTest'`。
- [x] **Step 3: 实现**
  - `DataSourceService.create` 与 `update` 的公开方法上不开事务：先做参数与归属校验，再调连接器 `validate`（外部调用），再用 `TransactionTemplate` 包住写库段落。不用私有方法加 `@Transactional`，同一个 bean 内自调用不会走代理。
  - `DataSourceCredentialService` 只读写 `data_source_credentials`，字段校验读 `registry.require(type).descriptor().credentialFields()`。
  - `DataSourceItemWriter` 每次调用是一个独立事务，写入经 `KnowledgeContentApi.createFromExternalRef` 一次落库（外部 ID 与元数据随创建写入，不留重复窗口），更新走 `updateMetadata`，删除走 `softDelete`。`createFromExternalRef` 是计划 04 的 `KnowledgeContentApi` 新增方法，见本文「对前置计划的新增需求」。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/datasource/service T/datasource`，`git commit -m "feat(datasource): 数据源用例与条目落库"`。

### Task 15: 同步作业与调度

**Files:**
- Create: `M/datasource/job/{DataSourceSyncJob,DataSourceSyncPipeline,DataSourceScheduleJob,SyncLogRetentionJob,DataSourceSyncEnqueuer}.java`
- Create: `T/datasource/job/{DataSourceSyncJobTest,DataSourceSyncPipelineTest,DataSourceScheduleJobTest,SyncLogRetentionJobTest}.java`

**Interfaces:**
- Consumes: 计划 04 的 `TenantJobEnqueuer`、`JobScheduler`、`RagAgentRole`；Task 14 的 `DataSourceItemWriter`
- Produces:
  - `DataSourceSyncEnqueuer.enqueue(long tenantId, long dataSourceId, long syncLogId, boolean forceFull)`：`jobId = "ds-sync:" + tenantId + ":" + dataSourceId + ":" + syncLogId`，每次同步一条新 `sync_logs` 行就带一个新后缀，避免同一数据源的下一次同步被已完成作业的 ID 静默吞掉；`sync_logs` 的 `(tenantId, dataSourceId, status='RUNNING')` 唯一约束才是真正的并发闸门，`countRunning` 非零时不入队
  - `DataSourceSyncJob implements TenantScopedJob`：`long tenantId()` 与 `void run()`，`dataSourceId`、`syncLogId`、`forceFull` 存构造器字段。不写 `run(Long, Long, Long, boolean)` 四参形式
  - `DataSourceScheduleJob.reconcile()` 与 `SyncLogRetentionJob.purgeExpired()` 都 `implements TenantScopedJob`，构造时 `tenantId` 传 `0L`，作业体内用 `DataSourceRepository.listAllTenantsActive()` 枚举租户，各自在 Task 15 内注册周期作业（`scheduleRecurrent(0L, "ds-cron-reconcile", "0 */10 * * * *", job)`、`scheduleRecurrent(0L, "sync-log-retention", "0 30 3 * * *", job)`），不依赖计划 04 的 `RecurrentJobRegistration`
  - `SyncLogRetentionJob.purgeExpired()`：周期作业，按租户清理超期日志
- [x] **Step 1: 写失败测试**
  - `DataSourceSyncJobTest.missingTenantFailsWithoutRetry`、`marksSyncLogFailedOnFatalError`、`marksPartialWhenSomeResourcesFail`、`continuesAfterSingleItemFailure`、`updatesLastSyncCursorAfterEachCheckpoint`、`runningSyncRejectsSecondTrigger`。
  - `DataSourceSyncPipelineTest.fetchesInBatchesThenWritesOutsideTransaction`（用事务同步回调断言 writer 调用不发生在对外调用内）、`fullModeIgnoresCursor`、`incrementalModeUsesCursor`。
  - `DataSourceScheduleJobTest.createsCronJobPerActiveDataSource`、`deletesCronWhenPaused`、`recreatesWhenScheduleChanged`。
  - `SyncLogRetentionJobTest.purgesOnlyLogsOlderThanRetention`、`keepsRunningLogs`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='DataSourceSyncJobTest,DataSourceSyncPipelineTest,DataSourceScheduleJobTest,SyncLogRetentionJobTest'`。
- [x] **Step 3: 实现**
  - `DataSourceSyncJob` 实现 `TenantScopedJob`，`tenantId()` 返回构造时字段值，由 `TenantJobFilter` 设置上下文，缺失时抛 `IllegalArgumentException` 并使 JobRunr 不重试（`RetryPolicy` 在入队处指定）。
  - `DataSourceSyncPipeline` 对实现 `StreamingSourceConnector` 的类型走 `fetchStream`，其余走 `fetchAll` 或 `fetchIncremental`。每批最多 50 条或 30 秒落一次检查点，写库逐条开事务。
  - `DataSourceScheduleJob` 与 `SyncLogRetentionJob` 的公开方法标 `@IgnoreTenant`，方法内先查租户列表再逐租户设置上下文，注释写明原因。
  - 作业入队经 `TenantJobEnqueuer`，不直接用 `JobScheduler`。
- [x] **Step 4: 跑绿**。
- [x] **Step 5: 提交**：`git add M/datasource/job T/datasource/job`，`git commit -m "feat(datasource): 同步作业与调度迁入 JobRunr"`。

### Task 16: datasource Controller、VO 与 Assembler

**Files:**
- Create: `M/datasource/controller/{DataSourceController,DataSourceCredentialController,DataSourceResourceController,DataSourceSyncLogController}.java`
- Create: `M/datasource/vo/{DataSourceVo,DataSourceSummaryVo,CreateDataSourceReqVo,UpdateDataSourceReqVo,ConnectorTypeVo,CredentialStateVo,UpdateCredentialsReqVo,ValidateCredentialsReqVo,SourceResourceVo,SyncLogVo,SyncLogSummaryVo,SyncResultVo}.java`
- Create: `M/datasource/assembler/DataSourceAssembler.java`
- Create: `T/datasource/{DataSourceControllerTest,DataSourceCredentialControllerTest,DataSourceSyncLogControllerTest}.java`

**Interfaces:**
- Produces: 4 个控制器，端点见 1.3
- [x] **Step 1: 写失败测试**（MockMvc）
  - `DataSourceControllerTest.listTypesIncludesNineImplementedAndEightPlanned`、`createWithBlankNameReturns4400`、`createWithUnknownTypeReturnsUnsupported`、`getNeverReturnsCredentialPlaintext`、`updateChangingKbReturnsImmutableCode`、`validateConnectionOnPausedReturnsInactive`、`credentialPreviewDoesNotPersist`、`syncOnPausedReturnsRunningCode`、`limitOutOfRangeReturnsParamError`。
  - `DataSourceCredentialControllerTest.putReturnsConfiguredFlags`、`clearUnknownFieldReturnsUnknownFieldCode`、`deleteLastFieldLeavesEmptyState`。
  - `DataSourceSyncLogControllerTest.listReturnsPagedEnvelope`、`detailOfOtherTenantReturnsNotFound`。
- [x] **Step 2: 跑红**：`MVN test -Dtest='DataSourceControllerTest,DataSourceCredentialControllerTest,DataSourceSyncLogControllerTest'`。
- [x] **Step 3: 实现**：控制器只做绑定、`@Valid`、调用一个 Service。凭据字段名与 `descriptor().credentialFields()` 的比对放在 Service，控制器不重复。`@SaCheckPermission` 标 1.11 的权限点。`DataSourceAssembler` 用 MapStruct，凭据只映射成 `configured` 布尔。
- [x] **Step 4: 跑绿**，再跑 `MVN test -Dtest=OpenApiExportTest` 与 `cd frontend/packages/api-client && npm run gen:api`。
- [x] **Step 5: 提交**：`git add M/datasource/controller M/datasource/vo M/datasource/assembler T/datasource frontend/packages/api-client/src/schema.d.ts`，`git commit -m "feat(datasource): 接口层与 VO"`。

### Task 17: datasource 前端 API 层与界面验收

**Files:**
- Modify: `frontend/src/api/datasource/index.ts`
- Modify: `tests/e2e/PAGES.md`

**Interfaces:**
- Consumes: Task 16 的 OpenAPI 产物 `frontend/packages/api-client/src/schema.d.ts`
- Produces: `frontend/src/api/datasource/index.ts` 的 16 个导出函数保持现有签名

- [ ] **Step 1: 写失败测试**：`cd tests/e2e && npx playwright test --grep '@datasource'`，预期失败。
- [ ] **Step 2: 跑红**：同上。
- [ ] **Step 3: 实现**：按 1.12 的表改 `frontend/src/api/datasource/index.ts`。`getSyncLogs` 内部把 `PageData` 转回 `{items,total}`；`deleteDataSourceCredentials` 的 field 参数从写死的 `credentials` 改为调用方传入，函数签名保持 `(id: string)`，内部固定传 `credentials` 以匹配旧行为，多一个可选参数 `field = 'credentials'`。
- [ ] **Step 4: 跑绿**：`cd frontend && npx vue-tsc --noEmit`，再跑 `npx playwright test --grep '@datasource'`，再跑受影响模块 `MVN test -pl app -am`。全量 `MVN verify` 留到 Task 18。
- [ ] **Step 5: 提交**：`git add frontend/src/api/datasource/index.ts tests/e2e/PAGES.md`，`git commit -m "feat(datasource): 前端 API 层切换与界面验收"`。

## 批次 C：收尾

### Task 18: 交接文档、架构规则与全量验证

**Files:**
- Create: `docs/migration/handoff-wiki-datasource.md`
- Modify: `backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（如需追加 `@IgnoreTenant` 白名单包）

**Interfaces:**
- Consumes: Task 1 到 Task 17 的全部产物
- Produces: `handoff-wiki-datasource.md`，含三张表：旧队列到 JobRunr 作业的一行一映射、旧 Redis 键到新机制的映射、跨域依赖方向表
- [x] **Step 1: 写交接文档**：抄 1.5 的内容，另加两列「新作业类」「jobId 规则」。跨域表抄 1.6，另加「消费方计划编号」。
- [x] **Step 2: 架构规则**：跑 `MVN test -Dtest='ArchitectureRulesTest,ModuleBoundaryTest,CodeQualityRulesTest'`，预期通过，三个类都在 `backend/app/src/test/java/com/ragagent/arch/` 下。`@IgnoreTenant` 白名单若缺本计划三个类，追加到 `ArchitectureRulesTest` 的白名单并登记 `progress.md`；`ModuleBoundaryTest` 与 `CodeQualityRulesTest` 只跑不改。若 ArchUnit 报 wiki 与知识域互相引用，检查是否还有旧 `contract.wiki` 或 `knowledge.api` 的双向 import，按偏离登记。
- [x] **Step 3: 全量验证**：`MVN verify`，预期全绿；`cd frontend && npx vue-tsc --noEmit`，预期无错误。
- [x] **Step 4: 提交**：`git add docs/migration/handoff-wiki-datasource.md backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`，`git commit -m "docs(migration): wiki 与 datasource 交接文档与架构规则"`。

# 3. 完成判据

路线图第 2 节对 05 号计划的闸门是「两域相关页面转绿」。逐条对应：

1. `MVN verify` 全绿，含 Modulith、ArchUnit、Checkstyle。输出贴进 `progress.md`。
2. `cd tests/e2e && npx playwright test --grep '@wiki'` 全部通过；`--grep '@datasource'` 全部通过。
3. `tests/api-smoke` 里 `wiki-page-create`、`wiki-revision-revert`、`datasource-sync-manual` 三条去掉 `test.fixme()` 后转绿。
4. `MVN test -Dtest=WikiRowLevelSecurityTest` 与 `-Dtest=DataSourceRowLevelSecurityTest` 全绿，反向验证（关闭行级安全）时各变红一次并已恢复。
5. `MVN test -Dtest=WikiCodeMessagesTest,DataSourceCodeMessagesTest` 全绿，19 加 17 个错误码七语种齐全。
6. `git grep -n "InProcessWikiIngestTaskQueue\|RedisWikiIngestTaskQueue\|RedisWikiSlugLock\|RedisDataSourceSyncTaskQueue\|InProcessDataSourceSyncTaskQueue" backend/` 无输出。
7. `git grep -n "rawBody\|Map<String, Object>" backend/app/src/main/java/com/ragagent/wiki backend/app/src/main/java/com/ragagent/datasource` 无输出。
8. `git grep -n "storePassword\|@TableField(\"field_value\")" backend/app/src/main/java/com/ragagent/datasource` 只命中 `@Encrypted` 标注的一处。
9. `docs/migration/handoff-wiki-datasource.md` 存在，三张表齐全。
10. `docs/migration/progress.md` 有 18 行本计划的任务记录，`docs/migration/deviations.md` 登记了所有偏离，`progress.md` 的 `IgnoreTenant 白名单` 有本计划三行。
