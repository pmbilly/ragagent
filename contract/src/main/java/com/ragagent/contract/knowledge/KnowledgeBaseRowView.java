package com.ragagent.contract.knowledge;

import java.time.OffsetDateTime;

import com.ragagent.contract.knowledge.domain.KnowledgeBaseStorageProviderConfig;
import com.ragagent.contract.knowledge.domain.KnowledgeBaseStorageConfig;
import com.ragagent.contract.knowledge.domain.KnowledgeBaseAsrConfig;
import com.ragagent.contract.knowledge.domain.KnowledgeBaseVlmConfig;
import com.ragagent.contract.knowledge.domain.KnowledgeBaseIndexingStrategy;
import com.ragagent.contract.knowledge.domain.KnowledgeBaseImageProcessingConfig;
import com.ragagent.contract.knowledge.domain.KnowledgeBaseChunkingConfig;

/**
 * 知识库**行视图**（L1）：{@code KnowledgeCatalogPort} 知识库面（读 / 建 / 改 / 授权）+ KB API 线格式
 * （{@code KnowledgeBaseResponse.from}）的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code KnowledgeBase} 已归位 `knowledge/domain`（HANDOFF §12.0 判据 1）；
 * 跨域方（数据源 7 处、初始化 1 处、评估 1 处、会话 7 处）与 L1 的 {@code KnowledgeBaseResponse}/
 * {@code KnowledgeBaseViews} 只应经本视图。</p>
 *
 * <p><b>口径</b>：镜像实体全部 **34 字段**，访问器体与实体**逐字一致**（含归一语义）；字段序与实体一致
 * ⇒ 序列化等价（KB API 由 {@code kb-*.json} / {@code init-*.json} 金片钉住）。</p>
 *
 * <p><b>落点登记（HANDOFF §12.5）</b>：含 5 个 {@code JsonNode} 配置字段 + 6 个 typed config ⇒
 * `:contract` 零依赖装不下 ⇒ 留 L1，登记**待去 Jackson**。</p>
 */
public class KnowledgeBaseRowView {

    private String id;
    private String name = "";
    private String type = "";
    private boolean isTemporary;
    private String description;
    private Long tenantId;
    private String creatorId;
    private KnowledgeBaseChunkingConfig chunkingConfig;
    private KnowledgeBaseImageProcessingConfig imageProcessingConfig;
    private String embeddingModelId;
    private String summaryModelId;
    private KnowledgeBaseVlmConfig vlmConfig;
    private KnowledgeBaseAsrConfig asrConfig;
    private KnowledgeBaseStorageProviderConfig storageProviderConfig;
    private String storageBackendId;
    private KnowledgeBaseStorageConfig storageConfig;
    private String vectorStoreId;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object extractConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object faqConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object questionGenerationConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object autoTagConfig;
    /** jsonb 原样（对象/数组/标量；null 表示无）。 */
    private Object wikiConfig;
    private KnowledgeBaseIndexingStrategy indexingStrategy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
    private boolean isPinned;
    private OffsetDateTime pinnedAt;
    private long knowledgeCount;
    private long chunkCount;
    private boolean isProcessing;
    private long processingCount;
    private long shareCount;
    private String creatorName;

