# contract 还乡与瘦身（2026-10-22）

> 状态：方案已拍板，按 B1→B4 执行；P2 另立项。
> 目标：把 contract 从 328 个顶层类型瘦到「真·多消费者内核」，单域形状回域；不拆 contract 模块、不建每域 api 子模块、不动 SSE wire 字段、不解 knowledge↔wiki 环。

## 1. 判据（本次执行口径）

一个 contract 类型可还乡，必须同时满足：

1. **消费面单域**：闭包内所有类型的跨模块 `main` import 只指向同一个业务域/模块；
2. **闭包可整体搬**：反向引用它的 contract 类型必须一起搬，不能留下 contract 内部断链；
3. **不制造跨域依赖**：搬走后，原消费方只允许 `boot`（装配层）跟随改 import；`platform` 或其他业务域出现 main 消费即为阻断；
4. **wire 字段不变**：带 Jackson 注解的类，注解与字段名原样跟随，迁移后跑金片/前端键守卫。

`test` / `testFixtures` 的 import 视为可跟随修改，不计入阻断。

## 2. 事实核对（2026-10-22 实测）

| 断言 | 实测 | 结论 |
|---|---|---|
| contract 328 类型 | `gen-contract-consumers.py` 输出 328 | 准确 |
| 6 条域间边/27 类零守卫 | `check-domain-contracts.py` 已是闸门第 42 条，基线 6 边/27 类，新增即红 | **守卫已存在，不新建** |
| T1 清单 48 类、−40 左右 | 按名单实点 47 类，可迁 29 类 | 高估，实际 −29 |
| `contract.tool` 15 类 | 可迁闭包 14 类（含 `ApprovalEvent`）；`McpApprovalOps` 被 embedchannel 消费、`ResponseType` 被全仓消费、`ToolCancellation` 被 agent-engine 消费 | 三者必须留 |
| session 12 类可迁 | 仅 `PipelineUsedMemoryView` + 4 个 payload 可迁；其余被 `MessageView`/`SessionView`/`PipelinePorts` 持有 | 7 类阻断 |
| `TextConv` 可直接删 | `FaqChunkMetadata:160` 调用 `TextConv.toSimplified`；P1.6 已判其为 contract 内部助手 | **不能删** |
| `KnowledgeQaRunner` 唯一消费者是 session | 全仓无调用方，只有 `KnowledgeQaRunnerImpl` 实现 | 零调用端口，按拍板删除 |
| 7 个窄 knowledge 端口可收拢 | H6-1（2026-10-20）刚把 38 方法拆成 7 窄端口；评估文档定性「结构就位、窄面未启用」 | **保留，不收拢** |

## 3. 执行批次

### P0 闸门与工具（不改形状）

- 不新增 ArchUnit 域间测试：`check-domain-contracts.py` 已覆盖 import 级跨域边，且带三档/棘轮。
- 给 `scripts/gen-contract-consumers.py` 增加 `--relocate`：输出每个 contract 类型的「反向闭包 + 目标域 + 阻断项」，作为本批的搬迁可行性答案。
- 验收：脚本对 `ToolApprovalGate` 报「可迁 mcp / 闭包 14」，对 `MessageImage` 报「阻断 / MessageView 跨 im、embedchannel、session、platform」。
- 红态探针沿用现有守卫：临时加一条 `domains.session → domains.mcp` 的 main import，`check-domain-contracts.py` 必须非零退出，回滚后恢复绿。

### B1 tool approval 族 → `domains:mcp`（−14）

迁移清单（整族，缺一不可）：

`ToolApprovalGate`、`ToolApprovalGateFactory`、`ToolApprovalException`、`ToolEnabledChecker`、`ToolEnabledChecks`、`ToolPolicyChecker`、`ToolBulkEnabledChecker`、`PendingRequest`、`OAuthPendingRequest`、`Decision`、`Cancellation`、`ApprovalEventBus`、`ApprovalCancellations`、`ApprovalEvent`。

- 落点：`domains/src/main/java/com/ragagent/mcp/approval/`（包 `com.ragagent.mcp.approval`）。
- 保留在 contract：`ResponseType`、`ToolCancellation`、`McpApprovalOps`、`ToolCapabilities`、工具 SPI 与 `ToolDefinitions`。
- 必做：迁移类若引用 `ResponseType`（原同包无 import）需**新增** `import com.ragagent.contract.tool.ResponseType;`。
- 同步：`McpWiring` 注入类型改本地包；`contract/tool/package-info.java` 更新描述；`gen-port-map.py` 重生成（若该端口在册）。
- 验收：mcp 域定向（`GateTest`/`GateCrossInstanceTest`/`SpringRedisPubSubTest`/`ToolPolicyTest`/`ApprovalWireFormatTest`/`ApprovalEventBusesTest` + 两个 controller 测试）→ 全量闸门。

### B2 agent-engine 6 件 → `agent-engine`（−6）

`PinnedSkillInfo`、`PinnedSkillInstructions`、`PinnedMCPServiceInfo`、`SystemPromptSection`、`CompactionSettings`、`CompactionOverflow`。

