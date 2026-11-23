# RagAgent 重建：三个引擎迁入与内部整理 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把旧 `model-runtime`、`retrieval-engine`、`agent-engine` 改包名搬进 `backend/` 同名三模块，contract 类型按 spec 5.2 归位，补出 `api` 与 `spi` 两个对外包；再按 spec 5.5、3.11、5.11 整理内部，停用 6 种检索引擎（源码迁入保留、不注册、不进下拉、不整理），PGroonga 替换 ParadeDB pg_search，agent-engine 用 5.10.4 三组用例守住 5.10.1 的 41 项能力。

**Architecture:** 三个引擎保持 Maven 模块，依赖方向 `app → agent-engine → model-runtime → framework` 与 `app → retrieval-engine → model-runtime → framework`。引擎对外只暴露 `api`（入口与 DTO）与 `spi`（引擎声明、app 实现的接口），内部包由 ArchUnit 守住。contract 解散后按 5.2 分配：`model.*` 进 model-runtime `api`，`retrieval.*` 与 `vectorstore.*` 进 retrieval-engine，`agent.*` 与 `tool.*` 进 agent-engine，`error.*`、`event.*`、`pagination`、`json` 进 framework，被业务域独占的包由 app 承接。装配只在 `com.ragagent.config` 的 `EngineAssemblyConfig`。

**Tech Stack:** JDK 21、Spring Boot 3.5.16、Jackson 2.21.4、MyBatis-Plus 3.5.x、Testcontainers（PostgreSQL 17 + pgvector + PGroonga，Elasticsearch 8.19，Milvus 2.6.11）、OpenTelemetry Java SDK、PGroonga、jtokkit、JUnit 5、AssertJ、ArchUnit 1.3、Spring Modulith 1.4。

**规格来源**：`docs/optimization/specs/2026-11-09-architecture-rebuild-design.md`。路线图：`docs/optimization/plans/2026-11-10-rebuild-00-roadmap.md`，本文是其中的 02 号计划。本文所有数字都已对冻结点源码复核。

## Global Constraints

继承路线图第 3 节全部约定。本计划最常用的几条：

- JDK 21，Spring Boot 3.5.16，Jackson 版本随 Boot BOM 为 2.21.4。
- Java 缩进 tab，续行一个 tab，行宽 140。import 顺序 java → javax → org → com → 其他 → static。
- 注释中文、独立行；public 类与 public 方法必须 Javadoc；`@author jiangguifan`。
- 类名与方法名按路线图 3.11：禁用 `Ops`、`Support`、`Helper`、`Facade`、`Manager`、`Common` 后缀；单实现不建接口；真实多实现用窄接口加 Spring 登记表。
- 尺寸硬门：方法 40 行、类 400 行、参数 5 个、圈复杂度 10、嵌套 3 层、构造器依赖 7 个。`@SuppressWarnings("checkstyle:<规则名>")` 必须紧邻一行中文理由。
- 迁入期间引擎旧代码用 `backend/config/checkstyle-suppressions-engines.xml` 按包豁免，参与整理的包在整理任务完成时同一提交删除该包条目；6 个停用引擎包的条目长期保留，不删。
- `JdbcTemplate` 只允许出现在 `com.ragagent.framework.tenant..` 与 `com.ragagent.retrieval..`（路线图 3.11 与 spec 5.11 第 3 条）。
- 主码无注解 SQL（`@Select`、`@Update`、`@Insert`、`@Delete`），手写 SQL 只写 Mapper XML。
- 业务错误码 10 位 `1` + `001` + 模块号 + 3 位序号。本计划模块号：model-runtime `020`、retrieval-engine `021`、agent-engine `022`。
- 错误码枚举放各引擎 `api` 包，命名 `ModelRuntimeCode`、`RetrievalCode`、`AgentEngineCode`，由 `com.ragagent.config.EngineCodeConfig` 在启动时 `CodeCache.register`。
- 文案 key `code_<错误码>`，七语种加默认文件齐全，放 `backend/app/src/main/resources/i18n/app-messages*.properties`（路线图 3.4 第 1 条要求业务文案统一在 app）。
- Maven 命令统一带 `-s C:/Users/jiangguifan/.m2/settings-coding-uat.xml`，`-pl` 必带 `-am`。下文 `MVN` 代表 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl app -am`。只改某一模块时用 `-pl <模块名> -am`，仍带 `-am`。
- `git add` 只写显式路径。每任务一次提交，提交后在 `docs/migration/progress.md` 追加一行。
- 旧模块只读。第 09 份之前不删 `contract/`、`platform/`、三个旧引擎目录。
- 旧代码里的 Go 兼容写法（`BizException` 硬编码英文文案、`Map<String, Object>` 传业务数据、`rawBody`）不得原样搬入新结构，处理规则见 1.12。

## Review Focus

五类最容易悄悄做错的点，每类在任务里有对应用例。

1. **引擎只暴露 api 与 spi。** 内部包被 app 直接 import 时必须让 `enginesExposeOnlyApiAndSpi` 变红，不是靠 review 肉眼。归属 Task 5。
2. **检索谓词与行级安全的边界。** 检索 SQL 里服务于索引命中的 `tenant_id`、`knowledge_base_id` 谓词必须保留（spec 第 6 节第 3 步例外），其余 SQL 不得手写 `tenant_id`。归属 Task 6、Task 8。
3. **流终态是两帧 done。** model-runtime 先发带工具调用的 done，再发带 usage 的 done，消费方只认第一帧会静默丢全部用量。归属 Task 2、Task 9。
4. **行级安全报错要收口。** 事务级 `set_config` 之后的查询会报 `invalid input syntax for type bigint: ""`，必须转成 `FrameworkCode.TENANT_CONTEXT_MISSING`，不能把数据库原文吐给用户。归属 Task 5。
5. **41 项能力一项不丢。** 压缩、坏 JSON 修复、截断拒执、事件顺序、取消关流这些路径没有单元断言就会在整理中被删掉。归属 Task 9。

## 文件结构

```
backend/model-runtime/src/main/java/com/ragagent/modelruntime/
├── api/ spi/            对外入口与 DTO、引擎声明
├── embedding/ llm/      BatchEmbedder 与厂商目录（llm.provider 26 家）
├── context/             modelcontext：ModelContextRegistry、ToolResultRenderer
├── provider/            chat、anthropic、asr、vlm 适配
├── rerank/ assembly/    rerank 工厂、ModelRuntimeAssembler
└── tracing/             TraceRecorderImpl 与四个装饰器

backend/retrieval-engine/src/main/java/com/ragagent/retrieval/
├── api/ spi/            对外入口与 DTO、引擎声明
├── service/             HybridSearchService、IndexingService、VectorStoreCatalogService
├── engine/              Engine 窄接口、EngineRegistry，及 pgvector、elasticsearch、milvus 三个子包
└── artifact/ graph/ support/ telemetry/

