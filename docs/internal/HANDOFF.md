# ragagent 后端重构交接文档（2026-11-19 起）

> **定位**：本仓库自 2026-11-19 起当作新项目起点，只做**后端分包与目录结构重构**。历史开发过程（v0.1.0~v0.11.x）不在本文累积，全部在归档。
> **范围**：抽取 `docs/blueprint/architecture-blueprint.md` 中"后端分包与目录结构"一项；不做蓝图整体重建。
> **方案**：`docs/internal/plans/backend-package-restructure-2026-11-19.md`（边界、五批判据、搬家风险清单、为何不做包名对齐）。
> **归档**：v0.1.0 · v0.2.0 · v0.10.x 三份全文快照，索引见 §13。
> **纪律**：只留活信息；历史走归档 + `git log --grep`。
> **分支**：`v0.11.x`（2026-11-08 自 `v0.10.x` 的 `b419993ac` 切出）。

## ⭐ 接手须知（5 分钟）

1. **开工顺序**：§0 目标与边界 → §5 批次表（唯一待办来源）→ §9 方法论 → §10 纪律 → 干活 → 回填 §12。
2. **基线核对**：先跑 §6 全量闸门，确认 `BUILD SUCCESSFUL` 与各模块测试条数（**numbers 守恒**；`boot`/`platform` 条数缺失＝静默少跑）。
3. **待办与读数都会过期**：宣称"未完成"或据此动手前先实测（`git log` / 守卫 / 脚本输出）；**以守卫为准**。
4. **提交**：批次号 `(P#)`，写明验证方式；**推送等用户发话**（历史纪律，一字不改）。
5. **越界即停**：动前端、动数据库、解散 contract，三者任一都要先回来问。

## 0. 本次重构：目标、边界、阶段

**基线**：2026-11-19 的 `v0.11.x`，7 模块（`contract` / `platform` / `model-runtime` / `retrieval-engine` / `agent-engine` / `domains` / `boot`），19 个业务域。

**目标**：让业务域的目录与职责边界一眼可读，把"靠 Python 脚本维持的规则"改成"由 Java 测试表达"，为后续按域重写铺路。

**三条边界（越界即停）**：

1. **不动前端**：路由、请求/响应字段、错误码、SSE 帧格式全不变。
2. **不动数据库**：无 DDL、无迁移脚本、无表结构变更。
3. **不做 contract 解散、不做模块改名**。

**阶段**：P0 基线 → P1 规则先行 → P2 纯包移动 → P3 类职责归位 → P4 `api` 语义与边界校验（见 §5）。

**当前状态（2026-11-19）**：**P0~P4 全部完成**。P0 三份基线可复现；P1 两条棘轮上线（R-1 跨域直连 · R-2 注解 SQL 282）；P2 32 个 Adapter 迁入 `adapter/`；P3 回引 62 → 已消 40 / 保留 22；P4 跨域直连 2 → 1（剩 `session → agentmanagement.service`，待 contract 解散时随 `CustomAgentConfig` 契约化一并解）。全量 **5257 / 0 / 2**，守卫 **49/49**。

## 1. 仓库身份与分支

- 远程 `origin` = `https://github.com/pmbilly/ragagent.git`（公开）；主分支 `main` = `b419993ac`（2026-11-08）。
- 工作分支 `v0.11.x`（2026-11-08 自 `b419993ac` 切出）；本次重构在它上面继续。
- 网络：本机↔github 的 https 不稳；用 `git -c http.version=HTTP/1.1 push …` + 重试。
- 兼容边界：**系统未上线、无历史数据包袱、前端适配后端**（全线适用）。

## 2. 已定决策（勿再重新讨论）

**本次重构（2026-11-19 用户拍板）**：

1. **只做后端分包与目录结构**；不动前端、不动数据库。
2. **不做 contract 解散**；`contract` 保持现状，直到"按域重写"批次（与蓝图 D8 解耦）。
3. **不做蓝图 5.5 的包名对齐**（`entity` / `vo` / `bo` / `assembler`）。理由：`api/` 现装 `*Adapter`，与蓝图"对外契约"语义相反；`domain/` 混了实体、值对象、事件消费者。详见方案 §3。
4. **纯包移动（P2）与类职责归位（P3）分两批**，不混做：前者可批量 revert，后者碰行为。
5. **P3 按域推进**，一次一个域，该域测试与金片全绿才进下一个。
6. **验证不依赖浏览器录制**：以全量闸门 + 1,339 金片 + OpenAPI 快照 + Bean/端点清单 + 跨域引用清单为准。
7. 排除项：行级安全与租户插件、表结构重建、JobRunr、PGroonga、Sa-Token、MCP 官方 SDK 替换（与"不动库/不动前端"冲突，或属域重写范畴）。

**沿用（上一阶段已定，本次不改）**：

8. 保留分层单体；模块名 = 顶层包名；三个 runtime 平级、互不引用实现。
9. 域间只经契约（R10）：属主边 2 条（`auth→tenant`、`system→auth`）登记维持，同族 5 条冻结。
10. 线格式 1,339 金片逐字节不变；协议变更属产品决策、不夹带。
11. platform 不放业务形状（判据见 §4）。

## 3. 三条红线（本次不变）

1. **载体红线**：不得上提持久化实体（`@TableName`/TypeHandler/可变 setter）到 L1；contract 不放**内部件/持久化形状**；platform 不放**业务形状**。
2. **线格式红线**：金片签名 1,339 逐字节不变；确需变更 ⇒ 显式签字 + 前端确认（产品决策）。
3. **纪律红线**：**不刷新 baseline 掩盖违例**；**守卫必须能红**（新增/改守卫必配红态探针）；**闸门退出码显式取用**（任何 `| head` 不得代替）。

## 4. 两条判定标准（沿用，2026-10-16 拍板）

**contract**：跨域词汇表的**唯一一处**；零框架依赖；**每一个类都能回答"谁在跨域消费我"**。

**platform**：只剩**技术适配**（框架、HTTP、存储、提示词、追踪、事件基建）；**任何业务形状出现即红灯**。

