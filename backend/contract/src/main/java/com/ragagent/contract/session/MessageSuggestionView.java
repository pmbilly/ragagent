package com.ragagent.contract.session;

import java.time.OffsetDateTime;
import java.util.List;


/**
 * 推荐问题集的**线格式视图**（embed 公开面响应形状）。
 *
 * <p><b>为什么有它</b>：实体 {@code MessageSuggestionSet} 属会话域内部（HANDOFF 判据 1），
 * 而 embed 面端口 {@code EmbedSessionPort} 需要把该形状作为响应体返回。本视图字段**逐一对应
 * 实体参与 JSON 的字段**（实体的 {@code @JsonIgnore} 字段 {@code agentTenantId}/{@code leaseUntil} 不迁），
 * 且 {@code questions} 复用同一 {@link SuggestionItem} 类型 ⇒ **JSON 逐字节等价**
 * （由 {@code emb-pub-suggestions-get.json} 金片钉住）。</p>
 *
 * <p><b>投影归属</b>：实体 → 视图的映射吃实体，故留在**会话域**（{@code session/api/EmbedSessionAdapter}）；
 * 本类只是可写数据持有者（与 {@code contract.knowledge.ChunkView} 同款）。</p>
 */
public class MessageSuggestionView {

    private String id;
    private Long tenantId;
    private String sessionId;
    private String assistantMessageId;
    private String agentId;
    private String placement;
    private String configHash;
    private String locale;
    private String status;
    private boolean allowRegenerate;
    private String suppressionReason;
    private List<SuggestionItem> questions;
    private String modelId;
    private int promptTokens;
    private int completionTokens;
    private long latencyMs;
    private String errorCode;
    private OffsetDateTime generatedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;


    public void setId(String id) {
        this.id = id;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setAssistantMessageId(String assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public void setPlacement(String placement) {
        this.placement = placement;
    }

    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setAllowRegenerate(boolean allowRegenerate) {
        this.allowRegenerate = allowRegenerate;
    }

    public void setSuppressionReason(String suppressionReason) {
        this.suppressionReason = suppressionReason;
    }

    public void setQuestions(List<SuggestionItem> questions) {
        this.questions = questions;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getAssistantMessageId() {
        return assistantMessageId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getPlacement() {
        return placement;
    }

    public String getConfigHash() {
        return configHash;
    }

    public String getLocale() {
        return locale;
    }

    public String getStatus() {
        return status;
    }

    public boolean isAllowRegenerate() {
        return allowRegenerate;
    }

    public String getSuppressionReason() {
        return suppressionReason;
    }

    public List<SuggestionItem> getQuestions() {
        return questions;
    }

    public String getModelId() {
        return modelId;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
