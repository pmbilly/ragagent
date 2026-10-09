package com.ragagent.session.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ragagent.chatpipeline.ChatManage;
import com.ragagent.chatpipeline.EventManager;
import com.ragagent.chatpipeline.PipelineBuilder;
import com.ragagent.chatpipeline.PipelineEventType;
import com.ragagent.chatpipeline.PipelineLog;
import com.ragagent.chatpipeline.PipelineProgress;
import com.ragagent.chatpipeline.PipelineProgress.StageProgress;
import com.ragagent.chatpipeline.plugin.PluginError;
import com.ragagent.chatpipeline.SummaryConfig;
import com.ragagent.common.context.TenantContext;
import com.ragagent.common.error.BizException;
import com.ragagent.common.prompt.MessageAttachmentsPrompt;
import com.ragagent.settings.ConversationProperties;
import com.ragagent.event.EventBus;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.service.KnowledgeBaseService;
import com.ragagent.knowledge.service.KnowledgeService;
import com.ragagent.model.domain.Model;
import com.ragagent.model.service.ModelService;
import com.ragagent.common.retrieval.SearchResult;

import static com.ragagent.session.service.QaSupport.TagScope;
import com.ragagent.chatpipeline.PipelinePorts;
import com.ragagent.session.support.PipelineViews;
import com.ragagent.agent.management.domain.CustomAgentEntity;
import com.ragagent.auth.service.TenantService;
import com.ragagent.common.retrieval.SearchTarget;
import com.ragagent.tracing.langfuse.LangfuseManager;
import com.ragagent.tracing.langfuse.Span;
import com.ragagent.websearch.mapper.WebSearchProviderRepository;

/**
 * 知识问答 service 面（chat_pipeline 的调用方）。
 *
 * <p>已知差异（备案）：检索执行面暂缺（hybridSearch adapter 为空实现）、
 * web_fetch/网页抓取在 RAG 路径可用。Langfuse span
 * 为 no-op seam，span 生命周期调用点保留。</p>
 *
 * <p>KnowledgeQA/KnowledgeQAByEvent/SearchKnowledge
 * 三条入口流是单一状态机,解析与降级已拆至 SessionQaResolution/SessionQaFallback。</p>

 */
@Service
public class SessionKnowledgeQaService {

    private static final Logger log = LoggerFactory.getLogger(SessionKnowledgeQaService.class);

    final EventManager eventManager;
    final ConversationProperties cfg;
    final ModelService modelService;
    final KnowledgeService knowledgeService;
    final KnowledgeBaseService knowledgeBaseService;
    final PipelinePorts.ModelService pipelineModelService;
    final TenantService tenantService;
    final WebSearchProviderRepository webSearchProviderRepository;
    final javax.sql.DataSource dataSource;

    /** 解析/降级协作者(构造期装配)。 */
    final SessionQaResolution resolution;
    final SessionQaFallback fallback;
    /** WebSearch/WebFetch 有效参数解析（B129 自本类外提）。 */
    final QaWebSearchParams webSearchParams;

    public SessionKnowledgeQaService(EventManager eventManager,
            ConversationProperties cfg,
            ModelService modelService,
            KnowledgeService knowledgeService,
            KnowledgeBaseService knowledgeBaseService,
            PipelinePorts.ModelService pipelineModelService,
            TenantService tenantService,
            WebSearchProviderRepository webSearchProviderRepository,
            javax.sql.DataSource dataSource) {
        this.eventManager = eventManager;
        this.cfg = cfg;
        this.modelService = modelService;
        this.knowledgeService = knowledgeService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.pipelineModelService = pipelineModelService;
        this.tenantService = tenantService;
        this.webSearchProviderRepository = webSearchProviderRepository;
        this.dataSource = dataSource;
        this.resolution = new SessionQaResolution(this);
        this.fallback = new SessionQaFallback(this);
        this.webSearchParams = new QaWebSearchParams(this);
    }