    public String getId() { return id; }
    public void setId(String v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v == null ? "" : v; }
    public String getType() { return type; }
    public void setType(String v) { type = v == null ? "" : v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { tenantId = v; }
    public String getCreatorId() { return creatorId == null ? "" : creatorId; }
    public void setCreatorId(String v) { creatorId = v == null ? "" : v; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String v) { embeddingModelId = v == null ? "" : v; }
    public String getSummaryModelId() { return summaryModelId; }
    public void setSummaryModelId(String v) { summaryModelId = v == null ? "" : v; }
    public KnowledgeBaseStorageProviderConfig getStorageProviderConfig() { return storageProviderConfig; }
    public void setStorageProviderConfig(KnowledgeBaseStorageProviderConfig v) { storageProviderConfig = v; }
    public String getStorageBackendId() { return storageBackendId; }
    public void setStorageBackendId(String v) { storageBackendId = v; }
    public String getVectorStoreId() { return vectorStoreId; }
    public void setVectorStoreId(String v) { vectorStoreId = v; }
    public Object getExtractConfig() { return extractConfig; }
    public void setExtractConfig(Object v) { extractConfig = v; }
    public Object getFaqConfig() { return faqConfig; }
    public void setFaqConfig(Object v) { faqConfig = v; }
    public Object getQuestionGenerationConfig() { return questionGenerationConfig; }
    public void setQuestionGenerationConfig(Object v) { questionGenerationConfig = v; }
    public Object getAutoTagConfig() { return autoTagConfig; }
    public void setAutoTagConfig(Object v) { autoTagConfig = v; }
    public Object getWikiConfig() { return wikiConfig; }
    public void setWikiConfig(Object v) { wikiConfig = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
    public OffsetDateTime getPinnedAt() { return pinnedAt; }
    public void setPinnedAt(OffsetDateTime v) { pinnedAt = v; }
    public long getKnowledgeCount() { return knowledgeCount; }
    public void setKnowledgeCount(long v) { knowledgeCount = v; }
    public long getChunkCount() { return chunkCount; }
    public void setChunkCount(long v) { chunkCount = v; }
    public long getProcessingCount() { return processingCount; }
    public void setProcessingCount(long v) { processingCount = v; }
    public long getShareCount() { return shareCount; }
    public void setShareCount(long v) { shareCount = v; }
    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String v) { creatorName = v; }

    public boolean hasVectorStore() {
        return vectorStoreId != null && !vectorStoreId.isEmpty();
    }

    public Capabilities capabilities() {
        KnowledgeBaseIndexingStrategy s = getIndexingStrategy();
        return new Capabilities(s.isVectorEnabled(), s.isKeywordEnabled(), s.isWikiEnabled(),
                s.isGraphEnabled() && JsonValueSupport.boolAt(extractConfig, "enabled", false),
                "faq".equals(type));
    }

    public String getStorageProvider() {
        if (storageProviderConfig != null) {
            String p = storageProviderConfig.getProvider().toLowerCase().trim();
            if (!p.isEmpty() && !p.equals("__pending_env__")) {
                return p;
            }
        }
        return getStorageConfig().getProvider().toLowerCase().trim();
    }

    public record Capabilities(boolean vector, boolean keyword, boolean wiki, boolean graph, boolean faq) {}

    public boolean isIsTemporary() { return isTemporary; }

    public void setIsTemporary(boolean v) { isTemporary = v; }


    public KnowledgeBaseChunkingConfig getChunkingConfig() {
        if (chunkingConfig == null) chunkingConfig = new KnowledgeBaseChunkingConfig();
        return chunkingConfig;
    }

    public void setChunkingConfig(KnowledgeBaseChunkingConfig v) { chunkingConfig = v; }

    public KnowledgeBaseImageProcessingConfig getImageProcessingConfig() {
        if (imageProcessingConfig == null) imageProcessingConfig = new KnowledgeBaseImageProcessingConfig();
        return imageProcessingConfig;
    }

    public void setImageProcessingConfig(KnowledgeBaseImageProcessingConfig v) { imageProcessingConfig = v; }

    public KnowledgeBaseVlmConfig getVlmConfig() {
        if (vlmConfig == null) vlmConfig = new KnowledgeBaseVlmConfig();
        return vlmConfig;
    }

    public void setVlmConfig(KnowledgeBaseVlmConfig v) { vlmConfig = v; }

    public KnowledgeBaseAsrConfig getAsrConfig() {
        if (asrConfig == null) asrConfig = new KnowledgeBaseAsrConfig();
        return asrConfig;
    }

    public void setAsrConfig(KnowledgeBaseAsrConfig v) { asrConfig = v; }

    public void setStorageConfig(KnowledgeBaseStorageConfig v) { storageConfig = v; }

    public void setIndexingStrategy(KnowledgeBaseIndexingStrategy v) { indexingStrategy = v; }

    public boolean isIsPinned() { return isPinned; }

    public void setIsPinned(boolean v) { isPinned = v; }


    public boolean isIsProcessing() { return isProcessing; }

    public void setIsProcessing(boolean v) { isProcessing = v; }

    public KnowledgeBaseStorageConfig getStorageConfig() {
        if (storageConfig == null) storageConfig = new KnowledgeBaseStorageConfig();
        return storageConfig;
    }

    public KnowledgeBaseIndexingStrategy getIndexingStrategy() {
        // NULL→Default 分支实际到不了）；IsZero→Default 只发生在 service 读路径的
        // EnsureDefaults 调用点（KB list/get），chunk 等路径不做此默认。
        // 历史近似（null→Default）与既有契约样例全兼容，仅补一处 carve-out：
        // faq 且 faq_config 为 NULL → EnsureDefaults 提前 return，策略保持零值。
        if (indexingStrategy != null) {
            return indexingStrategy;
        }
        return "faq".equals(type) && faqConfig == null
                ? new KnowledgeBaseIndexingStrategy() : KnowledgeBaseIndexingStrategy.defaultStrategy();
    }

    public void setStorageProvider(String provider) {
        KnowledgeBaseStorageProviderConfig c = new KnowledgeBaseStorageProviderConfig();
        c.setProvider(provider);
        this.storageProviderConfig = c;
    }
}
