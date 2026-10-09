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

用法：python3 scripts/check-json-key-case.py [--list|--strict]
  默认＝报告（列出基线外命中但**退出 0**）；--strict＝闸门（有基线外命中即退出 1，
  供将来判定完成后接 CI 用——当前基线只含 3 组已逐条复核的例外，其余待判项
  （SQL 参数键假阳性、诊断载荷等）不能当作已复核例外入基线）。

**已知边界（诚实声明）**：本扫描器按文本模式匹配 `put/set("snake"` 等，**会命中 SQL 参数
Map / MyBatis 列名等非 JSON 键**（如 `*Repository` 的 `deleted_at`），故 `--list` 是**待判
清单**而非违规清单；棘轮模式（默认）只有在基线外**新增**命中时才失败，不会因为既有噪音而红。
判定某键是真债还是冻结/数据值时，**必须看消费者**（FE 读？夹具断言？第三方 API？）。

**2026-10-08（B88）**：工具面（`agent/tools/**` 及 agent 管线消费侧）已全量 camel 化，
原先整目录的 `agent/tools/` 冻结豁免已摘除；该目录残留的 snake 仅限三类并逐条登记在本脚本 BASELINE：
① 外部载荷读侧（docreader image_info 的 `original_url`/`ocr_text` 等，键名由对方服务决定）、
② MyBatis/JDBC 列名与 SQL 参数、③ 第三方面（websearch metadata `published_at` 等）。
工具**名**（`wiki_write_page` 等 36 个）与工具 schema 的 **enum 值**（`list_servers`/`list_tools`…）按 §2.4「字段名 camel、值按各自语义」保留 snake。

