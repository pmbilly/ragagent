# 后端分包地图与体检结论（2026-09-30）

> **用途**：新人 30 分钟建立"哪个功能在哪个包"的全局观；后续会话按本文的 **P0–P3 待办**推进，
> 不用重新摸索。**复测**：`python3 scripts/pkg-audit.py`（口径见脚本 docstring）。
> **基线数据**：**31 个顶层包**（2026-09-30：`apikey`→`auth`、`agentm` 拆出 `initialization`、`searchutil`→`retrieval/support`、`storageurl`→`storage/support`、`webfetch`→`agent/support`）/ **1634 文件 / 284k 行**（`com.ragagent` 子树）；顶层 `package-info` **33/34**（仅 `session` 待其批次补）。

## 1. 三类包（先分清性质，再判断"合理与否"）

| 性质 | 包 | 判定 |
|---|---|---|
| **业务域（19）** | knowledge、agent（含 `management/` 子域，2026-10-03 由 `agentm` 并入）、initialization、wiki、session、datasource、memory、mcp、auth（含 `apikey/` 子域）、audit、im、storage、model、system、websearch、embed、vectorstore、favorite、evaluation | 有 `controller/service/domain/dto/mapper/repository` 六件套，HTTP 面明确 |
| **库式域（4）** | `llm`、`retrieval`、`chatpipeline`、`event` | **无 controller 是对的**——被其他域调用的引擎/管线（`retrieval` 被 chatpipeline 19 文件、knowledge 10、session 7 消费） |
| **基础设施（7）** | `common`、`config`、`stream`、`modelcontext`、`tracing`、`embedding`、`rerank`（`searchutil`/`storageurl`/`webfetch` 已并入宿主域，见 §3.5）| 横切能力；`common` 被 **30 个包**依赖（位置正确） |

**分层约定**（§2.13）：`mapper/` 只放 MyBatis-Plus 接口；`repository/` 放仓储门面（软删/乐观锁/方言）；
`service/` 放 Spring 服务；`support/` 放无状态算法；其余按 `task/client/storage/security` 角色；
**一类型一文件**，禁 `*Dtos/*Util` 类容器。`knowledge` 为范本（12 子包、零容器类）。

## 2. 已达标（别动）

- 分层方向正确：`common` 在底（30 包依赖）、`retrieval`/`llm` 是库式域不暴露 HTTP；
- `im` 一渠道一子包（feishu/wechat/dingtalk/slack/mattermost/telegram/qqbot/wecom/yunzhijia + runtime）；
- 真分层倒挂**极少**：`controller → mapper/repository` 仅 **5 个**（apikey/memory/session×2/storage）；
  `service → controller` 仅 **2 个**（wiki/service 的 Ingest 两个类）；`domain/dto → service/controller` 仅 1 个；
- `controller/` 包里放错的文件只有 **2 个**（`audit/AuditLogListResponse`、`wiki/WikiActivityAudit`）。

## 3. 待办（按优先级；**一次只动一个轴**，别混批）

### P0 包间成环：34 组 —— 实测后**大部分比想象中便宜**

> ⚠️ **别用"成环数量"估成本**：决定成本的是**背边规模**（环里文件数较少的那一侧要切几刀）。
> 2026-09-30 画像：**背边 ≤2 文件 = 22 组**（多数是**单个类型越界**造成）、4–7 文件 ≈ 7 组、**≥8 文件 = 5 组**（贵重）。

**便宜的环长什么样**（一个类型造成的整组环）：

| 环 | 背边类型（切它即可） | 现用途 |
|---|---|---|
| `common ⇄ config` | `TenantProperties ×1` | `RbacInterceptor` 读租户属性 |
| `auth ⇄ memory` | `MemoryConfig ×1` | 租户目录页读记忆配置 |
| `audit ⇄ auth` | `TenantRole ×1` | 审计控制器判角色 |
| `llm ⇄ retrieval` | `SearchResult ×1` | 流式响应里放检索结果 |
| `storage ⇄ session` | `Message ×1` | 判断"消息是否引用该文件" |
| `embedding`/`rerank`/`llm ⇄ model` | `Model ×1` | provider 只要**配置值**却拿了模型实体 |

**五种手法**（22 组归为 5 类，不是 22 个独立难题）：

