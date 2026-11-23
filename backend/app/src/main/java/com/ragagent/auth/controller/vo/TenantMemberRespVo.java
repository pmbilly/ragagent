package com.ragagent.auth.controller.vo;

import java.time.OffsetDateTime;

/**
 * 成员投影的 HTTP 响应体（字段序 = JSON 键序）。
 *
 * <p>{@code avatar} 空串归一为 null，{@code joinedAt} 恒输出。</p>
 */
public record TenantMemberRespVo(
        String userId,
        String email,
        String username,
        String avatar,
        String role,
        String status,
        String invitedBy,
        OffsetDateTime joinedAt) {

    public TenantMemberRespVo {
        if (avatar != null && avatar.isEmpty()) {
            avatar = null;
        }
    }
}
