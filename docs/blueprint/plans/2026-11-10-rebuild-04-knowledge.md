# RagAgent 重建：knowledge 域迁入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `domains/.../knowledge`（283 文件、30590 行）按新规范重写进 `backend/app`，同时落地三件首次出现的基础设施：JobRunr 开源版队列、`ragagent.role` 部署角色开关、Spring Modulith 事件发布登记表，并交付路线图第 6 节里 knowledge 作为实现方的四个 ReferenceProvider 与 retrieval-engine 的 `spi.KnowledgeSearchSource`。

**Architecture:** 按聚合根分三批迁入：`knowledge_bases`（含标签与置顶）、`knowledges`（含处理 span 与任务进度）、`chunks`（含修订与推荐问题）。文档入库主链路改成 JobRunr 作业，阶段划分与 `knowledges.parse_status` 状态机保持旧行为，界面仍显示同一套解析状态。旧 7 套自研队列里属于文档入库的三套由 JobRunr 取代，其余四套在 1.4 表里标明归属计划。JobRunr 作业仓储、租户上下文过滤器与角色开关放 `com.ragagent.config`。

**Tech Stack:** Spring Boot 3.5.16、Spring Modulith 1.4、MyBatis-Plus 3.5.x、Flyway、JobRunr 7.x（开源版，存储在 PostgreSQL）、grpc-java 直接使用（不引入 starter）、Jackson 2、MapStruct、Testcontainers PostgreSQL、Playwright。

**规格来源**: `docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图：`docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 04 号计划，第 3 节约定全部继承。

## Global Constraints

继承路线图第 3 节。本计划最常用的几条：

- JDK 21、Spring Boot 3.5.16。Java 缩进 tab、行宽 140、中文 Javadoc、`@author jiangguifan`、魔法字符串收口常量或枚举。
- 错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号 knowledge 为 `010`，枚举 `KnowledgeCode` 放 `com.ragagent.knowledge.api`，启动时 `CodeCache.register`。文案 key `code_<错误码>`，放 `backend/app/src/main/resources/i18n/app-messages*.properties`，七语种加默认文件。
- Flyway 脚本放 `backend/app/src/main/resources/db/migration/allenv/`，命名 `V{yyyyMMdd}_{两位序号}__{DDL|DML}_rebuild_knowledge_{描述}_jiangguifan.sql`，版本与描述之间是双下划线。JobRunr 与 Modulith 自建的表不进本域脚本。
- 主键 bigint 雪花 ID，对外序列化为字符串。租户表 `tenant_id bigint NOT NULL` 加行级安全，策略用计划 01 Task 5 的 fail-closed 模板。软删 `deleted_at timestamptz NOT NULL DEFAULT '1970-01-01 00:00:00+00'`，唯一索引含 `deleted_at`。
- 路径三组：`/api/admin/v1`、`/api/v1`、`/api/open/v1`。知识库与文档管理全部是后台功能，只挂 `/api/admin/v1`。请求体 VO 加 `@Valid`，响应 `RestResponse`，ID 为字符串。
- 代码质量按路线图 3.11：方法 40 行、类 400 行、参数 5 个、圈复杂度 10、嵌套 3 层、构造器依赖 7 个，Checkstyle 拦截。类名不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common`。单实现不建接口。构造器注入用 `@RequiredArgsConstructor`。
- 事务内不做外部调用。解析、Embedding、向量库、模型、OCR、ASR 都算外部调用。提交后动作用 `@TransactionalEventListener(AFTER_COMMIT)`，可靠投递用 Modulith 事件发布登记表。
- 下文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`，`-pl` 必带 `-am`。命令前确认 `mvn -v` 显示 Java 21。
- `git add` 只写显式路径。每个任务一次提交，提交后在 `docs/migration/progress.md` 追加一行。与计划不一致的决定写 `docs/migration/deviations.md`。
- 旧代码只读。旧系统副本在 `../ragagent-legacy`（计划 01 Task 0），e2e 与回归对比都要用它。

## Review Focus

五类最容易出错的地方，每条都有任务里的测试断言。

1. **入库任务丢租户。** JobRunr 作业没有 HTTP 请求，租户只能来自任务参数。期望：参数缺租户时作业立刻失败且不重试，不出现查全库。归属 Task 2、Task 10。
2. **入队与业务写库不同事务。** 上传接口插入 `knowledges` 行与投递入库作业必须同一事务。期望：事务回滚时作业不存在，提交后作业可见。归属 Task 2、Task 10。
3. **解析状态机漂移。** 界面按 `knowledges.parse_status` 显示进度，取值集合与流转顺序不能变。期望：pending 到 processing 走 CAS，重解析用 `parse_attempt` 让旧尝试失效，finalizing 等富化子任务归零。归属 Task 10、Task 12。
4. **检索引擎隔离只靠应用层。** Elasticsearch 与 Milvus 不受行级安全保护。期望：跨租户 `kbId` 检索返回归属错误或不返回另一租户数据。归属 Task 14。
5. **签名 URL 与媒体预览回归。** 冻结前四个提交做了视频签名 URL 与播放器修复，容易只搬后端接口不搬前端适配。期望：视频直接走签名 URL，封面与分块视图一致。归属 Task 16、Task 17。

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 | framework 的 `RestResponse`、`BusinessRuntimeException`、`CodeCache`、`@IgnoreTenant`、`@Encrypted`、`TenantAwareJdbcTemplate`、`PostgresContainerTest`、`OpenApiExportTest`；`@ragagent/api-client`；`tests/e2e` 的 `PAGES.md` 与 `[knowledge]` 用例；`tests/api-smoke/tests/knowledge-flow.spec.ts`；`tests/regression` 的 `compare.mjs` 与 `baseline.json` |
| 计划 02 | retrieval-engine 的检索与向量写入入口，`spi.KnowledgeSearchSource` 声明。名字与签名以计划 02 的 `api` 与 `spi` 为准；model-runtime 的 `EmbeddingPort`、`LlmChatClient`、`AsrTranscriber` |
| 计划 03 | `model.api` 模型查询与 `ModelReferenceProvider` 声明；`vectorstore.api` 的 `VectorStoreReferenceProvider` 声明；`storage.api` 的 `StorageReferenceProvider` 声明、文件读写与签名 URL；`tenant.api` 默认存储后端；`auth.api` 当前主体与权限点；`audit.api` 审计写入；`system.api` 解析引擎选项；`docs/migration/golden-sample.md` |
| 计划 09 | `services/docreader/proto/docreader.proto` 作为唯一来源。本计划先按过渡做法从旧 `docreader/` 目录生成 |

计划 02 与 03 的接口名尚未落盘，本文一律写「以计划 02 / 03 的 api 为准」，不猜内部方法。本文需要而它们没声明的写在第 3 节。

### 交付

1. `knowledge` 域 9 张表、`api` 包、Service、Controller、前端 API 层。
2. JobRunr 接入：`com.ragagent.config` 下的 `JobRunrConfig`、`TenantJobFilter`、`TenantJobEnqueuer`、`JobRunrDashboardConfig`，角色开关 `RagAgentRole`、`RoleGatedWebConfig`。计划 05、06、07、08 直接复用。
3. `knowledge.api` 五个 api：`KnowledgeBaseQueryApi`、`KnowledgeContentApi`、`KnowledgeQuestionApi`、`KnowledgeIngestApi`、`KnowledgeSearchApi`。
4. 四个 ReferenceProvider 实现与 retrieval-engine `spi.KnowledgeSearchSource` 实现。
5. `docs/migration/knowledge-job-map.md`：旧后台任务到 JobRunr 作业的一行一映射，供计划 05、06、08 抄。

### 对外承诺

**`knowledge.api` 的 api**，包 `com.ragagent.knowledge.api`。租户来自调用方当前上下文，不作为参数传入；跨租户系统通道见 1.9。

| 接口 | 方法 | 消费方 |
|---|---|---|
| `KnowledgeBaseQueryApi` | `boolean isOwned(long kbId)`；`boolean isInTenant(long tenantId, long kbId)`；`Optional<KnowledgeBaseBo> find(long kbId)`；`List<Long> filterExistingKbIds(List<Long> kbIds)`；`Map<Long, KbIndexingCapabilityBo> indexingCapabilities(List<Long> kbIds)`；`List<Long> listMoveTargets(long kbId)`；`List<Long> resolveAutoTagIds(long kbId, String dataSourceType)`；`long provisionChatHistoryKnowledgeBase(String embeddingModelId)` | storage、model、vectorstore、agent、session、datasource、tenant |
| `KnowledgeContentApi` | 读：`findDocument(long knowledgeId)`、`filterExistingDocumentIds(List<Long>)`、`findByExternalRefs(long kbId, long dataSourceId, List<String>)`、`findByExternalRefPrefix(long kbId, long dataSourceId, String prefix)`、`listTextChunks(long knowledgeId)`、`findChunks(List<Long> chunkIds)`；写：`createFromFile(long kbId, FileUploadBo)`、`createFromUrl(long kbId, UrlImportBo)`、`createManual(long kbId, ManualDocumentBo)`、`createFromExternalRef(long kbId, ExternalRefDocumentBo)`、`updateMetadata(long knowledgeId, Map<String,String>)`、`enrichImageInfo(String content, List<Long> chunkIds)`、`deleteChunk(long knowledgeId, long chunkId)`、`softDelete(long knowledgeId)`；文件：`readFile(long knowledgeId)`、`signedFileUrl(long knowledgeId, Duration ttl)` | datasource、storage、wiki、session/attachment、channel |
| `KnowledgeQuestionApi` | `filterExistingKbIds`；`List<Long> resolveKnowledgeIdsByTags(long kbId, List<Long> tagIds)`；`List<String> listFaqQuestions(KnowledgeScopeBo scope, int limit)`；`List<String> listGeneratedQuestions(KnowledgeScopeBo scope, int limit)` | agent（计划 06 Task 8） |
| `KnowledgeIngestApi` | `void enqueueIngestion(long knowledgeId)`；`FinalizeSubtaskBo finalizeSubtask(long knowledgeId)`；`void reclaimTenantKnowledge(long tenantId, long kbId)`；`String contentHash(byte[] content)`；`String fileNameFromUrl(String url)` | datasource、wiki、websearch、session/attachment、channel |
| `KnowledgeSearchApi` | `SearchResultPageBo hybridSearch(HybridSearchQueryBo query)`；`GrepResultBo grep(GrepQueryBo query)`；`Map<Long, Integer> countEnabledChunks(List<Long> knowledgeIds)` | session、agenttools、wiki |

`record KnowledgeScopeBo(List<Long> kbIds, List<Long> knowledgeIds, List<Long> tagIds)`，三组可空，空表示不限。`record KbIndexingCapabilityBo(long kbId, boolean vectorEnabled, boolean keywordEnabled)`。`record HybridSearchQueryBo(long kbId, String queryText, float[] queryEmbedding, Boolean disableKeywordsMatch, Boolean disableVectorMatch, List<Long> knowledgeIds, List<Long> tagIds, Integer topK)`。`record GrepQueryBo(List<Long> kbIds, List<Long> knowledgeIds, List<Long> tagIds, List<String> includePatterns, List<String> excludePatterns, int limit)`。`record ChunkTextBo(long id, String content, String chunkType, int chunkIndex, Integer startAt, Integer endAt)`。`record FinalizeSubtaskBo(boolean decremented, boolean promoted)`。`record KnowledgeDocumentBo` 含 `id`、`knowledgeBaseId`、`title`、`lang`、`parseStatus`、`metadata`。`KnowledgeBaseQueryApi` 另加 `long provisionChatHistoryKnowledgeBase(String embeddingModelId)`：旧 `KnowledgeBaseService.provisionChatHistoryKnowledgeBase` 被 tenant 域的 `TenantConfigOps.putChatHistory` 调用（`TenantConfigOps.java:272`），隐藏库的实体语义归本域，调用方只传模型 ID、只消费 kb ID。返回的 kb 行 `is_temporary=true`、`name="__chat_history__"`、`type="document"`。`record ExternalRefDocumentBo(String title, String sourceUrl, String content, String externalId, long dataSourceId, Map<String,String> metadata)`，供 datasource 建条目时一次写入外部 ID 与元数据，避免先建后 `updateMetadata` 之间出现 `findByExternalRefs` 查不到的重复窗口。`findByExternalRefs` 依赖 `knowledges.metadata->>'external_id'` 上的表达式索引（见 1.3 表），`dataSourceId` 存 `metadata->>'data_source_id'`。渠道附件入库用 `createFromFile`，返回的知识 ID 与 `PENDING` 状态即「已受理」。

**ReferenceProvider 与 retrieval spi 实现**（声明方在计划 02 与 03，签名以它们的 `api` 与 `spi` 为准）：

| 实现类 | 实现的声明与方法 | 数据来源 |
|---|---|---|
| `KnowledgeModelReferenceProvider` | `model.api.ModelReferenceProvider`：`List<ResourceReferenceBo> references(long modelId)` | `knowledge_bases` 的 `embedding_model_id`、`summary_model_id`、`image_processing_config.modelId`、`vlm_config.modelId`、`asr_config.modelId`、`wiki_config.synthesisModelId`，字段集与旧 `ModelService.kbBindings` 一致 |
| `KnowledgeVectorStoreReferenceProvider` | `vectorstore.api.VectorStoreReferenceProvider`：`List<ResourceReferenceBo> references(long storeId)` | `knowledge_bases.vector_store_id` |
| `KnowledgeStorageReferenceProvider` | `storage.api.StorageReferenceProvider`：`List<ResourceReferenceBo> references(long storageBackendId)` | `knowledge_bases.storage_backend_id` |
| `KnowledgeResourceReferenceProvider` | `storage.api.ResourceReferenceProvider`：`Set<Long> knowledgeIdsOf(String resourceUrl)` | `storage_resource_bindings` 绑定行 join `knowledges`、`knowledge_bases`，旧 `ResourceRepository` 的知识归属两段 SQL |
| `KnowledgeSearchFactsSource` | `retrieval.spi.KnowledgeSearchSource`：`KnowledgeBaseFacts knowledgeBase(String kbId)`；`List<KnowledgeDocumentFacts> accessibleDocuments(long tenantId, List<String> knowledgeIds)`；`List<ChunkFacts> chunks(long tenantId, List<String> chunkIds)` | 旧 `KnowledgeBaseSearchGateway`、`KnowledgeDocumentGateway`、`ChunkSearchGateway` 三个实现合并。`knowledgeBase` 同时是 ES 与 Milvus 的归属校验入口：kb 不存在、已软删、`tenant_id` 与当前租户不符都返回 null |

`ResourceReferenceBo` 从 `storage.api` 引用，不在 knowledge 域重复定义。`KnowledgeBaseFacts(id,tenantId,type,embeddingModelId,vectorStoreId,vectorEnabled,keywordEnabled)`、`KnowledgeDocumentFacts(id,title,metadata,fileName,source,channel,description,knowledgeBaseId,tenantId,fileType,filePath)`、`ChunkFacts(id,knowledgeId,knowledgeBaseId,content,chunkType,indexStatus,enabled,chunkIndex,startAt,endAt,contentRevision,parentChunkId,preChunkId,nextChunkId,relationChunks,metadata,imageInfo)` 三个 record 由计划 02 在 `retrieval.spi` 定义（字段沿用旧 `contract.knowledge` 三个同名 record），本计划只做查询与装配。四个 Provider 的实现类放 `com.ragagent.knowledge.api`，因为声明的消费方是同级域或引擎，Modulith 只放行 `api` 包的 Bean。

**对路线图第 6 节的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| knowledge（04） | `KnowledgeBaseQueryApi`、`KnowledgeContentApi`、`KnowledgeIngestApi`、`KnowledgeSearchApi` | 归属判定、按 ID 过滤、写入入口、grep 检索、分块读写 | 本域 |
| knowledge（04） | `KnowledgeQuestionApi` | 智能体推荐问题数据源 | 本域 |
| retrieval-engine（02） | `spi.KnowledgeSearchSource` | KB 元数据、文档可达性、分块回填，ES 与 Milvus 归属校验 | `KnowledgeSearchFactsSource`（本域） |
| config（04） | `org.jobrunr.scheduling.JobScheduler` 与 `TenantJobEnqueuer` | 后台作业入队与租户参数 | 计划 05、06、08 使用 |

## 本计划新做的设计决定（待用户核对）

只列 spec 与路线图没有定、本计划新定的决定，按重要度排序。

### 一、新表结构与聚合根划分

1. **`knowledge_tag_relations` 与 `knowledge_processing_spans` 补 `tenant_id` 并开行级安全。** 决定：两张旧表没有租户列，新表加 `tenant_id bigint NOT NULL` 并套 fail-closed 模板。理由：路线图 3.5 要求所有租户表带行级安全，靠 join 追溯租户等于把隔离交给每条 SQL。否决：保持无租户列，在 Repository 里 join 校验。
2. **新增 `knowledge_task_progress` 表承载进度轮询与导入互斥。** 决定：move、clone、FAQ 导入三类任务写这张表，字段 `task_id`、`task_type`、`subject_id`、`status`、`progress jsonb`、`expires_at`，部分唯一索引保证同一知识库同时只有一个活动导入。理由：HTTP 轮询不应依赖 JobRunr 的作业元数据形状，旧三套进度 Store 合并成一张表。否决：直接读 `jobrunr_jobs` 拼进度。
3. **入库按主链加三个富化作业拆分，不按旧类拆。** 决定：`DocumentIngestionJob`（每文档每 attempt 一个）、`ChunkExtractJob`（每文档）、`QuestionGenerationJob`（每 20 个 chunk）、`KnowledgeSummaryJob`（每文档）。`jobId` 用确定性字符串，重复投递由 JobRunr 去重。理由：主链失败即 failed，富化失败只释放槽位，处置不同才能各自重试。否决：一个作业跑全链路，整条重来。
4. **`chunks.video_info` 新表不保留。** 决定：基线有该列，本域 Java 无写入点无读取点（grep 只有 DDL 与测试建表命中），新表删掉。否决：保留空列，把「谁填」留给下一个人。
5. **FAQ 条目继续是 `chunk_type='faq'` 的 chunks 行。** 决定：不建独立 `faq_entries` 表，metadata 编解码沿用旧结构。理由：FAQ 条目与分块共享索引、标签、检索路径，拆表要写双份索引代码。否决：独立表，检索时 union。

### 二、新接口路径与 VO 形状

