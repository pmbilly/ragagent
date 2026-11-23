package com.ragagent.auth.controller.vo;

/**
 * OIDC 授权地址的 HTTP 响应体。
 *
 * <p>Nonce 只进 HttpOnly cookie，不建模。</p>
 */
public record OidcAuthUrlRespVo(
        String providerDisplayName,
        String authorizationUrl,
        String state) {
}
