package com.ragagent.mcp.dto;

import com.ragagent.mcp.domain.McpToolApproval;
import java.time.OffsetDateTime;

/** MCP 工具审批策略响应：字段名/顺序对齐实体既有 JSON。 */
public record McpToolApprovalResponse(
        String id,
        Long tenantId,
        String serviceId,
        String toolName,
        boolean requireApproval,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static McpToolApprovalResponse from(McpToolApproval row) {
        return new McpToolApprovalResponse(
                row.getId(),
                row.getTenantId(),
                row.getServiceId(),
                row.getToolName(),
                row.isRequireApproval(),
                row.isEnabled(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
