package com.ragagent.auth.controller.vo;

import java.time.OffsetDateTime;

/**
 * 邀请投影的 HTTP 响应体（字段序 = JSON 键序）。
 *
 * <p>八个可空文本字段空串归一为 null（输出 null，不省略键）。</p>
 */
public record TenantInvitationRespVo(
        long id,
        long tenantId,
        String tenantName,
        String inviteeUserId,
        String inviteeEmail,
        String inviteeName,
        String invitedBy,
        String inviterEmail,
        String inviterName,
        String role,
        String status,
        String message,
        OffsetDateTime expiresAt,
        OffsetDateTime respondedAt,
        OffsetDateTime createdAt,
        String inviteUrl,
        boolean isShareLink,
        int acceptedCount) {

    public TenantInvitationRespVo {
        tenantName = emptyToNull(tenantName);
        inviteeEmail = emptyToNull(inviteeEmail);
        inviteeName = emptyToNull(inviteeName);
        invitedBy = emptyToNull(invitedBy);
        inviterEmail = emptyToNull(inviterEmail);
        inviterName = emptyToNull(inviterName);
        message = emptyToNull(message);
        inviteUrl = emptyToNull(inviteUrl);
    }

    private static String emptyToNull(String v) {
        return v == null || v.isEmpty() ? null : v;
    }
}
