# 提示词统一与 agent 包归位 实施计划（F 波）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 全仓提示词的「文案源 / 装载 / 组装」三层收敛为单一权威：文案与装载沉 `:common`（L1），组装决策收 `agent/prompt`（L3）；agent 包按「门面 / 循环 / 提示词 / 预算 / 技能 / 工具」重划子包。全程**文案零字节变化、对外契约零变化**。

**Architecture:** 六期推进，先守门再收敛、先纯移动后行为：F1 装载收敛 + 资源下沉 → F2 文案消副本 → F3 守门测试（含三链路 golden 录制）→ F4 纯移动归位 → F5 组装收口（golden 必须绿）→ F6 文档回填。每期独立全绿、独立提交。

**Tech Stack:** Java 21 / Gradle 多模块（`common` → `engine` → `domains` → `boot`）/ Jackson + snakeyaml / JUnit 5。

**Spec:** 本仓证据（2026-10-12 实测）：`docs/agent-module-guide.md` §1.1/§5/§7、`docs/backend-package-map.md`、`HANDOFF.md` §2（包结构/命名）、既有计划 `docs/superpowers/plans/2026-09-30-agent-module-java-refactor.md` Task 9。

---

## 关键决策：为什么"提示词不能统一搬进 agent 包"

依赖方向 `domains → engine → common`，而 engine 的 chatpipeline 插件必须消费共享文案常量（实测引用数）：

| 共享类（现居 `common/prompt`） | engine 引用 | domains 引用 | 结论 |
|---|---|---|---|
| `AgentPromptPlaceholders` | 9 | 17 | **禁止搬进 domains**（成环） |
| `MessageAttachmentsPrompt` | 10 | 6 | **禁止搬进 domains** |
| `PromptConstants` | 3 | 10 | **禁止搬进 domains** |
| `PromptTemplateCatalog` | 0 | 6（+boot 1） | 留 L1（对外契约投影） |
| `PromptInstructions` | 0 | 8 | 留 L1（与上同居一层，保持可预测） |

因此正确切法是**两层**：

- **L1 `common/prompt` = "说什么"（文案 + 装载 + 渲染）**——唯一字节源；
- **L3 `agent/prompt` = "何时说、按什么顺序说"（组装决策）**——消费 L1。

> ⚠️ 既有计划 Task 9 曾把 `AgentPromptPlaceholders / PromptTemplateCatalog / PromptInstructions` 列入搬入 `agent/prompt/` 的清单，**照做会产生非法依赖**；本计划 F4 同步修正该清单。

### 目标架构

```
L1  common/prompt
    resources/prompts/*.yaml        11 份模板（由 domains 迁入）
    PromptSource                    唯一装载器（清单 + open/read/readAll）
    PromptIds                       模板 id 常量（消灭字符串字面量）
    PromptRenderer                  占位符 + 分段渲染（吸收 AgentPromptPlaceholders）
    PromptConstants / PromptInstructions / MessageAttachmentsPrompt   原地不动
    PromptTemplateCatalog           降级为"对外 HTTP 契约投影"，内部消费 PromptSource

L2  engine/chatpipeline             只声明"用哪些 id、什么顺序"（RagStreamSections）
    engine/modelcontext             `protocolPrompt()` 作为契约级 section 提供者登记

L3  domains/agent/prompt            唯一组装决策层
    PromptComposer                  唯一渲染入口，三链路共用
    AgentSectionSpecs               段选择 / 顺序 / 条件
    AgentPromptTexts                agent 专属文案（steer guidance、runtime contract tail）
    RuntimeContextBlock / MustUseBlock / GroundingPrompt / AgentPromptTemplates
    ConversationAssembler           原 PromptAssembly 瘦身后（只管 messages 数组）
```

三链路现状 → 目标（**必须先统一到同一渲染器**）：

| 链路 | 现在的组装点 | 段数 | 目标 |
|---|---|---|---|
| agent | `agent/AgentPrompts.buildSystemPromptSections` | 10 | `PromptComposer.render(AgentSectionSpecs.ALL, ctx)` |
| 快答 | `chatpipeline/PipelineCommon.prepareChatModel` | 3（直拼） | `PromptComposer.render(RagStreamSections.ALL, ctx)` |
| fallback | `session/SessionQaFallback` | 第三次拼同一批常量 | 同上，复用 SectionSpec |

声明式段表（F5 落地形态）：

```java
record SectionSpec(String promptId, Position position, Predicate<PromptContext> enabled) {}
enum Position { PRE_BASE, POST_BASE, PRE_PROTOCOL, POST_PROTOCOL }
```

### 文案归属规则