| 手法 | 约解 | 例 |
|---|---|---|
| A 配置/常量类归位到最低层 | 6 组 | `TenantProperties`、`ConversationProperties`、`MemoryConfig` |
| B 无状态工具下沉（→`common`/中立 support） | 5 组 | `SearchTextUtil`、`AgentPromptPlaceholders`、`ExtractPrompts` |
| C 端口化：上层不直连下层 mapper/service | 6 组 | `audit`/`apikey` 直查 `KnowledgeBaseMapper`；`auth` 直用 `KnowledgeBaseService` |
| D 传值不传实体 | 4 组 | provider 客户端只取模型配置值 |
| E 引擎伴生类型归位 | 3 组 | `Gate`/`Decision`/`ApprovalException`、`Registry`/`StreamDecoder` |

**批次**：批 1 = A+B（约 11 组）｜批 2 = C（约 6 组）｜批 3 = D+E（约 5 组）｜**5 组贵重的留阶段 4**
（`knowledge ⇄ wiki`、`chatpipeline ⇄ session`、`chatpipeline ⇄ knowledge`、`knowledge ⇄ retrieval`、`agent ⇄ mcp`：
要么是领域实质耦合，要么绑定仍待重构的域——现在硬解会返工）。
**更新（2026-09-30 批 4 系列）**：这 5 组全部已解（`knowledge ⇄ wiki`、`chatpipeline ⇄ knowledge`、
`knowledge ⇄ retrieval`、`agent ⇄ mcp`、`chatpipeline ⇄ session`）——**全仓零包间环**。

**守卫（已入库，2026-09-30 起挂 CI 的 `guards` job）**：`python3 scripts/check-package-cycles.py` —— **环只许减不许增**（基线
`scripts/package-cycles.baseline.json`：环 34 / 依赖 config 5 包 / L2→L3 19 条）；解掉后跑 `--write` 刷新基线。
当前基线（2026-09-30 批 4n 后）：**环 0 组 / 依赖 `config` 的包 1 个 / 能力层→业务层直连 6 条**（拆分使 `agentm ⇄ knowledge`/`agentm ⇄ model` 改名为 `initialization ⇄ …`，净数不变）。

**更新（2026-10-02，B33）**：B6 批 10 一度把两处工具放错层——`config/AppEnvLookup` 被 11 包引用、`llm/chat/ImageResolver` 直连 `storage/StoragePaths`，守卫红灯（环 5 组 / 依赖 config 11 包）。
B33 已归位：`AppEnvLookup` → `common/deployment`、`StorageRuntimeEnv` → `common/storage`（+ `ImageResolver` 脱开 storage），守卫回绿至上述基线（环 0 / 依赖 config 1 包 / L2→L3 6 条）。

**修正（2026-10-08，B111）**：上述读数均为"**只解析 import 行**"的代理值。清掉 1,331 行内联全限定名后，依赖图第一次完整，真读数为 **环（两两）2 组 / 间接环 1 组（8 域）/ L2→L3 2 条**——那些边一直存在，只是此前不计数。脚本已新增 **R8 禁内联 FQ** 并重刷基线；详见 `docs/phase4-module-boundaries-plan.md` §3。

### P1 扁平包（原 10 个 → 余 5 个）

**已拆**（2026-09-30 批 P1，纯移动 + 引用改写 + 全绿；`storageurl`/`searchutil`/`webfetch` 三个早先批次已合并掉）：

| 包 | 结果 | 拆法 |
|---|---|---|
| `chatpipeline` | 根 13 + `plugin/`(19) + `support/`(6) | `Plugin*` + `Plugin` 接口进 `plugin/`；纯逻辑（SearchSupport/QueryTokenizer/ReferencesSupport/ImageInfoCollector/MemoryUsedMemories/MatchTypes）进 `support/`；骨架与跨域 seam（PipelinePorts/Builder/Common/EventType/Log/Progress/ChatManage/History/QueryIntent/SummaryConfig/EventManager）留根 |
| `event` | 根 13 + `payload/`(26) | 26 个 `*Data` 事件载荷进 `payload/`；总线机制（Event/EventBus*/EventHandler/EventMiddleware/EventIds/EventJson/EventType/GlobalEventBus/PanicError）与事件信封 `TenantContextSnapshot` 留根 |

**provider 族（2026-09-30 批 P1 收尾，已拆）**：