1. **hybrid-search 只保留 POST。** 决定：`POST /api/admin/v1/knowledge-bases/{id}/hybrid-search`，请求体 camelCase VO，响应包在 `RestResponse` 里。删除旧 GET 双路由。理由：新前端统一走 `@ragagent/api-client`，GET 带 body 是兼容旧客户端的临时做法。否决：两个方法同义，OpenAPI 多一条路径。
2. **资源名改复数。** 决定：`/knowledge-bases`、`/knowledge-bases/{id}/documents`、`/knowledge-items/{id}`、`/knowledge-items/{knowledgeId}/chunks`、`/knowledge-bases/{id}/faq-entries`。旧 `/knowledge/{id}` 与 `/chunk` 单数全部改名。理由：旧路径同时有 `/knowledge/{id}` 与 `/knowledge-bases/{id}`，读接口的人要记住资源归属。
3. **`/knowledge/{id}/signed-url` 不提供。** 决定：前端 `getSignedFileUrlByKnowledge` 是死调用，后端从来没有这条路由（只有 `/knowledge-bases/{id}/files/signed-url`）。新 API 提供 `POST /knowledge-bases/{id}/files/signed-url`（body `{filePath}`）与 `POST /knowledge-items/{id}/signed-url`。前端 API 层删除 `getSignedFileUrlByKnowledge` 前先确认无页面引用，当前 grep 只有定义与 `signedMedia.ts` 的可选注入。否决：补一条后端路由迁就死函数。
4. **FAQ 导入进度归知识库子资源。** 决定：`GET /knowledge-bases/{id}/faq-imports/{taskId}`，替代 `/faq/import/progress/{taskId}`。理由：路径表达归属，越权判定不必解析任务 ID 里的租户。

### 三、Provider 与 spi 的签名

1. **JobRunr 租户参数契约。** 决定：租户相关作业在 JobRunr 标签里带 `tenant:<id>`，`TenantJobFilter` 在 `onProcessing` 读取并设置上下文，缺失时把重试次数置零并抛异常。`TenantJobEnqueuer` 入队方法强制非空 `tenantId`。理由：spec 5.9 要求 worker 执行前按任务参数设置租户。否决：作业体内各自读租户，漏一处就是扫全库的作业。
2. **`TenantJobEnqueuer` 不建接口。** 决定：四个方法直接放类上，签名见 2.2。计划 05、06、08 注入这个类。理由：只有一个实现，符合「单实现不建接口」。否决：`JobEnqueuer` 接口加 Impl。
3. **文档解析器与分块器用窄接口加 Spring 登记表。** 决定：`DocumentParser`（docreader、URL、手工正文、音视频四类实现）与 `ChunkingTier`（HEADING、HEURISTIC、LEGACY 三个实现）各一个窄接口，实现注册 Bean，使用方按 `supports` 或类型取。理由：这是真实变化点，旧 `KnowledgeTextExtractor` 已有四条分支，新增途径只加一个类。否决：一个 if 链，第五个分支出现时方法超 40 行。
4. **不另建归属校验类，`KnowledgeSearchSource.knowledgeBase` 兼作归属校验。** 决定：删掉原稿的 `KnowledgeBaseOwnershipResolver`，ES 与 Milvus 的归属判定统一走 `KnowledgeSearchFactsSource.knowledgeBase(kbId)`，返回 null 即无权。理由：计划 02 的 spi 已给出 kb 事实，另建一个只查 `(tenant_id, deleted_at)` 的类等于把同一次判定写两遍，且引擎侧还得决定信哪一个。否决：保留两个类，spi 实现委托 resolver。

### 四、与旧行为不一致的地方

1. **主处理不再依赖上传落到哪个副本。** 旧主处理是进程内虚拟线程，重启丢在飞任务。新实现作业写库，任何 worker 都能取。界面解析状态不变。
2. **重解析增加作业取消。** 保留 `parse_attempt` 自增让旧尝试 CAS 失效，另加 `jobScheduler.delete(jobId)`；取消失败不阻塞，CAS 兜底。
3. **`chunks.video_info` 删除**，见决定一第 4 条。
4. **FAQ 导入互斥从 Redis 锁换成数据库部分唯一索引。** 理由：JobRunr 已把任务持久化，再加 Redis 锁会出现两套事实。

# 1. 盘点（执行方核对，不重新盘点）

数据取自冻结点工作区，Task 1 抽查。旧主代码 283 文件 30590 行，测试 41 文件 5426 行，复核一致。

## 1.1 规模与目标包

| 目标包 | 文件数 | 行数 | 职责 |
|---|---:|---:|---|
| `knowledge/api` | 20 | 1100 | 五个 api、BO、`KnowledgeCode`、Provider 与 spi 实现 |
| `knowledge/controller` | 6 | 900 | 知识库、文档、分块、FAQ、标签、搜索 |
| `knowledge/service/base` | 6 | 1150 | 知识库用例、复制、回收、标签、置顶 |
| `knowledge/service/document` | 8 | 1600 | 导入、重解析、批量、文件夹、查询、摘要、move/clone |
| `knowledge/service/ingest` | 9 | 1700 | 入库作业、阶段、span、富化作业、周期清扫 |
| `knowledge/service/chunk` | 5 | 900 | 分块编辑、查询、推荐问题、分块算法 |
| `knowledge/service/faq` | 8 | 1400 | FAQ 导入、校验、条目、批量字段、编码 |
| `knowledge/service/access`、`client` | 5 | 800 | 访问守卫、docreader 客户端 |
| `knowledge/repository`、`entity`、`mapper`、`vo`、`assembler` | 60 | 3500 | 按聚合根划分、出入参与转换 |

超出 3.11 上限的旧类及拆分归宿：

| 旧类 | 行 | 拆成 |
|---|---:|---|
| `domain/span/SpanTracker` | 678 | `IngestSpanRecorder` ≤250、`IngestSpanTree` ≤200、`SpanHeartbeat` ≤120 |
| `service/enrichment/KnowledgeSummaryService` | 654 | `DocumentSummaryService` ≤250、`KnowledgeSummaryJob` ≤200、`SummarySourceReader` ≤200 |
| `service/management/KnowledgeService` | 648 | `DocumentImportService` ≤280、`DocumentQueryService` ≤250、`DocumentManualService` ≤200 |
| `service/management/KnowledgeBaseService` | 647 | `KnowledgeBaseService` ≤300、`KnowledgeBaseBindingService` ≤200、`KnowledgeBaseSearchService` ≤200 |
| `chunker/LegacySplitter` | 618 | `LegacyChunker` ≤350、`SemanticOverlapFinder` ≤150、`SeparatorPicker` ≤120 |
| `service/chunk/ChunkQuestionService` | 595 | `ChunkQuestionService` ≤300、`GeneratedQuestionWriter` ≤200、`QuestionPromptBuilder` ≤150 |
| `repository/ChunkRepository` | 582 | `ChunkRepository` ≤350、`ChunkRevisionRepository` ≤200、`ChunkQuestionQuery` ≤150 |
| `service/faq/FaqImportService` | 554 | `FaqImportService` ≤300、`FaqImportJob` ≤200 |
| `service/faq/FaqValidateOps` | 541 | `FaqImportValidator` ≤250、`FaqReplacePlanner` ≤200、`FaqIndexMergePlanner` ≤150 |
| `service/faq/FaqEntryQueryService` | 505 | `FaqEntryQueryService` ≤280、`FaqEntryCsvWriter` ≤150 |
| `repository/KnowledgeRepository` | 492 | `KnowledgeRepository` ≤350、`IngestStateRepository` ≤200、`KnowledgeCleanupRepository` ≤120 |
| `service/management/KnowledgeTagService` | 467 | `KnowledgeTagService` ≤280、`TagIndexCleaner` ≤150 |
| `service/management/KnowledgeCloneService` | 454 | `KnowledgeCloneService` ≤250、`KnowledgeCloneJob` ≤200 |
| `service/faq/FaqEntryCommandService` | 446 | `FaqEntryCommandService` ≤300、`FaqEntryProjector` ≤120、`FaqDuplicateChecker` ≤120 |
| `service/HousekeepingService` | 439 | `IngestionTimeoutSweeper` ≤250、`SummaryFailoverSweeper` ≤150 |
| `service/chunk/ChunkEditService` | 437 | `ChunkEditService` ≤300、`ChunkRevisionWriter` ≤150 |
| `task/KnowledgeMediaTranscriber` | 410 | `MediaTranscriptionStage` ≤250、`CoverExtractor` ≤150 |
| `controller/KnowledgeController` | 404 | `KnowledgeBaseController` ≤300、`KnowledgeItemController` ≤300、`KnowledgeFileController` ≤200 |
| `api/KnowledgeCatalogAdapter` | 403 | 删除，能力按五个 api 重写 |

超过 100 行的旧方法及处置：

| 旧方法 | 行 | 处置 |
|---|---:|---|
| `FaqBatchFieldUpdater.updateFields` | 150 | 拆成校验、构造更新、写库、重建向量四步 |
| `ChunkEditService.updateDocumentChunk` | 129 | 拆成取行校验、写修订、更新分块、同步索引 |
| `FaqValidateOps.validateReplaceMode` | 128 | 归入 `FaqReplacePlanner.plan`，三步 |
| `FaqBatchOps.executeImportBatches` | 118 | 归入 `FaqImportJob`，抽出 `importOneBatch` |
| `LegacySplitter.mergeUnits` | 117 | 拆出 `shouldMerge` 与 `mergePair` |
| `ChunkerPreviewController.previewChunking` | 109 | 校验与执行分开，执行放 `ChunkingService.preview` |
| `KnowledgeFileService.updateImageInfo` | 106 | 归入 `DocumentFileService.updateCover` |
| `KnowledgeSummaryService.doRegenerateKnowledgeSummary` | 105 | 归入 `KnowledgeSummaryJob` |
| `LegacySplitter.findSemanticOverlapBoundaryEndingAtOrAfter` | 103 | 迁入 `SemanticOverlapFinder`，参数收成 record |

## 1.2 旧接口到新接口

旧前缀 `/api/v1` 一律改 `/api/admin/v1`。下表只列需要说明的行，其余为机械换前缀。旧盘点 69 条路由，新接口 58 条。

| 旧接口 | 新接口 | 说明 |
|---|---|---|
| `GET/POST /knowledge-bases`、`GET/PUT/DELETE /knowledge-bases/{id}`、`/move-targets`、`/duplicate`、`/rebuild-index`、`GET/POST/DELETE /knowledge-bases/{id}/tags...` | 同路径换前缀 | 机械 |
| `PUT /knowledge-bases/{id}/pin` | `POST /knowledge-bases/{id}/pin` | 动作接口用 POST |
| `POST /knowledge-bases/copy`、`GET /knowledge-bases/copy/progress/{taskId}` | `POST /knowledge-bases/{id}/copy`、`GET /knowledge-bases/{id}/copy-tasks/{taskId}` | ID 进资源层级 |
| `POST/GET /knowledge-bases/{id}/hybrid-search` | 只留 `POST /knowledge-bases/{id}/hybrid-search` | 删 GET，见设计决定二第 1 条 |
| `GET /knowledge-bases/{id}/files/signed-url?filePath=` | `POST /knowledge-bases/{id}/files/signed-url`，body `{filePath}` | 见设计决定二第 3 条 |
| `GET/HEAD /knowledge-bases/{id}/files` | 同路径 | 机械 |
| `POST /knowledge-bases/{id}/knowledge/file` | `POST /knowledge-bases/{id}/documents`（multipart `file`、`fileName`、`metadata`、`tagIds`、`processConfig`） | 资源名复数 |
| `.../knowledge/url`、`.../knowledge/manual`、`GET .../knowledge`、`.../knowledge/folders` | `.../documents/url`、`.../documents/manual`、`GET .../documents`、`.../documents/folders` | 同上 |
| `GET /knowledge/{id}`、`/batch`、`/stages`、`/spans`、`/download`、`/preview` | `GET /knowledge-items/{id}` 同后缀 | 单数改名 |
| `PUT|DELETE /knowledge/{id}`、`PUT /knowledge/manual/{id}` | `PUT|DELETE /knowledge-items/{id}`、`PUT /knowledge-items/{id}/manual` | 同上 |
| `POST /knowledge/{id}/reparse`、`/cancel-parse`、`/regenerate-summary` | `POST /knowledge-items/{id}` 同后缀 | 同上 |
| `GET /knowledge/search`、`PUT /knowledge/tags`、`POST /knowledge/batch-delete`、`/batch-reparse` | `GET /knowledge-items/search`、`PUT /knowledge-items/tags`、`POST /knowledge-items/batch-delete`、`/batch-reparse` | 资源名同改 |
| `POST /knowledge/folder`、`POST /knowledge/move`、`GET /knowledge/move/progress/{taskId}` | `PUT /knowledge-items/folder`、`POST /knowledge-items/move`、`GET /knowledge-items/move-tasks/{taskId}` | 幂等写用 PUT，进度写 `knowledge_task_progress` |
| `PUT /knowledge/image/{id}/{chunkId}` | `PUT /knowledge-items/{id}/chunks/{chunkId}/image` | 归子资源 |
| `DELETE /knowledge-bases/{id}/knowledge` | `DELETE /knowledge-bases/{id}/documents` | 清空库内文档 |
| `GET /chunks/{knowledgeId}`、`DELETE /chunks/{knowledgeId}` | `GET|DELETE /knowledge-items/{knowledgeId}/chunks` | 归子资源 |
| `GET /chunks/by-id/{id}` | `GET /chunks/{id}` | 取消 by-id 旁路 |
| `GET/PUT/DELETE /chunks/{knowledgeId}/{id}`、`POST .../revert`、`GET .../revisions` | 同上去掉 knowledgeId | chunk ID 全局唯一 |
| `PUT /chunks/by-id/{id}/questions`、`POST .../regenerate`、`DELETE .../questions` | `PUT|DELETE /chunks/{id}/questions`、`POST /chunks/{id}/questions/regenerate` | 取消 by-id 旁路 |
| `POST /chunker/preview` | `POST /chunking/preview` | 名词用动名词 |
| `GET/POST /knowledge-bases/{id}/faq/entries`、`GET/PUT/DELETE .../{entryId}`、`.../fields`、`.../tags`、`.../similar-questions`、`.../export`、`POST /faq/search` | `/knowledge-bases/{id}/faq-entries` 同形状 | 连字符 |
| `POST /knowledge-bases/{id}/faq/entry` | 合并进 `POST /faq-entries` | 重复路由 |
| `GET /faq/import/progress/{taskId}` | `GET /knowledge-bases/{id}/faq-imports/{taskId}` | 见设计决定二第 4 条 |
| `PUT .../faq/import/last-result/display` | `PUT .../faq-imports/last-result/display` | 机械 |

旧 `POST /api/v1/knowledge-search` 属于 session 域（问答检索面），不在本计划，由计划 08 迁。

## 1.3 旧表到新表

通用规则：`id varchar(36)` 改 bigint 雪花，`tenant_id integer` 改 bigint，时间改 `timestamptz`，软删改哨兵值，租户表开行级安全。`chunks.seq_id` 与 `knowledge_tags.seq_id` 改 `sort_key bigint`，全局唯一约束换成 `(tenant_id, sort_key)` 索引。

| 新表 | 旧表 | 关键差异 | 写属主 |
|---|---|---|---|
| `knowledge_bases` | 同名 | 12 个 jsonb 配置列保留原名（`chunking_config`、`image_processing_config`、`storage_config`、`vlm_config`、`extract_config`、`faq_config`、`question_generation_config`、`storage_provider_config`、`asr_config`、`wiki_config`、`indexing_strategy`、`auto_tag_config`）；`embedding_model_id`、`summary_model_id`、`vector_store_id`、`storage_backend_id` 改 bigint 可空；`creator_id` 改 bigint；`is_temporary` 保留（聊天历史隐藏库靠它过滤）；删 `is_pinned`、`pinned_at`（旧 Java 已是 `@TableField(exist=false)` 瞬态字段，置顶数据在 `user_kb_pins`）；索引 `(tenant_id, vector_store_id)`、`(tenant_id, storage_backend_id)`、`(tenant_id, creator_id)`、`(tenant_id, is_temporary)` | `KnowledgeBaseRepository` |
| `user_kb_pins` | 同名 | 三列改 bigint，主键改雪花；唯一 `(tenant_id, user_id, knowledge_base_id, deleted_at)`；索引 `(tenant_id, user_id, pinned_at DESC)` | `UserKbPinRepository` |
| `knowledge_tags` | 同名 | `seq_id` 改 `sort_key`；唯一 `(tenant_id, knowledge_base_id, name, deleted_at)` | `KnowledgeTagRepository` |
| `knowledge_tag_relations` | 同名 | 加雪花主键与 `tenant_id`；两列改 bigint；唯一 `(tenant_id, knowledge_id, tag_id)`；开行级安全 | `KnowledgeTagRepository` |
| `knowledges` | 同名 | `knowledge_base_id`、`embedding_model_id` 改 bigint；`parse_attempt bigint`；状态列保留 varchar 加 Java 枚举；索引 `(tenant_id, knowledge_base_id, deleted_at)`、`(tenant_id, parse_status)`、`(tenant_id, summary_status)`、`(tenant_id, knowledge_base_id, folder_path)`、表达式索引 `(knowledge_base_id, (metadata->>'external_id')) WHERE deleted_at IS NULL` | `KnowledgeRepository` |
| `knowledge_processing_spans` | 同名 | 加 `tenant_id` 开行级安全；`knowledge_id` 改 bigint；唯一 `(knowledge_id, attempt, span_id)`；索引 `(tenant_id, status, started_at)` | `KnowledgeSpanRepository` |
| `knowledge_task_progress` | 新增 | `task_type`（`MOVE`、`CLONE`、`FAQ_IMPORT`）、`subject_id`、`status`、`progress jsonb`、`expires_at`；部分唯一索引 `(tenant_id, task_type, subject_id) WHERE status IN ('PENDING','RUNNING')` | `KnowledgeTaskProgressRepository` |
| `chunks` | 同名 | 各 ID 改 bigint；删 `video_info`；`relation_chunks`、`indirect_relation_chunks`、`metadata` 保留 jsonb；`image_info` 保留 text；`status`、`flags`、`content_revision` 保留 integer；索引 `(knowledge_id, is_enabled, deleted_at)`、`(knowledge_base_id, tenant_id)`、`(content_hash)`、`(parent_chunk_id)` | `ChunkRepository` |
| `chunk_revisions` | 同名 | 各 ID 改 bigint；唯一 `(chunk_id, revision)` | `ChunkRevisionRepository` |

特殊说明：`chunks.status` 三值（0、1、2）保留，Java 侧用枚举 `ChunkWriteStatus`，库列仍 integer。`chunks.video_info` 在本域与 session 域 Java 里都没有读写点（grep 只命中基线 DDL 与 `TestSchema`），删列。`knowledge_bases.is_temporary` 不能删：`KnowledgeBaseService.provisionChatHistoryKnowledgeBase` 建聊天历史隐藏库时置 true（`KnowledgeBaseService.java:299`），`listActiveByTenant`、`listMoveTargets` 都按它过滤，`SqlInjectionAnalyzer` 还给 `database_query` 注入 `is_temporary = false`。（Task 1 实测校准：`AgentEngineAssembler` 没有 `isTemporary` 命中，它按查询参数过滤隐藏库，不读该列。）`is_pinned`、`pinned_at` 两列旧 Java 实体已标 `@TableField(exist = false)`、不映射，删列不丢数据。

