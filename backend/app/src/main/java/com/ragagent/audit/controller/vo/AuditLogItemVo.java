package com.ragagent.audit.controller.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * 审计行的 HTTP 响应体（15 个键恒输出，键名与声明顺序即线格式）。
 *
 * <p>{@code details} 是 jsonb 原样节点，可空且显式输出 null。</p>
 */
public record AuditLogItemVo(
        Long id,
        Long tenantId,
        String actorUserId,
        String actorRole,
        String action,
        String scopeType,
        String scopeId,
        String targetType,
        String targetId,
        String targetUserId,
        String requestPath,
        String requestMethod,
        String outcome,
        JsonNode details,
        OffsetDateTime createdAt) {
}
