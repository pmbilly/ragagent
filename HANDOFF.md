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
| B241 | 2 | A2：全轮一次性提交门（CAS）+ 消息更新失败不启动索引/建议/记忆 | ⬜ |
| B242 | 2 | A3：可关闭模型流（句柄 + `ThinkPhase` 取消 probe + 装饰器队列关闭语义） | ⬜ |
| B243 | 2 | A5：跨线程上下文传播（NORMAL 消费线程 / STOP watcher replay） | ⬜ |
| B244 | 2 | A18 状态所有权规则（phase 不回引 runner / 无公共可变运行态字段） | ⬜ |
| B245 | 3 任务可靠 | K4：CAS 保护整轮数据（失败清场限本代；`KnowledgeParseService` 排斥处理中） | ⬜ |
| B246 | 3 | K5：图任务失败/重试/计数同一契约（`finalAttempt` 消费、异常不吞、任务级去重） | ⬜ |
| B247 | 3 | K6：主任务恢复口径统一（**拍板点②**：判据分裂先修；持久化深度待定） | ⬜ |
| B248 | 4 索引/配置 | K10a：embedding 响应对齐校验（`index` 消费 + 数量/维度校验 + 乱序拒绝） | ⬜ |
| B249 | 4 | K10b：索引分批政策收敛（worker 与 `ChunkVectorIndexer` 共用执行） | ⬜ |
| B250 | 4 | K7：事务外副作用（revision + outbox；事务内不调远程模型） | ⬜ |
| B251 | 4 | K8：配置能力矩阵（parser rules / parent-child 显式拒绝或实现；不静默回落） | ⬜ |
| B252 | 5 结构收敛 | K9：跨 store 融合（RRF 按 store 内 rank；context expansion 修正） | ⬜ |
| B253 | 5 | A4：并发工具状态提交顺序（`seenChunks` 单一 writer / 按调用序回填） | ⬜ |
| B254 | 5 | controller 剥离编排（`KnowledgeQaController` 14 构造参数、`QaRequestParser` 拆分） | ⬜ |
| B255 | 5 | 检索中立 `SearchHit`（去上层对 `PgVectorRetrieveRepository` 内部类型依赖） | ⬜ |
| B256 | 6 包结构 | §10 改名清算：10 组同名歧义 + 3 组域前缀（纯改名） | ⬜ |
| B257 | 6 | §7 命名与边界：`agent/tools`→`agenttools`、`agent/management`→`agentmanagement` + 边界规则 | ⬜ |
| B258 | 6 | contracts 包边界（模块 DAG + 跨域边界规则） | ⬜ |
| B259 | 6 | platform 包边界（`:common` 其余收拢） | ⬜ |
| B260 | 6 | agent-engine 包边界（`com.ragagent.agent` 运行面收拢） | ⬜ |
| B261 | 6 | retrieval-engine 包边界（`retrieval.*` 收拢 + policy 归位） | ⬜ |
| B262 | 6 | model-runtime 包边界（模型调用 / provider / 流生命周期） | ⬜ |
| B263 | 6 | domains 域内重排①：knowledge（29 包 248 文件；SCC 拆解 + mapper/repository 主战场） | ⬜ |
| B264 | 6 | ②：session（21 包 183 文件）+ agenttools/agentmanagement 落位 | ⬜ |
| B265 | 6 | ③：wiki（17 包 147）+ datasource（16 包 124）+ mcp（9 包 125） | ⬜ |
| B266 | 6 | ④：im/memory/auth/storage/websearch/model + 其余小域 | ⬜ |
| B267 | 6 | 门禁收紧收尾：§12.2「分层方向」「装配契约」两规则 + A11b 清零核查 + 复杂度指标（可选） | ⬜ |

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
