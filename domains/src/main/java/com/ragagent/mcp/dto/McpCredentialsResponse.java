package com.ragagent.mcp.dto;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * PUT {@code /{resource}/{id}/credentials} 的共享响应。
 *
 * <p>按字段名（{@code apiKey} / {@code token}）索引；前端据此在不重新拉取整个资源的前提下
 * 更新内存中的元数据。{@code fields} 恒输出；
 * map 的键用 {@link LinkedHashMap} 固定插入序（apiKey &lt; token）。</p>
 */
public record McpCredentialsResponse( Map<String, McpCredentialFieldMetadata> fields) {

    /** 键名＝凭据字段名（{@code apiKey} / {@code token}）。 */
    public static McpCredentialsResponse of(boolean apiKeyConfigured, boolean tokenConfigured) {
        Map<String, McpCredentialFieldMetadata> fields = new LinkedHashMap<>();
        fields.put("apiKey", new McpCredentialFieldMetadata(apiKeyConfigured));
        fields.put("token", new McpCredentialFieldMetadata(tokenConfigured));
        return new McpCredentialsResponse(fields);
    }
}
