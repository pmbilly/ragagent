# shared 解散实施计划（交执行 Agent）

> **生成**：2026-11-21，基线提交 `d024c7107`（v0.11.x，工作区 clean）。
> **v2 修订（2026-11-21 复核）**：五档分解与 WP1 清单**重扫替换**（原 dead 33 实为 9，见 §3 与 WP1）；R10 同级 api 判据已获批并落地（`level_verdict` + 两条红态探针）；三条空转守卫已修复（port-map / contract-consumers / ArchUnit `isContractPort`）。
> **上游文档**：`docs/internal/plans/backend-package-restructure-2026-11-19.md` §12（六阶段）与 §12.4.2（P4 重评）。本文件是它的**剩余工作执行细化**。
> **执行前必读**：第 8 节坑清单——每一条都有实测翻车记录。

## 1. 任务定义

`backend/framework/src/main/java/com/ragagent/shared`（下称 shared）起点 **188** 个类型 = 35 通用面 + 153 业务词汇；**当前剩余 147**（36 通用面 + 111 业务词汇）——已归位 42 个业务词汇。任务终态：

1. **153 个业务词汇清零**（已完成 42）——删除或归位到域 `api/`、引擎 `api/`、framework 正式包。
2. **35 个通用面迁出 shared 命名空间**——`error` 3 · `event` 26 · `json` 3 · `text` 2 · `pagination` 1 → `framework/error|event|json|text|pagination`（蓝图 §5.2 明确归 framework）。
3. **`shared` 命名空间消失**。`contract`、`shared` 二词不再出现在主码包名里。

已完成、**不要重做**：P1a（mixin 三组迁移，`9c4091ec4`/`8dbd17902`/`644a12344`）、域合并 wiki→knowledge（`c3c4f1b49`）与 system→auth（`dd91a0f29`）、P3 主体归位 55 个（`e64ee5340`/`10308652f`/`d024c7107`）。app 现有 **17 个业务域 + config**，域分级单一来源是 `scripts/capability-kinds.json`。

## 2. 不可协商约束（每批验收都包含）

| # | 约束 | 判据 |
|---|---|---|
| 1 | **不动前端** | 路由、请求/响应字段、错误码、SSE 帧格式全不变；金片测试（`*ContractTest`）逐字节不变 |
| 2 | **不动数据库** | 无 DDL、无迁移脚本、无表结构变更 |
| 3 | **延用金片** | 金片是核心安全网；mixin/序列化路径改动尤其依赖它 |
| 4 | **延用守卫** | `bash scripts/run-guards.sh` 全绿（脚本汇总行为准：**49** 条）；路径类基线只许 `--write` 重登记。**唯一已批准的语义变更是 R10 同级 api**（2026-11-21：`la == lb` 且目标 ⊆ {api,dto} 放行，实现包仍禁），其余语义判据不动 |
| 5 | 全量闸门 | `mvn -B clean verify`（根 `pom.xml`，`backend/pom.xml` 不存在） 绿，测试条数守恒（与上一批相比不减少） |
| 6 | 包级环 | `check-package-cycles.py` 只减不增 |

