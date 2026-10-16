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
- 验收工具：`check-contracts-purity.py`（C1/C2/C3 全零）+ 新增 **C8**（platform 内 `*View/*Port/*Request/*Response/*Payload/*Facts` 命名红灯，例外须登记理由）。

## 5. 执行方案（摘要；详情＝实施计划 v2）

> **阶段路线（2026-10-16 用户拍板）**：**P1~P5（两模块纠偏）在 `v0.3.0` 全部完成** ⇒ **推送 `v0.3.0` + 合并 `main`** ⇒ 新建 **`v0.4.0`** 分支，R1~R6（三 runtime）在 v0.4.0 上继续。 **✅ 2026-10-16 已执行**：`v0.3.0` 推送（95349098..c07786fe）→ `main` 合并推送（ef89e4ab..c07786fe）→ `v0.4.0` 新建并推送；R1~R6 在 v0.4.0 继续。

| 批 | 内容 | 状态 |
|---|---|---|
| **P1** | platform 域 DTO 归位示范：`StopSessionRequest` → `session/dto` + 端口中性签名 | 见 §12 |
| **P2** | KB config 形状三层拆分：7 件 Config 回 `knowledge/domain`；`KnowledgeBaseRowView` 载体改吃视图；View 的 from/toDomain 映射外移；`UserPreferences`→auth、`ParserEngineConfig`→tenant（余 2 件并入 P5） | ✅ 675b782b |
| **P3** | 跨域形状去框架化（mixin 机制、金片护航）：`SearchResult`→contract.retrieval；`AgentStep/AgentToolCall/ToolCallTarget`→contract.agent；`MessageView`→contract.session；`MemoryMessageCursor`→contract.memory | ✅ 744fdcb2 / 2a9b3453 / cac610c2 / 5ef29e07 |
| **P4** | 端口中性化进 contract：`AdminUserPort/AdminUserView/ApiKeyAuthPort` · `SessionQaPort` · `KnowledgeCatalogPort` · `TenantConfigLookup` · `EmbedSessionPort`（`SseSink`/`EmbedReply`）；config/settings 五件裁定留 platform（C8 豁免登记） | ✅ **6 刀全落**（163ddfcd/9db5b06d/d792a002/92c5eb70/151c7f25/4d7accdb）——**platform 业务形状清零** |
| **P5** | platform 收尾：fileserve/support 逐件裁；目录与 package-info 清理；**C8 门禁上线**（业务形状零命中验收） | ✅ e6f99afd / 1851b3ad / 1b020e97 |
| **R1** | 契约面先行：`contract.model.chat`（llm.domain 11 件）+ `contract.retrieval` 缺口 + `contract.agent` 6 件 + `contract.execution` 按需 | ✅ **收官**：R1a `ae0a17c0`（11 件 → `contract.model.chat`）· R1b-1 `b5a36912`（4 件 → `contract.websearch`）；余项 **R1b-2→R3 / R1c→R5 / R1d 不建**（判定见 §2.9） |
| **R2** | `model-runtime` 提取（**实际 153 件** = llm 58 / modelruntime 46 / embedding 21 / rerank 15 / modelcontext 13；计划的"163 件"含 tracing 6 + webfetch 4——**按计划自身 R4/R3 行裁定：tracing 归 R4、webfetch 归 R3**）；公开面分层（契约类 vs 实现面） | 🔄 **R2a ✅ `925071a2`+`f0355a7f`** · **R2b ✅ `c160b749`**（模块切出） · **R2c-1a ✅ `c25d90e7`**（`ModelRuntimePort`→`contract.model.ModelRuntime`、`VlmConfig` 契约化） · **R2c-1b ✅ `76c30539`**（装配器+配置映射入 model-runtime、共享传输归位 platform.http、模块内环清零）；R2c-2（把 domains 其余 ~6 处"自建客户端"改为走 `ModelRuntime` 端口 + 收窄 engine 的 api 传递）待做 |
| **R3** | `retrieval-engine` 提取（96 件）；与 model 侧 8~9 处边接口化；`webfetch` 归属拍板 | ⬜ |
| **R4** | 归位：`tracing`(6)→`platform.tracing.decorators`；`chatpipeline`(46)→`domains/session/service.rag` | 🔄 **R4a ✅ `fff4d3ba`**（tracing 六件 → `platform.tracing.decorators`；契约流句柄三件 → `platform.stream`；顺带换锚 ArchUnit A16 禁用前缀）；R4b（chatpipeline 归位）待做 |
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

### 8.1 现状模块（6）与目标（7）

> 计数口径：`<模块>/src/main/java/**/*.java`，**不含 package-info**（2026-10-16 R2b 后实测）。

