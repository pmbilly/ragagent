# 事件面（SSE / Redis 流）snake_case 清单（B93b 侦察，2026-10-08）

> **状态：已实施**（B93b 完成，见 §15.1 与 batch-records）。本清单保留为口径与三方消费面的事实依据。

口径：`server/src/main/java/com/ragagent/event/**`（26 个载荷类）+ `stream/**`（流条目）+ `session/sse/**`（SSE 帧）。
数据源：主源码定义点；`server/src/test/**`（实录，逐字夹具）；`frontend/src`（逐站点点算，**词边界**口径）。

## 0. 关键结构发现（决定了这批的形态与成本）

**事件载荷是「Java camelCase 字段 + 显式 `@JsonProperty("snake_case")`」**——26 个类、**151 处注解**，键名映射集中在注解里：

| 形态 | 数量 | 含义 |
|---|---|---|
| 注解值 == 字段名的 snake 形式 | **149** | 注解**可整条删除**，Jackson 直接按 camel 字段名出键（与全仓 DTO 惯例一致：§2.4「JSON 字段名 = Java 字段名」） |
| 注解值 ≠ 字段名 | **2** | `ToolApprovalRequiredData` / `MCPOAuthRequiredData` 的 `requested_at` ↔ 字段 `requestedAtUnix` ⇒ 不能删、要改值（新键 `requestedAtUnix`，语义更准：**Unix 秒**） |

⇒ 本批在后端**不是"改名"而是"删注解"**（净减 ~149 行），这是最大的收益点；风险是"字段名于是成为线格式契约"——与全仓 DTO 面已完全一致，且由 `ArchitectureRulesTest` R2/R10 之外的既有测试兜底。

## 1. 汇总

| 面 | 数量 | 消费方 | 是否本批 |
|---|---|---|---|
| 事件载荷 distinct 键 | **78** | 前端事件处理器 + Redis 回放 | 其中**复合键 48 个会变**；30 个单词键（`content`/`query`/`error`/`type`…）**不变** |
| 载荷类 | 26 | — | 151 注解 → 删 149 / 改 2 |
| `event`+`stream` 手搓键 | **59** | 同上（多数与载荷键同名） | 逐站点判定（含"值"与"列名"混入） |
| **EventType 事件名（值）** | **38** | 前端按名 switch（`tool_approval_required` ×4…） | ❓**决策点 1**：值的语义 vs Go 时代命名 |
| **URL 路径变量**（`{session_id}`/`{channel_id}`…） | `{channel_id}` 27 / `{session_id}` 21 / `{pending_id}` 6 / `{message_id}` 4 / `{inv_id}` 3 / `{user_id}` 2 / `{svc_id}` 2 / `{key_id}` 2 …**且已有 `{sessionId}`（`MessageController`）= 现状混用** | 前端 `api/chat/*` 拼 URL；外部调用方 | ❓**决策点 2**：属 HTTP 面（阶段 3 未覆盖到此层） |
| 前端消费面 | **55 文件 / 532 处**（词边界，仅复合键） | — | 逐站点分类（载荷读 / URL 位 / 局部变量名） |
| 实录命中 | **336 处**（46B 81+36+…、46C 24+…；60 键有命中 / 24 复合键零实录） | 逐字夹具禁手改 | 随批更新（与 B88 同法） |

## 2. A. 载荷键（48 复合键 × 消费面）

`实录` = `GoRecording*.java` 命中；`其它测试` = 非实录测试命中（处/文件）。

