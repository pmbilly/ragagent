# 架构与代码可维护性评估及优化方案

- 日期：2026-10-24
- 范围：模块划分、核心业务链路、管理后台配置、能力抽象、代码质量、前端可维护性
- 性质：基于当前工作区的只读评估；不包含源码改造
- 核心结论：保留七模块和 19 个业务域，优先收敛配置规则、问答状态、扩展描述符和前端上帝组件，不拆微服务、不机械拆包

## 1. 总体结论

当前项目整体架构良好。模块划分、依赖方向、契约纯度、端口化和架构守卫明显优于一般业务项目，不需要推倒重来。

主要问题不是“模块分错了”或“类太多”，而是：

1. 管理后台与运行时的部分配置规则多点维护，扩展时容易漂移。
2. 问答核心链路中，请求配置、执行状态和收尾逻辑仍有较强耦合。
3. 少数门面类、构造器和前端组件承担了过多变化原因。
4. 现有测试与架构守卫较强，但大文件内部的局部可测试性仍不均衡。

建议优先实施四项改造：

- 配置单一事实源；
- 管线状态收敛；
- 扩展描述符统一；
- 前端上帝组件拆分。

## 2. 验证范围与实测结果

本次评估覆盖核心问答、知识入库、混合检索、模型配置、租户配置、存储、Wiki、Agent 编辑和流式聊天链路。

实测结果：

- `bash scripts/run-guards.sh`：48 项架构守卫全部通过。
- 19 个业务域、40 个 Port、326 个 contract 文件、356 份 `package-info.java`。
- 跨业务域实体 import 为 0；模块依赖、包环、契约纯度、租户作用域、路由权限和工具注册检查合规。
- 52 个后端主源码文件超过 600 行、28 个方法超过 150 行，均处于存量基线内，没有继续增长。
- `session/dto` 有 28 个文件、`session/service` 有 27 个文件，属于包规模提醒，不是硬性问题。
- `python scripts/complexity-report.py --top 25`：扫描 2,483 个主源文件，方法分支候选 266 处、构造器依赖超过 8 的候选 38 处。该报告用于人工审查，分支数不等同于精确圈复杂度。
- 前端 `npm run type-check` 通过，`npm test` 的 771 条测试全部通过。
- 后端问答收尾、Agent 模式和租户配置脱敏等 18 条定向测试全部通过。
- 未运行后端全量 `mvn verify`，定向测试不能等同于全量后端验证。

主要构造依赖候选：

| 类 | 构造参数数 | 判断 |
| --- | ---: | --- |
| `KnowledgeProcessWorker` | 20 | 阶段边界已存在，应收拢装配而不是重写流程 |
| `KnowledgeService` | 18 | 聚合门面过大，宜按用例拆分入口 |
| `ImTurnOrchestrator` | 15 | 后续按 IM 生命周期评估 |
| `SessionAgentQaService` | 13 | 与问答上下文改造联动评估 |
| `HybridSearchService` | 9 | 保留算法，按处理阶段改善导航 |

主要前端文件：

| 文件 | 行数 | 主要问题 |
| --- | ---: | --- |
| `frontend/src/views/knowledge/wiki/WikiBrowser.vue` | 6,593 | 目录、分页、图、搜索、revision、issues、内嵌 Chat 共存 |
| `frontend/src/views/agent/AgentEditorModal.vue` | 6,250 | 多组配置、发布渠道、校验、序列化和弹窗生命周期共存 |
| `frontend/src/views/knowledge/components/FAQEntryManager.vue` | 5,613 | 列表、编辑、批量操作和导入导出集中 |
| `frontend/src/views/knowledge/KnowledgeBase.vue` | 4,509 | 多业务面板聚合 |
| `frontend/src/views/chat/components/AgentStreamDisplay.vue` | 4,097 | 流式事件、时间线和答案展示耦合 |
| `frontend/src/components/Input-field.vue` | 3,711 | 输入、队列、附件和 steer 交互集中 |
| `frontend/src/composables/useChatStreamHandler.ts` | 1,333 | 多类事件共用较弱消息类型 |

