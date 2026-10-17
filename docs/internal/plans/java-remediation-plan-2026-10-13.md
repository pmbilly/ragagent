# Java 后端代码与架构整改方案（最终版）

> 基线：`/Users/billy/ragagent`，分支 `main`，提交 `0f468aa8`，2026-10-13
> 性质：**设计方与整改计划，尚未实施**。本文件不修改任何源码、构建配置或现有包结构。
> 命名口径：域内统一采用团队熟悉的 `controller / service / mapper / repository / domain / dto / config`。

## 0. 结论摘要

后端架构师反馈"代码可读性差"**有充分源码依据**。但根因不是"AI 写得多"，而是三条：

1. **文件拆分多于责任拆分**——协作者普遍回引大对象（`ObservePhase` 持有整个 `AgentEngine`），文件变短但依赖与状态未隔离。
2. **状态与副作用所有权不清**——SSE 写出行、停止 watcher、任务消费者、trace 各自修改业务状态；同一政策存在多份实现。
3. **迁移兼容逻辑主导设计**——大量"逐字节一致""Go 对齐"注释与重复实现，使读者难以判断当前真实不变量。

**建议：保留分层单体，渐进整改，不整体重写、不急于微服务化。**
先修终态、租户、幂等与任务持久性（正确性），再收敛用例边界与共享政策（可维护性），最后以自动化门禁防回退。
仅批量改名、补注释或继续按行数拆类，无法解决现有问题。


## 1. 审查范围与限制

### 1.1 范围

| 项目 | 数值 |
|---|---|
| 生产 Java 文件 | 1,928 个（`src/main/java`，不含测试与生成代码） |
| 生产源码非空行 | 257,997 行（含注释） |
| 现有生产包 | 254 个（`common` / `engine` / `domains` / `boot`） |
| 测试 Java 文件 | 444 个（**不等于覆盖率**） |

重点审查 `agent`、`knowledge` 及其贯穿的 `session` / `retrieval` / `chatpipeline`：
- Agent 相关 171 个文件 / 27,680 物理行
- knowledge 218 个文件 / 26,031 物理行
- session 115 个文件 / 22,352 物理行

### 1.2 限制

- 本轮**未修改业务源码**，未运行构建、测试、服务或线上复现。
- 未做逐文件全量审计、性能压测、AST 复杂度测量或真实覆盖率采集。
- 下文"确定"指源码中的实现事实；**生产是否已发生故障、影响比例与具体延迟不作推断**。
- 优先级 P1/P2 是本次整改建议顺序，不等同漏洞评级。


## 2. 现状架构

```
boot     → domains / engine / common
domains  → engine / common
engine   → common（api）
common   → 无生产 project 依赖
```

真实启动入口 `RagAgentApplication`；Java toolchain 21；声明版本 Spring Boot 3.3.5、MyBatis-Plus 3.5.7、protobuf 3.25.5、gRPC 1.66.0；Gradle wrapper 8.10.2（本轮未解析最终依赖树）。

### 2.1 需要保留的设计

- **编译级单向模块依赖**：`common` 无法反向引用业务模块，比单纯约定有效。
- **facts / ports**：检索侧依赖知识事实投影而非知识实体，方向正确。
- **能力集中**：`ToolRegistry` 已集中工具转换、校验与执行；检索已有统一混合检索底座、多 store ownership 校验与 typed 异常。
- **防回退机制**：ArchUnit、文件规模棘轮、契约测试、版本目录、opt-in 覆盖率均已接入。

### 2.2 边界与命名之间的落差

| 现象 | 证据 |
|---|---|
| `common` 并非纯契约模块 | `api` 暴露 Spring MVC、JDBC、Redis、Boot 自动配置与 MyBatis starter；源码注释"只放词汇/端口""不用 starter"与实际声明不一致（`common/build.gradle.kts:12-14、57-72`） |
| `domains` 无进一步编译隔离 | 数据源桥接器直接操作 knowledge mapper（`MapperKnowledgeBridge:216-234`） |
| 组合根承担业务恢复 | `StartupTaskRecovery` 位于 `boot/config` 却执行知识状态复位 SQL（`:128-174`） |
| 包级依赖环 | 静态 import 图检出 **13 个完整包循环强连通分量**，含 knowledge 的 repository/security/service/task 与 agent 的 runtime/compaction/prompt 等。**这是源码包依赖环，不是 Gradle 依赖环，也不是已证实的 Spring Bean 环** |
| 守卫自身有盲区 | `check-package-cycles.py:217-224` 的持久层引用检查缩进在内层循环之外，只检查每个 common 根目录最后读到的文件；包图仅解析普通 import 并聚合到顶层域 |
| 实际层次倒挂 | `ChunkRepository:136-138` 调 service 包的 `ChunkPortAdapter.factsAll`，而 `ChunkPortAdapter:33-38` 又注入仓储，构成类型环 |


## 3. 可读性差的八个根因

| 根因 | 源码体现 | 整改原则 |
|---|---|---|
| 1. 搬方法式拆类 | `ObservePhase:34-50` 构造只接整个 `AgentEngine`；`SessionQaResolution:112-118` 把整个 QA service 传给多个 helper | 按状态所有权与用例拆分；协作者只接自己需要的窄端口 |
| 2. 包可见共享状态 | `AgentEngine:105-154` 多个可变包字段；`QaSupport:46-129` 公开字段且直接共享 session、ObjectNode、列表引用 | 不可变输入快照 + 单一 RunState；集合防御复制 |
| 3. Controller 承担装配与业务 | `KnowledgeQaController:118-154` 14 个构造参数并手工创建协作者；`QaRequestParser:60-295` 单方法约 236 行，混合校验、权限、Agent 配置、附件落盘，且返回 Controller 内类型 | Controller 只绑定/响应；业务 DTO 独立；装配在 config |
| 4. 门面同时是工具类与实现 | `KnowledgeService:79-145` 828 行、16 个构造依赖，既委托又直接建知识/chunk；`KnowledgeAccessHelper:40-121` 回调其静态工具 | 门面只暴露用例；tenant/投影独立 |
| 5. 同一政策多处实现 | Agent 与 QA wiring 重复模型/追踪包装；worker 与 `ChunkVectorIndexer` 重复索引分批；`MapperKnowledgeBridge` 复制摄取逻辑 | 收敛模型运行时、摄取与索引政策 |
| 6. 名字不能预测行为 | `concurrentSearchByTargets` 实际顺序执行且日志写 concurrent；`KnowledgeVectorWrites.boundEngine` 返回 null 表示"回落直连" | 用业务角色命名；线格式兼容留在 DTO |
| 7. 历史账本占据源码 | 大量 Bxxx、Go 对齐、逐字节差异说明；`KnowledgeProcessWorker:616-629` 注释仍写 `pending_subtasks_count=1`，方法已接收多任务计数 | 源码注释解释当前不变量；迁移史放决策记录 |
| 8. 框架/存储形状进入上层 | 通用检索中间结果转成 `PgVectorRetrieveRepository` 的 `RetrieveResult`/`IndexHit`；`common` 暴露重框架 api；domain 实体引用 mapper TypeHandler | 中立 `SearchHit`；区分 persistence entity 与 domain |

**不是所有"大类"和"重复 DTO"都应删除。** `KnowledgeProcessWorker`（847 行）、`ActPhase`（763 行）、`AgentEngine`（734 行）、`SessionKnowledgeQaService`（753 行）、`AgentStreamBridge`（668 行）是审查热点，不是自动判罪；展示、持久化、模型上下文三种工具结果投影有不同脱敏与裁剪需求，不应为减少类数而合并。


## 4. 必须先修的正确性问题

> 这些是"确定实现缺口"或"条件性运行风险"，不是已复现的线上故障。每条都给出源码位置，便于先写失败测试再修。

### 4.1 会话与 Agent 生命周期（P1）

**A1 错误终态没有一致收口**
- NORMAL：模型 `ERROR+done` 或 120 秒队列超时只退出消费，未补全轮终态（`PluginChatCompletionStream:147-182、247-254`）；`QaTurnExecutor:149-175` 仅在 `FINAL_ANSWER.done` 时完成消息并发 COMPLETE；主 SSE 轮询仅 STOP/COMPLETE 退出（`QaSseOrchestrator:225-299`）。
- AGENT：`executeLoop` 的 `finally` 先发 COMPLETE，外层 catch 再发 ERROR（`AgentEngine:473-481、601-604`），完成载荷无显式失败 outcome（`FinalizePhase:151-156`）。
- 结论：存在"assistant 未完成、SSE 持续轮询"的确定路径；同批错误仍可能送达，下一批到达则可能在 SSE 已退出后漏送——**不能表述为"错误必然丢失"**。

**A2 完成幂等只在局部**
- STOP watcher 与 HTTP 消费都能发 STOP（`QaSseOrchestrator:99-106、177-188、228-239`），runner 的 finally 又收尾。
- `QaTurnFinalizer:108-160` 无全轮一次性提交门；消息更新失败只 warning，之后仍启动知识索引、建议与记忆蒸馏。
- 索引会新建 knowledge，最后才关联消息 → 重复索引或孤立产物风险。

**A3 "停止消费"不等于取消模型**
- LLM 流接口只返回 `BlockingQueue`，无 close/cancel 句柄（`LlmChatClient:26-30`）。
- `ThinkPhase:73-100、220-249` 的 poll 不读用户取消 probe；建流调用发生在 watchdog 计时初始化之前。
- `ConcurrencyChatClient:102-148` 可阻塞于 `inner.take`；`LangfuseChatClient:107-157` 用无界队列，可能掩盖消费者已放弃。

**A4 读工具存在写状态**
- `knowledge_search` 在并发读白名单（`ExecutionPolicy:14-31`），但同实例的 `LinkedHashSet seenChunks` 由 formatter 写入（`KnowledgeSearchTool:255-256`、`KnowledgeSearchOutputFormatter:154`），与"单实例顺序使用"注释矛盾。
- `ActPhase:235-271` acquire 中断后仍 release；父线程 join 中断后不取消/等齐子线程就继续收集结果，可能跨越写屏障。

**A5 跨线程上下文传播不统一**
- QA runner 捕获了 `TenantContextSnapshot`，但 NORMAL 流消费线程与 STOP watcher 未 replay（`PluginChatCompletionStream:127-131`）。
- `QaTurnFinalizer:57-68` 的 `runWithTenant` 只保存/切换"当时已有身份"，不会凭空恢复新线程缺失的 principal。

**A6 检索"无结果"与"失败"混淆**
- 检索各分支异常被 catch+warn，最终空列表渲染为 `success=true` 的"无相关内容"（`KnowledgeSearchTool:551-578`、`KnowledgeSearchOutputFormatter:86-106`）。
- 同 ID 去重第一遍已保留先到者，第二遍"保最高分"永远看不到被丢弃的高分项（`KnowledgeSearchRanking:103-151`）。

```
可推导反例：同 id=x，输入 [(score=0.1), (score=0.9)]
第一遍 seen 在 0.1 时登记 x，0.9 被 continue 丢弃；
第二遍最高分比较只剩 0.1，最终仍得 0.1。
```

### 4.2 知识摄取与任务（P1）

**K1 主 worker 绑定 store 路由丢失租户**
`KnowledgeProcessWorker:182-205` 只捕获 trace；`LangfuseTaskScope:48-73` 只恢复观测上下文；`KnowledgeVectorWrites:38-46` 却取 ThreadLocal 并把空值转 `0L`，再按 0 做 store ownership 校验（`RetrieveEngineFactories:118-140`）。非零租户的绑定 store 可能在预清理阶段即被拒绝，而 `preCleanKnowledge` 已先删 chunks（`:263-269`）。**主要是错误拒绝与可用性风险，不是已证实的跨租户写入。**

**K2 数据源 URL 摄取旁路未闭环**
`MapperKnowledgeBridge:216-234` 写 `source=url`、`filePath=""`，注释称"处理队列异步下载"；worker 非 manual 只读 filePath（`KnowledgeProcessWorker:273-288`），空路径经 `TenantFileStorage:118-127` → `LocalStorageService:60-62` 被拒。独立的 `KnowledgeService.createFromUrl:185-217` 确有下载实现，但不会救这个旁路。

**K3 DocReader 流错误被吞**
`DocReaderClient:107-156` 的 `onError` 只 countDown，不保存/传播原异常，`await` 返回值也被忽略。异步 `UNIMPLEMENTED` 不会正常进入 read 外层的 fallback；已有 meta 后发生流错误也可能返回成功。

**K4 CAS 只保护接管，不保护整轮数据**
pending→processing 有 CAS，但 `failOrComplete:759-805` 仅按 ID 改状态，失败清场按 knowledgeId 删全部 chunks/向量。`KnowledgeParseService:43-59` 不排斥处理中的任务；`KnowledgeFileService:174-199` 明确不写 `pending_subtasks_count` 列。

**K5 图任务失败/重试/计数不是同一契约**
`ChunkExtractService:116-159` 在处理 try 的 finally 每次 drain；`RedisChunkExtractTaskQueue:38-39` 丢弃 `finalAttempt`；`AbstractRedisKnowledgeTaskQueue:92-118` 无异常即 `complete`；`KnowledgeFinalizeAdapter:32-69` 的 `count>0` 只防负数，无任务级去重。`runExtract:196-233` 又把模型/抽取/写图错误转字符串，通常不抛出。**不能简单说"所有失败都重复扣槽"**：常见错误可能直接成功 ack，重复投递或 drain 后再抛异常才可能再次扣槽。

**K6 主任务是内存任务，恢复却按"有 Redis"跳过**
唯一 `KnowledgeProcessingQueue` 实现是进程内 executor（`KnowledgeProcessWorker:108-109、182-188`）；`StartupTaskRecovery:94-97、128-131` 依据 `REDIS_ADDR` 非空跳过知识复位；而真实 Redis 富化装配由 `knowledge.redis-enabled` 控制（`KnowledgeRedisWiring:33、50-78`）。`HousekeepingService:215-236` 可将卡住行判 failed，但不重投。