## 1.4 旧队列与后台任务去向

旧系统 7 套自研队列，属于文档入库的三套由 JobRunr 取代，其余四套不在本计划。

| 旧机制 | 新做法 | 归属 |
|---|---|---|
| `KnowledgeProcessingQueue`（主处理，进程内虚拟线程） | `DocumentIngestionJob`，一次性作业，`jobId=ingest:{knowledgeId}:{attempt}`，不重试 | Task 10 |
| `ChunkExtractTaskQueue`（双实现） | `ChunkExtractJob`，重试 3 次 | Task 12 |
| `QuestionGenerationTaskQueue`（双实现） | `QuestionGenerationJob`，每 20 个 chunk 一个，重试 3 次 | Task 12 |
| `FaqImportTaskStore`（进度与互斥，双实现） | `knowledge_task_progress` 加部分唯一索引，`FaqImportJob` 写进度 | Task 13 |
| `KnowledgeTaskProgressStore`（move/clone 进度，双实现） | `knowledge_task_progress` | Task 12 |
| `KnowledgeTaskExecutor`（虚拟线程池） | JobRunr 一次性与周期作业 | 本计划 |
| `HousekeepingService`（启动后 5 分钟循环） | `IngestionTimeoutSweeper`、`SummaryFailoverSweeper`，JobRunr 周期作业，开关 `ragagent.knowledge.housekeeping.enabled` | Task 12 |
| `KnowledgeStartupRecovery`（启动复位，Redis 模式跳过） | 删除。JobRunr 持久化作业，重启续跑；卡死由 sweeper 兜底 | 本计划 |
| `KnowledgeRecycleService` 启动重试 | 由 sweeper 扫 `delete_failed` | Task 12 |
| `DataSourceSyncTaskQueue`（双实现） | JobRunr，作业类在 datasource 域 | 计划 05 |
| `WikiIngestTaskQueue`（双实现） | JobRunr | 计划 05 |
| `MemoryExtractTaskQueue`（双实现） | JobRunr | 计划 06 |
| `QaQueue`（IM 有界队列加可选 Redis） | 不用 JobRunr，保留排队语义，改 Redisson 队列 | 计划 07 |
| Langfuse 追踪跨线程传递（手工 inject） | `TracingJobFilter` 从标签恢复 | Task 2 |

## 1.5 跨域依赖与新做法

| 本域用途 | 旧做法 | 新做法 |
|---|---|---|
| 业务异常 | `contract.error.BizException`、`AppError` | `BusinessRuntimeException` 加 `KnowledgeCode` |
| 模型查询与调用 | `contract.model.*`、`ModelCatalogPort`、`AsrTranscriber` | `model.api`（以计划 03 为准）与 model-runtime 的 `EmbeddingPort`、`LlmChatClient`、`AsrTranscriber` |
| 向量与索引 | `contract.retrieval.*`、`IndexEngineResolver` | retrieval-engine 的 `api` 与 `spi`（以计划 02 为准） |
| 文件存储与签名 URL | `ProviderFileStorePort`、`TenantConfigLookup` | `storage.api`（以计划 03 为准） |
| 租户、追踪、JSON、响应 | `TenantContext`、`TenantScopes`、`LangfuseTracing`、`JsonMappers`、`DatabaseDialects`、`ApiResponse`、`NonNullBody` | framework 的 `TenantContextHolder`、Jackson、`TypeHandler`、`RestResponse`；方言分支删除 |
| 审计与 API Key 范围 | `AuditAction`、`TenantAPIKeyScope` | `audit.api`、`auth.api`（以计划 03 为准） |
| wiki 富化交接 | `DefaultWikiKnowledgeFinalizer`、`WikiIngestService` | 发 `KnowledgeIngestedEvent`，wiki 监听并入 JobRunr；槽位释放经 `KnowledgeIngestApi.finalizeSubtask(long knowledgeId)` 暴露 |

**其他域直接访问 `knowledge_bases`、`knowledges`、`chunks` 的位置与新做法**：

| 域 | 旧位置 | 新做法 |
|---|---|---|
| agentmanagement | `AgentQuestionMapper.java:20-106` 五段 SQL | `KnowledgeQuestionApi` 四个方法 |
| session | `AgentToolKbBackends.java:226-233` grep join | `KnowledgeSearchApi.grep` |
| session | `AgentToolKbBackends.java:319-323` 按知识统计启用分块 | `KnowledgeSearchApi.countEnabledChunks` |
| session | `AgentToolKbBackends.java:280-283`、`:418-421` 标签范围 | `GrepQueryBo.tagIds`，scope 解析在 knowledge 内 |
| storage | `ResourceRepository.java:181-214`、`StorageBackendRepository.java:153-159` | `KnowledgeResourceReferenceProvider`（实现 storage 声明的 `ResourceReferenceProvider`）与 `KnowledgeStorageReferenceProvider` |
| vectorstore | `VectorStoreConfigService.java:244-247` | `KnowledgeVectorStoreReferenceProvider` |
| model | `ModelUsageMapper.java:19-22` | `KnowledgeModelReferenceProvider` |
| agenttools | `GrepChunksTool.java:92-95` seam 接口 | 调 `KnowledgeSearchApi.grep` |
| agenttools | `DatabaseQueryTool.java:45-104`、`SqlGuard`、`SqlInjectionAnalyzer` | 提示词里的表名改新表名；表白名单随之更新；手写租户谓词检查删除（行级安全兜底） |
| datasource | `MapperKnowledgeBridge.java:98-261` 经 `KnowledgeCatalogPort` | `KnowledgeContentApi` 与 `KnowledgeIngestApi` |
| wiki | `ChunkPortAdapter` 四个消费方 | `KnowledgeContentApi` 的分块四个方法 |
| platform | `TenantFilterGuard`、`SoftDeleteFilterGuard` 白名单 | 删除，由 ArchUnit 与行级安全测试替代 |

## 1.6 旧测试处置

41 个旧测试类，处置分三类。丢弃只能因为钉 Go 语义。

| 旧测试类 | 测什么 | 处置 |
|---|---|---|
| `KnowledgeCodeConventionsTest` | 禁历史黑话与逐字段 JSON 注解 | 丢弃，规则由 Checkstyle 与 ArchUnit 表达 |
| `domain/KnowledgeBaseConfigJsonContractTest` | jsonb 形状，钉死键集合与零值输出 | 丢弃，钉的是 Go 版零值字节形状（spec 5.11 表格第 5 行） |
| `storage/TenantFileStorageTest` | 租户存储回落与 `golden` 形态 | 丢弃，断言 Go 零值与回退形状；能力由 `storage.api` 承担 |
| `task/KnowledgeProcessWorkerFixture` | 测试装配夹具 | 丢弃，夹具绑旧线程池装配，Task 10 重建 |
| `task/RedisFaqImportTaskStoreTest`、`RedisKnowledgeTaskProgressStoreTest`、`RedisKnowledgeTaskQueueTest` | Redis 存储与队列租约 | 丢弃，存储与队列被 JobRunr 与 `knowledge_task_progress` 取代 |
| `chunker/ChunkerTest` | 分块算法与上限，`Go SplitText` | 改写：算法断言保留，`CodePoints` 换 `codePointCount`，删 Go 文案 |
| `task/KnowledgeProcessWorkerTenantScopeTest` | 虚拟线程租户传递 | 改写为 `TenantJobFilterTest` |
| `api/KnowledgeFinalizeAdapterTest`、`domain/ingest/IngestJobTest` | 槽位递减、晋升、阶段进度 | 移植到 `IngestStateRepositoryTest`、`IngestStateMachineTest` |
| `client/DocReaderClientTest`、`config/KnowledgeOcrPropertiesTest` | 流式解析、回退、超时、OCR 配置 | 移植，mock 改直接构造 `ManagedChannel` |
| `service/SummaryPipelineLogicTest`、`service/access/ChunkAccessGuardMoveGuardTest`、`service/chunk/ChunkExtractServiceTest` | 摘要流水线、分块权限、图抽取 | 移植到 `DocumentSummaryServiceTest`、`KnowledgeAccessGuardTest`、`ChunkExtractJobTest` |
| `index/KnowledgeIndexCleanerTest`、`KnowledgeVectorWritesTest`、`service/image/*` | 索引清理、向量写入、图片落盘 | 移植 |
| `KnowledgeFileServicePostCommitTest`、`KnowledgeParseServiceReparseOverridesTest`、`KnowledgeRecycleServiceTest`、`UrlFileTypeTest`、`HtmlTitlesTest`、`KnowledgeBaseEnsureDefaultsTest` | 提交后清理、重解析覆盖、回收、URL 类型、标题、零值回填 | 移植。零值回填是业务默认值，不是 Go 语义 |
| `chunker/HeadingHierarchyTest`、`domain/KnowledgeBaseChunkingConfigCapabilityTest`、`support/` 下 11 个纯函数用例、`task/KnowledgeImageOcrTest`、`KnowledgeMediaTranscriberTest`、`KnowledgeTextExtractorParserRuleTest`、`KnowledgeProcessWorkerImageWiringTest` | 标题分块、配置矩阵、纯函数规则、OCR 与转写、解析引擎、图片接线 | 全部移植，类名按新类改 |

## 1.7 错误码清单

模块号 `010`。旧代码 301 处硬编码文案归并到下表 51 个码，其余并入 `CommCode`（参数用 `PARAM_ERROR`，不存在用 `OBJECT_NOT_EXIST`，无权限用 `PERMIT_ERROR`）。

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent | `KB_NOT_FOUND`（`1001010001`） | 按 ID 取知识库不存在或已删 | 知识库不存在 | Knowledge base not found |
| ragagent | `KB_ACCESS_DENIED`（`1001010002`） | 非属主且非管理员操作知识库 | 无权操作该知识库 | No permission to operate on this knowledge base |
| ragagent | `KB_NOT_OWNER_CANNOT_DELETE`（`1001010003`） | 非属主删除知识库 | 只有知识库属主可以删除 | Only the knowledge base owner can delete |
| ragagent | `KB_INDEXING_STRATEGY_REQUIRED`（`1001010004`） | 向量与关键词都关 | 至少启用一种索引策略 | At least one indexing strategy must be enabled |
| ragagent | `KB_VECTOR_STORE_UNAVAILABLE`（`1001010005`） | 向量库不存在或连接失败 | 向量库当前不可用，请检查连接 | Vector store is currently unavailable, check its connection |
| ragagent | `KB_STORAGE_UNAVAILABLE`（`1001010006`） | 存储后端不可用 | 存储后端不可用 | Storage backend is unavailable |
| ragagent | `KB_CLONE_FAILED`（`1001010007`） | 复制知识库配置失败 | 复制知识库配置失败 | Failed to clone knowledge base configuration |
| ragagent | `TAG_NAME_REQUIRED`（`1001010008`） | 创建或更新标签名为空 | 标签名称不能为空 | Tag name is required |
| ragagent | `TAG_DUPLICATE_NAME`（`1001010009`） | 同库内标签重名 | 标签名称已存在 | Tag name already exists |
| ragagent | `TAG_NOT_IN_KB`（`1001010010`） | 标签不属于当前知识库 | 标签不属于当前知识库 | Tag does not belong to this knowledge base |
| ragagent | `TAG_STILL_REFERENCED`（`1001010011`） | 删除仍被引用的标签 | 标签仍有知识或 FAQ 条目引用，无法删除 | Tag is still referenced by documents or FAQ entries |
| ragagent | `DOCUMENT_NOT_FOUND`（`1001010012`） | 按 ID 取文档不存在 | 文档不存在 | Document not found |
| ragagent | `DOCUMENT_CONTENT_EMPTY`（`1001010013`） | 手工知识正文为空 | 内容不能为空 | Content cannot be empty |
| ragagent | `DOCUMENT_CONTENT_TOO_LONG`（`1001010014`） | 正文超过 20 万字符 | 内容长度超出限制（最多 200000 个字符） | Content exceeds the limit of 200000 characters |
| ragagent | `DOCUMENT_STATUS_INVALID`（`1001010015`） | 状态不是 draft 或 publish | 状态仅支持 draft 或 publish | Status must be draft or publish |
| ragagent | `DOCUMENT_MANUAL_ONLY`（`1001010016`） | 对非手工知识在线编辑 | 仅支持手工知识的在线编辑 | Only manual knowledge can be edited inline |
| ragagent | `DOCUMENT_FILE_TOO_LARGE`（`1001010017`） | 上传文件超限 | 文件大小不能超过 {0}MB | File size cannot exceed {0}MB |
| ragagent | `DOCUMENT_DUPLICATE`（`1001010018`） | 同库同哈希同类型重复 | 该文件已存在于当前知识库 | This file already exists in the knowledge base |
| ragagent | `DOCUMENT_DELETING`（`1001010019`） | 对删除中的文档操作 | 知识正在删除中，无法执行该操作 | The document is being deleted |
| ragagent | `DOCUMENT_PARSE_FINISHED`（`1001010020`） | 取消已结束的解析 | 解析已结束，无法取消 | Parsing has finished and cannot be cancelled |
| ragagent | `DOCUMENT_MOVE_IN_PROGRESS`（`1001010021`） | 搬迁未完成时再操作 | 知识存在未完成的移动操作，请先重试移动 | The document has an unfinished move, retry the move first |
| ragagent | `DOCUMENT_MOVE_SAME_KB`（`1001010022`） | 源与目标知识库相同 | 源知识库与目标知识库不能相同 | Source and target knowledge bases cannot be the same |
| ragagent | `BATCH_IDS_EMPTY`（`1001010023`） | 批量操作未传 ID | 未提供知识 ID | No knowledge IDs provided |
| ragagent | `BATCH_IDS_TOO_MANY`（`1001010024`） | 批量超过 200 条 | 单批最多 200 条 | At most 200 ids per batch |
| ragagent | `CHUNK_NOT_FOUND`（`1001010025`） | 分块不存在 | 分块不存在 | Chunk not found |
| ragagent | `CHUNK_NOT_IN_DOCUMENT`（`1001010026`） | 分块不属于该文档 | 分块不属于该知识文档 | Chunk does not belong to its knowledge document |
| ragagent | `CHUNK_CONFLICT`（`1001010027`） | 并发编辑版本不一致 | 分块已被他人修改，请刷新后重试 | The chunk was modified by another user, refresh and retry |
| ragagent | `CHUNK_QUESTION_NOT_FOUND`（`1001010028`） | 推荐问题不存在 | 推荐问题不存在 | Generated question not found |
| ragagent | `CHUNK_QUESTION_EMPTY`（`1001010029`） | 推荐问题内容为空 | 问题不能为空 | Question cannot be empty |
| ragagent | `CHUNK_QUESTION_TEXT_ONLY`（`1001010030`） | 对非文本分块生成问题 | 只有文本分块可以生成推荐问题 | Questions can only be generated for text chunks |
| ragagent | `FOLDER_PATH_INVALID`（`1001010031`） | 文件夹路径含非法字符 | 文件夹路径包含非法字符 | Folder path contains invalid characters |
| ragagent | `FOLDER_SELF_NESTED`（`1001010032`） | 移动到自己的子目录 | 不能将文件夹移动到它自己的子目录下 | Cannot move a folder into its own subdirectory |
| ragagent | `FAQ_NOT_FAQ_KB`（`1001010033`） | 非 FAQ 库做 FAQ 操作 | 仅 FAQ 知识库支持该操作 | Only FAQ knowledge bases support this operation |
| ragagent | `FAQ_ENTRY_NOT_FOUND`（`1001010034`） | FAQ 条目不存在 | FAQ 条目不存在 | FAQ entry not found |
| ragagent | `FAQ_IMPORT_MODE_INVALID`（`1001010035`） | 模式不是 append 或 replace | 模式仅支持 append 或 replace | Mode must be append or replace |
| ragagent | `FAQ_IMPORT_RUNNING`（`1001010036`） | 同库已有导入在跑 | 该知识库已有导入任务正在进行中（任务 ID: {0}） | An import task is already running for this knowledge base (task id: {0}) |
| ragagent | `FAQ_IMPORT_DUPLICATE_ENTRY`（`1001010037`） | 标准问或相似问重复 | 标准问或相似问与已有条目重复 | The question duplicates an existing entry |
| ragagent | `FAQ_ANSWER_EMPTY`（`1001010038`） | 条目没有答案 | 至少提供一个答案 | At least one answer is required |
| ragagent | `JOB_TENANT_MISSING`（`1001010039`） | 作业参数缺租户 | 后台任务缺少租户上下文 | Background job is missing tenant context |
| ragagent | `JOB_PAYLOAD_INVALID`（`1001010040`） | 作业参数无法解析 | 后台任务参数不合法 | Background job payload is invalid |
| ragagent | `DOCREADER_UNAVAILABLE`（`1001010041`） | docreader 不可达 | 文档解析服务不可用 | Document parsing service is unavailable |
| ragagent | `DOCREADER_TIMEOUT`（`1001010042`） | docreader 调用超时 | 文档解析超时 | Document parsing timed out |
| ragagent | `DOCREADER_PARSE_FAILED`（`1001010043`） | docreader 返回业务错误 | 文档解析失败：{0} | Document parsing failed: {0} |
| ragagent | `OCR_UNAVAILABLE`（`1001010044`） | OCR 不可达 | OCR 服务不可用 | OCR service is unavailable |
| ragagent | `MEDIA_DURATION_EXCEEDED`（`1001010045`） | 音视频超过 600 秒 | 音视频时长超过上限 | Media duration exceeds the limit |
| ragagent | `MEDIA_TRANSCRIBE_FAILED`（`1001010046`） | ASR 转写失败 | 音视频转写失败 | Media transcription failed |
| ragagent | `SIGNED_URL_UNAVAILABLE`（`1001010047`） | 路径不在允许命名空间 | 签名地址不可用 | Signed url unavailable |
| ragagent | `FILE_NOT_IN_KB`（`1001010048`） | 文件不属于该知识库 | 文件不属于该知识库 | File does not belong to this knowledge base |
| ragagent | `CHUNKING_PREVIEW_TIMEOUT`（`1001010049`） | 分块预览超时 | 分块预览超时 | Chunking preview timed out |
| ragagent | `CHUNKING_PREVIEW_FAILED`（`1001010050`） | 分块预览执行失败 | 分块预览失败 | Chunking preview failed |
| ragagent | `KB_NAME_REQUIRED`（`1001010051`） | 建库或改名时名称为空 | 知识库名称不能为空 | Knowledge base name is required |

其余五种语言由机器翻译出初稿，同提交入库，标注待人工校对。

## 1.8 旧类到新类映射

重点是 Ops 与 Support 的拆解，旧大类按变化原因拆成几个新类。