## 3. 应保持的架构资产

### 3.1 七模块拓扑

保留：

- `contract`：公开 API、SPI、不可变载荷，纯 JDK；
- `platform`：Web、错误、SSRF、事件、SSE、追踪和配置底座；
- `model-runtime`：模型、Embedding、Rerank 和模型上下文；
- `retrieval-engine`：向量引擎、混合检索、融合和索引；
- `agent-engine`：ReAct、工具、技能、预算和上下文压缩；
- `domains`：19 个业务域和持久层；
- `boot`：应用入口、组合根和跨模块装配。

不因为 `domains` 文件多就机械拆成多个 Maven 模块。只有出现独立发布、独立团队或独立扩容需求时，才重新评估物理拆分。

### 3.2 核心问答生命周期划分

普通问答和 Agent 共用入口执行簇：

```text
KnowledgeQaController
  → QaEntryRunner
  → QaTurnExecutor
  → QaSseOrchestrator
  → SessionKnowledgeQaService / SessionAgentQaService
```

普通问答按请求动态组装 RAG 管线，Agent 走 ReAct；SSE 生成侧与输出侧分离。

一次请求跨越 20 多个类并不天然是问题。`QaEntryRunner`、`QaTurnExecutor`、`QaSseOrchestrator` 和 `QaTurnFinalizer` 分别承担不同生命周期职责，不建议为了减少类数而合并。

100ms 轮询是现有 SSE 设计的明确取舍。没有延迟、帧序和多实例数据支撑时，不直接改成推送。

### 3.3 架构守卫、契约金片和文档生成

现有守卫能够防止：

- 跨域实体依赖回流；
- 模块依赖和包环恶化；
- 跨线程租户上下文丢失；
- 路由权限遗漏；
- 工具注册不一致；
- 响应契约静默变化；
- 大文件和长方法继续增长；
- 架构文档与代码失配。

后续重构以“守卫不削弱、金片不静默变化、行为兼容”为基本约束。

## 4. 问题优先级总表

| 优先级 | 问题 | 主要影响 | 改造方向 |
| --- | --- | --- | --- |
| P0 | 模型、向量库、存储、租户配置规则分散 | 保存成功但运行失败；新增厂商需修改多处 | 校验统一接线，建立 Provider Descriptor |
| P1 | `ChatManage`、请求上下文和手工复制逻辑耦合 | 新字段容易漏复制、漏覆盖或串状态 | 不可变策略对象与显式 Fork Context |
| P1 | 问答编排、持久化查询和收尾职责混杂 | 主流程难读，异常路径修改风险高 | SQL 下沉、规划与执行分离、统一收尾协调 |
| P1 | 部分门面和构造器过大 | 依赖难理解，测试装配成本高 | 按用例或处理阶段收拢 |
| P1 | Wiki、Agent 编辑器、流式聊天组件过大 | 局部修改影响面大，状态难追踪 | 薄壳、composable、面板组件和判别联合类型 |
| P2 | 手工 DTO 和静态检查仍有提升空间 | 契约变更依赖人工同步 | 渐进式 OpenAPI 类型生成、ESLint/knip |

## 5. P0：管理配置与运行时消费同一份规则

### 5.1 模型配置校验统一接线

现状：`Provider.validateConfig()` 已有 26 家实现，但生产保存链路未调用，只有测试使用。保存模型时主要检查通用字段，厂商特定错误可能延迟到首次调用才暴露。

目标：

- 在模型运行时提供统一的 `ModelConfigValidator`。
- 模型创建、模型更新、凭据更新和初始化向导调用同一个校验器。
- 保持现有 Provider 特定校验逻辑，不在管理面复制。

明确区分：

- 结构校验：必填字段、字段组合、URL 格式、Provider 特定约束；保存时同步执行。
- 联网探测：凭据有效性、模型是否真实可用；只在显式“测试连接”时执行。

