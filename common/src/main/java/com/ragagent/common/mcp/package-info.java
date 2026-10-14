/**
 * MCP 操作 L1 端口面：跨域消费方（embed 渠道）经此转 MCP OAuth 授权与工具审批解析，不直连 mcp 的 controller。
 *
 * <p>归属：L1 底座（{@code common.<domain>} 端口形态，与 {@code common/agent}、{@code common/knowledge}
 * 同族）。实现由 mcp 域提供（{@code mcp/api/McpApprovalOpsAdapter}）。</p>
 */
package com.ragagent.common.mcp;
