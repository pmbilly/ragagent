# chat 管线事件表

> 由 `scripts/gen-pipeline-map.py` 从代码生成，**不要手改**；`--check` 守卫防漂移。
> 读/写字段来自插件里对 `ChatManage` 的 getter/setter 调用，是粗略数据流。

## 事件 → 插件（按注册顺序）

### `LOAD_HISTORY`

| 插件 | 读 | 写 |
|---|---|---|
| PluginLoadHistory | `maxRounds`、`sessionId` | `history` |

### `MEMORY_RECALL`

| 插件 | 读 | 写 |
|---|---|---|
| PluginMemoryRecall | `eventBus`、`query`、`sessionId`、`usedMemories` | `memoryPrompt`、`usedMemories` |

### `QUERY_UNDERSTAND`

| 插件 | 读 | 写 |
|---|---|---|
| PluginQueryUnderstand | `attachments`、`chatModelId`、`chatModelSupportsVision`、`enableRewrite`、`history`、`imageDescription` | `history`、`imageDescription`、`intent`、`rewriteQuery`、`systemPromptOverride` |
| PluginExtractEntity | `chatModelId`、`knowledgeBaseIds`、`knowledgeIds`、`query`、`tenantId` | `entity`、`entityKbIds`、`entityKnowledge` |

### `CHUNK_SEARCH`

| 插件 | 读 | 写 |
|---|---|---|
| PluginSearchParallel | `entity`、`results`、`rewriteQuery`、`sessionId` | `results` |
| PluginSearch | `embeddingTopK`、`enableQueryExpansion`、`keywordThreshold`、`knowledgeBaseIds`、`knowledgeIds`、`results` | `results` |

### `CHUNK_SEARCH_PARALLEL`

| 插件 | 读 | 写 |
|---|---|---|
| PluginSearchParallel | `entity`、`results`、`rewriteQuery`、`sessionId` | `results` |

### `ENTITY_SEARCH`

| 插件 | 读 | 写 |
|---|---|---|
| PluginSearchParallel | `entity`、`results`、`rewriteQuery`、`sessionId` | `results` |
| PluginSearchEntity | `entity`、`entityKbIds`、`entityKnowledge`、`graph`、`results`、`sessionId` | `graph`、`results` |

### `CHUNK_RERANK`

| 插件 | 读 | 写 |
|---|---|---|
| PluginRerank | `faqPriorityEnabled`、`faqScoreBoost`、`rerankModelId`、`rerankThreshold`、`rerankTopK`、`results` | `rerankThreshold`、`results` |
| PluginWikiBoost | `results`、`searchTargets` | — |
| PluginMemoryAffinity | `results` | — |

### `WEB_FETCH`

| 插件 | 读 | 写 |
|---|---|---|
| PluginWebFetch | `results`、`webFetchEnabled`、`webFetchTopN`、`webSearchEnabled` | — |

### `CHUNK_MERGE`

| 插件 | 读 | 写 |
|---|---|---|
| PluginMerge | `query`、`results`、`rewriteQuery`、`sessionId`、`tenantId` | `results` |

### `DATA_ANALYSIS`

| 插件 | 读 | 写 |
|---|---|---|
| PluginDataAnalysis | `chatModelId`、`query`、`results`、`sessionId` | `results` |

### `INTO_CHAT_MESSAGE`

| 插件 | 读 | 写 |
|---|---|---|
| PluginIntoChatMessage | `attachments`、`chatModelSupportsVision`、`faqDirectAnswerThreshold`、`faqPriorityEnabled`、`imageDescription`、`intent` | `renderedContexts`、`userContent` |

### `CHAT_COMPLETION`

| 插件 | 读 | 写 |
|---|---|---|
| PluginChatCompletion | `chatModelId`、`history`、`sessionId`、`userContent` | `chatResponse` |

### `CHAT_COMPLETION_STREAM`

| 插件 | 读 | 写 |
|---|---|---|
| PluginChatCompletionStream | `chatModelId`、`eventBus`、`history`、`sessionId`、`userContent` | — |

### `FILTER_TOP_K`

| 插件 | 读 | 写 |
|---|---|---|
| PluginFilterTopK | `rerankTopK`、`results`、`sessionId` | `results` |

## 注册顺序（`QaWiring`）

1. PluginRerank
2. PluginWebFetch
3. PluginMerge
4. PluginDataAnalysis
5. PluginIntoChatMessage
6. PluginChatCompletion
7. PluginChatCompletionStream
8. PluginFilterTopK
9. PluginQueryUnderstand
10. PluginLoadHistory
11. PluginMemoryRecall
12. PluginExtractEntity
13. PluginSearchParallel
14. PluginWikiBoost
15. PluginMemoryAffinity
16. PluginSearch
17. PluginSearchEntity
