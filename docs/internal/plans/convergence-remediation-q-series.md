# 配置单源与状态收敛专项（Q 系列）

- 日期：2026-10-24
- 输入：`docs/internal/plans/architecture-assessment-2026-10-24.md`（下称"报告"）
- 基线：`v0.8.x` @ `3fc63bcc2`（工作区有 4 份未提交文档删除，见 §2.1）
- 性质：立项与批次计划；本轮不改业务代码

## 0. 一句话结论

报告的方向正确：**不拆模块、先收配置与状态、前端巨石单独排期**。但报告有 **4 处把已完成或已否决的事重新列了一遍**，**5 处落法需要改写**，数字口径有 **6 处需更正**。本方案只保留真实欠账，按"低风险先行 → 核心状态 → 前端巨石"排成 Q0~Q11。

## 1. 核实结果（本轮实测）

### 1.1 数字对账

| 报告读数 | 本轮实测 | 判定 |
|---|---|---|
| `run-guards.sh` 48 项全部通过 | `run` 行 **48** 条；当前 **47/48 绿**（`check-reference-truth` 红 1 处） | 条数对，绿态已变 |
| 19 个业务域 | 19（`domain-index.md` 与 `ls domains/.../ragagent` 一致） | ✅ |
| 40 个 Port | 40 个 `*Port.java` | ✅ |
| 326 个 contract 文件 / 356 份 `package-info` | 326 / 356 | ✅ |
| 52 个主源码文件 >600 行 | 守卫输出 **52**（≥800 为 0） | ✅ |
| 28 个方法 >150 行 | 棘轮输出 **28**，无新增 | ✅ |
| `session/dto` 28 / `session/service` 27 | 28 / **29** | 近似 |
| 2,483 主源文件 / 266 方法分支 / 38 构造器 >8 | 2,483 / 266 / 38 | ✅ |
| 前端 771 条测试通过 | 实跑 **771 pass / 0 fail**（4.96s） | ✅ |
| 26 家 `Provider.validateConfig`、生产 0 调用 | 26 个 `ProviderName`、26 个实现类；生产调用 **0** | ✅ |
| 前端 7 个文件行数 | 逐件一致（6,593 / 6,250 / 5,613 / 4,509 / 4,097 / 3,711 / 1,333） | ✅ |
| `package.json` 无 lint script | 无（有 type-check / test / i18n 脚本） | ✅ |

补充口径：报告引用的"38 个构造器 >8"来自 `complexity-report.py`（**>8**，候选审查线）；守卫 `check-hygiene-budget.py` 的基线是 **53 个 ≥8 参构造器**（含跨行签名）。两个数字不矛盾，但**不能互相替代**，也不作为硬门。

### 1.2 关键技术事实（复核成立）

| 报告项 | 实测证据 |
|---|---|
| 模型保存不跑厂商校验 | `ModelService.createModel/updateModel/updateModelCredentials` 均无 `validateConfig` 调用；仅 `ProviderValidationTest` 调用 |
| 向量库控制面双份知识 | `VectorStoreConfigService`（417 行）有**两个独立 switch**：必填字段 `:285`、SSRF 地址规则 `:317`；`EngineFactory` 内已有 `SPECS`（构造 + 地址策略） |
| 存储规则分散 | 4 处：`FileServiceFactory` 的创建 switch、`StorageBackendService.validateForProvider` 的必填 switch、`StorageBackendService` 的探测 switch、`FileServiceResolver.PROVIDERS` 数组（9 个） |
| 租户 KV 分发集中在一个类 | `TenantConfigOps` 391 行；`getTenantKV`/`updateTenantKV` 两个 key switch + 7 个 get/put + 脱敏 |
| `ChatManage` 手工复制面 | 309 行 / 50 个字段；`cloneChatManage()` 手工复制 **41 个请求段字段** + 5 组状态位（`:254-308`）；`fork()` 直接等于全量 clone（`:229`） |
| 问答 Service 里有裸 SQL | `SessionKnowledgeQaService` 701 行；`DataSource` 字段 `:69`、构造参数 `:86`、SQL `:148`，异常包成 `RuntimeException` |
| 占位 Port | `PipelinePorts` 内 3 个：`SessionService`、`WebSearchStateService`、`WebSearchProviderRepository`，均标注"存而不读" |
| 入库 Worker 构造面 | `KnowledgeProcessWorker` **20 参**；`KnowledgeIndexStage`/`KnowledgeFinalizingStage` 已在构造器内组装（`:85-86`、`:124`），文本提取器亦内联创建（`:117-120`） |
| 检索 Service 依赖面 | `HybridSearchService` 758 行 / 9 参；已有 `HybridFusionOps`/`HybridStoreGroupOps`/`HybridResultOps` |
| 流式消息弱类型 | `useChatStreamHandler.ts:9` 与 `utils/steerStreamFork.ts:5` 均为 `ChatMessage = Record<string, unknown>` |

