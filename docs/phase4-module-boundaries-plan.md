# 阶段 4 盘点与方案：Gradle 多模块 + ArchUnit 边界固化（2026-10-08）

> **用途**：阶段 4（§7.3 第 3 条）的决策输入。含**实测数据**、目标模块图、**必须先解的跨包环（含具体边）**、
> 分档迁移顺序与每步闸门、ArchUnit 规则清单、风险与非目标。
> **复测命令**：`python3 scripts/pkg-audit.py`、`python3 scripts/check-package-cycles.py`、
> 本文件 §1 的依赖矩阵脚本口径（`import com.ragagent.<pkg>.` 计数）。

## 0. 一句话结论

**2026-10-08 收官：包图层级与分层纪律全部归零 —— 环 0 组（含间接环 SCC 0 组）、`L2 → L3` 直连 0 条、
L1 底座（`common`/`event`/`stream`/`tracing`）→ 业务域 0 条、依赖 `config` 的包 0 个、内联全限定名违规 0 处。
包图是 DAG，阶段 4（Gradle 多模块）的前置条件达成。**

三条主线各自的解法：

1. **间接环（SCC）**：SCC-A（7 域）由 B94/C8 一条 `audit → wiki` 边瓦解；SCC-B（4 域）由 C1
   （`StreamProperties` 搬入 `stream`）瓦解。**B111 发现守卫只解析 `import` 行 ⇒ 内联全限定名是盲区**，
   清掉 1,331 行后第一次看见真实的 2 组两两环与一组 8 域间接环；**B112 用最小反馈边集（2 条边 / 3 处）
   一次收口**（§4）。
2. **跨域直连边**：C2~C10 逐条端口化/下沉（§2），`wiki→knowledge` 43→0、`chatpipeline→knowledge` 17→0 等。
3. **分层违例 `L2 → L3`**：B106~B114 分七批清零（§2.4）。

ArchUnit 1.3.0 **已是测试依赖**（`server/build.gradle.kts:111`），可直接落规则 ⇒ 下一步是 M1
（拆 Gradle 多模块 + 模块间只许单向）。

## 1. 现状实测（2026-10-08）

### 1.1 规模与包清单

- 主源码 **1,862 文件 / 284,531 行**，顶层包 **30 个**（`RagAgentApplication` 在根）。
- 文件/行数 top8：`agent` 188/35.9k、`knowledge` 218/26.6k、`datasource` 129/27.7k、`wiki` 156/23.9k、
  `session` 111/22.0k、`retrieval` 92/21.6k、`im` 76/17.4k、`auth` 96/12.9k。
- 测试 **418 个 `*Test.java`**，按域分布（agent 58 / session 38 / datasource 38 / wiki 32 / retrieval 31 / mcp 27 / knowledge 27 / im 25 / llm 20 / storage 19 …）。

### 1.2 依赖方向（出度/入度）

| 视角 | 结果 |
|---|---|
| 入度最高（被依赖的底） | `common` **28** 包、`llm` 18、`auth` 14、`knowledge` 13、`retrieval`/`model` 7 |
| 出度最高（依赖别人的顶） | `session` **17** 包、`chatpipeline` 10、`config` 10、`agent` 9、`knowledge` 9 |
| 枢纽类（被导入次数） | `common.error.BizException` 140、`common.context.TenantContext` 120、`common.error.AppError` 75、`common.security.SsrfGuard` 71、`knowledge.domain.{KnowledgeBase,Knowledge,Chunk}` 63/58/58、`llm.domain.ChatMessage` 54 |

> 读法：`common` 在底、业务域在顶，方向总体健康；问题集中在**少数"跨域直连"边**（见 §2）。

### 1.3 环：既有守卫只覆盖"两两双向"，真实环是间接的

`scripts/check-package-cycles.py` 的 R1 只查 `A→B 且 B→A`，但按"包级强连通分量"（SCC）实测曾存在 **2 个间接环**（下表均已解）：

> ⚠️ **B111 修正**：下表"已解 / 包图 DAG"的结论建立在**只解析 import 行**的读数上。清掉 1,331 行内联全限定名后，依赖图第一次完整，实测 **R1 = 2 组（`agent⇄auth`、`agent⇄im`）、R1b = 1 组（8 域）**——这些边一直都在，只是此前不可见。详见 **§3**。
>
> ✅ **B112 已解**：整张图的最小反馈边集只有 **2 条边 / 3 处**（`auth → agent` 2 处、`agent → im` 1 处）——两处都是小改动，却同时消掉两组两两环与 8 域间接环 ⇒ **R1 = 0 组、R1b = 0 组，包图现为 DAG**。详见 **§4**。

| SCC | 成员 | 状态（2026-10-08 B91） |
|---|---|---|
| **SCC-A（7）** | ~~`audit`、`auth`、`knowledge`、`model`、`retrieval`、`storage`、`wiki`~~ | ✅ **已解**（B94/C8：把 `WikiActivityAudit` 端口由 `wiki.domain` 搬入 **`common.audit`** ⇒ `audit → wiki` 这条边消失，**整个 7 包环随之瓦解**——它在环中只依赖这一条出边；守卫 `间接环 1 → 0 组`，**包图现为 DAG**） |
| **SCC-B（4）** | ~~`config`、`im`、`session`、`stream`~~ | ✅ **已解**（C1：`StreamProperties` 由 `config` 搬入 `stream`；脚本已补 SCC 棘轮 `R1b` 防回归，探针验过） |

**这就是阶段 4 的真正前置**：模块化 = 把包图变成 DAG，间接环不解决，模块无法切。

### 1.4 装配与守卫现状

- `RagAgentApplication`：`@SpringBootApplication`（根包扫描）+ `@MapperScan("com.ragagent.**.mapper")`
  + `@ConfigurationPropertiesScan({…14 个包白名单…})`——**注释明确禁止改成根包扫描**（会绑定未注册的配置类，属行为变化）。
  ⇒ 模块化时：新增模块若含配置属性类，必须把包加进白名单（这一步是**逐模块搬家**，不能漏）。
- 既有守卫：`check-package-cycles.py`（环/分层棘轮）、`pkg-audit.py`（分包体检）、
  `check-json-key-case.py`（JSON 键名 camel 棘轮 260 条）、`check-go-anchors.py`（Go 锚点棘轮）、
  `check-fe-contract-keys.py`（前后端跨面键名），外加 ~25 个 `ab-*.sh` 验收脚本。
- 构建：**多模块**（B116 起 `settings.gradle.kts` include `domains` + `common`；共享内核 `common`/`event` 在 `:common`，其余在 `:domains`；B117 定名），产物 Spring Boot jar；
  其它目录 `docreader/`（Go 容器）、`frontend/`、`mcp-server/`、`otlp-proto/` 不在 Gradle 内。

## 2. 必须先解的环（切割清单，按性价比排序）

### 2.1 零风险（一行搬家）

| # | 边 | 处数 | 位置 | 修法 |
|---|---|---|---|---|
| ✅ C1 | `stream → config` | 1 | `stream/StreamManagerConfig` 用 `config.StreamProperties` | **已完成（B91）**：`StreamProperties` 搬入 `com.ragagent.stream`（扫描名单同步加 `com.ragagent.stream`）；守卫 `依赖 config 的包` 1→0，SCC 2→1 组 |

破环后 SCC-B 变成 `config → {im → session → stream}`（单向，装配层允许 →域），模块化可直接按 §3 落。

### 2.2 需要端口化（SCC-A 的 5 条大边）

| # | 边 | 处数 | 样例 | 修法（端口/门面化） |
|---|---|---|---|---|
| ✅ C2 | `wiki → knowledge` | **43 → 0** | `wiki/controller/WikiPageController → mapper.KnowledgeBaseMapper`、`WikiKbAccessGuard → domain.KnowledgeBase` | **已完成（B98/B99/B100）**：wiki 只依赖 `common.knowledge` 的七个类型（`ChunkPort`/`KnowledgeSpanPort`/`KnowledgeFinalizePort`/`EmbeddingModelPort`/`KnowledgeBaseLookup` + `ChunkView`/`KnowledgeBaseView`/`KnowledgeView`），实现全在 knowledge 侧适配器；守卫新增 **R4 解耦对棘轮**禁止回流。详见下方侦察表 |

**C2-b 侦察结论（B99，17 文件 / 36 处的能力分组——定端口签名的依据）**

