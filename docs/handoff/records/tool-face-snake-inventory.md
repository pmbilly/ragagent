# 工具面 snake_case 键清单（B88 决策输入，2026-10-08）

> **执行结果（B88，2026-10-08 完成）**：本清单 30 输入键 + 107 输出键**已全量 camel 化**，工具名与 enum 值按用户决策保留 snake。
> 实际执行键集 **142 个**（清单外另有 5 个字符串拼接键：`tenant_id`/`created_at`/`updated_at`/`deleted_at`/`reported_by`，
> 以及复合键形态 `<tool>|<arg>`，如 `data_analysis|knowledge_id`）。
> - 后端主源码 41 文件 / 445 处；实录 3,037 处（仅工具面 45A/45B/45C/46A）；前端 84 文件涉及、32 文件改名。
> - 跨面同批：`ReferencesSupport`（引用载荷）与 `QaAttachmentResolver`（附件卡）的键必须与工具面同批改，否则 `ModelOutput` 渲染器读不到（实测复现）。
> - 保留 snake（非遗留）：工具名 36 个、schema enum 值（`list_servers`/`list_tools`…）、外部载荷读侧（`ocr_text`/`original_url`）、
>   websearch metadata `published_at`、JDBC 列名、SSE/事件面同名键（`session_id`/`tool_name`）、citation markup 属性 `chunk_id`。
> - 闸门：后端 4,836/0；前端 734/734 + `vue-tsc` 0 错；`scripts/check-json-key-case.py --strict` 绿（已摘除 `agent/tools/` 冻结豁免）。
> - 过程发现（真问题）：`ToolPolicy.sourceArgumentAllowed` 与 `SourceToolCodec` 共 6 处 `key.toLowerCase()` 键比较在 snake 时代是恒等操作，
>   改 camel 后全失配（句柄解析/检索目标/MCP 路由连锁挂）——已改大小写不敏感；详见 B88 记录。

口径：`server/src/main/java/com/ragagent/agent/tools/**` 中对**模型 / 前端 / 实录**三方可见的 snake_case **JSON 键**与**工具名**。
数据源：主源码定义点；`server/src/test/java/**/GoRecording*.java`（逐字夹具，禁手改）；`frontend/src`。
列义：`实录args / 实录out` = 命中该键的实录条数（输入参数侧 / 输出与 data 侧）；`前端` = 命中文件数。

## 汇总

| 面 | 数量 | 消费方 |
|---|---|---|
| 输入 schema 键（`SCHEMA_JSON.properties`） | 30 | 模型（必按此产出参数） |
| 输出 / data 键（output 文本 + `ToolResult.data`） | 107 | 模型 + 前端渲染 |
| 工具名（值，非键） | 36 | 模型（函数名） |
| 合计 distinct（14 键输入输出两用） | 173 | — |

> 受影响的实录常量：**535** 条（分布在 7 个 `GoRecording*` 文件）。
> 受影响的前端文件：**137** 个。

## A. 输入 schema 键（模型必须照此产出参数）