backend/agent-engine/src/main/java/com/ragagent/agentengine/
├── api/ spi/            入口、工具协议、事件载荷、SkillFrontmatter、四个 spi
├── react/ context/      ReAct 四阶段与工具批次、Token 估算与预算
├── compaction/ prompt/  压缩十个类、提示词组装
├── skill/               Skill、SkillFrontmatter、SkillDisclosureLevels
└── tool/                ParamCaster、内置工具与 runtime 子包
```

## 前置、交付、对外承诺

### 前置

| 来源 | 需要的产物 |
|---|---|
| 计划 01 Task 1 | `backend/pom.xml` 聚合、三个引擎模块的 pom 与 `package-info.java`、`com.ragagent.config.EngineAssemblyConfig` 空壳 |
| 计划 01 Task 2 到 Task 4 | `RestResponse`、`Code`、`CommCode`、`FrameworkCode`、`CodeCache`、`BusinessRuntimeException`、`I18nUtil` 与七语文件 |
| 计划 01 Task 5 | `TenantContextHolder`、`@IgnoreTenant`、`TenantAwareJdbcTemplate`（含 `22P02`、`42501` 到 `TenantContextMissingException` 的收口）、`PostgresContainerTest` |
| 计划 01 Task 6 到 Task 10 | MyBatis-Plus 配置、`EncryptTypeHandler`、Web 异常处理、Sa-Token、`TenantClientRegistry`、Redisson 锁 |
| 计划 01 Task 11 | `ArchitectureRulesTest`（含 `enginesExposeOnlyApiAndSpi`、`jdbcTemplateAllowedOnlyInTenantAndRetrieval`、`noGoCompatSuffixes`）与 `ModuleBoundaryTest` |
| 计划 01 Task 11c | `backend/config/checkstyle.xml` 与空的 `checkstyle-suppressions-engines.xml` |
| 计划 01 Task 14 | `tests/regression/` 的检索回归基线与录制说明 |
| 计划 01 Task 18 | 自建镜像 `ragagent/postgres:17-pgvector-pgroonga` |

### 交付

1. 三个引擎模块的 `api` 与 `spi` 包，签名见 1.7。
2. 完整的引擎内部实现（chat、embedding、rerank、vlm、asr、provider、混合检索、ReAct、压缩、技能、工具运行时）。
3. `EngineAssemblyConfig` 的全部引擎 Bean 与 `spi` 装配点，spi 实现由计划 03、04、06、08 提供。
4. `backend/config/checkstyle-suppressions-engines.xml` 只剩停用引擎包的 1 条豁免，其余引擎主码通过 3.11 全部硬门。
5. `tests/regression/RESULT-engines.md`：检索回归对拍结论。
6. `docs/migration/handoff-engines-spi.md`：每个 spi 的用途、实现方、缺失时的行为。

### 对外承诺

**model-runtime api**（包 `com.ragagent.modelruntime.api`）：

| 类型 | 方法或字段 | 消费方 |
|---|---|---|
| `ModelRuntime` | `LlmChatClient chatClient(String modelId)`；`Embedder embedder(String modelId)`；`EmbeddingPort embeddingPort()`；`Reranker reranker(String modelId)`；`VlmPredictor vlmPredictor(String modelId)`；`AsrTranscriber asrTranscriber(String modelId)`；`ChatConfig chatConfig(String modelId)` | app 各域、agent-engine、retrieval-engine |
| `LlmChatClient` | `ChatResponse chat(List<ChatMessage>, ChatOptions)`；`ChatStream chatStream(List<ChatMessage>, ChatOptions)`；`String modelName()`；`String modelId()` | agent-engine、app |
| `ChatStream` | `StreamResponse take()`；`StreamResponse poll(long timeout, TimeUnit unit)`；`void close()`；`boolean isClosed()`（保留旧 `BlockingQueue` 消费语义，`ThinkPhase` 的停顿看门狗靠 `poll` 超时） | agent-engine、app |
| `EmbeddingPort` | `List<float[]> embed(ModelFacts model, List<String> texts)` | retrieval-engine、app |
| `Reranker` | `List<RankResult> rerank(String query, List<String> documents)`；`String modelName()`；`String modelId()` | app session（重排插件） |
| `Embedder` | `float[] embed(String text)`；`List<float[]> batchEmbed(List<String> texts)`；`String modelName()`；`int dimensions()`；`String modelId()` | app knowledge（索引写入） |
| `AsrTranscriber` | `TranscriptionResult transcribe(AsrConfig config, byte[] audioBytes, String fileName)`（`AsrConfig(baseUrl,modelName,apiKey,modelId,language,customHeaders)`、`TranscriptionResult(text,segments)` 沿用旧形状） | app knowledge、system 模型调试 |
| `GraphValue` 族 | `GraphNode`、`GraphRelation`、`GraphData`、`EntityExtractionResult` | app knowledge、retrieval-engine |
| `TraceRecorder` | `SpanHandle startSpan(String name, Map<String, String> attributes)`；`GeneratorHandle startGeneration(String name, Map<String, Object> metadata)` | retrieval-engine、agent-engine |
| `SpanHandle` | `String id()`；`void finish(Object output, Map<String, Object> metadata, String error)`（旧 `Span` 的 finish 语义：metadata 合并而非覆写） | retrieval-engine、agent-engine |
| `GeneratorHandle` | `String id()`；`void finish(Object output, TokenUsage usage, String error)`；`void markCompletionStart()`（旧 `Generation` 的能力，装饰器必需） | model-runtime 内部装饰器，`TokenUsage` 从 `com.ragagent.framework.stream` 导入 |
| `PromptLanguage` | `static String name()`：取当前请求语言的可读名（来自 framework 的 Locale 解析，不再读 `RAGAGENT_LANGUAGE`） | agent-engine |

**model-runtime spi**（包 `com.ragagent.modelruntime.spi`）：

| 接口 | 方法签名 | 实现方 |
|---|---|---|
| `ModelCatalogReader` | `Optional<ModelRow> findOrEmpty(String modelId)`；`ModelRow requireVisible(long tenantId, String modelId)`；`ModelRow requireRow(long tenantId, String modelId)`。`ModelRow(id,name,type,source,status,defaultModel,parameters)` 字段集与旧 `ModelRowView` 相同，只改 record 写法 | model（03） |
| `ModelFactsReader` | `ModelFacts find(String modelId)`（查不到返回 null，`ModelFacts` = `(modelId,name,baseUrl,apiKey)`，`apiKey` 已由实现方解密） | model（03） |
| `ModelCredentialReader` | `String apiKey(long modelId)`；`String appSecret(long modelId)`（返回明文；空值归一 `""`） | model（03），读 `@Encrypted` 列 |

**retrieval-engine api**（包 `com.ragagent.retrieval.api`）：

| 类型 | 方法或字段 | 消费方 |
|---|---|---|
| `HybridSearchApi` | `List<SearchResult> search(SearchRequest request)`；`float[] queryEmbedding(String knowledgeBaseId, String query)`；`Map<String, String> embeddingModelKeys(List<String> kbIds)` | app knowledge、session |
| `IndexingApi` | `void index(String storeId, IndexDocument document)`；`void batchIndex(String storeId, List<IndexDocument>)`；`void deleteByChunkIds(String storeId, List<String>, int dimension)`；`void deleteByKnowledgeIds(String storeId, List<String>, int dimension)`；`void copyIndices(CopyIndicesCommand command)`；`void moveKnowledgeIndices(MoveIndicesCommand command)`；`void updateChunkEnabled(Map<String, Boolean>)`；`void updateChunkTag(Map<String, String>)`；`long estimateStorageSize(List<IndexDocument>)`；`void verifyBinding(String storeId)` | app knowledge |
| `VectorStoreCatalogApi` | `VectorStoreEngineRules rulesFor(String engineType)`；`String probe(String engineType, ConnectionConfig config)`；`List<String> configurableEngineTypes()` | vectorstore（03）、控制台 |
| `VectorEngineType` | 常量 `POSTGRES`、`ELASTICSEARCH`、`MILVUS`，`ALL`、`CONFIGURABLE` 两个集合 | 全仓 |
| `SearchRequest`、`SearchResult`、`IndexDocument`、`IndexRow`、`ConnectionConfig`、`IndexConfig`、`VectorStoreView`、`RetrievalCode`、`RetrievalException` | 字段名与线格式沿用旧 contract.retrieval 与 contract.vectorstore，只改 record 写法 | app |

**retrieval-engine spi**（包 `com.ragagent.retrieval.spi`）：

| 接口 | 方法签名 | 实现方 |
|---|---|---|
| `VectorStoreCatalog` | `Optional<VectorStoreView> byId(long tenantId, String storeId)`；`boolean isOwnedBy(String storeId, long tenantId)`。返回空表示“不存在或不属于该租户”，实现方查询异常照抛，调用方按旧 `VectorStoreLookup` 语义转 `ENGINE_UNAVAILABLE` | vectorstore（03） |
| `KnowledgeSearchSource` | `KnowledgeBaseFacts knowledgeBase(String kbId)`；`List<KnowledgeDocumentFacts> accessibleDocuments(long tenantId, List<String> knowledgeIds)`；`List<ChunkFacts> chunks(long tenantId, List<String> chunkIds)`。`KnowledgeBaseFacts(id,tenantId,type,embeddingModelId,vectorStoreId,vectorEnabled,keywordEnabled)` 沿用旧 `KnowledgeBaseSearchFacts` 字段，查不到返回 null；后两条缺失 id 跳过、查不到返回空列表 | knowledge（04） |
| `TenantRetrievalSettings` | `List<RetrievalDriverRef> effectiveEngines(long tenantId)`（`RetrievalDriverRef(retrieverType,engineType)`，租户未配置时返回空列表，由调用方按部署驱动回落）；`RetrievalTuning tuning(long tenantId)`（`RetrievalTuning(rrfK,rrfVectorWeight,rrfKeywordWeight)`，读不到返回缺省 60/0.7/0.3） | tenant（03） |

**agent-engine api**（包 `com.ragagent.agentengine.api`）：

| 类型 | 方法或字段 | 消费方 |
|---|---|---|
| `AgentEngineFactory` | `AgentEngineHandle create(AgentEngineRequest request, LlmChatClient chat, AgentEventSink sink)` | app session |
| `AgentEvent` | 字段 `id`、`type`（`AgentEventType`）、`sessionId`、`data`（`AgentEventPayload`）、`metadata`（`Map<String,Object>`）、`requestId` | session（08） |
| `AgentEngineHandle` | `void setMemoryPrompt(String)`；`void setSteerSink(SteerSink)`；`void setCancellationSource(Supplier<String>)`；`void execute(AgentTurn turn)` | app session |
| `AgentEngineRequest`、`AgentRunSpec`、`AgentTurn`、`KnowledgeBaseInfo`、`SelectedDocumentInfo` | 字段沿旧 `AgentEngineRequest` 与 `AgentRunSpec`，`AgentTurn(sessionId,messageId,query,llmContext,imageUrls)` 替代旧 `execute` 的五个参数，`AgentEngineHandle.execute(AgentTurn)` 与之一致 | app session |
| `AgentTool`、`ToolRequest`、`ToolExecContext`、`ToolResult`、`ToolCancellation`、`ToolOutput`、`ToolDefinitions` | 工具协议，签名与字段名沿用旧 contract（前端与历史回放依赖） | agenttools（08）、mcp（06） |
| `AgentEvent`、`AgentEventType`、`AgentEventPayload` 族 | 事件类型与载荷；键名与顺序沿用旧 `contract.event.payload`，键序规则见 Task 9。`AgentEventSink` 因参数用 `AgentEvent` 而放 `api`（与事件同包），`spi` 表里不再重复 | session（08） |
| `SkillFrontmatter` | `SkillDraft parse(String skillMd)`；`String render(SkillDraft draft)`；`String instructions(String skillMd)`（旧 `Skill.parseSkillFile(content).instructions`，计划 06 的 `instructions` spi 转发到这里）。`SkillDraft(name,description,slug,body,frontmatterErrors)`，解析失败抛 `AgentEngineCode.SKILL_INVALID` | agent（06） |

**agent-engine spi**（包 `com.ragagent.agentengine.spi`）：

| 接口 | 方法签名 | 实现方 |
|---|---|---|
| `AgentEventSink` | `void emit(AgentEvent event)`（接口文件 `agentengine.api.AgentEventSink`，见上表说明） | session（08） |
| `SteerSink` | `List<SteerMessage> poll(String sessionId, String messageId, int lastOffset)`；`String persist(SteerMessage message)`。`SteerMessage(id,content,mentionedItems,channel)` 字段沿用旧 steer 事件 map 的四个键 | session（08） |
| `SkillCatalog` | `List<SkillRecord> listVisible(long tenantId)`；`Optional<SkillRecord> findVisible(long tenantId, String skillId)`；`String instructions(String skillMd)`。`SkillRecord(id,tenantId,slug,name,description,skillMd)`，`tenantId` 为 null 表示平台内置层 | agent（06） |
| `McpToolProvider` | `McpToolSession open(long tenantId, McpSelection selection)`。`McpSelection(mode,serviceIds,authWaitTimeoutSeconds)`，`mode` 取 `all`、`selected`、`none` | agenttools（08），配置读 mcp（06） |
| `McpToolSession` | `void prepare(boolean direct)`；`void refresh()`；`void rememberHistory(List<ChatMessage>)`；`ToolCallTarget callTarget(String name, Object raw)`；`boolean isPrepared()`；`boolean isDirect()`；`boolean hasServer(String serviceId)`；`ToolResult execute(ToolCancellation cancellation, ToolExecContext meta, AgentTool tool, Object args)`（旧 `ToolRegistryPort` 的共享执行管线入口） | 同上 |

**对路线图第 6 节交接表的增补**：

| 声明方 | 接口 | 用途 | 实现方 |
|---|---|---|---|
| model-runtime（02） | `ModelCatalogReader`、`ModelFactsReader`、`ModelCredentialReader` | 引擎读模型行、嵌入模型事实、解密凭据 | model（03） |
| retrieval-engine（02） | `VectorStoreCatalog` | 向量库配置查询与归属校验 | vectorstore（03） |
| retrieval-engine（02） | `KnowledgeSearchSource` | KB 元数据、文档可达性、分块回填 | knowledge（04） |
| retrieval-engine（02） | `TenantRetrievalSettings` | 租户有效引擎与检索调参 | tenant（03） |
| agent-engine（02） | `SkillCatalog` | 技能披露 | agent（06） |
| agent-engine（02） | `McpToolProvider` | MCP 工具注册与调用 | agenttools（08） |
| agent-engine（02） | `AgentEventSink`、`SteerSink` | 事件流与运行中插话 | session（08） |

### 对前置计划的新增需求

1. **计划 01 的 `BusinessRuntimeException` 补静态方法 `String wireText(Throwable err)`**：沿 cause 链找最近的业务异常取其码加 i18n 文案，找不到取最深非空 message。agent-engine 有 7 处 `BizException.wireText` 调用，SSE 错误帧文案必须带错误码前缀。计划 01 已实现则复用，签名不同时登记偏离并同步改 Task 2 与 Task 4。
2. **`SsrfGuard` 若未注册为 Spring Bean，本计划用 `ObjectProvider` 兜底**，计划 03 注册后自动切换。
3. **`noGoCompatSuffixes` 增加六个停用引擎包的排除清单**：路径 `com.ragagent.retrieval.engine.{doris,opensearch,qdrant,sqlite,tencentvectordb,weaviate}`。停用包里有 14 个 `*Ops` 类，按 D17 不做命名整理，规则排除后仍需对其余包生效。计划 01 已落盘的规则加 `.that().resideOutsideOfPackages(...)` 六条即可，签名不变；若计划 01 已用别的写法，Task 8 Step 4 按等价写法改并登记偏离。重启某个引擎时删掉它那一条。
4. **`contract.retrieval.SearchTextUtil` 保留到计划 04**：停用包 `qdrant` 与 `retrieval.support` 仍 import 它，本计划迁移时不改这两个停用文件的 import。计划 04 把它迁进 knowledge 后，若 qdrant 仍未启用，只改停用包的 import 路径即可。`CleanInvalidUtf8` 同理（`platform.text` 临时保留，计划 04 迁走）。

### 对后续计划的要求

| 计划 | 要求 |
|---|---|
| 03 vectorstore | 实现 `retrieval.spi.VectorStoreCatalog`；控制台向量库下拉从 9 项改 3 项，`VectorEngineType.CONFIGURABLE` 是唯一取值来源；模型域实现 `modelruntime.spi` 三个接口 |
| 03 tenant | 实现 `retrieval.spi.TenantRetrievalSettings`（有效引擎与检索调参） |
| 04 knowledge | 实现 `retrieval.spi.KnowledgeSearchSource`；Flyway 里追加 PGroonga 建索引 DDL（文本见 Task 6）；`embeddings` 表的写属主在 knowledge 的入库流程 |
| 06 agent | 实现 `agentengine.spi.SkillCatalog`，用 `agentengine.api.SkillFrontmatter` 组装与校验；接管 `skills` 表与 `SkillCatalogService` 的管理面 |
| 08 agenttools 与 session | agenttools 实现 `agentengine.spi.McpToolProvider`；session 实现 `AgentEventSink` 与 `SteerSink` |

## 本计划新做的设计决定（待用户核对）

只列 spec 与路线图没有定、由本计划新定的决定，按重要度排序。

### 一、新表结构与聚合根划分

1. **`embeddings` 表由计划 04 建，引擎只写 SQL 不建表。** 决定：pgvector 引擎继续读写 `embeddings`（spec 5.11 第 3 条把 `JdbcTemplate` 留给它），表结构与 PGroonga 索引由计划 04 的 Flyway 建。理由：写属主必须唯一，引擎不是域。否决：把 pgvector 挪进 knowledge 域，违背 spec 5.9 的引擎分层与 Milvus、ES 的对称性。
2. **关键词检索改 PGroonga，表达式在本计划定死。** 决定：查询用 `content &@~ ?`，排序按 `pgroonga_score(tableoid, ctid)`，查询串在应用侧做 NFKC，不按语言分词。理由：D18 与 spec 5.9 第 3 条。否决：保留 `content ||| ?` 与 `paradedb.score`，新镜像已不含 `pg_search`。

### 二、新接口路径与 VO 形状

无。三个引擎不对外提供 HTTP 接口，界面变化只有向量库下拉选项减少，归计划 03 的 vectorstore 前端任务。

### 三、Provider 与 spi 签名

1. **`ModelCatalogReader` 一接口三方法，替代旧 `ModelCatalogPort` 的 9 个方法。** 决定：引擎只要“取一行、取不到就空、取不到就报错”三种语义，管理写面（创建、更新、全字段视图）不在引擎职责内。理由：旧端口的 9 个方法里 7 个只被 app 管理面使用。否决：原样搬 `ModelCatalogPort`，让引擎 api 里带 `ModelView` 与 `createModel`。
2. **`KnowledgeSearchSource` 合并旧三个 Gateway。** 决定：`KnowledgeBaseSearchGateway`、`KnowledgeDocumentGateway`、`ChunkSearchGateway` 合成一个接口，因为三者只有一个实现方（knowledge 域），且总是一起被同一次检索调用。理由：三个一方法接口是 spec 反目标第 3 条的“纯转发层”。否决：保留三个接口，app 里写三个一行 Adapter。
3. **`McpToolProvider` 替代旧 `McpToolRegistrationPort` 加 `McpToolingGateway`。** 决定：旧两个接口的职责是“把 MCP 工具注册进注册表”与“按名调用”，新接口返回一个会话对象 `McpToolSession`，把注册与调用收在一次装配里。理由：旧实现里两个接口由同一个 `McpTooling` 实现，接口分离只产生一个空 Adapter。否决：两个 spi 并存。
4. **`AgentEventSink` 放 spi，事件类型与载荷放 api。** 决定：引擎发事件，app 消费，所以“谁实现”决定归属；事件本身是引擎的对外词汇，必须放 api。理由：ArchUnit 规则要求 app 只能看见 api 与 spi，混放会让 app 依赖引擎内部。否决：事件放 spi，app 只能看到接口看不到载荷类型。
5. **`TraceRecorder` 放 model-runtime api，Langfuse 与 OTel 实现放内部。** 决定：三个引擎都要打链路，retrieval 与 agent 直接依赖 model-runtime（spec 5.2 已定），所以 span 入口归 model-runtime api，实现只有一份。理由：旧代码里 `LangfuseManager` 是 platform 的静态单例，retrieval 与 agent 各 import 一次，跨模块传递靠静态状态。否决：framework 提供 `TraceRecorder`，framework 不认识引擎词汇（span 属性里有 model、kb、tool 名）。

### 四、与旧行为不一致的地方

1. **otlp-proto 不再使用。** 决定：Langfuse 导出改用 OpenTelemetry 官方 SDK（`opentelemetry-sdk`、`opentelemetry-exporter-otlp` 加 okhttp 发送器），`otlp-proto/` 目录本计划不改不删，由计划 09 Task 12 在 `OTLP_STATE=已改` 分支下核对无引用后删除。理由：spec 5.1 与第 9 节第 6 条要求删除 otlp-proto 改官方 SDK；追踪是引擎的直接依赖（model-runtime 的四个装饰器、retrieval 与 agent 的 span），不迁进本计划则引擎无法编译。计划 09 已写明两种情形，本计划走“已改”分支，不重复实现 `LangfuseExportTest`。
2. **6 种检索引擎迁入但停用。** 决定：pgvector（`postgres`）、`elasticsearch`、`milvus` 三种完整迁移并上线，控制台下拉只显示三项。其余 `sqlite`、`opensearch`、`doris`、`qdrant`、`weaviate`、`tencent_vectordb` 六种旧代码随引擎模块改包名迁入 `retrieval-engine`，但停用：不注册进 `EngineRegistry`、不进 `VectorEngineType` 与 `CONFIGURABLE`、不进下拉、不装配、不写测试、不做 3.11 整理；`checkstyle-suppressions-engines.xml` 对这六个包继续保留豁免，不逐包删除。理由：D17（spec 已同步为“迁入但停用”），以后补回不必重写。否决：直接删除六种引擎，补回时要从冻结点重迁一遍。重新启用的动作清单见 1.19，启用时另行规划。
3. **Elasticsearch 只保留 8.x 客户端路径。** 决定：旧代码按 `version` 前缀分 v7 与 v8 两套仓储，新实现只留 v8 的请求形状，`version` 配置项保留但只接受 `8.`。理由：v7 已于 2025 年停止维护，D17 只要求保留 Elasticsearch 一种引擎，不要求保留两个大版本。否决：两套并存，代码量翻倍且无测试环境。
4. **PGroonga 关键词检索打分与 ParadeDB 不同。** 决定：融合沿用 RRF，只看名次不看分数，所以换打分器不影响融合结果；单引擎关键词结果的 `score` 字段改为 `pgroonga_score` 归一化到 0 到 1。理由：D18 与 spec 5.9 第 4 条。否决：把 `|||` 与 `&@~` 并存，同表两套全文索引，写入成本翻倍。
5. **检索 SQL 保留索引谓词。** 决定：`embeddings` 检索里 `knowledge_base_id IN (...)`、`knowledge_id IN (...)`、`tag_id IN (...)`、`tenant_id = ?` 保留为查询条件（spec 第 6 节第 3 步明确例外），隔离仍由行级安全兜底。理由：这些谓词是索引命中的过滤条件，删掉会跨知识库召回。否决：全部交给行级安全，p95 延迟不可接受。
6. **模型流终态固定两帧 done。** 决定：所有消费方先收工具调用帧再收 usage 帧，`ChatStream` 增加 `close()` 语义测试。理由：spike 2026-11-09 记录的真实行为，适配任何新消费方都会踩。否决：合并成一帧，事件序列改变违反第一阶段约束。
7. **行级安全空串错误由 framework 收口。** 决定：引擎不自己捕获 `22P02`，统一由计划 01 Task 5 的 `TenantAwareJdbcTemplate` 与 `RowLevelSecurityInterceptor` 转成 `TenantContextMissingException`，本计划只补一条跨模块验收用例。理由：spec 5.10.5 最后一段要求 framework 收口。否决：每个引擎各写一份捕获。

# 1. 盘点

数字取自冻结点工作区，已用 `wc -l` 与 `grep` 复核对得上。

## 1.1 包规模

| 模块 | 文件 | 行数 | 其中测试文件 | 测试行数 |
|---|---:|---:|---:|---:|
| model-runtime | 158 | 18,399 | 29 | 6,275 |
| retrieval-engine | 95 | 20,643 | 37 | 9,934 |
| agent-engine | 72（另 testFixtures 7） | 11,027 | 37 | 4,629 |
| contract 被三引擎直接 import 的包 | 见 1.4（14 个包，含被单引擎引用的 4 个） | | | |

src/main 子包分布：model-runtime 的 `llm.provider` 36 文件、`adapter.provider.chat` 系列 34 文件、`modelcontext` 14 文件（4,123 行）、`embedding` 与 `embedding.provider` 21 文件、`rerank` 与 `rerank.provider` 14 文件；retrieval-engine 的 `engine` 21 文件、七个引擎适配子包 50 文件、`retrieval` 根 5 文件（2,889 行）；agent-engine 的 `agent` 根 20 文件（4,956 行）、`compaction` 12 文件、`skills` 8 文件、`tool` 17 文件。

## 1.2 超限类与超限方法（3.11：类 400 行、方法 100 行）

类（盘点给了 34 个。6 个停用引擎包里的 10 个不整理，ES v7 两个随“只留 v8”删除，本表列需要整理的 23 个）：

| 旧类 | 行 | 处置与新归宿 |
|---|---:|---|
| `modelcontext.ModelOutput` | 759 | 按工具种类拆：`ToolResultRenderer`（入口）、`KnowledgeToolRendering`、`WebToolRendering`、`DatabaseToolRendering`、`ModelTextLimits` |
| `chat.anthropic.AnthropicChat` | 758 | `AnthropicChat`（编排）、`AnthropicRequestWriter`、`AnthropicStreamReader`、`AnthropicToolDecoder` |
| `modelcontext.ToolPolicy` | 648 | `ToolSchemaPolicy`（schema 与预算）、`ToolArgumentPolicy`（参数改写） |
| `modelcontext.Registry` | 644 | `ModelContextRegistry`（门面）、`ResourceHandleEncoder`、`ToolArgumentResolver`、`ToolResultCompactor` |
| `llm.ollama.OllamaService` | 608 | `OllamaAdmin`（拉取与版本）、`OllamaModelCatalog`、`OllamaHealth` |
| `modelcontext.SourceRegistry` | 519 | `SourceRegistry`（注册）、`SourceHandleTable` |
| `llm.extract.EntityExtraction` | 516 | `EntityExtraction`（编排）、`EntityExtractionPrompts`、`EntityResponseParser` |
| `chat.RemoteApiChat` | 500 | `RemoteChat`（编排）、`RemoteRequestAssembler`、`RemoteStreamDecoder`、`RemoteCompletionReader`、`RemoteHttpExchange` |
| `chat.PromptCache` | 479 | `PromptCache`（键）、`PromptCacheHeaders`、`PromptFingerprint` |
| `chat.OllamaChat` | 454 | `OllamaChat`、`OllamaStreamDecoder` |
| `modelcontext.SourceToolCodec` | 431 | `SourceToolCodec`（编解码）、`McpSourceRewriter` |
| `retrieval.HybridSearchService` | 762 | `HybridSearchService`（用例）、`StoreGroupResolver`、`HybridFanout`、`HybridFusion`、`HybridResultAssembly` |
| `engine.elasticsearch.ElasticsearchV8RetrieveRepository` | 536 | `ElasticsearchEngine`（门面）、`ElasticsearchV8Search`、`ElasticsearchV8IndexWriter`、`ElasticsearchIndexAdmin` |
| `engine.EngineRegistry` | 461 | `EngineRegistry`（登记表）、`StoreEngineLoader`（懒加载与超时） |
| `engine.milvus.MilvusRetrieveRepository` | 608 | `MilvusEngine`（门面）、`MilvusSearch`、`MilvusIndexWriter`、`MilvusCollectionAdmin` |
| `engine.EngineFactory` | 422 | `EngineFactory`（三引擎登记表）、`EngineAddressPolicy`、`EngineProbe` |
| `agent.AgentEngine` | 709 | `AgentEngine`（编排）、`AgentRunContext`、`AgentEventPublisher` |
| `agent.ActPhase` | 691 | `ActPhase`（阶段）、`ToolCallDispatcher`、`TruncatedCallGuard` |
| `agent.ThinkPhase` | 581 | `ThinkPhase`（阶段）、`ModelStreamPump`、`CancellationWatch`、`EmptyReplyNudge` |
| `agent.AgentPrompts` | 510 | `AgentPrompts`（段顺序）、`AgentPromptSections`、`SkillDisclosure` |
| `agent.ConversationAssembler` | 461 | `ConversationAssembler`、`RuntimeContextBlock`、`HistoryRetrievalRedactor` |
| `agent.tool.ToolRegistry` | 442 | `ToolRegistry`（登记）、`ToolCatalogGuard`、`DeferredToolLoader` |
| `agent.ObservePhase` | 421 | `ObservePhase`、`TerminalDecision` |

盘点还列了 12 个超限类：10 个属 6 个停用引擎（Doris、OpenSearch、Qdrant、Sqlite、TencentVectorDb、Weaviate 各若干），随包停用原样保留，不做 3.11 整理；`ElasticsearchV7WriteOps` 与 `ElasticsearchV7RetrieveRepository` 属 ES v7，按“设计决定”第四类第 3 条只删不重建。

方法（旧代码超 100 行的 10 个）：

| 旧方法 | 行 | 新归宿 |
|---|---:|---|
| `ReActIteration.runReActIterationBody` | 174 | `AgentRunLoop.iterate` 拆成 `think`、`act`、`observe`、`finalize` 四个阶段方法 |
| `ActPhase.runToolCallInner` | 155 | `ToolCallDispatcher.dispatch` 拆成 `validate`、`authorize`、`execute`、`collect` |
| `ThinkPhase.streamLLMToEventBus` | 151 | `ModelStreamPump.pump` 拆成 `readChunk`、`emitThinking`、`emitText`、`emitToolCalls` |
| `ThinkPhase.callLLMWithRetry` | 147 | `ModelCallRetrier.call` 拆成 `attempt`、`classifyFailure`、`backoff` |
| `ThinkPhase.streamThinkingToEventBus` | 131 | `ModelStreamPump.emitThinking` 与 `ThinkStreamSplitter` 协作 |
| `HybridSearchService.hybridSearch` | 126 | `HybridSearchService.search` 拆成 `loadSources`、`resolveGroups`、`fanout`、`fuse`、`expand` |
| `HybridResultOps.processSearchResults` | 122 | `HybridResultAssembly.assemble` 拆成 `rank`、`expandContext`、`attachMedia` |
| `AgentEngineFactoryImpl.create` | 108 | `AgentEngineFactoryImpl.create` 拆成 `buildToolRegistry`、`attachMcpTools`、`attachSkills`、`newEngine` |
| `QdrantWriteOps.copyIndices` | 105 | 随包停用，原样保留 |
| `ModelOutput.modelWebFetchOutput` | 102 | `WebToolRendering.fetch` 拆成 `renderEvidence`、`renderSummary`、`renderContent` |

## 1.3 Ops 与 Support 的拆解去向

盘点结论：三模块共 27 个 `*Ops` 加 1 个 `FileOps`，无 `*Support`、`*Helper`、`*Facade` 类。逐个归宿：

**model-runtime（4 个）**

| 旧类 | 行 | 新类 | 拆分依据 |
|---|---:|---|---|
| `RemoteApiRequestOps` | 309 | `RemoteRequestAssembler` | 变化原因：出站请求体形状随厂商而变 |
| `RemoteHttpOps` | 122 | `RemoteHttpExchange` | 变化原因：传输与重试 |
| `RemoteApiStreamOps` | 360 | `RemoteStreamDecoder` | 变化原因：SSE 帧解析 |
| `RemoteApiResponseOps` | 108 | `RemoteCompletionReader` | 变化原因：非流式响应解析 |

**retrieval-engine（23 个）**

| 旧类 | 行 | 新类 | 拆分依据 |
|---|---:|---|---|
| `HybridFusionOps` | 316 | `HybridFusion` | RRF 融合规则 |
| `HybridResultOps` | 241 | `HybridResultAssembly`（再拆 `rank`、`expandContext`、`attachMedia`） | 结果装配三个变化原因 |
| `HybridStoreGroupOps` | 128 | `StoreGroupResolver` | store 分组与引擎解析 |
| `DorisSearchOps` / `DorisWriteOps` / `DorisAdminOps` | 177 / 477 / 363 | 停用保留 | 引擎停用，不整理 |
| `ElasticsearchV7SearchOps` / `ElasticsearchV7WriteOps` | 205 / 408 | 删除 | 只留 v8 |
| `ElasticsearchV8SearchOps` / `ElasticsearchV8WriteOps` | 157 / 316 | `ElasticsearchV8Search` / `ElasticsearchV8IndexWriter` | 读面与写面 |
| `MilvusSearchOps` / `MilvusWriteOps` | 167 / 345 | `MilvusSearch` / `MilvusIndexWriter` | 读面与写面 |
| `OpenSearchSearchOps` / `OpenSearchWriteOps` / `OpenSearchAdminOps` | 237 / 552 / 446 | 停用保留 | 引擎停用，不整理 |
| `QdrantSearchOps` / `QdrantWriteOps` | 206 / 379 | 停用保留 | 引擎停用，不整理 |
| `SqliteSearchOps` / `SqliteWriteOps` | 188 / 387 | 停用保留 | 引擎停用，不整理 |
| `TencentVectorDbSearchOps` / `TencentVectorDbWriteOps` | 187 / 267 | 停用保留 | 引擎停用，不整理 |
| `WeaviateSearchOps` / `WeaviateWriteOps` | 208 / 328 | 停用保留 | 引擎停用，不整理 |

**agent-engine（1 个）**

| 旧类 | 行 | 新类 | 拆分依据 |
|---|---:|---|---|
| `compaction.FileOps` | 212 | `CompactionFiles` | 沙箱路径存续，只改类名与私有方法粒度；无宿主回引用，不是 Ops 模式 |

统一规则：新类构造器显式收依赖，不持门面回引用；宿主只留编排方法。这一步在 Task 7、Task 8、Task 9 落地。

## 1.4 contract 各包到新位置映射

只列三引擎直接 import 的包。规则：引擎 api 只放入口与 DTO，需要业务库数据的放 spi。

| 旧包 | 类/行 | 新位置 | 说明 |
|---|---|---|---|
| `contract.model` | 37 类 / 1,672 行 | `modelruntime.api` | `ModelCatalogPort`→`spi.ModelCatalogReader`；`ModelFactsPort`→`spi.ModelFactsReader`；`ModelView` 不进引擎（管理面），归计划 03 |
| `contract.retrieval` | 17 / 1,495 | `retrieval.api` | `HybridSearchPort`→`HybridSearchApi`；`IndexEngineResolver`、`IndexEngine`→`IndexingApi`；`VectorIndexStore`→`IndexingApi` 的写面方法；`SearchTextUtil` 不进本计划（临时保留在 `contract` 未删，停用包 `qdrant` 仍 import 它；归计划 04 的 `SearchTextUtil`，`SearchUtilTest` 同迁） |
| `contract.vectorstore` | 10 / 355 | `retrieval.api` | `VectorStoreLookup`→`spi.VectorStoreCatalog`；`VectorEngineTypes` 收窄为三值 |
| `contract.knowledge`（检索用 3 类） | `ChunkFacts`、`KnowledgeBaseSearchFacts`、`KnowledgeDocumentFacts` 与三个 Gateway | `retrieval.spi` | 合成 `KnowledgeSearchSource` 与三个 record，`KnowledgeBaseSearchFacts` 改名为 `KnowledgeBaseFacts` |
| `contract.tenant`（`TenantConfigLookup`） | 1 接口 | `retrieval.spi.TenantRetrievalSettings` | 只留检索用到的两项配置 |
| `contract.graph` | 5 / 92 | `modelruntime.api` | `EntityExtractionResult` 与图值类型；retrieval 通过 model-runtime 依赖取得 |
| `contract.agent`（引擎消费面） | 21 / 1,405 | `agentengine.api` 与 `agentengine.spi` | `AgentEngineFactory`、`AgentEngineHandle`、`AgentEngineRequest`、`AgentRunSpec`、`AgentStep`、`AgentToolCall`、`AgentBudgets`、`KnowledgeBaseInfo`、`SelectedDocumentInfo`、`ThinkBlocks`、`ToolCallTarget`、`ImageDescriberFunc`、`Cleanable` 进 api；`SkillCatalogPort`→`spi.SkillCatalog`；`SteerSink`→`spi.SteerSink`；`AgentLookup`、`AgentChannelCleaner` 不进引擎（app 域内） |
| `contract.tool` | 11 / 764 | `agentengine.api` | `AgentTool`、`ToolRequest`、`ToolExecContext`、`ToolResult`、`ToolCancellation`、`ToolOutput`、`ToolDefinitions`；`BaseTool` 丢弃（app 自建工具用组合）；`McpCatalogGuardedTool` 归计划 08 |
| `contract.pipeline` | 5 / 331 | `agentengine.spi` | `McpToolRegistrationPort` 与 `McpToolingGateway` 合成 `McpToolProvider` 与 `McpToolSession`；`ToolRegistryPort` 收成引擎内 `ToolRegistry` 的包可见方法 |
| `contract.event` | 5 / 236 | `agentengine.api` 与 `agentengine.spi` | `Event`→`AgentEvent`，`EventBusInterface`→`spi.AgentEventSink`，`EventBusFactory` 不进引擎（app 装配） |
| `contract.event.payload`（引擎用 10 类） | 21 / 2,073 | `agentengine.api` | 只迁 10 个 agent 载荷：Action、Complete、FinalAnswer、Thought、ToolCall、ToolResult、ContextCompacted、Error、UserMessageInjected、Plan；其余 11 个归计划 06、08 |
| `contract.error` | 3 / 213 | framework | `BizException`→`BusinessRuntimeException`，`AppError` 与 `ErrorCode` 丢弃（错误码改枚举） |
| `contract.wiki.WikiLanguageSupport`（仅取语言名） | 1 类 | `agentengine.api` 的 `PromptLanguage` | 只搬 `languageNameFromContext()` 一个能力，线程本地由 framework 的 Locale 解析承担 |
| `contract.session.MessageArtifact` | 1 类 | 不迁 | retrieval 只在 javadoc 里提到它，代码不引用，改成文字说明 |

## 1.5 旧入口到新 api 与 spi

| 旧入口类 | 行 | 新位置 |
|---|---:|---|
| `modelruntime.application.ModelRuntimeAssembler` | 302 | `modelruntime.api.ModelRuntime` 的实现，内部包 `modelruntime.assembly.ModelRuntimeAssembler` |
| `modelruntime.application.ModelRuntimeEmbeddingAdapter` | 33 | 合并进 `ModelRuntimeAssembler`（增加 `EmbeddingPort embeddingPort()`），删除独立类 |
| `modelruntime.application.LlmChatClients` | 73 | `modelruntime.assembly.ChatClientFactory` |
| `llm.extract.EntityExtractionAdapter` | 30 | 合并进 `EntityExtraction`，删除独立类 |
| `llm.ollama.OllamaService` | 608 | 拆成 `ollama.OllamaAdmin`、`OllamaModelCatalog`、`OllamaHealth` |
| `retrieval.HybridSearchService` | 762 | `retrieval.api.HybridSearchApi` 的实现 `retrieval.service.HybridSearchService` |
| `retrieval.engine.IndexEngineGateway` | 250 | `retrieval.api.IndexingApi` 的实现 `retrieval.service.IndexingService` |
| `retrieval.engine.VectorStoreEngineCatalog` | 292 | `retrieval.api.VectorStoreCatalogApi` 的实现 `retrieval.service.VectorStoreEngineCatalog` |
| `retrieval.engine.EngineRegistry` | 461 | 内部 `retrieval.engine.EngineRegistry`（不进 api） |
| `retrieval.engine.PgVectorRetrieveRepository` | 297 | 拆 `retrieval.engine.pgvector.PgVectorSearch` 与 `PgVectorIndexWriter` |
| `retrieval.engine.VectorStoreService` | 204 | `retrieval.engine.pgvector.PgVectorIndexWriter` |
| `retrieval.graph.Neo4jGraphRepository` | 269 | 内部 `retrieval.graph.Neo4jGraphRepository`（不对外暴露，app 通过 `IndexingApi` 不触及图） |
| `agent.AgentEngineFactoryImpl` | 165 | `agentengine.api.AgentEngineFactory` 的实现 |
| `agent.AgentEngineHandleAdapter` | 50 | 合并进 `AgentEngineFactoryImpl`，删除独立类 |
| `agent.AgentEngine` | 709 | 内部 `agentengine.react.AgentEngine` |
| `agent.skills.SkillCatalogService` | 376 | 删除：管理面归计划 06 的 `SkillService`；`SkillFrontmatter` 能力进 `agentengine.api` |
| `agent.tool.ToolRegistryPortImpl` | 64 | 合并进 `agentengine.react.ToolRegistry` |

## 1.6 旧表到新表

引擎没有自己的表与业务队列，只碰 `embeddings` 与 `skills` 两张。

| 新表 | 旧表 | 聚合根与写属主 | 字段映射 | 本计划动作 |
|---|---|---|---|---|
| `embeddings` | `embeddings` | 计划 04 的 knowledge 域（`chunk` 聚合） | `id` integer 改 bigint；`embedding` halfvec 保留；`content` 加 PGroonga 索引；其余列名不变（`source_id`、`source_type`、`chunk_id`、`knowledge_id`、`knowledge_base_id`、`tag_id`、`dimension`、`is_enabled`） | 不改 DDL，只改检索与写入 SQL；PGroonga 索引 DDL 文本交计划 04 |
| `skills` | `skills` | 计划 06 的 agent 域（`skill` 聚合） | 见计划 06 的 1.4 | 本计划只保留 `SkillCatalog` spi，删除 `SkillEntity`、`SkillMapper`、`SkillCatalogService` |

并发机制与后台任务（无 `@Scheduled`、`@Async`、业务队列）：

| 项 | 旧位置 | 新做法 |
|---|---|---|
| Redis ZSET `weknora:modelsem:<modelId>` | `llm.limiter.RedisLimiter` | 保留，键前缀改 `ragagent:model:sem:<modelId>`，常量收口 `LimiterKeys` |
| 虚拟线程执行器 6 处 | 批量嵌入、索引分片、多 store 扇出、复合引擎、删引擎前的 Doris、工具批次 | 保留，统一用 `Thread.ofVirtual()` 或 `Executors.newVirtualThreadPerTaskExecutor()`，并发上限用 `Semaphore` 常量 |
| 引擎懒加载超时 | `EngineRegistry` 10 秒 | 保留，`ENGINE_BUILD_TIMEOUT_SEC` 常量 |
| 后台任务、`@Scheduled`、`@Async` | 无 | 不适用 |

## 1.7 api 与 spi 清单摘要

见“对外承诺”五张表。api 与 spi 骨架在 Task 1 落地，实现分别在 Task 2、Task 3、Task 4，装配在 Task 5。任务执行时以那几张表为准。

## 1.8 停用的 6 种引擎

六个引擎的源码随 Task 3 原样迁入 `backend/retrieval-engine`，不注册、不进下拉、不装配、不写测试、不做 3.11 整理。下表记录它们的旧实现规模、依赖与配置，供以后启用时对照。

| 引擎 | 旧实现类（行数） | 依赖 | 配置项与缺省 | 停用连带 |
|---|---|---|---|---|
| sqlite | `SqliteRetrieveRepository` 530、`SqliteSearchOps` 188、`SqliteWriteOps` 387、`SqliteCjkBigram` 131 | `sqlite-jdbc` | `addr` 当路径，env `SQLITE_PATH`，缺省 `./data/weknora-retrieval.sqlite` | 保留 `sqlite-jdbc` 依赖与全部源码；`EngineTypes` 去掉 `ENGINE_SQLITE`，`EffectiveEngines` 去掉 `sqlite` 分支 |
| opensearch | `OpenSearchRetrieveRepository` 575、`OpenSearchSearchOps` 237、`OpenSearchWriteOps` 552、`OpenSearchAdminOps` 446、`OpenSearchDriverException` 81 | 自持 HTTP，无额外依赖 | `addr` 必填、`username`、`password`、`insecureSkipVerify`、env `OPENSEARCH_INDEX` 缺省 `weknora` | 保留源码；去 `EngineFactory` 分支、`VectorStoreEngineCatalog` 探测分支；`boot` 侧 `OpenSearchAuditSinkAdapter` 不迁（计划 09 删旧模块时一并消失），停用包里的 `AuditSink` 签名保留 |
| doris | `DorisRetrieveRepository` 413、`DorisSearchOps` 177、`DorisWriteOps` 477、`DorisAdminOps` 363、`DorisSql` 409、`DorisSqlExecutor` 51、`JdbcDorisSqlExecutor` 162、`DorisStreamLoadClient` 271、`DorisCompatMode` 91、`DorisVectorEmbedding` 21 | `mysql-connector-j`（runtime）、`HikariCP` | `addr`、`database`、`username`、`password`、`httpPort` 缺省 8030、env `DORIS_COMPAT_MODE`、`DORIS_TABLE_PREFIX` | 保留 `mysql-connector-j` 与 `HikariCP` 依赖与全部源码；去 `EngineFactory` 分支与 `EffectiveEngines` 的 `doris` 一条 |
| qdrant | `QdrantRetrieveRepository` 547、`QdrantSearchOps` 206、`QdrantWriteOps` 379、`QdrantRestClient` 170、`QdrantVectorEmbedding` 20 | 自持 HTTP | `host` 必填、`port` 缺省 6334、`apiKey`、`useTls`、env `QDRANT_COLLECTION` | 保留源码；去工厂分支与探测分支 |
| weaviate | `WeaviateRetrieveRepository` 551、`WeaviateSearchOps` 208、`WeaviateWriteOps` 328、`WeaviateRestClient` 246、`WeaviateGql` 359、`WeaviateVectorEmbedding` 18 | 自持 HTTP + GraphQL | `host` 缺省 `weaviate:8080`、`scheme` 缺省 `http`、`apiKey`、`grpcAddress`、env `WEAVIATE_COLLECTION` | 保留源码；去工厂、探测；CI 的 Weaviate 服务移除；`WeaviateDriverLocalIT` 丢弃 |
| tencent_vectordb | `TencentVectorDbRetrieveRepository` 634、`TencentVectorDbSearchOps` 187、`TencentVectorDbWriteOps` 267、`TencentVectorDbRestClient` 259、`TencentVectorDbBm25` 354、`JiebaTokenizer` 379 | 自持 HTTP | `addr` 必填且 https 被拒、`username`、`apiKey`、`database`、`collectionName` | 保留源码与 `jieba` 资源；去工厂与探测；差分测试不写 |

保留项：`elasticsearch`（v8 请求形状，保留 `username`、`password`、`indexName`、`numberOfShards`、`numberOfReplicas`、env `ELASTICSEARCH_INDEX` 缺省 `xwrag_default`）、`milvus`（`addr` 缺省 `localhost:19530`、`username`、`password`、`database`、env `MILVUS_METRIC_TYPE`、`MILVUS_COLLECTION`）、`postgres`（默认连接或 `addr`，env store 才能出现）。

## 1.9 ES 与 Milvus 的 Testcontainers

| 引擎 | 镜像 | 启动参数 | 就绪判定 | 用例 |
|---|---|---|---|---|
| Elasticsearch | `docker.elastic.co/elasticsearch/elasticsearch:8.19.0` | `xpack.security.enabled=false`、`xpack.security.http.ssl.enabled=false`、`discovery.type=single-node`、`ES_JAVA_OPTS=-Xms512m -Xmx512m`、内存 1g | `GET /_cluster/health?wait_for_status=yellow&timeout=30s` 返回 `status` | `ElasticsearchEngineIT`（建索引、批量写、向量与关键词检索、删除、拷贝） |
| Milvus | `milvusdb/milvus:v2.6.11` | `command`：`milvus`、`run`、`standalone`；环境 `ETCD_USE_EMBED=true`、`ETCD_DATA_DIR=/var/lib/milvus/etcd`、`COMMON_STORAGETYPE=local`、`DEPLOY_MODE=STANDALONE`；`--security-opt seccomp=unconfined` | `GET /healthz` 200，再轮询 `/v2/vectordb/collections/list` 成功 | `MilvusEngineIT`（建集合、Upsert、检索、回写、拷贝、删除），超时 120 秒 |

两个 IT 用 Testcontainers `GenericContainer`，`@Testcontainers(disabledWithoutDocker = true)`，不打 `@EnabledIfEnvironmentVariable`（CI 起不来时显式失败，不伪装成 skip）。Milvus 的容器启动慢，`withStartupTimeout(Duration.ofMinutes(3))`。

## 1.10 旧测试处置

处置三种：移植业务断言、改写为新规范、丢弃。丢弃理由只允许“钉 Go 版文案、零值或字节形状”。

**model-runtime（29 个）**

| 旧测试类 | 处置 |
|---|---|
| `ConcurrencyEmbedderValidationTest`、`EmbeddingVectorsTest`、`ConcurrencyGovernorTest`、`RedisLimiterTest`、`ModelPredicateTest`、`AsrTranscriberRouterTest`、`AnthropicChatTest`、`ConcurrencyChatClientTest`、`ImageResolverTest`、`JsonFieldExtractorTest`、`OllamaChatTest`、`OllamaServiceTest`、`PromptCacheTest`、`ProviderAdapterRegistryTest`、`VlmClientTest`、`ModelOutputTest` | 移植，类名随拆分后的新类改 |
| `SseReaderTest`、`ThinkingEmitterTest`、`RemoteApiChatTest`、`VlmOllamaTest`、`LlmChatClientsTest`、`ModelRuntimeAssemblerVlmTest` | 改写为新规范：断言流语义与错误码，去掉“照 Go 原文”“Go 零值 StreamResponse”这类断言；`RemoteApiChatTest` 的键序用例保留为 `RemoteRequestAssemblerTest` |
| `ProviderValidationTest`、`ProviderRegistryCompletenessTest`、`ProviderModelConfigValidatorTest` | 改写：校验文案改错误码断言，`ProviderRegistryCompletenessTest` 保留枚举全覆盖，删字节金片 |
| `JsonBridge`、`ModelContextGolden`、`ModelContextRecordingTest` | 丢弃：测试辅助与录制常量，钉 Go encoding/json 转义与拍摄字节 |
| `AnthropicToolsTest` | 改写：保留 schema 键与工具三例，删“逐字节等价地保留”断言 |

**retrieval-engine（37 个）**

| 旧测试类 | 处置 |
|---|---|
| `CompositeRetrieveEngineTest`、`EngineAwareNormalizerTest`、`EngineRegistryTest`、`IndexEngineGatewayTest`、`KeywordsVectorHybridRetrieveEngineServiceTest`、`RetrieveEngineFactoriesTest`、`HybridFusionOpsStoreRankTest`、`HybridResultOpsContextExpansionTest`、`HybridResultOpsMediaMetadataTest`、`HybridSearchServiceStoreGroupTest`、`RetrievalNeutralTypesTest`、`VectorStoreServiceIndexRowArityTest`、`Neo4jGraphRepositoryTest`、`MilvusDriverLocalIT` | 移植，`MilvusDriverLocalIT` 改写成 Testcontainers 版 `MilvusEngineIT` |
| `WeaviateDriverLocalIT` | 丢弃：Weaviate 停用，该用例不随停用包保留 |
| `EngineTypesSourceTest`、`VectorStoreEngineCatalogTest`、`EngineFactoryTest` | 改写：引擎集合从 9 值改 3 值，`EngineFactoryTest` 只测 postgres、elasticsearch、milvus 三分支与未支持类型的错误码 |
| `ElasticsearchV7RetrieveRepositoryTest`、`ElasticsearchV8RetrieveRepositoryTest` | 合并为 `ElasticsearchEngineTest`，只留 v8 请求形状，去掉逐请求字节断言里的冗余字段 |
| `MilvusFilterTest`、`MilvusRetrieveRepositoryTest` | 改写：保留过滤表达式、度量、集合命名、Upsert 与回写，去掉“照 Go 的括号”类断言 |
| `PgVector` 相关（`boot/src/test/.../PgVectorEngineRepositoryTest`） | 重写为 `PgVectorSearchTest` 与 `PgVectorIndexWriterTest`，跑在 Testcontainers PostgreSQL 17 上 |
| `SearchUtilTest` | 迁移到计划 04（`SearchTextUtil` 归 knowledge） |
| `RetrievalEngineTestSupport` | 移植为测试辅助类，随保留引擎的用例一起保留，不单独计入 37 个处置项 |
| `ArtifactReferenceRewriterTest` | 改写：保留改写规则，删 `rewriteMatchesAgentPromptGoldens` 的逐字节 fixture |
| `JiebaTokenizerDiffTest`、`TencentVectorDbBm25Test`、`TencentVectorDbRetrieveRepositoryTest`、`DorisPureFunctionsTest`、`DorisRetrieveRepositoryTest`、`DorisStreamLoadTest`、`QdrantRetrieveRepositoryTest`、`SqliteCjkBigramTest`、`SqliteRetrieveRepositoryTest`、`OpenSearchRetrieveRepositoryTest`、`WeaviateGqlTest`、`WeaviateRetrieveRepositoryTest`、`ElasticsearchV7*`、`HybridSearchServiceStoreGroupTest` 里 `knownEngineTypesMatchGoTable` | 丢弃或 `@Disabled`：引擎停用后不要求执行，或断言的是 Go SDK 的实测输出 |

**agent-engine（37 个）**

| 旧测试类 | 处置 |
|---|---|
| `ActPhaseInterruptJoinTest`、`AgentConfigKeyUsageTest`、`AgentEngineFailureTerminalTest`、`AgentEngineNullContextTest`、`ConversationAssemblerCurrentTimeTest`、`SkillPinnedInstructionsTest`、`DbSkillSourceTest`、`ManagerTest`、`SkillFrontmatterTest`、`ExecutionPolicyStatefulReadTest`、`ParamCasterRecordingTest`、`ParamValidatorRecordingTest`、`JsonRepairRecordingTest`、`NormalizeToolCallIdRecordingTest`、`StripThinkRecordingTest`、`ThinkStreamSplitterRecordingTest`、`SequentialThinkingRecordingTest`、`TodoWriteRecordingTest`、`ToolRegistryRecordingTest`、`TruncateRecordingTest` | 改写：去掉 `RecordingSupport` 的录制常量比对，改成行为断言与边界表；语料保留在测试文件里作为参数化数据 |
| `AgentConstsTest`、`AgentPromptsTest`、`ContextDiagnosticsTest`、`TokenEstimatorTest`、`CompactionOverflowTest`、`CompactionPreparationTest`、`CompactionSettingsTest`、`CompactorTest`、`ConversationSerializerTest`、`CutPointTest`、`FileOpsTest` | 移植：压缩与提示词的业务断言全部保留，引用 `AgentPromptGolden` 的常量改为内联期望值 |
| `AgentStepsJsonTest` | 改写：保留 `agent_steps` jsonb 形状断言，去掉逐字节比对 |
| `SkillCatalogServiceTest` | 改写为 `SkillServiceTest`，进计划 06 |
| `SkillReadFileToolTest`、`ToolImagesTest`、`ScopeAuthRecordingTest` | 改写：保留三段式输出、图片注入、鉴权判定，去 golden fixture |
| `ToolJsonRecordingTest` | 丢弃：钉 Go 版重编码键序（新实现用 `ToolJson.write` 的递归键排序，由 `ToolJsonTest` 覆盖通用规则） |

## 1.11 5.10.1 的 41 项到用例映射（按 5.10.4 三组分）

组别代号：R 回归对拍（旧引擎与新引擎同题比对 SSE 序列）、F 故障注入（`stub-llm-server.py` 场景）、L 长会话（超窗口压缩）。41 项每项都有类名与用例名，用例在 Task 9 落地。

**计数说明（待用户核对）**：spec 5.10.4 与 D24 都写“41 项”，但 5.10.1 的表逐行数只有 38 行。差额 3 项在 spec 里没有点名。本计划把 spike 结论要求的 2 项（两帧 done、行级安全报错收口）与架构规则 1 项（引擎只在配置类装配）补成第 39 到 41 项，标注来源，不臆造其它能力。若用户知道 spec 原意的另 3 项，补进本表并改 Task 9。

| # | 能力 | 组 | 用例 |
|---:|---|---|---|
| 1 | 上下文压缩与摘要 | L | `ContextCompactionTest.compactsWhenWindowExceeded` |
| 2 | 压缩阈值与预算计算 | L | `CompactionBudgetTest.triggersAtConfiguredThreshold` |
| 3 | 切点选择 | L | `CutPointTest.neverSplitsToolCallFromResult` |
| 4 | 摘要注入与增量更新 | L | `CompactionPreparationTest.nextSummaryInheritsPrevious` |
| 5 | 溢出错误识别 | F | `CompactionOverflowTest.matchesTwentyFourVendorPatterns`、`excludesRateLimitWording` |
| 6 | 工具结果裁剪兜底 | L | `ContextBudgetTest.trimsSingleHugeToolResult` |
| 7 | Token 估算 | L | `TokenEstimatorTest.estimatesFromUsageBaselineDelta` |
| 8 | 估算漂移告警 | R | `ContextDiagnosticsTest.emitsDriftWarnWhenEstimateExceedsActual` |
| 9 | 运行中插话 | R | `SteerIntakeTest.injectsMidTurnMessage`、`overBudgetAllowsOneTurnOverdraft` |
| 10 | 工具参数类型矫正 | F | `ParamCasterTest.castsStringBooleansAndNumbers` |
| 11 | 坏 JSON 修复 | F | `JsonRepairTest.repairsTrailingCommaAndBadEscape` |
| 12 | 截断识别 | F | `JsonRepairTest.marksTruncated`、`TruncatedCallGuardTest.rejectsAllCallsOnLengthStop` |
| 13 | 工具调用 ID 归一 | F | `NormalizeToolCallIdTest.replacesEmptyLongAndChineseIds` |
| 14 | 消息清洗 | F | `MessageSanitizerTest.mergesConsecutiveRolesAndDropsOrphanTool` |
| 15 | 流式 think 标签拆分 | F | `ThinkStreamSplitterTest.splitsAcrossChunkBoundary` |
| 16 | 空回复重试 | F | `EmptyReplyNudgeTest.retriesTwiceThenFails`（场景 `empty`） |
| 17 | 重复内容卡死检测 | F | `RepeatedContentGuardTest.stopsAfterTwoIdenticalRounds`（场景 `repeat`） |
| 18 | `length` 停止时拒绝全部工具调用 | F | `TruncatedCallGuardTest.rejectsEveryCallAfterLengthFinish` |
| 19 | `content_filter` 终态处理 | F | `TerminalDecisionTest.contentFilterEmitsFixedRefusal` |
| 20 | 迭代上限兜底合成 | R | `FinalizePhaseTest.synthesizesAnswerAtMaxIterations` |
| 21 | LLM 失败后从工具结果合成 | F | `ThinkPhaseTest.synthesizesFromToolResultsAfterModelFailure`（场景 `early-error`） |
| 22 | 工具执行中断语义 | R | `ToolBatchExecutorTest.cancelsSiblingsOnInterrupt` |
| 23 | 取消到关闭模型流 | R | `CancellationWatchTest.closesStreamOnCancel`（场景 `slow`） |
| 24 | 停顿看门狗 | F | `StreamWatchdogTest.abortsIdleHalfOpenStreamWithoutTotalTimeout`（场景 `slow`） |
| 25 | 提示词分段与占位符 | R | `AgentPromptsTest.sectionsFollowDeclaredOrder`、`GroundingPromptTest.rendersOnlyRegisteredTools` |
| 26 | runtimeContext 块 | R | `RuntimeContextBlockTest.containsLocalAndUtcTimeAndPinnedDocs` |
| 27 | 历史检索结果脱敏 | R | `HistoryRetrievalRedactorTest.replacesOldToolResultsWithNotice` |
| 28 | Level 1/2/3 技能披露 | R | `SkillDisclosureTest.disclosesCatalogAndPinnedInstructions` |
| 29 | 技能目录与白名单 | R | `VisibleSkillCatalogTest.filtersByTenantAndAllowList`、`SkillFrontmatterTest.toleratesBrokenFrontmatter` |
| 30 | 工具并发策略 | R | `ExecutionPolicyTest.readOnlyToolsRunConcurrentlyAndWritesBarrier` |
| 31 | 结果槽非空保证 | F | `ToolBatchExecutorTest.fillsFailurePlaceholderInEveryCase` |
| 32 | MCP 工具每轮刷新与清理 | R | `McpToolSessionTest.refreshesEachRoundAndCleansInFinally` |
| 33 | MCP 目录鉴权先于 schema 校验 | R | `ToolCatalogGuardTest.rejectsBeforeSchemaValidation` |
| 34 | 工具下线重定向 | R | `ToolDefinitionsTest.retiredToolReturnsReplacement` |
| 35 | 工具输出预算与自报 | R | `ToolOutputTest.honorsSelfReportedBudget` |
| 36 | 事件发射顺序契约 | R | `AgentEventOrderTest.errorBeforeTerminalOnce`、`doneAfterSteerDrainReusesAnswerId` |
| 37 | 事件载荷键序稳定 | R | `AgentEventPayloadTest.keysAreDeepSortedByUtf8Bytes` |
| 38 | 持久化载荷剥离 | R | `AgentEventPayloadTest.stripsBase64AndChunkLists` |
| 39 | 两帧 done 终态（spike 结论 1） | R | `ChatStreamTerminalTest.emitsToolCallDoneThenUsageDone` |
| 40 | 租户上下文缺失明确报错（spike 结论 2） | R | `TenantContextMissingTest.mapsRowLevelSecurityErrorToFrameworkCode` |
| 41 | 引擎装配只经配置类（架构规则） | R | `EngineAssemblyBoundaryTest.enginesAreConstructedOnlyInAppConfig` |

## 1.12 Go 兼容写法使用点统计

| 写法 | model-runtime | retrieval-engine | agent-engine | 处理 |
|---|---:|---:|---:|---|
| `BizException` throw 点 | 71 | 3 | 0 | model-runtime 69 处是 `BizException.badRequest("英文文案")`，合并成 3 个错误码；retrieval 3 处改 `RetrievalCode` |
| `BizException.wireText` 调用 | 0 | 0 | 7 | 改成 `BusinessRuntimeException` 的 `wireText(Throwable)` 工具，语义保留（SSE 错误帧文案要带错误码前缀） |
| `AppError` / `ErrorCode` 引用 | 3 | 12 | 2 | 全部删除，换 `BusinessRuntimeException` 与 `RetrievalCode` |
| `Map<String, Object>` 出现点 | 24（10 文件） | 183（29 文件） | 40（11 文件） | 区分两类：JSON 组装与 jsonb 透传保留（`Map<String, Object>` 是 Jackson 与 jsonb 的天然形状）；业务数据传递改为 record 或 BO，逐处清单在 Task 2、3、8 的步骤里，共 48 处要求改 |
| `Map<String,Object>` 无空格写法 | 0 | 0 | 0 | 不适用 |
| `rawBody` | 2（`OpenAiAsrTranscriber` 的局部变量，HttpClient 响应体） | 0 | 0 | 改名 `responseBody`，不是请求绑定，无 Go 语义 |
| `trimGo`、`orGoZero`、`GoJsonBindError` | 0 | 0 | 0 | 不适用 |

`Map<String, Object>` 保留与改写的分界：载荷序列化、JSON 解析中间结构、jsonb 读写、工具 `data` 字段保留；跨方法传业务字段（实体、BO、命令）改为 record。要求改写的 48 处集中在 `AgentState`、`RetrievalObs`、`HybridSearchService` 的观测字段、`ModelOutput` 的渲染中间值。

## 1.13 错误码清单

模块名称统一写 `ragagent`。业务错误码格式 `1` + `001` + 模块号 + 3 位序号。

| 模块名称 | 功能点描述（key） | 业务场景 | 中文 | English |
|---|---|---|---|---|
| ragagent（020） | `modelruntime.chat-config-required` | 装配聊天客户端时配置为空 | 聊天模型配置缺失 | Chat model configuration is required |
| ragagent（020） | `modelruntime.chat-source-unsupported` | 模型来源既非 local 也非 remote | 不支持的模型来源 | Unsupported chat model source |
| ragagent（020） | `modelruntime.model-not-found` | 按 ID 取模型行失败 | 模型不存在或不可用 | Model does not exist or is unavailable |
| ragagent（020） | `modelruntime.image-url-forbidden` | 图片地址被 SSRF 校验拒绝 | 图片地址不允许访问 | Image address is not allowed |
| ragagent（020） | `modelruntime.provider-config-invalid` | 保存模型时厂商结构校验失败 | 模型厂商配置不完整 | Model provider configuration is incomplete |
| ragagent（021） | `retrieval.engine-unavailable` | 向量库连接失败或探测超时 | 向量库当前不可用 | Vector store is currently unavailable |
| ragagent（021） | `retrieval.store-binding-invalid` | 知识库绑定的向量库不属于本租户 | 知识库绑定的向量库不可用 | The vector store bound to the knowledge base is not available |
| ragagent（021） | `retrieval.embedding-model-mixed` | 多知识库检索时嵌入模型不一致 | 多知识库检索要求嵌入模型一致 | Multi-knowledge-base search requires a single embedding model |
| ragagent（021） | `retrieval.engine-type-unsupported` | 配置了未启用的引擎类型 | 不支持的检索引擎 | Unsupported retrieval engine |
| ragagent（022） | `agentengine.execution-failed` | 引擎执行失败且已发错误帧 | 智能体执行失败 | Agent execution failed |
| ragagent（022） | `agentengine.skill-invalid` | 技能文档解析或校验失败 | 技能内容不合法 | Skill content is invalid |
| ragagent（022） | `agentengine.tool-not-found` | 模型调用未注册的工具 | 工具不存在 | Tool does not exist |
| ragagent（022） | `agentengine.tool-arguments-invalid` | 工具参数校验不通过 | 工具参数不合法 | Tool arguments are invalid |

错误码分配：020 从 `1001020001` 起 5 个，021 从 `1001021001` 起 4 个，022 从 `1001022001` 起 4 个。`SkillCatalog` 的五个旧异常（`DuplicateSlugException`、`SkillNameConflictException`、`PlatformSkillReadOnlyException`、`RenameWhileReferencedException`、`SkillValidationException`）由计划 06 的 `AgentCode` 承接，不在引擎里留。

## 1.14 `@IgnoreTenant` 系统通道白名单

| 位置 | 原因 | 是否进入白名单 |
|---|---|---|
| 引擎主码 | 三引擎不使用 `@IgnoreTenant`；租户来自 app 调用方，由 `TenantContextHolder` 传递 | 否 |
| `SkillCatalog` 实现（计划 06） | 平台技能层是全局表 | 计划 06 登记，本计划不涉及 |
| 引擎读 `vector_stores`（`VectorStoreCatalog` 实现，计划 03） | 按 `(id, tenant_id)` 查，不用系统通道 | 否 |

结论：本计划为零条。`docs/migration/progress.md` 登记一行“计划 02 无 `@IgnoreTenant`”。

## 1.15 跨域依赖与新做法

| 旧依赖 | 旧做法 | 新做法 |
|---|---|---|
| `platform.web.JsonMappers`、`ProviderJson`、`ToolJson`、`ContractJsonModule` | 引擎直接 import platform 静态工具 | 迁 `framework` 的 `json` 能力（计划 01 Task 3 的 `JacksonConfig` 提供 `ObjectMapper`），`ToolJson` 的递归键排序保留为 `framework.core.json.ToolJson`；`ContractJsonModule` 的契约 mixin 随契约类进各 `api` 包的 `@JsonNaming` 与显式序列化器 |
| `platform.security.SsrfGuard` | 静态白名单单例 | 计划 03 提供 Spring Bean `SsrfGuard`（与计划 06 的“Spring Bean 形式的 SsrfGuard”一致），引擎构造器注入；本计划 Task 5 的 `EngineAssemblyConfig` 用 `ObjectProvider<SsrfGuard>` 允许测试缺失 |
| `platform.security.CryptoService` | 引擎解密模型 key | 改走 `spi.ModelCredentialReader` |
| `platform.access.TenantContext` | 静态 ThreadLocal | 改走 `framework.tenant.TenantContextHolder` |
| `platform.access.TenantContextSnapshot`、`TenantScope` | 静态快照 | 迁 framework，供引擎扇出线程与事件异步派发使用 |
| `platform.event.EventBus`、`EventIds`、`EventType` | 静态事件总线 | 改走 `spi.AgentEventSink`；ID 生成用 `UUID`，事件类型枚举进 `agentengine.api` |
| `platform.tracing.langfuse.*`、`decorators.*` | 静态单例 | 改 `modelruntime.api.TraceRecorder` 加内部实现（OTel 导出），装饰器放 `modelruntime.tracing` |
| `platform.prompt.PromptSource`、`PromptComposer`、`AgentPromptPlaceholders`、`PromptConstants`、`PipelineConfig` | 静态工具与 classpath 模板 | `PromptSource`、`PromptComposer` 与模板资源进 framework；`AgentPromptPlaceholders` 与 `PromptConstants` 进 agent-engine，只有 agent 使用 |
| `platform.tool.ToolSchemas` | 静态校验 | 进 agent-engine `tool` 内部 |
| `platform.persistence.DatabaseDialects` | H2 与 PG 方言分支 | 删除，测试统一 Testcontainers PostgreSQL（spec 5.11） |
| `platform.config.RetrievalDriverProperties`、`AppEnvLookup`、`StorageRuntimeEnv` | 静态环境读取 | 引擎配置走 `@ConfigurationProperties`（`ModelRuntimeProperties`、`RetrievalProperties`），env 回落点集中在各模块的 `EngineProperties` |
| `contract.session.MessageArtifact`（仅 javadoc） | 注释引用 | 改文字说明 |

## 1.16 stub 场景清单

`scripts/stub-llm-server.py`（默认端口 8181）。现有场景已在冻结点实现，标注“新增”的由本计划 Task 9 扩展，扩展开关是请求末条 user 消息里的 `<<SCENARIO:xxx>>` 标记，与现有写法一致。

| 场景 | 端点 | 状态 | 行为 | 对应能力（5.10.1 编号） |
|---|---|---|---|---|
| `chat` | `POST /v1/chat/completions` 流式与非流式 | 现有 | 两分片加 done，`usage` 固定 12/9/21 | 16、36、39 |
| `long` | 同上 | 现有 | 三分片，用于分片重组与扣留缓冲 | 15、36 |
| `think` | 同上 | 现有 | `think` 块加正文 | 15、23、25 |
| `echo` | 同上 | 现有 | 原样回显末条 user | 25、26 |
| `graph` | 非流式 | 现有 | 带围栏的抽取 JSON | 不在 41 项内，保留做实体抽取回归 |
| `early-error` | 流式 | 现有 | 200，首帧 error 对象后关流 | 5、21 |
| `early-close` | 流式 | 现有 | 200，零帧关流 | 16、24 |
| `empty` | 流式 | 新增 | 首帧 `finish_reason=stop` 且 `content` 为空串，第二帧 done 带 usage | 16 |
| `repeat` | 流式 | 新增 | 两次请求返回完全相同的正文，供连续轮次比对 | 17 |
| `slow` | 流式 | 新增 | 首帧正文后暂停 `STUB_SLOW_GAP_SEC`（默认 90）再发 done；暂停期间不写 socket | 23、24 |
| `slowtool` | 流式 | 新增 | 返回一个 `tool_calls` 分片，`arguments` 每 2 秒一片共 3 片，再发 done | 10、11、13、22、23、31 |
| `badjson` | 流式 | 新增 | `tool_calls.arguments` 为尾逗号加非法转义的坏 JSON | 11、12 |
| `truncated` | 流式 | 新增 | `finish_reason=length` 且 `arguments` 从中间截断 | 12、18 |

`scripts/stub-ollama-server.py`（默认端口 8182）：HEAD `/` 返回 200，GET `/api/version` 返回 `w5b-stub`，GET `/api/tags` 返回 `stub-model:latest`，POST `/api/pull` 返回四行 NDJSON 进度。用途是本计划 Task 2 的 Ollama 管理面用例（拉取进度、版本、模型列表），不扩展。

## 1.17 不适用的表

引擎不提供 HTTP 接口，因此“前端 API 层切换表”与“页面到 e2e 用例映射表”本计划不适用。唯一可见变化是向量库下拉从 9 项变 3 项，归计划 03 的 vectorstore 前端任务，本计划只在 Task 6 的交接说明里写清三项取值。

## 1.18 多实现点与窄接口

路线图 3.11 要求只在真实多实现处建“窄接口加 Spring 登记表”。逐处结论：

| 位置 | 实现数 | 结论 |
|---|---|---|
| 检索引擎（postgres、elasticsearch、milvus） | 3 | **建接口。** `Engine` 两方法，三个实现注册为 Bean，`EngineRegistry` 注入 `Map<VectorEngineType, Engine>`。真实变化点：后续按 spi 补回引擎只加一个实现类 |
| 聊天模型厂商（`llm.provider` 目录 26 家） | 26 | **建接口，保留 `Provider`，登记表用枚举键。** 旧实现已是接口加静态注册表，新实现把注册表改 Spring Bean，注入 `Map<ProviderName, Provider>`。`Provider` 只保留 `info()` 与 `validateConfig(Config)` 两方法 |
| 嵌入厂商（`embedding.provider` 9 家） | 9 | **建接口。** `EmbeddingProvider` 一个方法 `float[] embed(EmbeddingRequest)`，实现按 `ProviderName` 登记 |
| 重排厂商（`rerank.provider` 7 家） | 7 | **建接口。** `RerankProvider` 一个方法 `List<RankResult> rerank(RerankRequest)` |
| 语音转写（DashScope、OpenAI 兼容） | 2 | **不建接口。** `AsrTranscriberRouter` 用 if 分流，只有两分支（3.11 设计第 2 条） |
| 聊天适配（Ollama 本地、Remote 远程、Anthropic） | 3 套请求形状 | **建接口。** `ChatAdapter` 两方法 `send` 与 `stream`，三个实现；旧的 `ProviderAdapters` 登记表改为 Bean 注入 |
| 文档解析器、存储后端、渠道适配器 | 不在本计划 | 不适用 |
| 思考策略（`ThinkingStrategies`） | 4 种 | **不建接口。** 判定与参数调整是两个纯函数，用 `Switch` 表加函数式映射，无状态、无第二个调用方 |

接口命名不用 `Port`、`Adapter` 之外的后缀（3.11 命名条款）；对接外部系统的类才叫 `Adapter`，本计划沿用 `RemoteChatAdapter` 一类命名时逐个核对，能并入实现类的并入。

## 1.19 停用引擎重启指南

本节只列动作清单，不写做法。启用某个停用引擎时另写一份计划，不复用本计划的免豁免。

1. 在新计划里给该引擎写 `Engine` 实现，登记进 `EngineRegistry` 的实现 Bean 集合。
2. 把引擎类型加回 `VectorEngineType` 的常量、`ALL` 与 `CONFIGURABLE`，同步 `EngineTypes` 别名与 `EffectiveEngines` 映射。
3. 写 `VectorStoreEngineDescriptor`（控制面必填字段、SSRF 地址字段、索引配置缺省）并接进 `VectorStoreCatalogApi` 与探测分支。
4. 补隔离证明：跨租户拒绝用例、连接失败到 `RetrievalCode.ENGINE_UNAVAILABLE` 的映射、SSRF 拒绝用例。
5. 补测试：引擎适配单测、Testcontainers 或真实服务 IT、CI 所需的容器服务。
6. 按 3.11 做内部整理，把该包的超限类与方法拆到门内。
7. 删掉 `checkstyle-suppressions-engines.xml` 里该包的 `suppress` 条目，跑全量 `verify`。

# 2. 任务

任务 0 到 5 是阶段一（改包名搬入，行为不变），任务 6 到 9 是阶段二（停用引擎与内部整理），任务 10 收口。每个任务一次提交。Task 7 与 Task 9 依赖 Task 2、Task 4 与共同的 api 与 spi 骨架，可并行；Task 8 依赖 Task 6 的引擎清单，排在它之后。提交顺序按编号。

### Task 0: 前置核对、盘点差异与引擎豁免文件

**Files:**
- Modify: `backend/config/checkstyle-suppressions-engines.xml`
- Test: 无（本任务只改配置，用 Task 0 Step 3 的临时探针验证）

**Interfaces:**
- Consumes: 计划 01 的聚合、三个引擎骨架、`backend/config/checkstyle.xml`
- Produces: 按包豁免的抑制文件，内容如下（`files` 用路径片段正则，执行机路径分隔符用 `[\\/]` 同时兼容）：

```xml
<?xml version="1.0"?>
<!DOCTYPE suppressions PUBLIC
    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"
    "https://checkstyle.org/dtds/suppressions_1_2.dtd">
