package com.ragagent.common.auth;

import java.time.OffsetDateTime;

import com.ragagent.common.auth.domain.UserPreferences;

/**
 * 用户行**视图**（L1）：{@code AdminUserPort} 管理面（读 / 建 / 晋升 / 撤权 / 重置密码）载荷，
 * 同时是系统管理面线格式（{@code SystemDtos.UserInfoResponse}）的形状。
 *
 * <p><b>为什么有它</b>：实体 {@code User} 已归位 `auth/domain`（HANDOFF §12.0 判据 1）；
 * 跨域方（system 的用户管理面）只应经本视图。</p>
 *
 * <p><b>口径</b>：镜像实体除 {@code @JsonIgnore passwordHash} 外的全部字段，访问器名逐字一致
 * （含 {@code isIsActive()} / {@code isIsSystemAdmin()}）⇒ JSON 等价。</p>
 *
 * <p><b>落点（HANDOFF §12.6.4 硬约束 2）</b>：{@code preferences} 是带 Jackson 注解的
 * {@code UserPreferences} 且直接出面 ⇒ 本视图与端口**只能落 L1**（零依赖 :contract 装不下）；
 * 去 Jackson 后可整批入 {@code contract.auth}。</p>
 */
public class AdminUserView {

    private String id;
    private String username;
    private String email;
    private String avatar;
    private Long tenantId;
    private boolean isActive;
    private boolean canAccessAllTenants;
    private boolean isSystemAdmin;
    private UserPreferences preferences;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;


    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getAvatar() { return avatar == null ? "" : avatar; }

    public void setAvatar(String avatar) { this.avatar = avatar; }

    public Long getTenantId() { return tenantId == null ? 0 : tenantId; }

    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public boolean isIsActive() { return isActive; }

    public void setIsActive(boolean active) { isActive = active; }

    public boolean isCanAccessAllTenants() { return canAccessAllTenants; }

    public void setCanAccessAllTenants(boolean v) { canAccessAllTenants = v; }

    public boolean isIsSystemAdmin() { return isSystemAdmin; }

    public void setIsSystemAdmin(boolean v) { isSystemAdmin = v; }

    public UserPreferences getPreferences() {
        if (preferences == null) {
            preferences = new UserPreferences();
        }
        return preferences;
    }

    public void setPreferences(UserPreferences preferences) { this.preferences = preferences; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(OffsetDateTime v) { createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(OffsetDateTime v) { updatedAt = v; }

    public OffsetDateTime getDeletedAt() { return deletedAt; }

    public void setDeletedAt(OffsetDateTime v) { deletedAt = v; }
}
