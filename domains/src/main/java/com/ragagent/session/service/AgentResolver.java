package com.ragagent.session.service;

import com.ragagent.agentmanagement.domain.CustomAgentEntity;
import com.ragagent.agentmanagement.service.CustomAgentService;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.web.JsonMappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * agent 解析。
 * 空间分享裁撤后只有自有 agent 一条路径：本租户直查；查询失败用默认空配置。
 */
@Component
public class AgentResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentResolver.class);
    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private final CustomAgentService customAgentService;

    public AgentResolver(CustomAgentService customAgentService) {
        this.customAgentService = customAgentService;
    }

    /** 三元返回保留形状（effectiveTenantId 恒当前租户、sharedAgentReadOnly 恒 false）。 */
    public record ResolvedAgent(CustomAgentEntity row, long effectiveTenantId,
                                boolean sharedAgentReadOnly) {
    }

    public ResolvedAgent resolve(String agentId, long sourceTenantId) {
        if (agentId == null || agentId.isEmpty()) {
            return new ResolvedAgent(null, 0, false);
        }
        CustomAgentEntity customAgent = null;
        long effectiveTenantId = 0;
        Long currentTenant = TenantContext.currentTenantId();
        if (currentTenant != null && currentTenant != 0) {
            try {
                var result = customAgentService.getAgentByID(agentId, null);
                customAgent = result == null ? null : result.row();
                effectiveTenantId = currentTenant;
            } catch (RuntimeException e) {
                log.warn("Failed to get custom agent, agent ID: {}, error, using default config", agentId, e);
            }
        }
        return new ResolvedAgent(customAgent, effectiveTenantId, false);
    }

    /**
     * 给未带实体的请求补 {@code agentRow}（im 面：请求只带 agentId）。
     * 已解析（web 面已填实体）或 id 为空 ⇒ 不动；解析失败 ⇒ 保持 null（与 {@link #resolve} 的宽松语义一致）。
     */
    public void resolveInto(QaSupport.QaRequest req) {
        if (req.agentRow != null || req.agentId == null || req.agentId.isEmpty()) {
            return;
        }
        req.agentRow = resolve(req.agentId, 0).row();
    }

    /** config 列（jsonb 文本）→ ObjectNode；非法 → RuntimeException。 */
    public static ObjectNode parseAgentConfig(CustomAgentEntity row) {
        try {
            return (ObjectNode) MAPPER.readTree(row.getConfig() == null ? "{}" : row.getConfig());
        } catch (tools.jackson.core.JacksonException e) {
            throw new RuntimeException("failed to parse agent config: " + e.getMessage(), e);
        }
    }
}
