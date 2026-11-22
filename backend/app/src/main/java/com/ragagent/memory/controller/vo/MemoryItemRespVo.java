package com.ragagent.memory.controller.vo;

import java.time.OffsetDateTime;

/**
 * 记忆条目的 HTTP 响应体。
 *
 * <p>字段名与声明顺序即线格式：Jackson 按声明顺序输出，可空字段也恒输出。</p>
 */
public record MemoryItemRespVo(
        String id,
        Long tenantId,
        String subjectId,
        String kind,
        String content,
        String topic,
        String normalizedKey,
        int importance,
        String origin,
        String status,
        String sourceSessionId,
        String sourceMessageId,
        OffsetDateTime validFrom,
        OffsetDateTime invalidAt,
        OffsetDateTime expiresAt,
        String replacesId,
        String supersededBy,
        OffsetDateTime lastUsedAt,
        int useCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