| 包 | 结果 | 拆法 |
|---|---|---|
| `embedding` | 根 9 + `provider/`(11) | 10 家 provider 实现进 `provider/`；公共骨架 `BaseEmbedder` 随行（它只被 provider 用，同包后无需放宽可见性）；框架（Embedder/Factory/Http/GoJson/池化）留根 |
| `rerank` | 根 6 + `provider/`(8) | 8 家 provider 实现进 `provider/`；框架（Reranker/Factory/Http/GoJson/RankResult）留根 |

**余 3 个（判定为"保持扁平"，不拆）**：`stream`(12，SSE/流存储单一契约)、`modelcontext`(12，工具协议层)、
`config`(9，装配层)。判据：**无天然族就不拆**（别为扁平而扁平）；`agent/tools` 已按能力分组（2026-10-02，B34，见 §P2）。

### P2 超大单层（≥70 文件）

> **口径提醒**：本节数字是"二级目录**递归**聚合"，子目录分好之后数字**不会变小**——
> `datasource/connector`(76) 早已按 provider 分好（`feishu/{core,drive,wiki}`、`gitlab`/`ima`/`notion`/`rss`/`yuque`，
> 测试树镜像），`wiki/service` 拆分后聚合数仍是 78。**看子结构，别看这个数。**

| 层 | 状态 | 拆法 / 内部族 |
|---|---|---|
| `wiki/service` | ✅ 已拆（2026-09-30）：根 11 + `ingest/`(45) + `page/`(19) | `ingest/` = 摄取管线（Ingest 门面 + 四阶段、任务队列、锁、幂等凭据、去重清理）；`page/` = 页面服务、文件夹/视图、链接与 lint、slug 锁与匹配、编辑上下文；根 = 跨切面端口（ChunkCleaner/ImageEnricher/KnowledgeFinalizer/ModelResolver + 缺省实现）与 LLM/提示词适配 |
| `datasource/connector` | ✅ 早已按 provider 分层 | 无需再动 |
| `agent/tools` | ✅ **已拆（2026-10-02，B34）**：根 42 + `wiki/` 30 + `knowledge/` 11 + `sql/` 5 + `data/` 4 + `web/` 2 | 分类按**调用点**不按名字前缀：子包 = 族内工具与辅助；根 = 框架/基建 + 跨域或跨族共享的接缝与值类型（`SearchTarget`/`SearchAuth`/`DocChunkSupport`）+ 通用单件工具 + **MCP 族**（与 `ToolRegistry` 同包紧耦合——含 protected 成员互访约 40 处，暂留根，待 MCP 段外提后再分组） |
| `knowledge/dto` | ✅ 已拆（2026-09-30）：`faq/`(21) + `chunk/`(12) + `kb/`(15) + `doc/`(16) + `tag/`(7) + 根 3 | 按功能切；根留批量删除、检索请求、任务 id 响应三个跨面载荷。**这批最干净**：DTO 之间零跨包引用，脚本 0 处补 import、0 处可见性放宽 |

- [x] `wiki/service` 与 `knowledge/dto` 已拆；`agent/tools` **已拆（2026-10-02，B34）**：
      根（框架 + 跨族共享 + 通用单件 + MCP 族） + `wiki/knowledge/sql/data/web` 五个能力子包。
      判据：有天然族才拆，别为扁平而扁平；MCP 族因与 `ToolRegistry` 同包紧耦合暂留根（已登记）。

### 端口归属判据（2026-10-08 B125 补：修正一次误判）

仓库里有两种端口形态——**不是"两套解法"，而是两种场景的正解**：

| 场景 | 端口放哪 | 例子 | 为什么 |
|---|---|---|---|
| **L3 ↔ L3**（域间互调）| `:common` 的 `common.<domain>` 包 | `common.knowledge.ChunkPort`、`common.session.SessionMessagePort` | 域之间不许互相依赖 ⇒ 契约只能下沉 L1；由**编译器**（模块边界）强制 |
| **L2 ↔ L3**（能力层用业务域）| **消费侧拥有**（留在 L2 包内）| `chatpipeline.PipelinePorts`（16 个内嵌接口 + 异常 + 3 个载荷 record）| 依赖方向本就合法（L3→L2 允许）⇒ 消费侧拥有接口正是依赖倒置；载荷用 L2 类型（`LlmChatClient`/`Reranker`）也合法 |

**判据一句话**：接口两边**都在 L3** ⇒ 进 `common.<domain>`；**有一边是 L2** ⇒ 留在 L2 消费侧。

