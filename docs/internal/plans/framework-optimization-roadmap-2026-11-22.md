# framework 优化路线图：P1 速赢 · channel 合并 · 三大替换 · vocabulary 终局（交执行 Agent）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按蓝图两把尺子（§5.3 能力表 + §1"普通 Java 工程师一眼看懂"）完成 framework 收官后的四段优化：P1 包名对齐速赢、channel 域合并与 MessageView 簇拆迁、Sa-Token/RLS/JobRunr 三大替换批的接入点、vocabulary 终局归零。

**Architecture:** P1 是纯位置整理（零行为变化，金片不动）；channel 批以"URL 不变 + JSON 逐字节不变"为硬约束做域合并与形状拆迁；三大替换批各由自己的里程碑触发（本计划给 WP 级方案与验收，触发前不启动）；终局批在解钉完成后把 vocabulary 收缩到零或改名。落点判据沿用上轮实证的晶格规则（R10 `src_lv>=tgt_lv`；引擎非域、不受 R10 约束；{app,ae,re} 全可见 model-runtime）。

**Tech Stack:** JDK 21 + Maven；金片契约测试 + 49 守卫；新增依赖仅在替换批：`sa-token-spring-boot3-starter`、`jobrunr-spring-boot-3-starter`（版本以兼容 Boot 3.3.13 为准，蓝图 §8 留给实施者的决定）。

**Spec:** `docs/blueprint/architecture-blueprint.md` §1（可读性目标）、§5.3/5.4/5.6/5.7/5.9（能力表/域分级/SSE/多租户/任务队列）、D10~D25；上游 `docs/internal/plans/framework-return-execution-2026-11-22.md`（已执行，终态 vocabulary 19+tenant 8）。

**生成**：2026-11-22，基线提交 `0b603e1dc`（v0.11.x，工作区 clean）。消费面数字为当日 grep 实测（主码，词边界）。

---

## 0. 依赖图与启动条件

```
WP1 P1 速赢（a~e 五个子批，任意时点可启，互不依赖）
WP2 channel 合并批 ──────────► WP6 终局批（vocabulary 8→0/改名）
WP3 Sa-Token 批（独立）──────►   ↳ 解 tenant 8 钉链
WP4 RLS 批（与计划03 Task2 合流）
WP5 JobRunr 批（独立）
```

WP2 建议 WP1 之后开工（避免 event/tenant 包同时被两批改写）；WP3/4/5 相互独立、与 WP1/2 也独立。**WP6 必须最后**（依赖 WP2 完成 + WP3 完成 tenant 侧）。

## 1. 不可协商约束（继承上轮 §2 全部六条 + 本轮特有）

| # | 约束 | 判据 |
|---|---|---|
| 1 | 不动前端 | URL、请求/响应字段、SSE 帧格式全不变；金片逐字节不变。**channel 域合并是纯后端内部重组**（蓝图 §4.3 明示"界面不变"），`/api/v1/im-channels`、`/api/v1/embed-channels`、`/api/v1/embed/{channelId}/*` 全部保留 |
| 2 | 不动数据库 | 无 DDL（WP4 例外：RLS 策略属计划03 已有轨道） |
| 3 | 闸门 | `mvn -B clean verify`（根 pom）绿 + 守卫 49/49；删除批测试条数下降须台账对账 |
| 4 | 环境口径 | `JAVA_HOME=/opt/homebrew/opt/openjdk@21` + `ZZ_PG_JDBC=jdbc:postgresql://localhost:15432/RagAgent`（HANDOFF.md:109） |
| 5 | 晶格 | R10 `src_lv>=tgt_lv`；引擎（ae/re/mr）不是域、不受 R10 约束；`capability-kinds.json` 是单一来源 |
| 6 | 迁移代号 | 主码注释禁批次号/日期戳（守卫钉住）；守卫锚随迁不许 `--write` 掩盖 |

## 2. P1 速赢批（WP1a~e，合计约 2~3 天，零行为变化）

### WP1a access 解体（0.5 天）

