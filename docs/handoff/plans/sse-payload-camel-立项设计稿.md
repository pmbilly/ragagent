# 桶 A·SSE 事件载荷 camel 化——立项设计稿（B72 产出，未执行）

> 2026-10-05 前端 snake 键全量排查（B72）的衍生立项。结论：**本面不是纯内部面，全面翻转必须走协议版本化**；现状推荐维持 snake 登记（路线 C），待真实协议演进需求出现时再启动路线 A。

## 结论先行（Phase 0 实证）

排查期曾把 SSE 载荷族归入「桶 A·纯在线无存量、可同 PR 翻转」。Phase 0 核查推翻了这个前提：

- `POST /api/v1/agent-chat/{session_id}`（KnowledgeQaController.java:157）是**集成页对外文档化的流式 API**：`ApiIntegrationSettings` 的 playground 把原始 SSE 帧直接展示给集成者（`playground.stream_output = compactText(raw)`），且 `apiPlaygroundSSE.ts` 按 snake `response_type` 解析（`answer`/`error`/`complete`）。
- 即外部 API-Key 集成者按 **snake 事件协议**对接本产品。整族翻转 = 破坏已发布对外契约。

因此本面 = 桶 A（工具结果载荷不落库，`ToolResultPersist.compactToolSummary` 把历史压成一行英文摘要——无 jsonb 存量）∩ 桶 C（有已发布外部消费者）。widget.js 不解析 SSE 载荷（0 命中、无 EventSource），iframe postMessage 协议独立，不受影响。

## 迁移面清单（若立项）

- **后端**：`event/payload/` 23 文件 ~70 键（显式 `@JsonProperty` snake）+ `llm/domain/StreamResponse` + `QaSseOrchestrator`/`AgentStreamBridge`/`StreamEventEmitter` 手工 `put(...)` 键。
- **前端**：AgentStreamDisplay.vue(~517)、useChatStreamHandler.ts(~213)、types/tool-results.ts(~165)、tool-results/*.vue、utils/referenceSources、grepResultsGroup、mcpToolDisplay、rag-pipeline-history、tool-capabilities ≈ **1,500 处**。
- **必须留 snake 或随版本走的共享键**：`session_id`/`message_id`（URL 路径变量 + embed 面）、`response_type`（对外文档化枚举键）。

## 三路线

| 路线 | 内容 | 评价 |
|---|---|---|
| **A·版本化端点** | `/api/v2/agent-chat` 或 `Accept` 头协商；内部聊天面切 camel，旧端点维持 snake 冻结 | **推荐**——但只在有真实协议演进需求时值得（顺势 v2 才摊得平成本） |
| B·双发窗口 | 同事件双键并存一版 | 不推荐——前端读侧双读+载荷翻倍，正是 B14 消灭过的「两套词汇」反模式 |
| **C·维持 snake 登记**（现状默认） | 把 SSE 载荷族正式登记为「对外权威面」，与 MCP OAuth（RFC 8414）同桶 | 零成本；LLM 生态线格式（OpenAI/Anthropic）本身是 snake，事件载荷与上游词汇同形，日志对照反而一致 |

## 回归风险提示（启动 A 时必读）

Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` + `JsonMappers.lenient()` ⇒ 任何漏改 = 静默 undefined、不报错（本仓头号缺陷族）。启动前必须：

1. 后端全量 `@JsonProperty` snake 键盘点脚本先行（基线 ≥70 键逐条销号）；
2. 前端把 B72 钉测（crossFaceKeyContract）扩展为 SSE 面棘轮；
3. B0 式 SSE 全事件真机走查（14 事件类型逐个对渲染）。

## 登记

B72/B73 执行记录见 `docs/handoff/records/batch-records.md`；本稿只立项不排期。
