package com.ragagent.common.knowledge;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 知识库只读视图（B98/C2：由 {@code KnowledgeBaseLookup} 内嵌类型提升为顶层类型，
 * 便于 wiki 侧 20+ 调用点直接 import）。
 *
 * <p><b>为什么可写</b>：本视图是 DTO——{@code knowledge} 侧适配器要填充、测试夹具要构造
 * （它们原先直接 new 实体再 set）。业务调用方（wiki 等）应当<b>只读</b>使用。</p>
 */
public final class KnowledgeBaseView {

    private String id;
    private long tenantId;
    private String name;
    private String type;
    private String description;
    private String creatorId;
    private String summaryModelId;
    private String embeddingModelId;
    private boolean wikiEnabled;
    private JsonNode wikiConfig;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTenantId() {
        return tenantId;
    }

    public void setTenantId(long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public String getSummaryModelId() {
        return summaryModelId;
    }

    public void setSummaryModelId(String summaryModelId) {
        this.summaryModelId = summaryModelId;
    }

    public String getEmbeddingModelId() {
        return embeddingModelId;
    }

    public void setEmbeddingModelId(String embeddingModelId) {
        this.embeddingModelId = embeddingModelId;
    }

    /** {@code indexing_strategy.isWikiEnabled()} 的等价投影。 */
    public boolean isWikiEnabled() {
        return wikiEnabled;
    }

    public void setWikiEnabled(boolean wikiEnabled) {
        this.wikiEnabled = wikiEnabled;
    }

    public JsonNode getWikiConfig() {
        return wikiConfig;
    }

    public void setWikiConfig(JsonNode wikiConfig) {
        this.wikiConfig = wikiConfig;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