**K7 事务中有外部索引副作用**
`KnowledgeFileService:255、343-355` 的 `@Transactional` 方法写 chunks 后同步重建向量；`ChunkVectorIndexer:268-275` 对绑定引擎先删旧索引再 batchIndex。外部失败不能由本地事务恢复。

**K8 配置"可保存"但主摄取不消费**
`KnowledgeBaseChunkingConfig:18-25` 声明 parser rules、parent-child；worker 固定 `parserEngine=null`，仅转普通 SplitterConfig 并生成 text chunks（`KnowledgeProcessWorker:285-305、324、543-554`）。JSON 转配置失败还回落空默认（`:65-74`）。

**K9 多 store 排名与上下文扩展语义缺口**
`HybridSearchService:269-293` 将各 store 命中直接 `addAll`，RRF 以拼接位置算 rank，后一个 store 的本地第一名被降级（`HybridFusionOps:77-122`）。`HybridResultOps:67-124` 加载父/邻/关联块到 map，最终只遍历原命中，额外块未输出且产生无效读取——**此问题不直接适用于聊天合并路径**。

**K10 索引策略重复且 embedding 响应对齐缺校验**
worker 与 `ChunkVectorIndexer` 重复分批与构造；`EmbedderClient:72-117` 按响应数组顺序拼 vectors，未消费 `index`、未校验数量与维度；`VectorStoreService:55-86` 按位置配行。


## 5. 目标架构

### 5.1 模块划分

```
ragagent/
├── contracts/          纯 JDK：公开 API / SPI / 不可变载荷
├── platform/           框架、可靠执行与通用技术适配；不含业务状态机
├── agent-engine/       ReAct 算法、工具调度、上下文预算
├── retrieval-engine/   召回 / 融合 / 重排 / 上下文 / 索引与驱动
├── model-runtime/      模型调用、provider、流生命周期
├── domains/            业务域；跨域只经 contracts
└── boot/               唯一组合根；不执行业务 SQL
```

### 5.2 编译依赖方向

```
boot             → domains, agent-engine, retrieval-engine, model-runtime, platform, contracts
domains          → platform, contracts
agent-engine     → platform, contracts
retrieval-engine → platform, contracts
model-runtime    → platform, contracts
platform         → contracts
contracts        → JDK
```

- 三个 runtime **平级**，互不引用实现；跨模块协作经 contracts 接口，由 boot 注入。
- 业务域之间只允许 contracts 中的公开 API/SPI；本域 `repository`/内部 port 不出域。
- 仅各域的 `controller` / `mapper` / `client` / `task` / `config` 等边界类允许依赖 platform；`service` 与 `domain` 只用 JDK 与 contracts。
- **仍是单进程分层单体。** 先建立包边界与 ArchUnit 规则，确有编译隔离收益时再逐域拆 Gradle 模块。


## 6. 域内统一结构（最终命名）

```
domains/src/main/java/com/ragagent/<域>/
├── controller/   HTTP/SSE 绑定：端点、请求映射、帧写出、过滤器
├── service/      用例与政策；按能力再分（如 knowledge/service/ingest）
├── domain/       聚合、状态、不变量；不引用 Spring / Mapper
├── mapper/       MyBatis Mapper
├── repository/   仓储端口与实现（端口 XxxRepository，实现 MyBatisXxxRepository）
├── dto/          线格式 DTO 与兼容字段
├── client/       调用外部系统并转换到本域内部端口
├── task/         队列消费者与恢复任务；不拥有业务终态
├── api/          实现公开 contract，供其他模块调用
├── support/      本域共享纯工具
└── config/       只装配
```

| 层 | 允许 | 禁止 |
|---|---|---|
| `controller` | 参数绑定、DTO 映射、SSE 帧写出、过滤器 | 编排、解析业务配置、附件落盘、启动线程 |
| `service` | 用例编排、政策、事务边界声明 | import MyBatis / Redis / 厂商 SDK / Langfuse / Servlet |
| `domain` | 状态迁移方法、不变量校验 | 引用 Spring、Mapper、其他域 |
| `mapper` | SQL 映射 | 业务规则 |
| `repository` | 持久化实现、CAS、outbox 同事务 | 回引 service / controller |
| `config` | Bean 装配、属性绑定 | 写业务状态、跑恢复 SQL、隐藏默认能力 |

**不强制空四层。** 简单 CRUD 域（`favorite` / `settings` / `audit` / `system`）可只保留 `controller` + `service` + `mapper`；没有独立不变量与测试意义的类型允许用嵌套 record 合并。


## 7. 命名决定：`agenttools`（采纳）

原拟名 `agentintegration`，**最终决定改为 `agenttools`**。

### 7.1 采纳理由

1. **名字与内容一致。** 该包 54 个类中，`*Tool` 占绝大多数（`KnowledgeSearchTool`、`WikiReadPageTool`、`DatabaseQueryTool`、`WebSearchTool`、`DataAnalysisTool`…），"tools" 直接预测内容；"integration" 则引入了一个源码中不存在的概念。
2. **避免"integration"一词的既有语义冲突。** 在本仓库里 `integration` 已被两类用法占用：`*IT` 集成测试（`engine/build.gradle.kts:97-119` 的 `integrationTest` 任务）与"集成测试/契约测试"文档语境。用它命名业务包会让读者误以为是测试或企业集成模式（EIP / Spring Integration）相关。
3. **不与 `agent-engine` 模块的包命名空间混淆。** 该包位于 `domains` 模块，而 `com.ragagent.agent.*` 属于 `agent-engine` 模块。若命名为 `com.ragagent.agent.tools`，读者会误判其模块归属与依赖方向——这正是本次整改要消除的一类误读。
4. **与 `agentmanagement` 形成对称。** 两个包都在 `domains` 侧、都以 `agent` 为前缀、都服务 Agent 能力但**不属于 Agent 运行时**：`agentmanagement` 管定义，`agenttools` 管工具实现。命名对称，边界一目了然。
5. **迁移成本相同。** 两者都是新增包名，改名不增加任何额外工作量。

### 7.2 明确不采用的两个候选

| 候选 | 不采用原因 |
|---|---|
| `com.ragagent.agent.tools` | 与 `agent-engine` 模块的 `com.ragagent.agent.*` 命名空间重叠，误导模块归属 |
| `com.ragagent.tools` | 层级过浅，与 `domains` 下其他业务域不对称，且无法表达"服务 Agent"的语义 |

### 7.3 该包的边界（比名字更重要）

```
agenttools（domains 层）
  职责：实现 Agent 工具契约，把调用翻译为业务域公开用例，并把结果格式化为模型可读输出
  允许：Tool 实现类、参数映射（如 SearchArguments）、工具输出格式化、工具局部视图
  禁止：Agent 循环 / 工具注册 / schema 校验 / 审批（归 agent-engine）
        业务状态与持久化（归各业务域）
        排序与去重政策（归 retrieval-engine/policy）
  依赖：仅 contracts；不 import session / knowledge / wiki 的 service 或 mapper
```

按此规则，原在工具包内的 `KnowledgeSearchRanking`（排序与去重政策）与 `GrepChunksScoring`（内容签名）已迁出：前者归 `retrieval-engine` 的 `policy`，后者归 `support`。

### 7.4 遗留说明

包内仍含少量非 `*Tool` 的局部支撑类（`WikiSlugs`、`WikiTexts`、`FaqSnippet`、`PageView`、`AppliedChange` 等），它们是工具输出的局部视图与格式化辅助。**这些属于工具适配，不需要再拆包**；但若将来出现与工具无关的适配需求，应新建对应业务域包，而不是继续往 `agenttools` 里堆。


## 8. 完整目标结构

### 8.1 契约层 `contracts/`


- `contract.access/`
  `AccessContext`、`CallerIdentity`、`ResourceScope`
- `contract.agent/`
  `AgentCatalog`、`AgentDefinition`、`AgentExecutor`、`AgentProgress`、`AgentRunOutcome`、`AgentRunRequest`
- `contract.apikey/`
  `APIKeyCapability`、`APIKeyScopeContext`、`APIKeyScopeType`、`ApiKeyAdminPort`、`ApiKeyAuthPort`、`TenantAPIKeyScope`
- `contract.audit/`
  `WikiActivityAudit`
- `contract.error/`
  `AppError`、`ErrorCode`
- `contract.event/`
  `IntegrationEvent`
- `contract.event.payload/`
  `AgentActionData`、`AgentCompleteData`、`AgentFinalAnswerData`、`AgentPlanData`、`AgentQueryData`、`AgentReferencesData`、`AgentReflectionData`、`AgentStepData`、`AgentThoughtData`、`AgentToolCallData`、`AgentToolResultData`、`ChatData`、`ContextCompactedData`、`ErrorData`、`MCPOAuthRequiredData`、`MCPOAuthResolvedData`、`MemoryRecalledData`、`MergeData`、`QueryData`、`RerankData`、`RetrievalData`、`SessionTitleData`、`StopData`、`ToolApprovalRequiredData`、`ToolApprovalResolvedData`、`UserMessageInjectedData`
- `contract.execution/`
  `CancellationToken`、`ExecutionMetadata`、`ProgressSink`
- `contract.graph/`
  `GraphData`、`GraphNode`、`GraphRelation`、`NameSpace`
- `contract.integration/`
  `ExternalToolCatalog`、`WebResearch`
- `contract.knowledge/`
  `ChunkFacts`、`ChunkPort`、`ChunkSearchGateway`、`ChunkView`、`DocumentChunkMetadata`、`EmbeddingModelPort`、`FaqChunkMetadata`、`GeneratedQuestion`、`KnowledgeBaseFacts`、`KnowledgeBaseGateway`、`KnowledgeBaseLookup`、`KnowledgeBaseProvisioner`、`KnowledgeBaseSearchFacts`、`KnowledgeBaseSearchGateway`、`KnowledgeBaseView`、`KnowledgeDocumentFacts`、`KnowledgeDocumentGateway`、`KnowledgeFinalizePort`、`KnowledgeIngest`、`KnowledgeIngestCommand`、`KnowledgeIngestResult`、`KnowledgeReader`、`KnowledgeSpanPort`、`KnowledgeView`
- `contract.memory/`
  `MemoryConfig`、`MemoryItemView`、`MemoryKeys`、`MemoryKinds`、`MemoryRecall`、`MemoryRetrievalContext`
- `contract.model/`
  `ModelAccessResolver`、`ModelCall`、`ModelChunk`、`ModelFacts`、`ModelResponse`、`ModelRuntime`、`ModelStream`、`PromptCacheStatus`、`ResponseType`、`TokenUsage`
- `contract.model.chat/`
  `CacheRetention`、`ChatConfig`、`ChatMessage`、`ChatOptions`、`ChatResponse`、`ChatTool`、`FunctionCall`、`FunctionDef`、`ImageUrl`、`MessageContentPart`、`ToolCall`
- `contract.parsing/`
  `DocumentParseResult`、`DocumentParsing`
- `contract.retrieval/`
  `ChunkTypes`、`ContextAssembler`、`ContextBundle`、`ContextRequest`、`IndexDocument`、`IndexRequest`、`IndexService`、`KnowledgeSearch`、`RetrievalCatalog`、`RetrievalDriverProperties`、`RetrievalService`、`SearchChunkMerge`、`SearchHit`、`SearchOutcome`、`SearchParams`、`SearchPolicy`、`SearchRequest`、`SearchResult`、`SearchTarget`
- `contract.session/`
  `ConversationSearch`、`ConversationTurns`、`PipelineMessageAttachmentView`、`PipelineMessageImageView`、`PipelineMessageView`、`PipelineUsedMemoryView`、`SessionDocuments`、`SessionMessagePort`、`SubmitTurn`、`TurnReceipt`、`TurnStreams`
- `contract.settings/`
  `EffectiveSettings`
- `contract.storage/`
  `StorageAccessResolver`、`StorageBackendProvisioner`、`StorageRef`
- `contract.task/`
  `TaskEnvelope`、`TaskPublisher`
- `contract.tenant/`
  `TenantAccess`、`TenantConfigLookup`、`TenantProperties`、`TenantRole`、`WebSearchConfig`
- `contract.tool/`
  `Tool`、`ToolApproval`、`ToolDefinition`、`ToolInvocation`、`ToolResult`
- `contract.vectorstore/`
  `ConnectionConfig`、`EnvStoreIds`、`IndexConfig`、`StoreAccessResolver`、`StoreConnection`、`VectorStoreLookup`、`VectorStoreView`
- `contract.websearch/`
  `ImageInfo`、`WebSearchFilters`、`WebSearchRequest`、`WebSearchResult`
- `contract.wiki/`
  `ExtractedItem`、`LanguageProperties`、`SlugUpdate`、`WikiFinalizePort`、`WikiImageMarkup`、`WikiIngestPort`、`WikiLanguageSupport`、`WikiService`

规则：只公布跨模块确有消费者的 API/SPI；本域 repository/port 不得全部搬到 contracts。
contracts 零 Spring / MyBatis / Redis / Jackson / Langfuse / 云 SDK 依赖；wire 序列化由 `controller` / `dto` 承担。

### 8.2 平台层 `platform/`


- `platform.access/`
  `DelegatedTaskIdentityResolver`、`RequestContext`、`TenantContextSnapshot`、`TracingContext`、`VerifiedAccessContextFactory`
- `platform.config/`
  `AppEnvLookup`、`DeploymentProperties`
- `platform.event/`
  `Event`、`EventBus`、`EventBusAdapter`、`EventBusException`、`EventBusInterface`、`EventHandler`、`EventIds`、`EventJson`、`EventMiddleware`、`EventType`、`GlobalEventBus`、`PanicError`
