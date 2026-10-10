package com.ragagent.auth.apikey.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.auth.apikey.domain.TenantAPIKey;
import com.ragagent.auth.apikey.domain.TenantAPIKeyProjections;
import com.ragagent.common.apikey.ApiKeyAdminPort;
import com.ragagent.common.apikey.TenantAPIKeyCreateResponse;
import com.ragagent.common.apikey.TenantAPIKeyResponse;
import com.ragagent.common.security.APIKeyScopeType;

/**
 * {@link ApiKeyAdminPort} 的属主模块实现（B208 ✓）。
 *
 * <p>原先是 {@code SystemAdminController} 直接调 {@code TenantAPIKeyService} + 自己脱敏 ✗；
 * 现在脱敏与投影都收在这里，调用方只看到 common 载荷 ✓。</p>
 */
@Component
public class ApiKeyAdminAdapter implements ApiKeyAdminPort {

    private final TenantAPIKeyService service;

    public ApiKeyAdminAdapter(TenantAPIKeyService service) {
        this.service = service;
    }

    @Override
    public List<TenantAPIKeyResponse> listPlatformMasked() {
        List<TenantAPIKeyResponse> out = new ArrayList<>();
        for (TenantAPIKey key : service.listPlatform()) {
            out.add(masked(TenantAPIKeyProjections.from(key), key.getApiKey()));
        }
        return out;
    }

    @Override
    public TenantAPIKeyCreateResponse createPlatformMasked(String name, List<String> capabilities,
                                                           OffsetDateTime expiresAt) {
        var result = service.create(new TenantAPIKeyService.TenantAPIKeyServiceCreateRequest(
                0L, APIKeyScopeType.PLATFORM, name, false, null, capabilities, expiresAt));
        TenantAPIKeyResponse masked = masked(TenantAPIKeyProjections.from(result.apiKey()), result.token());
        return TenantAPIKeyCreateResponse.of(masked, result.token());
    }

    @Override
    public void revokePlatform(long id) {
        service.revokePlatform(id);
    }

    @Override
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