> **修正记录（B124 → B125）**：B124 的架构评述把「`PipelinePorts` 没进 `common`」列为"第 1 号不一致"，
> 侦察后**修正**：它引用了 4 个 L2 类型（`llm.LlmChatClient`、`rerank.Reranker`、
> `retrieval.domain.WebSearchResult`、`retrieval.graph.RetrieveGraphRepository`），
> **整体搬进 `:common` 会造成 L1→L2 反向依赖**——那是错的。它该留，判据补在此处，
> 免得以后有人反过来搬（或把 L2 端口硬塞进 L1）。

### 守卫编号地图（2026-10-08 B124 定：两套体系互不重号）

| 前缀 | 出处 | 条数 | 主题 | 在哪看 |
|---|---|---|---|---|
| **`R*`** | `scripts/check-package-cycles.py` | 10（R1/R1b/R2/R3/R3b/R4~R8）| **依赖与结构**：包间环、间接环（SCC）、依赖 `config`、L2→L3 条数与处数、`common` 纪律、`package`↔路径一致、**禁内联全限定名** | 脚本 docstring + 运行输出 |
| **`A*`** | `com.ragagent.arch.ArchitectureRulesTest` / `ClasspathResourcesTest` | 14（A1~A12b）| **代码规范**：裸 `getenv`、属性类扫描覆盖、双装配、`install*` 调用面、裸 NUL、`@Lazy`、裸 JDBC、字符串列名 wrapper、`.last(` 拼接、分层倒挂、`*Mapper` 包约定、classpath 资源存在性 | 测试 `@DisplayName` |
| **`S*`** | `scripts/check-stray-dirs.py` | 2（S1/S2）| **目录卫生**：游离目录（对 git 不可见的空目录）、死包目录（整棵子树无 `.java`）| 脚本 docstring |
| 其余脚本 | `check-json-key-case` / `check-go-anchors` / `check-fe-contract-keys` / `check-event-face-case` / `check-file-size` | — | 换锚命名、注释锚点、前后端契约键、事件面命名、文件体量棘轮 | 各自 docstring |

> **为什么改**：B124 之前，脚本侧与 ArchUnit 侧**都用 `R*` 且互不对应**——例如 `R7`：脚本里是「`package` 声明 ↔ 路径一致」，ArchUnit 里是「裸 JDBC 白名单」。提交信息或文档里说「R7 复核」时，读者无法判断指哪一条。
> **改法**：脚本侧保持 `R*`（HANDOFF/phase4 里 ~165 处历史与计划引用无需改写），**ArchUnit 侧改为 `A*`**（14 条；引用它的只有 4 份活的手册，已同批同步）。
> **读历史文档时**：HANDOFF / phase4 里的 `R*` 一律指脚本侧；若上下文是 `getenv` / JDBC / wrapper / `.last(` / `@Lazy` / `*Mapper` 包等**代码规范**，应读作对应 `A*`。

### P2b 超大文件（> 600 行；棘轮 `scripts/check-file-size.py`，B121 上线）

**现状**（2026-10-08 B127 盘点）：主源码里 **> 600 行有 67 个**、其中 ≥800 **4 个**（3 个已论证例外 + 1 个待还债）。**已出榜**：`im/service/ImService`（B123~B127 四刀 1,091→554，守卫自动清理其豁免登记）。
最大的 12 个（**就是后续拆分的待办清单**）：

| 行数 | 文件 | | 行数 | 文件 |
|---|---|---|---|---|
| 936 | `im/service/ImService`（B123 首刀 1091→995；B125 二刀 →936） | | 772 | `retrieval/HybridSearchService` |
| 1041 | `session/service/SessionKnowledgeQaService` | | 765 | `memory/service/MemoryService` |
| 919 | `memory/mapper/MemoryIndexStore` | | 763 | `agent/ActPhase` |
| 847 | `knowledge/task/KnowledgeProcessWorker` | | 761 | `im/runtime/ToolDisplay` |
| 827 | `knowledge/service/KnowledgeService` | | 760 | `llm/chat/AnthropicChat` |
| 789 | `storage/fileserve/StorageFileResolver` | | 759 | `wiki/service/ingest/WikiIngestCitePipeline` |
| 780 | `knowledge/service/FaqEntryCommandService` | | 757 | `auth/service/UserService` |

**棘轮口径（B121 起 5 条，B122 执行 §14.5 政策）**：

