# v0.6.0 业务内聚专项整改方案（H 系列）

> 2026-10-20 立项。基线 `v0.6.x` @ `c29414b83`。
> **本文是执行方案，不是第三份评估**：现状盘点见 `architecture-assessment-2026-10-20.md`（同日，未提交），历史见 `architecture-assessment-2026-10-19.md`。
> 与 10-20 评估的关系：**采纳其 G1~G10 全部收尾批次**；本文只写它未覆盖或判断不同的部分，编号从 **H0** 起，不与 G 冲突。
> 执行锚点＝本文件 + `HANDOFF.md` §5.0b 批次表 + §12 执行记录。推送只在用户发话后执行。

## 1. 定位：从"技术分层的正确"到"业务链路的收敛"

10-20 评估的裁决是"7 模块骨架与 20 域划分合理，无需结构性手术"，欠的是已规划批次、整改尾巴、一处 P0。这个判断在**模块骨架**层面成立，但它没有回答一个更基础的问题：

**为什么骨架正确、代码规范、守卫 40 条，改一个业务需求仍然要读十几个文件？**

因为边界切在了"技术变化的方向"（契约/平台/运行时/业务/组合根），而业务是按"链路"长的。本轮只做三件事，不改骨架：

| # | 主题 | 一句话 | 与 10-20 的关系 |
|---|---|---|---|
| **A** | 链路内聚 | 每条业务链路一个入口、一个状态机，阶段间只传不可变快照 | 补充：10-20 只提 `QaRequestContext` 三段拆分，未提 `ChatManage` 总线与第二编排层（批 H1~H3） |
| **B** | 横切内聚 | 任务/软删/分页/限流 全仓各一套，域只能配置 | **新增**：10-20 未盘点 7 套队列（`§5.5` 只提 metrics/schema）（批 H4/H5） |
| **C** | 边界内聚 | 端口设准入、契约设形状预算、平台设依赖判据、外部协议**逻辑分层（不切模块）** | 部分重叠：G3 还乡只搬 platform/knowledge 9 件，未动 contract 形状与适配层（批 H6~H8） |

三者的共同判据（业务反向五问）：

| 问题 | 现状答案 | 目标 |
|---|---|---|
| 一个业务概念几个家？ | 知识库 5 家、问答 4 家、工具 4 家、任务 7 套 | 1 家 |
| 一条链路跨几个边界？ | 问答 10~15 跳、入库 18~30 类 | 问答 ≤5 跳、入库 ≤6 阶段 |
| 一个横切约束几套实现？ | 软删 79 文件、分页 7 种、队列 7 套 | 各 1 套 |
| 一个抽象有可验证收益？ | 32 Port 中 20+ 单方法单实现单调用方 | 全过准入 |
| 一个新需求改几处？ | 新增一个 Port 8~11 处；新增一个插件 ≥5 处 | 各 ≤3 处 |

## 2. 现状：三个结构性欠账（实测证据）

### 2.1 链路欠账

**问答（NORMAL）**：`Controller(1 行) → QaEntryRunner(3 行) → QaTurnExecutor(重载转发) → QaSseOrchestrator → SessionKnowledgeQaService(门面 7 方法) → EventManager → 17 插件 → AgentStreamBridge → 100ms 轮询`，**10~15 跳，其中 4 跳纯签名转换**。

- `ChatManage` **62 字段**，**36 个生产类**读写；`cloneChatManage` 手抄 57 字段并**故意漏 7 个**（类注释解释）；漏设 `tenantId` 会让 FAQ 富化静默跳过（`SessionKnowledgeQaService.java:445` 注释）。
- 插件顺序即语义，只写在注册处注释；新增插件改 **≥5 处**；`PluginChatCompletion` 在生产管线不可达。
- SSE **3 套并行抽象**（`EventBus` / `ApprovalEventBus` / `SseSink`）、**2 个独立轮询**（100ms / 300ms，各自从 offset 0 读同一流）、**3 把不同的锁**保护同一批 assistant 行。
- 分层硬违规：`session/qa/QaSseOrchestrator.java:24-26` 反向 import `session/controller/sse/*`。
- 6 处隐式顺序约束只写在注释，其中一处记录真实事故（`QaTurnExecutor.java:277-305`）。

