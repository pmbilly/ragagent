# ragagent 交接文档（v0.2.0 全面整改版）

> **换版说明（2026-10-13，B235）**：本文件以 v0.2.0 全面整改为新起点重写。
> v0.1.0 迭代全文（建仓/裁剪/神类拆分/契约换锚/统一外壳/守卫建设等批次史）已归档为
> **`docs/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md`**；历史细节仍可用 `git log --grep="(B###)"` 取回。
> **外部引用注意**：§8 / §9 / §10 / §13 / §14 / §15.2 / §15.3 **编号与语义保持不动**（docs/ 与源码注释有 40+ 处按号引用）；
> §11 压缩为指针；**§12 为新建**（原多处引用"§12 结构地图"长期悬空，此次补齐）；§0/§2/§4/§5/§7 重写；§15.1 换为 R 系列批次表。
> 方案与计划：`outputs/java-remediation-plan-2026-10-13.md`（整改方案）· `outputs/java-remediation-implementation-plan-2026-10-13.md`（具体实施计划）。

## ⭐ 接手须知（v0.2.0 版，5 分钟）

0. **本版是什么**：v0.1.0 的"脱 Go / 提可读性"各轴已收官（见 §7）；**v0.2.0 = 按整改方案做全面整改**（正确性修复 → 结构收敛 → 整体包结构整改），批次表见 §15.1。
1. **开工顺序**：读 §7 现状 → §15.1 挑批次（按 ⬜ 顺序）→ §15.2 纪律 → 干活 → 回填 §15.1 状态 + §15.4 记录。
2. **第一步永远先验基线**：`env -u SYSTEM_AES_KEY JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew spotlessCheck build`（期望 **4,908 用例 / 0 失败 / 跳过 4**）＋ 十一守卫（已接入 build）。
3. **行为不变是底线**：一切整改以测试套件为安全网；契约变更走显式签字（B221 守卫）。
4. **两个拍板点**在 §15.1 标注（B238 K2 语义 / B247 K6 深度），到批前找用户定。
5. **环境三件套**（起服/停服/端口）见 §8；⚠️ 跑完 build 必须重启后端（陈旧类全站 500）。
6. **历史考古**：v0.1.0 全文在 `docs/handoff/archive/`；批次详情 `git log --grep="(B###)"`。

## 0. 总目标

**v0.2.0：按整改方案完成全面整改，达到三问验收。**

- 三问（总验收）：**用例可读**（顺主线能读完） / **状态可控**（任一状态字段写入者唯一） / **政策可复用**（同一规则全仓一份实现 + 契约测试）。
- 手段分层：先正确性（阶段 1~3，修真实缺陷）→ 再结构收敛（阶段 4~5）→ 最后整体包结构整改（阶段 6：改名清算 → contracts/platform → 三 runtime → domains 重排 → 门禁收紧）。
- 底线：行为不变；搬迁与行为修改拆成不同提交/批次；守卫不许用刷新 baseline 掩盖违例。
- v0.1.0 的目标（按 Java 标准提升可读性）已被本目标吸收，不再单列。

## 1. 仓库身份与分界（保留）

- 本仓 = 原 WeKnora Go 后端的 Java 翻译版的**后续演进线**；自 seed 起**不承担与 Go 仓的任何契约对齐义务**。
- 旧仓 `~/ragagent-java` 已冻结零修改，仅作考古参照（§10）。不要往旧仓推任何代码。
- **origin 已建立**：`https://github.com/pmbilly/ragagent.git`（公开）；**v0.2.0 分支** 2026-10-13 自 main（`53266b03`）新建，R 系列提交落在该分支（本地 ahead；推送需显式执行）。

## 2. 已定决策（勿再重新讨论）——v0.2.0 修订版

> 本节 = 仍然承重的决策；v0.1.0 中被后续执行取代的条目已改写（全文见归档）。

1. **旧仓零修改**；本仓即唯一工作仓。
2. **产品未上线，无数据连续性负担**：schema 基线化管理（`V1__baseline.sql`），不写存量兼容迁移；前端随后端同批调整。
3. **契约标准（现行 = B183 统一响应外壳）**：`{code,message,data}` 一套外壳 + 前端唯一解包点（`utils/request.ts`），约定见 `docs/api-response-convention.md`；错误体摊平到顶层；**取代** v0.1.0 早期"删除 204 / 错误体三段式"版本（已退役）。例外仅 `ImCallbackController`（外部平台协议）与 `HealthController`（探针），登记在守卫里。
4. **字段命名 = camelCase，JSON 名 = Java 字段名**（禁逐字段 `@JsonProperty`，`A14` 硬门守）；落库 jsonb 同走 Java 字段名；宽松读统一 `JsonMappers.lenient()`（`A15` 硬门守）。
5. **功能裁剪五件套（2026-09-28 定稿）**：浏览器连接 / 沙箱 / CLI / Chrome 插件 / Claw Skill 已移除；技能降级为指令型（playbook），执行型扩展走 MCP。
6. **自研基础设施保留**：EventBus / StreamManager（Redis Stream）/ chatpipeline 管线 / ToolRegistry / 各 Bridge——是架构不是技术债，整改不替换。
7. **Go 兼容层已全部退役**：逐字段 Go 注解 0、`Go*` 序列化器 0（provider 面由 `ProviderJson`、事件/流面由 `EventJson`/`StreamJson` 承接）、Go 锚点注释 0（棘轮守）。工具面 schema 键已按 Java 标准 camel。
8. **包结构约定**：`mapper/` 只放 MyBatis-Plus 接口、`repository/` 放仓储门面（`A11b` 守）；`service/` 不得依赖边界层与具体技术（`A16/A17` 守，基线只减）；**v0.2.0 目标结构**见整改方案 §6（域内十一层）与 §5（七模块），执行见 §15.1 阶段 6。
9. **命名政策**：类型名全词（禁 `Kb`/`Ops` 缩写；实体可跟随表名）；请求侧布尔不带 `is` 前缀；抽象接口后缀 `Gateway`。
10. **v0.2.0 整改的硬约束**（整改方案 §13/§14）：不重写、不拆微服务；不按行数机械拆类；不造空层；**不刷新 baseline 掩盖违例**；**不用 `@Lazy` 掩盖环**；搬迁 PR 不夹带行为变更。

## 3. 三条红线

1. **一次只动一个轴**：正确性批不动结构、搬迁批不改行为；每批结束必须全绿可运行。
2. **裁剪/大改期单工作流**：缝合点文件高度重叠时串行合入；其余按批并行。
3. **先失败测试再改实现**（v0.2.0 新增）：每条正确性修复必须先有能红的测试/反例；守卫类改动必配红态探针（§15.2 第 6 条）。

## 4. 关键测量数据（2026-10-13 实测）

- 后端测试：**4,908 用例 / 0 失败 / 跳过 4**（B234 收尾实测；全量闸门口径）。
- 结构规模：后端 **254 个生产包**；`:domains` **204 包 / 1,363 文件**（整改方案 §8.7 口径）；四 Gradle 模块 common/engine/domains/boot。
- 守卫面：**11 个 Python 守卫**（`scripts/run-guards.sh` 单一入口，已接入 `./gradlew build`）＋ **ArchUnit A1~A17** ＋ **R9 包级 SCC 棘轮**。
- 棘轮基线（只许减）：A16=**76** 类 · A17=**61** 类 · R9=**13** 组 · A11b=**11** 域 · 大文件登记例外 4 个 · 外壳 50/52。
- 覆盖率（B220 聚合口径）：行 **67.8%** / 分支 54.0%（`-Pcoverage`）；盲区 = session 49.3% / vectorstore 49.1%。
- 前端：`vue-tsc` 0 错误；`npm test` **753/0**（B231）。

## 5. 转型路线图（v0.2.0 六阶段）

| 阶段 | 内容 | 批次 |
|---|---|---|
| 0 边界固化 | 守卫修复 + R9 + A16/A17 两条硬规则 | B232~B233 ✅ |
| 1 最小正确性 | K1 / K2 / K3 / A6 | B234 ✅、B236~B238 |
| 2 会话终态 | A1 / A2 / A3 / A5 / A18 | B239~B244 |
| 3 任务可靠性 | K4 / K5 / K6 | B245~B247 |
| 4 索引与配置 | K7 / K8 / K10 | B248~B251 |
| 5 检索与结构收敛 | K9 / A4 / controller 剥离 / 中立 SearchHit | B252~B255 |
| 6 整体包结构整改 | 改名清算 → contracts/platform → 三 runtime → domains 重排 → 门禁收紧 | B256~B267 |

## 6. 缝合点（历史段，已执行完毕）

> 原"五功能移除 + 浏览器/CLI 陆地页 + browserskill"缝合点清单已全部落地、不再维护；复看用 `git log --grep="缝合" --grep="五功能"` 或归档 §6。

## 7. 当前状态（v0.2.0 起点，2026-10-13）

**v0.1.0 已收官的各轴**（细节见归档；均全绿交付）：
- 裁剪五功能（净删 ~8.3 万行）；schema 基线化（V1 单文件，60 表）；
- 神类拆分全仓收官（≥800 行只剩 4 个登记例外；`AgentEngine` 3,235→712、`FaqService` 3,086→七协作者等）；
- 契约换锚 + 统一响应外壳（50/52 控制器；jsonb 全 camel；Go 兼容序列化层全退役）；
- getenv 149→0；Go 锚点注释 0（棘轮）；批次代号注释 0；
- 多模块（common/engine/domains/boot）+ Dockerfile 进 CI（4 job）；
- 守卫体系：11 Python 守卫 + ArchUnit A1~A17 + R9（本版新增）；覆盖率聚合口径建立（行 67.8%）。

**v0.2.0 已开工**：B232（守卫修复 + R9）· B233（A16/A17）· B234（A6 去重）· B235（本文档换版）。

**已知边界与登记例外（摘录，全量见归档 §15）**：
① 外壳例外 2 个（ImCallback / Health）；② A11b 余 11 个域 mapper 包里有仓储门面（只许减）；③ 大文件登记例外 4 个；④ session / vectorstore 覆盖率盲区（QA 主链路）；⑤ `docs/site/*.html` 陈旧（需 Archify 重渲染）；⑥ IM 真机联调与 embed 已发布副本残留（第三方页面旧脚本）；⑦ `process_overrides` 保留现状（前端控件在、后端不做）；⑧ 存量空 embed 会话可运维软删。

**下一步**：§15.1 批次表，B236（K3 DocReader 流错误传播）起连续推进。

## 8. 环境与运行

- **后端端口改 8083**（避开旧仓 8082 本地走查环境）。
- **PG 独立库名**（建议 `ragagent`）：基线合并会改 schema，不能与旧仓共用 dev 库；docker-compose 里 ParadeDB/Redis 实例可共用，建新库即可。
  **⚠️ 排查数据问题前先确认库名**（2026-10-11 B223 实测踩坑）：同一实例（:15432）下有 `WeKnora`（旧仓）与 `ragagent`（本仓，见 `.env` 的 `DB_NAME`）两个库；
  用 `psql -d WeKnora` 查本仓的表会得到**空结果**，看起来像"数据根本没写进去"，从而把排查引向错误方向（实测为此误判了一轮）。
  **正确姿势**：`grep DB_NAME .env` 或看后端启动日志里的 `jdbc:postgresql://.../<库名>`（后者最可靠）。
- 前端开发代理：`VITE_DEV_PROXY_TARGET=http://localhost:8083`；**`vite.config.ts` 的默认值已改为 8083**（B222：此前继承 Go 仓的 8080，而本仓后端在 8083 ⇒ 忘设环境变量时所有 `/api` 请求打到**空端口**，浏览器侧只有 XHR 失败、无醒目提示，实测症状是"填了保存、重开为空"）。
- `.env` 已从旧仓原样复制（未入库，gitignore 正常），**已改**（`SERVER_PORT=8083` 实测生效 ✓，B202 起后端就绪）；库名按本仓独立库 ✓，`SYSTEM_AES_KEY` 沿用 ✓。
- **`LOCAL_STORAGE_BASE_DIR` 必须放持久目录、严禁 /tmp**（旧环境实测踩坑 2026-09-28：放在 `/tmp/weknora-java-files`，macOS 定期清理 /tmp 导致已入库文档原始文件丢失——文档列表正常、检索可能正常，但 preview 全 500、重处理报 "failed to read file"，原始文件不可恢复只能重传）。建议 `~/ragagent-data/files` 之类仓库外持久路径。
- **CI（2026-10-09 现状，B157/B158）**：三 job —— **guards**（**11 条** Python 守卫（B221 起），`bash scripts/run-guards.sh` 为准 ✓；B214 起该脚本也接入 Gradle `check`）· **backend**（spotless（ratchet 自 `seed` tag）+ 全量测试 + **把 `V1__baseline.sql` 灌进全新 ParadeDB**；两个连真 PG 的录测试在 CI 真跑）· **frontend**（type-check + test + build）。ArchUnit 规则随测试套件跑（B10），包级规则仍归 guards；ArchUnit 边界规则在 v0.2.0 阶段 0 已落 A16/A17（见 §12.3）。
- **远程仓库（2026-09-30 起）**：`origin` = `https://github.com/pmbilly/ragagent.git`（**公开**）；首次推送只推了 `main`（`bbf7443`），**`seed` tag 已于 2026-10-09 推送**（B157——CI 的 spotless ratchet 需要它可达）；本地 wip 分支按需推；此后本地提交若要同步，记得 `git push`。**2026-10-13 起：`v0.2.0` 分支为整改工作分支**（自 main `53266b03` 新建），R 系列提交落在该分支（本地 ahead，未推送）。

- **⚠️ 跑完 `./gradlew build` / `test` 必须重启后端** ✗：构建会**就地重写** bootRun 正在用的 `build/classes` ⇒ 在线服务**全站 500 + NoClassDefFoundError**（看起来像业务炸了 ✗ 其实是陈旧类 ✓）。**重启即解，别去查代码** ✓（README「坑」一节同款记录 ✓）。顺带：**停服用 `bash scripts/java-server-down.sh`**（B216 补：此前文档引用的 `scripts/*-down.sh` **并不存在** ⇒ 照文档停服静默失败、端口一直被占，进而触发"up 打到旧进程误报 ready"的连环坑 ✗）⇒ 停完记得重启 ✓。**up 脚本现已自挡端口占用**（B216）✓。

## 9. 测试与安全网

- 435 个测试类 / 1,349 契约金片（**4,908 用例 / 0 失败 / 跳过 4**，2026-10-13 B234 实测 ✓；早前记录 4,871 为 2026-10-10 ✓）是重构回归网，**每一步（哪怕纯移动）结束都必须全绿**——近两轮的工作方式就是"改一步 → 全量验证 → 提交"。这是"种子 fork + 渐进转型"优于重写的全部意义。
- **三条验证命令（接手先跑一遍确认基线）**：
  ```bash
  # 后端全量（约 1.5 分钟；期望 BUILD SUCCESSFUL，4,908 用例 / 0 失败 / 跳过 4；同 CI 口径）
  cd ~/ragagent && env -u SYSTEM_AES_KEY JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew spotlessCheck build
  # 前端类型检查（期望 0 错误）
  cd ~/ragagent/frontend && npx vue-tsc --build --force
  # 前端单测（期望 pass 690 / fail 0）
  cd ~/ragagent/frontend && npm test
  ```
- 已知偶发：`WebToolsRecordingTest.searchWithContentFetchesLeadingPagesViaSharedFetchTool` 在全量并发下**偶发失败**（单独重跑通过，与代码改动无关）；遇到它单独重跑确认即可，别误判为回归。
- 已知偶发（第二个）：`EvaluationContractTest.getTerminalRunsExecution` 在**全量并发**下偶发失败
  （期望 `model ID cannot be empty`、实际空串；单独 `--tests "*EvaluationContractTest"` 重跑通过）。
  与代码改动无关，遇到时先单独重跑确认（2026-09-30 首次观察到）。
- 已知偶发（第三个，2026-10-09 B164 观察到）：`EmbedRateLimiterTest.redisPathSharesBudgetAcrossInstances`
  在**全量并发**下偶发失败（Redis 面跨实例共享预算，时序敏感）；单独 `--tests "*EmbedRateLimiterTest"` 重跑通过。
  与代码改动无关，遇到先单独重跑确认。
- 裁剪功能的测试/fixture 随 PR 删除；阶段 1 末对比器改 **JSON 语义对比**后，fixture 锚定的是**本仓自己的行为**，与 Go 再无关系（键序/转义差异不算失败）。
- **覆盖率（B220，聚合口径）**：`./gradlew testCodeCoverageReport -Pcoverage` ⇒ 行 **67.8%** / 分支 54.0% / 类 91.4%。**分域**：audit 96.6 · config 87.8 · llm 82.8 · common 83.7 · datasource 84.5 · agent 71.6 · knowledge 69.8 · **session 49.3** · **vectorstore 49.1**（其余 65~80%）。盲区细节见 §7.3-6。
- A/B 对拍脚本与 `artifacts/` 产物未带入本仓（留在旧仓）。

## 10. 考古指引（需要时去旧仓查）

- 某行为为什么是这样：旧仓 `docs/HANDOFF.md`（翻译约定正文）、`docs/known-issues/`（坑史，尤其 04 沙箱/技能卷、05 事件契约/工具/引擎卷）。
- 某行代码来历：直接在本仓 `git blame`（seed 前历史完整保留）。
- 架构总览（裁剪前状态）：`docs/site/` 门户、`architecture.html` / `agent-workflow.html` 交互图、`api/` 452 路由清单——阶段 1 后按新形态重生成。

## 11. v0.1.0 里程碑总览（压缩为指针）

- 全文归档：**`docs/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md`**（含 §11.1~11.5 全部批次表与旧"边界清单"）。
- 取回 recipe：`git log --oneline | grep -E "\(B[0-9]+"`；提交信息一律带 `(B###)`。
- 旧引用"§11 边界清单"涉及的手搓/蛇形载荷口径，现行权威见 **§15.3 冻结面**与 `check-json-key-case.py` 冻结快照。

## 12. 结构地图与包约定（v0.2.0 新建；对齐整改方案 §5/§6）

### 12.1 Gradle 模块与依赖方向（实测 build.gradle.kts）

```
boot      → domains, engine, common   （唯一组合根；不执行业务 SQL）
domains   → engine, common            （业务域；34 顶层包）
engine    → common（api 暴露）         （检索 / LLM / chatpipeline 能力层）
common    → JDK（+ 工具链）            （平台与契约底座）
```

- `:engine` 的包根是 `com.ragagent.llm` / `retrieval` / `chatpipeline` 等（**无 `engine.` 中缀**）——模块边界靠 ArchUnit 与脚本规则强制，不靠包名前缀。
- v0.2.0 阶段 6：`common` 收敛为 `contracts` + `platform` 两个面、`engine` 收敛为 agent-engine / retrieval-engine / model-runtime 三个面（**先包边界 + 规则，后 Gradle 模块**，见整改方案 §5.2）。

### 12.2 域内分层约定（整改方案 §6 表）

`controller/`（绑定与帧写出）· `service/`（用例与政策）· `domain/`（状态与不变量）· `mapper/`（MyBatis 接口）· `repository/`（仓储端口与实现）· `dto/` · `client/` · `task/`（队列消费者与恢复）· `api/`（实现 contract）· `support/` · `config/`（只装配）。

禁止项（逐条有守卫，见 §12.3）：`service` import 边界层或具体技术（A16/A17）· `domain` 引 Spring/Mapper（阶段 6 规则）· `repository` 回引 service（阶段 6 规则）· `controller` 跑编排/解析/落盘（B254）· `mapper` 放仓储门面（A11b）。

### 12.3 守卫与规则总表（防回流面；新增规则前先查这里）

| 守卫 | 语义 | 基线 |
|---|---|---|
| `check-package-cycles.py` | R1/R1b 环 · R2 依赖 config · R3/R3b L2→L3 · R4 解耦对回流 · R5 L1 底座 · R6 common 实现痕迹 · R7 声明↔路径 · R8 内联 FQN · **R9 包级 SCC** | `scripts/package-cycles.baseline.json`（只许减） |
| `check-go-anchors.py` | Go 锚点注释棘轮 | 0 |
| `check-fe-contract-keys.py` | 前端契约键棘轮（前端 snake / 后端 camel） | 只许减 |
| `check-json-key-case.py --strict` | 换锚棘轮 + 冻结覆盖快照 | 冻结面登记 |
| `check-event-face-case.py` | 事件面/路由面命名口径 | — |
| `check-file-size.py` | 大文件棘轮（行数 + 单行 64KB + 文件 512KB） | 4 个登记例外 |
| `check-stray-dirs.py` | 游离目录 / 死包目录 | 0 |
| `check-dead-members.py` | 死成员（main + test） | 0 |
| `check-api-envelope.py` | 统一响应外壳（清单只许减） | 50/52 |
| `check-exception-semantics.py` | 检索引擎 catch 不得清空边界条件 | 0 |
| `check-contract-sync.py` | 契约金片键签名变化必须显式签字 | 1,349 金片 |
| ArchUnit `ArchitectureRulesTest` 等 | A1 裸 env · A2 属性扫描 · A3 双装配 · A4 install 面 · A5 裸 NUL · A6 @Lazy · A7 裸 JDBC · A8 字符串 wrapper · A9 `.last` 拼接 · A10 分层倒挂 · A11/A11b Mapper 包约定 · A13 生命周期钩子 · A14 JsonProperty 卫生 · A15 序列化工厂 · **A16 边界层引用禁令（76 类）** · **A17 service 技术依赖禁令（61 类）** | 各基线只许减 |

## 13. 落刀方法论（新 Agent 必读；全部是踩坑换来的）

> **编号说明**：文中与提交信息里可见的 `§13.9`~`§13.28` 是 §13 改写前的历史条目编号
### 13.1 切片的先后动作（固定套路）

1. **侦察**（只读，1~2 条命令）：成员清单与行区间 → **三类依赖扫描**（字段／方法／常量；字段要含 `@Autowired`）→
   调用点（簇内 vs 簇外）→ 外部引用与测试床 → 打印出来再动刀。
2. **判定切片边界**：**按调用点定，不按名字猜**（同名/近名工具常是双用 → 留门面并放宽包内可见）。
3. **写 harness 脚本**（模板：`scripts/refactor-harness.sh`）：脚本补丁 → 落刀 → **干跑断言** → 编译 → 测试 → spotless → 忠实性 → 文档 → 提交，
   **任一环失败自动回退**。命令 >8KB 会超平台限制 → 一律写成 `/tmp/runN.sh` 再执行。
4. **落刀后核验**：忠实性（搬走的成员体**逐字**比对，允许且仅允许登记过的替换反向归一）。
5. **回填文档**：`HANDOFF §11` 总览表加一行 + `§14.3` 计数刷新（都是**实测值**，不估算）。

### 13.2 依赖判据（决定"能不能搬"）

| 依赖形态 | 处理 |
|---|---|
| **共享值**（可参数化） | 传参：`AgentResolver`、`readerTenant`、`maxFileBytes()` |
| **共享静态**（方法/常量） | 留门面 → 放宽为包内可见 + 簇内按类名限定（`SessionQaResolution.xxx(`） |
| **共享行为**（实例方法，带字段态） | ① **随簇搬走**（首选）② **先落被依赖方**（把该成员先搬进协作者，再让依赖它的刀持有协作者）③ 调整刀序 |
| **`@Autowired` 字段** | 构造期未注入，普通协作者拿不到 → 改 **`ObjectProvider` 构造注入**（仓内既有样式，语义等价）或作参数传值 |
| **嵌套/公共类型** | 类型不能委托 → 留门面，放宽包内可见，簇内按 `外层类.类型` 引用 |
| **`record`** | 跨类搬运要把**字段访问**改成**访问器**（`r.x` → `r.x()`） |

### 13.3 对外契约的保法：**全量薄委托**

每个搬走的成员，在门面留一行委托（`return collaborator.member(args);`）→ 宿主与同族调用点**零改动**。
注意：static 成员委托要写 `QaXxx.member(...)`；类型/常量不能委托。

### 13.4 守卫必须"正向证据"（血泪）

- 编译：断言日志含 **`BUILD SUCCESSFUL`**；**不要**用"没出现 `error:`"当判据（shell 报错/`command not found` 会被漏掉，
  曾因此误推坏提交）。
- 测试：读 `server/build/test-results/test/TEST-*.xml` 的 **`failures + errors == 0` 且用例数达标**；
  `compileTestJava` 绿 ≠ 测试绿（曾把一条失败测试推上去）。
- harness 里 Gradle 一律**显式 `./gradlew`**（别用变量跨进程传，曾因 `$GW` 未导出导致命令静默失败）。

### 13.5 忠实性核验（机械搬运的"逐字比对"）

从门面旧版（备份）与协作者新文件各抽同名成员体 → 剥掉注释与全部前导修饰符（**循环剥**，只剥一层会出假差异）→ 空白归一 → 逐字比较。
**只允许登记过的替换**反向归一（限定名前缀、参数化、字段→访问器、重命名）。

### 13.6 脚本与正则的坑（都踩过）

- **点号 lookbehind 会骗人**：`(?<![\w.])name\(` 匹配不到 `field.name(` → 报"0 处"（假 0）。判"是否还有裸调用"要剥限定词或用 `(?<![\w])`。
- **`this.X(` 形态**要单独识别。
- **空白行折叠**会改行数（曾一次折叠掉 935 行）：干跑断言必须写明 `剩余 == 行数 − 删除集 + 新增`，差值必须为 0。
- heredoc 里写正则补丁极易双重转义（曾有 `unterminated subpattern`）→ 补丁用**免转义字符串替换**。
- 机械改写自伤两例：拼实参漏逗号、`replace("...) {", …)` 把参数名前缀留在签名里 → **长签名改完必看一次现场**。

### 13.7 搜索/检查纪律

- 依赖扫描**只用于预估**，**编译才是权威**；每刀准备"显式补充清单"（扫描三次漏掉 `stringListOf`/`waitForAttachments`/`templateContentByIdAndFile`）。
- 找不到 import 时，先怀疑"它是嵌套类型"（`grep "class X\b"` 直接搜声明）。
- 差 1~2 行的异常先别慌：**先解释异常再动刀**（本次两次"异常"分别是空白折叠与扫描顺序假象）。

### 13.8 切片产物的排版与源码卫生（2026-10-01 wiki page 面批次，3 条）

1. **切片产物"每行后跟一个空行"的排版 artifact**（wiki/session 两批共 14 文件、空白行占比
   55%~78%，仓库中位数 13%）：文件被撑大一倍以上，`wc -l` 榜单随之失真——`WikiPageFolderSupport`
   表面 822 行、实际 383 行；`SessionQaResolution` 707→184。**落刀写盘一律单空行**；发现后
   用 `scripts/normalize-blank-lines.py` 折叠（只删空行 + "非空行逐一相同"断言；口径：>100 行且
   空白率 >30%）。**推广**：任何 `wc -l`/行数榜单先看空白率，别把排版当规模。
2. **源码里的裸 NUL 字节**：字符串字面量内嵌真实 NUL（`"...\x00..."`）会让 **git 把文件当二进制**
   （`Bin 5600 -> 5443 bytes`，`git grep` 只回 "Binary file matches"）→ diff/blame 全不可读。
   写成转义 `"\0"`（值不变）即可恢复文本。**判据**：`file <f>` 报 `data` / diff 报 `Bin` 就是它。
3. **死方法的判别不能信 javadoc 自述**：`assertNoFolderConflict` 的 javadoc 写着"供 service 在
   创建前显式判定冲突时抛错"，但全仓 `git grep` 0 调用方（service 侧真正用的是 `folderNameExists`）
   ——属未接线遗留，删。**删前两种写法都扫**：`<name>(` 与 `.<name>(`（含 `Type.name(`）。
   另一条连带经验：拆仓储时若某方法与目标聚合共用 mapper（`listDistinctCategoryPaths` 走
   `WikiFolderMapper`），**按数据归属判**（它是文件夹路径查询）→ 随该聚合走，别为了让新仓储
   少背一个 mapper 而留下它。

### 13.9 闸门命令的环境卫生（2026-10-01 evaluation 打样批，1 条）

**source .env 的 shell 会把 `SYSTEM_AES_KEY` 等变量泄漏给同 shell 里跑的 Gradle 测试**：
CLI 起服务常写 `set -a && . ./.env && set +a && ./gradlew :domains:bootRun`；Agent 工具链会
**复用同一 shell**，后续在同一会话里跑的全量测试就带上了这些变量——`DataSourceConfig.toJSON()`
走 AES 加密分支，`DataSourceJsonTest.dataSourceConfigToJsonMatchesGoMarshal` 的"无 KEY 时凭据
原样落库"断言失败（**1/4669，其余全绿，极易误判为回归**）。
**判据**：全量里出现"孤零零 1 个与环境相关的失败"时，先 `env | grep SYSTEM_AES`；
**修法**：跑闸门用 `env -u SYSTEM_AES_KEY ./gradlew ...`（或换独立 shell 复跑该单类确认）。

### 13.10 校验文案的 locale / Accept-Language 漂移（2026-10-01 model 域换锚，1 条）

**`@Valid` 默认 message 的 400 文案随请求头/进程 locale 变化**：同一条缺失字段的请求，
不带 `Accept-Language` 时 details = `name: 不得为空白`（Hibernate Validator 中文资源包原样），
带 `Accept-Language: en` / `zh-CN` 时 details = `name: 不能为空`（英文资源包经全局
`translateMessage` 归一）——**同一个二进制、两种文案**。
**根因**：契约测试（MockMvc 不带该头）只能钉住其中一种，真实匿名客户端与浏览器看到的可能不同。
**修法（随批次）**：`@Valid` 注解显式写 `message = "字段名: 不能为空"`——`^[a-z][A-Za-z0-9_]*: `
前缀格式被全局 `handleBind` 原样采用，不依赖任何资源包（model 域 2026-10-01 已示范）。
**全仓现状**：knowledge/session 等域的旧注解仍用默认 message，属"错误形态统一批"存量，逐域顺手改。

### 13.11 前端测试运行时不解析 `@/` 别名（2026-10-01 session S2 前端批，1 条）

**给被 `node:test`（`tsx --test`）直接加载的模块加 `@/...` 的"值导入"会让整个测试文件在加载期崩掉**
（`ERR_MODULE_NOT_FOUND: Cannot find package '@/types'`）——报错停在测试文件那一层，看起来像用例失败，
其实是模块解析失败。`paths` 只在 `tsconfig.app.json` 里，而根 `tsconfig.json` 没有，tsx 以根为准。
**判据**：某测试文件"整档失败"（`# Subtest: src/xxx.test.ts` 直接 not ok、无具体断言）时先看这条。
**修法**：这类模块用相对导入（`../types/mention`）；`import type { X } from '@/...'` 是安全的（类型导入被擦除）。
App 侧（Vite / vue-tsc）不受影响。

### 13.12 契约夹具批量重录（换锚批的省力工具，2026-10-01 embed E1 起可用）

换锚一次会改几十个 golden（去信封 / 键改名 / 键恒输出）。手改慢且容易漏，从 embed E1 起提供开关：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :domains:test --tests "com.ragagent.embed.EmbedContractTest" -Dcontract.refresh=true
```

命中时把**掩码后的实际响应**写回 `src/test/resources/contracts/`（`server/build.gradle.kts` 负责把该
属性转发给 fork 出的测试 JVM——Gradle 的 `-D` 只作用于 daemon，不转发测试读不到）。
目前 `EmbedContractTest` 已接入；其它契约测试按需照搬 `REFRESH_FIXTURES` 那三行。

**纪律（重要）**：重录之后**必须结构化复核差异**（解析新旧 JSON、比键集与取值），确认差异只是
本次换锚该有的那几类；否则就成了"测试适应实现"，夹具失去契约价值。复核脚本思路见 §14.9m。

### 13.13 契约夹具的掩码按键名匹配——键改名必须同步放宽正则（2026-10-01，三次实录）

**夹具掩码用的正则形如 `"([a-z_]+)":"<uuid>"`（按键名锚定）**：换锚（下划线 → camelCase）之后，
`knowledgeBaseId`/`dataSourceId`/`createdAt` 这类键不再匹配 → **掩码静默失效**，
夹具里混进真实 UUID 与**逐次变化的真实时间戳**（下次跑就红，看起来像业务回归）。
**判据**：一批换锚后先看"重录的夹具里还有没有未掩的 id/时间戳"，再跑第二遍确认确定性。
**修法**：把键名字符集放宽成 `[A-Za-z_]+`（含大写），并在掩码定义处留注释。
**三次实录**：S3 `att-get` 的 sessionId（掩码未覆盖，夹具混进真实会话 id）→ M4 `McpContractTest`
（加 `serviceId`）→ D1 `DataSourceHttpContractTest`（`[a-z_]+` → `[A-Za-z_]+`，一次影响 12 个夹具）。

## 14. 逐包重构范式（knowledge 为范本，其余域照此推进）

> **用户定稿（2026-09-30）：以 knowledge 包的重构为范本，逐步重构其他包。**
> §12 是"拆完长什么样"，本节是"**怎么拆**"——把 knowledge 那 40+ 个提交里可复制的部分固化成 SOP。
> knowledge 的量化基线（目标形态，实测 2026-09-30）：197 文件 / 25,138 行 / 最大类 1,234 行 /
> ≥800 行 3 个（皆有 javadoc 注明的例外理由）/**Go 锚点 0 / rawBody 0 / 逐字段 `@JsonProperty` 0 / 未使用 import 0**。

### 14.1 三条内核（范本之所以有效的地方）

1. **沿注释边界拆，不按行数硬切**：神类里的 `// ── X 段 ──` 分割线就是拆解点（`KnowledgeService`
   3,392 行 → 门面 + 7 切片服务就是照这个来的）。拆完"门面保留全部公共委托"→ 18+ 注入点与
   Mockito 测试**零改动**，这是能把大手术做小的关键。
2. **每步全绿再走下一步**（2026-09-30 起分档，命令见 §14.4）：闸门按改动性质分档——迭代中只跑单类/单域；
   **常规批**（同包抽协作者、成员增删、卫生、文案）= `--rerun-tasks` 重编（~31s）+ 受影响域测试（~30s）
   + `spotlessCheck`（~10s）≈ 1m10s；**结构搬迁批**（跨包 `git mv`、改共享 API、删类）=
   `clean :domains:test :spotlessCheck`（~3m25s）。**判据：抓断链靠"强制重编"（便宜），防跨域行为回归才靠全量（贵）**
   ——别把两件事混成一件（§13.21 的两次假绿根因都是**编译错误**被增量编译掩盖，重编即可暴露；
   用例数只认"干净一遍"后的 `build/test-results/test/*.xml` 汇总）。
   这是"种子 fork + 渐进转型"优于重写的全部意义（§9）。
3. **一次只动一个轴**（§3 红线 1）：拆类期不改契约、换锚期不拆类、卫生期不动逻辑。
   轴混了就退化成大爆炸重写。

### 14.2 单域 SOP（七步，每步独立提交、独立全绿）

| 步 | 动作 | 关键点 / 产出 |
|---|---|---|
| 0 侦察 | 按 §14.4 命令出该域体检表 | 规模 / 神类 / Go 锚点 / `@JsonProperty` / rawBody / 未用 import |
| 1 边界 | 判"该域哪些**不能动**" + 列跨包缝合点 | 对照 §11 边界清单；缝合点用 `git grep` 实测，别凭印象 |
| 2 拆分 | 神类沿注释边界 → 门面 + 切片；容器类 → 一类型一文件 | 测试随被拆类**同包 `git mv`**；容器级常量/私有 helper 先安置（§13.4/13.5） |
| 3 分层 | `controller/service/support/task/client/storage/security/repository/mapper/domain/dto` | 每包一份 `package-info.java`（职责地图） |
| 4 契约 Java 化 | controller 入参 → DTO + `@Valid`；去 `@JsonNaming` / 逐字段 `@JsonProperty` / 信封 | **顺手消灭手写绑定器**（那是真实缺陷温床，§7 第 1 条）；同批带前端 |
| 5 数据访问去 Go | 落库 jsonb 去 snake（若该域有此面）| 改完必须 `grep` 全仓"按旧键读取"的代码（§11 ② 的 4 处真实缺陷） |
| 6 卫生 | 注释判据与 import 卫生（§13.8）/ 坏 `{@link}` / 批次代号清除 | `spotlessApply` 是标准手段，别自己写替换脚本 |
| 7 收尾 | 更新 §4 数据、§12 地图、§14.3 候选表 | 顺带把该域新踩的坑写进 §13 |

### 14.3 候选域盘点（2026-10-02 收官复测：真单类清零，≥800 只剩 **4 个登记例外**）

> ⚠️ **2026-10-08 B122 修正（复测失真）**：上句的「4 个」是 2026-10-02 的读数，
> 现已失真——实测 **≥800 有 5 个**，且**只有 3 个是已论证例外**：
> `MemoryIndexStore` 919 / `KnowledgeProcessWorker` 847 / `KnowledgeService` 827（三者类头均有自述理由 ✓）；
> 另两个是**欠债**：`ImService` **1,091**（2026-10-01 切片至 664「出榜」后 7 天回涨 **+427**，
> 无人察觉——§14.5 只在 ≥800 时登记、当时的守卫只管新文件）与
> `SessionKnowledgeQaService` **1,041**（HANDOFF 记「已登记例外」但类 javadoc 无自述理由，
> 违反 §14.5 自己的要求；本文件 §⭐ 待办里早已列为「例外复核」）。
> 更根本的问题：**政策在散文里，就会漂移**。B122 已把 §14.5 的三条要求做成 `check-file-size.py`
> 的可执行规则（R-c ≥800 必须登记 / R-d 例外必须自述 / R-e 自述数字必须相符），
> 并新增 R-b 拦截「出榜后回涨」。登记表见 `scripts/file-size.baseline.json` 的 `exempt`
> （`accepted` = 已论证；`pending` = 欠债，即后续批次的工作清单）。
>
> **进展（2026-10-08 B123）**：`ImService` 第一刀已落，1091 → **995**（`ImStopOps` 外提停止链路）；出榜（<800）还需 2~3 刀，候选见 B123 行。

| 域 | ≥800 的类（行数） |
|---|---|
| datasource | **已清零**（`DataSourceService` 1,828→666 §14.7.15；`FeishuClient` 1,155→709、`NotionConnector` 1,093→625 §14.7.16） |
| memory | **已清零**（`MemoryService` 1,662→965→**768** 于 m5 出榜 §14.7.17；`MemoryIndexStore` 929 为**登记例外**：六段同属「索引侧读写」一个关注点，用户 2026-10-01 定调不硬切；仓储 1,515→458、`MemoryExtractionService` 1,216→718 亦已出榜） |
| retrieval | **已清零**（9 引擎仓 + HybridSearchService 1,260→775 均出榜；§14.7.5/§14.7.6） |
| wiki | **已清零**（`WikiPageController`/`WikiIngestService` §14.7.2；page 面 4 类 §14.7.14：1,008→732 / 858→708 / 851→468 / 822→383，最大类 `WikiIngestCitePipeline` 760） |
| mcp | **已清零**（McpServiceController 937→372 + OAuthHandler 825→220，§14.7.10） |
| 其余单类 | **已清零**（`SourceRegistry` 878→534，工具参数编解码外提 `SourceToolCodec`；`UserService` 876→748，会话令牌操作外提 `UserSessionOps`——§14.9s） |
| chatpipeline | **已清零**（PluginMerge 1,155→670 C1 + PluginSearch 899→278 P1-P3，§14.7.13） |
| embed | **已清零**（EmbedChannelController 925→561，§14.7.12；EmbedChannelService 792 本就 <800） |
| im / llm | **均已清零**（llm：RemoteApiChat 1,366→513，§14.7.8；im：FeishuAdapter 926→331，§14.7.11） |
| session | `SessionKnowledgeQaService` 1,036（§14.5 已登记例外）；**本域已清零**（§11.3） |
| auth | **controller 已清零**（§14.7.7 AuthController + §14.7.9 TenantCatalogController 980→133）；service 域剩 `UserService` 876 + apikey 未动 |

**例外复核结论（2026-10-02，§14.5 判据＝接缝优先）**：四个例外全部**维持登记**——
`SessionKnowledgeQaService` 1,036（三条入口流单一状态机，javadoc 已备案）、`MemoryIndexStore` 932
（六段同属索引侧读写，用户定调不硬切；javadoc 本批补例外说明）、`KnowledgeService` 848 /
`KnowledgeProcessWorker` 816（knowledge 门面与摄取状态机，类注释已备案；后者本批补 javadoc）。

复测命令：`git ls-files 'domains/src/main/java/**/*.java' | xargs wc -l | sort -rn | head -20`

### 14.4 体检命令（复制即用）

```bash
cd ~/ragagent
# 神类/大文件排行（全仓）
git ls-files 'domains/src/main/java/**/*.java' | xargs wc -l | sort -rn | head -25
# Go 债务：锚点注释 / 逐字段 @JsonProperty / 手写 rawBody 绑定
git grep -cE '对照 Go|GORM|Go 的' -- 'domains/src/main/java/**/*.java' | sort -t: -k2 -nr | head -15
git grep -c '@JsonProperty(' -- 'domains/src/main/java/**/*.java' | sort -t: -k2 -nr | head -15
git grep -nE '@RequestBody\s+(String|Map<|JsonNode|Object)' -- 'domains/src/main/java/**/*.java'
# 卫生闸门（ratchet：只覆盖 seed 后触碰过的文件，这是设计不是遗漏）
./gradlew :domains:spotlessCheck
# 收尾闸门（按改动分档；实测：重编 ~31s / 单域 ~30s / spotless ~10s / 全量 ~2m50s / clean 全量 ~3m25s）
# ① 常规批（同包抽协作者、成员增删、卫生、文案）≈ 1m10s
./gradlew :domains:compileJava :domains:compileTestJava --rerun-tasks   # 专抓"引用被搬走"的断链
./gradlew :domains:test --tests "com.ragagent.<受影响域>.*"
./gradlew :domains:spotlessCheck
# ② 结构搬迁批（跨包 git mv / 改共享 API / 删类）≈ 3m25s
./gradlew :domains:clean :domains:test :domains:spotlessCheck
# ③ 迭代中：单类/单域（秒级）
./gradlew :domains:test --tests "com.ragagent.session.service.SomeTest"
# 前端契约同步（触及前端契约时）
(cd frontend && npx vue-tsc --build --force && npm test)
```

### 14.5 完成判据（Acceptance，逐项核对）
> **800 行是启发式判据（2026-10-01 用户定调）**：切完只略超、或再切不再落在自然接缝上时，
> **不硬切**——按本节登记为已知例外并说明理由；判据是接缝，不是行数。

- [ ] 该域最大类 < 800 行；例外必须在类 javadoc 写明理由（对齐 knowledge 的 3 个例外）
- [ ] Go 锚点注释 0 / 注释掉的代码 0 / 坏 `{@link}` 0 / 批次与阶段代号 0
- [ ] 请求侧无 `@JsonNaming`、无逐字段 `@JsonProperty`；无 `{data,success}` 信封；删除返 204；可空显式 `null`
- [ ] controller 入参全部 `@Valid` DTO（multipart 与"固定文案兜底"端点可保留手绑，但须在 javadoc 注明）
- [ ] 每个子包有 `package-info.java`；`*Util`/容器类等反模式命名清零
- [ ] **无全限定名注解**（除真同名冲突并在注释说明）；校验 `message` 保持「字段名: 原因」前缀格式
- [ ] **死成员清零**：未使用 logger / `ObjectMapper` / 私有方法 / 局部变量 / **只注入不读取的 final 依赖**（口径见 §13.15 ①④）；javac 不报未使用私有成员、Spotless 也只查 import，**必须主动扫**——B131 起 `scripts/check-dead-members.py` 覆盖其中可自动化的三类（重复 import / 未使用 import / 遗留 Logger·ObjectMapper 字段）；私有方法、局部变量、只注入不读取的依赖仍需人工（常量族的"声明未用"是词汇表性质，全仓 934 处，刻意不做硬门）
- [ ] 触点变更后按 §14.4 分档收口：**常规批** = `--rerun-tasks` 重编 + 受影响域测试 + `spotlessCheck`；
      **结构搬迁批** = `clean :domains:test :spotlessCheck`（+ 触及前端契约时 `vue-tsc` 0 错误 / `npm test` 全绿）
- [ ] §4 数据、§12 地图、§13 经验、本节候选表四处同步更新

### 14.6 不要做什么（踩过的坑，别再踩）

- **别把"Go 序列化层删除"拆到各域**：409 处引用 / 94 文件的那一刀按 §3 红线必须**一次性全仓完成**。
  按域先换锚（同 PR 带前端）是允许的，删序列化器本体不是。
- **别动冻结面，但先核对理由**：现行口径见 **§15.3**（2026-10-08 重排为「外部决定 / 协议行为面 / 落库·DDL」三类，并单列「已解除」）。
  以旧结论（"工具输出自有 schema""存量回放面"）为由拒绝改造前，先看 §15.3 的理由栏是否仍成立——
  「兼容历史数据」已整体作废；「协议/行为面」要连提示词与解析器同批且需拍板；「外部决定」才是真不能动。
- **别为数字写注释**：getter/POJO 访问器保持 0 javadoc（§13.8 第 3 条）。
- **别做全仓文本替换**：先用单文件验证再决定扩大（§13.2 的两次翻车）。
- **别跳过闸门**：只跑 `:domains:test` 会漏掉 Spotless（§13.9）。


### 14.9 契约工作（阶段 3 换锚）——何时做 / 做什么 / 怎么验收（2026-10-01 补写）

**为什么单独立节**：标准在 §2 第 4 条、进度散在 §2/§5、细则在 `docs/knowledge-api-contract-v1.md`，
而"什么时候做、一次做多少、怎么算完成"此前没有集中交代 —— 新 Agent 容易误判（本轮曾误写成"均未开始"）。

**已定、勿再讨论**
- 标准 = §2 第 4 条四段：camelCase 且 **JSON 字段名＝Java 字段名**（禁逐字段 `@JsonProperty` / `@JsonNaming`）；
  成功响应不包 `{data,success}`（分页 `{"items","page","pageSize","total"}`、删除返 **204**）；
  错误统一 `{"error":{"code","message","details"}}`；时间 ISO-8601 带时区；可空字段**显式 null**；不用 Problem Details。
- 落库格式（jsonb）同走 Java 字段名、**不加兼容别名**（§2 第 11 条）。
- 每个改契约的 PR **同批带前端**（§2 第 3 条）；产品未上线，无兼容期（§2 第 2 条）。
- 细则与历史差异表：`docs/knowledge-api-contract-v1.md`（v1.0，32KB）。

**进度（2026-10-02 收官）**
- 已完成：knowledge（含 34 个端点 DTO 化）/ retrieval / 会话链 / chunker-preview / evaluation（§14.9b）/ model（§14.9c/e）/ system（§14.9f/g）/ auth（§14.9h/i/j + 补刀 §14.9s）/ memory（§14.9k）/ session（§14.9l）/ embed（§14.9m）/ mcp（§14.9n/o/p + M6 §14.9s）/ datasource（§14.9q）/ wiki（§14.9r）/ **散存量七域 + 错误体统一（§14.9s）**。
- **无未完成项**。残留 `@JsonInclude` **213 处**（**2026-10-09 机械重算订正**：原写 ~288 处 ✗；NON_EMPTY 104 / NON_DEFAULT 57 / NON_NULL 46 / CUSTOM 3（三个队列载荷）/ ALWAYS 3 ✓，全部落在 §14.6 登记面内）与 Go 零值时间哨兵为登记尾巴（§14.6 / ⭐ 第 4 条），非在办。

**存量表（2026-10-01 盘点实测，§14.9 第 1 步交付物；只读扫描，三分法甄别）**

`@JsonProperty` 全仓 1,978 处 / 221 文件，**不是都是债**：

| 类别 | 量级 | 处置 |
|---|---|---|
| ① 外部 API 映射面（第三方 snake_case 合法映射） | 346 处 / 24 文件（feishu/yuque/ima/gitlab/notion 等 connector+client） | **保留**（映射外部 API 不是 Go 债） |
| ② §11 已登记边界面（SSE/Redis 事件载荷、provider 请求体、手搓载荷、agent config jsonb） | **event 155 + stream 9 的冗余注解已清（B151）· 外部协议面的冗余注解也已清 43 处（B152，2026-10-09：只删注解、键与字段序未动 ⇒ 线格式不变；两批共 278 处）**；agent(`AgentConfig`) 14 + tracing 7 + llm 大部（provider 面）保留 | **保留**（§14.6 边界清单；动它=改事件契约，须独立切片）；**全域改由守卫 A14（`JsonPropertyHygieneTest`）统一硬门守**——B152 起**没有面级放行表**：只剩四类必要形态（键≠字段名 / 键形如 isXxx / Jackson 探测不到 / 混用改名注解的类型=字段序锚，整文件跳过）|
| ③ 真·阶段 3 存量（HTTP 契约面 + 落库 jsonb 面） | **~897 处 / ~150 文件**，重域：auth 247 / datasource 127 / memory 123 / mcp 110 / system 86 / **wiki 39 → 0（W1 收官，§14.9r：17 键换锚 + 8 处死注解摘除；余 22 处 = `lf_*` 5 + LLM 解析面 17，登记冻结）**；evaluation 62 → **0**（打样，§14.9b）；model 87 → **0**（四块收官，§14.9c/§14.9e）；**session 188 → 0（S1+S2+S3+S4+S5 全部收官，§14.9l，含 5 处落库 jsonb 迁移 SQL）**；**embed 23 → 0（E1 收官，§14.9m）**；**mcp 119 → 58（M1，§14.9n）→ 35（M4，§14.9o）→ 22（M5 收官，§14.9p；余 22 处全是第三方协议面：RFC 8414/9728/6749 文档 + 授权服务器 token 响应，永久冻结）**；**datasource 493 → 417（D1）→ 366（D2）→ 355（D3 收官，§14.9q）：余 350 处为 connector 第三方线格式 + 5 处 `lf_*` 平铺载具，**均为永久冻结** ⇒ 该域可换锚面 0**。**2026-10-02 两段收官判定**：域级换锚（§14.9r）后散尾巴约 77 处由 §14.9s 七域批处理完——甄别结果：
真存量已换锚（audit 1+信封、favorite 5、im 17、storage 24、retrieval WebSearchResult 7、TempKbState 3、
APIPrincipalConfig 5、RuntimeStat 5 死注、WebSearchResult 死注）；**判冻结新增登记**：image_info（docreader
第三方）、SearchParams（chat span 载荷）、RankResult（第三方 rerank API）。复查批后全仓 `@JsonProperty` 余量 **913 处
/ 102 文件** 全部是登记冻结面（§14.6）⇒ **③ 类真存量 = 0，阶段 3 收官**。⚠️ **计数口径**：`QaRequests` 那批用的是全限定注解（`@com.fasterxml…JsonProperty`），只 grep `@JsonProperty` 会漏——盘点时两种写法都要扫 | 按域推进，一域一 PR 同批带前端 |

`@JsonInclude`（Go omitempty 直译）存量：**213 处**（**2026-10-09 机械重算订正**：原写 ~487 处 ✗ = 更早快照的过期计数；NON_EMPTY 104 / NON_DEFAULT 57 / NON_NULL 46 / **CUSTOM 3**（`DataSourceSyncPayload`/`MemoryExtractPayload`/`WikiIngestPayload` 三个队列载荷）· ALWAYS 3 ✓ ——ALWAYS 是正确形态的显式 null ✓）。**按面**：llm 59 · event 50 · tenantconfig(jsonb) 43 · common 19 · agent 10 · connector 8 · tracing(lf_*) 7 · approval 6 · 其余 15 ✓ ⇒ **全在 §15 B3 明文豁免的冻结面内** ✓；**B3 真面（wiki domain 全家 / websearch 三 DTO / VectorStoreTypes）已清零** ✓（机械扫描确认 ✓），我们自己的面仅剩 14 处且逐条有明文理由（如 `RssCursor` javadoc 的「为空省略：`null` 或空 map 时整个键消失」✓ = 该注解即线格式契约 ✓）。
`@JsonNaming` **0**、Problem Details **0**、Go 序列化器线上引用 **0**（2026-09-30 已一次性删除）。
Controller 全仓 52 个；每域 PR 入场时再做该域的"端点 × 前端调用点"细清单（§14.9 执行顺序第 3 步的入场检查）。

**执行顺序（关键约束）**
1. **先做清单盘点**（低风险、只读）：按域扫出「未换锚端点 + 对应前端调用点 + 涉及的 Go 序列化残留（注解 / jsr310 / NON_NULL / Problem Details 引用）」，
   产出一张存量表（形如 §14.3）。
2. **再一次性全仓删除序列化层**（§2 第 7 条已删的 156 处注解 + `JacksonConfig` 是第一批；剩余引用按清单扫净）——
   **不允许半删状态**（§5 阶段 3 红字：一部分端点走 Go 格式、一部分走标准 Jackson 最危险）。
3. 端点/落库面**按清单逐域推进**：一个域一个 PR、同批带前端、重录 fixture。

**验收（Acceptance）**
- 全量测试绿（当前口径 **4,871 用例**，2026-10-10 B218 ✓）；每一步"改一步 → 全量验证 → 提交"。
- 契约 fixture 重录后**语义对比**（键序/转义归一化）全过；200 路径 fixture 应零改动。
- 前端同 PR；`docs/knowledge-api-contract-v1.md` 同步更新版本与差异表。

**与阶段 2 的关系**：路线图上可对调（§5）；但换锚要动 HTTP 面与落库面，
**建议目标域先做完"神类切片"再换锚**，避免同一批文件反复改。

---

### 14.7+ 域级执行记录索引（正文已移出，节号保持原样）

| 节 | 状态 | 正文 |
|---|---|---|
| 14.7 wiki/im/retrieval/llm/auth/mcp/embed/chatpipeline/datasource/memory 神类拆分 | ✅ 执行完毕 | `docs/handoff/plans/14.7-域神类拆分执行记录.md` |
| 14.9b/14.9c/14.9e 小域打样与 model 收官 | ✅ | `docs/handoff/plans/14.9a-换锚总纲与小域打样.md` |
| 14.9f~14.9k system/auth/memory 域换锚 | ✅（14.9k 的 M3 亦已完成） | `docs/handoff/plans/14.9b-system-auth-memory.md` |
| 14.8 与 14.9l~14.9s session/embed/mcp/datasource/wiki 换锚 | ✅（14.9l/14.9q 标题原写"待执行"系过期，正文含 ✅ 交付记录） | `docs/handoff/plans/14.9c-session-embed-mcp-datasource-wiki.md` |

> 在这些计划文件里按**节号 Ctrl-F**（如 `14.7.2`、`14.9l`）即可定位原节。

## 15. 整改计划与纪律（v0.2.0）

> 本版 §15 重写：v0.1.0 批次表（205 条）已归档（见 §11）；现行 = **R 系列批次表（§15.1）**，执行记录见 §15.4。
> 方案与详细计划：`outputs/java-remediation-plan-2026-10-13.md` / `outputs/java-remediation-implementation-plan-2026-10-13.md`。

### 15.1 R 系列批次总表（B232~B267，共 36 批；状态 ✅ 完成 / ⬜ 待做；记录见 §15.4）

| 批 | 阶段 | 范围 | 状态 |
|---|---|---|---|
| B232 | 0 边界固化 | 守卫修复（`check-package-cycles.py` PERSIST_RE 缩进）+ R9 包级 SCC 棘轮（基线 13 组） | ✅ 2026-10-13 |
| B233 | 0 | ArchUnit A16 边界层引用禁令（76）+ A17 service 技术依赖禁令（61） | ✅ 2026-10-13 |
| B234 | 1 正确性 | A6 同 ID 去重保最高分（`KnowledgeSearchRanking`） | ✅ 2026-10-13 |
| B235 | — 文档 | HANDOFF 换版 v0.2.0（v0.1.0 全文归档）+ 实施计划展开（阶段 6 明细 + 4 处缺口） | ✅ 2026-10-13 |
| B236 | 1 | K3：`DocReaderClient` 流错误传播（onError 捕获 + await 超时 + 异步 UNIMPLEMENTED 走 unary） | ✅ 2026-10-13 |
| B237 | 1 | K1：worker→向量店显式传租户（`KnowledgeVectorWrites` 收 tenantId，消除 ThreadLocal+0L） | ✅ 2026-10-13 |
| B238 | 1 | K2：URL 摄取统一（**拍板点①**：同步下载 vs worker 下载） | ✅ 2026-10-13（拍板 A：同步下载） |
| B239 | 2 会话终态 | A1-NORMAL：流错误/超时轮终态一致收口 | ✅ 2026-10-13（建流前失败兜底→登记待办） |
| B240 | 2 | A1-AGENT：`AgentEngine` finally/COMPLETE 与外层 catch/ERROR 收口 + `FinalizePhase` 失败 outcome | ✅ 2026-10-13 |
| B241 | 2 | A2：全轮一次性提交门（CAS）+ 消息更新失败不启动索引/建议/记忆 | ✅ 2026-10-13 |
| B242 | 2 | A3：可关闭模型流（句柄 + `ThinkPhase` 取消 probe + 装饰器队列关闭语义） | ✅ 2026-10-13 |
| B243 | 2 | A5：跨线程上下文传播（NORMAL 消费线程 / STOP watcher replay） | ✅ 2026-10-13 |
| B244 | 2 | A18 状态所有权规则（phase 不回引 runner / 无公共可变运行态字段） | ✅ 2026-10-13 |
| B245 | 3 任务可靠 | K4：CAS 保护整轮数据（终态写入按尝试守卫 + 失败清场限本代；接管语义保留） | ✅ 2026-10-13 |
| B246 | 3 | K5：图任务失败/重试/计数同一契约（`finalAttempt` 消费、异常不吞、任务级去重） | ✅ 2026-10-13 |
| B247 | 3 | K6：主任务恢复口径统一（**拍板点②**：判据分裂已修＝按面判据；持久化主任务另立项） | ✅ 2026-10-13 |
| B248 | 4 索引/配置 | K10a：embedding 响应对齐校验（`index` 消费 + 数量/维度校验 + 乱序拒绝） | ✅ 2026-10-13 |
| B249 | 4 索引/配置 | K10b：索引执行收敛（worker 委托索引器唯一实现 + 数量断言） | ✅ 2026-10-13 |
| B250 | 4 索引/配置 | K7：事务外副作用（`KnowledgeFileService` `@Transactional` 内同步重建向量） | ✅ 2026-10-13 |
| B251 | 4 | K8：配置能力矩阵（parser rules / parent-child 显式拒绝或实现；不静默回落） | ✅ 2026-10-13 |
| B252 | 5 结构收敛 | K9：跨 store 融合（RRF 按 store 内 rank；context expansion 修正） | ✅ 2026-10-13 |
| B253 | 5 | A4：并发工具状态提交顺序（`seenChunks` 单一 writer / 按调用序回填） | ✅ 2026-10-13 |
| B254 | 5 | controller 剥离编排（`KnowledgeQaController` 14 构造参数、`QaRequestParser` 拆分） | ✅ 2026-10-13 |
| B255 | 5 | 检索中立 `SearchHit`（去上层对 `PgVectorRetrieveRepository` 内部类型依赖） | ✅ 2026-10-13 |
| B256 | 6 包结构 | §10 改名清算：10 组同名歧义 + 3 组域前缀（纯改名） | ✅ 2026-10-13 |
| B257 | 6 | §7 命名与边界：`agent/tools`→`agenttools`、`agent/management`→`agentmanagement` + 边界规则 | ✅ 2026-10-13 |
| B258 | 6 | contracts 包边界（模块 DAG + 跨域边界规则） | ✅ 2026-10-13 |
| B259 | 6 | platform 包边界（`:common` 其余收拢） | ✅ 2026-10-13 |
| B260 | 6 | agent-engine 包边界（`com.ragagent.agent` 运行面收拢） | ✅ 2026-10-13 |
| B261 | 6 | retrieval-engine 包边界（`retrieval.*` 收拢 + policy 归位） | ✅ 2026-10-13 |
| B262 | 6 | model-runtime 包边界（模型调用 / provider / 流生命周期） | ✅ 2026-10-13 |
| B263 | 6 | domains 域内重排①：knowledge（29 包 248 文件；SCC 拆解 + mapper/repository 主战场） | ✅ ①~㊸（mapper 归零 / `{service,task}` SCC 消除 / 门面 + 4 用例类归位 `service.management` / 五个子面切出；§8.8.2 包重排面到位；余三项属重构级，见收口记录） |
| B264 | 6 | ②：session + agenttools/agentmanagement 落位 | ✅ ①~⑦（agentmanagement `service.{config,builtin}` / **B257 基线清零** / agenttools 四面对齐 `datasource` / session `controller.sse`+`domain` 三簇+`service.suggestion`；session 余项属重构级，见收口记录） |
| B265 | 6 | ③：wiki（17 包 147）+ datasource（16 包 124）+ mcp（9 包 125） | ✅ ⑧~⑫（mcp 对齐 `tool`+改名 / wiki `domain` 五簇+`ingest.graph` / datasource `domain.connector`+`task`；余项属重构级与命名级，见收口记录） |
| B266 | 6 | ④：im/memory/auth/storage/websearch/model + 其余小域 | ✅ ①~⑤（memory `domain` 三簇 / im `channel.*` + 改名 / auth 两处 filter 归位 / model+im 命名对齐 / tenant `repository` + embedchannel `controller.filter`；余项属重构/模块级，见收口记录） |
| B267 | 6 | 门禁收紧收尾：§12.2「分层方向」「装配契约」两规则 + A11b 清零核查 + 复杂度指标（可选） | ✅ A19/A20 两规则落地 + A11b 注解判据收紧（基线 11→9）+ 复杂度报告脚本 |

#### 15.1.1 结项对账（2026-10-14，v0.2.0 R 系列收官；**硬读数全为当日实测**）

**总量**：R 系列 **36 批（B232~B267）全 ✅**；阶段 6（12 批，B256~B267）收官；两处拍板点闭合（B238 拍板 A：同步下载 / B247：按面判据 + 持久化另立项）。

**验收硬读数**

| 面 | 读数 |
|---|---|
| 测试 | **4982 / 失败 0 / 跳过 4**（domains 2949 · engine 561 · common 141 · boot 1331） |
| 守卫 | **12**（`bash scripts/run-guards.sh` ⇒ 退出码 0；汇总结论已改**自计数**） |
| 包级 | 环 **0** · 包级 SCC **12 组**（基线 12）· 依赖 `config` 0 · L2→L3 0 |
| 代码级规则 | ArchUnit **28 条**测试（A1~A20 + B257/B260 命名规则） |
| 行数棘轮 | `file-size.limits` **65** 条（阈值 600） |
| JSON 键 | snake 基线 **262** 条（逐条复核）+ 冻结覆盖 **149** 文件 |
| 其它棘轮 | Go 锚点 **48** 条 · 前端契约键无新增 |
| 契约证据 | `*ContractTest*` **49** 个文件 · 金片键签名 **1349** 个与基线一致 |

**三问对账**

1. **用例可读（顺主线能读完）** ✅ —— 主线 `controller → service.{query,faq,chunk,management,enrichment} → repository/mapper` 成型（B263 ①~㊸）；**`service` 直连 mapper 归零**（㉓）；`{service,task}` SCC 消除（㉛/㉜）；大件拆分有**行数刹车信号**机制（㉖ 触发 → ㉗ 解除）。
   余量：复杂度候选 **270 处**方法分支 >15、**2 处**构造依赖 >8（报告级，未立门）。
2. **状态可控（任一状态字段写入者唯一）** ✅ —— **A18**（运行态禁公共可变实例字段：基线 **1**；agent 协作者不得回引 `AgentEngine`：基线 **8**）；**A4**（`install*` 只许装配层，白名单 **8** 个目标）；任务恢复口径按面判据（K4~K6：B245~B247）；**A20**（契约提供方唯一 **12/12**）。
   余量：A18 两条基线共 **9** 条。
3. **政策可复用（同一规则全仓一份实现 + 契约测试）** ✅ —— 三副本合一：`escapeLikeKeyword` → `common.mybatis.LikeEscapes`（B263 ㉚）、`ImFormat` 去重（B264）、`FeishuWecomCrypt` 归 `runtime`（B265）；跨域调用统一走 **12 个 `common/**Port` 契约**（A20 守唯一实现）；契约测试 **49** 个；金片 **1349** 键签名受 `check-contract-sync.py` 守。
   余量：`SELF_MADE_MAPPER_BASELINE` **7** 条（每处类注释写明不能收编的理由）。

**基线余量总表（棘轮只许减，逐条可核）**

| 规则 | 余量 | 起点 → 现 | 说明 |
|---|---|---|---|
| A16 边界层引用 | 61 | 76（B233）→ 61 | 已降 15 |
| A17 service 技术依赖 | 41 | 61（B233）→ 41 | 已降 20 |
| A11b 门面留 mapper 包 | 9 | 11 → 9（B267 收紧） | 剔 2 条命名巧合假阳性，余 9 逐域收敛 |
| A19 domain 技术依赖 | 10 | B267 新立 | 4 实体 import 他域 mapper + 6 个 `@Component` 守卫/接线 |
| A18a agent 回引 engine | 8 | — | 状态所有权 |
| SELF_MADE_MAPPER | 7 | — | 有理由的自建 mapper |
| A18b 运行态可变字段 / A8 字符串列名 | 1 / 1 | — | |
| file-size >600 行 | 65 | — | 主源码 |
| json snake 键 / Go 锚点 | 262 / 48 | — | 均已逐条复核 |
| A6 `@Lazy` · A9 `.last(`拼接 · B257 · B260 | **0** | — | 零容忍 |

**有意偏离台账（正文明说过的，不算遗留问题）**：knowledge `service.faq` 单列 · `SpanTracker` 留 `domain.span`（否则复活 `task ⇄ service` 环）· `KnowledgeAccessHelper` 留顶层（语义不等价 + 环）· `auth/api`、`audit/api` 为端口模式**自愿增面** · websearch `fetch/` 4 类在 **`:engine` 的 `webfetch`**（分层有意）· storage `provider/`(11)+`StoragePathGuard` 计划归 `platform.storage.*`（**模块级，未迁**）· approval 四簇拆分**被互引阻断**（`service ⇄ domain ⇄ root` + 包私有测试助手跨面共用）· skill 目标面现居 `agent/skills`（域级未迁）· model 两个 DTO 泛名保留 · A19 判据**有意不收** `org.apache.ibatis.type`（49 实体注解约定）。

**未闭环（开放项，不谎报为已完成）**

1. **主任务持久化（K6 大改）**：B247 登记"另立项"，**未启动**，待拍板；
2. **覆盖率基线（`-Pcoverage`）未执行** ⇒ 本项目**不声称任何覆盖率数字**（对齐计划 §12.4"不能声称通过"）；
3. **计划 §12.3 八类测试场景**（终态矩阵 / 幂等 / 并发 / 租户 / 摄取 / 任务 / 外部索引 / 检索）**未逐条核账** —— 候选下一批；
4. 复杂度三条线**只落了报告脚本**，增量门槛未立（计划 §12.5 本就定"候选审查线，不是立即生效的全库硬门"）。

> **发布状态（2026-10-14）**：**v0.2.0**（R 系列 36 批 + 结项对账 + 缺陷修复 `ef89e4ab`）已**合并 `main` 并推送远程**；**`v0.3.0` 分支**自 `main`（`ef89e4ab`）开出并推送 ⇒ 自本行起，新工作落在 `v0.3.0`。`v0.2.0` 分支保留（与 `v0.1.0` 同例）；如需与 `v0.1.0` 对齐打 tag，属独立一步。

**本批顺手修的漂移**：`scripts/run-guards.sh` 成功汇总行硬编码「十一守卫全绿」而实际跑 12 个（加守卫时头注释 / README / CI 都改了、漏了这行）⇒ 改为 **`ran` 自计数**，并写入 §15.2 第 12 条。

### 15.2 批次纪律（每批通用，违者必翻车——全是本轮实锤）

1. §14.2 七步 SOP：一次只动一个轴、独立提交独立全绿、双端闸门（后端全量+spotless；触前端契约则 vue-tsc+npm test）。
2. "同 PR 带前端"必须核到**视图层字段读取**——api 类型文件对齐 ≠ 消费端对齐（TS 断言会藏住一切）。
3. 键名替换前分清三类键：**线格式键**（换）/ **系统设置键与 i18n 键**（不换，DB/文案字符串）/ **客户端本地态字段**（保留并注释）。
4. 触 HTTP 面的批做真实服务冒烟；金片重录前先看该测试用字节级还是语义级对比器（B2 统一后此条自动消解）。
5. 每批执行记录写回本节（沿用 §14.9x 的 ✅ 格式）。
6. **规则型/守卫型测试必须发明可证伪的探针**：新增静态闸门（架构规则、源码扫描、契约断言）后，故意造一个违例看它是否**真的红**——B10 实测两条空转：`noClasses().should(自定义条件)` 会把条件取反（手写 violation 被反转成通过）、匹配键写成带参数的方法全名则永不命中。**没探针的规则等于没规则**。
7. **注册类扩展点必须实测行为**：Spring 扩展点（`EnvironmentPostProcessor` 等）写错注册文件是**静默**失效（不报错不执行）；B6 批 10 实测 Boot 3.3 的 `EnvironmentPostProcessor` 仍走 `META-INF/spring.factories`，写成 `.imports` 被忽略。别只信「文件已就位」。
8. **拿日志/行为当证据前先读被验对象的类注释**：B6 批 10 实测——我曾把「没有 distributed=true 日志」判成文案写死，实为该类注释明确写的设计（分布式模式刻意不复位）。
9. **动包/类落点的批次，包结构守卫是必跑闸门（与测试同级）**：`python3 scripts/check-package-cycles.py`——B6 批 10 只跑了测试、漏跑守卫，把 5 组环带进 main（B33 才修复）；B34 分包批每步都用它自检。**反向用法**：新类该放哪个包，先跑一次守卫看红灯，比事后解环便宜一个量级。
10. **跨进程 JSON 键名按「权威」分桶，不按风格统一**（B72 定谳）：REST DTO=Java 字段名 camel（默认、零注解）；**SSE 事件载荷=对外权威面**（agent-chat 是集成页文档化 API，playground 按 snake `response_type` 解析——别当纯内部面翻转）；MCP OAuth/OIDC 连接器键=RFC/厂商规范原文；工具名/枚举值/错误码=词表不是字段。判据口诀仍是 B19 的「这个键是不是我们定义的、跨进程 JSON 字段」，外加一句「这个面有没有已发布的外部消费者」。两道棘轮（`scripts/check-fe-contract-keys.py` 41 条 + `crossFaceKeyContract.test.ts`）是执行面；python 守卫四个结构性盲区（TS 只盖 api 面/后端 camel 只认带引号字面量/后端 snake 全仓点亮/api-system-initialization 曾整面豁免）见 B72 记录。
11. **闸门的退出码必须显式取用——任何 `| grep | head` 都不能替它作证**（B162 实锤 ✗）：把 `./gradlew … build | grep … | head` 接进 `&&` 链时，管道退出码来自最后一段（`head`）⇒ **红着的构建照样提交并推送了**。正解：`cmd > log 2>&1; rc=$?; grep … log; [ $rc -eq 0 ] || exit 1`。与第 6 条同族：**闸门/规则必须能自证**。
12. **"看得见的数字"要自计数**（B267 结项对账实测 ✗）：加第 12 个守卫时，头注释 / README / CI 三处都改了，唯独 `run-guards.sh` **成功汇总行**的硬编码「十一守卫全绿」没人读（绿行的文案不会引起注意）⇒ 数字漂了一个批次。凡"数量/清单"类文案，**能自计数就自计数**（`ran=$((ran+1))` ⇒ `echo "✓ ${ran} 守卫全绿"`），不能就纳入守卫扫描面。与第 6/11 条同族：**判据必须自证，不靠人手同步**。

**✅ B10（2026-10-02，代码级架构规则进 CI）**
- **前提核对**：B10 原描述「环 0 组基线 + 包依赖白名单固化」**已于 2026-09-30 在 CI**（`scripts/check-package-cycles.py` 挂 guards job，环/分层/域依赖 `config` 三项带基线棘轮）。本批的真实缺口是 **ArchUnit 级（代码级）规则**。
- 新增 `domains/src/test/java/com/ragagent/arch/ArchitectureRulesTest.java`（测试依赖 `com.tngtech.archunit:archunit:1.3.0`），四条**当前零违例**的规则：
  - **R1 禁裸 `System.getenv`**（守 B6 成果 149→0；含无参重载）；
  - **R2 `@ConfigurationProperties` 类必须被 `@ConfigurationPropertiesScan` 名单覆盖**（漏扫描 = **静默**取默认值，B6 期间反复踩）；
  - **R3 配置类不得同时 `@Component/@Service`**（双装配）；
  - **R4 `install*`（启动期写入查找面/快照）只许 `*.config` 装配层调用**（把批次 6~10 各类注释里的约束变成红条）。
- 分工写进类注释与 CI 注释：**包级**归脚本棘轮、**代码级**归 ArchUnit，不重复建设。
- 两条实现要点（都属「静态闸门盲区」同类）：① 导入面必须按输出目录过滤到 **main**（`importPackages` 会连测试类一起扫，而测试里读真实 env 是**合法**的——各 connector 桩要读宿主 env 拼 SSRF 白名单），并加「导入面 >500 类」自证断言防**规则空转**；② **`noClasses().should(自定义条件)` 会把条件取反**（手写 violation 被反转成通过），R4 因此一度「永远绿」。
- **验证（核心）**：造一个探针类（未扫描包 + `@Component`&`@ConfigurationProperties` 双注解 + 内部调 `System.getenv` 与 `AppEnvLookup.install`）同时触发四规则 → **四条确实全红**（首次只红三条，正是借此发现 R4 空转）→ 删探针复绿。闸门：`spotlessCheck` 绿 + 全量 **4697** 测试绿（+4）。命令：`./gradlew :domains:test --tests "com.ragagent.arch.ArchitectureRulesTest"`。
- 新增纪律已写入 §15.2 第 6~8 条（探针 / 扩展点注册实测 / 先读被验对象注释）。

### 15.3 非目标（冻结面，见 §14.6，勿列入修复）

**2026-10-08 重排（B92）**：冻结项按**理由**分三类——改前先核对理由是哪种、是否仍成立；
「因为要兼容历史数据」这一理由已**整体作废**（产品未上线、不保留历史数据），原先按它冻结的项移入文末「已解除」。

**① 外部决定（字段名由对方 API / 协议定）**
connector 第三方线格式（`datasource/connector/**`）、provider 请求体（`llm/**`：anthropic/openai/ollama）、
websearch metadata（`published_at`，`WebResultConverter` 写、ProviderJson 出站）、
image_info（docreader 容器：`original_url`/`ocr_text`/`start_pos`…）、rerank `RankResult`（`index`/`document`/`relevance_score`）、
MCP OAuth 载荷、langfuse（`lf_*`）、IM 平台 ACK、i18n 键与系统设置键（`tenant.default_storage_quota_gb` 等，文案/DB 字符串）。

**② 协议 / 行为面（模型输出契约与提示词方言；改 = 提示词正文 + 解析器 + 实录金片同批，需拍板）**
引用协议标记 `<kb>`/`<web>`/`<ref>` + `doc`/`chunk_id`/`kb_id`/`url`/`title`；
wiki 摄取契约标签 13 个（`new_information`/`candidate_slugs`/`previous_slugs`/`shared_source_contexts`/`deleted_documents`/
`document_summaries`/`existing_folders`/`page_metadata`/`remaining_source_documents`/`valid_wiki_links`/`available_wiki_pages`/
`existing_page_content`/`current_introduction`）；
chatpipeline/evaluation 模板标签 4 个（`asker_background`/`images_uploaded`/`no_image_attached`/`no_document_attached`）；
工具名 36 个与 schema enum 值（`list_servers`/`list_tools`…——值是各自语义，不是字段名）。

**③ 落库 / DDL 形态（与"历史数据"无关：改列名是 schema 迁移）**
MyBatis 列名与 SQL 参数、租户配置 jsonb 列、`agent_steps`/`messages` 等列名、检索引擎索引文档字段（ES/OpenSearch/Milvus/Qdrant DSL）。

**Go 零值时间哨兵**（`0001-01-01T00:00:00Z`）：属**形状**变更（前端既有依赖），与命名无关，另批。

**已解除（2026-10-08）——原先按"存量回放/历史数据"冻结，现按普通改造处理**

| 面 | 状态 |
|---|---|
| 工具输出/提示词标记（JSON 键、XML 属性/元素名、多词标签名） | ✅ 已完成（B88/B89/B90） |
| 引用载荷（`knowledge_references` / `data.references`） | ✅ 已 camel（B88），双读分支已清（B92）；**但 B135b1 复核发现"Redis 回放"的两个 Map 重建读者（`StreamResponseBuilder` / `AgentStreamBridge` 的 `searchResultFromMap`）仍按 snake 读 11 键**（唯独 `knowledgeBaseId` 是 camel ——冒烟枪）⇒ 该路径字段**静默为空**：已修为 camel + 结构化断言 + 红态证明 |
| SSE/Redis 事件载荷键（`session_id`/`tool_name`/`total_steps`…） | ◐ 部分完成——**B93b 已 camel 化 `event/` 与 `stream/`**（并从冻结名单摘除，另有 `check-event-face-case.py` 守）；**B134 完成 `chatpipeline/PipelineProgress`**（`candidateCount`/`docCount`/`webCount`/`searchSource`/`hasImages`）。**B135c 完成 mention/steer 载荷**（`mentionedItems`/`steerId`/`kbId`/`kbName`/`kbType`/`serviceId`/`skillName`——两处生产侧 `QaSupport` 与 `SteerController`、解析侧 `MentionedItem`、读取侧 `SteerIntake`/`SteerSinkBridge`；`mentioned_items` **作为表列名**保持不动 §15.3 ③）。**B135b1 修掉一处静默缺陷**（引用载荷的 Redis 回放路径，见「已解除」表引用行）；**B135b2 完成收尾**：SSE `agentQuery`/complete 事件的最后 3 个漏网键（`finalContent`/`userCreatedAt`/`assistantCreatedAt`）→ camel ⇒ **事件面自此无 snake 键**。前提复核结论：§14.9l 前提判定 2 的"已实测后端无这些键"**是错的**（后端就在写），而 B72 立项稿的"维持 snake（路线 C）"其实**已被 B93b 执行翻转**（含 `AgentStreamBridge` 38 键位 + 前端 43 文件）⇒ 3 键只是漏扫尾巴；立项稿与 FE 白名单的两处过期依据已同批销账。另：`check-event-face-case.py` 新增 ⑤——**手搓 map 形态的事件载荷键守卫**（B93b 自列的余项，覆盖 `event/payload` 之外的 8 个生产/消费文件，含 27 个工具名的值面豁免），红态证明：把 `userCreatedAt` 退回 snake ⇒ 该行即刻被抓 ✓。**B136 结案（负结果）**：wiki 摄取 36 键经逐站复核**属 span 观测面**（去向 `spans.endSpan(...)`、FE 零命中）⇒ **不改**，基线理由已订正（第 4 处误标：原写"内部 jsonb 状态（存量）"）；其中 `new_slugs`/`source_chunks` 两条是 **LLM 输出契约**（§15.3 ② 拍板项，仍在用）。余「agent_steps 同族」与落库面待续 |
| wiki 内容图片标记（`<image_caption>`/`<image_ocr>`/`<image_original>`） | ✅ 已完成（B93a：标记名 → `<imageCaption>`/`<imageOcr>`/`<imageOriginal>`；**chunk_type 值 `image_ocr`/`image_caption` 是枚举值，保留**） |
| 落库 jsonb 存量键（agent_steps payload、memory 抽取状态、租户配置内容） | ✅ **完成（2026-10-10 B205）**——**判定**：落库面键已全 camel ✓（B132/B133/B135a/B135d1 ✓）；残留 snake 字符串**全在登记冻结面**（provider 请求体 / DB 列名 / MCP 枚举值 / LLM 载荷 ✓）。**实测**：dev 库 `tenants` 六列 + `memory_subjects` 两列，仅 4 列共 6 行还有旧键 ✗ ⇒ 按口径「开发库可清，不写迁移脚本」**一次性就地修数** ✓（先备份 → 事务 → 幂等 ✓；修后旧键行数**全 0** ✓）。修数 SQL 是 dev 一次性操作，**不入库** ✗（要点在提交 `(B205)` ✓）。 |

### 15.4 R 系列执行记录

**✅ B232（2026-10-13，守卫修复 + R9 包级 SCC 棘轮）**
- **R6 持久层引用检查修复**：`check-package-cycles.py` 的 `PERSIST_RE` 检查原在内层循环**之外** ⇒ 每个 common 根目录只检查最后读到的 1 个文件（可静默漏检）。修复后全量重扫实测 **0 命中 ⇒ 读数不变**（纯漏检面修复，无基线 churn）。
- **R9 包级 SCC 棘轮上线**：按完整包（`com.ragagent.a.b`）聚合 import 边求 SCC，**13 组**入基线（只许减；**新增与已消除/变形都红** ⇒ 刷新须 `--write` 并在说明里写明原因）。R1b 只看顶层域，这 13 组此前完全不可见；方案 §2.2 的"13 个包级强连通分量"由此**精确复现**。
- **三个红态探针全验**：① 注入 `common/probe` 持久层引用 ⇒ 修前 R6 计数 **0**（漏检）、修后 `持久层 ['probe']`；② 注入子包环 `probe.a ⇄ probe.b` ⇒ R9 `14 组（基线 13）；✗ 新增` + rc=1（同时 R1/R1b 保持 0）；③ 基线塞假 SCC ⇒ `已消除/变形（请 --write 刷新基线）` + rc=1。探针全删后复绿。
- **Tarjan 抽取**：顶层域与完整包共用一份 `sccs_of()`；包图**两趟收边**（单趟版把 13 组看成 9 组，漏"目标包尚未读到"的边）。
- **闸门**：十一守卫全绿 ✓ · `spotlessCheck build` ✓ **1m31s** ✓（脚本批，Java 零改动）。

**✅ B233（2026-10-13，ArchUnit A16/A17 两条硬规则）**
- **A16「边界层不得被用例层引用」**：`..service..` 不得直接依赖 `controller/mapper/task/client/channel`（同包依赖除外）；**ArchUnit 语义实测基线 76 类**（import 口径 81 文件——ArchUnit 含签名/注解/泛型面，以实测为准）。
- **A17「service 不得依赖具体技术」**：禁 `com.baomidou` / `spring-data-redis` / `redis.clients` / `jakarta.servlet` / `tracing.langfuse` / `tracing.decorators`；**基线 61 类**。
- **基线生成口径**：空基线首跑 → 测试报告 XML 取实际集合 → 回填（不做手抄近似）；棘轮双断言（新增即红 + 过期也红）。
- **红态探针**：注入 `knowledge.service.ProbeBoundaryDeps` ⇒ 双红且 fresh 集合精确只含探针；删除后复绿。
- **闸门**：`spotlessCheck build` ✓ **1m25s** ✓ · **4903 / 失败 0 / 跳过 4** ✓（+2 = 本批新规则）。

**✅ B234（2026-10-13，A6 同 ID 去重保最高分）**
- **缺口**（方案 A6）：`KnowledgeSearchRanking.deduplicateResults` 第一遍按 id 键登记先到者后 `continue`，第二遍"留最高分"只能看到幸存项 ⇒ 反例（x: 0.1 先到、0.9 被丢）成为终值。
- **修复**：同 ID 保最高分**前移为第一遍**（`LinkedHashMap` 保首次出现位置、替换不改次序；同分保留先到者 = 确定 tie-break）；多键 + 内容签名去重保持"留先"语义不变。
- **测试**：新增 `KnowledgeSearchRankingDedupTest` 5 例（2 反例先红 + 3 回归守卫）；录制回放 `KnowledgeSearchRecordingTest` 15/0 未受影响。
- **闸门**：`spotlessCheck build` ✓ **1m50s** ✓ · **4908 / 失败 0 / 跳过 4** ✓（+5 = 新测试）。

**✅ B235（2026-10-13，文档换版：HANDOFF → v0.2.0 + 实施计划展开）**
- **换版口径**：v0.1.0 全文归档 `docs/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md`（150 KB 快照 + 归档头）；新 HANDOFF 以 v0.2.0 整改为主体；**§8/§9/§10/§13/§14/§15.2/§15.3 编号与内容保持不动**（外部 40+ 处按号引用不断：docs 手册 + Java 注释）；§11 压缩为指针；**新建 §12 结构地图**（补齐长期悬空的"§12"引用：模块图 + 域内十一层 + 守卫总表）；§0/§2/§4/§5/§7 重写为 v0.2.0 口径。
- **计划展开**：阶段 6 由 3 行伞状批展开为 **12 批**（B256 改名清算 → B257 §7 命名 → B258 contracts → B259 platform → B260~B262 三 runtime → B263~B266 domains 域内重排 → B267 门禁收紧）；**补录 4 处缺口**（方案 §7 命名 / §10 改名 / §12.2 两条规则 / §12.5 复杂度指标）；批次号顺延（文档批 = B235）⇒ R 系列共 **36 批（B232~B267）**。
- **实测补录**：方案 §10.1 十组改名**大多未做**（旧名 7 个仍在：`ToolPolicy`/`TokenUsage`/`ModelResponse`/`ProviderRegistry`/`FileService`/`WebSearchProvider`/`Event`+`EventBus`；仅 `AgentToolCall` 已存在）⇒ 入 B256。
- **验证**：文档批零代码改动（参照 B204 先例不跑全量闸门）；十一守卫 rc=0 作底线 ✓。

**✅ B236（2026-10-13，K3：DocReader 流错误传播）**
- **缺口**（方案 K3）：`DocReaderClient.readStream` 的 `onError` 只 `latch.countDown()` 丢弃 `Throwable` ⇒ ① 异步 UNIMPLEMENTED 不回退 unary（外层 catch 只覆盖同步抛）；② meta 之后的错误被当"成功"返回部分结果；③ deadline 到期伪装成 "first frame missing meta"；`latch.await` 返回值同样被忽略。
- **修复**：`AtomicReference<Throwable>` 留证 + `await` 结果判定（真超时显式报错）+ 失败原样传播（`StatusRuntimeException` 直抛 ⇒ UNIMPLEMENTED 走既有 unary 回退；DEADLINE/传输错误上报调用方）。
- **测试接缝**：包级构造器注入短超时（生产 = 30 分钟常量）——⚠️ 多构造器立即触发 Spring 装配歧义（`No default constructor found`）⇒ 按仓内惯例给公开构造器加 `@Autowired` 显式指定。**这一步是"先跑定向（绿）→ 再跑全量"才暴露的**（`:boot:test` 上下文全炸）：**`@Service` 类的构造器改动，定向测试不足以作证，必须全量**。
- **测试**：新增 `DocReaderClientTest` 5 例（真实 Netty 回环端口 + 假 DocReader 服务；正常流 / 异步 UNIMPLEMENTED 回退 / meta 后错误 / deadline 真实状态 / 空流拒绝）。**红态探针**：还原"丢错误"形态 ⇒ 精确 3 红（三个缺陷场景）+ 2 绿（回归面）⇒ 还原修复复绿。
- **闸门**：`spotlessCheck build` ✓ **1m46s** ✓ · **4913 / 失败 0 / 跳过 4** ✓（+5 = 新测试）。

**✅ B237（2026-10-13，K1：租户显式路由）**
- **缺口**（方案 K1）：`KnowledgeVectorWrites.boundEngine` 读 `TenantContext` 的 ThreadLocal 且 null→0L；处理队列的 executor 线程不携带上下文 ⇒ **非零租户 + 绑定 store 一律被判跨租户 FORBIDDEN**（`resolveBoundEngine` 的 ownership 校验），且 `preCleanKnowledge` 已先删 chunks ⇒ 失败后行状态与数据不一致。
- **修复**：`boundEngine(KnowledgeBase kb, Long tenantId)`——租户由调用方显式给出（`null` 仍按 0 处理，保持"无上下文调用"旧语义）；网关内移除 ThreadLocal 读取与 `TenantContext` import。
- **调用点 9 处（两族）**：异步族（worker ×2）传任务行的 `k.getTenantId()`；同步族（move ×2 / tag / clone / chunk-indexer / faq ×2）传当前租户（`TenantContext.currentTenantId()` 或方法内已有的显式 `tid`）——同步族与旧行为逐字一致，**唯一行为变更点 = worker 线程**。
- **测试**：新增 `KnowledgeVectorWritesTest` 3 例——真实虚拟线程（无上下文）+ 非零租户绑定 store 解析成功（K1 验收口径）/ 显式传错租户仍被拒（纵深防御不放松）/ 未绑定与 null KB 返 null 且不触碰注册表（回归）。**红态探针**：还原 ThreadLocal+0L 形态 ⇒ 精确 1 红（K1 场景）+ 2 绿 ⇒ 还原复绿。
- **闸门**：`spotlessCheck build` ✓ **1m57s** ✓ · **4916 / 失败 0 / 跳过 4** ✓（+3 = 新测试）。

**✅ B238（2026-10-13，K2：URL 摄取统一——用户拍板 A：同步下载）**
- **缺口**（方案 K2）：数据源侧 URL 条目（真实来源 = IMA 连接器的"无扩展名媒体"裸 URL，源码注释明写"交给 ingest 层去抓"）由 `MapperKnowledgeBridge.createFromUrl` 只写 `source=url` + `filePath=""` 就入队，而 worker 对非 manual 只读 filePath ⇒ **空路径必被存储层拒绝（死链）**。
- **拍板**：A = 同步下载、与文件行同形（2026-10-13 用户定）。理由：知识库侧 URL 导入本就是"同步下载"语义，A 让两侧统一到一份实现；行形态与普通文件一致（worker / K4 的 CAS 与清场无需特判）；失败落同步报告（可观测性好）。B（worker 下载）的唯一优势是同步更快，但 IMA 连接器本来就在同步期下载大多数媒体（口径一致）。
- **实现**：① 新建 **`common/web/UrlBytesFetcher`**（口径自 `KnowledgeService.fetchUrl` 原样搬入：30s 连接 / 2min 读 / NORMAL 重定向 / 非 2xx 与**超限**文案同文；SSRF 由调用方注入）；② `KnowledgeService.createFromUrl` 改用它（私有 `fetchUrl` 删除、URL 路径的尺寸复检并入组件 ⇒ 同一政策一份实现）；③ `MapperKnowledgeBridge` 注入 `SsrfGuard`，`createFromUrl` = **SSRF 校验 → 抓取 → 与 `createFromFile` 共用 `insertFileRow` 闭环**（hash / 落 Storage / 入队）——URL 与文件自此一行形态。
- **测试**：新增 `MapperKnowledgeBridgeUrlIngestTest` 3 例（真实 HTTP 回环 + mock 数据面）——URL 落行同形（filePath/hash/size/source/type/parseStatus 全断言）/ SSRF 受限主机抓取前被拒（不落行、不入队）/ `createFromFile` 回归。**红态探针**：还原"URL 行不落文件"形态 ⇒ 精确 1 红 + 2 绿 ⇒ 还原复绿。
- **守卫连带（棘轮真实生效）**：`KnowledgeService` 830 → **796 行**跌破 800 ⇒ 文件大小守卫 **R-e 触发**（"规模例外 ~830 行"自称过期）⇒ 删自称 + `check-file-size.py --write` 清理失效 exempt；`--write` 顺带刷新 `limits` 历史漂移 29 处（均在 30 行 slack 内，非新增违例）。
- **闸门**：`spotlessCheck build` ✓ **2m03s** ✓ · **4919 / 失败 0 / 跳过 4** ✓（+3 = 新测试）。

**✅ B239（2026-10-13，A1-NORMAL：流终态收口）**
- **缺口**（方案 A1）：NORMAL 轮的生命周期挂在「答案 done」上（`QaTurnExecutor` 的 FINAL_ANSWER 处理器只在 `done=true` 时完成消息并发 COMPLETE）；异常收束（生产者关闭 / 120s 空闲超时 / 线程中断 / `ERROR+done`）原先只 `flush + closeThinking` 就返回 ⇒ assistant 停在未完成、SSE（只认 STOP/COMPLETE）永远轮询。
- **修复**：`PluginChatCompletionStream` 新增统一收束出口 `endStream()` = flush 尾帧 → **补一帧终态答案（done=true，幂等；已终态不重复发）** → 关思考块；两条异常出口（队列结束/超时、ERROR+done）都改走它。错误事件仍**先于**终态帧（客户先见错误、轮再收口）。
- **测试**：新增 `PluginChatCompletionStreamTerminalTest` 3 例——① 无终态帧（用"poll 立即返回 null"的队列桩瞬时驱动，与 120s 超时同一代码路径）⇒ 终态帧恰好一帧；② `ERROR+done` ⇒ 错误事件先于终态帧且仍补终态；③ 正常 `ANSWER+done` 回归（只一帧、不追加空终态帧）。**红态探针**：注释终态帧调用 ⇒ 精确 2 红（两个 A1 场景）+ 1 绿 ⇒ 还原复绿。chatpipeline 全包金片回放（`PipelineLifecycleRecordingTest` 8/0 等）不受影响。
- **登记待办（同族，已实测确认）**：**建流前失败**的兜底收口未做——管线在 `CHAT_COMPLETION_STREAM` 之前抛错时，`QaTurnExecutor` 的 catch 只发 EVENT_ERROR、不发 COMPLETE ⇒ SSE 仍会轮询。安全性条件已核：`CHAT_COMPLETION_STREAM` 是 NORMAL 两条链的**末段**（纯聊天 / RAG 均如此）⇒ 该 catch 触发时流必未启动，兜底"立即完成 + 发 COMPLETE"不会截断在途答案。未实现原因 = `QaTurnExecutor` 全类**零测试基建**，按"先失败测试再改实现"纪律需先建挂点（并入 B253 controller 剥离的前置）。
- **闸门**：`spotlessCheck build` ✓ **2m08s** ✓ · **4922 / 失败 0 / 跳过 4** ✓（+3 = 新测试）。

**✅ B240（2026-10-13，A1-AGENT：终态顺序与失败 outcome）**
- **缺口**（方案 A1）：`executeLoop` 的 `finally` **无条件**先发终态帧（`agentComplete`），外层 catch 再发错误——而终态帧正是 SSE 的退出条件 ⇒ **错误帧落在流关闭之后**（客户端只看到"成功收尾"）；完成载荷也没有显式失败 outcome（`FinalizePhase`）⇒ 消费者无法区分"正常收尾"与"失败收尾"。
- **修复**：① 终态门（`AtomicBoolean`）上提到 `executeInner`——成功（`loopCompleted`）由 `executeLoop` 的 finally 收口、失败由 catch 在**错误事件之后**补终态，两条路径共用门 ⇒ 恰好一次；② catch 由 `AgentEngineException` 放宽到 `RuntimeException`（非引擎异常也有终态，不再只靠会话层兜底）；③ `FinalizePhase` 新增 `failureError` 重载：失败时载荷带 `extra={"outcome":"failed","error":<msg>}`（键序固定；成功不带 = 缺省即成功，不改既有线格式；语义已写进 `AgentCompleteData` 字段注释）。
- **测试**：新增 `AgentEngineFailureTerminalTest` 2 例——失败序列必须 `[error, agentComplete]` 且 `extra.outcome=failed`（**先红证据**：修复前实测 `["agentComplete","error"]`）/ 成功回归（恰好一帧终态、不带 outcome）。
- **金片签名变更（已评审）**：`GoRecording46B.R_EXECUTE_STREAM_ERROR_EVENTS` 由"终态→错误"改为"错误→终态 + extra"（方案 §14 风险表的既定处置：旧 golden 把缺陷当契约 ⇒ 经评审同步常量），常量上方写明原因；`EngineRecordingTest` 20/20 全绿。
- **闸门**：`spotlessCheck build` ✓ **1m45s** ✓ · **4924 / 失败 0 / 跳过 4** ✓（+2 = 新测试）。
- **登记观察（本批未改）**：`SessionAgentQaService` 的 catch 仍会**再发一份** `stage=agent_execution` 错误事件（引擎已发一份）⇒ 生产链路失败时会出现 **2 条错误事件**。属"重复上报"而非终态缺口，归入后续（B244 A18 状态所有权 / 阶段 5 controller 剥离）。

**✅ B241（2026-10-13，A2 全轮一次性提交门）**
- **缺口**（方案 A2）：收尾有多个并发入口（STOP watcher / HTTP 消费 / runner finally，共 5 处调用 `completeAssistantMessage`），而 `QaTurnFinalizer` 无一次性门 ⇒ 每处各排一遍下游（KB 索引会**新建 knowledge**、追问建议、记忆蒸馏）——重复索引与孤立产物；且消息更新失败只 warning，之后照样启动下游（"消息未完成、索引已建"）。
- **修复**：① 完成态 **CAS**：`MessageRepository.completeOnce`（`UPDATE … WHERE id/session/未删 AND is_completed = false`，0 行→1 行的赢家返回 true）经 `MessageService.completeMessageOnce`（含 owner 范围校验）暴露；`completeAssistantMessage` 改为 CAS 先行——**输家**只补一次幂等落库、**不启动**下游；**提交失败短路**（不落库、不启动下游）。② `update` 与 `completeOnce` 共用一份"where 条件 + 可变列清单"（`updateWhere` / `applyMutableColumns`），CAS 写入与 `update` **同形**——agent 轮的内存态（refs/steps/usage/时长）只有终态这一次落库机会，少写一列就是丢数据（重构中识别并规避的回归面）。完成位**不进**共用清单（否则拼出重复 SET 列，H2 报 `Duplicate column name`）。
- **测试**：`QaTurnFinalizerCommitGateTest` 2 例——双收尾下游只跑一次（**先红证据**：修复前索引实测 **2 次**）/ 提交失败短路（先红：修复前下游仍被调用）；`MessageRepositoryTest` 增 1 例真实 SQL——首次 `completeOnce` true、再次 false、输家不覆盖、富列与 `update` 同形落库。
- **语义**（已写进注释）：CAS 门只管**下游** exactly-once；行写入保持幂等 last-writer-wins（同一 `Message` 实例，后到的内存态可能更全——STOP 之后桥可能又补最终答案），故输家仍补一次落库。
- **闸门**：`spotlessCheck build` ✓ **1m43s** ✓ · **4927 / 失败 0 / 跳过 4** ✓（+3 = 新测试）。

**✅ B242（2026-10-13，A3 可关闭模型流）**
- **缺口**（方案 A3）：流接口只返回 `BlockingQueue`——"停止消费"≠"取消模型"：用户取消后上游仍读 SSE、并发 permit 继续被占（只能等 120s 放弃阈值靠猜释放）；`ThinkPhase` 的 poll 不读用户取消 probe；langfuse 用无界队列，消费者放弃无从感知。
- **A3 句柄**：新增 `ChatStream extends BlockingQueue<StreamResponse>`（`close()` / `isClosed()`），`LlmChatClient.chatStream` 改返句柄；契约写进接口注释：① 终止上游 ② 释放挂载资源（permit / 转发线程 / 观测收尾）③ **解阻塞**（补终态帧，消费者不再靠超时）④ 幂等。实现：`QueuedChatStream`（无界）+ `SyncChatStream`（无缓冲，保住放弃检测语义）+ `StreamLifecycle`（close-once；关流钩子异常不穿透）。
- **四家 provider**：`RemoteApiChat` / `AnthropicChat` 关闭即关 provider 连接（读取循环随之中止）；`OllamaChat` 关闭即中断生产线程（回调的 `isInterrupted()` 检查生效、阻塞 `put` 解除）；两个装饰器：`ConcurrencyChatClient`（关闭 ⇒ 中断转发 ⇒ permit 在 finally 归还 + 向上游传播，不再等 120s）、`LangfuseChatClient`（关闭 ⇒ 中断转发 ⇒ generation 在 finally 收尾 + 向上游传播）。
- **消费者纪律接线**：`ThinkPhase`（poll 前置用户取消 probe；取消 / 停顿 / 中断三条提前离开路径 close）、`PluginChatCompletionStream`（120s 无 chunk 的收束路径 close）、`SessionQaFallback`（提前收束 close）、`ModelDebugController`（排空窗口结束 close）。正常读到 done **不关**（生产者已自己收尾；关会中断 langfuse 尾窗的 usage 收集）。
- **测试**：`ConcurrencyChatClientTest.closeReleasesSlotImmediatelyAndAbortsUpstream`——记录型 limiter 直读 permit 归还时机（60s 放弃阈值 vs 15s 内归还，判别力明确）+ 关闭传播到上游 + `isClosed()`。**红态探针**：注释掉 `forwarder.interrupt()` ⇒ 该测试实测失败（归还落回 60s 超时兜底）⇒ 还原后绿。
- **适配面**：接口改返句柄波及 4 家 provider + 2 个装饰器 + 8 个测试桩（机械适配为 `ChatStream`/`QueuedChatStream`）。
- **闸门**：`spotlessCheck build` ✓ **1m55s** ✓（含 javadoc 任务：修掉 2 处类型变更导致的失效引用）· **4928 / 失败 0 / 跳过 4** ✓（+1 = 新测试）· 十一守卫 ✓。
- **残留（登记）**：① **建立连接阶段**的取消仍由 transport deadline 约束（建流调用同步返回前无法响应 probe，与方案 §A3 第 2 条同源）——留待 A3 后续或 B243 上下文批次一并评估；② `OllamaChat` 的上游中止依赖既有"线程中断"约定（第三方库若不响应中断，中止弱于 HTTP provider）。

**✅ B243（2026-10-13，A5 跨线程上下文传播）**
- **缺口**（方案 A5）：ThreadLocal 不跨线程，而三条路径没有显式传递——① NORMAL 流消费线程（`PluginChatCompletionStream` 起虚拟线程直接消费）；② STOP watcher 虚拟线程（`QaSseOrchestrator.startStopWatcher`）；③ `QaTurnFinalizer.runWithTenant` 只 capture → withTenantId → replay → **restore**：新线程 capture 到的是**空身份**，save/restore 等于把"没有身份"照搬回去 ⇒ owner 推导成 `""`、下游索引/建议/记忆 SessionNotFound（历史事故的根因形态）。
- **修复**：① `PluginChatCompletionStream` 与 `SessionQaFallback` 的消费线程：本线程 `capture()` → 消费线程 `replay()` + `finally clear()`；② `QaSseOrchestrator`：STOP 处理器在**注册时**（身份完整）捕获快照，触发时作为 fallback 传入；stop watcher 循环抽成 `watchStopEvents()`，线程内 replay/clear；③ `TenantContextSnapshot` 新增 `hasIdentity()` + `runWithTenant(tenantId, fallback, body)` 重载：**当前线程身份缺失时用 fallback 补齐，已有身份则以调用方为准**（授权面永远看调用方），"只换执行租户"语义不变。
- **测试（先红）**：`PluginChatCompletionStreamIdentityTest`——在**消费线程**首次读取分片时探针身份：修复前实测 `userId=null`（红），修复后 `u-777` + principal + 执行租户齐备；`QaTurnFinalizerTenantContextTest` 3 例（新线程 fallback 补齐 / 调用方身份优先 / 无 fallback 保持既有语义 + 收尾恢复）。
- **红态探针**：把补齐退化回旧语义（`identity = prev`）⇒ 新线程用例实测失败、另两例不受影响 ⇒ 还原后绿。
- **顺带核对（无改动）**：`SessionService.generateTitleAsync` 已显式 set(tenantId/requestId/userId)；`TemporaryDocumentService` 的清理 ticker 是系统级无身份设计 ✓ 均无本类缺口。
- **闸门**：`spotlessCheck build` ✓ **1m53s** ✓ · **4932 / 失败 0 / 跳过 4** ✓（+4 = 新测试）· 十一守卫 ✓。

**✅ B244（2026-10-13，A18 状态所有权规则 + 失败上报唯一所有者）**
- **规则 A18a「agent 协作者不得回引 `AgentEngine`」**：`com.ragagent.agent` 包内（引擎自身除外）声明引擎字段的类入基线 **8 类**（ActPhase / ContextDebugEmitter / ConversationAssembler / FinalizePhase / ObservePhase / ReActIteration / SteerIntake / ThinkPhase）。口径据方案 §9.1 / §12.2：协作者只接自己需要的窄端口——回引整个引擎会让运行态"谁都能写"、单测被迫整机装配；存量随阶段 6（domains 重排）逐域消化。
- **规则 A18b「运行态类不得有公共可变实例字段」**：作用域 = agent / chatpipeline / session.controller 的**顶层类**（内嵌 DTO/视图载体不治理——它们是数据形状而非运行态，实测该类共 39 处 public 字段全在内嵌载体）。实测存量 **1 类**：`agent.skills.Skill`（带 `loaded` / `frontmatterRepaired` 运行标记）已登记并注明收编方向（不可变 model + 独立状态容器）。
- **红态探针 ×2（真实注入）**：`ProbeEngineBackref`（agent 包内持引擎字段）⇒ A18a 精确点名且**不误报基线**；`ProbeMutableField`（public 可变字段）⇒ A18b 精确点名；移除后复绿。
- **失败上报唯一所有者**（清理 B240 登记的观察项）：`AgentEngine` 的 catch 把闸门内**非契约异常**统一收编为 `AgentEngineException`（新增 `(message, state, cause)` 重载；消息沿用 `BizException.wireText` 口径，保留 state 与 cause）；`SessionAgentQaService` 按类型分流——引擎异常只留带栈日志（**不再补发第二份 error**），其他 `RuntimeException`（引擎入口自身失败：建 span / 组装 prompt / 建工具集）仍兜底上报。原先两条路径都发，客户端会看到两次报错。
- **验证口径**：两条规则以首跑实测为准（空基线 → 报告回填，同 B233）；失败上报分流无直接单测（会话层装配重），由全量套件 + B240 的"恰好一份 error + 一份终态"引擎测试共同约束。
- **闸门**：`spotlessCheck build` ✓ **1m40s** ✓ · **4934 / 失败 0 / 跳过 4** ✓（+2 = 新规则）· 十一守卫 ✓。

**✅ B245（2026-10-13，K4 按尝试划界：终态写入 CAS + 失败清场限本代）**
- **缺口**（方案 K4）：pending→processing 有 CAS，但**整轮数据**没有——① `failOrComplete` 终态写入只按 ID 更新（cancel/delete/接管后的行会被旧尝试翻状态）；② 失败清场按 `knowledgeId` 删全部 chunks/向量（接管后连坐删掉新尝试的数据）。
- **修复**：① 两处终态写入加 CAS 守卫——完成要求行仍为 `processing` **或** `finalizing`（后者=本次 `promoteFinalizing` 原子晋升过）、失败要求行仍为 `processing`；被抢走的行不再被旧尝试覆盖（**旧 generation 不覆盖**），跳过时打日志。② 失败清场改为**按本次尝试的 chunk 清单**精确删（`attemptChunkIds`）；清单未知（落行阶段自身抛错、可能已部分落行）才退回知识粒度兜底（warn 标注）。
- **范围决定（重要，已撤销原计划的"排斥处理中"）**：**重解析的接管语义保留**——Go 合约金片 `kg-reparse-null-config` 期望连点两次第二次 **200**，且实测该行的 processing 状态由进程内 worker 时序决定（改判 400 会让金片随机红）。方案 K4 标题即"CAS 只保护接管、**不保护整轮数据**"⇒ 根治方向是"按尝试划界"而非拒绝接管；本批据此改口径（批次表行已同步）。
- **登记（后续）**：① **向量行仍是知识粒度**（现有 API 只有 `DeleteByKnowledgeIDList` / `deleteByKnowledgeId`）——接管场景下旧尝试仍可能删掉新尝试刚写的向量行；完整按代隔离需给 chunks 加代次列（`parse_attempt`）或补 chunk 粒度删除 API。② 批量重解析 / 重建索引仍会在处理中 reset（与单条同源，需上面的代次列才能统一）。③ `KnowledgeFileService` 的 `pending_subtasks_count` 归 B246（K5 计数契约）。
- **验证**：知识操作合约测试（含 6 条 reparse 金片）✓ · 全量 **4934 / 失败 0 / 跳过 4** ✓ · 十一守卫 ✓（含模块代码约定：内联全限定名；大文件棘轮：本文件 871 行 = 基线 847 + 24，仍在 +30 余量内——论证细节在本记录，代码里只留要点）。**本批无新增单测**（worker 无测试基建；改动为守卫与清场范围，已登记）。

**✅ B246（2026-10-13，K5 图任务失败/重试/计数同一契约）**
- **缺口**（方案 K5）：① `ChunkExtractService` 的 `finally` **每次尝试**都递减槽位（重试链把槽位扣穿 ⇒ 父知识在富化落库前提前完成）；② 两个 chunk-extract 队列**丢弃** `finalAttempt`（只调 1 参 `handleJson`）；③ `runExtract` 把 5 处失败（取块 / 取 KB / 取模型 / 抽取 / 写图）转成**返回字符串** ⇒ 队列当成成功 ack（既不重试也不按终态释放）。**不是"所有失败都重复扣槽"**：常见错误在此之前直接 ack 成功，只有 drain 后再抛异常/重复投递才会二次扣槽——方案此处的克制描述已核实。
- **修复（对齐仓库既有先例）**：问题生成路径早已实现正确契约（`QuestionGenerationService.handleJson(payload, terminal)` + "成功或终态才递减"）——本批把 chunk-extract **对齐到同一形态**：新增 `handleJson(String, boolean terminal)` / `handle(p, terminal)`（1 参重载默认 `terminal=true` 保兼容）+ `if (succeeded || terminal) drainSubtask(...)`；5 处失败改为**抛出**（`IllegalStateException`，消息原文不变）；两个队列分别传 `finalAttempt` / `attempt > MAX_RETRY`。
- **契约**：同一任务的重试链**只释放一次**槽位（终态那次）；失败可重试（退避）、预算耗尽才放弃并释放；成功即释放。
- **测试**：`ChunkExtractServiceTest` +3（未到终态不释放 / 终态释放一次 / 重试链合计一次）；`RedisKnowledgeTaskQueueTest` +1（`chunkTaskPassesFinalAttemptFlag`：terminal 序列 `[false,false,false,true]`，与既有 question 侧同款断言）+ 4 处夹具由 1 参改 2 参（旧夹具把"每次尝试都递减"当契约）。**红态探针**：终态门退化回 `if (true)` ⇒ 两条"未到终态不释放"用例实测失败 ⇒ 还原后绿。
- **登记（后续）**：真正的**重复投递去重**（同一任务被 Redis 重投两次仍会释放两次）需任务级幂等键（`(knowledgeId, attempt, chunkIndex)` 唯一行 / seen 集）——本批只保证"重试链 + 单次投递"的 exactly-once；`KnowledgeFinalizeAdapter` 的 `count>0` 仍只防负数（其调用时机已由上述契约保证）。
- **闸门**：`spotlessCheck build` ✓ **1m32s** ✓ · **4938 / 失败 0 / 跳过 4** ✓（+4 = 新测试）· 十一守卫 ✓。

**✅ B247（2026-10-13，K6 主任务恢复口径统一——判据按面分离）**
- **缺口**（方案 K6）：`StartupTaskRecovery` 用 `REDIS_ADDR` 非空统一代理"分布式"⇒ ① 只把 Redis 用于 IM/embed 等其它面（知识 Redis 面未装配）的部署被误判成分布式 ⇒ 卡住的知识/摘要行**永不复位**（前端永远转圈；`HousekeepingService` 只判 failed 不重投）；② 真实装配由 `knowledge.redis-enabled`（`KnowledgeRedisWiring`）与 `datasource.redis-enabled`（`DataSourceTaskQueueWiring`）分别控制——**两个开关 + 一个环境变量三方不一致**。
- **修复（判据按面分离：各跟随自己守护的任务基础设施）**：构造注入两个开关（`@Value`）；`@EventListener` 路径传 `(knowledgeRedisEnabled, datasourceRedisEnabled)`——知识/摘要行复位跟 `knowledge.redis-enabled`（与知识 Redis 面装配同一开关），同步日志复位跟 `datasource.redis-enabled`；删除 `AppEnvLookup.get("REDIS_ADDR")` 代理与类注释旧口径（改写为"三个开关各管各的任务基础设施"）。测试便捷重载 `resetPendingTasks(boolean)`（两面同值）保留，既有 5 处调用不变。
- **测试**：新增 `StartupTaskRecoverySplitTest` 2 例——① 知识面跳过时**同步日志面仍复位**（断言"一行 `knowledges` 查询都不发 + `sync_logs` 的 UPDATE 照发"）；② 知识面未装配时知识行照常被挑出复位。**红态探针**：让生产路径忽略知识面开关 ⇒ 用例①实测失败 ⇒ 还原后绿。
- **登记（拍板点②的"大改"，本期不做）**：**主任务持久化**始终缺位——唯一 `KnowledgeProcessingQueue` 实现是进程内 executor（重启丢在途主任务，`HousekeepingService` 只判 failed 不重投）。做成可重领/lease 属基建改造（新表/字段、续约、与 span/attempt 配合），建议独立立项（阶段 4/5 之后）；届时本批的按面判据就是"单机 vs 多实例"的统一入口。
- **闸门**：`spotlessCheck build` ✓ **1m26s** ✓ · **4940 / 失败 0 / 跳过 4** ✓（+2 = 新测试）· 十一守卫 ✓。

**✅ B248（2026-10-13，K10a：embedding 响应校验）**
- **缺口**（方案 K10a）：`EmbedderClient.embedBatch` 按响应**数组顺序**拼装 vectors——不消费每条声明的 `index`、不校验数量（vs 输入条数）、不校验维度（批内混维也照收）；而下游 `VectorStoreService.saveIndexRows` 是 `rows[i] ↔ vectors[i]` **按位置配行** ⇒ 顺序错位 / 少条 / 混维都会**静默写错向量**（比报错危险）。
- **修复**：按 `index` 归位（缺 `index` 时退回数组序 + 数量校验兜底）；数量必须等于输入条数；批内维度必须一致；`index` 越界 / 重复、空向量一并抛 `IllegalStateException`（文案含 size mismatch / dimension / out of range / duplicated，便于定位）。
- **测试缝**：`EmbedderClient` 的 `HttpClient` 由硬编码字段改为**可注入**（新增包内可见构造；生产装配走无参构造）——响应校验因此可在单测里直接覆盖。
- **测试（先红）**：新增 `EmbedderClientResponseValidationTest` 5 例（乱序归位 / 少条抛 / 混维抛 / 越界与重复抛 / 回归照常）。修复前实测 **4 红 1 绿**（乱序把 `[9.0,9.0]` 放首位、三处"该抛未抛"），修复后全绿。
- **登记（后续）**：① 与 store 的**声明维度交叉校验**——`EmbedConfig` 只有 `(baseUrl, apiKey, modelName)`，无声明维度，批内一致性是当前上限；② `saveIndexRows` 可补 `rows.size() == vectors.size()` 断言（并入 B249 K10b）。
- **闸门**：`spotlessCheck build` ✓ **1m41s** ✓ · **4945 / 失败 0 / 跳过 4** ✓（+5 = 新测试；**无既有测试因"变严格"失败**——既有桩本就自洽）· 十一守卫 ✓。

**🟡 B249（2026-10-13，K10b 索引执行收敛——数量断言已上；委托化待拍板）**
- **测绘结论（三条路径）**：① `ChunkVectorIndexer.indexAndStore` 的 pg 直连分支（`BatchEmbedProperties.embedSize()` 分批 → `embedBatch` → `saveIndexRows`）；② 同一索引器的引擎分支（`indexInfo()` 助手建 `IndexInfo` → `deleteByChunkIdList` → `engine.batchIndex`）；③ **worker 的 `embedChunks` 把 ①② 各重写了一遍**（内联 `IndexInfo` 字段赋值 + 自建 `texts/rows` + 自己分批）——即方案所称"重复分批与构造"。
- **本批已做（无争议部分）**：`VectorStoreService.saveIndexRows` 增加 `rows.size() == vectors.size()` **数量断言**（按位置配行的最后一道闸，B248 登记项）；新增 `VectorStoreServiceIndexRowArityTest` 2 例（数量不符 / null ⇒ 抛且一行 SQL 都不发）。
- **待拍板（委托化的语义目标）**：直接让 worker 委托索引器会**静默改变索引行为**——实测两条路径**语义不等价**：① 索引器先 `deleteByChunkIdList`（worker 靠前置 `preCleanKnowledge`，效果等价）；② 索引器 pg 分支**会一并索引生成问题行**（worker 不写）；③ 索引器**过滤 `parent_text` 块**（worker 全索引）；④ 索引器按块 `enabled` 判定（worker 硬编码 `true`）；⑤ 错误语义：索引器包成 `BizException(badRequest)`，worker 裸抛（B245/B246 的重试/终态契约依赖后者）；⑥ 批大小来源不同（`BatchEmbedProperties.embedSize()` vs worker 自有 `embedBatchSize()`）。⇒ 委托化属**行为变更批**（父块是否入库 / 问题行谁写 / 禁用块如何处理 / 失败文案），建议独立一批并先定目标语义（推荐：一律采用索引器语义、worker 变薄壳），到批前给逐条对照与回归面。
- **闸门**：`spotlessCheck build` ✓ **2m05s** ✓ · **4947 / 失败 0 / 跳过 4** ✓（+2）· 十一守卫 ✓。

**✅ B249 收口（2026-10-13，K10b 委托化——采用索引器语义，worker 变薄壳）**
- **实施**：① `ChunkVectorIndexer` 新增摄取入口 `indexChunks(kb, chunks)`（与 `updateChunkVector` **同一实现**，只是调用方已持有 KB 行）；② worker 的 `embedChunks` 删除双分支（~55 行：内联 `IndexInfo` 构造 + 自建 `texts/rows` + 自有分批）→ 变薄壳：span 埋点 + `embedConfig` 空跳过 + `BizException`→`IllegalStateException`（保住 B245/B246 依赖的"失败不吞"契约）。
- **六处语义差异的实测处置**：① 索引器多一次 `deleteByChunkId`（worker 前置 `preCleanKnowledge` 后为 no-op）✓；② 生成问题行——摄取期块无 `generatedQuestions` ⇒ **实际不写**（K8 落地后才有意义）；③ `parent_text` 过滤——当前 worker 只产 text 块（K8）⇒ **no-op**，K8 落地后自动获得"父块不向量化"语义；④ 按块 `enabled`——新块默认 true ⇒ **no-op**；⑤ 错误语义已包回原契约 ✓；⑥ 批大小统一到 `BatchEmbedProperties` ✓（本批正收益）。
- **验证**：全量 `spotlessCheck build` ✓ **1m47s** ✓ · **4947 / 失败 0 / 跳过 4** ✓——**含知识操作合约金片**（端到端摄取路径，kg-* 系列）全绿，即委托在当前配置下行为等价；十一守卫 ✓。数量断言部分另有 2 例新测试；委托化本身无新增单测（worker 无测试基建，靠合约金片覆盖）。
- **登记**：K8（配置能力矩阵）落地后，②③④ 会从 no-op 变为**生效语义**——到那时需补一条明确回归（"父块不入库 / 问题行由索引器写"）；阶段 6 仍建议把该政策移入 `retrieval-engine/execution` 的 `IndexService`。

**✅ B250（2026-10-13，K7 事务外副作用）**
- **缺口**（方案 K7）：`KnowledgeFileService.updateImageInfo`（`@Transactional`）在**事务内**调 `chunkVectorIndexer.updateChunkVector`——绑定引擎路径会**先删旧索引再 batchIndex** ⇒ 本地事务回滚时外部索引已删/写了一半（脏索引，且本地事务回滚不了它）。
- **修复**：① 索引器拆出 `assertIndexable(kbId)`（KB 可解析 + 模型非空且存在；复用既有错误映射 ⇒ 1007/404 文案与契约样例一致）与 `resolveEmbeddingModel(kb)` 单一实现（`updateChunkVector` / `indexChunks` 共用）；② `KnowledgeFileService`：**写库前**先 `assertIndexable`（失败在事务内抛且**一行都不写**），真正的 `updateChunkVector` 经新增 `afterCommit(Runnable)` 排到**提交后**执行（`TransactionSynchronization` 注册；无事务上下文内联执行 ⇒ 直调/单测行为不变）；③ 提交后失败**只记日志**——DB 已提交，不能把成功请求变成 500；幂等重试 / 索引意图 outbox 化登记为后续（方案 §9.1）。
- **测试**：新增 `KnowledgeFileServicePostCommitTest` 4 例（无事务内联 / 回滚零副作用 / 提交恰好一次 / 提交后失败不抛）；**知识合约金片（`kg-image-*` 端到端）全绿** ⇒ 校验前置 + 索引后移未改变对外契约。
- **顺带修复（诚实记入本批）**：本批闸门抓到 B241 测试的**竞态假红**——建议生成跑在虚拟线程上而断言未等待；改为 `timeout(5s).times(1)` 轮询断言。
- **闸门**：`spotlessCheck build` ✓ **1m33s** ✓ · **4951 / 失败 0 / 跳过 4** ✓（+4）· 十一守卫 ✓。

**✅ B251（2026-10-13，K8 配置能力矩阵）**
- **口径（用户拍板，2026-10-13）**：**"接受 + 显式标注未生效"**（选项 b）——不改既有保存契约；未接能力不硬拒，但每处都不许再静默。
- **能力矩阵（实测，勘察中修正过一处）**：`chunkingConfig` 11 键中，**已生效 6**：`chunkSize`/`chunkOverlap`/`separators`（`worker.toSplitterConfig:526-529`）+ `strategy`/`tokenLimit`/`languages`（`:530-532` → `Chunker:273/314-319`，此前误判为未接线）；**未生效 5**：`enableParentChild`/`parentChunkSize`/`childChunkSize`（worker 只出 `text` 块，`Chunker.splitParentChildWithDiagnostics` 仅预览侧用）、`parserEngineRules`（worker 固定 `docReader.read(..., null)`）、`tableMetadataInstructions`（全仓无消费点，仅初始化请求写入）。
- **修复**：① `KnowledgeBaseChunkingConfig.from()`——`null`/缺省对象仍回落默认（"没配"合法），**形状损坏改为 `BizException.badRequest` 明确报错**（原先静默回落 512/80 ⇒ 配置坏了摄取照跑、用户以为生效）；② 新增 `ingestionInactiveKeys()`（固定顺序的能力矩阵，注释写明"接进 worker 时请同步删项"）；③ `ChunkingConfigView` 增 `inactiveFeatures` 钉在响应里（**恒输出、空数组 = 全部生效**——知识库模块约定禁 `@JsonInclude`，`KnowledgeCodeConventionsTest` 挡住了我第一版的 NON_EMPTY 写法）；④ worker 分块 span 记 `inactive_config_keys`（结构化可观测，摄取侧同类不静默）。
- **现实影响面**：`InitializationConfigService:203-208` 确实在写 `enableParentChild`/父子尺寸 ⇒ 经 `agent.management` 初始化建的 KB 现在会在响应里看到 `inactiveFeatures`（这正是 K8 要暴露的"以为生效"）。
- **测试**：新增 `KnowledgeBaseChunkingConfigCapabilityTest` 5 例（**先红证据**：损坏配置不抛错 / 未生效键标注为空）+ 既有容错与契约测试全绿。
- **金片签名变更（已评审 + B221 签字）**：12 个 KB 响应金片 `chunkingConfig` 增 `inactiveFeatures`（键集合 66→67 等，**纯新增**）；前端 API 层该字段为 `any`（`frontend/src/api/knowledge-base/index.ts:83,135`）⇒ 非破坏，无需同步；`python3 scripts/check-contract-sync.py --write` 已更新基线（1349 金片）。
- **未做（登记）**：真实现**父子块摄取**与**解析器引擎选择**（chunker/doc-reader 管道改造，与 K10b 索引策略统一联动）；`parserEngineRules` 若要接，须先定"KB 规则 vs agent/租户 chat 规则"的优先级语义。
- **闸门**：`spotlessCheck build` ✓ **1m33s** ✓ · **4956 / 失败 0 / 跳过 4** ✓（+5）· 十一守卫 ✓。

**✅ B252（2026-10-13，K9 跨 store 融合）**
- **缺口一（排名退化）**：`HybridSearchService` 把同类型的多个 store 命中先 `addAll` 成一个大列表，RRF 只能按"拼接位置"算 rank ⇒ 第二个 store（另一个 KB / 跨租户组 / 另一个引擎）的**本地第一名被系统性降级**，且结果随 groups 顺序漂移（交换 KB/store 顺序结果就变）。
- **缺口二（扩展白读）**：`HybridResultOps` 收集父块 / 相邻块 / 关系块并逐条 `fetchChunk` 取回，但装配循环只遍历原命中 ⇒ 这些读取全是白读：请求里 `skipContextEnrichment=false`（= 明确要求扩展）也拿不到任何上下文块。
- **修复一**：融合改为**按店分组保序**——`HybridSearchService` 按 `RetrieveResult`（每 store 一个列表）分成 `vectorStores`/`keywordStores`，`HybridFusionOps` 的 `fuseWithRRF`/`deduplicateByScore` 改收 `List<List<IndexHit>>`，rank 在**店内**计算（同一 chunk 命中多店取最好名次）；并列名次新增**同分定序 = chunkId 升序**（否则并列项顺序由插入顺序决定 ⇒ 交换顺序即漂移）。单一来源路径保持"按分降序 + 同分保持来源顺序"（既有语义不变）。FAQ 后处理需要扁平向量命中 ⇒ 新增 `flattenStores` 展平（原转发缝合并到新签名）。
- **修复二**：扩展块**取回即输出**——按来源命中登记（`extrasBySource`），装配时**紧随其来源命中**；不可检索（`processing`/停用）或文档不可访问的扩展块丢弃；分数继承来源命中（父块原有语义推广到相邻/关系块）；**关系块可能跨文档** ⇒ 其文档也要过同一道可访问性闸门（补一次 `findAccessibleDocuments`）。`skipContextEnrichment=true` 语义不变（只有原命中）。
- **测试**：`HybridFusionOpsStoreRankTest` 4 例（第二组 #1 等权 / 交换 store 顺序稳定 / 同分按 chunkId / 单一来源顺序不变）+ `HybridResultOpsContextExpansionTest` 5 例（父块、相邻块、跨文档关系块输出 + 开关与不可检索过滤）。**先红证据**：扩展侧 3 条实测只输出 `["c-1"]`（白读现场）；排名侧用探针把 rank 退回拼接口径 ⇒ 2 条必红（已还原，源码无残留）。
- **对外行为变更（本批未改金片，因无金片覆盖该端点）**：搜索响应现在**会带上下文扩展块**（紧随命中、带 `matchType` 2/4/5）；这正是 `skipContextEnrichment=false` 的既有契约语义。如需"只出不扩展"的旧行为，调用方传 `skipContextEnrichment=true` 即可；若产品要求默认不扩展，改默认值即可（单点）。
- **闸门**：`spotlessCheck build` ✓ **1m49s** ✓ · **4965 / 失败 0 / 跳过 4** ✓（+9）· 十一守卫 ✓。
- **登记（未做）**：单一来源多店的**同分跨店**仍按来源顺序（同引擎同分时"店自身顺序"才是权威）；跨引擎刻度可比性（归一化只在混合引擎时发生）属检索质量议题，未在本批动。

**✅ B253（2026-10-13，A4 并发工具状态提交顺序）**
- **缺口一（白名单与状态自相矛盾）**：`knowledge_search` / `grep_chunks` 在并发读白名单里，但输出带 `already_seen`，依据是工具实例上的**会话级 `seenChunks`**（`KnowledgeSearchTool:256` / `GrepChunksTool:125`，由渲染代码写）⇒ 并发时多线程写同一个**非线程安全** `LinkedHashSet`：① 数据竞争；② 谁算"已见"取决于**完成顺序**而非**调用顺序** ⇒ 同一轮乱序完成得到不同输出。
- **修复一**：两个有状态读工具**移出并发白名单**（成为执行屏障）——`seenChunks` 由此只有**单一 writer**（渲染永远在引擎线程、按调用序），白名单注释写明"要恢复并发得先把取数与渲染+标记拆两段（渲染交给单 writer 按调用序）"。其余读工具不受影响（`WebFetchTool` 自带锁，`web_search`/`read_file` 等无状态）。
- **缺口二（中断语义）**：① 子线程 `permits.acquire()` 被中断后仍在 `finally` 里 `release()` ⇒ 许可数虚增、后续批次并发上限被悄悄放宽；② 父线程 `join()` 被中断就**跳过**继续收集结果 ⇒ 子线程还在跑，"本轮收集/下一批启动/下一轮工具写"与它们并发（跨越写屏障）。
- **修复二**：`ToolBatchExecutor`（**新拆**，见下）里：acquire 成功才 release；父线程中断 ⇒ **先中断全部子线程、再逐个等齐**，恢复中断标志后**按取消收束**（剩余调用不再启动、槽位补 `tool call canceled` 失败占位——结果槽不许留 null）。
- **结构收益（棘轮要求"拆一刀"）**：并发+屏障+中断语义从 `ActPhase` 抽到 `domains/.../agent/ToolBatchExecutor.java`（164 行）——`ActPhase` **803 → 699 行**（退出 ≥800 登记面、回落到基线以下）；入口只剩"调用执行器 + 结果按调用序回填 + 发事件"三行。
- **测试**：`ExecutionPolicyStatefulReadTest` 2 例（有状态读工具是屏障 / 无状态读工具仍在白名单）+ `ActPhaseInterruptJoinTest` 1 例（**join 被打断 ⇒ 取消全部子线程 + 等齐后才返回 + 恢复中断标志**；**先红**：白名单测试实测 `knowledge_search` 可并发；中断测试在 helper 缺位时编译红）。
- **闸门**：`spotlessCheck build` ✓ **1m51s** ✓ · **4968 / 失败 0 / 跳过 4** ✓（+3）· 十一守卫 ✓（棘轮：大文件 ≥800 从 2 个回到 1 个）。
- **登记（未做）**：① 恢复 `knowledge_search`/`grep_chunks` 并发需要"取数与渲染分离（渲染单 writer 按调用序）"或"同类有序、异类可并行"的批次策略；② 多 query 场景本就可以一次调用传 `queries[]`，故当前串行代价有限。

**✅ B254（2026-10-13，阶段 5 controller 剥离编排）**
- **验收达标**：`session/controller` 包内**不再有** `Executor` / `Parser` / `Orchestrator` 类——新增守卫 `SessionControllerPackagingTest`（**先红**：实测违规清单 = `QaSseOrchestrator.java` / `QaTurnExecutor.java` / `QaRequestParser.java`）。
- **搬迁**：`QaRequestParser`（295 行）/ `QaSseOrchestrator`（380）/ `QaTurnExecutor`（417）/ `QaTurnFinalizer` 与协作件 `QaRequestBinder` / `QaAttachmentResolver` → **新包 `com.ragagent.session.qa`**（含 `package-info`）；测试随类迁移（`QaRequestBindingTest` / `QaTurnFinalizerCommitGateTest` / `QaTurnFinalizerTenantContextTest` → `session/qa`）。
- **控制器内嵌件提出**（否则搬迁会造出 `qa → controller` 倒挂 ✗）：`stringListOf` / `ensureQuickAnswerStep` / `appendQuickAnswerReasoning` / `maxFileBytes` → `session.qa.QaTurnSupport`；`record ParsedRequest` → `session.qa.ParsedRequest`；`Base64Support` → `session.qa.Base64Support`；`SessionStreamController.sanitizeForLog`（两侧都在用）→ 中立 `session.support.LogSanitizer`。现依赖方向只有 `controller → qa / support`，`qa` 包内**零** `controller` 引用。
- **可见性**：迁移类的编排面（类/构造器/被跨包调用的方法）由包私有改为 `public`；`SessionStreamController.sanitizeForLog` 与 `QaRequestBinder` 的静态入口随之公开或在调用点改助手。
- **文档口径纠正（如实记）**：`session/controller/package-info.java` 原写"协作类属表现层、不是放错位置（2026-10-10 判据）"，本批按阶段 5 目标改写并说明差异与做法（把静态助手/内嵌类型一起提出⇒不再有倒挂）；auth / wiki / embedchannel 三处 `package-info` 中以本包为例的引用加了迁移注记。
- **守卫联动**：`check-event-face-case.py` 的 `EVENT_MAP_FILES` 键需随文件搬家（`session/controller/QaSseOrchestrator.java` → `session/qa/...`），已同步。
- **闸门**：`spotlessCheck build` ✓ **1m30s** ✓ · **4969 / 失败 0 / 跳过 4** ✓（+1）· 十一守卫 ✓。
- **登记（未做）**：① `KnowledgeQaController` 构造参数仍 **14 个**（编排类虽已迁出，控制器仍直接注入它们与 4 个 `ObjectProvider`）——真正减参需要"QA 轮门面/Facade"一层，列入后续；② `QaRequestParser`（295 行）内部拆分未做（验收只要包规则）；③ `QaAttachmentResolver` 的附件编排与 `QaRequestBinder` 的绑定逻辑仍可再向内收。
- **观察（非本批引入，flake）**：本次全量闸门首跑 `ModelConcurrencyGovernorWiringTest.settingsFailureLeavesGovernorUnwired` 偶发失败，**重跑 4/4 全绿**（并发/环境时序），已登记观察。

**✅ B256（2026-10-13，§10 改名清算：16 组，纯改名零行为变更）**
- **改名映射（§10.1 十组）**：agent 侧 `ToolCall`→`AgentToolCall`；approval 侧 `ToolPolicy`→`ApprovalToolPolicy`；langfuse 侧 `TokenUsage`→`LangfuseTokenUsage`；model.service 侧 `ProviderRegistry`→`ModelProviderCatalog`；storage.support 侧 `FileService`→`FileUrlPort`；websearch **实体** `WebSearchProvider`→`WebSearchProviderEntity`；approval 侧 `Event`/`EventBus`→`ApprovalEvent`/`ApprovalEventBus`；approval 侧 `ToolApprovalRequiredData`/`ToolApprovalResolvedData`→`ApprovalRequiredNotice`/`ApprovalResolvedNotice`。
- **§10.2 域前缀（三份 wire DTO）**：`datasource|mcp|model` 各自加前缀 → `Datasource|Mcp|Model` + `CredentialsResponse` / `CredentialFieldMetadata`（类名变了、**JSON 字段不变** ⇒ 金片零变化）；`FeishuRegion` / `ZhipuProvider` 按方案"可保留（不同域/不同能力）"未动。
- **顺带收敛**：改名带出的 **R8 内联全限定名 30 处清零**（`check-package-cycles.py` R8：4 允许 / 0 违规）——改回"import + 短名"，不新增任何依赖边；**环 0 / 包级 SCC 13=基线 / R7 0 不一致**，`已解耦包对`未回增。
- **基线同步（按结构批纪律，非掩盖）**：`scripts/json-key-freeze.baseline.json` 随 `tracing/langfuse/TokenUsage.java → LangfuseTokenUsage.java` 搬家同步（离开 1 进入 1，守卫自身指引 `--write`；前缀 `tracing/langfuse` 覆盖未变）。
- **测试**：**4970 / 失败 0 / 跳过 4**（纯改名，无新增测试）；契约金片（1349 个）**键签名零变化** ⇒ 对外线格式未变。
- **闸门**：`spotlessCheck build` ✓ **1m38s** ✓ · 十一守卫 ✓。
- **教训（写入 §15.4 方法论，务必遵守）**：整仓"简单名替换"会连坐同前缀类（`ToolCall`→`ToolCallTarget`）、误伤**字符串字面量 / javadoc 提及 / `package` 声明 / 包名前缀**，还会因 span 截断产出"包名当类型"的半截 FQN——本批第一次尝试因此产生 4 次大规模误伤（1405 文件 / 2094 处包声明 / 39 文件），全部 stash 撤销。**正确姿势**：① 按 import 判据限定作用域；② 只替换"匹配文本本身即完整类型"的 FQN（绝不按前缀切）；③ 占用判据必须含"本文件已声明的内嵌类型"；④ **每改一批立刻编译 + 跑守卫**（本次最终只动 12+1 个文件、逐条验证即收口）。
- **登记（未做）**：方案 §10.1 里的 `ModelResponse`（只剩 1 份，无歧义）未改名；`ToolResult` 已在早期统一（1 份）。

**✅ B257（2026-10-13，§7 命名与边界）**
- **包改名（§7.1 采纳 `agenttools`）**：`com.ragagent.agent.tools` → **`com.ragagent.agenttools`**（91 文件 / 7 子包）、`com.ragagent.agent.management` → **`com.ragagent.agentmanagement`**（21 文件 / 5 子包）；理由是方案 §7.1 的四条（名字与内容一致、避开 "integration" 既有语义、不再与 `agent-engine` 的 `com.ragagent.agent.*` 命名空间混淆、与 `agentmanagement` 对称）。
- **改动面**：前缀替换 **560 处 / 214 文件**（纯包前缀替换 ⇒ 无同前缀类连坐风险）；测试/夹具随包归位 **40 个文件**（`domains` + `boot` 两侧）；**非 Java 文件零改动**——实测无 Spring 扫描/yaml/MyBatis 别名引用旧包名（`@SpringBootApplication` 从 `com.ragagent` 扫，天然覆盖）。
- **新增边界规则（B257 验收）**：`ArchitectureRulesTest.agentAdapterPackagesDoNotTouchDomainServices`——`agenttools`/`agentmanagement` 不得依赖**各业务域的 service/mapper**（域清单显式列举，避免自身 `agentmanagement.service/mapper` 假阳性）。**规则先按严格版跑 ⇒ 当场抓到 3 处真实越界**（`SkillCatalogController`→audit、`CustomAgentService`→knowledge/auth、`AgentSuggestedQuestions`），按本仓棘轮纪律登记为基线（**只许减**；落位批 **B264** 负责清零）。
- **连带同步（结构批既定处置，非掩盖）**：① 环/SCC 基线随包名刷新（"新增/已消除"两组**逐名同构**＝纯改名产物）；② 大文件基线随路径刷新（`GrepChunksTool`/`WebFetchTool` 行数未变、仅路径变）；③ 换锚棘轮 BASELINE 键随路径更新（历史注释保留旧路径并注明改名）；④ 空包目录清理（S2）。
- **测试侧守卫同步**：`AgentEngineBoundaryTest`（`MANAGEMENT_SEGMENT`/`ENGINE_ROOT` 指到新位置；`agenttools` 加入 `ALLOWED_TOP_LEVEL`——原名因同属 `agent.*` 隐式通过，改名后需显式允许，真正解耦见 B260/B264；`MIN_SCANNED_FILES` 140→55，因 91 个工具文件迁出引擎面）、`AgentConfigKeyUsageTest`（豁免路径随包名更新）。
- **测试**：**4971 / 失败 0 / 跳过 4** ✓（+1 新规则）；契约金片零变化（纯包子树搬迁，wire 未动）。
- **闸门**：`spotlessCheck build` ✓ **1m34s** ✓ · 十一守卫 ✓。
- **踩坑记录（重要，写入方法论）**：`git mv` 保留时间戳 ⇒ **Gradle 增量/VFS 快照认为搬走的文件"已编译"**，于是 `domains` 少跑 **166** 个测试、`boot` 少 6 个（首跑全绿但测试数悄悄下降）。**识别信号**：测试汇总里 `domains/boot` 计数下降或 `--tests "新包.*"` 报 "No tests found"。**处置**：`./gradlew :<模块>:test --rerun-tasks`（或清 `build/classes/java/test`）后复跑；**结构批的闸门必须对照测试总数**，否则会以"全绿"掩盖覆盖丢失。

**✅ B258（2026-10-13，§8.1/§12.2 contracts 包边界）**
- **契约面落地**：新建 `com.ragagent.contract`（`:common` 内）+ `package-info` 写明硬约束（**只依赖 JDK**；零 Spring/MyBatis/Redis/Jackson/Langfuse/云 SDK；wire 序列化归 controller/dto）；**首片收纳**方案点名的 `contract.error.ErrorCode`（纯 JDK；11 文件 FQN 替换 + 同包引用者补 import）。`AppError` 带 `@JsonInclude` ⇒ 按"零 Jackson"口径留待后续（去注解属 wire 变更，需与金片同批动）——**登记为契约面下一片**。
- **三条规则**（新类 `ContractBoundaryRulesTest`，避免把 1000+ 行的 `ArchitectureRulesTest` 继续撑大）：**R1** 契约面只依赖 JDK 与自身；**R2** `client` 不得引用**他域** `mapper`（同域允许；`domainOf` 取第 3 段判域）；**R3** 模块 DAG 方向（`:common` 侧与 engine 侧不得依赖业务域）+ 棘轮（只许减，现基线 **0**）。
- **规则探针（验收达成）**：三条各自注入违例**全部转红**——R1 用 `contract.probe` 沾 Jackson、R2 用 `wiki.client` 直连 `session.mapper`、R3 用配置变体（engine 侧无法编译出对 domains 的引用，故以"把 engine 包临时算作业务域"验证判定逻辑）；清理后三条全绿。
- **起草期自我纠错（值得记）**：R3 初稿把 `com.ragagent.embedding` 当业务域，误判 `CompositeRetrieveEngine`/`LangfuseEmbedder` 等 **4 处违例**；查实 `embedding` 是 **engine 模块自己的包**（与 domains 侧同名包不是一回事）⇒ 业务域清单改为**按模块实际目录派生**（domains 有、engine 无），R3 基线随之**归零**（engine→domains 本就干净）。
- **守卫联动**：`check-package-cycles.py` 的 `L1`/`L1_CORE` 加入 `contract`（契约面在 `:common` 内、属 L1 底座）——否则守卫把 `retrieval→contract`、`common→contract` 误判成"新增分层违例"。
- **测试**：**4974 / 失败 0 / 跳过 4** ✓（+3 规则；domains 计数未变 ⇒ 无覆盖丢失）；契约金片零变化。
- **闸门**：`spotlessCheck build` ✓ **1m42s** ✓ · 十一守卫 ✓。
- **登记（未做）**：① `AppError` 去 Jackson 入契约面；② §8.1 其余契约（约 200 型）按 B259~B266 逐簇收纳；③ "业务域之间只经 contracts"要等契约面成形后再收紧（本轮只覆盖已存在的坏味道面）。

**✅ B259（2026-10-13，§8.2 platform 包边界）**
- **平台面落地**：新建 `com.ragagent.platform`（`:common` 内）+ `package-info` 写明边界（框架适配/可靠执行/通用技术能力，**不含业务状态机**；依赖方向 `platform → contracts`）。**首簇落位**：`com.ragagent.stream`（14 文件：`StreamManager`/`StreamEvent`/`LiveRun`/`RedisStreamManager`/`MemoryStreamManager`/`StreamStopWatcher`/`TurnStreamStore`…）→ `com.ragagent.platform.stream`；测试目录（4 个测试类）随包迁移；前缀替换 **39 + 21 文件**（第二遍补 `package` 声明——第一遍模式带尾点，漏掉了包声明行）。
- **新增规则 R4**（`ContractBoundaryRulesTest`）：**platform 面不得依赖业务域与三个 runtime 的实现**（runtime 清单：`agent`/`llm`/`retrieval`/`modelcontext`/`rerank`/`webfetch`/`embedding`/`chatpipeline`/`tracing.decorators`/`agenttools`/`agentmanagement`）+ 棘轮（现基线 **0**）。同时把 `platform` 并入 R3 的"底座侧"分类（`isCommonSide`）——底座/引擎层反向依赖业务域一律红。
- **规则探针（验收达成）**：R4 用配置变体注入（`:common` 看不见 engine/domains 的类型，无法编译出真实越界引用）→ **当场转红** → 还原后四条规则全绿。
- **守卫联动**：`check-package-cycles.py` 的 `L1`/`L1_CORE` 加入 `platform`（平台面属底座）——环/分层/SCC 基线**无需改动**即通过（`stream` 本来就是 L1，属位置平移）。
- **验收（方案要求的"每步启动测试"）**：显式跑 `:boot:test --tests com.ragagent.datasource.DataSourceRepositoryTest`（`@SpringBootTest` 真启动 Spring 上下文）→ **25 例全绿** ✓。
- **测试**：**4975 / 失败 0 / 跳过 4** ✓（boot +1 = R4；common 仍 139 ⇒ 搬走的 4 个测试类未丢，已按 B257 教训 `--rerun-tasks` 核对）。
- **闸门**：`spotlessCheck build` ✓ **1m46s** ✓ · 十一守卫 ✓。
- **登记（未做）**：平台面**其余簇**继续收拢（`event` 40 文件 → `platform.event`、`tracing` 19 → `platform.tracing.*`、`common.*` 149 → `platform.{access,config,http,persistence,prompt,security,storage,text,execution,outbox,parsing,task,model}` 各面）；本批只按阶段 6 纪律"一次一簇"落位首个簇，**未动**其余包名。

**✅ B260（2026-10-13，§8.3 agent-engine 包边界）**
- **口径**：§8.3 把工具**框架**（`AgentTool`/`BaseTool`/`ToolDefinitions`/`ToolRegistry`/参数校验/执行上下文与预算/输出持久化/流式修正）归 **Agent 运行面**（`agent.tool/` + `agent.tool.runtime/` + `agent.tool.spi/`），§7.3 把工具**实现**（按能力分族）留在适配面 `agenttools`。B260 落前者，**不动**业务工具。
- **实测到的真环（本批的意外收获）**：搬迁前 `com.ragagent.agent ⇄ com.ragagent.agenttools` **本身就是一对双环**（运行面 7 文件反向 import 工具实现面：`ToolDefinitions`×4、`agenttools.runtime.*`×5、`ToolRegistry`/`ToolExecContext`/`ExecutionPolicy`；工具实现面 4 文件反向 import 运行面）——即 `agent` 与 `agenttools` 同属一个 SCC。这解释了"引擎为何看起来认识具体工具"。
- **搬迁**：21 个框架类（`AgentTool`/`ArgumentValidator`/`BaseTool`/`Cleanable`/`DocChunkSupport`/`ExecutionPolicy`/`McpCatalogGuardedTool`/`OutputBudgets`/`OutputLimitProvider`/`ParamCaster`/`ParamValidator`/`SequentialThinkingTool`/`SkillReadFileTool`/`TodoWriteTool`/`ToolCancellation`/`ToolCapabilities`/`ToolDefinitions`/`ToolExecContext`/`ToolOutput`/`ToolRegistry`/`ToolRequest`）+ `runtime/`(8) + `spi/`(2) → `com.ragagent.agent.tool{,.runtime,.spi}`；测试 6 个 + `runtime/`(5 个) 随包迁移；FQN 替换 73 文件 + 迭代补 import（`RecordingSupport`/`GoRecording45A` 是 **testFixtures** 的助手，**留在** `agenttools` 面 ⇒ 搬走的测试补 import 即可，不动可见性）。
- **新规则**（`ArchitectureRulesTest`）：「**B260：Agent 运行面不得依赖工具实现面**」（`agent..` → `agenttools..`，基线 **0 = 零容忍**）。
- **规则探针（验收达成，且踩到一个坑）**：第一次用**纯 import** 注入探针 ⇒ **没红** ✗——ArchUnit 读**字节码**，未被使用的 import 会被擦除；改为真实引用（`AgentEngine` 内 `KnowledgeSearchTool.class` 字段）⇒ **当场转红** ✓，还原后零残留 ✓。**经验：ArchUnit 探针必须制造真实字节码依赖。**
- **环守卫刷新（只减）**：`--write` 后基线 diff **只有删除**——两两环 `agent⇄agenttools` 消除、SCC `{agent,agenttools}` 消除；包级 SCC 13 组不变（`agenttools{,runtime,spi}` 组**改名**为 `agent.tool{,runtime,spi}`）。**B260 因此消掉了仓内一对双环**。
- **换锚（守卫文档 B168 的既有教训，本批踩到并照办）**：`check-json-key-case.py` 里「模型可见工具名保留 snake」的键级豁免锚在 `agenttools/ToolCapabilities.java`，搬迁后锚失效 ⇒ 更新为 `agent/tool/ToolCapabilities.java`（19 个键与理由**均不变**）+ 脚本注释里的路径说法同步（`agent/tools`→`agenttools`→`agent/tool` 三段迁移史）。
- **验收（"每步启动测试"）**：`:boot:test --tests com.ragagent.datasource.DataSourceRepositoryTest`（`@SpringBootTest`）25 例全绿 ✓。
- **测试**：**4976 / 失败 0 / 跳过 4** ✓（boot +1 = B260 规则；domains 仍 2949 ⇒ 搬走的 11 个测试类未丢，`--rerun-tasks` 核对过）；`spotlessCheck build` ✓ **1m29s** ✓ 十一守卫 ✓。
- **登记（未做）**：`agentmanagement → agent`（2 文件，方向正确，保留）；`agent` 与 8 个业务域间接环仍在（属 B263~B266 域内重排的范围）；`agent` 根包与八个段协作者的 seam（2026-10-12 定案：不做 `loop/` 归位）**本批未触碰**。

**✅ B261（2026-10-13，§8.4 retrieval-engine 包边界）**
- **实测到的真缠绕**：① grep 的**结果视图** `GrepChunkView` 藏在业务工具类 `GrepChunksTool` 内 ✗；② **检索政策** `GrepChunksScoring`（内容签名 / 打分 / 去重 / MMR）住在工具实现面 `agenttools.knowledge` ✗；③ 它还要借用工具侧 `FaqSnippet.regexMatchesAny` ✗ ⇒ **工具实现面 ↔ 检索面互为依赖**的雏形（engine 根本看不见 domains，`GrepChunksScoring` 一旦要进引擎就编译不过——本批正是这样暴露的）；④ `retrieval.obs`(1 文件) 与 §8.4 的 `telemetry` 面名字不一致。
- **归位**：`GrepChunkView` 由嵌套提为**顶层** `com.ragagent.retrieval.support.GrepChunkView`（`nz`/`toChunk` 提 public，补 `ChunkFacts` import）；`GrepChunksScoring` → `com.ragagent.retrieval.support`（类与方法 public 化，删旧 import）；`regexMatchesAny` → `com.ragagent.retrieval.support.SearchTextUtil`（`FaqSnippet` 改为**委托**，语义逐字保留）；`retrieval.obs` → `retrieval.telemetry`（1 文件 + 8 处引用，测试目录随迁）；调用方 `GrepChunksTool` / `KnowledgeSearchRanking` / `AgentToolKbBackends`(session) / `GrepChunksRecordingTest` 同步。
- **新规则**（`ContractBoundaryRulesTest`）：「**B261：retrieval 面不得依赖业务域与工具实现面**」（`retrieval..` → 业务域 / `agenttools` / `agentmanagement`，基线 **0 = 零容忍**；实测搬迁前后全域均为 0 ⇒ 这条锁的是"不许再缠回去"）。**探针**：配置变体注入当场转红 ✓ 还原 ✓——这里**不能**用真实代码探针：`:engine` 看不见 `:domains`，越界引用根本编译不过（B260 是反向情形，故用真实字节码依赖）。
- **换锚两处（B168/B169 既有教训，B261 又踩）**：① `check-json-key-case.py` 的 `FROZEN_PREFIXES`：`retrieval/obs/` → `retrieval/telemetry/`；② **冻结覆盖快照** `scripts/json-key-freeze.baseline.json` 同行更新（diff 恰一行：`retrieval/obs/RetrievalObs.java` → `retrieval/telemetry/RetrievalObs.java`）。③ 空目录 `retrieval/obs/` 触发「目录卫生 B124：死包目录」⇒ 删除（`git mv` 只搬文件、不删目录）。
- **第一版被守卫拦下的写法**：`FaqSnippet` 委托时我写了内联 FQN `com.ragagent.retrieval.support.SearchTextUtil.regexMatchesAny(...)` ⇒ **R8 内联全限定名**守卫点名（`agenttools/knowledge/FaqSnippet.java:393`）⇒ 改为 import ✓（这条守卫的存在价值现场可见）。
- **测试**：**4977 / 失败 0 / 跳过 4** ✓（boot +1 = B261 规则；engine 561 / domains 2949 均未丢）；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m39s** ✓ 十一守卫 ✓。
- **登记（未做）**：① **两份 `buildContentSignature` 语义有别**（`GrepChunksScoring` 用 `\s+` 折叠、`SearchTextUtil` 用 Unicode 空白感知的 `trimUnicodeWhitespace`+`splitFields`）⇒ **未合并**，需先评估对去重/签名的影响再统一；② `KnowledgeSearchRanking` **仍留工具面**——它的构造器吃整个 `KnowledgeSearchTool`（读 `tool.reranker` / `tool.searchTargets`）⇒ 归位到 `retrieval.policy` 必须先做**状态外提**（reranker/threshold/targets 参数化）重构，属独立批次；③ 因此 **`retrieval.policy` 面本批未创建**（避免死包目录，B124 守卫）；④ `retrieval.engine`(69 文件) → `retrieval.application.engine`、`retrieval.vlm`/`graph`/`domain` 的最终归位仍未做。

**✅ B262（2026-10-13，§8.5 model-runtime 包边界；三 runtime 收口）**
- **搬迁**：① `llm.chat`(37) 按 §8.5 拆成三子面——`adapter.provider.chat`(12：`RemoteApi*`/`RemoteHttpOps`/`SseReader` 之外的远端家族)、`adapter.provider.chat.anthropic`(11 Anthropic 族)、`adapter.provider.chat.common`(11 底座 + 流三件 `Sync/Queued/StreamLifecycle`)；② `llm.asr`(1) → `adapter.provider.asr`；③ `LlmChatClients` → `modelruntime.application`（见下）；④ 新建 6 个面的 `package-info` + 同步 `llm/package-info`（chat 已迁出）。
- **本批解掉两处真环（都是搬迁过程逼出来的）**：① **`llm ⇄ modelruntime`**——反向边只有**一个文件**（`llm/asr/AsrTranscriber` → chat 的 `LlmTransport`），按 §8.5 把 asr 归位 `adapter.provider.asr` 即解；② 拆分后**新造**的包级 SCC `{chat, chat.anthropic, chat.common}`——罪魁是**装配类** `LlmChatClients`（在 `common` 里引用全部 provider 实现 ⇒ 三面互指），按"装配层在适配器之上"归位 `modelruntime.application` 即解 ⇒ **包级 SCC 回到基线 13（零增长）**。
- **新规则**（`ContractBoundaryRulesTest`）：「**B262：modelruntime 面不得依赖业务域与工具实现面**」（零容忍）；探针用**配置变体**（engine 看不见 domains，同 B261），当场转红 ✓ 还原 ✓。
- **纪律点（本批最重要）**：环守卫第一次 `--write` 把 **"新增 1 对环 + L2→L3 5 条"**也记进了基线 ✗ —— `--write` 不是"消红按钮"，那条命令当场被**回退基线和自查**纠正：真正的两处修法是 ① 给守卫 `L2` 分类补 `"modelruntime"`（新面属运行时，不是业务域 ⇒ 消除 `tracing→modelruntime`、`L1 底座→业务域` 误报）、② 修上面两处真环。**最终带环基线零变化。**
- **换锚四处（B168/B169/B121 教训）**：① `check-json-key-case.py` 的 `FROZEN_PREFIXES` 补 `'modelruntime/'`；② 冻结覆盖快照 `--write`（diff = 24 行**纯路径迁移**：`llm/chat/**` → `modelruntime/**`）；③ `scripts/file-size.baseline.json` 的 `llm/chat/AnthropicChat.java`(769) → 新路径（**同一个文件**，773 行）；④ 环守卫 `L2` 分类。空目录 `llm/chat`、`llm/asr` 已清（B124）。
- **其他守卫现场拦截**：`ProviderAdapters.trimRightSlash` 非 public（跨子面用）⇒ 提 public；`ProviderAdapterRegistryTest` 测的是 `RemoteApiChat` 的包内 API ⇒ 测试随目标子面归位（不硬提可见性）；**`:engine:javadoc` 三处 `{@link SseReader|ThinkingStrategy|ProviderAdapter}` 失链** ⇒ 改**全限定 `{@link}`**（仓内既有风格，见 `chatpipeline/plugin/package-info.java`）。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓（boot +1 = B262 规则；engine 561 未丢）；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m43s** ✓ 十一守卫 ✓。
- **登记（未做）**：§8.5 其余面未搬——`adapter.provider.{ollama(7), registry(34), rerank(7), vlm(2), embedding(10)}`、`adapter.access/api`、`application{,.port,.rerank}`（`Reranker` 族/`Embedder` 族/`EmbeddingHttp`/`TranscriptionExecution`）、`domain.stream`（`StreamResponse`）、`policy{,.concurrency}`（limiter 9）、`stream`（`ManagedModelStream`/`StreamAccumulator`/`StreamLifecycle` —— 本批 `StreamLifecycle` 暂在 `chat.common`）。

**🔶 B263 第①步（2026-10-13，§8.8.2 knowledge 域内重排）**
- **口径**：§8.8.2 的 knowledge 目标树（`api/ chunker/ client/ config/ controller/ domain{,.access,.document,.graph,.task}/ dto{,.chunk,.doc,.faq,.kb,.tag}/ index/ mapper/ repository/ service{,.enrichment,.index,.ingest,.management,.process,.query}/ storage/ support/ task/`）；验收 **R9 基线只减**。
- **本步落位**：`knowledge.security`(4 类 + package-info) → **`knowledge.domain.access`**（逐类 1:1 对应 `ChunkAccessGuard`/`FaqGuard`/`KnowledgeAccessGuard`/`KnowledgeRouteGuards`）；测试 `ChunkAccessGuardMoveGuardTest` 随迁；环基线 diff = **恰一行纯改名**（`com.ragagent.knowledge.security` → `com.ragagent.knowledge.domain.access`，13 组/成员数不变 ⇒ 属"换锚"，不是放宽）。
- **A10 规则随目标树细化（含探针）**：`ArchitectureRulesTest.A10`「domain 不得依赖 service/controller」把 **`..domain.access..` 排除**——访问守卫的职责就是代表域校验访问，必须查 service 侧状态（§8.8.2 把它归在 `domain.access`）。**探针**：给 `knowledge/domain/Knowledge.java` 注入 `HousekeepingService.class` 真实引用 ⇒ 规则**当场转红** ✓ 还原零残留 ⇒ 细化后仍抓真违例（只放过守卫面）。
- **本步最重要的实测（决定 B263 的走法）**：先按 §8.8.2 把 `service/`(36) 拆成四子面（25 文件：`service.{enrichment,process,query,management}`）——编译全绿，但**环守卫给出反证**：老 4 节点 SCC `{repository,security,service,task}` 被消除的**同时**，根 `service` 与四个子面 + `task` + `repository` + `domain.access` 连成**更大的 8 节点 SCC**（成员数 4 → 8 ✗）。逐边实测（`service→service.management` 9、`service.enrichment→service` 15、`service.management→service` 11、`task→service` 9 …）后判定：**只要 `KnowledgeService`(796 行)/`KnowledgeBaseService`/`KnowledgeFileService`/`KnowledgeParseService`/`ChunkVectorIndexer`/`KnowledgeVectorWrites`/`KnowledgeFinalizeAdapter`/`ChunkPortAdapter` 这 8 个枢纽类还共处 `service` 包，拆面只会把环扩大**——而目标树里这 8 个类**根本不存在**（其职责被拆成 `repository/*` 的 `DocumentParser`/`KnowledgeChunker`/`KnowledgeOutbox`/`KnowledgeRepository`… 与 `service.process/*` 的 `DocumentProcessingPipeline`/`ProcessKnowledgeUseCase`… 以及 `service.{management,query,ingest,index}/*` 的 use-case 类）。
- **因此按纪律回退**（`R9 基线只减` 优先于进度）：拆面 25 文件**全部搬回**、FQN 指回、同包 import 清理、**7 处为跨面而放开的可见性全部还原**（`KnowledgeService.tenantId/timeString`、`KnowledgeBaseService.ensureDefaults`、`KnowledgeFileService.updateKnowledgeRow`、`KnowledgeParseService.resetKnowledgeForReparse`、`ChunkVectorIndexer.embedBatchSize`、`ChunkQuestionService.generateAndStoreQuestionsForWorker`）⇒ 工作树回到"只有 `security→domain.access`"的**全绿**状态。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓（domains 2949 `--rerun-tasks` 重编核对未丢）；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m34s** ✓ 十一守卫 ✓。
- **B263 余步（登记，按依赖排）**：② 拆 `KnowledgeService`(796)/`KnowledgeBaseService`/`KnowledgeFileService`/`KnowledgeParseService` 为 `repository/*`（`KnowledgeRepository`/`KnowledgeBaseRepository`/`DocumentParser`/`KnowledgeChunker`/`KnowledgeOutbox`…）+ use-case（`service.ingest|process|management|query|index/*` 的目标类名）；③ 8 枢纽消失**之后**再拆 `service` 七子面（本次已实测：提前拆 = 环扩大 ✗）；④ `domain.{document,task,graph}`（`graph` 的 `EntityExtraction`/`ExtractPrompts`/`PipelineConfig` 目前在 `:engine` 的 `llm.extract`，属跨模块归位）；⑤ `api/`(4 适配器)、`index/RetrievalIndexAdapter`、`client/EmbeddingExecutionClient`、`mapper/repository` 目标名对齐（`KnowledgeBaseMapper`/`StorageBackendMapper` 已在位）。

**🔶 B263 第②步（2026-10-13，断掉 knowledge SCC 的真实成环边）**
- **发现**：4 节点 SCC `{domain.access, repository, service, task}` 里，`repository → service` 只有**一处**：`ChunkRepository.findChunks` 调用 `ChunkPortAdapter.factsAll(...)`（借的是**静态投影助手**，不是适配器实例）。
- **修法（抽到环外 + 落目标面）**：① 把 `ChunkPortAdapter` 的四个静态投影 `factsOf/factsAll/view/viewAll` 抽到 **`knowledge/support/ChunkProjectionAdapter`**（环外；`support/` 也是 §8.8.2 的目标面）；② 适配器本体 `service/ChunkPortAdapter` → **`knowledge/api/ChunkPortAdapter`**（§8.8.2 的 `api/` 适配面）。
- **结果（验收达成）**：**`repository` 退出该 SCC** ⇒ 老组 4 成员 → 新组 `{domain.access, service, task}` **3 成员**；包级 SCC 组数 **13 不变**、`--write` 后基线 diff **恰为删除一行**（`"com.ragagent.knowledge.repository"`）⇒ 纯减少 ✓。
- **踩到并修掉的两个"我自己的脚本坑"**：① 补 import 的启发式把 **javadoc 文本**里的 `ChunkPortAdapter` 当成代码引用，给 `support/ChunkProjectionAdapter` 与 `ChunkRepository` 各加了一条**多余 import** ⇒ 立刻形成新 SCC `{api, repository, support}`（组数 13→14 ✗）——按"分组/成员只许减"**当场清掉**，SCC 回到 13 组；② 同一启发式给 **engine** 的 `ImageInfoEnricher`（只有 javadoc 提到该类）加了 domains 的 import ⇒ 编译报 "package does not exist"（engine 看不见 domains）⇒ 改为 javadoc 文本用全限定名。
- **守卫现场拦截（都是"基线变好要收紧"）**：A16/A17 基线里的 `com.ragagent.knowledge.service.ChunkPortAdapter` 随搬迁改名后**已不再违例** ⇒ 按守卫提示**删除两条基线**（规则自带的收紧机制）；`:domains:javadoc` 两处 `{@link #view}/{#viewAll}` 锚点随静态方法搬走而失链 ⇒ 指向新类并写明原因。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m39s** ✓ 十一守卫 ✓。

**🔶 B263 第③步（2026-10-13，SCC 组成员 3→2）**
- **选边（先测后选）**：两条候选边对里，`service → domain.access` 的 7 处分散在 7 个类（守卫调用点，天然合法），而反向 `domain.access → service` 只有 **3 处且全在守卫类**（把整个 `KnowledgeService` 当字段/参数）⇒ 断反向更划算。
- **修法一（窄接口反转）**：新增 `domain.access.KnowledgeReadPort`（守卫真正用到的 4 个读方法：`findKb` / `getKnowledge` / `getKnowledgeByIdOnly` / `getKnowledgeBatch`），`KnowledgeService implements KnowledgeReadPort`，三个守卫（`FaqGuard` / `KnowledgeAccessGuard` / `KnowledgeRouteGuards`）改依赖端口 ⇒ **`domain.access ↛ service`**（端口与守卫同包，不产生新包边；Spring 按类型注入，端口只有一个实现）。
- **修法二（三角环的另一条腿）**：断掉 service 边后环仍在——手工核算 SCC 显示真正的回路是 **`service → domain.access → task → service`**：守卫 `KnowledgeRouteGuards` 调 `KnowledgeTaskIdCodec.taskTenantId(...)` 做任务路由鉴权，而编解码器在 `task/` 里。该编解码是**纯值编解码** ⇒ 迁到环外的 **`knowledge/support/KnowledgeTaskIdCodec`**（6 处引用换锚）⇒ 环断。
- **结果（验收达成）**：SCC 组 **3 成员 → 2 成员**（`{service, task}`）；组数 13 不变；`--write` 后基线 diff **恰为删除一行**（`"com.ragagent.knowledge.domain.access"`）⇒ 纯减少 ✓。B263 累计：4 → 3 → **2** 成员。
- **守卫现场拦截**：① **R8 内联全限定名** 抓到我第一版 `implements com.ragagent.knowledge.domain.access.KnowledgeReadPort` ✗ ⇒ 改 import；② **A16** 的基线条目 `evaluation.service.EvaluationService` 因编解码器迁出 `task/` 边界而**不再违例** ⇒ 按规则提示删除条目收紧基线。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m31s** ✓ 十一守卫 ✓。

**🔶 B263 第④步（2026-10-13，枢纽拆分起步：`repository/DocumentParser` 落位）**
- **为什么不能"整个搬"**：最轻的枢纽 `KnowledgeParseService`（124 行 / 仅 1 处引用）直接搬 `repository/` 会**复活 B263② 刚断掉的 `repository → service`**（它依赖 `KnowledgeAccessHelper`/`KnowledgeFolderService`/`KnowledgeFileService`/`SpanTracker` 四个 service 类）⇒ 按目标树应当是**切分**，不是搬家。
- **切法**：① 新增 **`knowledge/repository/DocumentParser`**——把两处行级读写抽成方法 `findById(tenantId, id)`（未软删、租户内、LIMIT 1）与 `markCancelled(id, now, message)`（状态+文案+子任务归零+updated_at）；本类**零 service 依赖**（方向恒为 repository ← service）；② `KnowledgeParseService` 保留编排与状态机判定（cancelled 幂等 / completed|failed→400 / deleting→400）+ span 收口 + 入队，行级 IO 全部改走 `DocumentParser` ⇒ **service 侧不再直接持有 MyBatis 语句**。
- **规则收紧（守卫现场拦截）**：A17 的基线条目 `com.ragagent.knowledge.service.KnowledgeParseService` **已不再违例**（MyBatis 迁出）⇒ 删除该条目；**A16 的同名条目保留**（它仍依赖 `task` 边界的 `KnowledgeProcessingQueue` ⇒ 仍属 A16 范围）。两条基线同名易混，按行号区分（A16 在 764、A17 在 831）。
- **SCC 复核**：13 组不变、`{service, task}` 2 成员不变（新 `DocumentParser` 未造新环 ✓）。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m28s** ✓ 十一守卫 ✓。
- **登记（枢纽拆分余步）**：按"最轻优先"继续——`KnowledgeVectorWrites`(51 行/1 引用)、`KnowledgeFinalizeAdapter`(71/1)、`ChunkVectorIndexer`(386/2)、`KnowledgeFileService`(422/2)、`KnowledgeParseService` 余部、`KnowledgeBaseService`(626/18)、`KnowledgeService`(796/28)；每个都按"切分而非搬家"处理（避免复活 `repository → service` 与 `service → task` 边）。

**🔶 B263 第⑤步（2026-10-13，两个最轻枢纽归位：`api/` 与 `index/` 面）**
- **`KnowledgeFinalizeAdapter`(71 行) → `knowledge/api/`**：它 `implements KnowledgeFinalizePort`（`common.knowledge` 的**跨域端口**）⇒ 与 B263② 的 `ChunkPortAdapter` 同一条规则——跨域端口实现归 `api/` 适配面（§8.8.2）。测试 `KnowledgeFinalizeAdapterTest` 随迁。
- **`KnowledgeVectorWrites`(51 行) → `knowledge/index/`**：它按 KB 解析"已绑定引擎"（依赖 `:engine` 的 `retrieval.engine.*`：registry / ownership / CompositeRetrieveEngine），属 §8.8.2 的 `index/` 面（本步顺带建了 `index/package-info`）。**副作用（有益）**：它原是 `task → service` 9 处引用之一 ⇒ 归位后该腿变为 `task → index`。
- **规则收紧**：A16/A17 里 `KnowledgeFinalizeAdapter` 的条目标签随搬迁变成 `com.ragagent.knowledge.api.KnowledgeFinalizeAdapter`，而它已**整体离开 `..service..`** ⇒ 两条均按守卫提示删除（这类"条目过期"在本批次已出现 5 次，都是搬迁带来的**债务减少**）。
- **SCC 复核**：13 组不变、`{service, task}` 2 成员不变（结构面未造新环）。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m43s** ✓ 十一守卫 ✓。
- **操作小坑**：`git mv` 迁测试文件前需先建目标目录（首次因目录不存在报 "No such file or directory"，已补 `mkdir` 重跑）。

**🔶 B263 第⑥步（2026-10-14，`ChunkVectorIndexer` → `index/`；并触发 `KnowledgeService` 的行数刹车信号）**
- **`ChunkVectorIndexer`(386 行) → `knowledge/index/`**：实测它**零 service 同胞依赖**（只靠 `KnowledgeBaseMapper`/`KnowledgeMapper`、`model.*`、`embedding.Embedder`、`vectorstore`、`repository.ChunkRepository`、以及 B263⑤ 刚归位的 `index.KnowledgeVectorWrites`）⇒ 本步是**整搬**（不是切分），与"切分而非整搬"的规矩不冲突（规矩针对的是"带 service 依赖的类"）。
- **副作用（有益）**：它原是 `task → service` 的引用之一 ⇒ 现为 `task → index`（`task → service` 9→8→**7 处**）。
- **可见性一处**：`FaqIndexWriter`（service）调 `ChunkVectorIndexer.embedBatchSize(String)`（原同包）⇒ 该方法是**纯静态解析**（空/null→5、非法→IllegalState），提为 `public` 并写明理由（两处共用一份规则，不复制）。
- **规则收紧**：A16/A17 的 `ChunkVectorIndexer` 条目（搬迁后标签已是 `knowledge.index.ChunkVectorIndexer`）因整体离开 `..service..` 而不再违例 ⇒ 删两条（本批次第 6 次）。
- **⚑ 刹车信号（下一步必须处理）**：本步给 `KnowledgeService` 补的必需 import 把它从 796 行推到 **正好 800 行**，撞上大文件棘轮的 **≥800 登记阈值**（守卫当场报 "≥800 行 2 个"）。我先把**自己造成**的 `implements` 折行消掉（回归 **799 行**，≥800 重新只剩已登记的 `KnowledgeProcessWorker`），闸门复原全绿——**但这只是止血**：`KnowledgeService` 已贴近阈值，**下一步（B263⑦）必须先拆它**（抽 `repository/KnowledgeRepository` + use-case），否则任何一行变化都会再撞红线。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m44s** ✓ 十一守卫 ✓（≥800 计数回到 1）。

**🔶 B263 第⑦步（2026-10-14，拆 `KnowledgeService`：投影抽 `support/` + 嵌套异常提顶层；刹车信号解除）**
- **触发**：第⑥步把 `KnowledgeService` 顶到 **799/800 行**（撞大文件棘轮 ≥800 登记阈值）⇒ 本步必须先拆它。
- **抽取一：`support/KnowledgeProjectionAdapter`**（4 个静态投影逐字迁出 + `MAPPER` 字段）：`factsOf(List<Knowledge>)` / `factsOf(Knowledge)` → `KnowledgeDocumentFacts`、`tagView(KnowledgeTag)` → 标签 JSON 视图、`timeString(OffsetDateTime)` → 线格式时间。与 B263② 的 `ChunkProjectionAdapter` 同一条思路（"实体 → 载荷"只留一份映射）。调用点全部换锚，含**两处方法引用** `KnowledgeService::tagView` → `KnowledgeProjectionAdapter::tagView`（全局字符串替换抓不到 `::` 形式，是编译错误逼出来的）。
- **抽取二：嵌套类 `DuplicateKnowledgeException` → 顶层类型**：它被 `datasource`(3)/`im`(1) 等域以 `KnowledgeService.DuplicateKnowledgeException` 形式引用 ⇒ 嵌套在门面里会让这些域反向依赖近 800 行的枢纽类。
- **⚠️ 一个必须记住的实测**：第一版把它放进 **`domain/`** ⇒ 守卫**当场报新环** `{knowledge.domain, knowledge.dto.kb}`（包级 SCC 13→14 ✗）——因为该异常携带 `dto.kb.DuplicateKnowledgeDetails` 负载，`domain → dto.kb` 与既有的 `dto.kb → domain` 形成双环。改放 **`service/` 顶层**（零新包边，仍达成"不再嵌套在枢纽里"）⇒ 守卫回到 13 组 ✓。**结论："提为顶层类型"不等于"必须放 domain/"——要看它的载荷在哪个包。**
- **结果**：`KnowledgeService` **799 → 748 行** ✓（≥800 阈值彻底解除，不再是一行之差）；新增 `support/KnowledgeProjectionAdapter`、`service/DuplicateKnowledgeException` 两个顶层类型。
- **偶发测试（记录）**：全量跑时 `RedisWikiDeletedTombstoneStoreTest.clearRemovesTombstone` 失败一次，**单跑即过** ⇒ Redis 依赖型偶发，与本步改动无关（本步未触碰 wiki/Redis 路径）。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m34s** ✓ 十一守卫 ✓（13 组 SCC 未变）。
- **登记（余步）**：
**🔶 B263 第㉙步（2026-10-14，`api/ChunkPortAdapter` + `domain/access/FaqGuard` 归仓储 ⇒ 域内 mapper 直连归零）**
- **只新增 2 个仓储方法**：`ChunkRepository.listTextChunks(tenantId, knowledgeId)`（TEXT 型 + `chunk_index` 升序）与 `deleteScoped(tenantId, chunkId)`（租户内按 id 硬删）；其余 4 处**全复用**——`listChunksById(tenantId, ids)`（与适配器原直连同形，含空集合短路同款）、`KnowledgeTagRepository.getBySeqId` / `getByName` / `listTagsBySeqIds`（三处 tag 读取各自逐字同形，已核对）。
- **两处"同包双入口"合并**：`ChunkPortAdapter` 与 `FaqGuard` 都**同时**注入了 mapper 与 repository（历史遗留）⇒ 删掉 mapper 入口（含字段/构造参数/import），只留仓储。
- **连带**：`ChunkTypes` 常量其实在 `:common` 的 `com.ragagent.common.pipeline`（放仓储里引用它是对的：仓储本就依赖 common 常量）⇒ import 修正；死成员守卫点名的 1 个未使用 import 清掉；`ChunkPortAdapter` **96 → 85 行**、`FaqGuard` **299 → 296 行**。
- **里程碑**：**`knowledge` 域内 mapper 直连归零** —— 全域只剩 `repository/` 8 个文件（数据层，合法持有）与 `service/KnowledgeBaseLookupAdapter` 那一处**刻意**的可选 mapper（㉕ 已写明理由）。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m41s** ✓ **十二守卫** ✓（环 0 / SCC 13 组未变）。
- **登记（收尾三项，B263 余步）**：
**🔶 B263 第㉚步（2026-10-14，`escapeLikeKeyword` 三副本合一 ⇒ `common.mybatis.LikeEscapes`）**
- **实测先行**：先把三处实现逐个读出来对比——`auth/service/TenantService`（private）、`session/repository/SessionRepository`（static，且被 `MessageRepository` 借用）、`knowledge/repository/KnowledgeRepository`（⑯ 由检索查询搬入）——**三份逐字相同** ✓ ⇒ 才动手合一（不同的就只登记不合并，如 ⑯ 那两份 `buildContentSignature`）。
- **归位点选择**：`com.ragagent.common.mybatis`（`PageRequests` / `TenantFilterGuard` 的邻居，同属"数据层小助手"），名为 **`LikeEscapes.escape(...)`**；类注释写明出处清单与**契约**（入参非 null、出参是可直接拼进 LIKE 的裸转义文本、PG LIKE 默认 `ESCAPE` 就是反斜杠）。
- **调用方改造**：三处本地实现删除，2 + 1 + 1 个调用点改为 `LikeEscapes.escape(...)`，`MessageRepository` 的跨类借用（`SessionRepository.escapeLikeKeyword`）也一并改指新助手；`TenantService` 注释里的旧名同步（javadoc 指向 {@code LikeEscapes.escape}）。
- **补聚焦单测**：`common/src/test/java/com/ragagent/common/mybatis/LikeEscapesTest`（2 例：三条元字符各自转义；纯 `%` 不退化成"匹配一切"、普通文本原样）——**common 139 → 141** ✓（建文件前先 `mkdir -p` 目标包目录，正是记忆里那条小坑）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m44s** ✓ **十二守卫** ✓（环 0 / SCC 13 组未变）。
- **登记（B263 剩余两项收尾）**：
**🔶 B263 第㉛步（2026-10-14，`task → service` 端口反转：6 文件 → 1 文件）**
- **实测先行**：四个队列 + 装配类**都只是把服务当成"JSON 载荷的消费者"**（`service.handleJson(body, finalAttempt)`），而两个服务**恰好都有同签名方法**（`handleJson(String, boolean)`）⇒ 反转成本极低：**实现方零方法改动**。
- **首版踩了两个坑（都由守卫/测试当场抓出，已改正并在端口 javadoc 写明）**：
  1. **端口放错层**：首版放 `task` ⇒ 两个 service 因 `implements` 被 ArchUnit **A16**（service 不得依赖边界层 task）判为**新增违例** ✗ ⇒ 端口移到 **`domain`**（payload 类型本就在此，两侧都能合法依赖：task→domain ✓、service→domain ✓）。
  2. **一个端口两个实现**：首版共享一个 `JsonTaskConsumer` ⇒ Spring 按类型注入**歧义** ⇒ **boot 1211 个测试全红** ✗ ✗ ⇒ 拆成**两个各自唯一的端口**（`domain.ChunkExtractConsumer` / `domain.QuestionGenerationConsumer`）。
- **结果**：`task → service` 从 **6 文件降到 1**（只剩 `KnowledgeProcessWorker → service.SpanTracker`，归下一步 ㉜）；`service → task`（11 文件）保持不变（单向）⇒ 环的**一半已断**（SCC 仍 2 成员，等 ㉜ 拆 SpanTracker 后收口）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓（含 boot **1329 / 0**——Spring 上下文真启动）；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m36s** ✓ **十二守卫** ✓。
- **登记（B263 剩余）**：
**🔶 B263 第㉜步（2026-10-14，`SpanTracker` 迁 `domain.span` ⇒ **`{service, task}` 包级 SCC 消除**）**
- **本步是 ㉛ 的收口**：㉛ 把 5 条 `task → service` 边反转掉后，只剩 `KnowledgeProcessWorker → service.SpanTracker` 一条 ⇒ 迁 `SpanTracker` 到 `domain.span` 后 **`task → service` 归零** ✓ ⇒ 守卫当场报 **「包级 SCC：12 组（基线 13）；已消除/变形：com.ragagent.knowledge.service, com.ragagent.knowledge.task」** ✓ ⇒ `--write` 后基线 diff **只有那 4 行删除**（纯收益，符合"只许减"）✓。
- **搬动暴露的两处真耦合（都已正确处置）**：
  1. `SpanTracker` 用了 **`KnowledgeService.ALL_STAGES`**（公共常量）与同包可见的 `tenantId()` —— 直接 import `KnowledgeService` 会造出 `domain.span → service` 的**反向边**（新环 ✗）⇒ 正解是**把共享物移出**：`ALL_STAGES` 上移到 `domain.KnowledgeProcessingSpan.ALL_STAGES`（那 5 个 `STAGE_*` 常量本就住这里），`KnowledgeService.ALL_STAGES` 改为**委任**（对外公共常量不变）；`tenantId()` 本步未涉及（SpanTracker 未用它）。
  2. **两处换锚**（B168/B263㉖ 既有教训）：① `check-json-key-case.py` 的键级豁免 `'knowledge/service/SpanTracker.java': {'langfuse_trace_id'}` ⇒ 换成 `knowledge/domain/span/SpanTracker.java`（键与理由不变）；② `file-size.baseline.json` 的 `limits` 键随路径改名（**702 → 698**）。
- **新增** `domain/span/package-info.java`（写明为何放在中立面）；`service/package-info` 与 `domain/package-info` 同步（后者补 `access/` 与 `span/` 子包说明）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m36s** ✓ **十二守卫** ✓（环 0 / 依赖 config 0 / L2→L3 0 / **包级 SCC 12 组**）。
- **登记（B263 最后一项）**：
**🔶 B263 第㉝步（2026-10-14，`service` 子面拆分的"断边第一步"）**
- **先测绘，再动手**：把 `service` 包当图算了一遍——**32 个类 / 70 条内部引用边 / 内部只有 2 个强连通簇**（9 类核心：`KnowledgeService` + `DuplicateKnowledgeException` + `KnowledgeAccessHelper` + 六个 `Knowledge*Service`；3 类 FAQ：`FaqBatchOps`/`FaqImportService`/`FaqValidateOps`）。**结论**：子面只能**整簇（含闭包）搬**，否则必然产生 `service ⇄ service.<子面>` 反向边、SCC 扩大（B263① 实测现象在此得到解释）。
- **本步断掉的是"最肥的一条反向边"**：`KnowledgeService.tenantId()`（**包内可见的静态助手，17 个调用点**）⇒ 上移为 **`domain.KnowledgeTenants.tenantId()`**（中立面，两侧都能合法依赖），门面保留**委任**；`ALL_STAGES` 的剩余门面引用（`KnowledgeSpanService` + `SpanTracker`）也改指 `domain.KnowledgeProcessingSpan.ALL_STAGES`。
- **可测量的收益**：与门面**双向**耦合的类从 **9 个降到 3 个**（`DuplicateKnowledgeException` / `KnowledgeAccessHelper` / `KnowledgeSpanService`）；**单向使用门面**的只剩 3 个（`ChunkEditService` / `KnowledgeTagService` / `QuestionGenerationService`）；**只被门面使用**的有 8 个。⇒ 子面搬迁的"闭包"因此明确：**这 3 个单向使用者必须与被搬的簇一起走**，否则环仍在。
- **过程中的两处小响**（都已修）：`KnowledgeService` 自身补 `KnowledgeTenants` import（顺带清掉不再使用的 `TenantContext`）；脚本给 `KnowledgeProcessingSpan` 加了**自 import** ⇒ 死成员守卫（D-d）当场点名 ⇒ 清掉。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m49s** ✓ **十二守卫** ✓（环 0 / **包级 SCC 12 组**不变）。
- **登记（B263 收尾的最后动作）**：
**🔶 B263 第㉞步（2026-10-14，**第一个子面 `service.faq` 整簇切出**）**
- **先纠正了 ㉝ 的测量假象（本步第一步，很重要）**：㉝ 算出"闭包 = 29/32"，是因为我把 **`package-info.java` 当成了图节点** ✗——它按 javadoc `{@link}` 引用了所有人 ⇒ 闭包被它拖爆。**排除 `package-info` 重算**：FAQ 簇闭包 = **8（就是它自己）**，`KnowledgeService` 闭包 = **18**（不是 29）。**教训（值得记住）：图分析先剔除非类型节点（`package-info`），否则结论会反。**
- **切出**：`FaqBatchOps`/`FaqChunkCodec`/`FaqEntryCommandService`/`FaqEntryQueryService`/`FaqImportService`/`FaqIndexRows`/`FaqIndexWriter`/`FaqValidateOps` **8 类 → `com.ragagent.knowledge.service.faq`**（`git mv` + 包声明 + 全仓 FQN 替换 6 文件）；测试面若有同名前缀者随迁；新建 `service/faq/package-info`（写明"为何能整簇切出"），`service/package-info` 同步（列出的 FAQ 类改为指向子面）。
- **为何这一步零风险（事先可证）**：FAQ 集**对外零依赖**（`FAQ → 外部` = 0），外部对它的**代码边也为零**（唯一命中是 `service/package-info` 的 javadoc）⇒ 切出后 `service` 与 `service.faq` 之间**不产生任何包间边** ⇒ 实测 **包级 SCC 仍 12 组、环 0** ✓。
- **换锚一处**：`file-size.baseline.json` 的 `limits` 键随 `FaqEntryCommandService` 路径改名（**783 → 772**，顺带收紧）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m43s** ✓ **十二守卫** ✓。
- **登记（`service` 其余子面）**：
**🔶 B263 第㊱步（2026-10-14，端口反转 + `ChunkQuestionService` 迁入 `service.chunk`；**并修掉新守卫自身的一个真 bug**）**
- **环边只有一个调用点**：`QuestionGenerationService` 对 `ChunkQuestionService` 的全部使用就是**一次** `generateAndStoreQuestionsForWorker(...)` ⇒ 反转成本极低：新建 **`service.ChunkQuestionPort`**（端口放在**消费者一侧**的包，只声明那一个方法），`ChunkQuestionService` 实现之（方法放开为 `public`）⇒ `service → service.chunk` 的边消失，方向固定为 `service.chunk → service` ⇒ `ChunkQuestionService` 得以迁入子面，**SCC 实测仍 12 组**（环没有形成）。
- **本步"抓到自己人"的一条**：搬迁脚本中途在"boot 测试树同名包"处 abort（`dst_dir` 计算没命中 `/service/` ⇒ `git mv X X` 报 128），主文件已搬、**包声明未改** ⇒ 此时 **`check-package-dirs.py`（㉖ 新增的守卫）却报"✓ 全绿"** ✗——根因是它用**前缀**匹配：`…/service/chunk/Foo.java` 也 `startswith("…/service/")` ✓ ⇒ 子目录错配溜过。**已修正为"父目录完全相等"**（并在脚本注释里写明这次误判的来由），修后复检仍全绿 ✓。
- **补完**：主文件包声明 + 端口 import、boot 测试随迁（`ChunkQuestionServiceTest` 实际只在 boot 树）+ 包声明、`QuestionGenerationService` 改认端口、全仓 FQN 替换、子面 `package-info` 更新（不再是"暂留"）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m42s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）。
- **登记（`service` 其余子面/收尾）**：
**🔶 B263 第㊲步（2026-10-14，拆掉门面的"残留绕环手法" ⇒ 门面包内 in-degree 归零）**
- **先复核"引用者"到底是什么**（这一步把 ㉝ 的"6 个引用者"逐个读成真）：`DuplicateKnowledgeException`/`KnowledgeAccessHelper`/`KnowledgeSpanService`/`QuestionGenerationService` 对 `KnowledgeService` 的提及**全在 javadoc 里**（无代码边）✓；只有 `KnowledgeTagService` 有一条真代码边——`ObjectProvider<KnowledgeService>` + `getIfAvailable()`（注释自称"延迟解析规避任何潜在的装配环"）。
- **实测证明它是残留**：① 它只用门面**一个**方法 `batchDeleteKnowledge`；② 门面里那方法是**纯转派** `return batchOpsService.batchDeleteKnowledge(...)`；③ **门面根本不依赖本类**（ctor 里没有它）⇒ **无环可绕**。⇒ 改为**直接依赖真正的实现者** `KnowledgeBatchOpsService`，删掉 Provider 与 null 分支（连带清 `ObjectProvider` import）。
- **可测量的结果**：`service` 包内**代码级**引用 `KnowledgeService` 的类 = **0 个**（去 javadoc/行注释后统计）⇒ 门面归位（`service.management`）不再被钉住，且它的 11 条出边全是单向（`service.management → service`），SCC 不会扩大。**登记**：R6 目前只禁 `@Lazy`，`ObjectProvider` 型绕环在仓内已有先例（B263㉕ 那处是**刻意**的装配裁剪容忍，本处是**残留**）⇒ 可考虑加一条"ObjectProvider 需自述理由"的守卫（待评估，不在本批）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫+新守卫 ✓（环 0 / 包级 SCC 12 组）。
- **下一步**：`KnowledgeService`（+ 视需要 `DuplicateKnowledgeException`）→ `service.management`（门面归位，B263 结构目标的收口）。
- **下一步**：`KnowledgeService`（+ 视需要 `DuplicateKnowledgeException`）→ `service.management`（门面归位，B263 结构目标的收口）。

**🔶 B263 第㊳步（2026-10-14，**门面归位 `service.management`** ⇒ B263 结构收口）**
- **动前断言**：脚本先断言 `service` 包内**代码级**（去 javadoc/行注释）**无一处**引用 `KnowledgeService` ——不过即 abort。断言通过 ⇒ 搬迁只会产生 `service.management → service` 与 `service.{chunk,faq} → service.management` 这类**单向边**。
- **搬迁**：`KnowledgeService` → `com.ragagent.knowledge.service.management`（`git mv` + 包声明 + **全仓 FQN 替换 23 文件**）；javadoc 简单名失链修复 1 处（`KnowledgeSpanService` 的 `{@link KnowledgeService}` ⇒ 全限定）；新建 `management/package-info`（写明"为何现在能搬"）；`service/package-info` 子面清单补 `management/`。
- **跨模块复核（避免误伤同名类型）**：`engine`/`common`/`boot` 里出现的 `KnowledgeService` **都不是**本门面——`chatpipeline.PipelinePorts.KnowledgeService` 是**同名嵌套接口**、另有纯 javadoc 提及 ⇒ FQN 替换用精确模式（`com.ragagent.knowledge.service.KnowledgeService`）因而**零误伤** ✓。
- **两处换锚**：`file-size.baseline.json` 的 `limits`（**796 → 717**，换锚 + 收紧）、`check-json-key-case.py` 的键级豁免 `{'updated_at'}`（路径换锚，键与理由不变）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m41s** ✓ **十二守卫** ✓ —— **包级 SCC 仍 12 组**（门面归位未造环，㉝/㊲ 的断边工作在此兑现）。
- **B263 结构目标至此收口**：mapper 直连归零 · `{service, task}` SCC 消除 · 门面归位（`service.management`）· 两个子面（`service.faq` 8 类 / `service.chunk` 4 类）已切出。
- **登记（后续可选，非 B263 必需）**：
**🔶 B263 第㊴步（2026-10-14，`service.query` 切出 + `service.management` 增补；**对齐 §8.8.2 命名**）**
- **先做目标树体检（重要发现）**：§8.8.2 的目标树**大部分已实现**——`dto.{chunk(13),doc(17),faq(22),kb(16),tag(8)}` 与顶层 `dto/`（3 类）**与计划精确吻合** ✓；`repository/mapper/task/storage/controller/chunker/config/client` 均已对齐。**但 `domain.{document,task,graph}` 的 12 个类型（`KnowledgeDocument`/`ProcessingTask`/`ExtractPrompts`…）在仓库里根本不存在** ⇒ 那是"待创建"的目标，属**重构**而非包重排 ⇒ **本批不做**（登记）。
- **本步两簇（都按纪律先证后搬）**：
  - `service.query/`（§8.8.2 命名）：`KnowledgeSearchService`、`KnowledgeSpanService`、`KnowledgeSpanAdapter` —— 三者在 `service` 顶层**代码级入度为零** ✓。
  - `service.management/` 增补：`KnowledgeBaseLookupAdapter`、`KnowledgeTagService`、`KnowledgeBatchOpsService`、`KnowledgeMoveService`、`KnowledgeCloneService` —— 同样零入度 ✓（门面已在其中）。
- **断言升级（本步抓到的东西）**：首版断言把"**同面成员之间**的引用"也算违规 ✗（`KnowledgeTagService → KnowledgeBatchOpsService` 被误报）⇒ 改为**同面放行**；同时**先测跨面边**：`query → management` 零 ✓、`management → query` **仅门面**（→`KnowledgeSearchService`/`KnowledgeSpanService`）✓ ⇒ 单向链 `service.management → service.query → service`，无环 ✓（实测 SCC 仍 12 组）。
- **顺带修 3 处跨包可见性**：搬出顶层包后，`KnowledgeFileService.updateKnowledgeRow`、`KnowledgeBaseService.ensureDefaults`、`KnowledgeParseService.resetKnowledgeForReparse` 变成"跨包调用包内方法" ✗ ⇒ 提为 `public` ✓（它们本就是跨面协作者）。为此写了一个**按编译错误自动提可见性**的小工具（正则首版漏了**返回类型** ✗ ⇒ 补上后一次改净）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m56s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）。
- **登记（§8.8.2 剩余）**：`service.enrichment/`（`KnowledgeSummaryService`/`QuestionGenerationService` 可并入——本仓 FAQ 已单列 `service.faq`，属**有意偏离**）、`service.process/`（本仓等价物是 `service.chunk`）、`service.index/ingest/`（目标类型不存在）；`domain.{document,task,graph}`（需先造类型）。`service` 其余子面继续细分（`KnowledgeTagService` 可作 `service.tag`；`DuplicateKnowledgeException`/`KnowledgeAccessHelper`/`KnowledgeSpanService` 与 11 个协作者面）；"ObjectProvider 需自述理由"守卫（㊲ 登记）。`service.chunk` 现 4 类；`KnowledgeTagService`（零入度，可作 `service.tag`，单类面偏薄）；**门面归位的最后一关**是 `KnowledgeService` 的 6 个引用者。
**🔶 B263 第㊵步（2026-10-14，`service.enrichment` 切出；**对齐 §8.8.2 命名**）**
- **动前断言（把"可测"变成"可证"）**：脚本先扫全仓 FQN 引用面并与白名单比对——`KnowledgeSummaryService` 2 处（门面 import + ArchUnit 基线）、`QuestionGenerationService` 3 处（自身 + `ChunkExtractService` 的 javadoc FQN + 一处测试 import）⇒ 断言通过才搬。其中 **QGS 在主代码里引用为零**（队列/装配侧早由 B263㉛ 端口反转、㊱ 又断掉它对 `ChunkQuestionService` 的边）⇒ 这是"零入度可切"的教科书样本。
- **切出**：2 类 → `com.ragagent.knowledge.service.enrichment`（`git mv` + 包声明 + **补 import**：SummaryService 需补 `KnowledgeAccessHelper`/`KnowledgeFileService`（原同包免 import ⇒ 搬包后编译才会暴露）、QGS 补 `ChunkQuestionPort`）；新建 `service/enrichment/package-info.java`（写明切出依据与新边方向：`management → enrichment → service`，单向）。
- **引用者 3 处更新**：门面 `management.KnowledgeService` 的 import、`RedisKnowledgeTaskQueueTest`（domains 测试树）的 import、`ChunkExtractService` javadoc 的 **FQN `{@link ...}`**（㉟ 教训：简单名 `{@link}` 会失链，FQN 形式要跟着换）。
- **守卫换锚（照㉟/㊳ 清单逐项核）**：A16/A17 基线**各一条 FQN 换名**（`...service.KnowledgeSummaryService` → `...service.enrichment.…`；QGS 同——两者仍违约，是换名不是删除）；`file-size.baseline.json` 键换锚 + **收紧 684 → 663**（不用 `--write`：它会连别处的漂移一起吞）；`check-json-key-case.py` 键级豁免路径换锚（`knowledge/service/enrichment/QuestionGenerationService.java`）；`:domains:javadoc` ✓ 无失链；`check-package-dirs.py` ✓（目录与包一致）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓（domains 2949 / engine 561 / common 141 / boot 1329）；启动测试 ✓；`spotlessCheck build` ✓ **1m47s** ✓ **十二守卫** ✓（环 0 / 包级 SCC **12 组不变**）。
- **登记（§8.8.2 剩余）**：`service.management/` 还差 `KnowledgeAccessHelper`/`KnowledgeFolderService`（二在顶层**仍有多个引用者** ⇒ 需先断边，同㉝/㊲ 的做法）；`KnowledgeBaseService`/`KnowledgeFileService`/`KnowledgeParseService` 未切面——§8.8.2 给它们的是 use-case 目标名（`ManageKnowledgeBase` 等）⇒ 属**改名级重构**，不在"包重排"范围内；`HousekeepingService`/`EmbeddingModelAdapter` 按目标树**本就留在顶层** ✓。

**🔶 B263 第㊶步（2026-10-14，`enrichment` 自足化：断掉对顶层 `KnowledgeFileService` 的边）**
- **为什么要先做这一步（㊵ 之后的动前测量结论）**：类级跨包边实测显示 `service` 顶层与子面已构成**互递归结**——`management → 顶层`（门面 → `AccessHelper`/`FileService`/`FolderService`/`ParseService`/`DuplicateKnowledgeException`；`KnowledgeCloneService`/`KnowledgeTagService` → `KnowledgeBaseService`；`BatchOps` → 3 个）与 `enrichment → 顶层`（`SummaryService` → `AccessHelper`/`FileService`、`QGS` → `ChunkQuestionPort`）并存 ⇒ **任何单类搬进子面都会造出双向边（新环）✗**（㉝ 记的"29 类互递归结"复现）⇒ 余步按"**先断边、后搬迁**"推进。
- **本步断的边**：`SummaryService` 调用 `KnowledgeFileService.updateSummaryColumns(...)` 的 **7 处**——该方法体内逐字就是 `knowledgeRepository.updateSummaryColumns(k)`（纯薄委托，已现场比对）⇒ 7 处改**直连仓储**（`SummaryService` 本就注入 `knowledgeRepository` ✓），依赖四件套（字段/构造参数/赋值/import）全删。
- **连带（死成员清理）**：该委托的**唯一**调用者就是这 7 处（全仓 grep 确认）⇒ 方法体 + javadoc 删除；那条**值钱的"窄写入"警告**（❌ 勿走全列写，否则把 `finalizing/completed` 打回 `processing`；用户报障 `05.03-问题发布.md`）**搬到** `KnowledgeRepository.updateSummaryColumns` 的 javadoc（`{@link #updateRow}` 指真身）——**改动删代码但不删知识**。
- **测量教训（新，重要）**：早先跨包边扫描用了 `^import\s+FQN;$` 正则 ⇒ **CRLF 文件全部漏判**（`$` 匹配不到 `\r` 前）✗，得出"`management → 顶层` 为零"的**反向错误结论**；改**子串判据**（`'import '+FQN+';' in text`）后结论翻转 ⇒ **结构性判断前先验证扫描法本身**（与 B263㉖"守卫自身也要能被证伪"同源）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m53s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组不变）；`:domains:javadoc` ✓。
- **登记（余步）**：`enrichment → 顶层` 仍剩 `SummaryService → KnowledgeAccessHelper`（2 处 `requireKb`，内含 API Key 作用域校验 `TenantAPIKeyScope.authorizeKnowledgeBases` ⇒ **不宜复制实现**，需端口/归属决策）与 `QGS → ChunkQuestionPort`（端口，可随 chunk 面归位）；`management` 侧的 §8.8.2 增补（`KnowledgeAccessHelper`/`KnowledgeFolderService` + `KnowledgeBaseService`/`FileService`/`ParseService`）待上述边断净后**协调一次搬**。

**🔶 B263 第㊷步（2026-10-14，`ChunkQuestionPort` 落位 `domain`——**环守卫当场抓到一次"放错面"**）**
- **本步目标**：收掉 ㊵ 后 `enrichment → 顶层` 的两条边（端口 + `AccessHelper`）。端口先按"随实现走"迁入 `service.chunk`——**编译全绿、ArchUnit 全绿，但环守卫当场红**：新增 `{chunk, enrichment, management}` **三元环**（`enrichment → chunk`（消费端口）+ 既有 `chunk → management`（`ChunkEditService` → 门面）+ `management → enrichment`（门面 → `KnowledgeSummaryService`））⇒ SCC 12 → **13 组** ✗。**"结构判断不能只靠编译 + ArchUnit"的又一现场证据**（环守卫是唯一权威读数）。
- **定案**：端口改放**中立 `domain`**（`domain.ChunkQuestionPort`）——理由写进 javadoc：① 它的签名本就**只用 `domain` 类型**（`Chunk`/`Knowledge`/`KnowledgeBase`）；② 放实现方（chunk）会造上面那个三元环，放消费方（enrichment）则实现方反向依赖消费方；③ 中立面让两侧都"向下"依赖它，服务子面图保持 `chunk → management → enrichment` **无环链**。落位后 SCC **回到 12 组** ✓。端口与 ㉛ 的两个消费者端口（`domain.{ChunkExtract,QuestionGeneration}Consumer`）同理同构。
- **配套**：实现方 import → domain（同面 import 删）；消费方 import → domain；`chunk/` + `enrichment/` 两处 package-info 措辞回正；端口无 mapper 依赖（不触 A16/A17）、无 json 键 / `FROZEN_PREFIXES` / file-size 锚点。
- **`AccessHelper` 决策（本步登记，暂不搬）**：`KnowledgeAccessHelper.requireKb` 与 `domain.access.ChunkAccessGuard.requireKbAccess` **语义不等价**（前者＝租户内查、缺 → 404 `BizException`；后者＝无租户查 + 显式租户比对，跨租户 → **403**）⇒ **不能换用**（会改行为 ✗）；且 `SummaryService`（enrichment）用它 2 处、门面（management）用它多处 ⇒ 搬进 `service.management` 会与"门面 → `KnowledgeSummaryService`"组成 `enrichment ⇄ management` 环 ⇒ **暂留顶层**（§8.8.2 的**有意偏离**，理由=环 + 语义归属需单独设计）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m56s** ✓ **十二守卫** ✓（环 0 / 包级 SCC **12 组**）；`:domains:javadoc` ✓。
- **登记（余步）**：协调搬迁 §8.8.2 的 `KnowledgeBaseService`/`KnowledgeFileService`/`KnowledgeParseService`/`KnowledgeFolderService` → `service.management`。动前实测：顶层残余（`AccessHelper`/`DuplicateKnowledgeException`/`EmbeddingModelAdapter`/`HousekeepingService`）**均不引用**这 4 类 ✓；**`ParseService` 必须与 `FileService`/`FolderService` 同批搬**——它既引用二者、又被门面/`BatchOps` 引用，单搬会造 `management → TOP → management` ✗。

**🔶 B263 第㊸步（2026-10-14，**协调搬迁 4 类 → `service.management`**；顶层只剩访问原语与支撑）**
- **为什么必须"协调"搬**：顶层与子面已互递归（㊶ 实测）——`management → TOP`（门面/`BatchOps`/`Clone`/`Tag` → 4 类）与"4 类 ↔ 顶层残余"并存 ⇒ 单类搬必造 `management → TOP → management` 环；其中 **`KnowledgeParseService` 必须与 `KnowledgeFileService`/`KnowledgeFolderService` 同批**（它既引用后二者、又被门面/`BatchOps` 引用）。
- **动前断言（三查）**：① 全仓 FQN 引用面实测（KBase **19** / File 4 / Parse 3 / Folder 1 个文件）；② 顶层残余（`AccessHelper`/`DuplicateKnowledgeException`/`EmbeddingModelAdapter`/`HousekeepingService`）**均不引用**这 4 类 ✓（否则就会造反向边）；③ 4 类对子面零依赖 ✓。
- **搬迁**：4 类 `git mv` → `com.ragagent.knowledge.service.management`；包声明 + 补 `KnowledgeAccessHelper` import（原同包免 import 的三处）；`management` 内 4 个引用者**删同包 import**；其它 **19 个文件**（`datasource`/`session`/`websearch`/`evaluation`/`initialization`/`agentmanagement` + 5 个测试）FQN 替换。
- **新坑（"同名包"第 3 次，形态再变）**：这次是**同模块测试树的同名包**——`domains/src/test/.../knowledge/service/` 下两个测试用**裸名**引用被搬类 ⇒ 编译才暴露 ✗（㉟ 是 boot 测试树、㊱ 是"改包未搬目录"）⇒ 一并**测试随迁**到 mirror 包 `service/management/`；顺带解决 `KnowledgeFileService.afterCommit`（**包内静态测试缝**）的可见性——**不放宽可见性，让测试跟着类走**。
- **换锚**：A16 两条 FQN（File/Parse，由通用替换同步完成）· `file-size.baseline.json` 键换锚 + 收紧（**626 → 565**）· `check-json-key-case.py` 键级豁免路径 · 两份 `package-info` 重写（顶层残部地图 + `management` 成员与"为什么能搬"）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓（domains 仍 2949——测试**随迁**非删除）；启动测试 ✓；`spotlessCheck build` ✓ **1m46s** ✓ **十二守卫** ✓（环 0 / 包级 SCC **12 组**）；`:domains:javadoc` ✓；定向测试 `--rerun-tasks` ✓。
- **顶层现状**：只剩 `KnowledgeAccessHelper`（㊷ 登记的环原因）+ `HousekeepingService`/`EmbeddingModelAdapter`（§8.8.2 本就留顶层）+ `DuplicateKnowledgeException`（⑦ 实测放 domain 会造环）⇒ **§8.8.2 的 service 面基本到位**。

**✅ B263 收口（2026-10-14，①~㊸）**
- **结构目标全部兑现**：mapper 直连归零 ✓ · `{service,task}` 包级 SCC 消除 ✓ · 门面归位 `service.management` ✓ · 五个子面 `{faq(8),chunk(4),query(3),management(10),enrichment(2)}` 切出 ✓ · 顶层只剩 `KnowledgeAccessHelper`（㊷ 登记）+ `HousekeepingService`/`EmbeddingModelAdapter`（§8.8.2 本就留顶层）+ `DuplicateKnowledgeException`（⑦ 实测放 `domain` 会造环）⇒ **§8.8.2 的包重排面到位**。
- **剩余三项（登记为"重构级"、非包重排，留待独立立项/后续批次）**：① `KnowledgeAccessHelper` 归属（搬 `management` 会造 `enrichment ⇄ management` 环；`requireKb` 与 `domain.access` 守卫**语义不等价** ⇒ 不能换用）；② `domain.{document,task,graph}` 需先造类型（12 个类型仓库里不存在）；③ §8.8.2 的 use-case 目标名（`ManageKnowledgeBase`/`SummarizeKnowledge`/`ProcessKnowledgeUseCase` …）属**改名/重构**级。
- **本批留下的通用资产**：环守卫"唯一权威读数"的两次现场（㊷ 三元环、㊴ 断言升级）；"换包连带清单"四件套（javadoc `{@link}` 失链 · `FROZEN_PREFIXES`/键级豁免按路径 · 别的测试树同名包 · file-size 键换锚）；**CRLF 会让 `^import...$` 正则全漏判 ⇒ 一律用子串判据**；"同名包"三形态（boot 测试树 / 改包未搬目录 / **同模块测试树**）。

**🔶 B264 第①步（2026-10-14，agentmanagement 落位：`service.config` + `service.builtin`）**
- **按 §8.8.3 切子面**：`AgentConfigJson`/`AgentPlaceholders`/`AgentTypePresets` → `service.config`；`BuiltinAgentRegistry` → `service.builtin`。
- **动前断言第一次"自己漏边"**：脚本只按 **import** 查反向边 ⇒ 断言通过；**编译当场揭穿**——`AgentSuggestedQuestions` 以**裸名**（同包免 import）反向持有 `CustomAgentService` ✗ ⇒ 搬进 `config` 会造 `service ⇄ service.config` 环（**同包裸名引用对 import 判据不可见**，本仓 SCC 守卫同样基于 import/类型边 ⇒ **编译是最硬的判据**；"同名包/裸名"家族第 4 形态）。
- **定案**：`AgentSuggestedQuestions` **退回 `service`**（它本质是**用例**——推荐问题的 KB scope 解析要读 agent 定义）+ 其测试随迁回；其余 4 类落位 ✓（重跑动前断言：对 `service` 零反向边）。`config/package-info` 写明"为什么它没进来"。
- **连带**：`CustomAgentService`/`BuiltinAgentRegistry`/`AgentSuggestedQuestions` 补 `service.config`/`service.builtin` import（原同包免 import）；全仓 FQN 替换 **10 文件**（含 ArchUnit 两处基线 FQN、`ClasspathResourcesTest`、以及 `AgentConfigKeyUsageTest` 的**豁免路径锚** `agentmanagement/service/AgentPlaceholders` → `…/service/config/AgentPlaceholders`）；三份 package-info（2 新建 + `service` 更新）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m48s** ✓ **十二守卫** ✓（环 0 / 包级 SCC **12 组**不变）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（B264 余步）**：① **B257 基线清零**——勘察发现三处里 `AgentSuggestedQuestions` 的条目**已过期**（它现只 import `knowledge.domain.KnowledgeBase`，规则只禁 `service`/`mapper` 面 ⇒ 应**删条目**），另两处是真债（`SkillCatalogController` → `audit.service.AuditLogService`；`CustomAgentService` → `knowledge.service.management.KnowledgeBaseService` + `auth.service.UserService`）；② agenttools `data/` → `datasource/` 改名（§8.8.3）；③ session 21 包重构（§8.8.1：`domain.{conversation,document,message}`、`service.{attachment,enrichment,history,rag,rag.stage,suggestion,turn}`、`controller.sse`、`event`、`task`）。

**🔶 B264 第②步（2026-10-14，B257 债 -1：KB 目录端口 + 删过期条目）**
- **B257 三条基线逐条复核（勘察）**：`AgentSuggestedQuestions` 的条目**已过期**——它现只 import `knowledge.domain.KnowledgeBase`，而规则只禁 `service`/`mapper` 面 ⇒ 应删；`SkillCatalogController → audit.service`、`CustomAgentService → {knowledge.service.management, auth.service}` 是**真债**。
- **本步修「`CustomAgentService → KnowledgeBaseService`」这一半**：按本仓既定跨域端口模式（`common/<域>/XxxPort` + 该域 `api/` 适配器；先例 `ChunkPort`/`KnowledgeFinalizePort`）新建 `common.knowledge.KnowledgeBaseCatalogPort`（JDK 视图 `record KbCapability(id, vector, keyword)`）+ `knowledge/api/KnowledgeBaseCatalogAdapter`（委托 `KnowledgeBaseService.listKnowledgeBases(creator)`，**保留 `ensureDefaults` 能力回填**——2026-09-25 线上抓回那条语义，逐字不动）。
- **调用方**：`AgentSuggestedQuestions` 的 `agents.kbService().listKnowledgeBases(null)` → `kbCatalog.listActiveCapabilities(null)`（只读 id + vector/keyword，投影后语义等价）；`CustomAgentService` 去掉 `KnowledgeBaseService` 字段/构造参数 + 包内访问面 `kbService()`（唯一使用者已改端口）。
- **删条目的坑**：A16 与 B257 两处基线的同名条目**文本与缩进完全相同** ⇒ 直接字符串替换会删错（脚本首次就断言到 2 处）⇒ 改为按"`AGENT_ADAPTER_DOMAIN_SERVICE_BASELINE` 声明之后"定位；**A16 条目保留**（它仍依赖 `AgentQuestionMapper`）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓（首轮一次 `EmbedRateLimiterTest` Redis 型偶发，按约定重跑即绿）；启动测试 ✓（新增 `@Component` 装配正常）；`spotlessCheck build` ✓ **1m56s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组不变）；`:domains:javadoc` ✓。
- **登记（B264 余下）**：B257 还剩两条真债（`SkillCatalogController → audit.service.AuditLogService.logBestEffort`；`CustomAgentService → auth.service.UserService.getUsersByIds`），同走"common 端口 + 域内 api 适配器"；其后 agenttools `data→datasource`、session §8.8.1。

**🔶 B264 第③步（2026-10-14，B257 基线**清零**：audit + auth 两端口）**
- **两条真债都改走"`common/<域>/XxxPort` + 该域 `api/` 适配器"**：
  - `SkillCatalogController → audit.service.AuditLogService.logBestEffort` ⇒ **`common.audit.AuditWritePort`**（JDK 记录 `AuditRecord(tenantId, actorUserId, actorRole, action, targetType, targetId, targetUserId, outcome, detailsJson)`）+ **`audit/api/AuditWriteAdapter`**（记录 → `AuditLog` 实体 → `logBestEffort`；详情 JSON 串解析回 `JsonNode`，解析失败按"无详情"——"尽力而为"语义保持）。
  - `CustomAgentService → auth.service.UserService.getUsersByIds` ⇒ **`common.auth.UserLookupPort`**（`record UserView(id, username, email)`）+ **`auth/api/UserLookupAdapter`**（委托 `getUsersByIds`，只投影展示字段；调用方 try/catch 与 username→email 回退**逐字保留**）。
- **基线清零**：`AGENT_ADAPTER_DOMAIN_SERVICE_BASELINE` → **`Set.of()`**（**零容忍**，javadoc 记录清零路径）——规则首跑即绿 ⇒ `agenttools`/`agentmanagement` 现在**零**直连业务域 service/mapper ✓。
- **语义要点**：`AuditAction`/`AuditOutcome` 是 **String 常量**（列允许任意值、刻意非 enum）⇒ 端口可 JDK-only；调用方仍依赖 `audit.domain` 常量（规则只禁 service/mapper ✓）。
- **踩坑**：三处新目录（`common/auth`、`audit/api`、`auth/api`）不存在 ⇒ 脚本 `open('w')` 不建目录 ✗ ⇒ 先 `mkdir -p`；死成员守卫现场抓到 `CustomAgentService` 残留的 `import com.ragagent.auth.domain.User;`。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 **26s 实跑**（两个新 `@Component` 装配正常）；`spotlessCheck build` ✓ **3m15s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓。
- **登记（B264 余下）**：agenttools `data/` → `datasource/`（§8.8.3）；session §8.8.1 大重构（21 包）。

**🔶 B264 第④步（2026-10-14，agenttools `data/` → `datasource/` 改名；§8.8.3 差集盘点）**
- **改名**：`agenttools.data` → `agenttools.datasource`（5 类 + package-info）——类集与 §8.8.3 的 `datasource/` 目标**逐名一致** ⇒ **纯改名**（无搬迁、无新增边）；测试树 `domains/src/test/.../agenttools/data/DataAnalysisRecordingTest.java` **随迁** mirror 包（"同名包"老办法）；两处空目录删除（`check-stray-dirs` 扫"游离/死包目录"）。
- **连带**：FQN 替换 4 文件（`session/service/QaWiring`、`session/service/AgentToolBackends`、`ArchitectureRulesTest` 的 `Map.entry("…agenttools.data.AnalysisDuckDbJdbc", "非业务库引擎（DuckDB）")` 白名单键、测试自身）；`datasource/package-info` 写明改名缘由。
- **锚点复核**：`scripts/*` 无 `agenttools/data` 路径锚 ✓；file-size 的 agenttools 条目在 `knowledge/`/`web/`（未动）✓；json 键豁免在 `agenttools/sql/`（未动）✓。
- **§8.8.3 差集盘点（本步顺带做完）**：`datasource` 5/5 ✓ · `sql` 5/5 ✓ · `web` 2/2 ✓ · `wiki` 30/30 ✓ —— **四面已完全对齐目标树**；`knowledge` 10/12：目标待创建 3 个（`KnowledgeToolFormatter` / `ReadKnowledgeTool` / `SearchArguments`），现有 `KnowledgeSearchRanking` 不在目标树（B261 已登记：归位需先做"工具状态外提"重构）⇒ **属重构级**，登记不硬搬。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓（首轮一次 `ImRedisStoreTest` Redis 型偶发，按约定重跑即绿）；启动测试 ✓；`spotlessCheck build` ✓ **1m39s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（B264 余下）**：session §8.8.1 大重构（21 包 / 120 文件）。

**🔶 B264 第⑤步（2026-10-14，session 起步：`sse/` → `controller.sse/`）**
- **首个最小簇**：`session.sse`（`SseContract` / `SseFrameWriter` / `StreamEventEmitter` / `StreamResponseBuilder`）→ `session.controller.sse`——类集与 §8.8.1 的 `controller.sse/` 目标**逐名一致** ✓；动前断言：四类对 session 域内包**零依赖**（出边只到 `common`/`llm`/`storage`/`platform`）⇒ 只产生 `controller → controller.sse`、`qa → controller.sse` 单向边。
- **两树测试随迁**：`domains/src/test/.../session/sse/StreamResponseBuilderTest` + `boot/src/test/.../session/sse/SseFrameWriterTest` → `session/controller/sse/`（"同名包"两处一起处理）；三处空目录删除。
- **锚点**：`scripts/check-event-face-case.py` 的 **`EVENT_MAP_FILES` 路径锚**（`session/sse/StreamResponseBuilder.java`，SSE 帧键位覆盖清单）换新路径——**这类"按路径的覆盖清单"漏换就静默丢覆盖**（与 `FROZEN_PREFIXES` 同族）。
- **新增** `controller/sse/package-info`（写明迁入依据）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m38s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓。
- **登记（session 余下，按零风险优先）**：① `domain/`(36) 拆 `domain.conversation`(8) / `domain.document`(1) / `domain.message`(15)；② `qa/`(10) 拆入 `service.turn` / `service.rag`；③ `service/`(37) 拆入 `service.{attachment,enrichment,history,rag,rag.stage,suggestion,turn}`；④ 新增 `api/`(4 适配器) / `event/`(2) / `task/`(1)。

**🔶 B264 第⑥步（2026-10-14，`session/domain` 拆三簇：conversation(8) / document(1) / message(16)）**
- **映射**：`domain.conversation` = Session 族 8；`domain.document` = `TemporaryDocument` 1；`domain.message` = Message 族 15 **+ `ArtifactVersions`**；留 `domain` 11（`AbstractJsonListTypeHandler` 与三个 `*ListTypeHandler`、`MentionedItem`/`SuggestionItem`/`UsedMemory` 族、`ChatHistoryKbStats`）。
- **本步最值钱的一刀（动前图分析）**：裸名引用图测出**唯一反向边** `ArtifactVersions → MessageArtifact` ⇒ 照搬会造 `domain ⇄ domain.message` **双环** ✗；让 `ArtifactVersions` 随 message 族走（实测其外部引用者仅 `session/service/MessageService` + 同名包测试）⇒ 簇间边成为 `conversation → domain`、`message → domain` **单向** ✓（SCC 仍 12 组）。
- **搬迁**：25 文件 `git mv` + 包声明；**智能补 import**（原同包免 import）；FQN 替换 **75 文件**（session 域内 43 + 跨域 `im`/`chatpipeline`/测试/fixtures）；测试 `ArtifactVersionsTest` 随迁 `domain/message/`；**新建 4 份 package-info**（三新簇 + 原本缺失的 `session.domain`）。
- **两处现场纠错**：① 首版"补 import"把 body 截断在**第一个 `{`** ⇒ 类声明头部（`extends AbstractJsonListTypeHandler`）被切掉、三个 `*ListTypeHandler` 漏补 ✗ ⇒ 改整文扫描（并先清"同包自 import"）；② `:domains:javadoc` 报 2 处 `{@link Session}` 失链（㉟ 同款）⇒ 按仓内风格改**全限定**（不加"javadoc 专用"import，避免死成员守卫判未使用）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m43s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓。
- **登记（session 余下）**：`qa/`(10) 拆入 `service.turn`/`service.rag`；`service/`(37) 拆入 `service.{attachment,enrichment,history,rag,rag.stage,suggestion,turn}`；新增 `api/`(4)/`event/`(2)/`task/`(1)；顶层 1 文件。

**🔶 注释风格（用户反馈，2026-10-14）：代码注释不带过程编号**
- **背景**：B232 起各批在 javadoc / 行内注释里留了 `B2xx㊸` 这类**批次与步骤编号**（如「（B263⑧：由 service 直连归位）」）。用户指出**新人看不懂**——这些编号只对参与过整改的人有意义，还会随批次表过期。
- **处置（本步）**：只扫**注释段**（字符串字面量与 `@DisplayName` 一律不动）去掉编号、**保留事实与理由**：`（B263⑧：与 X 逐字等价…）`→`（与 X 逐字等价…）`；`**B264④ 改名**：`→`**改名**：`；`「B260」零容忍守卫`→`零容忍守卫`；`B261（§8.4）：…`→`…`。共 **145 处 / 85 文件**。
- **有意保留**：ArchUnit 的**守卫标签字符串**（`@DisplayName("B257：…")` 与 `ratchet("B257 适配包→域 service/mapper", …)`）——它们是守卫输出里指代规则的"规则名"，改了会让复现口径与历史记录对不上；`HANDOFF.md` 与记忆仍属过程文档，照旧带批次号。
- **约定（此后）**：**代码注释不写批次/步骤号**；要留痕写进提交信息与 HANDOFF；注释只写"是什么 / 为什么 / 坑在哪"。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **1m38s** ✓ **十二守卫** ✓；`:domains:javadoc` ✓（失链 0）。

**🔶 B264 第⑦步（2026-10-14，session `service.suggestion` 切出；其余候选簇实测不可切）**
- **动前图分析（本步主要产出，结论改变后续走法）**：
  - `service.suggestion` = {`MessageSuggestionPipeline`, `MessageSuggestionService`} 是 session 内**唯一双向边为零**的簇：入边只有 `QaTurnFinalizer`（回合收尾），出边只有 `MessageService` ⇒ 切出后 `turn → suggestion → service` 单向，无环 ✓。
  - 其余候选簇（§8.8.1 的 `service.attachment` / `service.history` / `service.turn`）实测**互有引用**：`attachment ⇄ turn`（`QaTurnExecutor → QaAttachmentResolver` vs `QaAttachmentResolver → QaTurnFinalizer/QaTurnSupport/QaSupport`）、`history ⇄ turn`（`SessionAgentQaService → …` vs `AgentConfigAssembler → QaAgentConfig/QaSupport`）、`turn ⇄ service`（`SessionKnowledgeQaService → turn` vs `turn → MessageService/SessionService/SessionLookupScope`）⇒ **单独切必造环** ✗ ⇒ 需先做**重构级断边**，不在"包重排"范围（与 B263① 同型结论：提前切面 = 环扩大）。
  - 另：§8.8.1 的 service 子面**多数类名今日并不存在**（`ChatManage` / `Plugin*` / `SubmitTurnUseCase` / `TurnExecutionCoordinator` …）⇒ 那一层是**重构**（把 rag pipeline 与 turn 用例落位），不是重排。
- **切出**：2 类 → `com.ragagent.session.service.suggestion`；测试 `MessageSuggestionGenerateTest`（用嵌套类型 `GenerationContext`/`Evidence`）随迁 mirror 包；补 `MessageService` import（原同包免 import）。
- **换锚**：`check-json-key-case.py` 键级豁免路径（`{'question_count'}`）；`file-size.baseline.json` 键换锚（**601 → 601**，路径变、体量不变）；A17 基线 FQN 换名（由通用替换同步）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m49s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（session 余下）**：`service.attachment` / `service.history` / `service.turn`（及 `service.rag` / `rag.stage` / `enrichment` 的目标类）**需先做断边重构**；`api/`(4) / `event/`(2) / `task/`(1) 同为**待创建**。

**✅ B264 收口（2026-10-14，①~⑦）**
- **落位成果**：`agentmanagement`→`service.{config(3),builtin(1)}`；**B257 基线清零**（audit/auth/knowledge 三处债改走 `common/<域>/XxxPort` + 域内 `api/` 适配器，`AGENT_ADAPTER_DOMAIN_SERVICE_BASELINE` = `Set.of()`）；`agenttools` 四面（`datasource`/`sql`/`web`/`wiki`）**与 §8.8.3 目标树逐名对齐**；session 切出 `controller.sse`(4)、`domain.{conversation(8),document(1),message(16)}`、`service.suggestion`(2)，测试全部随迁。
- **登记为"重构级"（非包重排）**：① session `service.{attachment,history,turn,rag,rag.stage,enrichment}`——实测与 `service` 及彼此**互有引用**（`attachment ⇄ turn`、`history ⇄ turn`、`turn ⇄ service`）⇒ 需先做定向断边/端口化；② §8.8.1 这些子面的**多数类名今日不存在**（`ChatManage`/`Plugin*`/`SubmitTurnUseCase`…）⇒ 属"把 rag pipeline 与 turn 用例落位"的重构；③ `session.{api,event,task}` 与 §8.8.3 的 `agenttools.knowledge` 待创建类（`KnowledgeToolFormatter`/`ReadKnowledgeTool`/`SearchArguments`）；④ `agenttools.knowledge.KnowledgeSearchRanking` 归位需先做"工具状态外提"。
- **本批留下的通用资产**：跨域端口模式定型（`common/<域>/XxxPort` + 域内 `api/` 适配器，含 JDK 视图记录写法）；"同名包"四形态清单（boot 测试树 / 改包未搬目录 / **同模块测试树** / **裸名反向边**——后者 import 判据不可见，**编译是终审**）；换锚清单再添两项（**按路径的覆盖清单**如 `EVENT_MAP_FILES`、**基线条目在多个基线里文本相同**需按声明定位）。

**🔶 B265 第⑧步（2026-10-14，B265 起步：mcp `tools/` → `tool/`；三域差集盘点）**
- **B265 = ③：wiki（17 包 147）+ datasource（16 包 124）+ mcp（9 包 125）**。本步先做**三域 vs 目标树差集盘点**，再切第一刀。
- **盘点结论**：
  - **mcp 几乎已对齐**：`controller`(6/6) / `domain`(20/20) / `mapper`(8/8) / `oauth`(33/33) / `protocol`(32/32) **逐名一致** ✓；差异三处——`tools/`→目标 `tool/`（本步改名）、`service.McpServiceService` vs 目标 `McpServiceManagement`（改名，重构级）、两个 DTO 名（`McpCredentialsResponse`/`McpCredentialFieldMetadata` vs 目标泛名——**有意偏离**：域前缀是防跨域同名混淆的既定决定）。
  - **wiki 缺两处拆簇（算术精确闭合）**：`domain/`(38) → `domain`(11) + `domain.folder`(8) + `domain.page`(13) + `domain.index`(3) + `domain.issue`(2) + `domain.graph`(1)；`service/ingest/`(57) → `service.ingest`(12) + `service.ingest.pipeline`(21) + `service.ingest.task`(22) + `service.ingest.graph`(1)。其余面（`controller` 8/8、`service/page` 21/21、`mapper` 6/6、`prompt` 2/2、`repository` 4/4、`service` 11/11）**已对齐** ✓。
  - **datasource**：本步差集脚本对该段边界取错（把 §8.9 后续域一并算了）⇒ **下次按 §8.9 段精确边界重测**（登记）。
- **本步改名**：`mcp.tools` → **`mcp.tool`**（10 类逐名一致 ⇒ 纯改名）；`file-size` 键换锚（**725 → 725**）；新面 package-info 记录成员与方案 §8.9 的对应。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m45s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（B265 余下）**：wiki 两处拆簇（`domain` 五簇、`service/ingest` 四簇，各自先做动前图分析；后者含 task/队列/锁，注意与 Redis 面）；datasource 差集重测后按面推进；mcp 两项改名留给命名批。

**🔶 B265 第⑨步（2026-10-14，wiki `domain/` 拆五簇：folder 8 / page 14（含 `WikiStats`）/ index 3 / issue 2 / graph 1）**
- **动前图分析**：38 类按目标树映射为 8+1+3+2+13 = 27 + 留 10（**算术闭合**）。簇间边实测只有**一条反向边**：`domain → page`（`WikiStats.recentUpdates: List<WikiPage>`）⇒ 照搬会造 `domain ⇄ domain.page` **双环** ✗ ⇒ **`WikiStats` 随 page 簇走**（它本就是"页面统计"，其引用者 controller/`service.page`×4 也都在 page 侧）⇒ 留 domain 后 `子面 → domain` 单向、`page → index` 单向 ⇒ 无环（SCC 12 组）。
- **搬迁**：28 文件 `git mv` + 包声明；FQN 替换 **43 文件**；**智能补 import 14 文件**（原同包免 import）；测试 `WikiDomainTest`（跨簇）**留原包 + 补 import**；5 份新 package-info + `wiki/domain/package-info` 重写（记录五子面与"`WikiStats` 为何在 page"）。
- **javadoc 失链 4 处**（㉟ 同款）⇒ 改**全限定**：`WikiFolder → WikiPage#getFolderId()` / `WikiConstants#FOLDER_ROOT_ID`、`WikiRevisionPruneRequest → WikiConstants#PRUNABLE_EDIT_SOURCES`（不加 javadoc 专用 import，避免死成员守卫判未使用）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓（首轮一次 `RedisWikiSlugLockTest` Redis 型偶发，按约定重跑即绿）；启动测试 ✓；`spotlessCheck build` ✓ **1m37s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓。
- **登记（B265 余下）**：`wiki/service/ingest/`(57) 拆四簇（12/21/22/1，含 task/队列/Redis 面，需核 `FROZEN_PREFIXES` 与 json 键锚）；datasource 差集按 §8.9 精确边界重测。

**🔶 B265 第⑩步（2026-10-14，wiki `service.ingest.graph` 切出；另三簇实测被互引阻断）**
- **动前图分析（56 类映射 + 簇间边）**：按目标树映射 `service.ingest`(12) + `pipeline`(21) + `task`(22) + `graph`(1)；实测 **只有 `graph` 零簇间边**——`ingest ⇄ pipeline`（3 / 7 条）与 `pipeline ⇄ task`（6 / 15 条）**互有引用** ⇒ 四簇单独切必造环 ✗ ⇒ 后三簇属**重构级**（与 session 的 turn/attachment 同型）。
- **另发现两处事实**：① `WikiIngestService`（ingest 面门面/端口实现，被 `repository`/`service`/`WikiRedisWiring` 引用）**不在 §8.9 任一 ingest 子面** ⇒ 作为"目标树孤儿"**留在 `service.ingest`**（登记）；② `scripts/package-cycles.baseline.json` 的**第 11 组**正是 `{wiki.service, wiki.service.ingest, wiki.service.page}`（本域既有环）；本步迁出后该组**不变**（graph 是叶子）⇒ 基线零改动 ✓。
- **切出**：`WikiGraphCalculator`（274 行）→ `com.ragagent.wiki.service.ingest.graph`；测试 `WikiGraphCalculatorTest` 随迁 mirror 包；FQN 替换 1 文件（`service/page/WikiPageServiceImpl`）；新面 package-info（写明"对本域 service 面零出边"）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m41s** ✓ **十二守卫** ✓（环 0 / 包级 SCC **12 组**）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（B265 余下）**：wiki `service.ingest`/`pipeline`/`task` 三簇**需先断边**（重构级）· `WikiIngestService` 归属 · **datasource 差集按 §8.9 精确边界重测** · mcp 两项改名（`McpServiceService` / 两个 DTO 泛名）。

**🔶 B265 第⑪步（2026-10-14，datasource 顶层两簇落位：`domain.connector` 9 + `task` 6，根包留 1）**
- **精确差集**：datasource 其实**几乎全对齐**——`connector.{feishu.core,feishu.drive,feishu.wiki,gitlab,ima,notion,rss,yuque}` 八面逐名一致 ✓、`controller`/`domain`/`mapper` ✓；缺口 = **顶层 16 文件**恰好是目标的两簇 + 根包 1（`Connector` 8 类 + `task` 7 类 + `DataSourceSyncEnqueueException`，**算术闭合**）。
- **本步的一刀（解环）**：实测 `conn ⇄ task` 互引只有两条真边——`StreamingConnector → StreamHandler` 与 `task → Connector/ConnectorException` ⇒ 让 **`StreamHandler`（连接器的流式回调契约，被 4 个连接器 + `StreamingConnector` + `SyncExecutor` 使用）随 `domain.connector` 走**（目标列在 `task`，属**有意偏离**）⇒ 两簇单向、根包只被 task 引用 ⇒ 无环。
- **又一处"假依赖"**：环守卫报 `{datasource.domain, datasource.domain.connector}` 新组，追下去是 `DataSourceConstants` 里**一个未使用的 import**（代码体零引用）⇒ 删掉即解环 ✓（**环的分析要区分"真引用"与"未使用 import"**）。
- **连带**：FQN 替换 **60 文件**；**测试随迁**按主体类归位（`ConnectorFrameworkTest`/`ConnectorHttpTest` → `domain/connector`；`SchedulerTest`/`InProcess…Test`/`Redis…Test` → `task`——首版按"引用面启发式"把后两个错投到 domain/connector，编译揪出包内私有访问 `Scheduler.triggerSync`/`ConnectorHttp.statusText` ⇒ 改按主体归位）；清两处空目录（目录卫生守卫抓到）；**snake 键路径锚 ×2 换新**（`datasource/task/…`，FROZEN 覆盖按路径）；javadoc 失链 3 处改全限定 + 1 处 `@throws` 类型改全限定（**javadoc-only 引用的类型不会进 import 扫描**⇒ 必须显式写全）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m47s** ✓ **十二守卫** ✓（环 0 / SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。

**🔶 B265 第⑫步（2026-10-14，mcp `McpServiceService` → `McpServiceManagement`，对齐 §8.9 命名）**
- **改名**：`com.ragagent.mcp.service.McpServiceService` → **`McpServiceManagement`**（目标树名）；`git mv` + 全仓标识符替换 **14 文件**（含 1 个测试的 `mock(...)` 与若干 javadoc 提及）。
- **换锚**：`file-size.baseline.json` 键随路径改名（**605 → 605**，体量与旧 `ServiceService` 的 605 行一致 ⇒ 顺带去掉"叠词"命名）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m45s** ✓ **十二守卫** ✓（环 0 / SCC 12 组）；`:domains:javadoc` ✓。

**✅ B265 收口（2026-10-14，⑧~⑫）**
- **成果**：mcp 结构对齐（`tools`→`tool`、`McpServiceService`→`McpServiceManagement`）· wiki `domain` 五簇 + `service.ingest.graph` · datasource 顶层两簇落位（`domain.connector` 9 + `task` 6，根包 1）。
- **登记为"重构级 / 命名级"（非包重排）**：① wiki `service.ingest`/`pipeline`/`task` 三簇互引（`ingest⇄pipeline` 3/7、`pipeline⇄task` 6/15）⇒ 需先断边；`WikiIngestService` 是目标树孤儿（留在 ingest 面）；② datasource `service.MapperKnowledgeBridge` vs 目标 `KnowledgeIngestClient`（命名差异，留命名批）；两域的两对 DTO 泛名（`Datasource|Mcp` 前缀是**既定防混淆决定**）；③ knowledge（B263）/session（B264）的重构级余项同理。
- **本批资产（四条新经验）**：① **"假依赖"识别**——未使用 import 会被环/入度分析当成真边（现场：`DataSourceConstants → Connector`，删 import 即解环）；② **测试随迁要按"主体类"归位**——按"引用面启发式"会把队列/调度测试投错包，**编译揪出包内私有访问**才是终审；③ `@throws` 与 javadoc-only 类型引用**不进 import 扫描** ⇒ 必须显式写全限定；④ **snake 键的"按路径冻结前缀"与 `EVENT_MAP_FILES` 同族**——搬包必须换锚，否则守卫报"非冻结面新增键"。

**🔶 B266 第①步（2026-10-14，B266 起步：memory `domain` 拆三簇；六域差集盘点）**
- **B266 = ④：im（14 包 79）+ memory（10 包 72）+ auth（13 包 82）+ storage（8 包 41）+ websearch（7 包 35）+ model（5 包 26）+ 其余小域**。本步先做**六域 vs 目标树差集盘点**，再切第一刀。
- **盘点结论（六域多数面已对齐）**：
  - `storage`：`config`/`controller`/`domain`/`dto`/`mapper`/`service`/`support` **逐名一致** ✓；`fileserve` 多一类（`StoragePathGuard`，目标无）；`provider/`(11) **目标树无此面**（待定性）。
  - `websearch`：`controller`/`domain`/`dto`/`mapper`/`provider`(19)/`service` **逐名一致** ✓；缺 `fetch/`(4：`AgentMarkdown`/`BrowserRenderer`/`FetchException`/`Fetcher`——待查落点)。
  - `memory`：`controller`/`dto`/`mapper`/`repository`/`service`/根包 **逐名一致** ✓；`domain/`(27) 需拆 `item`(3)+`scope`(4)+`vector`(3)+留 17（**算术闭合**）。
  - `auth`：`apikey.*` 与 `controller`/`domain`/`dto`(20)/`mapper`/`service` **逐名一致** ✓；差两处**面位置**——`filter/`(2) → 目标 `controller.filter/`、`apikey/filter/`(9) → 目标 `apikey.controller.filter/`；缺 `apikey.dto/`(2)；`api/`(1) 是本批端口模式的**自愿增面**（登记）。
  - `im`：`controller`/`domain`/`mapper`/`runtime`(19) **逐名一致** ✓；9 个渠道面需**改名加 `channel.` 前缀**（dingtalk/feishu/mattermost/qqbot/slack/telegram/wechat/wecom/yunzhijia，44 文件）；`service.ImService` vs 目标 `ImTurnOrchestrator`（改名级）。
  - `model`：`controller`/`domain`/`mapper` **逐名一致** ✓；三处改名（`ModelResponse`→`ModelDetailResponse`、`ModelRuntimeFactory`→`ModelRuntimeAssembler`；两个 DTO 泛名属**有意偏离**）。
- **本步切出**：`memory.domain` → `domain.item`(3) / `domain.scope`(4) / `domain.vector`(3)，留 17；10 文件迁移 + FQN 替换 **44 文件** + 补 import 3 文件 + **4 份 package-info**（3 新建 + `domain` 补建，原本缺失）。
- **动前断言升级（本步新得）**：判据从"新面对留存面零引用"改为**面级图 DAG 检查**——`scope → domain`（`MemorySubject` → 状态处理器/类型处理器）是**合法单向边**，硬套"零引用"会误杀 ✗；实测面级边 `domain → item`、`vector → item`、`scope → domain`，**无环** ✓（SCC 12 组）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m50s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（B266 余下）**：① im `channel.*` 改名（9 面 44 文件）；② auth 两处 filter 面归位 + `apikey.dto` 缺 2 类（查 DTO 落点）；③ model 两处改名；④ storage `provider/` 定性 + `fileserve` 多出 `StoragePathGuard`；⑤ websearch `fetch/`(4) 落点核查。

**🔶 B266 第②步（2026-10-14，im 九渠道面加 `channel.` 前缀）**
- **改名**：`im.{dingtalk,feishu,mattermost,qqbot,slack,telegram,wechat,wecom,yunzhijia}` → **`im.channel.<name>`**（对齐 §8.9）；main **42** 文件 + 测试 **14** 文件（**9 个测试面同名包一起随迁**）。
- **换锚（本步踩到两处"按路径冻结"）**：① `json-key-freeze.baseline.json` 的 **24 条** `im/<face>/…` 键——它的结构是 `{"note","files"}` **两层**，第一版脚本在顶层改键 ⇒ **换锚 0 条**，复跑守卫才暴露 ✗ ⇒ 改在 `files` 层换锚 ✓；② `file-size.baseline.json` 3 条键（值不变）；③ `FROZEN_PREFIXES` 本身是宽前缀 `'im/'`（覆盖子路径）⇒ 无需改；④ `check-event-face-case.py` 注释里的 `im/feishu/**` 一并更新。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m42s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。
- **登记（B266 余下）**：auth 两处 filter 面归位（`filter/`→`controller.filter/`、`apikey/filter/`→`apikey.controller.filter/`）+ `apikey.dto`(2) 落点 · model 两处改名（`ModelResponse`/`ModelRuntimeFactory`）· storage `provider/` 定性 + `fileserve` 多 `StoragePathGuard` · websearch `fetch/`(4) 落点 · im `service.ImService` vs 目标 `ImTurnOrchestrator`。

**🔶 B266 第③步（2026-10-14，auth 两处 filter 面归位）**
- **归位**：`auth/filter`(2) → **`auth/controller/filter`**；`auth/apikey/filter`(9) → **`auth/apikey/controller/filter`**（对齐 §8.9 的面名）；测试 `auth/apikey/filter/APIKeyAuthChannelPrincipalTest` 随迁 mirror 包；两处新面 package-info。
- **换锚**：`check-json-key-case.py` 键级豁免路径（`auth/apikey/controller/filter/APIKeyAuthChannel.java`）；`file-size.baseline.json` 键换锚（**717 → 717**）；FQN 替换 9 文件（含 `WebConfig`、`common/apikey/ApiKeyAuthPort` 与 4 个 boot 契约测试）。
- **踩坑**：`git mv <file> <dir>/` **要求目标目录已存在**（首版只建了父级 `auth/controller`）⇒ 脚本报 `No such file or directory` 后中止（未产生半成品改动）⇒ 补 `mkdir -p` 目标目录重跑 ✓。
- **登记**：`apikey.dto`(2) 的 `TenantAPIKeyCreateResponse`/`TenantAPIKeyResponse` 全仓**不存在** ⇒ 属**待创建**（重构级，登记）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。

**🔶 B266 第④步（2026-10-14，命名对齐：model 两类 + im 一类）**
- **改名**：`model.dto.ModelResponse` → **`ModelDetailResponse`**（§8.8.4 目标名）；`model.service.ModelRuntimeFactory` → **`ModelRuntimeAssembler`**；`im.service.ImService` → **`ImTurnOrchestrator`**（§8.9 目标名，去掉"ServiceService"式的泛名）。
- **连带**：`git mv` 3 文件 + **全仓 token 替换 68 文件**——同时改**小写标识符**（`modelRuntimeFactory` → `modelRuntimeAssembler` 92 处、`imService` → `imTurnOrchestrator` 25 处）保持"类型名/变量名"一致（本仓统一派取向）；`json-key-freeze` 键 `im/service/ImService.java` 换锚（**该基线第三次咬人**）。
- **风险预检（做在前面）**：Strings/Bean 名（`@Qualifier`/`getBean`/配置键）全仓零命中 ✓ ⇒ 改名不动装配。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓；定向 `--rerun-tasks` ✓。

**🔶 B266 第⑤步（2026-10-14，tenant 3 类 → `repository/`；embedchannel `filter` → `controller.filter`）**
- **tenant**：`Tenant` / `TenantConfigRedaction` / `TenantLookup` → **`tenant/repository`**（对齐 §8.9 的 `repository/`(3)）；根包留 6 个 `*Config` ✓（算术闭合）；测试 `TenantConfigRedactionTest` 随迁 mirror 包；FQN 替换 **87 文件**（`tenant.Tenant` 单类就有 85 个引用者）。
- **embedchannel**：`EmbedAuthFilter` → **`controller/filter`**（对齐 §8.9 面名）。
- **三处现场纠错**：① 脚本把一个面的**源路径写成域根** ⇒ `git mv` 前断言中止（tenant 已搬、包声明未改）⇒ 写**续跑脚本**补齐（教训：目标目录先建、源路径写全）；② 搬包后**原同包免 import** 的类需补 import——`TenantConfigRedaction` 补 `ParserEngineConfig`/`StorageEngineConfig`、`Tenant` 补 `APIPrincipalConfig`(+`TypeHandler`)、随迁测试补 2 个，**编译三轮才收敛**；③ **SCC 基线"成员换名"**：既有组 `{embedchannel, embedchannel.filter, embedchannel.service}` 成员路径变了（组结构不变）⇒ `package-cycles.baseline.json` 成员换名 **1 处**（组数仍 12）；守卫从"新增/变形"↔"已消除/变形"两侧同时报警 ⇒ 精确定位后**外科式改**，未用 `--write`（避免吞别处漂移）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m39s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）；`:domains:javadoc` ✓。
- **登记（B266 余下）**：① approval `24` → 4 面（root 8 + domain 7 + redis 2 + service 7，**算术闭合**）；② skill 域（现类在 `agent/skills`，目标 `skill/`，**域级**）；③ storage `provider/`(11) + `StoragePathGuard` → 计划归 **`platform.storage.*`**（现 `platform.*` 只在 `:common` 有 `stream/`）⇒ **模块级**；④ websearch `fetch/`(4) 已在 **`:engine` 的 `com.ragagent.webfetch`**（分层有意偏离）；⑤ auth `apikey.dto`(2) 待创建。

**✅ B266 收口（2026-10-14，①~⑤）**
- **成果**：memory `domain` 三簇（item/scope/vector，留 17）· im 九渠道面加 `channel.` 前缀 + `ImService`→`ImTurnOrchestrator` · auth 两处 filter 面归位（`controller.filter` / `apikey.controller.filter`）· model `ModelResponse`→`ModelDetailResponse`、`ModelRuntimeFactory`→`ModelRuntimeAssembler` · tenant 三根包类 → `repository/` · embedchannel `filter` → `controller.filter`。
- **六域 + 11 个小域盘点结论**：**完全对齐**的有 vectorstore / audit / evaluation / favorite / settings / system / initialization / storage（除下述两项）/ websearch（除下述一项）/ model / memory / auth / im（除下述各项）。
- **登记为"重构级 / 模块级 / 命名级"**：
  ① **approval**：目标 4 面（root 8 + domain 7 + redis 2 + service 7，算术闭合）**实测三对互引**（`service ⇄ domain`、`service ⇄ root`、`domain ⇄ root`）⇒ 拆分被阻断；且两个测试助手（`TestCancellation`/`FakeRedisPubSub`）是**包私有**、被跨面测试共用 ⇒ 同样需先重构（提公开或收敛用法）。
  ② **skill 域**：目标 `skill/{mapper,service}` 的 7+1 类现居 `agent/skills`（`SkillCatalogService` 等）⇒ **域级迁移**（涉及 agent 与 skill 的边界决策）。
  ③ **storage `provider/`(11) + `StoragePathGuard`**：计划归 **`platform.storage.*`**（`platform.*` 现只有 `:common` 的 `stream/`）⇒ **模块级迁移**。
  ④ **websearch `fetch/`(4)**：已在 **`:engine` 的 `com.ragagent.webfetch`** ⇒ 分层**有意偏离**（fetcher 属运行时底座）。
  ⑤ **auth `apikey.dto`(2)**：`TenantAPIKeyCreateResponse`/`TenantAPIKeyResponse` 全仓不存在 ⇒ 待创建。
  ⑥ `audit/api`(1)、`auth/api`(1)：本批跨域端口模式的**自愿增面**（`AuditWriteAdapter`/`UserLookupAdapter`）。
- **本批资产（三条新经验）**：① **动前断言从"零反向边"升级为"面级图 DAG 检查"**（`scope → domain` 这类单向边合法，硬套零引用会误杀）；② **改名要连小写标识符一起改**（`modelRuntimeFactory` 92 处 / `imService` 25 处）并**预检 Strings/Bean 名**（`@Qualifier`/`getBean`/配置键零命中才动）；③ **按路径冻结的基线共三处**（`json-key-freeze.files` 两层结构、`file-size.limits` 键、`package-cycles` 组成员）——搬包/改名必须逐处换锚，其中 SCC 基线要**外科式成员换名**（别用 `--write`，会吞别处漂移）。

**✅ B267 门禁收紧收尾（2026-10-14，A19/A20 两规则 + A11b 收紧 + 复杂度报告）**
- **A19「分层方向」**（新增，两条子规则）：① **`repository` 不得依赖 `service`**——实测 **0 违例** ⇒ **零容忍硬规**（`ratchet(Set.of(), …)` 表达"基线外新增即红"，同 B257/B260 的写法）；② **`domain` 不得依赖 Spring 构造型 / `..mapper..` 包**——实测 **10 条** ⇒ 棘轮基线逐条附理由：**4 个实体**直接 import 他域 `mapper` 的 `JsonbRawStringTypeHandler`（`CustomAgentEntity`/`EmbedChannelEntity`/`ChannelSessionEntity`/`ImChannelEntity`，收敛=处理器下沉或提 `common`）、**6 个 `@Component` 领域守卫/接线**（knowledge 4 guard + `SpanTracker` + `ConnectorHttpWiring`，收敛=装配层注入或移出 domain）。
- **A19 的判据边界（有意偏离，写进规则 javadoc）**：`org.apache.ibatis.type.*` **未纳入**判据——实体注解 `@TableField(jdbcType=…, typeHandler=…)` 让它覆盖 **49 个实体**，是全仓 MyBatis-Plus 实体约定，纳入即"每加一个 JSONB 列就红"⇒ 等于给日常写法立规 ✗；待实体注解下沉出 domain 再把判据加回。**诊断手法**：先把命中包写进违例串（`类 ⇐ 包1|包2`）跑一次、按包归类（49/6/4），定案后回退为类级基线——**"判据边界"要拿实测分布来定，不靠拍脑袋**。
- **A20「装配契约」**（新增）：**每个 `common/**` 顶层 `*Port` 接口的 main 实现恰为 1**（0=契约悬空 / ≥2=提供方不唯一）⇒ 实测 **12/12 恰一**（`UserLookupPort`→`UserLookupAdapter` … `SessionMessagePort`→`MessageRepository`）⇒ **零容忍硬规**；规则注释写明它是"`@Lazy` 环消失 + 装配面可审计"的前提（`@Lazy` 本身由 A6 守，不重复立规）。
- **A11b 收紧（"清零核查"结论）**：检测从「名字以 `Repository` 结尾」升级为**注解判据**（带 `@Mapper` 或祖先链含 `BaseMapper` 者视为**真映射接口**，不算门面）⇒ 剔除 2 条**命名巧合**假阳性（`vectorstore.mapper.VectorStoreRepository`、`websearch.mapper.WebSearchProviderRepository`），基线 **11 → 9**；余 9 条逐条读注解确认均为 `@Component`/`@Repository` **手写门面**（收敛路径=碰到哪域归哪域搬 `repository/`）。**这条规则此前带假阳性长期占着基线**——"基线逐条读一遍"本身就能查出规则精度问题。
- **复杂度报告**（`scripts/complexity-report.py`，**报告级非硬门**，对齐 §12.5"候选审查线不是立即生效的全库硬门"）：首跑快照 —— 扫 2004 主源文件；**方法分支 > 15：270 处**（Top：`webfetch/Fetcher#detectContentType` 113、`agenttools/sql/SqlSelectDeepChecker#deepCheck` 104、`mcp/tool/McpInputSchemaValidator#validate` 78）；**构造器依赖 > 8：2 处**（`session/service/AgentEngineAssembler` **14**、`session/qa/QaTurnExecutor` 9）；公开可变实例字段 237（**多数是 `@ConfigurationProperties` 绑定类**，打印时已加提示"复核先剔该族"）；包级 SCC 复用既有硬门脚本不重复。`--strict` 预留为将来当门用。
- **测试**：**4982 / 失败 0 / 跳过 4**（boot **1329 → 1331**，正是新增 2 条规则 ✓）；启动测试 ✓；`spotlessCheck build` ✓ **1m36s** ✓ **十二守卫** ✓（环 0 / 包级 SCC 12 组）。
- **阶段 6 收官**：B263 ✅ · B264 ✅ · B265 ✅ · B266 ✅ · B267 ✅。

**🔶 B268 第①步（2026-10-14，**v0.3.0 起：§5.1/§8.2 平台面收纳第一批**）**
- **背景**：v0.2.0 收官后转 v0.3.0，按方案把 7 个逻辑面"走到底" = ① platform 成员收纳 ② 契约面收纳 ③ 立「域间只经契约」。本步做 ① 的第一批。
- **权威映射（脚本产物）**：把 §8.1（contracts **25 面 / 172 类**）+ §8.2（platform **23 面 / 147 类**）与 `:common`（+`:engine/tracing`）现状比对 ⇒ **213 类可确定归属、0 歧义、12 类计划未列（待裁决）**——这份映射就是后续 ②③ 批的施工图。
- **本步搬迁（31 文件）**：`com.ragagent.event` 12 类 → **`platform.event`**；`TenantContextSnapshot` → **`platform.access`**；`com.ragagent.tracing.langfuse` 18 类 → **`platform.tracing.langfuse`**。FQN 替换 **73 文件**；测试树 **11 文件 mirror 迁移**（同名包）+ 9 处补 import。
- **payload 26 类留在原地**：目标面 `contract.event.payload`，但 **20/26 沾 Jackson**（50×`@JsonInclude` + 6×`@JsonProperty`）与契约面「纯 JDK」（R1）冲突 ⇒ 待 ② 批**先去 Jackson** 再迁；`event/package-info` 已改写为"过渡态"说明。
- **换锚五处（全中，含一处"漏跑"现场）**：① `FROZEN_PREFIXES`：`tracing/langfuse` → `platform/tracing/langfuse`（+注释补 B268）② 冻结快照 **3 键**换锚 ③ `check-event-face-case.py` 路径常量 `event/EventType.java` → `platform/event/EventType.java` ④ **A2** 属性扫描名单 `+platform.tracing.langfuse`（并删陈旧项 `tracing.langfuse`）⑤ **引擎边界名单** `AgentEngineBoundaryTest.ALLOWED_TOP_LEVEL` `+platform` —— 它的**红态探针字符串被全局 FQN 替换连带改写**成 `platform.event.EventBus` ⇒ 名单不补即红（探针自动跟着换包"自证" ✓）。
- **两条曲折（值得记）**：① `/tmp/b268_1.py` 在 **`git rm` 处 `exit 1` 截断后续步骤**（换锚三步没跑）⇒ 守卫当场报"非冻结面 snake 键 + 冻结覆盖变化" ✗ ⇒ 补跑修正；**教训：脚本里会失败的动作（git rm/断言）要放最后或容错**。② `tracing/package-info.java` 迁空成"幽灵" ⇒ 删；5 处空包目录由 **B124 目录卫生守卫**点名 ⇒ 清。
- **测试**：**4987 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m40s** ✓ **十二守卫** ✓；`arch.*` 28 条 ✓（含 A2）；`:common:javadoc` / `:domains:javadoc` ✓。
- **登记（②批及后续）**：payload 26 去 Jackson · `common.{web,prompt,text,security,crypto,mybatis,jdbc,deployment,context,embedding,model,taskqueue,error,filter,storage}` → 对应 `platform.*`（213 类里本批仅搬 31）· `engine/tracing/decorators` 5 类目标 `platform.tracing.decorators` ⇒ **跨模块**待定 · 12 类计划未列者逐条裁决（`TenantContext`/`TenantScopes`→`platform.access`、`LikeEscapes`→`platform.persistence`、`UrlBytesFetcher`→`platform.http`、两个 apikey Response DTO→`auth/apikey/dto`、`AuditWritePort`/`UserLookupPort` 等端口→`contract.<域>`）。
- **顺带更正**：B266③ 登记的"`apikey.dto` 两类不存在"**实为误判**（当时 grep 只扫 `domains/src`，它们在 `:common/apikey`）⇒ 目标面是 §8.9 的 `auth/apikey/dto`，待 ② 批处理。

**🔶 B268 第②步（2026-10-14，平台面剩余收纳 71 类 + **破 `common ⇄ platform` 顶层环**）**
- **payload 26 被实测挡住（重要发现，②批第 1 项暂停）**：目标面 `contract.event.payload` 要求纯 JDK（R1），但 `@JsonInclude` 是 **33×NON_DEFAULT + 17×NON_EMPTY** 的**逐类策略**（`EventJson` 的三条集中行为里**没有**它），`@JsonProperty("isStream")` 自带注释"否则 Jackson 会把 `isStream()` 拆出多余 `stream` 属性（**实测踩过**）"，且 `EventJson` 在 `platform/event` 之外**零使用**（真正序列化走框架 mapper）⇒ **注解承重，不动线格式就进不了契约面** ⇒ 该步转为**「契约面去框架化」专项**（下一批），本步只做不被挡的平台面剩余。
- **搬迁 71 类**：`common/web`(26 → platform.web 16 · platform.text 6 · platform.persistence 2 · platform.web.health 1 · platform.http 1)、`common/prompt`(8) · `common/text`(5) · `common/filter`(1) · `common/security`(6) · `common/crypto`(2) · `common/mybatis`(4) · `common/jdbc`(1) · `common/deployment`(2) · `common/context`(3) · `common/embedding`(1) · `common/model`(1) · `common/taskqueue`(1) · `common/error`(4) · `common/storage`(5)。FQN 替换 **732 文件**；测试镜像 16；新面 package-info 12。
- **⚠️ 守卫抓到真问题：新增顶层环 `common ⇄ platform`**（`环：1 组（基线 0）`）✗ —— 根因是 **5 条 `common → platform` 反转边**：4 个共用工具（`Whitespace`/`TextConv` 被 `common.wiki.*` 用；`JsonMappers`/`SortedMapSerializer` 被契约类型 `common.knowledge/retrieval` 用）+ `TenantAPIKeyScope → BizException`。**修法（零语义变化）**：① `BizException` 下移 **`common/error`**（与 `AppError` 同层）；② 4 个共用工具**回 L1 底座** `common.{text,web}`（登记理由：被契约面共用，放 platform 必造环；去除路径 = 契约面去 Jackson/去框架，与 B258 登记的 `AppError` 同族）。破环后 **顶层环 0 ✓ · 包级 SCC 12 → 11 组**（**真减少** ✓）；破环 FQN 再替换 397 文件。
- **A16 判据修正（第二个真问题）**：`platform/task/RedisTaskQueueCore` 被 `memory.service` 引用 ⇒ 裸段名匹配把**底座面** `platform.task` 误判成边界层 `task` ✗。判据改为**排除 L1 底座**（`common/platform/contract/event/tracing/stream/config`）后再匹配 `com.ragagent.<段>.{controller,mapper,task,client,channel}` —— 既修假阳性，又保住 `agentmanagement/agenttools` 这类适配包的**真违例**（我第一版"域内清单匹配"漏了 `agentmanagement`（不在业务域清单里）⇒ 两条 A16 基线当场过期 ⇒ 回退重写 ✓）。
- **其余换锚**：键级豁免 3 处（`TracingContext`/`RequestIdFilter`/`PromptTemplateCatalog`）· A2 扫描名单 4 处（去重后 14 条）· `api-envelope.baseline.json` 的 `HealthController` 路径 + 文档提法 · **`PromptOwnershipTest`** 的"模板文件名唯一权威包"路径常量 · `AgentEngineBoundaryTest.ALLOWED_TOP_LEVEL`（①已补 `platform`）。
- **测试**：**4987 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m50s** ✓ **十二守卫** ✓；`arch.*` 28 条 ✓；`:domains:javadoc` / `:common:javadoc` ✓。
- **登记（下一批）**：**契约面去框架化**（`AppError` · `FaqChunkMetadata` · `SearchResult` · `WikiLanguageSupport` · `TenantAPIKeyScope` 去 Jackson/去 platform 依赖 ⇒ 之后 payload 26 与 4 个共用工具才可归位 `contract.*`/`platform.*`）；`common/{agent,apikey,audit,auth,graph,knowledge,llm,memory,pipeline,retrieval,session,storage,tenant,vectorstore,wiki}` → `contract.*`（映射表已建）；`engine/tracing/decorators` → `platform.tracing.decorators` 属**跨模块**待定；12 类计划未列者待裁决。

**🔶 A② 契约面收纳第②批（2026-10-14，**去框架化 31 类 + payload 26 整批**）**
- **分档方法**：42 个沾框架类按 **"类型级 vs 仅注解"** 切开——**仅注解 31**（可 mixin 去框架化）/ **类型级 8**（`JsonNode`×7 + servlet×1）/ **Spring 绑定 3**（`@ConfigurationProperties`）。**算术闭合 42 = 31 + 8 + 3** ✓。
- **落位**：31 类（`AppError`/`TokenUsage`/`SearchParams`/`WebSearchConfig`/`ToolResult`/`ConnectionConfig`/`IndexConfig`/`ExtractedItem`/`SlugUpdate`/`ResponseType`/`PromptCacheStatus` + 20 个事件载荷）+ **6 个 `orEmpty` 组载荷**（整批 26 ✓）= **37 类**入 8 个契约面；FQN 替换 292 + 12 文件。
- **机制（关键资产）**：**Jackson mixin + 集中模块**——线格式（键名/省略策略/枚举字面量/派生 getter 抑制）从契约类剥离到 `common/web/ContractJsonMixins{Core,Event}` + `ContractJsonModule`，契约类恢复纯 JDK ✓。**注册点 7 处**：`JsonMappers.lenient()`（jsonb 读路径）· `EventJson`（事件）· `StreamJson`（SSE 流）· `JsonValues`（上下文预算）· `LangfuseAttributes` · **boot 的 Spring MVC customizer**（HTTP 出参/入参）· 测试侧 5 处自建 mapper。枚举（`ResponseType`/`PromptCacheStatus`）走显式 ser/deser（避 mixin 静态工厂）。
- **⚠️ 四条现场教训**：
  1. **`@JsonIgnore` 写在 mixin 方法上不被 Jackson 采信** ✗（`JsonContractRoundTripTest` 抓到派生 getter 仍输出）⇒ 改**类级 `@JsonIgnoreProperties`** ✓（且必须 `value = {...}` 具名——数组默认值放在具名属性前是**非法注解** ✗）；**我用一个临时探针测试**（`MixinProbeTest`，跑清即删）把"生产模块/成员级/类级/两者同发"四种机制一次试清，才定位到真因是**测试助手自建 mapper** ✗。
  2. **R1 假阳性**：`SearchParams.queryEmbedding` 的 `float[]` ⇒ JVM 描述符 `[F` 剥括号后是单字母 `F`、不以 `java.` 开头 ⇒ 被判"契约面引入框架依赖" ✗ ⇒ 规则补"原始类型（含数组）描述符 ⇒ 归 JDK" ✓（与 B267 的 A11b 精度收紧同族）。
  3. **"去框架化"的真实边界**：注解可分离 ✓，但 **API 面用框架类型**（`JsonNode` 7 类、`HttpServletRequest` 端口 1 类、`@ConfigurationProperties` 3 类）**不是机械活**——后者需重设计（中性 JSON 表示/typed records/端口签名），属 K 级 ⇒ 本批**不硬做**，走登记 ✓。
  4. **测试侧 mapper 也是线格式守门人**：换 mixin 后所有自建 mapper 的测试都必须注册模块（本批踩 6 处）⇒ **这恰好让"哪条路径没注册"暴露**（守卫 + R1 + 测试三方夹击）✓；`check-json-key` 前缀冻结也当场点名 4 处"离开前缀覆盖" ⇒ 快照刷新（`--write`，本脚本既定路径）+ mixin 模块入**冻结面**（它就是线格式载体 ✓）。
- **换锚**：`FROZEN_PREFIXES` —— `common/llm` 整目录迁空 ⇒ 拆 4 个文件级前缀（`contract/model/{ResponseType,TokenUsage,PromptCacheStatus}`、`contract/tool/ToolResult`）；`SearchParams`/`ExtractedItem` 换 home；**新增 `common/web/ContractJsonMixins`**；冻结快照 4 键换锚 + 覆盖 146 文件重登记；事件面守卫 2 处路径常量（`contract/event/payload`、`contract/model/ResponseType`）；死包目录 5 个；2 份 package-info 补 `com.ragagent.` 前缀（R7 守卫点名 ✓）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **1m52s** ✓ **12 守卫** ✓（环 0 / SCC 11 组）；`arch.*` 28 条 ✓；四模块 javadoc ✓；`JsonContractRoundTripTest` 35 条方法全绿 ✓。
- **登记（A② 余项）**：**类型级 11 类**——`JsonNode` 7（`ChunkFacts`/`KnowledgeBaseView`/`KnowledgeDocumentFacts`/`DocumentChunkMetadata`/`FaqChunkMetadata`/`SearchResult`/`TenantConfigLookup`）· servlet 端口 1（`ApiKeyAuthPort`）· Spring 绑定 3（`TenantProperties`/`RetrievalDriverProperties`/`LanguageProperties`）；外加 A②-1 的 9 个回退件（其依赖已部分解锁，下一批处理）。

**🔶 A② 契约面收纳第③批 + ✅ 收口（2026-10-14，`common/**` 90 → **12 类**）**
- **本批（第③批，9 类 + 3 处回退）**：契约侧再落 4 类（`SearchChunkMerge`/`SearchTarget` → `contract.retrieval`；`EmbeddingModelPort` → `contract.knowledge`；`TextConv` → `contract.text`）；**5 个 Jackson 工具自 `common/web` 迁 `platform/web`**（`JsonMappers`/`SortedMapSerializer`/`ContractJsonModule`/`ContractJsonMixins{Core,Event}`——A②-2a 时被判"环阻塞"，本批复查**已无环** ✓ 顺带把线格式载体放到平台面）；FQN 替换 320 文件。
- **⚠️ 又踩同一个坑（第 3 次）**：`ChunkSearchGateway`/`KnowledgeBaseLookup`/`KnowledgeDocumentGateway` 的依赖是**同包裸引用**（无 import ⇒ 我的"扫 import"式依赖检查看不见 ✗）⇒ 编译 + R1 抓回、**逐类按"残留类名做裸引用正则"复核后回退** ✓。⇒ **铁律升级**：*闭包/可搬性判据必须按**裸引用**扫（`(?<![\w.])Name(?![\w])`），不能只看 import* —— A②-1 与 A②-2c 两批各栽一次，已写进本记录与记忆。
- **A② 收口读数**：四个子批共 **99 类**入契约面（42 + 37 + 11 + 9）；`common/**` **90 → 12 类**；契约面现存 `contract/**`：15 面 + `error`/`tool`/`text` = 18 面。
- **残留 12 类（逐条登记，`12 = 7 + 4 + 1` 算术闭合）**：
  ① **`JsonNode` 载体 7**（`ChunkFacts`/`DocumentChunkMetadata`/`FaqChunkMetadata`/`KnowledgeBaseView`/`KnowledgeDocumentFacts`/`SearchResult`/`TenantConfigLookup`）——**API 面就是 Jackson 类型**，去框架化＝换中性 JSON 表示或 typed records（K 级重设计，非机械活）；
  ② **依赖上述载体的纯 JDK 4**（`ChunkSearchGateway`←`ChunkFacts`、`KnowledgeBaseLookup`←`KnowledgeBaseView`、`KnowledgeDocumentGateway`←`KnowledgeDocumentFacts`、`PipelineMessageView`←`SearchResult`）——**依赖闭包**不成立，随 ① 一起走；
  ③ **servlet 端口 1**（`ApiKeyAuthPort`：`authenticate(HttpServletRequest, HttpServletResponse)`）——签名重设计＝改 auth 流转（K 级）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m18s** ✓ **12 守卫** ✓（环 0 / SCC 11 组）；`arch.*` 28 条 ✓；四模块 javadoc ✓。
- **A② 全批提交**：`a7d3b13e`（第①批 42 类）· `37b5fbb8`（第②批 去框架化 37 类）· `2ee75327`（第②批 补：解锁 11 类）· 本批（第③批 9 类 + 收口）。
- **A 的下一步**：**③ 立「域间只经契约」**（A20 已守"契约唯一实现"；还差"业务域之间只经 `contract`"这条规则——应先测现状再决定硬规则 vs 棘轮基线）。可选后续：① 的 K 级重设计（`JsonNode` → typed records）另立项。

**🔶 A③ 口径定盘：R10 三档分级（2026-10-14，边 57 / 类 280 不变——本批只改"怎么读"）**
- **回答"理想状态是不是清零"**：R10 **已只算"对他域实现类"的依赖**（`contract.*` 与装配层 `config/platform/common` 不计）⇒ "清零"＝"不再有任何域直接依赖他域实现"，有意义 ✓；但**一刀切清零会逼出假抽象** —— 属主边（如 `auth→tenant`）清零就得给"租户实体字段清单"造端口 ✗，比直接依赖更差 ⇒ **目标不是"0 条边"，而是"0 条未分类边"** ✓。
- **三档口径（写进基线 `tiers`，检查器读档）**：
  - ① `clearable` **49 条 / 194 类** ← **目标 0（A③ 主线）**，也是 `domains` 拆 Gradle 模块的先决条件；
  - ② `owner` **3 条 / 21 类**（`auth→tenant` 14 · `system→auth` 5 · `system→tenant` 2）—— 同数据属主/管理面，**登记允许**，条件＝读侧走 L1 端口、写/管理面集中在属主域；
  - ③ `intra-family` **5 条 / 65 类** —— 族 **`agent-runtime` = `agent`/`agentmanagement`/`agenttools`/`session`** ⇒ **豁免但冻结**（本档新增类仍判红），跨族仍按 ①。
- **分档证据**：`auth→tenant` 属主依据＝§8.9 给 `tenant/` 只留 3 包/10 文件（配置 + `TenantMapper` + `repository/Tenant|TenantConfigRedaction|TenantLookup`），**管理面计划上就留在 auth** ⇒ 清零要么违背计划面清单、要么改公开 API 形状；`system→auth` 属主依据＝§8.9 把 `SystemAdminUserService` 放在 `system/`（**待裁定**：写侧直用他域 mapper 与 §9.1「状态唯一写入者」有张力）；族内依据＝B211 试切 `:channels` 回退的结论「紧耦合对内聚优先」。
- **④ 待判 15 条全部归位 ⇒ "未分类"清零 ✓**：13 条入 ①（tool SPI ×3 边 / `TenantService` 读取口 ×3 边 / **`embedchannel` 直用他域 controller ×4** ✗ 违反 §12.1 规则 1 / `im` ×3 / `session→mcp` 等）、1 条入 ②（`system→auth`）。
- **① 档高杠杆刀清单（写进 `tiers.clearable.focus`，供后续逐刀）**：**tool SPI**（`agent.tool.*`）提 contract ⇒ **一刀清 3 条**（`approval→agent`/`im→agent`/`mcp→agent`，11 类，消费方全在族外）；**`auth.service.TenantService` 读取口** ⇒ **一刀清 3 条**（`datasource`/`embedchannel`/`memory→auth`，4 类）；`embedchannel` ×4（优先级最高）；`im` ×3；`session` 能力边（63 类）。
- **守卫改造（`scripts/check-domain-contracts.py`）**：分档读自基线 + **分档元数据自检**（族内登记必须与族定义一致，否则守卫自身判红）+ `--write` 自动清理**陈旧登记**（边已消失的 tiers 条目）+ 失败信息标注"⇒ 默认入 ①（目标 0）"与"**登记不是新增许可**（棘轮先于分档）"。`--list` 现在带档位标记与 ②③ 档理由。
- **三条安全阀实测**：① `--write` 幂等（连跑 2 次 hash 不变 ✓）；② 造新边 ⇒ strict 红 + **退出码 1** ✓、`--write` 拒绝 + 退出码 1 ✓；③ 还原 ⇒ 绿 + 退出码 0 ✓（基线 hash 复原一致）。
- **⚠️ 两条自伤（记录）**：① 反向探针我**直接改源码造边**，撤销时多留一行空行（`git diff` 抓到）⇒ 改为"**只动基线**"的探针法（安全、可逆、零源码触碰）✓；② `cmd | head` 之后的 `$?` 取的是 `head` 的退出码 ✗ ⇒ **退出码必须不经管道测** ✓。
- **验证**：`bash scripts/run-guards.sh` ⇒ **13 守卫全绿** ✓（R10 行现在带三档进度与族定义）；工作树仅两文件（检查器 + 基线）。
- **A③ 的 DoD（定盘）**：① `clearable` = **0**；② `owner` 3 条带条件维持（读侧端口化已做 ✓）；③ `intra-family` 5 条冻结；**未分类 = 0（本批已达成 ✓）**。
**🔶 A③ 收敛第八刀：扫余量小边（2026-10-14，R10 **边 60 → 57**、类 284 → 280）**
- **迁 4 个只读类**（一律零 API 形状变化 ✓）：
  - `memory/MemoryService.workspaceConfig`：`getTenantById` + `tenant.getMemoryConfig()` → **`tenantService.memoryConfig(id)`** ✓（try/catch 与会零值语义保持 ✓）；
  - `embedchannel/controller/filter/EmbedAuthFilter`：只做**存在性判断** ⇒ `tenantService.exists(tenantId)` ✓；
  - `datasource/service/DataSourceSyncExecutor`（2 处）：同样只判存在 ⇒ `exists(...)` ✓；
  - `system/controller/SystemController`（5 处）：`currentTenant()` 改回 **`TenantStorageView`**（`storageView(tid)` ✓）、3 处 `getStorageEngineConfig()` → `storageEngineConfig()` ✓、1 处 `getParserEngineConfig()` → **`tenantService.parserEngineConfig(tid)`** ✓。
- **收益（一次消 3 条边）**：**`datasource→tenant` / `embedchannel→tenant` / `memory→tenant` 三条边整条清零** ✓ ⇒ **边 60 → 57**；`system→tenant` 3 → 2；类 284 → 280。
- **残余登记（同"属主/管理面"类）**：`system→tenant` 余 2 类（`SystemAdminController` 直接 import `tenant.mapper` ✗、`SystemAdminUserService` 写实体字段 ✗）—— 与 `auth→tenant` 同性质（系统管理面），已在基线 note 的"边分级"里归为**属主边**（不追求清零，除非拍板搬管理面/改 API）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m06s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向（Memory/Embed/DataSourceSync/SystemController ✓）全绿。
- **A③ 累计（八刀）**：基线 **317 → 280 类（−37）**、**边 64 → 57（−7）**；**已清零 7 条**：`storage→tenant`、`session→auth`、`session→tenant`、`knowledge→tenant`、`datasource→tenant`、`embedchannel→tenant`、`memory→tenant` ✓；**属主边**：`auth→tenant`（14 类，待拍板）、`system→tenant`（2 类，同性质）。

**🔶 A③ 第七刀：`auth→tenant` 分档裁定（2026-10-14，类 285 → 284；**该边判定为"属主边，不追求清零"**）**
- **实测分档（15 类）**：**属主 4 类**（`TenantService`（mapper 13 次 + 实体 setter 12 次）/`TenantConfigOps`/`TenantCreateOps`/`UserService` —— 读写租户实体）；**管理/响应成形 11 类**（`TenantResponse` 26 次 getter、`AuthController` 10、`TenantCatalogController` 直接 `List<Tenant>` 出参 …）。
- **关键结构结论（写进 R10 基线 note 的"边分级"）**：计划 §8.9 给 `tenant/` 定的面是 **3 包 / 10 文件**（根包配置类 + `mapper/TenantMapper` + `repository/{Tenant,TenantConfigRedaction,TenantLookup}`）——**租户的"管理面"计划上就留在 auth** ✗ ⇒ `auth→tenant` 是**域属主边**：
  - 清零路径 A＝把管理面（服务/控制器/响应成形）搬进 `tenant/` ⇒ **与计划的面清单相悖** ✗；
  - 清零路径 B＝把 API 出参从实体改成视图 ⇒ **改公开 API 形状**（需前端适配 + 金片验证 ✗）；
  ⇒ **需拍板**；在读侧已"能走的都走 L1 端口" ✓。
- **本刀实做（读侧净化，零形状变化）**：端口补 **`apiPrincipalConfig(long)`（返回契约类型 ✓）** 与 **`exists(long)`**（存在性探针，避免为此拉实体 ✓）；`auth/apikey/.../APIKeyAuthChannel` 迁到端口 ✓（其 stub 测试同步改造 ✓）⇒ `auth→tenant` **15 → 14 类**。
- **⚠️ 两处守卫现场**：① 我用内联全限定名写 `com.ragagent.contract.tenant.APIPrincipalConfig` ✗ ⇒ **R8 判红** ✓（改 import + 简名 ✓ —— R8 第二次抓到我的内联 FQN ✓）；② 判"死 import"时被**字符串字面量/注释里的 `X-Tenant-ID`** 骗过 ✗ ⇒ 最终以**编译器为判据**（删了能编译即确为死 ✓）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m06s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向（APIKey/Auth 族 ✓）全绿。
- **A③ 累计（七刀）**：基线 **317 → 284 类（−33）**、**边 64 → 60（−4）**；**已清零边 4 条**（`storage→tenant`/`session→auth`/`session→tenant`/`knowledge→tenant` ✓）；**判定为属主边 1 条**（`auth→tenant` 14 类 ✗ 待拍板）；余量小边：`system→tenant` 3 · `datasource`/`embedchannel`/`memory→tenant` 各 1（合计 6 类 ⇒ 可再消 4 条边 ✓）。

**🔶 A③ 收敛第六刀：`knowledge→tenant` 清零（2026-10-14，R10 **边 61 → 60**、类 287 → 285）**
- **发现的问题（比"多一条 import"严重）**：knowledge 的 FAQ 索引写入链 **直连他域持久层** —— `knowledge/storage/TenantStorageService` 注入 `TenantMapper`（读租户行）+ **裸 SQL `UPDATE tenants SET storage_used=…`**（写租户表 ✗✗）。
- **做法**：立 **L1 端口 `common.tenant.TenantStorageQuotaPort`**（读 `quotaView` / 写 `adjustUsed` + 嵌套 record `TenantQuotaView(tenantId, quota, used)` ✓），实现落在**属主域** `tenant/api/TenantStorageQuotaAdapter`（**SQL 一并从 knowledge 收回租户域** ✓）；**删除** knowledge 侧门面 `TenantStorageService` ✓，两个调用方（`FaqIndexWriter` 4 处、`KnowledgeMoveService` 1 处）改注入端口 ✓。
- **换锚（按路径冻结的第 5 类）**：ArchUnit 的"**允许的自建 SQL**"基线条目由 `com.ragagent.knowledge.storage.TenantStorageService` 换到 `com.ragagent.tenant.api.TenantStorageQuotaAdapter` ✓（理由同步注明"A③ 自 knowledge 收回租户域" ✓）。
- **收益**：**`knowledge→tenant` 整条边清零** ✓ ⇒ **边 61 → 60**；类 287 → 285。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m11s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向（Faq/KnowledgeMove/TenantStorage ✓）全绿。
- **A③ 累计（六刀）**：基线 **317 → 285 类（−32）**、**边 64 → 60（−4）**；**已清零的边**：`storage→tenant` ✅ · `session→auth` ✅ · `session→tenant` ✅ · `knowledge→tenant` ✅（`auth→tenant` 仍有 15 类 ✗，属下一批）。
- **下一步**：`auth→tenant`（15 类，`TenantService` 与 auth 各控制器/AuthBindingSupport 等 ⇒ 多为"读租户配置/实体"✓ 同手法可逐类清）；或 `memory→tenant`(1)/`system→tenant`(3)/`datasource→tenant`(1)/`embedchannel→tenant`(1) 这几条小边一并清（合计 6 类 ⇒ 再消 4 条边 ✓）。

**🔶 A③ 收敛第五刀：`session→auth` / `session→tenant` 收尾（2026-10-14，R10 **边 63 → 61**、类 292 → 287）**
- **迁移 4 类**：`session/service/{QaWiring, SessionKnowledgeQaService, TemporaryDocumentProcessor, TemporaryDocumentService}` —— 一律由**具体类** `auth.service.TenantService` ✗ 换成 **L1 端口** `TenantConfigLookup` ✓：
  - `QaWiring`：`PipelinePorts.TenantService`（chatpipeline 的**契约化**嵌套端口 ✓，只声明 `currentWebSearchConfig()` ✓）的实现源改为端口调用 `webSearchConfig(tid)` ✓ ⇒ 不再 import 实体 ✓；
  - `SessionKnowledgeQaService`：`RetrievalConfigView(tenant.getRetrievalConfig())` → `RetrievalConfigView(tenantService.retrievalConfig(id))` ✓；
  - `TemporaryDocumentProcessor`：`tenant.getParserEngineConfig().get("chatParserEngineRules")` → `tenantService.parserEngineConfig(id).get(...)` ✓；
  - `TemporaryDocumentService`：仅构造透传 ⇒ 换类型即可 ✓。
- **收益（两条边同时清零）**：**`session→auth` 4 → 0** ✓、**`session→tenant` 1 → 0** ✓ ⇒ **边 63 → 61**；类 **292 → 287**。
- **⚠️ 又栽在同一个坑（第 2 次）**：`s.replace('TenantService tenantService,', …)` 这类**无前缀排除**的整词替换，把 `PipelinePorts.TenantService tenantService,` 也改成了 `PipelinePorts.TenantConfigLookup` ✗ ⇒ 编译报错定位后修回 ✓。**教训升级**：替换类型名必须带**左边界排除**（`(?<![\w.])TenantService`），否则会穿透 `X.TenantService` 这种**限定名**；仓内已有 R8（内联全限定名）同族守卫，但**限定名做前缀**这一形态仍需人工小心 ✓。
- **测试**：`TemporaryDocumentProcessContractTest` 的 stub 由"造实体 + `getTenantById`"改为"造配置节点 + `parserEngineConfig`" ✓（helper 改名 `parserEngineConfigWithRule` ✓）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m03s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向（QaWebSearch/TemporaryDocument/SessionKnowledgeQa/QaWiring ✓）全绿。
- **A③ 累计（五刀）**：基线 **317 → 287 类（−30）**、**边 64 → 61（−3）**；已清零的边：`storage→tenant` ✗ ✅、`auth→tenant` ✅、`session→auth` ✅、`session→tenant` ✅；余量：`knowledge→tenant` 2 类。
- **下一步**：`knowledge→tenant`（2 类：`FaqIndexWriter`/`TenantStorageService` —— 引 `tenant.services.*` 或实体 ✗，同手法可清）；或换向 `knowledge→storage`(21) / `agenttools→agent`(114，最大边，需"族"级拍板) / v0.3.0 收尾对账。

**🔶 A③ 收敛第四刀（D）：`tenant` 类型族入契约面（2026-10-14，R10 基线 298 → 292 类，**边 64 → 63**）**
- **动机**：前几刀的余量多卡在"引 `tenant` 的**类型**"（`StorageEngineConfig`/`ChatHistoryConfig`/`RetrievalConfig`/`APIPrincipalConfig`）⇒ 把这一族入契约面可**一次解锁多处** ✓（与 A② 的类型级残留同族问题，但这一族**可解**：注解可分离、无 `JsonNode` ✓）。
- **落位**：4 类 → `contract.tenant`（`StorageEngineConfig` 含 **8 个嵌套 provider 子结构**：Local/Minio/Cos/Tos/S3/Oss/Ks3/Obs ✓ 随外层一起走）；**新增 `platform/web/ContractJsonMixinsTenant`**（4 个 mixin ✓，注册进 `ContractJsonModule` ✓——嵌套子结构**无注解**故不需要 mixin ✓，`@JsonInclude(NON_NULL)` 由 mixin 承载 ⇒ golden `ct-kv-storage-*` 形态不变 ✓）。
- **留下的两处（登记）**：`ParserEngineConfig`（含 **`JsonNode`** ✗ ⇒ 与 A② 类型级同族）；`APIPrincipalConfigTypeHandler`（MyBatis `ObjectMapper` 持久化件 ⇒ 属域内 ✓）。
- **迁移方式**：FQN 替换 19 文件 + **同包裸引用补 import 2 文件**（`tenant/**` 内部：`APIPrincipalConfigTypeHandler`、`repository/Tenant` ✓ ——"同包裸引用"这个坑第 4 次出现，已固化在记忆里 ✓）。
- **收益**：**`storage→tenant` 整条边清零** ✓ ⇒ **边数 64 → 63**；`session→tenant` 9 → **1**；基线 **298 → 292 类**。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m15s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；**线格式金片**（`JsonContractRoundTripTest` 35 条方法含 `tenantKvConfigsRoundTrip`/`kbConfigsRoundTrip` ✓ + storage 族 ✓）全绿 ✓ —— mixin 承载的租户配置形态与搬迁前一致 ✓。
- **A③ 累计（四刀）**：基线 **317 → 292（−25 类）**、**边 64 → 63**；`storage→tenant` **9 → 0** · `auth→tenant` **24 引用 → 0** · `knowledge→tenant` 3 → 2 · `session→auth` 11 → 4 · `session→tenant` 9 → 1。
- **下一步**：`session→auth` 余 4 类（`QaWiring`/`SessionKnowledgeQaService`/`TemporaryDocumentProcessor`/`TemporaryDocumentService` —— 均注入具体 `TenantService` 或走会话内端口 ⇒ 同第三刀手法 ✓，其中 `QaWiring` 走会话内端口属独立设计步）；或换向 `knowledge→storage`（21 处）/`agenttools→agent`（114 处，最大边）。
- **⚠️ 记录回正（第六刀时发现）**：本刀曾写"`auth→tenant` 整条边清零"——**不实** ✗：该边当时仍有 **15 类**（`TenantService` 等在 auth 侧仍引实体），边数 64 → 63 的减项是 `storage→tenant` ✓。已在第六刀处回正。

**🔶 A③ 收敛第三刀：`session` 的 `currentTenant()` 一族（2026-10-14，R10 基线 307 → 298 类）**
- **切面**：三个控制器共用同一模式（`private Tenant currentTenant()` → 传给 `Rewriter.forRequest` / `QaRequestParser`）⇒ 一刀多类 ✓，且**把第二刀留下的过渡重载一并收掉**（它的唯一用途就是这一族 ✓）。
- **迁移 5 类**：`controller/{MessageController, SessionStreamController, KnowledgeQaController}` + `qa/{QaRequestParser, QaTurnExecutor}` —— 字段/构造/`ObjectProvider<T>` 类型 `TenantService`（具体类 ✗）→ `TenantConfigLookup`（L1 端口 ✓）；`currentTenant()` 返回 `TenantStorageView`（`storageView(tid)` ✓）；解析器参数 `Tenant readerTenant` → `TenantStorageView readerTenant`；`QaTurnExecutor` 的存在性判断改 `storageView(...) != null` ✓。
- **收尾**：`storage/support/Rewriter` 的**过渡重载删除**（+ `Tenant` import 移除 ✓）⇒ `storage→tenant` 边再减 1 类 ✓；基线 note 里那段"过渡重载待收"说明同步作废 ✓。
- **收益**：R10 基线 **307 → 298 类**（`session→auth` **−4**、`session→tenant` **−4**、`storage→tenant` **−1**）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m05s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向（MessageController/SessionStreamController/KnowledgeQaController/QaTurnExecutor/QaRequestParser/Rewriter ✓）全绿。
- **A③ 累计（三刀）**：`storage→tenant` 9→4 · `knowledge→tenant` 3→2 · `session→auth` 11→4 · `session→tenant` 9→3；**基线 317 → 298 类**（−19 ✓ 均为实测收紧）。
- **下一步**：`session` 余下 `session→auth` 4 类（`QaWiring`/`SessionKnowledgeQaService`/`TemporaryDocumentProcessor`/`TemporaryDocumentService`）与 `session→tenant` 3 类（`MessageSearch`/`MessageService`/`QaWiring` —— 前两者仍引 `tenant.StorageEngineConfig`/`ChatHistoryConfig` 等**类型** ✗，属 `tenant` 类型族，与 A② 类型级残留同批处理更合算）；或换向 `auth→tenant`（24 处）。

**🔶 A③ 收敛第二刀：`session` 面（2026-10-14，R10 基线 312 → 307 类）**
- **端口扩容**：`common.tenant.TenantConfigLookup` 补 **3 个方法**（`webSearchConfig`/`chatHistoryConfig`/`parserEngineConfig`，均为 `JsonNode`，语义与既有同款："租户不存在或配置为空 ⇒ null"），实现在 `auth/TenantService`（新增私有 `configOf(tenantId, getter)` 统一取法 ✓）。
- **迁移 4 类**（session 面同时减两条边）：`service/{QaWebSearchParams, AgentToolBackends, MessageSearch, MessageService}` — `getTenantById(tid)` + `tenant.getXxxConfig()` 改写为 `tenantService.xxxConfig(tid)`（**语义等价**：端口把"租户不存在"与"配置为空"归并为 null，与调用原判据一致 ✓）；字段/构造类型 `TenantService`（**具体类** ✗）→ `TenantConfigLookup`（L1 端口 ✓ ⇒ 同 bean，零 wiring ✓）。
- **收益**：R10 基线 **312 → 307 类**（`session→auth` **11 → 8**、`session→tenant` **9 → 7**）；净减 5 类（4 个类各少 1 条 auth 边 + 2 个类少 tenant 边，另 1 条为死 import 清理 ✓）。
- **⚠️ 两条现场教训**：
  1. **整词替换会误改"同名嵌套类型"**：`QaWiring` 用的是会话内端口 `PipelinePorts.TenantService`（同名 ✗）⇒ 我的 `TenantService → TenantConfigLookup` 整词替换把它也改了 ✗ ⇒ 编译报错定位后**整文件回退** ✓（该类的租户接线属独立设计步：它已有会话内端口，迁移方式与普通类不同 ⇒ 登记）。
  2. **死 import 既是脏也是"假依赖"**：4 个类改完后仍留 `import …tenant.repository.Tenant;` ✗ ⇒ 它同时(a)触发死成员守卫、(b)让 R10 误以为该域仍依赖 tenant ✗ ⇒ 删净后基线才真正下降（312 → 307 的最后 2 类来自这里）✓。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **1m56s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向（MessageService 系 / ImRedisStore ✓；后者首跑因 Redis 连接失败，单跑即绿 ⇒ 同 flaky 家族，已登记）。
- **下一步**：`session` 面余下 8+7 类中，`QaWiring`/`SessionStreamController`/`MessageController`/`KnowledgeQaController` 共用 `currentTenant()` 模式（可一刀多类 ✓）；`QaRequestParser`/`QaTurnExecutor` 走会话内端口（独立设计步）；再往后 `auth→tenant`（24 处）/`knowledge→storage`（21 处）/`tenant.StorageEngineConfig` 类型族。

**🔶 A③ 收敛第一刀：`tenant` 枢纽（2026-10-14，R10 基线 317 → 312 类）**
- **为什么选 `tenant`**：实测五个枢纽的被引分布——`→tenant` 62 处但只 **9 个目标类**（`Tenant` 39 + `TenantLookup` 6 + 配置类等）⇒ 80/20 最优 ✓；反例：`→storage` 72 处散在 **28 个类** ✗、`→knowledge` 114 处 / 37 类 ✗、`→model` 的目标是 **Spring AI 运行时对象**（`ModelRuntimeAssembler` 等，签名不可能进纯 JDK 契约 ✗）。
- **机制（关键发现：端口与视图早就位）**：`common.tenant.TenantConfigLookup.storageView(long)` 与其嵌套 record **`TenantStorageView(long tenantId, String defaultStorageBackendId, JsonNode storageEngineConfig)`** 已存在（由 `auth/service/TenantService` 实现——它同时实现 `TenantLookup` ⇒ **换注入类型即换 bean，零新增 wiring** ✓）⇒ 本刀是**纯迁移**：`Tenant` 实体 / `TenantLookup` ⇒ L1 端口 + 视图。
- **落位**：storage 侧 6 类（`fileserve/{ChatLocalImageResolverWiring,FileProxyService,FileserveStorageBackendResolver,StorageFileResolver}`、`support/FileServiceResolver`、`support/Rewriter`）+ knowledge 侧 1 类（`storage/TenantFileStorage`）+ 测试 5 个文件；`hydrateTenantStorage` 由"造新实体"改为"造新视图"（不再触碰实体 setter ✓）。
- **收益**：R10 基线 **317 → 312 类**（`storage→tenant` **9 → 5 类**；`knowledge→tenant` 减 1；留在边上的 4 类是 `tenant.StorageEngineConfig` **类型**引用 + 1 个过渡重载）。hmm 目标类 `StorageEngineConfig`（8 个 provider 子结构 + Jackson 注解）⇒ 属"A② 类型级残留"同族，另批。
- **⚠️ 三条现场教训**：
  1. **R8 与 R10 的盲区互补，接力钉出"隐形依赖"**：过渡重载里我写了**内联全限定名** ⇒ **R8 判红** ✓；改成 import 后，该依赖从 R10 的"import 扫描盲区"**现形**、被判"新增" ✓✓ —— 一条依赖被两个守卫接力抓出，正是"判据必须自证"的价值。
  2. **签名收窄的涟漪要按"可控切面"截断**：把 resolver 的 `Tenant` 参数收成视图后，涟漪顺着调用链穿到 `session`（3 类 → 再 6 类 …）✗ ⇒ 处理＝**回退 session 侧签名改动** + 在切面边界保留**过渡重载** `Rewriter.forRequest(Mode, Tenant, …)`（1 处实体引用换掉全链迁移 ✓），并在 javadoc 写明"session 面迁完后删本重载"。
  3. **"只许减"的守卫会拒绝合法新增** ⇒ 处理方式是**明账登记**（手工加基线条目 + 在 note 里写清收回条件），**不绕过守卫** ✗。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **2m07s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；定向测试（Tenant/FileProxy/Rewriter/FileServiceResolver/ChatLocalImage ✓）全绿。
- **下一步（同手法）**：`session→tenant`（9 类）与 `session→auth`（11 类，因注入的是**具体类** `TenantService` ⇒ 换 L1 端口可**一刀减两条边** ✓）；再往后是 `tenant.StorageEngineConfig` 类型族（与 A② 类型级残留同批）。

**✅ A③ 立「域间只经契约」（2026-10-14，新守卫 R10 —— 第 13 个守卫）**
- **规则**：**业务域 A 不得 `import com.ragagent.<业务域B>.*`（A≠B）**；跨域调用只许经 **`contract.*`（端口/载荷）** 或**装配层**（`config`/`platform`/`common`）。域清单取方案 §8.9 的 **24 域**（脚本内显式列出，新增域须同步——否则新域的跨域引用不会被看见）。
- **判据为什么只扫 import 就够**：跨域必然跨包 ⇒ 一定有 import（与 A② 的"同包裸引用"坑不同——那条坑只在**同包**存在）。静态 import 一并覆盖；内联全限定名由既有 **R8** 守 ✓（三方互补）。
- **立规实测（现状）**：域→域直接依赖 **64 条边 / 317 个类（约 500 处引用）** ⇒ 一次清零＝K 级重构 ✗ ⇒ 按仓内惯例（A16/A17/A11b 同族）立**棘轮**：新增**边**或**边内新增类**立即判红，减小后 `--write` 收紧。
  - Top 边：`agenttools→agent` **114**、`session→knowledge` **53**、`session→agent` 46、`session→agenttools` 42、`session→storage` 41、`mcp→approval` 29、`mcp→agent` 26、`datasource→knowledge` 25 …
  - 枢纽域（入度）：`agent` **191** · `knowledge` **114** · `storage` 72 · `model` 68 · `tenant` 62 · `agenttools` 42 · `session` 38；源域（出度）：`session` **278** · `agenttools` 114 · `mcp` 59 · `knowledge` 58。
- **`--write` 安全阀**：**存在新增项时拒绝写入**（否则"顺手 --write"会把新增一起吞掉——与 `check-package-cycles.py` 的教训同族）；首建（基线文件不存在）才允许直接登记现状 ✓。
- **能红自测** ✓：临时注入两类违规——①新边（`favorite→knowledge`）②既有边新增类（`session→knowledge` 新类）——**两类都判红、退出码 1** ✓，清理后回绿 ✓；`--write` 在新增存在时**拒绝** ✓（探针跑清即删）。
- **接入守卫链**：`run-guards.sh` 加入第 13 个守卫 ⇒ 汇总行 **`✓ 13 守卫全绿`**（**B267 的 `ran` 自计数机制第一次派上用场**：加守卫无需改任何文案 ✓）。
- **收敛路径（登记，另批）**：① 优先处置高入度枢纽——把 `agent`/`knowledge`/`storage`/`model`/`tenant` 对外的**能力**提到 `contract.*` 端口（已有的 12 个端口模式可循 ✓），调用方只 import contract；② 运行面之间的重耦合（`session`/`agenttools`/`agent`/`agentmanagement` 相互 278+114 处）可能需要**"族"级定义**（方案未给 ⇒ 属架构决策，需拍板）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；`spotlessCheck build` ✓ **1m45s** ✓ **13 守卫** ✓（环 0 / SCC 11 组）；`arch.*` 28 条 ✓。
- **v0.3.0「§5.1 走到底」进度**：① platform 成员收纳 ✅ · ② 契约面收纳 ✅ · **③ 域间只经契约 ✅（本次）**。

**🔶 A② 契约面收纳第①批（2026-10-14，**v0.3.0**：42 类 → `contract.*`，§8.1 走到底）**
- **方法（本条最重要的资产）**：① **纯度盘点**——`common/**`+`event/**` 逐类判"纯 JDK / 沾框架"⇒ **55 纯 / 42 沾框架 / 11 待裁决**；② **依赖闭包**——R1 只允许契约类依赖 JDK+自身，故只有**闭集**能整批搬（迭代剔除依赖落在集合外的）⇒ 初算 51，增补 6（3 个跨域端口 + `AgentChannelCleaner`（本就是契约端口）+ 2 个 apikey 响应 DTO——被 `ApiKeyAdminPort` 签名引用，只能留契约侧）。
- **落位**：**42 类**入 14 个契约面（`contract/` 现 43 类 + 15 份 package-info）；`common/**` 从 90 类降到 **48 类**。FQN 替换 261 文件。
- **换锚**：`FROZEN_PREFIXES` 与冻结快照（`ExtractedItem` 一度被我**错锚**——该类其实沾 Jackson 未搬 ⇒ 当场回退 ✓）；**A20 判据扩容**（`common`+`contract` 双前缀——防移植后规则静默失覆盖 ✓）；引擎边界名单 `+contract`；SCC 基线**外科收紧**（wiki 组去掉已离开成员，组数 12→**11**）。
- **⚠️ 七处自伤与教训（本批最值钱的部分，全部由守卫/编译/规则抓出）**：
  1. **面名当包名**：`package {face};`（少了 `com.ragagent.`）⇒ 71 处声明 + 457 处 import 全错 ✗（编译暴露）；
  2. **闭包只算 import，漏"原同包裸引用"** ⇒ 9 个类搬走后仍引 `common.*` ✗（**R1 当场抓回**，为规则立功）；
  3. **回退脚本漏剥 `.java`** ⇒ 包声明变成 `com.ragagent.common.knowledge.ChunkSearchGateway` ✗ ⇒ **B263㉖ 路径/包对应守卫（P2）点名** ✓✓（守卫立功）；
  4. **补 import 缺"简单名已被别的 import 占用"判据** ⇒ 同名类型冲突（连续两批各一次）✗；
  5. **遮罩 import 清理规则过宽** ⇒ 删掉**合法**的 `io.opentelemetry.proto.trace.v1.Span`（Java 允许单类型 import 遮罩同包同名）✗；
  6. **"清理未使用 import"宽扫误删 javadoc-only import**（29 文件）⇒ javadoc 报 24 处 `reference not found` ✗ ⇒ 升级为 **javadoc 驱动的全限定修正器**（21 处自动 + 3 处手工）；
  7. **payload 6 类**依赖 `QueryData.orEmpty`（**包私有**）⇒ 半个面搬不动 ⇒ 回退（必须整批 26 类一起去 Jackson）。
  ⇒ **结论：宽扫类脚本（批量补/清 import）风险最高，必须带"同名占用 + 同包/自身 + javadoc 引用"三重判据；编译驱动的定向修补优于宽扫**（本批后半程改用此法才收敛）。
- **验证**：**4987 / 失败 0 / 跳过 4** ✓；启动测试 ✓；`spotlessCheck build` ✓ **1m54s** ✓ **12 守卫** ✓（环 0 / 包级 SCC **11 组**）；`arch.*` **28 条** ✓（R1 覆盖新契约类、A20 双前缀、A19、A16 均绿）；四模块 `javadoc` ✓。
- **flaky 发现（登记）**：`EmbedRateLimiterTest.redisPathSharesBudgetAcrossInstances` 全量**首跑红、单跑与重跑绿** ⇒ 嵌入式 Redis + 跨实例共享预算在并行下抖动，与本批改动无关。
- **登记（A② 余项）**：**42 个沾框架类**待"去框架化"（Jackson 28 / Spring 3 / Servlet 1 / …：`AppError`、5 个 knowledge 元数据、3 个 llm 值对象、`TenantProperties` 等）；**payload 26 类**待整批；`BizException`（计划归 platform.web.error，但契约侧 `TenantAPIKeyScope` 仍在用 ⇒ 移动会造 contract→platform 反转）· `TextConv`/`Whitespace`/`JsonMappers`/`SortedMapSerializer`（被契约面共用 ⇒ 留 L1 底座）；因闭包外依赖回退的 9 类：`TenantAPIKeyScope`←`BizException`、`PipelineMessageView`←`SearchResult`、`WikiImageMarkup`/`WikiLanguageSupport`←`Whitespace`、`ChunkSearchGateway`/`KnowledgeBaseLookup`/`KnowledgeDocumentGateway`←`KnowledgeBaseView`(沾 Jackson)、`VectorStoreView`←`ConnectionConfig`/`IndexConfig`、`EmbeddingModelPort`←`DocumentChunkMetadata`、`SearchChunkMerge`/`SearchTarget`←`SearchResult`/`SearchParams`、`APIKeyScopeContext`←`TenantAPIKeyScope`。

**✅ 缺陷修复（2026-10-14，knowledge 处理链租户作用域缺失 ⇒ 1003 "Model not found"）**
- **现场**：上传文档后 `parse_status=failed`、`error_message=error code: 1003, error message: Model not found`；`knowledge_processing_spans` 显示 docreader / chunking **done**、**embedding 阶段**失败（两次自动重试同点失败）；`chunks=0` 是 K4 失败清场的结果。
- **根因（实测坐实，非本批引入）**：KB（租户 11）的 embedding/summary 模型是该租户**自建**（`is_builtin=false`）；`ModelService.getModelByID` 按可见性过滤 `tenant_id = 当前租户 OR is_builtin`，而"当前租户"读 `TenantContext`（ThreadLocal）——**worker 虚拟线程没有 HTTP Filter 链填它** ⇒ 按 0 查 ⇒ **0 行**（同一过滤式按 11 查 **1 行**，已现场对照）。wiki 摄取早有 `enterTenantScope` 修过同款，knowledge 侧没有；K10b 之前的旧路径同样走 `ModelRuntimeAssembler → getModelByID` ⇒ 旧缺陷。
- **根治（五处）**：
  1. **新共享助手** `common/context/TenantScopes`（把 wiki 的局部实现提上来；`close` 恢复**全部 8 个字段**，含访客 / 请求 id）+ `TenantScopesTest`（3 例）。
  2. **knowledge 解析链**：`KnowledgeProcessWorker.enqueue` 在**提交线程**捕获租户（与 traceparent 同一套"跨线程显式传值"），`process` 进作用域；捕获不到（后台入队 / 回放）回落 **`KnowledgeTenantResolver`** 读知识行租户；都无 ⇒ `enterCaptured(null)` 保持原行为。
  3. **富化队列**：`ChunkExtractService` / `QuestionGenerationService` 的 `handleJson` 按 payload 已携带的租户进作用域 —— 该 KB `question_generation_config={"enabled":true}` ⇒ 不修就是"下一个 1003"。
  4. **memory**：`MemoryExtractionService.handle` 进 payload 租户作用域（主体→`handleInScope`）；`MemoryVectorService.embedText` 的**嵌套虚拟线程**改为"提交线程 capture + 任务体 enter"。
  5. **wiki 去重**：`WikiBatchSupport.enterTenantScope` 委托共享助手（调用点仅换类型）。
- **探针（可证伪）**：`KnowledgeProcessWorkerTenantScopeTest`（2 例）——把 `process` 里的 `TenantScopes.enterCaptured(...)` 临时换成 `null` ⇒ **2 例当场转红** ✓，还原 ⇒ 绿 ✓。
- **守卫两处现场修正**：① 新测试带 2 个未用 import ⇒ **B131 死成员守卫**点名 ⇒ 删；② worker 824 行越过 **≥800 需登记**线 ⇒ 抽 `KnowledgeTenantResolver`（解释随逻辑落座）+ 精简 javadoc ⇒ **797 行**；`file-size.baseline.json` 用**外科式收紧**（worker 789→797 + 删过期条目 `KnowledgeBaseService 565`），**拒绝 `--write`**（它会把 `GitLabConnector 602→603` 这类**回涨**一起吞掉）。
- **测试**：**4987 / 失败 0 / 跳过 4**（domains 2949→**2951**、common 141→**144**，+5 = 新测试）；启动测试 ✓；`spotlessCheck build` ✓ **1m55s** ✓ **十二守卫** ✓；`:domains:javadoc` / `:common:javadoc` ✓；`arch.*`（含 A19/A20）28 条 ✓（包级 SCC 12 组不变）。
- **登记（同族未做，均核实无需）**：`MemoryRunBudget.CALLS`（body 不查模型）、`MemoryService` 的 `memory-touch` 后台线程（仅 DB touch）、`WikiBatchSupport.runParallel` 仍用裸 `TenantContext.set/clear`（语义等价，未换助手）。

**🔶 B263 第㉟步（2026-10-14，第二个子面 `service.chunk` 切出）**
- **动前断言（把"可测"变成"可证"）**：脚本**先断言** `ChunkEditService`/`ChunkExtractService`/`ChunkReadService` 三者在 `service` 包内**入度为零**（无人引用，`package-info` 除外），断言不过就 abort —— 这样"切出只产生单向外边"不再靠推理。`ChunkQuestionService` **故意留在** `service`：它被 `QuestionGenerationService` 引用，单独搬会造出 `service ⇄ service.chunk` 环（已在子面 package-info 写明）。
- **切出**：3 类 → `com.ragagent.knowledge.service.chunk`（`git mv` + 包声明 + 全仓 FQN 替换 3 文件）；测试面 `ChunkExtractServiceTest` 随迁；新建 `service/chunk/package-info`；`service/package-info` 同步。
- **本步新踩的三类"换包连带"（都已修，值得记住）**：
  1. **同包 javadoc `{@link SimpleName}` 会失链** ✗（`:domains:javadoc` 报 `reference not found`）——FQN 正则抓不到它（它是**简单名**）⇒ 改成全限定形式（仓内既有风格）；并写成**全量扫描**（凡 javadoc 里指向旧同包类且无 import 的，一次改完）。
  2. **`FROZEN_PREFIXES` 里存在"按文件"的前缀** ✗（`knowledge/service/ChunkExtractService`）⇒ 换包即静默失效，守卫原话点名"离开/进入" ⇒ 换锚到新路径 + `--write` 刷冻结快照（**diff 恰一行** = 纯换锚 ✓）。
  3. **别的模块的测试树里也有同名包** ✗（`boot/src/test/java/com/ragagent/knowledge/service/Chunk*Test.java` 裸引用被搬走的类）⇒ 补 import 器需把编译目标扩到 `:boot:compileTestJava`（只跑 `:domains:*` 会漏）。
- **测试**：**4980 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m46s** ✓ **十二守卫** ✓（环 0 / **包级 SCC 12 组**不变）。
- **登记（`service` 其余子面）**：门面 `KnowledgeService` 闭包仍 **18 类**（其 6 个引用者是归位的最后一关）；`ChunkQuestionService` 需与 `QuestionGenerationService` 的边一起处置；`KnowledgeTagService`（零入度）可作下一簇 `service.tag`。`KnowledgeService` 闭包 = **18 类**（`KnowledgeService` + 3 双向伙伴 + 3 单向使用者 + 11 个被依赖者）⇒ 下一簇可切的目标是这 18 类里**离门面最远的自洢子集**（`Chunk*` 四类与 `HousekeepingService`、3 个 adapter 已在闭包外）；门面本体归位仍需先让这 18 类继续收缩。按上述闭包把「`KnowledgeService` + 3 个双向伙伴 + 3 个单向使用者（+ 递归闭包）」整体搬进目标子面（`service.management` 与其协作者面），届时 SCC 不扩大、门面归位。`KnowledgeService`(717) → `service.management/*`（**需配套断边**：B263① 实测直接搬会让 SCC 扩大）。① ㉜ `SpanTracker` 移出 service（`domain.span` 候选）⇒ 断最后一处 task→service，`{service, task}` SCC 收口；② `KnowledgeService`(717) → `service.management/*`（需配套断边）。① `KnowledgeService`(716) 余部 → `service.management/*`（**需配套断边**）；② **`task ⇄ service` 环**（task→service 6 文件 / service→task 11 文件）。① `KnowledgeService`(716) 余部 → `service.management/*`（**需配套断边**：B263① 已实测"往子面搬会让 SCC 扩大"）；② **`task ⇄ service` 环**（现测：task→service **6** 文件 / service→task **11** 文件）；③ `escapeLikeKeyword` **多副本统一到 `common`**（§9.2 式，⑯ 登记）。
**🔶 B263 第㉘步（2026-10-14，`KnowledgeProcessWorker` 归仓储：824 → 792 行）**
- **本步 16 处 + 9 个新仓储方法**（都是"CAS / 条件 / 硬删"形态，SQL 逐字搬入）：`casPendingToProcessing`、`setSummaryStatus(id, status, now)`（带 updated_at 的重载）、`markIndexedEnabled`、`promoteToFinalizing`（processing→finalizing + 子任务计数）、`completeIfOwned(id, resetSummaryStatus, now)`（**K4 CAS**：只有 processing/finalizing 才许翻 completed，`resetSummaryStatus` 为真时额外写 `summary_status=none`）、`failIfProcessing`（只许 processing → failed）、`ChunkRepository.deleteByKnowledgeId`、`deleteByIds`、`countByKnowledgeIdAndChunkType`。
- **顺手清掉一个已废弃 API**：`chunkMapper.deleteBatchIds(...)`（**MyBatis-Plus 已标 deprecated**，IDE 一直在提示）⇒ 仓储内改用非废弃的 `chunkMapper.deleteByIds(...)` ✓（外部调用方只看到 `deleteByIds(List<String>)`）。
- **本步的"意外收获"（守卫自己抓出来的）**：文件降到 **789 行（<800）** 后，`check-file-size.py` 立刻报 **R-e 过期自称**——源码 javadoc 里还写着「规模例外（~810 行）」✗ ⇒ 三处同步：① javadoc 改为现状陈述（并且**不再出现"规模例外"字样**——第一版我写成"曾长期…并登记规模例外"，守卫仍判为活跃自称 ✗ ⇒ 干脆不写这个词 ✓）；② `file-size.baseline.json` 的 `limits` 收紧 **843 → 789**；③ 删掉 `exempt` 条目（**该文件从此不再是规模例外** ✓）。**这条守卫的价值现场可见：它比人更早发现"例外已随切片解除"。**
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m42s** ✓ **十二守卫** ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：`KnowledgeService`(716) 余部 → `service.management/*`（需配套断边）、`task ⇄ service` 余 7 处、`escapeLikeKeyword` 多副本统一到 `common`、`service` 七子面与 `domain.{document,task,graph}` 归位。
**🔶 B263 第㉗步（2026-10-14，包外 3 类归仓储）**
- **范围**：`api/KnowledgeFinalizeAdapter`（wiki 收尾适配器）、`index/ChunkVectorIndexer`、`domain/access/ChunkAccessGuard` ⇒ 三者 mapper 直连**全部清零**（`ChunkAccessGuard` 404 → 373 行、`ChunkVectorIndexer` 392 → 386 行）。
- **动前先查环（本步第一步）**：`domain.access → repository` 会新增一条包间边 ⇒ 先确认 `repository/*` **不引用** `ChunkAccessGuard`/`domain.access`（实测空 ✓）⇒ 改完 SCC 仍 13 组、环 0 ✓（R9 复核在场）。
- **新增 3 个仓储方法**（都是"条件/原子"形态，逐字搬 SQL）：`KnowledgeRepository.decrementPendingSubtasks(id, now)`（**原子递减并钳在零**：`gt(pendingSubtasksCount, 0)` + `setSql(...)`）、`promoteFinalizingToCompleted(id, now)`（带守卫晋升：`finalizing` + 计数为 0 ⇒ completed）、`ChunkRepository.findByIdAny(id)`（**不过滤软删**，与 `selectById` 等价——调用方自己判 `deleted_at`）。
- **复用既有**：租户内/仅 id 的 knowledge/KB 读取共 10 处 → `KnowledgeRepository.findById(tenantId, id)`/`findByIdOnly(id)`、`KnowledgeBaseRepository.findById(tenantId, id)`/`findByIdOnly(id)`；`ChunkAccessGuard` 里**同时存在的** `chunkMapper` 与 `chunkRepository` 两个入口合并为一个（删 `chunkMapper`）。
- **测试跟着改（保留值钱的断言）**：`KnowledgeFinalizeAdapterTest` 的桩是**wrapper 级**（captor 抓 `LambdaUpdateWrapper` 断言 SQL 文本与守卫条件）⇒ **不改成 mock 仓储**（那会丢掉 SQL 断言），而是让**真实仓储包住 mock mapper**（`new KnowledgeFinalizeAdapter(new KnowledgeRepository(knowledgeMapper))`）⇒ 全部桩/断言原样保留 ✓。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m36s** ✓ **十二守卫** ✓。
- **偶发（环境，已按约定先重跑）**：本轮两次全量中出现两个**不同**的计时/Redis 型偶发——`RedisWikiFinalizeLockTest.redisErrorFailsClosed`、`QaQueueTest.workerDrainsQueueAndExitsOnStop` ⇒ 各自**强制单跑均绿**、随后全量复跑 4978/0 ✓。**与本次改动无关**（改动只是仓储间接层）。
- **登记（余步）**：`task/KnowledgeProcessWorker`（**824 行超大文件**）——含 `delete`/`deleteBatchIds`（**已废弃 API**）/`insert` ⇒ 需加删除类仓储方法，**下一批（㉘）**；其后 `KnowledgeService`(716) 余部 → `service.management/*`（需配套断边）、`task ⇄ service` 余 7 处、`escapeLikeKeyword` 多副本统一到 `common`。
**🔴 B263㉖（2026-10-14，用户报 IDE 报错 ⇒ 修「改包不搬目录」+ 补第十二守卫「路径/包对应」）**
- **现象**：用户贴来 20+ 个测试文件的 IDE 报错——`EmbeddedRedis cannot be resolved`（连带 `withBean(...)`/`await(...)` 级联），而**`./gradlew build` 一直全绿**。
- **根因（B259 漏搬 testFixtures）**：`common/src/testFixtures/java/com/ragagent/stream/EmbeddedRedis.java` 的包声明在 B259 被改成 `com.ragagent.platform.stream`，但**文件留在旧目录**——当时脚本只枚举了 `src/main` 与 `src/test` 两个源码集，**漏了 `src/testFixtures`**。`javac` **不校验**目录/包对应（照样编到新包名下），所以构建、测试、闸门全绿，**只有 IDE（jdt）报** "declared package does not match the expected package"。
- **为什么后来连测试都红了**：`git mv` 后 Gradle 的 fixtures 产物目录里**缺该类**（任务被判 UP-TO-DATE ✗）⇒ `:common/:engine/:domains:compileTestJava` 找不到同包符号 ⇒ 强制重编（`--rerun-tasks`）后产物回到 `build/classes/java/testFixtures/com/ragagent/platform/stream/EmbeddedRedis.class` ✓。
- **修复**：`git mv` 到 `common/src/testFixtures/java/com/ragagent/platform/stream/EmbeddedRedis.java` + 删旧空目录（B124 目录卫生）。**全仓审计**：路径与包不一致的文件**恰为这 1 个**（脚本先自查出 2454 的假阳，是审计脚本自己漏了 `src/` 段，修正后为 1）。
- **新守卫（十二守卫）**：`scripts/check-package-dirs.py`——`*/src/*/java/**` 下每个 `.java` 的**相对包根目录必须等于包名**（零容忍、无基线；排除 `build/`）；接入 `run-guards.sh`（**十一 → 十二**，活文档/README/CI 注释同步，历史归档不动）；**探针已验红**（临时造 `package com.ragagent.probe.wrong;` 的文件 ⇒ 精确报 P2 + 处置指引 ⇒ 删除即绿）。同时把 `check-stray-dirs.py` 的 `SOURCE_ROOTS` **补上 `testFixtures`**（死包目录同样会从这类漏枚举里溜过）。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m27s** ✓ **十二守卫** ✓。
- **经验（换锚族长新成员）**：**「改包」必须同时「搬目录」，且要枚举所有源码集（main/test/testFixtures）**；`javac` 不报错 ⇒ 必须靠守卫（`check-package-dirs.py`）。
- **连带清理（同一份 IDE 诊断里的 main 代码警告）**：`KnowledgeProcessWorker` 的死成员——`private int embedBatchSize()`（无人调用）与 `private final EmbedderClient embedder`（只赋值、从不读）⇒ 删字段/构造参数/赋值与死方法（构造点只有 Spring 一处、无测试构造，安全）；`KnowledgeProcessWorker` **844 → 824 行**。**登记**：同一处还有 `chunkMapper.deleteBatchIds(...)` 已废弃（IDE 提示）——它正落在下一步 ㉗（`task/KnowledgeProcessWorker` 归仓储）的范围内，届时一并处理。
**🔶 B263 第㉕步（2026-10-14，`KnowledgeBaseLookupAdapter`：**service 包 mapper 直连收尾**）**
- **本步的关键判断是"不搬"**：该类持 `ObjectProvider<KnowledgeMapper>` 并用 `getIfAvailable()` 保守返回，是**刻意的装配裁剪容忍**（注释原文："无知识仓储可查（装配裁剪）——无法判定，保守地当作'还在'"）。若换成 `KnowledgeRepository`（无条件 Bean、构造期就要求 mapper），"缺 mapper"会从**保守返回**变成**上下文启动失败** ⇒ **语义退化**，故**保留**该可选依赖（与 R6 禁 `@Lazy` 规避环无关），并在类 javadoc 写明这条理由（防后人误"清理"）。
- **能搬的都搬了**：两处 KB 读取 → `KnowledgeBaseRepository.findByIdOnly(kbId)`（带软删过滤）与 **`findByIdAny(kbId)`**（`knowledgeBaseByIdIncludingDeleted`，⑱ 新增的"不过滤软删"读法，正好同形）。
- **结果**：`service` 包 mapper 直连**清零**（唯一保留项即上面这条刻意的可选 mapper，A16/A17 条目**保留**且已注明理由）。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第㉔步（2026-10-14，`KnowledgeCloneService` 归仓储：480 → 451 行）**
- **只新增 1 个方法**：`ChunkRepository.listActiveByKnowledgeId(tenantId, knowledgeId)`（租户 + knowledge_id + 未软删）；其余 15 处全复用——`findById`（3 处租户内 KB 读，含 `TenantContext.currentTenantId()` 变体）、`findByIdOnly`、`knowledgeRepository.listByTenantAndKb`（源/目标两列表）、`kbRepository.listByTenant`（重名探测的 KB 列表）、`softDeleteIfActive`（㉑ 新增，正好同形）、`ChunkRepository.softDeleteByKnowledgeId`、三处 `insert`（KB/knowledge/chunk）。
- **连带**：A17 删 1 条（A16 保留）；死成员守卫点名 2 个未使用 import 清掉；`ChunkMapper` 字段换成 `ChunkRepository`。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m42s** ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第㉓步（2026-10-14，`KnowledgeMoveService` 归仓储：383 → 343 行）**
- **新增 3 个"搬行"形态方法**：`KnowledgeRepository.moveRowToKnowledgeBase(id, targetKbId, now)`（reuse_vectors 终态：改 KB + 落 completed + 清 error + 推 updated_at）、`resetRowForReparseInKnowledgeBase(id, targetKbId, embeddingModelId, now)`（reparse 待解析态：改 KB/嵌入模型 + pending + disabled + 清描述/时间/容量）、`ChunkRepository.moveToKnowledgeBase(knowledgeId, targetKbId, now)`。
- **复用既有**：两处租户内按 id 读行 → `findById(tenantId, id)`；**四处** KB 读取（source/target 两变量名 × 两方法）→ `KnowledgeBaseRepository.findByIdOnly(id)`；两处标签关联清理 → `KnowledgeTagRepository.deleteRelations(knowledgeId)`（⑨ 已落位）。
- **连带**：A17 删 1 条（A16 条目保留——仍依赖 `KnowledgeProcessingQueue`/进度存储等 task 面）；死成员守卫点名 3 个未使用 import 清掉；`ChunkMapper` 参数删除（类里本就注入 `chunkRepo`）。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第㉒步（2026-10-14，`KnowledgeSummaryService` 归仓储：687 → 661 行）**
- **只新增 1 个仓储方法**：`KnowledgeRepository.setSummaryStatus(id, status)`（pending/failed 两态共用，5 处调用点收敛）；其余 9 处**全部复用**——`findById(tenantId, id)`（2 处）、`findByIdOnly(id)`（**无租户**的 post-process 读，语义正合）、`KnowledgeBaseRepository.findByIdOnly(id)`、以及类里**本来就有的** `chunkRepo`（`chunkMapper` 是历史遗留的第二个入口 ⇒ 删掉，`updateById`/`insert` 改走 `chunkRepo`）。
- **连带**：A17 删 1 条（A16 条目保留——仍依赖 task 面）；死成员守卫点名的 2 个未使用 import 清掉；构造器去掉 `ChunkMapper` 参数。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m41s** ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第㉑步（2026-10-14，`KnowledgeBatchOpsService` 归仓储：148 → 126 行）**
- **新增 3 个仓储方法**（都是"条件/批量"形态，逐字搬 SQL）：`softDeleteIfActive(id, now)`（**带 `isNull(deleted_at)`** 的软删——与既有 `softDelete(id, now)` 的区别就在这个条件，故**不复用**、单列并写明差异）、`setParseStatusDeleting(ids, now)`、`updateStatusColumns(id, parseStatus, errorMessage, enable, now)`（把 worker 的**条件列更新**语义整块搬进仓储：`errorMessage`/`enable` 为 `null` 表示不更新该列）。
- **复用既有**：`chunkMapper.update(...set deleted_at)` → `ChunkRepository.softDeleteByKnowledgeId`（实现逐字相同，已核对）；两处 `selectList(kbId + tenant + 未软删)` → `listByTenantAndKb`；`selectById(id)` → `findByIdAny`（⑱ 新增的"不过滤软删"读法，正好用于 worker 跨请求线程加载）。
- **连带**：A17 删 1 条（该类仍依赖 `KnowledgeProcessingQueue` ⇒ A16 条目**保留**）；死成员守卫点名的 3 个未使用 import 清掉。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第⑳步（2026-10-14，`FaqEntryQueryService` + `FaqEntryCommandService` 归仓储）**
- **FAQ 对的查询全在 tag 侧 + 知识行读取**：`KnowledgeTagRepository` +2——`listTagsByKbPage(tenantId, kbId, limit, offset)`（原样透传，分页归一与关键词过滤仍归 {@code listByKb}）、`listTagsBySeqIds(tenantId, seqIds)`；其余全部复用既有：`getBySeqId`（= `selectByTenantAndSeqId`，已核对逐字同形）、`listTagsByIds`（= `selectByTenantAndIds`）、`KnowledgeRepository.findById(tenantId, id)`（Command 的 3 处知识行读取同形，批量替换）。
- **替换顺序有讲究**：`tagMapper.selectByTenantAndSeqId(` 是 `…AndSeqIds(` 的**前缀** ⇒ 必须先替换复数、再替换单数，否则单数规则会把复数调用改坏（本步按此顺序执行）。
- **连带**：A16 删 1 条（`FaqEntryQueryService`）/ A17 删 1 条（`FaqEntryCommandService`）；两个类的字段/构造器/import 换仓储；`FaqEntryCommandService` 784 → 773 行。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m39s** ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第⑲步（2026-10-14，`SpanTracker` + `FaqBatchOps` + `FaqImportService` 归仓储）**
- **修掉一处包内字段直取坏味道**：`FaqBatchOps` 原本是 `service.knowledgeMapper` / `service.tagMapper` 直取 `FaqImportService` 的**包内字段**（这也是那两个字段不能收紧为 private 的原因）⇒ 改为 `FaqBatchOps` 自己注入 `KnowledgeRepository` / `KnowledgeTagRepository`（构造器在包内 `new` 出来，一处调用点同步），并把 `FaqImportService` 的这两个字段**收紧为 private**。
- **新增两个仓储方法**：`KnowledgeRepository.touchUpdatedAt(id, now)`（心跳——**不按租户、不看软删**，与 SpanTracker 原直连语句逐字一致）；`KnowledgeTagRepository.deleteUnusedTags(tenantId, kbId)`（replace 导入后清未引用标签）。
- **复用既有方法**：`FaqBatchOps` 的 tag 读取 → `KnowledgeTagRepository.listTagsByIds`（与 `tagMapper.selectByTenantAndIds(List.of(id))` 同形）；`FaqImportService` 三处按 id 读行 → `KnowledgeRepository.findById(tenantId, id)`（含 `tenantId()` 与 `job.tenantId()` 两个变体）、FAQ 容器列表 → `listByTenantAndKb`、`updateById` → 仓储。
- **连带**：A16 删 1 条 / A17 删 3 条；死成员守卫点名的 3 个未使用 import 清掉；`FaqImportService` 566 → 558 行、`SpanTracker` 702 → 700 行（**超大文件基线内**，路径未变故无需换锚）。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
**🔶 B263 第⑱步（2026-10-14，5 个小块类归仓储：`ChunkExtractService`/`ChunkQuestionService`/`ChunkEditService`/`QuestionGenerationService`/`ChunkReadService`）**
- **关键语义判断（本步最花心思处）**：两处 `mapper.selectById(...)` 与三个 `selectOne(...)` 看似都能用现成仓储方法，实则**不等价**——`Knowledge( Base)Repository.findByIdOnly(id)` **过滤软删**，而 `selectById` **不过滤**；管线任务（图抽取/问题生成）正是要**看到已软删行**才能判定 aborted 并跳过 ⇒ 新增 `findByIdAny(id)`（Knowledge 与 KB 各一，javadoc 写明"与 `findByIdOnly` 的区别"），**不复用**。
- **其余三处复用既有方法**：`ChunkReadService.findForSummaryReload` → `KnowledgeRepository.findById(tenantId, id)`（`LogSanitizer.sanitize` 保留在服务侧、参数位置不变）；`ChunkEditService.findKnowledgeRow` → 同上；`ChunkQuestionService.findKbRow` → `KnowledgeBaseRepository.findByIdOnly(kbId)`（原查询本就带 `isNull(deletedAt)` + `LIMIT 1`，逐字等价）。
- **连带**：5 个类的字段/构造器全换仓储（其中 `ChunkExtractService` 有**委托构造器**、`ChunkReadService` 是**单行参数表** ⇒ 第一版精确串替换漏了两处，改用灵活正则补齐）；**A16 删 5 条 / A17 删 3 条**（条目清单直接从 ArchUnit 失败信息里解析出来，逐条核对后删）；死成员守卫点名的 2 个未使用 import 清掉；`ChunkExtractServiceTest` 的 mock 由 mapper 改为仓储（桩改 `findByIdAny`）。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **经验（已存记忆）**：搬包/换依赖时，**"同名不同义"最危险**——`selectById` vs `findByIdOnly` 这种只差一个软删过滤的方法，必须逐个读实现再决定复用还是新增。
- **登记（余步）**：`KnowledgeService` 仍有 748 行/60 方法，继续按目标树抽（`service.management/*` use-case、`repository/KnowledgeRepository`）；`KnowledgeFileService`(422/2)、`KnowledgeBaseService`(626/18)；最后一对 `task ⇄ service` 余 7 处。

**🔶 B263 第⑧步（2026-10-14，`repository/KnowledgeRepository` 落位 + §9.2 重复实现合并）**
- **`repository/KnowledgeRepository`（§8.8.2 目标类）**：从 `KnowledgeService` 抽出 4 个行级操作——`findDuplicateByHash(kbId, hash)` / `findDuplicateBySource(kbId, url)`（未软删 + `LIMIT 1`）/ `insert(Knowledge)` / `updateById(Knowledge)`；**契约写进类头**：本层只做数据访问、**不抛业务异常**（重复冲突仍由 service 侧判定并抛 `DuplicateKnowledgeException`）、不依赖任何 service 类型，方向恒为 repository ← service（避免复活 B263② 断掉的边）。改走仓储的调用点 **9 处**（2 处重复查询 + 7 处 insert/updateById）⇒ 枢纽内 `knowledgeMapper.insert/updateById` 归零。
- **`support/KnowledgePaths` + 一次 §9.2 合并**：`ensureManualFileName` / `extractFileNameFromUrl` 由 `KnowledgeService` 迁出；同时发现 `datasource/service/MapperKnowledgeBridge` 有一份**同名私有实现**——逐字比对（各 13 行、规范化后 diff 为空 ✓）**确认等价**后合并为单一实现，datasource 侧改为**委托**调用（这是本批次第一次主动收掉"同一规则两份实现"）。
- **顺带查明**：`KnowledgeService.normalizeKnowledgeFolderPath` 只是**转发**（真实现在 `KnowledgeFolderService`，附带 `stripTrailing`/UTF-8 截断等逻辑）⇒ 本步不动，登记随 `KnowledgeFolderService` 的归位一起处理（避免把 60+ 行文件夹路径规则塞进 support 再迁一次）。
- **自查一坑**：`MapperKnowledgeBridge` 的委托第一版写成内联 FQN（R8 守卫的典型目标）⇒ 在跑守卫前先改成 import 化 ✓。
- **结果**：`KnowledgeService` **748 → 726 行**（B263⑦+⑧ 累计 **799 → 726**，-73）；新增 `repository/KnowledgeRepository`、`support/KnowledgePaths`。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m38s** ✓ 十一守卫 ✓（包级 SCC 仍 13 组、`{service, task}` 2 成员）。
- **登记（余步）**：`KnowledgeService` 726 行继续抽（`service.management/*` use-case、剩余 mapper 直连 8 处）；`KnowledgeFileService`(422/2)、`KnowledgeBaseService`(626/18)；`normalizeKnowledgeFolderPath` 真实现随 `KnowledgeFolderService` 归位；`task ⇄ service` 余 7 处。

**🔶 B263 第⑨步（2026-10-14，`KnowledgeService` 的 mapper 直连归零）**
- **最后 9 处直连分归三个仓储**：
  - `KnowledgeRepository` +3：`selectPage(Page, wrapper)`（薄转发：**查询构建仍在用例侧**，登记为后续归位点）、`findByIdOnly(String id)`、`softDelete(id, now)`；
  - `ChunkRepository` +2：`insert(Chunk)`、`softDeleteByKnowledgeId(knowledgeId, now)`；
  - `KnowledgeTagRepository` +4：`listTagsWithKnowledgeId`、`listTagsByIds`、`deleteRelations`、`insertRelation`。
- **连带清理（死成员守卫的功劳）**：枢纽内 mapper 使用归零后，三个 `*Mapper` 字段 + 构造参数 + 赋值 + import 全成死成员 ⇒ 一并删除 ⇒ **`KnowledgeService` 不再注入任何 Mapper**（A16"service 直连 mapper"这条债在它身上清空；A17 仍因 `LambdaQueryWrapper`（分页条件构建）在册）。
- **模块约定拦下的写法（新经验）**：`KnowledgeCodeConventionsTest`（知识库模块**禁止代码体内内联全限定类名**，比仓级 R8 更严）抓到我新仓储方法里的 `com.baomidou…` / `java.time…` 全限定名 ✗ ⇒ 改 import ✓。**该模块内写新代码时默认走 import。**
- **结果**：`KnowledgeService` **726 → 719 行**（B263⑦⑧⑨ 累计 **799 → 719**，-80）；mapper 直连 **0**。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m40s** ✓ 十一守卫 ✓（SCC 13 组 / `{service, task}` 2 成员未变）。
- **登记（余步）**：`KnowledgeService` 719 行余部（`service.management/*` 用例归位需**配套断边**——B263① 已实测"先拆面会扩大 SCC"）；`selectPage` 的**查询构建**搬入仓储；`KnowledgeFileService`(422/2)、`KnowledgeBaseService`(626/18)；`normalizeKnowledgeFolderPath` 真实现随 `KnowledgeFolderService` 归位；`task ⇄ service` 余 7 处。

**🔶 B263 第⑩步（2026-10-14，`KnowledgeFileService` 归仓储：422 → 380 行）**
- **仓储扩面**：`KnowledgeRepository` +4（`findById(tenantId,id)` / `updateSummaryColumns(k)` / `updateRow(k,metadata)` / `updateFileHash(id,hash,now)`），`ChunkRepository` +2（`updateById(Chunk)`、**`findByIdOrNull(tenantId,id)`**）；**复用**既有 `listChunkByParentId`（语义核对等价 ✓）。
- **保留薄委托**：service 侧 `updateSummaryColumns` / `updateKnowledgeRow` 变成一行委托 ⇒ 全部调用方（`KnowledgeParseService`/`KnowledgeSummaryService`/`KnowledgeService`/controller）**零改动**。
- **连带清理**：mapper 使用归零 ⇒ 两个 `*Mapper` 字段/构造参数/赋值/import 删除；死成员守卫点名 2 个未使用 import（`DocumentParser` 的 `LambdaQueryWrapper`、本类的 `JsonNodeFactory`）⇒ 清；A17 基线里 `KnowledgeFileService` 条目**已不再违例** ⇒ 按提示删除（A16 保留：仍依赖 `task` 队列）。
- **单一实现**：`DocumentParser.findById` 改为**委托** `KnowledgeRepository.findById`（同一查询不再两份）。
- **⚠️ 契约测试现场抓错（本步最值钱的教训）**：`updateImageInfo` 里的 chunk 查询我复用了既有 `ChunkRepository.getChunkById` ✗ —— 它找不到时**抛 `ChunkNotFoundException`**，而原契约是"返回 null → 调用方抛 `AppError.internal("chunk not found")`（**500**）"✗ ⇒ `KnowledgeOperationsContractTest.imageInfoMatchesGo` **当场失败** ✓ ⇒ 新增 `findByIdOrNull(tenantId,id)` 保留 null 语义并改回 ✓。**结论：复用仓储方法前要读语义（异常 vs 空值），不能只看签名。**
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m42s** ✓ 十一守卫 ✓（SCC 13 组 / `{service, task}` 2 成员未变）。
- **登记（余步）**：`KnowledgeBaseService`(626/18)；`KnowledgeService`(719) 余部（往 `service.<子面>` 搬需配套断边）；`selectPage` 的查询构建搬入仓储；`normalizeKnowledgeFolderPath` 真实现随 `KnowledgeFolderService` 归位；`task ⇄ service` 余 7 处。

**🔶 B263 第⑪步（2026-10-14，`KnowledgeBaseService` 归仓储：626 → 585 行）**
- **`repository/KnowledgeBaseRepository` 落位（§8.8.2 目标类）**，7 个方法：`insert` / `findById(tenantId,id)` / **`findByIdOnly(id)`**（**无租户过滤**——move/copy/duplicate 的 403/404 分歧前提，注释里写明）/ `listActiveByTenant(tenantId)`（**两处同形列表查询合并为一份**）/ `listMoveTargets(tenantId,type,embeddingModelId,excludeId)` / `updateById` / `softDelete(id,now)`。
- **`KnowledgeRepository` +3**：`countByKnowledgeBaseId`、**`countByParseStatuses(kbId, String... statuses)`**（参数化，同时覆盖"列表口径 2 个状态 / 摘要口径 3 个状态"两处差异）、`softDeleteByKnowledgeBaseId`；**`ChunkRepository` +2**：`countByKnowledgeBaseId`、`softDeleteByKnowledgeBaseId`。
- **结果**：`kbMapper` / `knowledgeMapper` / `chunkMapper` 直连**全部归零** ⇒ 三个 mapper 的字段/构造参数/赋值/import 删除（含 `kbMapper` 整个字段）。**保留并登记**：`storageBackendMapper`(266/277) 与 `pinMapper`(491/577) 共 7 处——目标树未给这两张表列仓储类，留待后续命名。
- **模块约定第二次抓错**：新仓储里写了内联 `java.util.List<≠>` ⇒ `KnowledgeCodeConventionsTest`（禁代码体内内联 FQN）当场失败 ✗ ⇒ 改 import ✓。**该模块写新代码一律先 import。**
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m40s** ✓ 十一守卫 ✓（SCC 13 组 / `{service, task}` 2 成员未变）。
- **登记（余步）**：`storageBackendMapper`/`pinMapper` 的仓储化（需先定名）；`KnowledgeService`(719) 余部 → `service.management/*`（需配套断边）；`selectPage` 查询构建搬入仓储；`normalizeKnowledgeFolderPath` 真实现随 `KnowledgeFolderService` 归位；`task ⇄ service` 余 7 处。

**🔶 B263 第⑫步（2026-10-14，pin / storage-backend 仓储化：`KnowledgeBaseService` 585 → 567）**
- **新建两个仓储**：`repository/UserKbPinRepository`（`findByUserAndKb` / `deleteByUserAndKb` / `deleteByKnowledgeBaseId` / `insert`）、`repository/KnowledgeStorageBackendRepository`（`findById(tenantId,id)` / `findLegacyAliasByProvider(tenantId,provider)`）。
- **7 处直连归零**：`KnowledgeBaseService` 的 **5 个 mapper 直连全部为 0** ⇒ 最后两个 mapper 字段/构造参数/import 删除 ⇒ **A16 与 A17 的基线条目双双过期**（该类已不碰任何边界层、不碰具体技术）⇒ 按守卫提示各删一条 ✓。
- **⚠️ 本步最大的坑（值得记住）**：我新建的 `StorageBackendRepository` 与 **`storage` 域既有同名类**（`storage.mapper.StorageBackendRepository`，基于 JdbcClient）**撞名** ✗ ⇒ Spring **Bean 名冲突**（默认取简单名）⇒ **整个应用上下文起不来**，boot **1211 / 1329** 条测试全挂 ✗。改名为 `KnowledgeStorageBackendRepository`（类头注释写明原因）后全部恢复 ✓。**教训：新建 `@Component` 仓储前先全仓查同名类——Bean 名默认取简单名，跨包同名一样冲突。**
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m38s** ✓ 十一守卫 ✓（SCC 13 组 / `{service, task}` 2 成员未变）。
- **登记（余步）**：`KnowledgeService`(719) 余部 → `service.management/*`（需配套断边）；`selectPage` 查询构建搬入仓储；`normalizeKnowledgeFolderPath` 真实现随 `KnowledgeFolderService` 归位；`task ⇄ service` 余 7 处。

**🔶 B263 第⑬步（2026-10-14，文件夹路径规则统一进 `support/KnowledgePaths`）**
- **统一**：`normalizeKnowledgeFolderPath` 的**真实现**（35 行 + `stripTrailing` 私有助手）由 `KnowledgeFolderService` 迁到环外的 **`support/KnowledgePaths`**（该类的第三个成员，前两个是 B263⑧ 的 `ensureManualFileName` / `extractFileNameFromUrl`）；`KnowledgeService` 里那个 **2 行转发层删除**；调用点全部换指（controller ×2 + `KnowledgeFolderService` 内部 ×3），死 import 清掉。
- **结果**：`KnowledgeService` 719 → **716 行**；`KnowledgeFolderService` ≈387 → **347 行**（约 -40）；规则只剩一份实现（§9.2）。
- **⚠️ 过程教训（值得记住）**：第一个脚本一次性预算了**两个**块的偏移 ✗ ⇒ 删掉第一块后第二个偏移失效，切**坏了另一个方法**（编译当场报 odd 错）✗。恢复方式：`git checkout` 还原两个受损文件 ✓ + 改为**"抽一块 → 重读文件 → 再抽下一块"**的顺序式抽取 ✓；追加锚点写错时，用 `git show HEAD:<path>` 把已删除的块**原样取回**再补 ✓。**结论：同一文件多处抽取绝不预排偏移；被删块可从 HEAD 取回，不必重写。**
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m41s** ✓ 十一守卫 ✓（SCC 13 组 / `{service, task}` 2 成员未变）。
- **登记（余步）**：`KnowledgeService`(716) 余部 → `service.management/*`（需配套断边）；`selectPage` 查询构建搬入仓储；`task ⇄ service` 余 7 处。

**🔶 B263 第⑭步（2026-10-14，`KnowledgeFolderService` 归仓储：347 → 318 行；并完成域内全景测量）**
- **域内全景测量（本步先做的关键动作）**：扫 `knowledge/service` 全部类，发现**仍有 18 个类直接持有 mapper / MyBatis 条件构造器（约 130 处）**——`KnowledgeCloneService`(16) / `KnowledgeMoveService`(13) / `KnowledgeSummaryService`(12) / `FaqEntryCommandService`(12) / `KnowledgeBatchOpsService`(10) / `FaqEntryQueryService`(9) / `KnowledgeBaseLookupAdapter`(9) / `KnowledgeFolderService`(9) / `FaqIndexWriter`(8) / `KnowledgeAccessHelper`(7) / `KnowledgeSearchService`(7) / `FaqImportService`(6) … ⇒ **"mapper/repository 分离主战场"远比五个枢纽类大**，本批余步按"一次一类"推进。
- **本步（`KnowledgeFolderService`）**：`KnowledgeRepository` +3——`listFolderPathsForTree(tenantId, kbId)`（只选 folder_path）、`updateFolderPath(tenantId, kbId, ids, folderPath, now)`（返回影响行数）、`listByFolderPathPrefix(tenantId, kbId, prefix)`（`folder_path = prefix` 或 `prefix/…`）；**复用**既有 `KnowledgeBaseRepository.findByIdOnly(id)`（rename 路径）、`KnowledgeRepository.findById(tenantId, id)`（`loadKnowledgeWrite`）、`KnowledgeBaseRepository.findById(tenantId, id)`；构造器换成两个仓储 ⇒ **mapper 直连归零**，A16/A17 基线条目双双过期 ⇒ 各删一条；死成员守卫点名的 2 个未使用 import（`LambdaQueryWrapper`/`LambdaUpdateWrapper`）清掉。
- **过程教训（新）**：整块**精确字符串替换失败**（`return knowledgeMapper.update(...)` 那段有肉眼看不出的空白/字符差异）✗ ⇒ 改用**正则 + `re.S` 非贪婪到 `));`** 的写法（多行语句更稳）✓；同时把仓储方法追加写成**幂等**（先查存在再追加 ✓）——因为脚本中途 assert 失败时，前一半改动可能已经落盘。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m42s** ✓ 十一守卫 ✓（SCC 13 组 / `{service, task}` 2 成员未变）。
- **登记（余步）**：继续按"一次一类"归仓储（上列 18 类）；`KnowledgeService`(716) 余部与 `service.management/*` 归位（需配套断边）；`task ⇄ service` 余 7 处。

**🔶 B263 第⑮步（2026-10-14，`KnowledgeAccessHelper` 归仓储：124 → 106 行）**
- **为什么挑它**：它是**访问原语**（`findKb`/`requireKb`/`getKnowledge`/`getKnowledgeInTenant`/`getKnowledgeBatch`/`attachTags`），被 `KnowledgeService` / `KnowledgeFileService` / `KnowledgeParseService` / `KnowledgeSummaryService` 等共用 ⇒ 清掉它的 mapper 依赖，等于把"域内所有读路径都经仓储"这件事一次覆盖一大片（**杠杆比按行数算更高**）。
- **归位**：`KnowledgeRepository` 只 +1 个方法 `listByIds(tenantId, ids)`（tenant + `id IN` + 未软删；空集合返回 `List.of()`，与调用方原短路语义一致）；其余 5 处**全部复用既有方法**——`KnowledgeBaseRepository.findByIdOnly(id)`（`findKb`）、`KnowledgeBaseRepository.findById(tenantId, id)`（`requireKb`）、`KnowledgeRepository.findById(tenantId, id)`（`getKnowledge` / `getKnowledgeInTenant`）、`KnowledgeTagRepository.listTagsWithKnowledgeId(id)`（`attachTags`，原本是 `tagMapper.selectTagsWithKnowledgeId(List.of(k.getId()))` ⇒ 仓储方法实现逐字相同，已现场核对）。
- **连带**：字段与构造器换成 3 个仓储（`@Component` 构造注入、仓内无 `new KnowledgeAccessHelper(...)` 调用点 ⇒ 无需改调用方）；A16/A17 条目双双过期 ⇒ 各删一条；未使用 import（`LambdaQueryWrapper` + 3 个 mapper）清掉。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m43s** ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：`KnowledgeSearchService`(7) / `FaqIndexWriter`(8) / `KnowledgeBaseLookupAdapter`(9) / `FaqEntryQueryService`(9) / `KnowledgeBatchOpsService`(10) / `FaqEntryCommandService`(12) / `KnowledgeSummaryService`(12) / `KnowledgeMoveService`(13) / `KnowledgeCloneService`(16) 等仍待归仓储；

**🔶 B263 第⑯步（2026-10-14，`KnowledgeSearchService` 归仓储：194 → 109 行）**
- **本步做的是"整体搬运"而非逐行替换**：检索主查询的**构建**（未软删 + 作用域 OR 组 + keyword 双列 LIKE + 文件类型别名模式含 `<<url>>` 分支 + `created_at DESC` + `limit+1` 探测）**整块**搬进 `KnowledgeRepository.searchScopedRows(scopes, keyword, fileTypes, offset, limit)`，返回新 record `SearchRows(rows, total)`；两个只服务该查询的私有助手（`escapeLikeKeyword` 转义、`fileTypePatterns` 别名→模式）**随查询一并搬入**（仓储里 `private static`）。语义逐字保留（`rows.subList(0, limit)` 的切分仍在服务侧）。
- **顺带纠正分层方向（本步发现）**：作用域 record `KnowledgeSearchScope` 原为服务的**嵌套类型**，但仓储也要用它 ⇒ 移入 `com.ragagent.knowledge.domain.KnowledgeSearchScope`（3 个使用点：`KnowledgeOperationsController`、`KnowledgeSearchService`、`KnowledgeService`）。**理由**：仓储不该依赖 service 包的类型（方向应为 `service → repository → domain`）。
- **一处语义差异（B263⑩ 教训再次生效）**：`KnowledgeBaseRepository` 已有 `listActiveByTenant(tenantId)`，但它**多一个"排除临时库"过滤且按 created_at 倒序**，检索面要的是"租户下全部未软删库"（含临时、不排序）⇒ **不复用**，新增 `listByTenant(tenantId)` 并在 javadoc 里写明两者的区别（避免日后误用）。
- **连带**：字段/构造器换成两个仓储；A16/A17 条目各删一条；`--rerun-tasks` 前先跑 `*Search*` 测试确认行为不变；死成员守卫点名的 3 个未使用 import（`PageRequests`/`HashSet`/`Set`）清掉。
- **一处踩坑**：脚本断言写早了（检查整份源码时 **import 行还在**，它在断言之后才删）✗ ⇒ 断言口径改为只查函数体里的构造（`new LambdaQueryWrapper`）✓；另 `PageRequests` 的真实包是 `com.ragagent.common.mybatis`（属数据层助手，仓储里用合规 ✓，服务里已不再引用 ✓）。
- **文件规模**：`KnowledgeRepository` 205 → 306 行（阈值 600，安全）；`KnowledgeSearchService` 194 → 109。
- **测试**：**4978 / 失败 0 / 跳过 4** ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ **1m43s** ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：`escapeLikeKeyword` 仍是**多副本**
**🔶 B263 第⑰步（2026-10-14，`FaqIndexWriter` 归仓储）**
- **归位**：`KnowledgeRepository` +`listByTenantAndKb(tenantId, kbId)`（未软删、不排序、不筛 type——FAQ 容器仍由调用方按 `type='faq'` 判定，语义逐字保留）；`knowledgeMapper.insert/updateById`（1 + 3 处）→ `KnowledgeRepository.insert/updateById`，`chunkMapper.insert` → `ChunkRepository.insert`（两者实现与直连逐字一致，已现场核对）。
- **本步是"诚实部分步"**：第 5 处是**跨域读 model 行**（`modelMapper.selectOne(Model by id+tenant)`，为取嵌入维度）⇒ **留原样**并登记——`Model` 属 model 域，knowledge 直连 `ModelMapper` 是域间耦合；两条候选修法（① 经 engine 侧端口暴露"嵌入维度"；② 经 model 域契约面取行）都要动跨域接口，**不在本步**。因此 A16/A17 中 `FaqIndexWriter` 的条目**保留**（它仍依赖 model 的 mapper），本步不做收紧。
- **测试**：4978 / 失败 0 / 跳过 4 ✓；启动测试 25 例 ✓；`spotlessCheck build` ✓ 十一守卫 ✓（环 0 / SCC 13 组未变）。
- **登记（余步）**：
（`TenantService` 私有、`SessionRepository` 静态、`MessageRepository` 借用、现在仓储内也有一份）⇒ 应统一到 `common` 一处（§9.2 式统一，独立批次）；`FaqIndexWriter`(8) / `KnowledgeBaseLookupAdapter`(9) / `FaqEntryQueryService`(9) / `KnowledgeBatchOpsService`(10) / `FaqEntryCommandService`(12) / `KnowledgeSummaryService`(12) / `KnowledgeMoveService`(13) / `KnowledgeCloneService`(16) 等仍待归仓储。`KnowledgeService`(716) 余部与 `service.management/*` 归位（需配套断边）；`task ⇄ service` 余 7 处。
- **登记（余步）**：⑦ `KnowledgeService`(799 行/28 引用) 拆分（**优先级最高：行数已到阈值边缘**）；⑧ `KnowledgeFileService`(422/2)、`KnowledgeParseService` 余部、`KnowledgeBaseService`(626/18)；⑨ 最后一对 `task ⇄ service`（`task→service` 现余 **7**：`ChunkExtractService`3 / `QuestionGenerationService`3 / `SpanTracker`）；⑩ `service` 七子面、`domain.{document,task,graph}`、`mapper` 目标名对齐。
- **登记（余步）**：按最轻优先继续——`ChunkVectorIndexer`(386/2)、`KnowledgeFileService`(422/2)、`KnowledgeParseService` 余部、`KnowledgeBaseService`(626/18)、`KnowledgeService`(796/28)；一律"切分而非整搬"。`task → service` 现余 8 处（`ChunkExtractService`3 / `QuestionGenerationService`3 / `SpanTracker` / `ChunkVectorIndexer`）。

## 附：文档地图与归档追溯

**✅ B255（2026-10-13，阶段 5 收口：检索中立类型）**
- **勘察结论（修正了方案的一句话）**："上层（domains/boot）依赖 `PgVectorRetrieveRepository` 内部类型"其实**已不成立**（实测 domains/boot main 源零引用）；真正的泄漏在**引擎内部**：编排层（`HybridSearchService` / `HybridFusionOps` / `HybridResultOps` / `RetrievalObs`）说的是 `PgVectorRetrieveRepository.IndexHit`/`RetrieveResult`——**具体向量库适配器**的行形状，而不是引擎契约里早就存在的 `EngineTypes.IndexWithScore`/`EngineTypes.RetrieveResult`，于是每次取回都要过一次 `toPgShape` 转换。
- **修复**：编排层整体切到中立类型（`EngineTypes.*`，字段形状同形 ⇒ 纯类型替换）；**删掉 `toPgShape` 转换层**（两处调用点：主检索 + FAQ 迭代取回）；删掉 `HybridSearchService` 的 `pgRepository` **死依赖**（只赋值不使用）；`PgVectorRetrieveRepository` 里重复的 `RETRIEVER_VECTOR`/`RETRIEVER_KEYWORDS` 常量删除（`EngineTypes` 已有同值常量，全仓本就用它）。
- **验收（实测）**：`PgVectorRetrieveRepository` 的 main 源引用**只剩 `retrieval.engine` 适配层**（`EngineTypes` / `RetrieveEngineRepository` / `EngineFactory` / `VectorStoreService` / `PgVector*`）+ `boot` 装配根 `RetrievalEngineWiringConfig`（组装 pg 引擎 bean，属组合根）。
- **测试**：新增 `RetrievalNeutralTypesTest`（**先红**：实测违规清单 = `HybridFusionOps` / `HybridResultOps` / `HybridSearchService` / …；只扫编排层本层、不含 `retrieval.engine`）——防"再退回 pg 行形状"；既有引擎测试（各向量库仓库测试、`EngineFactoryTest`、store-group 测试）全绿 ⇒ 类型切换无行为变化。
- **闸门**：`spotlessCheck build` ✓ **1m37s** ✓ · **4970 / 失败 0 / 跳过 4** ✓（+1）· 十一守卫 ✓。
- **登记（未做）**：`PgVectorRetrieveRepository.IndexHit`（含 `sourceId`/`sourceType`/`tagId`）与 `EngineTypes.IndexWithScore` 仍有字段重叠，长期可给中立类型加"构造自适配器行"的映射方法（现在映射在 `PgVectorEngineRepository.mapHits`，位置已正确）；`IndexWithScore` 的 `isEnabled` 在编排侧未消费。

## 附：文档地图与归档追溯

| 文档 | 用途 |
|---|---|
| `HANDOFF.md`（本文） | v0.2.0 常读面：现状 / 批次表 / 纪律 / 环境 / 方法论 |
| `docs/handoff/archive/HANDOFF-v0.1.0-2026-10-13.md` | v0.1.0 全文归档（历史追溯，勿作现行依据） |
| `outputs/java-remediation-plan-2026-10-13.md` | 整改方案（设计产物，gitignored） |
| `outputs/java-remediation-implementation-plan-2026-10-13.md` | 具体实施计划（含批次表原件与勘察证据） |
| `docs/handoff/records/batch-records.md` | v0.1.0 早期逐批记录（~B110，历史） |
| `docs/backend-package-map.md` · `docs/*-module-guide.md` | 包地图与 34 域模块手册 |
| `docs/api-response-convention.md` | 统一响应外壳约定（权威） |

批次详情取回：`git log --oneline | grep "B2xx"`；提交信息一律带 `(B###)` 与验证方式。
