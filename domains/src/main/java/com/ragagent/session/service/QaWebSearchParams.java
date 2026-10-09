package com.ragagent.session.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.common.context.TenantContext;
import com.ragagent.tenant.Tenant;
import com.ragagent.common.tenant.WebSearchConfig;

/**
 * WebSearch / WebFetch 的**有效参数解析**：provider 选择、开关、fetch topN 与结果条数上限。
 *
 * <p>B129 自 {@link SessionKnowledgeQaService} 外提（逐字搬迁 + 依赖改为 {@code service.} 前缀）。
 * 它是「检索参数解析」这一关注点的独立叶子：只读 agent 配置、租户配置与 provider 表，
 * 不碰会话/消息/事件——与本域既有的 {@code QaKbScope}/{@code QaModelSelection} 同形
 * （持门面引用，便于取共享依赖）。</p>
 */
final class QaWebSearchParams {

    private static final Logger log = LoggerFactory.getLogger(QaWebSearchParams.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SessionKnowledgeQaService service;

    QaWebSearchParams(SessionKnowledgeQaService service) {
        this.service = service;
    }

    // ==================================================================
    // web search 解析
    // ==================================================================

    String resolveWebSearchProviderId(QaSupport.QaRequest req, long tenantId) {
        if (req.agentConfig != null) {
            String providerId = req.agentConfig.path("webSearchProviderId").asText("");
            if (!providerId.isEmpty()) {
                return providerId;
            }
        }
        try {
            for (var provider : service.webSearchProviderRepository.list(tenantId)) {
                if (provider != null && provider.isDefault() && provider.getId() != null
                        && !provider.getId().isEmpty()) {
                    return provider.getId();
                }
            }
        } catch (RuntimeException ignored) {
            // 获取失败 → 留空
        }
        return "";
    }

    boolean resolveWebFetchEnabled(QaSupport.QaRequest req) {
        if (req.agentConfig != null) {
            return req.agentConfig.path("webFetchEnabled").asBoolean(false);
        }
        return false;
    }

    int resolveWebFetchTopN(QaSupport.QaRequest req) {
        if (req.agentConfig != null) {
            int topN = req.agentConfig.path("webFetchTopN").asInt(0);
            if (topN > 0) {
                return topN;
            }
        }
        return 3;
    }

    int resolveWebSearchMaxResults(QaSupport.QaRequest req) {
        if (req.agentConfig != null) {
            int max = req.agentConfig.path("webSearchMaxResults").asInt(0);
            if (max > 0) {
                return max;
            }
        }
        // 租户缺省分支：读租户 WebSearchConfig 的 maxResults
        Long tid = TenantContext.currentTenantId();
        if (tid != null) {
            try {
                Tenant tenant = service.tenantService.getTenantById(tid);
                if (tenant != null && tenant.getWebSearchConfig() != null
                        && !tenant.getWebSearchConfig().isNull()) {
                    WebSearchConfig cfg =
                            JSON.treeToValue(tenant.getWebSearchConfig(),
                                    WebSearchConfig.class);
                    int max = cfg.getMaxResults();   // 局部 cfg（本段解析出的 WebSearchConfig），非门面字段
                    if (max > 0) {
                        return max;
                    }
                }
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("resolveWebSearchMaxResults tenant config parse failed: {}", e.getMessage());
            }
        }
        return 10; // types.DefaultWebSearchMaxResults
    }
}
