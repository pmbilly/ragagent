package com.ragagent.memory.dto;

import com.ragagent.memory.domain.item.MemoryItem;
import java.time.OffsetDateTime;

/** 记忆条目响应视图：字段名/顺序对齐 {@link MemoryItem} 的既有 JSON 契约。 */
public record MemoryItemResponse(
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

    public static MemoryItemResponse from(MemoryItem item) {
        return new MemoryItemResponse(
                item.getId(),
                item.getTenantId(),
                item.getSubjectId(),
                item.getKind(),
                item.getContent(),
                item.getTopic(),
                item.getNormalizedKey(),
                item.getImportance(),
                item.getOrigin(),
                item.getStatus(),
                item.getSourceSessionId(),
                item.getSourceMessageId(),
                item.getValidFrom(),
                item.getInvalidAt(),
                item.getExpiresAt(),
                item.getReplacesId(),
                item.getSupersededBy(),
                item.getLastUsedAt(),
                item.getUseCount(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