验收：配置结构错误在保存时发现，保存操作不依赖外部厂商网络。

### 5.2 向量库 Descriptor 单源化

现状：`EngineFactory.EngineSpec` 已绑定构造和运行时地址策略，但 `VectorStoreConfigService` 仍通过独立 `switch` 维护必填字段和 SSRF 地址规则，控制面与运行时存在双份知识。

目标结构：

```text
VectorStoreEngineDescriptor
├── engineType
├── requiredFields / defaults
├── addressPolicy
├── runtimeFactory
└── connectivityProbe
```

模块落点：

- `contract`：定义纯 JDK 的描述符类型和查询 Port；
- `retrieval-engine`：拥有具体引擎描述符、工厂和探测实现；
- `domains/vectorstore`：通过 Port 获取规则，不再维护引擎 `switch`。

验收：新增一种向量库时，不再分别修改控制面校验、SSRF 规则和运行时工厂。

### 5.3 存储 Provider 规则收敛

现状：存储“测试连接”已复用真实后端探测并检查 Bucket，不是简单 TCP 拨号，应保留。需要改善的是厂商展示信息、必填字段、配置转换和运行时创建等规则仍分散。

方案：

- 在 `storage` 域内建立 `StorageProviderDescriptor`；
- 集中 provider 类型、展示元数据、必填字段、默认值、配置转换、运行时创建和探测；
- S3 兼容厂商共享基础实现，但保留各厂商字段、错误文案和默认值差异；
- 不引入包含大量空方法的超级 Provider 接口。

### 5.4 租户 KV 配置按类型注册

现状：`TenantConfigOps` 同时负责 KV 分发、读取、合并、校验、脱敏和写回。

保持 HTTP API 不变，内部调整为：

```text
TenantConfigRegistry
  key → TenantConfigHandler<T>
          ├── read
          ├── merge
          ├── validate
          ├── redact
          └── write
```

每种配置一个 Handler；权限判断继续保持横切统一，不复制到每个 Handler。`SystemSettingService` 的 Registry、DB/ENV/default 和副作用分离方式可作为参考，不需要重写。

## 6. P1：收敛问答管线的状态和生命周期

### 6.1 `ChatManage` 区分不可变策略与可变状态

现状：已经拆出 `QueryPlan`、`SearchOutcome`、`AnswerState`、`TurnRuntime`，方向正确；请求段仍有约 50 个可变配置字段，并行分支依赖手工 `cloneChatManage()` 维护复制面。

先归组为不可变对象：

```java
RetrievalPolicy   // KB、阈值、TopK、FAQ、向量库
ModelPolicy       // Chat、Rerank、VLM、视觉能力
WebPolicy         // Web Search、Fetch、数量限制
GenerationPolicy  // Prompt、Fallback、Citation、Summary
```

实施原则：

- `ChatManageFactory` 一次构建策略对象；
- `QueryPlan / SearchOutcome / AnswerState / TurnRuntime` 继续保留可变性；
- 并行分支不再复制整个 `ChatManage`，改为显式 `PipelineForkContext`，只复制白名单内需要的输入；
- 先完成字段归组和测试，不要求一次性全部 `record` 化。

`QaRequestContext / QaRequest` 按同样原则拆成：

- `QaCommand`：入口原始命令；
- `ResolvedQaContext`：鉴权、Agent、KB、模型解析后的不可变上下文；
- `TurnExecutionState`：取消、消息、SSE、steer 等运行状态。

### 6.2 主 Service 下沉 SQL，并分离规划与执行

现状：`SessionKnowledgeQaService` 同时承担请求解析、检索策略、管线选择、进度/trace，并直接使用 `DataSource` 执行标签查询 SQL。

改造顺序：

1. 将标签查询下沉到 `knowledge` 域的窄 Port/Repository；
2. 抽出 `QaPipelinePlanner`，只根据解析后的上下文决定阶段列表；
3. 明确 `QaPipelineRunner`，负责执行、事件推进和阶段观测；
4. 主 Service 只保留用例编排。

