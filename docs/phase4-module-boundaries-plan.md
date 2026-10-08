# 阶段 4 盘点与方案：Gradle 多模块 + ArchUnit 边界固化（2026-10-08）

> **用途**：阶段 4（§7.3 第 3 条）的决策输入。含**实测数据**、目标模块图、**必须先解的跨包环（含具体边）**、
> 分档迁移顺序与每步闸门、ArchUnit 规则清单、风险与非目标。
> **复测命令**：`python3 scripts/pkg-audit.py`、`python3 scripts/check-package-cycles.py`、
> 本文件 §1 的依赖矩阵脚本口径（`import com.ragagent.<pkg>.` 计数）。

## 0. 一句话结论

**环不在"两两双向"层（守卫基线 0 组），而在"三包以上间接环"（实测 2 个 SCC，覆盖 11 个包）**；
其中 **1 处是一行配置搬家的零风险切割**（`stream → config`），另 **1 个 SCC 的 5 条大边是真正的模块化前置**
（`wiki→knowledge` 43 处、`knowledge→retrieval` 47 处、`knowledge→auth` 17 处、`storage→auth` 16 处、`audit→wiki` 1 处）。
ArchUnit 1.3.0 **已是测试依赖**（`server/build.gradle.kts:111`），可直接落规则。

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
- 构建：单模块 `server`（`settings.gradle.kts` 只 include("server")），产物 Spring Boot jar；
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
| `chatpipeline → knowledge` | ◐ B110+B111（17 → **15 处**，B111 盲区修正 +3） | **B110 清掉 5 处**：① **元数据载荷下沉**——`FaqChunkMetadata`/`DocumentChunkMetadata`/`GeneratedQuestion`（三者的 `com.ragagent.*` import 实测为 0，纯 JSON 载荷）→ `common.knowledge`；② **算法下沉**——`SearchChunkMerge`（187 行，`mergeTextChunks` 只读 `getStartAt/getChunkIndex/getContent/getEndAt`，**正好落在 6 字段 `ChunkView` 里**）→ `common.retrieval` 并改收 `ChunkView`（`KnowledgeSummaryService` 用 `ChunkPortAdapter.viewAll` 投影，投影方法转 public 作域内公用）。**剩余 12 处**：9 处**实体**（`Chunk`×3/`Knowledge`×3/`KnowledgeBase`×3）+ 3 处 `ImageInfoEnricher`（用的是 `collectImageInfoByChunkIds`/`enrichContentWithImageInfoForChat`/`clearImageInfoTextMatchingBody` **三个不同的静态方法**，其中 collector 的形参是 `BiFunction<…, List<Chunk>>` ⇒ 要么给 `ChunkView` 补回 `imageInfo` 字段、要么按 C2 的做法扩端口；三个方法本体是纯文本函数，可单独下沉）|
| `webfetch → datasource` | 🆕 **B111 新暴露（2 处）** | `webfetch/AgentMarkdown` 静态复用 `datasource.connector.rss` 的 `HtmlToMarkdown`/`JdkHtmlToMarkdown`（此前整条边在图里不存在）⇒ 候选修法：两个实现若是纯文本函数（实测域依赖为 0）则下沉 `common.text`/`common.web`，否则给 webfetch 立窄端口。**待 B112+ 侦察** |

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
