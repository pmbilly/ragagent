package com.ragagent.websearch.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ragagent.chatpipeline.PipelinePorts;
import com.ragagent.chatpipeline.spi.WebSearchExecutor;
import com.ragagent.contract.tenant.WebSearchConfig;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.contract.websearch.WebSearchRequest;
import com.ragagent.contract.websearch.WebSearchResult;
import com.ragagent.websearch.service.WebSearchService;

/**
 * 联网搜索两个对外执行面的 websearch 域实现（同源双面）：
 *
 * <ul>
 *   <li>{@link WebSearchExecutor}（工具面）：L2 请求 → 域执行配置的转换
 *       自会话 {@code AgentToolBackends#toServiceConfig} 原样搬入——
 *       域内配置形状不外泄给工具；</li>
 *   <li>{@link PipelinePorts.WebSearch}（管线面）：L1 租户配置 → 执行面配置的转换
 *       与"当前租户缺省补 0"自会话 {@code QaWiring#qaPipelineWebSearch} 原样搬入。</li>
 * </ul>
 *
 * <p>逐字转发 {@link WebSearchService#search}，不新增缓冲、不改失败口径
 * （工具侧折叠为 success=false 由工具层负责）。</p>
 */
@Component
public class WebSearchBackendsAdapter implements WebSearchExecutor, PipelinePorts.WebSearch {

    private final WebSearchService webSearchService;

    public WebSearchBackendsAdapter(WebSearchService webSearchService) {
        this.webSearchService = webSearchService;
    }

    @Override
    public List<WebSearchResult> search(long tenantId, String providerId,
            WebSearchRequest request, String query) {
        return webSearchService.search(tenantId, providerId, toServiceConfig(request), query);
    }

    @Override
    public List<WebSearchResult> search(String providerId, WebSearchConfig config,
            String query) {
        // L1 租户配置 → 执行面配置的转换留在域侧（管线不接触 WebSearchService.WebSearchConfig）
        WebSearchService.WebSearchConfig exec = WebSearchService.WebSearchConfig.from(config);
        Long tenantId = TenantContext.currentTenantId();
        return webSearchService.search(tenantId == null ? 0L : tenantId, providerId, exec, query);
    }

    /** L2 {@link WebSearchRequest} → websearch 域执行配置（域内形状不外泄给工具）。 */
    private static WebSearchService.WebSearchConfig toServiceConfig(WebSearchRequest request) {
        WebSearchService.WebSearchConfig cfg = new WebSearchService.WebSearchConfig();
        cfg.provider = request.provider();
        cfg.apiKey = request.apiKey();
        cfg.maxResults = request.maxResults();
        cfg.includeDate = request.includeDate();
        cfg.blacklist = new ArrayList<>(request.blacklist());
        cfg.embeddingModelId = request.embeddingModelId();
        cfg.documentFragments = request.documentFragments();
        cfg.proxyUrl = request.proxyUrl();
        cfg.filters = request.filters();
        return cfg;
    }
}