不合并现有入口、Executor、SSE 和 Finalizer。

### 6.3 统一终态收尾

普通问答和 Agent 在成功、失败、取消、steer follow-up 下的收尾路径不完全对称。普通问答失败补偿说明这一区域对状态顺序和竞争条件非常敏感。

引入 `TurnCompletionCoordinator`，集中定义：

- 谁可以提交最终完成态；
- 成功与失败竞争时如何保证单赢家；
- 取消时如何清理 steer backlog；
- follow-up 何时交接；
- `live-run` 何时清除；
- `SessionLookupScope` 和租户上下文何时恢复。

先补状态转换测试，再收拢实现。不要用大事务包裹异步链路，也不强制引入重型状态机框架。

### 6.4 清理无消费者的抽象

`PipelinePorts` 仍有明确标注“存而不读”的占位接口，`QaWiring` 的构造和装配面也偏大。

方案：

- 删除无消费者的占位 Port 及对应装配参数；
- 复用重复的 KB/Knowledge 投影；
- 技术端口逐步改为能力命名，如 `ChatModelProvider`、`KnowledgeRetrievalGateway`；
- `QaWiring` 保留插件顺序这一核心职责，减少装配细节噪音；
- 不为未来可能出现的需求预留空接口。

## 7. P1：大类按变化原因拆分

### 7.1 `KnowledgeProcessWorker`

构造器有 20 个参数，但提取、索引和最终化阶段已经存在，主流程不需要重写。

由装配层构建：

```text
ExtractionStage
IndexingStage
FinalizingStage
```

Worker 只接收队列、任务状态和三个阶段。不要引入一个包含所有依赖的巨型 Context，也不要拆坏现有阶段边界。

### 7.2 `KnowledgeService`

624 行、18 个构造依赖，且已有大量专项协作者。继续抽纯委托方法收益有限，按用例拆分入口：

- `KnowledgeCommandService`；
- `KnowledgeQueryService`；
- `KnowledgeIngestionFacade`。

Controller 和其他域只依赖需要的窄接口，共享访问原语继续复用，避免增加纯转发层。

### 7.3 `HybridSearchService`

已有 Fusion、Result、StoreGroupOps，不重写检索算法。按以下阶段组织代码和测试：

```text
prepare → route/retrieve → fuse → enrich
```

保持多 Store 扇出、RRF、FAQ、租户归属和超时语义不变。

### 7.4 Agent 工具后端

`AgentToolBackends` 可后续按工具族 Factory 注册，工具定义携带 `name / factory / requiredScope / risk`，使构造与目录同源。现有工具注册守卫已经较好，本项优先级低于配置与问答管线。

## 8. P1：前端上帝组件拆分

### 8.1 Wiki

目标：

```text
WikiBrowserShell
├── useWikiDirectory
├── useWikiGraph
├── useWikiIssues
├── WikiReader
├── WikiGraphPanel
└── WikiIssuesPanel
```

优先抽离目录分页桶和懒加载，再抽图与 issues，最后收薄 Shell。样式随面板拆分，不保留一个全局大样式块。

### 8.2 Agent 编辑器

目标：

```text
AgentEditorShell
├── useAgentEditorForm
├── agentEditorSerializer / validator
├── BasicPanel
├── ModelPanel
├── RetrievalPanel
├── ToolsPanel
├── PromptsPanel
└── PublishingPanel
```

IM 和 Embed 已有面板，优先抽离 Shell 状态、保存序列化和校验，不一次性移动所有模板。

### 8.3 流式聊天和输入区

拆分：

- `streamEventNormalizer`：把后端事件归一为判别联合；
- `messageReducer`：处理消息创建、更新、工具调用和终态；
- `AgentTimeline / AnswerContent / InputComposer`：负责渲染和交互。

当前 `ChatMessage = Record<string, unknown>` 削弱了 TypeScript 对事件字段和消息形状的检查，应改成按 `type` 判别的联合类型。

