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

- [x] **Step 1**: 新增 `PromptSource`：唯一清单 `files()`（11 项，顺序即 `FindTemplateByID` 查找序）+ `InputStream open(String)`（缺失 → null，与 ClassLoader 语义一致）+ `String read(String)`（UTF-8，缺失 → ""）；资源根 `prompts/`。
- [x] **Step 2**: `git mv` 资源目录到 `common/src/main/resources/prompts/`，逐处改 7 个消费方为 `PromptSource`，删除各自清单（`ConversationProperties.TEMPLATE_FILES`、`BuiltinAgentRegistry.TEMPLATE_FILES`、`PromptTemplateCatalog.DIR`）。
- [x] **Step 3**: 新增 `PromptSourceTest`：清单 ⇄ 目录双向一致、每份可读非空、**模板 id 跨文件唯一**（保证查找序无关）。
- [x] **Step 4**: 编译 + `spotlessCheck` + F1 定向用例（`AgentPromptsTest` / `BuiltinAgentRegistry*` / `AgentConfigAssembler*` / `SessionQaResolution*` / `AgentConfigKeyUsageTest` / `ClasspathResourcesTest`）全绿；**`TenantCatalogContractTest`（42KB `ct-kv-get-prompt-templates.json`）字节不变通过** → 提交见执行记录。

**执行记录（2026-10-12）**
- API 落地为 `files()` / `open()` / `read()`（未做 `readAll()`：现有消费方都是逐文件解析 snakeyaml，多一个聚合方法反而无消费者）。
- 统一清单后**行为等价**已证：11 份模板的 `id` 跨文件唯一（`PromptSourceTest` 长期守门），因此 `ConversationProperties`（11 项序）与 `BuiltinAgentRegistry`（原为另一顺序）合并到同一清单不改变任何 `FindTemplateByID` 取值。
- 域侧清单同步：`ClasspathResourcesTest` 的 11 条资源路径改为 `prompts/*.yaml`（消费方标注 `common.prompt.PromptSource`）；`docs/agent-module-guide.md` §5 口径同步。
- ⚠️ **基线曾红（与本批无关，已用 `git stash` 对照复现）**：`EngineRecordingTest`(5) + `PipelineLifecycleRecordingTest`(3) + `ModelContextRecordingTest`(1) 共 9 个实录断言红——根因是并行会话提交 `3e4dd368` 从 `Registry.RESOURCE_HANDLE_PROTOCOL_PROMPT` 删掉了 `sandbox:<file name>` 下载约定那一行，实录仍钉旧文案。修法二选一（需产品决策）：**重录实录** 或 **恢复该行**。

**基线同步（2026-10-12，用户决策：重录）**
- **现实约束**：仓库内没有可用的重录程序（`GoRecording*` 的录制源是已下线的 Go 服务，见 `docs/handoff/records/batch-records.md:501`「无法重录」；`-Dcontract.refresh=true` 只管 `contracts/*.json`）⇒ 实操 = **按当前实现更新实录常量**，属显式基线变更，须在 F6 回填 HANDOFF 留痕。
- **改动**：`GoRecording46A`（2 处）/ `GoRecording46B`（5 处）/ `GoRecording46C`（7 处）删除被删子串 `- For downloadable deliverables generated in the session workspace, use sandbox:<file name>; `；另 `GoRecording46C` 的派生字段 `prompt_len` 1494 → 1401（差值 93 = 该子串长度，与断言差值逐字吻合）。
- **结果**：9 个实录用例全绿；`:common:test :engine:test :domains:test :boot:test` 全量 BUILD SUCCESSFUL。
- **遗留待决**：`Registry.java` 删行后该段变成小写开头、失去列表项前缀（`never reuse or invent a resource handle. …`），模型可见文案观感问题——若调整需再同步一次上述三份实录。

**验证**：`./gradlew --no-daemon :common:test :engine:test :domains:test :boot:test`；契约用例 `TenantCatalogContractTest` 全绿。（注意：跑测试前 `unset SSRF_WHITELIST_EXTRA`，否则 `ct-kv-parser-put-ssrf.json` 会因 127.0.0.1 被放行而红。）

## F2（P2）：文案消副本

**盘点修正（2026-10-12 实测，覆盖方案初稿的判断）**：