## 2. 对报告的修正（先纠偏，再排期）

### 2.1 当前工作区不是 48/48 绿（Q0 已按"删除"处置）

`git status` 有 4 份**未提交删除**：`docs/api-response-convention.md`、`docs/known-gaps.md`、`docs/internal/golden-fixtures.md`、`docs/migration-troubleshooting.md`。

- 用户拍板（2026-10-24）：**这些文件不要了**，按删除收口，不恢复；
- 引用迁移：API 外壳 → `docs/api/README.md`；已知缺口 → `HANDOFF §11.1`；契约金片 → `HANDOFF §7`；迁移排障 → `getting-started.md` 常见失败；
- 报告"48 项全部通过"对改动前的工作区不成立；Q0 收口后恢复 48/48。

### 2.2 §6.3 的"TurnCompletionCoordinator"已完成大半，不能再建一个

`TurnLifecycle`（68 行）已经实现：终态单赢家（CAS）、`STREAMING → FINALIZING → DONE/FAILED`、"只有轮次所有者可写终态"（`QaTurnExecutor`）。报告列的"谁可以提交最终完成态 / 成功与失败如何单赢家"正是它。

真实欠账只有 **H1b-2**（HANDOFF 已登记待做）：stop watcher、100ms 轮询、3 把锁的收拢与状态落库，以及 steer backlog / live-run / `SessionLookupScope` 的收尾对称性。**不新建协调器**，Q7 并入 H1b-2。

### 2.3 §6.1 有两处必须先改写

1. `TurnInputView` 声明"这些字段在一次执行内不再变化"，但 `rerankThreshold` 被 `PluginRerank` 在降级重试里改写 3 次（`:172/:177/:190`），另有 4 处 setup 写入。**冻结请求段之前，先把 `rerankThreshold` 移进 `RerankOutcome`**，否则"不可变"是假的。
2. 报告建议的 4 个 Policy 记录会重排约 300 个 getter 调用点，并与 `gen-pipeline-map.py`（从 `ChatManage` getter/setter 生成 14 事件 / 17 插件表）耦合。**改用"共享不可变快照 + 委托 getter"**：方法名不变，插件与管线表零改动，`fork()` 只新建状态对象。收益相同，改动面小一个量级。
3. `QaRequestContext` 仍在（34 个可变字段 + `buildQaRequest()` 约 30 行手工复制）；它不是新问题，是 H2b-3/N7 的**残留复制面**，与 `ChatManage` 一起收，不单独重写三类。

### 2.4 §7.2 `KnowledgeService` 三分不做

`KnowledgeService` 624 行 / 18 参，但 M6e 已抽出 `KnowledgeTagBatchService`；M 系列明确"不追 300 行硬指标、不制造纯转发层"，L15 也否决过"为拆而拆"。三分后 Controller 只是多一层转发，**不排期**。

### 2.5 §5.4 租户 KV Registry 降级为观察项

`TenantConfigOps` 的 7 个 key 分发确实集中，但 N3 刚落地"逐字段加密 + 读兼容明文"、P1.10/P1.11 刚改过 websearch/parser 同一面。此时再包一层 Handler 会对同一批代码二次翻修，收益/风险比低。**保留观察**，等 Q1/Q4/Q5 完成后再评估。

### 2.6 §5.2 的 Descriptor 不应把 `runtimeFactory` 放进 contract

报告的目标结构把 `runtimeFactory` 列进描述符。控制面（`domains/vectorstore`）只需要"必填字段 / 默认值 / 地址策略"，**不需要也不应该拿到运行时工厂**——拿到就会绕过模块边界。落法改为：

