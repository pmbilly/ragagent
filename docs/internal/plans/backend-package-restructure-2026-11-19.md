# 后端分包与目录结构重构方案（2026-11-19 起）

> **性质**：从 `docs/blueprint/architecture-blueprint.md` 抽取"后端分包与目录结构"一项单独实施，不是蓝图整体重建。
> **基线**：2026-11-19 的 `v0.11.x`。历史开发过程见 `docs/internal/handoff/archive/HANDOFF-v0.10.x-2026-11-19.md`。
> **指挥面**：批次表与执行记录在 `docs/internal/HANDOFF.md` §5 / §12，本文件只写方案与判据。

## 1. 目标

让 19 个业务域的目录与职责边界一眼可读，把"靠 Python 脚本维持的规则"改成"由 Java 测试表达"，为后续按域重写铺路。

不追求：蓝图 5.5 的 `entity / vo / bo / assembler` 全套命名，也不做蓝图 5.2 的 contract 解散。

## 2. 边界（越界即停）

| # | 边界 | 判据 |
|---|---|---|
| 1 | **不动前端** | 路由、请求/响应字段、错误码、SSE 帧格式全不变；1,339 金片 + OpenAPI 快照守 |
| 2 | **不动数据库** | 无 DDL、无迁移脚本、无表结构变更 |
| 3 | **不做 contract 解散** | `contract` 保持现状；`domains` 仍是单个 Maven 模块。**例外（2026-11-19 经决策后追加）**：P4-3~P4-5 把 21 个契约类型归位到引擎模块与基础域，属"内部减薄"而非"解散"——模块数、依赖图、`contract` 的存在性均未变（见 §4 末尾的说明） |

排除项（与上述边界冲突或属于域重写范畴）：行级安全与租户插件、表结构重建、JobRunr、PGroonga、Sa-Token、MCP 官方 SDK 替换、agentscope。

## 3. 为什么不做蓝图 5.5 的包名对齐

| 蓝图 5.5 | 现状 | 直接照做的后果 |
|---|---|---|
| `api/` = 对其他域公开的接口与 BO | `api/` 装的是 `*Adapter`（实现 contract 接口的适配器） | 语义相反：内部实现细节被标注成对外契约 |
| `entity/` = 持久化实体 | `domain/` 混了实体、值对象（`KnowledgeBaseChunkingConfig`）、事件消费者（`ChunkExtractConsumer`）、Go 兼容类（`WireValued`） | 改名即错标 |
| `dto/` 拆成 `vo/` + `bo/` | `dto/` 是控制器出入参 | 纯改名，无收益，diff 大 |

因此本次只做**职责归位**与**边界可执行化**，命名沿用 §8.2 的现有分层。

## 4. 五批计划

| 批 | 内容 | 验收 |
|---|---|---|
| **P0** | 三份只读基线：OpenAPI 快照、Bean 清单 + 端点清单、跨域引用清单 | 快照入库；后续每批 diff 必须为空或差异可解释 |
| **P1** | 结构规则先行：只补 2 条缺口规则（跨域直连、注解 SQL 棘轮）+ 扫描名单核对 | 先出现状违规清单（只量不改）；违规数只减不增 |
| **P2** | `api/` 里的 `*Adapter` 迁到 `adapter/`（32 个 / 11 域），纯移动、不改类名与逻辑 | 每域：编译 + 定向测试（clean 后确认条数）+ 三份快照 diff 为空 |
| **P3** | 类职责归位：合并 `*Ops`（50）/ `*Support`（21）/ `*Wiring`（13） | 该域定向测试 + 金片 + 全量闸门 |
| **P4** | `api/` 包语义纠正 + 域间边界改为测试校验 | 结构校验通过；跨域直连 2 → 1（余 1 条待解散时解） | ✅ 2026-11-19 |

**边界外扩展（2026-11-19 经决策后追加，不计入上表五批）**

| 批 | 内容 | 为什么追加 | 结果 |
|---|---|---|---|
| **P4-3** | 消 6 处基础域 → 业务域的**逆级依赖**（`MemoryConfig` → `contract.tenant`、`KnowledgeBaseProvisioner` → `tenant`、`DocReaderAdminPort`/`ParserEngineCatalogPort` → `settings`、新增 `platform.web.AcceptLanguages`） | 用户选择"评估 contract 解散"后，先清"下级依赖上级"这类结构违规 | 13 处里消 6、论证保留 3、其余 4 非逆级 |
| **P4-4** | `contract.vectorstore` 的 8 个类型 → `retrieval-engine/retrieval/api`（留 `ConnectionConfig`/`IndexConfig`） | 同上，验证"引擎面归位"的流程 | 模块图新增 `domains → retrieval-engine` 边 |
| **P4-5** | `contract.retrieval` 的 13 个类型 → `retrieval/api`（留 `SearchParams`/`SearchResult`/`SearchTarget`/`RetrieveGraphRepository`） | 同上 | 包级 SCC 5 → 4 组 |

**这三批不属于 §2 边界第 3 条的原意（"contract 保持现状"）**，是决策后追加的。共同结论：`contract` 仍在、模块数仍是 7、依赖图只多一条边——所以它们没有改变"不做 contract 解散"这个决定，只把 21 个类型的归属挪到了更合适的位置。详细判据见 §9.7。

### 4.1 P1 的规则：先盘点已有，只补缺口

2026-11-19 实测：`backend/app/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java` 已有 **22 条规则**（A1~A20 + B257/B260），原计划的四条里三条已被覆盖：

| 原计划规则 | 现状 | 结论 |
|---|---|---|
| `mapper` 只被 `repository` 引用 | A11（Mapper 必须在 `..mapper..`）+ A11b（mapper 包只放 MyBatis-Plus 接口）+ A16（service 不引 mapper）+ C10（controller 不引实体） | **已覆盖，不新增** |
| `JdbcTemplate` 白名单 | A7（裸 JDBC 只许白名单类） | **已覆盖，不新增** |
| 主码零注解 SQL | 无。现状 281 条注解 SQL（含 72 条 `<script>`） | 改为**棘轮**（只减不增）；一次改 281 条不是分包任务 |
| 跨域不引对方 `repository` / `mapper` / `domain` | 无。`check-domain-contracts.py` 只管"域间只经 contract"，B257 只管两个域 | **P1 唯一要新增的规则** |

所以 P1 实际只有两件事：

1. **新增 ArchUnit**：跨域不得 import 对方的 `repository` / `mapper` / `domain` / `service` / `controller` 子包（`api` 与 `dto` 方向逐条实测后决定）。
2. **新增注解 SQL 棘轮**：基线取当前条数，只减不增。

另有三个"搬家漏登记"的实测事实，进 P1 核对清单：

- **`@ConfigurationPropertiesScan` 列了 11 个精确包**（在 `RagAgentApplication`），类移出名单即**配置静默不绑定**——无编译错、无启动错、属性为 null。这比日志级别更隐蔽，是本次最该盯的一项。
- `@MapperScan("com.ragagent.**.mapper")` 是通配，Mapper 只要留在任一 `mapper` 包内即安全，且 A11 已守着。
- 全仓**没有 `@Aspect` 类**（实测 0），"切点写包路径导致静默失效"这类风险当前不存在；将来引入切面时再登记。

### 4.2 P2 顺序（按 `api/` 下的 Adapter 数从小到大）

> 2026-11-19 修正：原计划"按域文件数从小到大逐域移动"没有目标——P0 实测 19 个域的分层已统一，不存在错位目录。唯一语义错位是 `api/` 装的是 `*Adapter`（蓝图 5.5 的 `api/` 应为对外契约）。故 P2 对象改为这 32 个 Adapter，原 P4 的语义纠正由本批完成，P4 只剩"域间边界改为 ArchUnit 校验"。

| Adapter 数 | 域 | 状态 |
|---|---|---|
| 1 | `agentmanagement` | ✅ 2026-11-19 |
| 1 | `audit` · `memory` · `model` · `wiki` | ✅ 2026-11-19 |
| 2 | `mcp` · `websearch` | ✅ 2026-11-19 |
| 3 | `auth` | ✅ 2026-11-19 |
| 4 | `storage` | ✅ 2026-11-19 |
| 5 | `session` | ✅ 2026-11-19 |
| 11 | `knowledge` | ✅ 2026-11-19 |

无 `api/` 的 8 个域跳过：`favorite` · `system` · `vectorstore` · `embedchannel` · `im` · `datasource` · `agenttools` · `initialization`。

每域一批、独立提交。`api/` 下的非 Adapter 类（`AuthUserErrors`、`CreateUserResult`、`FinalizeResult`、`MemoryAccess`）原地不动——它们是域契约。

### 4.3 已知阻塞项

`knowledge ⇄ wiki` 域级循环。P2-7 移动后确认**两边都是实现类直连**（不是 Port）：

| 方向 | 位置 | 处数 |
|---|---|---|
| `knowledge -> wiki.service` | `KnowledgeProcessWiring` / `KnowledgeFinalizingStage` / `ChunkExtractService` / `QuestionGenerationService` | 7 |
| `wiki -> knowledge.adapter` | `DefaultWikiChunkCleaner` / `DefaultWikiKnowledgeFinalizer` / `WikiIngestBatchHandler` / `WikiIngestCitePipeline` / `DefaultWikiImageEnricher` | 5 |

解环方向：`wiki` 侧改为注入 `contract.knowledge` 的 `ChunkPort` / `KnowledgeFinalizePort`（Adapter 已实现这些契约，按接口注入即可，不动逻辑）；`knowledge` 侧改依赖 wiki 声明的回调端口或下沉为事件。必须在 P3 之前单独一批完成，不与包移动混做。

> **✅ 已解（P3-0，2026-11-19）**：新增 `contract.knowledge.ChunkPort` / `KnowledgeFinalizePort`、`contract.wiki.WikiIngestPort` / `WikiKnowledgeFinalizer`；上提 `FinalizeResult` 与 `WikiEnqueueResult`。R-1 基线回到 2 条边，跨域快照 13 → 10 条。实施细节与三处踩坑见 `HANDOFF.md` §12 的 P3-0 行。