口径：
- 判定标准是**原则**，优先于方案 §8.1/§8.2 的枚举清单（方案原文："只公布跨模块确有消费者的 API/SPI"；"不为 DDD 完整编造空层"）。
- **跨域端口/视图面扩容 = 追认**（A③ 域间契约化的必然产物）；**platform 业务形状 = 归正**（去框架化进 contract / 中性化 / 归域 / 归 agent-engine，见实施计划 v2 §1.2）。
- 验收工具：`check-contract-purity.py`（C1/C2/C3 全零）+ **C8**（platform 内 `*View/*Port/*Request/*Response/*Payload/*Facts` 命名红灯，例外须登记理由）。

## 5. 批次计划（唯一待办来源）

| 批 | 内容 | 验收 | 状态 |
|---|---|---|---|
| **P0** | 三份只读基线：`scripts/gen-package-baseline.py` 生成端点 / 装配面 / 跨域引用三份 JSON | 端点 397 · Mapper 45 · `@Configuration` 23 · 跨域边 11 · 可复现（`--check` 无变化） | ✅ 2026-11-19 |
| **P1** | 结构规则先行：新增 `PackageRestructureBoundaryTest`（R-1 跨域直连棘轮 2 条边 / R-2 注解 SQL 精确 282）+ 扫描名单核对（`@ConfigurationPropertiesScan` 11 包 / `@MapperScan` 通配 / 无 `@Aspect`） | R-1 与 R-2 均绿；`ArchitectureRulesTest` 22 条不受影响；守卫 49/49 | ✅ 2026-11-19 |
| **P2** | `api/` 里的 `*Adapter` 迁到 `adapter/`（32 个 / 11 域），纯移动不改逻辑；8 个无 `api/` 的域跳过。顺序见方案 §4.2 | 每域：编译 + 定向测试（clean 后确认条数）+ 三份快照 diff 为空 | ✅ 完成（11/11，2026-11-19） |
| **P3** | 类职责归位：`*Ops`(50) / `*Support`(21) / `*Wiring`(13) 合并回 service | 该域定向测试 + 金片 + 全量闸门 | 待做 |
| **P4** | `api/` 语义纠正（P2 已做）+ 域间边界校验 | 跨域直连 2 → 1；余 1 条已论证并写明后续归属 | ✅ 2026-11-19 |

**P2 顺序**（文件数从小到大；前 6 个域用来跑顺流程）：`favorite`(13) → `system`(18) → `vectorstore`(20) → `audit`(20) → `embedchannel`(25) → `agentmanagement`(27) → `model`(38) → `websearch`(52) → `agenttools`(59) → `storage`(62) → `memory`(91) → `im`(106) → `auth`(122) → `datasource`(147) → `wiki`(165) → `mcp`(171) → `session`(212) → `knowledge`(283)。

**阻塞项**：`knowledge ⇄ wiki` 域级循环（`KnowledgeProcessWiring` ↔ `WikiIngest*`，实现类直连而非 Port）。P1 规则上线第一天会红，P3 前必须解开。

### 5.1 历史欠账（不在本次范围；明细见归档）

H7（platform 判据换血）· H8（适配层逻辑化）· H9（管理面模板化）· L11（异常语义守卫扩面）· Q6（`KnowledgeProcessWorker` 构造面）· Q8b / Q9 / Q10 / Q11（三个前端组件拆分 + 前端 lint）· R2c-3d 余项（Ollama 能力端口、`PromptCache`）· M8（响应信封统一，待会签，不排期）。

> 已否决项（L13 / L15 / L19 / L20 / L21 / L22、`CapabilityRegistry`、RBAC 双表合并、端口改名等）见归档 §5.0b，**勿再提案**。

## 6. 环境与运行

- 后端端口 **8083**；PG 独立库名 **`ragagent`**（同实例 :15432 下另有旧仓 `WeKnora` 库 —— **排查数据前先确认库名**：`grep DB_NAME .env` 或看启动日志 jdbc url）。
- 前端：`VITE_DEV_PROXY_TARGET=http://localhost:8083`（vite 默认值已改 8083）。
- `LOCAL_STORAGE_BASE_DIR` 必须持久目录（严禁 /tmp，macOS 会清理 ⇒ 原始文件丢失）。
- **起停服**：`bash scripts/java-server-up.sh` / `bash scripts/java-server-down.sh`（up 自挡端口占用；down 是唯一正确停服方式）。
- **指标面**：`/actuator/health`、`/actuator/metrics`（只暴露 health/info/metrics）；与业务接口同一认证链，匿名 401，带 JWT 200。QA 轮次计数在 `ragagent.qa.turns` / `ragagent.qa.failures`（按 mode 打 tag），终态在 `ragagent.qa.terminal{mode,outcome=done|failed|cancelled}`，首次问答后出现。
- **⚠️ 跑完 `mvn verify` 必须重启后端**（jar 被替换而进程仍跑旧代码 ⇒ 表现为"改了没生效"；用 `spring-boot:run` 时则是 `target/classes` 被就地重写）。
- CI 三 job：guards（`bash scripts/run-guards.sh` 为准）· backend（spotless ratchet + 全量测试 + 灌 `V1__baseline.sql` 进全新 ParadeDB）· frontend（type-check + test + build）。`seed` tag 已推送（spotless ratchet 需要）。
- 全量闸门（**推送/批次收尾必跑**；迭代中按 §9 分档）：
  ```bash
  cd ~/ragagent && env -u SYSTEM_AES_KEY \
    ZZ_PG_JDBC=jdbc:postgresql://localhost:15432/RagAgent \
    JAVA_HOME=/opt/homebrew/opt/openjdk@21 mvn -B verify
  ```
  **`ZZ_PG_JDBC` 必给**：`GrepChunksRecordingTest` 与 `DatabaseQueryRecordingTest` 默认连 `WeKnora`（CI 的库名），本机库名是 `RagAgent`；不给会在 domains 报 `database "WeKnora" does not exist`，两个测试设计为「不可达即失败、不许跳过」。
  期望：`BUILD SUCCESSFUL` + 各模块测试汇总条数守恒（2026-11-19 全量实测 **5257 / 失败 0 / 跳过 2**：contract 12 · platform 262 · retrieval-engine 286 · model-runtime 320 · agent-engine 157 · domains **2862** · boot **1358**；旧口径 5082 已过期）。`PATH` 须 `/opt/homebrew/bin` 在前（python3.9 会让 file-size 假红）。
  **环境前置**：PG `max_connections=100`，多个 boot jar 残留会占满连接（实测 5 个实例 = 50 条 ⇒ `too many clients`）。跑闸门前先 `ps -Ao pid,command | grep '[b]oot/build/libs/boot'` 清残留。
