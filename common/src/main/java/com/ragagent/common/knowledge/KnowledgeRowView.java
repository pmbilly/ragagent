package com.ragagent.common.knowledge;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 知识行（文档）**行视图**（L1）：KnowledgeCatalogPort 知识行面（读 / 建 / 批 / 写）的载荷。
 *
 * <p><b>为什么有它</b>：实体 Knowledge 已归位 knowledge/domain（HANDOFF §12.0 判据 1）；
 * 跨域方（会话 10 文件、数据源 4 文件、初始化 / 评估 / wiki / config）与 L1 的
 * KnowledgeProjectionAdapter 只应经本视图。</p>
 *
 * <p><b>口径</b>：镜像实体全部 30 字段（含 3 个 JsonNode 与 2 个 @TableField(exist=false) 字段），
 * 访问器体与实体逐字一致；字段序与实体一致 ⇒ 序列化等价（doc-*.json 金片钉住）。</p>
 *
 * <p><b>落点登记（HANDOFF §12.5）</b>：含 3 个 JsonNode ⇒ :contract 零依赖装不下 ⇒ 留 L1，
 * 登记待去 Jackson（去 Jackson 后与端口知识行面整批入 contract.knowledge）。</p>
 */
public class KnowledgeRowView {

    private String id;
    private Long tenantId;
    private String knowledgeBaseId;
    private List<JsonNode> tags;
    private String type;
    private String title;
    private String description = "";
    private String source = "";
    private String channel = "web";
    private String parseStatus = KnowledgeParseStatus.PARSE_PENDING;
    private int pendingSubtasksCount;
    private String summaryStatus = "none";
    private String enableStatus = "enabled";
    private String embeddingModelId = "";
    private String fileName;
    private String folderPath = "";
    private String fileType;
    private Long fileSize;
    private String fileHash;
    private String filePath;
    private long storageSize;
    private JsonNode metadata;
    private JsonNode customMetadata;
    private JsonNode lastFaqImportResult;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime processedAt;
    private String errorMessage = "";
    private OffsetDateTime deletedAt;
    private String knowledgeBaseName = "";

    public String getId() { return id; }
    public void setId(String v) { id = v; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { tenantId = v; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String v) { knowledgeBaseId = v; }
    public List<JsonNode> getTags() { return tags; }
    public void setTags(List<JsonNode> v) { tags = v; }
    public String getType() { return type; }
    public void setType(String v) { type = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description = v == null ? "" : v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v == null ? "" : v; }
    public String getChannel() { return channel; }
    public void setChannel(String v) { channel = v == null || v.isEmpty() ? "web" : v; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String v) { parseStatus = v; }
    public int getPendingSubtasksCount() { return pendingSubtasksCount; }
    public void setPendingSubtasksCount(int v) { pendingSubtasksCount = v; }
    public String getSummaryStatus() { return summaryStatus; }
    public void setSummaryStatus(String v) { summaryStatus = v; }
    public String getEnableStatus() { return enableStatus; }
    public void setEnableStatus(String v) { enableStatus = v; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String v) { embeddingModelId = v == null ? "" : v; }
    public String getFileName() { return fileName; }
    public void setFileName(String v) { fileName = v; }
    public String getFolderPath() { return folderPath; }
    public void setFolderPath(String v) { folderPath = v == null ? "" : v; }
    public String getFileType() { return fileType; }
    public void setFileType(String v) { fileType = v; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long v) { fileSize = v; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String v) { fileHash = v; }
    public String getFilePath() { return filePath == null ? "" : filePath; }
    public void setFilePath(String v) { filePath = v; }
    public long getStorageSize() { return storageSize; }
    public void setStorageSize(long v) { storageSize = v; }
    public JsonNode getMetadata() { return metadata; }
    public void setMetadata(JsonNode v) { metadata = v; }
    public JsonNode getCustomMetadata() { return customMetadata; }
    public void setCustomMetadata(JsonNode v) { customMetadata = v; }
    public JsonNode getLastFaqImportResult() { return lastFaqImportResult; }
    public void setLastFaqImportResult(JsonNode v) { lastFaqImportResult = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(OffsetDateTime v) { processedAt = v; }
    public String getErrorMessage() { return errorMessage == null ? "" : errorMessage; }
    public void setErrorMessage(String v) { errorMessage = v == null ? "" : v; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
    public String getKnowledgeBaseName() { return knowledgeBaseName == null ? "" : knowledgeBaseName; }
    public void setKnowledgeBaseName(String v) { knowledgeBaseName = v == null ? "" : v; }
}
