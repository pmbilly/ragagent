# Java 后端整改 具体实施计划（R 系列 · v0.2.0）

> 输入方案：`docs/java-remediation-plan-2026-10-13.md`（基线 commit `0f468aa8`，2026-10-13）
> **本计划修订 3：2026-10-15 —— 模块提取立项与准入（承评审 P0-3）**：B256~B267 的完成口径确认为"**包边界 + 规则**"（**未新增 Gradle 模块**，唯 `:contract` 已于 B258 立）；**模块提取（`:common` → `platform` + 残余入 `:contract`；`:engine` → 三 runtime）改为"前置达标后启动"的独立专项**，准入清单 G1~G4 与提取顺序见 `HANDOFF.md` **§12.5**（G1＝`:common` 载荷型文件清零/登记，由新增门禁 **C4** 棘轮保证；G2＝platform↔common 无环；G3＝组件/属性/Mapper 扫描面同步；G4＝"确有编译隔离收益"证据）。**纪律：准入未达标前不新增 Gradle 模块**。
> 本计划修订 2：2026-10-13（① 批次号顺延：文档换版批 = B235；② 阶段 6 按"整体包结构整改"展开为 12 批；
> ③ 补录 4 处缺口——方案 §7 命名 / §10 改名 / §12.2 两条规则 / §12.5 复杂度指标；④ 跟踪面改挂 v0.2.0 分支）
> 排期登记：`HANDOFF.md` §15.1（v0.2.0 版，每次会话唯一指挥面）。量级：B232~B267 共 36 批。

## 0. 勘察证据与已完成批次（全部实测）

| 项 | 结果 | 证据 |
|---|---|---|
| 守卫 PERSIST_RE 缩进 bug | ✅ 属实，已修 | 修前每个 common 根目录只查最后 1 个文件；修后全量重扫 0 命中 ⇒ 读数不变（纯漏检面） |
| "13 个包级 SCC" | ✅ 精确复现，已入 R9 棘轮 | 探针（完整包粒度 import 图）输出 13 组：`retrieval.engine.*`(9) · `knowledge.repository/security/service/task` · `agent/compaction/prompt` · `storage.fileserve/provider/service/support` · `wiki.service/ingest/page` · `agent.tools/runtime/spi` · `common.error/security/web` · `embedchannel/filter/service` · `chatpipeline/plugin/support` · `{agent.management.domain,mapper}` · `{agent.skills,mapper}` · `{embedding,provider}` · `{rerank,provider}` |
| K1/K2/K3/A6 源码位置 | ✅ 逐条对上 | B236/B237/B238/B234 各批（B234 已修完） |
| A16 基线 | **76 类**（ArchUnit 语义实测；import 口径 81 文件） | B233 红态探针：注入 ⇒ 双红且 fresh 仅含探针 |
| A17 基线 | **61 类**（同上） | 同上 |
| 阶段 6 目标结构 | 方案 §5 七模块 / §8 完整目标树（domains 204 包 1363 文件 + 254 条包映射）/ §6 域内十一层 | 本计划 B256~B267 逐批落地 |

**已完成**（每批独立提交、独立全绿）：

| 批 | 内容 | 关键证据 |
|---|---|---|
| **B232** ✅ | 守卫修复 + R9 包级 SCC 棘轮（基线 13 组） | 3 探针（漏检修复 / 新增环红 / 过期基线红）；build 1m31s |
| **B233** ✅ | ArchUnit A16（边界层引用禁令）+ A17（service 技术依赖禁令） | 探针双红精确点名；build 1m25s；4903/0 |
| **B234** ✅ | A6 同 ID 去重保最高分 | 2 反例先红后绿；录制回放未受影响；4908/0 |
| **B235** ✅ | 文档换版：HANDOFF → v0.2.0 版（v0.1.0 全文归档）+ 本计划展开 | 本批（文档，零代码） |

## 1. 对方案的修正与建议（含 4 处缺口补录）