| 键 | 实录 | 分布 | 其它测试 | 前端 |
|---|---|---|---|---|
| `session_id` | 105 | 46B 81 / 46C 24 | 106/23 | ✓ |
| `final_answer` | 43 | 46B 36 / 46C 7 | 9/3 | ✓ |
| `tool_call_id` | 39 | 46B 28 / 46C 11 | 34/6 | ✓ |
| `tool_name` | 35 | 46B 25 / 46C 10 | 33/10 | ✓ |
| `duration_ms` | 17 | 45C 15 / 46C 1 / 46B 1 | 42/10 | ✓ |
| `knowledge_base_id` | 14 | 45B 14 | 195/45 | ✓ |
| `message_id` | 14 | 46B 13 / 46C 1 | 62/10 | ✓ |
| `args_json` | 12 | 45C 12 | 3/1 | — |
| `total_steps` | 11 | 46B 11 | 8/3 | ✓ |
| `agent_steps` | 11 | 46B 11 | 5/4 | ✓ |
| `tool_calls` | 9 | 46B 9 | 33/9 | ✓ |
| `tool_input` / `tool_output` | 6 / 6 | 46B | 7/1 · 6/1 | ✓ |
| `user_message_id` | 3 | 46B 2 / 46C 1 | 4/3 | ✓ |
| `steer_id` / `tenant_id` / `user_id` | 2 / 1 / 1 | 46B / 45B / 46C | 6/1 · 234/54 · 61/25 | ✓ |
| `total_duration_ms`、`messages_before`、`messages_after`、`tokens_before`、`tokens_after`、`rewritten_query`、`split_turn` | 各 1 | 46B / 46C | 各 3-10 | ✓ |
| 零实录（24 键，仍要改）：`service_id`、`request_id`、`pending_id`、`assistant_message_id`、`timeout_seconds`、`requested_at`、`model_id`、`mcp_tool_name`、`service_name`、`input_count`、`output_count`、`error_code`、`merge_type`、`original_query`、`result_count`、`retrieval_type`、`top_k`、`is_fallback`、`is_stream`、`registered_tool_name`、`timed_out`、`token_count`、`knowledge_refs`、`stream_chunk` | 0 | — | 各 2-35 | ✓ |

**跨面同名警告（逐站点判定，勿平替）**：`tenant_id` / `user_id` / `knowledge_base_id` / `tool_name` / `session_id` 在本仓**同时**出现在 ① 事件载荷键、② DB 列名（`@TableField`）、③ 工具面入参/schema、④ URL 路径。B88 的教训（`AgentToolKbBackends` 的 JDBC 列标签、`SearchTarget` 常量值）在这里会重演——**必须按文件+按行分类**。

## 3. B. `event`/`stream` 手搓键（59 distinct，逐站点判定）

```
agent_execution agent_steps args_json assistant_message_id context_compacted duration_ms error_code
final_answer input_count is_fallback is_stream knowledge_base_id knowledge_refs mcp_oauth_required
mcp_oauth_resolved mcp_tool_name memory_recalled merge_type message_id messages_after messages_before
model_id original_query output_count pending_id registered_tool_name request_id requested_at result_count
retrieval_type rewritten_query service_id service_name session_id session_title split_turn steer_id
stream_chunk tenant_id timed_out timeout_seconds token_count tokens_after tokens_before tool_approval_required
tool_approval_resolved tool_call tool_call_id tool_calls tool_input tool_name tool_output tool_result
top_k total_duration_ms total_steps user_id user_message_id user_message_injected
```

**角色分类（实施时必须按此过滤）**

| 角色 | 样例 | 处置 |
|---|---|---|
| 载荷键（与 A 同名） | `session_id`、`tool_call_id`、`duration_ms`… | 随 A 一起 camel |
| **事件名（值）** | `tool_call`、`tool_result`、`final_answer`、`context_compacted`、`memory_recalled`、`user_message_injected`、`mcp_oauth_required/resolved`、`tool_approval_required/resolved` | ❓决策点 1（值） |
| 内部/DB 面 | `agent_steps`、`agent_execution`、`args_json`、`knowledge_refs` | 逐站点判定（列名/RPC 载荷） |

## 4. C. EventType 事件名（38 个，**值**）

```
query.received query.validated query.preprocess query.rewrite query.rewritten
retrieval.start retrieval.vector retrieval.keyword retrieval.entity retrieval.complete
rerank.start rerank.complete merge.start merge.complete
chat.start chat.complete chat.stream
agent.query agent.plan agent.step agent.tool agent.complete
thought tool_call tool_result reflection references final_answer
tool_approval_required tool_approval_resolved mcp_oauth_required mcp_oauth_resolved
error memory_recalled context_compacted user_message_injected session_title stop
```

形态是**点号层级 + snake 复合**混合（`agent.step` 与 `tool_approval_required` 并存）。前端按名 switch（`tool_approval_required` ×4、`agent.step` 等）。
**决策点 1**：(i) 不动（值属语义，§2.4）；(ii) 只统一复合名（`tool_approval_required` → `toolApprovalRequired`）保留点号；(iii) 全改 camel 且去点号。

## 5. D. URL 路径变量（HTTP 面，**阶段 3 未覆盖**）

