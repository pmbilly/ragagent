package com.ragagent.model.controller.vo;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 模型详情的 HTTP 响应体。
 *
 * <p>秘密字段（apiKey/appSecret）不在形状里；{@code credentials} 为 null 表示不可见
 * （内置模型对非系统管理员）。</p>
 */
public record ModelDetailRespVo(
        String id,
        long tenantId,
        String name,
        String displayName,
        String type,
        String source,
        String description,
        ModelParametersRespVo parameters,
        boolean isDefault,
        boolean isBuiltin,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Map<String, ModelCredentialFieldMetaVo> credentials) {
}