| 文案类型 | 代表 | 归属 | 理由 |
|---|---|---|---|
| 跨链路共用 / 契约级 | `PromptConstants`、附件指令、占位符渲染 | **L1** | engine 要用 |
| 对外契约投影 | `PromptTemplateCatalog` JSON | **L1** | 42KB 夹具是契约 |
| 链路级系统文案 | `system_prompt.yaml`、`context_template.yaml`、`agent_system_prompt.yaml` | **L1 资源** | 唯一目录，页面可配 |
| 句柄/引用协议（system-owned） | `modelcontext.Registry.protocolPrompt()` | **L2，登记为 section 提供者** | 字节即契约，两链路消费 |
| agent 运行时决策 | runtime_context、must_use、steer、grounding | **L3 `agent/prompt`** | 依赖引擎运行时状态 |
| 工具/域内微指令 | `McpCatalog`、`WebFetchTool`、`WikiPrompts`、`MemoryExtraction*` | **原地**（登记 + javadoc 标注"域内文案"） | 内聚优先 |
| 离线/评估用 | `EvaluationPromptDefaults` | 改为引用 L1 同名 id，**禁止再存正文** | 防评估口径漂移 |

**铁律：同一句话，全仓只能有一个字节源；跨链路必须下沉，单链路允许内聚但要登记。**

---

## 全局约束

1. **行为不变底线**：每任务收尾后端全量测试绿 + `spotlessCheck` 绿；**提示词文本零字节变化**（F3 录制的 golden 与现有 `GoRecording*` 实录共同钉住）。
2. **一次只动一个轴**：F1/F4 不换字（装载与移动），F2/F5 不移动文件（内容与组装）。
3. **禁止全仓文本替换修 import**：用编译器枚举（`git mv` + IDE/`--continue` 编译报错逐处修）。
4. **契约面不动**：`PromptTemplateCatalog` 的 JSON 字段顺序与键集、`domains/src/testFixtures/resources/contracts/ct-kv-get-prompt-templates.json`（42KB）、`modelcontext` 句柄协议字节、`agent/management` HTTP 面、`agent/domain` 落库类型。
5. **engine 不得依赖 domains**：三个共享提示词类（`AgentPromptPlaceholders` / `MessageAttachmentsPrompt` / `PromptConstants`）永久留 L1。
6. **命名与提交**：新类型全词命名；提交信息 `refactor(prompt): …` / `refactor(agent): …` / `test(prompt): …` / `docs(...): …`。

---

## F1（P1+P3）：模板装载收敛为 `PromptSource` 单入口 + 资源下沉 `:common`

**现状**：`agent/management/prompt_templates/` 被 7 处生产代码各读一遍、各带一份清单：
`PromptTemplateCatalog`（自带清单）· `ConversationProperties:25-29`（11 个）· `BuiltinAgentRegistry:157` · `AgentPromptTemplates:81`（1 个）· `QaWiring:396`（intent）· `SessionQaResolution:133` · `AgentConfigAssembler:302`。

**Files:**
- Create: `common/src/main/java/com/ragagent/common/prompt/PromptSource.java`
- Move: `domains/src/main/resources/agent/management/prompt_templates/*.yaml`（11 份）→ `common/src/main/resources/prompts/`
- Modify: 上列 7 个消费方（改为 `PromptSource`）+ `common/.../PromptTemplateCatalog.java`（DIR 常量）
- Modify: `domains/src/test/java/com/ragagent/arch/ClasspathResourcesTest.java:60`（资源路径表）
- Create: `common/src/test/java/com/ragagent/common/prompt/PromptSourceTest.java`（清单 ⇄ 目录双向一致）

- [ ] **Step 1**: 新增 `PromptSource`：唯一清单 `FILES`（11 项，顺序即 `FindTemplateByID` 查找序）+ `Optional<InputStream> open(String)` + `String read(String)` + `Map<String, String> readAll()`；资源根 `prompts/`。
- [ ] **Step 2**: `git mv` 资源目录到 `common/src/main/resources/prompts/`，逐处改 7 个消费方为 `PromptSource`，删除各自的文件名清单（`ConversationProperties.TEMPLATE_FILES` 等）。
- [ ] **Step 3**: 新增 `PromptSourceTest`：断言 `FILES` 与 `src/main/resources/prompts/` 实际文件双向一致（新增/改名模板不登记即红）。
- [ ] **Step 4**: 全量测试 + `spotlessCheck` 绿（重点：`ct-kv-get-prompt-templates.json` 契约用例字节不变）→ Commit `refactor(prompt): 模板装载收敛为 PromptSource 单入口 + 资源下沉 :common`。