- **⚠️ IDE 自动构建会与 Maven 抢 `target/`**：2026-11-19 实测，`clean verify` 期间 IDE 写入半成品 class，domains 测试随机报 `Unresolved compilation problems`（JDT 专属措辞）或 `cannot access Xxx` / `incompatible types`，且每次报错的文件不同。**判别：换一个错误文件就是竞争，不是回归。**
  根因是语言服务器进程用 Homebrew openjdk **27** 启动（`ps -Ao command | grep eclipse.jdt.ls` 可证），与 Maven 的 21 不一致。分两层处理：
  1. **语言服务器 JVM 固定到 JDK 21**：`.vscode/settings.json` 的 `java.jdt.ls.java.home` 指向 `/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`。`.vscode/` 已整体 gitignore，各人本地配置。
  2. **关闭自动构建**：`java.autobuild.enabled: false`，放用户级设置——它会写 `target/classes`，与 `clean verify` 抢目录。
  **该设置对已启动的语言服务器不生效**：2026-11-19 实测，用户级 10-16 就设了这项，但 LS 仍跑 27；`⌘⇧P` → `Java: Clean Java Language Server Workspace` 也不够，**必须完全退出 IDE 再启动**。验证：`ps -Ao command | grep "[e]clipse.jdt.ls" | tr ' ' '\n' | grep -m1 openjdk`。
  若仍复现，二选一：跑闸门前 `pkill -f eclipse.jdt.ls`，或把后端复制到独立目录构建（`rsync --exclude target --exclude .git --exclude frontend`，守卫留在真实仓库另跑）。
- **dev 数据修复（2026-10-20）**：软删守卫误拦窗口（H5b~H5b-fix 之间）里生成失败的 5 条 embed 历史回答，已按会话用 embed `ensure` 补建 `message_suggestion_sets`（状态 `suppressed/disabled`，该智能体追问关闭）；全库 embed 历史已无「缺建议集合」行。
- 前端（触契约时）：`cd frontend && npx vue-tsc --build --force` + `npm test`。

## 7. 测试与安全网

- 回归网：**1,339 契约金片 + 5,257 条用例**（2026-11-19 全量实测，失败 0 / 跳过 2）；每一步（哪怕纯移动）结束必须全绿。
- 已知偶发（全量并发下，单独重跑即绿，勿误判回归）：`WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool` · `EvaluationContractTest.getTerminalRunsExecution` · `EmbedRateLimiterTest.redisPathSharesBudgetAcrossInstances`。
- **Redis 瞬态族已修（2026-10-19）**：根因是 `EmbeddedRedis` 用 `ServerSocket(0)` 探端口在 `maxParallelForks` 下会撞——后到者借用先到者的实例，先到者 close 杀进程后连接全断。现改为固定端口池 + 跨进程 `FileLock`，红态探针 `EmbeddedRedisPortAllocationTest` 守着；全量连跑 5 遍 0 失败。再见到同类失败按真回归查。
- 覆盖率：**未执行基线**（`-Pcoverage`）⇒ 本项目**不声称任何覆盖率数字**（开放项）。
- 前端：`vue-tsc` 是最终文件集上的最后一步（`tsx --test` 只转译不查类型）。
- **契约金片**：`platform/src/testFixtures/resources/golden/`（`agent-45a/45b/45c/46/46b`、`chat-pipeline-46c`、`modelcontext-46a`，各带 `.sha256`），加载器 `platform.fixtures.GoldenResources` 逐字节校验；"重录"= 按当前实现同步常量并重算 sha256，禁止手改 JSON 或 sha256；新增 bundle 时使用方模块依赖 `platform` 的 `test-jar`。

## 8. 结构地图与守卫总表

### 8.1 现状模块（7）与目标（7）

> 计数口径：`<模块>/src/main/java/**/*.java`，**不含 package-info**（2026-10-16 R5a 后实测）。

```
现状（2026-10-24 实测，此后未重测）      目标（上一阶段方案 §5.1，非本次目标）
contract      293 件（纯 JDK，零依赖块）  →  contracts/
platform      174 件（技术适配 + 追踪装饰/流句柄/共享传输 + 提示词/配置）→ platform/
model-runtime 134 件（llm 52 · modelruntime 39 · embedding 19 ·
                 modelcontext 12 · rerank 12）
                                        →  model-runtime（已成）
retrieval-engine 80 件（engine 69 · 根 4 · graph 3 · support/artifact/config/telemetry 各 1）
                                        →  retrieval-engine（已成）
agent-engine   63 件（根 19 · tool 15 · compaction 11 · skills 8 · prompt 7 · budget 2 · domain 1）
                                        →  agent-engine（已成）
domains      1366 件（knowledge 232 · session 182〔含 rag 50〕· mcp 155 · wiki 145 ·
                 datasource 128 · auth 104 · im 88 · memory 77 · agenttools 53 · …）
                                        →  domains
boot           17 件                      →  boot

依赖方向：boot → 全部；domains / 三个 runtime → platform, contract；platform → contract；contract → JDK
（**`:engine` 已解散**：chatpipeline → `domains/session/service/rag`、webfetch/抓取面 → `domains/websearch/fetch`
 ⇒ 旧的 `api(:model-runtime)`/`api(:retrieval-engine)` 传递边随模块消失；domains/boot 显式声明
  各 runtime（含 R5a 的 `:agent-engine`，domains 那条是**过渡边**待 R5b/R5c 收窄）
 ——其中 domains→model-runtime 是**过渡边**，待 R2c-3d 余项收完）
```
### 8.2 域内分层（上一阶段方案 §6，本次沿用）

