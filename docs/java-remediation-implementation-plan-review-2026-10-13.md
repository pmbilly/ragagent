# 对 `java-remediation-implementation-plan-2026-10-13.md` 的实施评审

> 评审对象：`docs/java-remediation-implementation-plan-2026-10-13.md`（v0.2.0，B232~B267 共 36 批）
> 评审方式：**只读核对仓库实际状态**，未修改任何源码/脚本/配置。评审日 2026-10-13。

## 0. 总体判断

**计划可以作为施工图，但不要按现状直接开工——有 1 处文档状态过期、1 处排序错误、1 处内部矛盾，都会在开工时立刻绊住执行者。**
其余结构（分批口径、闸门、棘轮基线、拍板点前置）是扎实的：我逐条核对，计划里 5 项核心事实断言全部属实。

## 1. 已核实为「计划属实」的部分

| 计划断言 | 核对结果 |
|---|---|
| 分支 `v0.2.0`、B232~B236 已提交 | ✅ `git log` 确认：`a52b0428`（B232）、`632c5262`（B233）、`3b1c9ad0`（B234）、`554493c9`（B235）、`aace0c30`（B236） |
| 守卫 `PERSIST_RE` 缩进 bug 已修 | ✅ `check-package-cycles.py:230-235` 有 B232 修复注释，检查已在内层逐文件循环内 |
| R9 包级 SCC 棘轮已上线 | ✅ 实现于 `check-package-cycles.py:238-379`，且已在 `run-guards.sh:26` 注册（随 `:guards` 进 `check`） |
| A16 基线 76 / A17 基线 61 | ✅ `ArchitectureRulesTest.java` 实测：`SERVICE_BOUNDARY_BASELINE` 76 条、`SERVICE_TECH_BASELINE` 61 条 |
| A18 尚未建立 | ✅ 源文件 0 处提及 A18（B244 待建，正确） |
| 环境 `JAVA_HOME=/opt/homebrew/opt/openjdk@21` | ✅ 路径存在 |
| 13 组包级 SCC | ✅ 与本人早前独立探针结果一致（知识域 4 包、检索 8 驱动等均在列） |

补充一条计划未记录的实测值：**`A11b`（mapper 包里的仓储门面）基线是 11 条**，不是 0。B267 写"A11b 基线清零核查"，但清零工作实际落在 B263/B264，本计划没有把 11 条的消减责任分配到具体批次。

## 2. 开工前必须先处理的 3 个阻塞项

### ❗ P0-1　文档状态过期：B236 已经做完，但计划里仍是"待办"

- `git log` 显示 `aace0c30 fix(knowledge): B236 DocReader 流错误传播（K3）` 就是当前 HEAD。
- 但 §0「已完成」表只列 B232~B235；§2 阶段 1 仍把 **B236 列为待办**。
- `HANDOFF.md` 的「下一步」也仍写"§15.1 批次表，B236 起连续推进"。

**后果**：下一名执行者会从已完成的批次重做，或误判进度基线。
**处理**：把 B236 移入 §0 已完成表（补提交号 `aace0c30`）、把 §2 阶段 1 的 B236 行标记 ✅、把 HANDOFF「下一步」改为 B237，**并回填 HANDOFF §15.4 执行记录**（该项计划里也没写）。

### ❗ P0-2　B256 存在前置依赖缺失：`ToolResult` 收敛无法在 contracts 建立前做

B256 的十组改名里含「`ToolResult` 收敛到 `contract.tool`」。但：

- 现存 `common/src/main/java/com/ragagent/common/llm/ToolResult.java` 就在 `common.llm`；
- `contracts` 模块/包**尚不存在**（已核实 `contracts/` 与 `platform/` 目录都不存在），要等 **B258** 才立。