**BASELINE 条目是文件级**：某文件登记后，将来在其中**新增**的 snake 键不会被点名——
在已登记文件里加新键时，请先看该文件是否在基线（或按族核查）。
"""
from __future__ import annotations

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
    'datasource/connector', 'llm/', 'common/tenant',
    'common/pipeline/SearchParams', 'mcp/oauth', 'memory/service/MemoryExtractionLlm',
    'memory/service/MemoryExtractPayload', 'docreader', 'rerank/RankResult',
    'tracing/langfuse', 'retrieval/engine/doris', 'retrieval/domain/ImageInfo',
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
    'agent/ActPhase.java': {'args_redacted', 'argument_resolution', 'data_keys', 'duration_ms', 'image_count', 'mcp_service', 'mcp_tool', 'model_arg_keys', 'model_arguments', 'output_len', 'resolved_arg_keys', 'resolved_arguments', 'session_id', 'tool_call_id', 'tool_index', 'unresolved_handle_count', 'unresolved_handles'},
    # 模板令牌（数据值，非 JSON 键）
    'agent/AgentPrompts.java': {'current_time', 'web_search_status'},
    # 工具名（模型可见的函数名；§2.4 只管字段名，B88 决策：工具名与 enum 值保留 snake）
    'agent/tools/ToolCapabilities.java': {'data_analysis', 'data_schema', 'database_query', 'get_document_info', 'grep_chunks', 'knowledge_search', 'list_knowledge_chunks', 'query_knowledge_graph', 'todo_write', 'wiki_delete_page', 'wiki_flag_issue', 'wiki_read_issue', 'wiki_read_page', 'wiki_read_source_doc', 'wiki_rename_page', 'wiki_replace_text', 'wiki_search', 'wiki_update_issue', 'wiki_write_page'},
    # MyBatis 列名/参数（非 JSON 键）
    'auth/service/TenantInvitationService.java': {'invitation_id', 'responded_at', 'updated_at'},
    # MyBatis 列名/参数（非 JSON 键）
    'auth/service/TenantMemberService.java': {'deleted_at', 'is_revoked', 'new_role', 'old_role', 'tenant_id', 'updated_at'},
    # 外部耦合（B133 逐键复核）：docreader config_overrides 的 map 键名由对方服务定义
    # （docreader/docreader.proto 里 config_overrides 是泛型 map，服务本体不在本仓）；
    # 租户 KV 复用同一词汇，改名须先加边界翻译层——属独立决策，故登记冻结。
    # chat_parser_engine_rules 则是 agent 侧规则的透传保留（§14.9 表②）。
    'tenant/ParserEngineConfig.java': {'chat_parser_engine_rules', 'mineru_api_key', 'mineru_cloud_enable_formula', 'mineru_cloud_enable_ocr', 'mineru_cloud_enable_table', 'mineru_cloud_language', 'mineru_cloud_model', 'mineru_enable_formula', 'mineru_enable_ocr', 'mineru_enable_table', 'mineru_endpoint', 'mineru_language', 'mineru_model', 'mineru_parse_method', 'mineru_vlm_server_url', 'odl_hybrid', 'odl_hybrid_fallback', 'odl_hybrid_mode', 'odl_hybrid_url', 'odl_markdown_with_html', 'paddleocr_vl_cloud_model', 'paddleocr_vl_cloud_token', 'paddleocr_vl_cloud_use_chart_recognition', 'paddleocr_vl_cloud_use_seal_recognition', 'paddleocr_vl_endpoint', 'paddleocr_vl_use_chart_recognition', 'paddleocr_vl_use_seal_recognition'},
    # 租户配置 jsonb（存量面；B132 起 chat-history / retrieval 两段已换锚，余段待判定）
    # 软删除列（B135d 复核：原条目混了 context_config 的 4 个键，已随本批换锚移出）
    'auth/service/TenantService.java': {'deleted_at'},
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
    # datasource 配置 jsonb（存量面）
    'datasource/dto/DataSourceResponse.java': {'feed_urls'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    'datasource/service/DataSourceItemOps.java': {'datasource_id', 'external_id', 'source_created_at', 'source_resource_id', 'source_updated_at'},
    # MyBatis 列名/参数（非 JSON 键）
    'datasource/service/DataSourceSupport.java': {'processing_status', 'resource_ids', 'task_id'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    'knowledge/service/FaqEntryCommandService.java': {'source_type'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名写入点（非 JSON 键）
    'knowledge/service/KnowledgeFileService.java': {'content_revision', 'embedding_model_id', 'enable_status', 'error_message', 'file_hash', 'file_name', 'file_path', 'file_size', 'file_type', 'parse_status', 'processed_at', 'summary_status', 'updated_at'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/参数（非 JSON 键）
    'knowledge/service/KnowledgeService.java': {'deleted_at', 'updated_at'},
    # MyBatis 列名/参数（非 JSON 键）
    # langfuse 面
    'knowledge/service/SpanTracker.java': {'langfuse_trace_id', 'updated_at'},
    # 提示词模板变量（数据值，模板里是 {{server_name}} 等）
    'mcp/controller/McpUsageInstructionsOps.java': {'omitted_tools', 'server_description', 'server_instructions', 'server_name'},
    # 8 MiB 门禁的体积度量形态（B135 复核改标：镜像 metadata 表列，属 §15.3 ③ DDL 面）
    'mcp/service/McpMetadataService.java': {'server_description', 'server_name', 'server_version', 'service_id', 'synced_at'},
    # memory 观测/追踪载荷（非契约）
    'memory/service/MemoryInsightOps.java': {'candidate_count', 'lexical_hits', 'matched_count', 'ranking_mode', 'subject_id', 'vector_hits', 'vector_outside', 'vector_skip'},
    # memory 观测/追踪载荷（非契约）
    'memory/service/MemoryRecallOps.java': {'block_runes', 'candidate_count', 'fused_candidates', 'interest_injected', 'interest_relevant', 'interest_total', 'lexical_hits', 'matched_count', 'prompt_runes', 'ranking_mode', 'resident_count', 'subject_id', 'used_count', 'vector_hits', 'vector_outside', 'vector_skip'},
    # memory 观测/追踪载荷（非契约）
    'memory/service/MemoryRecallSelector.java': {'outside_pool', 'skip_reason'},
    # memory 观测/追踪载荷（非契约）
    'memory/service/MemoryTrace.java': {'conditioned_items', 'document_count', 'interest_count', 'recalled_items', 'recalled_items_truncated'},
    # model 凭据面（两端自洽，统一另立批）
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名/统计查询（非 JSON 键）
    'model/service/ModelService.java': {'agent_total', 'deleted_at', 'knowledge_base_total'},
    # WeKnora Cloud 第三方 API
    # 读取 SQL/检索行键（存量面）
    # Cypher 字段
    'retrieval/graph/Neo4jGraphRepository.java': {'knowledge_id', 'source_labels', 'target_labels'},
    # ImageInfo 面（§15.3 冻结族）
    'retrieval/support/ImageInfoMatchUtil.java': {'end_pos', 'ocr_text', 'original_url', 'start_pos'},
    # web 引用载荷（同 ReferencesSupport 存量面）
    'retrieval/support/WebResultConverter.java': {'published_at'},
    # 工具结果/附件载荷（存量面，同 tool-results）
    # SSE `agentQuery` 的 data 载荷＝**冻结的线协议**（§14.9l 前提判定 2；FE `messageTimestamp.ts` 明写"别顺手 camelCase"且有断言钉住）⇒ 待拍板；条目按实际存在的键收窄（原 5 键里 3 个后端已无）
    'session/controller/QaSseOrchestrator.java': {'assistant_created_at', 'user_created_at'},
    # 同上（SSE/消息载荷）
    'session/controller/SessionController.java': {'message_id', 'session_id'},
    # MyBatis 列名写入点（非 JSON 键）
    'session/mapper/MessageRepository.java': {'agent_duration_ms', 'agent_id', 'agent_tenant_id', 'is_completed', 'is_fallback', 'knowledge_id', 'model_id', 'rendered_content', 'request_id', 'updated_at'},
    # MyBatis 列名写入点（非 JSON 键）
    # agent_steps/推荐面落库 + 回放
    # 观测面（B135 复核改标：setupSpan.finish 的 setup 输出 + 日志字段 Map.of("event", …, "duration_ms", …)）
    'session/service/SessionKnowledgeQaService.java': {'duration_ms', 'error_type', 'knowledge_base_ids', 'search_targets', 'session_id', 'total_duration_ms', 'total_stages'},
    # HTTP query 参数名（非 JSON 键）
    'storage/fileserve/FileProxyService.java': {'file_path'},
    # 存储引擎面（snake，B14 冻结）
    'storage/fileserve/StorageFileResolver.java': {'default_provider', 'path_prefix'},
    # 存储引擎配置面（snake，同上）
    'system/controller/SystemController.java': {'access_key_id', 'bucket_name', 'mineru_parse_method', 'secret_access_key', 'use_ssl', 'weknoracloud_app_id'},
    # MyBatis 列名/参数（非 JSON 键）
    # MyBatis 列名写入点（非 JSON 键）
    # 模型输出契约（提示词里就是 new_slugs；只解析入站）
    'wiki/service/ingest/WikiIngestCitePipeline.java': {'new_slugs'},
    # wiki 摄取内部 jsonb 状态（存量）
    'wiki/service/ingest/WikiIngestMapPhase.java': {'body_preview', 'candidate_slugs', 'cited_chunks', 'cited_slugs', 'classify_batches', 'concepts_preview', 'content_chars', 'doc_title', 'entities_preview', 'extracted_pages', 'extracted_slugs', 'new_slugs', 'new_slugs_sample', 'old_pages', 'pass0_fallback', 'reparse_slugs', 'stale_slugs', 'summary_chars', 'summary_line', 'summary_preview', 'top_cited', 'uncited_slugs'},
    # wiki 摄取内部 jsonb 状态（存量）
    'wiki/service/ingest/WikiIngestReducePhase.java': {'addition_failed', 'affected_type', 'chunk_refs', 'content_preview', 'page_summary', 'page_title', 'page_type', 'source_refs'},
    # wiki 摄取内部 jsonb 统计（存量）
    'wiki/service/ingest/WikiIngestRunSupport.java': {'failed_slug_writes', 'pages_dropped', 'pages_dropped_preview', 'pages_total', 'pages_written', 'pages_written_preview'},
    # 模型输出契约（LLM 载荷）
    'wiki/service/page/NewSlugFromCitation.java': {'source_chunks'},
}

PATTERNS = (
    re.compile(r'@JsonProperty\("([a-z0-9]+(?:_[a-z0-9]+)+)"\)'),
    re.compile(r'@JsonPropertyOrder\(\{([^}]*)\}\)'),
    re.compile(r'\.(?:put|set)\("([a-z0-9]+(?:_[a-z0-9]+)+)"\s*,'),
    re.compile(r'\b(?:putNonEmpty|putTrue|putAlways|putOmitEmpty)\([^,"]*,\s*'
               r'"([a-z0-9]+(?:_[a-z0-9]+)+)"'),
)
SNAKE_IN_LIST = re.compile(r'"([a-z0-9]+(?:_[a-z0-9]+)+)"')


def hits() -> dict[str, set[str]]:
    found: dict[str, set[str]] = {}
    for path, rel in sorted(
            (p, str(p.relative_to(r))) for r in PKG_ROOTS for p in r.rglob('*.java')):
        if rel.startswith(FROZEN_PREFIXES):
            continue
        text = path.read_text(encoding='utf-8', errors='ignore')
        keys: set[str] = set()
        for m in PATTERNS[0].finditer(text):
            keys.add(m.group(1))
        for m in PATTERNS[1].finditer(text):          # @JsonPropertyOrder({...})
            keys.update(SNAKE_IN_LIST.findall(m.group(1)))
        for pattern in PATTERNS[2:]:
            for m in pattern.finditer(text):
                keys.add(m.group(1))
        if keys:
            found[rel] = keys
    return found


def main() -> int:
    found = hits()
    violations = {f: sorted(k - BASELINE.get(f, set())) for f, k in found.items()}
    violations = {f: k for f, k in violations.items() if k}
    stale = sorted(f for f in BASELINE if f not in found)

    if '--list' in sys.argv:
        for f, keys in sorted(found.items()):
            mark = '基线' if f in BASELINE else '**新增**'
            print(f'  [{mark}] {f}: {sorted(keys)}')
        return 0

    if violations and '--strict' not in sys.argv:
        print(f'ℹ 待判清单：{len(violations)} 个文件有基线外的 snake 键 '
              f'（未复核为真债，也未被入基线；详见 --list）。默认不判失败。')
        return 0
    if violations:
        print('✗ 非冻结面出现新的 snake JSON 键（§2：自己的 JSON 面用 camel）：')
        for f, keys in sorted(violations.items()):
            print(f'    {f}: {keys}')
        print('  → 改 camel（同一提交带上前端与夹具）；确属第三方/模型契约则加入本脚本 BASELINE 并写明理由。')
        return 1
    note = f'（基线 {sum(len(v) for v in BASELINE.values())} 条，均已逐条复核）'
    if stale:
        note += f'；注意基线中有 {len(stale)} 个文件已无命中，可清理：{stale[:3]}'
    print(f'✓ 无新增 snake JSON 键 {note}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