`controller`（绑定/帧写出）· `service`（用例与政策）· `domain`（状态与不变量）· `mapper` · `repository` · `dto` · `client` · `task` · `api` · `support` · `config`（只装配）。
禁止：`service` 引边界层/具体技术（A16/A17）· `domain` 引 Spring/Mapper · `repository` 回引 service · `controller` 跑编排 · `mapper` 放仓储门面（A11b）。

### 8.3 守卫与规则总表（**全部守卫 + ArchUnit**；新增规则前先查这里）

| 守卫 | 语义 | 基线 |
|---|---|---|
| `check-package-cycles.py` | 环/分层/域依赖/SCC（R1~R9） | `package-cycles.baseline.json`（只许减；包级 SCC 5 组） |
| `check-domain-contracts.py` | **R10 域间只经契约**（分档①②③） | `domain-contracts.baseline.json`（**6 条边 / 27 类**；clearable 0） |
| `check-contract-purity.py` | C1 导入根（纯 JDK）· C2 构建依赖 · C3 框架形状 | 硬零，无基线 |
| `check-contract-sync.py` | 金片键签名变化必须签字 | 1,339 金片 |
| `check-json-key-case.py --strict` | 换锚棘轮 + 冻结覆盖快照 | 冻结面登记 |
| `check-event-face-case.py` | 事件面/路由面命名口径 | — |
| `check-file-size.py` | 大文件棘轮（>600 棘轮，≥800 须登记） | `file-size.baseline.json` |
| `check-hygiene-budget.py` | 卫生预算计数（>100 行方法 / 通用 trim 副本 / ≥8 参构造器） | `hygiene-budget.baseline.json`（106 / 11 / 53，只许减） |
| `check-tool-registry.py` | 工具注册一致性（T1~T4 实现/分发/装配；T5 工具名常量只许在 ToolDefinitions；T6 前端能力表键 ⊆ 后端工具名） | 22 实现 / 22 条分发 / 33 个常量 |
| `check-go-anchors.py` | Go 锚点注释棘轮 | 0 |
| `check-fe-contract-keys.py` | 前端契约键棘轮 | 只许减 |
| `check-stray-dirs.py` | 游离/死包目录 | 0 |
| `check-dead-members.py` | 死成员（main+test+testFixtures） | 0 |
| `check-api-envelope.py` | 统一响应外壳 | 只许减 |
| `check-exception-semantics.py` | 检索引擎 catch 不得清空边界条件 | 0 |
| `check-package-dirs.py` | 包声明 ↔ 目录一致（含 testFixtures） | 0 |
| `check-module-graph.py` | 主码依赖矩阵 · 同包 · 测试边登记 · 测试错位登记 · 夹具包 | 登记表（只许减） |
| `check-platform-shapes.py` | platform 业务形状（业务词包 / 业务词文件 / 红旗命名三档） | `platform-shapes.baseline.json`（15 条） |
| `check-contract-shape.py` | R1/R2 契约形状预算（端口 ≤7 方法 / 行视图禁入 / 字段 ≤20） | `contract-shape.baseline.json`（19 条 = 端口 4 / 行视图 7 / 字段 8，只许减） |
| `gen-contract-consumers.py --check` | 每个 public 契约类型至少有一个消费者（跨模块 import 或 contract 内同包引用） | 硬零 |
| `check-port-budget.py` | R1 端口准入（域内 / 无实现端口 = 0） | `port-budget.baseline.json`（空） |
| `gen-arch-index.py --check` | `package-info` 与 `docs/architecture/packages.md` 同步 | 硬零 |
| `check-tenant-scope.py` | 跨线程身份必须走 `TenantScope`；禁止裸 capture/replay | 硬零 |
| `check-log-exceptions.py` | log 里禁止 `Throwable.toString()` / `.getMessage()` | 硬零 |
| `check-duplicate-classes.py` | 同一简单类名不得出现在多个模块主码 | 硬零 |
| `check-internal-codes.py` | 主码注释禁止批次号/裸日期戳（ISO-8601 时间示例保留）；旧夹具名不得回流 | 硬零 |
| `gen-module-map.py --check` | 模块/包根、业务域文件/控制器数量与 `module-package-map.md` 同步 | 硬零 |
| `check-version-consistency.py` | Boot/springdoc/MyBatis-Plus jsqlparser/README/wrapper/Dockerfile 版本一致 | 硬零 |
| `check-method-length.py` | 方法长度棘轮（>150 行只许减，新增即红） | `method-length.baseline.json`（只许减） |
| `check-wiring-points.py` | 装配点清单（W1~W4：@Configuration 必须登记；分类/理由/overview 三处一致） | `wiring-points.baseline.json` |
| `check-doc-truth.py` | 文档事实（README 的模块数/控制器数/package-info 覆盖率必须与代码一致） | 硬零 |
| `check-known-gaps.py` | 已知缺口（类注释声明缺口必须指向 `HANDOFF §11.1`） | 硬零 |
| `check-log-level.py` | 日志级别（warn 不用于 404 / 跳过语义） | 硬零 |
| `check-controller-purity.py` | 控制器纯度（HTTP 面只编排：禁直连 Mapper；裸转发 catch 只许减） | `controller-catch.baseline.json`（只许减） |
| `check-route-policy.py` | 路由权限覆盖（未登记公开路由/幽灵规则/对照表过期） | `route-policy.baseline.json`（384 路由 / 372 规则） |
| `check-controller-entity-imports.py` | 控制器实体导入（C10：只许降） | `controller-entity-imports.baseline.json`（0 / 0） |
| `check-ingest-flow.py` | 入库链路文档（`ingest-flow.md` 引用的类必须存在） | 硬零 |
| `gen-pipeline-map.py --check` | 管线事件表（`pipeline-map.md` 必须与插件代码同步） | 硬零 |
| `gen-port-map.py --check` | Port 对照表（`port-map.md` 必须与接口/实现/调用方同步） | 硬零 |
| `check-qa-flow.py` | 链路文档（`qa-flow.md` 引用的类必须存在） | 硬零 |
| `check-schema-parity.py` | schema 对账（TestSchema 建表 vs migrations/versioned） | 硬零（55 张表） |
| `check-reference-truth.py` | 引用真实性（package-info/公开文档不得断链、旧包名、死路径） | 硬零 |
| `check-capability-kinds.py` | 能力种类边界（跨 kind 直连禁止；同 kind 允许） | 硬零 |
| `check-package-info-fields.py` | 业务域 package-info 四要素（种类/入口/契约面/依赖） | 硬零 |
| `check-package-size.py` | 业务域包规模（>30 只许降；26-30 报警） | `package-size.baseline.json` |
| `check-comment-narrative.py` | 迁移叙事棘轮（注释只讲现在，不写迁移史） | `comment-narrative.baseline.json`（0） |
| `check-comment-decor.py` | 注释装饰（禁止整行横线分隔，统一 `// ── 段名 ──`） | 硬零 |
| `gen-domain-index.py --check` | 业务域索引（`domain-index.md` 必须与分类/源码同步） | 硬零 |
| ArchUnit `ArchitectureRulesTest` | A1~A20（含 A16/A17 边界禁令、A18 状态所有权、A19 分层、A20 装配契约） | 各基线只许减 |