**验收命令**（每批一次，不用管道以免吃退出码）：

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # keg-only，不 export 必炸
cd /Users/billy/ragagent
mvn -B clean verify; echo "exit=$?"   # 根 pom；backend/pom.xml 不存在
bash scripts/run-guards.sh; echo "exit=$?"
```

开工第一件事：跑一次上面的命令，把**测试总条数 / 失败数 / 守卫条数（49）** 三个数字记入下文台账，作为后续批次"条数守恒"的基线。

## 3. 剩余 153 个的五档分解（2026-11-21 v2 重扫）

**口径**（v1 的清单在此口径下重扫后被替换）：主码 `src/main/java`；FQN 精确匹配（import 或全限定使用）；shared 内部互引不计；`app/config` 不算业务域；优先级 dead > framework > engine > single > multi。同一提交内可复现。

| 档 | v2 实测 | v1 声称 | 对应工作包 |
|---|---|---|---|
| dead（任何地方都无引用） | **0** | ~~33~~ | 无 |
| single（单域） | **19** | ~~21~~ | WP2 域归位 |
| engine（被引擎用，含引擎+域） | **12** | 24 | WP3 引擎归位 |
| multi（≥2 域） | **31** | ~~46~~ | WP4 域归位 |
| framework（被 framework 引用） | **22** | ~~29~~ | WP5（永驻词汇） |

合计 **84**（已归位 69）。**dead 归零**：v1 的 33 个里，9 个是 shared 内闭包（7 个端口分面 + 2 个被同包调用），其余 24 个是外部活代码；single/multi/framework 三档随之重算。

收尾 WP6 = 35 通用面迁出 + 守卫平移 + 删命名空间。

### 3.1 守卫预算预检（2026-11-21 补，用户复核抓出）

`check-contract-shape.py` 只扫 `com/ragagent/contract/` 前缀与一切 `*/api/` 路径——**shared 现路径不在扫描面内，搬进 api 的那一刻才触发**。已按守卫自己的 `count_fields` / `count_port_methods` 口径全量预检：

**字段预算（≤20）撞墙 4 个：**

| 类型 | 字段数 | 计划去向 | 处置 |
|---|---|---|---|
| `knowledge.KnowledgeBaseResponse` | 32 | WP2 → knowledge/api | 搬迁同 commit 登记 `contract-shape.baseline.json` |
| `tenant.ParserEngineConfig` | 27 | WP4-A → auth/api | 同上 |
| `retrieval.SearchResult` | 25 | **永驻 framework**（model-runtime/agent-engine 在用） | 非 api 路径，天然不进扫描面 |
| `tenant.StorageEngineConfig` | 63 | D-2 永驻 framework 非 api 包 | 不撞（非 `/api/` 路径不在扫描面） |

（`ChunkRowView` 30 / `KnowledgeRowView` 30 / `MessageView` 26 走 D-3/D-4 永驻路线，同理不撞；若拍板改走 api 则一律登记基线。）

**端口方法预算（≤7）：** 守卫只对**名字以 `Port` 结尾的接口**计数。shared 现有 20 个 `*Port` 全部 ≤7，唯一贴线的是 `SessionQaPort`（恰好 7）——搬迁后**不得再加方法**。`agent.SearchAuth`（11 方法）与 `tenant.TenantConfigLookup`（9 方法）都**不以 Port 结尾**，不进这条预算；不要为它们登记基线（登记一条永不命中的条目只会让守卫读数失真）。

**登记纪律**：基线按**路径**登记 ⇒ 必须在搬迁的**同一 commit** 用**新路径**写入 `scripts/contract-shape.baseline.json`（旧路径条目会因 stale 报红）。棘轮只降不升——登记是承认现状，不是放松未来；这些形状的收窄（H6 路线，`MAX_FIELDS` 20 → 12）属后续治理，不在本计划。

## 4. 工作包

### 4.0 归属总表（剩余 43 个）

> 起点 153 个业务词汇，已归位 87 个（WP4-C 1 · 知识簇 40 · 模型面 21 · 检索面 6 · 域面 18 · 内联 1）；下表是**剩余 48 个**的唯一归属。

| 落点 | 数量 | 类型 |
|---|---|---|
| `app/storage/api` | 7 | `FileContentService` `MessageFileFacts` `PublicModeForbiddenException` `ResourceModeException` `StorageEnvLookup` `StorageUrlContext` `WritableFileContentService` |
| `app/session/api` | 9 | `MentionedItem` `MessageExecutionContext` `MessageImage` `PipelineMessageAttachmentView` `SessionLastRequestState` `SseSink` `SuggestionAttribution` `SuggestionItem` `UsedMemory` |
| `app/memory/api` | 2 | `MemoryKeys` `MemoryKinds` |
| framework 正式包（永驻） | 22 | `APIKeyScopeContext` `AuditAction` `ChunkFacts` `ChunkView` `FaqChunkMetadata` `GraphNode` `GraphRelation` `KnowledgeBaseProvisioner` `KnowledgeView` `MessageView` `SearchAuth` `SearchResult` `SearchTarget` `StorageBackendPort` `StorageBackendProvisioner` `StorageBackendView` `StorageConnectivityPort` `StorageEngineConfig` `TenantAPIKeyScope` `TenantConfigLookup` `TenantRole` `WikiLanguageSupport` |
| agent-engine `agentengine.api` | 10 | `AgentStep` `AgentTool` `AgentToolCall` `BaseTool` `ToolCallTarget` `ToolCancellation` `ToolExecContext` `ToolOutput` `ToolRequest` `ToolResult` |
| `app/storage/api`（随 platform/storage 整包） | 7 | `FileUrlPort` `MessageFactsLookup` `Mode` `OpenedFile` `Resolver` `SeekableSource` `StorageBackendResolver` |
| framework 工具包 | 1 | `KnowledgeIngest` |
| retrieval-engine `retrieval.api | 1 | `MessageArtifact` |
| `app/im/api` | 1 | `MessageAttachment` |
| `app/auth/api` | 4 | `APIKeyCapability` `APIKeyScopeType` `APIPrincipalConfig` `MemoryConfig` |
| 待拍板（`KnowledgeBaseGateway`） | 1 | `KnowledgeBaseGateway` |
| `app/audit/api` | 1 | `KnowledgeBaseFacts` |

**六条落点约束**：

1. `app/knowledge/api` 已落地（知识簇 39 个），子包 `kb/chunk/catalog/config/projection/error/wiki`；`memory/api`、`im/api`、`embedchannel/api`、`audit/api`、`auth/api` 本批落地。
2. **`framework` 永驻 22 个**：被 framework 视图/工具与引擎引用。`MemoryKeys`/`MemoryKinds`/`MemoryConfig`/`APIPrincipalConfig`/`APIKeyCapability`/`APIKeyScopeType`/`KnowledgeBaseFacts`/`MessageAttachment`/`AgentStep`/`AgentToolCall`/`ToolCallTarget` 因被永驻类型引用而留下。
3. 模型面（20）与检索面（6）已离开 shared；`Embedder`/`ModelFacts`/`EmbeddingPort` 在 `platform/model`（C8 登记）。
4. `SearchResult` 永驻 framework。
5. `app/knowledge/api` 计划 31 个，实际 39（含 D-6 组）。
6. `KnowledgeBaseGateway` 仍待拍板。

### 4.5 storage 批次结论（2026-11-21 完成，18/18）

storage 18 个类型分两批归位：

| 批次 | 数量 | 落点 | 说明 |
|---|---|---|---|
| 业务/开通面 | 7 | `app/storage/api` | `FileContentService` `WritableFileContentService` `StorageBackendPort` `StorageBackendProvisioner` `StorageBackendView` `StorageConnectivityPort` `StorageEnvLookup` |
| IO 载体与策略词汇 | 11 | `platform/storage/{fileserve,support}` | `OpenedFile` `SeekableSource` `Mode` `Resolver` `FileUrlPort` `StorageUrlContext` `ResourceModeException` `PublicModeForbiddenException` `StorageBackendResolver` `MessageFactsLookup` `MessageFileFacts` |

**为什么不是"门面重构"**：原计划估 3 人日改 5 个文件，实测后发现 11 个里有 9 个是纯 IO/策略词汇——`OpenedFile`（seekable/stream/bytes 三形态）、`SeekableSource`（`size()`+`open(offset)`）、`Mode`/`Resolver`/`FileUrlPort`（重写策略的入参类型）。任何"中性化"都会重新造出同形状的类型，属于换名不换结构。因此按用户已拍板的「能力接口/IO 载体算基础设施」口径归 `platform/storage`，`shared/storage` 整包删除。

