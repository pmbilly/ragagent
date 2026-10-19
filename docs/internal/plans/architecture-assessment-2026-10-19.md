# 业务导向架构评估与目标架构方案（2026-10-19）

> 评估基线：`v0.5.0` @ `efc06fa25`（取证时工作树推进到 `afb9fc3d7`，含 L34a/L34b，不影响结论）。
> 方法：7 路并行深读（入库链路 / 问答链路 / 管理域 16 个 / 能力抽象与三 runtime / 平台设施 / 结构量化 / 前端），全部结论基于 HEAD 静态取证（file:line 或实测数字），未跑构建与测试。
> 与 2026-10-18 评估的关系：其后落地 41+ 提交（C0~C11、L25~L34），本文对昨日全部 P0/P1 逐条重验，被推翻的结论明确标注。

## 1. 总裁决

**骨架已对，不必动；残余问题集中在「业务归属错配」与两处缺陷级问题；可读性已高于主流基线。**

| 维度 | 裁决 | 关键证据 |
|---|---|---|
| 模块划分（7 模块） | **合理，维持** | 模块 DAG 零环零反向；域间跨包 import 仅 **8 条边 / 36 文件**（与守卫基线完全对账，clearable=0）；boot→domains import=0 |
| 业务域划分（22 域） | **大体合理，4 处归属错配** | tenant 空壳（359 行、唯一写入方是 auth）、evaluation 孤儿（前端零调用、外部零消费）、initialization 双头（手动 new ×4 + 与 model 重复）、system 抢了 storage 的探测 |
| 可读性 | **高于主流** | >800 行类 **0** 个；问答/入库链路核心类无 >100 行方法；文档引用代码存在率 **100%**（377 处引用全真）；5 个 gen-\*-map `--check` 全绿 |
| 可维护性 | **中上** | 40 条守卫 + ArchUnit 28 规则 + ~5000 测试；短板：V1(PG)/TestSchema(H2) 双源、2 张顺序敏感手工路由表、前端 top5 组件占 .vue 21% |
| 能力抽象（三 runtime） | **全部维持** | 26 家 LLM 收敛到共享客户端（重复度极低）；EngineRegistry 四道闸（冷却/代数/singleflight/panic）；ReAct 四段 + 终态门 + 纯函数预算 |

## 2. 对 2026-10-18 评估的重验与修正

**被新证据推翻 / 明显改善的（不再成立）：**

| 昨日结论 | 今日实况 |
|---|---|
| JPA/MyBatis-Plus 混用 | **不成立**：全仓 0 处 jakarta.persistence/JpaRepository，纯 MP |
| datasource 连接器各写各的 | **不成立**：`Connector`/`StreamingConnector` 契约统一（datasource/domain/connector/Connector.java:29），7 连接器全部落约，Client/Config/Cursor 文件形状平行 |
| contract 99 个算法化 class | **过时**：实为 **~31 个**（154 class 中含异常/工厂）；最大件 SearchAuth 455 / ToolResultPersist 427 |
| 三张人工路由表 | **已消解一张**：endpoint 清单进 `check-route-policy.py` 守卫（386 路由从注解解析）；剩 RBAC 321+55 条（boot）与 APIKey 294 条（domains）两张手工镜像表，仍顺序敏感 |
| 租户过滤 guard fail-open | **已修**：默认 enforce（application.yml:59）；残余 fail-open 仅 JSqlParser 解析失败一处；18 处快照已全部包进 `TenantScope` 且有 check-tenant-scope 守卫 |
| contract 4 包环 {knowledge,model,model.chat,retrieval} | **已消解**：model.chat 并入 model；仅剩 model↔retrieval 2 包环（各 1 行 import） |
| wiki↔knowledge finalizer 归属倒置 | **半修**：`KnowledgeFinalizeAdapter` 已建且 wiki 侧已走它；但 3 个 knowledge 调用点仍 import wiki 具体类（KnowledgeFinalizingStage.java:17、ChunkExtractService.java:29、QuestionGenerationService.java:16，后两者各带一份逐字拷贝的 drainSubtask） |
| service 杂物包 12070 行 | **已拆 37%**：session/service 30 文件/7641 行，qa/、rag/、suggestion/ 已分离 |
| QaTurnExecutor/长方法群 | **已修**：executeQA 七步、agentQA 26 行、parseQARequest 104 行、mapOneDocument 411→102、runToolCallInner 153；>150 行方法 25 个（外部报告的 167 是统计虚高，1945c1596 已修正口径） |