## 5. "搬家漏登记"风险清单

编译期抓不到、运行期才炸的项，逐条附 2026-11-19 实测状态：

| 项 | 实测状态 | 后果 | 检出方式 |
|---|---|---|---|
| **`@ConfigurationPropertiesScan` 的精确包名单** | 存在，**列了 11 个包** | 类移出名单 ⇒ 配置**静默不绑定**（属性 null，无报错） | Bean 清单 `configurationPropertiesScan` + A2 |
| `@MapperScan` | 通配 `com.ragagent.**.mapper` | 移出任一 `mapper` 包才失效 | A11 已守 |
| `@ComponentScan` 根包 | `@SpringBootApplication` 扫 `com.ragagent.**` | 移出根包 ⇒ Bean 静默消失 | Bean 清单 `configurations` diff |
| `AutoConfiguration.imports` / `spring.factories` | 1 个 `spring.factories`（boot），0 个 `.imports` | 类名写死处改动即启动失败（响亮） | 启动冒烟 |
| AOP 切点写包路径 | **全仓 0 个 `@Aspect`** | 当前无此风险 | 清单 `aspects` 为空即证明 |
| `logging.level.com.ragagent` | 1 条（`com.ragagent: ${LOG_LEVEL:INFO}`） | 日志静默变少 | 清单 `loggingLevels` |
| Jackson 多态按类名做 type id | 由金片兜底 | 输出类型名变化 | 金片 |

最隐蔽的是第一项：它既不会编译失败，也不会启动失败，只在运行到该配置时表现为"取不到值"。界面录屏同样抓不到，所以本次不采用浏览器录制。

## 6. 为什么不用界面录制验收

"用户可见行为不变"可以拆成三层，每层都有更便宜、更精确的抓法：

```
用户看到的
├── 页面长什么样 ── 前端不改（人工走查清单，迁移前后各一遍）
├── 接口返回什么 ── 1,339 契约金片 + OpenAPI 快照 diff（零成本，可重跑）
└── 检索/回答什么 ── 检索快照 + SSE 事件对拍（脚本级）
```

浏览器录制的价值是"可重复回归"，不是"一次性证明"；本次改动面在后端内部，界面出错的主因只可能是接口或检索变化，两者都有脚本级证据。待第二阶段真正改界面时再做 Playwright。

## 7. 验收标准

1. 全量闸门 `mvn -B clean verify` 绿，测试条数守恒（不因移动而少跑）。
2. 48 条守卫全绿，`check-reference-truth` / `check-package-dirs` / `check-package-cycles` 零断链、零错位、零新增环。
3. 三份快照 diff 为空，或差异逐条有解释。
4. 主码无 `*Ops` / `*Support` / `*Wiring` 后缀类（P3 完成后）。
5. 跨域对 `repository` / `mapper` / `domain` 的引用数为 0（P4 完成后）。

## 7.1 P3 回引普查方法（2026-11-19 修正）

"大类靠回引拆分"的实际形态是构造器里 `new Xxx(this)`。**按类名后缀（`*Ops`/`*Support`）统计会错两次**：混入接口（`WikiDedupSupport` 其实是端口）、漏掉 `*Phase`/`*Dedup`/`*Repair` 等命名。正确口径是直接扫创建点：

```bash
grep -rnF "(this)" --include="*.java" domains/src/main/java/com/ragagent | grep -F "new "
```

实测全仓 **70 处**：wiki 16 · mcp 12 · datasource 11 · memory 10 · im 7 · auth 6 · session 6 · knowledge 4。

每个回引点先判两件事再动手：

1. **协作者用了父类哪些成员**（`owner.repo` / `owner.updateXxx(...)`）——纯字段依赖可直接改构造参数；调父类方法说明有**双向调用**，必须先拆。
2. **父类是否也调它**——双方互调时，提升 Bean 会触发 Spring 循环依赖，必须先搬走其中一侧的实现。

改法有两种，按协作者是否被父类以外的地方使用来选：

| 改法 | 适用 | 代价 |
|---|---|---|
| **轻量**：仍 `new Xxx(...)`，但传实际依赖 | 只在父类内部使用的协作者 | 父类构造参数不变，风险最低 |
| **提升**：`@Service` + 父类注入 | 被多处调用、需要独立装配的协作者 | 父类构造参数 +1；多个协作者同时提升会撞"≥8 参构造器"红线 |

### 7.2 回引不必全消：三分类判据（wiki 实测，2026-11-19）

wiki 的 17 处回引逐一评估后，只有一类值得改：

| 类型 | 特征 | 处置 | wiki 实例 |
|---|---|---|---|
| **纯依赖** | 只用父对象的注入字段（repo / lookup / provider） | **改**：构造注入实际依赖 | 10 个已改 |
| **编排/阶段** | 用父对象 6–13 个成员，且成员之间互相协作 | **保留**：拆开会把参数摊成 8–13 个，破坏阶段共享上下文 | `MapPhase`(6) · `ReducePhase`(4) · `FinalizePhase`(8) · `RunSupport`(13) |
| **运行态状态** | 用父对象的可变状态（单飞锁 / 缓存 / 线程池） | **保留**：外传等于共享可变状态 | `WikiIngestLlmSupport` |
| **父类真实业务** | 调父类的真实业务方法，且该方法属于对外接口 | **保留**：搬走会与接口契约冲突或形成循环依赖 | `WikiPageRevisionOps`（`updatePage`）· `WikiPageViewsSupport`（`getIndex` / `ingest` / `listIssues`） |
| **编排工厂** | 需要父类的上下文构造方法（如 150 行的 `newWikiBatchContext`） | **保留**：把工厂搬进"依赖容器"会让容器兼当工厂，等价于 god class 换名 | `WikiIngestRunSupport` · `WikiIngestFinalizePhase` |

最终结果：wiki 17 处回引 → **13 消 / 4 留**（4 留分布在"父类真实业务"2 与"编排工厂"2）。

判据一句话：**看协作者需要父对象的哪一部分**——只有"注入依赖"可外提；"编排、状态、业务方法"外提只是把耦合换个地方。

**可操作形式（2026-11-19 最终核验）**：统计协作者引用的父类成员里，字段（F）与方法（M）各几个。**只有 M = 0 才可改**。

对全仓剩余 17 处逐条统计，**全部 M ≥ 1**，没有一处是纯字段依赖：

| 类 | F | M | 卡在哪 |
|---|---|---|---|
| `MemoryInsightOps` | 4 | 8 | `enabledScope` / `extractModelId` / `recallSelector` 等 |
| `UserSessionOps` | 3 | 8 | 父类就是它唯一依赖（传 `this` 等于传依赖本身） |
| `MemoryCatalogOps` | 2 | 5 | `enabledScope` / `enforceCapacity` / `rebuildBlock` |
| `MemoryRecallOps` | 2 | 3 | `enabledScope` / `scopeDisableReason` |
| `OAuthTokenOps` | 3 | 3 | `getResourceUrl` / `getServerMetadata` / `stateLock` |
| `NotionFetchOps` · `FeishuDriveOps` · `FeishuWikiTreeOps` | 0 | 2 | client 的 HTTP 与分页方法 |
| `AuthSessionOps` | 1 | 2 | `invalidParams` / `bindingError`（web 工具） |
| `WikiPageViewsSupport` | 3 | 2 | `getIndex` / `listIssues` |
| `WikiIngestFinalizePhase` | 0 | 2 | `getKnowledgeBaseByIdOnly` / 锁 |
| `EmbedChannelDelegateOps` | 3 | 2 | `ensureSession`（读 `request0()`） |
| `DataSourceSyncExecutor` | 8 | 1 | 8 个字段可传，但 +1 后 9 参超红线 |
| `AuthOidcOps` | 3 | 1 | `resolveDefaultTenantMode` |
| `WikiPageRevisionOps` | 1 | 1 | `updatePage`（65 行业务链） |
| `SessionQaResolution` | 0 | 1 | `buildSearchTargets`（构造 5 个子协作者） |

另一条易误判：**方法参数不算回引**。`ImOutboundFormatter` 里的 `adapter.sendReply(...)` 中 `adapter` 是方法参数，不是父类成员——它只有 `storageResolver` 一个字段依赖，已改（P3-13）。核对前缀是字段还是局部变量/参数，可避免这类误判。

**判据的术语来源**：这套判据不是自创规则，背后是三条标准原则——依赖倒置（依赖抽象而非实现）、接口隔离（端口要窄，如把 10 个方法拆成查询/写两个端口）、迪米特法则（只和直接朋友说话：`service.repo.getBySlug(...)` 属"通过熟人拿陌生人"，改成自己持有 `repo`）。"构造只依赖窄接口"只是对这三条的中文概括。

**不设自动化守卫的理由**：`Service` 类互相注入是合法的（编排者本来就要调下游），按"构造参数是 `*Service`"判会把正常注入一起判红；且这种回引是历史包袱的产物，新代码很难自然写出（Spring 自动给依赖，没人会 `new X(this)`）。已有 `check-file-size`（类太大就红）会逼着人拆，拆时自然会想到不传 `this`。故本条落为**人工评审判据**，不入守卫。

**待 contract 解散时解决的最后一条边**：`session -> agentmanagement.service`（仅剩 `AgentConfigJson` 的 4 处静态调用）。它同时被两条约束卡住——`contract` 零第三方依赖（不能带 Jackson）、`platform` 的 C8 禁业务词文件（`Agent`）。正解是把 `CustomAgentConfig` 契约化到 `contract.agent`，届时随解散归位到 `agentmanagement/api`，`AgentConfigJson` 作为该域工具被 session 通过契约类型访问。现已登记在 R-1 基线（1 条）。

**搬迁陷阱（2026-11-19 实测，`KnowledgeSearchRanking`）**：父类对构造参数做兜底时（`this.config = config == null ? SearchConfig.defaults() : config`），如果给新协作者传的是**形参**而不是兜底后的**字段**，测试传 `null` 就会：父类字段有默认值、协作者里是 `null`，运行期 NPE。正确写法是 `new Ranking(this.config, ...)`，不是 `new Ranking(config, ...)`。

