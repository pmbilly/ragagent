package com.ragagent.websearch.controller.vo;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 搜索提供方的 HTTP 响应体。
 *
 * <p>{@code apiKey} 不在形状里；{@code credentials} 恒输出单键 {@code apiKey.configured}。
 * {@code proxyUrl}/{@code extraConfig} 的可见性由源 DTO 决定，装配层不判断。</p>
 */
public record WebSearchProviderRespVo(
        String id,
        long tenantId,
        String name,
        String provider,
        String description,
        ParametersVo parameters,
        boolean isDefault,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Map<String, CredentialFieldMetaVo> credentials) {

    /** 提供方参数视图：除 apiKey 外的全部参数。 */
    public record ParametersVo(
            String engineId,
            String baseUrl,
            String proxyUrl,
            Map<String, String> extraConfig) {
    }

    /** 凭据字段的对外形态：只暴露是否已配置。 */
    public record CredentialFieldMetaVo(boolean configured) {
    }
}
