# RAG 管线：阶段、插件与装配

管线不是一条写死的 `for` 循环，而是 **事件 → 插件链**：

- `PipelineEventType` 是 14 个值的 enum（编译期注册表）；
- `EventManager` 按 `eventType` 维护 `List<Plugin>` 与闭包链；
- `QaWiring` 负责注册；
- `PipelineBuilder.presets()` 决定某条业务路径按什么顺序触发哪些阶段。

## 执行语义

`Plugin.onEvent(eventType, chatManage, next)` 返回 `PluginError`：

- `null` = 成功；
- 非 `null` = 该阶段失败（`PluginError.SEARCH_NOTHING` 是"没搜到"的哨兵，不是异常）；
- `next.invoke()` 调用链的下一环。

`EventManager.buildHandler` 从**后往前**包闭包，因此**注册顺序就是执行顺序**：先注册的在外层，先执行。

## 14 个阶段

注册位置：`domains/.../session/service/QaWiring.java:371-391`。

| 阶段 | 执行的插件（按序） | 说明 |
|---|---|---|
| `LOAD_HISTORY` | `PluginLoadHistory` | 载入会话历史；`rag_stream` 第一站 |
| `MEMORY_RECALL` | `PluginMemoryRecall` | 长期记忆召回（条件阶段） |
| `QUERY_UNDERSTAND` | `PluginQueryUnderstand` → `PluginExtractEntity` | 查询改写/意图理解，再做实体抽取 |
| `CHUNK_SEARCH` | `PluginSearch` | 普通 `rag` 预设的分块检索 |
| `CHUNK_SEARCH_PARALLEL` | `PluginSearchParallel` | 主问答默认；内部复用 `PluginSearch` + `PluginSearchEntity`（构造注入，不注册） |
| `ENTITY_SEARCH` | `PluginSearchEntity` | 实体/图谱检索（条件阶段） |
| `CHUNK_RERANK` | `PluginRerank` → `PluginWikiBoost` → `PluginMemoryAffinity` | 三插件按注册序执行 |
| `WEB_FETCH` | `PluginWebFetch` | 网页抓取（条件阶段） |
| `CHUNK_MERGE` | `PluginMerge` | 合并/去重 |
| `FILTER_TOP_K` | `PluginFilterTopK` | TopK 过滤 |
| `DATA_ANALYSIS` | `PluginDataAnalysis` | 表格/数据分析（条件阶段） |
| `INTO_CHAT_MESSAGE` | `PluginIntoChatMessage` | 检索结果转消息与引用 |
| `CHAT_COMPLETION` | `PluginChatCompletion` | 非流式生成 |
| `CHAT_COMPLETION_STREAM` | `PluginChatCompletionStream` | 流式生成（主路径） |

## 预设管线

`PipelineBuilder.presets()`：

| 预设 | 阶段序列 |
|---|---|
| `chat` | `CHAT_COMPLETION` |
| `chat_stream` | `CHAT_COMPLETION_STREAM` |
| `chat_history_stream` | `LOAD_HISTORY` → `CHAT_COMPLETION_STREAM` |
| `rag` | `CHUNK_SEARCH` → `CHUNK_RERANK` → `CHUNK_MERGE` → `INTO_CHAT_MESSAGE` → `CHAT_COMPLETION` |
| `rag_stream` | `LOAD_HISTORY` → `QUERY_UNDERSTAND` → `CHUNK_SEARCH_PARALLEL` → `CHUNK_RERANK` → `CHUNK_MERGE` → `FILTER_TOP_K` → `DATA_ANALYSIS` → `INTO_CHAT_MESSAGE` → `CHAT_COMPLETION_STREAM` |

条件阶段（记忆召回、网页抓取、实体检索）由业务配置追加，或由 `PluginSearchParallel` 在内部执行；不在默认 `rag_stream` 序列里。

## 为什么 `PluginSearchParallel` 的内部插件不注册

它把"分块检索"和"实体检索"并行 fan-out，再合并结果。两个内部插件由 `QaWiring` 通过构造器显式传入（不再藏在类体里 `new`），但**不注册**到 manager：

- 若注册，`CHUNK_SEARCH` / `ENTITY_SEARCH` 会多出第二个 handler，执行序与结果合并语义都会变；
- 并行分支会与主链共享同一份 `ChatManage` 可变状态，产生竞态。

所以它们是**内部执行体**，生命周期与 `PluginSearchParallel` 绑定。看到构造器参数不要"顺手"改成 `mgr.register(...)`。

## 加一个新阶段

1. 在 `PipelineEventType` 加枚举值；
2. 实现 `Plugin`，`activationEvents()` 返回新常量；
3. 在 `QaWiring` 注册——**顺序即执行顺序**；
4. 如果默认路径要跑，加入 `PipelineBuilder.presets()` 的对应预设；
5. 跑 `:domains:test --tests "com.ragagent.session.*"` 与录制测试；`PipelineRegistryCompletenessTest` 会要求该阶段至少注册一个插件；
6. 不要重排已有阶段：SSE 帧顺序与金片都锁着。

## 相关文件

- `domains/.../session/service/rag/PipelineEventType.java`
- `domains/.../session/service/rag/EventManager.java`
- `domains/.../session/service/rag/PipelineBuilder.java`
- `domains/.../session/service/rag/plugin/`
- `domains/.../session/service/QaWiring.java`