这类 bug 编译期完全看不出来，只有"用 null 走默认值"的测试能抓住——所以搬迁后必须跑该域的全量测试，不能只跑编译。

### 7.3 注释判据（2026-11-19，以 `UserSessionOps` 为样板）

仓库已有 `check-comment-narrative.py` 拦"历史叙事"（原先 / 后来 / 曾），但抓不到另外三类。判据一句话：**写了之后，读者能多知道什么？** 答不出来的就删。

**该写的四类**

| 类型 | 例子 | 为什么留 |
|---|---|---|
| **为什么用这个 API** | `parseSignedAllowExpired`：过期令牌也能解出 user_id | 换成 `parseSigned` 会破坏"不必先 refresh 再登出" |
| **为什么是这个顺序** | 偏好先落库再签发（200 必须对应持久化成功） | 顺序错了要回滚，读代码看不出这层业务约束 |
| **容错策略** | 旧 refresh 吊销尽力而为，失败只记日志、不拖垮切换 | 说明这里是**故意**吞异常，不是漏写 |
| **刻意的同值设计** | 记录缺失与已吊销返回同一文案 | 防探测，换文案就是安全回归 |

**该删的四类**

| 类型 | 例子（`UserSessionOps` 改前） | 问题 |
|---|---|---|
| **复述下一行代码** | `用户不存在 → 透传 "record not found"` | 下一行 `throw` 字面就是它；代码一改注释必失真 |
| **翻译方法体** | `校验成员关系 → 记偏好 → 签发 → 吊销` | 读代码比读这句话快 |
| **历史叙事** | `自 {@code UserService} 外提` | 新读者没有这段上下文 |
| **把调用方实现写进契约** | `controller 包成 500 "Logout failed"` | 控制器改文案即过期；应写"由调用方映射为响应" |

**两条可自动检查的硬判据**

1. **注释里的标识符必须存在**：`{@code user}` 而字段实际叫 `userService`，属失真——比没有注释更糟，读者会去找一个不存在的名字。可做成守卫：扫 `{@code}` / `{@link}` 的简单名，检查同文件声明或 import 是否存在。
2. **`@throws` 的类型名必须能解析**：写 `LogoutException` 但实际是 `UserService.LogoutException` 时 Javadoc 链接是断的。`javadoc -Xdoclint` 即可覆盖。

**类注释的三段模板**（`AdminUserPort` 为样板）

1. **职责**：一句话说这是什么（"管理员用户的查询端口"）
2. **为什么这样切**：架构约束，不是历史（"system 需要读管理员用户，但不依赖 auth 的实现类"）
3. **边界语义**：读者会踩的坑（"查不到返回 `null`，不抛异常"）

历史与过程一律进提交信息，不进注释。

**守卫扩展（2026-11-19，全仓零存量）**

`check-comment-narrative.py` 原有五类（退役 / 备案 / Go 锚点 / 迁移期 / 旧实现），新增三类：

| 模式 | 反例 | 为什么能拦 |
|---|---|---|
| 过程记录 | `被架构规则抓出后提为本端口` | "怎么发现、怎么改"属于提交信息 |
| 基线术语 | `棘轮基线` / `登记为基线` | 守卫的内部实现细节，读者不需要 |
| 演化叙事 | `此前 system 直接注入…`（与"改成/迁移/上提"共现时） | 过去状态 + 变更动作同句出现是强信号 |

**故意不拦**：「此前」「原先」单独出现（脚本第 27 行说明：普通时间副词，误报率高）；规则编号（`R-1` / `A7`）也不做正则——实测 218 处命中里绝大多数是 `H2`、`PG`、`S3` 这类技术名。

**写作时自检一句**：如果这个类型是三年前写的，这句话还成立吗？不成立的删掉。

**格式**：方法之间 1 个空行（与同目录其他文件一致）。`@return` 若为数组，必须写明下标含义（`[0] access token，[1] 新 refresh token`），不能只写 `{a, b}` 让人猜顺序。

### 9.7 实测结论：contract 解散在现架构下不可行（2026-11-19）

统计 24 个契约包的**主码消费者模块**：

| 消费模块数 | 包 |
|---|---|
| 1（仅 domains） | `doc` · `settings` |
| 2 | `memory` · `websearch` · `auth` · `apikey` · `pipeline` |
| 3 | `knowledge` · `session` · `audit` · `graph` · `tenant` |
| 4+ | `wiki` · `storage` · 通用面 `error` / `model` / `retrieval` / `agent` / `tool` |

再往下看"仅 domains"的两个包被几个**域**使用：

- `doc`：`im`(2) · `initialization`(1) · `knowledge`(3) · `session`(2) · `system`(2)
- `settings`：`auth`(4) · `system`(1)

**结论**：24 个包里没有一个只服务单一域。contract 不是"错把域契约放进公共模块"，而是确实承担了**跨域共享词汇**的职责。因此"域专属契约搬回各域"这条路线不成立。

解散的真正前置条件是**先改变域间通信方式**（事件驱动，或把共享词汇变成真正的通用概念），那属于架构重写，不是搬包。在现架构下执行解散，只会把跨域依赖从 `contract` 挪到某个域，形成新的双向耦合。

**替代方向（成本低、可分批）**：

1. 保留 `contract` 作为共享词汇层，用 R-1 规则约束（已完成）。
2. 若目标是"减薄"，逐包检查内部**只被 1 个域使用的类型**，把它下沉回该域——不需要解散 contract，也不会产生新的跨域边。这是可以逐步推进的。

**减薄空间同样很小（2026-11-19 实测）**：

| 口径 | 数量 | 占 309 个契约类型 |
|---|---|---|
| 只被 1 个**模块**引用（含引擎） | 149 | 48% |
| 其中只被 `domains` 引用 | 134 | 43% |
| **真正单域专属**（1 个模块 · 1 个域 · 不被引擎用） | **23** | **7.4%** |

23 个的分布：`session` 7 · `knowledge` 6 · `memory` 4 · `im` 2 · `wiki` / `storage` / `agenttools` / `embedchannel` 各 1。

也就是说：134 个里多数被 2 个以上域共享，属真正的跨域词汇。**contract 不是放错了地方，它确实承担了共享词汇层的职责**。

结论：不解散、也不值得为 7.4% 做一轮下沉。contract 的定位应写成"跨域共享词汇层"，边界由 R-1 约束（已完成），而不是"待解散的临时模块"。

**闭包修正（2026-11-20，S5 实测）：7.4% 这个数仍然偏高**

"单域专属"是**按类型单独统计**的，但搬迁有闭包约束——contract 不能反向依赖 app，所以一个类型能否离开 contract，前提是**contract 内没有其他类型引用它**。

拿 `contract.session` 的 6 个"单域专属"类型试搬，编译立刻失败：`MessageView` / `MessageSuggestionView` / `SessionQaPort` 用**简单名**引用它们（同包，本来不需要 import）。按闭包重算，这 6 个牵出 16 个类型（`SessionQaPort → AgentEngineFactory` 等），**一个都动不了**。

用"单域 + 无 contract 内部引用 + 不被引擎模块引用"三条同时约束重扫 288 个类型：

| 判据 | 数量 |
|---|---|
| 无 contract 内部引用 | 89 |
| 其中只被 1 个模块用（app） | 32 |
| **同时只被 1 个域用** | **2** |

这 2 个还都是语义错位而非位置错位（`OutputBudgets` 在 `contract.agent` 但只被 agenttools 用；`ToolCapabilities` 在 `contract.tool` 但只被 im 用）——按语义它们本来就该待在原包。

**最终结论：contract 减薄的实际空间是 0.7%（2/288），且没有一处是"放错了"。** 这条路线到此关闭，不再重提。

## 8. 不在本次范围

H7 / H8 / H9 / L11 / Q6 / Q8b / Q9 / Q10 / Q11 / R2c-3d 余项 / M8（见 `HANDOFF.md` §5.1）。已否决项见归档 §5.0b。

## 9. 终态与留路（假设性：contract 解散后长什么样）

## 9. 终态与留路（假设性）

> 本节是早期设想。**权威路径以 §12 为准**：`contract` 已并入 `framework/shared`，解散的实测分解（35 通用面 + 189 业务词汇）与六阶段方案见 §12。

## 9. 终态与留路（contract 解散后长什么样）

> 本节即**目标终态**。可达性判定与前置条件见 §10：**可达**，但必须先完成 P1（把 30 个业务邻接文件搬出 `platform`）——它们目前把 38 个契约类型钉死在 contract。
> 早期曾据「剥洋葱」误判为不可达，原因见 §10.1 的错判说明。

> 本节记录长期目标，以及本次方案如何为它铺路。**本次不做 contract 解散**，本节只用于判断"现在这一步会不会返工"。

### 9.1 本次实施后的形态（7 模块，contract 仍在）

```text
ragagent/
├── contract/                      只剩「引擎面 + 通用面」
├── platform/                      技术适配（不改）
├── model-runtime/  retrieval-engine/  agent-engine/
├── domains/                       19 个域，目录不变
│   └── <domain>/
│       ├── api/                   ← P4 后：域对外契约（现在装 *Adapter）
│       ├── adapter/               ← P4 新增：*Adapter 从 api/ 迁出
│       ├── controller/ service/ repository/ mapper/ domain/ dto/ config/
└── boot/                          装配（不改）
```

目录基本不变，变的只有 `api/` 里装什么。

### 9.2 contract 解散后的终态（5 模块）

```text
ragagent/
└── backend/                       1 个 jar，api / worker 两角色
    ├── framework/                 现 platform + 通用面（error · event · json · text · pagination）
    ├── model-runtime/api/         ← contract.model
    ├── retrieval-engine/  api/ + spi/   ← contract.retrieval · vectorstore
    ├── agent-engine/      api/ + spi/   ← contract.agent · tool
    └── app/                       现 domains + boot/config
        ├── <domain>/              api/ controller/ service/ repository/ mapper/ domain/ dto/ adapter/ config/
        └── config/                现 boot 的 17 个装配类
```

