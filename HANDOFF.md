# ragagent 交接文档（v0.3.0 · 载体纠偏 + 三 runtime 版）

> **版本谱系**：v0.1.0 归档 `docs/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md`；**v0.2.0 全文归档 `docs/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md`**（含 B232~B267 全部逐批记录、v0.2.0 结构规范与 C/G1/D/E 系列过程记录）。
> 本版 2026-10-16 生效：**只留活信息**；历史一概在归档，追溯用 `git log --grep`。
> 工作分支：`v0.3.0`。

## ⭐ 接手须知（5 分钟）

1. **开工顺序**：读 §4 两条判定标准（2026-10-16 拍板）→ §5 执行方案（详情＝`docs/java-remediation-implementation-plan-v2-2026-10-16.md`）→ §9 方法论 → §10 纪律 → 干活 → 回填 §12。
2. **基线核对**：先跑 §6 的全量闸门，确认 `BUILD SUCCESSFUL` + 各模块测试条数（**numbers 守恒**；只信汇总数字，`boot`/`platform` 条数缺失＝静默少跑）。
3. **待办与读数都会过期**：宣称"未完成/待办"或据此动手前先实测（`git log` / 守卫 / 脚本输出）；**以守卫为准**。
4. **提交**：批次号 `(P#)`/`(R#)`，写明验证方式；**推送等用户发话**（历史纪律，一字不改）。

## 0. 总目标与当前阶段

- **终态（方案 §5.1）**：7 个 Gradle 模块 —— contract / platform / agent-engine / retrieval-engine / model-runtime / domains / boot；单进程分层单体；跨模块只经 contract。
- **现状（2026-10-16）**：**5 模块** —— `:contract`（241 件）· `:platform`（168 件）· `:domains` · `:engine`（318 件）· `:boot`。`:common` 已完全解散（125→0）。
- **当前阶段（v0.3.0）**：**载体纠偏（P1~P5）+ 三 runtime（R1~R6）** ⇒ 计划见 §5。

## 1. 仓库身份与分支

- 远程 `origin` = `https://github.com/pmbilly/ragagent.git`（公开）；主分支 `main`（停 `ef89e4ab`，含 v0.2.0 结项 + 缺陷修复）。
- 分支谱系：`v0.1.0`（归档）· `v0.2.0`（R 系列 36 批，已合并 main 并推送）· **`v0.3.0`（当前工作分支；起源自 `main`，含 A②/A③/A④、C 批1、G1-a~n、D0/D1/E1、platform 提取、:common 解散、IDE 告警清零等系列提交）**。
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
- 验收工具：`check-contracts-purity.py`（C1/C2/C3 全零）+ 新增 **C8**（platform 内 `*View/*Port/*Request/*Response/*Payload/*Facts` 命名红灯，例外须登记理由）。

## 5. 执行方案（摘要；详情＝实施计划 v2）

> **阶段路线（2026-10-16 用户拍板）**：**P1~P5（两模块纠偏）在 `v0.3.0` 全部完成** ⇒ **推送 `v0.3.0` + 合并 `main`** ⇒ 新建 **`v0.4.0`** 分支，R1~R6（三 runtime）在 v0.4.0 上继续。

| 批 | 内容 | 状态 |
|---|---|---|
| **P1** | platform 域 DTO 归位示范：`StopSessionRequest` → `session/dto` + 端口中性签名 | 见 §12 |
| **P2** | KB config 形状三层拆分：7 件 Config 回 `knowledge/domain`；`KnowledgeBaseRowView` 载体改吃视图；View 的 from/toDomain 映射外移；`UserPreferences`→auth、`ParserEngineConfig`→tenant（余 2 件并入 P5） | ✅ 675b782b |
| **P3** | 跨域形状去框架化（mixin 机制、金片护航）：`SearchResult`→contract.retrieval；`AgentStep/AgentToolCall/ToolCallTarget`→contract.agent；`MessageView`→contract.session；`MemoryMessageCursor`→contract.memory | ✅ 744fdcb2 / 2a9b3453 / cac610c2 / 5ef29e07 |
| **P4** | 端口中性化进 contract：`SessionQaPort/EmbedSessionPort`（servlet 面归边界）· `AdminUserPort/AdminUserView/ApiKeyAuthPort` · `KnowledgeCatalogPort`（去 MyBatis-Plus Page）· config 4 件 · settings 2 件 | ⬜ |
| **P5** | platform 收尾：fileserve/support 逐件裁；目录与 package-info 清理；**C8 门禁上线**（业务形状零命中验收） | ⬜ |
| **R1** | 契约面先行：`contract.model.chat`（llm.domain 11 件）+ `contract.retrieval` 缺口 + `contract.agent` 6 件 + `contract.execution` 按需 | ⬜ |
| **R2** | `model-runtime` 提取（163 件：llm/modelruntime/embedding/rerank/modelcontext）；公开面分层（契约类 vs 实现面） | ⬜ |
| **R3** | `retrieval-engine` 提取（96 件）；与 model 侧 8~9 处边接口化；`webfetch` 归属拍板 | ⬜ |
| **R4** | 归位：`tracing`(6)→`platform.tracing.decorators`；`chatpipeline`(49)→`domains/session/service.rag` | ⬜ |
| **R5** | `agent-engine`：契约面 + 工具 SPI 先行 → 迁 `domains/agent` 核心（79 件） | ⬜ |
| **R6** | engine 解散收口 + domains 逐域对账（§8.9 余项） | ⬜ |