**确认仍存在的（昨日判断成立）：** QaRequestContext 34 个 public 可变字段；SSE 100ms×3 路轮询（有文档化的刻意取舍）；EventType⇄ResponseType 双词汇（thought/thinking 同义不同值）；Ops 伪解耦（实测 46 个，TenantCrudOps:27 回引控制器、EmbedChannelDelegateOps:58 调控制器静态方法）；system/storage 厂商知识三处重复；V1⇄TestSchema 双源 + 测试 flyway 关闭；return null 450 处（集合返 null 81 处）。

**方向反转的重要发现：** 昨日「还乡」方案（platform/knowledge 域算法回 domains）没有按原方向执行——C5 系列把 SearchTextUtil、MemoryKeys、ToolResultPersist、SearchAuth、WikiImageMarkup 五族**抬进了 contract**。后果：contract 里住了 ~855 行只被 domains 单模块消费的"假跨层"算法（MemoryKeys 322 行 14 处引用全在 domains；WikiImageMarkup 344 行 4 处全在 domains）；platform/knowledge 仍有 9 文件/1853 行。共享内核没有被消灭，而是换了个地方且更大了。

## 3. 四个业务面的深读结论

### 3.1 核心链路一：知识入库 —— 维持 + 小改

- 链路地图：多触发源（HTTP/datasource 队列/wiki 支线）→ `KnowledgeProcessWorker`（C11 后 206 行纯编排，4 个 stage 85/113/198/385 行）→ 取文本/分块/向量化/晋升收尾 → wiki/图谱/问题三路 fan-out。IM 不是入库入口（附件现场解析，im 对 knowledge/wiki import=0）。
- 幂等/重试：全链 CAS 状态机（pending→processing→finalizing→completed）+ RedisRetryingTaskQueue 租约续期 + housekeeping 兜底 sweep，并发论证写进了 DefaultWikiKnowledgeFinalizer.java:20-33。
- 边界知悉项：主 worker 队列是进程内虚拟线程执行器（无跨实例能力，javadoc 已自认命名误导）；Redis/InProcess 双实现靠 @Primary，连不上时启动即失败（不静默退化，正确）。
- 遗留：`reduceSlugUpdatesBody` 309 行、`DataSourceSyncExecutor.handle` 225 行、`runFinalize` 192 行是链路剩余三大长方法；knowledge/task 的 package-info 落后 C6/C11 一个身位。

### 3.2 核心链路二：问答/Agent —— 维持 + 一处 P0 缺陷

- 链路地图：KnowledgeQaController → QaEntryRunner → QaRequestParser → QaTurnExecutor 七步 → NORMAL 走 14 阶段事件管线（EventManager 注册序闭包链 + 24 插件）/ AGENT 走 AgentEngine ReAct 四段 → AgentStreamBridge 映射 StreamEvent → StreamManager → SSE。
- **缺陷（逻辑推演坐实，建议 P0 修）**：NORMAL 模式早段失败死轮询。管线失败→startAsyncRunner 只 emit ERROR（QaTurnExecutor.java:268）且 finally 仅 AGENT 模式收尾（:273）→ AgentStreamBridge.handleError 只 append done=true 的 ERROR 帧（:465-475）→ SSE 主循环只认 STOP/COMPLETE/IOException 不认 ERROR-done（QaSseOrchestrator.java:246,273,281）且无 deadline ⇒ HTTP 线程 100ms 无限轮询直到客户端断开，assistant 消息永久 completed=false。AGENT 路径有终态兜底（AgentEngine.executeInner:500-506），NORMAL 没有——不对称收尾的直接后果。修法一处双向兜底：finally 无条件补 COMPLETE（或循环认 ERROR done 即退出+落库）。
- 附带小缺陷：title 等待循环 3s 无 sleep 忙转（QaSseOrchestrator.java:293-314）。
- 健康面：agent-engine 是全链路最佳实践（终态门 AtomicBoolean 恰一次、ContextBudget 纯函数、compaction 独立包）；RAG 管线顺序契约有 pipeline-map 守卫；QaWiring 手工 new 插件让顺序可见，合理取舍。
- 待改：QaRequestContext 34 可变字段按 phase 拆 immutable 段；EventType/ResponseType 映射收单表；planTools 3 份硬编码工具清单（AgentEngineAssembler.java:300）。