> 守卫总数以 `scripts/run-guards.sh` 的 `run` 行数为准（当前 48 条）；本表是语义索引，二者增删要同步。

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

### 11.1 已知缺口登记（代码注释只留一行指针：`见 HANDOFF §11.1`）

> 口径：状态（设计取舍 / 待实现 / 待核查）、影响、替代路径。新增缺口登记在这里，不在类注释里展开。

| # | 缺口 | 状态 | 影响 / 替代 |
|---|---|---|---|
| 1 | 跨租户共享（org-share / shared-agent） | 设计取舍 | 不实现跨租户授予链；同租户按成员角色授权，API Key 按租户隔离 |
| 2 | 资源目录 / 共享授权解析（产物、附件） | 设计取舍 | 产物下载恒 404；消息文件代理对 owner ≠ caller 恒 403；公开资源走预签名 URL |
| 3 | 初始化 `storageBackendId` 解析分支 | 待实现 | 该字段被忽略、按缺省存储处理；先建后端再切换 |
| 4 | `POST /system/sandbox-check` | 待实现 | 路由未登记，Spring 返回 404 |
| 5 | 连接器目录中的规划项 | 待实现 | 目录 17 条 / 实现 9 个；未实现类型不可创建 |
| 6 | Agent 推荐问题的 Wiki fallback | 设计取舍 | 无 FAQ/chunk 的知识库推荐问题恒空；手工配置开场问题 |
| 7 | `allowed_tools → capability` 派生表 | 待实现 | 只做白名单过滤，不派生能力表 |
| 8 | 模型运行时 debug / langfuse 装饰器 | 设计取舍 | 只有 `LangfuseChatClient` / `LangfuseReranker`；embedding/rerank debug 装饰不存在 |
| 9 | Langfuse 概率采样 | 待实现 | `sampleRate` 在 (0,1) 时不生效，trace 恒采样 |
| 10 | 提示词缓存指纹元数据 | 待实现（非行为面） | `prompt_prefix_fingerprint` / `call_purpose` 未写入 span |
| 11 | 消息建议的 Langfuse span | 待实现 | 该链路无 trace |
| 12 | 向量店进程内引擎注册表 | 设计取舍 | 注册表为空，连通性探测走厂商直连 |
| 13 | OpenSearch 超大批量 scroll | 待实现 | 单次写入超批量上限时不分页；调小分块批量 |
| 14 | Ollama 私部署分支 | 设计取舍 | 只支持 `localhost`，不处理自定义私部署路径 |
| 15 | 工具 SPI 迁入 `contract.tool` | 已关闭 | `AgentTool` / `BaseTool` / `ToolDefinitions` 均在契约层 |
| 16 | 审批闸门未装配 | 设计取舍 | MCP OAuth / 工具审批受理分支返回 500，不静默放行 |
| 17 | IM 知识桥接的存疑接线 | 待核查 | 生产 `/info` 与 `/search` 的 KB/检索面恒为空 |
| 18 | Wiki 页面 → chunks 同步 | 待实现 | Wiki 内容不写入 `chunks`，不参与常规 RAG；走 Wiki 专用检索 |
| 19 | `Provider.validateConfig()` 生产接线 | 已关闭（Q1） | `ModelConfigValidator` 在模型保存（创建/更新/凭据）路径统一调用，只做结构校验 |
| 20 | `system → auth.adapter.AdminUserAdapter` 跨域直连具体类 | 待实现 | `AdminUserAdapter` 未实现任何接口，system 两个 service 直接注入（字段名却叫 `adminUserPort`）；根治需在 `contract.auth` 声明端口并把 `CreateUserResult` 上提。P2-4 移动时暴露，现登记在 R-1 棘轮基线 |

## 12. 执行记录（本次重构；模板：批次｜内容｜读数｜验证）

