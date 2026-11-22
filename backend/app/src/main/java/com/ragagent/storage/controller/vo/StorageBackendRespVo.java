package com.ragagent.storage.controller.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * 存储后端的 HTTP 响应体（{@code config} 已掩码）。
 *
 * <p>字段名与声明顺序即线格式：键恒输出，{@code deletedAt} 恒 null。</p>
 */
public record StorageBackendRespVo(
        String id,
        long tenantId,
        String name,
        String provider,
        JsonNode config,
        String source,
        String status,
        boolean legacyAlias,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt) {
}
