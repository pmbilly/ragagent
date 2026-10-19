# ragagent 交接文档（v0.3.0 · 载体纠偏 + 三 runtime 版）

> **内部迁移作战文档（非新人入口）**：新人请从仓库根 `README.md` → `docs/README.md` 开始。
> 本文件记录重构/迁移的过程与决策，路径与读数随批次变化，不是当前架构说明。

> **版本谱系**：v0.1.0 归档 `docs/internal/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md`；**v0.2.0 全文归档 `docs/internal/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md`**（含 B232~B267 全部逐批记录、v0.2.0 结构规范与 C/G1/D/E 系列过程记录）。
> 本版 2026-10-16 生效：**只留活信息**；历史一概在归档，追溯用 `git log --grep`。
> 工作分支：`v0.4.0`。

## ⭐ 接手须知（5 分钟）

1. **开工顺序**：读 §4 两条判定标准（2026-10-16 拍板）→ §5 执行方案（详情＝`docs/internal/plans/java-remediation-implementation-plan-v2-2026-10-16.md`）→ §9 方法论 → §10 纪律 → 干活 → 回填 §12。
2. **基线核对**：先跑 §6 的全量闸门，确认 `BUILD SUCCESSFUL` + 各模块测试条数（**numbers 守恒**；只信汇总数字，`boot`/`platform` 条数缺失＝静默少跑）。
3. **待办与读数都会过期**：宣称"未完成/待办"或据此动手前先实测（`git log` / 守卫 / 脚本输出）；**以守卫为准**。
4. **提交**：批次号 `(P#)`/`(R#)`，写明验证方式；**推送等用户发话**（历史纪律，一字不改）。

## 0. 总目标与当前阶段

- **终态（方案 §5.1）**：7 个 Gradle 模块 —— contract / platform / agent-engine / retrieval-engine / model-runtime / domains / boot；单进程分层单体；跨模块只经 contract。
- **现状（2026-10-17）**：**7 模块** —— `:contract` · `:platform` · `:model-runtime` · `:retrieval-engine` · `:agent-engine` · `:domains` · `:boot`。`:common` 与 `:engine` 均已解散。
- **当前阶段（v0.3.0）**：**载体纠偏（P1~P5）+ 三 runtime（R1~R6）** ⇒ 计划见 §5。

## 1. 仓库身份与分支

- 远程 `origin` = `https://github.com/pmbilly/ragagent.git`（公开）；主分支 `main`（停 `ef89e4ab`，含 v0.2.0 结项 + 缺陷修复）。
- 分支谱系：`v0.1.0`（归档）· `v0.2.0`（R 系列 36 批，已合并 main 并推送）· `v0.3.0`（P1~P5 两模块纠偏）· `v0.4.0`（R1~R6 三 runtime，已合并 main）· **`v0.5.0`（当前工作分支；2026-10-18 自 `main` 切出，可读性专项 L1~L22）**。
- 网络实录：本机↔github 的 https 不稳；用 `git -c http.version=HTTP/1.1 push …` + 重试。
- 兼容边界：**系统未上线、无历史数据包袱、前端适配后端**（知识库模块三约束，全线适用）。

## 2. 已定决策（勿再重新讨论）

1. **保留分层单体，渐进整改**；不整体重写、不拆微服务（整改方案 §0/§13）。
2. **域内命名**：`controller / service / mapper / repository / domain / dto / config`（方案 §6）；`agenttools`/`agentmanagement` 命名（§7，B257 已执行）。
3. **模块名 = 顶层包名**：契约面取**单数 `contract`**（不取方案字面的 `contracts`；理由见 `contract/build.gradle.kts` 头注释，已登记）。
4. **三个 runtime 平级、互不引用实现**；跨模块协作经 contract、由 boot 注入（方案 §5.2）。
5. **域间只经契约（R10）**：①可清零档 = **0** ✅；②属主边 2 条（`auth→tenant`、`system→auth`）**登记维持**；③同族 5 条（agent 家族）**冻结**。
6. **线格式（1,349 金片）逐字节不变**；协议变更属**产品决策**、不夹带（`check-contract-sync` 是执行面）。
7. **agent-engine 结论（2026-10-16 架构复核）**：**做**——但必须"**先建 `contract.agent` 契约面 + 工具 SPI，再搬 `domains/agent` 实现**"；不设计边界直接搬 = 把族内协作变成跨模块直连（比现状更差）。
8. **platform 的"落点法则"作废**：v0.2.0 曾以"去不了契约的跨域件落 platform"补缺口；v0.3.0 起按 §4 判定标准——platform **不放业务形状**。
9. **R1 余项判定（2026-10-16 拍板）**：`contract.retrieval` 缺口 **并入 R3**（域侧直注的是实现类，索引面签名穿透 `Embedder`{模型族}与可变 setter `IndexInfo`）；`contract.agent` 6 件 **并入 R5**（今日跨域消费者仅 boot 一处测试）；`contract.execution` **判"不建"**（取消/元数据已存在于 `contract.tool.ToolCancellation`/`ToolExecContext`，再建即三套取消语义并存）。

## 3. 三条红线（本版口径）

1. **载体红线**：不得上提持久化实体（`@TableName`/TypeHandler/可变 setter）到 L1；contract 不放**内部件/持久化形状**；platform 不放**业务形状**。
2. **线格式红线**：金片签名 1,349 逐字节不变；确需变更 ⇒ 显式签字 + 前端确认（产品决策）。
3. **纪律红线**：**不刷新 baseline 掩盖违例**；**守卫必须能红**（新增/改守卫必配红态探针）；**闸门退出码显式取用**（任何 `| head` 不得代替）。

## 4. 两条判定标准（2026-10-16 拍板 ★ 本版核心）

**contract**：跨域词汇表的**唯一一处**；零框架依赖；**每一个类都能回答"谁在跨域消费我"**。

**platform**：只剩**技术适配**（框架、HTTP、存储、提示词、追踪、事件基建）；**任何业务形状出现即红灯**。

口径：
- 判定标准是**原则**，优先于方案 §8.1/§8.2 的枚举清单（方案原文："只公布跨模块确有消费者的 API/SPI"；"不为 DDD 完整编造空层"）。
- **跨域端口/视图面扩容 = 追认**（A③ 域间契约化的必然产物）；**platform 业务形状 = 归正**（去框架化进 contract / 中性化 / 归域 / 归 agent-engine，见实施计划 v2 §1.2）。
- 验收工具：`check-contract-purity.py`（C1/C2/C3 全零）+ **C8**（platform 内 `*View/*Port/*Request/*Response/*Payload/*Facts` 命名红灯，例外须登记理由）。

## 5. 执行方案

### 5.0 当前阶段：v0.5.0「可读性专项」（2026-10-18 起）

> 完整方案：`docs/internal/plans/readability-remediation-v0.5.0.md`。总目标：一条问答链路必读文件数 20 → ≤10；迁移叙事 769 → 0；控制器 catch 151 → 0；`package-info` 覆盖 48% → 100%。

**边界**：不重写 RAG 管线 / `StreamManager` / `TenantContext`；不改 DB schema；不为架构形式拆模块；前端大文件单独立项。**允许**：删类改名、合并包、拆 `:knowledge`、一次清掉 Go 兼容语义。

| 批 | 内容 | 状态 |
|---|---|---|
| **L1** | 迁移叙事清零（退役/旧/历史/备案/已知差异/Go，共 769 处） | ✅ 基线 196 → **0**（守卫硬零） |
| **L2** | 行号与 Go 语法锚点（`L141-144`/`uuid.New()`/旧模块名 `common`）+ 构建脚本批次号 | ✅ 16 处注释 + 8 个构建脚本 |
| **L3** | 修 7 处文档失真（README ×4 + 3 个类注释） | ✅ `check-doc-truth` 上线 |
| **L4** | 补 156 个 `package-info`（职责/依赖/从哪读起） | ✅ 覆盖率 100%（322 包） |
| **L5** | 注释装饰清理：491 行纯装饰分隔线删除、216 行统一格式 | ✅ `check-comment-decor` 硬零 |
| **L6** | "未实现/暂缺"收敛到 `docs/known-gaps.md` | ✅ 18 项 + 9 处指针 |
| **L7** | 死代码删除（空包、`@Deprecated`、`platform/session`；`platform/tool` 归 L22） | ✅ `@Deprecated` 归零 |
| **L8** | 日志分级：404/跳过 → debug、配置缺失 → info（不设总数门） | ✅ 116 处降级（893→777） |
| **L9** | 删纯转发的控制器 catch（12 处），统一交 `GlobalExceptionHandler` | ✅ 其余带业务语义或金片钉住，保留 |
| **L10** | 错误形态统一为 `{code,message,data}`（**行为变更**） | ✅ 删 2 个异常类 + RawJsonError；金片 8 条 |
| **L11** | 异常语义守卫扩到 domains 全量 | 待做 |
| **L12** | 数据访问定规 + controller 纯度守卫 | ✅ controller 直连 Mapper 实为 0（原 22 是 ObjectMapper 误报） |
| **L13** | 4 个核心实体 → `XxxView` record | ⏸ 评估后不做：收益（架构纯粹性）小于成本（157 金片 + 序列化细节）与可读性损失（多一层跳转） |
| **L14** | 事务模板收敛 + 队列 worker 补事务边界说明 | ✅ 2 类合并为 `platform.persistence.TxTemplate` |
| **L15** | `ChatManage` 拆分 | ⏸ 评估后不做：127 方法 = 60 字段 × 2，是 JavaBean 固有成本；拆分只把复杂度搬到两个类，插件链需持两个引用 |
| **L16** | `executeQA` 200 行 → 7 个具名步骤 | ✅ 主方法 ~30 行 |
| **L17** | 插件表自动生成 + `--check` 守卫 | ✅ `pipeline-map.md`（14 事件 / 17 插件） |
| **L18** | 插件契约三行（读/写/失败） | ✅ 写在 `Plugin` 接口（统一规则不逐个重复） |
| **L19** | 拆 `:knowledge`（241 文件） | ⏪ **已回退**（L23）：只换来独立编译，知识库代码仍分散 4 处 |
| **L20** | `modelcontext` 下沉 `contract` | ⏸ 不可行：入口类 `Registry` 依赖 Jackson，搬进 contract 破坏「纯 JDK」红线 |
| **L21** | `platform` 业务残留归位 | ⏸ 不可行：9 类被 6 个模块共享，移到 domains 会让 retrieval/agent-engine 反向依赖 |
| **L22** | `BaseTool` 归位 `agent-engine` | ⏸ 不可行：domains/mcp 的 2 个工具用它，移过去会让 domains 反向依赖 agent-engine（R5c 刚清零） |

**新增守卫**（各配红态探针）：`check-comment-narrative` / `check-doc-truth` / `check-controller-purity` / `check-pipeline-map` / `check-known-gaps`；另加 `scripts/readability-report.py` 输出 6 个指标。

### 5.1 历史批次（v0.3.0 P1~P5 / v0.4.0 R1~R6，全部 ✅）

> **阶段路线（续，2026-10-16）**：**R1~R6（三 runtime + 模块收口）在 `v0.4.0` 全部完成** ⇒ **推送 `v0.4.0` + 合并 `main`**。 **✅ 2026-10-16 已执行**：`v0.4.0` 推送（995696f9..e3dce136）→ `main` 快进合并并推送（`c07786fe` → 含本记录的 tip）。**R1~R6 收口读数**：七模块 → 六模块（`:engine` 解散）、末轮增出 `:agent-engine`（七模块新图，见 §8.1 终检）；domains 对全部运行时模块只剩契约 + `modelcontext` 终态边；**16 守卫全绿**（新增模块图守卫）· 测试 5000 / 0 / 4。

> **阶段路线（2026-10-16 用户拍板）**：**P1~P5（两模块纠偏）在 `v0.3.0` 全部完成** ⇒ **推送 `v0.3.0` + 合并 `main`** ⇒ 新建 **`v0.4.0`** 分支，R1~R6（三 runtime）在 v0.4.0 上继续。 **✅ 2026-10-16 已执行**：`v0.3.0` 推送（95349098..c07786fe）→ `main` 合并推送（ef89e4ab..c07786fe）→ `v0.4.0` 新建并推送；R1~R6 在 v0.4.0 继续。