### 1.1 已执行的修正
- **守卫修复 + R9 包级 SCC 棘轮**（B232）：13 个包级环是拆环工作的进度度量，原守卫只看顶层域、完全失明；R9 上线后"只许减"，每消一组更新一次基线。
- **三条硬规则按棘轮基线落地**（B233 + 后续）：A16=76 / A17=61 只许减；第三条"状态唯一写入者"归 B244（A18 规则）。

### 1.2 阶段 6 展开原则（回答"整体包结构整改"）
- 方案的执行口径是 **"先规则后搬迁、按模块/域逐批、搬迁不夹带行为变更"**（§11/§13.7/§14）——不做一次性大搬家。
- 顺序固定：**改名清算（B256/B257）→ contracts（B258）→ platform（B259）→ 三 runtime（B260~B262）→ domains 域内重排（B263~B266）→ 门禁收紧（B267）**。
- 每批硬性动作：同步组件/属性/Mapper 扫描面 + 跑启动测试 + 守卫显式核（§15.2 第 9 条）；Gradle 模块化只在"包边界 + 规则"证明有收益后做（方案 §5.2 原文）。

### 1.3 4 处缺口（本轮补录，均已入批次表）
| 缺口 | 内容 | 落点 |
|---|---|---|
| 方案 §7 命名决定 | `agenttools` / `agentmanagement` 包改名与边界规则（现码 `agent/tools`、`agent/management`） | **B257** |
| 方案 §10 改名清单 | §10.1 十组同名类（`ToolPolicy`→`ApprovalToolPolicy`、`TokenUsage`→`LangfuseTokenUsage`、`ModelResponse`→`ModelDetailResponse`、`ProviderRegistry`→`ModelProviderCatalog`、`FileService`→`FileUrlPort`、`WebSearchProvider`→`WebSearchProviderEntity`、`Event/EventBus`→`ApprovalEvent/ApprovalEventBus` 等，实测旧名均仍在）+ §10.2 三组域前缀 | **B256** |
| 方案 §12.2 两条规则 | 「分层方向」（domain 不依赖 Spring/Mapper；repository 不依赖 service 实现）+「装配契约」（每个 contract 唯一实现） | **B267**（contracts 落地后） |
| 方案 §12.5 复杂度指标 | 方法复杂度 / 构造依赖数 / 公开可变字段数（方案原文：候选审查线，非硬门） | **B267**（可选附加） |

### 1.4 两个拍板点（到批前拍板）
1. **K2（B238）URL 摄取**：建议统一到知识域现有**同步下载**实现（`KnowledgeService.createFromUrl`：SSRF 校验 + 落存储 + hash + URL 去重），数据源桥不再写"空路径 + 等 worker 下载"死链；备选 = worker 支持下载（改动大、重复实现）。到批前给侦察对比。
2. **K6（B247）恢复深度**：先修"判据分裂"（`StartupTaskRecovery` 看 `REDIS_ADDR`，Redis 富化装配看 `knowledge.redis-enabled`）——小改；**主任务持久化（重领/lease/generation）**是较大基建（本仓主队列为进程内 executor + Housekeeping 兜底），是否本期做需单独拍板。

## 2. R 系列批次表（B232~B267，共 36 批）

> 每批首步 = 七步 SOP 侦察（HANDOFF §14.2）；闸门见 §3；完成一批回填 `HANDOFF.md` §15.1 与 §15.4。

### 阶段 0 · 边界固化（不改目录）
| 批 | 范围 | 验收 |
|---|---|---|
| **B232** ✅ | 守卫修复 + R9 包级 SCC 棘轮（基线 13 组） | 3 红态探针；十一守卫绿 |
| **B233** ✅ | ArchUnit A16（76 类）+ A17（61 类） | 探针双红精确点名 |

### 阶段 1 · 最小正确性修复（不动包）
| 批 | 范围 | 验收 |
|---|---|---|
| **B234** ✅ | A6 同 ID 去重保最高分 | 反例先红后绿 |
| **B236** | K3：`DocReaderClient` onError 捕获传播 + await 超时判定 + 异步 UNIMPLEMENTED 走 unary fallback | unary-only / meta 后错误 / 超时 三场景 |
| **B237** | K1：worker→向量店显式传租户（`KnowledgeVectorWrites.boundEngine` 收 tenantId，消除 ThreadLocal+0L） | 非零租户 + 绑定 store 的 worker 线程测试 |
| **B238** | K2：URL 摄取统一（拍板点①） | 真实 adapter URL → 存储 → 解析 → chunks → index 闭环测试 |

