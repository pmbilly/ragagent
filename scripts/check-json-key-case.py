#!/usr/bin/env python3
"""换锚棘轮：非冻结面不许出现新的 snake JSON 键。

判据（§2）：**我们自己的** JSON 面（HTTP 请求/响应、自有 jsonb）键名＝Java 字段名（camel）。
第三方线格式（datasource connector、event/llm 载荷、langfuse、租户配置 jsonb（余段）、Doris 客户端、
图片信息、SearchParams 等）与**模型输出契约**、**既有内部 jsonb 状态**属冻结面，保留 snake。

本脚本扫描 main 代码里的三类键写入点，与基线（本文件内联）比对：
  ① `@JsonProperty("snake")` / `@JsonPropertyOrder({... "snake" ...})`
  ② `ObjectNode` 的 `put("snake"` / `set("snake"`
  ③ 帮手式写入：`putNonEmpty|putTrue|putAlways|putOmitEmpty(<任意>, "snake"`
基线只记录**已逐条复核**的例外；出现基线之外的新命中即失败（棘轮：只许减不许增）。

用法：python3 scripts/check-json-key-case.py [--list|--strict|--write]
  默认＝报告（列出基线外命中但**退出 0**）；--strict＝闸门（有基线外命中即退出 1，
  供将来判定完成后接 CI 用——当前基线只含 3 组已逐条复核的例外，其余待判项
  （SQL 参数键假阳性、诊断载荷等）不能当作已复核例外入基线）。
  --write＝重新登记**冻结覆盖快照**（见下条，登记即声明责任）。

**冻结覆盖快照（B169 / scripts/json-key-freeze.baseline.json）**：把「当前被 FROZEN_PREFIXES
覆盖**且含 snake 键**的文件 → 命中前缀」存成快照，**离开**或**新进入**冻结集合都会使 --strict 失败。
起因（B168 实测 ✗）：B162 把 `llm/TokenUsage` 搬进 `common/llm`、B163 把 6 个 Langfuse 装饰器从
`tracing/langfuse` 换包到 `tracing/decorators` ⇒ **前缀不再命中、冻结静默失效** ✗，直到 CI 才报出；
更糟的是当时 CI 的提示语是「确属第三方/模型契约则加入 BASELINE」——**若照做就会把静默失保合法化** ✗。
有了快照，同一提交内就报「离开冻结面」✓。前缀冻结的固有脆弱点：**换包 = 失效**，故换包后要回核名单。

**已知边界（诚实声明）**：本扫描器按文本模式匹配 `put/set("snake"` 等，**会命中 SQL 参数
Map / MyBatis 列名等非 JSON 键**（如 `*Repository` 的 `deleted_at`），故 `--list` 是**待判
清单**而非违规清单；棘轮模式（默认）只有在基线外**新增**命中时才失败，不会因为既有噪音而红。
判定某键是真债还是冻结/数据值时，**必须看消费者**（FE 读？夹具断言？第三方 API？）。

**2026-10-08（B88）**：工具面（`agent/tools/**` 及 agent 管线消费侧）已全量 camel 化，
**已知盲区**：自定义 key-value helper（如 `KnowledgeTagService.details(Object... keyValues)`）里的键名**七种形态都扫不到** ⇒ B147b 用一次性 grep 复核过 `grep -rnE "details\\(\" server/src/main`；新增此类 helper 时请照做。

原先整目录的 `agent/tools/` 冻结豁免已摘除；2026-10-09 B141 又摘除 `datasource/connector` 的整目录豁免（改为文件级，见 FROZEN_PREFIXES 内注释——该豁免曾让 134 个键静默逃逸）；该目录残留的 snake 仅限三类并逐条登记在本脚本 BASELINE：
① 外部载荷读侧（docreader image_info 的 `original_url`/`ocr_text` 等，键名由对方服务决定）、
② MyBatis/JDBC 列名与 SQL 参数、③ 第三方面（websearch metadata `published_at` 等）。
工具**名**（`wiki_write_page` 等 36 个）与工具 schema 的 **enum 值**（`list_servers`/`list_tools`…）按 §2.4「字段名 camel、值按各自语义」保留 snake。

**BASELINE 条目按“文件 + 具体键”登记**（键级 ✓）：只有列出的键被放行，将来在其中**新增**的 snake 键仍会被点名——
在已登记文件里加新键时，请先看该文件是否在基线（或按族核查）。
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
# 多模块（B116）：遍历所有模块的 com/ragagent 包根；rel 仍是相对包根的路径
# （与冻结基线里的键形状一致）。
import _source_roots as _sr

PKG_ROOTS = _sr.backend_pkg_roots()

# 冻结面（第三方线格式/租户配置/模型载荷等）——整目录豁免，理由见 HANDOFF §15.3。
FROZEN_PREFIXES = (
    # 2026-10-08 B93b：`event/` 与 `stream/` 已 camel 化（事件面），从冻结名单摘除；
    # 事件面口径另由 scripts/check-event-face-case.py 守（载荷注解/事件名/响应类型值）。
    #
    # 2026-10-09 B141：`datasource/connector` 整目录豁免 → **文件级**（原豁免是"可换锚面 0"
    # 这一误判的根因：一摘掉就露出 26 文件/134 键，见 §15.1.1 B133/B139/B140/B141）。
    # 下列文件是**对方 API 的载荷类型与客户端**——键名由外部服务定义，改名会导致请求/解析失败：
    #   · 飞书：docx block 结构 / 各 API 响应类型 / 客户端 / 错误码
    #   · Notion：block·page·parent·rich-text·paginated 等响应结构（NotionConnector/NotionCursor/
    #     NotionFetchOps **不在**名单里——它们的键是我们自己的，B140/B141 已换锚）
    #   · GitLab：响应字段（ref/per_page/page 等查询参数与响应结构）
    #   · Ima：响应类型 + 请求体字段（req.put("knowledge_base_id"…) 等，见 ImaClient）
    #   · 语雀：响应类型
    'datasource/connector/feishu/core/DocxBlocks',
    'datasource/connector/feishu/core/FeishuApiTypes',
    'datasource/connector/feishu/core/FeishuClient',
    'datasource/connector/feishu/core/FeishuErrors',
    'datasource/connector/gitlab/GitLabClient',
    'datasource/connector/ima/ImaApiTypes',
    'datasource/connector/ima/ImaClient',
    'datasource/connector/notion/NotionBlock',
    'datasource/connector/notion/NotionClient',
    'datasource/connector/notion/NotionFile',
    'datasource/connector/notion/NotionMention',
    'datasource/connector/notion/NotionPage',
    'datasource/connector/notion/NotionPaginatedResponse',
    'datasource/connector/notion/NotionParent',
    'datasource/connector/notion/NotionRichText',
    # B143 补：Notion 载荷的文本/markdown 侧读取器（键名同属 Notion 响应结构）
    'datasource/connector/notion/NotionMarkdown',
    'datasource/connector/notion/NotionProperties',
    'datasource/connector/yuque/YuqueApiTypes',
    'llm/', 'common/llm', 'common/tenant',
    'common/pipeline/SearchParams', 'mcp/oauth', 'memory/service/MemoryExtractionLlm',
    'memory/service/MemoryExtractPayload', 'docreader', 'rerank/RankResult',
    'tracing/langfuse', 'tracing/decorators', 'retrieval/engine/doris', 'retrieval/domain/ImageInfo',
    # ⚠️ 前缀冻结会在**换包**时静默失效（B168 实测 ✗）：B162 把 llm/TokenUsage 搬进 common/llm、
    # B163 把 6 个 Langfuse 装饰器从 tracing/langfuse 换包到 tracing/decorators ⇒ 旧前缀不再命中，
    # 冻结悄悄失效、CI 才报"非冻结面出现新的 snake 键"。**换包时务必回来核这里** ✓。
    'common/wiki/ExtractedItem', 'chatpipeline/plugin', 'agent/AgentEngine',
    'agent/ReActIteration', 'retrieval/HybridSearchService', 'retrieval/vlm/VlmClient',
    'knowledge/service/ChunkExtractService', 'knowledge/task/KnowledgeProcessWorker',
    'memory/mapper', 'knowledge/domain/KnowledgeBase', 'mcp/domain/McpService',
    # 上游 API 载荷的适配器族（embedding/im/websearch/rerank/asr/vlm 的 provider 与客户端：
    # 键名由对方 API 定，冻结）
    'embedding/provider', 'im/', 'websearch/provider', 'rerank/', 'asr/', 'vlm/',
    'retrieval/vlm', 'storage/provider',
    # 检索引擎适配器族（ES/OpenSearch/Milvus/Qdrant 的 DSL 字段）与检索观测面
    'retrieval/engine/', 'retrieval/obs/',
    # 2026-10-09 B134：`ReferencesSupport`（0 蛇键）与 `PipelineProgress`（5 键已换锚）摘除。
    # 原先的冻结理由＝「存量回放面」（B25 判定：引用随 messages.knowledge_references 落库、
    # 前端 rag-pipeline-history 以同形键重建）⇒ 该理由属 B92 已整体作废的「兼容历史数据」类；
    # 两者改完后随 §15.3「已解除」表一起出册，本棘轮自此覆盖这两个文件。
)

# 基线：已逐条复核的例外（文件相对路径 → 允许的键集合）。新增即失败。
BASELINE: dict[str, set[str]] = {
    # Langfuse span/trace 观测面（B135 复核改标：原写「agent_steps 落库列 + 历史回放」，实测站点是 toolSpanInput/toolSpanMeta 与 traceArgumentValue 构造 ⇒ 观测面，非 JSON 契约）
    'agent/ActPhase.java': {'args_redacted', 'argument_resolution', 'data_keys', 'image_count', 'mcp_service', 'mcp_tool', 'model_arg_keys', 'model_arguments', 'output_len', 'resolved_arg_keys', 'resolved_arguments', 'tool_index', 'unresolved_handle_count', 'unresolved_handles'},
    # 模板令牌（数据值，非 JSON 键）
    'agent/AgentPrompts.java': {'current_time', 'web_search_status'},
    # 工具名（模型可见的函数名；§2.4 只管字段名，B88 决策：工具名与 enum 值保留 snake）
    'agent/tools/ToolCapabilities.java': {'data_analysis', 'data_schema', 'database_query', 'get_document_info', 'grep_chunks', 'knowledge_search', 'list_knowledge_chunks', 'query_knowledge_graph', 'todo_write', 'wiki_delete_page', 'wiki_flag_issue', 'wiki_read_issue', 'wiki_read_page', 'wiki_read_source_doc', 'wiki_rename_page', 'wiki_replace_text', 'wiki_search', 'wiki_update_issue', 'wiki_write_page'},
    # MyBatis 列名/参数（非 JSON 键）
    'auth/service/TenantInvitationService.java': {'invitation_id'},
    # MyBatis 列名/参数（非 JSON 键）
    'auth/service/TenantMemberService.java': {'new_role', 'old_role'},
    # 外部耦合（B133 逐键复核）：docreader config_overrides 的 map 键名由对方服务定义
    # （docreader/docreader.proto 里 config_overrides 是泛型 map，服务本体不在本仓）；
    # 租户 KV 复用同一词汇，改名须先加边界翻译层——属独立决策，故登记冻结。
    # chat_parser_engine_rules 则是 agent 侧规则的透传保留（§14.9 表②）。
    'tenant/ParserEngineConfig.java': {'chat_parser_engine_rules', 'mineru_api_key', 'mineru_cloud_enable_formula', 'mineru_cloud_enable_ocr', 'mineru_cloud_enable_table', 'mineru_cloud_language', 'mineru_cloud_model', 'mineru_enable_formula', 'mineru_enable_ocr', 'mineru_enable_table', 'mineru_endpoint', 'mineru_language', 'mineru_model', 'mineru_parse_method', 'mineru_vlm_server_url', 'odl_hybrid', 'odl_hybrid_fallback', 'odl_hybrid_mode', 'odl_hybrid_url', 'odl_markdown_with_html', 'paddleocr_vl_cloud_model', 'paddleocr_vl_cloud_token', 'paddleocr_vl_cloud_use_chart_recognition', 'paddleocr_vl_cloud_use_seal_recognition', 'paddleocr_vl_endpoint', 'paddleocr_vl_use_chart_recognition', 'paddleocr_vl_use_seal_recognition'},
    # 内部预设名（presets() 仅内部查表）
    'chatpipeline/PipelineBuilder.java': {'chat_history_stream', 'chat_stream', 'rag_stream'},
    # PipelineLog 观测面（日志字段，非契约）
    'chatpipeline/support/SearchSupport.java': {'chunk_id', 'dropped_id', 'kept_id', 'match_type'},
    # langfuse 线上字段（第三方契约）
    'common/context/TracingContext.java': {'lf_parent_obs_id', 'lf_session_id', 'lf_trace_id', 'lf_traceparent', 'lf_user_id'},
    # MDC 日志键（非 JSON）
    'common/filter/RequestIdFilter.java': {'request_id'},
    # OpenSearch 字段
    'config/OpenSearchAuditSinkAdapter.java': {'dst_alias', 'src_alias'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名写入点（非 JSON 键）
    'knowledge/service/KnowledgeFileService.java': {'updated_at'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    'knowledge/service/KnowledgeService.java': {'updated_at'},
    # MyBatis 列名/参数（非 JSON 键）
    # langfuse 面
    'knowledge/service/SpanTracker.java': {'langfuse_trace_id'},
    # 提示词模板变量（数据值，模板里是 {{server_name}} 等）
    'mcp/controller/McpUsageInstructionsOps.java': {'omitted_tools', 'server_description', 'server_instructions', 'server_name'},
    # 8 MiB 门禁的体积度量形态（B135 复核改标：镜像 metadata 表列，属 §15.3 ③ DDL 面）
    'mcp/service/McpMetadataService.java': {'server_description', 'server_name', 'server_version', 'service_id', 'synced_at'},
    # memory 观测/追踪载荷（非契约）
    'memory/service/MemoryRecallSelector.java': {'outside_pool', 'skip_reason'},
    # memory 观测/追踪载荷（非契约）
    'memory/service/MemoryTrace.java': {'conditioned_items', 'document_count', 'interest_count', 'recalled_items', 'recalled_items_truncated'},
    # WeKnora Cloud 第三方 API
    # Cypher 字段
    'retrieval/graph/Neo4jGraphRepository.java': {'knowledge_id', 'source_labels', 'target_labels'},
    # ImageInfo 面（§15.3 冻结族）
    'retrieval/support/ImageInfoMatchUtil.java': {'end_pos', 'start_pos'},
    # websearch metadata 的 published_at（§15.3 ① 外部决定：键名由搜索服务给，非我们的 JSON 面）。
    # B147a 复核改标：原写「存量面」是错的——与 ReferencesSupport 同族，属外部词汇而非历史数据。
    'retrieval/support/WebResultConverter.java': {'published_at'},
    # SSE complete 事件的 data 键（B136 复核：与上条同属**冻结的线协议** §14.9l 前提判定 2；
    # 同上（SSE/消息载荷）
    'session/controller/SessionController.java': {'message_id', 'session_id'},
    # MyBatis 列名写入点（非 JSON 键）
    'session/mapper/MessageRepository.java': {'agent_duration_ms', 'agent_id', 'agent_tenant_id', 'is_completed', 'is_fallback', 'knowledge_id', 'model_id', 'rendered_content', 'request_id', 'updated_at'},
    # 文件服务面（B136 复核）：HTTP query 参数名（`request.getParameter`，非 Spring 注解
    # ⇒ `check-event-face-case.py` 扫不到）+ **代理响应体键**（FileProxyService:281 是真 JSON 键）
    # ⇒ 按 §14.9「自有查询参数名统一 camel」该改，登记为候选（见 HANDOFF）
    'storage/fileserve/FileProxyService.java': {'file_path'},
    # 存储引擎配置面 + 云厂商凭据字段（snake，同上；B143 起含读侧形态 access_key/secret_id/secret_key 与 legacy mineru_enable_ocr）
    'system/controller/SystemController.java': {'access_key', 'access_key_id', 'bucket_name', 'mineru_enable_ocr', 'mineru_parse_method', 'secret_access_key', 'secret_id', 'secret_key', 'use_ssl'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名写入点（非 JSON 键）
    # 模型输出契约（§15.3 ② 拍板项）：提示词里就是 new_slugs，只解析入站；
    # B136 复核确认仍在用（WikiChunkCitationPrompt 的输出项）
    'wiki/service/ingest/WikiIngestCitePipeline.java': {'new_slugs'},
    # span 观测面（B136 复核改标：原写「wiki 摄取内部 jsonb 状态（存量）」是错的——
    # 站点全是 extractInput/extractOut/summaryInput/summaryOut/classifyOut 的 span 进出，
    # 去向 handler.spans.endSpan(...)；FE 零命中 ⇒ 同 ActPhase 面，非 JSON 契约）
    'wiki/service/ingest/WikiIngestMapPhase.java': {'body_preview', 'candidate_slugs', 'cited_chunks', 'cited_slugs', 'classify_batches', 'concepts_preview', 'content_chars', 'doc_title', 'entities_preview', 'extracted_pages', 'extracted_slugs', 'new_slugs', 'new_slugs_sample', 'old_pages', 'pass0_fallback', 'reparse_slugs', 'stale_slugs', 'summary_chars', 'summary_line', 'summary_preview', 'top_cited', 'uncited_slugs'},
    # span 观测面（B136 复核改标：原写「wiki 摄取内部 jsonb 状态（存量）」是错的——
    # 站点全是 extractInput/extractOut/summaryInput/summaryOut/classifyOut 的 span 进出，
    # 去向 handler.spans.endSpan(...)；FE 零命中 ⇒ 同 ActPhase 面，非 JSON 契约）
    'wiki/service/ingest/WikiIngestReducePhase.java': {'addition_failed', 'affected_type', 'chunk_refs', 'content_preview', 'page_summary', 'page_title', 'page_type', 'source_refs'},
    # span 观测面（B136 复核改标：同上——mapsStats→endSpan 的收尾输出，FE 零命中）
    'wiki/service/ingest/WikiIngestRunSupport.java': {'failed_slug_writes', 'pages_dropped', 'pages_dropped_preview', 'pages_total', 'pages_written', 'pages_written_preview'},
    # 模型输出契约（LLM 载荷）
    # ── 读侧形态族（B143 落第 5 种形态时逐条复核；全是外部契约/数据值，非我们的 JSON 面）──
    # OIDC/JWT claim 名（IdP 定义，非我们的 JSON 面）
    'channels/api/filter/APIKeyAuthChannel.java': {'tenant_id'},
    # OAuth 响应字段（IdP 契约）
    'auth/controller/AuthSessionOps.java': {'refresh_token'},
    # JWT claim 名（IdP 契约）
    'auth/service/JwtService.java': {'tenant_id'},
    # OIDC 标准参数名（协议契约）
    'auth/service/OidcStateCodec.java': {'redirect_uri'},
    # JWT claim 名（IdP 契约）
    'auth/service/UserService.java': {'user_id'},
    # JWT claim 名（IdP 契约）
    'auth/service/UserSessionOps.java': {'user_id'},
    # websearch metadata（§15.3 ① 外部决定）
    'chatpipeline/support/ReferencesSupport.java': {'published_at'},
    # 提示词模板变量（数据值，非 JSON 键）
    'common/prompt/PromptTemplateCatalog.java': {'has_knowledge_base', 'has_web_search'},
    # provider 配置字段（对方词汇）
    'embedding/EmbedderFactory.java': {'api_version'},
    # agent 侧解析规则族（同 chat_parser_engine_rules，§14.9 ②）
    'knowledge/support/ParserEngineRules.java': {'file_types'},
    # memory 抽取 payload 键（模型输出契约族）
    'memory/service/MemoryTopicResolver.java': {'same_as'},
    # ── 第 6 种形态族（B144 加 SQL JSON 路径扫描时逐条复核；全是列名/表名/外部部署配置）──
    # 数据库表名（SqlGuard 的 {"knowledge_bases","knowledges","chunks"} 表名单，SQL 注入分析的词表）
    'agent/tools/sql/SqlInjectionAnalyzer.java': {'knowledge_bases'},
    # 冻结写侧的透传（写侧在 FROZEN_PREFIXES 里的 FeishuErrors；读侧必须与冻结面一致 ⇒ 保留 snake）
    'datasource/service/DataSourceSyncResultOps.java': {'error_reason', 'error_reason_code', 'error_reason_code_value'},
    # 注释里的历史说明（描述该面 SQL 的口径；正文 SQL 已随 B144 改 camel）
    'datasource/service/KnowledgeBridge.java': {'datasource_id'},
    # 外部部署配置（./config/builtin_models.yaml，env BUILTIN_MODELS_CONFIG；文档 §4.4 记为可选、默认 no-op ⇒ 键名是对外契约，同 docreader 的 config_overrides）
    'model/service/BuiltinModelsReconciler.java': {'builtin_models', 'is_default', 'tenant_id'},
    # MyBatis 列名（ModelUsageMapper.listKnowledgeBaseRows 的 @Select 直接选这些列：knowledge_bases 表的 embedding_model_id/summary_model_id/image_processing_config/vlm_config/asr_config/wiki_config ⇒ 非我们的 JSON 面）
    'model/service/ModelService.java': {'asr_config', 'embedding_model_id', 'image_processing_config', 'knowledge_bases', 'summary_model_id', 'vlm_config', 'wiki_config'},
    # ── 集合字面量形态族（B145 加该形态扫描时逐条复核）──
    # Langfuse 任务 span 载荷（LangfuseTaskScope.start 的 metadata，观测面）
    'datasource/InProcessDataSourceSyncTaskQueue.java': {'data_source_id'},
    # Langfuse 任务 span 载荷（同上，观测面）
    'datasource/RedisDataSourceSyncTaskQueue.java': {'data_source_id'},
    # 飞书 OAuth 请求体字段名（对方契约；值与 typed 字段解耦，见 §15.1.1 B137/B139）
    'datasource/connector/feishu/core/FeishuTransport.java': {'app_id'},
    # Langfuse 任务 span 载荷（LangfuseTaskScope.start 的 metadata，观测面）
    'knowledge/service/QuestionGenerationService.java': {'knowledge_id'},
    # Langfuse 任务 span 载荷（同上，观测面）
    'memory/service/InProcessMemoryExtractTaskQueue.java': {'subject_id'},
    # span 观测面（同本文件既有条目）
    'memory/service/MemoryInsightOps.java': {'candidate_count', 'lexical_hits', 'matched_count', 'ranking_mode', 'subject_id', 'tenant_id', 'vector_hits', 'vector_outside', 'vector_skip'},
    # span 观测面（同本文件既有条目）
    'memory/service/MemoryRecallOps.java': {'block_runes', 'candidate_count', 'fused_candidates', 'interest_injected', 'interest_relevant', 'interest_total', 'lexical_hits', 'matched_count', 'prompt_runes', 'ranking_mode', 'resident_count', 'subject_id', 'tenant_id', 'used_count', 'vector_hits', 'vector_outside', 'vector_skip'},
    # Langfuse 任务 span 载荷（同上，观测面）
    'memory/service/RedisMemoryExtractTaskQueue.java': {'subject_id'},
    # 工具名（模型可见的函数名；§2.4 只管字段名，B88 决策：工具名与 enum 值保留 snake。同族见 agent/tools/ToolCapabilities.java 的登记）
    'modelcontext/ToolPolicy.java': {'call_mcp_tool', 'data_schema', 'discover_mcp_tools', 'edit_sandbox_file', 'edit_skill_file', 'execute_skill_script', 'get_document_info', 'grep_chunks', 'knowledge_search', 'list_knowledge_chunks', 'list_sandbox_files', 'query_knowledge_graph', 'read_file', 'read_sandbox_file', 'read_skill', 'search_conversations', 'search_memory', 'shell_exec', 'todo_write', 'web_fetch', 'web_search', 'wiki_delete_page', 'wiki_flag_issue', 'wiki_read_page', 'wiki_read_source_doc', 'wiki_rename_page', 'wiki_replace_text', 'wiki_search', 'wiki_write_page', 'write_sandbox_file', 'write_skill_file'},
    # span 观测面（followUpSpan.finish 的 metadata）
    'session/service/MessageSuggestionService.java': {'question_count'},
    # span 观测面（同本文件既有条目）
    'session/service/SessionKnowledgeQaService.java': {'error_type', 'event_type', 'knowledge_base_ids', 'search_targets', 'total_stages'},
    # 队列任务 span 载荷（观测面；同名键在别处是 DB 列名，此处不是）
    'wiki/service/ingest/WikiIngestTaskRunner.java': {'knowledge_base_id'},
    'wiki/service/page/NewSlugFromCitation.java': {'source_chunks'},
}

PATTERNS = (
    re.compile(r'@JsonProperty\("([a-z0-9]+(?:_[a-z0-9]+)+)"\)'),
    re.compile(r'@JsonPropertyOrder\(\{([^}]*)\}\)'),
    re.compile(r'\.(?:put|set)\("([a-z0-9]+(?:_[a-z0-9]+)+)"\s*,'),
    re.compile(r'\b(?:putNonEmpty|putTrue|putAlways|putOmitEmpty)\([^,"]*,\s*'
               r'"([a-z0-9]+(?:_[a-z0-9]+)+)"'),
    # 读侧形态（2026-10-09 B143）：写侧改成 camel 而**读侧仍 snake** ⇒ 静默失效。
    # B132（HybridSearchService 的 rrf_* / MessageService）/ B138（MapperKnowledgeBridge）/
    # B140（NotionConnector 的 object_type）三次真实事故全是这一形态，此前扫不到。
    re.compile(r'\.(?:get|path|getOrDefault|containsKey|remove)\("([a-z0-9]+(?:_[a-z0-9]+)+)"\)'),
    # SQL 里的 JSON 路径（2026-10-09 B144）：metadata->>'snake' / ->'snake' / ->'$.snake'。
    # 起因：B138 把写侧改成 camel，却漏了 MapperKnowledgeBridge 的两条 metadata->>'datasource_id'
    # /->>'external_id' SQL —— 当时扫描用的是双引号字面量，SQL 里是单引号，整类漏掉；
    # 同批还查出 ChunkRepository/ChunkMapper 的 metadata->>'standard_question' 也是死的（写侧 camel）。
    re.compile(r"->>?'\$?\.?([a-z0-9]+(?:_[a-z0-9]+)+)'"),
    # SQL 里的 JSON 路径（2026-10-09 B144）：metadata->>'snake' / ->'snake' / ->'$.snake'。
    # 起因：B138 把写侧改成 camel，却漏了 MapperKnowledgeBridge 的两条 metadata->>'datasource_id'
    # /->>'external_id' SQL —— 当时扫描用的是双引号字面量，SQL 里是单引号，整类漏掉；
    # 同批还查出 ChunkRepository/ChunkMapper 的 metadata->>'standard_question' 也是死的（写侧 camel）。
    re.compile(r"->>?'\$?\.?([a-z0-9]+(?:_[a-z0-9]+)+)'"),
    # 集合字面量里键在前（B145）：Map.of("snake", …) / Map.entry("snake", …)。
    # 第 3 种形态要求 .put|.set＋逗号，这两种字面量此前完全在扫描面之外
    # （预演：全仓 43 键，其中 31 个是 B88 已拍板的工具名）。
    re.compile(r'(?:Map\.of|Map\.entry|ImmutableMap\.of|ofEntries)\("([a-z0-9]+(?:_[a-z0-9]+)+)"\s*,'),
)
SNAKE_IN_LIST = re.compile(r'"([a-z0-9]+(?:_[a-z0-9]+)+)"')


def keys_of(path: pathlib.Path) -> set[str]:
    """扫一个 Java 文件里的 snake 键写入点（三类形态；供 hits 与冻结覆盖快照共用）。"""
    text = path.read_text(encoding='utf-8', errors='ignore')
    keys: set[str] = set()
    for m in PATTERNS[0].finditer(text):
        keys.add(m.group(1))
    for m in PATTERNS[1].finditer(text):              # @JsonPropertyOrder({...})
        keys.update(SNAKE_IN_LIST.findall(m.group(1)))
    for pattern in PATTERNS[2:]:
        for m in pattern.finditer(text):
            keys.add(m.group(1))
    return keys


def all_files():
    """(绝对路径, 相对包根的键路径) —— 两处扫描（违规面 / 冻结面）共用同一枚举口径。"""
    for r in PKG_ROOTS:
        for p in sorted(r.rglob('*.java')):
            yield p, str(p.relative_to(r))


def hits() -> dict[str, set[str]]:
    found: dict[str, set[str]] = {}
    for path, rel in all_files():
        if rel.startswith(FROZEN_PREFIXES):
            continue
        keys = keys_of(path)
        if keys:
            found[rel] = keys
    return found


def freeze_coverage() -> dict[str, str]:
    """冻结覆盖快照口径（B169）：被 FROZEN_PREFIXES 覆盖**且含 snake 键**的文件 → 命中前缀。

    只收含 snake 键的文件：没有键的文件进出冻结区无信息量（避免噪音）；含键的才有被罩着的价值。
    """
    covered: dict[str, str] = {}
    for path, rel in all_files():
        prefix = next((p for p in FROZEN_PREFIXES if rel.startswith(p)), None)
        if prefix is None:
            continue
        if keys_of(path):
            covered[rel] = prefix
    return covered


FREEZE_SNAPSHOT = ROOT / 'scripts' / 'json-key-freeze.baseline.json'


def load_snapshot() -> dict[str, str]:
    """读冻结覆盖快照；缺文件时返回空（首次运行会提示 --write）。"""
    if not FREEZE_SNAPSHOT.exists():
        return {}
    return json.loads(FREEZE_SNAPSHOT.read_text(encoding='utf-8')).get('files', {})


def save_snapshot(covered: dict[str, str]) -> None:
    FREEZE_SNAPSHOT.write_text(
        json.dumps({
            'note': '冻结覆盖快照（B169）：被 FROZEN_PREFIXES 覆盖且含 snake 键的文件 → 命中前缀。'
                    '由 --write 生成；离开或新进入冻结集合都会使 --strict 失败（确认后用 --write 登记）。'
                    '⚠️ 前缀冻结会在「换包」时静默失效 ⇒ 换包后务必回核 FROZEN_PREFIXES 并 --write。',
            'files': dict(sorted(covered.items())),
        }, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main() -> int:
    found = hits()
    covered = freeze_coverage()
    old = load_snapshot()
    left = sorted(set(old) - set(covered))
    entered = sorted(set(covered) - set(old))

    if '--write' in sys.argv:
        save_snapshot(covered)
        print(f'✓ 冻结覆盖快照已写入：{FREEZE_SNAPSHOT.name}（{len(covered)} 个文件）；'
              f'相对旧快照：离开 {len(left)}、进入 {len(entered)}')
        for f in left[:8]:
            print(f'    离开：{f}（原前缀 {old[f]}）')
        for f in entered[:8]:
            print(f'    进入：{f}（现前缀 {covered[f]}）')
        return 0

    violations = {f: sorted(k - BASELINE.get(f, set())) for f, k in found.items()}
    violations = {f: k for f, k in violations.items() if k}
    stale = sorted(f for f in BASELINE if f not in found)

    if '--list' in sys.argv:
        for f, keys in sorted(found.items()):
            mark = '基线' if f in BASELINE else '**新增**'
            print(f'  [{mark}] {f}: {sorted(keys)}')
        print(f'  [冻结覆盖] {len(covered)} 个文件（快照 {len(old)} 个）')
        return 0

    drift = bool(left or entered)
    if (violations or drift) and '--strict' not in sys.argv:
        if violations:
            print(f'ℹ 待判清单：{len(violations)} 个文件有基线外的 snake 键 '
                  f'（未复核为真债，也未被入基线；详见 --list）。默认不判失败。')
        if drift:
            print(f'ℹ 冻结覆盖有变化：离开 {len(left)}、进入 {len(entered)}（详见 --strict）。默认不判失败。')
        return 0
    failed = False
    if violations:
        print('✗ 非冻结面出现新的 snake JSON 键（§2：自己的 JSON 面用 camel）：')
        for f, keys in sorted(violations.items()):
            print(f'    {f}: {keys}')
        print('  → 改 camel（同一提交带上前端与夹具）；确属第三方/模型契约则加入本脚本 BASELINE 并写明理由。')
        failed = True
    if drift:
        print('✗ 冻结覆盖发生变化（B169 快照）——前缀冻结在「换包」时会静默失效：')
        for f in left:
            print(f'    离开：{f}（原前缀 {old[f]}）⇒ 不再被任何前缀罩住，多半是换包 ✗；'
                  f'请回核 FROZEN_PREFIXES（补新位置前缀），确属有意解除再 --write')
        for f in entered:
            print(f'    进入：{f}（现前缀 {covered[f]}）⇒ 确认它确属第三方/模型载荷（我们自己的面应改 camel），'
                  f'确认后 --write 登记')
        failed = True
    if failed:
        return 1
    note = f'（基线 {sum(len(v) for v in BASELINE.values())} 条，均已逐条复核；冻结覆盖 {len(covered)} 个文件）'
    if stale:
        note += f'；注意基线中有 {len(stale)} 个文件已无命中，可清理：{stale[:3]}'
    print(f'✓ 无新增 snake JSON 键 {note}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