| 批 | 内容 | 状态 |
|---|---|---|
| **P1** | platform 域 DTO 归位示范：`StopSessionRequest` → `session/dto` + 端口中性签名 | 见 §12 |
| **P2** | KB config 形状三层拆分：7 件 Config 回 `knowledge/domain`；`KnowledgeBaseRowView` 载体改吃视图；View 的 from/toDomain 映射外移；`UserPreferences`→auth、`ParserEngineConfig`→tenant（余 2 件并入 P5） | ✅ 675b782b |
| **P3** | 跨域形状去框架化（mixin 机制、金片护航）：`SearchResult`→contract.retrieval；`AgentStep/AgentToolCall/ToolCallTarget`→contract.agent；`MessageView`→contract.session；`MemoryMessageCursor`→contract.memory | ✅ 744fdcb2 / 2a9b3453 / cac610c2 / 5ef29e07 |
| **P4** | 端口中性化进 contract：`AdminUserPort/AdminUserView/ApiKeyAuthPort` · `SessionQaPort` · `KnowledgeCatalogPort` · `TenantConfigLookup` · `EmbedSessionPort`（`SseSink`/`EmbedReply`）；config/settings 五件裁定留 platform（C8 豁免登记） | ✅ **6 刀全落**（163ddfcd/9db5b06d/d792a002/92c5eb70/151c7f25/4d7accdb）——**platform 业务形状清零** |
| **P5** | platform 收尾：fileserve/support 逐件裁；目录与 package-info 清理；**C8 门禁上线**（业务形状零命中验收） | ✅ e6f99afd / 1851b3ad / 1b020e97 |
| **R1** | 契约面先行：`contract.model.chat`（llm.domain 11 件）+ `contract.retrieval` 缺口 + `contract.agent` 6 件 + `contract.execution` 按需 | ✅ **收官**：R1a `ae0a17c0`（11 件 → `contract.model.chat`）· R1b-1 `b5a36912`（4 件 → `contract.websearch`）；余项 **R1b-2→R3 / R1c→R5 / R1d 不建**（判定见 §2.9） |
| **R2** | `model-runtime` 提取（**实际 153 件** = llm 58 / modelruntime 46 / embedding 21 / rerank 15 / modelcontext 13；计划的"163 件"含 tracing 6 + webfetch 4——**按计划自身 R4/R3 行裁定：tracing 归 R4、webfetch 归 R3**）；公开面分层（契约类 vs 实现面） | 🔄 **R2a ✅ `925071a2`+`f0355a7f`** · **R2b ✅ `c160b749`**（模块切出） · **R2c-1a ✅ `c25d90e7`**（`ModelRuntimePort`→`contract.model.ModelRuntime`、`VlmConfig` 契约化） · **R2c-1b ✅ `76c30539`**（装配器+配置映射入 model-runtime、共享传输归位 platform.http、模块内环清零）；**R2c-2 ✅ `fda13bbe`**（六处自建客户端改走端口工厂面 `chatClientFor`/`embedderFor`/`rerankerFor`/`chatConfigFor`；domains 对模型族的 import 70 处/22 文件 → **40 处/17 文件**）；**R2c-3a/3b ✅ `2e914b31`**（ASR 端口化 + `QaWiring` 装配走端口；`AsrTranscriber` 实现抽成独立类并自持 `@Component`，摘掉 domains 的手工 @Bean）⇒ domains 对模型族的 import **40 处/17 文件 → 27 处/14 文件**；**R2c-3c ✅ `1377e4ab` + `f83e2976`**（thinking 判定收进端口 `effectiveThinkingControl`——provider 词汇 5 处退场，`ModelDebugController` 对模型族 import 清零；顺带清 `InitializationController` 死参数）⇒ domains 余 **22 处/13 文件**；**R2c-3d 待做**（余项多为设计项：① `VlmDescriberWiring` 的 VLM 并发闸门（`ConcurrencyGovernor`+`Release`）建议收进端口（`ModelRuntime` 给"按模型闸门"或整个 VLM 调用）② `OllamaManageService` 的 Ollama 能力（status/models/pull）需契约端口 ③ `llm.extract` 族 9 处与 R4b 一并裁定（`PipelineConfig` 随 chatpipeline 走，`ExtractPrompts`/`EntityExtraction` 归 knowledge 或入契约）④ `PromptCache`/`ImageResolver` 各 1 逐条裁定；⑤ `modelcontext` 4 处**不动**——R5 之后是 `agent-engine → model-runtime` 的合法终态边）**R2c-3d 部分 ✅ `bafd7030`**（图片解析桥接 `ChatLocalImageResolverWiring` 归位组合根 boot ⇒ domains 余 **20 处/12 文件**）。**R2c 实质收敛**：余下 20 处里 4 处是合法终态边（`modelcontext` → R5），12 处与 R3/R4b 天然耦合（`retrieval.vlm` 的 VLM 客户端 + `ConcurrencyGovernor`/`Release` 闸门 ⇒ 随 R3 的 VLM 面一并；`llm.extract` 族 9 处 ⇒ 随 R4b：`PipelineConfig` 随 chatpipeline 走、`ExtractPrompts`/`EntityExtraction` 归 knowledge 或入契约），只剩 2 项可独立做：`OllamaService`/`OllamaModelInfo` 4 处（需 Ollama 能力端口）与 `PromptCache` 1 处（纯函数拆分到 platform）。⇒ **收窄 engine 的 `api(:model-runtime)` 应排到 R3/R4b 之后**
| **R3** | **retrieval-engine 模块提取**（实测 85 件 + 30 测试） | ✅ **收官**：**R3a** `a3d26936`（VLM 面归位 ⇒ retrieval 对模型族 = 0）· **R3b-1** `f9870dc6`（自足件上提）· **R3c** `8b0b3782`（`:retrieval-engine` 切出）· **R3b-3** `9b10c247`/`19d578d5`/`f322f5e2`/`6bb310d7`（向量店面 / 读面 / 写面+解析面 / fallback 变体）· **R3b-4** `a0b8f121`·**R3b-5** `195ed447`·**R3b-6** `8b1380a5`（工具面 / 落点裁定 / 观测面拆类）· **R3d-1..5** `3bf266fc`/`6ab7b52a`/`2e2fb0e1`/`616d3650`/`1c23a2f0`（补漏 / pg 直连句柄 / verifyBinding / 连通性面 / 工具面归位 + 依赖收窄）。**验收读数**：`retrieval` 对模型族 import = 0 · **`domains` 主码对 `com.ragagent.retrieval.*` import = 0** · `engine` 已摘 `:retrieval-engine` 依赖 · `boot` 显式声明 |
| **R4** | 归位：`tracing`(6) → `platform.tracing.decorators`；`chatpipeline`(46) → `domains/session/service.rag`（实落 `session/service/rag`）；`webfetch`(4) → `websearch` 域 fetch 面 | ✅ **收官**：**R4a** `fff4d3ba`（tracing 六件 + 契约流句柄三件）、**R4b-1** `89df46b3`（管线 SPI 4 端口 → `contract.pipeline`）· **R4b-2a** `10faf9f7`（`PipelinePorts` 契约化）· **R4b-2b** `592309b8`（webfetch 归位 + 抓取面两端口）· **R4b-2c-1** `91ec80ae`（`KnowledgeQaRunner` 端口，解评估域阻塞）· **R4b-2c-2** `3ecf29af`（本体 43 件归位 `session/service/rag` + `:engine` 解散） |
| **R5** | agent-engine 自 `domains/agent`（80 件）外提 + `contract.agent`（并入判定）+ 工具 SPI | ✅ **收官**：**R5a** `e69c99c9`（切出 `:agent-engine`，七模块）· **R5b-1..4** `8321006e`/`39cb352f`/`6a111e7a`/`02b19f11`（纯形状上提 · 预算/转向面 · `DocChunkSupport` 归 platform · `SkillCatalogPort`）· **R5c-1..3** `533d626e`/`ff9b8d21`（`AgentRunSpec` 读面 · 装配权下沉 `AgentEngineFactory`+句柄+A18 保真 · 过渡边摘除）。**验收**：domains 主码对 agent-engine import **55 → 0**；模块图 domains → contract/platform（agent-engine → model-runtime 的 `modelcontext` 为合法终态边）· 余：R6 模块图终检 + 两处检索错位测试迁移（`HybridResultOpsContextExpansionTest`/`ArtifactReferenceRewriterTest`）与随之摘 `domains` 的测试期检索依赖 |
| **R6** | engine 解散收口（剩余包归位 + 收窄 `api` 传递 + 模块图终检） | ✅ **实做部分随 R4b-2c-2 完成** `3ecf29af`：`:engine` 整模块解散（settings/jacoco/守卫清单/Dockerfile/依赖全摘）⇒ 两条 `api` 传递边自动消失；**余**：模块图终检放到 R5 之后（agent-engine 落位后一次跑）　+ **终检 ✅**：`e16c2fa8`（测试期检索依赖收口）· `d02f89db`（模块图守卫上线，**16 守卫**；夹具包改名消同名）。**实测：主码 16 条边全在矩阵内 · 主码同包 0 · 重复 FQN 0 · 夹具占用 0** |

## 6. 环境与运行

- 后端端口 **8083**；PG 独立库名 **`ragagent`**（同实例 :15432 下另有旧仓 `WeKnora` 库 —— **排查数据前先确认库名**：`grep DB_NAME .env` 或看启动日志 jdbc url）。
- 前端：`VITE_DEV_PROXY_TARGET=http://localhost:8083`（vite 默认值已改 8083）。
- `LOCAL_STORAGE_BASE_DIR` 必须持久目录（严禁 /tmp，macOS 会清理 ⇒ 原始文件丢失）。
- **起停服**：`bash scripts/java-server-up.sh` / `bash scripts/java-server-down.sh`（up 自挡端口占用；down 是唯一正确停服方式）。
- **⚠️ 跑完 `./gradlew build/test` 必须重启后端**（构建就地重写 `build/classes` ⇒ 在线服务 500/NoClassDefFoundError，重启即解，别查代码）。
- CI 三 job：guards（`bash scripts/run-guards.sh` 为准）· backend（spotless ratchet + 全量测试 + 灌 `V1__baseline.sql` 进全新 ParadeDB）· frontend（type-check + test + build）。`seed` tag 已推送（spotless ratchet 需要）。
- 全量闸门（**推送/批次收尾必跑**；迭代中按 §9 分档）：
  ```bash
  cd ~/ragagent && env -u SYSTEM_AES_KEY JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew spotlessCheck build
  ```
  期望：`BUILD SUCCESSFUL` + 各模块测试汇总条数守恒（最近基数 **5022 / 失败 0 / 跳过 4**，含 agent-engine 155 条——旧口径 4867 漏算该模块）。`PATH` 须 `/opt/homebrew/bin` 在前（python3.9 会让 file-size 假红）。
  **环境前置**：PG `max_connections=100`，多个 boot jar 残留会占满连接（实测 5 个实例 = 50 条 ⇒ `too many clients`）。跑闸门前先 `ps -Ao pid,command | grep '[b]oot/build/libs/boot'` 清残留。
- 前端（触契约时）：`cd frontend && npx vue-tsc --build --force` + `npm test`。

## 7. 测试与安全网

- 回归网：**~4996 用例 / 1,349 契约金片**；每一步（哪怕纯移动）结束必须全绿。
- 已知偶发（全量并发下，单独重跑即绿，勿误判回归）：`WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool` · `EvaluationContractTest.getTerminalRunsExecution` · `EmbedRateLimiterTest.redisPathSharesBudgetAcrossInstances`。
- **Redis 瞬态族已修（2026-10-19）**：根因是 `EmbeddedRedis` 用 `ServerSocket(0)` 探端口在 `maxParallelForks` 下会撞——后到者借用先到者的实例，先到者 close 杀进程后连接全断。现改为固定端口池 + 跨进程 `FileLock`，红态探针 `EmbeddedRedisPortAllocationTest` 守着；全量连跑 5 遍 0 失败。再见到同类失败按真回归查。
- 覆盖率：**未执行基线**（`-Pcoverage`）⇒ 本项目**不声称任何覆盖率数字**（开放项）。
- 前端：`vue-tsc` 是最终文件集上的最后一步（`tsx --test` 只转译不查类型）。

## 8. 结构地图与守卫总表

### 8.1 现状模块（7）与目标（7）

> 计数口径：`<模块>/src/main/java/**/*.java`，**不含 package-info**（2026-10-16 R5a 后实测）。

```
现状（R6 后）                              目标（方案 §5.1）
contract      323 件（纯 JDK，零依赖块）  →  contracts/
platform      159 件（技术适配 + 追踪装饰/流句柄/共享传输 + 提示词/配置）→ platform/
model-runtime 132 件（llm · modelruntime · embedding · rerank ·
                 modelcontext 12 · vlm 2 · application 装配面）
                                        →  model-runtime（已成；余 R2c-3d 少量过渡边）
retrieval-engine 80 件（engine 67 · support 7 · 根 5 · graph/artifact/config/telemetry）
                                        →  retrieval-engine（已成；R3 收官）
agent-engine   57 件（tool 23 · compaction 12 · prompt 11 · skills 9 · budget 4 · domain/根 21）
                                        →  agent-engine（已成；R5b/R5c 以 `contract.agent` 收窄消费面）
domains      1261 件（knowledge 240 · session 165〔含 rag 43〕· wiki 158 · mcp 131 ·
                 datasource 127 · auth 88 · memory/im 各 80 · agenttools 58 · …）
                                        →  domains（R5b/R5c 端口化后消费面再收）
boot           14 件                      →  boot

依赖方向：boot → 全部；domains / 三个 runtime → platform, contract；platform → contract；contract → JDK
（**`:engine` 已解散**：chatpipeline → `domains/session/service/rag`、webfetch/抓取面 → `domains/websearch/fetch`
 ⇒ 旧的 `api(:model-runtime)`/`api(:retrieval-engine)` 传递边随模块消失；domains/boot/boot 显式声明
  各 runtime（含 R5a 的 `:agent-engine`，domains 那条是**过渡边**待 R5b/R5c 收窄）
 ——其中 domains→model-runtime 是**过渡边**，待 R2c-3d 余项收完）
```
### 8.2 域内分层（方案 §6）

`controller`（绑定/帧写出）· `service`（用例与政策）· `domain`（状态与不变量）· `mapper` · `repository` · `dto` · `client` · `task` · `api` · `support` · `config`（只装配）。
禁止：`service` 引边界层/具体技术（A16/A17）· `domain` 引 Spring/Mapper · `repository` 回引 service · `controller` 跑编排 · `mapper` 放仓储门面（A11b）。

### 8.3 守卫与规则总表（**全部守卫 + ArchUnit**；新增规则前先查这里）

| 守卫 | 语义 | 基线 |
|---|---|---|
| `check-package-cycles.py` | 环/分层/域依赖/SCC（R1~R9） | `package-cycles.baseline.json`（只许减；SCC 12 组） |
| `check-domain-contracts.py` | **R10 域间只经契约**（分档①②③） | `domain-contracts.baseline.json`（**6 条边 / 51 类**；①0/②2/③5） |
| `check-contract-purity.py` | C1 导入根（纯 JDK）· C2 构建依赖 · C3 框架形状 | 硬零，无基线 |
| `check-contract-sync.py` | 金片键签名变化必须签字 | 1,349 金片 |
| `check-json-key-case.py --strict` | 换锚棘轮 + 冻结覆盖快照 | 冻结面登记 |
| `check-event-face-case.py` | 事件面/路由面命名口径 | — |
| `check-file-size.py` | 大文件棘轮（>600 棘轮，≥800 须登记） | `file-size.baseline.json` |
| `check-go-anchors.py` | Go 锚点注释棘轮 | 0 |
| `check-fe-contract-keys.py` | 前端契约键棘轮 | 只许减 |
| `check-stray-dirs.py` | 游离/死包目录 | 0 |
| `check-dead-members.py` | 死成员（main+test+testFixtures） | 0 |
| `check-api-envelope.py` | 统一响应外壳 | 只许减 |
| `check-exception-semantics.py` | 检索引擎 catch 不得清空边界条件 | 0 |
| `check-package-dirs.py` | 包声明 ↔ 目录一致（含 testFixtures） | 0 |
| `check-module-graph.py` | 主码依赖矩阵 · 同包 · 测试边登记 · 测试错位登记 · 夹具包 | 登记表（只许减） |
| `check-platform-shapes.py` | platform 业务形状红旗 | `platform-shapes.baseline.json` |
| `gen-arch-index.py --check` | `package-info` 与 `docs/architecture/packages.md` 同步 | 硬零 |
| `check-tenant-scope.py` | 跨线程身份必须走 `TenantScope`；禁止裸 capture/replay | 硬零 |
| `check-log-exceptions.py` | log 里禁止 `Throwable.toString()` / `.getMessage()` | 硬零 |
| `check-duplicate-classes.py` | 同一简单类名不得出现在多个模块主码 | 硬零 |
| `check-internal-codes.py` | 主码注释禁止批次号/裸日期戳（ISO-8601 时间示例保留）；旧夹具名不得回流 | 硬零 |
| `gen-module-map.py --check` | 模块/包根、22 域文件/控制器数量与 `module-package-map.md` 同步 | 硬零 |
| `check-version-consistency.py` | Boot/springdoc/MyBatis-Plus jsqlparser/README/wrapper/Dockerfile 版本一致 | 硬零 |
| ArchUnit `ArchitectureRulesTest` | A1~A20（含 A16/A17 边界禁令、A18 状态所有权、A19 分层、A20 装配契约） | 各基线只许减 |

## 9. 落刀方法论（精要；**全量判据见归档 v0.2.0 §13/§14**）

