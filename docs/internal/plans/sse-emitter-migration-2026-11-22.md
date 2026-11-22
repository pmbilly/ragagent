# SseEmitter 迁移收尾批（交执行 Agent）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subgroup-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按蓝图 5.6.6 完成流式接口的 SseEmitter 化：控制器返回 emitter 即释放请求线程、流泵在虚拟线程串行 send、断连经 onCompletion/onError/onTimeout 感知；删除 SseFrameWriter 与 ServletSseSink；vocabulary 归零（最后一个成员 SseSink 迁出）。

**Architecture:** 写面换轨不改流模型——`streamManager.getEvents` 轮询 + offset 回放（断线续接）原样保留（蓝图明示"引擎写 StreamManager、流泵增量读取推送的模型保留"）。SseSink 端口语义从**帧级**（writeFrame(String)）改为**载荷级**（send(event, json)），由新的 EmitterSseSink 用 SseEmitter 实现；过渡期 ServletSseSink 改同签名保持旧行为，入口逐个切换后删除。帧字节以 WP0 实测为准：骨架一致则金片零改，不一致则按蓝图授权（"消费方均按 SSE 规范解析，帧格式细微变化不影响"）重锚。

**Tech Stack:** Spring MVC SseEmitter（已在 Boot 3.3.13 内，零新依赖）；金片 + 49 守卫；探针测试先行。

**Spec:** `docs/blueprint/architecture-blueprint.md` 5.6.6（本批的授权原文）、§1 反目标 2（界面交互不变——SSE 帧语义对前端等价）。

**生成**：2026-11-22，基线 `b1b5cca3e`（v0.11.x；另有并行会话 5 个未提交 scripts 改动，不属本批、不触碰）。

---

## 0. 调查结论（已实测，执行时不必重查）

| 事实 | 证据 |
|---|---|
| 流泵已在虚拟线程 | `TurnStreamPump:57`、`QaSseOrchestrator:123` 均 `Thread.ofVirtual().start` |
| 请求线程被占住 | `SessionStreamController:262-335` 在请求线程 Thread.sleep 轮询 streamManager 直写 servlet 输出 |
| 入口共三处 | `KnowledgeQaController:89/97`（经 QaEntryRunner）、`EmbedChannelDelegateOps:62-67`（经 sessionPort）、`SessionStreamController`（续接轮询） |
| 断线续接机制 | 回放 events + `streamManager.getEvents(sessionId, messageId, offset)` 轮询（保留不动） |
| 金片锚 | `SseFrameWriterTest`（帧骨架逐字节：`event:message\ndata:<json>\n\n` 冒号无空格）+ `KnowledgeQaContractTest`（9 测试） |
| mcp-server 兼容 | `ragagent_mcp_server.py:508` 只 `startswith("data:")`——空格有无都命中，后续 strip |
| HTML 转义 | 已全局移除（EventPayloadJsonTest 注释+期望值佐证），蓝图 5.6.6 该子项已完成，SseFrameWriter javadoc 陈旧 |
| async 雷区 | `AuthFilter`/`APIKeyScopeCleanupFilter`/`EmbedAuthFilter` 三个 OncePerRequestFilter：async dispatch 默认重复执行（shouldNotFilterAsyncDispatch 默认 false） |
| 端口底子 | `session/service/sse/SseFrameSink`（编排层已依赖端口而非 HTTP 实现） |

## 1. 不可协商约束

| # | 约束 |
|---|---|
| 1 | **流语义对前端等价**：事件序列、载荷 JSON、complete 终态语义不变；帧字节允许 spec 级差异（蓝图明示），但差异必须限于帧骨架形态（冒号空格），**不得**改事件名 `message` 与 data JSON 内容 |
| 2 | 断线续接与多副本的 streamManager 轮询模型**原样保留**（只换写面与线程归属） |
| 3 | 闸门：`mvn -B clean verify`（根 pom，JAVA_HOME+ZZ_PG_JDBC 口径）+ 守卫 49/49；金片重锚须在 WP0 结论指导下进行 |
| 4 | 并行会话的 5 个未提交 `scripts/*.sh` 改动不触碰、不提交、不回滚 |

## 2. 工作包

### WP0 帧字节探针（0.25 天，先于一切）

**Files:** 新 `app/src/test/java/com/ragagent/session/controller/sse/EmitterFrameBytesTest.java`（临时探针，结论落账后可留作回归）。