### 3.3 管理后台 16 域 —— 归属错配集中地（本轮主要整改对象）

- 域间直接 import 仅 3 条（system→auth 5、auth→tenant 3、memory→session 2），控制极好；唯一违规形态：memory/service/MemoryExtractionService.java:22-23 直捅 session 的 MessageRepository/SessionMessageView，应走契约端口。
- 四处归属错配（详见 §5 C 批）：tenant 该并 auth；initialization 该收缩；evaluation 该删或做实；system 的 7 厂商 S3 探测该归 storage。
- C10 读模型抽查：model 干净；auth 主体干净但 register 返回 User 实体（AuthController.java:229，passwordHash 仅靠 @JsonIgnore 兜底）；mcp 半成品（ResponseEntity<?> 弱类型 + 401 行 CrudOps 暂存区）。
- RBAC：4+1 等级角色制、默认拒绝已闭环（未命中 /api/v1/** 即 403）；APIKey 正交（24 capability，Key 管理端点对 Key 本身永远 default-deny，防自续命——设计正确）。
- 杂项：SkillCatalog 与 SkillsCatalog 两个近重名控制器（/api/v1/skills vs /skills/catalog）。

### 3.4 能力抽象：contract / platform / 三 runtime

- **contract（小改）**：形状层主体健康（158 record+interface）；动作三件——① 单模块消费算法还乡（~855 行）；② 拆 model↔retrieval 2 包环（各 1 行 import，StreamResponse↔SearchResult / IndexEngine↔Embedder）；③ 砍 EmbeddingModelPort/ModelFactsPort（各仅 1 消费者的过度抽象）。SearchAuth/ToolResultPersist/SearchTextUtil（合计 1204 行）是真跨层算法，建议明示为「契约共享算法」文档化留下。
- **platform（小改）**：18 个顶级包均基础设施词汇，StreamManager/EventBus/TenantContext/SsrfGuard 质量高；唯一域词汇泄漏 = platform/knowledge（1853 行）。
- **model-runtime / retrieval-engine / agent-engine：全部维持**。新增一种向量库改 4 处（engine 子包/EngineTypes/EngineFactory 两段 switch/boot bean），可接受的 OCP 成本。

### 3.5 平台基础设施

- 持久层：纯 MP + PageRequests/TxTemplate/守卫文化成熟；软删 304 处手工谓词、租户注入 89 处手工 lambda——一致但靠守卫兜底而非抽象兜底（可接受，上 TenantLine 的三条反对理由有批记录 batch-records.md:806）。
- 双源 schema：V1__baseline.sql 3574 行(PG) vs TestSchema 948 行(H2/55 表)，测试 flyway 关闭、主套件跑 H2；CI 灌迁移进全新 ParadeDB 缓解腐烂，但列级漂移只能靠测试炸才发现。
- 可观测性：tracing（langfuse+otlp）与审计（durable 表）在，**metrics 面为零**（全仓无 micrometer/actuator）。
- 悬空引用 2 处：TenantFilterGuard.java:23 引已删的 persistence-tenant-filtering-evaluation.md；persistence-architecture-rules.md 同病。
- 测试基建：boot 25 包 98 文件从 HTTP 面进（覆盖 24/53 控制器面）；无 Testcontainers 但 CI 服务容器路线等效；~5000 条全绿。

### 3.6 前端 —— 小改

- 162k 行 src（.vue 129k）；API 层按后端域分 32 文件 + 全前端唯一信封解包点 + rawFetch 守卫测试 + SSE 封装（401 刷新重放/防串流）——契约纪律优于同类管理后台。
- 最大维护税：top5 巨型组件 WikiBrowser 6593 / AgentEditorModal 6320 / FAQEntryManager 5613 / KnowledgeBase 4487 / AgentStreamDisplay 4097，合计 27k ≈ 全部 .vue 的 21%。
- api/system/index.ts 1252 行成「system 域杂物抽屉」；evaluation 前端零调用属实；im 域前端登记落点与实际不符（views/integrations vs components/IMChannelPanel.vue）。

## 4. 问题清单（分级汇总）

**P0 缺陷（2）**
1. NORMAL 早失败死轮询（§3.2，逻辑推演坐实；未运行时复现）
2. AuthController.register 返回 User 实体，passwordHash 仅靠 @JsonIgnore（:229）

**P1 结构（7）**
3. 还乡 2.0：platform/knowledge 9 件 1853 行中 7 件可整包回 knowledge（消费者纯 domains）；contract 内 ~855 行单模块算法还乡；GrepChunksScoring/GrepChunkHit（283 行，被 retrieval-engine 消费）与 SearchTextUtil 需拍板「第三居所」。合计约 **2700 行**待动
4. finalizer 收尾：3 个 knowledge→wiki import 换注入已有 KnowledgeFinalizeAdapter + 消 2 份 drainSubtask 拷贝
5. tenant 并入 auth（359 行、唯一写入方 TenantService）
6. initialization 收缩：控制器手动 new ×4（全仓唯一）改构造注入；/embedding/test、/multimodal/test 与 model /models/{id}/debug 双头合一
7. system 的 7 厂商探测 + minio 特判 + 失败文案表归 storage（厂商知识现存三处）
8. evaluation 裁决：前端零调用 + 外部零消费（26 文件/2088 行）——删除或做实，**需拍板**
9. Ops 伪解耦 46 件：回引控制器/调静态方法的降级回 service；SkillCatalog/SkillsCatalog 合一；memory→session 走端口

**P2 卫生（按收益排序）**
10. 两张手工路由表 → @RouteSecurity 注解 + 启动校验（RBAC 3-5 人日 + APIKey 2-3 人日）
11. 测试库单源化：先加 PG+flyway 测试 profile（1-2 人日），H2 分阶段退役（以周计）
12. metrics：actuator+micrometer（半天）
13. 长方法棘轮：>100 行 87 个（top：SqlSelectDeepChecker.deepCheck 275、reduceSlugUpdatesBody 309、WikiLintService.runLint 253）
14. 构造器 ≥8 参 51 类（极值 KnowledgeProcessWorker 18 / KnowledgeService 17 / ImTurnOrchestrator 16）→ 参数对象化
15. QaRequestContext 34 字段分段 immutable；EventType/ResponseType 映射单表 + thought/thinking 差异测试钉死；title 忙转加 sleep
16. trim 私拷贝 36 处收敛（24 处空白语义应归 contract.text/platform.text）；集合返 null 81 处棘轮；悬空文档引用 2 处
17. 前端 top5 组件拆分 + api/system 拆分（单独立项，readability 方案已预留）

## 5. 目标架构方案

### 5.1 终态结构（不变的部分 = 大盘）

```
boot（组合根，只出不进）
 ├─ domains（20 个业务域；tenant 并入 auth、evaluation 删除/做实后 22→20/21）
 │    分层：controller → service → domain → mapper/repository，域间只经 contract 端口
 ├─ model-runtime / retrieval-engine / agent-engine（三 runtime 平级，全部维持现结构）
 ├─ platform（纯技术适配，platform/knowledge 消亡）
 └─ contract（纯形状 + 明示的 3 件契约共享算法；30 端口 → 28）
```

不拆 :knowledge/:session 独立模块：L19 拆过、L23 回退，理由是「知识代码分散 4 处」；**还乡 2.0 恰好消除这个根因**（platform/knowledge 与 contract 假俘虏回域后，知识代码只剩 knowledge + contract 形状两处，后者本就是合法边界）。届时拆不拆模块变成纯粹的编译期偏好问题，收益都不足以付 1600+ 文件 diff 的认知成本。

### 5.2 行动批次（建议顺序；每批独立全绿，遵守 §10 批次纪律）

| 批 | 内容 | 估时 | 验收 |
|---|---|---|---|
| **A 缺陷修复** | NORMAL 死轮询双向兜底（finally 无条件补 COMPLETE + SSE 循环认 ERROR done 退出 + assistant 落库收尾，先写失败测试）；title 忙转 sleep；register 改返回 DTO | 1-2 人日 | 新增红态→绿态测试；金片不变 |
| **B 还乡 2.0** | platform/knowledge 7 件回 knowledge；GrepChunksScoring 族 + SearchTextUtil 第三居所拍板（建议：SearchTextUtil 留 contract 并文档化为契约算法，Grep 族随行）；contract 侧 MemoryKeys/WikiImageMarkup/WikiLanguageSupport 回 domains；contract 拆 model↔retrieval 环；砍 EmbeddingModelPort/ModelFactsPort | 3-5 人日 | check-platform-shapes 清零；contract 类数只减；消费方 import 全改 |
| **B2 finalizer 收尾** | 3 调用点换 KnowledgeFinalizeAdapter；消 2 份 drainSubtask | 0.5 人日 | knowledge→wiki import=0 |
| **C 域归属整备** | tenant→auth；initialization 手动 new 改注入 + 连通性并入 model/debug；system 探测归 storage；Ops 46 件降级/升格；Skill 双控制器合一；memory→session 走端口；evaluation 删除或做实（**先拍板**） | 5-8 人日 | 22 域→20/21；域间边只减；前端路由同步 |
| **D 横切升级** | @RouteSecurity 注解化（RBAC 先行，APIKey 随后）；PG+flyway 测试 profile 上线后 H2 分阶段退役；actuator+micrometer | 7-12 人日 | 两张手工表删除；CI 多一个 PG 套件 |
| **E 可读性尾巴** | 长方法棘轮（先钉 top5）；18/17/16 参构造器参数对象化；QaRequestContext 分段；EventType/ResponseType 单表；trim/null 棘轮；package-info 补 2 处 | 持续 | 棘轮只减 |
| **F 前端（单独立项）** | top5 巨型组件按 tab/面板拆分；api/system 拆分 | 独立 | vue-tsc + 130 测试全绿 |

### 5.3 明确不做（继承已定决策 + 本轮数据支撑）

1. 不拆微服务、不动 7 模块骨架（域间 8 边/36 类证明单体分层健康）。
2. 不为形式拆 :knowledge/:session（L19/L23 教训；还乡后根因消除）。
3. SSE 轮询不改推送（request-path.md:66 已记录取舍：先测量再动）。
4. 不引入 JPA（本就纯 MP，昨日混用判断不成立）。
5. 不上 TenantLine（batch-records.md:806 三条理由仍成立：NULL 平台内置语义/方言 fail-closed/非 SQL 面不覆盖）。
6. 不动金片冻结面与 connector/provider 线格式。

## 6. 量化附录（HEAD 实测）

- 模块主码：contract 348 文件/20,444 行 · platform 189/17,630 · model-runtime 152/17,925 · retrieval-engine 95/20,484 · agent-engine 74/12,321 · domains 1,539/211,164(70.0%) · boot 19/2,203
- domains 22 域规模 top：session 184 文件/29,794 行 · knowledge 252/27,605 · datasource 144/26,556 · wiki 163/23,543 · mcp 143/16,410 · auth 107/12,268 · im 98/17,627 · memory 87/12,753 · agenttools 58/11,386
- 域间边 8 条：session→agentmanagement 11 文件、session→agenttools 6、wiki→knowledge 5、session→memory 4、knowledge→wiki 4、system→auth 3、memory→session 2、auth→tenant 1（合计 36 文件，全部族内/owner 登记，clearable=0）
- 卫生：>800 行类 0；>100 行方法 87；≥8 参构造器 51；trim 私拷贝 36；return null 450（集合 81）；批次代号 0
- 守卫 40 条（run-guards.sh 单入口）+ ArchUnit 28 规则 + MISPLACED_TEST_ALLOW 10 条（只许减）
- 测试：485 文件（boot 100、domains 254）；boot 集成测试覆盖 24/53 控制器面；套件 ~5000/0/4
- 文档一致性：docs/architecture 12 篇引用 377 个代码符号，存在率 100%；5 个生成器 --check 全绿
- 近 30 天变化热点：knowledge 1887 次 > session 1707 > wiki 811——两大核心链路即代码活动中心，与业务重要性一致

## 7. 与在途 C 系列的关系

C0~C11 已覆盖：业务能力分类守卫、路由默认拒绝、控制器读模型、入库阶段化、队列内核收口、契约子包重命名。本方案与其互补：C 系列未覆盖的正是 A（缺陷）、B/B2（还乡方向修正）、C 批（域归属）、D（注解化路由/PG 测试/metrics）。建议本文件作为 C 系列后续批次的规划输入，批次号沿用其序列。