**入库**：18~30 类、5 个包层、4 个模块、3 次线程切换。`KnowledgeProcessWorker` 以进程内虚拟线程作队列（类注释自陈"重启会丢掉在飞任务"）；状态由 4 处 CAS 拼成；另有第二个执行器 `KnowledgeTaskExecutor`。现有 5 个 stage 类本身是对的（10-20 评估"优等生"的判断成立），欠的是**显式阶段接口 + 单一状态机 + 统一队列**。

### 2.2 横切欠账（本轮最大新增发现）

| 横切 | 现状 | 证据 |
|---|---|---|
| 任务队列 | **7 套**，每套 InProcess+Redis 双实现 + 一套 wiring + 一个 `setRetryDelayOverrideSeconds` | wiki 的 Redis 队列 398 行，**完全不用** `platform.task.RedisTaskQueueCore`；`{body,attempt}` 包装重复 4 份；租约实现 3 份 |
| 软删 | **79 个主码文件**手写 `deleted_at IS NULL`；语义三种（软删 / 墓碑表 / 保留期硬删） | `wiki/WikiPageMapper` 32 处、`im` 10、`datasource` 9 |
| 分页 | **7 种**类型；多数管理端点**不分页**（直出全量） | `TagPageResult` / `ChunkPageResponse` / `MemoryPage` / `AdminUserPage` … |
| 限流 | 全仓仅 1 处（embedchannel），其余各写节流 | IM / wiki / notion 客户端各一套 |
| 事件/推流 | 3 套（`EventBus` / `ApprovalEventBus` / `SseSink`） | 同一次推流两套抽象 |

### 2.3 边界欠账

- **代理税**：32 个 Port 中 20+ 是单方法、单实现、单调用方，且实现与调用方都在 domains（编译器不拦，纯靠 R10 棘轮）。
- **门面倒灌**：`KnowledgeCatalogPort` **34 方法**；`contract/knowledge` **48 个文件 / 59 个类型**，`KnowledgeBaseRowView` **9.6 KB** 持久层行 + 另 4 种同概念形状并存。
- **平台寄生**：`check-platform-shapes.py` 只认文件名后缀，于是 `ImageInfoEnricher`(21.8KB)、`DocChunkSupport`(11.2KB)、`GrepChunksScoring`(9.3KB)、`SystemSettingRegistry`(13.1KB)、`PipelineConfig`/`ExtractPrompts` 全部合法驻留；`platform.tracing` 24 类全是 Langfuse/OTLP 单一厂商。
- **适配层混装**：datasource/connector 73 + SPI 8、im/channel 42、mcp/{protocol,oauth} 65、storage/{provider,fileserve} 21、websearch/{provider,fetch} 24 ≈ **232 文件**是对外协议翻译，却与业务规则同住 domains。实测跨域 import：`datasource/connector` 只 import datasource 自己(189)+platform(89)+contract.text(3)；`im/channel` 只 import im 自己(119)；`mcp/{protocol,oauth}` 只 import mcp 自己(115) ⇒ **可逻辑分层，无需切模块**（切模块的裁决见 §3）。

## 3. 与 10-20 评估的关系