**Files:** `framework/access/{TenantContext,TenantScopes,TenantScope,TenantContextSnapshot,PrincipalTypes}.java` → `framework/tenant/`；`framework/access/TracingContext.java` → `framework/tracing/`；删 `access/package-info.java` 与空目录；更新 `tenant/package-info`（补"请求上下文"段）与 `tracing/package-info`。

- 实测依据：TenantContext 170 文件消费（framework 内 13 处跨 8 包：tenant/tracing/eventbus/web/storage/event 互引，同模块搬后补 import 即可）；TracingContext 的 framework 内消费全在 tracing/ 包（LangfuseTaskScope/TraceInjector/LangfuseTracing）——搬入同包反而消 import。
- [ ] 标准流程五步（上轮验证版）×6 类；`framework/access` 消费方 import 全量改写（app 侧约 150 文件，机械 sed）
- [ ] 台账记录"access 包消失"；提交 `refactor(framework): WP1a 租户上下文归 tenant、链路上下文归 tracing，access 包解体`

### WP1b EventType 归契约（0.5 天）

**Files:** `framework/eventbus/EventType.java` → `framework/event/`；`scripts/check-event-face-case.py:37` 的 `EVENT_TYPE` 锚改 `framework/event/EventType.java`。

- 实测依据：EventType 21 文件消费；事件协议枚举与 Event 包络同包（event/ 是 C1 契约白名单子包，EventType 纯常量符合纯 JDK 判据——搬前跑 `check-contract-purity.py` 预检）。
- [ ] 搬迁+import 改写+守卫锚改；`event/package-info` 与 `eventbus/package-info` 同步（eventbus 自述去掉 EventType）
- [ ] 提交 `refactor(framework): WP1b EventType 归契约面 event/`

### WP1c 零值时间同址（0.25 天）

**Files:** `framework/web/ZeroTimeSerializer.java` → `framework/json/`（与 `ZeroTimes` 同包）。

- 实测依据：装配点是 `eventbus/EventJson`（注册该 serializer）——import 改一处；app 侧无直接引用（消费的是 ZeroTimes 常量）。
- [ ] 搬迁+EventJson import；`web/package-info` 摘条目、`json/package-info` 补
- [ ] 提交 `refactor(framework): WP1c ZeroTimeSerializer 与 ZeroTimes 同址 json/`

### WP1d html+text 合并（1 天）

**Files:** `framework/html/` 14 类 → `framework/text/`（平铺，`Html*` 前缀自解释）；`framework/text/` 既有 4 类不动；删 html 目录；`text/package-info` 重写为"平台文本与标记卫生面"。

- 实测依据：html 37 文件、text 32 文件跨模块消费（合计 69 处 import 改写，机械）；html 的 C8/守卫零登记（上轮已核）。
- [ ] 搬迁+import 改写；`docs/architecture/packages.md` 等三个生成物重生成
- [ ] 提交 `refactor(framework): WP1d html 并入 text，文本卫生面单包化`

### WP1e 陈旧账清尾（0.25 天）

**Files:** `app/src/test/java/com/ragagent/arch/ClasspathResourcesTest.java:67,72`（消费方标签 `common.prompt.PromptSource`/`platform.prompt.ExtractPrompts` → 现路径）；跑一遍 `run-guards.sh` 把所有"基线已无命中可清理"提示清零（json-key-case 5 个陈旧文件、platform-shapes 已对齐）；`jsonbind/ProviderJson.java:59` 的 `[]string` Go 词汇注释顺手清。

- [ ] 提交 `chore(framework): WP1e 守卫与文档陈旧账清零`

**WP1 全批验收**：`mvn -B clean verify` 5241/0/2 守恒 + 守卫 49/49（event-face-case 锚改后计数不变）。

## 3. channel 合并批（WP2，约 3~4 天）

### WP2a 域合并 im+embedchannel → channel（1 天）

**Files:** `git mv app/src/main/java/com/ragagent/im/* app/src/main/java/com/ragagent/embedchannel/* → app/src/main/java/com/ragagent/channel/`（api 两包并一：`im/api` 4 类 + `embedchannel/api` 6 类 → `channel/api`）；`scripts/capability-kinds.json` 删 im/embedchannel 两项、增 channel（level 2 / kind integration / frontend 合并两条的映射）；测试树同步。

