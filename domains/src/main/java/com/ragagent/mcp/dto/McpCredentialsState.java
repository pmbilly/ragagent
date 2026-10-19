package com.ragagent.mcp.dto;

/** MCP 凭据“已配置”状态；控制器不再读取 McpService 实体。 */
public record McpCredentialsState(boolean apiKeyConfigured, boolean tokenConfigured) {
}