1. **固定套路**：侦察（图分析+引用面）→ 动前断言（零入度/引用面白名单）→ `git mv` → FQN/import 修复（编译驱动）→ 定向测试 → 全量闸门 → 提交；**一次只搬一个包簇**。
2. **搬前三道判据**：① 同包裸名共居检查；② 消费域核查（只被本域用 ⇒ 回域，不进契约）；③ §5.1 三测 + **持久化形状排除**（内部件/实体字段类型不得进 contract）。
3. **三维闭包预检**：import + **同包裸名** + **字符串 FQN/注册名**（三类全查，缺一必栽）。
4. **`git mv` 后必须 `--rerun-tasks`**（时间戳让增量编译认为"已编译" ⇒ 测试静默少跑；每批必须核对测试条数守恒）。
5. **换锚五处**：`FROZEN_PREFIXES`（json-key）· `file-size.limits` 键 · json 键文件路径 · SCC 基线成员 · 扫描名单（`_source_roots.py` 的 `MODULE_DIRS` 一行覆盖多脚本）。
6. **编译是最硬判据**：同包裸引用/测试树同名包只有编译能揭穿；"改实现"必须**同步清 import**（R10 按 import 计边）。
7. **javadoc 是模块边界探针**：跨模块 `{@link}` 断链即报；契约模块内严格（FQN 降级 `{@code}`）、其它模块宽松，**不要用一把尺子扫全仓**。
8. **守卫必须能红**：新增/改守卫配红态探针；**判据自证**（退出码不经管道；数字能自计数就自计数）。
9. **搬构建插件必须连配置块**；**资源归属看"谁加载"**；新模块建好核对"测试条数总量守恒"（`build` 绿 ≠ 测试跑了）。
10. **先测后改**：行为变更先给失败测试/反例；金片/合约测试会挡住"看起来对但违约"的改动。
11. **改名/批量替换**：只替换"匹配文本本身即完整类型"的 FQN；带左边界排除（`(?<![\w.])`）；每批编译 + 跑 `check-package-cycles.py`。
12. **baseline `--write` 不是消红按钮**：先区分 ①换锚 ②分类缺失 ③真违例；存在新增时 `--write` 必须拒绝。
13. **用例层欠账收敛三手法**（L36~L45 清零 69 类时定型）：① **门面化**——service 注入 mapper ⇒ 建 `repository/XxxRepository` 薄转发；② **端口化**——service 依赖具体类（task 执行器 / client / 调度的 Scheduler）⇒ 抽 `domain` 或 `support` 的窄接口，实现侧 `implements`；③ **落点纠偏**——类本身没错、住错包（适配器 → `api`、`@Configuration` → `config`、Redis 实现 → `repository`/`runtime`）。**落点先过三关**：C10（控制器不 import `..domain..`）· A16（包名任一段等于 `controller/mapper/task/client/channel`）· 包规模红线（>30 文件）——三者常互斥，`support` 往往是唯一都放行的地方。

## 10. 批次纪律（v0.2.0 §15.2 十二条，全部保留）

1. 一次只动一个轴、独立提交独立全绿、双端闸门。2. "同 PR 带前端"核到视图层字段读取。3. 键名替换分清三类键（线格式/系统设置与 i18n/本地态）。4. 触 HTTP 面做真实冒烟；金片重录先看对比器。5. 每批写回 §12。
6. **规则型/守卫型测试必须发明可证伪的探针**（没探针的规则等于没规则）。7. 注册类扩展点必须实测行为（静默失效）。8. 拿日志/行为当证据前先读被验对象的类注释。9. **动包/类落点的批次，包结构守卫必跑**（build 已含 guards，此为显式复核）。
10. 跨进程 JSON 键名按"权威"分桶（REST=camel/SSE=对外权威面/RFC 原文/词表）。11. **闸门退出码必须显式取用**。12. **"看得见的数字"要自计数**。

## 11. 非目标（冻结面，勿列入修复）

**① 外部决定**：connector 第三方线格式 · provider 请求体 · websearch metadata · image_info（docreader）· rerank `RankResult` · MCP OAuth 载荷 · langfuse `lf_*` · IM 平台 ACK · i18n 与系统设置键。
**② 协议/行为面（需拍板）**：引用协议标记 · wiki 摄取契约标签 13 个 · chatpipeline/evaluation 模板标签 · 工具名 36 个与 schema enum。
**③ 落库/DDL 形态**：列名与 SQL 参数 · jsonb 列 · 检索引擎索引文档字段。
**Go 零值时间哨兵**：形状变更，另批。

## 12. 执行记录（v0.3.0 起；模板：批次｜内容｜读数｜验证）