| 能力组 | 站点（文件:方法） | 端口建议 |
|---|---|---|
| **KB 读** | `WikiIngestBatchHandler.getKnowledgeBaseByIDOnly`、`WikiPageServiceImpl:444` | ✅ 已有 `kbById`/`kbByIdIncludingDeleted` |
| **Knowledge 读** | `WikiIngestBatchHandler.getKnowledgeByIDOnly`、`WikiIngestContentSupport.isKnowledgeGone`、`DefaultWikiKnowledgeFinalizer`（`getParseStatus`/`getTitle`） | `knowledgeById`（视图：parseStatus/title）+ 已有 `knowledgeGone` |
| **Knowledge 写** | `DefaultWikiKnowledgeFinalizer:75,90`（`knowledgeMapper.update` ×2） | `updateKnowledge(KnowledgePatch)` |
| **Chunk 读写** | `WikiIngestBatchHandler.listTextChunksByKnowledgeID`、`WikiIngestCitePipeline:595`（`selectList`）、`DefaultWikiChunkCleaner:35`（`delete`）、`DefaultWikiImageEnricher`（`ChunkRepository`） | `textChunks(kbId, knowledgeId)` / `deleteChunks(...)` / `chunksByTypes(...)`（**Chunk 视图**是关键设计点：`Chunk` 实体目前被 7 个文件传递使用） |
| **Span 生命周期** | `WikiBatchSupport:278-339`（`latestAttempt`/`lookupStage`/`beginSubSpan`/`endSpan`/`failSpan`/`skipSpan`）、`WikiIngestMapPhase`/`WikiIngestReducePhase`/`WikiIngestRunSupport`/`WikiIngestFinalizePhase` | 句柄式 `SpanHandle`（begin/end/fail/skip）+ `latestAttempt`/`lookupStage` 查询 |
| **图片富化** | `DefaultWikiImageEnricher`（`ImageInfoEnricher` 三方法：`collectImageInfoByChunkIds`/`mergeImageInfoJson`/`enrichContentWithImageInfo`） | 三方法直搬（视图参数） |
| **嵌入** | `DefaultWikiModelResolver:73`（`EmbedderClient.configFrom`）、`EmbedderClient.embedBatch` | `embedConfig(modelId)` / `embedBatch(texts, config)` |
| **类型引用** | `WikiIngestTaxonomy`/`WikiIngestFinalizePhase`/`WikiIngestRunSupport`（仅 `KnowledgeBase` 字段） | 随 KB 读一并换视图 |

**C2 结果（B100，2026-10-08）：主源码引用 43 → 0** —— wiki 只依赖 `common.knowledge` 的七个类型（五个端口 + 两个视图），
脚本新增 **R4 解耦对棘轮**（`wiki → knowledge` 绝对禁止回流，红态探针验证过）。分三步落地：B98（C2-a 只读门面）、B99（C2-b 侦察 + KB 读再收口）、B100（C2-b 实施 + C2-c 收尾）。

> C2~C8 全部做完后，SCC-A 消失，包图成为 DAG，`audit`/`common`/`llm` 在底，业务域在顶。

### 2.3 common 纪律与减重（B101~B104）


| 项 | 状态 |
|---|---|
| 契约层 vs 实现 | `common.<domain>` 只放**跨域端口与载荷**（例：`common/knowledge` 18 文件 = 15 接口/record + 2 个零方法体 DTO + package-info，纪律写在包注释里："载荷只带消费方真正读取的字段，别把实体漏出去"） |
| **R5** 底座不得依赖业务域 | ✅ B101：`common`/`event`/`stream`/`tracing` → 业务域 = **0 条**（绝对禁止；红态探针验过，退出码 1） |
| **R6** common 实现痕迹棘轮 | ✅ B101：bean = `crypto`/`security`/`storage` 各 1、域持久层引用 0；按子包只许减不许增 |
| **`common/approval` 归位** | ✅ B102：1,758 行 / 24 文件搬到顶层 `com.ragagent.approval`（审批门 Gate + Redis pub/sub + 待审请求/决议）；出向依赖**只有 common** ⇒ 不引入环；`common` 11,183 → **9,374 行**（-16%） |
| **`common/settings` 归位** | ✅ B103：`ConversationProperties`/`SystemSettingGateway`/`SystemSettingRegistry` → 顶层 `com.ragagent.settings`（461 行；`@ConfigurationPropertiesScan` 条目随迁）；`MemoryConfig`/`MemoryKeys`/`MemoryKinds` **判定为跨域词汇** ⇒ 留 common（新建 `common/memory`，见下） |
| **B103 的关键教训** | 曾把 `Memory*` 一并搬进 `memory/domain` ⇒ **SCC 守卫立刻抓出 `auth ↔ memory` 新环**（新边 `auth/controller/TenantConfigOps → memory.domain.MemoryConfig`；旧边 `memory/service/MemoryService → auth.service.TenantService`）⇒ 判定它们是 **auth 租户配置 + memory 域 + datasource 共享的词汇**，回退到 `common/memory/`（新包 + package-info 写明原因） |
| 剩余减重候选与判定 | `common/security`（1,107 行 / 24 消费方）与 `common/web`（1,135 / 26 消费方）= **横切基础设施**（非域）⇒ 应留；`common/tenant`（1,371，**是域**）但被 `common/web/RbacInterceptor → TenantRole/TenantProperties` 反向使用 ⇒ 搬前须先解（把 RBAC 词汇留 common，或把 `RbacInterceptor` 移出 common/web）|
| **`common/tenant` 拆分（方案 a）** | ✅ B104：租户域实现（`Tenant` 实体 + `TenantMapper` + 6 个租户配置 VO + `TenantConfigRedaction`）→ 顶层 `com.ragagent.tenant`；**`TenantRole`（13 包用）/`TenantProperties`/`TenantConfigLookup`/`WebSearchConfig` 留 common**；`TenantConfigLookup.tenantById`（返回实体）拆成域侧端口 `tenant.TenantLookup`（仅 L3 用：storage 7 处 / knowledge 1 处）⇒ `retrieval`（L2）只见 L1 安全的 JsonNode/视图方法 |
| **R6 追加第三条** | ✅ B104：`common/**/mapper/**`、`common/**/repository/**` **不得存在**（实体+mapper 归域）；探针（common 下建 mapper 子包）验证会红 |
| **R7** `package` 声明↔路径一致 | ✅ B105：`package X;` 必须等于路径推导包名（main/test 都查）——**javac 与 spotless 的盲区**（按源集全量编译不看目录），只有 IDE 抓；探针（声明 `com.ragagent.wrong`）验证会红 |
| common 体量轨迹 | 11,183（B101 前）→ 9,374（B102 approval）→ 8,175（B103 settings + memory 词汇）→ **7,812 行 / 114 文件**（B104 tenant 拆分），四批共 **-30%** |

### 2.4 L2 → L3 清零（R3：能力层不得依赖业务域）

