package com.ragagent.auth.controller.vo;

/**
 * {@code POST /auth/invitations/lookup} 的 HTTP 响应体。
 *
 * <p>刻意收窄到注册页所需；{@code expiresAt} 是 UTC 预格式化字符串，不含小数秒。</p>
 */
public record InvitationLookupRespVo(
        long tenantId,
        String tenantName,
        String role,
        String expiresAt) {
}