| 初稿说法 | 实测结论 |
|---|---|
| `EvaluationPromptDefaults` 与 yaml 逐句同文 | ✅ 成立，且是 **4 个常量**：`SUMMARY_PROMPT`≡`system_prompt.yaml#default_kb.content`、`SUMMARY_CONTEXT_TEMPLATE`≡`context_template.yaml#default_context.content`、`REWRITE_PROMPT_SYSTEM/USER`≡`rewrite.yaml#default_rewrite.content/user` |
| `PluginQueryUnderstand:299-333` 内嵌改写文案 | ❌ **误报**：该处只做占位符渲染，文案来自 `PipelineConfig`（YAML 装载）/会话覆写，无副本 |
| `EvaluationPromptDefaults.FALLBACK_RESPONSE`、`SUMMARY_NO_MATCH_PREFIX` 是模板副本 | ❌ **不是**：仓库内无同文模板，两者是评估专用语义（固定兜底句 / 摘要调用约定），保留常量 |
| `SessionQaFallback:110-111` 第三次拼接 boundary | ✅ 成立，但那是**组装重复**而非文案重复 → 并入 **F5** 一次做（引入 `PromptComposer` 时消掉），F2 不动它以免制造二次搬迁 |

**Files:**
- Create: `common/src/main/java/com/ragagent/common/prompt/PromptTemplates.java`（按 id 取 content/user，L1 唯一 YAML 解析点，按文件缓存）
- Modify: `domains/src/main/java/com/ragagent/evaluation/service/EvaluationPromptDefaults.java`（4 个常量改为模板取值）
- Create: `domains/src/test/java/com/ragagent/evaluation/service/EvaluationPromptDefaultsTest.java`（非空守门：模板改名/缺失即红）

- [x] **Step 1**: 逐处替换为"单一来源引用"（文本逐字不变，已用 pyyaml 逐字比对确认同文）。
- [x] **Step 2**: `EvaluationPromptDefaultsTest` + `EvaluationContractTest`（golden `ev-post.json`）全绿 → 响应体字节不变。
- [x] **Step 3**: `:common:test` + `spotlessCheck` 绿 → 提交见执行记录。

**执行记录（2026-10-12）**：`git rev-parse` 见提交 `refactor(prompt): 评估默认值改取单一模板源`。验证：`:common:test`、`:domains:test --tests '*EvaluationPromptDefaultsTest*'`、`:boot:test --tests '*EvaluationContractTest*'`、`:common/:domains:spotlessCheck` 全绿。

## F3（P0）：守门测试

**落点调整（2026-10-12 执行时定）**：初稿把守卫放 `boot` 的 ClassGraph arch 层、并另写一条"三链路 golden"。实际：

- 归属规则的判定依据是**源码文本**（不是类图），放 ClassGraph 反而绕 → 落在 `domains/.../arch/PromptOwnershipTest`（与既有 `ClasspathResourcesTest` 同层，且沿用其"源码扫描 + 清单"手法；跨模块根用 `Path.of("..")` 定位仓库根）。
- **不另造三链路 golden**：既有实录已经逐字节钉住三条链路的 messages（agent → `GoRecording46B`/`EngineRecordingTest`；快答 → `GoRecording46C`/`PipelineLifecycleRecordingTest`；句柄协议 → `GoRecording46A`/`ModelContextRecordingTest`），另有 `EvaluationContractTest` 钉评估响应体。再造一份 golden = 重复安全网，故 F5 的保护网就是这些既有实录。

**Files:**
- Create: `domains/src/test/java/com/ragagent/arch/PromptOwnershipTest.java`（A1 归属 / A2 残迹 / A3 装载方式）
- Modify: `common/.../prompt/PromptSource.java`（11 个模板文件名升为 public 常量，成为全仓唯一字面量来源）
- Modify: 6 处消费方改引常量（`PromptTemplateCatalog` / `AgentPromptTemplates` / `EvaluationPromptDefaults` / `QaWiring` / `AgentConfigAssembler` / `QaChatManageOverrides`）

- [x] **Step 1**: A1 = 11 个模板文件名字面量只允许出现在 `common/prompt`；A2 = 旧目录 `agent/management/prompt_templates` 零残留；A3 = 禁止任何自拼模板路径的 `getResourceAsStream`（只能走 `PromptSource`）。守门测试自身用 `isSelf` 跳过（其说明文字必然含被禁串）。
- [x] **Step 2**: 严格 A1 的前提是"消费方不再自写文件名" → 已把 11 个名字升为 `PromptSource` 常量并改掉 6 个消费点（含 `AGENT_SYSTEM_PROMPT_FILE` 本地常量删除）。
- [x] **Step 3**: 全量后端测试 + `spotlessCheck` 绿 → 提交见执行记录。