| 边 | 状态 | 处置 |
|---|---|---|
| `chatpipeline → websearch` | ✅ B106（1 处） | 端口签名改收 **L1 配置**（`common.tenant.WebSearchConfig`）；执行面配置的转换搬进域侧 `WebSearchService.WebSearchConfig.from(...)`，适配仍在 `session/QaWiring` |
| `chatpipeline → memory` | ✅ B106（5 处） | `MemoryRecall`/`MemoryRetrievalContext` 下沉 `common.memory`（条目改 `MemoryItemView` ✓ 实体不越层）；`MemoryText.mergeUsedMemories` 的通用去重下沉 `common.text.ListMerges`（记忆侧保留薄委托）；`QaWiring` 变成纯委托 |
| `retrieval → vectorstore` | ✅ B107（14 处 + 1 处全限定遗漏） | **性质判定**：`vectorstore` 有 `domain`(7)/`mapper`/`controller`/`service` ⇒ 是**业务域**（驱动在 `retrieval/engine/*`），不能靠"重分类"绕过 ⇒ 处置：① **值对象下沉**——`IndexConfig`/`ConnectionConfig`（公开字段 JSON DTO，域与 L2 共用）→ `common.vectorstore`；② **实体/mapper 端口化**——`VectorStoreView`（id/tenantId/name/engineType + 两个配置）+ `VectorStoreLookup.byId(tenantId, storeId)`，实现留 `vectorstore/service/VectorStoreLookupAdapter`（直接委托 `VectorStoreRepository#getByID`）；③ 纯谓词也下沉——`EnvStoreIds.isEnvStoreId`（`__env_` 前缀，`vectorstore.domain.EnvVectorStores` 保留同名委托）；④ 装配层 `config/RetrievalEngineWiringConfig` 改注入端口；测试替身 `FakeStoreRepo` 从"实现整个 mapper"收窄为"实现 1 个方法的端口"（净删 4 个多余重写）|
| `chatpipeline → knowledge` | ✅ **B114（17 → 0）** | **B110 清掉 5 处**：① **元数据载荷下沉**——`FaqChunkMetadata`/`DocumentChunkMetadata`/`GeneratedQuestion`（三者的 `com.ragagent.*` import 实测为 0，纯 JSON 载荷）→ `common.knowledge`；② **算法下沉**——`SearchChunkMerge`（187 行，`mergeTextChunks` 只读 `getStartAt/getChunkIndex/getContent/getEndAt`，**正好落在 6 字段 `ChunkView` 里**）→ `common.retrieval` 并改收 `ChunkView`（`KnowledgeSummaryService` 用 `ChunkPortAdapter.viewAll` 投影，投影方法转 public 作域内公用）。**剩余 12 处**：9 处**实体**（`Chunk`×3/`Knowledge`×3/`KnowledgeBase`×3）+ 3 处 `ImageInfoEnricher`（用的是 `collectImageInfoByChunkIds`/`enrichContentWithImageInfoForChat`/`clearImageInfoTextMatchingBody` **三个不同的静态方法**，其中 collector 的形参是 `BiFunction<…, List<Chunk>>` ⇒ 要么给 `ChunkView` 补回 `imageInfo` 字段、要么按 C2 的做法扩端口；三个方法本体是纯文本函数，可单独下沉）|
| `webfetch → datasource` | ✅ **B113（2 处）** | `webfetch/AgentMarkdown` 静态复用 `datasource.connector.rss` 的 `HtmlToMarkdown`/`JdkHtmlToMarkdown`（此前整条边在图里不存在）⇒ 候选修法：**B113 已解**：`HtmlToMarkdown`（接缝，32 行）+ `JdkHtmlToMarkdown`（有界实现，544 行）+ `HtmlConversionException` + `HtmlEntities` **四个类实测 `com.ragagent.*` import 全为 0**（纯 HTML 工具）⇒ 一并下沉 **`common/web`**（与既有 `HtmlText` 同族）；`RssConnector`/`JdkXmlFeedParser`（留在 datasource）与 `webfetch/AgentMarkdown` 两侧补 import；`HtmlEntities` 由包级可见改 **public**（跨包访问需要）；测试 `JdkHtmlToMarkdownTest` 随包搬入 `test/…/common/web/`（R7 一致），其空白归一等价改用 L1 `common.text.Whitespace.trimSpace`（18 条语料测试全绿，证明等价） |

## 3. B111 发现：内联全限定名是依赖图的盲区（已上 R8 守卫）

### 3.1 问题

`check-package-cycles.py` 的 R1/R1b/R3/R3b **只解析 `import` 行**
（`^import com\.ragagent\.(\w+)\.`）。代码里写成 `com.ragagent.x.y.Z` 的内联全限定名
**不产生 import 行**，于是：编译期/字节码层面的依赖**真实存在**，但守卫的图里**没有这条边**
⇒ "环 0 组 / L2→L3 1 条" 一直是**代理指标**的读数。

B107 已踩过一次局部（"14 处 + 1 处全限定遗漏"），B111 把它系统性清算。

### 3.2 清算结果（2026-10-08 B111）

| 项 | 处数 |
|---|---|
| 清点（改前，含内联 FQ 的行） | **1,331 行 / 312 文件**（主源码 842/186、测试 489/126） |
| 转为 `import` + 简单名 | **1,343 处**（主源码 838 + 测试 505） |
| 保留：必要消歧 | **49 处**（主 31 + 测试 18） |
| 保留：不可解析（非类型引用） | 2 处 |

转换**只替换类型前缀**、保留 `.MEMBER` 尾巴——`case ToolDefinitions.TOOL_KNOWLEDGE_SEARCH ->`
（枚举常量）与 `ContractJson.semantic`（静态调用）两类均抽样验证；全量编译 0 错误、
后端全量测试绿。

**转换后守卫第一次说出真实读数**（对照旧基线，全部是**暴露**而非新增）：

| 指标 | 转换前（盲读数） | 转换后（真读数） | 来源 |
|---|---|---|---|
| R1 两两环 | 0 组 | **2 组** | `agent⇄auth`（各 1 文件 2 处）、`agent⇄im`（1 处 + 14 处 / 9 文件） |
| R1b 间接环 | 0 组 | **1 组（8 域）** | `{agent,auth,chatpipeline,datasource,im,memory,session,webfetch}` |
| R3 L2→L3 直连 | 1 条 | **2 条** | 新增 `webfetch→datasource`（2 处）；`chatpipeline→knowledge` 处数 12 → **15** |

基线已按真读数重刷（脚本 docstring 记明"不是新增违例，而是原先看不见"）。

### 3.3 新增守卫 R8（禁内联全限定名）

规则：代码内（非注释 / 非字符串 / 非 `package`·`import` 声明行）不得出现 `com.ragagent.*` 内联引用；
**唯一例外是"必要消歧"**——简单名在本文件作用域内已被占用（已导入其它包的类型 / 本文件已声明 /
同包有顶层同名类型）。此时内联 FQ 不产生隐形依赖（依赖已被其它途径表达），且换成 import 根本无法编译。

- 当前读数：**必要消歧 31 处（允许）/ 违规 0 处**
- 红态探针：往 `agent/AgentConsts.java` 注入 `com.ragagent.event.EventIds.class.hashCode()`
  → 报 `违规 1 处` 并置守卫为失败 ✓（探针已删）
- **真正价值**：内联 FQ 归零后，`import` 成为唯一的跨域引用方式 ⇒ R1/R1b/R3/R3b
  从"代理测量"升级为"真测量"，图再无旁路。

### 3.4 顺延与修正

- 原计划的 **B111（L2→L3 剩余清零）顺延为 B113**（B112 先插入了 C9/C10 解环），目标数由 **12 修正为 15 处**（那 3 处本来就是依赖，只是此前不计数）。
- **新增切割项 C9/C10**（两两环，规模都不大）：

| # | 边 | 处数 | 站点 | 修法建议 |
|---|---|---|---|---|
| ✅ C9 | `agent → auth` | 2（1 文件） | `agent/management/service/CustomAgentService` | **B112 保留**：拓扑序里 `agent < auth`，这条边合法 ⇒ 不动 |
| ✅ C9b | `auth → agent` | 2（1 文件） | `auth/controller/TenantConfigOps`（`PromptTemplateCatalog.toJson`/`load`、`BuiltinAgentRegistry.localeFromRequest`） | **B112 已解**：`PromptTemplateCatalog` → `common.prompt`（实测域依赖 = 0，纯 classpath 装载器）；`localeFromRequest` → `common.wiki.WikiLanguageSupport`（agent 侧留薄委托，9 处调用点零改写） |
| ✅ C10 | `agent → im` | 1（1 文件） | `agent/management/service/CustomAgentService`（与 C9 同文件） | **B112 已解**：新增端口 `common.agent.AgentChannelCleaner`（`ImService implements` 之，签名逐字一致）；consumers 侧 `ObjectProvider` 由 `ImService` 改为端口，破 Spring 级环语义不变 |
| ✅ C10b | `im → agent` | 14（9 文件） | `im/domain/*Entity`、`im/runtime/*`、`im/service/*` | **B112 保留**：拓扑序里 `im < agent`（im 在底、agent 在上），合法 ⇒ 不动 |

> 注：本节编号属**包图守卫脚本**的编号空间（R1~R8），与 `ArchitectureRulesTest`
> 的代码级规则编号（R6~R10）相互独立。


## 4. B112：解环收官——包图成为 DAG

### 4.1 反直觉的结论：最小反馈边集只有 3 处

B111 暴露的 8 域间接环看着吓人（`agent`/`auth`/`chatpipeline`/`datasource`/`im`/`memory`/
`session`/`webfetch`），子图内部 **15 条边**。但按"删边条数最少"求最小反馈边集
（8 节点暴力枚举全排列拓扑序）后：

| 割边 | 处数 | 站点 |
|---|---|---|
| ✂ `auth → agent` | 2 | `auth/controller/TenantConfigOps` |
| ✂ `agent → im` | 1 | `agent/management/service/CustomAgentService` |

删这两条边后拓扑序 `im < session < agent < chatpipeline < memory < webfetch < datasource < auth`
成立 ⇒ **整个 8 域 SCC 瓦解，且两组两两环同时消失**。

> 方法论：环的"体积"要看**割**而不是看**成员数**。8 域看着比之前解掉的 7 域环（SCC-A）大，
> 但割集只有 3 处——SCC-A 当年也是靠 1 条 `audit → wiki` 边瓦解的。

