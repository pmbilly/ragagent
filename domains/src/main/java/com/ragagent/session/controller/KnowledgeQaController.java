package com.ragagent.session.controller;

import com.ragagent.session.qa.QaRequestBinder;
import com.ragagent.session.qa.QaAttachmentResolver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ragagent.contract.error.BizException;
import com.ragagent.contract.retrieval.SearchResult;
import com.ragagent.session.qa.ParsedRequest;
import com.ragagent.session.qa.QaRequestParser;
import com.ragagent.session.qa.QaSseOrchestrator;
import com.ragagent.session.qa.QaTurnExecutor;
import com.ragagent.session.qa.QaTurnFinalizer;
import com.ragagent.session.dto.QaRequests.CreateKnowledgeQARequest;
import com.ragagent.session.dto.QaRequests.SearchKnowledgeRequest;
import com.ragagent.session.service.AgentResolver;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.suggestion.MessageSuggestionService;
import com.ragagent.session.service.QaSupport;
import com.ragagent.session.service.QaSupport.QaMode;
import com.ragagent.session.service.QaSupport.QaRequestContext;
import com.ragagent.session.service.SessionAgentQaService;
import com.ragagent.session.service.SessionKnowledgeQaService;
import com.ragagent.session.service.SessionService;
import com.ragagent.session.service.TemporaryDocumentService;
import com.ragagent.session.controller.sse.StreamEventEmitter;
import com.ragagent.platform.stream.StreamManager;

import jakarta.servlet.http.HttpServletResponse;
import com.ragagent.contract.tenant.TenantConfigLookup;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.contract.apikey.TenantAPIKeyScope;
import com.ragagent.contract.memory.MemoryExtractionPort;
import com.ragagent.session.service.SteerRunCoordinator;
import com.ragagent.session.controller.sse.SseFrameWriter;
import com.ragagent.contract.storage.support.FileUrlPort;
import com.ragagent.contract.storage.support.Mode;
import com.ragagent.contract.storage.support.StorageBackendResolver;
import com.ragagent.contract.tenant.TenantConfigLookup.TenantStorageView;
import com.ragagent.platform.web.ApiResult;
import com.ragagent.platform.storage.support.FileResolverFactory;


/**
 * chat 三入口。
 *
 * <h2>三个端点</h2>
 * <ul>
 *   <li>POST /api/v1/knowledge-chat/{sessionId} — KnowledgeQA（RAG/纯聊天管线）</li>
 *   <li>POST /api/v1/agent-chat/{sessionId} — AgentQA（agent 引擎）</li>
 *   <li>POST /api/v1/knowledge-search — SearchKnowledge（无 LLM 总结检索）</li>
 * </ul>
 *
 * <h2>关键时序（SSE 时序最高危）</h2>
 * <ol>
 *   <li>agent 模式先 rejectIfOtherAgentRunLive（409）→ Emit(agent.query)；</li>
 *   <li>persistTurnMessages 先建 user/assistant 行；</li>
 *   <li>setupSSEStream：SetLiveRun 在 SSE 头<b>之前</b>（409/503 必须还能改状态码）；
 *       agent_query 事件写流；stop 处理器 + 独立 stop watcher + AgentStreamBridge 订阅；</li>
 *   <li>异步执行 QA 服务（虚拟线程），主线程 handleAgentEventsForSSE 100ms 轮询
 *       StreamManager 推帧，complete 后补 completion 事件；</li>
 *   <li>agent 模式清理时机：completeAssistantMessage → follow-up 交接 → clearLiveRun。</li>
 * </ol>
 *
 * <h2>已备案差异</h2>
 * <ul>
 *   <li>共享 agent 解析：resolveAgent 共享优先、source==0 才回落 own；
 *       GetSharedAgentForTenant 的 ApplyBuiltinAgentLocalization 只覆盖
 *       name/description/avatar（QA 消费 config/tenant，不进字节契约），随 agent.management 装配层
 *       统一补齐；access.WithSharedAgent 的 KB grant 机制（检索授权收窄）随检索面
 *       专项收口——Java 检索租户已取 agentRow.tenantId（等价执行范围）。</li>
 *   <li>图片上传/附件的存储写入与 VLM 分析：saveImageAttachments 的对象存储写入
 *       在 dev（本地盘）下同样生效；VLM 分析 emit-only 形态保留。</li>
 *   <li>临时附件的 ResolveForPrompt 内容选择（等待/跳过的事件形态保留，内容解析
 *       seam 随附件管线收口）。</li>
 * </ul>
 */
@RestController
@ApiResult
public class KnowledgeQaController {


    private final SessionService sessionService;