**执行记录（2026-10-12）**
- 验证：`PromptOwnershipTest`（A1/A2/A3）+ `ClasspathResourcesTest` + `EvaluationPromptDefaultsTest` + `:common:test` 全绿；`:common:test :engine:test :domains:test :boot:test` + 三模块 `spotlessCheck` 全绿（domains 2,890 用例）。
- 已知偶发（与本批无关）：`EmbedRateLimiterTest` 的时间窗用例会偶发单条红（连跑两次可复现"不同用例失败 / 全绿"），属限流时间敏感，非确定性失败。

## F4（P5）：agent 包纯移动归位

> ⚠️ **执行前盘点把本波降级了（2026-10-12，very thorough 盘点，结论有据）**：
>
> 1. **`loop/` 归位不可行（最大降级点）**：8 个段协作者（`ThinkPhase`/`ActPhase`/`ObservePhase`/`FinalizePhase`/`ReActIteration`/`SteerIntake`/`ContextDebugEmitter`/`PromptAssembly`）**全部是 `AgentEngine` 包内状态的直读者/写者**——实测涉及 33 个字段中约 21 个 + 约 17 个包内方法 + 4 个包内嵌套类型（`MsgRef`/`IterOutcome`/`WindowOutcome`/`TrimOutcome`）。搬进子包至少要把这 ~38 处改 `public`（封装代价 + 触碰 59 个同包测试），**"纯移动、零语义变化"不成立** → 应另立一波「engine seam 打开」并单独评审。
> 2. **`AgentEngine` 必须留根**：`boot/src/test/java/com/ragagent/agent/EngineRecordingTest.java` 与 `domains/src/testFixtures/java/com/ragagent/agent/Engine46bStubSupport.java` 以**跨模块 split package** 直取 `*ForTest` seam（29 处），`AgentEngine` 一动这两个工件必须跟着改包。
> 3. **`SkillMetadata → skills/` 降级**：`skills/Skill.java` 内已有同名嵌套 `Skill.SkillMetadata`，搬过去同包同名易混，收益（解根⇄skills 环）在本波并不需要 → 留根。
> 4. **`tools/` 横切 8 件**（`SearchAuth`/`MessageSanitizer`/`NormalizeToolCallId`/`ThinkBlocks`/`ThinkStreamSplitter`/`JsonRepair`/`PythonSyntax`/`ToolResultPersist`）实测全部 `public final`、被跨包以 public 调用，**与 `ToolRegistry`/`Mcp*` 零耦合** → 可整体搬 `tools/runtime/`，但它属"tools 包治理"而非根包治理，**拆成独立一批**做。
> 5. 口径修正：根包实际是 **22 类（23 文件含 package-info）**，不是 17；原定验收"根包 23 → ≤10"因 loop/ 降级不适用（见验收标准的新口径）。

**本波实际执行（保守版，零语义损失）：**

| 动作 | 内容 |
|---|---|
| 新建 `budget/` | `TokenEstimator`、`ContextDiagnostics`、`AgentBudgets`（三者已 public，零可见性改动） |
| 新建 `prompt/` | `GroundingPrompt`、`AgentPromptTemplates`（agent 侧提示词构件；`AgentPrompts`/`PromptAssembly` 留给 F5 一步到位，避免同文件改两次） |
| 删除 `support/` | 空壳（仅 package-info） |
| 测试随类走 | `TokenEstimatorTest`、`ContextDiagnosticsTest` → `agent/budget/` |
| import 同步 | 主代码 10 文件 + 测试 4 文件（编译驱动枚举，非全仓替换）；`boot/EngineRecordingTest` 补 1 处 |
| package-info | 根包改写（子包清单 + seam 降级说明）；新增 `budget/`、`prompt/` 两份 |