- 落点：PinnedSkill\*/SystemPromptSection → `com.ragagent.agent.prompt`；Compaction\* → `com.ragagent.agent.compaction`（以现有包归属为准）。
- 跟随改 import：`agent-engine` main 15 处、`boot` 测试 3 处。
- 验收：`AgentEngine` 相关定向 + 全量闸门。

### B3 单件 5 类 → 各域（−5）

| 类 | 落点 |
|---|---|
| `WikiConstants` | `com.ragagent.wiki` |
| `TenantRowView` | `com.ragagent.auth.dto` |
| `ModelViews` | `com.ragagent.initialization.dto` |
| `SourceTypes` | `com.ragagent.knowledge.support` |
| `PipelineUsedMemoryView` | `com.ragagent.session.dto` |

- 跟随改 import：wiki 27 处 + boot 测试 4 处；auth 19 处 + session 测试 1 处；initialization 1 处；knowledge 2 处；session 10 处。
- `TenantRowView` 迁出后清理 `contract-shape.baseline.json` 的 rows/fields 登记（该条已到期）。
- 验收：各域定向 + 全量闸门。

### B4 event.payload 4 件 → `domains:session`（−4）

`SessionTitleData`、`MemoryRecalledData`、`AgentReferencesData`、`AgentReflectionData`。

- 落点：`com.ragagent.session.dto`。
- Jackson 注解（snake 键）原样跟随，不改字段名、不改构造器。
- 验收：`EventPayloadJsonTest` + `FaqContractTest` 族 + `check-fe-contract-keys.py` + `check-contract-sync.py` + 全量闸门。

### P3 死码与包说明

- 删除 `AgentStepData`（−1）：仅 `domains/event` 测试与历史计划文档引用，前端零痕迹；删前再查一次 wire 文档。
- 删除 `KnowledgeQaRunner` / `KnowledgeQaRunRequest` / `KnowledgeQaRunResult`（−3）及 `session/api/KnowledgeQaRunnerImpl`、`package-info` 提及：用户 2026-10-22 拍板「评估不做了」。
- `TextConv` 保留：`FaqChunkMetadata` 在用，P1.6 已判内部助手。
- `event.payload/package-info.java` 写明「事件载荷=外部权威面，platform 序列化，不得还乡」；按实测类型重盘，不沿用未经核实的 7 类说法。

### P2 前置解耦（另立项，不在本批）

以下 18 类暂留 contract，阻断原因与解除条件：

| 组 | 类 | 解除条件 |
|---|---|---|
| session 视图 | `MessageImage`、`MentionedItem`、`UsedMemory`、`SessionLastRequestState`、`SuggestionItem`、`PipelineMessageView`、`PipelineMessageImageView` | 先拆 `MessageView`/`SessionView`/`PipelinePorts` 的字段族（H6 级） |
| knowledge.config | `StorageConfigView`、`ImageProcessingConfigView`、`IndexingStrategyView`、`StorageProviderConfigView`、`KnowledgePageView` | 先处置 `KnowledgeBaseRowView`/`KnowledgeBaseResponse`/`KnowledgeCatalogPort`（shape 基线已登记） |
| 其余 | `WikiException`、`StorageUrlContext`、`OllamaProgressListener`、`OllamaPullProgress`、`VlmConfig`、`CodePoints` | 分别先解 `WikiPageNotFoundException`、`Mode`、`OllamaAdminPort`、`ModelRuntime`、`Tokens` |
| memory | `MemoryKeys`、`MemoryKinds`、`MemoryRecall`、`MemoryRetrievalContext` | 先解 `MemoryConfig`（auth 消费）与 `PipelinePorts` |

## 4. 结果预期

- 本批：328 − 29 − 4 = **295** 个类型（−10%）。
- 每批独立提交、独立全绿；提交信息写批次号与验证方式。
- 每批结束跑 `gen-contract-consumers.py --check`，确认无零消费者。
- 文档数字随批更新：`docs/architecture/packages.md` 的包描述、`overview.md` 的装配点/类数。

## 5. 回填

执行记录写回 `docs/internal/HANDOFF.md` §12，格式 `(B1)`…；本文件末尾追加「执行结果」一节。

## 6. 执行结果（2026-10-22）

- P0：`gen-contract-consumers.py --relocate` 上线，29 个目标类全部判定可迁；顺带修复 `consumer_label` 的绝对路径匹配 bug（此前 domains 消费一律显示为 `domains`）。
- B1：approval 族 14 件 → `mcp.approval`；定向 80 用例绿。
- B2：agent-engine 6 件 → `agent.prompt` / `agent.compaction`；定向 45 用例绿。
- B3：5 个单件各归其域；编译绿，`TenantRowView` 的形状基线登记已清理。
- B4：4 个 wire 载荷 → `session.dto`（`QueryData.orEmpty` 包私有，已内联为同语义三元）；`EventPayloadJsonTest` 64 + `AgentStreamBridgeTest` 9 + `FaqContractTest` 13 全绿。
- P3：`KnowledgeQaRunner` 组 3 件 + `AgentStepData` 删除；`TextConv` 保留；`event.payload` 包说明改为「外部权威面，不随批次搬动」。
- 结果：contract **328 → 295** 类型；全量 `mvn -B clean verify` 绿（5072 / 0 / 2），48 守卫绿；三份架构索引已按生成器同步。
