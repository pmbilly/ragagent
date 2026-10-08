package com.ragagent.common.knowledge;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 文本 chunk 只读视图（B98/C2）。
 *
 * <p>字段取 wiki 侧实际读取的投影（正文重建、引用分批、图片富化、清理）——
 * 排序/重叠去重仍由 wiki 的 {@code WikiChunkMerge} 负责。</p>
 *
 * <p><b>可写说明</b>：DTO——{@code knowledge} 侧适配器填充、测试夹具构造；
 * 业务调用方应只读使用。</p>
 */
public final class ChunkView {

    private String id;
    private long tenantId;
    private String knowledgeId;
    private String knowledgeBaseId;
    private String content;
    private String chunkType;
    private int chunkIndex;
    private int startAt;
    private int endAt;
    private String parentChunkId;
    private String imageInfo;
    private JsonNode metadata;

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

    public String getKnowledgeId() {
        return knowledgeId;
    }

    public void setKnowledgeId(String knowledgeId) {
        this.knowledgeId = knowledgeId;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChunkType() {
        return chunkType;
    }

    public void setChunkType(String chunkType) {
        this.chunkType = chunkType;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getStartAt() {
        return startAt;
    }

    public void setStartAt(int startAt) {
        this.startAt = startAt;
    }

    public int getEndAt() {
        return endAt;
    }

    public void setEndAt(int endAt) {
        this.endAt = endAt;
    }

    public String getParentChunkId() {
        return parentChunkId;
    }

    public void setParentChunkId(String parentChunkId) {
        this.parentChunkId = parentChunkId;
    }

    public String getImageInfo() {
        return imageInfo;
    }

    public void setImageInfo(String imageInfo) {
        this.imageInfo = imageInfo;
    }

    public JsonNode getMetadata() {
        return metadata;
    }

    public void setMetadata(JsonNode metadata) {
        this.metadata = metadata;
    }
}