| 旧类 | 行 | 新类与去向 |
|---|---:|---|
| `service/faq/FaqValidateOps` | 541 | `FaqImportValidator`、`FaqReplacePlanner`、`FaqIndexMergePlanner`。取消 `new FaqValidateOps(this)` 回引，由 `FaqImportService` 注入 |
| `service/faq/FaqBatchOps`、`FaqBatchFieldUpdater` | 488 | `FaqBatchImporter`、`FaqFieldBatchUpdater`（拆校验、更新、向量重建） |
| `service/KnowledgeAccessHelper` | 103 | 拆入 `KnowledgeAccessGuard`（断言）与 `KnowledgeQueryService`（行读取） |
| `service/management/KnowledgeBatchOpsService`、`KnowledgeMaintenanceAdapter` | 135 | `DocumentBatchService`；MaintenanceAdapter 删除，能力进 `KnowledgeContentApi.softDelete` |
| `service/faq/FaqEntryProjector`、`FaqDuplicateChecker` | 139 | 保留，改抛 `KnowledgeCode` |
| `api/*Adapter`（11 个） | 961 | 全部删除，按五个 api 重写 |
| `support/` 下 30 个类 | 1943 | 迁入 `service/chunk/`（hash、索引正文、文本归一）、`service/document/`（metadata 覆盖、路径、类型、标题）、`service/faq/`（编码、标签视图）、`service/ingest/`（心跳、槽位）。`KnowledgeProcessedEvent` 改名 `KnowledgeIngestedEvent` 放 api |
| `task/KnowledgeProcessWorker`、`KnowledgeIngestPipeline` | | `DocumentIngestionJob`、`DocumentIngestionPipeline`、`IngestStateMachine` |
| `task/KnowledgeIndexStage` | | `DocumentIndexStage`、`DocumentPreCleaner`、`DocumentEmbeddingStage`、`EmbeddingModelResolver` |
| `task/KnowledgeFinalizingStage` | | `DocumentFinalizingStage`、`SubtaskCoordinator`、`EnrichmentFanOut` |
| `task/KnowledgeChunkWriter`、`KnowledgeTextExtractor` | | `ChunkWriter`、`DocumentTextExtractor`、`DocumentParserRegistry`、`ChunkingService` |
| `task/KnowledgeImageOcr`、`KnowledgeMediaTranscriber` | | `ImageOcrStage`、`MediaTranscriptionStage`、`CoverExtractor` |
| `task/KnowledgeProcessWiring`、`KnowledgeRedisWiring`、`KnowledgeTaskExecutor`、五个 Redis 队列与 Store、`recovery/KnowledgeStartupRecovery` | | 全部删除，见 1.4 |
| `service/HousekeepingService` | 439 | `IngestionTimeoutSweeper`、`SummaryFailoverSweeper` |
| `domain/span/SpanTracker` | 678 | `IngestSpanRecorder`、`IngestSpanTree`、`SpanHeartbeat` |
| `index/*`（3 个） | 530 | `DocumentEmbeddingStage`、`ChunkVectorWriter`、`KnowledgeIndexCleaner` |
| `chunker/` 13 个类 | 2555 | `ChunkingService` 加 `ChunkingTier` 三个实现；算法类迁入 `service/chunk`，静态工具改构造器注入的组件 |
| `storage/LocalStorageService`、`TenantFileStorage` | 440 | 删除，文件读写走 `storage.api` |
| `client/DocReaderClient` | 263 | 保留，改直接构造 `ManagedChannel` |
| `repository/*`（10 个） | 2275 | 按聚合根划分成 9 个 Repository 加 3 个查询辅助 |
| `domain/*`（25 个） | 1468 | 改 entity 包；`FaqGuard` 合并进 `KnowledgeAccessGuard` |
| `mapper/*`（7 个） | 402 | 改 `BaseMapper`，手写 SQL 进 XML，删全部手写 `tenant_id` |

## 1.9 租户写法与 `@IgnoreTenant` 白名单

旧手写租户条件：`ChunkMapper` 3 条语句、`KnowledgeTagMapper` 19 条语句、wrapper 41 处。新代码全部删除，由 `TenantLineInnerInterceptor` 注入、行级安全兜底。

| 旧跨租户入口 | 新做法 |
|---|---|
| `ChunkRepository.findByIdAny`、`KnowledgeRepository.findByIdAny`、`KnowledgeBaseRepository.findByIdAny` | 删除。Repository 只按 ID 取，取不到即 `KB_NOT_FOUND` 或 `DOCUMENT_NOT_FOUND`。复制与移动要区分「在别的租户」与「不存在」，改用下表系统通道 |
| `KnowledgeRecycleService.recycleForTenant` | `KnowledgeIngestApi.reclaimTenantKnowledge`，系统通道内执行 |
| `KnowledgeRepository.touchUpdatedAt` 不按租户不看软删 | `SpanHeartbeat` 在作业租户上下文内执行，租户条件由框架注入 |
| `KnowledgeStartupRecovery`、`HousekeepingService` 全局扫描 | 系统通道先取租户列表，再逐租户进入上下文 |

**`@IgnoreTenant` 白名单**（登记到 `progress.md` 的 IgnoreTenant 白名单小节）：

| 类 | 方法 | 原因 |
|---|---|---|
| `KnowledgeBaseQueryService` | `resolveOwnershipAcrossTenants(long kbId)` | 复制与移动要区分 404 与 403，行级安全下看不到别的租户的行 |
| `IngestionTimeoutSweeper` | `listStaleTenants()` | 周期任务无租户上下文，先取有卡死作业的租户 |
| `SummaryFailoverSweeper` | `listFailedSummariesByTenant()` | 同上 |
| `KnowledgeRecycleService` | `retryFailedCleanups(long tenantId)` | 兜底重试 `delete_failed`，由 sweeper 按租户调用 |

JobRunr 与 Modulith 的表不是业务表，加入 `gac.furnace.tenant.ignore-tables`，不标注解。

## 1.10 前端 API 层切换

只改 `frontend/src/api/knowledge-base/index.ts`（621 行，62 个 `export function`）与 `frontend/src/api/chunker/index.ts`（15 行，1 个函数）。组件与页面不改，导出名与返回形状不变，差异在函数内部适配。

| 旧函数 | 新路径 | 适配点 |
|---|---|---|
| `listKnowledgeBases`、`createKnowledgeBase`、`getKnowledgeBaseById`、`updateKnowledgeBase`、`deleteKnowledgeBase`、`listMoveTargets`、`duplicateKnowledgeBase`、`rebuildKBIndex`、标签五个函数 | 原路径换 `/api/admin/v1` 前缀 | 无，`tagSeqId` 改 string |
| `togglePinKnowledgeBase`、`moveKnowledgeToFolder` | `POST .../{id}/pin`、`PUT /knowledge-items/folder` | 方法改 POST 与 PUT |
| `copyKnowledgeBase` | `POST /knowledge-bases/{sourceId}/copy` | sourceId 进路径，body 只留 targetId |
| `getKnowledgeMoveProgress`、`moveKnowledge` | `/knowledge-items/move-tasks/{taskId}`、`/knowledge-items/move` | 无 |
| `uploadKnowledgeFile`、`createKnowledgeFromURL`、`createManualKnowledge`、`listKnowledgeFiles`、`listKnowledgeFolders`、`renameKnowledgeFolder` | `/knowledge-bases/{id}/documents...` | multipart 字段名与分页字段不变 |
| `getKnowledgeDetails`、`batchQueryKnowledge`、`delKnowledgeDetails`、`regenerateKnowledgeSummary`、`reparseKnowledge`、`cancelKnowledgeParse`、`getKnowledgeSpans` | `/knowledge-items/{id}...` 同后缀 | 无 |
| `updateKnowledgeMetadata`、`updateKnowledgeSummary` | `PUT /knowledge-items/{id}` | 两个函数共用一个请求，字段区分 |
| `getKnowledgeDetailsCon` | `GET /knowledge-items/{knowledgeId}/chunks` | 内部页码口径旧从 0 开始改从 1 开始 |
| `getChunkByIdOnly`、`updateDocumentChunk`、`listChunkRevisions`、`revertDocumentChunk`、`deleteGeneratedQuestion`、`upsertGeneratedQuestion`、`regenerateGeneratedQuestions` | `/chunks/{id}...` | knowledgeId 参数保留签名但不发 |
| `downKnowledgeDetails`、`previewKnowledgeFile` | `/knowledge-items/{id}/download`、`/preview` | blob 通道保留 |
| `batchDeleteKnowledge`、`batchReparseKnowledge`、`searchKnowledge`、`updateKnowledgeTagBatch` | `/knowledge-items/...` | 无 |
| `getSignedFileUrl` | `POST .../files/signed-url`，body `{filePath}` | 参数移进 body，签名不变 |
| `getSignedFileUrlByKnowledge` | 删除该函数 | 后端从来没这条路由。先 grep 确认无页面引用（当前只有定义与 `signedMedia.ts:18` 注入的 `getSignedFileUrl`），再删；`signedMedia.ts` 不改 |
| FAQ 九个函数 | `/knowledge-bases/{id}/faq-entries...` | `entryId` 由 number 改 string |
| `getFAQImportProgress` | `/knowledge-bases/{id}/faq-imports/{taskId}` | 新增 kbId 参数；调用处只有 `FAQEntryManager.vue` 一处，API 层从最近一次导入的上下文缓存补齐，不改组件 |
| `updateFAQImportResultDisplayStatus`、`previewChunking` | `.../faq-imports/last-result/display`、`POST /chunking/preview` | 无 |
| `knowledgeSemanticSearch` | 不变更 | 属 session 域，归计划 08 |

前端类型由 `npm run gen:api` 生成，产物提交，`api-drift` 守漂移。路径一律从 `@ragagent/api-client` 的 `paths` 类型取，不手写字符串；缺某条说明 OpenAPI 没导出该操作，先补 Controller。

## 1.11 e2e 域标签与冒烟

| 用例 | 文件 | 覆盖交互 | 依赖任务 |
|---|---|---|---|
| 知识库列表 | `tests/e2e/tests/knowledge/kb-list.spec.ts` | 新建、编辑、删除；命令面板输入关键字出结果 | Task 16、Task 17 |
| 文档管理 | `tests/e2e/tests/knowledge/kb-documents.spec.ts` | 上传 `sample.txt` 等解析完成；卡片与列表切换；新建文件夹；管理标签；批量栏；删除 | Task 16、Task 17 |
| FAQ | `tests/e2e/tests/knowledge/kb-faq.spec.ts` | 新增条目，列表出现，删除 | Task 13、Task 16 |
| 知识库设置 | `tests/e2e/tests/knowledge/kb-settings.spec.ts` | 每个子页签一份快照；改分块大小保存后刷新保持 | Task 15、Task 17 |

冒烟三步（`tests/api-smoke/tests/knowledge-flow.spec.ts`）：

| 步骤 | 请求 | 转绿判据 |
|---|---|---|
| 建库 | `POST /api/admin/v1/knowledge-bases`，`{name}` | `code=0000`，`data.id` 为字符串 |
| 上传 | `POST /api/admin/v1/knowledge-bases/{kbId}/documents`，multipart `file` | `code=0000`，`data.id` 非空 |
| 入库 | `GET /api/admin/v1/knowledge-items/{docId}` 轮询 | 60 秒内 `data.parseStatus=COMPLETED` |

Task 16 另补一条「重解析与取消」冒烟：上传后 `POST .../cancel-parse` 到 `CANCELLED`，再 `POST .../reparse` 到 `COMPLETED`，验证 `parse_attempt` 与作业取消。

检索回归：计划 01 Task 14 的 `adapters/rebuild.mjs` 由 Task 18 写，接口 `POST /api/admin/v1/knowledge-bases/{kbId}/hybrid-search`，请求体 `{queryText, knowledgeBaseIds}`，响应 `RestResponse.data` 为命中数组，字段 camelCase。`cases.json`、`baseline.json`、`compare.mjs` 不改。

# 2. JobRunr 与部署角色开关（首次出现，后续域复用）

### 2.1 角色开关

| 项 | 决定 |
|---|---|
| 配置键 | `ragagent.role`，环境变量 `RAGAGENT_ROLE`，取值 `api`、`worker`、`all`，默认 `all` |
| 枚举 | `com.ragagent.config.RagAgentRole`，方法 `boolean hasHttp()`（api、all 为真）、`boolean hasWorker()`（worker、all 为真） |
| HTTP 门 | `RoleGatedWebConfig` 把 `WorkerRoleRequestGate`（`OncePerRequestFilter`）注册为过滤器。`hasHttp()` 为假时 `/api/` 前缀直接写 404 并结束，`/actuator/health/**` 放行。注册顺序在鉴权之前 |
| 后台服务 | `org.jobrunr.background-job-server.enabled` 由 `JobRunrConfig` 按 `hasWorker()` 设置，不写在 yml 里 |
| 文档入库 | `DocumentIngestionJob` 只在 `hasWorker()` 为真时被消费。api 角色可以入队，不能执行 |
| 小规模部署 | `all` 单实例承担两个角色 |
| 校验 | 非 `api`、`worker`、`all` 的取值启动失败，报 `IllegalStateException`，不做静默回落 |

spec 5.8 要求 worker 跑文档入库与数据源同步，api 只处理 HTTP。计划 09 的部署编排读 `RAGAGENT_ROLE` 与 `/actuator/health/liveness`。

### 2.2 JobRunr 接入

| 项 | 决定 |
|---|---|
| 依赖 | `org.jobrunr:jobrunr-spring-boot-3-starter`，版本随 Boot 3.5.16 兼容版本，写在 `backend/pom.xml` 的 `jobrunr.version` |
| 存储 | PostgreSQL，`org.jobrunr.database.type=sql`。worker 与 all 角色 `skip-create=false` 自动建表，api 角色 `skip-create=true`。JobRunr 7.x 的配置前缀是 `org.jobrunr`，8.x 起改为 `jobrunr`，升级时整体换前缀。计划 09 的 `JOBRUNR_TABLES` 取 JobRunr 建出的表名清单 |
| 事务 | 入队与业务写库同事务。`TenantJobEnqueuer.enqueue` 在调用方事务内执行。JobRunr 7 OSS 的存储层不加入 Spring 事务，`JobRunrConfig` 用 `TransactionAwareDataSourceProxy` 包一层 DataSource 交给 JobRunr，事务回滚时作业不落库。详见下表之后的事务传播说明 |
| 租户 | 作业标签带 `tenant:<id>`，`TenantJobFilter` 在 `onProcessing` 读取并设 `TenantContextHolder`；标签缺失或非法时把重试次数置零并抛异常，作业进 FAILED 不死循环。`tenantId=0` 的系统作业标签写 `tenant:system`，上下文走 `@IgnoreTenant` 档位，不做数值比较 |
| 追踪 | 入队时把当前 `traceparent` 写进标签 `trace:<value>`，`TracingJobFilter` 恢复上下文后再执行 |
| 面板 | `JobRunrDashboardConfig` 只在 `ragagent.jobrunr.dashboard.enabled=true` 时开，默认 false，路径 `/jobrunr`，不进业务鉴权，由部署层限制来源 |
| 重试 | 一次性作业默认重试 3 次，指数退避起始 10 秒；`DocumentIngestionJob` 重试 0 次（失败即 failed，与旧行为一致）；周期作业不重试，下一轮重新扫 |
| 周期 | `IngestionTimeoutSweeper` 与 `SummaryFailoverSweeper` 每 5 分钟、`EventRetryJob` 每 1 分钟、`EventArchiveJob` 每天，这四个由本计划 Task 12 的 `RecurrentJobRegistration` 注册；`McpApprovalExpiryJob` 由计划 06 自己注册，不进本类 |

`TenantJobEnqueuer` 与作业契约的签名（Task 2 产出，计划 05、06、08 直接注入这个类，不建接口）。入队必须发生在调用方事务内：

```java
public class TenantJobEnqueuer {

	/** 入队一次性作业。jobId 是业务字符串，内部转 UUID 后作为 JobRunr 作业 id，重复投递不新建。 */
	public String enqueue(long tenantId, String jobId, TenantScopedJob job);

	/** 延迟入队，delay 小于等于零时等价 enqueue。 */
	public String enqueue(long tenantId, String jobId, TenantScopedJob job, Duration delay);

	/** 注册周期作业，jobId 即 JobRunr 的 recurrent id，cron 六段。tenantId 传 0 表示跨租户系统作业，作业体内自行枚举租户。 */
	public String scheduleRecurrent(long tenantId, String jobId, String cron, TenantScopedJob job);

	/** 删除作业，取消失败返回 false 不抛。 */
	public boolean delete(String jobId);
}

/** 作业请求载荷：租户与业务 ID 都是字段，随 JobRunr 一起序列化；执行逻辑在同类名加 Handler 后缀的 Bean 里。 */
public interface TenantScopedJob extends JobRequest {

	long tenantId();
}
```

`TenantScopedJob` 必须是 `JobRequest` 而不是自带 `run()` 的接口。原因：JobRunr 7 OSS 只序列化请求对象的字段，再用请求里的 `getJobRequestHandler()` 从容器取处理器；接口上的 `run()` 无法把 `knowledgeId`、`attempt`、`batchIndex` 这些每作业不同的参数带到 worker。每个作业写成两个类：请求 `record DocumentIngestionJob(long tenantId, long knowledgeId, long attempt) implements TenantScopedJob`，处理器 `DocumentIngestionJobHandler implements JobRequestHandler<DocumentIngestionJob>`，处理器标 `@Component` 并注入本任务需要的 Repository 与 Service。请求对象由 `TenantJobEnqueuer` 直接 `new` 后交给 `jobScheduler.enqueue(uuid, job)`，不要捕获 Service 实例。

`jobId` 到 JobRunr 作业 id 的转换固定为 `UUID.nameUUIDFromBytes(jobId.getBytes(StandardCharsets.UTF_8))`：JobRunr 7 一次性作业的显式 id 只接 `UUID`，OSS 版没有 `JobId.fromIdentifier`。`TenantJobEnqueuerTest.jobIdIsStableAcrossCalls` 断言同字符串两次转换得到同一 UUID，`delete` 用同一转换找回作业。

`TenantJobFilter` 实现 `JobServerFilter`（不是空的 `JobFilter` 标记接口）：`onProcessing(Job)` 读 `tenant:` 标签设置上下文，缺失即把重试次数置零并抛异常；`onProcessingFailed` 与 `onFailedAfterRetries` 里清理上下文且幂等。`TracingJobFilter` 同样实现 `JobServerFilter`。两个类与 `TenantJobEnqueuer` 都有中文 Javadoc 与 `@author jiangguifan`。

事务传播要注意：JobRunr 7 OSS 的 `DefaultSqlStorageProvider.save` 直接 `dataSource.getConnection()` 并自行 `commit`，不加入 Spring 事务，也不认 `TransactionAwareStorageProvider`（该类不存在）。要让「业务回滚则作业不落库」，`JobRunrConfig` 必须用 `TransactionAwareDataSourceProxy` 包一层作为 JobRunr 的 `DataSource`，或者改走 `@TransactionalEventListener(AFTER_COMMIT)` 入队。本计划选第一种，理由：入队点少且要同事务；第二种把入队推迟到提交后，回滚时业务行已回滚，两种都能满足断言，但第二种在提交后失败时作业静默丢失。该结论在 Task 2 Step 1 的 `TenantJobEnqueuerIT.jobIsInvisibleWhenBusinessTransactionRollsBack` 上验证，不成立则改第二种并登记 `deviations.md`。