**验证**：`./gradlew :common:test :engine:test :domains:test :boot:test`；契约用例 `ab-ct.sh`/`AcceptanceContract` 相关全绿。

## F2（P2）：文案消副本

**现状同文三处**：`EvaluationPromptDefaults`（Java 常量）与 `prompt_templates/rewrite.yaml` 逐句同文；`PluginQueryUnderstand` 内嵌改写文案；`SessionQaFallback` 第三次拼接 `SOURCE_DATA_BOUNDARY`。

**Files:**
- Modify: `domains/.../evaluation/service/EvaluationPromptDefaults.java`（改为引用 `PromptSource.read("rewrite.yaml")` 同 id，删除内嵌正文）
- Modify: `engine/.../chatpipeline/plugin/PluginQueryUnderstand.java:299-333`（内嵌文案 → 取 id）
- Modify: `domains/.../session/service/SessionQaFallback.java:110-111`（走同一渲染器/常量，不再手工 `+ "\n\n" +`）

- [ ] **Step 1**: 逐处替换为"单一来源引用"，**文本逐字不变**（`diff` 逐字节核对）。
- [ ] **Step 2**: 评估口径用例 + fallback 用例全绿。
- [ ] **Step 3**: 全量测试 + 闸门绿 → Commit `refactor(prompt): 消三处同文副本（评估/改写/fallback 引用单一来源）`。

## F3（P0）：守门测试（含三链路 golden 录制）

**Files:**
- Create: `boot/src/test/java/com/ragagent/arch/PromptOwnershipTest.java`（①只有 `PromptSource` 打开模板目录 ②新增文案字面量必须登记）
- Create: `domains/src/test/java/com/ragagent/agent/prompt/SystemPromptGoldenTest.java`（录制 agent/快答/fallback 三条链路的 system 文本 golden，F5 必须原样通过）

- [ ] **Step 1**: 写装载边界 arch 规则（ClassGraph 扫 `agent/management/prompt_templates`、`prompts/` 字面量的引用方，白名单仅 `PromptSource`）。
- [ ] **Step 2**: 录制三链路 golden（sha256 + 全文对照）；对现有 `GoRecording*` 做一次核对，确保未覆盖链路。
- [ ] **Step 3**: 全量测试绿 → Commit `test(prompt): 提示词单源守门（装载边界 / 文案登记 / 三链路 golden）`。

## F4（P5）：agent 包纯移动归位

**前置拆环三刀（`git mv` 前必须完成，否则子包反向依赖根包）：**

1. `SkillMetadata`(18 行) → `skills/`（切断 根 ⇄ `skills` 环）；
2. `TokenEstimator`(164) → `budget/`、`AgentToolNames`(38) → `compaction/`（切断 根 ⇄ `compaction` 环）；
3. `ToolRegistry` ⇄ `McpCatalog/McpExposure/McpCatalogPagination` 包私有互访**本波不解耦**——MCP 族留 `tools/` 根。

**目标树：**

```
com.ragagent.agent
├─ AgentEngine / AgentEngineException / AgentConfig / AgentConsts
├─ loop/        ThinkPhase ActPhase ObservePhase FinalizePhase ReActIteration SteerIntake SteerSink ToolImages
├─ prompt/      PromptComposer(F5 落地) AgentSectionSpecs AgentPromptTexts
│               RuntimeContextBlock MustUseBlock GroundingPrompt AgentPromptTemplates ConversationAssembler
├─ budget/      TokenEstimator AgentBudgets ContextDiagnostics ContextDebugEmitter
├─ compaction/ domain/ management/ skills/   （不动）
├─ tools/       根=框架件；mcp/ 暂缓；runtime/=横切件（SearchAuth MessageSanitizer
│               NormalizeToolCallId ThinkBlocks ThinkStreamSplitter JsonRepair PythonSyntax）
└─ support/     → 删除（空壳）
```

**Files:** agent 根包 17 文件 + `support/`；同步：`domains/src/test/java/com/ragagent/agent/**`（55）、`domains/src/testFixtures/java/com/ragagent/agent/**`（6）、`boot/src/test/java/com/ragagent/agent/**`（3，跨模块 split package）。

- [ ] **Step 1**: 拆环三刀（纯移动 + import 修复，编译驱动）。
- [ ] **Step 2**: `git mv` 归位（仅移动，无编辑）；`support/` 删除并清理 `package-info` 过时表述。
- [ ] **Step 3**: 同步测试 package 声明 + `AgentConfigKeyUsageTest:44-51` 路径白名单 + `ArchitectureRulesTest:306-307` FQCN 白名单 + `ArchitectureRulesTest` 同包夹具。
- [ ] **Step 4**: 每个 agent/agentm 子包补 `package-info.java`；`git grep '{@link 旧名}'` 清死链。
- [ ] **Step 5**: 全量测试 + `spotlessCheck` 绿 → Commit `refactor(agent): 包结构归位（提示词/循环/预算/工具四簇 + 拆环三刀）`。