顺序：P1→…→P5 →（**推送 + 合 main + 开 `v0.4.0`**）→ R1→…→R6（纠偏先行：承载面清净后再拆 runtime）。

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
  期望：`BUILD SUCCESSFUL` + 各模块测试汇总条数守恒（最近基数 **4996 / 失败 0 / 跳过 4**）。`PATH` 须 `/opt/homebrew/bin` 在前（python3.9 会让 file-size 假红）。
- 前端（触契约时）：`cd frontend && npx vue-tsc --build --force` + `npm test`。

## 7. 测试与安全网

- 回归网：**~4996 用例 / 1,349 契约金片**；每一步（哪怕纯移动）结束必须全绿。
- 已知偶发（全量并发下，单独重跑即绿，勿误判回归）：`WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool` · `EvaluationContractTest.getTerminalRunsExecution` · `EmbedRateLimiterTest.redisPathSharesBudgetAcrossInstances` · Redis 瞬态族（`RedisDataSourceSyncTaskQueueTest` 等，**建议给测试侧 Lettuce 加超时/重试**，仍在 housekeeping 队列）。
- 覆盖率：**未执行基线**（`-Pcoverage`）⇒ 本项目**不声称任何覆盖率数字**（开放项）。
- 前端：`vue-tsc` 是最终文件集上的最后一步（`tsx --test` 只转译不查类型）。

## 8. 结构地图与守卫总表

### 8.1 现状模块（5）与目标（7）

```
现状（2026-10-16 实测）                      目标（方案 §5.1）
contract  241 件（纯 JDK，零依赖块）   →     contracts/
platform  168 件（技术适配 + 业务形状归正中）→ platform/
engine    318 件（9 包：llm 68 · retrieval 96 · chatpipeline 49 · modelruntime 46 ·
                  embedding 21 · rerank 15 · modelcontext 13 · tracing 6 · webfetch 4）
                                        →     model-runtime（163）/ retrieval-engine（96）
                                              + tracing→platform / chatpipeline→session
domains   ~1390 件
boot      13 件                        →     agent-engine（自 domains/agent 外提）

依赖方向：boot → 全部；domains / 三个 runtime → platform, contract；platform → contract；contract → JDK
```

### 8.2 域内分层（方案 §6）

`controller`（绑定/帧写出）· `service`（用例与政策）· `domain`（状态与不变量）· `mapper` · `repository` · `dto` · `client` · `task` · `api` · `support` · `config`（只装配）。
禁止：`service` 引边界层/具体技术（A16/A17）· `domain` 引 Spring/Mapper · `repository` 回引 service · `controller` 跑编排 · `mapper` 放仓储门面（A11b）。

### 8.3 守卫与规则总表（**14 守卫 + ArchUnit**；新增规则前先查这里）

| 守卫 | 语义 | 基线 |
|---|---|---|
| `check-package-cycles.py` | 环/分层/域依赖/SCC（R1~R9） | `package-cycles.baseline.json`（只许减；SCC 12 组） |
| `check-domain-contracts.py` | **R10 域间只经契约**（分档①②③） | `domain-contracts.baseline.json`（**6 条边 / 51 类**；①0/②2/③5） |
| `check-contracts-purity.py` | C1 受控导入根 · C2 持久化形状 · C3 依赖清单（+C4 载荷型） | `contracts-purity.baseline.json`（只许减；全零） |
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
| —（杂项） | 2026-10-16 | 61 份过期文档清理（用户确认；活文档零引用已核）· `scripts/dev-env.sh` login 提取器适配统一响应壳（token 移入 data） | 冒烟 sessions / knowledge-bases 200 · token 265 字符 |

（历史 B232~B267 与 v0.3.0 前段全部记录 → 归档 `docs/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md`；追溯：`git log --grep="(B###)"`）

## 13. 文档地图与归档

| 文档 | 用途 |
|---|---|
| `HANDOFF.md`（本文） | **唯一指挥面**：现状 / 批次表 / 判定标准 / 纪律 / 环境 / 方法论精要 |
| `docs/java-remediation-plan-2026-10-13.md` | 整改方案（目标结构/状态所有权/规则的出处） |
| `docs/java-remediation-implementation-plan-v2-2026-10-16.md` | **v0.3.0 执行计划（P/R 批次全部细节与验收）** |
| `docs/java-remediation-implementation-plan-2026-10-13.md` | v1 历史（36 批已完成；已标注归档） |
| `docs/java-remediation-implementation-plan-review-2026-10-13.md` | v1 实施评审（P0-3 模块提取立项出处） |
| `docs/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md` | v0.2.0 全文（结构规范/方法论全量/逐批记录） |
| `docs/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md` | v0.1.0 全文（远古追溯） |
| `docs/handoff/records/batch-records.md` | v0.1.0 早期逐批记录（~B110） |
