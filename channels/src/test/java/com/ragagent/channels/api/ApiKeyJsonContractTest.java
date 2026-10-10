package com.ragagent.channels.api;

import static com.ragagent.common.JsonRoundTrip.assertRoundTrips;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ragagent.channels.api.domain.TenantAPIKey;
import com.ragagent.channels.api.domain.TenantAPIKeyProjections;
import com.ragagent.common.apikey.TenantAPIKeyCreateResponse;
import com.ragagent.common.apikey.TenantAPIKeyResponse;

/**
 * API-Key 的 JSON 契约往返（B210 自 domains 的 {@code JsonContractRoundTripTest} 外提 ✓）。
 *
 * <p>该段依赖实体 {@code TenantAPIKey}（属主已迁 {@code :channels}）⇒ 留在 domains 会形成
 * 测试期的反向依赖 ✗。三类风险各钉一条：</p>
 * <ol>
 *   <li>{@code TenantAPIKey.isPlatform()} / {@code tenantIdValue()} 是派生访问器——
 *       漏 {@code @JsonIgnore} 会把 {@code "platform":true} 写进序列化结果；</li>
 *   <li>{@code keyHash} 不进 JSON，必须双向忽略；</li>
 *   <li>响应体 {@code TenantAPIKeyResponse} 的键名与 {@code TenantAPIKeyCreateResponse} 的 token 末位。</li>
 * </ol>
 */
class ApiKeyJsonContractTest {

    @Test
    void tenantApiKeyContractsRoundTrip() {
        TenantAPIKey key = new TenantAPIKey();
        key.setId(7L);
        key.setTenantId(42L);
        key.setScopeType("tenant");
        key.setName("integration");
        key.setKeyHash("deadbeef");          // json:"-" → 不进 JSON
        key.setApiKey("sk-plaintext");
        key.setFullAccess(false);
        key.setKnowledgeBaseIds(List.of("kb-1", "kb-2"));
        key.setCapabilities(List.of("retrieve", "chat"));
        // 时间字段留空：本工具用的是**裸** ObjectMapper（未注册 JSR-310 模块），
        // 非空 OffsetDateTime 会在这里炸，而时间键名/格式已由
        // TenantAPIKeyControllerTest 与 JacksonConfig 覆盖。
        assertRoundTrips(key, TenantAPIKey.class,
                "types.TenantAPIKey ← TenantAPIKey（jsonb 数组列 + 派生方法须 @JsonIgnore）");

        TenantAPIKeyResponse response = TenantAPIKeyProjections.from(key);
        assertRoundTrips(response, TenantAPIKeyResponse.class,
                "handler.tenantAPIKeyResponse ← TenantAPIKeyResponse");
        // 三个成功响应体都经它派生，token 在末位
        assertRoundTrips(TenantAPIKeyCreateResponse.of(response, "sk-once"),
                TenantAPIKeyCreateResponse.class,
                "handler.tenantAPIKeyCreateResponse ← TenantAPIKeyCreateResponse");
    }
}
