package com.ragagent.auth.apikey.controller.filter;

/** X-API-Key 认证所需的最小请求面（边界从 servlet 请求提取）。 */
public record ApiKeyAuthRequest(String apiKeyHeader, String tenantIdHeader,
                                String externalUserIdHeader, String externalUserTokenHeader,
                                String requestUri, String method) {
}