- `contract.vectorstore`：只放**控制面规则**（`requiredFields` / `defaults` / 地址策略查询）；
- `retrieval-engine`：内部描述符持有 `runtimeFactory` + `connectivityProbe`（复用现有 `EngineSpec`，目录端口为 `VectorStoreEngineCatalogPort`）。

### 2.7 §6.4 的"端口改能力命名"不做

`ChatModelProvider` / `KnowledgeRetrievalGateway` 这类改名是纯词汇变更，无行为收益，且会打断既有提交与文档的追溯。**只删 3 个占位 Port**。

### 2.8 §8 前端的两个事实修正

- `WikiBrowser` 已有起步：`wikiDirectoryState.ts`（45 行）+ 70 行测试，首刀范围小于报告假设；
- `AgentEditorModal.test.mjs`（111 行）是**源码正则断言**，`AgentStreamDisplay.style.test.mjs` 只测样式——不是行为覆盖。拆分前必须补真实特征测试。

## 3. 采纳 / 修正 / 不做总表

| 报告项 | 判定 | 落点 |
|---|---|---|
| 5.1 模型配置校验接线 | **采纳**（K3 曾主动暂缓，见 `known-gaps` §19） | Q1 |
| 5.2 向量库 Descriptor | **采纳，改写**：contract 只放控制面规则 | Q4 |
| 5.3 存储 Provider 规则 | **采纳**（域内描述符，不上提 contract） | Q5 |
| 5.4 租户 KV Registry | **降级观察** | — |
| 6.1 ChatManage 策略化 | **采纳，改写**：快照 + 委托 getter，不做 4 个 Policy 记录 | Q3 |
| 6.1 QaRequestContext 拆三类 | **修正**：只收手工复制面，不重写 | Q3c |
| 6.2 SQL 下沉 | **采纳**（仓库已有同名方法，见 Q2） | Q2 |
| 6.2 抽 QaPipelinePlanner | **降级**：管线选择仅约 40 行，先不动 | — |
| 6.3 统一终态收尾 | **修正**：并入 H1b-2，不建 Coordinator | Q7 |
| 6.4 删占位 Port | **采纳** | Q2 |
| 6.4 端口改名 | **不做** | — |
| 7.1 Worker 装配收敛 | **采纳**（只移构造，不重写阶段） | Q6 |
| 7.2 KnowledgeService 三分 | **不做** | — |
| 7.3 HybridSearch 阶段化 | **观察**（已有三个 Ops，重排收益低） | — |
| 7.4 AgentToolBackends 工厂化 | **观察**（报告自评低优先） | — |
| 8.1~8.3 前端巨石与流式类型 | **采纳**，先测试后拆分 | Q8~Q10 |
| 8.4 ESLint/knip/规模预算 | **采纳（report-only 起步）** | Q11 |
| 9 OpenAPI 类型生成 | **观察**：无当前痛点，金片已是兼容保障 | — |

## 4. 批次

### Q0 删除收口与基线归位（S，先决）

- 按用户拍板**删除** 4 份文档，不恢复；
- 引用迁移：`api-response-convention.md` → `docs/api/README.md`；`known-gaps.md` → `HANDOFF §11.1`（守卫与 10 处类注释同步）；`golden-fixtures.md` → `HANDOFF §7`；`migration-troubleshooting.md` → `getting-started.md`「常见失败对照」；
- 目标：`bash scripts/run-guards.sh` **48/48**；`check-known-gaps` 改指针后配红态探针；
- 不夹带业务代码改动。

### Q1 模型配置结构校验接线（M，P0）

- `contract.model` 新增窄端口 `ModelConfigValidator`（1 方法）+ `ModelConfigDraft`（provider / 类型 / baseUrl / apiKey / appSecret / extra）；
- `model-runtime` 实现：映射到现有 `Config` 后调用 26 家 `Provider.validateConfig`；
- 接线点：`ModelService.createModel` / `updateModel` / `updateModelCredentials`（初始化向导与 `/model-catalog` 均经 `ModelService`，无需另接）；
- 内置模型不走 `ModelService`（`BuiltinModelsReconciler` 直写仓库），**不会**被新校验误伤；
- 只做结构校验，**不联网**；"测试连接 / 调试"维持现状；
- 完成后删除 `Provider` 类注释的缺口指针与 `known-gaps` 第 19 条；
- 验收：缺失必填字段保存即 400；合法保存无任何出站请求（测试断网可证）；金片零变更；全量闸门绿。

