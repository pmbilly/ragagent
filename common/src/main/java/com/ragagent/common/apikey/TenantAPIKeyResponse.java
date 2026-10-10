package com.ragagent.common.apikey;

import java.time.OffsetDateTime;
import java.util.List;
import com.ragagent.common.security.APIKeyScopeType;

/**
 * API Key 的响应投影。
 *
 * <p>record 字段声明序即 JSON 键序（键序按字段声明序输出）。</p>
 *
 * <p>逐字段输出语义：</p>
 * <ul>
 *   <li>{@code id} / {@code fullAccess}：非可空 → 恒输出（0 / false 也输出）；</li>
 *   <li>{@code scopeType}：经 {@link APIKeyScopeType#normalize} 归一，恒输出，
 *       未知口径回落 {@code "tenant"}；</li>
 *   <li>{@code knowledgeBaseIds}：恒输出，null 时输出 {@code null}
 *       （full-access Key 就是这个形态）；</li>
 *   <li>{@code capabilities}：恒输出；且构造时经
 *       {@code APIKeyCapability.normalizeAll}，null 会变成 {@code []} ——所以这一列
 *       **永远不会是 null**，与上一行形成刻意的不对称；</li>
 *   <li>{@code lastUsedAt} / {@code expiresAt}：可空 → null 时**省略键**；</li>
 *   <li>{@code createdAt}：非可空 → 恒输出。</li>
 * </ul>
 */
public record TenantAPIKeyResponse(
        long id,
        String scopeType,
        String name,
        String apiKey,
        boolean fullAccess,
        List<String> knowledgeBaseIds,
        List<String> capabilities,
        OffsetDateTime lastUsedAt,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt) {
}