模块数 7 → 5；`contract` 消失；`domains` 改名 `app`；`platform` 改名 `framework`；`boot` 并入 `app`。

### 9.3 24 个 contract 包的去向（实测消费者）

统计口径：各模块 `src/main/java` 中 `import com.ragagent.contract.<包>.` 的文件数（2026-11-19 实测）。

| 去向 | 包 | 消费者模块数（主要） |
|---|---|---|
| **framework** | `error` | **5**（domains 125 · model-runtime 32 · platform 6 · retrieval 4 · agent-engine 4） |
| | `event` `json` `text` `pagination` | 1–3；`text` / `pagination` 只有 domains |
| **model-runtime/api** | `model` | 5（model-runtime 51 · domains 101 · agent-engine 26 · platform 14） |
| **retrieval-engine/api** | `retrieval` `vectorstore` | 6 / 4 |
| **agent-engine/api+spi** | `agent` `tool` | 3 / 4 |
| **各域 api** | `knowledge` `wiki` `memory` `session` `storage` `tenant` `auth` `apikey` `audit` `settings` `websearch` `doc` `graph` | 1–4，主体是 domains |
| **待定** | `pipeline` | domains 27 + agent-engine 3；建议随 `session/api`，agent-engine 走 spi |

### 9.4 本次三个动作如何留路

| 本次动作 | 对解散的作用 |
|---|---|
| P1 规则 4（跨域不引对方 `repository` / `mapper` / `domain`） | Modulith 边界规则的预演，将来直接换校验器 |
| P3 消掉 `*Ops` / `*Support` / `*Wiring` | 域内不再有外挂依赖，`api/` 才有资格承载契约 |
| P4 把 `*Adapter` 从 `api/` 迁到 `adapter/` | **唯一有顺序要求的一步**：`api/` 必须先腾空，否则域契约搬进来会和 Adapter 混住 |

结论：本次实施后解散 contract 是"加一层"（删模块 + 改 import），不是"改一遍"。

### 9.5 解散的起点不是搬域契约

> **2026-11-19 实测修正**：本节原判"先把 `error` / `event` / `json` / `text` / `pagination` 下沉 framework"，逐一验证后**五个都不成立**：
>
> | 包 | 结论 | 原因 |
> |---|---|---|
> | `error` | 不沉 | `AppError` / `ErrorCode` 是 **API 契约**（前端按 code 分支），不是技术实现 |
> | `event` | 不沉 | contract 侧是接口（`EventBusInterface` / `EventHandler`），platform 侧是同名**实现**包，属契约/实现的正当分层 |
> | `text` | 不沉 | 同上：contract 是 `Whitespace` / `Tokens` 契约常量，platform 是 `UnicodeText` / `HtmlText` 实现 |
> | `json` | 不沉 | 被 contract 内部 **5 处**引用（`SearchResult` / `AgentStep` / `JsonValueSupport` 等）；下沉会让 contract 反向依赖 platform——实测编译失败，守卫同时报"新环/分层违例" |
> | `pagination` | 不沉 | `PageResult` 是域间传输形状 |
>
> 结论：**contract 是全仓唯一的零依赖基础层**。凡被 contract 内部引用的类型都不能下沉（会造成反向依赖）；其余"纯契约"本就是它的职责。解散的正确起点不是"削薄通用包"，而是"把域专属契约搬回各域"——但 24 个包里只有 4 个仅被 domains 消费，其余跨模块，需逐包确认消费者后才能动手。

24 个包里只有 4 个（`doc` / `pagination` / `settings` / `text`）只被 domains 消费；其余 20 个全部跨模块，`knowledge` 还被 `retrieval-engine` 与 `platform` 各用 4 处。

正确顺序：**先把 `error` / `event` / `json` / `text` / `pagination` 下沉 framework**（技术面、无业务形状、不动任何域），contract 剩下的才是纯中介，之后按引擎面、域面两块搬。该步骤不在本次范围，登记为解散批次的第一刀。

### 9.6 两个前置坑

1. **包根不统一**：`domains` 的源根是 `com.ragagent.<域>`，不是 `com.ragagent.domains.<域>`。改名 `app` 时若顺手统一包根，会触碰全部 1,642 个文件 —— **只改模块名，不动包根**。
2. **`boot` 并入 `app`** 后，17 个装配类落到 `app/config/`，`check-wiring-points.py`（W1~W4）的登记表与 `docs/architecture` 的装配清单要同步。

## 10. 终态与实施路径（B 路线：放宽 R10 + 域分级）

> 2026-11-20 决策：选 B（贴合蓝图 §5.4/§5.5）。本节给出完整归属清单与五步执行计划，先验证再动手。

### 10.1 为什么 contract 搬不空——根因是 R10 比蓝图更严

`check-domain-contracts.py`（R10）规定**跨域不得直接 import**。而蓝图：

- §5.5 `api/` = 对其他域公开的接口与 BO
- §5.4 域分三级，**只允许上级依赖下级**；同级互相需要时，依次考虑下沉到基础域、改用事件、合并

两者冲突。R10 一刀切的结果是：任何被 ≥2 个域使用的类型都无处可去，只能堆在 `contract`（或 platform）。
实测：`platform` 的 30 个"业务邻接"文件里，**只有 3 个**能被搬进单一域，24 个被多域共用而必须留在原地。

**结论**：不先放宽 R10，任何搬迁都会卡住；放宽后，契约才有家可归。

### 10.2 域分级（19 个业务域 + config）

| 级 | 域 |
|---|---|
| **L1 基础域** | `tenant` · `auth` · `system` · `storage` · `model` · `vectorstore` · `websearch` · `audit` · `initialization` |
| **L2 业务域** | `knowledge` · `wiki` · `datasource` · `agentmanagement` · `mcp` · `memory` · `favorite` · `im` · `embedchannel` |
| **L3 编排域** | `session` · `agenttools` |

规则：**只允许上级 import 下级**（L3→L2→L1），且目标必须是对方的 `api/`（或 `dto/`）契约包；禁止下级→上级、禁止同级互相 import。

### 10.3 每个契约类型的归宿（造 288 个实测；已含 kind 约束）

判据：home = 消费方里**级别最低**的那一方（这样所有其它消费方都是"上级"，都允许 import 它）；
若最低级别有多个域（同级互需）→ 下沉 `framework`；若被 `platform` 用 → `framework`。

| 去向 | 数量 | 说明 |
|---|---|---|
| `framework` | **92** | 被 platform 引用，或跨模块共享（`error` / `event.payload` / 通用端口） |
| `framework`（同级下沉） | **43** | 被同级的多个域共用，如 `AdminUserPort`（auth+system）、`APIKeyScopeContext`（auth+agentmanagement+knowledge+initialization） |
| `agent-engine/api` | 24 | `contract.agent` / `tool` 主体 |
| `model-runtime/api` | 16 | `contract.model` 主体 |
| `retrieval-engine/api` | 13 | `contract.retrieval` / `vectorstore` |
| `app/<域>/api` | **49** | `knowledge` 19 · `memory` 9 · `wiki` 8 · `session` 7 · `agentmanagement` 2 · `im` 2 · `embedchannel` 1 · `storage` 1（另有 `agenttools` 1 已搬） |
| 无引用 | 10 | 需确认死代码后删除 |
| 待定 | 2 | 消费方含 `config` / `_root`，归属需人工判定 |

**共享层从 288 降到 173（-40%）**，53 归引擎，49 归域，10 个无引用待清理。

**第二层约束：kind（能力种类）**。实测搬 `WikiActivityAudit`（`contract.audit` → `app/audit/api`）时被
`check-capability-kinds.py` 拦下：`wiki`（product）→ `audit`（control）属**跨 kind 直连**，同样禁止。
故归属判据必须同时满足两条：① 级别上级→下级；② **同一 kind**。跨 kind 共用的一律下沉 framework——
这也是 `framework(跨 kind)` 从 43 涨到 56 + 同级 25 = 173 的原因。

### 10.4 五步执行计划

| 步 | 内容 | 规模 | 验收 |
|---|---|---|---|
| **B1** | 落地域分级：`scripts/capability-kinds.json` 加 `level` 字段；`check-domain-contracts.py` 从"禁跨域"改为"禁下级→上级 + 禁同级，允许上级→下级 `api`" | 19 域 + 1 守卫 | 现状仍 0 违规（当前无跨域 import，规则收紧前先证明不误报） |
| **B2** | 建 14 个 `app/<域>/api` 包（含 `package-info`），搬 88 个域专属契约 | 88 类型 | 每域一次全量；`contract` 剩余数单调递减 |
| **B3** | 引擎面归位：`agent` 24 + `model` 16 + `retrieval` 13 → 三个引擎的 `api/`，mixin 随类型搬（S1 机制） | 53 类型 | 金片 1339 逐字节不变 |
| **B4** | 135 个共享类型下沉 `framework`；`platform` 改名 `framework`；删 `contract` | 135 类型 | `contract` 目录为空可删 |
| **B5** | 目录前移 `backend/`；CI 与 24 个脚本路径；ArchUnit/Modulith 接管边界校验 | — | `mvn -f backend/pom.xml clean verify` 绿 · 守卫 49/49 |

合计 **10~14 天**。B1 是闸门：它不改任何业务代码，但决定后面能否搬。

### 7.4 搬迁陷阱三：目标类被 platform 的**包私有**内部类钉住（2026-11-20 实测）

想解绑 `contract.model` 剩下 12 个 LLM 协议形状（`ChatMessage` / `ChatStream` / `StreamResponse` /
`ResponseType` 等），必须先搬走 `platform/tracing/decorators`（6 个 Langfuse 装饰器）——它们是这些形状在
platform 侧的唯一使用者。

