package com.ragagent.auth.apikey.service;

import com.ragagent.auth.apikey.domain.TenantAPIKey;
import com.ragagent.auth.apikey.dto.TenantAPIKeyProjections;
import com.ragagent.contract.apikey.APIKeyScopeType;
import com.ragagent.contract.apikey.TenantAPIKeyCreateResponse;
import com.ragagent.contract.apikey.TenantAPIKeyResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * API-Key 管理面（认证族内直接依赖）：脱敏、投影与窄面都收在这里，
 * 调用方只看 {@code TenantAPIKeyResponse} / {@code TenantAPIKeyCreateResponse} 载荷。
 */
@Component
public class ApiKeyAdminAdapter {

    private final TenantAPIKeyService service;

    public ApiKeyAdminAdapter(TenantAPIKeyService service) {
        this.service = service;
    }

    public List<TenantAPIKeyResponse> listPlatformMasked() {
        List<TenantAPIKeyResponse> out = new ArrayList<>();
        for (TenantAPIKey key : service.listPlatform()) {
            out.add(masked(TenantAPIKeyProjections.from(key), key.getApiKey()));
        }
        return out;
    }

    public TenantAPIKeyCreateResponse createPlatformMasked(String name, List<String> capabilities,
                                                           OffsetDateTime expiresAt) {
        var result = service.create(new TenantAPIKeyService.TenantAPIKeyServiceCreateRequest(
                0L, APIKeyScopeType.PLATFORM, name, false, null, capabilities, expiresAt));
        TenantAPIKeyResponse masked = masked(TenantAPIKeyProjections.from(result.apiKey()), result.token());
        return TenantAPIKeyCreateResponse.of(masked, result.token());
    }

    public void revokePlatform(long id) {
        service.revokePlatform(id);
    }

    public String createDefaultTenantKey(long tenantId) {
        var result = service.create(new TenantAPIKeyService.TenantAPIKeyServiceCreateRequest(
                tenantId, null, "default", true, null, null, null));
        return result.token();
    }

    /** 脱敏：<=12 位 → "***"；否则 first7 + "..." + last4（逐字搬自原 SystemAdminController ✓）。 */
    private static TenantAPIKeyResponse masked(TenantAPIKeyResponse item, String token) {
        String t = token == null ? "" : token.trim();
        String masked = t.length() <= 12 ? "***" : t.substring(0, 7) + "..." + t.substring(t.length() - 4);
        return new TenantAPIKeyResponse(item.id(), item.scopeType(), item.name(), masked,
                item.fullAccess(), item.knowledgeBaseIds(), item.capabilities(),
                item.lastUsedAt(), item.expiresAt(), item.createdAt());
    }
}