测试断言：租户标签缺失时作业 FAILED 且重试次数为 0；两个不同租户的作业串行执行后上下文不残留；入队事务回滚时 `jobrunr_jobs` 无该 `jobId`；提交后作业可被 worker 取到。

### 2.3 Modulith 事件发布登记表

| 项 | 决定 |
|---|---|
| 依赖 | `spring-modulith-starter-core`、`spring-modulith-events-jdbc`、`spring-modulith-events-api` |
| 存储 | `event_publication` 与 `event_publication_archive`，由 Modulith 的 schema 脚本建，加进 `gac.furnace.tenant.ignore-tables` |
| 发布 | 域内一律 `ApplicationEventPublisher.publishEvent`，监听用 `@ApplicationModuleListener`（等价 `@TransactionalEventListener(AFTER_COMMIT)` 加 `@Async`） |
| 完成策略 | 监听方法正常返回后标记完成；抛异常则保持未完成 |
| 失败重投 | `EventRetryJob` 每分钟经 JobRunr 扫未完成且尝试次数小于 10 的记录重投；超过 10 次记 error 日志并跳过 |
| 清理 | `EventArchiveJob` 每天归档 7 天前的完成记录 |
| 事件清单 | `KnowledgeIngestedEvent`（入库完成，wiki 与 agent 用）、`KnowledgeDeletedEvent`（文档软删，wiki 与 session 用）、`KnowledgeBaseDeletedEvent`（知识库删除，storage 与 vectorstore 用）、`DocumentMovedEvent`（文档搬迁，session 用） |

事件放 `knowledge.api`，字段用 record，全部字段在创建点显式赋值。跨域消费方只能读事件，不能回调 knowledge 的内部类。

# 3. 对前置计划的新增需求

计划 02 与 03 尚未落盘，下面各项在对应计划里没有声明时，由执行方在本计划 Task 1 停下来登记 `deviations.md` 并通知用户，不在 knowledge 域内另建一份。

| 前置 | 需要的接口或能力 | 用途 |
|---|---|---|
| 计划 02 | 检索入口（租户、知识库 ID、查询文本、查询向量、向量与关键词开关）；`spi.KnowledgeSearchSource` 与三个 facts record；向量写入与删除入口 | hybrid-search、入库向量化、ES 与 Milvus 隔离 |
| 计划 03 | `model.api` 模型查询（嵌入、摘要、VLM、ASR）；`storage.api` 文件读写与签名 URL；`auth.api` 当前主体与权限点；`audit.api` 审计写入；`system.api` 解析引擎选项；`tenant.api` 默认存储后端 | 知识库与文档的完整用例 |

计划 02 另外要求本计划交付两件：`embeddings` 表的 DDL 与 PGroonga 索引（计划 02 Task 6 给 SQL 文本，建表在本文 Task 4 的 DDL 脚本里）；`contract.retrieval.SearchTextUtil` 与 `platform.text.CleanInvalidUtf8` 迁进 knowledge（计划 02 第 3 节新增需求第 4 条）。两件分别落到 Task 4 与 Task 15 的 Files，不另立任务。

对计划 06 的回应：`KnowledgeQuestionApi` 的四个方法按计划 06「对外承诺」实现，签名见本文「对外承诺」。`filterExistingKbIds` 同时供 `KnowledgeBaseQueryApi` 使用，实现类 `KnowledgeQuestionService` 只写一份，`KnowledgeBaseQueryApi.filterExistingKbIds` 委托给它。

对计划 05 的回应：wiki 与 datasource 需要的 `listTextChunks`、`findChunks`、`enrichImageInfo`、`deleteChunk`、`finalizeSubtask`、`findByExternalRefs`、`findByExternalRefPrefix`、`updateMetadata`、`resolveAutoTagIds` 全部写进「对外承诺」；`KnowledgeIngestedEvent` 带 `language` 字段。

对计划 07 的回应：附件入库用 `KnowledgeContentApi.createFromFile`，返回的知识 ID 与 `PENDING` 状态即「已受理」；`ragagent.role` 取值 `api`、`worker`、`all`，`RoleGatedWebConfig` 负责 HTTP 门。

# 4. 任务

每个任务给出 Files、Interfaces、带复选框的 Step。路径前缀约定：`M` 为 `backend/app/src/main/java/com/ragagent/knowledge`，`T` 为 `backend/app/src/test/java/com/ragagent/knowledge`，`R` 为 `backend/app/src/main/resources`，`C` 为 `backend/app/src/main/java/com/ragagent/config`。验证命令只依赖该任务及之前的产物。

### Task 1: 冻结点核对与盘点校准

**Files:**
- Create: `docs/migration/knowledge-job-map.md`
- Modify: 本计划（盘点有差异时）

**Interfaces:**
- Consumes: 计划 01 的 `docs/migration/frozen-bugs.md`、`progress.md`、`deviations.md`
- Produces: 校准后的盘点；`knowledge-job-map.md` 一行一条：旧后台任务、JobRunr 作业类、触发点、重试次数、同事务写入的表

- [ ] **Step 1: 读冻结后修复并核对规模**
```bash
git log --oneline rebuild-freeze..main -- domains/src/main/java/com/ragagent/knowledge
grep -n "knowledge" docs/migration/frozen-bugs.md
find domains/src/main/java/com/ragagent/knowledge -name '*.java' | wc -l
find domains/src/main/java/com/ragagent/knowledge -name '*.java' -print0 | xargs -0 cat | wc -l
```
Expected: 283 与 30590。`frozen-bugs.md` 里本域条目抄进后续任务的测试断言；有 `knowledge` 相关修复时在同一次提交里改本计划。

- [ ] **Step 2: 核对三处易错结论**
```bash
grep -rn "video_info" --include=*.java --include=*.xml --include=*.sql domains | head -20
grep -rn "is_temporary\|isTemporary" --include=*.java --include=*.xml domains | head -20
grep -rn "getSignedFileUrlByKnowledge" frontend/src
grep -rn "findByIdAny" --include=*.java domains/src/main/java/com/ragagent/knowledge | wc -l
```
Expected: `video_info` 只命中 `TestSchema` 建表与基线 DDL，无 Java 读写；`is_temporary` 命中 `KnowledgeBaseService.provisionChatHistoryKnowledgeBase`、`listActiveByTenant`、`listMoveTargets`、`SqlInjectionAnalyzer`，即必须保留该列（**Task 1 实测：`AgentEngineAssembler` 不命中，已从本条与 §1.3 移除**）；`getSignedFileUrlByKnowledge` 只有定义与 `signedMedia.ts:17` 的 `getSignedFileUrl` 注入；`findByIdAny` 20 处（`grep` 计数含 `ChunkRepository`、`KnowledgeRepository`、`KnowledgeBaseRepository` 声明与 17 处调用）。不符则改本文对应段落。

- [ ] **Step 3: 写作业映射表**
按 1.4 表逐行写全，另加一列「同事务写入的表」。

- [ ] **Step 4: 提交**
提交：`git add docs/migration/knowledge-job-map.md docs/optimization/plans/2026-11-10-rebuild-04-knowledge.md` 后 `git commit -m "docs(knowledge): 作业映射表与盘点校准"`。

### Task 2: JobRunr 接入与角色开关

**Files:**
- Modify: `backend/pom.xml`（`jobrunr.version`）、`backend/app/pom.xml`
- Create: `C/RagAgentRole.java`、`C/RoleGatedWebConfig.java`、`C/WorkerRoleRequestGate.java`、`C/JobRunrConfig.java`、`C/TenantScopedJob.java`、`C/TenantJobFilter.java`、`C/TracingJobFilter.java`、`C/TenantJobEnqueuer.java`、`C/JobRunrDashboardConfig.java`
- Modify: `R/application.yml`（`ragagent.role`、`ragagent.jobrunr.*`）
- Create: `T/config/{RagAgentRoleTest,TenantJobFilterTest,TenantJobEnqueuerTest,TenantJobEnqueuerIT}.java`

**Interfaces:**
- Consumes: 计划 01 的租户上下文与 `PostgresContainerTest`
- Produces:
  - `enum RagAgentRole { API, WORKER, ALL }`，`static RagAgentRole from(String raw)`，非法值抛 `IllegalStateException`
  - `interface TenantScopedJob { long tenantId(); void run(); }`
  - `class TenantJobEnqueuer`：四个方法，签名见 2.2
  - `class TenantJobFilter implements JobServerFilter`、`class TracingJobFilter implements JobServerFilter`
  - `class RoleGatedWebConfig` 与 `class WorkerRoleRequestGate extends OncePerRequestFilter`
  - `class JobRunrConfig`、`class JobRunrDashboardConfig`

- [ ] **Step 1: 写失败测试**
`RagAgentRoleTest.invalidRoleFailsFast`、`apiRoleHasHttpOnly`、`workerRoleHasWorkerOnly`、`allRoleHasBoth`。
`TenantJobFilterTest.missingTenantLabelFailsWithoutRetry`（断言作业状态 FAILED 且重试次数为 0）、`tenantLabelSetsContextAndClearsAfterwards`、`twoTenantsDoNotLeakContext`。
`TenantJobEnqueuerIT.jobIsInvisibleWhenBusinessTransactionRollsBack`、`jobIsExecutedAfterCommit`、`duplicateJobIdIsIgnored`（同字符串两次 `enqueue`，`jobrunr_jobs` 只有一行）、`recurrentJobRegisteredWithCron`。
`TenantJobEnqueuerTest.jobIdIsStableAcrossCalls`、`enqueueResolvesJobBeanFromContext`（作业实例由容器创建，捕获的闭包参数只有租户与业务 ID）。
Run: `MVN test -Dtest=RagAgentRoleTest,TenantJobFilterTest,TenantJobEnqueuerTest,TenantJobEnqueuerIT`
Expected: FAIL，类不存在。

- [ ] **Step 2: 实现**
`JobRunrConfig` 按 `RagAgentRole.from(properties.role)` 决定 `BackgroundJobServer` 是否启用，并把 `DataSource` 用 `TransactionAwareDataSourceProxy` 包一层注册给 JobRunr 的存储层；`TenantJobEnqueuer` 把字符串 `jobId` 经 `UUID.nameUUIDFromBytes` 转成作业 id，按 2.2 的两种写法之一入队，标签含 `tenant:<id>` 与可选 `trace:<traceparent>`；`delete` 用同一转换调 `jobScheduler.delete(uuid)`，返回布尔不抛。
`RoleGatedWebConfig` 用 `FilterRegistrationBean` 注册 `WorkerRoleRequestGate`，顺序取计划 01 `FilterOrders` 里紧邻 `TENANT`（-50）之前的一个新常量 `ROLE_GATE = -45`，早于鉴权，`shouldNotFilter` 放行 `/actuator/`。`FilterOrders` 在计划 01 Task 7 落盘，本任务只追加这一个常量并在 `deviations.md` 登记，不改既有取值。
`application.yml` 增加 `ragagent.role: ${RAGAGENT_ROLE:all}`、`ragagent.jobrunr.dashboard.enabled: false`、`ragagent.jobrunr.tenant-label-prefix: tenant:`、`ragagent.jobrunr.trace-label-prefix: trace:`。`jobrunr_*` 与 `event_publication*` 加入 `gac.furnace.tenant.ignore-tables`。

- [ ] **Step 3: 跑绿并反向验证**
Run: `MVN test -Dtest=RagAgentRoleTest,TenantJobFilterTest,TenantJobEnqueuerTest,TenantJobEnqueuerIT`
Expected: PASS。再把 `JobRunrConfig` 注册给 JobRunr 的 DataSource 从 `TransactionAwareDataSourceProxy` 临时换回裸 DataSource，`jobIsInvisibleWhenBusinessTransactionRollsBack` 必须变红；恢复实现，不留开关。若换回裸 DataSource 后该用例仍绿，说明 JobRunr 已自行加入 Spring 事务，删掉代理并在 `deviations.md` 登记。

- [ ] **Step 4: 提交**
提交：`git add backend/pom.xml backend/app/pom.xml backend/app/src/main/java/com/ragagent/config/RagAgentRole.java backend/app/src/main/java/com/ragagent/config/RoleGatedWebConfig.java backend/app/src/main/java/com/ragagent/config/WorkerRoleRequestGate.java backend/app/src/main/java/com/ragagent/config/JobRunrConfig.java backend/app/src/main/java/com/ragagent/config/TenantScopedJob.java backend/app/src/main/java/com/ragagent/config/TenantJobFilter.java backend/app/src/main/java/com/ragagent/config/TracingJobFilter.java backend/app/src/main/java/com/ragagent/config/TenantJobEnqueuer.java backend/app/src/main/java/com/ragagent/config/JobRunrDashboardConfig.java backend/app/src/main/resources/application.yml backend/app/src/test/java/com/ragagent/knowledge/config` 后 `git commit -m "feat(config): JobRunr 接入与 api/worker 角色开关"`。

### Task 3: Modulith 事件发布登记表

**Files:**
- Modify: `backend/app/pom.xml`（`spring-modulith-events-jdbc`）
- Create: `C/ModulithEventsConfig.java`、`C/EventRetryJob.java`、`C/EventArchiveJob.java`、`M/api/KnowledgeIngestedEvent.java`、`M/api/KnowledgeDeletedEvent.java`、`M/api/KnowledgeBaseDeletedEvent.java`、`M/api/DocumentMovedEvent.java`、`T/config/{EventRegistryIT,EventRetryJobTest}.java`
- Modify: `R/application.yml`

**Interfaces:**
- Consumes: Task 2 的 `TenantJobEnqueuer`
- Produces:
  - `record KnowledgeIngestedEvent(long tenantId, long kbId, long knowledgeId, String language)`、`record KnowledgeDeletedEvent(long tenantId, long kbId, long knowledgeId)`、`record KnowledgeBaseDeletedEvent(long tenantId, long kbId)`、`record DocumentMovedEvent(long tenantId, long knowledgeId, long fromKbId, long toKbId)`
  - `class EventRetryJob implements TenantScopedJob`、`class EventArchiveJob implements TenantScopedJob`

- [ ] **Step 1: 写失败测试**
`EventRegistryIT.listenerSuccessMarksPublicationComplete`、`listenerThrowsKeepsPublicationIncomplete`、`listenerRunsOnlyAfterCommit`（回滚事务里发事件，监听不执行，表无记录）。
`EventRetryJobTest.resubmitsIncompleteAndIncrementsAttempts`、`givesUpAfterTenAttemptsAndLogsError`。
Run: `MVN test -Dtest=EventRegistryIT,EventRetryJobTest`
Expected: FAIL，配置与表不存在。

- [ ] **Step 2: 实现**
`ModulithEventsConfig` 声明 JDBC 版 `EventPublicationRepository`，`spring.modulith.events.jdbc.schema-initialization.enabled=true`。`EventRetryJob` 调 `EventPublications.resubmitIncompletePublications`，单次上限 50 条，尝试次数达到 10 记录 error 并跳过。`EventArchiveJob` 调 `archiveCompletedPublications`。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=EventRegistryIT,EventRetryJobTest`
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/pom.xml backend/app/src/main/java/com/ragagent/config/ModulithEventsConfig.java backend/app/src/main/java/com/ragagent/config/EventRetryJob.java backend/app/src/main/java/com/ragagent/config/EventArchiveJob.java backend/app/src/main/java/com/ragagent/knowledge/api/KnowledgeIngestedEvent.java backend/app/src/main/java/com/ragagent/knowledge/api/KnowledgeDeletedEvent.java backend/app/src/main/java/com/ragagent/knowledge/api/KnowledgeBaseDeletedEvent.java backend/app/src/main/java/com/ragagent/knowledge/api/DocumentMovedEvent.java backend/app/src/main/resources/application.yml backend/app/src/test/java/com/ragagent/knowledge/config` 后 `git commit -m "feat(config): Modulith 事件发布登记表与失败重投"`。

### Task 4: DDL、行级安全与跨租户拒绝

**Files:**
- Create: `R/db/migration/allenv/V<D>_<序号>__DDL_rebuild_knowledge_tables_jiangguifan.sql`、`T/repository/{KnowledgeSchemaTest,KnowledgeRowLevelSecurityTest}.java`。`<D>` 取写脚本当天 `yyyyMMdd`，`<序号>` 取 `ls R/db/migration/allenv | grep <D>` 的下一个两位序号；计划 01 已占用同日 `_01`，本脚本从 `_02` 起，不写死 `_01`

**Interfaces:**
- Consumes: 计划 01 Task 5 的 fail-closed 策略模板与 `PostgresContainerTest.execAsOwner`
- Produces: 9 张业务表与 `embeddings` 表、每张租户表一条策略、PGroonga 扩展与 `embeddings` 全文索引，字段映射见 1.3

- [ ] **Step 1: 写失败的 schema 测试**
`KnowledgeSchemaTest.createsAllNineTables`、`everyTenantTableHasRowLevelSecurity`、`chunksHasNoVideoInfoColumn`、`chunksHasNoLegacyPinColumns`、`knowledgeBasesKeepsIsTemporary`、`taskProgressPartialUniqueIndexExists`、`createsEmbeddingsAndPgRoongaIndex`（断言 `embeddings` 表存在、`embeddings_content_pgroonga` 索引存在、`CREATE EXTENSION IF NOT EXISTS pgroonga` 生效）。
`KnowledgeRowLevelSecurityTest` 每张表一条 `rejectsCrossTenantReadOn<Table>`：租户 1 写入后租户 2 读不到、更新影响 0 行、删除影响 0 行（`embeddings` 不在此列，它由计划 02 的检索 SQL 保留谓词，行级安全仍兜底）。
Run: `MVN test -Dtest=KnowledgeSchemaTest,KnowledgeRowLevelSecurityTest`
Expected: FAIL，表不存在。

- [ ] **Step 2: 写 DDL**
每张租户表带策略，模板固定：
```sql
ALTER TABLE knowledge_bases ENABLE ROW LEVEL SECURITY;
ALTER TABLE knowledge_bases FORCE ROW LEVEL SECURITY;
CREATE POLICY knowledge_bases_tenant ON knowledge_bases
  USING (
    current_setting('app.tenant_id', true) = 'system'
    OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint
  )
  WITH CHECK (
    current_setting('app.tenant_id', true) = 'system'
    OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint
  );
```
部分唯一索引：`CREATE UNIQUE INDEX uq_knowledge_task_progress_active ON knowledge_task_progress (tenant_id, task_type, subject_id) WHERE status IN ('PENDING','RUNNING')`。