    // ── seam 委托:实现随 Resolution 协作者(外部消费面不变) ──

    public static boolean isAgentMode(ObjectNode c) {
        return SessionQaResolution.isAgentMode(c);
    }

    public long resolveRetrievalTenantId(QaSupport.QaRequest req) {
        return resolution.resolveRetrievalTenantId(req);
    }

    public String resolveChatModelId(QaSupport.QaRequest req, List<String> knowledgeBaseIds,
            List<String> knowledgeIds) {
        return resolution.resolveChatModelId(req, knowledgeBaseIds, knowledgeIds);
    }

    public SessionQaResolution.MentionScope restrictMentionsToAgentScope(
            CustomAgentEntity agent, ObjectNode agentCfg,
            long sessionTenantId, List<String> kbIds, List<String> knowledgeIds) {
        return resolution.restrictMentionsToAgentScope(agent, agentCfg, sessionTenantId, kbIds, knowledgeIds);
    }

    public KnowledgeResolution resolveKnowledgeBases(QaSupport.QaRequest req) {
        return resolution.resolveKnowledgeBases(req);
    }

    public List<SearchTargetView> buildSearchTargets(long tenantId, List<String> knowledgeBaseIds,
            List<String> knowledgeIds, List<TagScope> tagScopes) {
        return resolution.buildSearchTargets(tenantId, knowledgeBaseIds, knowledgeIds, tagScopes);
    }

    public KnowledgeBase findKnowledgeBase(String kbId) {
        return resolution.findKnowledgeBase(kbId);
    }