    /** 执行/落库簇。 */
    private final QaTurnExecutor executor;

    /** 租户服务（A3-3 接线；原 @Autowired 字段，改构造注入保持可选语义）。 */
    private final TenantConfigLookup tenantService;

    /** 附件解析簇。 */
    private final QaAttachmentResolver attachmentResolver;

    /** SSE 编排簇。 */
    private final QaSseOrchestrator sseOrchestrator;

    /** 收尾簇。 */
    private final QaTurnFinalizer turnFinalizer;

    /** 请求解析主体。 */
    private final QaRequestParser qaRequestParser;
    private final MessageService messageService;
    private final StreamManager streamManager;
    private final SessionKnowledgeQaService knowledgeQaService;
    private final SessionAgentQaService agentQaService;
    private final MessageSuggestionService suggestionService;
    private final TemporaryDocumentService temporaryDocuments;
    private final SteerRunCoordinator steerCoordinator;
    private final StreamEventEmitter emitter;
    private final SseFrameWriter sseFrameWriter;
    private final FileUrlPort fileService;
    private final StorageBackendResolver storageBackendResolver;
    private final MemoryExtractionPort memoryExtraction;

    public KnowledgeQaController(SessionService sessionService,
            MessageService messageService,
            StreamManager streamManager,
            SessionKnowledgeQaService knowledgeQaService,
            SessionAgentQaService agentQaService,
            MessageSuggestionService suggestionService,
            TemporaryDocumentService temporaryDocuments,
            SteerRunCoordinator steerCoordinator,
            StreamEventEmitter emitter,
            SseFrameWriter sseFrameWriter,
            org.springframework.beans.factory.ObjectProvider<FileUrlPort> fileService,
            org.springframework.beans.factory.ObjectProvider<StorageBackendResolver> storageBackendResolver,
            org.springframework.beans.factory.ObjectProvider<MemoryExtractionPort> memoryExtraction,
            org.springframework.beans.factory.ObjectProvider<TenantConfigLookup> tenantServiceProvider,
            FileResolverFactory fileResolverFactory) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.streamManager = streamManager;
        this.knowledgeQaService = knowledgeQaService;
        this.agentQaService = agentQaService;
        this.suggestionService = suggestionService;
        this.temporaryDocuments = temporaryDocuments;
        this.steerCoordinator = steerCoordinator;
        this.emitter = emitter;
        this.sseFrameWriter = sseFrameWriter;
        // 两个端口按 ObjectProvider 取（A3-3 起 StorageBackendResolver 有生产实现）；
        // 缺 bean 时 Rewriter 按未装配分支降级（handle 模式同形）
        this.fileService = fileService.getIfAvailable();
        this.storageBackendResolver = storageBackendResolver.getIfAvailable();
        this.memoryExtraction = memoryExtraction.getIfAvailable();
            this.qaRequestParser = new QaRequestParser(sessionService, temporaryDocuments,
                    this.fileService, this.storageBackendResolver, fileResolverFactory);
        this.turnFinalizer = new QaTurnFinalizer(this.sessionService, this.messageService, this.suggestionService, this.temporaryDocuments, this.memoryExtraction);
        this.sseOrchestrator = new QaSseOrchestrator(this.streamManager, this.emitter, this.sessionService, this.messageService, this.sseFrameWriter, this.turnFinalizer);
        this.attachmentResolver = new QaAttachmentResolver(this.messageService, this.temporaryDocuments, this.turnFinalizer);
        this.tenantService = tenantServiceProvider.getIfAvailable();
        this.executor = new QaTurnExecutor(this.messageService, this.streamManager, this.knowledgeQaService,
                this.agentQaService, this.steerCoordinator, this.sseOrchestrator, this.turnFinalizer,
                this.attachmentResolver, this.tenantService);
}

    // ── 端点 ──────────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/knowledge-chat/{sessionId}")
    public void knowledgeQA(@PathVariable("sessionId") String rawSessionId,
            @RequestBody(required = false) String rawBody,
            @RequestParam(value = Mode.QUERY_PARAM, required = false) String resourceUrls,
            HttpServletResponse response) throws IOException {
        CreateKnowledgeQARequest request = QaRequestBinder.bindQaRequest(rawBody);
        ParsedRequest parsed = qaRequestParser.parseQARequest(rawSessionId, request, resourceUrls, "KnowledgeQA", agentResolverField, currentTenant());
        executor.executeQA(parsed.reqCtx(), QaMode.NORMAL, !request.disableTitle, response);
    }

    @PostMapping("/api/v1/agent-chat/{sessionId}")
    public void agentQA(@PathVariable("sessionId") String rawSessionId,
            @RequestBody(required = false) String rawBody,
            @RequestParam(value = Mode.QUERY_PARAM, required = false) String resourceUrls,
            HttpServletResponse response) throws IOException {
        CreateKnowledgeQARequest request = QaRequestBinder.bindQaRequest(rawBody);
        ParsedRequest parsed = qaRequestParser.parseQARequest(rawSessionId, request, resourceUrls, "AgentQA", agentResolverField, currentTenant());
        QaRequestContext reqCtx = parsed.reqCtx();

        // agent 模式判定：customAgent.agent_mode > request.agent_enabled
        boolean agentModeEnabled = request.agentEnabled;
        if (reqCtx.agentConfig != null) {
            agentModeEnabled = SessionKnowledgeQaService.isAgentMode(reqCtx.agentConfig);
        }

        if (agentModeEnabled && reqCtx.agentConfig == null) {
            throw BizException.badRequest("agent_id is required when agent mode is enabled");
        }

        if (agentModeEnabled) {
            executor.executeQA(reqCtx, QaMode.AGENT, true, response);
        } else {
            executor.executeQA(reqCtx, QaMode.NORMAL, !request.disableTitle, response);
        }
    }

    @PostMapping("/api/v1/knowledge-search")
    public List<SearchResult> searchKnowledge(@RequestBody(required = false) String rawBody) {
        SearchKnowledgeRequest request = QaRequestBinder.bindSearchRequest(rawBody);
        if (request.query.isEmpty()) {
            // 空 query 已被绑定校验拦截，此分支仅兜底
            throw BizException.badRequest("Query content cannot be empty");
        }

        // 合并单个 knowledge_base_id 进 knowledge_base_ids
        List<String> knowledgeBaseIds = new ArrayList<>(request.knowledgeBaseIds());
        if (!request.knowledgeBaseId.isEmpty() && !knowledgeBaseIds.contains(request.knowledgeBaseId)) {
            knowledgeBaseIds.add(request.knowledgeBaseId);
        }

        List<QaSupport.TagScope> mentionScopes = QaSupport.tagScopesFromMentionedItems(request.mentionedItems());
        List<String> requestTagIds = QaSupport.dedupRequestStrings(request.tagIds());
        String tagError = QaSupport.validateUnscopedTagIds(
                QaSupport.orphanTagIdsForScope(requestTagIds, mentionScopes), knowledgeBaseIds);
        if (tagError != null) {
            throw BizException.badRequest(tagError);
        }
        List<QaSupport.TagScope> tagScopes = QaSupport.mergeTagScopesFromRequestIds(
                mentionScopes, requestTagIds, knowledgeBaseIds);

        if (knowledgeBaseIds.isEmpty() && request.knowledgeIds().isEmpty() && tagScopes.isEmpty()) {
            throw BizException.badRequest(
                    "At least one knowledge_base_id, knowledge_base_ids, knowledge_ids, or scoped tag must be provided");
        }
        TenantAPIKeyScope.authorizeKnowledgeTargets(
                knowledgeBaseIds, request.knowledgeIds());

        List<SearchResult> searchResults = knowledgeQaService.searchKnowledge(
                knowledgeBaseIds, request.knowledgeIds(), tagScopes, request.query);

        // 裸列表（无 {success,data} 信封）：检索结果直出。
        // 引用形式（resource_urls）在检索面不带存储引用（不经 Rewriter.copyReferences 改写）；
        // handle 模式为透传（public 模式的直链生成经 provider 级文件服务，A3-3 起已接线）。
        return searchResults;
    }

    // ── 请求体绑定（绑定错误文案逐字对齐） ───────────────────────────────


    /** 共享/自有 agent 解析（与附件上传入口共用同一组件）。 */
    @org.springframework.beans.factory.annotation.Autowired
    private AgentResolver agentResolverField;

    /**
     * 读者租户实体（A3-3 接线）——供 Rewriter 解析"引用不带 provider scheme 时的租户默认
     * provider"。取不到租户实体时引用一律保留成 handle（降级形态）；
     * 现在按 TenantContext 的 id 取实体，与 {@code SystemController} /
     * {@code HybridSearchService} 同一写法。
     */
    private TenantStorageView currentTenant() {
        Long tid = TenantContext.currentTenantId();
        try {
            return tid == null || tid <= 0 || tenantService == null
                    ? null : tenantService.storageView(tid);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ── executeQA ─────────────────────────────────────────────────────────────


    // 曾有的 errorEventText（剥 BizException 取 appError().message()）已删除：
    // 其前提被线上 A/B 推翻（W5γ5.12），现统一走 BizException.wireText，理由见上面的调用点注释。
}