**残留**：`FileUrlPort`、`MessageFileFacts` 两个名字撞 C8 红旗命名，已登记基线并写明「IO 能力契约，非业务形状」。若将来要求 framework 零 storage 词汇，仍需把 `Rewriter`/`FileProxyPort` 的策略参数化（约 1.5 人日）。

### 4.7 包根收敛（2026-11-21，已完成）

`platform` 与通用面的落点已收敛为**单一根**：

```
com.ragagent.framework
├── error · event · json · text · pagination     ← 契约面（纯 JDK，107 文件受 C1 覆盖）
├── eventbus · html                              ← 原 platform.event / platform.text 的技术实现下沉一层
├── web · http · persistence · redis · security · access · config · settings
│   · concurrent · task · tracing · storage · prompt · tool · model · audit
│   · knowledge · text                           ← 原 platform 的 183 文件
└── shared                                       ← 暂存区：剩余 48 个业务词汇，清零后删除
```

**为什么不是顶层 `com.ragagent.{error,event,...}`**：`framework.event`（事件总线实现）与事件契约同名；合并会破坏 C1「契约面零第三方依赖」。改为实现下沉 `framework.eventbus` / `framework.html`，契约留在 `framework.{error,event,json,text,pagination}`。

**为什么 `shared` 收进 `framework.shared`**：通用面进 framework 后，`shared → framework` 与 `framework → shared` 形成包级环（R1 新增环即红）。收进同一根后环消失，且暂存区边界仍然可见（清零后直接删目录）。

**守卫同步**：C1 从「扫目录」改为「扫 framework 下契约子包白名单」（107 文件，红态探针仍有效）；C8 豁免 `framework.shared` 与契约子包；`L1/L1_CORE` 增补 `framework`；`check-event-face-case` 锚改 `framework/event/payload` + `framework/eventbus/EventType`；json 冻结前缀、装配点、形状基线、错位测试登记全部重键。
### WP1 复核结论：0 个可删（已执行：`JsonValueSupport` 内联）

删除前按坑 15 做了三处扫描（简单名 / 反射字符串 / SPI），结果推翻了"9 个死代码"：

| 类型 | 为什么不是死码 | 处置 |
|---|---|---|
| `ChunkFaqReadPort` `KnowledgeBaseReadPort` `KnowledgeBaseWritePort` `KnowledgeContentWritePort` `KnowledgeDeletePort` `KnowledgeEntryReadPort` `KnowledgeIngestLookupPort` | 全是 `KnowledgeCatalogPort` 的 `extends` 分面（复合端口按能力拆窄的产物） | 随复合端口整组搬（见 D-6） |
| `JsonValueSupport` | 被 `KnowledgeBaseRowView` 调用（`JsonValueSupport.boolAt(...)`，同包不需要 import） | **内联**进 `KnowledgeBaseRowView`，消 1 个类型 |
| `DuplicateKnowledgeDetails` | 由 `DuplicateKnowledgeException` 构造（同包） | 随异常进 `knowledge/api` |

**结论**：`dead = 0`，WP1 不再有删除动作。这 9 个的共性是"外部 import 为零"——FQN 口径看不见同包引用，**坑 3 的原样复现**（上一版清单是同一个错误）。后续判死必须用"简单名 + 同包 + 测试 + 反射 + SPI"五路扫描，不能只看 import。

**已完成**：`JsonValueSupport` 是 `JsonValues.boolAt` 的一行委托，已直接改调 `JsonValues` 并删除该类（唯一调用点是 `KnowledgeBaseRowView`）。

### WP2 单域 21 个 → 域 `api/`（1~2 天）

| 落点 | 类型 |
|---|---|
| `app/knowledge/api`（9） | `ChunkPort` `ChunkView` `EmbeddingModelPort` `KnowledgeBaseLookup` `KnowledgeBaseResponse` `KnowledgePageView` `KnowledgeSpanPort` `KnowledgeView` + `wiki.WikiException`（wiki 已并入 knowledge） |
| `app/memory/api`（4） | `MemoryKeys` `MemoryKinds` `MemoryRecall` `MemoryRetrievalContext` |
| `app/session/api`（6） | `MentionedItem` `MessageImage` `SessionLastRequestState` `SuggestionAttribution` `SuggestionItem` `UsedMemory` |
| `app/auth/api`（2） | `apikey.APIKeyCapability` `apikey.APIKeyScopeType`（消费方只有 auth；同包的 `APIKeyScopeContext`/`TenantAPIKeyScope` 走 WP5 留 framework） |

**每类型标准流程**（WP2~WP4 通用，P3 批次已验证）：

1. **闭包预检**：`grep -rn "shared.<包>.<类型>" backend/ --include='*.java'`——重点看 **shared 内部**是否还有"留下的类型"引用它（同包简单名引用不需要 import，FQN 匹配抓不到同包的，要肉眼看同包文件）。有引用则连带搬或先搬引用者。
2. 移动文件到目标 `api/` 包，改 `package` 声明；目标包无 `package-info.java` 必须新建（D3 守卫）。
3. 改所有消费方 import（或全限定名，本项目两种都有——`846f6acb5` 之后守卫认全限定名）。
4. `mvn -pl backend/<模块> -am compile`（模块名形如 `backend/app`） 快速失败，再跑第 2 节全量验收。
5. spotless 对**每个受影响模块分别** `apply`（跨模块 import 改动后一次性 apply 会漏）。

### WP3 引擎 24 个 → 引擎 `api/`（2~3 天）