| 批 | 日期 | 内容 | 读数/验证 |
|---|---|---|---|
| P1 | 2026-10-16 | `StopSessionRequest` 归位 `domains/session/dto`（方案 §8.8.1 原文落点）+ `EmbedSessionPort.stopSession` 中性化（rawBody 透传、解析归会话域，口径对齐 `MessageSuggestionController.ensureSuggestions` 先例）；`platform.session.dto` 目录消亡 | 14 守卫绿 · 金片 1349 签名一致 · gate 2m16s / **4996 / 失败 0 / 跳过 4** · 端点覆盖 `EmbedContractTest` + `SessionG6ContractTest` 全绿 |
| P2 | 2026-10-16 | KB 配置形状三层归正：7 领域形状回域 + 契约视图 7 件纯化 + 域侧映射单一实现（`KnowledgeBaseConfigMaps`）+ 未生效标注收进契约 + VLM 凭据写回合并保护 | 14 守卫全绿（R10 无新增边）· 金片 1349 一致 · gate 2m14s / **4996 / 失败 0 / 跳过 4**；**契约测试当场抓到并修复 `toDomain` 缺 null 透传（EvaluationContractTest 500→绿）** |
| P3 | 2026-10-16 | 跨域形状去框架化四刀：`SearchResult`→contract.retrieval · `AgentStep` 族→contract.agent · `MessageView/PipelineMessageView`→contract.session · `MemoryMessageCursor`→contract.memory（mixin 承载注解；`ByteKeyOrder`/`ZeroTimes`/`ProviderMetadataMaps` 三共享物上提） | 四刀各自 14 守卫全绿 · 金片 1349 一致 · gate 2m21s~3m1s / **4996 / 失败 0 / 跳过 4**；platform 的 retrieval/agent/memory 三包消亡、session 收缩至 2 端口（P4 对象） |
| P4 | 2026-10-16 | 端口中性化五刀：AdminUser 族→contract.auth · SessionQaPort+QA 链 EventBusInterface→contract.session · TenantConfigLookup→contract.tenant · KnowledgeCatalogPort（去 Page）→contract.knowledge · ApiKeyAuthPort 中性化→contract.apikey；config/settings 五件裁定留 platform | 五刀各自 14 守卫全绿 · 金片 1349 一致 · gate 1m56s~2m45s / **4996 / 失败 0 / 跳过 4**；platform 的 auth/apikey/retrieval/agent/memory 消亡、knowledge 仅余 codec、session 仅余 EmbedSessionPort（P4f） |
| P4f | 2026-10-16 | `EmbedSessionPort` 中性化入契约：`SseSink`（帧出口）+ `EmbedReply`（status+body）；SSE 链全链换型（FrameWriter→Emitter→Orchestrator→Executor→Controller）+ 边界包装（DelegateOps/Adapter）；`platform.session` 包消亡 | 14 守卫全绿（R8 修复）· 金片 1349 一致 · gate 2m6s / **4996 / 失败 0 / 跳过 4** |
| P5 | 2026-10-16 | platform 收尾三件：`ParserEngineConfig` 去框架化入 `contract.tenant`（27 注解 → Core mixin）· fileserve 逐件裁（4 端口 + `OpenedFile` 上提契约；HTTP 三件 + `StoragePaths` 留登记）· **C8 门禁上线**（业务形状棘轮，3 条登记） | 15 守卫全绿 · 金片 1349 一致 · gate 1m39s~2m23s / **4996 / 失败 0 / 跳过 4**；platform 只剩技术适配 + 3 条业务形状登记 |
| —（杂项） | 2026-10-16 | 61 份过期文档清理（用户确认；活文档零引用已核）· `scripts/dev-env.sh` login 提取器适配统一响应壳（token 移入 data） | 冒烟 sessions / knowledge-bases 200 · token 265 字符 |
| R3b-6 | 2026-10-16 | **观测面拆类**：`RetrievalObs` 的纯函数族（截断/命中预览/分数汇总/段落预览/格式化）上提 `contract.retrieval.RetrievalObsRows`；引擎内部形状两法（`summarizeRetrieveOutput`/`summarizeIndexHits`，签名取 `EngineTypes.*`）留实现模块；全仓接收者重指向 + 清 6 处失效 import；json-key 按 BASELINE 登记 12 个**观测 span 既有 wire 键**（同 `agent/ActPhase.java` 同族） | 15 守卫绿 · 金片 1349 一致 · gate 2m39s / **5000 / 0 / 4** · 冒烟 200 |
| R3d-5 | 2026-10-16 | **工具面归位 + 依赖收窄**：`GrepChunkView`→**`GrepChunkHit`** + `GrepChunksScoring` → `platform.knowledge`（改名是为过 C8 红旗「View」，职责不变）；**domains 主码对检索实现面 import = 0**；`engine` 摘掉 `api(:retrieval-engine)`（engine 引用实测为 0）· `boot` 显式 `implementation` · `domains` 仅 `testImplementation`（两个错位测试登记为待迁债务） | 15 守卫绿 · 金片 1349 一致 · gate 2m49s / **5000 / 0 / 4** · 冒烟 200 |
| R3d-4 ✅ `616d3650` | 2026-10-16 | **连通性面搬迁落地**（照施工单执行）：契约面 `VectorStoreConnectivity` + 探测体整块 → `retrieval.engine.VectorStoreConnectivityProbe`；域侧 563→344 行（只剩校验+委托）。原施工单留档：`domains/vectorstore/service/VectorStoreConfigService.java` 的探测块 = `testConnection` 215-233 + 8 个私有探测法（`testElasticsearch` 244-279 · `testPostgres` 281-297 · `testQdrant` 303-313 · `testMilvus` 320-329 · `testTencentVectorDB` 336-350 · `testWeaviate` 352-381 · `testDoris` 389-401 · `testOpenSearch` 409-424）≈ 185 行；对外 5 个厂商静态口（306 Qdrant · 322 Milvus · 338-340 Tencent（含 `TencentVectorDbRestClient` 的 catch 分支）· 394 Doris · 416 OpenSearch）。**五步**：① `sed -n '215,424p'` 整块取出；② 契约面建 `VectorStoreConnectivity`（`String probe(String engineType, ConnectionConfig config)`，`ConnectionConfig` 已是契约类型 ✓）；③ 整块搬进 retrieval-engine 新类实现该端口（厂商 repo 本就在那，`ssrfGuard` 来自 platform ✓）；④ 域侧只留两条校验（`validateConnectionConfig`/`validateConnectionAddrSSRF`）与错误包装 + 委托端口调用；⑤ 编译-守卫-闸门一遍，随后即可**收窄 `engine` 的 `api(:retrieval-engine)`**（engine 主码对 retrieval import 已 = 0）| 未动手原因：185 行整块读写的预算不足，宁留下可执行施工单也不留"编译过、没验过"的中间态 |
| R3d-3 | 2026-10-16 | **解析端口补 `verifyBinding`**（归属校验收进实现侧；`KnowledgeBaseService` 去掉 registry/factories/ownership 三件）+ **`FaqEntryCommandService` 的批量更新改走既有 `VectorIndexStore` 端口**（那两个方法 R3b-3 第一片就已上端口 ⇒ 零新设计）⇒ domains 检索直连 **13 → 10 处**（余：agent 工具面 4 待 R5；厂商探测 5 + `TencentVectorDbRestClient` 1 = 连通性面整块，施工前置已勘察） | 15 守卫绿 · 金片 1349 一致 · gate 首轮 1 例 Redis 族失败（隔离 6/0 绿）· 二次全量 3 例同族（隔离 11/0 绿，本批不触 Redis 路径 ⇒ 本地环境抖动族）| 
| R3d-2 | 2026-10-16 | **pg 直连句柄落地**（先取证后动手）：先核 3 个失败测试 = `@SpringBootTest` 全上下文 ⇒ 真因是pg 引擎为 **boot 显式 @Bean**、而注册表按引擎类型查的是 env-store 面（上下文里查不到）。修法：`IndexEngineGateway` **直注 pg 引擎 bean**；`PostgresAdapter` 基于**具体类型**（实测 pg 件实现的是 `RetrieveEngineRepository`，上一版的 `RetrieveEngineService` 假设是错的）；域侧 Move/Clone 字段换端口 ⇒ **domains 对检索实现类 import 清零**（仅余 agent 工具面 4 条待 R5） | 15 守卫绿 · 金片 1349 一致 · gate 2m50s / **5000 / 0 / 4**（6 例此前失败测试全过）· 冒烟 200 |
| R3d-2 试做（已回退） | 2026-10-16 | **pg 直连句柄**：加 `IndexEngineResolver.postgresDirect()` + `PostgresAdapter`（基于 `RetrieveEngineService` 接口 —— **编译通过**，证明接口确有 save/index/delete*/copy/batchUpdate/estimate 那一套，适配器代码下次可复用），并把 Move/Clone 的 `PgVectorEngineRepository` 字段换成解析端口。**结果：6 例 move/copy 契约测试红**（KnowledgeSearchMoveContractTest×2 · KnowledgeMoveReparseTest · KnowledgeVectorRoutingTest 等）⇒ **语义不等价**：旧字段是 pg 引擎 **bean**（测试直接注入真实/桩件），新路径经 registry **按引擎类型查找**（测试上下文中查不到）。已按纪律 `git checkout` 全量回退（不为过闸门改动契约测试）。**下次正确做法**：① `postgresDirect()` 在网关里**直接注入 pg 引擎 bean**（同模块合法）而非走 registry；或 ② 契约面暴露"pg 句柄 bean"端口 + 装配层注入；两条都要先看那 4 个失败测试的构造方式。 | 守卫全绿 · 工作区回到 HEAD（gate 基线 5000 / 0 / 4）|
| R3d 施工前置（勘察 2） | 2026-10-16 | 连通性面可施工性已核：**`ConnectionConfig` 已是契约类型**（`contract/vectorstore/ConnectionConfig`，公有字段）⇒ 端口可直接收；探测块 = `VectorStoreConfigService` 的 `switch(engineType)` + 9 个私有探测方法（`testElasticsearch`/`testPostgres`/`testQdrant`/`testMilvus`/`testTencentVectorDB`/`testWeaviate`/`testDoris`/`testOpenSearch`/`sqlite→""`），它们只调各厂商 repo 的测试口 + `ssrfGuard`(platform) + 驱动属性 ⇒ **整块可迁 retrieval-engine**（厂商 repo 本就在那），域侧留两条校验（`validateConnectionConfig`/`validateConnectionAddrSSRF`）与错误包装。**pg 直连句柄**：`IndexEngineResolver` 补 `postgresDirect()`，rettrieval-engine 侧加第二个适配器（包 `PgVectorEngineRepository`：save/batchSave/estimateStorageSize/deleteBy*List/copyIndices/batchUpdate*/moveKnowledgeIndices）。两块共用一次编译-闸门循环，建议合批 | 勘察命令见本行内容 |
| R3d-1 | 2026-10-16 | 补两处漏网的实现注入（`MessageService`/`AgentToolBackends` → `HybridSearchPort`）+ 两处 javadoc 措辞。**webfetch 归属拍板**：按方案原文归 **websearch 域 fetch 面**，但**执行挪到 R4b 之后**——实测 `engine/chatpipeline/plugin/PluginWebFetch` 在用（另 `agenttools/web/WebFetchTool`）⇒ 现在迁会造成 `engine → domains` 违规。**R3d 余**：① `VectorStoreConfigService` 的探测面 11 处（各厂商 `*RetrieveRepository` 7 + `VectorStoreService` + `RetrieveEngineRegistry` + `RetrieveEngineFactories`）⇒ 需把"向量店连通性探测"整段沉进 retrieval-engine（契约端口 + `ConnectionConfig` 载体裁定）；② `PgVectorEngineRepository` 2 处（KnowledgeMove/Clone 的 pg 直连回退）⇒ 解析端口补"pg 直连句柄"；③ 工具面 4 处待 R5；④ 之后才收窄 `api(:retrieval-engine)` | 15 守卫绿 · 金片 1349 一致 · gate 2m16s / **5000 / 0 / 4** · 冒烟 200 |
| R3d 勘察 | 2026-10-16 | **engine 主码对 retrieval 的 import = 0** ✓（chatpipeline 早已走契约端口）。**收窄 `api(:retrieval-engine)` 的前置** = 清 domains 剩余 ~15 处直连：① agent 工具面 `GrepChunkView`/`GrepChunksScoring`（各 2 处）⇒ **R5 后由 agent-engine → retrieval-engine 合法消费**；② `PgVectorEngineRepository`（2 处：KnowledgeMoveService/KnowledgeCloneService 的 pg 专有操作）⇒ 需给 `IndexEngine` 或其伴生端口补 pg 专有面；③ 各厂商 `*RetrieveRepository`（7 处，集中 `VectorStoreConfigService` 的**连通性测试**：Tencent/Doris 等 `testConnection(...)`）⇒ 需契约面「向量店连通性」端口；④ `HybridSearchService` 2 处**仅 javadoc 提及**（KnowledgeQaController/SessionStreamController）⇒ 改措辞即可。另：`webfetch` 4 件**对模型族/检索零出边**（只依 contract/platform）⇒ 按方案原文可整体迁 `domains/websearch`，但需先核其消费方（若 chatpipeline 在用则须等 R4b，否则 engine → domains 违规） | 勘察命令：`grep -rh "^import com\.ragagent\.retrieval\." engine/src/main`（空）|
| R3b-5 | 2026-10-16 | **工具面落点裁定 + 零头**：`ChunkSearchUtil`/`ImageInfoEnricher`/`ImageInfoMatchUtil` → `platform.knowledge`（技术/文本解析工具，非业务形状 ⇒ 合 C8）；`GrepChunkView`/`GrepChunksScoring` 留实现模块（C8 红旗命名「View」+ 二者是 agent 工具面 ⇒ R5 后 agent-engine → retrieval-engine 合法消费）。契约面新增 `SourceTypes` （域侧两处常量换掉）；`RetrieveGraphRepository` 补 `default enabled()`（`SystemInfoService` 去 instanceof）；错位测试 `HybridSearchServiceStoreGroupTest` 随迁 retrieval-engine（`TenantService` → 契约 `TenantConfigLookup`）、`ChunkSearchUtilTest` 随件迁 platform。**未完成并说明**：`telemetry/RetrievalObs` 域侧与引擎内部消费混在一个类（内部签名取 `EngineTypes.*`）⇒ 需拆类或等内部件口径，本批不动 | 15 守卫绿 · 金片 1349 一致 · json-key 换锚 2 处（BASELINE 口径）· gate 2m30s / **5000 / 0 / 4** · 冒烟 200（含 system/info）|
| R3b-4 | 2026-10-16 | **工具面上提**：`SearchTextUtil`（322 行，纯逻辑）· `WebResultConverter`（109 行，仅契约依赖）retrieval-engine/support → `contract.retrieval`（消费方：domains 三件 + chatpipeline 六件不再穿透实现模块）。**暂留**：`ImageInfoEnricher`（579）/`GrepChunksScoring`（249）——它们引同包 `ChunkSearchUtil`/`GrepChunkView`（带 Jackson）⇒ 进不了契约（零依赖红线），落点与这两件一并裁定。json-key 按 **BASELINE 的 repo 根相对口径**换锚（`published_at` 是既有 wire 键；**没**写成冻结前缀——那是源根相对口径，写错即成死条目，本会话已踩过） | 15 守卫绿 · 金片 1349 一致 · gate 2m52s / **5000 / 0 / 4** · 冒烟 200 |
| R3b-3 第三片补 | 2026-10-16 | **解析端口补 fallback 变体**：新增 `contract.retrieval.TenantRetrievalDefault`（租户回落口径：引擎表{不透明 JSON} + 驱动名）+ `IndexEngineResolver.resolveFor(tenantId, storeId, fallback)`；`IndexEngineGateway` 按 `EffectiveEngines.of(...)` 实现（口径逐字保留）；`ChunkQuestionService` 去掉六个检索内部件 import，只留回落口径构造。**效果：domains 主码对检索实现类 import 归零**——剩余全是工具面（support 六件 + telemetry/RetrievalObs + graph 实现件）与 `EngineTypes` 常量 | 15 守卫绿 · 金片 1349 一致 · gate 2m41s / **5000 / 0 / 4** · 冒烟 200 |
| R3b-3 第三片 | 2026-10-16 | **索引写面端口化 + 绑定解析下沉**：新增 `contract.retrieval.IndexDocument`（域侧真给的 10 字段）· `IndexEngine`（已绑定 store 的写面句柄，9 方法）· `IndexEngineResolver`（租户+店 → 句柄，未绑定由调用方判定）；retrieval-engine 新增 `IndexEngineGateway`（解析口径逐字保留 + `IndexDocument→EngineTypes.IndexInfo` 一次映射，内部形状不上提）；domains 的 `KnowledgeVectorWrites` 只留域判定与转交，ChunkVectorIndexer/KnowledgeProcessWorker/FaqIndexWriter + 迁移克隆标签 FAQ 服务改走端口；测试随行为分家（租户/跨租户/虚拟线程断言 → 新 `IndexEngineGatewayTest`）。**未动**：`ChunkQuestionService` 的「无绑定回落租户有效引擎」变体（需给解析端口补 fallback 变体）· `HybridSearchServiceStoreGroupTest`（错位测试待随迁） | 15 守卫绿 · **R9 SCC 一组变形已登记理由** · 金片 1349 一致 · gate 2m47s / **5000 / 0 / 4** · 冒烟 200 |
| R3b-3 第二片 | 2026-10-16 | **混合检索读面端口化**：新增 `contract.retrieval.HybridSearchPort`（三方法 = 域侧真调用面：hybridSearch / getQueryEmbedding / resolveEmbeddingModelKeys；载体已是契约 `SearchParams`/`SearchResult` ⇒ 零设计）；`HybridSearchService implements` 之；域侧 5 消费点改走端口（KnowledgeBaseController · FaqEntryQueryService · QaWiring · AgentToolKbBackends · MessageSearch） | 15 守卫绿 · 金片 1349 一致 · gate 2m27s / **4997 / 0 / 4** · 冒烟 200 |
| R3b-3 第一片 | 2026-10-16 | **向量索引写面端口化**（路线丙）：`IndexRow` 上提 `contract.retrieval` + 新增 `VectorIndexStore` 端口（7 方法=域侧真调用面）；`VectorStoreService implements` 之（静态 estimateStorageSize 改名 *Static、端口面给实例方法）；**内部形状原地不动**（不再跨模块即可可变，载体红线不适用 ⇒ 省掉 500~800 处机械波）；域侧 4 消费点改走端口（ChunkVectorIndexer/KnowledgeTagService/FaqIndexWriter/KnowledgeProcessWorker） | 15 守卫绿 · 金片 1349 一致 · gate --rerun-tasks **4997 / 0 / 4** · 冒烟 200（首两轮各一例 Redis 族失败，隔离重跑 3/0、4/0 绿）|
| R3b-2 勘察 | 2026-10-16 | **形状 reshape 的实测规模与改道建议**（未动代码）：`EngineTypes.{IndexInfo, RetrieveParams, IndexWithScore}` reshape 波 = **构造 93 + 字段赋值 143 + 字段读取约 250~550**（字段访问 `x.f` → record 访问器 `x.f()`）/ 34+ 文件。**依据检查**：载体红线有明文（§2.1「不得上提…可变 setter 到 L1」+ 方案「contracts = 不可变载荷」）⇒ 可变形状确实不能上提，但**未被要求必须上提**。**推荐改道（丙）**：内部形状留在 retrieval-engine（不再跨模块即可保持可变 ✓ 合法），契约面改为**领域级载体 + 端口**——新增 `contract.retrieval.IndexDocument`（不可变 record，字段=域侧真给的 10 项），读面复用**已有** `SearchParams`/`SearchResult`；端口按域侧实测调用面切：写面 `batchIndex(docs)`/`deleteByChunkIdList`/`deleteByKnowledgeIdList`（取 ids + dimensions + kbType + Embedder{契约✓}）、向量店面 `saveIndexRows`/`deleteByChunkId`/`estimateStorageSize`/`IndexRow`、读面 `HybridSearchService` 的检索入口。收益：把"500~800 处全仓机械波"换成"1 个契约 record + 2~3 个端口 + 域侧 ~13 处消费点改造"，且分层更正确（引擎内部形状不再出门） | 实测命令：`grep -rn "\.\(isEnabled\|score\|matchType\|…\)\b" retrieval-engine/src/main domains/src/main | grep -v " = "` = 550 |
| R3c | 2026-10-16 | **`:retrieval-engine` 模块切出**：`com.ragagent.retrieval` 整树（main 91 / test 30）+ `jieba` 资源（hmm_model 1.1MB + 测试基线）engine → retrieval-engine，包名不变；依赖按实测 import 面（contract/platform api + Jackson/spring-context·jdbc·boot + neo4j/sqlite/Hikari implementation + mysql/postgres runtimeOnly）；**测试任务口径随迁**（integrationTest 显式入口 + test 排除 *IT——否则两台 env 门控 IT 混进 test：实测 +2 条/+2 跳过）；七处模块清单同步 + 异常语义守卫扫描根随迁（守卫自报"扫描根不存在"）+ 大文件基线 3 条换锚（ModelDebugController 出榜属收紧）。engine 过渡期 api 传递；**engine 的检索遗留依赖未删**（Gradle implementation 不传递，删前要逐个核 runtime 可达性，留 R6） | 15 守卫绿（环 0 · SCC 9 · 域间 6 条不变）· 金片 1349 一致 · gate 1m58s / **4997 / 0 / 4**（engine 315→16，新模块 292）· 冒烟 200 |
| R3b-1 | 2026-10-16 | **检索契约面第一刀**：三个自足件上提 `contract.retrieval`——`RetrieveGraphRepository`（图库写/删/读端口，签名只取 contract.graph 的 NameSpace/GraphData）· `TenantStoreOwnership`（@FunctionalInterface）· `RetrieveEngineException`（哨兵异常族：Kind + 四哨兵 + 分类/重抛工具）；裸引用补 import 10 文件；javadoc 两处跨界 {@link} 改 {@code}。**未动**：`RetrieveEngineRegistry`/`RetrieveEngineService` 取 `EngineTypes.*`（可变形状，待 reshape）；工具面 7 件与 impl 端口待后续批 | 15 守卫绿（环 0）· javadoc 0 错 · 金片 1349 一致 · gate 2m32s / **4997 / 0 / 4** · 冒烟 200 |
| R3a | 2026-10-16 | **VLM 面归位**：`VlmClient`+`VlmHttpTransport` retrieval/vlm → `modelruntime/adapter/provider/vlm`（两测试随迁；传输层 env 面换 `platform.config.AppEnvLookup`，否则 model-runtime → engine/retrieval 成环）；契约面新增 `VlmPredictor` + `ModelRuntime.vlmPredictorFor(ModelRowView)`，装配链（配置/SSRF/观测/命名闸门）整段收进 model-runtime；消费点 `VlmDescriberWiring`/`ModelDebugController` 改走端口；测试重分家（装配断言 → 新 `ModelRuntimeAssemblerVlmTest`，HTTP 形状 → 随迁的 `VlmClientTest`）。**A17 棘轮收紧**：`VlmDescriberWiring` 摘出 SERVICE_TECH_BASELINE（守卫报"基线条目已不再违例"） | 15 守卫绿 · 金片 1349 一致 · json-key 换锚 `--write`（1 出 1 入）· gate 1m46s / **4997 / 0 / 4** · 冒烟 200 · **retrieval 对模型族 import = 0**（R3 验收项达成）· domains 模型族 import 20 → 18 处 |
| R2c-3d | 2026-10-16 | **图片解析桥接归位**：`ChatLocalImageResolverWiring`（domains 存储 ↔ model-runtime `ImageResolver` 静态钩子）+ 其单测 → `boot/config`（跨模块胶水按"由 boot 注入"归组合根）。**收敛判定**：余 20 处 = 4 合法（R5）+ 12 耦合 R3/R4b + 4 Ollama 端口 + 1 PromptCache 拆分的纯函数 | 15 守卫绿 · 金片 1349 一致 · gate 2m21s / **4996 / 0 / 4** · 起服 200 |
| R2c-3c | 2026-10-16 | **thinking 判定收进端口**：`ModelRuntime.effectiveThinkingControl(ChatConfig)`（provider 能力 + extra_config 覆盖的生效解析）；`ModelDebugController` 删静态判定、改调端口 ⇒ 该文件对模型族 import 清零。实测 `ProviderName`/`ProviderRegistry` 跨模块只有 2 处，故不把 34 件的 provider 定义族搬进契约。顺带清 `InitializationController` 死参数（ConcurrencyGovernor 在 R2c-2 后无人用） | 15 守卫绿 · 金片 1349 一致 · gate 2m30s / **4996 / 0 / 4** · 起服 200（死参数清理按纪律只跑编译 + 起服） |
| R2c-3a/3b | 2026-10-16 | **ASR 端口化 + QA 装配走端口**：`AsrTranscriber`（接口 + 三形状 + 两异常）→ `contract.model`；缺省实现抽成独立类 `OpenAiAsrTranscriber`（model-runtime，自持 `@Component`）——原先实现**嵌在接口里**，逼得 InitializationWiring 手工 new 并 @Bean；QaWiring 匿名装配改委托端口（保留自身 wrap 与取数闸门）；顺带修 VlmHttpTransport 对内嵌实现静态方法的限定引用 | 15 守卫绿 · 金片 1349 一致 · gate 2m1s / **4996 / 0 / 4** （首轮两例 Redis 族失败 → 隔离重跑 10/0 绿）· 起服 200 |
| R2c-2 | 2026-10-16 | **六处"自建客户端"改走端口**：端口新增"按行视图的工厂面"四方法（不做取数、不挂观测装饰——取数口径本就因调用方而异，只统一"怎么造"）；改走：DefaultMemory/WikiModelResolver · McpUsageInstructionsOps/Controller · SessionAgentQaService（保留自身 wrap）· ModelConnectivityTestService（chat×2/embedding/rerank）· ModelDebugController。顺带清死码（域侧 appSecret 解密与 provider 分支——解密收进工厂面；对 lkeap/volcengine 现在会给 appId，属修正已注明） | 15 守卫绿 · :domains:javadoc 0 错 · 金片 1349 一致 · gate 2m21s / **4996 / 0 / 4** · 冒烟 200。（改写过程中曾误按截断读取覆盖两个 resolver 全文，已 git checkout 恢复后外科式改写） |
| R2c-1b | 2026-10-16 | **组装权下沉**：`ModelRuntimeAssembler`+`ModelRuntimeConfigs` → `modelruntime.application`（依赖换成契约端口 `ModelCatalogPort`，闸门错误文案仍在装配器拆包）；`LlmTransport`+`Wiring` → `platform.http`（共享裸 HTTP 传输，非模型语义）⇒ **模块内环清零**（2 组→0、间接环 1→0），domains 的 7 个消费点不再 import model-runtime | 15 守卫绿 · 金片 1349 一致 · gate 2m12s / **4996 / 0 / 4**（platform 195→205、model-runtime 239→229）· 起服 200 |
| R2c-1a | 2026-10-16 | 模型运行时端口契约化：`chatpipeline.spi.ModelRuntimePort` → `contract.model.ModelRuntime`（§8.1 目标名）；`VlmClient.VlmConfig` 抽为 `contract.model.VlmConfig`（纯记录 + 两个派生方法）；全仓类型名同步。**发现**：方案 §8.1 的 `ModelAccessResolver` 已被既有 `ModelCatalogPort` 覆盖，无需新建 | 15 守卫绿 · 金片 1349 一致 · gate 2m11s / 4996 / 0 / 4 · 冒烟 200 |
| R4b-1 | 2026-10-16 | **管线 SPI 契约化**：`McpToolingGateway`/`McpToolRegistrationPort`/`ToolRegistryPort`/`WebSearchExecutor` `chatpipeline.spi` → `contract.pipeline`（四者的实现散在 mcp/knowledge/evaluation/agenttools 等域，消费方是管线本体 ⇒ 放实现包里会把整条管线拖成跨域依赖）；`KnowledgeQaEventPort` 未随迁（签名取内部件 `ChatManage`）。契约纯度：两处 `JsonNode` 参数改**不透明 JSON**（`Object`），实现侧还原。**A20 逮到真问题**：`ToolRegistryPort` 的实现原为 `ToolRegistry` 里的包内匿名类（守卫只认顶层类 ⇒ 报"契约悬空"）⇒ 抽成同包顶层 `ToolRegistryPortImpl` + 补包内访问器 | 15 守卫绿 · 金片 1349 一致 · gate 1m51s / **5000 / 0 / 4** · 冒烟 200 |
| R4b-2a | 2026-10-16 | **管线端口束契约化**：`chatpipeline.PipelinePorts`（201 行，嵌套端口束 + 三对齐 record）→ `contract.pipeline`——session 之外的 4 个 api 适配器（knowledge/memory/websearch/datasource）只需这一束 ⇒ 契约化后不再依赖管线本体（本体归位的前置）；`DataAnalysisSession.execute(JsonNode)` → 不透明 JSON；顺带清 4 处"仅注释提及"的无效 import | 15 守卫绿 · 金片 1349 一致 · gate 2m14s / **5000 / 0 / 4** · 冒烟 200 |
| R4b-2b | 2026-10-16 | **webfetch 归位 websearch 域 + 抓取面契约化**：`FetchException` → `contract.websearch`（可机读码是跨域失败词汇）；`Fetcher`/`AgentMarkdown`/`BrowserRenderer` + 测试 → `domains/websearch/fetch`；抓取面拆**两个单方法端口**（各恰一顶层实现，合 A20「唯一实现」）：`PipelinePageFetcher`（15s/100KB）/`ToolPageFetcher`（markdown/2MB/60s/浏览器兜底）；消费点 `PluginWebFetch`（QaWiring 注入）· `WebFetchTool`/`WebSearchTool`（AgentToolBackends 注入）⇒ **agenttools→websearch / session→websearch 两条新域间边均未产生**（R10 仍 6 条） | 15 守卫绿 · 金片 1349 一致 · gate 1m57s / **5000 / 0 / 4**（首轮 1 例 Redis 族失败，隔离 5/0 绿）· 冒烟 200 |
| R4b-2c-1 | 2026-10-16 | **评估侧去管线本体依赖**（本体归位前置）：契约三件 `KnowledgeQaRunRequest`（评估路径实际赋值的字段子集 + 内嵌 `SummarySpec`）/ `KnowledgeQaRunResult`（检索/重排/生成三产物，均为契约形状）/`KnowledgeQaRunner`；会话域实现 `KnowledgeQaRunnerImpl`（顶层，A20 唯一实现）做 请求→`ChatManage` 还原→跑 rag 预设→回带产物；`EvaluationService` 的 `buildChatManage` 换成 `toRunRequest` ⇒ 对管线本体 import 清零 | 15 守卫绿 · 金片 1349 一致 · gate 2m13s / **5000 / 0 / 4** · 冒烟 200 |
| R4b-2c-2 + R6 | 2026-10-16 | **管线本体归位 + `:engine` 解散**：chatpipeline 本体 43 件（`ChatManage`/`EventManager`/`PipelineBuilder`/`PipelineLog`/`PipelineProgress`/`SummaryConfig`/`spi.KnowledgeQaEventPort` + plugin 24 + support 7）→ `domains/session/service/rag`（包名整体换）；测试 9 + 夹具 2 随迁；`:engine` 模块解散（settings/jacoco/`_source_roots`/stray-dirs/test-summary/dev-stale-check/Dockerfile/依赖一并摘除）；补偿：domains/boot 显式声明 `:model-runtime`（domains 那条是过渡边）、engine 的 wire 录制夹具迁入 `domains/src/testFixtures/resources/wire`；**实物损失已修复**：`rm -rf engine` 连带删掉 `engine/src/main/resources/initialization/extract_config.yaml`（致 1249 例失败）⇒ 按"资源随读者走"恢复到 `model-runtime/src/main/resources/initialization/`；换锚：R9 包级 SCC 组名 · json-key 前缀/BASELINE · B93b EVENT_MAP_FILES · 大文件基线 · A17 两个管线插件 | 15 守卫绿（环 0 · R10 仍 6 条）· 金片 1349 一致 · gate 1m35s / **5000 / 0 / 4** · 冒烟 200 · **六模块** |
| R6-1 | 2026-10-16 | **测试期检索依赖收口**（domains 摘掉 `:retrieval-engine`）：四处错位测试归位——`ArtifactReferenceRewriterTest` → retrieval-engine（随迁录制夹具 `contracts/w5g3c-artifacts.tsv`，资源随读者走）· `HybridResultOpsContextExpansionTest` → retrieval-engine（`TenantService` → 契约 `TenantConfigLookup`）· `WeaviateGqlTest` → retrieval-engine · `EffectiveEnginesTest` → **boot**（断言"租户实体默认值 = 引擎 defaults"，属跨模块一致性）· `ClasspathResourcesTest` → **boot**（核对全模块 classpath 资源）；四处 mock 实现类 → mock 契约端口（`HybridSearchService`→`HybridSearchPort` ×3 · `VectorStoreService`→`VectorIndexStore`；故障改抛契约 `RetrieveEngineException(Kind.VECTOR_STORE_UNAVAILABLE,…)`）。**domains 对检索模块主码与测试皆零依赖** | 15 守卫绿 · 金片 1349 一致 · gate 1m56s / **5000 / 0 / 4** · 冒烟 200 |
| R6-2 | 2026-10-16 | **模块图守卫（第 16 个）+ 夹具包改名**：`scripts/check-module-graph.py` 四条判据——A 主码依赖矩阵（16 条实测边全在方案矩阵内；`boot → agent-engine` 声明但为 0）· B 主码不得跨模块同包（0）· C 测试期边 ⊆ 矩阵 ∪ 登记表（两条登记附理由）· D 夹具不得占用别模块主码包（根包 `com.ragagent` 豁免）+ D2 同一 FQN 不得跨模块（0）。**为什么不用 ArchUnit**：它看类/包看不到 Gradle 模块边界。夹具包改名：`com.ragagent.agenttools`/`com.ragagent.support` → **`com.ragagent.agent.fixtures`**（72 文件机械改名；原先与 domains 主码同名 = split package 隐患） | **16 守卫全绿** · 金片 1349 一致 · gate 1m55s / **5000 / 0 / 4** · 冒烟 200 |
| R6-3 | 2026-10-17 | **测试归位（O12）**：contract 2、modelcontext 3、platform 2 共 7 个测试迁到目标模块；`ParserEngineRulesTest` 需 platform 的 `JsonMappers` ⇒ 落 platform（platform test → contract 合法）。剩余 10 个跨模块测试（platform 5 / model-runtime 4 / contract 1）依赖 domains 载荷，迁走会形成反向边 ⇒ 登记 `MISPLACED_TEST_ALLOW`（附理由，只许减；新增/失效均红）。夹具包 `com.ragagent.common` / `com.ragagent.support` → **`com.ragagent.fixtures`**（18 处 import 同步）。模块图守卫加判据 E + 幽灵夹具包硬零。 | **19 守卫全绿** · **4867 / 0 / 4**（条数守恒：domains 2746→2699、platform 219→225、model-runtime 241→271、contract 0→11）· build 1m55s |
| R6-4 | 2026-10-17 | **重复类与命名尾巴（O13）**：`LogSanitizer` 只保留 platform 版（删 domains 副本，测试改 `sanitize`）；`modelcontext.JsonValues/HtmlEntities` → `ModelJsonValues/HtmlCodec`；websearch `ZhipuProvider` → `ZhipuWebSearchProvider`；`WikiPageServiceImpl` → `DefaultWikiPageService`（file-size 基线同步）；9 个锁字段 `mu` → `lock`；`PluginError.err` 私有 + `cause()`；新增 `check-duplicate-classes.py` 硬零（2039 个简单类名唯一）。 | **20 守卫全绿** · **4867 / 0 / 4** · build 2m07s |
| R6-5 | 2026-10-17 | **ChatManageFactory（O14）**：40+ setter 收成 create / applyRetrieval / applyModel / applyGeneration / applyWeb / summaryConfig；`ChatManage` 保持可变（录制等价性测试依赖），服务层装配从 40 行降到 6 行。 | **20 守卫** · **4867 / 0 / 4** · build 2m12s |
| R6-6 | 2026-10-17 | **Go 录制外置（O15）**：7 个 `GoRecording*` 的 JSON 外置到 `platform/src/testFixtures/resources/golden/`，`GoldenResources` 按 SHA-256 加载；Java 4448→1294 行，资源 956KB；tamper 探针（改一个字节）测试红；类名行为化留 O16。 | **20 守卫** · **4867 / 0 / 4** · build 2m06s |
| R6-7 | 2026-10-17 | **迁移代号清理（O16）**：7 个 `GoRecording*` 改为行为名（`AgentPromptGolden` / `AgentExecutionGolden` / `ToolContractGoldenA/B/C` / `ChatPipelineGolden` / `ModelContextGolden`）；主码 168 文件注释中 195 处批次号/日期戳/§ 引用清零；新增 `check-internal-codes.py` 硬零（C1/C2）；`packages.md` 重生成。 | **21 守卫** · **4867 / 0 / 4** · build 2m34s |
| R6-8 | 2026-10-17 | **模块/包映射（O17）**：新增 `scripts/gen-module-map.py` 生成 `docs/architecture/module-package-map.md`（7 模块包根/包数/文件/控制器 + 22 域文件/控制器）；`--check` 接入第 22 条守卫（改包/加控制器不更新文档即红）；docs/README 与 overview 加链接。 | **22 守卫** · **4867 / 0 / 4** |
| R6-9 | 2026-10-17 | **版本升级评估（O18）**：临时 worktree 实测 Boot **3.5.16** / springdoc **2.8.17** / MyBatis-Plus **3.5.17**；主码/测试码编译通过，测试 **4867 / 4 fail / 4 skip**，4 fail 均为 Jackson 2.19 非法 JSON 列号 4→1；列出 5 类必需改动（jsqlparser 拆分、JUnit launcher、Jackson fields()、Spring 方法校验 API、4 条契约断言）；**未改主工作区版本**。 | 评估完成；建议按 O18a 落地 |
| R6-10 | 2026-10-17 | **O18a 落地**：Boot **3.5.16** / springdoc **2.8.17** / MyBatis-Plus **3.5.17**；`mybatis-plus-jsqlparser` 独立 artifact、test-conventions 显式 JUnit launcher、Jackson `properties()`、Spring 6.2 `getParameterValidationResults()`、4 条 Jackson 列号 4→1 契约断言；build 3m07、**4867 / 0 / 4**、**22 守卫**；Swagger 冒烟 OpenAPI 3.1.0（disabled 404 / enabled 200+302）。 | 升级完成；`@MockBean` 迁移留独立提交 |
| R6-11 | 2026-10-17 | **CI smoke（O19）**：新增 `check-version-consistency.py`（Boot/springdoc/MyBatis-Plus jsqlparser/README/wrapper/Dockerfile，第 23 条守卫）与 `scripts/ci-smoke.sh`（真实 boot jar：/health 200、Swagger 默认 404、进程可停）；CI backend 增加启动冒烟，guards 标签改为单一入口。 | **23 守卫** · **4867 / 0 / 4** · smoke 本地通过 |
| R6-12 | 2026-10-17 | **Scalar UI 替换 Swagger UI**：springdoc 改 API-only starter（移除 swagger-ui webjar）；新增 `ScalarUi` 条件 bean + `scalar/index.html`（CDN `@scalar/api-reference`，data-url=/v3/api-docs）；AuthFilter 白名单改 `/scalar`；默认关闭时 `/scalar` 与 `/v3/api-docs` 均 404。 | **23 守卫** · **4867 / 0 / 4** · build 2m12 · 冒烟 /scalar 200（enabled）/404（disabled） |
| R6-13 | 2026-10-17 | **Boot 4 评估**：目标 Boot 4.1.1 / Spring 7 / Jackson 3 / Jakarta EE 11 / JUnit 6；生态就绪（springdoc 3.1.1、MyBatis-Plus Boot4 3.5.17），但主码 **651 文件 / 1329 处 databind import** 需迁 `tools.jackson`（annotations 179 处不变）；另需 `javax.inject` 6 处、Jackson2 customizer 1 处。**3.5 OSS 已于 2026-06-30 结束**（3.5.16 为最后 OSS 版本）；无商业支持则 Boot 4.1 迁移应近期排期。 | 评估完成；独立分支迁移 |
| R6-14 | 2026-10-17 | **v0.4.1 Boot 4 空跑**：Boot 4.1.1 / springdoc 3.1.1 / MyBatis-Plus Boot4；starter-aop→aspectjweaver、Tomcat 包迁移、AutoConfigureMockMvc→spring-boot-webmvc-test、RequestBuilder 签名；Jackson 2 兼容桥（spring-boot-jackson2 ObjectMapper、JsonNode ser/deser、ContractJsonMixins + Jackson3 模块）；Jackson 3 默认行为对齐；wiki 空列表契约更新。 | **23 守卫** · **4867 / 0 / 4** · build 2m11 · Scalar enabled 200 |
| R6-15 | 2026-10-17 | **去 spring-boot-jackson2**：删除 Boot 4 的 Jackson 2 自动配置依赖，WebConfig 显式提供 `JsonMappers.lenient()` 的 ObjectMapper bean；业务仍用 Jackson 2，Jackson 3 迁移完成后再删该 bean。 | **23 守卫** · **4867 / 0 / 4** · build 2m10s |
| R6-16 | 2026-10-17 | **v0.4.2 Jackson 3 全量迁移**：666 个 Java 文件包名/API 切到 `tools.jackson`，移除 Jackson 2 依赖、`spring-boot-jackson2` 与过渡桥；MyBatis `PgJsonTypeHandler` 改继承 `AbstractJsonTypeHandler` + `JsonMappers.lenient()`；补齐 Jackson 3 的日期/排序/空值行为；错误消息（byte offset）与 wiki 空列表契约按实际线格式更新。 | **23 守卫** · **4867 / 0 / 0** · build 1m52 · Scalar enabled 200 |
| R5c-1 | 2026-10-16 | **引擎配置读面契约化**：`contract.agent.AgentRunSpec`（16 getter + 2 派生 default + `UNLIMITED_MAX_ITERATIONS`）。为什么是接口不是 record：域侧配置要**从 agent 配置 jsonb 反序列化填充**（需 Jackson 注解，契约零依赖不能带），而引擎**只读**（实测无 `config.setXxx`）⇒ 契约收读面，两侧各自持有可变实现。`AgentConfig implements` 之（注解/setter 原样）；`AgentEngine` 的字段与构造参数改读面类型；`QaAgentConfig` **停止继承**、自持 16 字段存取（getter 名不变 ⇒ 会话侧零改动） | 15 守卫绿 · 金片 1349 一致 · gate 2m45s / **5000 / 0 / 4** · 冒烟 200 |
| R5c-2/3 | 2026-10-16 | **装配权下沉 + 过渡边摘除（R5 收官）**：契约三件 `AgentEngineFactory`/`AgentEngineRequest`（16 字段）/`AgentEngineHandle`（域侧真用 4 方法）；agent-engine 新增 `AgentEngineFactoryImpl`（注册表/内置工具/MCP/技能装载/模板/pinned/引擎构造/图片描述器）与 `AgentEngineHandleAdapter`（唯一适配器）；domains 的装配器改为"`planTools`（允许清单+wiki 上下文，私有记录）→ `buildTools`（外部工具列表）→ `wireWebPageFiles`（列表版就地接线）→ 请求 + 工厂"；**A18 语义原样保留**：新增 `AgentExecutionException`，适配器包裹引擎异常（message 逐字）⇒ 两级 catch 逐条不变（A18a 登记适配器并写明理由）；三个错位测试随类迁回 agent-engine；**domains 摘掉主码过渡边**（仅留 fixtures 面） | **验收：domains 主码对 agent-engine import 55 → 0 处/0 文件** · 15 守卫绿（SCC 9 组不变 · A20 两端口各一实现）· 金片 1349 一致 · gate 2m11s / **5000 / 0 / 4**（全量重跑 5004，差 4 例=随迁测试方法数 1+1+2）· 冒烟 200 |
| R5b-1 | 2026-10-16 | **agent 纯形状/纯工具上提 `contract.agent`**（12 件，零同包耦合）：`SearchAuth`（456：检索范围授权 + 4 view + 4 fetch 口）· `ThinkBlocks` · `ToolResultPersist` · 7 个 prompt 形状 · `CompactionSettings`/`CompactionOverflow`。试搬时被编译器逮到 2 件不纯并退回（`DocChunkSupport` 用 `platform.web.JsonMappers`；`Skill` 依赖包私有 + snakeyaml 的 `SkillFrontmatter`）⇒ 留后续片。**踩坑三例**：① **前缀替换误伤**（`Skill` 前缀连带命中 `SkillReadFileTool`）⇒ 修回 + 写"引用真实性校验"脚本复核 5 包 133 名 ② spotless 自报可修 ⇒ 用 `spotlessApply` + 检查 diff 只含 import 行 ③ 两处 `{@link}` 迁移后不可见 ⇒ 改 `{@code}`。**既有边界守卫 B0 按设计报红**（80→68 件 < 70 阈值）⇒ 阈值同步 60 | 15 守卫绿（R9 SCC 9 组不变）· 金片 1349 一致 · gate 1m57s / **5000 / 0 / 4** · 冒烟 200；domains 对 agent-engine **55 → 16 文件** |
| R5b-2 | 2026-10-16 | **预算/转向/清理面 + ImageDescriberFunc**：4 件纯件 → `contract.agent`（`SteerSink` 47 · `AgentBudgets` 46 · `OutputBudgets` 60 · `Cleanable` 11）；`AgentEngine.ImageDescriberFunc` 嵌套接口**抽出**为契约类型（`VlmDescriberWiring` 对 AgentEngine 的 import 随之清零）。`skills.Manager`/`DbSkillSource` 因**状态化**（锁+缓存）与依赖目录服务**未搬** | 15 守卫绿 · gate 2m20s / **5000 / 0 / 4** · 冒烟 200；消费面 **46 → 40 处 / 16 → 11 文件** |
| R5b-3 | 2026-10-16 | **`DocChunkSupport` → `platform.knowledge`**（288 行；与 `ImageInfoMatchUtil`/`ChunkSearchUtil` 同族：对契约形状做技术性组装）。两个嵌套 record 改名过 C8 红旗：`ImageInfoView`→**`ImageInfoRow`**、`KnowledgeInfoView`→**`KnowledgeInfoRow`**（改名不改职责，同 R3d-5 的 `GrepChunkView`→`GrepChunkHit`）。手法复盘：**点号形式的嵌套引用**（`DocChunkSupport.ImageInfoRow`）需单独一遍替换（我的负向后顾刻意避开点号 ⇒ 漏了嵌套类型导入，编译器立刻报 4 处） | 15 守卫绿（C8 通过）· gate 2m20s / **5000 / 0 / 4** · 冒烟 200；消费面 **40 → 18 处 / 11 → 9 文件** |
| R5b-4 | 2026-10-16 | **技能目录端口化**：新增 `contract.agent.SkillCatalogPort`（7 方法：listVisible/findVisibleById/create/update/softDelete/listReferences + `skillInstructions`【原域侧 `Skill.parseSkillFile(content).instructions`】）；随迁既有类型（按块抽取、语义逐字保留）：`SkillRow`（含 `platform()`）· `SkillReference` · 四异常 · `SkillValidationException` + `SKILL_FILE_NAME` 常量。`SkillCatalogService implements` 之；消费点三处 + 装配器收端口。**踩坑两例**：① 嵌套类型的 **import 形式** 被替换带偏 ⇒ 改 FQN + 8 文件补外层 import ② 控制器 `skillBody` 原 static 却要用实例字段 ⇒ 改实例方法 | 15 守卫绿（A20 唯一实现）· gate 2m31s / **5000 / 0 / 4** · 冒烟 200（含 skills/catalog）；消费面 **18 → 13 处 / 9 → 3 文件** |
| R5a | 2026-10-16 | **切出 `:agent-engine`**：`com.ragagent.agent` 整树（main 80 / test 33）domains → agent-engine，包名不变；出边实测极干净（contract 139 / platform 51 / modelcontext 3，**零业务域**）⇒ 依赖按实测声明（contract/platform api + `:model-runtime`（modelcontext）+ Jackson/jtokkit/MyBatis-Plus），启用 `java-test-fixtures`；录制夹具 4 件随迁（只依赖 platform）⇒ domains 以 `testFixtures(:agent-engine)` 消费（同向不成环）；七处模块清单同步；domains 显式 `implementation(:agent-engine)`（**过渡边**，55 消费文件待 R5b/R5c）；**R10 域口径同步**（agent 摘出 DOMAINS + 族收敛）⇒ 6 条边 → **3 条**（owner 1 + 族内 2）；**既有边界守卫 `AgentEngineBoundaryTest` 随迁更新**（扫描根 → 新模块；白名单摘 `agenttools`（业务域、实测 0 引用 ⇒ 收紧）与已归位的 `chatpipeline`/`webfetch`；下限 55→70（实测 80）；管理面路径改仓库相对）；大文件基线纯换锚 | 15 守卫全绿 · 金片 1349 一致 · gate 2m / **5000 / 0 / 4** · 冒烟 200（**七模块**）|
| R2c-3d-① | 2026-10-16 | **Ollama 管理面端口化**：契约面 `OllamaModelInfo`（record）上提 + 新 `OllamaPullProgress`/`OllamaProgressListener`/`OllamaAdminPort`（六方法=域侧真调用面）；端口方法名 `pullModel`（与实现侧内部 `pullWithProgress(String, ProgressCallback)` 区分——同名同元数下两个函数式接口会让 λ 歧义，实测踩到）；进度回调**不再吃原始 JsonNode**（域侧原先自己 `path("total")`）；**`InitializationWiring` 删除**——它挂在业务域的两个 @Bean（`OllamaService`/`ExtractPrompts`）都是模型运行时的件 ⇒ 迁 `boot/config/ModelRuntimeBeansWiring`；`OllamaService` 619 行（+15 行端口桥接）超 600 阈值 ⇒ 按守卫指引在 `check-file-size.py` 头写明**内聚论证**后登记；顺带清 3 条陈旧 json-key 登记 | 15 守卫绿 · 金片 1349 一致 · gate 2m4s / **5000 / 0 / 4** · 冒烟 200 |
| R2c-3d-②③ | 2026-10-16 | **提示词/配置面归 platform.prompt**：`ExtractPrompts`（config.yaml 的 extract 段装载）与 `PipelineConfig`（管线配置）`llm/extract` → `platform.prompt`（消费方横跨 knowledge/initialization/session 三域，platform 是唯一都够得着又不该进契约的落点；`extract_config.yaml` 随读者迁）；`PromptCache` 的**纯函数族**（`fingerprintPromptPrefix`/`promptPrefixFingerprint`/`buildPromptCacheKey`）拆出为 `platform.prompt.PromptFingerprints`——整件迁不了（另一半方法吃 `llm.provider.ProviderName`），故拆纯函数、`PromptCache` 三方法改薄委托（内部零改动）、wiki 侧 5 处调用改走 platform；`ClasspathResourcesTest` 读者标签更新；json-key 换锚（提取器 5 键=存量 yaml 配置键）+ 冻结快照 | 15 守卫绿 · 金片 1349 一致 · gate 2m15s / **5000 / 0 / 4** · 冒烟 200 |
| R2c-3d-④ | 2026-10-16 | **实体关系抽取端口化**（domains 对模型族过渡边清零）：契约面 `contract.graph.EntityExtractionResult`（只读 record，替代域侧直读实现内部类的可变字段）+ `contract.knowledge.EntityExtractionPort`（模板按**不透明配置载体**传入）；model-runtime 新 `EntityExtractionAdapter`（A20 唯一实现，内部形状不出模块、实现零改动）；三消费点改走端口（`ChunkExtractService` 两构造对齐 · `TextExtractionTestService` 的过滤改对列表就地过滤 · `PluginExtractEntity` + `QaWiring` 注册行）；`ChunkExtractServiceTest` 注入真实适配器（假模型驱动真解析，行为不变）；**R9 包级 SCC 再次变形**（契约词汇组加 contract.knowledge，已在守卫脚本头登记理由） | 15 守卫绿 · 金片 1349 一致 · gate 2m1s / **5000 / 0 / 4** · 冒烟 200 |
| R4a | 2026-10-16 | **tracing 归位**：6 个 Langfuse 装饰器 → `platform.tracing.decorators` + 3 个契约流句柄（QueuedChatStream/SyncChatStream/StreamLifecycle）→ `platform.stream`（装饰器要建/转发流 ⇒ 句柄须在更低层）；错位测试随迁；engine 的 tracing 目录清空。**本批是 R2c 的解阻塞项**（装饰器已只依赖契约面）。顺带换锚 ArchUnit A16 禁用前缀（不改则 service 违规静默失明） | 15 守卫绿 · json-key 换锚（5 出 5 入）· 金片 1349 一致 · gate 2m4s / **4996 / 失败 0 / 跳过 4**（platform 188→195、engine 322→315）· 冒烟 200 |
| R2b | 2026-10-16 | **`:model-runtime` 模块切出**：五包（llm/modelruntime/embedding/rerank/modelcontext）148 件主源码 + 19 件测试 `git mv`；模块清单四处同步（settings · `_source_roots.py`（守卫单一来源）· 根 jacoco 聚合 · stray-dirs/test-summary/dev-stale-check）；engine 过渡期 `api(:model-runtime)`；**Dockerfile 顺带校正**——原写已裁撤的 `common/` 且漏拷 contract/platform，`docker build` 必失败 | 15 守卫绿（R9 SCC 9 组不变 · 域间契约 6 条不变）· 大文件守卫按路径换锚 4 条（另回退 8 处无关数值刷新）· 金片 1349 一致 · gate 2m9s / **4996 / 失败 0 / 跳过 4**（model-runtime 实跑 239，engine 561→322 ⇒ 搬迁测试真跑）· 起服冒烟 200 |
| R2a-2 | 2026-10-16 | 模型面端口与结果形状上提：`embedding.Embedder`（9）/`rerank.Reranker`（8）/`rerank.RankResult`（3）→ `contract.model`；**RankResult 拆分**——纯形状进契约面，parse/marshal 移实现侧新件 `rerank.RankResultCodec`；@JsonProperty 对位进 `ContractJsonMixinsModel`（含内嵌 DocumentInfo）；同包裸引用 7 处补 import | 15 守卫绿 · 金片 1349 一致 · json-key 换锚（离开 1 / 进入 2）· gate 1m55s / **4996 / 失败 0 / 跳过 4** · 冒烟 200 |
| R2a-1 | 2026-10-16 | 模型运行时而契约化：`llm.LlmChatClient`（35 域内消费）/`llm.ChatStream`（3）/`llm.domain.StreamResponse`（8，SSE 帧核心）→ `contract.model`；StreamResponse 的 7 处 `@JsonInclude(NON_EMPTY)` 对位进 `ContractJsonMixinsModel` | 15 守卫绿 · 金片 1349 一致 · gate 2m12s / **4996 / 失败 0 / 跳过 4** · 冒烟 200。**R9 包级 SCC 8→9**（父子包内聚组 {contract.model, contract.model.chat}，理由已登记守卫脚本头；同类先例 3 组） |
| R1 收官 | 2026-10-16 | **R1 余项判定**（勘察取证 + 用户拍板）：① 检索公开面 → **并入 R3**（域侧直注实现类 `CompositeRetrieveEngine`12/`HybridSearchService`10/`VectorStoreService`9；索引面签名穿透 `Embedder`{模型族}与可变 setter `IndexInfo`；方案点名的 SearchHit/IndexService/SearchPolicy 全仓无对应物、域侧无消费者）② `contract.agent` 6 件 → **并入 R5**（跨域消费者仅 `boot/.../EngineRecordingTest`；AgentEngine 唯一生产调用方 `SessionAgentQaService` 在同模块；六件全仓无对应物）③ `contract.execution` → **判"不建"**（`contract.tool.ToolCancellation`/`ToolExecContext` 已在，引擎另有 `Supplier<String> cancellationSource`；进度为事件流无 sink 需求） | 判定入 §2.9 + 计划 v2 R1 行；R5 新情报：`AgentEngine` 直连 `platform.tracing.langfuse` 与 `platform.access/event`（拆 agent 时 platform 边比 engine 边重） |
| R1b-1 | 2026-10-16 | 契约面先行第二件：`retrieval.domain` 整包 4 件（WebSearchResult/Filters/Request/ImageInfo）→ `contract.websearch`（§8.1 目标落点）；ImageInfo 注解对位进 `ContractJsonMixinsWebSearch`；修 `AgentReferencesData` 陈旧 javadoc | 15 守卫绿 · 金片 1349 一致 · json-key 换锚 `--write`（离开 1 / 进入 1）· gate 1m57s / **4996 / 失败 0 / 跳过 4** · 冒烟 200；首轮闸门 `ImRedisStoreTest` 单例失败 → 隔离重跑 7/0 绿（已知 Redis 瞬态族） |
| R1a | 2026-10-16 | 契约面先行第一件：`llm.domain` 11 件聊天形状 `git mv` → `contract.model.chat`（ChatMessage/ChatOptions/ChatResponse/ChatTool/FunctionCall/FunctionDef/ImageUrl/MessageContentPart/ToolCall/CacheRetention/ChatConfig）+ Jackson 注解逐条对位进 `platform.web.ContractJsonMixinsModelChat`（ContractJsonModule 注册）+ 4 处不透明 JSON 字段改 `Object`（format/parameters/providerMetadata）· **逐件过跨域消费者判据**（ImageUrl 为 MessageContentPart 公开形状件随迁；StreamResponse/ProviderMetadataMaps 留 R2 公开面分层） | 15 守卫全绿（金片 1349 一致 · R10 无新增边 · C8 无新增）· json-key 换锚 `--write`（离开 5 / 进入 1，均换包）· gate 2m15s / **4996 / 失败 0 / 跳过 4** · 定向 engine 456 + domains 266 · 冒烟 sessions/knowledge-bases/models 200（后端已重启） |

