package com.ragagent.auth.controller.vo;

/**
 * API 主体配置的 HTTP 响应体：五个字段恒输出。
 *
 * <p>{@code hasHmacSecret} 只报是否已配置，明文密钥永不回显。</p>
 */
public record APIPrincipalConfigRespVo(
        String mode,
        String directHeaderName,
        String signedTokenHeaderName,
        boolean requireDirectHeader,
        boolean hasHmacSecret) {
}