| 规则 | 内容 | 判据来源 |
|---|---|---|
| R-a | 不得新增 > 600 行的主源码文件 | 膨胀源是**新增**大文件 |
| **R-b** | **基线内文件不得比基线多出 > 30 行** | `ImService` 出榜后 **7 天回涨 +427** 而无人察觉 —— 「已出榜文件回涨」是盲区 |
| **R-c** | **≥ 800 行必须登记**（`exempt`） | §14.5「该域最大类 < 800 行」 |
| **R-d** | **登记为 `accepted` 的文件必须在源码里自述理由** | §14.5「例外必须在类 javadoc 写明理由」 |
| **R-e** | **自述里的行数必须与实际相符，且 < 800 不得再自称例外** | `FaqImportService` 已 566 行仍写「>800 行」（B119 只管 `{@link}`，抓不到散文数字）|

登记表 `scripts/file-size.baseline.json` 的 `exempt`：`accepted` = 已论证接缝的例外；
**`pending` = 欠债（不是豁免）**，守卫输出里标【待还债】，即后续批次的工作清单。
`--write` 只刷新行数上限并清理失效登记，**新增豁免必须人工决策**。

> **教训（B122）**：政策写在散文里（HANDOFF §14.5 / §14.3）就会漂移——实测「≥800 只剩 4 个登记例外」
> 已变成 5 个、且其中一个连理由都没有。**要么落到脚本里，要么每批复测。**

**拆分范式（项目既有，见 `wiki/controller` + `*Ops`）**：把一组内聚方法提到同包的 `*Ops`
**普通类**（**不加** `@Component`），由门面构造持有（`this.pageOps = new WikiPageOps(...)`）
⇒ **零 Spring 装配改动、零调用点改写**，门面退化为薄转发。四例的可切性已侦察：
`MemoryIndexStore`（1 字段 `repo`，四簇：topic / affinity / embedding+vector / extraction 队列）、
`ImService`（Leader 选主簇字段自成一体）、`SessionKnowledgeQaService`（0 字段，WebSearch 参数
解析与小工具簇无状态）、`StorageFileResolver`（3 字段）。

### P3 命名与文档

- [x] **顶层 `package-info` 全覆盖 31/31**（2026-09-30；28 个见 `809115c`，`session` 由批 P3 补：该域批次已交付，
      当时"故意留空避免撞车"的顾虑不再成立）；
- [ ] `model` 既是顶层域又是层名（`model/domain` vs `auth/domain`）→ 至少在文档里点名，改名后议；
- [ ] 四个近邻包易混：`embed`(12，HTTP 叶子域，0 包引用) / `embedding`(21，provider 客户端) / `vectorstore`(12) / `rerank`(14)；
- [x] **放错包 / 倒挂清零（2026-09-30，批 P3，体检 ④⑤ 已无输出）**：
      `AuditLogListResponse` → `audit/dto`、`WikiActivityAudit`（wiki→audit 的端口接缝）→ `wiki/domain`；
      4 个控制器改走服务层/领域类型（`MemoryController`/`SessionController`/`MessageSuggestionController`/
      `StorageBackendController`）——做法是把仓储的嵌套返回类型提成领域类型
      （`memory/domain/MemoryPage`、`session/domain/SessionPage`、`session/domain/MessageSuggestionSetNotFoundException`）
      与在服务上加读面方法，而不是给控制器开新面；`agent/management/dto/AgentResponses`（原 `agentm/dto/`，B36 并入后路径）的 dto→service 倒挂同法
      （`CustomAgentService.Result` → `agent/management/dto/CustomAgentResult`）。

## 3.5 目标结构（重组后）

### 分层规则（`scripts/check-package-cycles.py` 可校验其一）

```
L4  config                      组合根：Spring 装配；**只出不进**（任何域不得依赖它）
L3  业务域                       knowledge agent session wiki datasource im memory mcp auth
                                 audit model storage system websearch embedchannel favorite evaluation
     └ 同级之间：禁直连对方 mapper/实体；跨域走**窄接口（port）或事件**
L2  能力层                       llm retrieval embedding rerank chatpipeline
     └ 不得依赖 L3；只接受**配置值**而非业务实体
L1  平台                         common event stream tracing
```

### 契约层命名规范（2026-10-08 B120 定）