| L1a | 2026-10-18 | **迁移叙事守卫上线（v0.5.0 起点）**：新增 `scripts/check-comment-narrative.py`（只扫注释行；拦退役/备案/Go 锚点/迁移期/旧实现五类；基线 196 处 / 147 文件，棘轮只许减）；红态探针（`BaseTool` 加一行叙事 → 红 → 恢复绿）；接入 `run-guards.sh`（**24 守卫**）；新增 `docs/internal/plans/readability-remediation-v0.5.0.md`（L1~L22）并登记 §5.0。 | **24 守卫全绿**（纯脚本/文档，全量闸门并入 L1b 验证） |
| L1b | 2026-10-18 | **迁移叙事清理第一批（mcp / vectorstore / wiki）**：23 处 / 12 文件——历史说明删除、当前约束改成现在时、业务语义换中性词（连接"退役"→"关闭"、工具"退役"→"从快照移除"）；顺带修正 `WikiPageController` 引用的已不存在包 `com.ragagent.org`；基线 196 → 173。 | **24 守卫** · **5022 / 0 / 4** · spotlessCheck build 1m58s |

| L1c | 2026-10-18 | **迁移叙事清零收尾**：137 文件 / 159 处——备案→实现边界、退役→移除/关闭、旧版→早期版本或回落路径、迁移期与 Go 锚点删除；修 `KnowledgeBaseService` 的 `\uXXXX` 转义乱码段与 14 处替换后语句不通。**守卫升级为硬零**（基线 `{}`），红态探针复验（加一行叙事即红）。 | **24 守卫** · **5022 / 0 / 4** · spotlessCheck build 2m29s |

