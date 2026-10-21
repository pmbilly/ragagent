package com.ragagent.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * user_kb_pins：per-(tenant, user, knowledge base) 置顶。
 * 主键 (tenant_id, user_id, knowledge_base_id)；索引
 * idx_user_kb_pins_user_tenant_pinned_at (tenant_id, user_id, pinned_at DESC)。
 *
 * <p>tenant_id 是**必填**（生产列 NOT NULL 无默认）：置顶是租户内的个人偏好，
 * 同一用户跨租户时不能互相看见。读写都带它，索引才用得上。</p>
 */
@TableName("user_kb_pins")
public class UserKbPin {

    @TableField("tenant_id")
    private Long tenantId;
    private String userId;
    private String knowledgeBaseId;
    private OffsetDateTime pinnedAt;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long v) { tenantId = v; }
    public String getUserId() { return userId; }
    public void setUserId(String v) { userId = v; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String v) { knowledgeBaseId = v; }
    public OffsetDateTime getPinnedAt() { return pinnedAt; }
    public void setPinnedAt(OffsetDateTime v) { pinnedAt = v; }
}