- URL 不变的保证：controller 类只搬包不改注解（实测前缀 `/api/v1/im-channels` 等与包名无关）。
- [ ] 合并+全量 import 改写（im/embedchannel 互引 + 其他域引它们的 api——R10 同级 api 放行不变）
- [ ] `gen-domain-index/gen-arch-index/gen-module-map` 重生成；`check-capability-kinds` 绿
- [ ] 提交 `refactor(app): WP2a im 与 embedchannel 合并为 channel 域（蓝图 §4.3 内部重组，URL 零变化）`

### WP2b MessageView 簇拆迁（2 天，金片护航）

**拆解映射**（26 字段整类保持 JSON 形状不变，拆的是"谁拥有形状"）：

| 类 | 去向 | 依据 |
|---|---|---|
| MessageView（26 字段本体） | `channel/api` | 消息线格式的服务面属主是 channel；session(L3)/config 引 channel/api ✓，engine 无引用 |
| MessageAttachment · MessageImage · MessageExecutionContext · UsedMemory · SuggestionAttribution | `channel/api`（随 MessageView 同址，消息线格式卫星词汇） | 被 MessageView 字段钉住；放 session/api 会让 channel(L2)→session(L3) 违 R10 |
| AgentStep · AgentToolCall · ToolCallTarget · MentionedItem | `agent-engine/agentengine/api` | 前置=MessageView 先离 framework；channel/api 在 app 内引 ae api 合法（引擎非域） |
| MessageArtifact | `retrieval-engine/retrieval/api` | re 的 ArtifactReferenceRewriter 消费（上轮 WP3 既定落点） |
| SearchResult · ToolResult | **留 vocabulary** | 晶格孤儿（SearchResult 双引擎+storage/Rewriter；ToolResult mr 钉）——终局批处理 |
| SseSink · KnowledgeBaseGateway · KnowledgeBaseFacts | **留** | 各有既定出口（SseEmitter 批/Provider 反转） |

- [ ] **顺序硬约束**：①MessageView+5 卫星 → channel/api（此时它引 framework 的 AgentStep/SearchResult 等全合法）→ ②四引擎形状 → agentengine/api（framework 内已无引用者）→ ③MessageArtifact → retrieval/api。逆序必炸（framework 不能 import app）。
- [ ] mixin 随行：MessageView 的 mixin 在 `app/config/json/SessionJsonMixins` 只改 import；AgentStep/AgentToolCall/ToolCallTarget 的 mixin 已在 `agentengine/json/AgentJsonMixins`（同模块改注册键包名）
- [ ] **金片专项**：`MessageJsonContractTest`(5)、`JsonContractRoundTripTest`、SSE 载荷金片全绿才算搬对（坑 1/18）
- [ ] `VocabularyManifestTest` 白名单 19→8（SearchResult/ChunkView/GraphNode/GraphRelation/ToolResult/KnowledgeBaseGateway/KnowledgeBaseFacts/SseSink）；vocabulary/package-info 同步
- [ ] 提交拆两个：`refactor(framework): WP2b-1 MessageView 簇六类归 channel/api` / `refactor(framework): WP2b-2 引擎四形状归 agentengine/api、MessageArtifact 归 retrieval/api`

### WP2c 守卫与文档收口（0.5 天）

- [ ] `AgentConfigKeyUsageTest` 等测试的 ALLOWED_PREFIXES 换锚（im/embedchannel→channel）；`check-module-graph` 测试边登记核对；三个生成物重生成
- [ ] 提交 `test(app): WP2c channel 合并后守卫锚与生成物收口`

## 4. 三大替换批（触发式，本节给 WP 级方案与验收）

### WP3 Sa-Token 批（3~5 天，触发：排期即启）