| 落点 | 类型 |
|---|---|
| `model-runtime`（7） | `model.CacheRetention` `model.EmbeddingPort` `model.FunctionCall` `model.FunctionDef` `model.ImageUrl` `model.MessageContentPart` `model.ModelFacts` |
| `agent-engine`（10） | `agent.AgentStep` `agent.AgentToolCall` `agent.ToolCallTarget` + `tool.AgentTool` `BaseTool` `ToolCancellation` `ToolExecContext` `ToolOutput` `ToolRequest` `ToolResult`（`ToolResult` 被 model-runtime 也用——拍板 D-5，若定 model-runtime 则从本行移出） |
| `retrieval-engine`（7） | `retrieval.RetrieveGraphRepository` `retrieval.SearchParams` `graph.GraphData` `graph.NameSpace` `websearch.WebSearchResult` `knowledge.KnowledgeDocumentFacts` `session.MessageArtifact`（后两个是检索载荷形状） |

**落点命名空间判据**（§7.5 实测）：搬入前跑 `check-package-cycles.py`，目标模块内部有环则换命名空间——`model-runtime` 用 `com.ragagent.llm.api`（实测可行），`agent-engine` 用 `agentengine.api`（B3 验证），`retrieval-engine` 用 `retrieval/api`（P4-4/P4-5 验证）。

**注意**：`mixin` 随类型走（S1 机制）；目标模块已有 provider 文件**只追加不重建**（ModelJsonModule 事故，见坑 1）。

### WP4 多域 46 个（3~6 天，含拍板项）

#### WP4-A 唯一 home 26 个（零业务逻辑，纯 WP2 流程）

| 落点 | 类型 |
|---|---|
| `app/auth/api`（5） | `tenant.UserPreferences` `tenant.APIPrincipalConfig` `tenant.ChatHistoryConfig` `tenant.ParserEngineConfig` `tenant.RetrievalConfig`（tenants 表 jsonb 配置列形状，auth 是租户属主域） |
| `app/auth/api`（1） | `tenant.MemoryConfig`（同上，P4-3 已论证语义） |
| `app/knowledge/api`（7） | `ChunkPageView` `FaqEntryPageView` `FaqEntryView` `KnowledgeBaseView` `SearchChunkMerge` + `wiki.ExtractedItem` `wiki.SlugUpdate`（wiki 并入后剩件） |
| `app/im/api`（4） | `session.MessageAttachment` `QaRequestPayload` `SessionQaPort` `SessionView` |
| `app/embedchannel/api`（3） | `session.EmbedReply` `EmbedSessionPort` `MessageSuggestionView` |
| `app/initialization/api`（2） | `knowledge.KnowledgeBaseRowView` `KnowledgeCatalogPort` |
| `app/memory/api`（2） | `MemoryItemView` `MemoryMessageCursor` |
| `app/session/api`（1） | `MessageExecutionContext` |
| `app/storage/api`（1） | `StorageEnvLookup` |

（消费方全是上级或同级——2026-11-21 起 R10 对同级 **api/dto** 也放行，本表落点全部 `level-ok`；`config` 装配面不算业务域。）

#### WP4-B 同级互需 18 个（每个二选一：下沉 framework / 下沉某域，逐组拍板）

> 2026-11-21 同级 api 放行后，下表 **3 条转正**（`KnowledgeBaseFacts` / `DocReaderAdminPort` / `WebSearchConfig` 直接落同级 `api`，不再需要拍板）；真正只剩 `KnowledgeBaseGateway` 一条方向违规（L1→L2）。

| 组 | 类型 | 推荐处置 |
|---|---|---|
| audit+auth（2） | `AuditAction`（6 域消费）· `KnowledgeBaseFacts` | **AuditAction → framework 正式包**（全平台审计词汇，蓝图 §5.3 框架层有审计能力）；`KnowledgeBaseFacts` → `app/audit/api`（同级 api 已放行） |
| audit+auth+websearch（1） | `KnowledgeBaseGateway` | `app/knowledge/api` 并按 §5.11 Provider 模式反转（knowledge 实现、audit/websearch 消费）——**这是 B 档唯一要动代码结构的**，或拍板永驻 framework |
| auth+initialization（1） | `DocReaderAdminPort` | `app/auth/api`（同级 api 已放行；system 已并入 auth） |
| auth+storage（3） | `StorageBackendProvisioner` `StorageConnectivityPort` `tenant.StorageEngineConfig` | 拍板 D-2 |
| initialization+storage（2） | `StorageBackendPort` `StorageBackendView` | 拍板 D-2 |
| auth+websearch（2） | `tenant.KnowledgeBaseProvisioner` `tenant.WebSearchConfig` | `tenant.WebSearchConfig` → `app/auth/api`（同级 api 已放行）；`KnowledgeBaseProvisioner` 同 D-2（开通链路） |
| datasource+knowledge（4） | `KnowledgeIngest`（纯函数工具）`KnowledgeParseStatus` `KnowledgeRowView` `KnowledgeTagView` | `KnowledgeIngest` 的 `contentHash`/`fileNameFromUrl` → **framework 工具包**（先例：SearchTextUtil 还乡）；其余 3 个 → 拍板 D-3（默认随 D-2 永驻 framework） |
| agentmanagement+knowledge（1） | `KnowledgeBaseCatalogPort` | 拍板 D-3 同组（默认永驻 framework；若批"同级 api 档"则进 `app/knowledge/api`） |
| embedchannel+im（1） | `MessageView` | 拍板 D-4（channel 合并） |
| embedchannel+knowledge（1） | `ChunkRowView` | 拍板 D-3 同组（默认永驻 framework） |
| agenttools+session（1） | `retrieval.SearchTarget` | **`app/knowledge/api`**（两个 L3 域依赖 knowledge(L2) 的 api 完全合法；SearchTarget 本就是知识检索目标形状，P4-5 时它是"必留 4 个"之一，现在两个 L3 消费方让它有了家） |

#### WP4-C `pipeline.PipelinePorts` 拆分（K11）✅ 2026-11-21

原 11 个嵌套接口 + 1 异常 + 3 record，按语义拆成 15 个独立类型（端口一律加 `Port` 后缀，避免与各域同名 service/repository 冲突）：

