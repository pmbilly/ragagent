# Java 后端整改 实施计划 v2 —— v0.3.0「载体纠偏 + 三 runtime」

> 输入：`docs/java-remediation-plan-2026-10-13.md`（整改方案，含 §5.1 七模块终态）+ **两条判定标准（2026-10-16 用户拍板，见 §0）**
> 前序：v1（`docs/java-remediation-implementation-plan-2026-10-13.md`）**B232~B267 共 36 批全部完成**；v0.3.0 前段已完成 A②/A③/A④、C 批1（实体 14→0）、G1-a~n、D0/D1/E1、`:platform` 模块提取、`:common` 完全解散（现状 **5 模块**）。
> 排期登记：`HANDOFF.md`（v0.3.0 版）§5 批次表；每批独立提交 + 全量闸门。
> 量级：纠偏 P1~P5（5 批）+ 三 runtime R1~R6（6 批）；批次内按"一次一个包簇"再分提交。

---

## 0. 两条判定标准（拍板原文）

**contract**：跨域词汇表的唯一一处；零框架依赖；每一个类都能回答"谁在跨域消费我"。

**platform**：只剩技术适配（框架、HTTP、存储、提示词、追踪、事件基建）；**任何业务形状出现即红灯**。

> 口径说明：判定标准是**原则**，优先于方案 §8.1/§8.2 的枚举清单（方案原文："只公布跨模块确有消费者的 API/SPI"、"不为 DDD 完整编造空层"）。
> ⇒ 现状中"跨域端口/视图面扩容"是 A③ 的必然产物（**追认**）；platform 的"业务形状挂靠"是方案未预见的一层（**归正**）。

---

## 1. 纠偏范围（实测 2026-10-16）

### 1.1 contract 侧

| 对象 | 实测 | 处置 |
|---|---|---|
| 3 件知识库配置（Asr/Storage/StorageProvider Config） | 被实体 `KnowledgeBase` 的 `@TableField` 引用（**同 7 件 Config 都是**：+Chunking/ImageProcessing/IndexingStrategy/Vlm）；`KnowledgeBaseRowView`（contract 载体）以其为字段；`AsrConfigView.from/toDomain` 反向映射；`InitializationConfigService` 经载体写配置 | **P2：形状三层拆分**——实体形状回 `knowledge/domain`；L1 载体改吃视图；View 的域映射外移 |
| 纯工具类（TextConv/Whitespace/JsonValues/CodePoints/Tokens） | 全部**被契约类引用**（contract 内 1~4 处）且被多域共享 | **已裁定：保留**（判据：被契约类引用的实现细节可留；HANDOFF §12.5 已有"共享工具正当"先例）。无需搬迁 |
| 缺失面（access/execution/integration/model.chat/parsing/task）+ 58 个未创建类 | 属"方案目标形态尚未实现" | **按需**：随 R 系列（契约先行）自然成型，不做清单式补齐（方案 §13.3） |
| 6 个面未建中的 model.chat | `llm.domain` 11 件聊天契约类在 engine（P2/R1 前先上提） | **R1** |

### 1.2 platform 侧（业务形状 ~25 件 → 四类处置）

| # | 类/簇 | 现状面 | 处置 | 批次 |
|---|---|---|---|---|
| 1 | `StopSessionRequest` | `platform.session.dto` | **归域** → `session/dto`（方案 §8.8.1 原文落点）；端口签名中性化 `stopSession(String, String)` | **P1** |
| 2 | 7 件 KB Config + `KnowledgeBaseRowView` 改型 + 5 View 映射外移 | `contract.knowledge.domain` | 见 §1.1（实体回域、载体换视图、映射归域） | **P2** |
| 3 | `AgentStep` · `AgentToolCall` · `ToolCallTarget` | `platform.agent` | 去框架化（mixin）→ `contract.agent`；执行时复核与 session 持久化的关系 | **P3** |
| 4 | `SearchResult`（`SortedMapSerializer` + `JsonNode`） | `platform.retrieval` | 去框架化 → `contract.retrieval`（方案 §8.1 原文落点；66 处消费，金片护航） | **P3** |
| 5 | `MessageView` · `PipelineMessageView` | `platform.session.embed` / `platform.session` | 去框架化 → `contract.session`（金片 `emb-pub-messages-load*` 钉住，逐字节验证） | **P3** |
| 6 | `MemoryMessageCursor`（`ZeroTimeSerializer`） | `platform.memory` | 去框架化 → `contract.memory`（零时序列化语义移至边界） | **P3** |
| 7 | `UserPreferences` | `platform.auth` | 归域 → `auth/domain`（方案 §8.9 原文落点） | **P2** |
| 8 | `LanguageProperties` · `RetrievalDriverProperties` · `TenantConfigLookup` · `TenantProperties` | `platform.config` | 去框架化 → `contract.wiki` / `contract.retrieval` / `contract.tenant`（均系方案 §8.1 原文落点） | **P4** |
| 9 | `SessionQaPort` · `EmbedSessionPort`（servlet+ResponseEntity 签名） | `platform.session.{qa,embed}` | 签名中性化（`EventBusInterface` 已就位；servlet/ResponseEntity 归边界）→ `contract.session` | **P4** |
| 10 | `AdminUserPort` · `AdminUserView` · `ApiKeyAuthPort` | `platform.{auth,apikey}` | 签名中性化 → `contract.auth` / `contract.apikey`（方案 §8.1 原文落点） | **P4** |
| 11 | `KnowledgeCatalogPort` · `FaqChunkMetadataCodec` | `platform.knowledge` | 端口中性化（去 MyBatis-Plus `Page`）→ `contract.knowledge`；codec 归域 | **P4** |
| 12 | `Settings` 2 件（`ConversationProperties`/`SystemSettingRegistry`） | `platform.settings` | 去 platform 依赖（`PromptSource`/`ToolJson`）→ `contract.settings` | **P4** |
| 13 | `ParserEngineConfig` | `platform.tenant` | 回 `tenant` 域（方案 §8.9 原文落点）；跨域读取走 `TenantConfigLookup` 端口 | **P2** |
| 14 | `BaseTool` | `platform.tool` | 归 `agent-engine`（工具实现基类） | **R5**（随 agent） |
| 15 | 文件服务面（`fileserve` 8 + `support` 4） | `platform.storage.*` | 逐件裁：端口中性化进 contract / servlet 技术件留 platform 边界 | **P5** |
| — | 合理技术件（`UrlBytesFetcher`·`StreamManager`·`ContractJsonMixins*`·`LikeEscapes`·`JsonbRawStringTypeHandler`·`RedisPubSub`·`TenantContext/TenantScopes` 等） | 各面 | **保留**（技术适配定位相符） | — |