1. 根 pom 引 `cn.dev33:sa-token-spring-boot3-starter`（版本兼容 Boot 3.3.13）。
2. 三账号体系：`StpLogic`×3（后台用户/C 端访客/开放接口调用方，蓝图 D20/5.3）。
3. 迁移面（app 侧）：`auth/controller/filter/AuthFilter`（jjwt 会话）→ Sa-Token 会话；`auth/apikey/controller/filter/` 四件（APIKeyAuthChannel/GateInterceptor/RouteAuthorizer/RoutePolicies 204 条）→ 开放接口 StpLogic + SaRouter；`config/RbacRoutePolicies`（373 条）→ SaRouter 集中注册（结构化 addRule 可脚本化转换）。
4. **行为等价先行**：先改"路由权限覆盖"守卫判据到 SaRouter 注册表，跑绿后再删旧——顺序颠倒=鉴权真空窗口。
5. 删 `framework/web/RbacInterceptor+RbacRules` → **tenant 8 钉链解**：APIKeyCapability/APIKeyScopeType/APIPrincipalConfig/TenantAPIKeyScope/APIKeyScopeContext/TenantConfigLookup/TenantRole → `auth/api`（7 类整链随迁）；tenant 包清空删除。
6. 验收：路由覆盖守卫（385 路由）绿 + 全量闸门 + 台账登记测试差值。

### WP4 RLS 批（与计划03 Task2 合流执行，勿另起炉灶）

本计划只登记 framework 侧交付物：`TenantLineInnerInterceptor` 注册替换 `TenantFilterGuard`；`@IgnoreTenant` 注解+切面（系统通道白名单：认证/调度）；事务开始 `SET LOCAL app.current_tenant`（蓝图 5.7.2；spike 已知坑：事务级 set_config 后空租户查询报 `invalid bigint`，须转明确错误）；删三守卫 + `MapperGuardReferenceTest` + 80 条白名单；注解 SQL 手写 `tenant_id` 207 处按守卫棘轮分批清零。RLS 策略与表结构归计划03 轨道。

### WP5 JobRunr 批（3~4 天，触发：排期即启）

1. 引 `org.jobrunr:jobrunr-spring-boot-3-starter`，存储 PostgreSQL（与业务同库，D19：入队与业务写库同事务）。
2. worker 角色门控（D11：api/worker 部署角色，配置项控制 JobRunr 是否启 worker）。
3. 四域队列家族迁移：knowledge（ChunkExtract/QuestionGeneration/KnowledgeProcessing）、wiki ingest、memory extract、datasource sync + im QaQueue → `@Job` 方法；域保留"队列名+载荷+handler 薄适配"（task/package-info 既定口径）。
4. **租户上下文测试钉**：JobParameter 必带 tenantId、worker 侧先设 TenantContext 再执行——缺此步=静默串租户，必须有一条失败测试守住（蓝图 5.9"worker 执行前按任务参数设租户上下文"）。
5. 删 `framework/task/` 727 行 + EmbeddedRedis 队列测试改 JobRunr 测试设施；验收=全量闸门 + 台账对账。

## 5. vocabulary 终局批（WP6，1~2 天，前置 WP2+WP3 完成）

终态 8 类：SearchResult · ChunkView · GraphNode · GraphRelation · ToolResult · KnowledgeBaseGateway · KnowledgeBaseFacts · SseSink。