```
现状（R2b 后）                              目标（方案 §5.1）
contract      266 件（纯 JDK，零依赖块）  →  contracts/
platform      150 件（技术适配；业务形状已归正；含 R4a 追踪装饰器 6 + 流句柄 3、R2c 共享传输 2）→ platform/
model-runtime 132 件（R2b 切出：llm 54 · modelruntime 39 · embedding 18 · rerank 12 ·
                 modelcontext 12；R4a 迁出流句柄 3、R2c 迁入装配器与配置映射 2 / 迁出传输 2）
                                        →  model-runtime（已成）
engine        139 件（retrieval 90 · chatpipeline 46 · webfetch 4；三者将迁：
                 retrieval→retrieval-engine（R3）· chatpipeline→session（R4b）· webfetch 归属拍板（R3））
domains      1290 件
boot           12 件                      →  agent-engine（R5 自 domains/agent 外提）

依赖方向：boot → 全部；domains / 三个 runtime → platform, contract；platform → contract；contract → JDK
（R2b 过渡期：engine 以 api 传递 :model-runtime，domains 借之可见其类型 ⇒ R2c 组装权下沉后收窄）
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
| P4 | 2026-10-16 | 端口中性化五刀：AdminUser 族→contract.auth · SessionQaPort+QA 链 EventBusInterface→contract.session · TenantConfigLookup→contract.tenant · KnowledgeCatalogPort（去 Page）→contract.knowledge · ApiKeyAuthPort 中性化→contract.apikey；config/settings 五件裁定留 platform | 五刀各自 14 守卫全绿 · 金片 1349 一致 · gate 1m56s~2m45s / **4996 / 失败 0 / 跳过 4**；platform 的 auth/apikey/retrieval/agent/memory 消亡、knowledge 仅余 codec、session 仅余 EmbedSessionPort（P4f） |
| P4f | 2026-10-16 | `EmbedSessionPort` 中性化入契约：`SseSink`（帧出口）+ `EmbedReply`（status+body）；SSE 链全链换型（FrameWriter→Emitter→Orchestrator→Executor→Controller）+ 边界包装（DelegateOps/Adapter）；`platform.session` 包消亡 | 14 守卫全绿（R8 修复）· 金片 1349 一致 · gate 2m6s / **4996 / 失败 0 / 跳过 4** |
| P5 | 2026-10-16 | platform 收尾三件：`ParserEngineConfig` 去框架化入 `contract.tenant`（27 注解 → Core mixin）· fileserve 逐件裁（4 端口 + `OpenedFile` 上提契约；HTTP 三件 + `StoragePaths` 留登记）· **C8 门禁上线**（业务形状棘轮，3 条登记） | 15 守卫全绿 · 金片 1349 一致 · gate 1m39s~2m23s / **4996 / 失败 0 / 跳过 4**；platform 只剩技术适配 + 3 条业务形状登记 |
| —（杂项） | 2026-10-16 | 61 份过期文档清理（用户确认；活文档零引用已核）· `scripts/dev-env.sh` login 提取器适配统一响应壳（token 移入 data） | 冒烟 sessions / knowledge-bases 200 · token 265 字符 |
| R2c-1b | 2026-10-16 | **组装权下沉**：`ModelRuntimeAssembler`+`ModelRuntimeConfigs` → `modelruntime.application`（依赖换成契约端口 `ModelCatalogPort`，闸门错误文案仍在装配器拆包）；`LlmTransport`+`Wiring` → `platform.http`（共享裸 HTTP 传输，非模型语义）⇒ **模块内环清零**（2 组→0、间接环 1→0），domains 的 7 个消费点不再 import model-runtime | 15 守卫绿 · 金片 1349 一致 · gate 2m12s / **4996 / 0 / 4**（platform 195→205、model-runtime 239→229）· 起服 200 |
| R2c-1a | 2026-10-16 | 模型运行时端口契约化：`chatpipeline.spi.ModelRuntimePort` → `contract.model.ModelRuntime`（§8.1 目标名）；`VlmClient.VlmConfig` 抽为 `contract.model.VlmConfig`（纯记录 + 两个派生方法）；全仓类型名同步。**发现**：方案 §8.1 的 `ModelAccessResolver` 已被既有 `ModelCatalogPort` 覆盖，无需新建 | 15 守卫绿 · 金片 1349 一致 · gate 2m11s / 4996 / 0 / 4 · 冒烟 200 |
| R4a | 2026-10-16 | **tracing 归位**：6 个 Langfuse 装饰器 → `platform.tracing.decorators` + 3 个契约流句柄（QueuedChatStream/SyncChatStream/StreamLifecycle）→ `platform.stream`（装饰器要建/转发流 ⇒ 句柄须在更低层）；错位测试随迁；engine 的 tracing 目录清空。**本批是 R2c 的解阻塞项**（装饰器已只依赖契约面）。顺带换锚 ArchUnit A16 禁用前缀（不改则 service 违规静默失明） | 15 守卫绿 · json-key 换锚（5 出 5 入）· 金片 1349 一致 · gate 2m4s / **4996 / 失败 0 / 跳过 4**（platform 188→195、engine 322→315）· 冒烟 200 |
| R2b | 2026-10-16 | **`:model-runtime` 模块切出**：五包（llm/modelruntime/embedding/rerank/modelcontext）148 件主源码 + 19 件测试 `git mv`；模块清单四处同步（settings · `_source_roots.py`（守卫单一来源）· 根 jacoco 聚合 · stray-dirs/test-summary/dev-stale-check）；engine 过渡期 `api(:model-runtime)`；**Dockerfile 顺带校正**——原写已裁撤的 `common/` 且漏拷 contract/platform，`docker build` 必失败 | 15 守卫绿（R9 SCC 9 组不变 · 域间契约 6 条不变）· 大文件守卫按路径换锚 4 条（另回退 8 处无关数值刷新）· 金片 1349 一致 · gate 2m9s / **4996 / 失败 0 / 跳过 4**（model-runtime 实跑 239，engine 561→322 ⇒ 搬迁测试真跑）· 起服冒烟 200 |
| R2a-2 | 2026-10-16 | 模型面端口与结果形状上提：`embedding.Embedder`（9）/`rerank.Reranker`（8）/`rerank.RankResult`（3）→ `contract.model`；**RankResult 拆分**——纯形状进契约面，parse/marshal 移实现侧新件 `rerank.RankResultCodec`；@JsonProperty 对位进 `ContractJsonMixinsModel`（含内嵌 DocumentInfo）；同包裸引用 7 处补 import | 15 守卫绿 · 金片 1349 一致 · json-key 换锚（离开 1 / 进入 2）· gate 1m55s / **4996 / 失败 0 / 跳过 4** · 冒烟 200 |
| R2a-1 | 2026-10-16 | 模型运行时而契约化：`llm.LlmChatClient`（35 域内消费）/`llm.ChatStream`（3）/`llm.domain.StreamResponse`（8，SSE 帧核心）→ `contract.model`；StreamResponse 的 7 处 `@JsonInclude(NON_EMPTY)` 对位进 `ContractJsonMixinsModel` | 15 守卫绿 · 金片 1349 一致 · gate 2m12s / **4996 / 失败 0 / 跳过 4** · 冒烟 200。**R9 包级 SCC 8→9**（父子包内聚组 {contract.model, contract.model.chat}，理由已登记守卫脚本头；同类先例 3 组） |
| R1 收官 | 2026-10-16 | **R1 余项判定**（勘察取证 + 用户拍板）：① 检索公开面 → **并入 R3**（域侧直注实现类 `CompositeRetrieveEngine`12/`HybridSearchService`10/`VectorStoreService`9；索引面签名穿透 `Embedder`{模型族}与可变 setter `IndexInfo`；方案点名的 SearchHit/IndexService/SearchPolicy 全仓无对应物、域侧无消费者）② `contract.agent` 6 件 → **并入 R5**（跨域消费者仅 `boot/.../EngineRecordingTest`；AgentEngine 唯一生产调用方 `SessionAgentQaService` 在同模块；六件全仓无对应物）③ `contract.execution` → **判"不建"**（`contract.tool.ToolCancellation`/`ToolExecContext` 已在，引擎另有 `Supplier<String> cancellationSource`；进度为事件流无 sink 需求） | 判定入 §2.9 + 计划 v2 R1 行；R5 新情报：`AgentEngine` 直连 `platform.tracing.langfuse` 与 `platform.access/event`（拆 agent 时 platform 边比 engine 边重） |
| R1b-1 | 2026-10-16 | 契约面先行第二件：`retrieval.domain` 整包 4 件（WebSearchResult/Filters/Request/ImageInfo）→ `contract.websearch`（§8.1 目标落点）；ImageInfo 注解对位进 `ContractJsonMixinsWebSearch`；修 `AgentReferencesData` 陈旧 javadoc | 15 守卫绿 · 金片 1349 一致 · json-key 换锚 `--write`（离开 1 / 进入 1）· gate 1m57s / **4996 / 失败 0 / 跳过 4** · 冒烟 200；首轮闸门 `ImRedisStoreTest` 单例失败 → 隔离重跑 7/0 绿（已知 Redis 瞬态族） |
| R1a | 2026-10-16 | 契约面先行第一件：`llm.domain` 11 件聊天形状 `git mv` → `contract.model.chat`（ChatMessage/ChatOptions/ChatResponse/ChatTool/FunctionCall/FunctionDef/ImageUrl/MessageContentPart/ToolCall/CacheRetention/ChatConfig）+ Jackson 注解逐条对位进 `platform.web.ContractJsonMixinsModelChat`（ContractJsonModule 注册）+ 4 处不透明 JSON 字段改 `Object`（format/parameters/providerMetadata）· **逐件过跨域消费者判据**（ImageUrl 为 MessageContentPart 公开形状件随迁；StreamResponse/ProviderMetadataMaps 留 R2 公开面分层） | 15 守卫全绿（金片 1349 一致 · R10 无新增边 · C8 无新增）· json-key 换锚 `--write`（离开 5 / 进入 1，均换包）· gate 2m15s / **4996 / 失败 0 / 跳过 4** · 定向 engine 456 + domains 266 · 冒烟 sessions/knowledge-bases/models 200（后端已重启） |

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