### 4.2 两处改动

**C9b `auth → agent`（2 处）**

| 符号 | 处置 | 依据 |
|---|---|---|
| `agent.PromptTemplateCatalog`（233 行） | → **`common.prompt`** | `com.ragagent.*` import **实测为 0**（纯 classpath YAML 装载器 + Jackson 序列化），且**只被 auth 消费**（agent 域自己不用）⇒ 放在 agent 域纯属历史摆放 |
| `BuiltinAgentRegistry.localeFromRequest` | → **`common.wiki.WikiLanguageSupport.localeFromRequest`** | "env + Accept-Language → locale" 纯解析，与既有 `envLanguage()`/`FALLBACK_LANGUAGE` 同一语义族；agent 侧留**薄委托** ⇒ `AgentController` 的 9 处调用点零改写 |

**C10 `agent → im`（1 处）**

新增端口 `common/agent/AgentChannelCleaner`（单方法 `deleteChannelsByAgent(agentId, tenantId)`）：
- `im` 侧：`ImService implements AgentChannelCleaner`（方法签名逐字一致，零改写）
- `agent` 侧：`CustomAgentService` 的 `ObjectProvider<ImService>` → `ObjectProvider<AgentChannelCleaner>`
  ——**保留原有的"破 Spring 级构造环 + 容器缺实现时静默跳过"语义**（`ImService` 的字段反向依赖
  `CustomAgentService`）

> 两处都**不动**合法边：`agent → auth`（拓扑序里 agent 在前）与 `im → agent`（im 在底）。

### 4.3 结果

| 指标 | B111 后 | B112 后 |
|---|---|---|
| R1 两两环 | 2 组 | **0 组** |
| R1b 间接环（SCC） | 1 组（8 域） | **0 组** |
| 依赖 `config` 的包 | 0 | 0 |
| L1 底座 → 业务域 | 0 | 0 |
| L2 → L3 直连 | 2 条 | 2 条（B113 处理） |

**⇒ 全仓包图层级成为 DAG，阶段 4（Gradle 多模块）的前置条件达成。**

- 红态探针：往 `agent` 注入 `import com.ragagent.im.service.ImService` →
  报 `新增环：1 agent⇄im` + `间接环新增成员 ['agent','im','session']` ✓（探针已删）
- 基线已归零（`两两环 0 组 / 间接环 0 组`）⇒ 之后任何回流都会立即变红
- 顺延：原 B112 计划（L2→L3 剩余清零）→ **B113**

**B114 落地记录（chatpipeline → knowledge 归零，实体不再越层）**

| 消费面 | 原载荷 | 换成 | 依据 |
|---|---|---|---|
| `KnowledgeBaseService.getKnowledgeBaseByIdOnly` / `getKnowledgeBasesByIdsOnly`、`KnowledgeBaseRepository.getKnowledgeBaseByIDs` | `knowledge.domain.KnowledgeBase` | **`KnowledgeBaseView`**（+3 字段：`vectorEnabled`/`keywordEnabled`/`extractConfig`） | 视图是 getter 风格 ⇒ 消费点零改写；`IndexingStrategy` 拆成扁平布尔与既有 `wikiEnabled` 同款 |
| `KnowledgeService.getKnowledgeById` / `getKnowledgeBatch` / `getKnowledgeBatchWithSharedAccess`、`KnowledgeRepository.getKnowledgeBatch` | `knowledge.domain.Knowledge` | **`KnowledgeDocumentFacts`**（+3 字段：`tenantId`/`fileType`/`filePath`） | 复用 retrieval 已在用的 facts 记录（"需要更多字段时先改这里"） |
| `ChunkRepository.listChunksById` / `listChunksByParentIds` | `knowledge.domain.Chunk` | **`ChunkFacts`**（+1 字段：`imageInfo`） | 同上 |

- **语义保持**：`QaWiring` 的适配器仍走 `kbService.getAllTenantById(...)`（含 `ensureDefaults`
  的"索引策略零值 → vector+keyword 默认"回填），**投影在回填之后取值** ⇒ 检索行为不变；
  每个方法各自的租户/软删语义也原样保留（投影只是最后一步换形状）。
- **投影统一**：`ChunkPortAdapter.factsOf/factsAll`（新增）、`KnowledgeService.factsOf`（新增，含批量重载）、
  `KnowledgeBaseLookupAdapter.view`（B114 转 public）——域内一份映射，装配层复用，避免漂移。
- **`ImageInfoEnricher`（575 行）迁 `knowledge.support` → `retrieval.support`**：B114 把它唯一还需要
  实体的入口（`collectImageInfoByChunkIds` 的 lister 回调）改成收 `ChunkFacts` 之后，本类
  **零 knowledge 依赖**、只依赖 `retrieval.*` ⇒ 与依赖同处；且 `retrieval → chatpipeline` 不存在
  ⇒ 只新增单向边 `chatpipeline → retrieval`（L2→L2，合法）。顺带修掉一处"知识域放检索工具"的错位。
- **测试侧**：四个替身（`StubKnowledgeService`/`StubKnowledgeRepo`/`StubChunkRepo`/`StubKBService`）
  **内部保留实体、端口方法投影**——镜像生产侧 `QaWiring` 的做法 ⇒ 测试夹具零改写、且更真实。

> ⚠️ **迁移教训（值得记下）**：`ChunkFacts`/`KnowledgeDocumentFacts` 是 **record**（访问器 `content()`
> 而非 `getContent()`），而消费方是 getter 风格 ⇒ 本次有 **124 处访问器改名**；另外
> `chunk` 这类名字在**不同作用域**分别指 `Chunk` 与 `SearchResult` ⇒ 全局改名会误伤，
> 必须按作用域（声明类型唯一性）过滤，剩下的用编译器错误行清单精确改写。
> 将来若还要给 getter 风格的消费方换 facts，优先考虑让 facts 带 getter 别名或新开 getter 风格载荷。


## 5. M1 可行性侦察（2026-10-08 B115，仅侦察，未动构建）

方法：A 线清点构建/装配/资源面（子代理），B 线算带权包图 + 拓扑分层 + 最优切分。

### 5.1 三条结论

**① 因为包图已是 DAG，拆模块"零业务代码改动"。**
拆模块 = 搬文件 + 每个模块写一行 `project()` 依赖；**`import` 语句全部不动**
（B112 解环 + B114 清分层带来的直接红利）。

**② 但"按业务域拆"不可行 —— 中部没有便宜缝。**
把"最优切分"形式化了：DAG 拓扑序里按**连续块**切分保证模块间无环，于是模块划分＝
找代价最小的切割点。实测：

| 口径 | 结果 |
|---|---|
| 按引用处数 | 每条边界 ≈1800 处，**几乎恒定**（被 `→ common` 底噪吞掉） |
| 按跨界的**不同类型数** | 中部边界 **240~416 类型**，只有两端便宜 |
| 最优切分的形态 | **退化**：只能摘出 `common`/`approval`/`audit`/`config` 等小域，剩下 ~272k LOC 长在一起 |

⇒ 15 个业务域若各自成模块，每个切点要暴露 178~268 个类型——**它们不是模块，它们就是"应用"**。

**③ 真正的缝在"架构层"，不在"业务域"**（与守卫的 L1/L2/L3 同构）：

| 模块 | 成员域 | 文件 | LOC | 对外暴露类型 |
|---|---|---|---|---|
| **`:common`** | `common`、`event` | 175 | 13.8k | **0**（自身零依赖 ⇒ build 文件无任何 `project()`）|
| **`:engine`** | `llm`、`retrieval`、`embedding`、`rerank`、`chatpipeline`、`modelcontext`、`webfetch`、`stream`、`tracing`、`model`、`vectorstore` | 368 | 58.3k | 79 → contracts |
| **`:app`** | `agent`、`auth`、`knowledge`、`session`、`wiki`、`im`、`mcp`、`memory`、`storage`、`system`、`tenant`、`websearch`、`settings`、`audit`、`approval` | 1151 | 177.9k | 102 → engine、129 → contracts |
| **`:datasource`** | `datasource`（**入度 0**，无人依赖） | 125 | 26.9k | 2 → engine、20 → app |
| **`:misc`** | `embed`、`initialization`、`evaluation`、`favorite` | 56 | 7.7k | 25 → engine、41 → app |
| **`:boot`** | `config` + `RagAgentApplication` + `application.yml` + `spring.factories` | 12 | 1.7k | 25 → engine、35 → app |

已核验：**无下层→上层引用，模块图为 DAG** ✓；`:engine → :common` 仅 **79 类型**
（非常干净，是 R3=0 的直接红利）。