| 新类型（顶层） | 落点 | 实现方 |
|---|---|---|
| `ModelFactoryPort` | model-runtime `llm.api` | `QaWiring` 匿名实现 |
| `KnowledgeBaseQueryPort` | `app/knowledge/api` | `QaWiring` 匿名实现 |
| `KnowledgeDocumentQueryPort` | `app/knowledge/api` | `QaWiring` 匿名实现 |
| `ChunkQueryPort` | `app/knowledge/api` | `knowledge/adapter/KnowledgeChunkPipelineAdapter` |
| `KnowledgeBatchQueryPort` | `app/knowledge/api` | `QaWiring` 匿名实现 |
| `KnowledgeBaseBatchQueryPort` | `app/knowledge/api` | `QaWiring` 匿名实现 |
| `MemoryQueryPort` | `app/memory/api` | `memory/adapter/MemoryPipelineAdapter` |
| `WebSearchRunPort` | `app/websearch/api` | `websearch/adapter/WebSearchBackendsAdapter` |
| `TenantWebSearchConfigPort` | `app/auth/api` | `QaWiring` 匿名实现 |
| `DataAnalysisSessionFactory` | `app/agenttools/api` | `agenttools/datasource/DataAnalysisSessionFactoryAdapter` |
| `DataAnalysisSession` | `app/agenttools/api` | 同上（`SessionImpl`） |
| `KnowledgeData` / `ColumnInfo` / `TableSchema` | `app/agenttools/api` | —（record） |
| `PipelinePortException` | `framework/shared/error` | — |

`PipelinePorts` 与 `shared/pipeline` 包已删除；30 个消费文件改 import。**两个守卫口径同步调整**：`gen-port-map` 认得装配点的匿名 `new XxxPort() {}` 实现（新增「装配点实现（不参与内联判定）」档），ArchUnit A20 不再按顶层类过滤实现方——否则这 6 个端口会被误判「契约悬空」。

### WP5 framework 档 36 个（K10a/K10b/K12/K16 + `platform/storage`，5~8 天）

这 36 个被 framework 自身文件钉住，**先搬 framework 的业务文件，类型才能走**。引用关系（2026-11-21 v2 实测）：

| 钉子（framework 文件） | 钉住的 shared 类型 | 处置 |
|---|---|---|
| `platform/tracing/decorators/`（**6 文件**：LangfuseChatClient / Embedder / Reranker / Payloads / `Vlm` / `Wiring`） | `model` 15 个中的 13 | **K10a 乙**：framework 提供公开 `LangfuseTestSupport`（暴露 langfuse 5 个包私有类的测试面），decorators 6 文件 + 测试搬 `model-runtime` |
| `platform/stream/`（SyncChatStream/QueuedChatStream/StreamEvent/StreamStopWatcher） | `ChatStream` `ResponseType` `StreamResponse` `TokenUsage` | **K10b 拍板 D-1**。注意 P1a 实测判据：`StreamJson`（framework 自身 mapper，SSE 进 Redis）路径上的 `TokenUsage`/`ResponseType` 的 mixin **必须留在 framework**——选乙（只搬 3 文件）则这 2 个类型永驻 framework 正式包 |
| `platform/prompt/PromptFingerprints`、`MessageAttachmentsPrompt`、`PipelineConfig`、`ExtractPrompts` | `ChatMessage` `ChatOptions` `PipelineMessageAttachmentView` `GraphNode` `GraphRelation` | **落点已定死**（v1 的"逐个定"会让执行 Agent 自由发挥）：`PromptFingerprints` / `PipelineConfig`（含嵌套 `PromptTemplateStructured`）→ `model-runtime`（model-runtime 有真实 import，**不能去 app**，否则造成 model-runtime→app）；`ExtractPrompts` / `MessageAttachmentsPrompt` → `app`（framework 内无其他引用）；`GraphNode` / `GraphRelation` → **永驻 framework**——被 framework / model-runtime / retrieval-engine / app 四模块消费，而 retrieval-engine 与 model-runtime 都只能看到 framework，搬去任何别处都会造出非法模块边 |
| `ContractJsonMixins(Core)` + `ContractJsonModule` | `PromptCacheStatus`（显式 ser/deser） | **K12**：两个序列化器随 mixin 机制搬 `ModelJsonModule`（只追加） |
| `platform/web/ServletSseSink`、`RbacInterceptor` | `SseSink` `APIKeyScopeContext` `TenantAPIKeyScope` `TenantRole` | `TenantRole`/`TenantConfigLookup` → framework 正式包（10+ 域消费的全平台词汇）；`SseSink` → `app/session/api`（ServletSseSink 随 K16 搬 app） |
| `platform/knowledge/`（DocChunkSupport / FaqChunkMetadataCodec / ImageRefRewriter / ChunkSearchUtil / GrepChunkHit） | `ChunkFacts` `FaqChunkMetadata` `SearchAuth` `ChunkView` `KnowledgeView` `SearchTarget` | **留 framework**：这些是跨域共用的技术助手（agenttools/session/knowledge 都在用），搬 app 会破 R10 与模块图；被它们引用的 6 个载荷随之永驻 |
| `platform/audit/ActivityAuditRecorder` | `AuditWritePort` `AuditOutcome` | 搬 `app/audit` 或随 AuditAction 归 framework（与 WP4-B 同批拍板） |
| `platform/storage/**`（15 文件：`fileserve` 4 + `support` 4 + `config` 5） | `SeekableSource` `MessageFactsLookup` `OpenedFile` `FileUrlPort` `Mode` `Resolver` `StorageBackendResolver`（7）+ `SearchResult` `TenantConfigLookup` | **整包搬 `app/storage`**：`Rewriter`/`FileResolverFactory`/`StreamRewriter` 的消费方只在 app（11 文件），**retrieval-engine 零引用**——v1 的"Rewriter → retrieval-engine"是误判。`TenantConfigLookup` 例外 → framework 正式包；`SearchResult` **永驻 framework**——model-runtime（`Registry`/`SourceRegistry`）与 agent-engine（`AgentState`）都在用，两个引擎只能看到 framework |
| `platform/config/LanguageProperties` | `WikiLanguageSupport` | LanguageProperties 搬 `app/knowledge`（wiki 并入后） |