所以「收敛到 contract.tool」在 B256 无落点。
**处理**：拆开——B256 做 9 组纯改名（`ToolPolicy`→`ApprovalToolPolicy`、`TokenUsage`→`LangfuseTokenUsage`、`ModelResponse`→`ModelDetailResponse`、`ProviderRegistry`→`ModelProviderCatalog`、`FileService`→`FileUrlPort`、`WebSearchProvider`→`WebSearchProviderEntity`、`Event/EventBus`→`ApprovalEvent/ApprovalEventBus`、`ToolCall`→`AgentToolCall`、三组域前缀），**`ToolResult` 归位移到 B258 之后**（新增 B258b 或并入 B261 之后）。

### ❗ P0-3　K4（B245）与 K6 拍板点②自相矛盾

- B245 的验收写「失败清场限本代」「旧 generation 不覆盖」——**必须有 generation 隔离**才能做到。
- 但 §1.4 拍板点②把「主任务持久化（**重领/lease/generation**）」列为"较大基建，是否本期做需单独拍板"。

即：拍板点若答"不做持久化"，B245 的验收标准就无法达成。
**处理**：把 generation 从"持久化基建"里拆出来单独表态——**generation（业务代次列 + 条件更新）是 K4 的最小必要件，必须做**；lease/重领才是可延后的部分。建议把这句话直接写进 §1.4，让拍板只需回答"是否做重领"。

## 3. 需要调整但不阻塞开工的问题

### P1-1　批粒度过大，4 个批次应再拆

| 批次 | 计划范围 | 问题 |
|---|---|---|
| **B242** | 可关闭模型流：改 `LlmChatClient` 返回句柄 + `ThinkPhase` 接取消 probe + 两个装饰器队列关闭语义 + 全部调用方 | 一个接口变更牵动 4 处以上，一批做完评审面过大 |
| **B255** | 检索中立 `SearchHit`：去掉上层对 `PgVectorRetrieveRepository` 内部类型依赖 | 横跨检索全表面 |
| **B263** | knowledge 域 29 包 / 248 文件（含 SCC 拆解 + mapper/repository 分离） | 一批 ≈ 一个中型重构 |
| **B266** | im/memory/auth/storage/websearch/model + 11 个小域 | 一个批次塞 12+ 域 |

计划结尾有"批内子步：一次只搬一个包簇 → 全绿 → 提交"的说明，能缓解，但**它仍登记为一个 B 号**，意味着一次 review、一次回填、一次"完成"。
**建议**：现在就给这 4 个批次拆出子批号（如 B263-a/b/c），或明确"一个大域 = 一个 B 号，但 B 号内每包簇一个提交、逐包簇回填 §15.4"，二者取一，别停在当前含糊状态。

### P1-2　B252 会改变行为顺序，可能触发契约金片守卫，计划未提前声明

B252 把跨 store RRF 从"拼接位置当全局 rank"改为"按 store 内 rank"，**这是排序行为变更**。
仓库已有录制回放类测试（`KnowledgeSearchRecordingTest`、`HybridSearchServiceStoreGroupTest`）与 `check-contract-sync` 守卫。
计划给 B256 写了"契约金片零变化"的验收，**却没给 B252 写**。
**建议**：B252 验收改为「金片变化需显式确认，并区分'行为修正'与'契约漂移'」——它不是回归，但必须有人签字。

### P1-3　两处方案内容没有批次承接

我早前审查归纳的可读性八根因中，有两个在 R 系列里找不到落点：

- **根因④「门面即工具类」**：`KnowledgeService`（828 行、16 构造依赖）+ `KnowledgeAccessHelper` 回调门面静态工具。B263 只写了"SCC 拆解 + mapper/repository 分离主战场"，没提门面拆分。
- **根因⑦「历史注释漂移」**：典型如 `KnowledgeProcessWorker:616-629` 注释仍写 `pending_subtasks_count=1`，方法早已接收多任务计数。全系列无"清理错误注释"批次。