**结果：搬不动。** 装饰器的测试 `LangfuseModelDecoratorsTest` 在 platform，它直接使用 platform
`tracing.langfuse` 的 5 个**包私有**类（`DefaultLangfuseManager` / `LangfuseContext` / `LangfuseRegistry` /
`NoopLangfuseManager` / `RecordedSpan`）。测试随装饰器搬到 `model-runtime` 后，这 5 个类都不可见，编译失败；
而测试留在 platform 又会让 platform 依赖 model-runtime（反向边）。

**判据**：搬一类文件前，先看它的**测试**是否依赖同模块的包私有类型；是则搬运等价于一次可见性重构。
**前置**：把 `tracing.langfuse` 的 5 个类提为 public，或提供公开的测试支撑 API。

因此 `contract.model` 剩余的 23 个类型，短期无法再瘦身：12 个协议形状被 decorators 钉住、
3 个被 Jackson mixin 注册、5 个被 `pipeline`/`session`/`retrieval-engine` 引用。

### 7.5 搬迁陷阱二：目标模块内部有环时不可迁入契约（2026-11-20 实测）

试搬 `contract.model` 的 4 个 Ollama 类型（`OllamaAdminPort` / `OllamaModelInfo` / `OllamaPullProgress` / `OllamaProgressListener`）到 `model-runtime`，两次放置都新增包级环：

| 放置位置 | 结果 |
|---|---|
| `com.ragagent.modelruntime.api` | 新增环 `llm ⇄ modelruntime`（守卫把 `modelruntime.*` 折叠成一个包） |
| `com.ragagent.model.api` | 新增环 `llm ⇄ model` |

**根因在目标模块自身**：`model-runtime` 的 `llm`（实现）与 `modelruntime`（装配）本来就互引；契约放进该模块的任何新包，都会因为「实现包 import 它」而并入那个既有的 SCC。

**判据**：搬契约到某模块前，先跑 `python3 scripts/check-package-cycles.py` 看该模块内部是否已有环；有环则先解内部的环。`retrieval-engine` 能成功（P4-4/P4-5）正是因为它内部无环。

**解法**（2026-11-20 验证）：把契约放到**不会产生反向边**的命名空间。实测 `com.ragagent.llm.api` 可行——`model-runtime` 内部只存在 `modelruntime.* → llm.*` 单向依赖，把 api 放进 `llm` 命名空间下，`llm.ollama → llm.api` 属包内引用，不产生新的跨包边。故 4 个 Ollama 类型已归位到 `llm.api`，包级环仍为 0。

仍需延后的是 `PromptCacheStatus`：它被 platform 的 `ContractJsonModule` 显式注册，须与 mixin 一起搬。

其中 `PromptCacheStatus` 另有一层阻塞：它被 platform 的 `ContractJsonModule` 显式注册，需与 mixin 一起搬。

### 10.5 守卫改造清单（终态会推翻 6 条规则前提）

终态不是"改路径"，有几条守卫的**语义**与它冲突，必须提前改，否则搬到最后一定红。

| 守卫 | 现状前提 | 与终态冲突 | 改法 | 何时 |
|---|---|---|---|---|
| **`check-platform-shapes.py`（C8）** | platform 禁业务词包/文件名、禁 `View`/`Port`/`Facts`/`Gateway` 后缀 | **framework 要承载 173 个共享词汇**，其中大量 `*Facts` / `*View` / `*Port` | 新增豁免：`framework/shared/**` 整包不看判据 1~3（它就是共享词汇层） | B4 前 |
| **`check-contract-purity.py`（C1/C2/C3）** | contract 纯 JDK、无框架依赖 | contract 消失 | 退役；或改成"`framework/shared/**` 禁第三方依赖"（保留原意） | B4 |
| `check-contract-shape.py` | 端口 ≤7 方法，只扫 `contract` | 端口分散到 14 个 `app/<域>/api` | 扫描根从 `contract` 改为 `app/*/api` + 引擎 `api` | B2 起 |
| `check-contract-sync.py` / `check-fe-contract-keys.py` | 前端契约源在 `contract` | 源变成各域 `api` + framework | 多根扫描 | B2 起 |
| `check-domain-contracts.py`（R10） | 跨域只经 contract；`clearable` 目标 0 | 跨域经对方 `api` 合法（B1 已加 ⓪ 档） | B1 已改分级；B5 把"目标 0"改成"⓪ 档合法、① 档递减" | B1/B5 |
| `check-module-graph.py` | 6 模块矩阵，含 contract 边 | 5 模块、无 contract | 改矩阵（app→framework，删 contract） | B5 |
| `_source_roots.py` / `check-doc-truth.py` / `check-duplicate-classes.py` / `check-hygiene-budget.py` | `MODULE_DIRS` 含 contract；"六模块"锚点 | 5 模块 | 改列表与锚点 | B5 |
| `PackageRestructureBoundaryTest`（R-1） | `CROSS_DOMAIN_BASELINE` 为空、禁一切跨域 | 跨域 `api` 合法 | 改为"允许 `.api.` / `.dto.`，其余仍禁" | B2 前 |
| `ArchitectureRulesTest`（22 条） | 含 contract 纯度与跨域规则 | 同上 | 删/改其中 contract 相关条目 | B5 |
| `check-json-key-case.py` 与 5 份基线 JSON | 冻结面按**路径**登记 | 每搬一批路径就漂移 | 每批 `--write`（已在做） | 全程 |

**两条最关键的**：

1. **C8（platform 禁业务词）**——如果不给它"共享词汇包"的豁免，B4 把 173 个类型下沉后，守卫会报出上百条违规，而它们**全都是预期内的**。
2. **C1/C2/C3（contract 纯 JDK）**——它存在的理由就是"契约面不能带框架依赖"；contract 消失后这条规则要转移到 framework 的共享包上，否则"纯 JDK 契约"这个约束就**凭空消失了**，将来可能有人在共享包里塞 Spring 注解。

### 10.6 风险与对策

| 风险 | 对策 |
|---|---|
| 放宽 R10 后跨域边从 0 变数十条，可能掩盖真问题 | 只允许 import 对方 `api`/`dto`；`service`/`repository`/`adapter`/`domain` 仍全禁（沿用 P2 的 R-1 规则） |
| 43 个"同级下沉"类型进 framework，framework 语义被稀释 | 已在 §10.3 单列；后续若发现某组同级域其实该合并（如 `im`+`embedchannel`→`channel`），随合并把它们挪回域 |
| B2 的 88 个类型会改约 400 处 import | 按域分批，每批一次全量 |
| 10 个"无引用"可能是死代码 | 先只做静态引用扫描，确认后再删；不确认的留在 framework |

### 10.6 明确不做

| 项 | 理由 |
|---|---|
| 包名 `com.ragagent.platform` → `framework` | 2000+ 处改动，收益为零 |
| 保留 `contract` 作为"共享词汇层" | B4 完成后它是空目录 |
| 把 24 个多域共用的 helper 搬进某个域 | R10 禁止；它们本就该留在 framework |

## 11. 全部卡点与解决路径（2026-11-20 全面复核）

> 本节是对"到终态还差什么"的一次性盘点：15 个卡点、五层依赖、每个卡点给根因/解法/规模/风险/前置。
> 目的是**先确认路径再动手**，不再边做边发现。

### 11.1 终态必须重新定义（测量结论，需先确认）

`contract` 现有 **251** 个类型（原 288，已迁出 37）。按 §10.3 的判据（级别 + kind + 模块可达性）全量分类：

| 归宿 | 数量 | 占比 |
|---|---|---|
| `framework`（被 platform 引用 / 跨 kind / 同级互需 / 引擎互斥） | **166** | 66% |
| `app/<域>/api` | 45 | 18% |
| 引擎 api（model 7 · retrieval 11 · agent 10） | 28 | 11% |
| 无引用（可删） | 10 | 4% |
| 待定 | 2 | 1% |

按包看，进 framework 的大头不是"通用面"：

```
knowledge 27 · storage 17 · model 16 · session 12 · tenant 11 · event 25
wiki 7 · auth 7 · apikey 6 · websearch 8 · storage.support 若干 …
```

**结论**：蓝图 §9.2 把 `framework` 定义为"现 platform + 通用面（error·event·json·text·pagination）"，但实测
**framework 必须再装下 166 个业务共享词汇**（`ChunkFacts` / `SearchResult` / `ChatMessage` / `SessionQaPort` …）。

所以"5 模块、contract 消失"在**模块数量**上可达，但它的实质是：

> **把 contract 合并进 framework，而不是"解散"。** framework 会变成"技术底座 + 跨域共享词汇层"两段结构。

这一点必须先确认——它决定 §11.2 里 K3/K4/K9 的做法。

### 11.2 卡点总表（15 个，五层）

#### L1 模块结构（无前置）

| # | 卡点 | 根因 | 解法 | 规模 | 风险 |
|---|---|---|---|---|---|
| **K1** | `platform` → `framework` 改名 + 全部移入 `backend/` | 终态要求 | 纯改名：目录 + artifactId + 根 pom `<modules>` 前缀 | 12 个 pom · 24 个脚本 · CI | 低（不改代码） |
| **K2** | 模块数锚点全线漂移 | README 写"六个"、`_source_roots` 的 `MODULE_DIRS`、`check-module-graph` 的 `MODULES` | 与 K1 同一提交改 | 4 处 | 低 |

#### L2 守卫（终态的前提，不改则 K9 必红）

| # | 卡点 | 根因 | 解法 | 风险 |
|---|---|---|---|---|
| **K3** | **C8（`check-platform-shapes`）：platform 禁业务词包/文件名/`*Facts`/`*View`/`*Port` 后缀** | framework 要装 166 个共享词汇，判据 2/3 会全命中 | 新增 `framework/shared/**` 整包豁免；判据改为"shared 只允许跨模块共享的形状，禁止 service/repository/adapter" | 中（放宽的是形状命名，不是分层） |
| **K4** | **C1/C2/C3（`check-contract-purity`）：contract 纯 JDK** | contract 消失后规则失效 → "契约不带框架依赖"约束凭空消失 | 规则目标从 `contract` 平移到 `framework/shared`；保留"零第三方依赖 + 只允许 JDK 类型" | 低（同语义换目标） |
| **K5** | 契约类守卫扫描根过窄 | `check-contract-sync` / `check-fe-contract-keys` 只扫 contract | 改为多根：contract + 各模块 `*/api`（`check-contract-shape` 已于 B3-2 改完） | 低 |
| **K6** | 基线按**路径**登记，换包即漂移 | 5 份基线 + `FROZEN_PREFIXES` | 每批 `--write`；换包时补新前缀（已踩 4 次，做法固定） | 低 |