| 10-20 批次 | 本文处置 |
|---|---|
| G1 缺陷收口（SSE ERROR-done、title 忙转） | **全文采纳，优先级最高**（P0） |
| G2 整改尾巴（连通性双头、存储探测、evaluation 前端、approval 拍板） | 采纳，不重复 |
| G3 还乡 2.0（platform/knowledge 9 件 1853 行） | 采纳；本文 H7 补"平台判据换血"（G3 的验收守卫测不出域词汇） |
| G4 B2 + 门面尾巴 | 采纳 |
| G5 Ops 整编（46 件） | 采纳 |
| G6 横切升级（metrics / PG profile / @RouteSecurity） | 采纳；与 H4/H5 并行，注意两张路由表与任务基座无耦合 |
| G7 配置收口 | 采纳 |
| G8 可读性棘轮 | 采纳；`QaRequestContext` 三段的实现并入 H2 |
| G9 前端（独立立项） | 采纳，独立 |
| G10 文档对齐 | 采纳；HANDOFF 头部版本与 §5.0b 已在本批一并修正 |

**三处分歧（其中第 1 条已撤回）**：

1. **适配层是否独立 —— 已撤回，不切模块**。初版建议把 232 个协议文件外提为 `:integrations`。按本仓既定的模块三问复核：① 不依赖 `:domains`——能过，但 SPI 必须一起走（`datasource/domain/connector` 8 件只被 datasource 自己引用 48 处）；② 金片与测试基建能随迁——能过（适配层 97 个测试文件仅 2 个引用 domains fixtures）；③ **迁移成本 ×2 小于强制力——不成立**：`check-capability-kinds.py` 已禁止跨 kind 直连，模块化不增加新强制力，只增加七处清单登记与守卫换锚（B211 试切 `:channels` 当天回退的同款教训）。故 **H8 改为"适配层逻辑化（零搬迁）"**：用落点规则 + 守卫约束协议实现包，不动文件。
2. **问答编排是否拆**：10-20 只把 `QaRequestContext` 34 字段三段拆分；本文认为 `ChatManage` 62 字段 + 第二编排层（`session/qa` 9 类）是同一问题的两个表征，应一起收口（H1/H2）。
3. **队列是否统一**：10-20 未列此项；本文列为 H4，理由是 7 套并行实现 + 进程内伪队列的跨实例语义缺口。

## 4. 目标架构

### 4.1 结构（7 模块，不新增模块）

```
boot（组合根，唯一装配点）
 ├─ domains（20 域 / 5 族 + 策略域；族内直连允许，族间只经 contract）
 │    ├─ 对话族：session · agentmanagement · memory · agenttools
 │    ├─ 知识族：knowledge · wiki
 │    ├─ 身份族：auth · system · audit · favorite
 │    ├─ 配置族：model · vectorstore · initialization · embedchannel
 │    ├─ 集成适配族：datasource · im · mcp · storage · websearch
 │    │     └─ 逻辑适配层（不切模块）：<域>/{connector|channel|protocol|oauth|provider}
 │    │        协议实现包不得依赖本域 service/domain，也不得跨域；由落点守卫约束
 │    └─ 策略域：approval（无 HTTP/无持久层，明示为 mcp 的策略实现）
 ├─ model-runtime / retrieval-engine / agent-engine（三 runtime 平级，零漂移）
 ├─ platform（纯技术适配；域词汇清零）
 └─ contract（纯形状 + 明示的契约共享算法；端口过准入）
```

**族**只用于认知与依赖规则，不强制搬包（10-20 的"不拆模块"不变）。`capability-kinds.json` 已有六类，升级为"族"并收紧族内豁免即可。

### 4.2 五条规则（守卫化）