- [x] **Step 1**: 三簇搬迁（`git mv` + 包声明 + 编译驱动的 import 修复）。
- [x] **Step 2**: `support/` 删除，根包 `package-info` 过时表述清理。
- [x] **Step 3**: 测试同步（2 个测试随类搬包；4 个测试补 import）；`AgentConfigKeyUsageTest` 路径白名单与 `ArchitectureRulesTest` FQCN 白名单**本波无需改**（`ArchitectureRulesTest:306-307` 指向 `skills.SkillCatalogService` 与 `tools.data.AnalysisDuckDbJdbc`，与根包搬迁无关——Task 9 把它列为本波同步项属误列）。
- [x] **Step 4**: `budget/`、`prompt/` 补 `package-info`；根包 `package-info` 增 seam 说明。
- [x] **Step 5**: 全量测试 + `spotlessCheck` 绿 → 提交见执行记录。

**执行记录（2026-10-12）**
- 第一刀：根包 22 类 → **17 类**（含 package-info 18 文件）；新增 `budget/`(3) 与 `prompt/`(2)。
- **第二刀（独立提交）**：`tools/` 横切 8 件 → **`tools/runtime/`**（`SearchAuth` / `ToolResultPersist` / `MessageSanitizer` / `NormalizeToolCallId` / `ThinkBlocks` / `ThinkStreamSplitter` / `JsonRepair` / `PythonSyntax`）；**5 个随类实录测试同步搬包**（`ScopeAuthRecordingTest` / `ThinkStreamSplitterRecordingTest` / `JsonRepairRecordingTest` / `StripThinkRecordingTest` / `NormalizeToolCallIdRecordingTest`）；新增 `tools/runtime/package-info`。盘点已证这 8 件全 `public`、与 `ToolRegistry`/MCP 族零耦合；`tools/` 根由 40 文件降到 32。
- 验证：`:common / :engine / :domains / :boot` 全量测试 + 三模块 `spotlessCheck` 全绿（`spotlessApply` 只动 import 顺序）；搬走的 5 个实录定向跑过确认"真的在跑"。
- 未做（明确留待后续）：`loop/` 归位（需先开 seam，独立评审）、`SkillMetadata` 归位（同名易混，收益不成立）、MCP 族搬 `tools/mcp/`（需先解 `ToolRegistry` 包私有耦合）、`AgentPrompts` 值类型外提 + `PromptAssembly` 改名（F5b-2）。

> 同步修正既有计划 Task 9 的清单：① `AgentPromptPlaceholders / PromptTemplateCatalog / PromptInstructions` **不搬**（留 L1，engine 引用会成环）；② `loop/` 归位降级为独立评审项；③ `SkillMetadata` 留根。

## F5（P4）：组装收口（PromptComposer）

**Files:**
- Create: `domains/.../agent/prompt/PromptComposer.java`、`AgentSectionSpecs.java`、`AgentPromptTexts.java`
- Modify: `agent/AgentPrompts.java`（拆为"文案常量 + 值类型 record"，值类型独立成文件）、`agent/PromptAssembly.java` → `ConversationAssembler`（只管 messages 数组）
- Modify: `engine/.../chatpipeline/PipelineCommon.java`（改为消费 `RagStreamSections`）、`engine/.../chatpipeline/support/ReferencesSupport.java:46`
- Modify: `domains/.../session/service/SessionQaFallback.java`（第三链路并入）
- Modify: `modelcontext/Registry.protocolPrompt()` 的注入方式（登记为 section 提供者，不再各自拼接）

**执行拆成两步（2026-10-12 执行时拆分——"拼法"与"段表"分开做，各自可验证）：**

- [x] **F5a（已做）**：落 L1 `common.prompt.PromptComposer`，把"怎么拼"收成唯一实现：
  - `joinSections`（逐段 trim / 跳空段 / `\n\n` 连接）← 取代 `AgentPrompts.renderSystemPromptSections` 的内联循环；
  - `SOURCES_AND_OUTPUT_SUFFIX`（源数据边界 + 有据作答尾巴，含前导空行）← 取代 `PipelineCommon` 与 `SessionQaFallback` 各自的手拼；
  - `appendProtocol`（右侧裁剪 + 协议段直连）← 取代 `ReferencesSupport` 与 `SessionQaFallback` 各自的"裁剪+拼接"；
  - `rightTrim`：**两处逐字节重复**的私有实现（`ReferencesSupport.trimRightWhitespace`、`SessionQaFallback.trailTrim`）已删除。
  - 新增 `PromptComposerTest` 钉规则语义（跳空段 / 右裁 / 尾巴字节形状）。