| L2 | 2026-10-18 | **行号引用与 Go 语法锚点清零**：16 处注释把 `L141-144`/`L100/L129`/`L2302` 改为类名/方法名指路，删 `uuid.New()`/`Order(...)`；8 个构建脚本的批次号/日期戳/§ 引用清零，`platform` 与 `contract` 头部注释按现状重写。守卫扩面：`check-go-anchors` 增加行号与 Go 语法模式（只扫注释行），`check-internal-codes` 增加 C3 覆盖 `*.kts`；两条各验红态探针。 | **24 守卫** · **5022 / 0 / 4** · spotlessCheck build 2m22s |

| L3+L4 | 2026-10-18 | **文档失真修正 + package-info 补齐**：README 统一外壳补「基础设施端点除外」、删除 `frontend/pnpm-workspace.yaml`（npm 残留）；`Session` 的「裸对象」改为 `ApiResultAdvice` 包壳、`SessionKnowledgeQaService` 删「hybridSearch 空实现」、`RagAgentApplication` 的 `common/*` 改 `platform` 配置包。**生成 156 个 package-info**（职责＝包内主要类型、依赖＝跨模块 import、入口＝Controller/Service/Repository），覆盖率 48%→100%（322 包）；`packages.md` 与 `module-package-map.md` 重生成；新增 `check-doc-truth.py`（D1 模块数/D2 控制器数/D3 package-info 覆盖），**第 25 条守卫**。 | **25 守卫** · **5022 / 0 / 4** · build 2m10s |
| L5 | 2026-10-18 | **注释装饰清理**：删 491 行纯装饰分隔线（`// ═══`/`// ===`/`// ---`），216 行带文字分隔线统一为 `// ── 段名 ──`；新增 `check-comment-decor.py`（**第 26 条守卫**，红态探针已验）。**不设注释密度硬指标**——密度会奖励删信息，本仓注释承载契约与线程纪律（实测 18.6%，最大类头 63 行，分布健康）。 | **26 守卫** · **5022 / 0 / 4** · build 3m04s |

