package com.ragagent.common.wiki;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * wiki 页**行视图**（L1）：{@code WikiPageOpsPort} 页面面（读 / 建 / 改 / 搜索）的载荷。
 *
 * <p><b>为什么有它</b>：实体 {@code WikiPage} 已归位 `wiki/domain`（HANDOFF §12.0 判据 1）；
 * 跨域方（会话域的 wiki 工具后端）只应经本视图读写——它对该实体用到 **42 个访问器**
 * （含 {@code pageMetadata}），故视图**镜像全 27 字段**、访问器体与实体**逐字一致**（含空值归一）。</p>
 *
 * <p><b>落点（登记 HANDOFF §12.5）</b>：{@code pageMetadata} 是 {@code JsonNode} ⇒ `:contract` 零依赖装不下
 * ⇒ 留 L1 与端口同处，登记为**待去 Jackson**（去 Jackson 后可与端口整批入 `contract.wiki`）。</p>
 */
public class WikiPageView {

    private String id;
    private Long tenantId;
    private String knowledgeBaseId = "";
    private String slug = "";
    private String title = "";
    private String pageType = "";
    private String status = "";
    private String content = "";
    private String summary = "";
    private List<String> aliases = new ArrayList<>();
    private String parentSlug = "";
    private String folderId = "";
    private List<String> categoryPath = new ArrayList<>();
    private String wikiPath = "";
    private int depth;
    private int sortOrder;
    private List<String> sourceRefs = new ArrayList<>();
    private List<String> chunkRefs = new ArrayList<>();
    private List<String> inLinks = new ArrayList<>();
    private List<String> outLinks = new ArrayList<>();
    private JsonNode pageMetadata;
    private int version;
    private String lastEditSource = "";
    private String lastEditorId = "";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public String getId() { return id; }
    public Long getTenantId() { return tenantId; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getPageType() { return pageType; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public String getSummary() { return summary; }
    public List<String> getAliases() { return aliases; }
    public String getParentSlug() { return parentSlug; }
    public String getFolderId() { return folderId; }
    public List<String> getCategoryPath() { return categoryPath; }
    public String getWikiPath() { return wikiPath; }
    public int getDepth() { return depth; }
    public int getSortOrder() { return sortOrder; }
    public List<String> getSourceRefs() { return sourceRefs; }
    public List<String> getChunkRefs() { return chunkRefs; }
    public List<String> getInLinks() { return inLinks; }
    public List<String> getOutLinks() { return outLinks; }
    public JsonNode getPageMetadata() { return pageMetadata; }
    public int getVersion() { return version; }
    public String getLastEditSource() { return lastEditSource; }
    public String getLastEditorId() { return lastEditorId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setId(String v) { this.id = v; }
    public void setTenantId(Long v) { this.tenantId = v; }
    public void setKnowledgeBaseId(String v) { this.knowledgeBaseId = v == null ? "" : v; }
    public void setSlug(String v) { this.slug = v == null ? "" : v; }
    public void setTitle(String v) { this.title = v == null ? "" : v; }
    public void setPageType(String v) { this.pageType = v == null ? "" : v; }
    public void setStatus(String v) { this.status = v == null ? "" : v; }
    public void setContent(String v) { this.content = v == null ? "" : v; }
    public void setSummary(String v) { this.summary = v == null ? "" : v; }
    public void setAliases(List<String> v) { this.aliases = v == null ? new ArrayList<>() : v; }
    public void setParentSlug(String v) { this.parentSlug = v == null ? "" : v; }
    public void setFolderId(String v) { this.folderId = v == null ? "" : v; }
    public void setCategoryPath(List<String> v) { this.categoryPath = v == null ? new ArrayList<>() : v; }
    public void setWikiPath(String v) { this.wikiPath = v == null ? "" : v; }
    public void setDepth(int v) { this.depth = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setSourceRefs(List<String> v) { this.sourceRefs = v == null ? new ArrayList<>() : v; }
    public void setChunkRefs(List<String> v) { this.chunkRefs = v == null ? new ArrayList<>() : v; }
    public void setInLinks(List<String> v) { this.inLinks = v == null ? new ArrayList<>() : v; }
    public void setOutLinks(List<String> v) { this.outLinks = v == null ? new ArrayList<>() : v; }
    public void setPageMetadata(JsonNode v) { this.pageMetadata = v; }
    public void setVersion(int v) { this.version = v; }
    public void setLastEditSource(String v) { this.lastEditSource = v == null ? "" : v; }
    public void setLastEditorId(String v) { this.lastEditorId = v == null ? "" : v; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
    public void setDeletedAt(OffsetDateTime v) { this.deletedAt = v; }
}