### Q2 SQL 下沉 + 占位 Port 清理（S，低风险）

- `SessionKnowledgeQaService.listKnowledgeIdsByTagIds` 的裸 SQL 删除；`KnowledgeTagRepository.listKnowledgeIdsByTagIds`（`:123`）**已有同一查询**，通过 `KnowledgeEntryReadPort`（或新窄标签读端口）暴露，避免 domain→domain 直连；
- 删 `PipelinePorts` 三个占位接口与 `QaWiring` 对应装配参数；
- 验收：`SessionKnowledgeQaService` 不再有 `javax.sql.DataSource` / `SELECT`；`gen-port-map` / `contract-shape` / `port-budget` 绿；问答定向测试 + 金片不变。

### Q3 请求段冻结与 fork 白名单（M，H2 收尾）

- **Q3a**：`rerankThreshold` 从 `TurnInputView`/`ChatManage` 移入 `RerankOutcome`（7 处写、11 处读）；
- **Q3b**：`ChatManage` 持不可变 `TurnInputSnapshot`（41 字段）+ 6 组状态对象；getter 委托、方法名不变；`fork()` = 共享快照 + 新建状态对象；删 `cloneChatManage()`；
- **Q3c**：`QaRequestContext.buildQaRequest()` 的手工复制收敛为一次显式映射（必要时引入 `ResolvedQaContext`），不强制拆三类；
- 配一条可证伪的守卫/测试：**插件包不得调用请求段 setter**（白名单为 0），红态探针必验；
- 验收：`gen-pipeline-map.py --check` 不漂移；并行检索（`PluginSearchParallel` 两 fork）测试绿；金片零变更。

### Q4 向量库 Descriptor 单源（M，P0 邻接）

- `contract.vectorstore.VectorStoreEngineRules` + 查询端口（引擎类型 → 必填字段 / 默认值 / 地址策略）；
- `retrieval-engine`：9 个引擎的描述符（内部持 factory + probe），与 `EngineFactory.SPECS`、`EngineTypes` 合表，避免第三份清单；
- `VectorStoreConfigService` 两个 switch 改为查表；
- 验收：新增引擎只改描述符一处 + 一条测试；9 个引擎的必填/SSRF 行为逐项等价（表驱动）；`contract-shape`（端口 ≤7 方法 / 字段 ≤20）绿。

### Q5 存储 Provider Descriptor（M）

- `storage.domain.StorageProviderDescriptor`：provider、展示名、必填字段、默认值、配置转换、运行时创建、探测；
- S3 兼容族（s3/minio/obs/ks3）共享基实现，保留厂商字段、错误文案、默认值差异；
- 分两步：先"校验单源"（`validateForProvider`），再"创建单源"（`FileServiceFactory` + `FileServiceResolver.PROVIDERS`）；
- 验收：新增 provider 只改描述符；连接测试仍做真实 bucket 探测；存储契约测试与金片不变。

### Q6 入库 Worker 装配收敛（S）

- 把 `KnowledgeTextExtractor` / `KnowledgeIndexStage` / `KnowledgeFinalizingStage` 的构造移到 `knowledge/config` 装配点；Worker 只收队列、任务状态、3 个阶段（20 参 → 约 5）；
- 不引入 god Context，不动阶段内部逻辑；`ObjectProvider` 条件语义保留；
- 验收：`check-hygiene-budget` 的 ≥8 构造器 53 → 52；入库定向测试 + 一条全链路金片；Worker 单测装配成本明显下降。

### Q7 终态收尾残余（M/L，并入 H1b-2）

- 先补状态转换矩阵测试：成功 / 失败 / 取消 / steer follow-up × 普通问答 / Agent，覆盖普通问答失败补偿、live-run 409/503、steer backlog 清理、`SessionLookupScope` 与租户上下文恢复；
- 再收 stop watcher、轮询、3 把锁（H1b-2 原范围）；
- **不新建 TurnCompletionCoordinator**，不包大事务，不改 100ms 轮询；
- 验收：矩阵测试全绿；无双重收尾与 live-run 泄漏；全量闸门绿。