| L6~L8 | 2026-10-18 | **缺口登记 + 死代码 + 日志分级**：新增 `docs/known-gaps.md`（18 项，状态/影响/替代/涉及类）；9 处类注释加指针、6 处误报改措辞；新增 `check-known-gaps.py`（第 27 条）。删 `@Deprecated getDocumentRawContent` + 2 个未用 record + 空包 `platform/session`，全仓 `@Deprecated` 归零。日志：116 处 warn 降级（95 debug / 21 info），warn 893→777；新增 `check-log-level.py`（第 28 条，warn 不得用于 404/跳过）。 | **28 守卫** · **5022 / 0 / 4** · build 2m48s |

| L9 | 2026-10-18 | **内联纯转发的 500 包装**：12 处 `catch (RuntimeException) { throw toInternal(e); }` 删除（MessageController / SessionController / TemporaryDocumentController / VectorStoreController）；保留带业务语义的 catch（notFound 转换、上下文前缀、条件分支）。WebSearchProviderController 保持原样（5 条金片钉住其 500 文案）。 | **28 守卫** · **5022 / 0 / 4** · build 2m15s |
| L10a | 2026-10-18 | **删除两个纯字符串异常**：`PlainErrorException`（22 处）→ `BizException` 工厂、`GuardForbiddenException`（7 处）→ `BizException.forbidden`；删两个类 + `GlobalExceptionHandler` 两个 handler（-40 行）；金片 8 条（`Forbidden:` 前缀去掉）、测试断言 1 条、前端 4 处 `data.error.message`→`data.message`。 | **28 守卫** · **5022 / 0 / 4** · build 1m58s · 前端 type-check 0 |
| L10b | 2026-10-18 | **删除 Wiki 的 RawJsonError 转译补丁**：43 处 `new RawJsonError(...)` → `BizException` 工厂 / `AppError.ofHttpStatus`；删嵌套异常类与 `@ExceptionHandler`；`WikiRequestSupport` 三个返回类型改 `BizException`；测试断言 3 处。**补 `docs/api-response-convention.md`**（此前被 5 处代码引用但文件不存在）与 baseline 两条例外理由。 | **28 守卫** · **5022 / 0 / 4** · build 1m53s |

| L11 | 2026-10-18 | **错误响应语法糖清理 + 守卫评估**：`DataSourceController` 35 处 `return error(400, msg)` 改 `throw BizException.*`（原写法像"返回错误"实为抛异常），删私有 `error()`。守卫评估结论：规则二已覆盖 domains 全量；规则一（catch 内清空过滤）扩面会误报 4 处（坏 JSON 宽松读），**不扩**。 | **28 守卫** · **5022 / 0 / 4** · build 1m52s |

| L12 | 2026-10-18 | **控制器纯度守卫 + 数据访问定规**：`check-controller-purity.py`（第 29 条；controller 禁直连 Mapper 硬零 + `catch (RuntimeException)` 棘轮 139 只许减，红态探针已验）；`mechanisms.md` §9 记录 Mapper（76）/Repository（28）的选择规则。**实测 controller 直连 Mapper 为 0**（此前统计的 22 处是 `ObjectMapper` 误报）。 | **29 守卫** · **5022 / 0 / 4** |
| L14 | 2026-10-18 | **事务模板收敛**：`DataSourceTxTemplate` / `ChunkTxTemplate` 合并为 `platform.persistence.TxTemplate`（Runnable + Supplier 两法），两个 Repository 改注入，删两人类；`MemoryTxTemplate` 保留（`FOR UPDATE` 行锁语义不同）；`KnowledgeProcessWorker` 补「为什么整链不包大事务」的边界说明。 | **29 守卫** · **5022 / 0 / 4** · build 2m18s |

| L16 | 2026-10-18 | **`executeQA` 拆分**：200 行 → 7 个具名步骤（memo 异步写 / 并发守卫 / 消息落库 / steer 交接 / 建流回滚 / 快答 handler / 异步执行），每步带"做什么 + 为什么"的 javadoc，顺序约束写进方法注释。纯重构，行为由 5022 条测试锁定。 | **30 守卫** · **5022 / 0 / 4** · build 1m48s |
| L17+L18 | 2026-10-18 | **控制流可见化**：新增 `gen-pipeline-map.py`——从 `activationEvents()` + `QaWiring` 注册序 + `ChatManage` getter/setter 生成 `docs/architecture/pipeline-map.md`（14 事件 / 17 插件 / 读写字段），接入第 30 条守卫（红态探针已验）；`Plugin` 接口补「读 / 写 / 失败」三条契约，plugin 目录 package-info 指向事件表。 | **30 守卫** · **5022 / 0 / 4** |

| L19 | 2026-10-18 | **拆出 `:knowledge`**：241 件主码 + 22 件测试（156 用例）迁出 domains；主码对 domains **0 出边**，测试期 4 处借 domains 存储/租户实现作夹具（单向）；新增模块构建（含 docreader proto）；守卫同步：模块图矩阵 + 测试边登记 + 域清单 + file-size 基线 + README 八模块/51 控制器；修一处跨模块 javadoc 引用。 | **30 守卫** · **5022 / 0 / 4**（domains 2543 + knowledge 156）· build 2m16s |
| L20~L22 | 2026-10-18 | **评估后不做**（三条硬约束）：L20 `modelcontext` 进 contract 会被 Jackson 破坏纯 JDK 红线；L21 platform 的 9 个业务工具被 6 个模块共享，移走会让 retrieval/agent-engine 反向依赖；L22 `BaseTool` 移 agent-engine 会让 domains 反向依赖（R5c 刚清零）。三者都是「移动即破红线」，不是成本问题。 | — |

| L23 | 2026-10-18 | **回退 `:knowledge` 合回 domains**（八模块 → 七模块）：拆分只带来看独立编译，知识库代码仍分散在 knowledge/retrieval/contract/platform 四处；11 个知识相关 Port 本就在跨域，收益小于「知识库到底在哪」的认知成本。244 主码 + 22 测试迁回，README/守卫/基线同步。 | **30 守卫** · **5022 / 0 / 4** · build 1m47s |
| L24 | 2026-10-18 | **Port 层治理**：① 删 `AuthTokenAdminPort`（auth 域内两级转发，直接内联到 `AdminUserAdapter`）；② 新增 `scripts/gen-port-map.py` 生成 39 个 Port 的「接口 → 实现 → 调用方 → 是否跨界」表（跨模块 8 / 域间 30 / 域内 1），接入**第 31 条守卫**（红态探针已验）。**两项取消**：`BaseTool` 移 agent-engine 会让 domains 反向依赖（R5c 回退）；`approval` 是纯 Port 实现域（无 HTTP/service），无需分层模板。 | **31 守卫** · **5022 / 0 / 4** · build 1m43s |

| L25 | 2026-10-18 | **分层解耦（规则 + 第一批）**：`check-domain-contracts` 的 `tier_of` 支持族定义推导（两端同族即豁免，**含新增边/新增类**）；`check-package-cycles` 族内双向环/间接环/包级 SCC 豁免；族映射改为**多族归属**（`agentmanagement` 同属 agent-runtime 与 identity）。删 4 个 memory 族内 Port（Extraction/Recall/Search/SessionMessage），`MemoryAccessAdapter` → `MemoryAccess`、`SessionMessageView` 提为独立 record。**identity 族 4 个 Port 未删**：`AdminUserPort` 内嵌 Page/CreateResult/常量，类型搬迁成本大于「-4 接口」收益。 | **31 守卫** · **5022 / 0 / 4** · build 1m41s |
| L26 | 2026-10-18 | **问答主链路一页纸**：新增 `docs/architecture/qa-flow.md`（Mermaid 时序图、4 入口判定、14 事件顺序、`executeQA` 七步表、改功能映射表、三条不变量、三个「去哪找」入口）；新增 `check-qa-flow.py`——文档里 `{@code Xxx}` 引用的类必须真实存在（红态探针已验），接入**第 32 条守卫**。 | **32 守卫** · **5022 / 0 / 4** · build 1m37s |

| L27 | 2026-10-18 | **identity 族内 4 个 Port 内联**（第二批解耦）：先把嵌套类型提为独立文件（`AdminUserPage` / `CreateUserResult` sealed / `AuthUserErrors` / `AuthUserView` / `ApiKeyAuthRequest` / `ApiKeyAuthResult` sealed），再删 `AdminUserPort` / `ApiKeyAdminPort` / `UserLookupPort` / `ApiKeyAuthPort`；4 个实现去 implements、6 处调用方改直接注入。**`contract` 的 `*Port` 39 → 31**；域间边族内 6 条 / 28 类。教训：删 Port 的真实成本在「接口里内嵌的 View/sealed/常量」，不在方法签名。 | **32 守卫** · **5022 / 0 / 4** · build 1m44s |

| L28 | 2026-10-18 | **知识族内 4 个 Port 内联**（第三批解耦）：划族 `knowledge + wiki`（双向依赖，族内允许）；删 `ChunkPort` / `WikiFinalizePort` / `WikiIngestPort` / `KnowledgeFinalizePort`；提取 `WikiEnqueueResult` / `FinalizeResult`；两处守卫修正——包级 SCC 域名提取兼容 `com.ragagent.X` 与 `contract.X`、R4「已解耦包对回流」认族。**包级 SCC 9 → 7 组**（wiki 内部环被消除）。**`contract` 的 `*Port` 39 → 27**（累计删 12 个）。 | **32 守卫** · **5022 / 0 / 4** · build 1m48s |

