package com.ragagent.contract.session;

import java.time.OffsetDateTime;


/**
 * 会话行**线格式视图**（会话 QA 面载荷与装配面读写形状）。
 *
 * <p><b>为什么有它</b>：实体 Session 属会话域内部（HANDOFF §12.0 判据 1），而
 * SessionQaPort 需经它读写会话、QaRequestPayload 需携带它触发 QA。本视图**逐一镜像实体的
 * 13 个字段与默认值**，并在同样字段上标 @JsonIgnore（embedVisitorId）⇒ **JSON 逐字节等价**
 * （由 session-*.json / emb-pub-sessions-*.json 金片钉住）；视图与实体**无损互转**
 * （适配器 toEntity 回填后可继续走域内管线，避免"视图缺字段"的语义丢失）。</p>
 *
 * <p><b>可写说明</b>：DTO——适配器填充、装配面构造；访问器名与实体一致（消费方零改动）。</p>
 */
public class SessionView {
    private String id;

    private String title = "";

    private String description = "";

    private Long tenantId;

    private String userId = "";
    private String embedVisitorId;

    private boolean pinned;

    private OffsetDateTime pinnedAt;

    private SessionLastRequestState lastRequestState;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;

    private String imPlatform = "";

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** **非 getter 命名**（embedVisitorIdValue()）⇒ 等价原 @JsonIgnore（序列化不出面）。 */
    public String embedVisitorIdValue() {
        return embedVisitorId;
    }

    public void setEmbedVisitorId(String embedVisitorId) {
        this.embedVisitorId = embedVisitorId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public OffsetDateTime getPinnedAt() {
        return pinnedAt;
    }

    public void setPinnedAt(OffsetDateTime pinnedAt) {
        this.pinnedAt = pinnedAt;
    }

    public SessionLastRequestState getLastRequestState() {
        return lastRequestState;
    }

    public void setLastRequestState(SessionLastRequestState lastRequestState) {
        this.lastRequestState = lastRequestState;
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

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getImPlatform() {
        return imPlatform;
    }

    public void setImPlatform(String imPlatform) {
        this.imPlatform = imPlatform;
    }
}
