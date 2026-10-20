package com.ragagent.memory.service;

import com.ragagent.contract.memory.MemoryConfig;
import com.ragagent.contract.memory.MemoryRecall;
import com.ragagent.contract.memory.MemoryRetrievalContext;
import com.ragagent.contract.pagination.PageResult;
import com.ragagent.contract.tenant.TenantConfigLookup;
import com.ragagent.memory.config.MemorySettings;
import com.ragagent.memory.domain.MemoryExtractPayload;
import com.ragagent.memory.domain.MemoryTopicStat;
import com.ragagent.memory.domain.item.MemoryItem;
import com.ragagent.memory.domain.scope.MemoryScope;
import com.ragagent.memory.dto.MemoryDocAffinity;
import com.ragagent.memory.dto.MemoryDocView;
import com.ragagent.memory.dto.MemoryItemResponse;
import com.ragagent.memory.dto.MemoryTopicView;
import com.ragagent.memory.repository.MemoryRepository;
import com.ragagent.platform.web.ZeroTimeSerializer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 跨会话长期记忆的写入、召回与记忆管理器。
 *
 * <h2>本类与兄弟类的边界</h2>
 * <ul>
 *   <li>召回装配 → {@link MemoryRecallOps}</li>
 *   <li>向量运算 → {@link MemoryVectorService}</li>
 *   <li>排序与选取 → {@link MemoryRecallSelector}</li>
 *   <li>话题解析 → {@link MemoryTopicResolver}</li>
 *   <li>后台蒸馏与整仓回顾 → {@link MemoryExtractionService} /
 *       {@link MemoryConsolidationService}</li>
 * </ul>
 * <p>它们都是同一个包里的兄弟类，靠<b>包级可见性</b>调用本类里的写路径
 * （{@code write*} / {@code rebuildBlock} / {@code enforceCapacity} / {@code observeTopics}）
 * ——用同一个包保住那个边界，
 * 而不是把它们开成 public 让 handler 也能绕过写路径。</p>
 *
 * <h2>调用方</h2>
 * <p>两个：HTTP handler（下一步）与 chat_pipeline 的 memory_recall。所以这里
 * <b>刻意没有</b>任何 HTTP 关注点（Resource / R 信封 / 状态码）——异常往上抛，
 * 由 handler 层映射。</p>
 */
@Service
public class MemoryService {

    static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    /**
     * 一条被拒绝的消息在这段时间内继续阻止重新推导。
     *
     * <p>要挡住的情形是：用户删掉了某条消息产出记忆之后几分钟，
     * 一次 debounce 的运行又读到同一条消息。过了这个窗口，用户说过的话就重新算数。</p>
     */
    static final Duration REJECTED_MESSAGE_WINDOW = Duration.ofHours(1);

    /** 到达改写器的背景上限。 */
    static final int RETRIEVAL_BACKGROUND_RUNE_BUDGET = 240;

    final MemoryRepository repo;
    final TenantConfigLookup tenantService;
    final MemoryVectorService vectorService;
    final MemoryRecallSelector recallSelector;
    final MemoryTopicResolver topicResolver;
    final MemoryModelResolver modelResolver;
    final MemoryScopeGate scopeGate;
    final MemoryWritePipeline writePipeline;
    final MemoryExtractionModelResolver extractionModelResolver;

    /** 目录管理协作者（构造期装配）。 */
    final MemoryCatalogOps catalogOps;

    /** 检索/亲和协作者（构造期装配）。 */
    final MemoryInsightOps insightOps;

    /** 召回装配协作者（构造期装配）。 */
    final MemoryRecallOps recallOps;

    public MemoryService(MemoryRepository repo,
                         TenantConfigLookup tenantService,
                         MemoryVectorService vectorService,
                         MemoryRecallSelector recallSelector,
                         MemoryTopicResolver topicResolver,
                         MemoryModelResolver modelResolver) {
        this.repo = repo;
        this.scopeGate = new MemoryScopeGate(repo, tenantService);
        this.writePipeline = new MemoryWritePipeline(repo, vectorService);
        this.extractionModelResolver = new MemoryExtractionModelResolver(modelResolver);
        this.catalogOps = new MemoryCatalogOps(this);
        this.insightOps = new MemoryInsightOps(this);
        this.recallOps = new MemoryRecallOps(this);
        this.tenantService = tenantService;
        this.vectorService = vectorService;
        this.recallSelector = recallSelector;
        this.topicResolver = topicResolver;
        this.modelResolver = modelResolver;
    }

    /** 实现随协作者。 */
    boolean topicWasForgotten(MemoryScope scope, String... labels) {
        return catalogOps.topicWasForgotten(scope, labels);
    }

    /** 实现随协作者。 */
    void tombstoneTopic(MemoryScope scope, MemoryTopicStat stat) {
        catalogOps.tombstoneTopic(scope, stat);
    }

    /** 实现随协作者。 */
    List<String> observeTopics(MemoryScope scope, MemoryConfig cfg, String modelId, List<String> topics, MemoryRunBudget budget) {
        return insightOps.observeTopics(scope, cfg, modelId, topics, budget);
    }