- [x] **F5b-1（已做，2026-10-12）**：**agent 段表声明式化**——`AgentPrompts` 内新增 `SectionSpec`（id + 内容提供者 + 启用条件，缺省恒启用）与只读 `SectionContext`（模板决策与派生开关在构造时冻结），10 段收成一张 `SECTION_SPECS` 表；`buildSystemPromptSections` 退化为"遍历表 → 条件过滤 → 产出段"的 5 行循环。**等价性证明**：`AgentPromptsTest`（含段顺序/内容的 golden 断言）+ `SkillPinnedInstructionsTest` + `EngineRecordingTest`(5) 全绿 → 段落 id/顺序/条件/文本逐字节不变。
  - 途中修掉一个编译约束：段表是静态字段初始化，直接引用类尾的 `STEER_GUIDANCE` 会触发 `illegal forward reference` → 经 `steeringGuidance()` 方法取文本（已注明原因）。
  - 快答 / fallback 的"段选择"仍在各自链路（`PipelineCommon` / `SessionQaFallback`），但**拼接原语已统一在 L1 `PromptComposer`**（F5a）——跨模块共享的表只能落 L1（engine 不能依赖 domains），故不把三条链路的段表物理合并，保持"L1 定拼法、各链路定选段"。
- [x] **F5b-2（已做，2026-10-12）**：`AgentPrompts` 值类型外提 + `PromptAssembly` 职责收窄。
  - 已外提 **8 个值类型**到 `agent/prompt/`：`RecentDocInfo` / `SelectedDocumentInfo` / `PinnedMCPServiceInfo` / `PinnedSkillInfo` / `PinnedSkillInstructions` / `KnowledgeBaseInfo` / `PlaceholderDefinition` / `SystemPromptSection`（一类型一文件；`KnowledgeBaseInfo` 内含 `RecentDocInfo` 同包引用）。
  - 引用面改写（约 140 处）用**脚本 + 编译器枚举**完成：`AgentPrompts.X` → 裸名 + 补 import；途中发现 **IMA 连接器有同名嵌套 `KnowledgeBaseInfo`**（`ImaApiTypes:130`），脚本误加的 import 已回退（3 文件），避免同名遮蔽。
  - `PromptAssembly` → **`ConversationAssembler`**：职责收窄为"会话消息装配"（系统提示词选段归段表、拼法归 L1 `PromptComposer`）；类 javadoc 已注明更名理由与边界。
  - **唯一留根**：`BuildSystemPromptOptions` —— 它引用根包 `SkillMetadata`，搬出会形成 `prompt → root` 包边；待 `SkillMetadata` 归位（受同名 `Skill.SkillMetadata` 阻塞）后一并搬。
  - 验证：`EngineRecordingTest`(5) + `AgentPromptsTest` + `SkillPinnedInstructionsTest` 全绿（**文本与消息数组逐字节不变**）+ 全量后端测试 + `spotlessCheck` 绿。
  - 数据口径：agent 域 179 文件 / 30,812 行；根包 18 文件（17 类）；`prompt/` 11 文件 / 331 行。

**F5a 执行记录（2026-10-12）**：主代码 4 文件（`AgentPrompts` / `PipelineCommon` / `ReferencesSupport` / `SessionQaFallback`）+ L1 一类一测。验证：`EngineRecordingTest`(5) + `PipelineLifecycleRecordingTest`(3) + `ModelContextRecordingTest`(1) 全绿 —— **三链路 system 文本逐字节不变**；全量后端测试 + `spotlessCheck` 绿。

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
- agent 根包文件：**23 → ≤10**（**口径修正**：loop/ 降级后本波实际 **22 类 → 17 类**；≤10 需先完成「engine seam 打开」独立波）；空壳 `support/` 删除；`tools/runtime/` 8 件与 `tools/` 根瘦身归后续批
- 新增守门测试 ≥3（装载边界 / 文案登记 / 三链路 golden）；全量测试 + `spotlessCheck` 绿；契约夹具字节零变化

## 明确不在本计划内（防止顺手扩散）

1. **不改任何提示词文案**——改字需独立提交 + 契约重录 + golden 重录。
2. **不动 `agent/management` HTTP 面与 `agent/domain` 落库类型**——属既有计划 C 波。
3. **不解耦 `ToolRegistry ⇄ Mcp*` 包私有耦合**——MCP 族暂留 `tools/` 根，后续以 `ToolCatalogPort` 接口另立计划。
4. **不动 `modelcontext` 句柄协议的字节**——F5 只改"注入方式"。
5. **不引入新 Gradle 模块**——资源放 `:common`，成本最低。
