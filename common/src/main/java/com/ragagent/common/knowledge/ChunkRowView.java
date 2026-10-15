package com.ragagent.common.knowledge;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 知识分块**行视图**（L1）：{@code KnowledgeCatalogPort} 分块面与 embed 公开分块响应（{@code /chunk}）的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code Chunk} 已归位 `knowledge/domain`（HANDOFF §12.0 判据 1）；
 * 跨域方（embedchannel 的公开读、会话的 agent 工具面、chatpipeline 的投影）只应经本视图。</p>
 *
 * <p><b>⭐ JSON 等价口径（金片 {@code emb-pub-chunk-ok.json} 钉住）</b>：本视图＝实体**参与序列化的字段**
 * 之镜像（**跳过 2 个 {@code @JsonIgnore} 字段**：{@code sourceContent}/{@code contextHeader}），
 * 且**字段序与实体一致**；视图无 Jackson 注解需求 ⇒ 序列化形态**逐字节等价**。</p>
 *
 * <p><b>落点登记（HANDOFF §12.5）</b>：含 3 个 {@code JsonNode}（{@code relationChunks}/
 * {@code indirectRelationChunks}/{@code metadata}）⇒ `:contract` 零依赖装不下 ⇒ 留 L1，
 * 登记**待去 Jackson**（去 Jackson 后与端口分块面整批入 `contract.knowledge`）。</p>
 */
public class ChunkRowView {

    private String id;
    private Long seqId;
    private Long tenantId;
    private String knowledgeId;
    private String knowledgeBaseId;
    private String tagId;
    private String content;

    @JsonIgnore
    private String sourceContent;
    private int contentRevision;
    private String indexStatus = "ready";
    private String lastEditorId;
    private int chunkIndex;
    private boolean isEnabled = true;
    private int flags = 1;
    private int status;
    private int startAt;
    private int endAt;
    private String preChunkId;
    private String nextChunkId;
    private String chunkType = "text";
    private String parentChunkId;
    private JsonNode relationChunks;
    private JsonNode indirectRelationChunks;
    private JsonNode metadata;
    private String contentHash;
    private String imageInfo;

    @JsonIgnore
    private String contextHeader;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public String getId() { return id; }
    public Long getSeqId() { return seqId; }
    public Long getTenantId() { return tenantId; }
    public String getKnowledgeId() { return knowledgeId; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getTagId() { return tagId == null ? "" : tagId; }
    public String getContent() { return content; }
    public int getContentRevision() { return contentRevision; }
    public String getIndexStatus() { return indexStatus; }
    public String getLastEditorId() { return lastEditorId == null ? "" : lastEditorId; }
    public int getChunkIndex() { return chunkIndex; }
    public boolean isIsEnabled() { return isEnabled; }
    public int getFlags() { return flags; }
    public int getStatus() { return status; }
    public int getStartAt() { return startAt; }
    public int getEndAt() { return endAt; }
    public String getPreChunkId() { return preChunkId == null ? "" : preChunkId; }
    public String getNextChunkId() { return nextChunkId == null ? "" : nextChunkId; }
    public String getChunkType() { return chunkType; }
    public String getParentChunkId() { return parentChunkId == null ? "" : parentChunkId; }
    public JsonNode getRelationChunks() { return relationChunks; }
    public JsonNode getIndirectRelationChunks() { return indirectRelationChunks; }
    public JsonNode getMetadata() { return metadata; }
    public String getContentHash() { return contentHash == null ? "" : contentHash; }
    public String getImageInfo() { return imageInfo == null ? "" : imageInfo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setId(String v) { id = v; }
    public void setSeqId(Long v) { seqId = v; }
    public void setTenantId(Long v) { tenantId = v; }
    public void setKnowledgeId(String v) { knowledgeId = v; }
    public void setKnowledgeBaseId(String v) { knowledgeBaseId = v; }
    public void setTagId(String v) { tagId = v; }
    public void setContent(String v) { content = v; }
    public void setContentRevision(int v) { contentRevision = v; }
    public void setIndexStatus(String v) { indexStatus = v; }
    public void setLastEditorId(String v) { lastEditorId = v; }
    public void setChunkIndex(int v) { chunkIndex = v; }
    public void setIsEnabled(boolean v) { isEnabled = v; }
    public void setFlags(int v) { flags = v; }
    public void setStatus(int v) { status = v; }
    public void setStartAt(int v) { startAt = v; }
    public void setEndAt(int v) { endAt = v; }
    public void setPreChunkId(String v) { preChunkId = v; }
    public void setNextChunkId(String v) { nextChunkId = v; }
    public void setChunkType(String v) { chunkType = v == null ? "text" : v; }
    public void setParentChunkId(String v) { parentChunkId = v; }
    public void setRelationChunks(JsonNode v) { relationChunks = v; }
    public void setIndirectRelationChunks(JsonNode v) { indirectRelationChunks = v; }
    public void setMetadata(JsonNode v) { metadata = v; }
    public void setContentHash(String v) { contentHash = v; }
    public void setImageInfo(String v) { imageInfo = v; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }

    public String getSourceContent() { return sourceContent; }

    public void setSourceContent(String v) { sourceContent = v; }

    public String getContextHeader() { return contextHeader; }

    public void setContextHeader(String v) { contextHeader = v; }
}