- `platform.execution/`
  `BoundedTaskGroup`、`ScopedTaskExecutor`
- `platform.http/`
  `RedirectPolicy`、`SafeHttpClient`、`SsrfPolicy`
- `platform.model/`
  `EmbeddingGateway`、`ModelGateway`
- `platform.outbox/`
  `OutboxDispatcher`、`OutboxReader`、`OutboxRecord`
- `platform.parsing/`
  `DocReaderResponseMapper`、`GrpcDocumentParsing`
- `platform.persistence/`
  `DatabaseDialects`、`FullTableWriteGuard`、`NaiveOffsetDateTimeTypeHandler`、`PageRequests`、`PgJsonTypeHandler`、`TenantFilterGuard`
- `platform.prompt/`
  `AgentPromptPlaceholders`、`MessageAttachmentsPrompt`、`PromptComposer`、`PromptConstants`、`PromptInstructions`、`PromptSource`、`PromptTemplateCatalog`、`PromptTemplates`
- `platform.security/`
  `CryptoEnvProperties`、`CryptoService`、`InputSanitizer`、`IpClass`、`LogSanitizer`、`SsrfGuard`、`SsrfWhitelistProperties`、`UserNameLookup`
- `platform.storage/`
  `ProviderContentStorage`、`StoragePathGuard`、`StorageReferenceCodec`
- `platform.storage.config/`
  `StorageAllowList`、`StorageAllowListProperties`、`StorageRuntimeEnv`、`UploadLimitProperties`、`UploadLimits`
- `platform.storage.provider/`
  `CosFileService`、`FileService`、`FileServiceFactory`、`LocalFileService`、`OssFileService`、`S3CompatibleFileService`、`SeekableFileService`、`SeekableSource`、`StorageObjects`、`TenantFileServiceResolver`、`TosFileService`
- `platform.stream/`
  `LiveRun`、`LiveRunExistsException`、`LiveRunPayload`、`MemoryStreamManager`、`RedisStreamManager`、`StreamBatch`、`StreamEvent`、`StreamJson`、`StreamManagerConfig`、`StreamProperties`、`StreamStopWatcher`、`StreamStoreException`、`TurnStreamStore`
- `platform.task/`
  `RedisTaskQueueCore`、`TaskConsumerRegistry`、`TaskLeaseSupport`
- `platform.text/`
  `CleanInvalidUtf8`、`CodePointOrder`、`ContentTypeByFilename`、`HtmlConversionException`、`HtmlEntities`、`HtmlText`、`HtmlToMarkdown`、`JdkHtmlToMarkdown`、`ListMerges`、`PosixPath`、`TextConv`、`Whitespace`
- `platform.tracing.decorators/`
  `LangfuseEmbedder`、`LangfusePayloads`、`LangfuseReranker`、`LangfuseVlm`、`LangfuseWiring`、`TracedChatClient`
- `platform.tracing.langfuse/`
  `BatchSpanProcessor`、`DefaultLangfuseManager`、`Generation`、`LangfuseAttributes`、`LangfuseConfig`、`LangfuseContext`、`LangfuseEnvProperties`、`LangfuseHttpInterceptor`、`LangfuseManager`、`LangfuseRegistry`、`LangfuseTaskScope`、`LangfuseTokenUsage`、`LangfuseTracing`、`NoopLangfuseManager`、`OtlpHttpExporter`、`RecordedSpan`、`Span`、`Trace`
- `platform.transaction/`
  `SpringUnitOfWork`
- `platform.web/`
  `ApiResponse`、`ApiResult`、`ApiResultAdvice`、`ApiResultInterceptor`、`ApiResultSupport`、`JsonMappers`、`NonNullBody`、`NonNullRequestBodyAdvice`、`PageParams`、`ProviderJson`、`RbacInterceptor`、`RejectEmptyBody`、`RequestFields`、`RequestIdFilter`、`SortedMapSerializer`、`ToolJson`、`ZeroTimeSerializer`
- `platform.web.error/`
  `BizException`、`GlobalExceptionHandler`、`GuardForbiddenException`、`PlainErrorException`
- `platform.web.health/`
  `HealthController`

驱动 owner：模型 provider 归 `model-runtime`，向量 store 归 `retrieval-engine`，文件 store 归 `platform/storage`，文档解析归 `platform/parsing`。
平台不包含 `KnowledgeService`、轮次状态机、业务任务恢复 SQL——避免形成新一轮巨型 `common`。

### 8.3 Agent 运行时 `agent-engine/`


- `agent.context/`
  `AgentBudgets`、`CitationStreamExpander`、`ContextDiagnostics`、`HandleStore`、`HandleTable`、`JsonValues`、`ModelOutput`、`Registry`、`ResourceRegistry`、`SourceRegistry`、`SourceToolCodec`、`StreamDecoder`、`TokenEstimator`、`ToolPolicy`
- `agent.context.compaction/`
  `CompactionOverflow`、`CompactionPreparation`、`CompactionPrompts`、`CompactionReason`、`CompactionResult`、`CompactionSettings`、`Compactor`、`ConversationSerializer`、`CutPoint`、`FileOps`、`NothingToCompactException`
- `agent.conversation/`
  `AgentConversation`、`AgentConversationBuilder`、`SteerInbox`、`ToolMessageMapper`
- `agent.domain/`
  `AgentStep`、`AgentToolCall`、`ToolCallTarget`
- `agent.policy/`
  `ContextBudgetPolicy`、`IterationPolicy`、`RetryPolicy`、`ToolSchedulingPolicy`
- `agent.prompt/`
  `AgentPromptTemplates`、`GroundingPrompt`、`KnowledgeBaseInfo`、`PinnedMCPServiceInfo`、`PinnedSkillInfo`、`PinnedSkillInstructions`、`PlaceholderDefinition`、`RecentDocInfo`、`SelectedDocumentInfo`、`SystemPromptSection`
- `agent.runtime/`
  `AgentConfig`、`AgentConsts`、`AgentEngineException`、`AgentFinalizer`、`AgentIteration`、`AgentPrompts`、`AgentRunSpec`、`AgentRunState`、`AgentRunner`、`AgentToolNames`、`ContextDiagnosticsEmitter`、`FallbackAnswer`、`IterationDecision`、`ModelTurn`、`Observation`、`SkillMetadata`、`SteerSink`、`ToolBatch`、`ToolImages`
- `agent.tool/`
  `AgentTool`、`ArgumentValidator`、`BaseTool`、`Cleanable`、`DocChunkSupport`、`ExecutionPolicy`、`McpCatalogGuardedTool`、`OutputBudgets`、`OutputLimitProvider`、`ParamCaster`、`ParamValidator`、`SequentialThinkingTool`、`SkillReadFileTool`、`TodoWriteTool`、`ToolCancellation`、`ToolCapabilities`、`ToolDefinitions`、`ToolExecContext`、`ToolExecutionContext`、`ToolExecutor`、`ToolOutput`、`ToolRegistry`、`ToolRequest`、`ToolResultProjector`、`ToolRunState`
- `agent.tool.runtime/`
  `JsonRepair`、`MessageSanitizer`、`NormalizeToolCallId`、`PythonSyntax`、`SearchAuth`、`ThinkBlocks`、`ThinkStreamSplitter`、`ToolResultPersist`
- `agent.tool.spi/`
  `McpToolingGateway`、`ToolRegistryPort`

禁止：`new ObservePhase(this)` 式回引、协作者持有整个 `AgentRunner`、公共可变字段、用可变包装类模拟引用传参。

### 8.4 检索运行时 `retrieval-engine/`


- `retrieval.adapter.api/`
  `ContextAssemblerAdapter`、`IndexServiceAdapter`、`RetrievalServiceAdapter`
- `retrieval.adapter.artifact/`
  `ArtifactReferenceRewriter`
- `retrieval.adapter.persistence.doris/`
  `DorisAdminOps`、`DorisCompatMode`、`DorisRetrieveRepository`、`DorisSearchOps`、`DorisSql`、`DorisSqlExecutor`、`DorisStreamLoadClient`、`DorisVectorEmbedding`、`DorisWriteOps`、`JdbcDorisSqlExecutor`
- `retrieval.adapter.persistence.elasticsearch/`
  `ElasticsearchV7RetrieveRepository`、`ElasticsearchV7SearchOps`、`ElasticsearchV7WriteOps`、`ElasticsearchV8RetrieveRepository`、`ElasticsearchV8SearchOps`、`ElasticsearchV8WriteOps`
- `retrieval.adapter.persistence.milvus/`
  `MilvusFilter`、`MilvusNumbers`、`MilvusRestClient`、`MilvusRetrieveRepository`、`MilvusSearchOps`、`MilvusVectorEmbedding`、`MilvusWriteOps`
- `retrieval.adapter.persistence.neo4j/`
  `Neo4jGraphConfig`、`Neo4jGraphRepository`、`Neo4jProperties`、`RetrieveGraphRepository`
- `retrieval.adapter.persistence.opensearch/`
  `OpenSearchAdminOps`、`OpenSearchDriverException`、`OpenSearchRetrieveRepository`、`OpenSearchSearchOps`、`OpenSearchWriteOps`
- `retrieval.adapter.persistence.qdrant/`
  `QdrantRestClient`、`QdrantRetrieveRepository`、`QdrantSearchOps`、`QdrantVectorEmbedding`、`QdrantWriteOps`
- `retrieval.adapter.persistence.sqlite/`
  `SqliteCjkBigram`、`SqliteRetrieveRepository`、`SqliteSearchOps`、`SqliteWriteOps`
- `retrieval.adapter.persistence.tencentvectordb/`
  `JiebaTokenizer`、`TencentVectorDbBm25`、`TencentVectorDbRestClient`、`TencentVectorDbRetrieveRepository`、`TencentVectorDbSearchOps`、`TencentVectorDbWriteOps`
- `retrieval.adapter.persistence.weaviate/`
  `WeaviateGql`、`WeaviateRestClient`、`WeaviateRetrieveRepository`、`WeaviateSearchOps`、`WeaviateVectorEmbedding`、`WeaviateWriteOps`
- `retrieval.application/`
  `ContextAssembly`、`IndexExecutionService`
- `retrieval.application.engine/`
  `CompositeRetrieveEngine`、`EffectiveEngines`、`EngineAwareNormalizer`、`EngineFactory`、`EngineRegistry`、`EngineTypes`、`KeywordsVectorHybridRetrieveEngineService`、`PgVectorEngineRepository`、`PgVectorRetrieveRepository`、`RetrieveEngineException`、`RetrieveEngineFactories`、`RetrieveEngineRegistry`、`RetrieveEngineRepository`、`RetrieveEngineService`、`RetrieverEngineParams`、`ScoreNormalizer`、`StoreEngineFactory`、`TenantStoreOwnership`、`VectorStoreRepoOwnership`、`VectorStoreService`
- `retrieval.config/`
  `RetrievalEnvLookup`
- `retrieval.execution/`
  `CandidateReranker`、`ContextChunkLoader`、`EmbeddingBatchExecutor`、`IndexExecution`、`RecallExecutor`、`SearchExecution`、`SearchResultAssembler`、`StoreIndexWriter`
- `retrieval.policy/`
  `CandidateDeduplication`、`ContextExpansionPolicy`、`FaqRetrievalPolicy`、`IndexWritePolicy`、`KnowledgeSearchRanking`、`RankFusionPolicy`、`RecallBudgetPolicy`、`RerankPolicy`、`StoreGroupingPolicy`
- `retrieval.support/`
  `ChunkSearchUtil`、`GrepChunksScoring`、`ImageInfoCollector`、`ImageInfoEnricher`、`ImageInfoMatchUtil`、`KeywordScoreNormalizer`、`MatchTypes`、`MemoryUsedMemories`、`QueryTokenizer`、`ReferencesSupport`、`SearchSupport`、`SearchTextUtil`、`WebResultConverter`
- `retrieval.telemetry/`
  `RetrievalObs`

检索政策可复用，由调用者选择不同 `SearchPolicy`：直接检索不必 rerank，RAG 与 Agent 可选不同上下文与预算。
工具侧不得再实现第二套排序与去重。

### 8.5 模型运行时 `model-runtime/`


- `modelruntime.adapter.access/`
  `ModelAccessClient`、`ModelCredentialAdapter`
- `modelruntime.adapter.api/`
  `ModelRuntimeAdapter`
- `modelruntime.adapter.provider.asr/`
  `AsrTranscriber`
- `modelruntime.adapter.provider.chat/`
  `CompletionBudget`、`ImageResolver`、`JsonFieldExtractor`、`OllamaChat`、`OpenAiStreamState`、`PromptCache`、`RemoteApiBodyCodec`、`RemoteApiChat`、`RemoteApiRequestOps`、`RemoteApiResponseOps`、`RemoteApiStreamOps`、`RemoteHttpOps`
- `modelruntime.adapter.provider.chat.anthropic/`
  `AnthropicCacheControl`、`AnthropicChat`、`AnthropicContentBlock`、`AnthropicMessage`、`AnthropicMessages`、`AnthropicRequest`、`AnthropicResponse`、`AnthropicStreamEvent`、`AnthropicTool`、`AnthropicToolChoice`、`AnthropicToolStream`
- `modelruntime.adapter.provider.chat.common/`
  `BaseProvider`、`ConcurrencyChatClient`、`LlmChatClients`、`LlmTransport`、`LlmTransportWiring`、`ProviderAdapter`、`ProviderAdapters`、`SseReader`、`ThinkingEmitter`、`ThinkingStrategies`、`ThinkingStrategy`