#### L3 契约归位（可立即做）

| # | 卡点 | 内容 | 规模 | 前置 |
|---|---|---|---|---|
| **K7** | 45 个域专属契约 → `app/<域>/api` | 分布：`knowledge` 19 · `memory` 9 · `wiki` 8 · `session` 7 · `storage` 1 · `embedchannel` 1 | 45 类型 · 约 400 处 import | 无（R10 分级已就绪，B2 已验证流程） |
| **K8** | 28 个引擎契约 → 引擎 api | `model` 7 · `retrieval` 11 · `agent` 10 | 28 类型 · 约 200 处 import | 无（落点判据已由 B3 的 `agentengine.api` / `llm.api` 验证） |
| **K9** | 166 个共享词汇 → `framework/shared` | 最大的一批 | 166 类型 · 约 1200 处 import | **K1 + K3 + K4**（不做守卫改造必红一片） |

#### L4 硬骨头（有结构性前置）

| # | 卡点 | 根因 | 解法（选项） | 规模 | 判定 |
|---|---|---|---|---|---|
| **K10a** | `platform/tracing/decorators`（6 文件 + 测试）搬不动 | 测试用 platform `tracing.langfuse` 的 **5 个包私有类**（`DefaultLangfuseManager` / `LangfuseContext` / `LangfuseRegistry` / `NoopLangfuseManager` / `RecordedSpan`），platform 内各被引用 5~9 次 | **甲**：5 类提 public（API 面 +5）<br>**乙**：在 platform 提供公开的 `LangfuseTestSupport`，测试改用它（API 面 +1，但要多写一层）<br>**丙**：不搬，`ChatMessage` 等 6~8 个协议形状永久留 contract | 5 类 / 34 处引用 | 推荐**乙**：暴露面最小，且不用把内部实现类变成公开 API |
| **K10b** | `platform/stream`（17 文件）中 4 个引用 model 契约 | `StreamEvent`/`StreamManager` 是通用流基建，但 `StreamEvent` 携带 `ResponseType` | **甲**：整体 17 文件搬 `model-runtime`（连贯，但 app 有 60+ 处引用要改）<br>**乙**：只搬 `SyncChatStream`/`QueuedChatStream`/`StreamStopWatcher`（3 文件），`StreamEvent` 留 platform → `ResponseType`/`StreamResponse` 永久留 contract<br>**丙**：不动 | 3 或 17 文件 | 推荐**乙**：收益/风险比最好，代价是 2~3 个类型留 contract |
| **K11** | `PipelinePorts`（12 个嵌套接口，5 文件）是枢纽 | 一个类 import 了 7 个包的契约；它把 knowledge/session/tool/retrieval 绑在一起 | 把 12 个接口按归属拆到各域 `api` 与引擎 `api`；`PipelinePorts` 变空壳后删除 | 12 接口 · 27 处调用 | **前置 K7**（域 api 包必须先存在）；拆完才能释放 `session`/`knowledge` 里被它引用的类型 |
| **K12** | `PromptCacheStatus` / `ResponseType` 被 `ContractJsonModule` **显式** ser/deser（不是 mixin） | 它们是枚举，用自定义序列化器 | 把两个序列化器搬进 `ModelJsonModule`（S1 机制已验证可用） | 2 类型 · 2 个 ser/deser 类 | 低；随 K10 一起做 |

#### L5 收尾

| # | 卡点 | 解法 | 规模 |
|---|---|---|---|
| **K13** | 10 个"无引用"契约类型 | 先确认是否死代码（含反射/SPI 使用），再删 | 10 |
| **K14** | 2 个"待定"类型（消费者含 `_root`/`config`） | 人工判定归属 | 2 |
| **K15** | ArchUnit（22 条）→ Modulith | R-1/R-2 迁进 Modulith，退役同类 Python 守卫 | 2 天 |

### 11.3 依赖 DAG 与推荐顺序

```text
阶段一（无前置，可并行）
  K1+K2  改名与目录前移 ──┐
  K7     45 域契约        │
  K8     28 引擎契约      │
  K6     基线随批 --write ┘

阶段二（终态守卫，必须在 K9 之前）
  K3      C8 加 shared 豁免
  K4      C1/C2/C3 平移
  K5      契约守卫多根扫描

阶段三（大头）
  K9      166 → framework/shared        ← 依赖 K1/K3/K4
  K12     2 个显式 ser/deser 随 mixin 走

阶段四（硬骨头，按收益排序）
  K10a    LangfuseTestSupport（解锁 6 个装饰器 + 6~8 个协议形状）
  K11     PipelinePorts 拆分（依赖 K7；解锁 session/knowledge 剩余）
  K10b    stream 3 文件（解锁 2~3 个类型）

阶段五（收尾）
  K13/K14 清理与判定
  K15     ArchUnit → Modulith
```

### 11.4 每阶段的验收与回滚点

| 阶段 | 验收（缺一不可） | 回滚点 |
|---|---|---|
| 一 | `mvn -f backend/pom.xml clean verify` 绿 · 守卫 49/49 · 测试条数不变 | 每次提交前 |
| 二 | 守卫自身仍能拦住**故意注入**的违规（每个改过的守卫做一次受控验证） | 守卫改动单独提交 |
| 三 | 金片 1339 逐字节不变 · `contract` 剩余数单调递减 | 每 20~30 个类型一次提交 |
| 四 | 每批全量绿 + 包级环 0 新增 | 单独提交 |
| 五 | Modulith 规则覆盖原 R-1/R-2 判据 | 守卫退役前先比对 |

### 11.5 需要先决策的三点

| # | 决策 | 影响 |
|---|---|---|
| **D1** | 接受"framework = 技术底座 + 166 个共享词汇"（即"解散"实为"合并"）吗？ | 决定 K3/K4/K9 是否要做；若不接受，则终态只能是"6 模块 + contract 保留"，§11 的 K9 及之后全部不做 |
| **D2** | K10a 选甲（提 public）还是乙（`LangfuseTestSupport`）？ | 决定 6~8 个 LLM 协议形状能否归位 |
| **D3** | K10b 选甲（搬 17 文件）还是乙（搬 3 文件）？ | 决定 `ResponseType`/`StreamResponse` 是否永久留 contract |

### 11.6 工作量估算

| 阶段 | 内容 | 工时 |
|---|---|---|
| 一 | 改名 + 前移 + 两批契约 | 3~4 天 |
| 二 | 守卫改造 + 三次受控验证 | 1~2 天 |
| 三 | 166 个类型（分 6~8 批） | 5~7 天 |
| 四 | K10a / K11 / K10b | 3~4 天 |
| 五 | 清理 + Modulith | 2~3 天 |
| **合计** | | **14~20 天** |

### 11.7 修订：共享词汇可以回到域（2026-11-20 二次评估）

§11.1 的"166 进 framework"是在**两个假设**下算的，两者都可变：

| 假设 | 它是否蓝图要求 | 改动后果 |
|---|---|---|
| `platform` 不动（其业务邻接代码仍算 platform 依赖） | 否——蓝图 §5.3 要求 framework 只留技术适配 | 搬走 30 个文件后，"platform 依赖"从 87 → 54 |
| 跨 kind 不得 import | **否——kind 是我们自己发明的轴，蓝图只有三级域规则** | 取消后"跨 kind"从 55 → 0，净增 55 个可归域 |

三种情形实测（251 个类型）：

| 情形 | framework | 域 api | 引擎 api |
|---|---|---|---|
| **A** 现状（platform 不动 + kind 约束） | **168** | 45 | 28 |
| **B** 搬走 30 文件 + 保留 kind | 147 | 52 | 42 |
| **C** 搬走 30 文件 + 取消 kind | **101** | **93** | 42 |

**即：按 C 做，framework 只装 101 个，其余 93 个走域 api。**

剩下的 101 拆解：

| 组成 | 数量 | 能否再降 |
|---|---|---|
| platform **真技术代码**还需（SSE 编码器、`JsonMappers`、安全、可观测性） | 54 | 否——技术层确实要有这些形状 |
| 同级域共享 | 42 | **可降**：见下 |
| 引擎互斥（agent + retrieval 同时用） | 5 | 否（两引擎互不可见） |
| 无引用 / 待定 | 15 | K13/K14 清理 |

**42 个同级共享的集中度很高**（蓝图 §5.4：同级互需时依次考虑"下沉基础域 / 改用事件 / 合并"）：

```
knowledge + wiki      13 个   ← 合并候选（守卫里本就同属 knowledge 族）
auth + system         11 个   ← 合并候选（守卫里本就同属 identity 族）
datasource + knowledge 4 个   ← 下沉或事件
其余 12 对            各 1~2 个
```

若做这两处**域合并**（knowledge+wiki、auth+system），约 24 个类型也回到域 api，
framework 进一步降到 **~77**。

**修订后的结论**：共享词汇**可以**回到业务域通过 api 提供，不是必须塞进 framework。
framework 的合理体积约 **77~101 个**（而非 166），前提是：

1. 搬走 platform 的 30 个业务邻接文件（新卡点 K16）
2. 取消 or 降级 kind 轴（新卡点 K17）
3. 合并 knowledge+wiki、auth+system 两对同级域（新卡点 K18，可选）

### 11.8 新增卡点与决策

