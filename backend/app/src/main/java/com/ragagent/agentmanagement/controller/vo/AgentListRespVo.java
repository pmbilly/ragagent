package com.ragagent.agentmanagement.controller.vo;

import java.util.List;

/**
 * 智能体清单的 HTTP 响应体（外壳 {@code data} 的内容，外壳由框架统一施加）。
 */
public record AgentListRespVo(
        List<AgentViewRespVo> agents,
        List<String> disabledOwnAgentIds) {
}