### 阶段 2 · 会话终态、取消与提交幂等
| 批 | 范围 | 验收 |
|---|---|---|
| **B239** | A1-NORMAL：流错误/超时轮终态一致收口 | 终态矩阵 NORMAL × 五场景唯一持久终态 |
| **B240** | A1-AGENT：`AgentEngine` finally/COMPLETE 与外层 catch/ERROR 收口；`FinalizePhase` 失败 outcome | 终态矩阵 AGENT |
| **B241** | A2：全轮一次性提交门（CAS）+ 消息更新失败不启动索引/建议/记忆 | 双 STOP + runner finally 只提交一次 |
| **B242** | A3：可关闭模型流（`LlmChatClient` 返回句柄；`ThinkPhase` 接取消 probe；两个装饰器队列关闭语义） | 取消后模型调用终止、permit 归还 |
| **B243** | A5：跨线程上下文传播（NORMAL 消费线程 / STOP watcher replay） | 新线程缺失 principal 场景测试 |
| **B244** | A18 状态所有权规则（phase 不回引 runner / 无公共可变运行态字段） | 规则探针红；基线只减 |

### 阶段 3 · 知识任务可靠性
| 批 | 范围 | 验收 |
|---|---|---|
| **B245** | K4：CAS 保护整轮数据（失败清场限本代；`KnowledgeParseService` 排斥处理中） | 重启可重领或明确失败；旧 generation 不覆盖 |
| **B246** | K5：图任务失败/重试/计数同一契约（`finalAttempt` 消费、异常不吞、任务级去重） | 重复投递只释放一次槽位 |
| **B247** | K6：主任务恢复口径统一（拍板点②） | 统一判据下重启回收有测试 |

### 阶段 4 · 索引一致性与配置生效
| 批 | 范围 | 验收 |
|---|---|---|
| **B248** | K10a：embedding 响应对齐校验（消费 `index`、数量/维度校验、乱序拒绝） | 乱序/缺项/错维度被拒 |
| **B249** | K10b：索引分批政策收敛（worker 与 `ChunkVectorIndexer` 共用执行） | 同一政策一份实现 |
| **B250** | K7：事务外副作用（revision + outbox；事务内不调远程模型） | rollback/outbox 重放测试 |
| **B251** | K8：配置能力矩阵（parser rules / parent-child 显式拒绝或实现；不静默回落） | 不支持配置明确报错 |

### 阶段 5 · 检索与结构收敛
| 批 | 范围 | 验收 |
|---|---|---|
| **B252** | K9：跨 store 融合（RRF 按 store 内 rank；context expansion 输出额外块或去无效读取） | 交换 KB/store 顺序结果稳定 |
| **B253** | A4：并发工具状态提交顺序（`seenChunks` 单一 writer / 按调用序回填；中断语义） | 乱序完成稳定回填 |
| **B254** | controller 剥离编排（`KnowledgeQaController` 14 构造参数、`QaRequestParser` 236 行拆分） | controller 包内无 Executor/Parser/Orchestrator |
| **B255** | 检索中立 `SearchHit`（去上层对 `PgVectorRetrieveRepository` 内部类型依赖） | 上层只依赖中立类型 |