| 规则 | 内容 | 守卫 |
|---|---|---|
| **R1 端口准入** | 分两档：① **跨模块 Port**——必须有 ≥2 实现、或有替换场景、或该能力是模块间唯一合法通道；② **域间 Port（同模块）**——受 R10 约束可保留，但必须"窄"：≤7 方法、只吃契约形状、不退化成域门面；单实现单调用且 ≤2 方法的退回域内 `api` 包，不占 contract | 扩 `gen-port-map.py` 三列；新增 `check-port-budget.py` |
| **R2 形状预算** | 契约类字段 ≤20（先卡明显膨胀，H6 后收紧到 12）；Port ≤7 方法；分页统一 `PageView<T>`；`*RowView` 禁入契约 | 新增 `check-contract-shape.py`（红态探针） |
| **R3 平台判据** | platform 不得 import `contract.<业务域>`；不得出现域词汇（知识/分块/会话/模型/渠道） | 换血 `check-platform-shapes.py`：从"后缀"改为"依赖面 + 词汇表" |
| **R4 链路单一入口** | 每条链路一个 orchestration 入口；第二编排层不得 import `controller/` | 目录规则 + 文档断言 |
| **R5 横切唯一** | 任务/事件/推流/分页/软删/租户/限流 各一套实现 | 每个横切一张只许减的登记表 |

### 4.3 两条链路的目标形态

```
问答：HTTP → QaTurnService.run(TurnCommand)
        ├─ TurnLifecycle：CREATED→PERSISTED→STREAMING→FINALIZING→DONE/FAILED
        │   （一个类持有状态与转换，取代 3 把锁 + 2 个轮询）
        ├─ Pipeline.run(TurnInput)
        │   QueryPlan → SearchOutcome → RerankOutcome → MergedContext → Answer
        └─ TurnEventSink（唯一出口：SSE / 内部 EventBus / IM 回写）

入库：IngestJob（DB 行 = 唯一状态）
        → IngestPipeline: Parse → Chunk → Embed → Index → Finalize → Enrich
        → TaskQueue：lease / renew / progress / retry / park / dead-letter
          （InProcess 与 Redis 只是同一端口的两个实现，配置选择）
```

## 5. 行动批次（H 系列；每批独立全绿）

