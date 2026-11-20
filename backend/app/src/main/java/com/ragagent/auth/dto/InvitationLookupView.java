package com.ragagent.auth.dto;

import java.time.OffsetDateTime;

/** 邀请 token 查询投影：控制器不再持有 TenantInvitation。 */
public record InvitationLookupView(long tenantId, String role, OffsetDateTime expiresAt) {
}