| # | 键 | 定义文件数 | 实录 args | 实录 out | 前端文件 | 备注 |
|---|---|---|---|---|---|---|
| 1 | `branch_from_thought` | 1 | 1 | 0 | 0 |  |
| 2 | `branch_id` | 1 | 1 | 0 | 0 |  |
| 3 | `chunk_id` | 1 | 1 | 58 | 20 | 两面 |
| 4 | `end_chunk_index` | 1 | 3 | 0 | 0 |  |
| 5 | `faq_id` | 1 | 3 | 8 | 5 | 两面 |
| 6 | `faq_ids` | 1 | 2 | 0 | 0 |  |
| 7 | `is_revision` | 1 | 1 | 0 | 0 |  |
| 8 | `issue_id` | 2 | 5 | 0 | 0 |  |
| 9 | `issue_type` | 1 | 2 | 2 | 0 |  |
| 10 | `knowledge_base_id` | 1 | 32 | 52 | 17 | 两面 |
| 11 | `knowledge_base_ids` | 2 | 17 | 35 | 5 | 两面 |
| 12 | `knowledge_id` | 3 | 54 | 74 | 23 | 两面 |
| 13 | `knowledge_ids` | 1 | 5 | 2 | 2 |  |
| 14 | `line_offset` | 1 | 2 | 0 | 0 |  |
| 15 | `max_bytes` | 1 | 0 | 0 | 0 | 零实录 |
| 16 | `needs_more_thoughts` | 1 | 1 | 0 | 0 |  |
| 17 | `new_slug` | 1 | 2 | 1 | 3 | 两面 |
| 18 | `new_text` | 1 | 4 | 2 | 3 | 两面 |
| 19 | `next_thought_needed` | 1 | 10 | 7 | 0 | 两面 |
| 20 | `old_text` | 1 | 4 | 2 | 3 | 两面 |
| 21 | `page_type` | 1 | 8 | 3 | 4 | 两面 |
| 22 | `revises_thought` | 1 | 1 | 0 | 0 |  |
| 23 | `server_id` | 1 | 18 | 14 | 1 | 两面 路由文本 |
| 24 | `source_refs` | 2 | 3 | 0 | 0 |  |
| 25 | `start_chunk_index` | 1 | 4 | 0 | 0 |  |
| 26 | `suspected_knowledge_ids` | 1 | 1 | 2 | 0 |  |
| 27 | `thought_number` | 1 | 10 | 7 | 1 | 两面 |
| 28 | `tool_name` | 1 | 6 | 23 | 13 | 路由文本 |
| 29 | `tool_ref` | 1 | 0 | 9 | 3 | 两面 路由文本 |
| 30 | `total_thoughts` | 1 | 10 | 7 | 1 | 两面 |

## B. 输出 / data 键（模型读 + 前端渲染）

