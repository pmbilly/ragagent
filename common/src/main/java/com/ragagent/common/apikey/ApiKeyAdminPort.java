package com.ragagent.common.apikey;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API-Key 管理口（B208 ✓）。
 *
 * <p><b>动机</b>：{@code :domains} 侧的 system/auth 控制器原先直接依赖
 * {@code auth.apikey.service.TenantAPIKeyService} ✗ —— 建 {@code :channels}（P4）时该方向会成环 ✗
 * （见 {@code docs/phase4-module-boundaries-plan.md} §12）。</p>
 *
 * <p><b>形状</b>：只暴露窄面 + 已定形的 common 载荷（{@link TenantAPIKeyResponse} /
 * {@link TenantAPIKeyCreateResponse} ✓）；<b>脱敏在实现侧完成</b>（与原先控制器里的 {@code masked(...)}
 * 逐字一致 ✓）。</p>
 */
public interface ApiKeyAdminPort {

    /** 平台密钥列表（{@code apiKey} 字段已脱敏 ✓）。 */
    List<TenantAPIKeyResponse> listPlatformMasked();

    /** 建平台密钥：返回创建响应（投影已脱敏；{@code token} 仅此一次 ✓）。 */
    TenantAPIKeyCreateResponse createPlatformMasked(String name, List<String> capabilities, OffsetDateTime expiresAt);

    /** 撤销平台密钥（不存在时抛运行时异常，由调用方翻译 ✓）。 */
    void revokePlatform(long id);

    /** 为租户建默认密钥，返回<b>明文 token</b>（仅此一次 ✓）。 */
    String createDefaultTenantKey(long tenantId);
}