- `modelruntime.adapter.provider.embedding/`
  `AliyunEmbedder`、`AzureOpenAiEmbedder`、`BaseEmbedder`、`GeminiEmbedder`、`JinaEmbedder`、`NvidiaEmbedder`、`OllamaEmbedder`、`OpenAiEmbedder`、`VolcengineEmbedder`、`ZhipuEmbedder`
- `modelruntime.adapter.provider.ollama/`
  `OllamaChatRequest`、`OllamaChatResponse`、`OllamaMessage`、`OllamaModelInfo`、`OllamaService`、`OllamaTool`、`OllamaToolCall`
- `modelruntime.adapter.provider.registry/`
  `AliyunProvider`、`AnthropicProvider`、`AzureOpenAIProvider`、`Config`、`DeepSeekProvider`、`ExtraFieldConfig`、`GPUStackProvider`、`GeminiProvider`、`GenericProvider`、`HunyuanProvider`、`JinaProvider`、`LKEAPProvider`、`LiteLLMProvider`、`LongCatProvider`、`MimoProvider`、`MiniMaxProvider`、`ModelScopeProvider`、`ModelType`、`MoonshotProvider`、`NovitaProvider`、`NvidiaProvider`、`OpenAIProvider`、`OpenRouterProvider`、`Provider`、`ProviderBaseURLs`、`ProviderInfo`、`ProviderName`、`ProviderRegistry`、`QianfanProvider`、`QiniuProvider`、`RequestyProvider`、`SiliconFlowProvider`、`VolcengineProvider`、`ZhipuProvider`
- `modelruntime.adapter.provider.rerank/`
  `AliyunReranker`、`JinaReranker`、`LkeapReranker`、`NvidiaReranker`、`OpenAiReranker`、`VolcengineReranker`、`ZhipuReranker`
- `modelruntime.adapter.provider.vlm/`
  `VlmClient`、`VlmHttpTransport`
- `modelruntime.application/`
  `BatchEmbedder`、`ChatExecution`、`ConcurrencyEmbedder`、`EmbedQueryContext`、`Embedder`、`EmbedderConfig`、`EmbedderFactory`、`EmbedderPooler`、`EmbeddingExecution`、`EmbeddingHttp`、`RerankExecution`、`TranscriptionExecution`
- `modelruntime.application.port/`
  `LlmChatClient`
- `modelruntime.application.rerank/`
  `RankResult`、`RerankHttp`、`Reranker`、`RerankerConfig`、`RerankerFactory`
- `modelruntime.domain.stream/`
  `StreamResponse`
- `modelruntime.policy/`
  `EmbeddingResponseValidator`、`ModelDeadlinePolicy`、`ProviderRetryPolicy`
- `modelruntime.policy.concurrency/`
  `BackgroundTaskContext`、`ConcurrencyGovernor`、`LocalLimiter`、`ModelConcurrencyLimiter`、`RedisLimiter`、`Release`、`RuntimeInspectable`、`RuntimeStat`、`TrackedSemaphore`
- `modelruntime.stream/`
  `ManagedModelStream`、`StreamAccumulator`、`StreamLifecycle`

厂商列表是示例：现有 provider 按协议家族归类并保留能力，不据此删除未列出的实现。
只有 `model-runtime` 的受限 credential resolver 能取调用密钥；不允许缓存 client 成为永久授权。

### 8.6 组合根 `boot/`


- `boot/`
  `RagAgentApplication`
- `boot.config/`
  `AppEnvLookupEnvironmentPostProcessor`、`ApplicationAssembly`、`CrossDomainBindings`、`EnvLookupWiring`、`EnvPropertyLookup`、`HttpContractConfiguration`、`ImAdapterWiringConfig`、`ModelConcurrencyGovernorWiring`、`MybatisPlusConfig`、`ObservabilityConfiguration`、`OpenSearchAuditSinkAdapter`、`RetrievalEngineWiringConfig`、`RuntimeBindings`、`RuntimeSnapshotWiring`、`WebConfig`、`WebSecurityConfiguration`
### 8.7 业务域总览

| 域 | 包数 | 文件数 | 职责 |
|---|---:|---:|---|
| `knowledge` | 29 | 248 | 知识库、文档摄取处理、索引、检索用例与 FAQ |
| `session` | 21 | 183 | 对话会话与单轮执行：提交、准备、取消、终态提交与轮次富化 |
| `wiki` | 17 | 147 | 知识库内联百科：页面、修订、链接图与摄取管线 |
| `mcp` | 9 | 125 | MCP 协议实现、OAuth、服务注册与工具调用面 |
| `datasource` | 16 | 124 | 外部数据源连接器、游标推进与同步编排 |
| `auth` | 13 | 82 | 登录、令牌签发与校验、用户目录与 API Key |
| `im` | 14 | 79 | 九渠道即时通讯接入与渠道无关编排 |
| `memory` | 10 | 72 | 长期记忆的抽取、去重合并、召回与生命周期 |
| `agenttools` | 5 | 54 | Agent 工具实现：把工具调用翻译为业务域公开用例调用 |
| `storage` | 8 | 41 | 存储后端配置与文件读写代理 |
| `websearch` | 7 | 35 | 联网搜索 provider 目录与搜索执行 |
| `model` | 5 | 25 | 模型条目、provider 配置与调用授权（不含模型调用实现） |
| `approval` | 4 | 24 | 工具人工审批门与跨实例 pub/sub |
| `agentmanagement` | 7 | 20 | Agent 定义、版本、发布与授权解析 |
| `evaluation` | 5 | 20 | 评估任务与指标计算 |
| `embedchannel` | 6 | 15 | 对外嵌入问答渠道与访客鉴权 |
| `audit` | 5 | 13 | 审计写入、查询与保留期清理 |
| `vectorstore` | 5 | 11 | 向量库配置、类型目录与连通性校验 |
| `initialization` | 3 | 10 | 系统初始化与模型连通性测试 |
| `system` | 5 | 10 | 系统设置读写与管理员面 |
| `tenant` | 3 | 10 | 租户、成员与 RBAC 业务 |
| `skill` | 2 | 8 | 技能定义、执行与沙箱 |
| `favorite` | 4 | 4 | 用户资源收藏 |
| `settings` | 1 | 3 | 跨域共享设置注册表与只读端口 |
| **合计** | **204** | **1363** | |

### 8.8 重点域结构

#### 8.8.1 `session/`（对话与单轮执行）


- `api/`
  `ConversationSearchAdapter`、`ConversationTurnsAdapter`、`SessionDocumentsAdapter`、`TurnStreamsAdapter`
- `controller/`
  `AgentChatController`、`ChatRequestMapper`、`ConversationController`、`KnowledgeChatController`、`MessageController`、`MessageFileProxyController`、`MessageSuggestionController`、`QaRequestBinder`、`SessionController`、`SessionStreamController`、`SseTurnWriter`、`SteerController`、`TemporaryDocumentController`、`TurnStreamController`
- `controller.sse/`
  `SseContract`、`SseFrameWriter`、`StreamEventEmitter`、`StreamResponseBuilder`
- `domain/`
  `AbstractJsonListTypeHandler`、`AgentStepListTypeHandler`、`ArtifactVersions`、`ChatHistoryKbStats`、`Conversation`、`ConversationHistory`、`ConversationTurn`、`MentionedItem`、`MentionedItemListTypeHandler`、`SearchResultListTypeHandler`、`SessionDocument`、`SuggestionAttribution`、`SuggestionItem`、`SuggestionItemListTypeHandler`、`TurnCompleted`、`TurnOutcome`、`TurnPlan`、`TurnStatus`、`UsedMemory`、`UsedMemoryListTypeHandler`
- `domain.conversation/`
  `EmbedVisitorSessionRow`、`Session`、`SessionLastRequestState`、`SessionListItem`、`SessionListQuery`、`SessionNotFoundException`、`SessionOwnerIds`、`SessionPage`
- `domain.document/`
  `TemporaryDocument`
- `domain.message/`
  `Message`、`MessageArtifact`、`MessageArtifactListTypeHandler`、`MessageAttachment`、`MessageAttachmentListTypeHandler`、`MessageExecutionContext`、`MessageImage`、`MessageImageListTypeHandler`、`MessageNotFoundException`、`MessageSearchGroupItem`、`MessageSearchResult`、`MessageSuggestionEvent`、`MessageSuggestionSet`、`MessageSuggestionSetNotFoundException`、`MessageWithSession`
- `dto/`
  `ArtifactView`、`BatchDeleteSessionsRequest`、`CreateSessionRequest`、`GenerateTitleRequest`、`GenerateTitleResponse`、`QaRequests`、`SearchMessagesRequest`、`SessionListResponse`、`SessionPinResponse`、`StopSessionRequest`、`UpdateSessionRequest`
- `event/`
  `TurnOutcomeProjector`、`TurnProgressProjector`
- `mapper/`
  `MessageMapper`、`MessageSuggestionMapper`、`SessionMapper`、`TemporaryDocumentMapper`
- `repository/`
  `CompletionOutbox`、`ConversationRepository`、`MessageRepository`、`MessageSuggestionRepository`、`SessionDocumentRepository`、`SessionRepository`、`SessionUnitOfWork`、`TemporaryDocumentRepository`、`TurnEventStore`、`TurnRepository`
- `service/`
  `ArtifactCollectorWiring`、`ConversationMessageService`、`ConversationService`、`KnowledgeSearchBackendFactory`、`MessageSearch`、`SessionDocumentService`、`SessionLookupScope`、`VlmDescriberWiring`
- `service.attachment/`
  `AttachmentFileStore`、`ExpireSessionDocuments`、`PrepareAttachments`、`ProcessSessionDocument`、`PromoteSessionDocument`、`QaAttachmentResolver`、`TemporaryDocumentProcessor`、`TemporaryDocumentPromptResolver`
- `service.enrichment/`
  `GenerateTurnSuggestions`、`IndexCompletedTurn`、`LearnTurnMemory`
- `service.history/`
  `AgentConfigAssembler`、`AgentHistoryAssembler`、`AgentResolver`、`AgentRunAssembler`、`AgentStreamEmitter`、`AgentToolCatalogFactory`、`AgentToolWikiBackends`、`AgentWebPages`
- `service.rag/`
  `ChatManage`、`EventManager`、`History`、`PipelineBuilder`、`PipelineCommon`、`PipelineEventType`、`PipelineLog`、`PipelinePorts`、`PipelineProgress`、`QueryIntent`、`RagPipelineWiring`、`RagStageOrder`、`RagTurnApplicationService`、`SummaryConfig`
- `service.rag.stage/`
  `MergeParentOps`、`Plugin`、`PluginChatCompletion`、`PluginChatCompletionStream`、`PluginDataAnalysis`、`PluginError`、`PluginExpansionOps`、`PluginExtractEntity`、`PluginFilterTopK`、`PluginIntoChatMessage`、`PluginLoadHistory`、`PluginMemoryAffinity`、`PluginMemoryRecall`、`PluginMerge`、`PluginQueryUnderstand`、`PluginRerank`、`PluginSearch`、`PluginSearchEntity`、`PluginSearchOps`、`PluginSearchParallel`、`PluginWebFetch`、`PluginWikiBoost`、`QueryTextOps`
- `service.suggestion/`
  `MessageSuggestionPipeline`、`MessageSuggestionService`
- `service.turn/`
  `AgentTurnApplicationService`、`CancelTurnUseCase`、`CompleteTurnUseCase`、`ContinueTurnUseCase`、`QaAgentConfig`、`QaChatManageOverrides`、`QaKbScope`、`QaMentionTagScope`、`QaModelSelection`、`QaSearchTargets`、`QaWebSearchParams`、`QuickAnswerExecution`、`SessionQaFallback`、`SessionQaResolution`、`SteerRunCoordinator`、`SteerSinkBridge`、`SubmitTurnUseCase`、`TurnAccessPolicy`、`TurnExecutionCoordinator`、`TurnPlanSupport`、`TurnPreparation`
- `support/`
  `MessageReferenceRewriter`、`PipelineViews`
- `task/`
  `TurnEnrichmentConsumer`

#### 8.8.2 `knowledge/`（知识与索引）


- `api/`
  `KnowledgeIngestAdapter`、`KnowledgeReaderAdapter`、`KnowledgeSearchAdapter`、`RetrievalCatalogAdapter`
- `chunker/`
  `ChunkPatterns`、`ChunkValidator`、`Chunker`、`CodePoints`、`DocumentProfiler`、`HeaderTracker`、`HeadingHierarchy`、`HeadingSplitter`、`HeuristicSplitter`、`LegacySplitter`、`ParsedChunk`、`SplitterConfig`、`TextNormalizer`、`Tokens`
- `client/`
  `EmbeddingExecutionClient`
- `config/`
  `BatchEmbedProperties`、`DocReaderProperties`、`HousekeepingProperties`
- `controller/`
  `ChunkController`、`ChunkerPreviewController`、`FaqController`、`KnowledgeBaseController`、`KnowledgeBaseFileProxyController`、`KnowledgeController`、`KnowledgeOperationsController`、`KnowledgeTagController`
- `domain/`
  `Chunk`、`ChunkNotFoundException`、`ChunkRevision`、`ChunkRevisionConflictException`、`EnableStatus`、`ExtractChunkPayload`、`Knowledge`、`KnowledgeBase`、`KnowledgeBaseAsrConfig`、`KnowledgeBaseChunkingConfig`、`KnowledgeBaseImageProcessingConfig`、`KnowledgeBaseIndexingStrategy`、`KnowledgeBaseStorageConfig`、`KnowledgeBaseStorageProviderConfig`、`KnowledgeBaseVlmConfig`、`KnowledgeProcessingSpan`、`KnowledgeTag`、`ParseStatus`、`QuestionBatchPayload`、`SummaryStatus`、`UserKbPin`、`WireValued`
- `domain.access/`
  `ChunkAccessGuard`、`FaqGuard`、`KnowledgeAccessGuard`、`KnowledgeRouteGuards`