- [ ] **6a Provider 反转**（0.5 天）：auth/websearch 侧声明所需端口（audit 已有先例），knowledge 实现 → `KnowledgeBaseGateway+KnowledgeBaseFacts → knowledge/api`（蓝图 §5.11 Provider 模式）
- [ ] **6b SseSink**：登记留给 SseEmitter 批（蓝图 5.6.6，独立轨道不在本路线图）
- [ ] **6c 晶格孤儿 5 类去向（D-5 拍板）**：**方案 A（推荐）**：前置把 `framework/storage/support/{Rewriter,StreamRewriter}` 迁 `app/session`（实测消费方 im/session/storage-api 全在 app，framework 内仅互引）→ 然后 SearchResult/ChunkView/GraphNode/GraphRelation/ToolResult **全部 → model-runtime `llm/api`**——晶格上 {app,ae,re} 全可见 mr；语义辩护：五类均有 mr 侧真实消费（modelcontext 打包 SearchResult/ToolResult、ModelView 用 ChunkView、EntityExtraction 产 GraphNode/Relation），即"模型上下文与模型产物的形状"。**vocabulary 归零删除，`VocabularyManifestTest` 棘轮清零随删**。方案 B：改名为 framework 内小包（如 `crossengine`）保留——零风险但黑话延续。
- [ ] **6d 终局验收**：`grep -r "framework\.vocabulary" backend/*/src/main --include='*.java' | wc -l` = 0；蓝图 §5.2 对齐陈述（`retrieval.*/model.*/agent.*/knowledge/session 面` 全部在引擎/域 api；framework 只剩 error/event.Event/pagination/json + 机制件）；全量闸门 + 守卫 49/49

## 6. 拍板项（开工前确认，均有推荐默认）

| # | 决策 | 推荐 | 后果 if 否决 |
|---|---|---|---|
| D-1 | access 解体方向 | TenantContext 族→tenant/、TracingContext→tracing/（本计划方案） | 备选"access 改名 context"省 150 文件改写，但 tenant 与上下文继续分居 |
| D-2 | html+text 合并目标 | text 单包平铺（Html* 前缀自解释） | text/html 子包多一份 package-info，收益有限 |
| D-3 | MessageView 拆解深度 | 整类迁 channel/api 保形状（金片护航）；字段级三视图拆解留给蓝图 D1 API 重设计批 | 现在就字段级拆=重写线格式，违反"不动前端"约束 |
| D-4 | Sa-Token 切换 | 一次性切换（系统未上线，双轨无收益）；路由覆盖守卫先改判据再删旧 | 双轨多一周且留两套鉴权语义 |
| D-5 | 晶格孤儿终局 | 方案 A：Rewriter 先迁 app，五类 → llm/api，vocabulary 归零 | 方案 B 改名保留：零风险，"vocabulary"黑话永驻代码库 |

## 7. 坑清单

上轮 19 条全部沿用（编号见 `framework-return-execution-2026-11-22.md` §8）。本轮新增：

23. **鉴权批顺序**：先改路由覆盖守卫判据到新机制并跑绿，再删旧拦截器——逆序产生鉴权真空（WP3 步骤 4 已钉）。
24. **队列迁移串租户**：JobRunr JobParameter 不带 tenantId 或 worker 不先设 TenantContext，任务会静默跑在错误租户——必须有失败测试钉住（WP5 步骤 4 已钉）。
25. **域合并的生成物联动**：capability-kinds 删域加域后，gen-domain-index/gen-arch-index/gen-module-map 三个生成物必须同 commit 重生成，否则 verify 内嵌守卫与 run-guards 同时红（上轮 WP0 已踩）。

## 8. 执行台账（执行 Agent 填写）

| 批 | 日期 | 内容 | 验收数字（测试/守卫/金片） | 备注 |
|---|---|---|---|---|
| 基线 | | @0b603e1dc 全量 | 5241/0/2 · 49/49 | 上轮终态 |

---

## Review Focus（实现者必读的五个易错面）

1. **金片线格式**：MessageView 与 AgentStep 族迁移时 mixin 注册键随包名改、注解逐字段不动；金片专项全绿才算搬对（WP2b 已钉步骤）。
2. **R10 晶格的引擎豁免**：channel/api 引 agentengine/api 合法（引擎不是域）；但 session(L3) 引 channel/api(L2) 合法、channel(L2) 引 session/api(L3) 违规——MessageView 卫星落 channel/api 而非 session/api 正是为此。
3. **capability-kinds 同步**：域合并/删除只改 json 一处，三个生成物同 commit 重生成（坑 25）。
4. **URL 与错误码冻结**：channel 合并批任何 controller 注解、错误文案改动都会破前端契约——只搬包不改行为。
5. **Sa-Token 行为等价**：373 条规则逐条迁移后路由覆盖守卫必须先绿；鉴权测试失败不许跳过（安全面无灰度）。