    public List<String> listKnowledgeIdsByTagIds(long tenantId, String kbId, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> ids = new ArrayList<>();
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < tagIds.size(); i++) {
            if (i > 0) {
                in.append(',');
            }
            in.append('?');
        }
        String sql = "SELECT DISTINCT k.id FROM knowledges k "
                + "JOIN knowledge_tag_relations ktr ON k.id = ktr.knowledge_id "
                + "WHERE k.tenant_id = ? AND k.knowledge_base_id = ? AND ktr.tag_id IN (" + in + ")";
        try (var conn = dataSource.getConnection(); var ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tenantId);
            ps.setString(2, kbId);
            for (int i = 0; i < tagIds.size(); i++) {
                ps.setString(3 + i, tagIds.get(i));
            }
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getString(1));
                }
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        return ids;
    }

    // ==================================================================
    // KnowledgeQA 主流程
    // ==================================================================

    public void knowledgeQA(QaSupport.QaRequest req, EventBus eventBus) {
        String sessionId = req.session.getId();
        log.info("Knowledge base question answering parameters, session ID: {}, query: {}, webSearchEnabled: {}",
                sessionId, req.query, req.webSearchEnabled);

        // qa.setup span 包住请求装配段（KB/模型解析、检索目标构建、
        // agent 覆盖应用）——补上 trace 开始到首个阶段观测之间的可见空档
        Span setupSpan =
                LangfuseManager.get().startSpan(
                        new LangfuseManager.SpanOptions(
                                "qa.setup", null,
                                java.util.Map.of("sessionId", sessionId == null ? "" : sessionId)));

        // Resolve knowledge bases using shared helper
        KnowledgeResolution kb = resolution.resolveKnowledgeBases(req);

        // Resolve chat model ID using shared helper
        String chatModelId = resolution.resolveChatModelId(req, kb.kbIds, kb.knowledgeIds);

        // Initialize ChatManage defaults from config.yaml
        SummaryConfig summaryConfig = new SummaryConfig();
        summaryConfig.setPrompt(cfg.getSummaryPrompt());
        summaryConfig.setContextTemplate(cfg.getSummaryContextTemplate());
        summaryConfig.setTemperature(cfg.getSummaryTemperature());
        summaryConfig.setNoMatchPrefix(cfg.getSummaryNoMatchPrefix());
        summaryConfig.setMaxCompletionTokens(cfg.getSummaryMaxCompletionTokens());
        String fallbackStrategy = cfg.getFallbackStrategy();
        if (fallbackStrategy == null || fallbackStrategy.isEmpty()) {
            fallbackStrategy = "fixed";
            log.info("Fallback strategy not set, using default: {}", fallbackStrategy);
        }

        // Resolve chat model vision capability and VLM model ID for image routing
        boolean chatModelSupportsVision = false;
        String vlmModelId = "";
        if (!chatModelId.isEmpty()) {
            try {
                Model chatModelInfo = modelService.getModelByID(chatModelId);
                if (chatModelInfo != null) {
                    chatModelSupportsVision = chatModelInfo.getParameters() != null && chatModelInfo.getParameters().isSupportsVision();
                }
            } catch (RuntimeException e) {
                // 获取失败 → 保持 false
            }
        }
        if (req.agentConfig != null) {
            vlmModelId = req.agentConfig.path("vlmModelId").asText("");
        }

        // Resolve retrieval tenant scope using shared helper
        long retrievalTenantId = resolution.resolveRetrievalTenantId(req);

        // Build unified search targets（computed once）
        List<SearchTargetView> searchTargets;
        try {
            searchTargets = resolution.buildSearchTargets(retrievalTenantId, kb.kbIds, kb.knowledgeIds, req.tagScopes);
        } catch (RuntimeException e) {
            throw new RuntimeException("build search targets: " + e.getMessage(), e);
        }

        log.info("Creating chat manage object, knowledge base IDs: {}, knowledge IDs: {}, chat model ID: {}, search targets: {}",
                kb.kbIds, kb.knowledgeIds, chatModelId, searchTargets.size());

        ChatManage chatManage = new ChatManage();
        chatManage.setQuery(req.query);
        chatManage.setSessionId(sessionId);
        chatManage.setUserId(SessionService.sessionUserIDForLookup());
        chatManage.setMaxRounds(cfg.getMaxRounds());
        chatManage.setKnowledgeBaseIds(kb.kbIds);
        chatManage.setKnowledgeIds(kb.knowledgeIds);
        chatManage.setSearchTargets(SearchTargetView.toPipeline(searchTargets));
        chatManage.setVectorThreshold(cfg.getVectorThreshold());
        chatManage.setKeywordThreshold(cfg.getKeywordThreshold());
        chatManage.setEmbeddingTopK(cfg.getEmbeddingTopK());
        chatManage.setRerankTopK(cfg.getRerankTopK());
        chatManage.setRerankThreshold(cfg.getRerankThreshold());
        chatManage.setChatModelId(chatModelId);
        chatManage.setSummaryConfig(summaryConfig);
        chatManage.setFallbackStrategy(fallbackStrategy);
        chatManage.setFallbackResponse(cfg.getFallbackResponse());
        chatManage.setFallbackPrompt(cfg.getFallbackPrompt());
        chatManage.setEnableRewrite(cfg.isEnableRewrite());
        chatManage.setEnableQueryExpansion(cfg.isEnableQueryExpansion());
        chatManage.setRewritePromptSystem(cfg.getRewritePromptSystem());
        chatManage.setRewritePromptUser(cfg.getRewritePromptUser());
        chatManage.setWebSearchEnabled(req.webSearchEnabled);
        chatManage.setWebSearchProviderId(webSearchParams.resolveWebSearchProviderId(req, retrievalTenantId));
        chatManage.setWebSearchMaxResults(webSearchParams.resolveWebSearchMaxResults(req));
        chatManage.setWebFetchEnabled(webSearchParams.resolveWebFetchEnabled(req));
        chatManage.setWebFetchTopN(webSearchParams.resolveWebFetchTopN(req));
        chatManage.setTenantId(retrievalTenantId);
        chatManage.setImages(req.imageUrls);
        chatManage.setVlmModelId(vlmModelId);
        chatManage.setChatModelSupportsVision(chatModelSupportsVision);
        chatManage.setAttachments(PipelineViews.ofAttachments(req.attachments));
        chatManage.setLanguage(currentLanguage());
        chatManage.setRewriteQuery(req.query);
        chatManage.setImageDescription(req.imageDescription);
        chatManage.setQuotedContext(req.quotedContext);
        chatManage.setEventBus(eventBus.asEventBusInterface());
        chatManage.setMessageId(req.assistantMessageId);
        chatManage.setUserMessageId(req.userMessageId);

        // Apply custom agent overrides
        resolution.applyAgentOverridesToChatManage(req, chatManage);

        // Pipeline 选择
        boolean hasKb = hasKnowledgeRetrievalScope(searchTargets, kb.kbIds, kb.knowledgeIds);
        boolean needsRag = hasKb || req.webSearchEnabled;
        boolean hasHistory = chatManage.getMaxRounds() > 0;

        List<String> pipeline;
        if (!needsRag) {
            // Pure chat — no retrieval needed.
            String userContent = req.query;
            if (!req.imageDescription.isEmpty() && !chatModelSupportsVision) {
                userContent += "\n\n[用户上传图片内容]\n" + req.imageDescription;
            }
            if (!req.quotedContext.isEmpty()) {
                userContent += "\n\n" + req.quotedContext;
            }
            if (!req.attachments.isEmpty()) {
                userContent += MessageAttachmentsPrompt.build(
                        PipelineViews.ofAttachments(req.attachments));
            }
            chatManage.setUserContent(userContent);

            pipeline = PipelineBuilder.builder()
                    .addIf(hasHistory, PipelineEventType.LOAD_HISTORY)
                    .add(PipelineEventType.MEMORY_RECALL)
                    .add(PipelineEventType.CHAT_COMPLETION_STREAM)
                    .build();
        } else {
            // RAG — dynamically assembled.
            pipeline = PipelineBuilder.builder()
                    .addIf(hasHistory, PipelineEventType.LOAD_HISTORY)
                    .add(PipelineEventType.MEMORY_RECALL)
                    .add(PipelineEventType.QUERY_UNDERSTAND)
                    .add(PipelineEventType.CHUNK_SEARCH_PARALLEL)
                    .add(PipelineEventType.CHUNK_RERANK)
                    .addIf(req.webSearchEnabled, PipelineEventType.WEB_FETCH)
                    .add(PipelineEventType.CHUNK_MERGE)
                    .add(PipelineEventType.FILTER_TOP_K)
                    .addIf(chatManage.isDataAnalysisEnabled(), PipelineEventType.DATA_ANALYSIS)
                    .add(PipelineEventType.INTO_CHAT_MESSAGE)
                    .add(PipelineEventType.CHAT_COMPLETION_STREAM)
                    .build();
        }

        log.info("Assembled pipeline ({} stages), hasKB={}, webSearch={}, history={}",
                pipeline.size(), hasKb, req.webSearchEnabled, hasHistory);

        // 进入 QA 事件处理前打上「按会话属主租户查」标记——管线内的会话/
        // 消息查询由此走**租户范围**（共享 agent 场景下当前主体不是属主，带 user 范围会查不到）。
        // 清理在请求收尾处（KnowledgeQaController 的 TenantContext.clear() 旁）。
        SessionLookupScope.mark();

        // setup span 收尾（stages / KB 列表 / 检索目标数）
        java.util.Map<String, Object> setupOutput = new java.util.LinkedHashMap<>();
        setupOutput.put("stages", pipeline.size());
        setupOutput.put("knowledge_base_ids", kb.kbIds);
        setupOutput.put("search_targets", searchTargets.size());
        setupSpan.finish(setupOutput, null, null);

        // Trigger（session tenant 设定 + sessionID 传播在 Java 侧由 TenantContext 承担）
        knowledgeQAByEvent(chatManage, pipeline);
        log.info("Knowledge base question answering initiated");
    }

    // ==================================================================
    // KnowledgeQAByEvent
    // ==================================================================

    public void knowledgeQAByEvent(ChatManage chatManage, List<String> eventList) {
        log.info("Start processing knowledge base question answering through events");
        log.info("Knowledge base question answering parameters, session ID: {}, query: {}",
                chatManage.getSessionId(), chatManage.getQuery());

        List<String> methods = new ArrayList<>(eventList);
        log.info("Trigger event list: {}", methods);

        long pipelineStart = System.currentTimeMillis();
        String lastRetrievalStage = PipelineProgress.lastConsolidatedRetrievalStage(eventList, chatManage);
        StageProgress retrievalProgress = null;
        long retrievalStart = 0;
        StageProgress understandProgress = null;
        long understandStart = 0;
        for (String eventType : eventList) {
            long stageStart = System.currentTimeMillis();
            // 阶段 span 包住本阶段；CHAT_COMPLETION_STREAM 跳过——
            // 该阶段的 chat.completion.stream generation 已覆盖完整时长，再套一层
            // 会产出"视觉上超出父节点"的子观测
            Span stageSpan = null;
            if (!PipelineEventType.CHAT_COMPLETION_STREAM.equals(eventType)) {
                stageSpan = LangfuseManager.get().startSpan(
                        new LangfuseManager.SpanOptions(
                                "pipeline." + eventType, null,
                                java.util.Map.of("event_type", eventType,
                                        "session_id", chatManage.getSessionId() == null
                                                ? "" : chatManage.getSessionId())));
            }
            if (PipelineEventType.QUERY_UNDERSTAND.equals(eventType)
                    && PipelineProgress.shouldEmitQueryUnderstandProgress(chatManage)) {
                understandStart = stageStart;
                understandProgress = PipelineProgress.beginQueryUnderstandProgress(chatManage);
            }
            if (PipelineProgress.isConsolidatedRetrievalStage(eventType, chatManage) && retrievalProgress == null) {
                retrievalStart = stageStart;
                retrievalProgress = PipelineProgress.beginRetrievalProgress(chatManage);
            }
            // Emit references before answer streaming（complete 关流前必达）
            if (PipelineEventType.CHAT_COMPLETION_STREAM.equals(eventType)) {
                SessionQaFallback.emitKnowledgeReferencesEvent(chatManage);
            }
            PluginError err = eventManager.trigger(eventType, chatManage);
            if (understandProgress != null && PipelineEventType.QUERY_UNDERSTAND.equals(eventType)) {
                PipelineProgress.endQueryUnderstandProgress(chatManage, understandProgress,
                        understandStart, err);
                understandProgress = null;
            }
            if (retrievalProgress != null
                    && PipelineProgress.shouldCloseRetrievalProgress(eventType, lastRetrievalStage, err)) {
                PipelineProgress.endRetrievalProgress(chatManage, retrievalProgress,
                        retrievalStart, err);
                retrievalProgress = null;
            }
            long stageDuration = System.currentTimeMillis() - stageStart;

            // 阶段 span 收尾（输出时长；SEARCH_NOTHING 不算错误）
            if (stageSpan != null) {
                String stageErr = err != null && err != PluginError.SEARCH_NOTHING
                        ? (err.err != null ? err.err.getMessage() : err.description) : null;
                stageSpan.finish(java.util.Map.of("durationMs", stageDuration), null, stageErr);
            }

            // 用户停止：先于"检索无结果"判定
            if (cancelled()) {
                PipelineLog.warn("Pipeline", "stage_cancelled", Map.of(
                        "event", eventType, "duration_ms", stageDuration, "reason", "context canceled"));
                throw new RuntimeException("context canceled");
            }

            if (err == PluginError.SEARCH_NOTHING) {
                PipelineLog.warn("Pipeline", "stage_fallback", Map.of(
                        "event", eventType, "duration_ms", stageDuration,
                        "reason", "search_nothing", "strategy", chatManage.getFallbackStrategy()));
                fallback.handleFallbackResponse(chatManage);
                return;
            }

            if (err != null) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("event", eventType);
                f.put("durationMs", stageDuration);
                f.put("error_type", err.errorType);
                f.put("description", err.description);
                PipelineLog.error("Pipeline", "stage_failed", f);
                throw err.err != null ? new RuntimeException(err.err) : new RuntimeException(err.description);
            }

            PipelineLog.info("Pipeline", "stage_complete", Map.of(
                    "event", eventType, "duration_ms", stageDuration));
        }

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("sessionId", chatManage.getSessionId());
        f.put("total_stages", eventList.size());
        f.put("totalDurationMs", System.currentTimeMillis() - pipelineStart);
        PipelineLog.info("Pipeline", "all_stages_complete", f);
    }

    /** 虚拟线程取消探测（检测中断标志；stop 链路 4.6d 接线）。 */
    private static boolean cancelled() {
        return Thread.currentThread().isInterrupted();
    }

    // ==================================================================
    // SearchKnowledge
    // ==================================================================

    public List<SearchResult> searchKnowledge(List<String> knowledgeBaseIds, List<String> knowledgeIds,
            List<TagScope> tagScopes, String query) {
        log.info("Start knowledge base search without LLM summary");
        Long tenantIdBoxed = TenantContext.currentTenantId();
        if (tenantIdBoxed == null) {
            throw new RuntimeException("workspace ID not found in context");
        }
        long tenantId = tenantIdBoxed;

        List<SearchTargetView> searchTargets;
        try {
            searchTargets = resolution.buildSearchTargets(tenantId, knowledgeBaseIds, knowledgeIds, tagScopes);
        } catch (RuntimeException e) {
            throw new RuntimeException("build search targets: " + e.getMessage(), e);
        }

        if (searchTargets.isEmpty()) {
            log.warn("No search targets available, returning empty results");
            return new ArrayList<>();
        }

        // Create default retrieval parameters — prefer tenant RetrievalConfig
        String userId = SessionService.sessionUserIDForLookup();
        RetrievalConfigView rc = new RetrievalConfigView(null);
        try {
            var tenant = tenantService.getTenantById(tenantId);
            if (tenant != null) {
                rc = new RetrievalConfigView(tenant.getRetrievalConfig());
            }
        } catch (RuntimeException e) {
            // 获取失败 → 保持 null（由 GetEffective* 兜底）
        }

        ChatManage chatManage = new ChatManage();
        chatManage.setQuery(query);
        chatManage.setUserId(userId);
        // 插件从 ChatManage 取租户（无隐式上下文；与 QA 路径同源
        // retrievalTenantId 的同款赋值）——漏了它 Merge 阶段 faq_enrich/expand 全跳过，
        // knowledge-search 响应的 content 就少了前后文扩块，与既定输出契约不符。
        chatManage.setTenantId(tenantId);
        chatManage.setKnowledgeBaseIds(knowledgeBaseIds);
        chatManage.setKnowledgeIds(knowledgeIds);
        chatManage.setSearchTargets(SearchTargetView.toPipeline(searchTargets));
        chatManage.setMaxRounds(cfg.getMaxRounds());
        chatManage.setEmbeddingTopK(rc.embeddingTopK());
        chatManage.setVectorThreshold(rc.vectorThreshold());
        chatManage.setKeywordThreshold(rc.keywordThreshold());
        chatManage.setRerankTopK(rc.rerankTopK());
        chatManage.setRerankThreshold(rc.rerankThreshold());
        chatManage.setRewriteQuery(query);

        // Use rerank model from RetrievalConfig if set, otherwise first available
        if (rc.rerankModelId() != null && !rc.rerankModelId().isEmpty()) {
            chatManage.setRerankModelId(rc.rerankModelId());
        } else {
            try {
                for (Model model : modelService.listModels()) {
                    if (model == null) {
                        continue;
                    }
                    if ("Rerank".equals(model.getType())) {
                        chatManage.setRerankModelId(model.getId());
                        break;
                    }
                }
            } catch (RuntimeException e) {
                log.error("Failed to get models: {}", e.toString());
                throw e;
            }
        }

        List<String> searchEvents = List.of(
                PipelineEventType.CHUNK_SEARCH,
                PipelineEventType.CHUNK_RERANK,
                PipelineEventType.CHUNK_MERGE,
                PipelineEventType.FILTER_TOP_K);

        log.info("Trigger search event list: {}", searchEvents);

        for (String event : searchEvents) {
            log.info("Starting to trigger search event: {}", event);
            // search_knowledge 流的阶段 span（恒开，含 SEARCH_NOTHING）
            Span stageSpan =
                    LangfuseManager.get().startSpan(
                            new LangfuseManager.SpanOptions(
                                    "pipeline." + event, null,
                                    java.util.Map.of("event_type", event,
                                            "flow", "search_knowledge")));
            PluginError err = eventManager.trigger(event, chatManage);

            // SEARCH_NOTHING 不算错误；其余带底层错误消息收尾
            String stageErr = err != null && err != PluginError.SEARCH_NOTHING
                    ? (err.err != null ? err.err.getMessage() : err.description) : null;
            stageSpan.finish(null, null, stageErr);

            if (err == PluginError.SEARCH_NOTHING) {
                log.warn("Event {} triggered, search result is empty", event);
                return new ArrayList<>();
            }
            if (err != null) {
                log.error("Event triggering failed, event: {}, error type: {}, description: {}, error: {}",
                        event, err.errorType, err.description, err.err);
                // 管线错误 → 500 内部错误，文案取底层错误消息
                String msg = err.err != null ? err.err.getMessage() : err.description;
                throw BizException.internal(msg);
            }
            log.info("Event {} triggered successfully", event);
        }

        log.info("Knowledge base search completed, found {} results",
                chatManage.getMergeResult() == null ? 0 : chatManage.getMergeResult().size());
        return chatManage.getMergeResult() == null ? new ArrayList<>() : chatManage.getMergeResult();
    }

    // ==================================================================
    // 共享 QA helpers
    // ==================================================================

    /**
     * 判定骨架（见 {@link #callerCanReadKb}）：
     * 作用域租户为 0 / 属主租户为 0 ⇒ 否；同租户 ⇒ 是；否则交给共享判定。
     * 抽成静态纯函数以便脱离 Spring 上下文做回归（API-key 作用域与共享判定在调用方装配）。
     */
    static boolean kbReadableByCaller(Long scopeTenantId, long ownerTenantId,
                                      java.util.function.BooleanSupplier sharedPermitsViewer) {
        if (scopeTenantId == null || scopeTenantId == 0 || ownerTenantId == 0) {
            return false;
        }
        if (scopeTenantId == ownerTenantId) {
            return true;
        }
        return sharedPermitsViewer != null && sharedPermitsViewer.getAsBoolean();
    }

    // substringByCodePoints 仍留在门面：modelcontext/ModelOutput 也在用（跨域共享的小工具）
    static String substringByCodePoints(String s, int max) {
        int i = 0;
        int cp = 0;
        while (i < s.length() && cp < max) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            cp++;
        }
        return s.substring(0, i);
    }




    // ==================================================================
    // 纯函数族
    // ==================================================================

    public static Map<String, List<String>> mergeTagScopesByKb(List<TagScope> scopes) {
        Map<String, List<String>> byKb = new LinkedHashMap<>();
        Map<String, Set<String>> seen = new LinkedHashMap<>();
        if (scopes == null) {
            return byKb;
        }
        for (TagScope scope : scopes) {
            if (scope.knowledgeBaseId.isEmpty()) {
                continue;
            }
            Set<String> seenTags = seen.computeIfAbsent(scope.knowledgeBaseId, k -> new LinkedHashSet<>());
            for (String tagId : scope.tagIds) {
                if (tagId.isEmpty() || seenTags.contains(tagId)) {
                    continue;
                }
                seenTags.add(tagId);
                byKb.computeIfAbsent(scope.knowledgeBaseId, k -> new ArrayList<>()).add(tagId);
            }
        }
        return byKb;
    }

    public static List<String> uniqueNonEmptyStrings(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            if (value == null || value.isEmpty() || seen.contains(value)) {
                continue;
            }
            seen.add(value);
            out.add(value);
        }
        return out;
    }

    public static List<String> intersectStrings(List<String> left, List<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> rightSet = new LinkedHashSet<>(right);
        List<String> out = new ArrayList<>();
        for (String value : left) {
            if (rightSet.contains(value)) {
                out.add(value);
            }
        }
        return out;
    }

    /** 是否有知识检索范围（以 target 视图判）。 */
    public static boolean hasKnowledgeRetrievalScope(List<SearchTargetView> targets,
            List<String> kbIds, List<String> knowledgeIds) {
        if (!kbIds.isEmpty() || !knowledgeIds.isEmpty()) {
            return true;
        }
        for (SearchTargetView t : targets) {
            if (!t.tagIds.isEmpty() || !t.knowledgeIds.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static List<String> stringListOf(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                if (n.isTextual()) {
                    out.add(n.asText());
                }
            }
        }
        return out;
    }

    static long requireTenantId() {
        Long tid = TenantContext.currentTenantId();
        if (tid == null) {
            throw new IllegalStateException("tenant ID not found in context");
        }
        return tid;
    }

    /** 当前语言（dev 缺省 en-US）。 */
    static String currentLanguage() {
        return "en-US";
    }

    // ==================================================================
    // 轻量视图
    // ==================================================================

    /** resolveKnowledgeBases 的二元返回。 */
    public record KnowledgeResolution(List<String> kbIds, List<String> knowledgeIds) {}

    /** SearchTarget 的 service 面视图（管线面经 toPipeline 转换）。 */
    public static final class SearchTargetView {
        public String type = "";
        public String knowledgeBaseId = "";
        public long tenantId;
        public List<String> knowledgeIds = new ArrayList<>();
        public List<String> tagIds = new ArrayList<>();
        public List<String> scopeTagIds = new ArrayList<>();
        public boolean disableRecallThresholds;

        public List<SearchTarget> toPipeline() {
            return toPipelineList();
        }

        public List<SearchTarget> toPipelineList() {
            List<SearchTarget> out = new ArrayList<>();
            out.add(asPipelineTarget());
            return out;
        }

        public SearchTarget asPipelineTarget() {
            return new SearchTarget(type, knowledgeBaseId, tenantId,
                    knowledgeIds, tagIds, scopeTagIds, disableRecallThresholds);
        }

        public static List<SearchTarget> toPipeline(List<SearchTargetView> views) {
            List<SearchTarget> out = new ArrayList<>();
            if (views == null) {
                return out;
            }
            for (SearchTargetView v : views) {
                out.add(v.asPipelineTarget());
            }
            return out;
        }
    }

    /** RetrievalConfig 的读取视图（GetEffective* 兜底）。 */
    private record RetrievalConfigView(JsonNode raw) {
        String rerankModelId() { return raw == null ? null : raw.path("rerankModelId").asText(null); }
        int embeddingTopK() { return effInt("embeddingTopK", 30); }
        double vectorThreshold() { return effDouble("vectorThreshold", 0.2); }
        double keywordThreshold() { return effDouble("keywordThreshold", 0.3); }
        int rerankTopK() { return effInt("rerankTopK", 30); }
        double rerankThreshold() { return effDouble("rerankThreshold", 0.3); }
        private int effInt(String f, int d) { return raw == null || raw.path(f).asInt(0) <= 0 ? d : raw.path(f).asInt(); }
        private double effDouble(String f, double d) {
            return raw == null || raw.path(f).asDouble(-1) < 0 ? d : raw.path(f).asDouble();
        }
    }
}