### 5.2 风险清单

1. **装配面绑定单根包**：`@SpringBootApplication`（无 `scanBasePackages`）+ `@MapperScan("com.ragagent.**.mapper")`
   + `@ConfigurationPropertiesScan` **15 个包白名单** + `spring.factories` 的 EPP
   ⇒ 包一动，入口类与 ArchUnit R2/R3/R11 守卫必须同步。
2. **13 处 classpath 资源是"静默 null"**（`if (in == null) return/continue`）：`agent/management/**`、
   `initialization/**`、`/jieba/**`、`common/text/**`、`/dataset/**`。已逐个核实**读法都是 classloader
   绝对路径 ✓**（不会因搬家解析失败），但**缺资源不报错** ⇒ 必须补一条"资源存在性断言测试"
   ——✅ **B118 已补**（见 §7）。
3. **路径硬编码**：proto srcDir 来自 `../docreader` + `$rootDir/otlp-proto`；迁移来自
   `$rootDir/migrations/versioned` 且运行时读 `filesystem:./build/generated-migrations`
   （绑定模块目录 + 工作目录）；**5 个守卫脚本 + `GoldenContract` 都硬编码 `server/src/...`**。
4. **spotless `ratchetFrom("seed")`**：大规模搬家会让 ratchet 判定面骤增 ⇒ CI 可能出现
   "无关文件被格式化"的巨大 diff（需一次性 `spotlessApply` 或重置 ratchet 基线）。
5. **测试归属（设计要点）**：419 测试文件 / ~109 个 `@SpringBootTest`。给 `:datasource` 写测试会形成
   项目环（`:datasource` 依赖 `:app`）⇒ 解法：**单测随域搬、集成测试集中到 `:boot`**（boot 在 DAG 顶端，
   能看见所有模块 ✓），共享基座（`TestSchema`、`GoldenContract`）改 `java-test-fixtures`。

### 5.3 价值判断与推进策略

| | 现守卫（脚本 + R8） | Gradle 多模块 |
|---|---|---|
| 边界强制力 | 脚本级（可被改白名单绕过） | **编译器级，绕不过** |
| 已有覆盖 | R1/R1b/R3/R5/R6/R7/R8 已覆盖同一批规则 | 同规则硬化 |
| 一次成本 | — | 搬 1,887 + 419 文件、6 个 build 文件、6 处守卫脚本改造、资源/装配/proto/迁移路径模块化 |
| 增量收益 | — | 主要来自 **`:common` 与 `:engine` 两条硬边界**（`:app` 内部 15 域仍是软的，因为中部无缝） |

⇒ **不做一次性 6 模块拆分。第一步只做 `:common`**：它是**零成本切点**
（自身零依赖、175 文件；界外→界内的 1,409 处引用全部只变成一行 `project(":common")`），
却能把"底座不得依赖任何域"从**脚本规则升格为编译规则**——整个 M1 里性价比最高的一刀。


## 6. B116：`:common` 抽取落地（M1 第一步）

按 §5.3 的策略只做**零成本切点**：把 `common` + `event`（175 文件 / LOC 13.8k）抽成
**唯一一个没有任何 `project(...)` 依赖的模块**，把"底座不得反向依赖任何域"从守卫脚本规则
**升格为编译规则**。

### 6.1 落地内容

| 项 | 内容 |
|---|---|
| 模块 | 新增 `:common`（`java-library` + `io.spring.dependency-management` + spotless）|
| 依赖 | `:common` **零 project 依赖**；`:domains` 声明 `implementation(project(":common"))` |
| 依赖声明 | 按 `common`/`event` 的**实际 import 面**声明（jackson / spring-context·web·webmvc·jdbc / spring-boot·autoconfigure / spring-data-redis / slf4j / mybatis-plus 3.5.7 / jakarta servlet·validation），**不用 starter**，避免把自动配置漏进库 |
| 搬迁 | `git mv` `common/`、`event/`（主源码）+ `resources/common/text/*.txt`（被 `/common/text/…` 绝对路径读）+ **9 个纯底座测试**（2 个 `@SpringBootTest` 与引用其它域的 5 个留在 `:domains`）|
| 资源 | 13 处 classpath 资源读法**逐个核实全是 classloader 绝对路径** ✓（不会因搬家解析失败）；**缺资源静默 null** 已由 ✅ **B118** 的 R12a/R12b 断言兜住（见 §7）|

### 6.2 守卫与规则的多模块化（否则会**静默失覆盖**）

新增 `scripts/_source_roots.py` 作为**源码根的单一事实来源**（`MODULE_DIRS` 一行加模块），5 个守卫全部改为按它遍历：

- `check-package-cycles.py`：R1/R1b/R3/R3b/R5/R6/R7/R8 全部改为跨模块（域集合合并、`common` 可能在任一模块）
- `check-json-key-case.py` / `check-go-anchors.py` / `check-fe-contract-keys.py` / `check-event-face-case.py`：路径常量 → 多模块遍历

> ⚠️ **拆模块时最容易踩的静默坑（实测）**：守卫仍扫 `server/...` 时，`R6 bean 3 → 0`、
> `R8 31 → 28`、`R5` 变成**空检查**——全绿但少覆盖 175 个文件。改造后 R6/R8 数字复原，
> 并做红态探针验证（往 `contracts/common` 注入 `knowledge.domain.Chunk` ⇒ R5 报 `common→knowledge` 且退出码 1）。

### 6.3 两个 ArchUnit 坑（都实测踩到）

1. **`ArchitectureRulesTest.MAIN` 的导入过滤器必须用 `Location.contains`，不能用 `location.asURI()`**：
   ArchUnit 对 **jar 内的类**求 `asURI` 会抛异常，而"抛异常的导入选项"被当作**排除** ⇒
   `:common`（在 `:domains` 类路径上以 **jar 形态**出现）被整段排除，R7 基线条目随即报
   "已不再违例"。探针四变体定位：`asURI` 版命中 0 / `Location.contains` 版命中 1。
   过滤器改为 `!location.contains("/classes/java/test/")`。
2. **源码遍历类规则**（R5 裸 NUL、R9 `.last` 拼接）原先固定 `Path.of("src/main/java")`，
   拆模块后只覆盖 `:domains` ⇒ 改为 `backendSourceRoots(...)`（同时遍历 `src/…` 与
   `../contracts/src/…`，不存在的根跳过）。

### 6.4 结果

| 指标 | 值 |
|---|---|
| 模块数 | 2（`:common` 零 project 依赖 / `:domains` → `:common`）|
| 编译期硬约束 | ✅ **探针验证**：往 `:common` 注入 `import com.ragagent.knowledge.domain.Chunk` ⇒ `:common:compileJava` **FAILED**（`package com.ragagent.knowledge does not exist`）——底座反向依赖从此**改不动** |
| 闸门 | `./gradlew spotlessCheck build` = **BUILD SUCCESSFUL**（4,778 测试）+ 五守卫绿 |

**✅ 已完成（B161，2026-10-09）**：`:engine`（L2 能力层，11 域 / 58.3k LOC，对 `:common` 只暴露 79 类型）
——它是第二条硬边界；再做则是 `:app` / `:datasource` / `:misc` / `:boot`（§5.1-③）。


### 6.5 B117：模块定名 `:common`（原 `:contracts`）

抽取当天即改名，理由是**本仓 `contracts` 一词已被占用**，不是风格偏好：

| 既有用法 | 规模 |
|---|---|
| `domains/src/test/resources/contracts/**`（golden HTTP 契约夹具） | **1,426 个文件** |
| 读它的测试类 | 18 个 |
| 类名含 `Contract` 的测试类 | **49 个** |

⇒ 在这仓里看到 "contracts" 的第一反应是"契约测试夹具"，模块再叫 `contracts` 是语义撞车。
另外两条支持：② **名实相符** —— 模块 27 个子包里既有端口/视图/facts，也有实现与 bean
（`CryptoService`/`SsrfGuard`/`StorageAllowList`/`HealthController`/`GlobalExceptionHandler`/`RbacInterceptor`
+ 6 个 `*Properties` + mybatis handler），是 **shared kernel** 而非纯契约层；
③ **与顶层包 1:1** —— 135/175 文件在 `com.ragagent.common`，模块名＝顶层包名最常规（且包名不动 ⇒ import 零改动）。

**评估过的其它候选**（结论：`:common` > `:kernel` ≫ `:core` / `:foundation`）：