| 批 | 内容 | 估时 | 验收 |
|---|---|---|---|
| **H0 判据先行** ✅ | R1~R3 三条守卫（各配红态探针）+ `business-capability-map.md`（业务链路 → 模块/包/类，一页纸）；R4/R5 的登记表随 H1/H4/H5 落地 | ✅ 2026-10-20 | **42 守卫绿**；新增 Port / 形状 / 平台业务件即红；基线 22 + 16 + 0 条 |
| **H1 问答-状态机** | **H1a ✅**：`TurnLifecycle` 作终态单赢家；**H1b-1 ✅**：`StreamEventEmitter`/`StreamResponseBuilder` 移 `service.sse`，`SseFrameSink` 端口，编排层不再依赖 `controller`。**H1b-2 待做**：stop watcher + 轮询 + 3 把锁收进状态机 | H1a/H1b-1 ✅ 2026-10-20 | 终态不可逆；编排层无 controller 反向依赖 |
| **H2a 问答-运行时段** ✅ | 抽 `TurnRuntime`（`eventBus`/`messageId`/`userMessageId`）；`ChatManage` 不再持有运行时字段，调用点经 `getRuntime()` | ✅ 2026-10-20 | 运行时段在类型上不可复制；会话测试全绿 |
| **H2b 问答-请求段** | **H2b-1 ✅**：`TurnInputView`（41 字段只读）+ `ChatManage implements` + 只读辅助收口；**H2b-2 待做**：插件签名改收视图、`QaRequestContext` 34 字段收口 | H2b-1 ✅ 2026-10-20 | 只读路径不再依赖可变对象；插件引用待 H2c 一并降 |
| **H2c 问答-状态段** | 15 状态字段按 5 组暴露（QueryPlan / SearchOutcome / RerankOutcome / MergedContext / Answer）；删 `ChatManage` | 1.5-2 人日 | `cloneChatManage` 消失；36 个消费类 → ≤10 |
| **H3 入库-状态机** | `IngestJob` 状态机（替代 4 处 CAS）+ `IngestStage` 接口（现有 5 个 stage 就地实现）+ 删 `KnowledgeTaskExecutor` | 3-4 人日 | 阶段显式化；主码 CAS 4→1；入库链路必读类 ≤10 |
| **H4a 任务-端口** | **H4a-1 ✅**：`TaskQueue`（name/start/stop/setRetryDelayOverrideSeconds）+ `RetryDelayOverride`；6 套队列接口继承；钩子 5→1 机制。**H4a-2/H4b**：`submit/lease/renew/progress/retry/park` 完整端口随实现迁移落地 | H4a-1 ✅ 2026-10-20 | 业务只依赖端口；钩子 5→1 |
| **H4b 任务-实现** ✅ | **datasource ✅ + wiki ✅**：基座补 per-task 预算/超时/幂等 + `FailurePolicy`（死信回调 / 锁冲突退避）；datasource ~120 行、wiki ~200 行薄适配；memory/knowledge 已在基座 | ✅ 2026-10-20 | 跨实例可恢复；wiki 特例参数化 |
| **H5a 分页统一** | **H5a-1 ✅**：`contract.pagination.PageResult<T>` 统一 11 个分页类型；**H5a-2**：管理端点加分页参数（默认页大小）+ 前端同步 | H5a-1 ✅ 2026-10-20；H5a-2 1 人日 | 形状 7→1；后台列表全部可分页 |
| **H5b 软删校验** ✅ | 新增 `SoftDeleteFilterGuard`：26 张带 `deleted_at` 的表，SELECT 主查询必须含谓词；**不做自动注入**；允许清单按需登记。实测存量 0 违规 | ✅ 2026-10-20 | 新增漏写即红；允许清单为空 |
| **H5c 软删收敛（可选）** | 仅在语义一致的表上逐步收进 `@TableLogic` / repository 基类；不追求 79→0 | 2-3 人日 | 不做满，只做低风险表 |
| **H6 契约-形状治理** | `KnowledgeCatalogPort` 拆窄（或退回域内）；`*RowView/*PageView` 回域；`PageView<T>` 统一；代理税端口逐个裁决 | 2-3 人日 | R1/R2 守卫绿；contract 类型净减；Port ≤7 方法 |
| **H7 平台-判据换血** | `check-platform-shapes.py` 改依赖面 + 域词汇；`platform.knowledge` / `settings` / `prompt` 业务件按 G3/G7 归位 | 1-2 人日 | platform 业务件 0（新判据）；与 G3 合并验收 |
| **H8 适配层逻辑化** | **不切模块、不搬文件**。加落点规则 + 守卫：协议实现只能在 `<域>/{connector\|channel\|protocol\|oauth\|provider}`；这些包不得 import 本域 `service/domain`，也不得跨域；生成 `integration-map.md`（协议包 / 管理面包 / SPI / 外部线格式） | 1-2 人日 | 新守卫绿（红态探针：connector 里 import 本域 service 即红）；映射表覆盖 5 域 |
| **H9 管理面模板化** | 凭证托管 4→1；CRUD 形状/分页/错误统一；`Responses/Dtos/Types/Rows/Views` → `Request/Response/Row` | 2-3 人日 | 4 套凭证 → 1；DTO 后缀 5→3；前端 api 同步 |

**顺序**：G1（P0）→ G10（半天，先消误导）→ H0 → G2/G3/G4（先还债，减轻后续 diff）→ **H5a（分页，最急）** → H4a（任务端口）→ H4b（按域迁实现）→ H5b（软删校验）→ H1 → H2a/H2b/H2c → H3 → H6/H7/H8 → H9。G5/G6/G7/G8 与 H 系列并行；G9 独立立项。

## 6. 目标度量