跨域端口/网关（`common/<domain>` 下的接口）是**全仓共享词汇**，命名分歧的代价最高
（读的人要在脑内维护"谁叫 kb、谁叫 knowledge"）。B120 盘点了 26 个契约接口后定下五条：

| # | 规则 | 反例（B120 已改） | 正例 |
|---|---|---|---|
| 1 | **用领域类型名做前缀，不用缩写** | `kbById`（`kb` 不是领域词，且与 `knowledge` 混用） | `knowledgeBaseById` |
| 2 | **单条 `…ById(id)`；批量 `…ByIds(ids)`**（`Id`/`Ids` 大小写统一） | `getKnowledgeBaseByIDs` | `getKnowledgeBaseByIds` |
| 3 | **不用无信息量的后缀**；语义写进 javadoc | `getKnowledgeBaseByIdOnly`（`Only` 指代不明，实为"不做调用方作用域过滤"） | `getKnowledgeBaseByIdUnscoped` |
| 4 | **仓储返回列表用 `list*`；服务返回实体用 `get*`**（现状已达标的约定，写下来防回退） | — | `listChunksById` / `getKnowledgeById` |
| 5 | **同名方法跨接口必须同义**（工具侧自带的窄视图接口与端口同名时，签名与语义一致） | — | `KnowledgeSearchTool.KBView#getKnowledgeBasesByIdsUnscoped` |

> 规范本身不靠人记：**B119 的 javadoc 守卫（`-Xdoclint:reference`，已接进 `check`）**
> 会把指向已改名方法/类型的注释在构建期变红；`{@code}` 里的旧名不会被捕获，
> 所以改名时仍需全仓 `grep` 一次（B120 的做法：改完立刻复跑残留检查 + 全量测试）。

**B120 落地范围**（5 组符号 / 20 文件，纯改名、零语义变更）：`kbById`、`kbByIdIncludingDeleted`
（L1 端口 `KnowledgeBaseLookup`）+ `getKnowledgeBaseByIDs`、`getKnowledgeBaseByIdOnly`、
`getKnowledgeBasesByIdsOnly`（chatpipeline 端口 + 两个工具侧接口及其实现/调用点）。
刻意未动：`VectorStoreLookup.byId`（通用词、124 处命中里多为同名局部变量，分辨成本大于收益）、
`QaSearchTargets` 的局部 `Map kbById`（局部变量，非契约面）。

### 顶层包 34 → 约 29（并入/改名 5 处 + 1 处待定）
### 顶层包 34 → 约 29（并入/改名 5 处 + 1 处待定）

| 现在 | 重组后 | 理由 |
|---|---|---|
| `searchutil` | `retrieval/support` | ✅ **已并入（2026-09-30，批 1）**：纯检索工具（消 `retrieval ⇄ searchutil` 环）|
| `storageurl` | `storage/support` | ✅ **已并入（2026-09-30，批 1）**：存储 URL 重写（消 `storage ⇄ storageurl` 与 `session ⇄ storageurl`）|
| `webfetch` | `agent/support` | ✅ **已并入（2026-09-30，批 1）**：agent 的抓取能力 |
| `apikey` | `auth/apikey` | ✅ **已并入（2026-09-30）**：一次消掉 `apikey ⇄ auth` 与 `apikey ⇄ knowledge` 两组环 |
| `embed` | `embedchannel` | 与 `embedding` 名字太近，语义不同（业务渠道 vs provider 客户端）|
| `modelcontext` | `agent/modelcontext` | ✅ **已并入（2026-10-02，B35）**：模型输出上下文协议层（13 文件）；消费方 agent/chatpipeline/session 改包路径；顶层包 31 → 30 |
| `agentm` | ✅ **已并入 `agent`（2026-10-03，B36）**：`agentm`（20 文件）→ `agent/management/`（子域形态，先例 `auth/apikey/`）| 2026-09-30 先与 `initialization` 分离（拆掉 Go 期混装），本次再并入 agent——名字的 "m"（management 缩写）无处解释，且与 agent 同族；agent 域由此获得唯一 HTTP 面 |

其余域**保留顶层**：`knowledge agent session wiki datasource im memory mcp auth audit model storage
system websearch favorite evaluation common config event stream tracing`。

### 域内标准骨架（§2.13）+ 二级子包阈值