| 名称 | 判定 |
|---|---|
| `:common` | ✅ 采用：与包名一致、Java 最常规、仓库已用该词（包名 + 守卫 `L1` 集合 + R6 基线键）|
| `:kernel` | ◐ 次选：语义最准（含 `event` 基底也说得通）且名字自带"最小内核"约束；但 Java 里不如 common 常见、且与包名不一致（路径读作 `kernel/…/com/ragagent/common/…`），本仓此前无此词汇 |
| `:core` | ❌ 误导：DDD 里 `core` = 核心**业务**领域，而本模块零业务逻辑（业务在 knowledge/session/agent…）|
| `:foundation` | ❌ Java 里几乎不用（多见于 JS/Android），语感偏"平台层"，且名字长 |

> 因名字不再承担约束，**门槛改写在 `common/build.gradle.kts` 头部注释里**：
> "这里只应有跨域词汇 / 不可变载荷 / 端口契约；带 `@Component`/`@Service` 的实现应各归其域
> —— 现存 3 个 bean 是 R6 棘轮基线，只许减不许增。"

**改名改动面（7 处，一次全闸门通过）**：`git mv contracts common`；`settings.gradle.kts`；
`server/build.gradle.kts` 的 `project(":common")`；`scripts/_source_roots.py` 的 `MODULE_DIRS`；
`ArchitectureRulesTest.backendSourceRoots()` 的 2 处字面量；`common/build.gradle.kts` 头注释；
本文档。**无影响**：包名、`import`（0 行改动）、Dockerfile（只取 `server/build/libs` 的 bootJar）、
jar 名（`common-*.jar`，无人依赖）。

**复验**：`./gradlew projects` → `:common` + `:domains` ✓；
编译期硬约束探针（往 `:common` 注入 `knowledge.domain.Chunk`）⇒ `:common:compileJava` **FAILED** ✓；
`./gradlew spotlessCheck build` = BUILD SUCCESSFUL + 五守卫绿。


## 7. B118：classpath 资源存在性断言（R12a / R12b）

对应 §5.2 风险 #2：主源码 15 处资源读取里 **13 处是"缺资源不报错"**
（`if (in == null) return/continue`），症状不是异常而是**功能悄悄降级**——模板为空、
内置 agent 列表为空、jieba 分词退化、甚至 `spring.factories` 的 EPP 不注册。
B116 搬家时已经搬过一批资源（`common/text/*.txt`），这类风险从此刻起是真实的。

**落地**：`domains/src/test/java/com/ragagent/arch/ClasspathResourcesTest.java`（`com.ragagent.arch` 守卫族），
两条断言：

| 断言 | 内容 | 覆盖的故障 |
|---|---|---|
| **R12a** | `RESOURCES` 清单（20 条，每条注明消费方）里每个路径都必须能从 classpath 读到，且**不能是 0 字节** | 资源没跟着模块走 / 被重命名 / 空文件 |
| **R12b** | 源码里每个 `getResourceAsStream("字面量")` 必须是清单里的精确路径，或清单某条路径的**目录前缀** | 新增/改动资源没登记（防止清单本身腐化）|

- **清单构成**：11 个提示词模板（三处文件列表共读同一目录）＋ `builtin_agents.yaml`／`agent_type_presets.yaml`
  ＋ `initialization/extract_config.yaml`／`asr_test.wav` ＋ `dataset/samples.json` ＋ `jieba/hmm_model.json`
  ＋ `common/text/TSPhrases.txt`／`TSCharacters.txt` ＋ `META-INF/spring.factories`
  （fail-fast 的两个也纳入——语义上它们"自守"，但纳入后连"文件被搬走"也一起覆盖）
- **R12b 的巧妙点**：只看 `getResourceAsStream("字面量")` 单实参形式，
  拼接形式（`DIR + fileName`）天然不命中 ⇒ **零误报**，且不必解析常量表
- **多模块复用**：扫描根复用 `ArchitectureRulesTest.backendSourceRoots("main/java")`
  （该函数 B118 起放开为包级可见），因此同时覆盖 `domains/src/main/java` 与 `../common/src/main/java`
- **维护**：新增 classpath 资源时在 `RESOURCES` 加一行（写清消费方），两条断言自动覆盖

**红态探针（两条各验一次）**：

| 探针 | 结果 |
|---|---|
| 把 `dataset/samples.json` 改名挪走 | R12a 失败：`dataset/samples.json（evaluation.DatasetService）：不在 classpath 上` ✓ |
| 往 `common` 主源码加 `getResourceAsStream("probe/missing.yaml")` | R12b 失败：`../common/src/main/java/…/TenantContext.java → "probe/missing.yaml"` ✓（顺带证明跨模块扫描生效）|

**闸门**：`./gradlew spotlessCheck build` = BUILD SUCCESSFUL + 五守卫绿。


## 8. B119：javadoc 引用漂移守卫（把 `-Xdoclint:reference` 接进 check）

对应本会话反复人工修的同一问题：改名/搬家/神类切片之后，注释里的 `{@link}`/`{@value}`
还指着**已不存在或已搬走**的类型与成员（本会话人工修过 ≥5 次）。

### 8.1 设计：用 javadoc 自己的解析器，零维护

| 决策 | 理由 |
|---|---|
| **接 `-Xdoclint:reference`** | 只启用 doclint 的「引用」检查组（`{@link}`/`{@see}`/`@param`/`@value` 的**目标是否存在**）；HTML 风格与 `@param` 完整性刻意不纳入（本仓实测 100+ 条噪声、价值低）|
| **接进 `check`** | `tasks.named("check") { dependsOn(tasks.named("javadoc")) }`（root `subprojects` + `plugins.withId("java")`，因为 root 的 `subprojects{}` 在子项目应用插件**之前**求值，直接 `tasks.named("check")` 会报 "Task with name 'check' not found"——实测）⇒ `./gradlew build`（CI backend job 跑的就是它）连带执行 javadoc |
| 不写自定义脚本 | 手写"简单名→类型"匹配远弱于 javadoc 的解析器（会漏 `#成员`、`@value`、跨包解析）；现在零维护 |

### 8.2 修了什么（首轮清算：44 error + 19 warning 行）

| 类别 | 处理 |
|---|---|
| **`#成员` 已搬走（神类切片遗留）** | 降级 `{@code}`（`canViewIntegrationSecrets`/`freeze`/`applyInsertDefaults`/`compressWithRag`/`getSlugParam`/`configDeepEquals`/`serveTenantFiles` 等 11 处）|
| **跨包类型未用全限定名** | 补 FQN（`MemoryRecall`/`ExtractedItem`/`ChunkRepository`/`ChunkAccessGuard`/`FaqChunkCodec#…` 等 12 处）|
| **指向 private/包级成员**（javadoc 只能链接可访问成员） | 降级 `{@code}`（`RETRY_BACKOFF`、`identity()`、`generateAndStoreQuestionsForWorker`）|
| **目标类型已不存在** | 降级 `{@code}`（`SyncItemErrorDeserializer`、`SessionAgentQaService#registerTools`、`WikiIngestService#cleanupContext`→改指 `WikiCleanupScope`）|
| **`@param` 写在类型上**（签名里没有这些参数） | 改写为字段列表（`SourceRefNeedle` 3 条、`ThinkingStrategies` 2 条、`EmbedChannelService` 1 条）|
| **`{@value}` 引用非编译期常量** | `OAuthRuntime`：`REFRESH_SKEW` 是 `Duration` ⇒ 改 `{@link #REFRESH_SKEW}` |
| **未转义的 `<`/`&`**（19 行） | 包 `{@code}` 或改散文（`<pre>` 块内用 `&lt;`） |

### 8.3 守卫顺手抓出的 4 处**真缺陷**（人眼没看到的）

1. **注释已被改坏**：`QuestionBatchPlanner:47` 的 `/** = 0; start < total; start += batchSize} …`
   ——原本应是 `{@code for (int start = 0; …)}`，某次编辑把 `{@code for (int start` 吞掉了
2. **javadoc 挂错对象**：`SearchChunkMerge` 的（含 `@return` 的）方法注释被误挂到 `record ExactResult` 上（B110 搬迁错位）
3. **包路径写错**：`GuardForbiddenException` 指向 `com.ragagent.web.RbacInterceptor`（应为 `com.ragagent.common.web.*`）
4. **`:common` 有 6 处注释反向引用上层类型**（`TenantAPIKey`、`APIKeyRouteAuthorizer`、`config.JacksonConfig`、`llm.domain.StreamResponse`、`ConnectionConfigTypeHandler`）
   ——它们是 **javadoc**、不产生编译依赖（守卫只看 import），但方向上是味道；`:common` 编译期看不到这些类型 ⇒
   现在 doclint 直接报错 ⇒ 全部**去链接降级 `{@code}`**（顺带把方向理正）。
   **这等于给"底座注释不得指向上层"补了一条免费的编译期约束。**