| 批 | 日期 | 内容 | 读数 / 验证 |
|---|---|---|---|
| **P0** | 2026-11-19 | 新增 `scripts/gen-package-baseline.py`，静态生成三份基线（不启动应用）：`endpoint-inventory.json`（397 条控制器映射）、`bean-inventory.json`（45 Mapper / 23 `@Configuration` / 0 `@Aspect` / `@MapperScan` 通配 / `@ConfigurationPropertiesScan` 11 包 / `spring.factories` 1 处 / `logging.level` 1 条）、`cross-domain-imports.json`（19 域、11 条跨域边、19 个域均引 contract） | `--write` 后 `--check` 三份均无变化（可复现）；守卫 48/48 绿 |
| **P1** | 2026-11-19 | 新增 `boot/src/test/java/com/ragagent/arch/PackageRestructureBoundaryTest`：**R-1** 跨域直连棘轮（19 域白名单，禁入对方 `repository`/`mapper`/`domain`/`service`/`controller`，基线 2 条边：`knowledge→wiki.service`、`session→agentmanagement.service`）；**R-2** 注解 SQL 精确计数（282 个方法，新增或减少都红，强制同步基线）。`ArchitectureRulesTest.MAIN` 改包内可见以复用一次性导入。四条原计划规则中三条已由既有 A7/A11/A11b/A16/A19/C10 覆盖，未重复造 | R-1/R-2 绿 · `ArchitectureRulesTest` 22 条绿 · boot 1,358 条绿 · domains 2,862 条绿（隔离目录） · 守卫 49/49 · 金片 1,339 未动 |
| **P2-1** | 2026-11-19 | `agentmanagement`：`api/AgentLookupAdapter` → `adapter/`，包声明与测试同步移动，删空 `api` 包（该域无其它契约类）；`packages.md` 重新生成（357 包）。**P2 定义修正**：全仓 19 域分层已统一，无错位目录可移；唯一语义错位是 `api/` 装 Adapter（32 个 / 11 域），故 P2 改为 `api/` → `adapter/`，原 P4 的语义纠正由本批完成 | `AgentLookupAdapterTest` 2/2 绿（**clean 后确认只跑 2 条**——增量构建会残影出旧包的 4 条）· 三份快照无变化（397/45/11）· 守卫 49/49 |
| **P2-2** | 2026-11-19 | `audit` / `model` / `wiki` / `memory` 四域各 1 个 Adapter 迁入 `adapter/`：前三者 `api` 包随之清空删除；`memory/api` 保留（`MemoryAccess` 是 session 依赖的对外入口）。顺带修 `memory` 两个 `package-info` 的过期描述——原文写 `MemoryAccessAdapter`，该类并不存在（`check-package-info-fields` 只查要素齐全，查不出"引用了不存在的类"，这类失真只能靠移动时人眼核对） | domains 主码 + 测试源集编译绿 · 三份快照无变化（397/45/11）· 守卫 49/49 · `packages.md` 357→358 |
| **P2-3** | 2026-11-19 | `mcp` / `websearch` 各 2 个 Adapter 迁入 `adapter/`，两域 `api` 包清空删除；`package-info` 随包名同步（原文已列全两个 Adapter，无失真） | domains 主码 + 测试源集编译绿 · 三份快照无变化（397/45/11）· 守卫 49/49 |
| **P2-4** | 2026-11-19 | `auth` 的 3 个 Adapter 迁入 `adapter/`，`api` 保留（`AuthUserErrors` / `CreateUserResult` 被 system 引用）。**移动暴露一条隐藏违规**：`system` 的两个 service 直接 import `auth.api.AdminUserAdapter`（具体类，字段名却叫 `adminUserPort`），原先躲在 `api` 包里，R-1 的禁入列表看不到。处置：R-1 的 `FORBIDDEN_TARGETS` 加 `adapter`，该边登记进基线（2→3 条），根治（提 `contract.auth` 端口 + `CreateUserResult` 上提）记 §11.1 第 20 条。`AdminUserAdapter` 原来靠同包可见引用 `AuthUserErrors` / `CreateUserResult`，移包后补显式 import | R-1 3 条基线绿 · R-2 282 绿 · domains 编译绿 · 守卫 49/49 · 跨域快照 11→12 条（新增 system→auth.adapter，已接受为基线） |
| **P2-5** | 2026-11-19 | `storage` 的 4 个 Adapter 迁入 `adapter/`，`api` 包清空删除。`api` 无外部引用，唯一受影响的是测试 `TenantFileStorageTest` 的 import（主码 grep 不到、测试目录才暴露——**侦察必须连 `src/test` 一起扫**） | domains 主码 + 测试源集编译绿 · 三份快照无变化（397/45/12）· 守卫 49/49 |
| **P2-6** | 2026-11-19 | `session` 的 5 个 Adapter 迁入 `adapter/`，`api` 包清空删除；`package-info` 从"等契约端口的适配层"改为列全 5 个实现 | domains 主码 + 测试源集编译绿 · 三份快照无变化（397/45/12）· 守卫 49/49 |
| **P2-7** | 2026-11-19 | `knowledge` 的 11 个 Adapter 迁入 `adapter/`，`api` 只留 `FinalizeResult`（wiki 消费收尾结果）；域外 10 个文件改 import，测试 `KnowledgeFinalizeAdapterTest` 同步移包，`KnowledgeFinalizeAdapter` / 该校验测试补 `FinalizeResult` 的显式 import（原靠同包可见）。**最大发现**：`wiki` 引用的 `knowledge.api.*` 6 处里有 5 处是 **Adapter 具体类**（`ChunkPortAdapter` / `KnowledgeFinalizeAdapter`），不是契约——`knowledge ⇄ wiki` 循环两边都是实现类直连，此前被 `api` 包名掩盖。R-1 基线 3→4 条 | R-1 4 条绿 · R-2 282 绿 · domains 主码 + 测试源集编译绿 · 守卫 49/49 · 跨域快照 12→13 条 |
| **P2 收官** | 2026-11-19 | 全量闸门暴露两处移动副作用并修复：① `wiki` 两个文件 `knowledge.adapter.*` 的 import 顺序（spotless，`adapter` 应在 `api` 前）；② A7 裸 JDBC 白名单里 `auth.api.TenantStorageQuotaAdapter` 类全名随移动失效（改 `auth.adapter.*`）。两处都是"按类全名/文本顺序记录"的守卫在包移动后的必然漂移，**P3 搬类名前必须按同样清单先搜一遍** | **`mvn -B clean verify` 绿：5257 / 0 / 2**（contract 12 · platform 262 · retrieval 286 · model-runtime 320 · agent-engine 157 · domains 2862 · boot 1358）· 守卫 49/49 |
| **P3-0** | 2026-11-19 | **解 `knowledge ⇄ wiki` 双向实现类直连**（P3 前置）：新增 4 个契约——`contract.knowledge.ChunkPort`（4 方法）、`KnowledgeFinalizePort`（1 方法）、`contract.wiki.WikiIngestPort`（1 方法）、`WikiKnowledgeFinalizer`（1 方法，原在 `wiki.service`）；上提 2 个类型——`FinalizeResult`（`knowledge.api` → `contract.knowledge`）、`WikiEnqueueResult`（`wiki.support` → `contract.wiki`）；4 个实现类补 `implements`，9 个调用方改注入端口。**R-1 基线 4 → 2 条**（循环四条边同时消失），跨域快照 13 → 10 条，port-map 41 → 44。实施踩到三处：① 类型名批量替换误伤 wiki 域内对 `WikiIngestService` 的 28 处使用（域内应继续用具体服务，只有 knowledge 侧换成端口）；② `enqueueWikiIngest` 的 `tenantId` 实际是 `long`，端口签名按 `String` 写会编译失败；③ `WikiIngestService.PendingBatch` 是嵌套类，删 import 后需显式导回 | **`mvn -B clean verify` 绿：5257 / 0 / 2** · R-1 2 条基线绿 · R-2 282 绿 · 守卫 49/49 · 金片 1,339 未动 |
| **P3-1** | 2026-11-19 | **示范批：`WikiPageLinkOps`(246) → `WikiPageLinkService`**（`@Service`，构造注入 `WikiPageRepository` + `ObjectProvider<WikiCrossLinker>`，不再回引父服务）。关键不是"提升为 Bean"，而是**先拆双向调用**：`injectCrossLinks → 父类.updateAutoLinkedContent → removeInLinks` 是真实回环；把 `updateAutoLinkedContent` 的**实现**搬入 link 服务（`WikiPageService` 接口方法保留在父类做委托），`removeString` 静态工具随迁，父类摘掉已无使用点的 `crossLinker` 依赖（构造参数 8 → 7，避免卫生预算 +1）。影响 6 个文件（含 2 个测试的类名引用），行为零变化 | **`mvn -B clean verify` 绿：5257 / 0 / 2** · `WikiPageServiceTest` 52 条绿 · 守卫 49/49。**示范结论**：单协作者约 1 小时；`wiki` 剩余 13 个协作者须逐个确认是否也有双向调用 |
| **P3-2** | 2026-11-19 | `wiki` 两个**纯依赖**协作者改构造注入（轻量改法，不提升 Bean）：`WikiPageFolderSupport` → `(WikiFolderRepository, WikiPageRepository)`、`WikiPageLinkRepair` → `(WikiPageRepository)`；父类 `new` 时传窄依赖，**构造参数仍 7 个**。**方法论修正**：回引清单必须按 `new X(this)` 扫，按类名后缀统计会①混入接口（`WikiDedupSupport` 是端口）②漏掉 `*Phase`/`*Dedup`/`*Repair` 命名。实测全仓 **70 处**回引：wiki 16 · mcp 12 · datasource 11 · memory 10 · im 7 · auth 6 · session 6 · knowledge 4 | 全量绿 5257 / 0 / 2 · `WikiPageServiceTest` 52 条绿 · 守卫 49/49 |
| **P3-3** | 2026-11-19 | `WikiIngestService` 的 4 个协作者改构造注入：`SettleOps` → `(deadLetterRepo, knowledgeFinalizer, pendingRepo, queueOps)`、`PageOps` → `(crossLinker, wikiService)`、`IndexOps` → `(llm, wikiService)`、`ExtractDedup` → `(dedupSupport, llm, wikiService)`；`cleanupScope()` 改 `WikiCleanupScope.open()` 静态工厂。装配顺序两处调整（`queueOps` 提到 `settleOps` 前、`llm` 提到 `indexOps`/`extractDedup` 前），顺序错会拿到 null。**wiki 收口**：回引 **17 处 → 已消 10 / 保留 7**，保留的按方案 §7.2 三分类判据（编排阶段 4 · 运行态状态 1 · 父类真实业务 2） |
| **P3-4** | 2026-11-19 | 再消 3 处：`WikiIngestLlmSupport` 抽 `LlmWarmupState`（单飞锁 / 预热标记表 / 回收线程，父类与协作者**共享同一实例**——两份单飞锁等于单飞失效）；`MapPhase` / `ReducePhase` 改依赖新增的 `WikiIngestStageContext`（7 字段 + 2 个薄查询），handler 只负责装配。**wiki 最终收口：17 处 → 13 消 / 4 留**。`run` / `finalize` 不搬的理由：它们需要 `newWikiBatchContext`（150 行批次上下文**工厂**）与 `scheduleFollowUp`，把工厂塞进"依赖容器"会让 context 从 40 行涨到 150 行，等于 god class 换名；`RunSupport` 是编排者，持有工厂能力属其职责 | 全量绿 5257 / 0 / 2 · domains 2862 条绿 · 守卫 49/49 |
| **P3-5~10** | 2026-11-19 | 其余域按同一判据推进：mcp 3（`OAuthDiscovery` / `McpUsageInstructionsOps` / `McpServiceCrudOps`）· memory 5（3 个 store 直接注入 mapper，探测状态随 store 迁走）· datasource 4（`FeishuTransport` + 3 个 service 协作者）· auth 4（3 个 `Tenant*Ops` + `UserSessionOps`）· embedchannel 2 · im 3（`FeishuCallbackOps` / `ImSessionResolver` / `ImQaRequests`）· session 5（`MergeParentOps` / `Plugin*Ops` / `SessionQaFallback` / `QaWebSearchParams`）· agenttools 1 · knowledge 1（`FaqValidateOps`） | 每批 `mvn -B clean verify` 绿 · 守卫 49/49 |
| **P3 收口** | 2026-11-19 | **回引 62 处 → 已消 40 / 保留 22**。保留的 22 处按方案 §7.2 五分类：父类真实业务 11 · 业务链 4 · 编排工厂 2 · 运行态状态 2 · 超参数量 1 · 传 `this` 即等价 2。过程中修正两处统计口径：① 回引要按 `this.x = new Y(this)` 扫（`throw`/`return new Y(this)` 是误报）；② 原始总数 62 = 已消 40 + 保留 22，此前"全仓 49 处"是改造中期的剩余量 | 全量绿 5257 / 0 / 2 · 守卫 49/49 |
| **P4-5** | 2026-11-19 | **retrieval 契约归位 13 个**：`contract.retrieval` 的端口 / 索引引擎 / 向量索引存储 / 异常 / 工具移到 `retrieval/api`；**4 个必留**（`SearchParams` / `SearchResult` / `SearchTarget` / `RetrieveGraphRepository`）——它们被 contract 内其他包（`agent` / `session` / `knowledge` / `model` / `pipeline`）或 platform 的 mixin 引用。**新判据**：移动前必查**两类**引用——① 保留类型是否引用待移类型；② **contract 其他包是否引用待移类型**（后者这次才发现：`contract.agent` 依赖 `contract.tool` / `contract.retrieval`，`contract.pipeline` 是隐藏枢纽，引用了 7 个此类类型）。副作用：`contract.model ⇄ contract.retrieval` 的包级 SCC 消失（5 → 4 组）；`check-json-key-case.py` 的冻结面按包路径登记，需同步新路径 | 全量绿 5257 / 0 / 3 · 守卫 49/49 · SCC 5 → 4 组 |
| **P4-4** | 2026-11-19 | **引擎面归位第一批**：`contract.vectorstore` 的 8 个类型（查找端口 / 视图 / 引擎类型 / 必填字段 / 地址字段）移到新建的 `retrieval-engine/retrieval/api`；**只留 `ConnectionConfig` / `IndexConfig` 在 contract**。流程验证出四条：① 移动前必查"保留类型是否引用待移类型"，否则 contract 反向依赖引擎；② `domains → retrieval-engine` 需加 pom 依赖并同步 `check-module-graph.py` 矩阵（蓝图 5.2 允许，且 retrieval 不依赖 domains，不成环）；③ 新包必须有 `package-info.java`（D3 守卫）；④ 跨模块改 import 后 spotless 要对**每个**受影响模块分别 apply。**结构性障碍已确认**：`ContractJsonMixins` 系列 1274 行 / 12 文件 / 单个类引用 52 个契约类型——platform 必须认识所有契约类型，所以归位只能是"形状留 contract、接口移引擎" | 全量绿 5257 / 0 / 3 · 守卫 49/49 · 模块矩阵新增 domains→retrieval-engine |
| **P4-3** | 2026-11-19 | **消逆级依赖 6 处**（基础域反向依赖业务域契约）：① `MemoryConfig` → `contract.tenant`——它本就存在 `tenants` 表的 jsonb 列，放 `contract.memory` 是历史错位（`TenantConfigOps` 已 import 6 个 `contract.tenant.*` 配置类，此举纠正不一致）；② `KnowledgeBaseProvisioner` → `contract.tenant`（"租户开通知识库"属租户生命周期）；③ `DocReaderAdminPort` / `ParserEngineCatalogPort` → `contract.settings`（外部解析服务的管理端口，`settings` 本就是系统设置契约；`DocReaderParsePort` 被 4 个域用，留 `contract.doc`）；④ 新增 `platform.web.AcceptLanguages`（纯 header 解析 + `zh-CN` 兜底），auth 不再依赖 `contract.wiki`。**余 3 处保留**：`KnowledgeBaseFacts/Gateway`（auth 的 API Key 校验、audit 的名称展示）与 `RetrieveGraphRepository`（系统统计）——都是"跨域只读查询"，属授权/审计固有需求，搬包只会换位置。实施踩坑：javadoc `-Werror` 抓到 6 处 `{@link}` 断链（类移包后简单名失效），需改全限定；`localeOrFallback` 少了 `zh-CN` 兜底会导致金片 `ct-kv-get-prompt-templates` 退回英文原文 | 全量绿 5257 / 0 / 2 · 守卫 49/49 · 跨域快照 9 条不变（这几条是 contract 引用，不进 R-1） |
| **P4-2** | 2026-11-19 | `session → agentmanagement` 从 6 处压到 4 处：`TagScope` 从 `AgentSuggestedQuestions` 内部 record 上提到 `contract.agent`；新增 `contract.agent.AgentSuggestionPort`（1 方法，**返回 JSON 文本**——契约模块不引 Jackson），`CustomAgentService implements`，`MessageSuggestionService` 改注入端口、`parseCandidates` 改为接收文本。**两处约束冲突**：`AgentConfigJson`（补默认值工具，带 Jackson）两次下沉均被拦——`contract` 零第三方依赖、`platform` 的 C8 守卫禁业务词文件（`Agent`）。故它保留在 `agentmanagement.service.config`，R-1 基线仍 **1 条**（`session -> agentmanagement.service`，仅剩 4 处静态调用）。`MessageSuggestionService` 触碰 600 行红线，压缩两处 `if` 后回到 598 | 全量绿 5257 / 0 / 2 · R-1 1 条绿 · 守卫 49/49 · 跨域快照 10 → 9 |

