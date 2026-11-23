package com.ragagent.auth.controller.vo;

/** OIDC 配置的 HTTP 响应体：未配置时 providerDisplayName 显式 null。 */
public record OidcConfigRespVo(boolean enabled, String providerDisplayName) {
}