拆分前为大组件补特征测试，覆盖保存载荷、流式事件顺序、取消、断流恢复和 steer。不能根据缺少同名测试文件就断言完全没有间接覆盖。

### 8.4 设置页和前端静态检查

设置页后续可增加前端 `settingsSections` 注册表，统一 component、route、role、capability 和导航元数据，不改成后端驱动 UI。

前端 `package.json` 当前没有 lint script，可加入 ESLint、未使用 import 检查和 knip。对新增代码设置规模预算，存量采用棘轮下降，不用 `max-lines` 一次性判红。

## 9. P2：契约与工程质量

- 对新增或高频变化的 API 渐进使用 OpenAPI 生成 TypeScript 类型；
- 保留手写 UI ViewModel，不把传输 DTO 直接扩散到所有组件；
- 不一次性生成覆盖全部路由，避免大范围无行为收益 diff；
- 契约金片继续作为响应兼容保障，类型生成不能替代金片；
- 触及文件时清理过时注释，不单列大规模注释重写。

## 10. 目标结构

业务执行链：

```text
Controller
  → Application UseCase
  → Resolved Context / Immutable Policy
  → Pipeline / Engine / Stage
  → Narrow Ports
  → Adapters / Repositories
```

扩展能力：

```text
Provider Descriptor
  → Validation
  → Runtime Factory
  → Connectivity Probe
  → Admin Metadata
```

核心原则：控制流显式、配置单源、状态有边界、扩展只改一个注册点。

## 11. 分阶段实施路线

### 第一阶段：低风险、直接减少漂移

1. 将模型结构校验接入创建、更新、凭据更新和初始化向导。
2. 下沉 `SessionKnowledgeQaService` 中的 SQL。
3. 删除无消费者的占位 Port。
4. 为普通/Agent 的成功、失败、取消、steer 收尾补状态转换测试。
5. 为 Wiki 和 Agent 编辑器补关键特征测试。

### 第二阶段：收敛核心接缝

1. 建立向量库 Descriptor，让管理面和运行时共同消费。
2. 收敛存储 Provider 元数据和校验规则。
3. 将 `ChatManage` 请求字段归组为不可变策略对象。
4. 用显式 Fork Context 替代手工全量复制。
5. 引入统一的 Turn Completion 协调。

### 第三阶段：持续改善前端和契约

1. 依次拆 `WikiBrowser`、`AgentEditorModal`、流式聊天组件。
2. 建立强类型 Stream Event 和 Message。
3. 对新增或高频变化的 API 渐进使用 OpenAPI 生成 TypeScript 类型。
4. 增加 ESLint、未使用 import 检查和 knip。
5. 对新增代码设规模预算，存量棘轮下降。

每个阶段独立交付、独立回归，不把全部改造合并为一次大重构。

## 12. 验收标准

- 新增模型或向量库 Provider，不需要在多个 Service 中重复添加校验分支。
- 模型配置结构错误在保存时发现，联网探测与结构校验语义分离。
- 核心问答 Service 不再直接包含 SQL。
- 新增管线配置字段时，不需要同步修改多个手工 clone。
- 普通问答和 Agent 的终态、取消、失败、follow-up 有统一转换表和测试。
- Wiki、Agent 编辑器的 Shell 只负责组合，单一 composable 不再同时管理多个独立业务面板。
- 新增代码不突破复杂度和文件规模预算，存量指标只减不增。
- 现有 48 项守卫、前端测试、后端相关测试和契约金片全部保持通过。

## 13. 明确不做

- 不拆微服务；
- 不机械拆 `domains`；
- 不合并所有 QA 类；
- 不直接替换 SSE 100ms 轮询；
- 不一次性重写前端；
- 不为统一而抹平 Provider 的真实差异；
- 不用文件行数或类数量作为唯一重构判据；
- 不用大型事务处理异步问答终态；
- 不通过新增纯转发层制造“看似分层”的复杂度。