### 8.4 红态探针

| 探针 | 结果 |
|---|---|
| 往 `AgentConsts` 注入 `{@link com.ragagent.does.NotExist}` | `:domains:javadoc` FAILED（`AgentConsts.java:10: error: reference not found`）✓；因 `check` 连带 javadoc，**`:domains:check` 退出码 1** ⇒ `build`/CI 会红 ✓ |

**闸门**：`./gradlew spotlessCheck build` = BUILD SUCCESSFUL（含两模块 javadoc，**0 error / 0 warning**）+ 五守卫绿。


### 8.5 B120：契约层命名规范（与 M1 命名讨论同源）

盘点 26 个契约接口后定下五条（全表见 `docs/backend-package-map.md` §3.5 与
`common/package-info.java`）：用领域类型名前缀（`knowledgeBaseById` 而非 `kbById`）、
单条 `…ById`／批量 `…ByIds`、不用无信息量后缀（`…Unscoped` 而非 `…Only`）、
仓储 `list*`／服务 `get*`、同名方法跨接口必须同义。改名 5 组 / 20 文件（纯改名零语义）。

> 与 M1 的关系：模块化会**放大**命名分歧的代价（跨模块引用要选 `api`/`implementation`、
> 读代码要跨模块跳转）⇒ 先把共享词汇理干净，M1 的边界才划得动（§5.3 的推进顺序同理）。

## 9. B161：`:engine` 抽取落地（M1 第二步，2026-10-09）

承 §6 的"下一步"，按同一套路做第二条硬边界（`:common` 是第一条）：

| 项 | 内容 |
|---|---|
| 模块 | 新增 `:engine`（`java-library` + `java-test-fixtures` + `io.spring.dependency-management` + spotless + protobuf）|
| 成员 | `llm` `retrieval` `embedding` `rerank` `chatpipeline` `modelcontext` `webfetch` `stream` `tracing` `model` `vectorstore`（368 文件 / 58.3k 行）|
| 依赖 | `api(project(":common"))` + 依 import 面声明（jackson/spring/slf4j/mybatis-plus/neo4j/sqlite/protobuf/snakeyaml/Hikari/jakarta）；`runtimeOnly` 驱动（mysql/pg）|
| 搬迁 | 主源码 368 · 测试 **65 搬 / 20 留**（`GoRecording*` 夹具 / `@SpringBootTest` / 跨域者留在 `:domains`）· 资源 `jieba/`(1.1M)、`extract_config.yaml`、`wire/`→testFixtures、jieba 基线→engine 测试资源 |
| 守卫 | `_source_roots.MODULE_DIRS` +engine · `check-stray-dirs` 两处 +engine · `check-event-face-case` 改 `_sr.find_pkg_path` · `check-file-size --write` · `backendSourceRoots` +`../engine/src/…` |
| 探针 | 注入 `com.ragagent.knowledge.domain.Chunk` ⇒ `:engine:compileJava` **FAILED**（`package … does not exist`）；还原后 SUCCESSFUL |
| 闸门 | `spotlessCheck build` BUILD SUCCESSFUL（1m39s）· 4,793 测试 0 失败（server 4072 + engine 721，与拆分前同数）· 八守卫绿 |

**三条新沉淀（写给下一次拆模块）**：

1. **共享测试基座走 `java-test-fixtures`**（§5.2 风险 5 的首次落地）：`EmbeddedRedis` 被 engine 2 个 +
   server 15 个测试共用 ⇒ 放 `engine/src/testFixtures`，server 侧 `testImplementation(testFixtures(project(":engine")))`。
2. **测试资源与驱动要随码走**：`wire/*.json`（engine 2 用 + server 1 用 ⇒ 放 testFixtures resources）、
   `jieba_baseline.json`（→ engine 测试资源）、JDBC 驱动（`runtimeOnly`，ServiceLoader 加载、编译期不可见）。
3. **ArchUnit 的导入过滤器要排 testFixtures 的两种形态**：项目依赖在 ArchUnit 眼里是 **jar**
   （B116 同款）⇒ 既排 `/classes/java/testFixtures/`（dir）也排 `-test-fixtures.jar`（jar）；
   否则 testFixtures 里的 `System.getenv`（EmbeddedRedis 探测 redis-server）会把 A1 判红。

## 10. B162：P1+P2（L1 对齐 + `:engine` 边界校准）+ 用户定案的 5 模块图

**用户定案（2026-10-09）**：`:common` / `:engine` / `:domains` / `:channels` / `:boot`；`datasource` 与
`:misc` 四域（`embedchannel` 除外）并进 `:domains`；`:channels` = `im` + `embedchannel` + `channels.api`。

| 动作 | 内容 | 实测依据 |
|---|---|---|
| P1 | `stream` 下沉 `:common`（含 `TokenUsage`/`PromptCacheStatus` 沉 `common.llm`、`EmbeddedRedis` 归 common testFixtures）| `stream → llm` 仅 **1 条边**；其余出边只有 `common` |
| P2 | `model` + `vectorstore` 出 `:engine` | 引擎内 **0 条**入边 ⇒ 零解边；顺带修 L2 有 controller |
| ✅ P3（**B163 已完成**）| `tracing` 按性质拆：core 20 文件 → `:common`；4 装饰器 + `LangfuseWiring`/`LangfuseVlm` → `:engine` 新包 `tracing.decorators` | 原「逐边定性 11 条」已在 B163 完成（见 §11 ✓）|
| 待 P4 | `:channels` + `channels.*` 改名 | 前置：`auth ⇄ apikey` **22 + 22** 处互依 ✗ |

**新沉淀（写给下一次拆模块 / 新建模块）**：

1. **新模块的测试任务必须显式配 `useJUnitPlatform()`**：B162 实测 `common` 的 8 个测试类自 B116
   起**静默未跑**（`build` 里 `common tests=0` 仍全绿）⇒ 补配后 +104 个测试。这与会"静默失覆盖"的
   守卫路径是同一族坑（B116 记录过）。
2. **共享测试基座放最底层模块的 testFixtures**：`EmbeddedRedis` 从 `:engine` 移到 `:common`
   （否则 `:common` 的测试反向依赖 `:engine` ⇒ 项目环）。
3. **javadoc 的 `{@link}` 是跨模块边**：每次搬家后 doclint 守卫（B119）都会把"注释还指着旧模块类型"
   当构建错误抓出来（B161 二例、B162 一例）⇒ 搬家批次里要预留这一步。

## 11. B163：`tracing` 按层次拆分（P3a）——"不是纯 L1"的实证

**逐边定性（11 条）**：全部落在 4 个**装饰器**文件（`LangfuseChatClient`/`LangfuseEmbedder`/
`LangfuseReranker`/`LangfusePayloads`），它们包装 `LlmChatClient`/`Embedder`/`Reranker`
三个 **L2 接口** ⇒ tracing 天然要依赖 L2 ✗（B115 当年据此把它放进 `:engine` 是对的）。

**处置**：不整体搬，按性质拆 ——

| 侧 | 内容 | 归属 |
|---|---|---|
| core | 20 文件（观测原语 + OTLP 导出 + 配置/属性）| `:common`（+ OTLP proto 从 `:engine` 移交）|
| 装饰 | 4 装饰器 + `LangfuseWiring` + `LangfuseVlm` = 6 文件 | `:engine` 新包 `com.ragagent.tracing.decorators` |

**两条新沉淀**：

1. **跨模块的"包私有"等于不可见**（B162/B163 连撞）：core 的 `RecordedSpan`/`LangfuseRegistry` 等
   包私有类只对**同名包**的测试可见 ⇒ 测试要么随 code 同包（`:common`），要么留在"能在类路径上同时
   看到两侧"的模块且**必须用同名包**（本例 `:engine` 的 `tracing.langfuse`）。这是 Gradle 多模块
   （非 JPMS）下最容易低估的一条。
2. **proto 生成源随消费者走**：`OtlpHttpExporter` 迁 `:common` ⇒ protobuf 插件 + `otlp-proto` srcDir
   + `spotless targetExclude("build/**")` 三件套一并迁（B161 在 `:engine` 已踩过一次同样的 spotless 坑）。

## 12. B206 侦察：P4 前置（`auth ⇄ apikey` 的真实边清单，2026-10-10 实测）

