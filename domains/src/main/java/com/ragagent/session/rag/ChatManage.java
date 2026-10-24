package com.ragagent.session.rag;

import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.contract.session.PipelineMessageAttachmentView;
import com.ragagent.session.dto.PipelineUsedMemoryView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一次 chat 管线执行的全部配置、状态与运行时句柄，按请求段、状态段、运行时段三部分组织。
 *
 * <h2>字段分区</h2>
 * <ul>
 *   <li><b>请求段（不可变配置）</b>：query/sessionId/userId/maxRounds、检索参数、重排参数、
 *       模型参数、改写开关、FAQ 策略、多模态/附件、web 检索开关、summaryConfig。</li>
 *   <li><b>状态段（插件间流转）</b>：history、usedMemories；检索产出见
 *       {@link SearchOutcome}，实体见 {@link EntityContext}，查询计划见
 *       {@link QueryPlan}，答复组见 {@link AnswerState}，重排结果见
 *       {@link RerankOutcome}，合并结果见 {@link MergedContext}。</li>
 *   <li><b>运行时段</b>：见 {@link TurnRuntime}（事件总线 + 消息 id），不在本类字段里。</li>
 * </ul>
 *
 * <h2>{@link #cloneChatManage()} 的复制面</h2>
 * <p>请求段全量复制（SearchTarget 不可变，列表换新）；状态段<b>只复制</b> 查询计划整组/
 * 实体上下文/usedMemories/renderedContexts——<b>history、检索产出、合并结果、
 * userContent、chatResponse、重排结果以及运行时段句柄都不复制</b>。</p>
 *
 * <p>本类型不落 jsonb 也不作响应体，不进 JsonContractRoundTripTest。</p>
 */
public final class ChatManage implements PipelineState {

    /** setup 结束时冻结的请求段；fork 共享同一份。 */
    private TurnInputSnapshot frozen;

    // ── 请求段（不可变配置） ──

    private String query = "";
    private String sessionId = "";
    private String userId = "";
    private int maxRounds;

    private List<String> knowledgeBaseIds = new ArrayList<>();
    private List<String> knowledgeIds = new ArrayList<>();
    private List<SearchTarget> searchTargets = new ArrayList<>();
    private double vectorThreshold;
    private double keywordThreshold;
    private int embeddingTopK;
    private String vectorDatabase = "";

    private String rerankModelId = "";
    private int rerankTopK;

    private String chatModelId = "";
    private SummaryConfig summaryConfig = new SummaryConfig();
    private String fallbackStrategy = "";
    private String fallbackResponse = "";
    private String fallbackPrompt = "";
    /** 只控制最终引用标注；null = 请求未指定（默认 true）。 */
    private Boolean citationEnabled;

    private boolean enableRewrite;
    private boolean enableQueryExpansion;
    private String rewritePromptSystem = "";
    private String rewritePromptUser = "";
    private String queryUnderstandModelId = "";

    private boolean faqPriorityEnabled;
    private double faqDirectAnswerThreshold;
    private double faqScoreBoost;

    private boolean dataAnalysisEnabled;

    private List<String> images;
    private String vlmModelId = "";
    private boolean chatModelSupportsVision;

    private List<PipelineMessageAttachmentView> attachments;
    /** intent → 覆写系统提示词（agent 级；空白值回落全局默认）。 */
    private Map<String, String> intentPromptOverrides;

    private long tenantId;
    private boolean webSearchEnabled;
    private String webSearchProviderId = "";
    private int webSearchMaxResults;
    private boolean webFetchEnabled;
    private int webFetchTopN;
    private String language = "";

    // ── 状态段 ──

    /** 查询计划（改写/意图/图像/引用/提示词覆盖/记忆段落）。 */
    private final QueryPlan queryPlan = new QueryPlan();
    private List<History> history;

    /** 检索产出（结果集 + 实体图）。 */
    private final SearchOutcome searchOutcome = new SearchOutcome();
    /** 重排结果（CHUNK_RERANK 写，下游只读）。 */
    private final RerankOutcome rerank = new RerankOutcome();
    /** 合并结果（CHUNK_MERGE 写，DATA_ANALYSIS 读写）。 */
    private final MergedContext merged = new MergedContext();
    /** 实体上下文（实体/所属 KB/实体知识映射）。 */
    private final EntityContext entityContext = new EntityContext();

    /** 答复组（用户消息 / 渲染上下文 / 模型回复）。 */
    private final AnswerState answer = new AnswerState();

    private List<PipelineUsedMemoryView> usedMemories;

    // ── 运行时段 ──

    /** 运行时段（事件总线 + 消息 id）；逐执行句柄，不参与 cloneChatManage。 */
    private final TurnRuntime runtime = new TurnRuntime();

    // ── 请求段访问器 ──

    public String getQuery() { return frozen != null ? frozen.query() : query; }
    public void setQuery(String v) { ensureMutable(); query = v == null ? "" : v; }
    public String getSessionId() { return frozen != null ? frozen.sessionId() : sessionId; }
    public void setSessionId(String v) { ensureMutable(); sessionId = v == null ? "" : v; }
    public String getUserId() { return frozen != null ? frozen.userId() : userId; }
    public void setUserId(String v) { ensureMutable(); userId = v == null ? "" : v; }
    public int getMaxRounds() { return frozen != null ? frozen.maxRounds() : maxRounds; }
    public void setMaxRounds(int v) { ensureMutable(); maxRounds = v; }

    public List<String> getKnowledgeBaseIds() { return frozen != null ? frozen.knowledgeBaseIds() : knowledgeBaseIds; }
    public void setKnowledgeBaseIds(List<String> v) { ensureMutable(); knowledgeBaseIds = v == null ? new ArrayList<>() : v; }
    public List<String> getKnowledgeIds() { return frozen != null ? frozen.knowledgeIds() : knowledgeIds; }
    public void setKnowledgeIds(List<String> v) { ensureMutable(); knowledgeIds = v == null ? new ArrayList<>() : v; }
    public List<SearchTarget> getSearchTargets() { return frozen != null ? frozen.searchTargets() : searchTargets; }
    public void setSearchTargets(List<SearchTarget> v) { ensureMutable(); searchTargets = v == null ? new ArrayList<>() : v; }
    public double getVectorThreshold() { return frozen != null ? frozen.vectorThreshold() : vectorThreshold; }
    public void setVectorThreshold(double v) { ensureMutable(); vectorThreshold = v; }
    public double getKeywordThreshold() { return frozen != null ? frozen.keywordThreshold() : keywordThreshold; }
    public void setKeywordThreshold(double v) { ensureMutable(); keywordThreshold = v; }
    public int getEmbeddingTopK() { return frozen != null ? frozen.embeddingTopK() : embeddingTopK; }
    public void setEmbeddingTopK(int v) { ensureMutable(); embeddingTopK = v; }
    public String getVectorDatabase() { return frozen != null ? frozen.vectorDatabase() : vectorDatabase; }
    public void setVectorDatabase(String v) { ensureMutable(); vectorDatabase = v == null ? "" : v; }

    public String getRerankModelId() { return frozen != null ? frozen.rerankModelId() : rerankModelId; }
    public void setRerankModelId(String v) { ensureMutable(); rerankModelId = v == null ? "" : v; }
    public int getRerankTopK() { return frozen != null ? frozen.rerankTopK() : rerankTopK; }
    public void setRerankTopK(int v) { ensureMutable(); rerankTopK = v; }
    public double getRerankThreshold() { return rerank.getThreshold(); }
    public void setRerankThreshold(double v) { rerank.setThreshold(v); }

    public String getChatModelId() { return frozen != null ? frozen.chatModelId() : chatModelId; }
    public void setChatModelId(String v) { ensureMutable(); chatModelId = v == null ? "" : v; }
    public SummaryConfig getSummaryConfig() { return frozen != null ? frozen.summaryConfig() : summaryConfig; }
    public void setSummaryConfig(SummaryConfig v) { ensureMutable(); summaryConfig = v == null ? new SummaryConfig() : v; }
    public String getFallbackStrategy() { return frozen != null ? frozen.fallbackStrategy() : fallbackStrategy; }
    public void setFallbackStrategy(String v) { ensureMutable(); fallbackStrategy = v == null ? "" : v; }
    public String getFallbackResponse() { return frozen != null ? frozen.fallbackResponse() : fallbackResponse; }
    public void setFallbackResponse(String v) { ensureMutable(); fallbackResponse = v == null ? "" : v; }
    public String getFallbackPrompt() { return frozen != null ? frozen.fallbackPrompt() : fallbackPrompt; }
    public void setFallbackPrompt(String v) { ensureMutable(); fallbackPrompt = v == null ? "" : v; }
    public Boolean getCitationEnabled() { return frozen != null ? frozen.citationEnabled() : citationEnabled; }
    public void setCitationEnabled(Boolean v) { ensureMutable(); citationEnabled = v; }

    public boolean isEnableRewrite() { return frozen != null ? frozen.enableRewrite() : enableRewrite; }
    public void setEnableRewrite(boolean v) { ensureMutable(); enableRewrite = v; }
    public boolean isEnableQueryExpansion() { return frozen != null ? frozen.enableQueryExpansion() : enableQueryExpansion; }
    public void setEnableQueryExpansion(boolean v) { ensureMutable(); enableQueryExpansion = v; }
    public String getRewritePromptSystem() { return frozen != null ? frozen.rewritePromptSystem() : rewritePromptSystem; }
    public void setRewritePromptSystem(String v) { ensureMutable(); rewritePromptSystem = v == null ? "" : v; }
    public String getRewritePromptUser() { return frozen != null ? frozen.rewritePromptUser() : rewritePromptUser; }
    public void setRewritePromptUser(String v) { ensureMutable(); rewritePromptUser = v == null ? "" : v; }
    public String getQueryUnderstandModelId() { return frozen != null ? frozen.queryUnderstandModelId() : queryUnderstandModelId; }
    public void setQueryUnderstandModelId(String v) { ensureMutable(); queryUnderstandModelId = v == null ? "" : v; }

    public boolean isFaqPriorityEnabled() { return frozen != null ? frozen.faqPriorityEnabled() : faqPriorityEnabled; }
    public void setFaqPriorityEnabled(boolean v) { ensureMutable(); faqPriorityEnabled = v; }
    public double getFaqDirectAnswerThreshold() { return frozen != null ? frozen.faqDirectAnswerThreshold() : faqDirectAnswerThreshold; }
    public void setFaqDirectAnswerThreshold(double v) { ensureMutable(); faqDirectAnswerThreshold = v; }
    public double getFaqScoreBoost() { return frozen != null ? frozen.faqScoreBoost() : faqScoreBoost; }
    public void setFaqScoreBoost(double v) { ensureMutable(); faqScoreBoost = v; }

    public boolean isDataAnalysisEnabled() { return frozen != null ? frozen.dataAnalysisEnabled() : dataAnalysisEnabled; }
    public void setDataAnalysisEnabled(boolean v) { ensureMutable(); dataAnalysisEnabled = v; }

    public List<String> getImages() { return frozen != null ? frozen.images() : images; }
    public void setImages(List<String> v) { ensureMutable(); images = v; }
    public String getVlmModelId() { return frozen != null ? frozen.vlmModelId() : vlmModelId; }
    public void setVlmModelId(String v) { ensureMutable(); vlmModelId = v == null ? "" : v; }
    public boolean isChatModelSupportsVision() { return frozen != null ? frozen.chatModelSupportsVision() : chatModelSupportsVision; }
    public void setChatModelSupportsVision(boolean v) { ensureMutable(); chatModelSupportsVision = v; }

    public List<PipelineMessageAttachmentView> getAttachments() { return frozen != null ? frozen.attachments() : attachments; }
    public void setAttachments(List<PipelineMessageAttachmentView> v) { ensureMutable(); attachments = v; }
    public Map<String, String> getIntentPromptOverrides() { return frozen != null ? frozen.intentPromptOverrides() : intentPromptOverrides; }
    public void setIntentPromptOverrides(Map<String, String> v) { ensureMutable(); intentPromptOverrides = v; }

    public long getTenantId() { return frozen != null ? frozen.tenantId() : tenantId; }
    public void setTenantId(long v) { ensureMutable(); tenantId = v; }
    public boolean isWebSearchEnabled() { return frozen != null ? frozen.webSearchEnabled() : webSearchEnabled; }
    public void setWebSearchEnabled(boolean v) { ensureMutable(); webSearchEnabled = v; }
    public String getWebSearchProviderId() { return frozen != null ? frozen.webSearchProviderId() : webSearchProviderId; }
    public void setWebSearchProviderId(String v) { ensureMutable(); webSearchProviderId = v == null ? "" : v; }
    public int getWebSearchMaxResults() { return frozen != null ? frozen.webSearchMaxResults() : webSearchMaxResults; }
    public void setWebSearchMaxResults(int v) { ensureMutable(); webSearchMaxResults = v; }
    public boolean isWebFetchEnabled() { return frozen != null ? frozen.webFetchEnabled() : webFetchEnabled; }
    public void setWebFetchEnabled(boolean v) { ensureMutable(); webFetchEnabled = v; }
    public int getWebFetchTopN() { return frozen != null ? frozen.webFetchTopN() : webFetchTopN; }
    public void setWebFetchTopN(int v) { ensureMutable(); webFetchTopN = v; }
    public String getLanguage() { return frozen != null ? frozen.language() : language; }
    public void setLanguage(String v) { ensureMutable(); language = v == null ? "" : v; }

    // ── 状态段访问器 ──

    public QueryPlan getQueryPlan() { return queryPlan; }
    public List<History> getHistory() { return history; }
    public void setHistory(List<History> v) { history = v; }

    public SearchOutcome getSearchOutcome() { return searchOutcome; }
    public RerankOutcome getRerank() { return rerank; }
    public MergedContext getMerged() { return merged; }
    public EntityContext getEntityContext() { return entityContext; }

    public AnswerState getAnswer() { return answer; }

    public List<PipelineUsedMemoryView> getUsedMemories() { return usedMemories; }
    public void setUsedMemories(List<PipelineUsedMemoryView> v) { usedMemories = v; }

    // ── 运行时段访问器 ──

    public TurnRuntime getRuntime() { return runtime; }

    @Override
    public PipelineState fork() {
        return cloneChatManage();
    }

    // ── 行为 ──

    /**
     * 当前执行是否要跑检索阶段。
     * web_search 意图只有开 web 检索才需要；其余委托给 intent 判定。
     */
    public boolean needsRetrieval() {
        if (QueryIntent.WEB_SEARCH.equals(queryPlan.getIntent())) {
            return isWebSearchEnabled();
        }
        return QueryIntent.needsKbRetrieval(queryPlan.getIntent());
    }

    /** 未指定（null）时默认 true。 */
    public boolean citationsEnabled() {
        Boolean enabled = getCitationEnabled();
        return enabled == null || enabled;
    }

    /** setup 结束时冻结请求段；之后请求段 setter 抛异常。 */
    public ChatManage freeze() {
        if (frozen == null) {
            frozen = snapshotInput();
        }
        return this;
    }

    private TurnInputSnapshot snapshotInput() {
        return new TurnInputSnapshot(
                query, sessionId, userId, maxRounds,
                knowledgeBaseIds, knowledgeIds, searchTargets,
                vectorThreshold, keywordThreshold, embeddingTopK, vectorDatabase,
                rerankModelId, rerankTopK,
                chatModelId, summaryConfig, fallbackStrategy, fallbackResponse, fallbackPrompt,
                citationEnabled, enableRewrite, enableQueryExpansion, rewritePromptSystem,
                rewritePromptUser, queryUnderstandModelId, faqPriorityEnabled,
                faqDirectAnswerThreshold, faqScoreBoost, dataAnalysisEnabled,
                images, vlmModelId, chatModelSupportsVision, attachments,
                intentPromptOverrides, tenantId, webSearchEnabled, webSearchProviderId,
                webSearchMaxResults, webFetchEnabled, webFetchTopN, language);
    }

    private void ensureMutable() {
        if (frozen != null) {
            throw new IllegalStateException("request segment is frozen");
        }
    }

    /**
     * 并发分支副本：共享不可变请求段，只复制状态段列出的字段；运行时段不复制。
     */
    public ChatManage cloneChatManage() {
        ChatManage c = new ChatManage();
        c.frozen = frozen != null ? frozen : snapshotInput();
        c.rerank.setThreshold(rerank.getThreshold());
        // 状态段只复制复制面列出的字段；history/结果集/userContent 等不复制
        c.queryPlan.setRewriteQuery(queryPlan.getRewriteQuery());
        c.queryPlan.setIntent(queryPlan.getIntent());
        c.queryPlan.setImageDescription(queryPlan.getImageDescription());
        c.queryPlan.setQuotedContext(queryPlan.getQuotedContext());
        c.queryPlan.setSystemPromptOverride(queryPlan.getSystemPromptOverride());
        c.queryPlan.setMemoryPrompt(queryPlan.getMemoryPrompt());
        c.usedMemories = usedMemories == null ? null : new ArrayList<>(usedMemories);
        c.answer.setRenderedContexts(answer.getRenderedContexts());
        c.entityContext.copyFrom(entityContext);
        return c;
    }
}
