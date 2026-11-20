package com.ragagent.contract.wiki;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * wiki 问题行**视图**（契约面载荷）：{@code WikiPageOpsPort} 的问题面（列 / 登记 / 改状态）口径。
 *
 * <p><b>为什么有它</b>：实体 {@code WikiPageIssue} 已归位 `wiki/domain`（HANDOFF 判据 1）；
 * 跨域方（会话域的 wiki 工具后端）只应经本视图读写。</p>
 *
 * <p><b>落点</b>：字段全是 JDK 类型（无 Jackson 注解需求——本视图不直接出面给客户端，
 * 工具侧另有其 {@code IssueView}）⇒ 落 `:contract` ✓。访问器名与实体**逐一相同** ⇒ 消费方改类型即等价。</p>
 */
public class WikiPageIssueView {

    private String id;
    private Long tenantId;
    private String knowledgeBaseId = "";
    private String slug = "";
    private String issueType = "";
    private String description = "";
    private List<String> suspectedKnowledgeIds = new ArrayList<>();
    private String status = "";
    private String reportedBy = "";
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    public String getId() {
        return id;
    }

    public void setId(String v) {
        this.id = v;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long v) {
        this.tenantId = v;
    }

    public String getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(String v) {
        this.knowledgeBaseId = v == null ? "" : v;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String v) {
        this.slug = v == null ? "" : v;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String v) {
        this.issueType = v == null ? "" : v;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = v == null ? "" : v;
    }

    public List<String> getSuspectedKnowledgeIds() {
        return suspectedKnowledgeIds;
    }

    public void setSuspectedKnowledgeIds(List<String> v) {
        this.suspectedKnowledgeIds = v == null ? new ArrayList<>() : new ArrayList<>(v);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String v) {
        this.status = v == null ? "" : v;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String v) {
        this.reportedBy = v == null ? "" : v;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime v) {
        this.createdAt = v;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime v) {
        this.updatedAt = v;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime v) {
        this.deletedAt = v;
    }
}