- `domain.document/`
  `IndexRevision`、`KnowledgeDocument`、`KnowledgePublished`、`KnowledgeSource`、`ProcessingGeneration`、`ProcessingStatus`
- `domain.graph/`
  `EntityExtraction`、`ExtractPrompts`、`PipelineConfig`
- `domain.task/`
  `EnrichmentCompletionPolicy`、`EnrichmentTask`、`ProcessingTask`
- `dto/`
  `BatchDeleteRequest`、`HybridSearchRequest`、`TaskIdResponse`
- `dto.chunk/`
  `BatchReparseRequest`、`BatchTaskData`、`ChunkPageResponse`、`ChunkResponse`、`ChunkRevisionResponse`、`ChunkUpdateResponse`、`PreviewPayload`、`PreviewRequest`、`ReparseTaskData`、`RevertChunkRequest`、`UpdateChunkRequest`、`UpdateImageInfoRequest`
- `dto.doc/`
  `ClearContentsResponse`、`CreateFromUrlRequest`、`CreateManualRequest`、`FolderMoveResponse`、`KnowledgeListResponse`、`KnowledgeMoveProgress`、`KnowledgeResponse`、`KnowledgeSearchResponse`、`MoveKnowledgeRequest`、`MoveKnowledgeResponse`、`MoveToFolderRequest`、`RebuildIndexResponse`、`RenameFolderRequest`、`UpdateKnowledgeRequest`、`UpdateLastImportDisplayStatusRequest`、`UpdateManualRequest`
- `dto.faq/`
  `AddSimilarQuestionsRequest`、`DeleteGeneratedQuestionRequest`、`FaqBatchUpsertPayload`、`FaqDeleteRequest`、`FaqEntry`、`FaqEntryFieldsBatchUpdate`、`FaqEntryFieldsUpdate`、`FaqEntryPage`、`FaqEntryPayload`、`FaqEntryTagBatchRequest`、`FaqExportEntry`、`FaqFailedEntry`、`FaqImportProgress`、`FaqImportResult`、`FaqMatch`、`FaqMergeDetail`、`FaqSearchRequest`、`FaqSuccessEntry`、`FaqTaskStartResponse`、`GeneratedQuestionResponse`、`UpsertGeneratedQuestionRequest`
- `dto.kb/`
  `AsrConfigView`、`ChunkingConfigView`、`CopyKnowledgeBaseRequest`、`CopyKnowledgeBaseResponse`、`CreateKnowledgeBaseRequest`、`DuplicateKnowledgeBaseResponse`、`DuplicateKnowledgeDetails`、`ImageProcessingConfigView`、`IndexingStrategyView`、`KBCloneProgress`、`KnowledgeBaseResponse`、`ParserEngineRuleView`、`UpdateKnowledgeBaseRequest`、`VlmConfigRequest`、`VlmConfigView`
- `dto.tag/`
  `CreateTagRequest`、`DeleteTagRequest`、`KnowledgeTagBatchRequest`、`KnowledgeTagResponse`、`KnowledgeTagWithStats`、`TagPageResult`、`UpdateTagRequest`
- `index/`
  `RetrievalIndexAdapter`
- `mapper/`
  `ChunkMapper`、`ChunkRevisionMapper`、`KnowledgeBaseMapper`、`KnowledgeMapper`、`KnowledgeTagMapper`、`StorageBackendMapper`、`UserKbPinMapper`
- `repository/`
  `ChunkRepository`、`ChunkTxTemplate`、`DocumentParser`、`EnrichmentTaskRepository`、`FaqChunkRepository`、`KnowledgeBaseRepository`、`KnowledgeChunker`、`KnowledgeOutbox`、`KnowledgeRepository`、`KnowledgeSpanRepository`、`KnowledgeTagRepository`、`ProcessingTaskRepository`
- `service/`
  `EmbeddingModelAdapter`、`HousekeepingService`
- `service.enrichment/`
  `CompleteEnrichmentTask`、`ExtractChunkGraph`、`FaqBatchOps`、`FaqEntryCommandService`、`FaqEntryQueryService`、`FaqImportService`、`FaqValidateOps`、`GenerateChunkQuestions`、`KnowledgeSummaryService`、`PlanKnowledgeEnrichment`、`QuestionGenerationService`、`RequestWikiIngestion`、`SummarizeKnowledge`
- `service.index/`
  `BuildKnowledgeIndexPlan`、`IndexKnowledgeRevision`、`ReconcileKnowledgeIndex`
- `service.ingest/`
  `IngestKnowledgeUseCase`、`KnowledgeIngestPolicy`、`ResolveKnowledgeSource`
- `service.management/`
  `CloneKnowledge`、`DeleteKnowledge`、`EditKnowledgeContent`、`KnowledgeAccessHelper`、`KnowledgeBaseLookupAdapter`、`KnowledgeBatchOpsService`、`KnowledgeCloneService`、`KnowledgeFolderService`、`KnowledgeMoveService`、`KnowledgeTagService`、`ManageFaqEntries`、`ManageKnowledgeBase`、`ManageKnowledgeTags`、`MoveKnowledge`
- `service.process/`
  `CancelKnowledgeProcessing`、`ChunkEditService`、`ChunkExtractService`、`ChunkProjectionAdapter`、`ChunkQuestionService`、`ChunkReadService`、`CleanupKnowledgeGeneration`、`DocumentProcessingPipeline`、`FaqChunkCodec`、`FaqIndexRows`、`FaqIndexWriter`、`KnowledgeIndexRouter`、`ProcessKnowledgeUseCase`、`ProcessingPlanBuilder`、`PublishKnowledgeGeneration`、`ReprocessKnowledgeUseCase`
- `service.query/`
  `KnowledgeQueryService`、`KnowledgeSearchService`、`KnowledgeSpanAdapter`、`KnowledgeSpanService`、`ReadKnowledgeChunks`、`SearchKnowledgeUseCase`、`SpanTracker`
- `storage/`
  `LocalStorageService`、`TenantFileStorage`、`TenantStorageService`
- `support/`
  `GraphChunkSelector`、`KnowledgeIndexContent`、`ParserEngineRules`、`QuestionBatchPlanner`
- `task/`
  `AbstractRedisKnowledgeTaskQueue`、`ChunkExtractTaskQueue`、`EnrichmentTaskConsumer`、`FaqImportTaskStore`、`InProcessChunkExtractTaskQueue`、`InProcessFaqImportTaskStore`、`InProcessKnowledgeTaskProgressStore`、`InProcessQuestionGenerationTaskQueue`、`KnowledgeProcessedEvent`、`KnowledgeProcessingQueue`、`KnowledgeRecoveryJob`、`KnowledgeRedisWiring`、`KnowledgeTaskConsumer`、`KnowledgeTaskExecutor`、`KnowledgeTaskIdCodec`、`KnowledgeTaskProgressStore`、`QuestionGenerationTaskQueue`、`RedisChunkExtractTaskQueue`、`RedisFaqImportTaskStore`、`RedisKnowledgeTaskProgressStore`、`RedisQuestionGenerationTaskQueue`

#### 8.8.3 `agentmanagement/` 与 `agenttools/`


- `controller/`
  `AgentController`、`SkillCatalogController`、`SkillsCatalogController`
- `domain/`
  `CustomAgentEntity`
- `dto/`
  `AgentResponses`、`CustomAgentResult`
- `mapper/`
  `AgentQuestionMapper`、`CustomAgentMapper`、`JsonbRawStringTypeHandler`
- `service/`
  `AgentCatalogAdapter`、`AgentChannelCleaner`、`CustomAgentService`、`ManageAgentUseCase`、`PublishAgentUseCase`、`ResolveAgentDefinition`
- `service.builtin/`
  `BuiltinAgentRegistry`
- `service.config/`
  `AgentConfigJson`、`AgentPlaceholders`、`AgentSuggestedQuestions`、`AgentTypePresets`

- `datasource/`
  `AnalysisDuckDbJdbc`、`DataAnalysisSessionBridge`、`DataAnalysisSessionFactoryAdapter`、`DataAnalysisTool`、`DataSchemaTool`
- `knowledge/`
  `FaqSnippet`、`GetDocumentInfoTool`、`GrepChunksTool`、`KnowledgeSearchOutputFormatter`、`KnowledgeSearchTool`、`KnowledgeToolFormatter`、`ListKnowledgeChunksTool`、`QueryKnowledgeGraphTool`、`ReadKnowledgeTool`、`SearchArguments`、`SearchConversationsTool`、`SearchMemoryTool`
- `sql/`
  `DatabaseQueryTool`、`SqlGuard`、`SqlInjectionAnalyzer`、`SqlSelectDeepChecker`、`SqlTokenizer`
- `web/`
  `WebFetchTool`、`WebSearchTool`
- `wiki/`
  `AppliedChange`、`IndexEntryView`、`IndexGroupView`、`IndexOverviewView`、`IssueView`、`PageView`、`PendingWikiPage`、`RenderedWikiPages`、`RepairResult`、`ResolvedPage`、`RewriteResult`、`WikiContentRewrite`、`WikiDeletePageTool`、`WikiFlagIssueTool`、`WikiIndexOverview`、`WikiPageRendering`、`WikiPages`、`WikiReadIssueTool`、`WikiReadPageTool`、`WikiReadSourceDocTool`、`WikiRenamePageTool`、`WikiReplaceTextTool`、`WikiRewriteException`、`WikiRouteResolver`、`WikiScope`、`WikiSearchTool`、`WikiSlugs`、`WikiTexts`、`WikiUpdateIssueTool`、`WikiWritePageTool`

#### 8.8.4 `model/`（模型配置与授权）


- `controller/`
  `ModelController`、`ModelCredentialsController`、`ModelDebugController`
- `domain/`
  `Model`、`ModelParameters`、`ModelParametersTypeHandler`
- `dto/`
  `CreateModelRequest`、`CredentialFieldMetadata`、`CredentialsResponse`、`ModelCredentialsPutRequest`、`ModelDebugAsrResponse`、`ModelDebugChatResponse`、`ModelDebugResult`、`ModelDetailResponse`、`ModelParametersDTO`、`ModelParametersRequest`、`ModelProviderDTO`、`UpdateModelRequest`
- `mapper/`
  `ModelMapper`、`ModelUsageMapper`
- `service/`
  `BuiltinModelsReconciler`、`ModelProviderCatalog`、`ModelRuntimeAssembler`、`ModelRuntimeConfigs`、`ModelService`

### 8.9 其余业务域


#### `mcp/`　9 个包 / 125 个文件
> MCP 协议实现、OAuth、服务注册与工具调用面

- `(本域根包)/`
  `McpWiring`
- `controller/`
  `AgentToolApprovalController`、`McpCredentialsController`、`McpOAuthController`、`McpServiceController`、`McpServiceCrudOps`、`McpUsageInstructionsOps`
- `domain/`
  `McpAdvancedConfig`、`McpAuthConfig`、`McpAuthConfigTypeHandler`、`McpAuthType`、`McpConfigFingerprint`、`McpMetadata`、`McpMetadataSummary`、`McpOAuthClient`、`McpOAuthToken`、`McpPrincipal`、`McpResource`、`McpSecretTypeHandler`、`McpService`、`McpStdioConfig`、`McpTestResult`、`McpTool`、`McpToolApproval`、`McpToolListTypeHandler`、`McpToolPolicyPatch`、`McpTransportType`
- `dto/`
  `CredentialFieldMetadata`、`CredentialsResponse`、`McpAuthConfigResponse`、`McpCatalogSummary`、`McpServiceCreateRequest`、`McpServiceResponse`、`McpToolApprovalPolicyRequest`、`ResolveToolApprovalRequest`、`RoleVisibility`
- `mapper/`
  `McpMetadataMapper`、`McpMetadataRepository`、`McpOAuthClientMapper`、`McpOAuthRepository`、`McpOAuthTokenMapper`、`McpServiceMapper`、`McpToolApprovalMapper`、`McpToolApprovalRepository`
- `oauth/`
  `AuthServerMetadata`、`DbTokenStore`、`ManagedTokenStore`、`McpOAuthRepositoryAdapter`、`McpOAuthSupportImpl`、`McpOAuthWiring`、`OAuthAttempt`、`OAuthAuthorizationRequiredException`、`OAuthAuthorizationStatus`、`OAuthCallbackException`、`OAuthConfig`、`OAuthDiscovery`、`OAuthError`、`OAuthHandler`、`OAuthHttp`、`OAuthManager`、`OAuthNoTokenException`、`OAuthProtectedResource`、`OAuthProtocolException`、`OAuthReauthorizationRequiredException`、`OAuthRefreshTemporaryException`、`OAuthRepository`、`OAuthRuntime`、`OAuthState`、`OAuthStateNotFoundException`、`OAuthStateRedis`、`OAuthStateStore`、`OAuthToken`、`OAuthTokenOps`、`OAuthTokenStore`、`OAuthTransport`、`Pkce`、`SpringOAuthStateRedis`
- `protocol/`
  `CallToolResult`、`ContentItem`、`DefaultMcpClient`、`InitializeResult`、`JsonRpcError`、`JsonRpcRequest`、`JsonRpcResponse`、`McpAuthHeaders`、`McpAuthorizationRequiredException`、`McpCancellation`、`McpClient`、`McpClientConfig`、`McpClientFactory`、`McpClientManager`、`McpContext`、`McpErrorCode`、`McpException`、`McpHttp`、`McpLog`、`McpOAuthRequiredException`、`McpOAuthRuntime`、`McpOAuthSupport`、`McpProtocol`、`McpServiceUrls`、`McpSseReader`、`McpTransport`、`ReadResourceResult`、`ResourceContent`、`ServerCapabilities`、`ServerInfo`、`SseTransport`、`StreamableHttpTransport`