### Q8 流式类型与聊天组件（M）

- `streamEventNormalizer`：后端事件归一为按 `type` 判别的联合；`ChatMessage` 去掉 `Record<string, unknown>`（`useChatStreamHandler.ts:9`、`steerStreamFork.ts:5`）；
- `messageReducer`：消息创建 / 更新 / 工具调用 / 终态；
- 先补特征测试：保存载荷、事件顺序、取消、断流恢复、steer（报告 §8.3）；
- 第一步只拆 `AgentStreamDisplay` 的 `AgentTimeline` / `AnswerContent`；
- 验收：`vue-tsc` + 771+ 测试绿；wire 键名零变更。

### Q9 WikiBrowser 拆分（L，多批）

- 首刀：把目录分页/懒加载扩成 `useWikiDirectory`（现有 `wikiDirectoryState.ts` 45 行是起点），Shell 只组合；
- 次刀：`WikiGraphPanel`；再 `WikiIssuesPanel`；最后 `WikiReader`；
- 每刀独立提交：`vue-tsc` + 组件行为测试；样式随面板；
- 阶段目标：单文件 <1,500 行（内部预算，不作硬门）。

### Q10 AgentEditorModal 拆分（L，多批）

- 先 `useAgentEditorForm`（表单/脏值/模式）+ `agentEditorSerializer` / `agentEditorValidator`（保存载荷与校验）；
- 再按面板拆 Basic / Model / Retrieval / Tools / Prompts / Publishing；
- 拆分前先补真实行为测试——现有 `AgentEditorModal.test.mjs` 是源码正则，不算覆盖；
- 验收：保存载荷测试不变；Shell 只组合；面板 props/emits 稳定。

### Q11 前端静态检查与规模棘轮（S/M）

- ESLint flat config + 未使用 import；`knip` 先 **report-only**（Vue 动态 import 易误报）；
- 规模预算：新增文件 >1,500 行即红，存量用棘轮脚本只减；不用 `max-lines` 一刀切；
- 验收：CI 只红新增；存量基线只减；不误伤现有 98 个测试文件。

## 5. 推荐顺序

1. **第一梯队（配置与低风险）**：Q0 → Q1 → Q2 → Q4
2. **第二梯队（扩展面与装配）**：Q5 → Q6
3. **第三梯队（核心状态）**：Q3 → Q7
4. **第四梯队（前端）**：Q8 → Q9 → Q10 → Q11

理由：Q1 直接消除"保存成功、运行失败"的用户可见问题；Q2 是最便宜的净收益；Q4/Q5 把新增厂商从"改多处"变成"改一处"；Q3/Q7 动的是问答竞态与状态面，必须先有守卫和测试；前端巨石独立排期，避免与后端批次互相拖累。

每批独立提交、独立全绿；收尾跑全量 `mvn -B verify` + `run-guards.sh` + 前端 `vue-tsc` / `npm test`。非功能改动只跑编译，不跳全量。

## 6. 明确不做

- 不拆微服务、不拆 `domains`、不重建 `:knowledge`/`:session` 模块；
- 不新建 `TurnCompletionCoordinator`（`TurnLifecycle` + H1b-2 已覆盖）；
- 不把 `KnowledgeService` 三分（纯转发层）；
- 不做端口能力化改名；
- 不把 `runtimeFactory` 放进 contract；
- 不一次性重写前端，不一次性生成全量 OpenAPI 类型；
- 不用"行数/类数"作为唯一判据，不为凑数字造碎类。

## 7. 总体验收

- 新增模型 Provider：只改注册表 + 校验器一处，保存时结构错误即 400，不联网；
- 新增向量库 / 存储 Provider：只改描述符一处，控制面与运行时同源；
- 核心问答 Service 不含 SQL；`ChatManage` 无手工全量 clone，插件不写请求段；
- 普通问答与 Agent 的终态 / 取消 / 失败 / follow-up 有统一测试矩阵；
- Wiki / Agent 编辑器的 Shell 只做组合，单一 composable 不再管多个独立面板；
- 48 守卫、`mvn -B verify`（基线 5082/0/2）、前端 `vue-tsc` + 771 测试、1,346 金片全部保持绿。