**完成后** `model` 15 个的归属：K10a+K10b 甲全做 → 全部随 `llm.api`；K10b 乙 → `ResponseType`/`StreamResponse`/`ChatStream`/`TokenUsage` 留 framework 正式包（正当，框架流基建形状）。

### WP6 收尾（2 天）

1. 剩余 shared = 35 通用面 + 拍板"永驻"的少量词汇 → 全部迁出：
   - 通用面 → `com.ragagent.error|event|json|text|pagination`（framework 正式包）
   - 永驻词汇 → framework 对应正式包（audit/tenant 面），**不放 shared**

   **通用面执行手法**（614 处引用 / 330 文件，手工改必错）：
   1. `git mv` 五个目录到 `backend/framework/src/main/java/com/ragagent/{error,event,json,text,pagination}`，批量改 `package` 声明（32 个 `package-info` 一并改）；
   2. 分两批改引用：先 `event`（80 文件 / 220 处），再 `error`（214 文件 / 329 处），`json|text|pagination` 跟第二批。单批命令形如
      `grep -rl 'com\.ragagent\.shared\.event' backend --include='*.java' | xargs sed -i '' 's|com\.ragagent\.shared\.event|com.ragagent.event|g'`；
   3. 每个受影响模块**分别** spotless（坑 5）；
   4. 校验顺序：`check-package-dirs`（目录/包一致）→ `check-reference-truth`（断链）→ `check-contract-purity`（C1 首次覆盖新路径）→ `check-fully-qualified-names`（批量替换可能制造内联 FQN）→ 全量 verify；
   5. 两批各一个 commit，出红时能二分。
2. 删除 `shared` 命名空间全部目录与 `package-info`。
3. 守卫平移（2026-11-21 逐条审计后的完整清单）：
   - **已修（本批，无需重做）**：`gen-port-map.py`（锚改全模块 `*Port` interface，端口表 0 → 52 条）、`gen-contract-consumers.py`（锚改全模块 `api` 包，123 类型 0 零消费者）、`ArchitectureRulesTest.isContractPort`（锚改 `api` 段）+ `BASE_TOP_LEVEL`（换成 error/json/text/pagination）；`check-domain-contracts.py` 同级 api 放行。
   - **必须改锚（不改就静默失覆盖）**：`check-contract-purity.py`（C1/C3 现扫 `framework/shared/**`，shared 没了会扫 0 个文件）、`check-package-cycles.py` 的 `L1_CORE`（含 `shared`，要换成 `error/json/text/pagination`）、`check-event-face-case.py`（写死 `shared/event/payload` 与 `shared/model/ResponseType`）、`check-platform-shapes.py`（`SHARED_SEGMENT="shared"` 豁免会消失，永驻 `platform.*` 的 `*View/*Port` 约 10 个要登记基线；`BUSINESS_PACKAGES` 里 `knowledge`/`settings` 搬空后成死规则）。
   - **会红、属健康红**：`check-contract-shape.py`（7 个类型字段 >20，理由直接复用现有 11 条失效基线）、`check-json-key-case.py`（冻结面按路径，`--write`）、`check-fully-qualified-names.py`（基线按文件路径 ⇒ 搬文件=新增且 `--write` 拒写，shared 侧 11 文件 36 处内联 FQN 必须在搬前清掉）、`check-package-info-fields.py`（新建 `session/api`、`im/api` 要四要素）、`check-reference-truth.py`（断链/旧路径）、`check-wiring-points.py`（`LangfuseWiring` 改分类 + overview.md 同步）、`check-package-size.py`（`knowledge/api` 进 26-30 告警带）。
   - **每批重生成**：`gen-arch-index.py`（packages.md）、`gen-module-map.py`、`gen-domain-index.py`、`gen-pipeline-map.py`（K11 拆完必变）、`gen-port-map.py`；四者在 `run-guards.sh` 里都是 `--check`。
   - **陈旧账目（顺手清）**：`check-doc-truth.py` / `check-hygiene-budget.py` / `check-wiring-points.py` / `check-tool-registry.py` / `check-stray-dirs.py` / `check-exception-semantics.py` 的 `MODULES`/`SOURCE_ROOTS`/白名单里还写着早已不存在的 `backend/contract`，统一换成 `_source_roots` 动态发现；`ArchitectureRulesTest`、`PackageRestructureBoundaryTest`（R-1）里 contract 条目与 `system`/`wiki` 陈旧域一并清。
4. 终态验收：`grep -r "com.ragagent.shared\|com.ragagent.contract" backend/*/src/main --include='*.java' | wc -l` = **0**。

## 5. 拍板项（开工前让用户确认，都有推荐默认）

