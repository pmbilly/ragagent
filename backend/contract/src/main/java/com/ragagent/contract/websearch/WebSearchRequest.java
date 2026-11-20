package com.ragagent.contract.websearch;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次联网搜索的执行请求（跨域载荷）。
 *
 * <p>与 {@code contract.tenant.WebSearchConfig}（租户配置投影）职责分离：本记录是
 * "这一次调用实际要发出去的形状"——租户段 + per-call 的 {@code maxResults} 与
 * {@link WebSearchFilters}。上提契约面是为了让 agent 工具与 websearch 域都只依赖下层，
 * 避免工具反向依赖业务域的执行配置（{@code WebSearchService.WebSearchConfig} 留作域内实现细节）。</p>
 */
public record WebSearchRequest(
        String provider,
        String apiKey,
        int maxResults,
        boolean includeDate,
        List<String> blacklist,
        String embeddingModelId,
        int documentFragments,
        String proxyUrl,
        WebSearchFilters filters) {

    public WebSearchRequest {
        provider = provider == null ? "" : provider;
        apiKey = apiKey == null ? "" : apiKey;
        blacklist = blacklist == null ? List.of() : new ArrayList<>(blacklist);
        embeddingModelId = embeddingModelId == null ? "" : embeddingModelId;
        proxyUrl = proxyUrl == null ? "" : proxyUrl;
        filters = filters == null ? WebSearchFilters.EMPTY : filters;
    }
}
