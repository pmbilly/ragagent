/**
 * MCP 域：Model Context Protocol 的仓内实现——协议、OAuth（含回调与令牌存储）、
 * 服务注册与调用面；对外 {@code /api/v1/mcp-services}。Agent 运行时通过工具注册表挂载 MCP 工具。
 *
 * <p><b>种类</b>：集成适配<br>
 * <b>入口</b>：{@code McpServiceController}、{@code McpOAuthController}<br>
 * <b>对外契约面</b>：{@code McpToolRegistrationPort}、{@code McpToolingGateway}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.mcp;