- [ ] 用真实 `SseEmitter` + MockMvc/独立 servlet 写一帧 `event("message").data(<json>)`，捕获实际输出字节
- [ ] 对照金片骨架 `event:message\ndata:<json>\n\n`：**一致** → D-1 判"金片零改"；**不一致**（如冒号后空格）→ D-1 判"重锚"，后续 WP5 执行
- [ ] 结论写台账（Ruling 形式），探针测试保留为 emitter 字节回归钉

### WP1 过滤器 async 语义（0.25 天）

**Files:** `AuthFilter`、`APIKeyScopeCleanupFilter`、`EmbedAuthFilter` 各加 `@Override shouldNotFilterAsyncDispatch() { return true; }`（带理由 javadoc：SseEmitter 引入 async dispatch 后鉴权/清理不得重复执行）。

- [ ] 三处 override + javadoc；编译 + 定向测试（既有过滤器测试若断言调用次数需同步）
- [ ] commit `feat(app): SSE 异步化前置——过滤器不做 async dispatch 重复执行`

### WP2 写面换轨：SseSink 载荷级 + EmitterSseSink + 过渡实现（0.5 天）

**Files:** 
- `framework/vocabulary/SseSink.java`：接口改形 `void send(String eventName, String json) throws IOException`（javadoc 重写：载荷级出口；applyHeaders 删除——头由控制器设）
- 新 `app/src/main/java/com/ragagent/session/controller/sse/EmitterSseSink.java`：包 SseEmitter，`send` → `emitter.send(SseEmitter.event().name(eventName).data(json))`
- `ServletSseSink`：**过渡改形**——同新签名，内部沿用旧拼帧直写（`event:`+name+`\n`+`data:`+json+`\n\n`），行为字节不变
- `SseFrameWriter` → 拆解：`frameOf` 迁 `SseContract`（或新 `SseFrames` 工具，静态方法），类删除；`SseFrameSink` 接口改形为 `String jsonOf(StreamResponse)` + `void write(SseSink, StreamResponse)`（write 内部 `sink.send(EVENT_NAME, jsonOf(...))`）
- 编排层调用点改造：`QaSseOrchestrator`/`TurnStreamPump`/`StreamEventEmitter` 的 writeFrame→send（+`applyHeaders` 调用点上移控制器）

- [ ] 全量闸门（此批后旧 servlet 路径字节仍逐字节不变——金片必须仍绿）
- [ ] commit `refactor(session): SseSink 端口载荷级改形，EmitterSseSink 就位，Servlet 路径过渡保持字节不变`

### WP3 三入口切换 emitter（1 天）

**Files:** `KnowledgeQaController`、`EmbedChannelDelegateOps`、`SessionStreamController`；新 `app/src/main/java/com/ragagent/session/controller/sse/SseEndpoints.java`（统一构造：emitter timeout 常量、四头设置、onCompletion/onError/onTimeout→clientGone/取消面接线）。

- [ ] KnowledgeQa 入口：返回 `SseEmitter`，QaEntryRunner 泵在虚拟线程（现状已虚拟线程，改等待模型：请求线程立即返回）
- [ ] EmbedChannelDelegateOps 两方法同型切换
- [ ] SessionStreamController 续接口：回放+轮询循环整体挪入虚拟线程，`Thread.sleep(POLL_INTERVAL)` 原样保留（断线续接语义），emitter 回调把 `clientGone` 置位（替代 IOException 感知路径，两者并存）
- [ ] 超时统一：`new SseEmitter(SseEndpoints.STREAM_TIMEOUT_MILLIS)`（与现 MAX_STREAM_DURATION_MILLIS 对齐）
- [ ] 全量闸门 + 手工冒烟（本地起服务打一条 embed 对话流，见台账记录要求）
- [ ] commit `feat(session,channel): 三 SSE 入口返回 SseEmitter，请求线程即释，断连回调感知`

### WP4 删旧件 + vocabulary 归零（0.5 天）

**Files:** 删 `ServletSseSink`、`SseFrameWriter` 残壳、`SseContract` 的 servlet 直写面（头常量保留）；`SseSink` → `channel/api`（EmbedSessionPort 签名消费，session L3→channel L2 合法）；删 `framework/vocabulary/` 目录；`VocabularyManifestTest` 删 vocabulary 测试（tenant 白名单测试保留，类名改 `TenantManifestTest` 或保留原名改 javadoc）。

- [ ] `grep framework.vocabulary` = 0；module-package-map/packages.md/domain-index 重生成；A16/A20 等测试锚核一遍
- [ ] commit `refactor(framework): SSE 旧写出清——ServletSseSink/SseFrameWriter 删除，SseSink 归 channel/api，vocabulary 归零`

