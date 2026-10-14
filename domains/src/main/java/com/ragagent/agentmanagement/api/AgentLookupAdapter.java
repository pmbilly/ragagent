package com.ragagent.agentmanagement.api;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.ragagent.agentmanagement.domain.CustomAgentEntity;
import com.ragagent.agentmanagement.mapper.CustomAgentMapper;
import com.ragagent.agentmanagement.service.CustomAgentService;
import com.ragagent.agentmanagement.service.builtin.BuiltinAgentRegistry;
import com.ragagent.common.agent.AgentLookup;

/**
 * {@link AgentLookup} 的 agentmanagement 侧实现：读路径（行查询 / 视图成形 / 推荐问题）都发生在属主域。
 *
 * <p>语义与迁移前 embedchannel 内的 {@code ensureAgentOwned} / {@code tryAgent} / {@code suggestedQuestions}
 * 逐一对齐（含"mapper 异常 ⇒ 视为未知"与"内建注册表兜底"两条）。</p>
 */
@Component
public class AgentLookupAdapter implements AgentLookup {

    private final CustomAgentMapper agentMapper;
    private final CustomAgentService agentService;

    public AgentLookupAdapter(CustomAgentMapper agentMapper, CustomAgentService agentService) {
        this.agentMapper = agentMapper;
        this.agentService = agentService;
    }

    @Override
    public boolean isAgentOwned(long tenantId, String agentId) {
        String id = agentId == null ? "" : agentId.trim();
        if (id.isEmpty()) {
            return false;
        }
        try {
            if (agentMapper.getByIDAndTenant(id, tenantId) != null) {
                return true;
            }
        } catch (RuntimeException e) {
            // 查询异常＝不可用 ⇒ 与"未知 agent"同归 false（调用方按各自错误语义收口）
            return false;
        }
        return BuiltinAgentRegistry.isBuiltinAgentID(id);
    }

    @Override
    public AgentView view(String agentId) {
        try {
            CustomAgentEntity row = agentService.getAgentByID(agentId, null).row();
            if (row == null) {
                return null;
            }
            return new AgentView(row.getId(), row.getName(), row.getDescription(), row.getAvatar(),
                    row.getConfig());
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public ArrayNode suggestedQuestions(String agentId, List<String> kbIds, int limit) {
        return agentService.getSuggestedQuestions(agentId, kbIds, null, null, limit, null);
    }
}