| # | 键 | 定义点数 | 定义示例 | 实录 out | 实录 args | 前端文件 | 备注 |
|---|---|---|---|---|---|---|---|
| 1 | `display_type` | 17 | TodoWriteTool.java:195 | 92 | 0 | 5 |  |
| 2 | `knowledge_base` | 3 | wiki/WikiReadSourceDocTool.java:168 | 70 | 33 | 34 |  |
| 3 | `knowledge_title` | 8 | wiki/WikiReadSourceDocTool.java:169 | 59 | 0 | 16 |  |
| 4 | `chunk_index` | 6 | wiki/WikiReadSourceDocTool.java:164 | 55 | 5 | 8 |  |
| 5 | `chunk_type` | 5 | wiki/WikiReadSourceDocTool.java:165 | 46 | 0 | 9 |  |
| 6 | `session_id` | 2 | SkillReadFileTool.java:134 | 39 | 0 | 22 |  |
| 7 | `match_type` | 2 | knowledge/QueryKnowledgeGraphTool.java:345 | 36 | 0 | 3 |  |
| 8 | `row_count` | 2 | data/DataAnalysisTool.java:359 | 27 | 0 | 1 |  |
| 9 | `total_chunks` | 4 | wiki/WikiReadSourceDocTool.java:320 | 27 | 0 | 4 |  |
| 10 | `kb_id` | 1 | knowledge/QueryKnowledgeGraphTool.java:480 | 25 | 0 | 10 |  |
| 11 | `match_snippet` | 2 | knowledge/GrepChunksTool.java:442 | 23 | 0 | 7 |  |
| 12 | `end_at` | 1 | knowledge/ListKnowledgeChunksTool.java:200 | 22 | 0 | 1 |  |
| 13 | `parent_chunk_id` | 1 | knowledge/ListKnowledgeChunksTool.java:201 | 22 | 0 | 0 | 前端未用 |
| 14 | `start_at` | 1 | knowledge/ListKnowledgeChunksTool.java:199 | 22 | 0 | 1 |  |
| 15 | `found_kbs` | 2 | wiki/WikiReadPageTool.java:186 | 17 | 0 | 1 |  |
| 16 | `kb_counts` | 2 | knowledge/QueryKnowledgeGraphTool.java:368 | 17 | 0 | 6 |  |
| 17 | `result_index` | 3 | web/WebSearchTool.java:239 | 17 | 0 | 4 |  |
| 18 | `total_steps` | 1 | TodoWriteTool.java:193 | 16 | 0 | 2 |  |
| 19 | `fetched_chunks` | 3 | wiki/WikiReadSourceDocTool.java:321 | 15 | 0 | 4 |  |
| 20 | `chunk_count` | 2 | knowledge/GetDocumentInfoTool.java:277 | 14 | 0 | 5 |  |
| 21 | `graph_config` | 2 | knowledge/QueryKnowledgeGraphTool.java:252 | 11 | 0 | 2 |  |
| 22 | `graph_configs` | 2 | knowledge/QueryKnowledgeGraphTool.java:251 | 11 | 0 | 0 | 前端未用 |
| 23 | `has_more` | 1 | McpCatalog.java:293 | 11 | 0 | 4 |  |
| 24 | `knowledge_base_type` | 1 | knowledge/KnowledgeSearchOutputFormatter.java:236 | 11 | 0 | 1 |  |
| 25 | `knowledge_metadata` | 1 | knowledge/KnowledgeSearchOutputFormatter.java:232 | 11 | 0 | 0 | 前端未用 |
| 26 | `query_type` | 1 | knowledge/KnowledgeSearchOutputFormatter.java:235 | 11 | 0 | 0 | 前端未用 |
| 27 | `source_query` | 1 | knowledge/KnowledgeSearchOutputFormatter.java:234 | 11 | 0 | 0 | 前端未用 |
| 28 | `chunk_results` | 1 | knowledge/GrepChunksTool.java:230 | 10 | 0 | 5 |  |
| 29 | `document_count` | 1 | knowledge/GrepChunksTool.java:234 | 10 | 0 | 3 |  |
| 30 | `knowledge_results` | 1 | knowledge/GrepChunksTool.java:231 | 10 | 0 | 5 |  |
| 31 | `max_results` | 1 | knowledge/GrepChunksTool.java:239 | 10 | 0 | 1 |  |
| 32 | `result_count` | 1 | knowledge/GrepChunksTool.java:233 | 10 | 0 | 2 |  |
| 33 | `total_matches` | 1 | knowledge/GrepChunksTool.java:235 | 10 | 0 | 2 |  |
| 34 | `ambiguous_slugs` | 1 | wiki/WikiReadPageTool.java:187 | 9 | 0 | 0 | 前端未用 |
| 35 | `omitted_slugs` | 1 | wiki/WikiReadPageTool.java:189 | 9 | 0 | 0 | 前端未用 |
| 36 | `truncated_slugs` | 1 | wiki/WikiReadPageTool.java:188 | 9 | 0 | 0 | 前端未用 |
| 37 | `chunk_hit_count` | 1 | knowledge/GrepChunksTool.java:481 | 8 | 0 | 4 |  |
| 38 | `distinct_patterns` | 1 | knowledge/GrepChunksTool.java:485 | 8 | 0 | 2 |  |
| 39 | `input_schema` | 1 | McpDiscoverTool.java:156 | 8 | 0 | 4 |  |
| 40 | `pattern_counts` | 1 | knowledge/GrepChunksTool.java:483 | 8 | 0 | 2 |  |
| 41 | `server_name` | 3 | McpCatalog.java:238 | 8 | 0 | 4 |  |
| 42 | `title_match` | 2 | knowledge/GrepChunksTool.java:438 | 8 | 0 | 3 |  |
| 43 | `total_chunk_count` | 1 | knowledge/GrepChunksTool.java:482 | 8 | 0 | 0 | 前端未用 |
| 44 | `total_pattern_hits` | 1 | knowledge/GrepChunksTool.java:484 | 8 | 0 | 2 |  |
| 45 | `incomplete_steps` | 1 | SequentialThinkingTool.java:221 | 7 | 0 | 0 | 前端未用 |
| 46 | `thought_history_length` | 1 | SequentialThinkingTool.java:218 | 7 | 0 | 0 | 前端未用 |
| 47 | `faq_question` | 5 | knowledge/ListKnowledgeChunksTool.java:309 | 6 | 0 | 5 |  |
| 48 | `graph_data` | 1 | knowledge/QueryKnowledgeGraphTool.java:371 | 6 | 0 | 0 | 前端未用 |
| 49 | `has_graph_config` | 1 | knowledge/QueryKnowledgeGraphTool.java:372 | 6 | 0 | 0 | 前端未用 |
| 50 | `kb_title` | 1 | knowledge/QueryKnowledgeGraphTool.java:481 | 6 | 0 | 0 | 前端未用 |
| 51 | `next_step` | 1 | McpCatalog.java:270 | 6 | 0 | 1 |  |
| 52 | `relevance_level` | 1 | knowledge/QueryKnowledgeGraphTool.java:341 | 6 | 0 | 2 |  |
| 53 | `total_edges` | 1 | knowledge/QueryKnowledgeGraphTool.java:491 | 6 | 0 | 0 | 前端未用 |
| 54 | `total_nodes` | 1 | knowledge/QueryKnowledgeGraphTool.java:490 | 6 | 0 | 0 | 前端未用 |
| 55 | `usage_instructions` | 2 | McpCatalog.java:217 | 6 | 0 | 2 | 路由文本 |
| 56 | `ocr_text` | 2 | knowledge/ListKnowledgeChunksTool.java:220 | 5 | 0 | 0 | 前端未用 |
| 57 | `page_size` | 2 | knowledge/ListKnowledgeChunksTool.java:245 | 5 | 0 | 6 |  |
| 58 | `plan_created` | 1 | TodoWriteTool.java:194 | 5 | 0 | 0 | 前端未用 |
| 59 | `steps_json` | 1 | TodoWriteTool.java:192 | 5 | 0 | 0 | 前端未用 |
| 60 | `faq_answers` | 3 | knowledge/FaqSnippet.java:172 | 4 | 0 | 2 |  |
| 61 | `faq_similar_questions` | 1 | knowledge/FaqSnippet.java:68 | 4 | 0 | 1 |  |
| 62 | `file_type` | 1 | knowledge/GetDocumentInfoTool.java:274 | 4 | 0 | 9 |  |
| 63 | `is_faq` | 2 | knowledge/GetDocumentInfoTool.java:279 | 4 | 0 | 5 |  |
| 64 | `total_docs` | 1 | knowledge/GetDocumentInfoTool.java:296 | 4 | 0 | 1 |  |
| 65 | `faq_similar_questions_omitted` | 1 | knowledge/FaqSnippet.java:70 | 3 | 0 | 0 | 前端未用 |
| 66 | `file_name` | 1 | knowledge/GetDocumentInfoTool.java:273 | 3 | 0 | 4 |  |
| 67 | `file_size` | 1 | knowledge/GetDocumentInfoTool.java:275 | 3 | 0 | 2 |  |
| 68 | `next_cursor` | 1 | McpCatalog.java:295 | 3 | 0 | 2 |  |
| 69 | `parse_status` | 1 | knowledge/GetDocumentInfoTool.java:276 | 3 | 0 | 1 |  |
| 70 | `affected_pages` | 2 | wiki/WikiDeletePageTool.java:131 | 2 | 0 | 3 |  |
| 71 | `replacement_count` | 1 | wiki/WikiReplaceTextTool.java:146 | 2 | 0 | 0 | 前端未用 |
| 72 | `single_chunk` | 1 | knowledge/ListKnowledgeChunksTool.java:306 | 2 | 0 | 1 |  |
| 73 | `updated_count` | 2 | wiki/WikiDeletePageTool.java:130 | 2 | 0 | 2 |  |
| 74 | `faq_standard_question` | 1 | knowledge/KnowledgeSearchOutputFormatter.java:267 | 1 | 0 | 3 |  |
| 75 | `old_slug` | 1 | wiki/WikiRenamePageTool.java:163 | 1 | 0 | 3 |  |
| 76 | `requested_limit` | 1 | knowledge/ListKnowledgeChunksTool.java:174 | 1 | 0 | 0 | 前端未用 |
| 77 | `requested_offset` | 1 | knowledge/ListKnowledgeChunksTool.java:173 | 1 | 0 | 0 | 前端未用 |
| 78 | `skill_name` | 1 | SkillReadFileTool.java:136 | 1 | 5 | 4 |  |
| 79 | `suggested_offset` | 1 | knowledge/ListKnowledgeChunksTool.java:175 | 1 | 0 | 0 | 前端未用 |
| 80 | `all_failed` | 1 | web/WebFetchTool.java:469 | 0 | 0 | 1 | 零实录 |
| 81 | `content_items` | 1 | McpToolWrapper.java:308 | 0 | 0 | 0 | 零实录 前端未用 |
| 82 | `content_length` | 1 | web/WebFetchTool.java:357 | 0 | 0 | 4 | 零实录 |
| 83 | `end_line` | 1 | SkillReadFileTool.java:129 | 0 | 0 | 0 | 零实录 前端未用 |
| 84 | `error_code` | 2 | web/WebFetchTool.java:399 | 0 | 0 | 2 | 零实录 |
| 85 | `error_message` | 2 | web/WebFetchTool.java:400 | 0 | 0 | 3 | 零实录 |
| 86 | `evidence_type` | 2 | web/WebSearchTool.java:245 | 0 | 0 | 0 | 零实录 前端未用 |
| 87 | `failed_count` | 1 | web/WebFetchTool.java:467 | 0 | 0 | 1 | 零实录 |
| 88 | `file_path` | 1 | SkillReadFileTool.java:130 | 0 | 0 | 3 | 零实录 |
| 89 | `full_output_path` | 2 | web/WebSearchTool.java:425 | 0 | 0 | 1 | 零实录 |
| 90 | `function_name` | 1 | McpDiscoverTool.java:150 | 0 | 0 | 0 | 零实录 前端未用 |
| 91 | `next_offset` | 2 | SkillReadFileTool.java:141 | 0 | 0 | 1 | 零实录 |
| 92 | `page_content` | 1 | web/WebSearchTool.java:423 | 0 | 0 | 1 | 零实录 |
| 93 | `page_error` | 3 | web/WebSearchTool.java:408 | 0 | 0 | 1 | 零实录 |
| 94 | `page_next_offset` | 1 | web/WebSearchTool.java:426 | 0 | 0 | 0 | 零实录 前端未用 |
| 95 | `page_status` | 3 | web/WebSearchTool.java:407 | 0 | 0 | 1 | 零实录 |
| 96 | `page_truncated` | 1 | web/WebSearchTool.java:424 | 0 | 0 | 1 | 零实录 |
| 97 | `page_verified` | 2 | web/WebSearchTool.java:246 | 0 | 0 | 1 | 零实录 |
| 98 | `published_at` | 1 | web/WebSearchTool.java:254 | 0 | 0 | 2 | 零实录 |
| 99 | `raw_content` | 1 | web/WebFetchTool.java:356 | 0 | 0 | 3 | 零实录 |
| 100 | `returned_bytes` | 1 | SkillReadFileTool.java:132 | 0 | 0 | 0 | 零实录 前端未用 |
| 101 | `returned_chars` | 1 | web/WebFetchTool.java:358 | 0 | 0 | 2 | 零实录 |
| 102 | `server_instructions` | 1 | McpDiscoverTool.java:140 | 0 | 0 | 0 | 零实录 前端未用 |
| 103 | `skipped_count` | 1 | web/WebFetchTool.java:468 | 0 | 0 | 4 | 零实录 |
| 104 | `start_line` | 1 | SkillReadFileTool.java:137 | 0 | 0 | 0 | 零实录 前端未用 |
| 105 | `storage_error` | 2 | web/WebSearchTool.java:429 | 0 | 0 | 1 | 零实录 |
| 106 | `successful_count` | 1 | web/WebFetchTool.java:466 | 0 | 0 | 1 | 零实录 |
| 107 | `total_lines` | 1 | SkillReadFileTool.java:138 | 0 | 0 | 0 | 零实录 前端未用 |