| # | 卡点 | 内容 | 规模 | 前置 |
|---|---|---|---|---|
| **K16** | platform 的 30 个业务邻接文件搬回域/引擎 | `knowledge/`(5) · `storage/`(5) · `prompt/`(4) · `stream/`(17，含 4 个碰 model) · `tracing/decorators`(6) · `audit/`(1) · `tool/`(1) · `event/EventType`(1) · `web/{RbacInterceptor, ServletSseSink}`(2) · `config/LanguageProperties`(1) | 30+ 文件 | 与 K10a/K10b 重叠，需合并推进 |
| **K17** | kind 轴怎么处理 | 选项：**甲** 整体取消（只留级别）；**乙** 保留但允许"级别合法时跨 kind 的 api import"；**丙** 保持现状（则 55 个类型留 framework） | 1 个守卫 | 无 |
| **K18** | 合并 knowledge+wiki、auth+system | 各消 13 / 11 个同级共享；代价是域数量 19 → 17，且要改 `capability-kinds.json`、`check-capability-kinds` 族定义、以及两个域的 controller 路由前缀保持不变 | 2 处 | 可选，收益 ~24 类型 |

**决策修订**：

| # | 决策 | 选项 |
|---|---|---|
| **D1′** | framework 目标体积 | ① 168（现状口径，只做 K7/K8）<br>② **101**（做 K16 + 取消 kind）← 推荐<br>③ ~77（再加 K18 域合并） |
| **D2′** | K17 选甲/乙/丙 | 推荐**乙**：保留 kind 的语义（product/control 分离有价值），但"级别合法 + 目标是 api"时放行 |
| **D3′** | K18 是否合并两个域对 | 推荐**做 knowledge+wiki**（本就同族、13 个类型），**auth+system 暂不**（职责差异大，合并风险高） |

### 11.9 最终结论：闭包感知后的真实分布（2026-11-20 定稿）

§11.1 / §11.7 的所有数字（45 / 93 / 101 / 168）都是**逐类型独立判断**，没有算闭包。
实跑 K7 时 45 个类型一搬，`contract` 内部立刻编译失败——因为 `KnowledgeBaseRowView` 等
**留下的**类型反向引用了被搬走的类型，而 contract 不能依赖 app。

把闭包纳入判据后（有任何一个「留在 contract」的引用者 ⇒ 本类型必须留），251 个类型的真实分布：

| 归宿 | 数量 | 占比 |
|---|---|---|
| **framework** | **208** | **83%** |
| app/<域>/api | 22 | 9% |
| 引擎 api | 11 | 4% |
| 无引用（可删） | 10 | 4% |

可去域的 22 个：`knowledge` 10 · `wiki` 8 · `memory` 4（其中 `KnowledgeBaseViews` 去 session）。
可去引擎的 11 个：`model-runtime` 7 · `agent-engine` 2 · `retrieval-engine` 2。

**方法论教训（必须写进判据）**：

> 任何"某类型归属哪里"的估算，必须做**闭包**：只要还有**一个留在原地的引用者**，它就搬不走。
> 逐类型独立判断会systematically高估可搬量——本次四次估算（45/93/101/168）全部偏高，真实值是 22/11/208。

**这直接改写终态的形态**：

- "5 模块"仍可达，但实质是 **contract 整体并入 framework**（208 个类型搬进 shared 包）
- 那 22 + 11 = 33 个类型可以真的分散到域/引擎
- 不存在"大部分契约回域、framework 只剩技术适配"的形态——`contract` 本身就是一张强耦合的词汇网，
  拆开它的成本等于重写域间通信，不是搬包

**由此 §11.8 的 D1′ 三档降为一档**：只有"contract 并入 framework"一种可行终态。
K16（搬 30 个 platform 业务文件）的收益也随之缩小——它不再"解锁 54 个类型"，因为那 54 个仍被
framework 内其他类型引用，闭包下依然搬不走。

## 12. shared 解散路径（2026-11-20 定稿）

`contract` 已并入 `framework/shared`，所以「解散 shared」就是原来「解散 contract」那一件事。
本节用实测数字给出分解、阶段与验收，替代 §9 的假设性描述。

### 12.1 先拆一个关键前提：shared 不是铁板一块

`shared` 现有 **224** 个类型，按蓝图 §5.2 分为两部分：

| 组成 | 数量 | 蓝图归属 | 是否债务 |
|---|---|---|---|
| 通用面：`error` 3 · `event` 26 · `json` 3 · `text` 2 · `pagination` 1 | **35** | framework（§5.2 明确） | **不是**——它们是 framework 的正当内容 |
| 业务词汇：`knowledge` 49 · `storage` 22 · `model` 21 · `session` 19 · `tenant` 11 · `websearch` 9 · `wiki` 8 … | **189** | 各域 `api` / 引擎 `api` | 是 |

所以「解散」的准确口径是：**189 个业务词汇清零**，35 个通用面留在 framework 并迁出 `shared` 命名空间
（变成 `framework/error`、`framework/event` …）。终态 `shared` 这个词消失，而不是 framework 变空。

### 12.2 189 个业务词汇的消费结构（决定了要做什么）

| 消费方 | 数量 | 需要什么才能搬走 |
|---|---|---|
| 被 **framework 自己**用 | **96** | 先解除 framework 依赖（P1） |
| 多域共用 | **64** | 域间通信改造（P4） |
| 单域 | **12** | 域内重构 + 闭包复核（P3） |
| 无引用 | **9** | 直接删（P2） |
| 被引擎用 | **8** | 随引擎 `api` 归位（P3） |

96 这一档是最大单块，也是唯一能在「不改业务逻辑」的前提下推进的。

### 12.3 关键发现：framework 的阻塞集中在 JSON mixin 注册表

`framework` 下有 **42 个文件**引用 shared 业务类型，但引用量高度集中：

| 文件 | 引用的 shared 类型数 |
|---|---|
| `platform/web/ContractJsonMixins.java` | **42** |
| `platform/tracing/decorators/*`（4 个） | 13 |
| `platform/storage/support/*`（3 个） | 6 |
| `platform/event/*`（6 个） | 6 |
| `platform/prompt/*`（4 个） | 5 |
| `platform/stream/*`（4 个） | 4 |
| 其余（web/error、knowledge、fileserve、http、audit、config） | 10 |

进一步拆 `platform/web` 引用的 53 个类型：

| 来源 | 数量 | 处置 |
|---|---|---|
| **只被 `ContractJsonMixins` 引用** | **42** | 拆分 mixin（P1a）即解除 |
| 还被 `RbacInterceptor` / `GlobalExceptionHandler` / `JsonBodies` 等用 | 11 | 都是 `AppError` / `BizException` / `TenantRole` / `ErrorCode` / `SseSink` 这类通用面或技术抽象，**本就该留 framework** |

**结论**：`shared` 解不开的头号原因不是 `stream`/`prompt`，而是**一个 1274 行的 mixin 注册表把 42 个业务类型锚在了 framework**。
`JsonModuleProvider`（按模块贡献 JSON 模块）的机制早已就位，但一直没用上——P1a 就是把它用起来。

### 12.4 六阶段路径

| 阶段 | 内容 | 释放 | 前置 | 工时 | 风险 |
|---|---|---|---|---|---|
| **P1a** | **JSON mixin 按归属拆分**：`ContractJsonMixins` 按 `model` / `agent` / `session` / `tenant` / `knowledge` 等切成多份，随类型搬到对应模块，各模块用 `JsonModuleProvider` 自报 | 解除 **42** 个的 framework 依赖 | 无（机制已就绪） | 3~5 天 | 中：金片必须逐字节不变 |
| **P1b** | **framework 业务代码下沉**：`stream` 4 · `prompt` 4 · `tracing/decorators` 4 · `knowledge` 3 · `storage` 5 · `audit` 1 · `config` 1 · `http` 1 | 解除其余 ~15 个的 framework 依赖 | 按域分级判据逐个定落点 | 5~7 天 | 中：部分文件被多域用，只能留 framework |
| **P2** | 删 9 个无引用类型 | 9 | 无 | 0.5 天 | 低（先确认无反射/SPI 使用） |
| **P3** | 引擎面 8 个 → 引擎 `api`；单域 12 个 → 域 `api` | 20 | P1 完成（framework 依赖已解除）+ 闭包复核 | 3~4 天 | 中：闭包会拦下一部分 |
| **P4** | **多域档分层处置**（见 §12.4.2）：A 唯一 home 改 import ／ B1 knowledge+wiki 合并 ／ B2 同级逐个拍板 ／ C 全平台词汇归 framework；**只有 `PipelinePorts` 需真改造** | **74** | P3 | **6~10 天 + 1 个拍板项** | 中：金片守出口、R10 守方向、闭包守搬迁 |
| **P5** | 收尾：`shared` 只剩通用面 35 → 迁出到 `framework/error`、`framework/event`、`framework/json`、`framework/text`、`framework/pagination`，删除 `shared` 命名空间 | 剩余 35 变成 framework 正式内容 | P4 | 2 天 | 低 |

**P1a 是分水岭**：它零业务逻辑改动、只重组 JSON 装配，却解掉最大的一块（42/96）。

### 12.4.2 P4 重评：从「不可估」到「74 个里只有 1 处真要动逻辑」（2026-11-20）

早期把 P4 整体标为「不可估」，理由是「64 个多域共用，需改域间通信」。这个结论没有拆开域组合、类型形态与分级落点，属于**过度悲观**。逐类型扫开后：

**数据口径**：类型级 `import`-only 扫描得 189 个业务词汇，其中多域档 136 个；剔除「还有引擎/框架消费者」的 36 个后，**纯域间档 74 个**（早期口径 64 为类型级粗算，另有文件级扫描得 72，差值来自嵌套类型，结论一致）。