| 形态 | 现状 |
|---|---|
| 后端路径模板 | `{channel_id}` 27、`{session_id}` 21、`{pending_id}` 6、`{message_id}` 4、`{inv_id}` 3、`{user_id}` 2、`{svc_id}` 2、`{key_id}` 2 …**且 `MessageController` 用 `{sessionId}`** ⇒ **混用** |
| 前端拼 URL | `api/chat/index.ts`（`/api/v1/sessions/${session_id}`、`/api/v1/knowledge-chat/${data.session_id}`）、`api/chat/streame.ts`（`startStream` 参数名）、`useEmbedChatSession.ts` 等 |

**决策点 2**：URL 路径变量与 SSE 事件键**不是同一层**（前者是路由模板、后者是 JSON 键）。要不要一起做？（一起做则"HTTP 面全 camel"，但会动**外部可见 URL**——embed 渠道 URL 是给第三方的。）

## 6. E. 前端消费面（55 文件 / 532 处，词边界口径）

| 处数 | 文件 |
|---|---|
| 117 | `composables/useChatStreamHandler.ts` |
| 106 | `views/chat/components/AgentStreamDisplay.vue` |
| 64 | `views/chat/index.vue` |
| 35 | `api/chat/index.ts` |
| 19 / 12 | `utils/steerStreamFork.ts` / `api/chat/steer.ts` |
| 11 | `useChatStreamHandler.test.mjs`、`utils/mcpToolDisplay.test.ts`、`utils/steerStreamFork.test.mjs`、`views/integrations/ApiIntegrationSettings.vue` |
| 10 | `utils/rag-pipeline-history.test.mjs`、`utils/finalAnswer.ts` |

**逐站点分类要求**：`载荷读`（改）／`URL/参数位`（看决策点 2）／`局部变量名`（可保留 snake，与线格式无关）／`localStorage 键`（前端私有，可不动）。

## 7. F. 外部决定面（不动，§14.6 已登记）

- **LLM provider 流字段**：OpenAI/Anthropic 的 `tool_calls`/`arguments`/`index`/`delta`/`finish_reason`（`llm/domain/StreamResponse`、`llm/chat/*`）—— 入站协议，键名由对方决定。
- **IM 平台载荷**：飞书 `aibot_msg_callback`/`aibot_event_callback`/`aibot_respond_msg`/`aibot_subscribe`、`app_id`/`app_secret`/`app_token`（`im/**`、`datasource/connector/feishu/**`）。
- **connector**：`datasource/connector/**`（GitLab/Notion/RSS/Ima…）字段名由对方 API 决定。
- **tracing**：`tracing/langfuse/**`（Langfuse 自有字段）。
- **DB 列名**：`@TableField("snake")` / `@Results` 映射 / JDBC `rs.get*("snake")`（B93c 面，本批不碰）。
- **SSE 帧本身**：`event:` / `data:`（W3C SSE 规范字段）+ `SseFrameWriter` 的"冒号后无空格"是**逐字契约**（有测试钉），不动。

## 8. G. 守卫现状

| 守卫 | 与事件面的关系 |
|---|---|
| `scripts/check-json-key-case.py` | BASELINE 与我们的事件键**交集为空** ✓（它管的是工具面/DTO 面）⇒ 本批要**新增/收紧**事件面口径（否则做完没有防漂移） |
| `frontend/src/components/crossFaceKeyContract.test.ts` | 钉 3 个**跨面键** `display_name`/`error_message`/`original_file_name`（知识卡片视图模型 ↔ 接口 camel）——与事件面无关 ✓ 不动 |
| `ArchitectureRulesTest` | 与本批无关（代码级规则）✓ |

## 9. 实施建议（供拍板）

1. **后端**：删 149 条可删注解 + 改 2 条（`requested_at` → `requestedAtUnix`）+ `event`/`stream` 手搓键按角色过滤后 camel。**同时**给载荷类加一条 ArchUnit/脚本守卫："事件载荷类不得出现 `@JsonProperty` 的 snake 值"（防回流，成本极低）。
2. **前端**：55 文件逐站点分类后改（载荷读必改；URL 位看决策点 2；局部变量名不动）。
3. **实录**：336 处命中（46B/46C 为主，另有 45B/45C 的嵌套面）随批更新；**注意 46B/46C 是跨面文件**（B88 时曾因误改被回退）⇒ 只改事件载荷位、保留工具面/外部位。
4. **批次建议**：**单批做**（载荷删注解 + 手搓键 + 前端 + 实录），因为 46B/46C 同时钉两侧，拆批会出现"改一半、两个面都红"。
5. **探针要求**：① 载荷类新加 snake 注解 → 守卫红；② 前端读旧键 → 相关测试红；③ 空值/缺键形态逐字比对（`tool_calls:null` 恒输出等注释里的坑）。