- `service/`
  `Adapter`、`McpMetadataException`、`McpMetadataService`、`McpServiceManagement`、`McpToolApprovalService`、`McpToolPolicySource`
- `tool/`
  `McpCallTool`、`McpCatalog`、`McpCatalogPagination`、`McpDiscoverTool`、`McpExposure`、`McpInputSchemaValidator`、`McpOAuthPromptSupport`、`McpRegisteredTool`、`McpToolWrapper`、`McpTooling`

#### `wiki/`　17 个包 / 147 个文件
> 知识库内联百科：页面、修订、链接图与摄取管线

- `(本域根包)/`
  `WikiRedisWiring`
- `controller/`
  `WikiActivityRecorder`、`WikiFolderOps`、`WikiKbAccessGuard`、`WikiMaintenanceOps`、`WikiPageController`、`WikiPageOps`、`WikiRequestSupport`、`WikiStatsOps`
- `domain/`
  `EmptyListAsNullSerializer`、`SourceRefNeedle`、`TaskDeadLetter`、`TaskPendingOp`、`WikiConfig`、`WikiConstants`、`WikiException`、`WikiExtractionGranularity`、`WikiSourceRefs`、`WikiStats`、`WikiStringListTypeHandler`
- `domain.folder/`
  `WikiFolder`、`WikiFolderConflictException`、`WikiFolderCreateRequest`、`WikiFolderListResponse`、`WikiFolderNode`、`WikiFolderNotEmptyException`、`WikiFolderNotFoundException`、`WikiFolderUpdateRequest`
- `domain.graph/`
  `WikiGraph`
- `domain.index/`
  `WikiCategoryPaths`、`WikiIndex`、`WikiIndexEntry`
- `domain.issue/`
  `WikiLintIssue`、`WikiLintReport`
- `domain.page/`
  `WikiPage`、`WikiPageConflictException`、`WikiPageIssue`、`WikiPageListRequest`、`WikiPageListResponse`、`WikiPageLite`、`WikiPageMoveRequest`、`WikiPageNotFoundException`、`WikiPageRevertRequest`、`WikiPageRevision`、`WikiPageRevisionListResponse`、`WikiPageUpdateRequest`、`WikiRevisionPruneRequest`
- `mapper/`
  `TaskDeadLetterMapper`、`TaskPendingOpMapper`、`WikiFolderMapper`、`WikiPageIssueMapper`、`WikiPageMapper`、`WikiPageRevisionMapper`
- `prompt/`
  `WikiPromptTemplate`、`WikiPrompts`
- `repository/`
  `TaskDeadLetterRepository`、`TaskPendingOpsRepository`、`WikiFolderRepository`、`WikiPageRepository`
- `service/`
  `DefaultWikiChunkCleaner`、`DefaultWikiImageEnricher`、`DefaultWikiKnowledgeFinalizer`、`DefaultWikiModelResolver`、`WikiChunkCleaner`、`WikiImageEnricher`、`WikiKnowledgeFinalizer`、`WikiLlmCallMetadata`、`WikiLlmRetryPolicy`、`WikiModelResolver`、`WikiPromptInstructions`
- `service.ingest/`
  `CombinedExtraction`、`WikiChunkHandleTable`、`WikiChunkMerge`、`WikiCleanupScope`、`WikiDeletedTombstoneStore`、`WikiFinalizeChange`、`WikiFinalizeRow`、`WikiIdentityClaimStore`、`WikiInflightLimiter`、`WikiPendingOp`、`WikiPendingOpsCounter`、`WikiRetractPayload`
- `service.ingest.graph/`
  `WikiGraphCalculator`
- `service.ingest.pipeline/`
  `DocIngestResult`、`WikiBatchConstants`、`WikiBatchContext`、`WikiBatchSupport`、`WikiIngestBatchHandler`、`WikiIngestCitePipeline`、`WikiIngestConstants`、`WikiIngestContentSupport`、`WikiIngestEnqueueOps`、`WikiIngestFinalizePhase`、`WikiIngestIndexOps`、`WikiIngestLlmSupport`、`WikiIngestMapPhase`、`WikiIngestOrchestrator`、`WikiIngestPageOps`、`WikiIngestPayload`、`WikiIngestPreviews`、`WikiIngestReducePhase`、`WikiIngestRunSupport`、`WikiIngestSettleOps`、`WikiIngestTaxonomy`
- `service.ingest.task/`
  `InProcessWikiDeletedTombstoneStore`、`InProcessWikiFinalizeLock`、`InProcessWikiIdentityClaimStore`、`InProcessWikiInflightLimiter`、`InProcessWikiIngestTaskQueue`、`RedisWikiDeletedTombstoneStore`、`RedisWikiFinalizeLock`、`RedisWikiIdentityClaimStore`、`RedisWikiInflightLimiter`、`RedisWikiIngestTaskQueue`、`SingleFlight`、`WikiDedupSupport`、`WikiFinalizeLock`、`WikiIdentityDedup`、`WikiIngestDedupService`、`WikiIngestExtractDedup`、`WikiIngestQueueOps`、`WikiIngestTask`、`WikiIngestTaskHandler`、`WikiIngestTaskQueue`、`WikiIngestTaskRunner`、`WikiPendingOpReplayer`
- `service.page/`
  `InProcessWikiSlugLock`、`NewSlugFromCitation`、`RedisWikiSlugLock`、`SlugFuzzy`、`WikiActiveFlag`、`WikiCrossLinker`、`WikiDeadLinks`、`WikiEditContext`、`WikiLinkify`、`WikiLintService`、`WikiPageFolderSupport`、`WikiPageLinkOps`、`WikiPageLinkRepair`、`WikiPageRevisionOps`、`WikiPageService`、`WikiPageServiceImpl`、`WikiPageViewsSupport`、`WikiRevertToCurrentVersionException`、`WikiSlugHandles`、`WikiSlugLock`、`WikiTextUtils`

#### `datasource/`　16 个包 / 124 个文件
> 外部数据源连接器、游标推进与同步编排

- `(本域根包)/`
  `DataSourceSyncEnqueueException`
- `connector.feishu.core/`
  `DocxBlocks`、`DocxFetcher`、`DocxMarkdown`、`FeishuApiTypes`、`FeishuClient`、`FeishuConfig`、`FeishuCursorCodec`、`FeishuDriveOps`、`FeishuErrors`、`FeishuRegion`、`FeishuSupport`、`FeishuTransport`、`FeishuWikiTreeOps`、`FetchTally`、`SyncEngine`
- `connector.feishu.drive/`
  `DriveConnector`
- `connector.feishu.wiki/`
  `WikiConnector`
- `connector.gitlab/`
  `GitLabClient`、`GitLabConfig`、`GitLabConnector`、`GitLabPath`、`GitLabUrl`
- `connector.ima/`
  `ImaApiTypes`、`ImaClient`、`ImaConfig`、`ImaConnector`、`ImaCursor`、`ImaFormats`、`ImaRetryPolicy`
- `connector.notion/`
  `NotionAnnotations`、`NotionAttachment`、`NotionBlock`、`NotionClient`、`NotionConfig`、`NotionConnector`、`NotionConstants`、`NotionCursor`、`NotionDatabaseInfo`、`NotionDateMention`、`NotionEquation`、`NotionFetchOps`、`NotionFile`、`NotionJson`、`NotionMarkdown`、`NotionMention`、`NotionPage`、`NotionPaginatedResponse`、`NotionParent`、`NotionProperties`、`NotionRichText`、`NotionTextContent`、`NotionValues`
- `connector.rss/`
  `AllFeedsFailedException`、`ArticleExtractionException`、`ArticleExtractor`、`FeedParseException`、`FeedParser`、`JdkXmlFeedParser`、`PartialFetchException`、`RssClient`、`RssConfig`、`RssConnector`、`RssCursor`、`RssFetchState`、`RssUtil`、`UnavailableArticleExtractor`
- `connector.yuque/`
  `YuqueApiTypes`、`YuqueClient`、`YuqueConfig`、`YuqueConnector`、`YuqueCursor`、`YuqueFormats`、`YuqueRetryPolicy`
- `controller/`
  `DataSourceController`、`DataSourceCredentialsController`
- `domain/`
  `DataSource`、`DataSourceConfig`、`DataSourceConstants`、`DataSourceException`、`DataSourceMapSerializer`、`DataSourceSyncPayload`、`FetchedItem`、`Resource`、`SubtreeChildIds`、`SyncCursor`、`SyncItemError`、`SyncLog`、`SyncResult`、`TaskInitiator`
- `domain.connector/`
  `Connector`、`ConnectorCatalog`、`ConnectorException`、`ConnectorHttp`、`ConnectorHttpWiring`、`ConnectorMetadata`、`ConnectorRegistry`、`StreamingConnector`
- `dto/`
  `CredentialFieldMetadata`、`CredentialsResponse`、`DataSourceConfigDto`、`DataSourceResponse`
- `mapper/`
  `DataSourceMapper`、`DataSourceRepository`、`DataSourceTxTemplate`、`SyncLogMapper`、`SyncLogRepository`
- `service/`
  `AutoTagProvider`、`DataSourceItemOps`、`DataSourceService`、`DataSourceSupport`、`DataSourceSyncExecutor`、`DataSourceSyncResultOps`、`DataSourceWiring`、`KnowledgeBridge`、`KnowledgeIngestClient`、`KnowledgeTagAutoTagProvider`
- `task/`
  `DataSourceSyncHandler`、`DataSourceSyncTaskQueue`、`DataSourceTaskQueueWiring`、`InProcessDataSourceSyncTaskQueue`、`RedisDataSourceSyncTaskQueue`、`Scheduler`、`StreamHandler`

#### `skill/`　2 个包 / 8 个文件
> 技能定义、执行与沙箱

- `mapper/`
  `SkillMapper`
- `service/`
  `DbSkillSource`、`Manager`、`Skill`、`SkillCatalogService`、`SkillEntity`、`SkillFrontmatter`、`SkillSource`

#### `storage/`　8 个包 / 41 个文件
> 存储后端配置与文件读写代理

- `config/`
  `LocalStorageEnvProperties`、`ResourceUrlModeProperties`、`StorageEnvLookup`、`StorageProviderEnv`
- `controller/`
  `FileProxyController`、`StorageBackendController`
- `domain/`
  `StorageBackend`、`StoredResource`
- `dto/`
  `StorageBackendResponse`、`StorageConfig`
- `fileserve/`
  `BackendScopedFileService`、`ChatLocalImageResolverWiring`、`FileAccess`、`FileAccessException`、`FileAccessResolver`、`FileContentService`、`FileProxyService`、`FileTransport`、`FileserveStorageBackendResolver`、`LocalFileContentService`、`ProviderFileContentService`、`StorageFileResolver`、`StoragePaths`、`StorageUrlWiringConfig`、`WritableFileContentService`
- `mapper/`
  `ResourceRepository`、`StorageBackendRepository`
- `service/`
  `DefaultStorageBackendProvisioner`、`ResourceCatalogService`、`StorageBackendService`、`StorageConfigCodec`
- `support/`
  `FileServiceResolver`、`FileUrlPort`、`Mode`、`PublicModeForbiddenException`、`Resolver`、`ResourceModeException`、`Rewriter`、`StorageBackendResolver`、`StorageUrlContext`、`StreamRewriter`

#### `vectorstore/`　5 个包 / 11 个文件
> 向量库配置、类型目录与连通性校验

- `controller/`
  `VectorStoreController`
- `domain/`
  `ConnectionConfigTypeHandler`、`EnvVectorStores`、`IndexConfigTypeHandler`、`VectorStore`、`VectorStoreEngines`
- `dto/`
  `VectorStoreResponse`、`VectorStoreTypes`
- `mapper/`
  `VectorStoreRepository`
- `service/`
  `VectorStoreConfigService`、`VectorStoreLookupAdapter`

#### `websearch/`　7 个包 / 35 个文件
> 联网搜索 provider 目录与搜索执行

- `controller/`
  `WebSearchController`、`WebSearchProviderController`、`WebSearchProviderCredentialsController`
- `domain/`
  `WebSearchParamsTypeHandler`、`WebSearchProviderEntity`、`WebSearchProviderParams`
- `dto/`
  `WebSearchProviderResponse`、`WebSearchProviderTypes`
- `fetch/`
  `AgentMarkdown`、`BrowserRenderer`、`FetchException`、`Fetcher`
- `mapper/`
  `WebSearchProviderRepository`
- `provider/`
  `BaiduProvider`、`BingProvider`、`BochaProvider`、`BraveProvider`、`DuckDuckGoProvider`、`EmptyTestResults`、`ExaProvider`、`GoogleProvider`、`KeenableProvider`、`MetasoProvider`、`OllamaProvider`、`SearchDecode`、`SearchHttp`、`SearxngProvider`、`SearxngValidation`、`TavilyProvider`、`WebSearchProvider`、`WebSearchProviderRegistry`、`ZhipuProvider`
- `service/`
  `WebSearchProviderService`、`WebSearchService`、`WebSearchTempKbStateService`

#### `memory/`　10 个包 / 72 个文件
> 长期记忆的抽取、去重合并、召回与生命周期

- `(本域根包)/`
  `MemoryContext`
- `controller/`
  `MemoryController`
- `domain/`
  `MemoryConflictException`、`MemoryConsolidationResult`、`MemoryDocAffinity`、`MemoryDocView`、`MemoryExtractionBatch`、`MemoryExtractionFailure`、`MemoryExtractionLeaseLostException`、`MemoryExtractionSession`、`MemoryExtractionState`、`MemoryExtractionStateTypeHandler`、`MemoryMessageCursor`、`MemoryPage`、`MemoryRender`、`MemoryStringListTypeHandler`、`MemoryText`、`MemoryTopicStat`、`MemoryTopicView`