## C. 工具名（值，非 JSON 键）

| # | 工具名 | 实录 args | 实录 out | 前端文件 |
|---|---|---|---|---|
| 1 | `call_mcp_tool` | 0 | 1 | 5 |
| 2 | `data_analysis` | 0 | 17 | 3 |
| 3 | `data_schema` | 0 | 1 | 3 |
| 4 | `database_query` | 0 | 13 | 5 |
| 5 | `discover_mcp_tools` | 0 | 7 | 5 |
| 6 | `edit_sandbox_file` | 0 | 1 | 0 |
| 7 | `edit_skill_file` | 0 | 1 | 0 |
| 8 | `execute_skill_script` | 0 | 1 | 0 |
| 9 | `get_document_info` | 0 | 1 | 5 |
| 10 | `grep_chunks` | 0 | 5 | 5 |
| 11 | `knowledge_search` | 0 | 21 | 12 |
| 12 | `list_knowledge_chunks` | 0 | 10 | 7 |
| 13 | `list_sandbox_files` | 0 | 0 | 0 |
| 14 | `query_knowledge_graph` | 0 | 1 | 4 |
| 15 | `read_file` | 2 | 1 | 1 |
| 16 | `read_sandbox_file` | 0 | 0 | 0 |
| 17 | `read_skill` | 0 | 0 | 0 |
| 18 | `search_conversations` | 0 | 0 | 0 |
| 19 | `search_memory` | 0 | 0 | 0 |
| 20 | `shell_exec` | 0 | 2 | 4 |
| 21 | `thinking` | 0 | 17 | 45 |
| 22 | `todo_write` | 0 | 2 | 5 |
| 23 | `web_fetch` | 0 | 1 | 4 |
| 24 | `web_search` | 0 | 10 | 23 |
| 25 | `wiki_delete_page` | 0 | 1 | 6 |
| 26 | `wiki_flag_issue` | 0 | 0 | 3 |
| 27 | `wiki_read_issue` | 0 | 0 | 3 |
| 28 | `wiki_read_page` | 0 | 4 | 9 |
| 29 | `wiki_read_source_doc` | 0 | 1 | 6 |
| 30 | `wiki_rename_page` | 0 | 1 | 6 |
| 31 | `wiki_replace_text` | 0 | 2 | 6 |
| 32 | `wiki_search` | 0 | 4 | 9 |
| 33 | `wiki_update_issue` | 0 | 0 | 3 |
| 34 | `wiki_write_page` | 0 | 3 | 6 |
| 35 | `write_sandbox_file` | 0 | 8 | 0 |
| 36 | `write_skill_file` | 0 | 0 | 0 |

## D. 不在本面

**外部决定（勿动，§14.6/§15.3 已登记）**
- `image_info`：docreader 外部载荷键（`original_url`/`ocr_text`/`start_pos`…）。
- rerank `RankResult`：`index`/`document`/`relevance_score`（Jina/Aliyun/Lkeap 第三方响应）。
- connector 第三方线格式（`datasource/connector/**`，字段名由对方 API 决定）。

**误判剔除**
- `order_id`：仅出现在 MCP 工具描述的 `examples` 文本里（示例值），不是 schema 键。
- SQL 表名与函数名（`SqlGuard`/`SqlInjectionAnalyzer` 的 `knowledge_bases`/`array_agg` 等）——非 JSON 键。
- 内部转发键（`error_message`/`raw_content`/`full_output_path`/`next_offset`/`storage_error`）：WebFetch→WebSearch 之间的中间层，出口键为 `page_*`；不含 MCP 服务器名（外部值）。