```
<domain>/
  controller/ 仅 *Controller      service/  Spring 服务        domain/  实体与值对象（jsonb 落库类型）
  dto/        请求/响应           mapper/   MyBatis-Plus 接口   repository/ 仓储门面（软删/乐观锁/方言）
  support/    无状态算法与规则    task/ client/ storage/ security/   按角色
```

**阈值**：一层 **>50 文件 或 ≥3 个自然族 → 建二级子包**（命中者：`agent/tools` 95、`wiki/service` 81、
`datasource/connector` 76、`knowledge/dto` 74；扁平包 `chatpipeline` 44、`event` 40）。

| 目标内部分组（沿已有命名族，纯移动） |
|---|
| `agent/tools/` → `tools/{knowledge,wiki,web,mcp,sql,data}/`（`Wiki*` 19、`Mcp*` 10、`Sql*` 4）|
| `wiki/service/` → `service/{ingest,page,link,folder}/`（`Wiki*` 60、`Ingest*` 6）|
| `knowledge/dto/` → `dto/{request,response,view}/`（或按族 `faq`17/`knowledge`8/`chunk`）|
| `datasource/connector/` → 已按供应商分子包，只需 14 个根级文件归位 |
| `chatpipeline/`（扁平 44）→ `plugin/`(19) + `pipeline/`(7) + `search/` + `payload/` |
| `event/`（扁平 40）→ `bus/` + `payload/` + `agent/`(11) |
| `embedding/`(21) `rerank/`(14) → 可选 `provider/` + `support/`（未超阈值，非必须）|

### 批次 → 结构变化的对应