### 1.3 纠偏的完成判定（验收 = 两条标准逐条机器可查）

- contract：`check-contracts-purity.py` C1/C2/C3 全零 + 每个类的跨域消费者可列举（脚本：引用面按模块统计 ≠ 仅自身模块）；
- platform：**业务形状零命中**（新增门禁 C8：platform 内 `*View`/`*Port`/`*Request`/`*Response`/`*Payload`/`*Facts` 命名一律红灯，除非在登记例外表——例外必须写明"为何属于技术适配"）。

---

## 2. 三 runtime 拆分（实测依据 2026-10-16）

### 2.1 engine 318 件的实测构成（判据）

```
① 模型族 163 = llm 68 · modelruntime 46 · embedding 21 · rerank 15 · modelcontext 13   ← 内聚，对外零依赖
② 检索族 100 = retrieval 96 · webfetch 4                                                ← 对模型族仅 8~9 处 import
③ 管道  49 = chatpipeline（与方案 §8.8.1 session/service.rag 10+ 类名逐名吻合）          ← 业务编排
④ 追踪   6 = tracing（与方案 §8.2 platform.tracing.decorators 6 件一一对应）             ← 归 platform
```

- 依赖单向、无环；engine 对 domains **零依赖**（只 184→contract / 168→platform）；
- 12+ 域消费模型族/检索族 ⇒ 模块化收益（编译边防域绕过运行时）成立。

### 2.2 批次

| 批 | 内容 | 前置/验收 |
|---|---|---|
| **R1** | **契约面先行**：`contract.model.chat`（`llm.domain` 11 件上提）+ `contract.retrieval` 缺口（`SearchHit`/`IndexService`/`SearchPolicy` 等按检索引擎公开面成型）+ `contract.agent`（6 件）+ `contract.execution`（按需） | 每件过"跨域消费者"判据；不建空层 |
| **R2** | **model-runtime 模块提取**（163 件）；公开面分层：契约类（纯 JDK）与实现面（允许框架）分开 | 复用 platform 提取判据（三维闭包预检 / 测试条数守恒 / 插件随配置块 / 扫描面核） |
| **R3** | **retrieval-engine 模块提取**（96 件）；与 model 侧的 8~9 处边接口化（走 `contract.retrieval`/`EmbeddingModelPort`） | `webfetch` 归属拍板（方案原文=websearch 域 fetch 面；现登记"有意偏离"） |
| **R4** | 归位：`tracing`(6) → `platform.tracing.decorators`；`chatpipeline`(49) → `domains/session/service.rag` | 每步全绿 + 扫描面同步 |
| **R5** | **agent-engine**：先建 `contract.agent` 契约面 + 工具 SPI（`ToolRegistryPort`/`McpToolingGateway`，工具实现留 agenttools 注册）→ 再迁 `domains/agent`（79 件核心） | **设计先行、不搬实现**；回引/共享状态由 A18 + 编译边双防 |
| **R6** | **engine 解散收口** + `domains` 收尾（§8.9 逐域对账余项） | 模块图 = 方案 §5.1 七模块；engine 目录删除 |

### 2.3 终态模块图（与方案 §5.1 逐字一致）

```
boot → domains, agent-engine, retrieval-engine, model-runtime, platform, contract
domains / 三个 runtime → platform, contract
platform → contract
contract → JDK
```

---

## 3. 每批闸门（沿用 v1 §3 / HANDOFF 批次纪律）

1. 写测试先行：行为变更先给失败测试；守卫类改动配**红态探针**；
2. 迭代：`--tests <受影响域>` 定向跑；结构批强制三维闭包预检（import + 同包裸名 + 字符串 FQN/注册名）；
3. 收尾：`env -u SYSTEM_AES_KEY JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew spotlessCheck build`（含 14 守卫）+ `check-contract-sync` 金片签名一致（**线格式不变 = 红线**）；
4. 环境：`PATH` 须 `/opt/homebrew/bin` 在前；`SYSTEM_AES_KEY` 先 `env | grep` 清理；
5. 回填：完成一批写 HANDOFF §12 执行记录；提交信息带 `(P#/R#)`；
6. 不刷新 baseline 掩盖违例；确需刷新须写明"为何不是新增违例"。

## 4. 执行顺序

**P1 → P2 → P3 → P4 → P5 → R1 → R2 → R3 → R4 → R5 → R6**（P 先行：纠偏把"承载面"清干净，runtime 拆分不再背负历史挂靠）。

> 缓行项（不阻塞）：`fileserve` 逐件裁（P5 内）；Phase 2「形状去框架化改协议」不独立立项——本轮 P3/P4 即其执行路径（mixin 机制、逐字节验证）。
