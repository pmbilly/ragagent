package com.ragagent.auth.apikey.domain;

import com.ragagent.common.apikey.TenantAPIKeyResponse;
import com.ragagent.common.security.APIKeyCapability;
import com.ragagent.common.security.APIKeyScopeType;

/**
 * 实体 → 响应投影（B207 迁出）。
 *
 * <p>响应记录 {@link TenantAPIKeyResponse} 已下沉 {@code :common}（纯载荷 ✓，见
 * {@code docs/phase4-module-boundaries-plan.md} §12），而映射需要实体 {@link TenantAPIKey}
 * ⇒ 映射留在属主模块，避免 {@code :common → domains} 反向边 ✗。</p>
 *
 * <p>语义与原先的 {@code TenantAPIKeyResponse.from} <b>逐字一致</b>：null 入参返回零值投影
 * （字符串 {@code ""}、{@code scopeType} <b>不归一</b>、{@code capabilities} 保持 null ✓）。</p>
 */
public final class TenantAPIKeyProjections {

    private TenantAPIKeyProjections() {
    }

    /** 实体 → 响应投影。null 入参返回零值投影（逐字段语义见类注释 ✓）。 */
    public static TenantAPIKeyResponse from(TenantAPIKey key) {
        if (key == null) {
            return new TenantAPIKeyResponse(0L, "", "", "", false, null, null, null, null, null);
        }
        return new TenantAPIKeyResponse(
                key.getId() == null ? 0L : key.getId(),
                APIKeyScopeType.normalize(key.getScopeType()),
                key.getName(),
                key.getApiKey(),
                key.isFullAccess(),
                key.getKnowledgeBaseIds(),
                APIKeyCapability.normalizeAll(key.getCapabilities()),
                key.getLastUsedAt(),
                key.getExpiresAt(),
                key.getCreatedAt());
    }
}
