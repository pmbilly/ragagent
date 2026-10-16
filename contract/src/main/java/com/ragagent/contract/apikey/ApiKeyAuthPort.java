package com.ragagent.contract.apikey;

/**
 * X-API-Key 认证通道口（中性签名）。
 *
 * <p><b>动机（历史）</b>：{@code auth/filter/AuthFilter} 原先直接依赖
 * {@code auth.apikey.filter.APIKeyAuthChannel}；经本口反转后归零。P4 起端口入契约面：
 * 只读中性输入、只产中性结果——servlet 交互（读 header / 写响应）留在边界
 * （filter 提取 {@link AuthRequest}、按 {@link AuthResult.Rejected} 写响应）。</p>
 *
 * <p><b>语义</b>：鉴权成功时由实现写入 principal / scope 上下文并返回 {@link AuthResult.Passed}
 * （调用方继续过滤链）；失败返回 {@link AuthResult.Rejected}（状态码与 JSON 体为契约原文，
 * 由边界逐字写出）。</p>
 */
public interface ApiKeyAuthPort {

    /** 尝试用 {@code X-API-Key} 认证；成功者已写上下文。 */
    AuthResult authenticate(AuthRequest request);

    /** 认证所需的最小请求面（边界从 servlet 请求提取）。 */
    record AuthRequest(String apiKeyHeader, String tenantIdHeader,
                       String externalUserIdHeader, String externalUserTokenHeader,
                       String requestUri, String method) {
    }

    /** 结果：{@code Passed} 已写上下文（边界继续链）；{@code Rejected} 边界写响应。 */
    sealed interface AuthResult {

        record Passed() implements AuthResult {
        }

        record Rejected(int status, String jsonBody) implements AuthResult {
        }
    }
}