### 阶段 6 · 整体包结构整改（改名清算 → contracts/platform → 三 runtime → domains 重排 → 门禁收紧）
| 批 | 范围 | 验收 |
|---|---|---|
| **B256** | §10 改名清算：10 组同名歧义（`ToolPolicy`/`TokenUsage`/`ModelResponse`/`ProviderRegistry`/`FileService`/`WebSearchProvider`/`Event`/`EventBus`/`ToolResult` 收敛/`ToolCall`）+ 3 组域前缀；纯改名不夹带行为 | 编译绿 + 启动测试 + 契约金片零变化 |
| **B257** | §7 命名与边界：`agent/tools`→`agenttools`、`agent/management`→`agentmanagement`；两包边界规则（不 import 各域 service/mapper） | 同上；边界规则探针红 |
| **B258** | contracts 包边界：`:common` 内立 contracts 面（纯 JDK）+ ArchUnit「模块 DAG」「跨域边界」（域间只经 contracts；client 不得引他域 mapper） | 规则探针红；每步启动测试 |
| **B259** | platform 包边界：`:common` 其余收拢为 platform 面 | 同上 |
| **B260** | agent-engine 包边界：`com.ragagent.agent` 运行面收拢（引擎/loop/段协作者） | 同上；不引 @Lazy 掩盖 |
| **B261** | retrieval-engine 包边界：`retrieval.*` 收拢 + policy 归位（`KnowledgeSearchRanking`/`GrepChunksScoring`） | 同上 |
| **B262** | model-runtime 包边界：模型调用/provider/流生命周期 | 同上 |
| **B263** | domains 域内重排①：knowledge（29 包 248 文件；含 SCC 拆解与 mapper/repository 分离主战场） | 按域目标树；R9 基线只减 |
| **B264** | ②：session（21 包 183 文件）+ agenttools/agentmanagement 落位 | 同上 |
| **B265** | ③：wiki（17 包 147）+ datasource（16 包 124）+ mcp（9 包 125） | 同上 |
| **B266** | ④：im/memory/auth/storage/websearch/model + 其余小域（approval/audit/evaluation/embedchannel/favorite/settings/system/initialization/tenant/vectorstore/skill） | 同上 |
| **B267** | 门禁收紧收尾：§12.2「分层方向」「装配契约」两规则 + A11b 基线清零核查 + 复杂度指标（可选） | 规则探针红；三问验收 |

> 阶段 6 的批内子步（每批执行时展开）：一次只搬一个包簇 → 全绿 → 提交；mapper/repository/controller 三类落点问题随批消减（A11b 与 A16/A17 基线只减）。

## 3. 每批闸门（沿用 §15.2 与既有分档）

1. **写测试先行**：正确性修复先给失败测试/反例；守卫类改动必配红态探针。
2. **迭代**：`--tests <受影响域>` 定向跑。
3. **收尾**：`env -u SYSTEM_AES_KEY ./gradlew spotlessCheck build`（含 :guards 十一守卫，同 CI 口径）+ 结构批必跑 `python3 scripts/check-package-cycles.py`（R9）+ 触面批**真实起服冒烟**。
4. **环境**：`JAVA_HOME=/opt/homebrew/opt/openjdk@21`；`PATH` 须 `/opt/homebrew/bin` 在前（避免 python3.9 假红）；`SYSTEM_AES_KEY` 泄漏先 `env | grep`。
5. **回填**：完成一批写回 `HANDOFF.md` §15.1（状态列）与 §15.4（执行记录）；提交信息带 `(B###)`。
6. **不刷新 baseline 掩盖违例**；确需刷新须写明"为何变化不是新增违例"（B232 先例）。

## 4. 验收（沿用方案原文）

- 阶段 0：注入越层依赖/包环能让检查红 ✓（B232/B233 已验）。
- 阶段 1：四条各有先失败后转绿测试；真实 worker 线程处理非零租户绑定 store 成功。
- 阶段 2：成功/失败/取消/超时各有唯一持久终态；双 STOP + finally 只提交一次；DB 更新失败不启动索引。
- 阶段 3：重启可重领或明确失败；重复投递只释放一次；旧 generation 不覆盖新数据。
- 阶段 4：事务不持锁调远程模型；乱序/缺项/错维度被拒；不支持配置明确报错。
- 阶段 5：交换 KB scope 顺序结果稳定；controller 包内无 Executor/Parser/Orchestrator。
- 阶段 6：每步同步扫描面、每步启动测试绿、不刷新 baseline 掩盖违例；包级 SCC 只减（R9）。
- 总验收三问：**用例可读 / 状态可控 / 政策可复用**。