`embeddings` 表与 PGroonga 索引同脚本建（计划 02 第 3 节交付说明与 Task 6 的 SQL 文本）：
```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgroonga;
CREATE TABLE embeddings (
  id bigint PRIMARY KEY,
  tenant_id bigint NOT NULL,
  source_id bigint,
  source_type varchar(32),
  chunk_id bigint,
  knowledge_id bigint,
  knowledge_base_id bigint,
  tag_id bigint,
  dimension integer,
  is_enabled boolean DEFAULT true,
  content text,
  embedding halfvec
);
ALTER TABLE embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE embeddings FORCE ROW LEVEL SECURITY;
CREATE POLICY embeddings_tenant ON embeddings USING (
  current_setting('app.tenant_id', true) = 'system'
  OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint
) WITH CHECK (
  current_setting('app.tenant_id', true) = 'system'
  OR tenant_id = nullif(current_setting('app.tenant_id', true), '')::bigint
);
CREATE INDEX embeddings_content_pgroonga ON embeddings
  USING pgroonga (content pgroonga_text_full_text_search_ops_v2);
```
列名与计划 02 的 pgvector 检索 SQL 一一对应，改名要同步改计划 02 的 SQL。

- [ ] **Step 3: 跑绿并反向验证**
Run: `MVN test -Dtest=KnowledgeSchemaTest,KnowledgeRowLevelSecurityTest`
Expected: PASS。再把某张表的 `ENABLE ROW LEVEL SECURITY` 注释掉重跑，对应用例变红；恢复后全绿。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/resources/db/migration/allenv/V<D>_<序号>__DDL_rebuild_knowledge_tables_jiangguifan.sql backend/app/src/test/java/com/ragagent/knowledge/repository/KnowledgeSchemaTest.java backend/app/src/test/java/com/ragagent/knowledge/repository/KnowledgeRowLevelSecurityTest.java` 后 `git commit -m "feat(knowledge): 九张表 DDL、embeddings 表与行级安全策略"`。

### Task 5: 知识库聚合的 Entity、Mapper 与 Repository

**Files:**
- Create: `M/entity/{KnowledgeBase,KnowledgeTag,KnowledgeTagRelation,UserKbPin}.java`、`M/mapper/{KnowledgeBaseMapper,KnowledgeTagMapper,UserKbPinMapper}.java`、`R/mapper/knowledge/{KnowledgeTagMapper,UserKbPinMapper}.xml`、`M/repository/{KnowledgeBaseRepository,KnowledgeTagRepository,UserKbPinRepository}.java`、`T/repository/{KnowledgeBaseRepositoryIT,KnowledgeTagRepositoryIT,UserKbPinRepositoryIT}.java`

**Interfaces:**
- Consumes: Task 4 的表
- Produces:
  - `KnowledgeBaseRepository.create`、`findById`、`findByIds`、`listByCreator`、`updateConfig`、`updateStorageBinding`、`updateVectorStoreBinding`、`softDelete`、`countByVectorStore`、`countByStorageBackend`
  - `KnowledgeTagRepository.create`、`listByKb`、`findById`、`replaceRelations`、`findKnowledgeIdsByTags`、`isTagReferenced`、`deleteTag`、`nextSortKey`
  - `UserKbPinRepository.pin`、`unpin`、`pinnedKbIds`

- [ ] **Step 1: 写失败的 Repository 测试**
`KnowledgeBaseRepositoryIT.createsAndFindsInTenant`、`softDeletedRowIsInvisible`、`findByIdsSkipsForeignTenant`、`countsBindingsForVectorStoreAndStorageBackend`。
`KnowledgeTagRepositoryIT.duplicateLiveNameRejected`、`recreateAfterSoftDeleteSucceeds`、`replaceRelationsReplacesAll`、`findKnowledgeIdsByTagsReturnsUnion`、`isTagReferencedDetectsDocumentsAndFaqEntries`、`nextSortKeyIncrementsWithinTenant`。
`UserKbPinRepositoryIT.pinIsIdempotent`、`pinnedKbIdsScopedToUser`。
Run: `MVN test -Dtest=KnowledgeBaseRepositoryIT,KnowledgeTagRepositoryIT,UserKbPinRepositoryIT`
Expected: FAIL。

- [ ] **Step 2: 实现**
Repository 用 `@Service`，单表聚合继承 `ServiceImpl`，多表组合注入 Mapper。XML 只写 `nextSortKey` 与 `isTagReferenced`。所有 wrapper 不写 `tenant_id`，不写 `deleted_at` 条件。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=KnowledgeBaseRepositoryIT,KnowledgeTagRepositoryIT,UserKbPinRepositoryIT`
Expected: PASS，12 条。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/entity backend/app/src/main/java/com/ragagent/knowledge/mapper backend/app/src/main/java/com/ragagent/knowledge/repository backend/app/src/main/resources/mapper/knowledge backend/app/src/test/java/com/ragagent/knowledge/repository` 后 `git commit -m "feat(knowledge): 知识库聚合的实体、Mapper 与 Repository"`。

### Task 6: api 包、错误码与文案

**Files:**
- Create: `M/api/KnowledgeCode.java`、`M/api/{KnowledgeBaseBo,KnowledgeDocumentBo,KbIndexingCapabilityBo,KnowledgeScopeBo,FileUploadBo,UrlImportBo,ManualDocumentBo,ChunkTextBo,FinalizeSubtaskBo,HybridSearchQueryBo,GrepQueryBo,GrepResultBo,SearchResultPageBo}.java`、`M/api/{KnowledgeBaseQueryApi,KnowledgeContentApi,KnowledgeQuestionApi,KnowledgeIngestApi,KnowledgeSearchApi}.java`、`R/i18n/app-messages{,_zh_CN,_zh_HK,_en_US,_es_ES,_pt_BR,_ru_RU,_th_TH}.properties`、`C/KnowledgeCodeRegistrationConfig.java`、`T/api/KnowledgeCodeMessagesTest.java`

**Interfaces:**
- Consumes: 计划 01 的 `Code`、`CodeCache`
- Produces: `enum KnowledgeCode implements Code`（50 个取值，见 1.7）；五个 api 接口；启动时 `CodeCache.register(KnowledgeCode.values())`

- [ ] **Step 1: 写失败测试**
`KnowledgeCodeMessagesTest.everyCodeHasAllSevenLanguages`（遍历全部取值，七种语言各取一次，断言不等于 `code_<code>` 本身）、`codeCountMatchesDocumentedTable`（断言取值数量为 50）。
Run: `MVN test -Dtest=KnowledgeCodeMessagesTest`
Expected: FAIL，文案缺失。

- [ ] **Step 2: 实现**
枚举按 1.7 逐条写，类级 Javadoc 写明模块号 010，`getMessage()` 返回中文兜底。八份文案文件各 50 条；其余五种语言标 `machine-translated, pending review`。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=KnowledgeCodeMessagesTest`
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/api backend/app/src/main/java/com/ragagent/config/KnowledgeCodeRegistrationConfig.java backend/app/src/main/resources/i18n backend/app/src/test/java/com/ragagent/knowledge/api` 后 `git commit -m "feat(knowledge): api 包、错误码与七语文案"`。

### Task 7: 知识库用例与引用 Provider

**Files:**
- Create: `M/service/base/{KnowledgeBaseService,KnowledgeBaseBindingService,KnowledgeBaseSearchService,KnowledgeTagService,KnowledgeCloneService,KnowledgeRecycleService}.java`、`M/service/access/{KnowledgeAccessGuard,ChunkAccessGuard}.java`、`M/api/{KnowledgeBaseQueryService,KnowledgeModelReferenceProvider,KnowledgeVectorStoreReferenceProvider,KnowledgeStorageReferenceProvider}.java`、`T/service/base/{KnowledgeBaseServiceTest,KnowledgeTagServiceTest,KnowledgeCloneServiceTest,KnowledgeRecycleServiceTest}.java`、`T/api/ReferenceProvidersTest.java`

**Interfaces:**
- Consumes: Task 5 的 Repository，Task 6 的 api，计划 03 的 `auth.api`、`audit.api`、`model.api`、`vectorstore.api`、`storage.api`
- Produces:
  - `KnowledgeBaseService.create`、`update`、`delete`、`list`、`detail`、`rebuildIndex`、`duplicate`、`copy`、`togglePin`
  - `KnowledgeBaseQueryService implements KnowledgeBaseQueryApi`
  - `KnowledgeModelReferenceProvider`、`KnowledgeVectorStoreReferenceProvider`、`KnowledgeStorageReferenceProvider`
  - 归属校验不经单独类，由 Task 14 的 `KnowledgeSearchFactsSource.knowledgeBase` 承担，本任务只提供 `KnowledgeBaseQueryApi.isOwned` 供域内判定

- [ ] **Step 1: 写失败测试**
`KnowledgeBaseServiceTest.createRequiresName`、`createAppliesDefaultIndexingStrategy`、`updateRejectsBothIndexingDisabled`、`deleteRequiresOwnership`、`deletePublishesKnowledgeBaseDeletedEventAfterCommit`、`duplicateCopiesConfigButNotDocuments`、`copyRejectsForeignTargetKb`、`provisionChatHistoryCreatesHiddenKb`（断言 `isTemporary=true`、`name="__chat_history__"`、`type="document"`、描述为旧字符串）。幂等由调用方负责，旧 tenant 域在 `putChatHistory` 里判断「已有 kbId 且模型未变则沿用」，新实现同语义，本方法只负责建。
`KnowledgeTagServiceTest.createRejectsDuplicateName`、`deleteRejectsReferencedTag`。
`KnowledgeCloneServiceTest.cloneWritesProgressRowThenCompletes`、`cloneOfForeignKbRejectedWithKbNotFound`。
`KnowledgeRecycleServiceTest.cleanupAfterDeleteMarksDeleted`、`cleanupFailureMarksDeleteFailed`、`retryFailedCleanupsProcessesOnlyDeleteFailed`。
`ReferenceProvidersTest.modelProviderReportsKbsPerModelId`（同一模型 ID 命中 `embedding_model_id`、`vlm_config.modelId`、`wiki_config.synthesisModelId` 三种绑定位，`ResourceReferenceBo.resourceType` 分别为 `embedding_model`、`vlm_model`、`wiki_synthesis_model`）、`modelProviderIgnoresSoftDeletedKb`、`vectorStoreProviderCountsBindings`、`storageProviderCountsBindings`、`storageProviderExcludesSoftDeletedKb`。
Run: `MVN test -Dtest=KnowledgeBaseServiceTest,KnowledgeTagServiceTest,KnowledgeCloneServiceTest,KnowledgeRecycleServiceTest,ReferenceProvidersTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
每个用例拆成前置校验、核心步骤、提交后动作三个私有方法。`delete` 事务内软删，事务内不删向量与文件；提交后发 `KnowledgeBaseDeletedEvent`，`KnowledgeRecycleService` 监听并调 retrieval-engine 与 `storage.api` 清理。`copy` 与 `move` 的目标库归属判定走 `@IgnoreTenant` 的 `resolveOwnershipAcrossTenants`。
三个 Provider 各一个方法，返回值统一 `List<storage.api.ResourceReferenceBo>`，只读本租户未软删行，不写库。模型引用匹配的字段集照旧 `ModelService.kbBindings`（`ModelService.java:375-391`）：`embedding_model_id`、`summary_model_id`、`image_processing_config.modelId`、`vlm_config.modelId`、`asr_config.modelId`、`wiki_config.synthesisModelId`；`question_generation_config` 旧实现不查，沿用旧语义不新增绑定位。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=KnowledgeBaseServiceTest,KnowledgeTagServiceTest,KnowledgeCloneServiceTest,KnowledgeRecycleServiceTest,ReferenceProvidersTest`
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/service/base backend/app/src/main/java/com/ragagent/knowledge/service/access backend/app/src/main/java/com/ragagent/knowledge/api backend/app/src/test/java/com/ragagent/knowledge/service/base backend/app/src/test/java/com/ragagent/knowledge/api` 后 `git commit -m "feat(knowledge): 知识库用例与引用 Provider"`。

### Task 8: 知识库、标签与文件 Controller

**Files:**
- Create: `M/controller/{KnowledgeBaseController,KnowledgeTagController,KnowledgeFileController}.java`、`M/vo/{CreateKnowledgeBaseVo,UpdateKnowledgeBaseVo,KnowledgeBaseVo,KnowledgeBaseListItemVo,MoveTargetVo,CreateTagVo,UpdateTagVo,TagVo,SignedFileUrlVo}.java`、`M/assembler/KnowledgeBaseAssembler.java`、`T/controller/{KnowledgeBaseControllerTest,KnowledgeTagControllerTest,KnowledgeFileControllerTest}.java`

**Interfaces:**
- Consumes: Task 7 的 Service
- Produces: 知识库、标签、文件三组路径，见 1.2

- [ ] **Step 1: 写失败的 MockMvc 测试**
`KnowledgeBaseControllerTest.createReturnsStringId`、`updateRejectsInvalidIndexing`（断言 `1001010004`）、`deleteRequiresOwnershipMapsTo403`、`copyMovesSourceIdIntoPath`、`pinUsesPost`。
`KnowledgeTagControllerTest.createTagReturnsSortKeyAsString`、`deleteReferencedTagReturns`、`tagIdSerializedAsString`。
`KnowledgeFileControllerTest.signedUrlReadsFilePathFromBody`、`signedUrlRejectsPathOutsideExports`（断言 `1001010047`）。
Run: `MVN test -Dtest=KnowledgeBaseControllerTest,KnowledgeTagControllerTest,KnowledgeFileControllerTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
控制器只做绑定、`@Valid`、调一个 Service。`SignedFileUrlVo` 字段 `url` 与 `expiresInSeconds`，与旧形状一致。路径常量收口到 `KnowledgePaths`。

- [ ] **Step 3: 跑绿**
Run:
```bash
MVN test -Dtest=KnowledgeBaseControllerTest,KnowledgeTagControllerTest,KnowledgeFileControllerTest
MVN test -Dtest=OpenApiExportTest
```
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/controller backend/app/src/main/java/com/ragagent/knowledge/vo backend/app/src/main/java/com/ragagent/knowledge/assembler backend/app/src/test/java/com/ragagent/knowledge/controller` 后 `git commit -m "feat(knowledge): 知识库、标签与文件 Controller"`。

### Task 9: 文档聚合的 Entity、Mapper 与 Repository

**Files:**
- Create: `M/entity/{Knowledge,KnowledgeProcessingSpan,KnowledgeTaskProgress}.java`、`M/mapper/{KnowledgeMapper,KnowledgeSpanMapper,KnowledgeTaskProgressMapper}.java`、`R/mapper/knowledge/{KnowledgeMapper,KnowledgeSpanMapper,KnowledgeTaskProgressMapper}.xml`、`M/repository/{KnowledgeRepository,IngestStateRepository,KnowledgeSpanRepository,KnowledgeTaskProgressRepository}.java`、`T/repository/{KnowledgeRepositoryIT,IngestStateRepositoryTest,KnowledgeTaskProgressRepositoryIT}.java`

**Interfaces:**
- Consumes: Task 4 的表
- Produces:
  - `KnowledgeRepository.create`、`findById`、`findByIds`、`query`、`findByHash`、`findByExternalId`、`updateFolderPath`、`updateMetadata`、`softDelete`、`softDeleteByKb`
  - `IngestStateRepository.claimPending`、`promoteToFinalizing`、`markIndexedEnabledIfOwned`、`completeIfOwned`、`failIfProcessing`、`cancelIfParsing`、`resetForReparse`、`releaseSubtask`、`retryDeleteFailed`
  - `KnowledgeSpanRepository.openAttempt`、`begin`、`finish`、`findByKnowledge`、`touchKnowledgeUpdatedAt`
  - `KnowledgeTaskProgressRepository.open`、`update`、`find`、`tryClaimFaqImport`、`expireOlderThan`

- [ ] **Step 1: 写失败的 Repository 测试**
`KnowledgeRepositoryIT.findByHashOnlyMatchesLiveRow`、`queryFiltersByParseStatusAndFolder`、`findByExternalIdMatchesJsonbMetadata`。
`IngestStateRepositoryTest.claimPendingWinsOnceForSameAttempt`、`staleAttemptCannotClaimAfterReparse`、`promoteToFinalizingRequiresProcessing`、`releaseSubtaskDecrementsAndCompletesAtZero`、`failIfProcessingTruncatesMessageAt2000`。
`KnowledgeTaskProgressRepositoryIT.secondActiveFaqImportForSameKbRejectedByPartialIndex`、`completedProgressAllowsNewImport`、`expireRemovesOldRows`。
Run: `MVN test -Dtest=KnowledgeRepositoryIT,IngestStateRepositoryTest,KnowledgeTaskProgressRepositoryIT`
Expected: FAIL。

- [ ] **Step 2: 实现**
状态推进用 XML 条件 UPDATE 返回影响行数，例：`UPDATE knowledges SET parse_status='processing' WHERE id=#{id} AND parse_status='pending' AND parse_attempt=#{attempt} AND deleted_at='1970-01-01 00:00:00+00'`。`releaseSubtask` 递减后在同事务判 0 晋升 completed。`tryClaimFaqImport` 直接 insert，捕获 `DuplicateKeyException` 后抛 `FAQ_IMPORT_RUNNING`。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=KnowledgeRepositoryIT,IngestStateRepositoryTest,KnowledgeTaskProgressRepositoryIT`
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/entity backend/app/src/main/java/com/ragagent/knowledge/mapper backend/app/src/main/java/com/ragagent/knowledge/repository backend/app/src/main/resources/mapper/knowledge backend/app/src/test/java/com/ragagent/knowledge/repository` 后 `git commit -m "feat(knowledge): 文档聚合与状态机 Repository"`。

### Task 10: 入库主链落成 JobRunr 作业

**Files:**
- Create: `M/service/ingest/{DocumentIngestionJob,DocumentIngestionPipeline,IngestStateMachine,DocumentPreCleaner,DocumentIndexStage,DocumentEmbeddingStage,EmbeddingModelResolver,DocumentFinalizingStage,SubtaskCoordinator,EnrichmentFanOut,KnowledgeIngestService}.java`、`M/service/ingest/{IngestSpanRecorder,IngestSpanTree,SpanHeartbeat,IngestStage}.java`、`C/KnowledgeIngestAssemblyConfig.java`、`T/service/ingest/{DocumentIngestionJobIT,IngestStateMachineTest,EnrichmentFanOutTest}.java`

**Interfaces:**
- Consumes: Task 2 的 `TenantJobEnqueuer`、`TenantScopedJob`；Task 3 的事件；Task 9 的 Repository；Task 11 的 `DocReaderClient`
- Produces:
  - `DocumentIngestionJob implements TenantScopedJob`，静态 `jobId(long knowledgeId, long attempt)`
  - `DocumentIngestionPipeline.run(Knowledge knowledge)`：预清理、取文本、分块、向量化、晋升 finalizing 五段，阶段名沿用 `IngestStage` 的五个 code（`docreader`、`chunking`、`embedding`、`multimodal`、`postprocess`）
  - `IngestStateMachine.claim`、`promote`、`complete`、`fail`、`isAborted`
  - `SubtaskCoordinator.release(long knowledgeId)`
  - `EnrichmentFanOut.enqueue(Knowledge knowledge)`
  - `KnowledgeIngestService implements KnowledgeIngestApi`

