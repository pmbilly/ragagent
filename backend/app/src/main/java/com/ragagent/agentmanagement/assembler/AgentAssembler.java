package com.ragagent.agentmanagement.assembler;

import com.ragagent.agentmanagement.controller.vo.AgentListRespVo;
import com.ragagent.agentmanagement.controller.vo.AgentViewRespVo;
import com.ragagent.agentmanagement.dto.AgentResponses;
import com.ragagent.agentmanagement.dto.AgentView;
import java.util.List;

/** 智能体视图到 HTTP VO 的装配。 */
public final class AgentAssembler {

    private AgentAssembler() {
    }

    /** 装配单个智能体。 */
    public static AgentViewRespVo toVo(AgentView src) {
        if (src == null) {
            return null;
        }
        return new AgentViewRespVo(
                src.id(),
                src.name(),
                src.description(),
                src.avatar(),
                src.builtin(),
                src.tenantId(),
                src.createdBy(),
                src.config(),
                src.createdAt(),
                src.updatedAt(),
                src.deletedAt(),
                src.creatorName());
    }

    /** 批量装配，保持原有顺序。 */
    public static List<AgentViewRespVo> toVoList(List<AgentView> src) {
        if (src == null) {
            return null;
        }
        return src.stream().map(AgentAssembler::toVo).toList();
    }

    /** 装配清单载荷；{@code disabledOwnAgentIds} 的空值兜底已在构造侧完成。 */
    public static AgentListRespVo toListVo(AgentResponses.AgentListPayload src) {
        if (src == null) {
            return null;
        }
        return new AgentListRespVo(toVoList(src.agents()), src.disabledOwnAgentIds());
    }
}
