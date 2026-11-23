package com.ragagent.auth.controller.vo;

/** API 主体测试令牌的 HTTP 响应体。 */
public record APIPrincipalTestTokenRespVo(
        String token,
        String headerName,
        int expiresInSeconds,
        long expiresAtUnix,
        String externalUserId) {
}