- `domain.item/`
  `MemoryItem`、`MemoryItemEmbedding`、`MemoryTombstone`
- `domain.scope/`
  `MemoryScope`、`MemorySettings`、`MemorySubject`、`MemorySubjectMissingException`
- `domain.vector/`
  `MemoryVectorHit`、`MemoryVectorQuery`、`MemoryVectors`
- `dto/`
  `CreateMemoryItemRequest`、`MemoryExportResponse`、`MemoryListResponse`、`UpdateMemoryItemRequest`、`UpdateMemorySettingsRequest`
- `mapper/`
  `MemoryDocAffinityMapper`、`MemoryExtractionSessionMapper`、`MemoryItemEmbeddingMapper`、`MemoryItemMapper`、`MemorySubjectMapper`、`MemoryTombstoneMapper`、`MemoryTopicStatMapper`、`VectorHitRow`
- `repository/`
  `MemoryIndexStore`、`MemoryItemStore`、`MemoryRepository`、`MemoryTxTemplate`、`MemoryVectorStore`
- `service/`
  `DefaultMemoryModelResolver`、`InProcessMemoryExtractTaskQueue`、`MemoryCatalogOps`、`MemoryConsolidationService`、`MemoryExtractPayload`、`MemoryExtractTaskQueue`、`MemoryExtractionLlm`、`MemoryExtractionService`、`MemoryInsightOps`、`MemoryLexical`、`MemoryModelResolver`、`MemoryRecallOps`、`MemoryRecallSelector`、`MemoryRunBudget`、`MemoryScopeExceptions`、`MemoryScopes`、`MemorySearchResult`、`MemoryService`、`MemoryTaskQueueWiring`、`MemoryTopicResolver`、`MemoryTrace`、`MemoryTranscriptOps`、`MemoryVectorService`、`MemoryViews`、`RedisMemoryExtractTaskQueue`

#### `auth/`　13 个包 / 82 个文件
> 登录、令牌签发与校验、用户目录与 API Key

- `apikey.controller/`
  `TenantAPIKeyController`
- `apikey.controller.filter/`
  `APIKeyAuthChannel`、`APIKeyGateInterceptor`、`APIKeyGateResponses`、`APIKeyRouteAuthorizer`、`APIKeyRoutePolicies`、`APIKeyRoutePolicy`、`APIKeyScopeCleanupFilter`、`AllowFileServeAPIKeyInterceptor`、`DenyAPIKeyPrincipalInterceptor`
- `apikey.domain/`
  `APIKeyRawJsonbTypeHandler`、`APIKeyStringListTypeHandler`、`TenantAPIKey`、`TenantAPIKeyProjections`、`TenantAPIKeyRequest`
- `apikey.dto/`
  `TenantAPIKeyCreateResponse`、`TenantAPIKeyResponse`
- `apikey.mapper/`
  `TenantAPIKeyMapper`、`TenantAPIKeyNotFoundException`、`TenantAPIKeyRepository`
- `apikey.service/`
  `ApiKeyAdminAdapter`、`TenantAPIKeyBootstrap`、`TenantAPIKeyService`、`TenantAPIKeyValidator`
- `config/`
  `JwtProperties`
- `controller/`
  `AuthBindingSupport`、`AuthController`、`AuthOidcOps`、`AuthSessionOps`、`TenantAPIPrincipalController`、`TenantBindSupport`、`TenantCatalogController`、`TenantConfigOps`、`TenantCreateOps`、`TenantCrudOps`、`TenantInvitationController`、`TenantMemberController`
- `controller.filter/`
  `AuthFilter`、`WsAuthSupport`
- `domain/`
  `AuthToken`、`TenantInvitation`、`TenantMember`、`User`、`UserPreferences`
- `dto/`
  `APIPrincipalDtos`、`AuthConfigResponse`、`AuthLoginResponse`、`ChangePasswordRequest`、`CurrentUserResponse`、`InvitationLookupRequest`、`InvitationLookupResponse`、`LoginRequest`、`Membership`、`OidcAuthUrlResponse`、`OidcConfigResponse`、`RegisterByInviteRequest`、`RegisterRequest`、`TenantInvitationResponse`、`TenantMemberResponse`、`TenantResponse`、`TokenPairResponse`、`UpdatePreferencesRequest`、`UserCapabilities`、`UserInfo`
- `mapper/`
  `AuthTokenMapper`、`TenantInvitationMapper`、`TenantMemberMapper`、`UserMapper`
- `service/`
  `JwtService`、`LoginResult`、`OidcConfig`、`OidcService`、`OidcStateCodec`、`PasswordPolicy`、`TenantInvitationService`、`TenantMemberService`、`TenantRbacException`、`TenantService`、`TokenValidationException`、`UserService`、`UserSessionOps`、`ValidatedToken`

#### `tenant/`　3 个包 / 10 个文件
> 租户、成员与 RBAC 业务

- `(本域根包)/`
  `APIPrincipalConfig`、`APIPrincipalConfigTypeHandler`、`ChatHistoryConfig`、`ParserEngineConfig`、`RetrievalConfig`、`StorageEngineConfig`
- `mapper/`
  `TenantMapper`
- `repository/`
  `Tenant`、`TenantConfigRedaction`、`TenantLookup`

#### `im/`　14 个包 / 79 个文件
> 九渠道即时通讯接入与渠道无关编排

- `channel.dingtalk/`
  `DingtalkAdapter`、`DingtalkAdapterFactory`、`DingtalkStreamClient`
- `channel.feishu/`
  `FeishuAdapter`、`FeishuAdapterFactory`、`FeishuCallbackOps`、`FeishuCardStreamOps`、`FeishuLongConnClient`、`FeishuMediaOps`、`FeishuRegion`、`FeishuSendOps`、`LarkEventConverter`、`LarkFrame`
- `channel.mattermost/`
  `MattermostAdapter`、`MattermostAdapterFactory`、`MattermostClient`
- `channel.qqbot/`
  `QqBotAdapter`、`QqBotAdapterFactory`、`QqBotClient`、`QqBotGatewayClient`
- `channel.slack/`
  `SlackAdapter`、`SlackAdapterFactory`、`SlackSocketModeClient`
- `channel.telegram/`
  `TelegramAdapter`、`TelegramAdapterFactory`、`TelegramLongPollingClient`
- `channel.wechat/`
  `WechatAdapter`、`WechatAdapterFactory`、`WechatCrypto`、`WechatLongPollClient`、`WechatQRCodeService`
- `channel.wecom/`
  `WecomAdapterFactory`、`WecomLongConnClient`、`WecomSupport`、`WecomWSAdapter`、`WecomWebhookAdapter`
- `channel.yunzhijia/`
  `YunzhijiaAdapter`、`YunzhijiaAdapterFactory`、`YunzhijiaLongConnClient`、`YunzhijiaSign`、`YunzhijiaTypes`、`YunzhijiaUrl`
- `controller/`
  `ImCallbackController`、`ImChannelController`
- `domain/`
  `ChannelSessionEntity`、`ImChannelEntity`
- `mapper/`
  `ChannelSessionMapper`、`ImChannelMapper`
- `runtime/`
  `AdapterInterfaces`、`CallbackExchange`、`Commands`、`FeishuWecomCrypt`、`ImAdapterVerify`、`ImCommandSet`、`ImCredentials`、`ImFormat`、`ImRedisKeys`、`ImRedisStore`、`ImSupervisor`、`ImTypes`、`IncomingMessage`、`QaQueue`、`ReplyMessage`、`SlackAdapterCore`、`StreamSection`、`ThinkDisplay`、`ToolDisplay`
- `service/`
  `ImAttachmentPreparer`、`ImChannelRuntimeOps`、`ImChannelService`、`ImInboundGuardOps`、`ImKnowledgeBridgeOps`、`ImOutboundFormatter`、`ImQaRequests`、`ImQaRunner`、`ImSessionResolver`、`ImStopOps`、`ImStreamPipeline`、`ImTurnOrchestrator`

#### `approval/`　4 个包 / 24 个文件
> 工具人工审批门与跨实例 pub/sub

- `(本域根包)/`
  `ApprovalEvent`、`ApprovalEventBus`、`ApprovalRequiredNotice`、`ApprovalResolvedNotice`、`McpApproval`、`McpOauthRequiredData`、`McpOauthResolvedData`、`ResolveMessage`
- `domain/`
  `ApprovalException`、`Cancellation`、`Decision`、`GateOptions`、`OAuthPendingRequest`、`PendingRequest`、`ResolveAck`
- `redis/`
  `RedisPubSub`、`SpringRedisPubSub`
- `service/`
  `ApprovalBridge`、`ApprovalJson`、`ApprovalToolPolicy`、`BulkEnabledChecker`、`Checker`、`EnabledChecker`、`Gate`

#### `audit/`　5 个包 / 13 个文件
> 审计写入、查询与保留期清理

- `controller/`
  `AuditLogController`
- `domain/`
  `AuditAction`、`AuditLog`、`AuditLogQuery`、`AuditOutcome`
- `dto/`
  `AuditLogListResponse`
- `mapper/`
  `AuditLogMapper`、`AuditLogRepository`
- `service/`
  `AuditLogRetentionRunner`、`AuditLogService`、`AuditRetentionLifecycle`、`RbacDeniedAuditorRegistrar`、`WikiActivityAuditRecorder`

#### `evaluation/`　5 个包 / 20 个文件
> 评估任务与指标计算

- `controller/`
  `EvaluationController`
- `domain/`
  `QaPair`
- `dto/`
  `EvaluationDtos`、`EvaluationRequest`
- `metric/`
  `BleuMetric`、`MapMetric`、`MetricCommon`、`MetricInput`、`MetricSegmenter`、`Metrics`、`MrrMetric`、`NdcgMetric`、`PrecisionMetric`、`RecallMetric`、`RougeMetric`、`RougeScore`
- `service/`
  `DatasetService`、`EvaluationPromptDefaults`、`EvaluationService`、`MetricHook`

#### `embedchannel/`　6 个包 / 15 个文件
> 对外嵌入问答渠道与访客鉴权

- `(本域根包)/`
  `EmbedError`、`EmbedRateLimiter`、`EmbedTokens`、`EmbedWiring`、`PlainTextErrorReportValve`
- `controller/`
  `EmbedChannelController`、`EmbedChannelDelegateOps`、`EmbedChannelMgmtOps`、`EmbedChannelPublicOps`
- `controller.filter/`
  `EmbedAuthFilter`
- `domain/`
  `EmbedChannelEntity`
- `mapper/`
  `EmbedChannelMapper`
- `service/`
  `EmbedChannelService`、`EmbedTokenStore`、`RedisEmbedTokenStore`

#### `favorite/`　4 个包 / 4 个文件
> 用户资源收藏

- `controller/`
  `UserFavoriteController`
- `domain/`
  `UserResourceFavorite`
- `mapper/`
  `UserResourceFavoriteMapper`
- `service/`
  `UserResourceFavoriteService`

#### `settings/`　1 个包 / 3 个文件
> 跨域共享设置注册表与只读端口

- `(本域根包)/`
  `ConversationProperties`、`SystemSettingGateway`、`SystemSettingRegistry`

#### `system/`　5 个包 / 10 个文件
> 系统设置读写与管理员面

- `controller/`
  `SystemAdminController`、`SystemController`
- `domain/`
  `SystemSetting`
- `dto/`
  `SystemDtos`
- `mapper/`
  `SystemSettingMapper`
- `service/`
  `DeploymentCapabilitiesHolder`、`ParserEngineRegistry`、`SystemAdminUserService`、`SystemInfoService`、`SystemSettingService`

#### `initialization/`　3 个包 / 10 个文件
> 系统初始化与模型连通性测试

- `controller/`
  `InitializationController`
- `dto/`
  `InitResponses`
- `service/`
  `AsrTestAudio`、`InitializationConfigService`、`InitializationRequests`、`InitializationWiring`、`ModelConnectivityTestService`、`OllamaDownloadTaskStore`、`OllamaManageService`、`TextExtractionTestService`


## 9. 状态所有权与政策归属

### 9.1 状态所有权（唯一写入者）

| 状态 | 唯一所有者 | 其他层允许做什么 |
|---|---|---|
| 单轮业务终态 | `session` / `ConversationTurn` + `CompleteTurnUseCase` | Agent 与 RAG 只返回执行结果；SSE、watcher、工具、trace 不得直接置完成 |
| Agent 运行态 | `agent-engine` / `AgentRunState`（runner 单 writer） | 工具并行无共享写入；seen 与结果按原调用序提交 |
| 知识可见版本 | `knowledge` / `PublishKnowledgeGeneration`（active generation） | 索引确认 generation + revision；查询只读已发布代；清理只针对指定代 |
| 主/子任务终态 | `knowledge` / `ProcessingTask`、`EnrichmentTask` | 平台负责至少一次投递；消费者以唯一 taskKey 幂等提交 |
| 模型技术流 | `model-runtime` / `ManagedModelStream` | 任何退出路径关闭连接、归还 permit；模型流完成不等于业务成功 |
| 会话临时文档 | `session` / `SessionDocument` | 解析技术能力来自 platform；TTL、失败、转知识库由会话用例控制 |
| 外部副作用 | `platform` / `OutboxDispatcher` + 各域 outbox | CAS、业务状态与 outbox 同库同连接同事务；外部写入靠幂等与补偿 |

### 9.2 政策归属（同一规则只允许一份实现）