| L29 | 2026-10-18 | **链路 A 注释纠错（A 档）**：删 `SessionAgentQaService` 尾部 4 段空注释 + 一段错挂在 `chatModel()` 上的 javadoc；`AgentStreamBridge` 的「应为 17」改为不写死；`EventManager.buildHandler` 补「先注册 → 外层 → 先执行」；`KnowledgeProcessWorker` 补进程内队列语义；`mechanisms.md` §10「可选 Bean 缺失行为一览」。 | **32 守卫** · **5022 / 0 / 4** |
| L30 | 2026-10-18 | **`session/service/rag` 提升为 `session/rag`**：实测 rag 44 文件与非 rag service 44 文件 **import = 0**，两个不相干子系统挤在同一包；迁移同步 6 处守卫登记；**ArchUnit A17 规则扩面**（`isServicePackage` 纳入 `rag`，否则换包即静默脱管）。`session/service` 88 → 44 文件。 | **32 守卫** · **5022 / 0 / 4** |
| L31 | 2026-10-18 | **链路 A 长方法拆分**：`mapOneDocument` 411 → 327（三个阶段方法）、`agentQA` 165 → 26（七步）、`runToolCallInner` 231 → 153（参数解析 + 失败调用 + sealed 结果）、`parseQARequest` 235 → 102（五个具名步骤）。**新增 `scripts/method-length-report.py`**（跳字符串的花括号计数）：实测 >150 行方法 **25 个**，而非外部报告的 167（`Fetcher.detectContentType` 报 374 实为 15、`ToolPolicy.normalizeMCPCallArguments` 报 276 实为 22）。 | **32 守卫** · **5022 / 0 / 4** |
| L32 | 2026-10-19 | **RBAC 注册长方法拆分**：`RbacRoutePolicies.registerAll` 526 行 → 13 个按路由族命名的私有方法（models/knowledge/chunks/faq/mcp/wiki/audit/session/datasource/infra/tenant/misc/agents/embed/public），`registerAll` 只保留固定顺序调用列表；**登记顺序即匹配优先级**，调用顺序逐段保持。文件 619 → 598，过 B121 阈值；`route-recon` 与 `W5aSundryRoutesContractTest` / `ArchitectureRulesTest` 全绿。长方法 >150：24 → 23。 | **40 守卫** · **boot 全量** · build 1m29s |
| L33 | 2026-10-19 | **Wiki 入库 Map 阶段长方法拆分**：`WikiIngestMapPhase.mapOneDocument` 327 → 102 行；并行摘要/引用分类与合并/重认领拆成 `classifyAndMerge`（含 `ClassifyResult` 记录），页面更新构建拆成 `buildUpdates`（含 `UpdateResult` 记录）；取消守卫、摘要失败重试、span 开闭与日志字段保持原语义。文件 588 行；长方法 >150：23 → 22。 | **40 守卫** · **wiki ingest 测试** · **boot 全量** · build 1m32s |
| L34a | 2026-10-19 | **wiki 队列的槽位收尾端口**：队列实现（Redis/进程内）此前直接 new `WikiIngestTaskRunner` 并持有 `WikiIngestService`，搬出 service 包会形成 repository → service 反向依赖。抽 `WikiIngestSlotReleaser`（单方法）到 `wiki.domain.ingest`，队列与 TaskRunner 只依赖该端口。 | **40 守卫** · :domains 编译 · wiki 定向测试 |
| L34b | 2026-10-19 | **wiki ingest 拆包**：技术实现移出用例层，46 → 24 文件。`wiki/repository/ingest`（5 端口 + Redis/进程内两套实现 + TaskRunner，17）；`wiki/domain/ingest`（载荷/finalize 行/超时常量，7）；`service/ingest` 只留管线门面与阶段协作者。**A17 基线收紧 6 条**；换锚：json-key 的 TaskRunner 路径、包规模基线、三份索引；javadoc 断链 5 处降级 `{@code}`。 | **40 守卫** · **clean + 5030 / 0 / 4** · javadoc 0 错 |
| L35 | 2026-10-19 | **mcp/oauth 拆子包**：34 文件 → `model`(7) / `error`(7) / `store`(7) / `wiring`(3) / 根(9)。`OAuthConfig`（持 TokenStore）与 `OAuthAuthorizationRequiredException`（持 OAuthHandler）刻意留根——放进 model/error 会让四包两两成环（实测 SCC +1，已回归 5 组）。换锚：json-key 4 条路径、包规模基线移除该条目、三份索引；javadoc 断链 7 处降级。 | **40 守卫** · **5030 / 0 / 4** · javadoc 0 错 |
| L36 | 2026-10-19 | **手写门面搬出 mapper 包**：6 个 `@Component`/`@Repository` 门面归位 `audit` / `auth.apikey` / `datasource`(×2) / `storage`(×2) 的 `repository/`；`storage/mapper` 清空删除。**A11b 基线 6 → 0**（零容忍）；**A16 基线 −7**（AuditLogService、DataSourceSupport/SyncExecutor/SyncResultOps、storage 三件）。 | **40 守卫** · **5030 / 0 / 4**（首轮 1 例 Redis 瞬态，隔离绿）· javadoc 0 错 |
| L37a | 2026-10-19 | **auth 令牌门面**：新增 `auth/repository/AuthTokenRepository`（按串查询/落库/单行吊销/整用户吊销）；`UserService` 与 `UserSessionOps` 不再持有 `AuthTokenMapper`；`TenantAPIKeyNotFoundException` 从 `mapper/` 移到 `auth/apikey/` 根包（放 `domain/` 会命中 C10「控制器不得 import 领域类型」——该规则本意是防实体泄漏，异常作为控制器捕获面不适用，故换落点而非改守卫）。**A16 −2、A17 −1**。 | **40 守卫** · **5030 / 0 / 4** |
| L37b | 2026-10-19 | **auth 邀请门面**：新增 `auth/repository/TenantInvitationRepository`，12 处持久化调用下沉（id/token/pair 查询、租户分页 count+list、收件箱列表与计数、插入、`accepted_count` 原子自增、pending 原子翻转、过期清扫）；状态常量单一来源（service 的 `STATUS_PENDING`/`STATUS_EXPIRED` 引用仓储常量）。**A16 −1、A17 −1**。 | **40 守卫** · **5030 / 0 / 4** |
| L37c | 2026-10-19 | **租户管理跨域解耦 + 成员门面**：新增 `tenant/repository/TenantAdmin`（域侧端口，返回实体，按 `TenantLookup` 注释只给 L3 用）+ `TenantAdminImpl`（12 处调用下沉）；`TenantService` 不再持有 tenant 域 `TenantMapper`。新增 `auth/repository/TenantMemberRepository`、`UserRepository`；`AuthTokenRepository` 扩展 `revokeActiveByUserId`。附带：成员分页的关键字过滤原对用户表查两次，改为解析一次 id 集复用。**A16 −2、A17 −2**。 | **40 守卫** · **5030 / 0 / 4** |
| L37d | 2026-10-19 | **UserService 收口**：`UserRepository` 扩为完整门面（邮箱/用户名/租户首用户、批量 id、插入含 tenantless 省略列、整行回写、`tenant_id` 置 NULL、恢复 tenantless、物理删除）；14 处 `userMapper` 调用下沉，service 不再 import MyBatis/mapper。**auth 域 A16/A17 清零（6 → 0）**。 | **40 守卫** · **5030 / 0 / 4** |
| L38 | 2026-10-19 | **六个小域门面化**：favorite / system / model（含 usage）/ vectorstore / embedchannel / agentmanagement（含 question）各建 `repository` 门面，9 个 service 不再注入 mapper。vectorstore 的 mapper 同时更名 `VectorStoreRepository` → `VectorStoreMapper`（A11 命名对齐，为门面腾出原名）。**A16 −9、A17 −3**。 | **40 守卫** · **5030 / 0 / 4**（首轮 2 例 Redis 瞬态，隔离绿） |
| L39 | 2026-10-19 | **IM 域门面 + Redis 运行面外提**：新增 `im/repository/ImChannelRepository`、`ChannelSessionRepository`；4 个 service 不再持有 mapper。新增 `im/runtime/ImRedisRuntime`——把 `im.redis-enabled` 判定、连接 ping 校验、`ImRedisStore`/`RedisPubSub` 创建从编排器构造体移出，编排器只取现成实例。**A16 −4、A17 −1（im 清零）**。 | **40 守卫** · **5030 / 0 / 4** |
| L40 | 2026-10-19 | **memory 运行面外提**：`MemoryTrace`（langfuse 门面）、进程内/Redis 两个蒸馏队列、队列装配搬到 `memory/runtime`；`MemoryExtractPayload`、`MemoryExtractTaskQueue` 搬到 `memory/domain`。新增 `MemoryExtractHandler` 执行端口——队列不再认 `MemoryExtractionService` 类型（否则 runtime ↔ service 成环）；service 实现该端口并把 `LangfuseTracing.inject()` 换成 `MemoryTrace.inject()`。换锚：json-key 前缀/键级基线与快照、Redis 队列测试随实现搬包。**A17 −5（memory 清零）**。 | **40 守卫** · **5030 / 0 / 4**（首轮 1 例 Redis 瞬态，隔离绿） |
| L41 | 2026-10-19 | **Redis 测试端口竞态修复（测试基建）**：`EmbeddedRedis` 弃 `ServerSocket(0)` 探测，改固定端口池（16390..16453）+ 跨进程 `FileLock`（同 JVM 重叠锁按占用跳过）；`close()` 改为先杀进程再关连接池。新增红态探针 `EmbeddedRedisPortAllocationTest`（4 并发实例断言端口互异）。**Redis 瞬态族移出「已知偶发」**。 | **40 守卫** · **全量连跑 5 遍（2 遍无缓存）0 失败** |
| L42 | 2026-10-19 | **datasource / websearch 端口化**：`DataSourceSyncHandler`/`DataSourceSyncTaskQueue` 接口搬 `datasource/domain`；新增 `DataSourceSchedulePort`（调度 + 重试/超时常量），`Scheduler` 实现之，service 不再依赖具体调度器；`DataSourceWiring` 搬 `datasource/config`；新增 `DataSourceTrace`。websearch 的 mapper 更名 `WebSearchProviderMapper`，新增 `repository/WebSearchProviderRepository` 门面；`WebSearchTempKbStateService` 搬 `websearch/runtime`。**A16 −4、A17 −2（两域清零）**。 | **40 守卫** · **5031 / 0 / 4** · javadoc 0 错 |
| L43 | 2026-10-19 | **mcp 门面 + wiki/embedchannel 运行面归位**：mcp 新增 `repository/McpServiceRepository`，3 个 service 不再注入 mapper。wiki 的 `WikiSlugLock` 接口搬 `domain/page`，进程内/Redis 实现搬 `repository/page`；新增 `WikiTrace`，`WikiIngestEnqueueOps` 不再直调 langfuse。embedchannel 的 `EmbedTokenStore` 搬 `domain`、Redis 实现搬 `repository`。**A16 −3（mcp 清零）、A17 −3**。 | **40 守卫** · **5031 / 0 / 4** · javadoc 0 错 |
| L44a | 2026-10-19 | **knowledge 任务面端口化**：新增 `support/KnowledgeTaskRunner`（submit/submitNamed），`KnowledgeTaskExecutor` 实现之，7 个 service（Housekeeping/Summary/FaqEntry/FaqImport/Clone/Move/Tag）不再依赖具体执行器；`KnowledgeTaskProgressStore`/`FaqImportTaskStore`/`KnowledgeTaskIdCodec`/`KnowledgeProcessedEvent` 从 `task` 搬 `support`（**不放 domain**：控制器要 import，会命中 C10）。**A16 −9（knowledge 15 → 6）**。 | **40 守卫** · **5031 / 0 / 4** · javadoc 0 错 |
| L44b | 2026-10-19 | **knowledge 适配器归位 + 观测门面**：`EmbeddingModelAdapter`、`KnowledgeBaseLookupAdapter` 搬 `knowledge/api`（本就是端口适配器）。新增 `runtime/KnowledgeTrace`——`startTask` 返回自有 `Scope` 包装（用例层不 import langfuse）、`previewPayload` 返回 `Object`；`ChunkExtractService`/`QuestionGenerationService` 改用它。**A16 −2（余 1）、A17 −3（knowledge 余 2）**。 | **40 守卫** · **5031 / 0 / 4** |
| L44c | 2026-10-19 | **A16 清零**：新增 `support/KnowledgePage` + `KnowledgeRepository.searchPage`（条件构造与分页插件类型收进仓储，`KnowledgeService` 不再 import MyBatis）；新增 `support/EmbeddingFacts`，`FaqIndexWriter` 改注入 `contract` 的 `EmbeddingPort`。**A16 基线 45 → 0**；A17 −1（knowledge 清零）。换锚：port-map。 | **40 守卫** · **cleanTest 5031 / 0 / 4** |
| L45 | 2026-10-19 | **A17 清零（收官）**：新增 `session/runtime/SessionModels`（`LangfuseChatClient`/`LangfuseReranker` 的 wrap 收口）与 `SessionTrace`（`start` 返回自有 `SpanHandle`、`attachTraceparent` 转发）；6 个 session 类改用它，不再 import langfuse/decorators。**三张棘轮全部归零：A16 45→0、A17 35→0、A11b 6→0**。 | **40 守卫** · **cleanTest 5031 / 0 / 4** |
| L46 | 2026-10-19 | **跨域直连清零**（评估文档点名项）：`SessionMessageView` 搬 `contract/session`（跨域读模型）；新增 `MessageTranscriptPort` + `session/api/MessageTranscriptAdapter`；`MemoryExtractionService` 改注入端口，不再直接持有 `session.repository.MessageRepository`。**memory 域对 session 的直接 import 清零（2 → 0）**。 | **40 守卫** · **cleanTest 5031 / 0 / 4** |
| L47 | 2026-10-19 | **7 厂商存储探测归位 storage**（评估文档点名项）：新增 `contract/storage/StorageConnectivityPort`（配置以纯 JDK `Map` 传入）+ `storage/service/StorageConnectivityService` 实现；`SystemController` 694 → 319 行，`SystemInfoService` 252 → 205（三个存储方法移出）。**走端口而非直接 import**——直连会给 system→storage 新增域间边（实测 `check-domain-contracts` 报红）。换锚：json-key 基线键随实现搬迁、domain-contracts 收紧 2 处。 | **40 守卫** · **cleanTest 5031 / 0 / 4** · 金片字节一致 |
| L48 | 2026-10-19 | **initialization 手动 new 改注入**：控制器构造体里 `new` 的 4 个用例服务加 `@Service`（`public final class`，注解放 final 前）并改为构造注入；控制器顺带去掉 10 个下游依赖参数 + 1 个从未使用的 `CryptoService`。**踩坑**：首轮只在 `public class` 前插注解 → `final class` 未命中，boot 1212 例上下文失败；补注解后复跑绿。 | **40 守卫** · **cleanTest 5031 / 0 / 4** |
| L49 | 2026-10-19 | **evaluation 域下线**（产品决策）：该域无持久化、前端零调用，仅 `POST/GET /api/v1/evaluation` 与内存指标算法。删 30 主文件 + 5 测试；清 APIKey 路由策略、RBAC 规则、WebConfig 排除名单、Langfuse 拦截命中名单。**保留 `APIKeyCapability.RUN_EVALUATIONS` 常量**（已持久化的 Key 可能携带，删了会解析失败）。换锚：域清单/能力分类/api-docs 映射/README 与 overview 控制器数/五份索引；`docs/site` 加入 .gitignore。**域数 22 → 21，控制器 51 → 50**。 | **40 守卫** · **cleanTest 5002 / 0 / 4**（−29 为随域删除的用例） |

（历史 B232~B267 与 v0.3.0 前段全部记录 → 归档 `docs/internal/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md`；追溯：`git log --grep="(B###)"`）

## 13. 文档地图与归档

| 文档 | 用途 |
|---|---|
| `HANDOFF.md`（本文） | **唯一指挥面**：现状 / 批次表 / 判定标准 / 纪律 / 环境 / 方法论精要 |
| `docs/java-remediation-plan-2026-10-13.md` | 整改方案（目标结构/状态所有权/规则的出处） |
| `docs/java-remediation-implementation-plan-v2-2026-10-16.md` | **v0.3.0 执行计划（P/R 批次全部细节与验收）** |
| `docs/java-remediation-implementation-plan-2026-10-13.md` | v1 历史（36 批已完成；已标注归档） |
| `docs/java-remediation-implementation-plan-review-2026-10-13.md` | v1 实施评审（P0-3 模块提取立项出处） |
| `docs/internal/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md` | v0.2.0 全文（结构规范/方法论全量/逐批记录） |
| `docs/internal/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md` | v0.1.0 全文（远古追溯） |
| `docs/internal/handoff/records/batch-records.md` | v0.1.0 早期逐批记录（~B110） |