| 批次 | 结构结果 |
|---|---|
| 批 1 解环·配置/工具归位（A+B） | ✅ **已执行（2026-09-30）：环 32 → 24（−8）**；顶层 −3；依赖 `config` 的包 5 → 1；L2→L3 直连 18 → 14 |
| 批 2 解环·端口化（C） | **已完成（环 17，批 2 全部收口）**：已完成 `AgentPromptPlaceholders`→`common/prompt`（消 `agent ⇄ knowledge`，环 24→23）；③-c `auth ⇄ storage`（`StorageAllowList`→`common/storage`；`StorageBackendProvisioner` 命令端口，106 行 env→实体映射收回存储域）；③-d `auth ⇄ knowledge`（`KnowledgeBaseProvisioner` 命令端口：隐藏 KB 的实体语义收回知识域）；余下：① `session ⇄ storage`——✅ **已完成（2026-09-30，环 22）**：端口载荷收窄 + `Rewriter` 消息段搬到会话侧；原「前半已完成」：`FileAccessResolver` 的端口载荷已收窄为 storage 侧 `MessageFileFacts`（会话侧 `factsOf` 映射）；**后半待做**：`storage/support/Rewriter` 的消息段（第 334–437 行）仍 import `Message`/`MessageImage`，建议整段搬到会话侧（见 HANDOFF §11.9）；② `audit ⇄ knowledge` **已完成**（`KnowledgeBaseGateway` 只读端口）；`auth ⇄ knowledge/storage` 各加窄接口（**`auth ⇄ memory` 已完成**：`MemoryConfig`/`MemoryKinds`/`MemoryKeys` 下沉 `common/settings`）（**`auth ⇄ system` 已完成**：`SystemSettingRegistry` 下沉 `common/settings` + 新增只读端口 `SystemSettingGateway`，由 system 侧实现）|
| 批 4 大项解环（④） | **已完成（环 0，全仓零包间环）**：④-a `agent ⇄ mcp` **已完成**——`ResponseType`（18 文件共享的事件契约枚举）→ `common/llm`；`agent/approval`（共享审批机制，1,861 行）→ `common/approval`，MCP 专用的 `Adapter`/`McpToolPolicySource` 下沉 `mcp/service`；④-b **能力层配置去实体化**：5 个配置类的 `fromModel(Model)` 映射收回 `model/service/ModelRuntimeConfigs`（消 `embedding`/`llm`/`model⇄rerank` 三组环，直连 14→11）；④-c **model 域边界收口**：`ModelGateway` 只读端口（消 `model ⇄ retrieval`）+ `UploadLimits` 纯规则搬 common（消 `knowledge ⇄ model`）；④-d **实体归位**：`StorageBackend`（storage_backends 表）从 `knowledge.domain` → `storage.domain`（10 文件引用）+ 清 `FileAccessResolver` 的死注入（消 `knowledge ⇄ storage`）；④-e **契约类型搬迁第一批**：`ChatManage` 的 4 个图形值类型 → `common/graph`；`SearchParams`/`ChunkTypes` → `common/pipeline`；`RetrievalObs` → `retrieval/obs`；`RetrieveGraphRepository` 端口 → `retrieval/graph`；`MessageAttachmentsPrompt` → `session`（消 `chatpipeline ⇄ retrieval`）；④-f-① `EntityExtraction`+`PipelineConfig` → `llm/extract`、`GoJsonMarshal`+`GoValueStr` → `common/web`（消 `chatpipeline ⇄ knowledge`）；④-g wiki 小簇（`WikiImageMarkup`/`WikiLanguageSupport`/`SlugUpdate`/`ExtractedItem`/**wiki 自己的 `GoStrings`**）→ `common/wiki` + `WikiIngestPort`/`WikiFinalizePort` 端口（消 `knowledge ⇄ wiki`）；④-h `SearchResult`（SSE 契约载荷、零域依赖）`retrieval.domain` → `common/retrieval`（消 `llm ⇄ retrieval`）；④-i `memory ⇄ session`：只读端口 `SessionMessagePort`（2 方法 + 视图，`MessageRepository` 实现）+ 删纯转发 `MemoryMessageReader` + `MemoryUsedMemories` 归位 chatpipeline（消 `memory ⇄ session`）；④-k `knowledge ⇄ retrieval` **已完成（环 5 → 4）**——先归位（`VectorStoreService` → `retrieval/engine`，引擎写面与读面合流；`ImageInfoEnricher`/`SearchChunkMerge` → `knowledge/support`），再端口组（`common/knowledge` 的 `KnowledgeBaseSearchGateway`/`KnowledgeDocumentGateway`/`ChunkSearchGateway` + `common/embedding` 的 `EmbeddingGateway`，全部由知识域实现；`HybridSearchService` 不再 import 知识域）；④-l `initialization ⇄ knowledge/model` **已完成（环 4 → 2）**——`ExtractPrompts` → `llm/extract`（与 `PipelineConfig` 成对，消 initialization⇄knowledge）、`AsrTranscriber` → `llm/asr`（复用 `LlmTransport` 的 provider 接缝，消 initialization⇄model，并顺带消掉 `retrieval → initialization` 违例）；④-m `agent ⇄ modelcontext` **已完成（环 2 → 1）**——`ToolResult` → `common/llm`（跨域协议载荷，与 `ResponseType` 同层）、`GoJsonCodec` → `common/web`（与 `GoJsonMarshal` 合流，`writeString` 转公开）；④-n `chatpipeline ⇄ session` **已完成（环 1 → 0）**——`common/session` 新增 4 个消息载荷记录（`PipelineMessageView`/`PipelineMessageImageView`/`PipelineMessageAttachmentView`/`PipelineUsedMemoryView`）+ `MessageAttachmentsPrompt` → `common/prompt` + 会话侧 `session/support/PipelineViews` 映射（端口实现/附件/用到记忆/事件落库）；余下 6 条 L2→L3 是合法方向（能力层正常使用业务域），属阶段 4 模块化 |
| 批 3 解环·传值 + 伴生类型（D+E） | provider 客户端只依赖配置值；引擎伴生类型归位 |
| P1/P2 分包子包 | 上表的域内二级结构 |
| P3 小修 | ✅ **已执行（2026-09-30）**：放错包 2→0、控制器直连仓储 4→0、真倒挂 3→0、顶层 package-info 31/31；余 40 处 controller→domain 属"响应装配读实体"（体检自标注为观察项） |
| 阶段 4 | 5 组贵重环 + 模块边界固化（`config`/L1 的物理模块化） |

## 4. 明确"别动"

- ~~`im` / `datasource`~~：**用户已定：保留**（2026-09-30，不再考虑删除）——其结构可按 §14 正常重构；
- `evaluation`：§2.5 待排期可选项；
- `agent` 根级 26 个文件：此前已裁定"扰动/收益比不划算"；
- §11 边界清单（租户配置 jsonb、auth 域、agent fixture、工具输出自有 schema、检索引擎索引文档、Go 工具面 5 类）。

## 5. 风险提示

**P1–P3 是可读性问题**（改动低风险、每步全绿可验证）；**P0 成环是架构问题**（动面最大）。
按 §3 红线"一次只动一个轴"，两者**不要混批**。