| # | 决策 | 推荐 |
|---|---|---|
| D-1 | K10b stream：甲（17 文件全搬 model-runtime，model 面类型全部随走）还是乙（只搬 3 文件，`ResponseType` 等 4 个永驻 framework） | **乙**：收益/风险比最好；甲多 3~4 天且 app 60+ 引用点要动 |
| D-2 | storage 开通链路 5 个（auth/initialization↔storage 同级） | **永驻 framework 正式包**（`storage` 面开通词汇）：开通链路横切 auth/storage/initialization 三个 L1，合并任何两个都伤语义 |
| D-3 | datasource+knowledge 4 个 + 同级 3 个（CatalogPort/ChunkRowView） | **重新推荐：3 个形状类进 `knowledge/api`**。同级 api 已于 2026-11-21 批准，v1 的"备选"现在是零守卫成本，且比永驻 framework 更贴合语义（`datasource`/`agentmanagement` 都是 L2，同级 api 合法）。`KnowledgeIngest` 纯函数 → framework 工具包。永驻 framework 降为备选 |
| D-4 | embedchannel+im 的 `MessageView` | 永驻 framework（channel 合并是蓝图第一阶段既定项但属独立批次，不混入本计划） |
| D-5 | `ToolResult` 落点 | `agent-engine/api`（随 `tool` 族） |
| D-6 ✅ 2026-11-21 | **16 个 KB 形状**（7 个配置形状 + `KnowledgeCatalogPort` 及 7 个分面 + `KnowledgeBaseRowView`） | 消费方都是 knowledge + **initialization(L1)**，`initialization→knowledge` 是下级→上级，同级放行救不了。**推荐 ①：把 `initialization` 的 level 从 1 改成 3**（它是 `setup` 编排面，本就只调契约、不承载业务规则）。连带成本实测只有一处：`im→initialization.api.DocReaderParsePort`（2 个文件）会反过来变红——把 `DocReaderParsePort` 移到 `knowledge/api` 即可（im L2→L2 同级 api 合法，initialization L3→L2 合法）；`session(L3)→initialization(L3)` 同级 api 在新规则下合法。② 端口倒置不再可行：16 个形状里有 8 个是端口/视图类型，倒置会让 initialization 的端口签名反向引用 knowledge 的 api。③ 永驻 framework 会把 KB 业务词塞进底座（C8 红）。 |

## 6. 执行顺序与依赖

```
WP1 复核：0 删除（9 个闭包随 D-6 走）───────┐
WP2 单域（21）─────────────────────────────┤ 可并行，互不依赖
WP3 引擎（24）─────────────────────────────┤
WP5 framework 档（36，K10a/K10b/K16 + platform/storage 独立线）┘
WP4-A 唯一 home（26）   ← 建议 WP1 之后（死代码干扰闭包）
WP4-B 同级（18，其中 3 条已因同级放行转正） ← WP4-A 之后
WP4-C PipelinePorts（已拆，2026-11-21）     ← ✅
WP6 收尾                ← 全部完成 + 拍板项落定
```

回收表的 24 个不单独成批：storage 13 随 WP5 的 `platform/storage` 走、knowledge 4 随 WP2/WP4-A 走、7 个配置形状等 D-6 拍板。

每批一个独立 commit，message 惯例 `refactor(shared): WPx-N <内容>`。台账登记进本文件第 9 节。

## 7. 全局验收（WP6 完成时）

1. 主码无 `com.ragagent.shared`、无 `com.ragagent.contract`（grep = 0）。
2. `mvn -B clean verify`（根 `pom.xml`，`backend/pom.xml` 不存在） 绿，测试条数 ≥ 开工基线。
3. `bash scripts/run-guards.sh` 全绿（守卫条数以 `run` 行为准）。
4. `check-package-cycles.py` 包级环 0 新增。
5. 蓝图终态对齐：`backend/framework` = 技术底座 + 通用面（36）+ 拍板永驻词汇（~10±）；三引擎 `api` 齐备。
6. **17 域 `api/` 覆盖**：现状 8 个域没有 `api/`（agentmanagement / datasource / favorite / im / mcp / model / session / vectorstore），本计划只新建 session、im 两个，**余 6 个须另立批次**——不写清楚，本条终态在本计划收尾时无法成立。

## 8. 坑清单（全部实测翻过车，执行前逐条对照）

1. **mixin 不能乱搬**：framework 自身 mapper（`StreamJson`/`EventJson`/`LangfuseAttributes`）序列化对象图上的类型，其 mixin 必须留 framework（`TokenUsage`/`ResponseType` 实测红过）。判定：类型出现在这三个类的序列化路径上就留。
2. **provider 文件只追加不重建**：`ModelJsonModule` 被整文件覆盖导致 `ChatMessage` 线格式失效。搬 mixin 前先看目标模块是否已有 provider。
3. **闭包**：只要还有一个"留在原地"的引用者（含 shared 内同包简单名引用，FQN 匹配抓不到），类型就搬不走。搬前逐类型 `grep` + 看同包。**判死同理**：外部 import 为零 ≠ 死——本轮 WP1 的 9 个候选全部是 `extends` 分面或被同包调用，五路扫描（简单名 / 同包 / 测试 / 反射 / SPI）之后归零。
4. **新包必须 `package-info.java`**（D3 守卫红）。
5. **spotless 每个受影响模块分别 apply**。
6. **`@ConfigurationPropertiesScan` 精确包名单**（`app` 的启动类）：搬任何 `@ConfigurationProperties` 类先核对名单，移出即静默不绑定（无编译错无启动错，属性 null）。
7. **javadoc `-Werror`**：类移包后 `{@link}` 简单名断链，改全限定。
8. **按类全名登记的守卫白名单**（A7 裸 JDBC 等）：搬类名前先 `grep -rn <旧全名> scripts/`，同步改。
9. **`check-json-key-case.py` 冻结面按路径登记**：每批搬包后 `--write` 重登记（已在做的惯例）。
10. **`capability-kinds.json` 是域分级单一来源**：改级别/合并域都要同步它；R10 的 `level-ok` 档依赖它。
11. **null 兜底陷阱**：父类构造参数有 `x == null ? default : x` 时，协作者必须传**兜底后的字段**不是形参。
12. **包级环**：搬入目标模块前跑 `check-package-cycles.py`；契约要放"被依赖方"命名空间（`llm.api` 先例）。
13. **管道吃退出码**：验收命令不用管道；后台任务必须查 `PIPESTATUS` 或 `echo exit=$?`。
14. **JAVA_HOME**：`export JAVA_HOME=/opt/homebrew/opt/openjdk@21`（keg-only，不设必炸）。
15. **删死代码前扫 `src/test` + 反射 + SPI**（WP1 主码口径不含测试）。
16. **`mvn -pl <模块> -am compile` 先快速失败**，全量 verify 放批末。
17. **搬进 `*/api/` 即触发形状守卫**（shared 路径不在扫描面、api 在）：字段 ≤20、端口方法 ≤7。搬前用守卫口径预检（`importlib` 加载 `scripts/check-contract-shape.py` 调 `count_fields` / `count_port_methods`），超限者同 commit 登记 `contract-shape.baseline.json` 新路径。已知撞墙项见 §3.1。

