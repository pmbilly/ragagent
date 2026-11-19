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
| 3 | **不做 contract 解散** | `contract` 保持现状；`domains` 仍是单个 Maven 模块 |

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
| **P4** | `api/` 包语义纠正 + 域间边界改为测试校验 | 结构校验通过；跨域直连引用只减不增 |

### 4.1 P1 的规则：先盘点已有，只补缺口

2026-11-19 实测：`boot/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java` 已有 **22 条规则**（A1~A20 + B257/B260），原计划的四条里三条已被覆盖：

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

## 8. 不在本次范围

H7 / H8 / H9 / L11 / Q6 / Q8b / Q9 / Q10 / Q11 / R2c-3d 余项 / M8（见 `HANDOFF.md` §5.1）。已否决项见归档 §5.0b。

## 9. 终态与留路（contract 解散后长什么样）

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

24 个包里只有 4 个（`doc` / `pagination` / `settings` / `text`）只被 domains 消费；其余 20 个全部跨模块，`knowledge` 还被 `retrieval-engine` 与 `platform` 各用 4 处。

正确顺序：**先把 `error` / `event` / `json` / `text` / `pagination` 下沉 framework**（技术面、无业务形状、不动任何域），contract 剩下的才是纯中介，之后按引擎面、域面两块搬。该步骤不在本次范围，登记为解散批次的第一刀。

### 9.6 两个前置坑

1. **包根不统一**：`domains` 的源根是 `com.ragagent.<域>`，不是 `com.ragagent.domains.<域>`。改名 `app` 时若顺手统一包根，会触碰全部 1,642 个文件 —— **只改模块名，不动包根**。
2. **`boot` 并入 `app`** 后，17 个装配类落到 `app/config/`，`check-wiring-points.py`（W1~W4）的登记表与 `docs/architecture` 的装配清单要同步。