- [ ] **Step 1: 写失败的集成测试**
`DocumentIngestionJobIT`（Testcontainers 起 PostgreSQL，JobRunr 用 `jobrunr.database.type=sql` 指向同一容器，测试内同步触发作业，docreader 与模型用桩）：
`ingestsPlainTextAndCompletes`（最终 `parse_status=COMPLETED`、`enable_status=enabled`、`processed_at` 非空）
`rerunAfterReparseIgnoresStaleAttempt`
`failureMarksFailedAndTruncatesError`
`abortsSilentlyWhenCancelled`
`preCleanRunsBeforeDocreader`（桩记录调用顺序）
`withWikiEnabledPromotesToFinalizingAndWaitsSubtasks`
`withoutEnrichmentCompletesDirectly`
`jobCarriesTenantLabelAndRunsInThatTenant`
`IngestStateMachineTest.claimIsCas`、`cancelOnlyFromPendingOrProcessing`、`abortedWhenDeletingOrCancelled`。
`EnrichmentFanOutTest.enqueuesOnlyEnabledSubjobs`、`releasesSlotImmediatelyWhenEnqueueFails`。
Run: `MVN test -Dtest=DocumentIngestionJobIT,IngestStateMachineTest,EnrichmentFanOutTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
`KnowledgeIngestService.enqueueIngestion` 在调用方事务内读行取 `parse_attempt`，调 `tenantJobEnqueuer.enqueue(tenantId, DocumentIngestionJob.jobId(knowledgeId, attempt), job)`。入库作业重试次数 0，与旧「主处理无重试，失败即 failed」一致。
`DocumentIngestionPipeline` 每阶段独立提交，阶段间检查 `isAborted`，命中即静默返回。`IngestSpanRecorder` 的写入是 best-effort，失败只 warn。`SpanHeartbeat` 每分钟推进 `knowledges.updated_at`。
`DocumentPreCleaner` 顺序固定：删旧 chunks 行、删旧向量（向量启用且模型可用）、删旧图谱、删旧抽取图片；模型解析失败发生在预清理之前。
`SubtaskCoordinator.release` 在独立事务递减富化子任务计数。主链晋升 `finalizing` 那一刻（`DocumentIngestionPipeline` 第五段结束时）若计数为零就直接发 `KnowledgeIngestedEvent`，否则等最后一个 `release` 归零时发。事件只发一次，用 `knowledges.ingested_event_at` 的 CAS 保证。wiki 依赖这个事件启动摄取，主链不调 `release`，所以不能只挂在 `release` 归零上。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=DocumentIngestionJobIT,IngestStateMachineTest,EnrichmentFanOutTest`
Expected: PASS。作业 FAILED 时 `parse_status` 是 `failed` 而不是 `processing`。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/service/ingest backend/app/src/main/java/com/ragagent/config/KnowledgeIngestAssemblyConfig.java backend/app/src/test/java/com/ragagent/knowledge/service/ingest` 后 `git commit -m "feat(knowledge): 入库主链改为 JobRunr 作业"`。

### Task 11: docreader 客户端、解析器登记表与媒体阶段

**Files:**
- Create: `M/client/{DocReaderClient,DocReaderProperties}.java`、`M/service/parser/{DocumentParser,DocumentParserRegistry,DocreaderParser,UrlParser,ManualContentParser,MediaParser,ParserEngineCatalog}.java`、`M/service/ingest/{ImageOcrStage,MediaTranscriptionStage,CoverExtractor,OcrImagePreprocessor,FfmpegMedia,MediaFileTypes}.java`、`C/DocReaderChannelConfig.java`
- Modify: `backend/pom.xml`、`backend/app/pom.xml`（grpc-java 与 protobuf 插件）
- Create: `T/client/{DocReaderClientTest,DocumentParserRegistryTest}.java`、`T/service/ingest/{ImageOcrStageTest,MediaTranscriptionStageTest}.java`

**Interfaces:**
- Consumes: 旧 `docreader/docreader.proto`（过渡）。计划 09 改指 `services/docreader/proto`
- Produces:
  - `DocReaderClient.read(ReadCommand)`、`listEngines()`、`reconnect()`；一个 `ManagedChannel`，单次调用 30 分钟，流式等待加 10 秒，最大入站 64MB，`ReadStream` 遇 `UNIMPLEMENTED` 回退 unary
  - `interface DocumentParser { boolean supports(SourceType type, String fileType); ParsedDocument parse(ParseCommand command); }`，四个实现注册 Bean，`DocumentParserRegistry` 注入 `List<DocumentParser>` 按 `supports` 选一
  - `DocReaderProperties`，配置键 `ragagent.docreader.address`、`ragagent.docreader.call-timeout`、`ragagent.docreader.max-inbound-message-size`

- [ ] **Step 1: 写失败的测试**
`DocReaderClientTest.streamsMetaBeforeImages`、`fallsBackToUnaryWhenUnimplemented`、`businessErrorComesFromResponseField`、`timeoutSurfacesAsDocreaderTimeout`（注入 100ms 超时）。
`DocumentParserRegistryTest.selectsBySourceTypeAndFileType`、`manualContentBypassesDocreader`、`mediaSelectsMediaParser`、`noParserThrowsDocreaderParseFailed`。
`ImageOcrStageTest.downscalesLargeImagePreservingAspect`、`skipsWhenEndpointBlank`、`limitsPagesToConfiguredMax`。
`MediaTranscriptionStageTest.rejectsDurationOver600Seconds`、`writesMediaRefsIntoMetadata`、`extractsCoverJpeg`。
Run: `MVN test -Dtest=DocReaderClientTest,DocumentParserRegistryTest,ImageOcrStageTest,MediaTranscriptionStageTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
`DocReaderChannelConfig` 只建一个 `ManagedChannel`，`@Bean(destroyMethod = "shutdownNow")`，不引入 `net.devh` starter。`protoSourceRoot` 暂指 `${project.basedir}/../../docreader`，文件头注释写明过渡做法与计划 09 的切换路径。
`MediaParser` 先 `FfmpegMedia` 抽音轨，再调 model-runtime 的 `AsrTranscriber`，封面由 `CoverExtractor` 生成，媒体引用写 `metadata.mediaRefs`。加载失败抛 `MEDIA_TRANSCRIBE_FAILED`，不降级为空正文。
`ParserEngineCatalog` 合并 8 个本地引擎与 docreader 远端发现，规则沿用旧实现：本地恒在，远端同名覆盖，远端独有追加。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=DocReaderClientTest,DocumentParserRegistryTest,ImageOcrStageTest,MediaTranscriptionStageTest`
Expected: PASS，全部用 mock stub，不需要真实 docreader。

- [ ] **Step 4: 提交**
提交：`git add backend/pom.xml backend/app/pom.xml backend/app/src/main/java/com/ragagent/knowledge/client backend/app/src/main/java/com/ragagent/knowledge/service/parser backend/app/src/main/java/com/ragagent/knowledge/service/ingest backend/app/src/main/java/com/ragagent/config/DocReaderChannelConfig.java backend/app/src/test/java/com/ragagent/knowledge/client backend/app/src/test/java/com/ragagent/knowledge/service/ingest` 后 `git commit -m "feat(knowledge): docreader 客户端与解析器登记表"`。

### Task 12: 富化作业、周期清扫与任务进度

**Files:**
- Create: `M/service/ingest/{ChunkExtractJob,QuestionGenerationJob,KnowledgeSummaryJob,SummarySourceReader,DocumentSummaryService,IngestionTimeoutSweeper,SummaryFailoverSweeper}.java`、`M/service/document/{KnowledgeCloneJob,DocumentMoveJob,DocumentMoveService}.java`、`C/RecurrentJobRegistration.java`、`T/service/ingest/{ChunkExtractJobTest,QuestionGenerationJobTest,KnowledgeSummaryJobTest,IngestionTimeoutSweeperTest}.java`、`T/config/RecurrentJobRegistrationTest.java`

**Interfaces:**
- Consumes: Task 2 的 `TenantJobEnqueuer`，Task 3 的事件，Task 9 的 Repository，Task 10 的 `SubtaskCoordinator`
- Produces:
  - `ChunkExtractJob`（重试 3 次）、`QuestionGenerationJob`（每 20 个 chunk 一个，重试 3 次）、`KnowledgeSummaryJob`（重试 3 次，stale 与 insufficient 不重试）
  - `IngestionTimeoutSweeper`：扫 `pending`、`processing`、`finalizing` 超时行，阈值 `max(1h, 文档超时) + 10min`，另扫 `delete_failed`
  - `SummaryFailoverSweeper`：超阈值的 `processing` 摘要置 `failed`
  - `DocumentMoveJob`、`KnowledgeCloneJob`：写 `knowledge_task_progress`
  - `RecurrentJobRegistration`：`ApplicationReadyEvent` 后注册本域四个周期作业（两个 sweeper、`EventRetryJob`、`EventArchiveJob`），`tenantId` 传 0 表示跨租户系统作业

- [ ] **Step 1: 写失败的测试**
`ChunkExtractJobTest.releasesSlotOnlyOnTerminalFailure`、`retriesThreeTimesThenReleases`、`successDecrementsSubtask`。
`QuestionGenerationJobTest.batchesTwentyChunksPerJob`、`preservesGeneratedQuestionsAcrossReruns`。
`KnowledgeSummaryJobTest.staleAndInsufficientAreNotRetried`、`regenerateWritesStatusTransitions`。
`IngestionTimeoutSweeperTest.marksStuckProcessingAsFailed`、`skipsRowsWithFreshHeartbeat`、`retriesDeleteFailedRows`。
`RecurrentJobRegistrationTest.registersFourRecurrentJobsWithCron`。
Run: `MVN test -Dtest=ChunkExtractJobTest,QuestionGenerationJobTest,KnowledgeSummaryJobTest,IngestionTimeoutSweeperTest,RecurrentJobRegistrationTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
富化作业的成功与失败都经 `SubtaskCoordinator.release`，只在终态释放，重试期间不释放。`jobId` 固定：`extract:{knowledgeId}`、`qgen:{knowledgeId}:{batchIndex}`、`summary:{knowledgeId}`。
两个 sweeper 用 `@IgnoreTenant` 取租户列表后逐租户处理，每次最多 200 条。开关 `ragagent.knowledge.housekeeping.enabled`，默认 true。
`DocumentMoveService` 事务内改 `knowledge_base_id` 与 `folder_path`，提交后发 `DocumentMovedEvent`。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=ChunkExtractJobTest,QuestionGenerationJobTest,KnowledgeSummaryJobTest,IngestionTimeoutSweeperTest,RecurrentJobRegistrationTest`
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/service/ingest backend/app/src/main/java/com/ragagent/knowledge/service/document backend/app/src/main/java/com/ragagent/config/RecurrentJobRegistration.java backend/app/src/test/java/com/ragagent/knowledge` 后 `git commit -m "feat(knowledge): 富化作业、周期清扫与任务进度"`。

### Task 13: FAQ 用例与导入作业

**Files:**
- Create: `M/service/faq/{FaqImportService,FaqImportJob,FaqBatchImporter,FaqImportValidator,FaqReplacePlanner,FaqIndexMergePlanner,FaqEntryCommandService,FaqEntryQueryService,FaqEntryProjector,FaqDuplicateChecker,FaqFieldBatchUpdater,FaqIndexWriter,FaqChunkCodec,FaqEntryCsvWriter}.java`、`M/repository/{FaqChunkRepository,ChunkQuestionQuery}.java`、`M/controller/FaqController.java`、`M/vo/{FaqEntryVo,FaqEntryPageVo,FaqBatchUpsertVo,FaqImportAcceptedVo,FaqImportProgressVo,FaqSearchVo,FaqFieldsBatchVo,FaqTagBatchVo,FaqDeleteVo}.java`、`T/service/faq/{FaqImportServiceTest,FaqImportValidatorTest,FaqReplacePlannerTest,FaqEntryCommandServiceTest,FaqEntryQueryServiceTest}.java`、`T/controller/FaqControllerTest.java`

**Interfaces:**
- Consumes: Task 9 的 Repository，Task 12 的 `FaqImportJob` 挂点，`KnowledgeSearchApi`（FAQ 检索）
- Produces:
  - `FaqImportService.upsertEntries(long kbId, FaqBatchUpsertBo payload)` 返回 `taskId`；`FaqImportService.progress(long kbId, String taskId)`
  - `FaqImportJob implements TenantScopedJob`，`jobId=faq-import:{taskId}`
  - `FaqEntryCommandService.create`、`update`、`delete`、`addSimilarQuestions`、`updateFieldsBatch`、`updateTagBatch`
  - `FaqEntryQueryService.list`、`get`、`exportCsv`、`exportJson`、`search`
  - FAQ 五组路径，见 1.2

- [ ] **Step 1: 写失败的测试**
`FaqImportServiceTest.rejectsEmptyEntries`、`rejectsInvalidMode`、`rejectsNonFaqKb`、`secondImportWhileRunningRejected`（断言 `1001010036`）、`acceptedTaskWritesProgressRowAndEnqueuesJob`。
`FaqImportValidatorTest.replaceModeKeepsUnchangedAnswers`、`replaceModeDeletesAbsentEntries`、`rejectsDuplicateStandardQuestion`（断言 `1001010037`）。
`FaqReplacePlannerTest.plansKeepAndDeleteSets`、`mergesIndexPlanWithoutDroppingTags`。
`FaqEntryCommandServiceTest.rejectsEntryFromOtherKb`、`requiresAtLeastOneAnswer`、`fieldBatchRebuildsVectorsForChangedRows`、`tagBatchValidatesTagScope`。
`FaqEntryQueryServiceTest.listFiltersByTagAndSearch`、`exportCsvEscapesQuotes`、`searchUsesHybridSearchApi`。
`FaqControllerTest.entryIdSerializedAsString`、`importReturnsTaskId`、`progressReadsKbSubresource`。
Run: `MVN test -Dtest=FaqImportServiceTest,FaqImportValidatorTest,FaqReplacePlannerTest,FaqEntryCommandServiceTest,FaqEntryQueryServiceTest,FaqControllerTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
导入受理顺序：校验知识库类型、校验模式、插入 `knowledge_task_progress`（靠部分唯一索引抢锁）、同事务投递 `FaqImportJob`。`FaqImportJob` 每 50 条一批，失败写明细 CSV 到存储并更新进度；`replace` 模式先算保留集与删除集再合并索引计划。
FAQ 条目编解码沿用旧 `FaqChunkCodec` 的 metadata 结构，`chunk_type='faq'`，`status` 写 1 或 2。

- [ ] **Step 3: 跑绿**
Run:
```bash
MVN test -Dtest=FaqImportServiceTest,FaqImportValidatorTest,FaqReplacePlannerTest,FaqEntryCommandServiceTest,FaqEntryQueryServiceTest,FaqControllerTest
MVN test -Dtest=OpenApiExportTest
```
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/service/faq backend/app/src/main/java/com/ragagent/knowledge/repository/FaqChunkRepository.java backend/app/src/main/java/com/ragagent/knowledge/repository/ChunkQuestionQuery.java backend/app/src/main/java/com/ragagent/knowledge/controller/FaqController.java backend/app/src/main/java/com/ragagent/knowledge/vo backend/app/src/test/java/com/ragagent/knowledge/service/faq backend/app/src/test/java/com/ragagent/knowledge/controller/FaqControllerTest.java` 后 `git commit -m "feat(knowledge): FAQ 用例、导入作业与 Controller"`。

### Task 14: 分块读写、推荐问题与检索 api

**Files:**
- Create: `M/entity/{Chunk,ChunkRevision,ChunkWriteStatus}.java`、`M/mapper/{ChunkMapper,ChunkRevisionMapper}.java`、`R/mapper/knowledge/{ChunkMapper,ChunkRevisionMapper}.xml`、`M/repository/{ChunkRepository,ChunkRevisionRepository}.java`、`M/service/chunk/{ChunkEditService,ChunkRevisionWriter,ChunkReadService,ChunkQuestionService,GeneratedQuestionWriter,QuestionPromptBuilder}.java`、`M/api/{KnowledgeSearchService,KnowledgeSearchFactsSource,KnowledgeResourceReferenceProvider}.java`、`T/repository/{ChunkRepositoryIT,ChunkRevisionRepositoryIT}.java`、`T/service/chunk/{ChunkEditServiceTest,ChunkQuestionServiceTest}.java`、`T/api/{KnowledgeSearchServiceTest,KnowledgeSearchFactsSourceTest}.java`

**Interfaces:**
- Consumes: Task 9 的 Repository，Task 5 的 `KnowledgeBaseRepository`，计划 02 的检索与向量入口与 `retrieval.spi.KnowledgeSearchSource`，计划 03 的模型 api 与 `storage.api.ResourceReferenceProvider`
- Produces:
  - `ChunkRepository.findById`、`findByKnowledge`、`findByIds`、`insertChunks`、`updateContent`、`updateMetadata`、`setEnabled`、`deleteById`、`deleteByKnowledge`
  - `ChunkRevisionRepository.append`、`findRevisions`、`findRevision`
  - `ChunkEditService.update`、`revert`、`delete`、`deleteByKnowledge`、`updateCover`
  - `ChunkQuestionService.upsert`、`delete`、`regenerate`
  - `KnowledgeSearchService implements KnowledgeSearchApi`
  - `KnowledgeSearchFactsSource implements retrieval.spi.KnowledgeSearchSource`：`knowledgeBase(String kbId)`、`accessibleDocuments(long tenantId, List<String> knowledgeIds)`、`chunks(long tenantId, List<String> chunkIds)`
  - `KnowledgeResourceReferenceProvider implements storage.api.ResourceReferenceProvider`：`Set<Long> knowledgeIdsOf(String resourceUrl)`