| 层 | 数量 | 内容 | 动作 | 动业务逻辑 |
|---|---|---|---|---|
| **A 唯一 home** | **41** | 最低级消费方唯一：knowledge 15 · websearch 12 · storage 9 · initialization 8 · model 8 · auth 6 · mcp 5 · embedchannel 4 · im 4 · system 3 · memory 2 · audit 1 | 搬 home 域 `api/` + 改 import；其余消费方全是上级，R10 分级直接放行 | **零** |
| **B1 knowledge+wiki 同级** | **13** | `ChunkPort` / `WikiIngestPort` / `KnowledgeFinalizePort` 等，即 P3-0 解环时上提的回调端口族 | 走 K18/D3′ 已推荐的 **knowledge+wiki 合并**，合并后变域内调用，一次清零 | 零 |
| **B2 其他同级** | **20** | auth+system 12 · datasource+knowledge 4 · initialization+model 4 · 其余零星 | 逐个二选一：**合并** 或 **永驻 framework** | 零或极小 |
| **C 全平台词汇** | 3~5 | `TenantRole`（13 域）· `TenantConfigLookup`（10 域）· `AuditAction`（7 域） | 归 framework——蓝图 §5.3 框架层本就有审计/租户能力，是正当终态 | 零 |
| 例外 | 1 | `pipeline.PipelinePorts`（12 个嵌套接口的枢纽，K11 已登记） | 按 K11 拆分归位 | **中（唯一真改造）** |

#### 事件化是伪选项（两处实锤）

蓝图 §5.4 的三选一里「改用事件」在 B 档几乎无处可用，因为同级互需的类型形态是**查询与纯函数**：

| 接口 | 消费域 | 实际方法 | 事件化 |
|---|---|---|---|
| `AdminUserPort` | auth + system | `byId` / `byEmail` / `byUsername` / `listSystemAdmins` | ❌ request-response |
| `SystemSettingGateway` | auth + system | `getString` / `getBool` / `getInt` | ❌ request-response |
| `KnowledgeIngest` | datasource + knowledge | `contentHash` / `fileNameFromUrl` / `newTaskId` | ❌ 纯函数 |

事件适合命令与通知，不适合同步查询与纯函数。**所以 B2 的真实选项只有两条：合并域，或永驻 framework。**

#### 由此浮出的唯一决策点

> **`auth` + `system` 若不合并，`AdminUserPort` 等 ~12 个查询型词汇只能永驻 framework。**

这与 §12.7 终态图（framework 只含 35 个通用面）有出入。二选一：

| 选项 | 代价 | framework 纯度 |
|---|---|---|
| 接受 framework 含少量跨域词汇（`shared` 命名空间仍可消失，只是这些类型迁到 framework 自己的包） | 零额外工时 | 多出 10~20 个跨域查询词汇 |
| 拍板 **auth + system 合并** | 多 2~3 天（族定义、`capability-kinds.json`、路由前缀不变） | 保持纯净 |

#### 决策落地：`auth` + `system` 已合并（2026-11-20）

按上一节的二选一，选择**合并**（D3′ 由「暂缓」改为「执行」）。形态与实测：

| 项 | 内容 |
|---|---|
| 形态 | `app/system/**` → `app/auth/system/**`（保留 `auth` 作域名，18 个文件 / 22 端点随迁） |
| 为何选 auth 作域名 | 它已有 122 文件 / 43 端点，且已含 `apikey` 子域；反向改名成本更高 |
| 同类同级别 | 两者都是 `control` / L1，合并后 kind 与 level 不变 |
| 路由 | 完全不变（`@RequestMapping` 路径未动），金片逐字节不变 |

**效果实测**：`auth+system` 的同级依赖从 **12 个 → 0**；业务词汇多域档 136 → 125；域数 19 → 18。`shared` 里那批 `AdminUserPort` / `SystemSettingGateway` 等查询型词汇因此变成「唯一 home = auth」，可在 P3 直接搬进 `auth/api`。

#### 决策落地：`knowledge` + `wiki` 已合并（2026-11-20）

| 项 | 内容 |
|---|---|
| 形态 | `app/wiki/**` → `app/knowledge/wiki/**`（173 主码 + 32 测试随迁，域名保留 `knowledge`） |
| 为何选 knowledge 作域名 | 它已有 8 个控制器 / 69 端点与 chunker·task·support 等子域；wiki 21 端点反向改名成本更高 |
| 同类同级别 | 两者都是 `product` / L2 |
| 路由 | 完全不变，金片逐字节不变 |

**效果实测**：`knowledge+wiki` 同级依赖 **10 → 0**；域数 18 → **17**；同级组合 21 → **11**。

**四处连带修正（合并时才暴露）**：

1. **MyBatis statement id 字符串**：`TenantFilterGuard` 里以字符串登记 `com.ragagent.wiki.mapper.*`，
   搬包后必须同步——它不是 import，静态检查抓不到，只有运行才炸。
2. **R10 的 api 判定按 `seg[1]` 取包种类**：`knowledge.wiki.api.X` 的第二段是 `wiki` 不是 `api`，
   被误判为「非契约包」而报方向违规。改为「路径里任一段是 api/dto 即可」。
3. **域约定测试的扫描根是 `knowledge/`**：合并后把 wiki 的历史债（内联全限定名、Go 时代序列化注解、
   移植期黑话）一并纳入零容忍范围。处理分三类：全限定名断言删除（仓级
   `check-fully-qualified-names.py` 已带基线覆盖，域内重复无意义）；序列化注解对摄取载荷豁免
   （`wiki/{service,domain}/ingest/**` 与 `NewSlugFromCitation` 是喂模型的载荷，键名由模型侧约定）；
   黑话逐条改写。
4. **`check-json-key-case` 冻结前缀**：`knowledge/wiki/` 需登记，否则 20+ 个模型载荷键被报「新增 snake」。

**剩余同级组合**（合并后实测）：`knowledge+wiki` 10 · `datasource+knowledge` 3 · `initialization+storage` 2 · `auth+storage` 2 · `audit+auth` 2 · 其余零星 3。

**踩坑（换包必漂的两类基线）**：`controller-catch.baseline.json` 的键是模块内相对路径、`fully-qualified-names.baseline.json` 的键是仓库相对路径——两者都要换锚，否则会把老债务当成「新增」报红。

#### 修正后的 P4 口径

| 项 | 旧口径 | 新口径 |
|---|---|---|
| 工时 | 不可估 | **6~10 天 + 1 个拍板项** |
| 动业务逻辑 | 整个 P4 | **仅 `PipelinePorts` 一处** |
| 风险 | 高 | **中**（三道闸都在沿用范围内） |

**一句话**：P4 不是「域间通信改造」，是「按域组合分四层、36~41 个纯改 import、13 个合并、20 个拍板、3~5 个归 framework，只剩一个枢纽要拆」。

#### 12.4.1 P1a 的第一手实测（2026-11-20，已回滚）

试搬 `TokenUsage` 的 mixin（`ContractJsonMixinsCore.TokenUsageMixin`）到 `model-runtime`，全量闸门在 framework 的 `StreamJsonTest` 报红：

```text
expected: ..."usage":{"prompt_tokens":3,...}
but was:  ..."usage":{"promptTokens":3,...}
```

**根因**：`framework` 自己的 `StreamEvent` 有字段 `TokenUsage usage` 与 `ResponseType type`，而 `StreamJson` 是 framework 内部 mapper（SSE 进出 Redis 用，不是 HTTP 那个）。mixin 搬到下游模块后，framework 的 mapper 拿不到它 → 线格式退回 camelCase。

**新增判据（P1a 必须遵守）**：

> **framework 自身 mapper 的序列化路径上的类型，其 mixin 必须留在 framework。**

判定方法：看该类型是否出现在 `StreamJson` / `EventJson` / `LangfuseAttributes` 的序列化对象图里。当前被钉住的是 `StreamEvent` 的字段类型——`TokenUsage`、`ResponseType`（后者由 `ContractJsonModule` 显式注册，同理）。

**另记一条操作教训**：`model-runtime` 的 `ModelJsonModule` 早已存在并注册了 10 个模型面 mixin；本次按「新建文件」写入把它覆盖了，导致 `ChatMessage` 的线格式整体失效。**搬 mixin 前先确认目标模块是否已有 provider 文件，只追加不重建。**

**修正后的 P1a 可搬量**：21 个业务面注册项中，`TokenUsage` 必须留 framework（占 1），其余 20 项按归属搬到 `agent-engine`（4）／`retrieval-engine`（4）／`app`（12）。
P4 是唯一真正需要改业务逻辑的阶段，也是唯一无法预估算力的阶段。

### 12.5 与 VO 化的关系：两条独立线，可并行

| | VO 化 | shared 解散 |
|---|---|---|
| 改什么 | HTTP 出口形状 | 模块间依赖 |
| 是否互相影响 | **否**——VO 化只改 app 内部，不触碰 shared 的消费结构 | 否 |
| 释放 shared | **0 个**（实测：唯一「仅控制器层 + 单域」的 `StorageUrlContext` 还被 shared 内 `Mode` 引用，闭包挡住） | — |
| 排序 | 与 P1~P5 无依赖，可任意穿插 | 与 VO 化无依赖 |

所以「做完所有域 VO 化就能解散 shared」不成立；但两条线可以同时推进，互不阻塞。

### 12.6 不可协商的判据（每次搬都要过）

1. **闭包**：只要还有一个「留在原处」的引用者，类型就搬不走。任何归属估算必须逐轮迭代到不动点。
2. **R10 + kind**：跨域只允许「上级 → 下级、且目标是 `api`/`dto`」，跨 kind 一律禁。
3. **金片**：`*ContractTest` 逐字节不变。JSON mixin 拆分尤其依赖它——序列化策略改一点，报文字节就变。
4. **包级环**：搬入模块前先跑 `scripts/check-package-cycles.py`；契约要放在「被依赖方」的命名空间。

### 12.7 终态

```text
backend/
├── framework/           技术底座；含 error / event / json / text / pagination（35 类型）
├── model-runtime/       api/ 收模型面契约
├── retrieval-engine/    api/ 收检索面契约
├── agent-engine/        api/ 收 agent/tool 契约
└── app/                 19 域，各域 api/ 收域契约，出入口全是 VO
```

`shared` 命名空间消失；`contract`、`shared` 这两个词都不再出现在代码里。
