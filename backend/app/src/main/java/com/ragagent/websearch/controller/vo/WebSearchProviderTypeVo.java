package com.ragagent.websearch.controller.vo;

import java.util.List;
import java.util.Map;

/**
 * 搜索提供方类型元数据的 HTTP 响应体。
 *
 * <p>条目顺序固定（契约），{@code configFields} 是不透明的表单字段描述列表。</p>
 */
public record WebSearchProviderTypeVo(
        String id,
        String name,
        boolean requiresApiKey,
        boolean supportsOptionalApiKey,
        boolean requiresEngineId,
        boolean requiresBaseUrl,
        boolean supportsProxy,
        String description,
        String docsUrl,
        List<Map<String, Object>> configFields) {
}