    /** 实现随协作者。 */
    void tombstoneEverything(MemoryScope scope) {
        catalogOps.tombstoneEverything(scope);
    }

    /** 实现随协作者。 */
    void renameInterestItem(MemoryScope scope, String oldLabel, String newLabel) {
        catalogOps.renameInterestItem(scope, oldLabel, newLabel);
    }

    /** 实现随协作者。 */
    int topicAliasCount(MemoryScope scope, String key) {
        return catalogOps.topicAliasCount(scope, key);
    }

    /** 实现随协作者。 */
    void invalidateInterestEmbedding(MemoryScope scope, String topic) {
        catalogOps.invalidateInterestEmbedding(scope, topic);
    }

    /** 实现随协作者。 */
    String[] renameTopic(MemoryScope scope, MemoryTopicStat stat, String newLabel, String currentKey) {
        return catalogOps.renameTopic(scope, stat, newLabel, currentKey);
    }

    /** 实现随协作者。 */
    MemoryTopicStat unpromotedTopic(MemoryScope scope, String id) {
        return catalogOps.unpromotedTopic(scope, id);
    }

    /** 实现随协作者。 */
    List<String> topDocumentTitles(MemoryScope scope) {
        return insightOps.topDocumentTitles(scope);
    }

    // ── 记忆管理器/检索侧：实现随协作者（MemoryCatalogOps / MemoryInsightOps） ──

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public PageResult<MemoryItem> listItems(String status, int limit, int offset) {
        return catalogOps.listItems(status, limit, offset);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public PageResult<MemoryTopicView> listTopics(int limit, int offset) {
        return catalogOps.listTopics(limit, offset);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public MemoryItem promoteTopic(String id) {
        return catalogOps.promoteTopic(id);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public void deleteTopic(String id) {
        catalogOps.deleteTopic(id);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public PageResult<MemoryDocView> listDocuments(int limit, int offset) {
        return catalogOps.listDocuments(limit, offset);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public void deleteDocument(String id) {
        catalogOps.deleteDocument(id);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public List<String> familiarKnowledgeIds() {
        return catalogOps.familiarKnowledgeIds();
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public MemoryItem createItem(String kind, String content, int importance) {
        return catalogOps.createItem(kind, content, importance);
    }

    // ── 控制面读模型（控制器不再 import MemoryItem） ─────────────────────

    public PageResult<MemoryItemResponse> listItemViews(String status, int limit, int offset) {
        PageResult<MemoryItem> page = listItems(status, limit, offset);
        return PageResult.of(
                page.items().stream().map(MemoryItemResponse::from).toList(), page.total());
    }

    public MemoryItemResponse createItemView(String kind, String content, int importance) {
        return MemoryItemResponse.from(createItem(kind, content, importance));
    }

    public MemoryItemResponse updateItemView(String id, String content, int importance) {
        return MemoryItemResponse.from(updateItem(id, content, importance));
    }

    public MemoryItemResponse confirmItemView(String id) {
        return MemoryItemResponse.from(confirmItem(id));
    }

    public MemoryItemResponse promoteTopicView(String id) {
        return MemoryItemResponse.from(promoteTopic(id));
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public MemoryItem updateItem(String id, String content, int importance) {
        return catalogOps.updateItem(id, content, importance);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public void deleteItem(String id) {
        catalogOps.deleteItem(id);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public long clear() {
        return catalogOps.clear();
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public MemorySettings getSettings() {
        return catalogOps.getSettings();
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public void setEnabled(boolean enabled) {
        catalogOps.setEnabled(enabled);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public MemoryItem confirmItem(String id) {
        return catalogOps.confirmItem(id);
    }

    /** 实现随协作者 {@link MemoryCatalogOps}。 */
        public void rejectItem(String id) {
        catalogOps.rejectItem(id);
    }

    /** 实现随协作者 {@link MemoryInsightOps}。 */
        public MemoryRetrievalContext retrievalContextFor() {
        return insightOps.retrievalContextFor();
    }

    /** 实现随协作者 {@link MemoryInsightOps}。 */
        public Map<String, Integer> documentAffinity(List<String> knowledgeIds) {
        return insightOps.documentAffinity(knowledgeIds);
    }

    /** 实现随协作者 {@link MemoryInsightOps}。 */
        public void recordAnswerSources(List<MemoryDocAffinity> refs) {
        insightOps.recordAnswerSources(refs);
    }

    /** 实现随协作者 {@link MemoryInsightOps}。 */
        public List<String> observeQuestionTopics(List<String> topics) {
        return insightOps.observeQuestionTopics(topics);
    }

    /** 实现随协作者 {@link MemoryInsightOps}。 */
        public MemorySearchResult searchMemory(String query, int limit) {
        return insightOps.searchMemory(query, limit);
    }

    // 开关：工作区配置与三层判定

    /**
     * 读工作区的记忆开关。
     *
     * <p>租户不存在或那一列没配时返回**零值配置**（{@code enabled=false}，
     * 且**不做 normalize**）——也就是"关着"。这个细节有后果：{@code write_mode}
     * 会是空串而不是 {@code explicit_only}，只有 {@link #getSettings} 会去补它。</p>
     */
    MemoryConfig workspaceConfig(long tenantId) {
        return scopeGate.workspaceConfig(tenantId);
    }

    /**
     * 解析作用域并检查每一层开关。
     *
     * <p>第三个分量在"不许用记忆"时恒为 false，读路径把它当"没有记忆"而不是失败；
     * 第四个分量是禁用原因，与判定来自同一次求值（见 {@link MemoryScopeGate}）。</p>
     */
    record ScopeState(MemoryScope scope, MemoryConfig cfg, boolean ok, String reason) {
        ScopeState(MemoryScope scope, MemoryConfig cfg, boolean ok) {
            this(scope, cfg, ok, "");
        }
    }

    ScopeState enabledScope() {
        return scopeGate.evaluate();
    }

    /** 解释这次请求为什么没有记忆；与 {@link #enabledScope()} 同一次求值。 */
    String scopeDisableReason() {
        return scopeGate.evaluate().reason();
    }

    /**
     * 这次请求能不能读记忆。
     *
     * <p>它刻意就是 {@code SearchMemory} 自己用的那个判定，而不是把三个开关再读一遍。
     * 决定"要不要提供一个记忆功能"的调用方与"真用起来时回答"的代码，
     * 绝不能对"记忆开没开"有分歧。</p>
     */
    public boolean memoryAvailable() {
        return enabledScope().ok();
    }

    // 召回


    /**
     * 装配一轮要注入的记忆。
     *
     * <p>它**永不调用模型、永不返回错误**：记忆是增强，任何失败都必须退化成一个普通回答，
     * 而不是一次失败的请求。实现随协作者 {@link MemoryRecallOps}。</p>
     */
    public MemoryRecall recall(String query) {
        return recallOps.recall(query);
    }

    /**
 * 记下使用情况，但不给请求的关键路径增加一次写。
 *
 * <p>它跑在异步线程上，HTTP handler 返回之后仍然活着。
 * 注意它<b>只</b>用显式传进去的 scope，
 * 不读 {@code TenantContext}——跨线程读 ThreadLocal 正是 禁止的。</p>
 */
    void touchAsync(MemoryScope scope, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(items.size());
        for (MemoryItem item : items) {
            if (item != null) {
                ids.add(item.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Thread.ofVirtual().name("memory-touch").start(() -> {
            try {
                repo.touchUsed(scope, ids);
            } catch (RuntimeException e) {
                log.warn("memory: touch used failed", e);
            }
        });
    }

    // 写入路径（唯一的插入口）

    /**
     * 存下一条陈述，并与同一主题上已知的东西解矛盾。
     */
    public MemoryItem remember(MemoryItem item) {
        ScopeState state = enabledScope();
        if (!state.ok()) {
            throw new MemoryScopeExceptions.Disabled();
        }
        return write(state.scope(), state.cfg(), item);
    }

    /** **唯一的插入路径**；实现见 {@link MemoryWritePipeline}。 */
    MemoryItem write(MemoryScope scope, MemoryConfig cfg, MemoryItem item) {
        return writePipeline.write(scope, cfg, item);
    }

    /** 写入并可选替换；顺序语义见 {@link MemoryWritePipeline#writeReplacing}。 */
    MemoryItem writeReplacing(MemoryScope scope, MemoryConfig cfg, MemoryItem item, String targetId) {
        return writePipeline.writeReplacing(scope, cfg, item, targetId);
    }

    /** 立刻生效还是等确认；规则见 {@link MemoryWritePipeline#statusForWrite}。 */
    static String statusForWrite(MemoryItem item) {
        return MemoryWritePipeline.statusForWrite(item);
    }

    /** 容量维护；实现见 {@link MemoryWritePipeline#enforceCapacity}。 */
    void enforceCapacity(MemoryScope scope, MemoryConfig cfg) {
        writePipeline.enforceCapacity(scope, cfg);
    }

    /** 重建常驻块；实现见 {@link MemoryWritePipeline#rebuildBlock}。 */
    void rebuildBlock(MemoryScope scope) {
        writePipeline.rebuildBlock(scope);
    }

    /** 抽取模型解析；回落规则见 {@link MemoryExtractionModelResolver}。 */
    String extractionModelId(MemoryConfig cfg, MemoryExtractPayload payload) {
        return extractionModelResolver.extractionModelId(cfg, payload);
    }

    /** 工作区问答模型回落；实现见 {@link MemoryExtractionModelResolver}。 */
    String workspaceChatModelId() {
        return extractionModelResolver.workspaceChatModelId();
    }

    // ── 供兄弟类使用的小访问器 ───────────────────────────────────────────

    MemoryRepository repo() {
        return repo;
    }

    MemoryVectorService vectorService() {
        return vectorService;
    }

    MemoryModelResolver modelResolver() {
        return modelResolver;
    }

    /** 当前时间（抽取段的截止时间等）。 */
    static OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    /** 时间是否为零值，供兄弟类统一口径。 */
    static boolean isZeroTime(OffsetDateTime t) {
        return ZeroTimeSerializer.isZeroValue(t);
    }

}