**建议**：B263 范围里显式加入"门面按用例拆分"，并新增一个小批次（可并入 B267 前的任意文档批）做"注释与代码一致性清查"。

### P1-4　A18 的棘轮基线会在阶段 2 长期冻结，需提前说明

A18（phase 不回引 runner / 无公共可变运行态字段）建在 B244，但真实违例要等 **B260**（engine 包边界收拢）才消。
**建议**：在 B244 验收里写明"基线全量登记、只许减；预计 B260 才出现实质下降"，否则 B244 会显得"建了规则什么都没修"。

### P1-5　本期范围与最终方案的落差没有显式声明

方案 §5.1 的终态是 **7 个 Gradle 模块**（contracts / platform / agent-engine / retrieval-engine / model-runtime / domains / boot）。
但本计划 B258~B262 全部写成**「`:common` 内立 contracts 面」**——即**纯包级、不建 Gradle 模块**（已核实 `contracts/` 目录尚不存在）。
**建议**：在本计划 §1.2 明确一句——"本期只做包边界与规则，不新增 Gradle 模块；模块提取待包边界稳定后单独立项"，避免评审者以为 B258 之后就有 contracts 模块。

### P1-6　改名与搬迁形成三次触碰

同一批文件会依次在 B256（类改名）→ B257（包改名）→ B258~B266（搬迁）中各出现一次 diff。
这是"先改名后搬迁、保证每批无行为变更"的合理代价，**但没有写出来**，执行者容易误判为重复劳动。
**建议**：在 §1.2 补一句理由声明。

## 4. 小问题（顺手可改）

1. **批次数在 §2 数不出来是 36**：§2 各阶段表合计 35 行，B235（文档批）只出现在 §0。建议在阶段 1 表补一行 B235 ✅，或在 §2 抬头注明"另含文档批 B235"。
2. **§3 闸门第 3 条冗余**：`build` 已含 `:guards`（`run-guards.sh:26` 注册了 `check-package-cycles.py`），"结构批必跑 `check-package-cycles.py`"是重复动作——保留无妨，但可注明"build 已覆盖，此为显式复核"。
3. **§4 阶段 6 验收缺一致性口径**：建议加"每批搬完跑一次启动测试 + 组件/属性/Mapper 扫描面核对清单"，与 §3 闸门呼应。
4. **A11b 基线 11 条未记录**：建议在 §0 勘察表补一行，与 A16/A17 并列。

## 5. 建议的最小修订清单

开工前至少完成这 5 处编辑，其余可按批推进时再补：

| # | 位置 | 动作 |
|---|---|---|
| 1 | §0 + §2 阶段 1 + HANDOFF 下一步/§15.4 | B236 标记已完成，进度基线改为 B237 |
| 2 | §2 B256 | 移出 `ToolResult` 收敛，注明"待 contracts 建立后" |
| 3 | §1.4 拍板点② | 拆分表态：generation 必做，lease/重领另议 |
| 4 | §2 B242/B255/B263/B266 | 拆子批或明确"一大域一号、逐包簇提交回填" |
| 5 | §1.2 / B252 验收 / B263 范围 | 分别补：范围声明（本期不建 Gradle 模块）、金片变化需签字、门面拆分与注释清查落点 |

## 6. 结论

计划的**方法论是对的**：先规则后搬迁、每批独立提交独立全绿、棘轮基线只许减、两个拍板点前置、正确性修复先失败测试——这些纪律与仓库现有守卫体系（ArchtUnit + 十一守卫 + golden）完全咬合，我核对后没有发现方向性问题。

但有 **3 个阻塞项必须先改**（B236 状态过期、B256 依赖缺失、K4/K6 矛盾）—— 它们都会在开工第一天造成返工或阻塞。改完之后，这份计划可以直接作为连续施工的指挥面。

> 本轮为只读评审，未修改计划文档、源码或脚本；如需我直接把第 5 节的 5 处修订写进计划文件，请确认。