| 政策 | 所有者 | 复用方 |
|---|---|---|
| 知识来源验证 / 幂等 / 摄取 | `knowledge/service/ingest` | HTTP、数据源、IM、会话索引、评估统一调 `KnowledgeIngest` |
| 召回融合 / 去重 / 重排 / MMR | `retrieval-engine/policy` | 直接检索、RAG、Agent 通过 `SearchPolicy` 选择 |
| 索引分批 / embedding 对齐 | `retrieval-engine/execution` + `model-runtime/policy` | 上传、编辑、FAQ、重解析、会话索引共用 `IndexService` |
| 模型使用授权 | `model/service/ResolveModelAccess` + runtime 每调用复校验 | 不把缓存 client 当永久授权 |
| 超时 / 背压 / 连接 / permit | `model-runtime/stream` + `policy` | chat、embedding、rerank、ASR、VLM 统一边界 |
| 资源授权 | 各资源所属业务域 | platform 只提供可信身份与技术原语；tenantId、Agent 配置、工具白名单都不等于权限 |
| 身份与 trace 传播 | `platform/execution/ScopedTaskExecutor` | 业务显式传 `AccessContext`；HTTP ThreadLocal 仅作兼容桥 |


## 10. 命名与文件边界约定

| 后缀 / 类型 | 含义与约束 |
|---|---|
| `UseCase` | 完整业务入口，含权限、事务、终态规则与可读顺序；**不是单方法转发壳** |
| `Policy` | 纯规则，不访问网络/DB，不隐式读 ThreadLocal |
| `Controller` | 只放端点、帧写出与请求映射 |
| `Service` | 用例与政策；不得依赖具体技术实现 |
| `Mapper` | MyBatis Mapper，不含业务规则 |
| `Repository` | 端口与实现同包，靠命名区分：`XxxRepository` 端口、`MyBatisXxxRepository` 实现 |
| `Domain` | 聚合与不变量，不引用 Spring / Mapper / 其他域 |
| `Config` | 只装配 |
| `State` | 单一 writer；不可变输入用 record，集合防御复制 |
| 禁用 | `Helper` / `Utils` / `Support` / `Ops` / `Manager` 作为通用兜底分类 |

**三个判断问题：** 一个用例能否顺着读完？一个状态字段的写入者是否唯一？同一政策是否存在第二份实现？
任一为否，说明分层尚未完成——**而不是文件还不够多**。

### 10.1 本次已消除的同名歧义

| 原名 | 处理 |
|---|---|
| `ToolResult`（contract.model 与 contract.tool 各一份） | 统一到 `contract.tool` |
| `ToolCall`（agent.domain 与 llm.domain 各一份） | agent 侧改名 `AgentToolCall` |
| `ToolPolicy`（agent.context 与 approval 各一份） | approval 侧改名 `ApprovalToolPolicy` |
| `TokenUsage`（contract.model 与 langfuse 各一份） | langfuse 侧改名 `LangfuseTokenUsage` |
| `ModelResponse`（contract.model 与 model.dto 各一份） | model.dto 侧改名 `ModelDetailResponse` |
| `ProviderRegistry`（model.service 与 model-runtime 各一份） | model.service 侧改名 `ModelProviderCatalog` |
| `FileService`（storage.provider 与 storage.support 各一份） | support 侧改名 `FileUrlPort`（单方法 `GetFileURL` 端口） |
| `WebSearchProvider`（domain 实体与 provider 接口各一份） | 实体改名 `WebSearchProviderEntity` |
| `Event` / `EventBus`（approval 域自有） | 改名 `ApprovalEvent` / `ApprovalEventBus` |
| `ToolApprovalRequiredData` / `ToolApprovalResolvedData` | approval 侧改名 `ApprovalRequiredNotice` / `ApprovalResolvedNotice` |

### 10.2 保留的跨域同名类（已核实为不同实现，实施时建议加域前缀）

| 类名 | 分布 | 建议 |
|---|---|---|
| `CredentialsResponse`、`CredentialFieldMetadata` | `datasource.dto`、`mcp.dto`、`model.dto` 各一份，wire 形状不同 | 加域前缀：`DatasourceCredentialsResponse` 等 |
| `FeishuRegion` | `datasource.connector.feishu.core`（抓取）与 `im.channel.feishu`（渠道） | 可保留，属不同域 |
| `ZhipuProvider` | `model-runtime`（模型厂商）与 `websearch.provider`（搜索源） | 可保留，属不同能力 |


## 11. 分阶段整改路线

> 每个阶段独立可验收；**先补失败测试，再改实现**。不要在搬迁包的同时改行为。

### 阶段 0 · 边界固化（不改目录）

- 在现有 `common` / `engine` / `domains` / `boot` 内建立目标 package 约定与 ArchUnit 规则。
- 优先落地三条硬规则（见 §12.1），此时无需任何改名。
- 修复守卫自身缺陷：`check-package-cycles.py:217-224` 的缩进错误；补守卫自测。
- **验收**：故意注入一处越层依赖能让检查失败。

### 阶段 1 · 最小正确性修复（不动包）

- K1 租户显式路由、K2 URL 来源统一、K3 RPC 异常传播、A6 同 ID 最高分。
- **验收**：四条各有一个先失败后转绿的测试；真实 worker 线程处理非零租户绑定 store 成功。

### 阶段 2 · 会话终态、取消与提交幂等

- A1 / A2 / A3 / A5：统一 `TurnOutcome`；可关闭的模型流；全轮 CAS + outbox；临时文档状态归位。
- **验收**：成功 / 失败 / 取消 / 超时各有唯一持久终态；双 STOP + runner finally 只提交一次；DB 更新失败不直接启动索引。

### 阶段 3 · 知识任务可靠性

- K4 / K5 / K6：持久主任务、generation 与 lease、子任务唯一终态、统一恢复口径。
- **验收**：进程重启可重领或明确失败收口；重复投递只释放一次；旧 generation 不覆盖新数据。

### 阶段 4 · 索引一致性与配置生效

- K7 / K8 / K10：统一索引执行、revision 与 outbox、embedding 响应校验；配置能力矩阵与显式拒绝。
- **验收**：本地事务不持锁调用远程模型；乱序/缺项/错维度响应被拒绝；不支持配置明确报错而非静默成功。

### 阶段 5 · 检索与结构收敛

- K9 / A6 / A4：跨 store 融合政策、无结果与失败分离、并发工具状态提交顺序；`controller` 剥离编排类、`mapper` 与 `repository` 分离。
- **验收**：交换 KB scope 顺序结果稳定；`controller` 包内无 `Executor` / `Parser` / `Orchestrator` 类。

### 阶段 6 · 模块提取与门禁收紧

- 先 `contracts` / `platform`，再三个 runtime，最后 `domains` 内部重排。
- **验收**：每步同步更新组件/属性/Mapper 扫描；不刷新 baseline 掩盖违例。

### 总验收（三问）

1. **用例可读**：新成员能顺着 `SubmitTurnUseCase`、`AgentRunner`、`ProcessKnowledgeUseCase`、`SearchExecution` 读完主线。
2. **状态可控**：任一状态字段写入者唯一；重放、取消、迟到写入有明确保护。
3. **政策可复用**：同一规则全仓只有一份实现，且有对应契约测试。


## 12. 质量门禁

### 12.1 三条硬规则（改名不产生价值，规则才产生价值）

1. **边界层不得被用例层引用。** `controller` / `mapper` / `task` / `client` / `channel` 只能被装配层与外部调用；`controller` 不得被 `service` 反向 import。
2. **`service` 不得依赖具体技术。** 不 import MyBatis、Redis、厂商 SDK、Langfuse、Servlet；需要外部能力时经 `repository` / `client` 端口。
3. **业务状态只有一个写入者；同一政策只有一份实现，并有契约测试。**

典型反例（均在本仓库中存在）：SSE 写出行与停止 watcher 各自置完成；索引分批两处实现；`KnowledgeAccessHelper` 回调门面静态工具。

### 12.2 需要新增的 ArchUnit 规则

| 规则 | 说明 |
|---|---|
| 模块 DAG | 禁止 `contracts` → 其他模块、`platform` → 业务域、runtime 之间互相引用实现 |
| 跨域边界 | 业务域之间只能经 `contracts`；`client` 不得直接引用其他域 `mapper` |
| 分层方向 | `domain` 不依赖 Spring/Mapper；`repository` 不依赖 `service` 实现 |
| 状态所有权 | 禁止 phase 回引 runner；禁止公共可变运行态字段 |
| 装配契约 | 每个 contract 的提供方唯一实现；无 `@Lazy` 掩盖环 |

### 12.3 需要补的测试场景

1. **终态矩阵**：NORMAL / AGENT × 成功 / 模型错误 / 无首 token / 中途断流 / STOP / 请求超时。
2. **幂等**：watcher 与 HTTP 同时 STOP；消息更新失败后重试；重复索引事件。
3. **并发**：两个 `knowledge_search` 命中相同 chunks，乱序完成但稳定回填；acquire / join 中断后无跨越写屏障。
4. **租户**：真实虚拟线程 + 非零租户绑定 store；共享 Agent 的调用身份与资源属主不同。
5. **摄取**：真实数据源 adapter URL → 存储 → 解析 → chunks → index；unary-only RPC、meta 后错误、超时。
6. **任务**：重复 payload、retryable / permanent failure、superseded；旧 generation 在新 generation 发布后完成。
7. **外部索引**：删除后写入失败、DB rollback、outbox 重放、批响应乱序 / 缺项 / 错维度。
8. **检索**：同 ID 低分先到、KB/store 顺序交换、分数相同的确定 tie-break、部分分支失败、真正空结果。

### 12.4 现有测试能证明什么、不能证明什么

| 现有证据 | 价值 | 不能推导 |
|---|---|---|
| `KnowledgeContractTest:253-280` | 上传 / 重复 409 / 返回结构 | `:265` 明确异步状态竞态，不能证明解析与索引完成 |
| `KnowledgeSearchRecordingTest:237-259` | 固定输出与顺序 alreadySeen | 语料刻意避开跨 query 同 ID，不能证明最高分去重与真实并发 |
| `ToolRegistryRecordingTest:157-166、210-232` | 工具 cast/validate 与权限顺序 | 不能替代流生命周期、线程取消测试 |
| `ArchitectureRulesTest:519-564` | 新增/过期违例双棘轮 | 未覆盖 `repository` → `service`、`controller` 内业务回引 |
| `boot` 测试配置 `:6-12` | 默认 H2 隔离外部库 | 不能作为 PG vector / JSONB / 真实 store 语义覆盖证明 |
| `engine/build.gradle.kts:97-119` | 真实服务 IT 有显式入口 | 默认 `check` 不执行 IT；本轮未运行，不能声称通过或给出覆盖率 |

### 12.5 覆盖率与复杂度

- 先执行并固化 `-Pcoverage` 的真实基线，关注终态与任务分支；随后为新改代码设**增量**门槛。
- **不要用 444 个测试文件或历史构建产物推导当前覆盖率。**
- 行数棘轮只防膨胀，不能证明内聚。建议补方法复杂度、构造依赖数、公开可变字段数、包依赖 SCC 四类指标；例如方法复杂度 > 15、构造依赖 > 8 触发人工复核——**这些是候选审查线，不是立即生效的全库硬门**。


## 13. 明确不做的事

1. **不整体重写，不拆微服务。** 现有模块分层、facts/ports、工具管线、架构守卫都值得保留。
2. **不按行数机械拆类。** `KnowledgeProcessWorker`（847 行）等大文件先看跨多少职责、多少可变写入者，而不是先看行数。
3. **不为"DDD 完整"编造空层。** 简单 CRUD 域不造聚合根、Repository 接口与领域事件。
4. **不合并语义不同的重复。** 展示 / 持久化 / 模型上下文三种工具结果投影；RAG 与 Agent 的上下文组装；MCP 与 skill 各自的工具目录——这些都应保留。
5. **不把 `contracts` 改造成新的 `common`。** 只公开跨模块确有消费者的接口；公开 facts 不携带原始密钥。
6. **不用 `@Lazy` 掩盖循环依赖。** 环应由依赖方向修正，而非注入技巧。
7. **不刷新 baseline 掩盖违例。** 守卫应当红，就让它红。

## 14. 主要风险

| 风险 | 缓解 |
|---|---|
| `controller` 包再次变成"杂物间" | 硬性规定只放绑定类；ArchUnit 检查类名与依赖；代码评审清单化 |
| `repository` 同包放端口与实现后，`service` 直接引用 MyBatis 实现 | 包级规则：`service` 只允许引用 `XxxRepository` 端口类型；必要时把实现挪到 `repository/mybatis` |
| 入口移包、Mapper 路径变化导致启动期缺 Bean | 迁移时同步组件扫描、属性扫描与 `@MapperScan`；每步跑启动测试 |
| 旧 golden 快照把缺陷当契约 | 明确 golden 只约束边缘协议；行为缺陷经评审后同步客户端与测试 |
| 迁移期长期双写、两套状态 | 每阶段设定退出条件；不保留"临时并存"超过一个阶段 |
| 大规模改名淹没行为变更 | 搬迁与行为修改拆成不同 PR；搬迁 PR 不夹带逻辑变更 |

---

## 附：本次审查的交付物

| 文件 | 说明 |
|---|---|
| `java-remediation-plan-2026-10-13.md` | 本文件：最终整改方案（结论 + 目标结构 + 路线） |
| `java-target-package-tree-variant-b-2026-10-13.{txt,html}` | 十六节完整结构与说明文档（含全部包与文件枚举） |
| `java-full-target-structure-2026-10-13.{txt,html}` | 按职责命名的完整结构与 254 条包映射 |
| `java-naming-variant-comparison-2026-10-13.{txt,html}` | 两种命名方式的迁移成本量化对比 |
| `java-architecture-review-2026-10-13.html` | 架构与可读性审查报告（含源码引用） |
| `java-target-package-tree-2026-10-13.txt`、`java-target-package-blueprint-2026-10-13.html` | 早期设计草稿（采用 `application`/`adapter` 命名）。**已由本文件取代**，仅作过程留档，不作为实施依据 |

> 以上文件均为设计产物；**本轮未修改任何业务源码、构建配置或现有包结构**。