### WP5 金片重锚（视 D-1，0~0.5 天）+ 终验

- [ ] 若 D-1=重锚：`SseFrameWriterTest` → 迁为 `SseContract/SseFrames + EmitterFrameBytesTest` 断言新骨架；`KnowledgeQaContractTest` 帧断言同步；前端 fetch-event-source 兼容性以 mcp-server 同款 `startswith("data:")` 口径复核
- [ ] 终态验收：`mvn -B clean verify` + 49 守卫 + 台账闭合（测试差值对账）

## 3. 拍板项

| # | 决策 | 推荐 |
|---|---|---|
| D-1 | 金片策略 | **WP0 实测定**：骨架一致→零改；不一致→重锚（蓝图授权原文在手；mcp-server 已核实 spec 解析） |
| D-2 | SseSink 终态家 | `channel/api`（EmbedSessionPort 签名消费方；session/console L3 引用合法） |
| D-3 | SessionStreamController 轮询模型 | 保留 Thread.sleep 轮询（断线续接依赖 streamManager offset；重构推送模型不属本批） |

## 4. 坑清单（上轮 25 条沿用，新增）

26. **OncePerRequestFilter 与 async dispatch**：SseEmitter 触发 async，过滤器默认二次执行——WP1 必须先于 WP3。
27. **emitter.send 的线程语义**：send 可从任意线程调用但 complete/completeWithError 只能一次；泵的 finally 收尾与 onCompletion 回调并发时以 AtomicBoolean 幂等。
28. **头部提交时机**：async 模式下 setHeader 必须在第一次 send 前完成——SseEndpoints 构造期统一设，不在泵里设。

## 5. 执行台账（执行 Agent 填写）

| 批 | 日期 | 内容 | 验收数字 | 备注 |
|---|---|---|---|---|
| 基线 | | @b1b5cca3e | 5240/0/2 · 49/49 | 并行 scripts 改动在野 |

## Review Focus

1. **流语义等价**：事件序列/complete 终态/断线续接 offset 回放——三入口各有测试面覆盖否（KnowledgeQaContractTest 覆盖入口一，续接靠现有 streamManager 测试）。
2. **async 双跑**：三过滤器 override 后，鉴权计数类测试仍绿。
3. **线程安全**：emitter 回调与泵 finally 的并发收尾幂等。
4. **vocabulary 归零的守卫联动**：module-map/packages/棘轮三件套同 commit。
5. **过渡批字节不变**：WP2 后金片仍绿（旧路径未变），WP3 才切流量。

## 6. Deferred 修复批台账（2026-11-22 追加，edc13619e）

| 项 | 内容 | 验收 |
|---|---|---|
| 平台入口 live 契约测试 | `KnowledgeQaLiveContractTest`（3 门）：400 同步信封 / AGENT 撞 live run 409 信封（completeError→异步错误派发）/ **租户身份断言**（memo 落 sessions.agent_config，F1 缺陷形态回归钉）；种子面=tenants+tenant_members+users+custom_agents（smart-reasoning）+直插 sessions（repository.create 会覆盖 id） | 3/3 绿，CI 盲区补齐 |
| F8 | Content-Type 实测值 `text/event-stream`（charset 后缀消失——spec 合法线上形态变化，SseContract 契约表本就无 charset）逐字钉回两处（SessionStreamControllerTest + EmitterFrameBytesTest） | 逐字断言恢复 |
| F11 | agentChat 分派语义注释现在时（按 agent 配置 agentMode 判定，尊重显式配置；quick-answer 默认解析为 NORMAL 与历史一致） | 迁移叙事棘轮绿 |
| **附带 Critical 前提修复** | **RbacInterceptor 跳过 ASYNC 派发**：completeError 的错误派发重跑拦截器时 AuthFilter finally 已清 TenantContext → 角色恒空 403（409 信封渲染的隐藏前提） | live 409 断言成立的前提 |
| C2/SCC 基线 | controller-catch 121 处登记（两入口 catch(RuntimeException)=异步边界收尾）；包级 SCC 5 组固化 | 49/49 |

## 7. 流程纪律修正（2026-11-22，用户复核提出）

**注释/文档-only 批次的最小验收口径**（全量 verify 对注释改动零检出能力）：生成物重生成（gen-arch-index 等）+ 守卫套件 + 受影响模块 javadoc-check。全量 verify 保留给代码批次。另：契约纯度守卫修复——扫描段 vocabulary 已删但白名单静默跳过（B168 同款覆盖丢失），改为消失段显式报错（commit 02446a4ab）。