**动因**：`:channels` = `im` + `embedchannel` + **`channels.api`（即 `auth/apikey` 的 API-Key 通道 ✓）**。
而 `im`/`embedchannel` 依赖 `session/knowledge/agent/auth…`（`:domains` ✓）⇒ 若 `auth`/`system` 反向
依赖 `apikey`，则 `:domains ⇄ :channels` **成环** ✗。故 P4 前置 = **切断 `:domains → apikey` 的全部边** ✓。

**旧读数作废**：文档此前写「`auth ⇄ apikey` 22 + 22」✗ —— 按符号/模块实测：**main 侧 6 处 + common 侧 4 处**
（反向 4 处是 `:channels → :domains` 方向，**允许** ✓ 无需处理 ✓）。

| # | 边（实测位置） | 方向 | 处置 |
|---|---|---|---|
| 1 | `system/controller/SystemAdminController:11,12 → apikey.domain.TenantAPIKeyCreateResponse / TenantAPIKeyResponse` | domains→channels | **沉载荷** ✓（两个 DTO 下沉 `:common` 的 api-key 载具位） |
| 2 | `system/controller/SystemAdminController:13 → apikey.service.TenantAPIKeyService` | domains→channels | **反转接口** ✓（`:common` 定义管理口 port，apikey 实现 ✓） |
| 3 | `auth/controller/TenantCatalogController:7`、`auth/controller/TenantCreateOps:11 → TenantAPIKeyService` | domains→channels | 同上（同一 port 复用 ✓） |
| 4 | `auth/filter/AuthFilter:18 → apikey.filter.APIKeyAuthChannel` | domains→channels | **反转接口** ✓（`:common` 定义认证通道 port ✓） |
| 5 | `common/…/TenantFilterGuard → apikey.mapper.TenantAPIKeyMapper.{listByPlaceholderHash,listPlatform,selectByHash,selectFirstPlaceholderHashId}`（4 处） | **common→channels** ✗ | **反转接口** ✓（`:common` 定义占位符/平台键查询 port ✓） |
| 6 | 反向：`APIKeyAuthChannel → auth.service.TenantService / domain.User / service.UserService`、`TenantAPIKeyBootstrap → auth.dto.TenantResponse`（4 处） | channels→domains | **无需处理** ✓（方向合法 ✓） |
| 7 | `boot` 侧：`WebConfig` 注册全部 apikey 过滤器/拦截器（7 类 ✓）+ 4 个契约测试断言 route policy 类 | boot→channels | **无需处理** ✓（`:boot` 依赖一切 ✓；测试同理 ✓） |

**四刀计划**（每刀独立提交 + 独立全绿 ✓，沿用 §13/§14 SOP ✓）：
1. ✅ **沉 DTO（B207 已完成）**：两个记录已迁 `com.ragagent.common.apikey` ✓；**映射工厂留属主**（新类 `TenantAPIKeyProjections` ✓ —— 记录依赖实体，直接搬会在 `:common` 造反向边 ✗）（边 1 ✓）：`TenantAPIKeyCreateResponse` / `TenantAPIKeyResponse`（+ 其引用的 `TenantAPIKeyRequest` 视需要 ✓）沉 `:common`；
2. ✅ **管理口 port（B208 已完成）**：`:common` 新增 `ApiKeyAdminPort`（窄面 ✓ 四个方法：平台列表/建/撤 + 租户默认键 ✓），apikey 侧 `ApiKeyAdminAdapter` 实现（**脱敏逐字搬过去** ✓，调用方只看到 common 载荷 ✓）；顺带清掉 `TenantCatalogController` 的**死依赖** ✗ 与 `TenantCreateOps` 的**迪米特违规**（`service.apiKeyService.…` ✗）⇒ **main 侧边数 5 → 1** ✓（只剩 `AuthFilter → APIKeyAuthChannel`，第 3 刀 ✓）（边 2/3 ✓）：`:common` 定义（create/list/update/delete/rotate 需要的窄接口 ✓），apikey 的 service 实现 ✓，三处调用点改注入 port ✓；
3. ✅ **认证通道 port（B209 已完成）**：`:common` 新增 `ApiKeyAuthPort`（签名与实现逐字一致，含 `throws IOException` ✓），
   `APIKeyAuthChannel implements` 该口、`AuthFilter` 改依赖口 ✓ ⇒ **main 侧边数 1 → 0** ✓✓。
   **⚠️ 边 5 更正（B209 实测）** ✗：`TenantFilterGuard` 并不引用 apikey 的 mapper **类型** —— 它持有的是
   **MyBatis 语句名字符串白名单**（4 条 ✓：`…TenantAPIKeyMapper.listByPlaceholderHash` / `listPlatform` / `selectByHash` / `selectFirstPlaceholderHashId` ✓）
   ⇒ **不是模块边** ✗（侦察正则误报 ✓，同 R-d 那类 ✓）；**但** apikey 改名 `channels.api` 时这 4 条字符串**必须同批更新** ✓（否则运行期守卫误判 ✓）。
4. ✅ **验环（B209 一并完成）**：`check-package-cycles` ✓ · 九守卫 ✓ · 全量闸门 ✓（1m40s / 4858-0 ✓）⇒ 代码边归零 ✓。**下一步（B211 已改判 ✓）**：**不建** `:channels` —— 试切后判定不划算，整批回退（见 §13）✓；`apikey` / `im` / `embedchannel` 均留在 `:domains` ✓。

## 13. B211：`:channels` 试切后**回退** —— 结论与判据（2026-10-10）

**经过**：按 §12 的边清单，B207–B209 先把 `auth ⇄ apikey` 的 22 + 22 代码边切净（沉 DTO + 两个 port ✓），
B210 据此建 `:channels` 并把 `apikey` → `channels.api`（1 包 / 33 条目迁移 / 3 个提交 ✓）。
**当天复盘判定"收益与成本不成比例" ⇒ B211 整批回退** ✓（`git revert '4950df34^..HEAD'` 零冲突 ✓；
32 改名回迁 + 15 改回 + 2 删 ✓；`spotlessCheck build` 1m27s · **4858 / 0** ✓（与 B209 基线逐条一致 ✓）· 九守卫 ✓ · 外壳 50/52 ✓）。

**回退理由（实测）**：

| # | 理由 | 证据 |
|---|---|---|
| 1 | 是**平行模块**而非**下沉模块** ⇒ 对减少耦合贡献≈0 | `channels/build.gradle.kts` 有 `implementation(project(":domains"))` ✓；`com.ragagent.auth.*` 4 处 + `com.ragagent.tenant.*` 2 处引用 ✓；只强制了"domains 不得反向依赖"（而切边后本就成立 ✓） |
| 2 | **测试面边界切不净** | apikey 的契约金片仍在 `:domains` 的 `testFixtures/resources/contracts/`（`api-key-401.json` / `ct-create-apikey.json` / `w5c-files-*` ✓）⇒ 模块要靠 `testImplementation(testFixtures(project(":domains")))` 取用 ✗ |
| 3 | **破坏命名惯例** ✗ | `channels.*` 成全仓唯一的"模块名进包名"（`:engine` 装 `chatpipeline/llm/…`、`:domains` 装 20 域、`:common` 装 `common/event/tracing`，均**扁平** ✓）⇒ 读包名判模块归属的习惯被打乱 ✓ |
| 4 | **基建登记是永久成本** | 新增模块要在 `settings.gradle.kts` / `boot/build.gradle.kts` / testFixtures / 九守卫模块清单（B116"对新模块静默失明"✗）/ `file-size.baseline.json` / JSON 键名基线 / 包地图 各登记一遍 ⇒ 永久维护面加宽 ✓ |
| 5 | **紧耦合对**应内聚，不宜拆分 | `auth ⇄ apikey` 是 B206 实测的 **22 + 22** 紧耦合对 ✓；拆分 = 把内部接口升格成跨模块 API ✗（改一处两边都动 ✓） |

**保留（回退不丢）**：B206–B209 的**切边成果** ✓（`auth` 不再互穿 `apikey` 内部，未撤 ✓）· B206 的 **44 边清单** ✓（再试的输入 ✓）· B116 的模块登记清单 ✓（现作为**"是否新增模块"的成本判据** ✓）。

**判据（新增模块前先答三问）**：

1. 它**不依赖 `:domains`** 吗（＝下沉，而不是架在其上）？
2. 它的**金片与测试基建**能随迁吗（不靠 `testFixtures(:domains)`）？
3. 迁移条数 ×2（切边 + 登记）**小于**它带来的强制力吗？

三问**有任一为否** ⇒ 先做**内聚**，不做模块 ✓。按此判据，`im` / `embedchannel` **暂不外提** ✓（P4 剩余部分待议 ✓）。
