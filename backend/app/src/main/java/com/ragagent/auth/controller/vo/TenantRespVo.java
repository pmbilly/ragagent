package com.ragagent.auth.controller.vo;

import java.time.OffsetDateTime;

/**
 * 租户的 HTTP 响应体。
 *
 * <p>秘密承载字段按调用者角色裁剪：装配前由源 DTO 置 null（非 admin 不可见），
 * 本 VO 只负责搬运——不做任何裁剪判断。{@code deletedAt} 恒输出。</p>
 */
public record TenantRespVo(
        Long id,
        String name,
        String description,
        String status,
        Object retrieverEngines,
        String business,
        Long storageQuota,
        Long storageUsed,
        Object contextConfig,
        Object webSearchConfig,
        Object parserEngineConfig,
        Object credentials,
        Object storageEngineConfig,
        Object chatHistoryConfig,
        Object retrievalConfig,
        Object memoryConfig,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt) {
}