> 同步修正既有计划 Task 9 的清单：`AgentPromptPlaceholders / PromptTemplateCatalog / PromptInstructions` **不搬**（留 L1）。

## F5（P4）：组装收口（PromptComposer）

**Files:**
- Create: `domains/.../agent/prompt/PromptComposer.java`、`AgentSectionSpecs.java`、`AgentPromptTexts.java`
- Modify: `agent/AgentPrompts.java`（拆为"文案常量 + 值类型 record"，值类型独立成文件）、`agent/PromptAssembly.java` → `ConversationAssembler`（只管 messages 数组）
- Modify: `engine/.../chatpipeline/PipelineCommon.java`（改为消费 `RagStreamSections`）、`engine/.../chatpipeline/support/ReferencesSupport.java:46`
- Modify: `domains/.../session/service/SessionQaFallback.java`（第三链路并入）
- Modify: `modelcontext/Registry.protocolPrompt()` 的注入方式（登记为 section 提供者，不再各自拼接）

- [ ] **Step 1**: 落 `SectionSpec` + `PromptComposer`（输入段表 + 上下文，输出 system 文本），先用**等价改造**：agent 链路 10 段按现顺序声明。
- [ ] **Step 2**: 快答链路（3 段）与 fallback 链路并入；删除两处手工拼接。
- [ ] **Step 3**: `AgentPrompts` 拆分（文案 / 值类型），`AgentEngine` 公开签名同步。
- [ ] **Step 4**: **F3 golden 必须逐字节通过**；全量测试 + 闸门绿 → Commit `refactor(prompt): 组装收口 PromptComposer（三链路单入口，文本零变化）`。

## F6（P6）：文档回填与验收

- [ ] **Step 1**: 更新 `docs/agent-module-guide.md`（§1.1 子包表、§5 改哪里、§7 坑）、`docs/backend-package-map.md`、`docs/README.md` 数据口径。
- [ ] **Step 2**: 既有计划 Task 9 勾选 + 清单修正记录；HANDOFF 追加执行记录。
- [ ] **Step 3**: 验收核对表（见下）→ Commit `docs(handoff): 提示词统一（F 波）完成回填`。

---

## Review Focus（契约暗礁，逐期必查）

1. **`ct-kv-get-prompt-templates.json`（42KB）必须字节不变**：F1 搬资源、F5 改装载路径后各跑一次契约用例。
2. **实录测试禁手改**：`GoRecording*`（根 / tools 45A-C / modelcontext 46A）+ `*RecordingTest` 逐字节比对；F4 搬包时**不得触碰 `modelcontext`**（句柄协议字节即契约）。
3. **`AgentConfigKeyUsageTest:44-51` 路径白名单**：F4 任何路径变化都要同步，否则该守卫静默扩大/缩小判定面。
4. **boot 侧 split package**：`boot/src/test/java/com/ragagent/{agent,agent.management}` 与主树同包，F4 必须同一提交内同步。
5. **engine ⇄ domains 方向**：F5 若把任何 engine 侧消费的文案/渲染件移入 domains，即为违例（用 `ArchitectureRulesTest` A 波棘轮拦）。
6. **同批不混前端**：本计划不触任何前端可见契约，无需 `vue-tsc`/`npm test`。

## 验收标准（量化）

- 打开模板目录的生产代码：**7 → 1**；文件名清单：**3 套 → 1 份**
- 同文副本：**3 → 0**；system 提示词组装入口：**3 → 1**
- agent 根包文件：**23 → ≤10**；`tools/` 根：**40 → ≤25**；空壳 `support/` 删除
- 新增守门测试 ≥3（装载边界 / 文案登记 / 三链路 golden）；全量测试 + `spotlessCheck` 绿；契约夹具字节零变化

## 明确不在本计划内（防止顺手扩散）

1. **不改任何提示词文案**——改字需独立提交 + 契约重录 + golden 重录。
2. **不动 `agent/management` HTTP 面与 `agent/domain` 落库类型**——属既有计划 C 波。
3. **不解耦 `ToolRegistry ⇄ Mcp*` 包私有耦合**——MCP 族暂留 `tools/` 根，后续以 `ToolCatalogPort` 接口另立计划。
4. **不动 `modelcontext` 句柄协议的字节**——F5 只改"注入方式"。
5. **不引入新 Gradle 模块**——资源放 `:common`，成本最低。