| 指标 | 现状 | 目标 |
|---|---:|---:|
| 问答链路必读类 / 跳数 | 36 类读写上下文 / 10~15 跳 | **≤10 / ≤5** |
| 入库链路必读类 | 18~30 | **≤10（6 阶段）** |
| 任务队列体系 | 7 套 / 12+ 实现类 | **1 端口 / 2 实现（wiki 最后迁）** |
| 手写软删谓词 | 79 文件 | **校验守卫 + 基线只许减（不做自动注入）** |
| 分页类型 | 7 种 | **1 种** |
| contract Port | 32（20+ 代理税） | **~20（跨模块 10 + 窄域间 10）** |
| `KnowledgeCatalogPort` | 34 方法 | **≤3 窄端口 / 或回域** |
| platform 业务件 | 约 10 类 / 90KB | **0** |
| 适配层混装 | 232 文件在 5 个业务域 | **0 跨域 import + 协议包落点 100% 一致** |
| >600 行主码文件 | 58 | **不增加**（先钉住，再逐项内聚论证） |
| ≥8 参构造器 | 49（极值 19 参） | **0**（参数对象） |

## 7. 拍板结论（2026-10-20）

| 项 | 决定 | 依据 |
|---|---|---|
| **H2** | **渐进三刀**：H2a 运行时段 → H2b 请求段（`TurnInputView`）→ H2c 状态段；配"`ChatManage` 消费点只许减"棘轮 | 17 插件顺序语义无编译期保护，金片锁不住中间状态；一次性拆的风险不对称 |
| **H4** | **先端口（H4a）再按域迁实现（H4b）**，顺序 datasource → memory → knowledge → wiki；知识主链的进程内队列按部署形态单独决定（单实例可保留，但须登记 `known-gaps.md`） | 7 套差异在进度模型与死信语义，不在队列语义；一次全迁会丢隐式行为 |
| **H5** | **不做自动注入**；先分页统一（H5a），再软删校验守卫（H5b），可选收敛（H5c） | 自动注入会误伤"故意读已删"场景（回收站/审计/恢复/对账），且现有测试套件跑 H2 数据库、测不出 PG 的真实行为；分页是更急的业务风险 |
| **H8** | 逻辑分层，不切 `:integrations` 模块 | 模块三问第 3 问不成立（kind 守卫已提供同等强制力；B211 教训） |

**重新评估软删自动注入的条件**（三者缺一不做）：PG + flyway 测试 profile 可用；完整的"故意读已删"白名单；表元数据（别名/表名）可靠可得。

## 8. 不做

1. 不按 20 域拆 20 个模块（L19/L23 两次实证）。
2. 不重写 RAG 管线 / `StreamManager` / `TenantContext`。
3. 不引入 DDD 全套（聚合/领域事件/值对象工厂）。
4. 不做微服务 / 事件溯源。
5. 不为"纯粹"把 platform 的 9 个**共享技术工具**搬回 domains（会让三个 runtime 反向依赖，L21/L22 判定仍成立）。注意与 H7 的区别：H7 归位的是 `platform.knowledge/settings/prompt` 的**业务件**，不是 `text/storage/security` 的技术工具。
6. 不动 1,349 金片与 connector 线格式。
7. 不切 `:integrations` 模块（模块三问第 3 问不成立：现有 kind 守卫已提供同等强制力，切模块只增加七处登记与守卫换锚；B211 同款教训）。

## 9. 风险

| 风险 | 缓解 |
|---|---|
| `ChatManage` 拆解（17 插件 / 36 类 / 隐式顺序） | H2 三刀 + 消费点棘轮；每刀独立提交；每阶段输入输出断言 |
| 队列迁移（在飞任务 / 双实例 / Redis 键格式） | 双写期 + 配置开关 + 死信保留；先无状态域（datasource sync），wiki 最后 |
| 协议包落点守卫误报（把合法的 domain SPI 当业务规则） | SPI 接口放 `<域>/domain`、实现在协议包；守卫只查"协议包 → service"方向，SPI 白名单登记 |
| 软删校验守卫误报（JOIN / 子查询里的 deleted_at） | 只查顶层 SELECT；回收站/审计等"故意读已删"查询白名单登记；基线只许减 |
| 新守卫误报 | 每条配红态探针 + 棘轮基线（只许减），沿用仓库纪律 |