- [ ] **Step 1: 写失败的测试**
`ChunkRepositoryIT.insertKeepsPreNextChain`、`findByKnowledgeSkipsDisabled`、`deleteByKnowledgeHardDeletes`。
`ChunkRevisionRepositoryIT.revisionIncrementsPerChunk`、`revisionUniquePerChunk`。
`ChunkEditServiceTest.rejectsConflictingRevision`（断言 `1001010027`）、`revertRestoresPreviousContent`、`updateRebuildsVectorAndKeywordIndex`、`rejectsForeignChunk`。
`ChunkQuestionServiceTest.upsertsIntoMetadataAndResyncsIndex`、`rejectsNonTextChunk`、`regenerateUsesSummaryModel`、`deleteUnknownQuestionRejected`。
`KnowledgeSearchServiceTest.hybridSearchRequiresOwnedKb`（跨租户 kbId 抛 `KB_NOT_FOUND`）、`grepOnlyMatchesContent`（篇名不参与召回）、`grepSplitsTenantBranchesIntoScope`、`countEnabledChunksGroupsByKnowledge`。
`KnowledgeSearchFactsSourceTest.ownedKbReturnsFacts`（断言 `id`、`tenantId`、`type`、`embeddingModelId`、`vectorStoreId`、`vectorEnabled`、`keywordEnabled` 七字段，零值索引策略按 vector 与 keyword 都开启回填）、`foreignKbReturnsNull`、`softDeletedKbReturnsNull`、`missingIdSkippedInDocumentBatch`、`documentBatchKeepsOrderAndDropsForeignRows`、`chunkBatchDropsForeignTenantRows`。
Run: `MVN test -Dtest=ChunkRepositoryIT,ChunkRevisionRepositoryIT,ChunkEditServiceTest,ChunkQuestionServiceTest,KnowledgeSearchServiceTest,KnowledgeSearchFactsSourceTest`
Expected: FAIL。

- [ ] **Step 2: 实现**
`KnowledgeSearchService.hybridSearch` 先调 `KnowledgeBaseQueryApi.isOwned` 与 `indexingCapabilities`，再调 retrieval-engine 的检索入口；参数透传 `disableKeywordsMatch`、`disableVectorMatch`、`topK`。`grep` 把 `GrepQueryBo` 转成「知识 ID 集合、标签集合、知识库集合」三个作用域分支，用 `~*` 正则只查 `content`，计数回填按 `knowledge_id` 分组。`countEnabledChunks` 用一条分组查询。
`ChunkEditService.update` 顺序：取行与归属校验、写修订、更新分块、提交后同步向量与关键词索引。事务内不做索引调用。
`KnowledgeSearchFactsSource.knowledgeBase` 走 `KnowledgeBaseRepository.findById` 加索引策略零值回填，语义等同旧 `KnowledgeBaseService.findSearchFacts`（`KnowledgeBaseService.java:326-340`）；`accessibleDocuments` 与 `chunks` 按 `(tenant_id, id IN ...)` 查，缺失 ID 跳过，跨租户行不返回。三个方法只读，不写库，不做外部调用。ES 与 Milvus 在读面与写面前都先调 `knowledgeBase`，返回 null 时抛引擎侧归属错误，这是 spec 5.7 第 7 条要求的应用层隔离点。
`KnowledgeResourceReferenceProvider.knowledgeIdsOf` 用一条 join 查 `storage_resource_bindings`（`owner_type='knowledge'`）到 `knowledges`、`knowledge_bases`，排除两侧软删，返回去重后的 kb ID 集合；对应旧 `ResourceRepository.knowledgeBaseIdsForBinding`（`ResourceRepository.java:203-213`）。这条 join 按路线图 3.7 与 spec 5.11 第 8 条属跨域只读，执行方 Task 1 核对计划 03 是否提供 `storage.api` 的绑定查询方法；有则改调它，没有则写在 `M/mapper/ChunkMapper.xml` 里并登记 `deviations.md`，不在 storage 表上建视图。

- [ ] **Step 3: 跑绿**
Run: `MVN test -Dtest=ChunkRepositoryIT,ChunkRevisionRepositoryIT,ChunkEditServiceTest,ChunkQuestionServiceTest,KnowledgeSearchServiceTest,KnowledgeSearchFactsSourceTest`
Expected: PASS。`hybridSearchRequiresOwnedKb` 与 `foreignKbReturnsNull` 在关掉行级安全后仍须成立，变红说明实现靠了行级安全而不是显式租户谓词。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/entity backend/app/src/main/java/com/ragagent/knowledge/mapper backend/app/src/main/java/com/ragagent/knowledge/repository backend/app/src/main/resources/mapper/knowledge backend/app/src/main/java/com/ragagent/knowledge/service/chunk backend/app/src/main/java/com/ragagent/knowledge/api backend/app/src/test/java/com/ragagent/knowledge` 后 `git commit -m "feat(knowledge): 分块读写、推荐问题与检索 api"`。

### Task 15: 文档 Service 与分块 Controller

**Files:**
- Create: `M/service/document/{DocumentImportService,DocumentQueryService,DocumentManualService,DocumentBatchService,DocumentFolderService,DocumentParseService,KnowledgeContentService}.java`、`M/service/chunk/{ChunkingService,ChunkingTier,HeadingChunker,HeuristicChunker,LegacyChunker,SemanticOverlapFinder,SeparatorPicker,ChunkValidator,DocumentProfiler,TextNormalizer,ChunkPatterns,HeaderTracker,HeadingHierarchy,SplitterConfig,ParsedChunk,SearchTextUtil,TextSanitizer}.java`、`M/controller/{KnowledgeItemController,ChunkController,ChunkingController}.java`、`M/vo/{CreateDocumentFromUrlVo,CreateManualDocumentVo,UpdateManualDocumentVo,DocumentVo,DocumentListItemVo,DocumentPageVo,StageVo,SpanVo,FolderVo,ChunkVo,ChunkRevisionVo,UpdateChunkVo,RevertChunkVo,UpsertQuestionVo,DeleteQuestionVo,PreviewChunkingVo,PreviewChunkingResultVo,UpdateImageVo}.java`、`T/service/document/{DocumentImportServiceTest,DocumentBatchServiceTest,DocumentFolderServiceTest,DocumentParseServiceTest}.java`、`T/service/chunk/{ChunkingServiceTest,SearchTextUtilTest}.java`、`T/controller/{KnowledgeItemControllerTest,ChunkControllerTest}.java`

**Interfaces:**
- Consumes: Task 9、Task 14 的 Repository，Task 10 的 `KnowledgeIngestService`
- Produces:
  - `DocumentImportService.importFile`、`importUrl`；`DocumentQueryService.query`、`batch`、`folders`；`DocumentManualService.create`、`update`；`DocumentBatchService.batchDelete`、`batchReparse`、`clearKb`、`move`；`DocumentFolderService.moveToFolder`、`renameFolder`；`DocumentParseService.reparse`、`cancel`
  - `KnowledgeContentService implements KnowledgeContentApi`，分块四个方法委托 `ChunkReadService` 与 `ChunkEditService`
  - `interface ChunkingTier { StrategyTier tier(); List<ParsedChunk> split(String text, SplitterConfig config); }`，三个实现注册 Bean
  - 文档与分块路径，见 1.2

- [ ] **Step 1: 写失败的测试**
`DocumentImportServiceTest.importFileRejectsDuplicateHashInSameKb`、`importFileWritesRowAndEnqueuesInSameTransaction`、`transactionRollbackLeavesNoRowAndNoJob`、`importUrlInfersFileTypeAndFilename`、`manualDraftIsNotEnqueued`。
`DocumentBatchServiceTest.batchDeleteOver200Rejected`、`clearKbRequiresOwnership`、`moveRequiresBothKbOwnership`、`moveToSameKbRejected`、`movePublishesDocumentMovedEventAfterCommit`。
`DocumentFolderServiceTest.rejectsSelfNestedMove`、`rejectsIllegalCharacter`。
`DocumentParseServiceTest.reparseIncrementsAttemptAndCancelsOldJob`、`cancelRejectsFinishedParse`。
`ChunkingServiceTest.headingTierWinsForMarkdown`、`fallsBackToLegacyWhenValidationFails`、`previewReturnsTierChain`。
`SearchTextUtilTest` 从 `contract` 的 `SearchUtilTest` 整体搬来，断言不变（分词降级、正则命中判定、中文判定）。
`KnowledgeItemControllerTest.uploadReadsMultipartFields`、`listRequiresPageAtLeastOne`、`detailsReturnsParseStatusCamelCase`、`cancelReturnsCancelledStatus`。
`ChunkControllerTest.chunkIdSerializedAsString`、`revisionConflictReturnsConflictCode`、`questionEndpointsUseChunkSubresource`。
Run: `MVN test -Dtest=DocumentImportServiceTest,DocumentBatchServiceTest,DocumentFolderServiceTest,DocumentParseServiceTest,ChunkingServiceTest,SearchTextUtilTest,KnowledgeItemControllerTest,ChunkControllerTest`
Expected: FAIL。
Expected: FAIL。

- [ ] **Step 2: 实现**
导入顺序：校验、查重、取存储回落、写文件、插行、同事务入队。事务内不做对象存储调用，文件写失败整笔回滚。
`reparse` 事务内 `parse_attempt + 1` 并回 `pending`，提交后删旧作业并投递新作业。
`ChunkingService` 按 `HeadingChunker`、`HeuristicChunker`、`LegacyChunker` 的链执行，`ChunkValidator` 拒绝时依次回退，最后一层返回 `LegacyChunker` 的结果。算法体从旧类逐行迁移，只改命名与注入方式。
`SearchTextUtil` 与 `TextSanitizer` 从旧类整文件搬入，旧调用点的 import 一并改：`TextSanitizer` 改本域 15 处，`SearchTextUtil` 改 retrieval-engine 停用包 `qdrant.QdrantRetrieveRepository` 一处；`contract` 与 `platform` 里的旧文件由计划 09 统一删除，本任务不改它们。

- [ ] **Step 3: 跑绿**
Run:
```bash
MVN test -Dtest=DocumentImportServiceTest,DocumentBatchServiceTest,DocumentFolderServiceTest,DocumentParseServiceTest,ChunkingServiceTest,SearchTextUtilTest,KnowledgeItemControllerTest,ChunkControllerTest
MVN test -Dtest=OpenApiExportTest
```
Expected: PASS。

- [ ] **Step 4: 提交**
提交：`git add backend/app/src/main/java/com/ragagent/knowledge/service/document backend/app/src/main/java/com/ragagent/knowledge/service/chunk backend/app/src/main/java/com/ragagent/knowledge/controller backend/app/src/main/java/com/ragagent/knowledge/vo backend/app/src/main/java/com/ragagent/knowledge/assembler backend/app/src/test/java/com/ragagent/knowledge` 后 `git commit -m "feat(knowledge): 文档 Service、分块算法与 Controller"`。

### Task 16: 前端 API 层切换与类型再生成

**Files:**
- Modify: `frontend/src/api/knowledge-base/index.ts`、`frontend/src/api/chunker/index.ts`
- Delete: `getSignedFileUrlByKnowledge`（`frontend/src/api/knowledge-base/index.ts`）
- Modify: `frontend/src/types/api.d.ts`（`npm run gen:api` 产物）、`tests/api-smoke/tests/knowledge-flow.spec.ts`（去掉三条 `test.fixme`，补重解析与取消用例）

**Interfaces:**
- Consumes: Task 8、13、15 的 Controller 与 OpenAPI；计划 01 的 `@ragagent/api-client`
- Produces: 1.10 的切换表落地；冒烟三步转绿

- [ ] **Step 1: 确认死函数无引用后删除**
```bash
grep -rn "getSignedFileUrlByKnowledge" frontend/src
```
Expected: 只有 `frontend/src/api/knowledge-base/index.ts:386` 一处定义。有页面引用时先停下问用户，不要自行改页面。

- [ ] **Step 2: 改 API 层并再生成类型**
按 1.10 表逐函数替换路径，路径从 `paths` 类型取。`copyKnowledgeBase`、`getFAQImportProgress`、`moveKnowledgeToFolder`、`togglePinKnowledgeBase` 的适配写在函数内。
```bash
cd frontend && npm run gen:api && npx vue-tsc --noEmit
cd frontend/packages/api-client && npm run check:api
```
Expected: 类型检查通过，`check:api` 无漂移。

- [ ] **Step 3: 冒烟三步转绿**
先按计划 01 Task 11b 起新栈（`dev` profile，JobRunr 与角色用 `all`），再：
```bash
cd tests/api-smoke && API_BASE=http://localhost:8080 npx playwright test tests/knowledge-flow.spec.ts
```
Expected: 建库、上传、入库三条通过，重解析与取消一条通过。失败时先看 `progress.md` 里 Task 10 的作业日志，不要改测试绕过。

- [ ] **Step 4: 提交**
提交：`git add frontend/src/api/knowledge-base/index.ts frontend/src/api/chunker/index.ts frontend/src/types/api.d.ts tests/api-smoke/tests/knowledge-flow.spec.ts` 后 `git commit -m "feat(frontend): 知识库 API 层切到新路径并删死函数"`。

### Task 17: 界面端到端转绿与媒体预览补齐

**Files:**
- Modify: `tests/e2e/tests/knowledge/{kb-list,kb-documents,kb-faq,kb-settings}.spec.ts`（去掉 `fixme`，快照重录）、`frontend/src/components/document-preview.vue`（仅当签名 URL 路径变化时改调用）、`frontend/src/utils/signedMedia.ts`（仅当接口形状变化时改）
- Create: `docs/migration/knowledge-e2e-notes.md`

**Interfaces:**
- Consumes: Task 16 的前端切换，计划 01 Task 13 的快照基线
- Produces: `[knowledge]` 四个页面转绿；媒体预览回归说明

- [ ] **Step 1: 核对冻结后修复是否已在旧基线里**
```bash
git log --oneline rebuild-freeze..main -- frontend/src/components/document-preview.vue frontend/src/utils/signedMedia.ts
```
Expected: 四个提交（`93909037f`、`26f401951`、`204b79ef3`、`0ca2128e3`）。若某条还在 main 未合到 `rebuild`，按 `frozen-bugs.md` 带入本任务，并在 `knowledge-e2e-notes.md` 记录。

- [ ] **Step 2: 录新栈快照并跑绿**
```bash
cd tests/e2e && npx playwright test --grep "\[knowledge\]" --update-snapshots
npx playwright test --grep "\[knowledge\]"
```
Expected: 第一次生成快照，第二次全绿。快照差异只允许出现在动态内容（时间、计数、ID）上，其他差异说明界面变了，停下来查。

- [ ] **Step 3: 媒体预览手测三条**
在浏览器里打开一个带视频的知识库文档：视频用签名 URL 直连（Network 里 `POST .../files/signed-url` 一条，`<video>` 的 src 是返回的 `url`）、拖动进度条能 Range 播放、分块视图与详情页封面一致。结果写进 `knowledge-e2e-notes.md`。

- [ ] **Step 4: 提交**
提交：`git add tests/e2e/tests/knowledge docs/migration/knowledge-e2e-notes.md` 后 `git commit -m "test(e2e): knowledge 四个页面在新栈转绿"`。

### Task 18: 检索回归对拍与全量校验

**Files:**
- Create: `tests/regression/adapters/rebuild.mjs`
- Modify: `tests/regression/README.md`（新系统启动与鉴权说明）
- Create: `backend/app/src/test/java/com/ragagent/arch/KnowledgeModuleBoundaryTest.java`（若计划 01 未覆盖 knowledge 域分类）
- Modify: `docs/migration/progress.md`

**Interfaces:**
- Consumes: 计划 01 Task 14 的 `record.mjs`、`compare.mjs`、`baseline.json`、`corpus/`；Task 16 的新栈
- Produces: `node record.mjs --adapter rebuild --out candidate.json` 可跑；`expectedRecall` 不低于 1.0

- [ ] **Step 1: 写新系统适配器**
`adapters/rebuild.mjs` 导出 `ingestCorpus(corpusDir)` 与 `search(kbName, query, language)`。`ingestCorpus` 走 `POST /api/admin/v1/knowledge-bases`、`POST .../documents` 与轮询 `GET /api/admin/v1/knowledge-items/{id}`，等到 `parseStatus=COMPLETED`。`search` 走 `POST /api/admin/v1/knowledge-bases/{kbId}/hybrid-search`，请求体 `{queryText}`，把 `RestResponse.data` 的命中映射成适配器契约的三个字段。鉴权用 `POST /api/admin/v1/auth/login` 取得的 token，请求头带 `Bearer ` 前缀。

- [ ] **Step 2: 录候选并对比**
```bash
cd tests/regression
E2E_TOKEN=*** EMBEDDING_API_KEY=*** BASE_URL=http://localhost:8080 \
  node record.mjs --adapter rebuild --out candidate.json
node compare.mjs --baseline baseline.json --candidate candidate.json --min-expected 1.0
```
Expected: `expectedRecall = 1.000`，退出码 0。低于 1.0 时逐条看缺哪条命中，先查分块参数是否与旧库一致（512 与 80 的默认值、逐文档覆盖），再查检索开关。

- [ ] **Step 3: 全量校验**
```bash
MVN verify
git diff --stat backend/app/target/openapi.json 2>/dev/null || true
```
Expected: `MVN verify` 全绿，含 Checkstyle 尺寸门、ArchUnit 后缀规则与跨域规则、Modulith `verify`。`knowledge` 域新类不带 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common` 后缀，构造器依赖不超过 7 个。

- [ ] **Step 4: 登记并提交**
提交：`git add tests/regression/adapters/rebuild.mjs tests/regression/README.md docs/migration/progress.md` 后 `git commit -m "test(regression): knowledge 新系统检索对拍"`。

## 5. 完成判据

对应路线图第 2 节 04 号闸门，逐条能落到命令输出。

```bash
# 本计划全部单测与集成测，含 Checkstyle 尺寸门、ArchUnit、Modulith verify
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify

# 知识库相关页面端到端转绿（新栈 8080，e2e 前端指向新栈）
cd tests/e2e && npx playwright test --grep "\[knowledge\]"

# 冒烟建库、上传、入库三步转绿
cd tests/api-smoke && API_BASE=http://localhost:8080 npx playwright test tests/knowledge-flow.spec.ts

# 检索回归不劣于基线
cd tests/regression && node compare.mjs --baseline baseline.json --candidate candidate.json --min-expected 1.0

# 类型生成无漂移
cd frontend/packages/api-client && npm run check:api
```

另有五项状态检查：

```bash
grep -rn "getSignedFileUrlByKnowledge" frontend/src          # 无输出
grep -rn "video_info" backend/app/src/main/resources/db/migration   # 无输出
grep -rn "tenant_id" backend/app/src/main/resources/mapper/knowledge | wc -l   # 0
grep -rn "@IgnoreTenant" backend/app/src/main/java/com/ragagent/knowledge | wc -l   # 4
ls docs/migration/knowledge-job-map.md docs/migration/knowledge-e2e-notes.md
```

`@IgnoreTenant` 的 4 处与 1.9 白名单一致，且每处有一行中文注释说明原因；`progress.md` 的 IgnoreTenant 白名单小节已登记。

JobRunr 与角色开关的四条验收：
```bash
grep -n "ragagent.role\|ragagent.jobrunr" backend/app/src/main/resources/application.yml
RAGAGENT_ROLE=worker MVN -pl app -am spring-boot:run   # 业务路由返回 404，健康检查 200
RAGAGENT_ROLE=api MVN -pl app -am spring-boot:run      # 作业入队后不被本进程消费
grep -rn "tenant:" backend/app/src/main/java/com/ragagent/config/TenantJobFilter.java
```