> 历史批次（v0.3.0~v0.11.x，含 P/R/L/M/H/K/X/Q/B269 系列）全部在归档 `handoff/archive/HANDOFF-v0.10.x-2026-11-19.md` §12；追溯用 `git log --grep`。

## 13. 文档地图与归档

| 文档 | 用途 |
|---|---|
| `HANDOFF.md`（本文） | **唯一指挥面**：本次重构的目标 / 批次表 / 纪律 / 环境 / 结构地图 |
| `docs/internal/plans/backend-package-restructure-2026-11-19.md` | **本次方案**：边界、五批判据、搬家风险清单、为何不做包名对齐 |
| `docs/blueprint/architecture-blueprint.md` | 未来架构蓝图（只作参考，本次只抽取分包一项） |
| `docs/internal/handoff/archive/HANDOFF-v0.10.x-2026-11-19.md` | v0.10.x 全文快照（v0.5.0~v0.11.x 全部逐批记录、各专项明细与否决理由） |
| `docs/internal/handoff/archive/HANDOFF-v0.2.0-2026-10-16.md` | v0.2.0 全文（结构规范 / 方法论全量 / 逐批记录） |
| `docs/internal/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md` | v0.1.0 全文（远古追溯） |
| `docs/internal/handoff/records/batch-records.md` | v0.1.0 早期逐批记录（~B110） |
| `docs/internal/plans/java-remediation-plan-2026-10-13.md` | 上一阶段整改方案（目标结构 / 状态所有权的出处） |
| `docs/internal/plans/java-remediation-implementation-plan-v2-2026-10-16.md` | 上一阶段 v0.3.0 执行计划（P/R 批次细节） |