<!--
  计划 02 专用：三个引擎改包名搬入期间的尺寸豁免。
  每条对应一个旧子包。参与整理的包在其整理任务里同一提交删除对应 suppress；
  6 个停用引擎（doris/opensearch/qdrant/sqlite/tencentvectordb/weaviate）的那条长期保留。
  整理完成后本文件只剩停用引擎 1 条，由 Task 10 验证。
  不豁免命名、注解 SQL、JdbcTemplate 规则，那三条由 ArchUnit 在任何阶段都生效。
-->
<suppressions>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]model-runtime[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]modelruntime[\\/]embedding([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]model-runtime[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]modelruntime[\\/]llm([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]model-runtime[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]modelruntime[\\/]context([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]model-runtime[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]modelruntime[\\/]assembly([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]model-runtime[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]modelruntime[\\/]rerank([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]retrieval-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]retrieval[\\/](Hybrid.*|artifact|config|graph|support|telemetry)([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]retrieval-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]retrieval[\\/]engine[\\/](CompositeRetrieveEngine|EffectiveEngines|EngineAwareNormalizer|EngineFactory|EngineRegistry|EngineTypes|IndexEngineGateway|KeywordsVectorHybridRetrieveEngineService|PgVectorEngineRepository|PgVectorRetrieveRepository|RetrieveEngineFactories|RetrieveEngineRegistry|RetrieveEngineRepository|RetrieveEngineService|RetrieverEngineParams|ScoreNormalizer|StoreEngineFactory|VectorStoreEngineCatalog|VectorStoreRepoOwnership|VectorStoreService)\.java"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]retrieval-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]retrieval[\\/]engine[\\/](elasticsearch|milvus)([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]retrieval-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]retrieval[\\/]engine[\\/](doris|opensearch|qdrant|sqlite|tencentvectordb|weaviate)([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]agent-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]agentengine[\\/]react([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]agent-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]agentengine[\\/]context([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]agent-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]agentengine[\\/]compaction([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]agent-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]agentengine[\\/]prompt([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]agent-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]agentengine[\\/]skill([\\/]|$).*"/>
	<suppress checks="FileLength|MethodLength|ParameterNumber|CyclomaticComplexity|NestedIfDepth|NestedForDepth|NestedTryDepth" files="[\\/]agent-engine[\\/]src[\\/]main[\\/]java[\\/]com[\\/]ragagent[\\/]agentengine[\\/]tool([\\/]|$).*"/>
</suppressions>
```

- [ ] **Step 1: 核对前置**

Run:
```bash
git branch --show-current
test -f docs/migration/frozen-bugs.md && grep -nE "engine|retrieval|model|agent" docs/migration/frozen-bugs.md
test -f backend/config/checkstyle.xml && test -f backend/config/checkstyle-suppressions-engines.xml && echo "config OK"
ls backend/model-runtime/src/main/java/com/ragagent/modelruntime/package-info.java backend/retrieval-engine/src/main/java/com/ragagent/retrieval/package-info.java backend/agent-engine/src/main/java/com/ragagent/agentengine/package-info.java
```
Expected: 分支 `rebuild`；`config OK`；三个 `package-info.java` 存在。`frozen-bugs.md` 里属于引擎的行逐条抄进 Task 7 到 Task 9 的对应步骤；没有则本计划不带入。

- [ ] **Step 2: 抽查盘点关键数字**

Run:
```bash
find model-runtime/src/main/java -name "*.java" | wc -l        # 158
find retrieval-engine/src/main/java -name "*.java" | wc -l     # 95
find agent-engine/src/main/java -name "*.java" | wc -l         # 72
find model-runtime/src/test/java -name "*.java" | wc -l        # 29
find retrieval-engine/src/test/java -name "*.java" | wc -l     # 37
find agent-engine/src/test/java -name "*.java" | wc -l         # 37
grep -rlc "Ops.java" /dev/null; find model-runtime retrieval-engine agent-engine -name "*Ops.java" | wc -l   # 27
grep -rn "throw new BizException\|throw BizException\." model-runtime/src/main/java | wc -l   # 71
```
Expected: 与注释中的数字一致。不一致时以源码为准，改本计划 1.1、1.12，并在 `deviations.md` 登记一行。

- [ ] **Step 3: 写豁免文件**

按上面 XML 内容整体写入 `backend/config/checkstyle-suppressions-engines.xml`。写入后临时在 `backend/model-runtime/src/main/java/com/ragagent/modelruntime/llm/Probe.java` 放一个 45 行方法，确认被豁免：

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml validate`。Expected: BUILD SUCCESS（豁免生效）。删掉 `Probe.java` 后重跑一次仍 SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add backend/config/checkstyle-suppressions-engines.xml
git commit -m "build(engines): 三个引擎迁入期的按包尺寸豁免"
```

### Task 1: contract 归位、api 与 spi 骨架、错误码与框架补件

**Files:**
- Create: `backend/model-runtime/src/main/java/com/ragagent/modelruntime/api/{ModelRuntime,LlmChatClient,ChatStream,Embedder,Reranker,VlmPredictor,AsrTranscriber,ChatConfig,ChatMessage,ChatOptions,ChatResponse,StreamResponse,ToolCall,FunctionDef,FunctionCall,ChatTool,MessageContentPart,RankResult,ModelParameters,ModelFacts,ModelRow,VlmConfig,GraphNode,GraphRelation,GraphData,EntityExtractionResult,TraceRecorder,SpanHandle,ModelRuntimeCode}.java`（`ResponseType`、`TokenUsage`、`PromptCacheStatus` 不在本包：它们被 `framework.stream` 的 SSE 帧契约引用，而 framework 不依赖本模块，已由计划 01 Task 11d 迁入 `com.ragagent.framework.stream`，本模块改为 import）
- Create: `backend/model-runtime/src/main/java/com/ragagent/modelruntime/spi/{ModelCatalogReader,ModelFactsReader,ModelCredentialReader}.java`
- Create: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/api/{HybridSearchApi,IndexingApi,VectorStoreCatalogApi,VectorEngineType,SearchRequest,SearchResult,IndexDocument,IndexRow,ConnectionConfig,IndexConfig,VectorStoreView,VectorStoreEngineRules,CopyIndicesCommand,MoveIndicesCommand,RetrievalCode}.java`
- Create: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/spi/{VectorStoreCatalog,KnowledgeSearchSource,KnowledgeBaseFacts,KnowledgeDocumentFacts,ChunkFacts,TenantRetrievalSettings,RetrievalDriverRef,RetrievalTuning}.java`
- Create: `backend/agent-engine/src/main/java/com/ragagent/agentengine/api/{AgentEngineFactory,AgentEngineHandle,AgentEngineRequest,AgentTurn,AgentRunSpec,AgentStep,AgentToolCall,AgentBudgets,KnowledgeBaseInfo,SelectedDocumentInfo,RecentDocInfo,ThinkBlocks,ToolCallTarget,ImageDescriberFunc,AgentTool,ToolRequest,ToolExecContext,ToolResult,ToolCancellation,ToolOutput,ToolDefinitions,AgentEvent,AgentEventType,AgentEventPayload,AgentActionData,AgentCompleteData,AgentFinalAnswerData,AgentThoughtData,AgentToolCallData,AgentToolResultData,ContextCompactedData,ErrorData,UserMessageInjectedData,AgentPlanData,SkillFrontmatter,PromptLanguage,AgentEngineCode}.java`
- Create: `backend/agent-engine/src/main/java/com/ragagent/agentengine/spi/{AgentEventSink,SteerSink,SteerMessage,SkillCatalog,SkillRecord,McpToolProvider,McpToolSession,McpSelection}.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/tenant/{TenantSnapshot,TenantScope}.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/core/json/ToolJson.java`
- Create: `backend/framework/src/main/java/com/ragagent/framework/prompt/{PromptSource,PromptComposer}.java`
- Create: `backend/framework/src/main/resources/prompts/`（从 `platform/src/main/resources/prompts/` 整目录复制 11 份 yaml）
- Create: `backend/app/src/main/java/com/ragagent/config/EngineCodeConfig.java`
- Create: `backend/app/src/main/resources/i18n/app-messages{,_zh_CN,_zh_HK,_en_US,_es_ES,_pt_BR,_ru_RU,_th_TH}.properties`
- Test: `backend/app/src/test/java/com/ragagent/engine/EngineCodeMessagesTest.java`

**Interfaces:**
- Consumes: 计划 01 的 `Code`、`CodeCache`、`BusinessRuntimeException`、`I18nUtil`
- Produces:
  - `TenantSnapshot`：`static TenantSnapshot capture()`、`void replay()`、`boolean hasIdentity()`；`TenantScope`：`static <T> T supply(TenantSnapshot s, Supplier<T> body)`、`static void run(TenantSnapshot s, Runnable body)`。语义照旧：进入时 replay，退出时清空并恢复原值
  - `ToolJson`：`static String write(JsonNode node)`（递归键排序后紧凑输出）、`static String compactJson(Object)`、`static String prettyJson(Object)`、`static String quoted(String)`、`static String expectedObjectMessage(JsonNode)`
  - `PromptSource`：`List<String> files()`、`InputStream open(String)`、`String read(String)`；`PromptComposer`：`static String joinSections(List<String>)`
  - `EngineCodeConfig`：`@Configuration`，`@PostConstruct` 里 `CodeCache.register(ModelRuntimeCode.values())`、`CodeCache.register(RetrievalCode.values())`、`CodeCache.register(AgentEngineCode.values())`
  - 错误码枚举与七语文案：1.13 的 13 个码，`ModelRuntimeCode` 5 个、`RetrievalCode` 4 个、`AgentEngineCode` 4 个，每个枚举值实现 `String getCode()`
  - 接口签名见“对外承诺”五张表，直接按表落地，不另加方法

- [ ] **Step 1: 写失败测试 `EngineCodeMessagesTest`**

断言：遍历三个枚举全部取值，七种语言加默认文件各取一次 `code_<码>`，断言非空且不等于 key；再断言 `CodeCache.find(code)` 命中。

Run: `MVN test -Dtest=EngineCodeMessagesTest`
Expected: FAIL，`ModelRuntimeCode` 不存在。

- [ ] **Step 2: 建 api 与 spi 骨架**

规则：record 优先；旧类有可变字段的（`ChatMessage`、`ChatOptions`、`ToolResult`、`StreamResponse`）保留 getter/setter 与字段名，线格式键名由 `@JsonProperty` 或显式序列化器固定，不改字段名（前端、历史回放、提示词缓存都依赖）。`ToolDefinitions` 的常量文本一字不改。`RetrievalCode` 与 `AgentEngineCode` 的 `getCode()` 返回 1.13 的 10 位码。

- [ ] **Step 3: 补 framework 三件**

`TenantSnapshot` 与 `TenantScope` 从 `platform.access` 迁入，字段集合与旧版一致（tenantId、principal、role、systemAdmin、userId、canAccessAllTenants、embedVisitorId、requestId）。`ToolJson` 从 `platform.web` 迁入，逻辑不变。`PromptSource` 与 `PromptComposer` 从 `platform.prompt` 迁入 framework，prompts 资源整目录复制，`PromptSource.files()` 的 11 份文件与顺序不变。`AgentPromptPlaceholders` 与 `PromptConstants` 不在 framework，Task 4 随 agent-engine 迁入 `agentengine.prompt`。

- [ ] **Step 4: 文案入库**

八个文件写全 13 个 key。zh-CN 与 en-US 用 1.13 的表，其余五种语言机器翻译出初稿并在文件头加 `# 待人工校对`。

- [ ] **Step 5: 跑绿并提交**

Run: `MVN test -Dtest=EngineCodeMessagesTest`。Expected: PASS，13 个码 × 8 文件全命中。

```bash
git add backend/model-runtime/src/main/java/com/ragagent/modelruntime/api backend/model-runtime/src/main/java/com/ragagent/modelruntime/spi
git add backend/retrieval-engine/src/main/java/com/ragagent/retrieval/api backend/retrieval-engine/src/main/java/com/ragagent/retrieval/spi
git add backend/agent-engine/src/main/java/com/ragagent/agentengine/api backend/agent-engine/src/main/java/com/ragagent/agentengine/spi
git add backend/framework/src/main/java/com/ragagent/framework/tenant/TenantSnapshot.java \
  backend/framework/src/main/java/com/ragagent/framework/tenant/TenantScope.java \
  backend/framework/src/main/java/com/ragagent/framework/core/json/ToolJson.java \
  backend/framework/src/main/java/com/ragagent/framework/prompt backend/framework/src/main/resources/prompts
git add backend/app/src/main/java/com/ragagent/config/EngineCodeConfig.java \
  backend/app/src/main/resources/i18n backend/app/src/test/java/com/ragagent/engine/EngineCodeMessagesTest.java
git commit -m "feat(engines): contract 归位为 api 与 spi，补错误码七语文案"
```

### Task 2: model-runtime 改包名搬入

**Files:**
- Create: `backend/model-runtime/src/main/java/com/ragagent/modelruntime/{embedding,llm,context,provider,assembly,rerank,tracing}/**`（从旧模块整体搬迁）
- Modify: `backend/model-runtime/pom.xml`（加 jackson、spring-context、spring-web、spring-jdbc、spring-data-redis、jtokkit、`opentelemetry-sdk`、`opentelemetry-exporter-otlp`、`opentelemetry-exporter-sender-okhttp`、`testcontainers` 的 `generic` 与 `junit-jupiter`）
- Delete（旧目录不动，仅不再引用）: 无
- Test: `backend/model-runtime/src/test/java/com/ragagent/modelruntime/**`（1.9 已处置的 29 个）

**Interfaces:**
- Consumes: Task 1 的 api、spi、`ToolJson`
- Produces: 包映射表

| 旧包 | 新包 |
|---|---|
| `com.ragagent.embedding`、`com.ragagent.embedding.provider` | `com.ragagent.modelruntime.embedding`、`...embedding.provider` |
| `com.ragagent.llm` 全部子包 | `com.ragagent.modelruntime.llm.*` |
| `com.ragagent.modelcontext` | `com.ragagent.modelruntime.context` |
| `com.ragagent.modelruntime.adapter.provider.asr` | `com.ragagent.modelruntime.provider.asr` |
| `com.ragagent.modelruntime.adapter.provider.chat` 全部子包 | `com.ragagent.modelruntime.provider.chat.*` |
| `com.ragagent.modelruntime.adapter.provider.vlm` | `com.ragagent.modelruntime.provider.vlm` |
| `com.ragagent.modelruntime.application` | `com.ragagent.modelruntime.assembly` |
| `com.ragagent.rerank`、`com.ragagent.rerank.provider` | `com.ragagent.modelruntime.rerank`、`...rerank.provider` |
| `platform.tracing.langfuse`、`platform.tracing.decorators` | `com.ragagent.modelruntime.tracing`（`OtelExporter`、`TraceRecorderImpl`、四个装饰器） |

- [ ] **Step 1: 写失败测试 `ChatStreamTerminalTest`（spike 结论 1）**

`emitsToolCallDoneThenUsageDone`：用 `stub-llm-server.py` 的 `slowtool` 与 `chat` 两个场景各起一次流，断言消费者按顺序收到“带 ToolCalls 的终态帧”再收到“带 Usage 的终态帧”，`take()` 在第二帧之后返回空或流关闭。断言 `close()` 后 `take()` 立刻返回不再阻塞。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl model-runtime -am test -Dtest=ChatStreamTerminalTest`
Expected: FAIL，类不存在。

- [ ] **Step 2: 整体搬迁并改 import**

搬迁用 `git mv` 保留历史：先把旧文件按上表 `git mv` 到新路径，再用脚本批量替换 `com.ragagent.embedding`→`com.ragagent.modelruntime.embedding`、`com.ragagent.llm`→`com.ragagent.modelruntime.llm`、`com.ragagent.modelcontext`→`com.ragagent.modelruntime.context`、`com.ragagent.rerank`→`com.ragagent.modelruntime.rerank`、`com.ragagent.modelruntime.adapter`→`com.ragagent.modelruntime.provider`、`com.ragagent.modelruntime.application`→`com.ragagent.modelruntime.assembly`，`package` 行同步。contract import 改指 Task 1 的 api 与 spi。`BizException` 改 `BusinessRuntimeException` 加 `ModelRuntimeCode`，`BizException.wireText` 改 `BusinessRuntimeException.wireText`（framework 已有同名静态方法，签名 `String wireText(Throwable)`）。`TenantContext` 改 `TenantContextHolder`。`JsonMappers.lenient()` 改注入的 `ObjectMapper`，静态方法无法注入的改为构造器传入。

脚本放系统临时目录，执行后删除，不进仓库。

- [ ] **Step 3: 实现 `TraceRecorder` 与 Langfuse 导出**

`TraceRecorderImpl` 用 `opentelemetry-sdk` 的 `Tracer`，resource 属性 `service.name=ragagent`，导出端点为 `{LANGFUSE_HOST}/api/public/otel/v1/traces`，头 `Authorization: Basic base64(public:secret)` 与 `x-langfuse-ingestion-version: 4`，发送器用 okhttp（JDK `HttpClient` 对明文会先发 h2c 升级请求，自建 Langfuse 会断连）。全部属性 `AttributeKey.stringKey`。无 keys 时返回 `OpenTelemetry.noop()`。四个装饰器（`LangfuseChatClient`、`LangfuseEmbedder`、`LangfuseReranker`、`LangfuseVlm`）只把 span 创建换成 `TraceRecorder`，对外行为不变。

- [ ] **Step 4: 移植测试**

按 1.10 的处置逐类处理。`EmbeddedRedis` 依赖删掉，Redis 相关用例改用 Testcontainers `GenericContainer<>("redis:7.0-alpine")`，与计划 01 的写法一致。`ModelContextGolden`、`ModelContextRecordingTest`、`JsonBridge` 删除。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl model-runtime -am test`
Expected: PASS。`ChatStreamTerminalTest` 通过。若 `LocalLimiter` 或 `RedisLimiter` 用例因容器未起而失败，先确认 Docker 可用。

- [ ] **Step 5: 提交**

```bash
git add backend/model-runtime
git commit -m "refactor(model-runtime): 改包名搬入 backend 并接入 OpenTelemetry 导出"
```

### Task 3: retrieval-engine 改包名搬入（9 引擎先原样保留）

**Files:**
- Create: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/**`（旧模块整体搬迁）
- Modify: `backend/retrieval-engine/pom.xml`（加 jackson、spring-context、spring-jdbc、spring-boot、neo4j-java-driver、sqlite-jdbc、HikariCP、mysql-connector-j、postgresql、testcontainers `postgresql` 与 `generic` 与 `junit-jupiter`）
- Test: `backend/retrieval-engine/src/test/java/com/ragagent/retrieval/**`（1.9 已处置的 37 个）

**Interfaces:**
- Consumes: Task 1 的 retrieval api 与 spi、Task 2 的 `ModelRuntime` 与 `EmbeddingPort`
- Produces: 包映射：旧包根已经是 `com.ragagent.retrieval`，本任务不改包根；只把 `retrieval.engine.<engine>` 的 9 个适配子包原样保留，`HybridSearch*`、`artifact`、`graph`、`telemetry`、`support`、`config` 原样保留；新增 `retrieval.service` 放 api 实现类的未来归宿（本任务不建，Task 8 建）。`RetrievalObs` 改名为 `RetrievalObservation` 并收口 `Map<String,Object>` 的 7 处观测字段

- [ ] **Step 1: 写失败测试 `PgVectorSearchTest`**

在 Testcontainers PostgreSQL 17（自建镜像）上建 `embeddings` 表与 `pgvector` 扩展，断言：关键词检索的 SQL 里不含 `paradedb`，向量检索返回 `distance` 阈值内的命中，`SET LOCAL hnsw.ef_search` 在事务内生效，GUC 不可用时降级重试仍返回结果。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl retrieval-engine -am test -Dtest=PgVectorSearchTest`
Expected: FAIL，类不存在。

- [ ] **Step 2: 搬迁并改 import**

`git mv` 整个 `retrieval-engine/src/main/java/com/ragagent/retrieval` 到新模块同路径。contract import 改指 Task 1 的 api 与 spi：`HybridSearchPort`→`HybridSearchApi`、`IndexEngineResolver` 与 `IndexEngine`→`IndexingApi`、`VectorIndexStore`→`IndexingApi`、`VectorStoreEngineCatalogPort`→`VectorStoreCatalogApi`、三个 knowledge Gateway→`KnowledgeSearchSource`、`TenantConfigLookup`→`TenantRetrievalSettings`、`ModelFactsPort`→`spi.ModelFactsReader`、`EmbeddingPort`→`modelruntime.api.EmbeddingPort`。`BizException` 改 `BusinessRuntimeException` 加 `RetrievalCode`；`ErrorCode.VECTOR_STORE_*` 改 `RetrievalCode`。`LangfuseManager` 与 `Span` 改 `TraceRecorder`。`DatabaseDialects` 删除，`PgVectorEngineRepository` 的 `postgres ? ... : MERGE ...` 分支只留 postgres 一支。

- [ ] **Step 3: 实现 `HybridSearchApi` 与 `IndexingApi` 的门面**

旧 `HybridSearchService` 先原样实现 `HybridSearchApi`，方法体只是把旧签名映射到新 record（`SearchParams`→`SearchRequest`），行为不变。旧 `IndexEngineGateway` 先原样实现 `IndexingApi`。这一步不拆类，拆类在 Task 8。

- [ ] **Step 4: 移植测试并跑绿**

按 1.10 处置。`WeaviateDriverLocalIT` 随停用包一起迁入，类上标 `@Disabled("Weaviate 引擎停用")`，Task 6 不再执行它；探针与差分用例同样不进常规套件。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl retrieval-engine -am test`
Expected: PASS，`PgVectorSearchTest` 通过。

- [ ] **Step 5: 提交**

```bash
git add backend/retrieval-engine
git commit -m "refactor(retrieval-engine): 改包名搬入 backend，实现 HybridSearchApi 与 IndexingApi"
```

### Task 4: agent-engine 改包名搬入

**Files:**
- Create: `backend/agent-engine/src/main/java/com/ragagent/agentengine/{react,context,compaction,prompt,skill,tool}/**`
- Modify: `backend/agent-engine/pom.xml`（加 jackson、spring-context、spring-jdbc、jtokkit；不引入 mybatis-plus，技能表归计划 06）
- Test: `backend/agent-engine/src/test/java/com/ragagent/agentengine/**` 与 `src/testFixtures/java/**`

**Interfaces:**
- Consumes: Task 1 的 agent api 与 spi、Task 2 的 `LlmChatClient`
- Produces: 包映射

| 旧包 | 新包 |
|---|---|
| `com.ragagent.agent` 根（`AgentEngine`、`ActPhase`、`ThinkPhase`、`ObservePhase`、`FinalizePhase`、`ReActIteration`、`ToolBatchExecutor`、`SteerIntake`、`AgentPrompts`、`ConversationAssembler`、`AgentConfig`、`AgentConsts`、`AgentEngineException`、`ContextBudget`、`ContextDebugEmitter`、`ToolImages`、`SkillMetadata`、`AgentEngineFactoryImpl`、`AgentEngineHandleAdapter`） | `com.ragagent.agentengine.react` |
| `com.ragagent.agent.domain`（`AgentState`） | `com.ragagent.agentengine.react` |
| `com.ragagent.agent.budget` | `com.ragagent.agentengine.context` |
| `com.ragagent.agent.compaction` | `com.ragagent.agentengine.compaction` |
| `com.ragagent.agent.prompt` | `com.ragagent.agentengine.prompt` |
| `com.ragagent.agent.skills`（`Skill`、`SkillFrontmatter`、`SkillSource`、`DbSkillSource`、`Manager`） | `com.ragagent.agentengine.skill`；`Manager` 改名 `SkillDisclosureLevels`（三级披露，去掉 `Manager` 后缀）；`SkillCatalogService`、`SkillEntity`、`mapper/` 删除（归计划 06） |
| `com.ragagent.agent.tool`、`com.ragagent.agent.tool.runtime` | `com.ragagent.agentengine.tool`、`...tool.runtime` |

- [ ] **Step 1: 写失败测试 `AgentEngineNullContextTest`**

从旧测试移植：`llmContext` 为空列表时按空历史跑完，事件序列 `AGENT_START`、`AGENT_COMPLETE` 各一次；断言终态载荷的 `outcome` 为空，不是 `failed`。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl agent-engine -am test -Dtest=AgentEngineNullContextTest`
Expected: FAIL，类不存在。

- [ ] **Step 2: 搬迁并改 import**

事件：旧 `EventBusInterface` 改 `spi.AgentEventSink`；`Event` 与 `payload` 改 `api.AgentEvent` 与 `api` 包的载荷类；`platform.event.EventType` 改 `api.AgentEventType`（枚举名保持 `EVENT_ERROR`、`EVENT_AGENT_COMPLETE` 等旧字面量，前端按这些字段分支）。`BizException.wireText` 改 `BusinessRuntimeException.wireText`。`SkillCatalogPort` 改 `spi.SkillCatalog`，`DbSkillSource` 改收 `List<SkillRecord>`。`McpToolRegistrationPort` 与 `McpToolingGateway` 改 `spi.McpToolProvider` 与 `McpToolSession`。`JsonMappers`、`ToolJson`、`PromptSource`、`PromptComposer`、`AgentPromptPlaceholders`（后者进 `agentengine.prompt`）。`TenantContext` 改 `TenantContextHolder`，`TenantContextSnapshot` 改 `TenantSnapshot`。

`AgentEngineHandleAdapter` 删除，`AgentEngineFactoryImpl` 直接实现 `AgentEngineHandle` 需要的装配与返回。`SkillCatalogServiceTest`、`DbSkillSourceTest` 随类删除，登记到计划 06（已在 1.9 说明）。

- [ ] **Step 3: 跑绿**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl agent-engine -am test`
Expected: PASS。`AgentEngineFailureTerminalTest` 必须绿：错误帧在终态帧之前，终态载荷 `outcome=failed`。

- [ ] **Step 4: 提交**

```bash
git add backend/agent-engine
git commit -m "refactor(agent-engine): 改包名搬入 backend，事件与工具改走 api 与 spi"
```

### Task 5: EngineAssemblyConfig 装配与暴露边界验收

**Files:**
- Modify: `backend/app/src/main/java/com/ragagent/config/EngineAssemblyConfig.java`
- Test: `backend/app/src/test/java/com/ragagent/engine/EngineAssemblyBoundaryTest.java`
- Test: `backend/app/src/test/java/com/ragagent/engine/TenantContextMissingTest.java`

**Interfaces:**
- Consumes: Task 2 到 Task 4 的 api 与 spi、计划 01 的 `ArchitectureRulesTest`
- Produces:
  - `EngineAssemblyConfig` 的 Bean 清单：`EngineRegistry`、`EngineFactory`、`HybridSearchApi`、`IndexingApi`、`VectorStoreCatalogApi`、`ModelRuntime`、`AgentEngineFactory`、`TraceRecorder`。spi 的三个 model 实现、三个 retrieval 实现、三个 agent 实现全部用 `ObjectProvider` 取，缺失时抛 `BusinessRuntimeException` 并在启动日志里点名缺失接口
  - `TenantContextMissingTest`：`mapsRowLevelSecurityErrorToFrameworkCode`。在 Testcontainers 上建一张开 RLS 的表，事务内 `set_config('app.tenant_id','',true)` 后查询，断言抛出 `TenantContextMissingException` 且 `getCode() == FrameworkCode.TENANT_CONTEXT_MISSING`，异常消息里不含 `invalid input syntax`（spike 结论 2 落地）

- [ ] **Step 1: 写失败测试 `EngineAssemblyBoundaryTest`**

断言：`enginesAreAssembledOnlyInAppConfig` 在 `EngineAssemblyConfig` 之外 `new` 引擎 spi 对象时变红；`enginesExposeOnlyApiAndSpi` 对三模块全量生效（Task 2 到 Task 4 已搬入，不再空转）。用计划 01 Task 11 Step 5 的反向 fixture 手法确认两条都会红，然后删除 fixture。

Run: `MVN test -Dtest='EngineAssemblyBoundaryTest,TenantContextMissingTest'`
Expected: FAIL，`EngineAssemblyConfig` 未提供 Bean。

- [ ] **Step 2: 写装配**

引擎之间只通过 api 交互：`retrieval-engine` 注入 `LlmChatClient` 与 `EmbeddingPort` 由 `ModelRuntime` 给出；`agent-engine` 注入 `LlmChatClient`。`SsrfGuard` 用 `ObjectProvider`，缺失时构造一个 `SsrfGuard` 实例（计划 03 会把它注册为 Bean，本计划不阻塞）。`ObjectMapper` 由 framework 的 `JacksonConfig` 提供。

- [ ] **Step 3: 跑绿并验证边界**

Run: `MVN test -Dtest='EngineAssemblyBoundaryTest,TenantContextMissingTest,ArchitectureRulesTest,ModuleBoundaryTest'`
Expected: PASS。

- [ ] **Step 4: 提交**

```bash
git add backend/app/src/main/java/com/ragagent/config/EngineAssemblyConfig.java \
  backend/app/src/test/java/com/ragagent/engine
git commit -m "feat(app): 引擎装配收口到 EngineAssemblyConfig，补租户缺失与暴露边界用例"
```

### Task 6: 停用 6 种检索引擎，PGroonga 替换 ParadeDB

**Files:**
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/api/VectorEngineType.java`（3 值）
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/{EngineFactory,EngineTypes,EffectiveEngines,VectorStoreEngineCatalog,CompositeRetrieveEngine,KeywordsVectorHybridRetrieveEngineService}.java`（Task 3 已迁入的 `EngineTypes` 只删 import 与已删常量，不整体删类）
- Create: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/{Engine,IndexBatch}.java`、`.../engine/{pgvector/PgVectorEngine,elasticsearch/ElasticsearchEngine,milvus/MilvusEngine}.java`
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/service/IndexingService.java`（旧 `IndexEngineGateway` 改名，只保留两分支）
- Modify: `backend/app/src/main/java/com/ragagent/config/EngineAssemblyConfig.java`（`EngineRegistry` 改注入三引擎 `Map`，不装配停用引擎）
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/elasticsearch/{ElasticsearchV7*,ElasticsearchV8*}.java`（删 v7 两套，v8 改名 `ElasticsearchSearch` 与 `ElasticsearchIndexWriter`）
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/pgvector/PgVectorSearch.java`（关键词改 PGroonga）
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/VectorStoreService.java`（删 `DatabaseDialects` 判断与 `MERGE` 分支，只留 PostgreSQL 一支）
- Modify: `backend/config/checkstyle-suppressions-engines.xml`（保留 6 引擎那条豁免）
- Modify: `.github/workflows/ci.yml`（`integration` job 去掉 Weaviate 服务、就绪等待与 `RAGAGENT_WEAVIATE_IT`）
- Create: `docs/migration/handoff-engines-spi.md`（向量库下拉小节）
- Delete: 停用引擎的测试类，见 1.10
- Test: `backend/retrieval-engine/src/test/java/com/ragagent/retrieval/engine/`（`PgVectorSearchTest` 加 PGroonga 用例、`ElasticsearchEngineTest`、`MilvusEngineTest`、`MilvusEngineIT`、`ElasticsearchEngineIT`）

**Interfaces:**
- Consumes: Task 3 的 api 与 spi
- Produces:
  - `VectorEngineType`：`POSTGRES = "postgres"`、`ELASTICSEARCH = "elasticsearch"`、`MILVUS = "milvus"`；`ALL` 与 `CONFIGURABLE` 均为这三个值（postgres 也在 CONFIGURABLE 里，因为 pgvector 是首选可注册引擎）
  - PGroonga 检索 SQL：

```sql
SELECT id, content, source_id, source_type, chunk_id, knowledge_id, knowledge_base_id, tag_id,
       pgroonga_score(tableoid, ctid) AS score
FROM embeddings
WHERE knowledge_base_id IN (...) AND (is_enabled IS NULL OR is_enabled = TRUE)
  AND content &@~ ?
ORDER BY score DESC LIMIT ?
```

  - 计划 04 的 DDL 追加段（本计划只给文本，不建表）：

```sql
CREATE EXTENSION IF NOT EXISTS pgroonga;
CREATE INDEX embeddings_content_pgroonga ON embeddings
    USING pgroonga (content pgroonga_text_full_text_search_ops_v2);
```

- [ ] **Step 1: 写失败测试**

`PgVectorSearchTest.keywordUsesPgRoongaOperatorAndScore`：断言生成的 SQL 含 `&@~` 与 `pgroonga_score`，不含 `paradedb` 与 `|||`。`VectorEngineTypeTest.exposesOnlyThreeEngines`：`ALL` 与 `CONFIGURABLE` 都等于 `Set.of("postgres","elasticsearch","milvus")`。`EngineRegistryTest.unsupportedEngineTypeReturnsCode`：传 `qdrant` 得到 `RetrievalCode.ENGINE_TYPE_UNSUPPORTED`。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl retrieval-engine -am test -Dtest='PgVectorSearchTest,EngineRegistryTest'`
Expected: FAIL。

- [ ] **Step 2: 停用 6 种引擎并用窄接口加登记表收敛**

六个引擎包原样保留（`doris`、`opensearch`、`qdrant`、`sqlite`、`tencentvectordb`、`weaviate` 与 `resources/jieba/` 都不删），只拆掉接线：`EngineFactory.SPECS` 去掉六条分支与对应 import，`VectorStoreEngineCatalog.probe` 去掉六条探测分支，`EffectiveEngines.RETRIEVER_ENGINE_MAPPING` 去掉六个驱动键，`VectorEngineType` 只留三值，`EngineRegistry` 不登记这六个实现。`EngineTypes` 的六个 `ENGINE_*` 常量与 `EngineAwareNormalizer` 的六个 case 保留：它们是停用包的编译依赖与纯函数归一分支，不等于可配置取值。

依赖处置选保留：`sqlite-jdbc`、`mysql-connector-j`、`HikariCP` 留在 `retrieval-engine/pom.xml`。理由：停用包仍在编译，`JdbcDorisSqlExecutor` 直接 import `HikariDataSource`，删依赖就要改停用包代码，与“停用不动源码”冲突。启用时再评估依赖收敛。

`Engine` 窄接口只有两个方法：`RetrieveResult retrieve(RetrieveParams)` 与 `void write(IndexBatch)`，三个实现 `PgVectorEngine`、`ElasticsearchEngine`、`MilvusEngine` 注册为 Bean，`EngineRegistry` 注入 `Map<VectorEngineType, Engine>`。这是真实变化点：新增引擎只加一个实现类，`EngineFactory` 的 switch 消失。

Elasticsearch 只留 v8：请求形状、批量写入、索引创建、k-NN 探针。Milvus 保留 REST v2 全部端点。

PGroonga：NFKC 归一化用 `java.text.Normalizer.normalize(query, Form.NFKC)`，查询串整串传给 `&@~`，不再做 jieba 分词。`pgroonga_score` 按 `1 - 1/(1+score)` 归一到 0 到 1。

- [ ] **Step 3: 保留停用引擎豁免、移除 CI 的 Weaviate 与写前端说明**

`checkstyle-suppressions-engines.xml` 中 `(doris|opensearch|qdrant|sqlite|tencentvectordb|weaviate)` 那条保留不动，它是停用包的长期豁免，本计划与计划 03 到 09 都不删。三类取值写入 `docs/migration/handoff-engines-spi.md` 的“向量库下拉”小节，注明“计划 03 的 vectorstore 前端把下拉从 9 项改 3 项，卡片样式 `.store-card--<engine>` 只留三个变体”，不提交前端改动。

`.github/workflows/ci.yml` 的 `integration` job 删掉 `起 Weaviate` 步骤、就绪等待里的 `9035` 端口判断与 `docker logs it-weaviate`、env `RAGAGENT_WEAVIATE_IT`，job 名改为 `真实驱动集成（Milvus，*IT）`。不改 `backend` 与 `guards` job。

- [ ] **Step 4: 跑绿**

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl retrieval-engine -am verify`
Expected: PASS。`PgVectorSearchTest` 通过（Testcontainers 用计划 01 的自建镜像，PGroonga 扩展可用）。`MilvusEngineIT` 与 `ElasticsearchEngineIT` 需要 Docker 与镜像拉取，首次运行十分钟级。停用包必须参与编译且 Checkstyle 走豁免，编译失败说明接线没拆干净。

- [ ] **Step 5: 提交**

```bash
git add backend/retrieval-engine backend/config/checkstyle-suppressions-engines.xml .github/workflows/ci.yml docs/migration/handoff-engines-spi.md
git commit -m "refactor(retrieval): 只保留 pgvector、Elasticsearch、Milvus，关键词检索改用 PGroonga"
```

### Task 7: model-runtime 内部整理

**Files:**
- Modify: `backend/model-runtime/src/main/java/com/ragagent/modelruntime/**`
- Modify: `backend/model-runtime/src/test/java/com/ragagent/modelruntime/**`
- Modify: `backend/config/checkstyle-suppressions-engines.xml`（删 model-runtime 的 5 条）

**Interfaces:**
- Consumes: Task 2 的搬入结果
- Produces: 1.2 表里 model-runtime 那一半的新类。4 个 `Ops` 的处置：`RemoteApiRequestOps`→`RemoteRequestAssembler`，`RemoteHttpOps`→`RemoteHttpExchange`，`RemoteApiStreamOps`→`RemoteStreamDecoder`，`RemoteApiResponseOps`→`RemoteCompletionReader`。四个新类各自持有一组构造参数（adapter、凭据、自定义头、模型名），不再持门面回引用。`ModelContextRegistry`、`ToolResultRenderer` 等按 1.2 与 1.3 表拆。`LlmChatClients` 的 71 处 `BizException.badRequest("英文")` 收敛到 4 个码：`CHAT_CONFIG_REQUIRED`、`CHAT_SOURCE_UNSUPPORTED`、`PROVIDER_CONFIG_INVALID`、`MODEL_NOT_FOUND`，其余英文文案改成 `BusinessRuntimeException` 加参数（文案进 i18n 的 key 参数，不留硬编码）

- [ ] **Step 1: 写失败测试 `NoGoCompatShapesTest`**

断言 model-runtime 主码里：不存在 `class .*Ops\b`、不存在 `BizException`，不存在 `import com.ragagent.platform`。这是模块级守卫，新增违规立即红。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl model-runtime -am test -Dtest=NoGoCompatShapesTest`
Expected: FAIL（当前有 27 个 Ops 与 71 处 BizException）。

- [ ] **Step 2: 拆 Ops 与门面回引用**

逐个替换 `new XxxOps(this)`：新类构造器显式收依赖，宿主只保留编排方法。替换完删掉宿主里的包可见字段。`RemoteChat` 的四个协作者通过构造器传入，宿主只留 `chat`、`chatStream` 两个入口。

- [ ] **Step 3: 拆大类与超限方法**

按 1.2 与 1.3 表拆 `ModelOutput`、`ToolPolicy`、`ModelContextRegistry`、`SourceRegistry`、`OllamaService`、`AnthropicChat`、`RemoteChat`、`PromptCache`、`OllamaChat`、`SourceToolCodec`；按 1.2 方法表拆超限方法。每拆一个类，先跑该类对应的旧测试。

- [ ] **Step 4: 改 `Map<String, Object>` 与注解 SQL 检查**

model-runtime 的 24 处 `Map<String, Object>` 中保留 12 处（JSON 组装、jsonb 透传、`ToolResult.data`），改 12 处（`AgentState` 相关、观测字段、`ProviderMetadataMaps` 的双向转换收成一个 `ProviderMetadata` record）。确认无注解 SQL（已核对为 0）与无 `JdbcTemplate`（已核对为 0）。

- [ ] **Step 5: 删豁免并跑全量**

从 `checkstyle-suppressions-engines.xml` 删除 `modelruntime/embedding`、`modelruntime/llm`、`modelruntime/context`、`modelruntime/assembly`、`modelruntime/rerank` 五条 suppress。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl model-runtime -am verify`
Expected: BUILD SUCCESS，Checkstyle 无违规，`NoGoCompatShapesTest` 通过。

- [ ] **Step 6: 提交**

```bash
git add backend/model-runtime backend/config/checkstyle-suppressions-engines.xml
git commit -m "refactor(model-runtime): 拆 Ops 与超限类，错误码与 Go 兼容写法清零"
```

### Task 8: retrieval-engine 内部整理

**Files:**
- Modify: `backend/retrieval-engine/src/main/java/com/ragagent/retrieval/**`
- Modify: `backend/retrieval-engine/src/test/java/com/ragagent/retrieval/**`
- Modify: `backend/config/checkstyle-suppressions-engines.xml`（删 retrieval 的 3 条）

**Interfaces:**
- Consumes: Task 6 的三引擎结果
- Produces: 1.2 表里 retrieval 的新类。`HybridFusionOps`→`HybridFusion`，`HybridResultOps`→`HybridResultAssembly`，`HybridStoreGroupOps`→`StoreGroupResolver`；ES 两个 `Ops` 变 `ElasticsearchV8Search` 与 `ElasticsearchV8IndexWriter`；Milvus 两个 `Ops` 变 `MilvusSearch` 与 `MilvusIndexWriter`。`HybridSearchService` 拆成 `HybridSearchService`（用例）加 `HybridFanout`（虚拟线程扇出、组超时）。`IndexingService` 实现 `IndexingApi`，内部按 store 解析后委派 Engine。`VectorStoreService` 的写 SQL 进 `PgVectorIndexWriter`（`JdbcTemplate` 保留，spec 5.11 第 3 条）

- [ ] **Step 1: 写失败测试 `RetrievalGoCompatTest`**

断言：主码无 `Ops` 类，`backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/{doris,opensearch,qdrant,sqlite,tencentvectordb,weaviate}` 六个停用目录除外；`JdbcTemplate` 只出现在 `retrieval.engine.pgvector` 与 `retrieval.service`（写面）；无注解 SQL；`HybridSearchService` 的行数小于 400。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl retrieval-engine -am test -Dtest=RetrievalGoCompatTest`
Expected: FAIL。

- [ ] **Step 2: 拆 Ops 与超限类**

按 1.2 与 1.3 表拆。`HybridSearchService.search` 拆成 `loadSources`、`resolveGroups`、`fanout`、`fuse`、`expand` 五个私有方法，每个不超过 40 行。`HybridFanout` 独立成类，扇出上限常量 `MULTI_STORE_FANOUT_LIMIT = 4`、组超时常量 `GROUP_TIMEOUT_SEC` 收口。

- [ ] **Step 3: 收敛 `Map<String, Object>`**

183 处里，六个停用引擎包占 134 处，原样保留（停用包不整理）；其余 49 处中保留 33 处（引擎请求与响应 JSON、ES 与 Milvus 报文、`EngineTypes` 的 `additionalParams`），改 16 处（检索结果组装中间值、观测字段、`RetrievalObservation` 的 7 个键改为 record 字段）。改写时 `knowledge_base_id`、`knowledge_id`、`tag_id`、`tenant_id` 谓词保留在 SQL 中（spec 第 6 节第 3 步例外），并在类 Javadoc 写一行中文说明“服务于索引命中的谓词，不是手写租户隔离”。

- [ ] **Step 4: 删豁免并跑全量**

删除 `retrieval/(Hybrid.*|artifact|config|graph|support|telemetry)`、`retrieval/engine/(根类清单)`、`retrieval/engine/(elasticsearch|milvus)` 三条 suppress。第三组只含两个保留引擎，6 个停用引擎那条在 Task 6 明确保留、本步也不删，删完 `grep -c "<suppress"` 应剩 agent-engine 的 6 条与停用引擎 1 条，共 7 条。

`noGoCompatSuffixes` 的处置：该规则对全部 `com.ragagent..` 生效，停用包里的 14 个 `*Ops` 类会让 `ArchitectureRulesTest` 变红，而停用包按 D17 不做命名整理。做法是给规则加排除包清单，见“对前置计划的新增需求”第 3 条；本步只改 `backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java` 的 `noGoCompatSuffixes`，加 `.that().resideOutsideOfPackages(...)` 六条路径，并在测试 Javadoc 写明排除原因与重启时删除。不改 `checkstyle.xml`，也不改 `noGoCompatSuffixes` 的后缀清单。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl retrieval-engine -am verify`
Expected: BUILD SUCCESS，`RetrievalGoCompatTest` 通过，`MilvusEngineIT` 与 `ElasticsearchEngineIT` 通过。

- [ ] **Step 5: 提交**

```bash
git add backend/retrieval-engine backend/config/checkstyle-suppressions-engines.xml
git commit -m "refactor(retrieval): 拆混合检索与引擎适配的大类，收敛 Go 兼容写法"
```

### Task 9: agent-engine 内部整理与 41 项用例

**Files:**
- Modify: `backend/agent-engine/src/main/java/com/ragagent/agentengine/**`
- Create: `backend/agent-engine/src/test/java/com/ragagent/agentengine/{react,compaction,context,tool,event,skill}/**`（1.11 的 41 项用例）
- Modify: `backend/config/checkstyle-suppressions-engines.xml`（删 agent-engine 的 6 条：`react`、`context`、`compaction`、`prompt`、`skill`、`tool`）
- Modify: `scripts/stub-llm-server.py`（新增 `empty`、`repeat`、`slow`、`slowtool`、`badjson`、`truncated` 六个场景）
- Modify: `docs/migration/handoff-engines-spi.md`（补“agent 事件与工具”小节）

**Interfaces:**
- Consumes: Task 4 的搬入结果、Task 1 的 api 与 spi
- Produces: 1.2 表里 agent 的新类。`FileOps`→`CompactionFiles`（沙箱路径存续，只做类名与私有方法整理，行为不变）。`AgentEngine` 拆 `AgentRunContext`、`AgentEventPublisher`；`ActPhase` 拆 `ToolCallDispatcher`、`TruncatedCallGuard`；`ThinkPhase` 拆 `ModelStreamPump`、`CancellationWatch`、`EmptyReplyNudge`；`AgentPrompts` 拆 `AgentPromptSections`、`SkillDisclosure`；`ConversationAssembler` 拆 `RuntimeContextBlock`、`HistoryRetrievalRedactor`；`ToolRegistry` 拆 `ToolCatalogGuard`、`DeferredToolLoader`；`ObservePhase` 拆 `TerminalDecision`

- [ ] **Step 1: 扩展 stub 与写失败测试**

在 `scripts/stub-llm-server.py` 的 `SCENARIOS` 加 `empty`、`repeat`，在 `chat` 分支加 `slow`、`slowtool`、`badjson`、`truncated` 四个分支（行为见 1.16）。原场景不改。

先写 1.11 表里 F 组的用例（第 5、10、11、12、13、14、15、16、17、18、19、21、24、31 项），用真实 stub 进程（`subprocess.Popen` 起 python），断言具体事件序列。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl agent-engine -am test -Dtest='EmptyReplyNudgeTest,RepeatedContentGuardTest,StreamWatchdogTest,JsonRepairTest,TruncatedCallGuardTest'`
Expected: FAIL，类不存在。

- [ ] **Step 2: 拆类与拆方法**

按 1.2 与 1.3 表逐个拆，每拆一个类跑对应旧测试。事件顺序契约集中在 `AgentEventPublisher` 一处实现：错误帧先于终态帧、终态恰好一次、Done 延后到 steer drain 之后、复用 `answerId`。载荷键序用 `ToolJson.write` 的递归键排序，UTF-8 字节序比较写在 `AgentEventPayloadTest`。

- [ ] **Step 3: 补齐 R 组与 L 组用例**

R 组按 1.11 表落地；L 组 7 项（第 1、2、3、4、6、7、8 项）需要超过上下文窗口的构造，用 `CompactionSettings` 的小窗口参数加长历史，不依赖真实模型。每项用例名必须与 1.11 表一致，`grep` 能一一定位。

Run: `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl agent-engine -am test`
Expected: PASS。`grep -c "@Test"` 统计的新用例数不少于 41。

- [ ] **Step 4: 5.10.4 第二组：故障注入全跑**

用 F 组用例覆盖 `early-error`、`early-close`、`slow`、`slowtool`、`empty`、`repeat`、`badjson`、`truncated` 八个场景，每个场景至少一条用例断言可解释的收束路径（错误帧、拒执、看门狗中断、nudge 重试、重复收束）。

Run: 同 Step 3 的命令，`Expected: PASS`。`scripts/stub-llm-server.py` 改动随本步骤提交。

- [ ] **Step 5: 删豁免并提交**

删 agent-engine 的 6 条 suppress，跑 `mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl agent-engine -am verify`，Expected: BUILD SUCCESS，Checkstyle 违规为 0。

```bash
git add backend/agent-engine backend/config/checkstyle-suppressions-engines.xml scripts/stub-llm-server.py docs/migration/handoff-engines-spi.md
git commit -m "refactor(agent-engine): 拆 ReAct 与上下文大类，41 项能力用例与故障注入落地"
```

### Task 10: 回归对拍、豁免收敛与收口

**Files:**
- Create: `tests/regression/RESULT-engines.md`
- Modify: `docs/migration/handoff-engines-spi.md`（补“缺失 spi 时的启动行为”）
- Modify: `docs/migration/progress.md`（计划 02 各任务行）
- Modify: `backend/config/checkstyle-suppressions-engines.xml`（只剩停用引擎那 1 条）

**Interfaces:**
- Consumes: Task 0 到 Task 9 全部产物，计划 01 Task 14 的回归基线
- Produces: 完成判据的五项输出，见文末

- [ ] **Step 1: 检索回归对拍**

按 `tests/regression/README.md` 的说明，在旧系统（`../ragagent-legacy`）与新栈各跑一遍基线题集，记录每题的命中分块内容哈希与前 5 名。新栈的召回条数不少于旧系统，命中集合的重合率不低于旧系统。把逐题结果写进 `tests/regression/RESULT-engines.md`，差异逐条写原因（允许的原因只有三类：引擎停用导致旧题落空、PGroonga 打分差异导致的排序变化、旧系统 ParadeDB 与 PGroonga 的分词差异）。

Run: `cat tests/regression/RESULT-engines.md`。Expected: 有 `结论` 段，逐题表与“不劣于旧系统”的判断。

- [ ] **Step 2: 豁免收敛验证**

Run: `grep -c "<suppress" backend/config/checkstyle-suppressions-engines.xml`
Expected: `1`。只剩停用引擎包 `(doris|opensearch|qdrant|sqlite|tencentvectordb|weaviate)` 那一条，其余按包豁免全部删除。

- [ ] **Step 3: 全量验证**

Run:
```bash
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml verify
grep -rn "Ops\|Support\|Helper\|Facade\|Manager\|Common" backend/*/src/main/java --include=*.java \
  --exclude-dir=doris --exclude-dir=opensearch --exclude-dir=qdrant --exclude-dir=sqlite \
  --exclude-dir=tencentvectordb --exclude-dir=weaviate | grep -c "class "
grep -rn "@Select\|@Update\|@Insert\|@Delete" backend/*/src/main/java --include=*.java | wc -l
grep -rn "paradedb\|content |||" backend --include=*.java | wc -l
```
Expected: BUILD SUCCESS；三个 grep 输出均为 0（第一条排除 6 个停用引擎目录）；Checkstyle 无违规，停用包走豁免；`ArchitectureRulesTest`、`ModuleBoundaryTest`、`CodeQualityRulesTest` 全绿。

- [ ] **Step 4: 登记与提交**

`progress.md` 每任务一行。`handoff-engines-spi.md` 补每个 spi 缺失时的行为（model 缺失时启动失败，retrieval 的三个缺失时检索与索引可用但探测不可用，agent 的三个缺失时技能与 MCP 降级、事件必填）。

```bash
git add tests/regression/RESULT-engines.md docs/migration/handoff-engines-spi.md docs/migration/progress.md \
  backend/config/checkstyle-suppressions-engines.xml
git commit -m "test(engines): 检索回归对拍结论与引擎豁免收敛到停用包"
```

## 完成判据

对应路线图第 2 节 02 号闸门，逐条给出命令与预期。

1. **三个引擎模块单测与集成测通过。**

```bash
mvn -s C:/Users/jiangguifan/.m2/settings-coding-uat.xml -f backend/pom.xml -pl model-runtime,retrieval-engine,agent-engine -am verify
```
Expected: BUILD SUCCESS，三个模块测试数与 1.10 处置后的移植数一致，`ChatStreamTerminalTest`、`PgVectorSearchTest`、`ElasticsearchEngineIT`、`MilvusEngineIT`、41 项能力用例全部通过。

2. **检索回归基线对拍不劣于旧系统。**

Run: `grep -A3 "结论" tests/regression/RESULT-engines.md`
Expected: 明确写出召回条数不少于旧系统，逐题差异已解释。

3. **ArchUnit 与 Modulith 通过。**

```bash
MVN test -Dtest='ArchitectureRulesTest,ModuleBoundaryTest,EngineAssemblyBoundaryTest'
```
Expected: PASS。`enginesExposeOnlyApiAndSpi`、`enginesAreAssembledOnlyInAppConfig`、`jdbcTemplateAllowedOnlyInTenantAndRetrieval`、`noGoCompatSuffixes` 四条对三引擎全量生效且不空转。注意 `noGoCompatSuffixes` 对全部 `com.ragagent` 包生效，6 个停用引擎包的 `*Ops` 类会让它变红，处理方式见 Task 8 Step 4。

4. **豁免收敛到停用包。**

Run: `grep -c "<suppress" backend/config/checkstyle-suppressions-engines.xml`
Expected: `1`（只剩停用引擎包那条），且全仓 `@SuppressWarnings("checkstyle:` 不超过 10 处，每处前一行是中文理由。

5. **三个模块只暴露 api 与 spi，可配置引擎只剩三种，6 种停用引擎不出现在接线处。**

```bash
find backend/model-runtime/src/main/java/com/ragagent/modelruntime -maxdepth 1 -type d
grep -n "POSTGRES\|ELASTICSEARCH\|MILVUS" backend/retrieval-engine/src/main/java/com/ragagent/retrieval/api/VectorEngineType.java
grep -rn "sqlite\|opensearch\|doris\|qdrant\|weaviate\|tencent_vectordb" \
  backend/retrieval-engine/src/main/java/com/ragagent/retrieval/api/VectorEngineType.java \
  backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/EngineRegistry.java \
  backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/VectorStoreEngineCatalog.java \
  backend/retrieval-engine/src/main/java/com/ragagent/retrieval/engine/EffectiveEngines.java \
  backend/retrieval-engine/src/main/java/com/ragagent/retrieval/config \
  backend/app/src/main/java/com/ragagent/config --include=*.java | wc -l
```
Expected: 第一行列出 `api`、`spi` 与内部包，无其它顶层入口；第二行只有三个常量；第三行为 `0`，即 `EngineRegistry`、`VectorEngineType` 与配置类中不出现这六个引擎名。停用目录 `engine/{doris,opensearch,qdrant,sqlite,tencentvectordb,weaviate}` 不在扫描范围；`EngineFactory` 与 `EngineTypes` 也不在扫描范围，它们保留停用包的编译引用与 `ENGINE_*` 别名。

6. **agent-engine 的 41 项一项不丢。**

Run: `grep -rc "@Test" backend/agent-engine/src/test/java/com/ragagent/agentengine | awk -F: '{s+=$2} END {print s}'`
Expected: 不少于 41，且 1.11 表里的每个用例名都能 `grep -r "<用例名>"` 命中。

7. **otlp-proto 的归属与计划 09 不重复。** 本计划完成了 OpenTelemetry SDK 接入（Task 2 Step 3），`otlp-proto/` 目录未删除。

```bash
grep -rn "otlp-proto" backend deploy .github | wc -l
```
Expected: `0`。目录本身仍在，由计划 09 Task 12 的“已改”分支删除。计划 09 的 `<OTLP_STATE>` 因此取值 `已改`。