## 9. 执行台账（执行 Agent 填写）

| 批 | 日期 | 内容 | 验收数字（测试/守卫/金片） | 备注 |
|---|---|---|---|---|
| 基座 | 2026-11-21 | 守卫修复：port-map / contract-consumers / A20 重锚；R10 同级 api 放行；本文件 v2 重扫 + 补 §4.0 唯一归属总表（153 校验通过）+ WP4-C 接口映射 + WP6 批量手法 | 定向守卫 5 条绿 + 探针 2 条（同级 api 过 / 同级 service 红） | 全量 verify 未跑（定向：ArchitectureRulesTest 22/22 绿、50 个 run 行→脚本自计 49 守卫全绿），等开工基线 |
| 清 shared（1） | 2026-11-21 | 5 个类型归位：`AuditAction`→audit/api、`KnowledgeIngest`/`KnowledgeBaseProvisioner`→knowledge·auth/api、`AgentTool`/`BaseTool`→agentengine/api | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 退回 4 个：`KnowledgeView`（被 SearchAuth 钉）、`StorageEngineConfig`（auth⇄storage 环）、`AgentTool`/`BaseTool` 首次因误加 import 失败 |
| 包根收敛 | 2026-11-21 | `platform→framework`（1,126 文件）；通用面并 `framework.{error,event,json,text,pagination}`；实现下沉 `eventbus`/`html`；`shared→framework.shared` 消环 | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | C1 改白名单扫描（107 文件）；12 处守卫/基线重键 |
| storage 收口 | 2026-11-21 | 11 个 IO/策略词汇 → `platform/storage/{fileserve,support}`；`shared/storage` 整包删除；两个名字登记 C8 基线 | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 结论：门面重构的必要性经实测取消（9/11 是 IO 载体） |
| storage 部分 | 2026-11-21 | 7 个类型 → `storage/api`；11 个经实测属 framework 闭包（§4.5） | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 整批搬迁撞 5 条 R10 已回退，改按闭包切开 |
| WP4-域面 | 2026-11-21 | 18 个类型归位：`memory/api` 4 · `im/api` 3 · `embedchannel/api` 3 · `audit/api` 2 · `auth/api` 6；`ActivityAuditRecorder` 迁 `audit/api` | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 回退：`MemoryKeys`/`MemoryKinds`/`MemoryConfig`（被 framework 永驻类型引用）、审计记录器初版落 `audit/support` 破 R10 |
| WP3-检索面 | 2026-11-21 | 6 个类型 → `retrieval-engine/api`（`GraphData` `KnowledgeDocumentFacts` `NameSpace` `RetrieveGraphRepository` `SearchParams` `WebSearchResult`） | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 发现：agent 整族被 `MessageView` 钉住，需等 channel 合并（D-4） |
| K10b-2 | 2026-11-21 | 模型面收尾：6 个类型 → `llm.api`（`shared/model` 清空删除）；`EmbeddingPort`/`ModelFacts` → `platform/model`（C8 登记）；`PromptCacheStatus` 线格式并入模型 JSON 模块 | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 坑：删序列化器时误删 `ContractJsonModule.Provider`，ServiceLoader 找不到 provider 导致 36 个测试静态初始化失败 |
| K10b-1 | 2026-11-21 | 模型面归位：12 个模型类型 → `llm.api`、`Embedder` → `platform.model`；`platform/stream` 17 文件 → `modelruntime.stream`；装饰器 6 文件 → `modelruntime.tracing`；`PromptFingerprints` → `modelruntime.prompt`；`TokenUsage`/`ResponseType` 线格式并入模型 JSON 模块 | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 坑：覆盖 model-runtime 既有 `META-INF/services` 导致线格式回退（已恢复并合并） |
| 知识簇 | 2026-11-21 | 40 个类型归位：39 → `knowledge/api/{kb,chunk,catalog,config,projection,error,wiki}`、`SearchChunkMerge` → `retrieval/api`；`JsonValueSupport` 内联；7 个类型永驻 framework（被 framework/引擎引用）；`platform/knowledge` 4 个工具留 framework | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 过程修正：K16 的 4 个工具文件搬 app 会破 R10，改留 framework |
| WP4-C | 2026-11-21 | 拆 `PipelinePorts`：11 接口 + 1 异常 + 3 record → 15 个顶层类型（端口加 `Port` 后缀）；30 个文件改 import；`shared/pipeline` 删除 | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 同步 `gen-port-map` 匿名实现识别、A20 实现方判定、两处 javadoc |
| A（D-6 ①） | 2026-11-21 | `initialization` level 1→3；`DocReaderParsePort` 移到 `knowledge/api`；`check-capability-kinds` 判据对齐同级 api（`src_lv >= tgt_lv`） | **5256 / 0 / 2** · 守卫 **49/49** · BUILD SUCCESS | 10 个文件改 import；`initialization/api` 清空删除；三份生成物重生成 |
| WP1 复核 | 2026-11-21 | 删除前五路扫描：9 个候选全是 shared 内闭包，**0 个可删**；计划 v3（dead 0 · multi 60 · D-6 扩到 16 个） | 无代码改动 | 坑 3 原样复现：FQN 口径看不见同包引用 |
| 基线 | 2026-11-21 | 全量 `mvn -B clean verify` + 49 守卫 | **5256 / 0 / 2**（framework 268 · model-runtime 286〔跳 1〕· retrieval-engine 321〔跳 1〕· agent-engine 157 · app 4224）· 守卫 **49/49** · BUILD SUCCESS 5:23 | 基线提交 `698437956` |
