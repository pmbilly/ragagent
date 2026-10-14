/**
 * MCP 工具实现族（方案 §8.9 的 {@code tool/}）：把 MCP 服务暴露的工具翻译为 Agent 工具——
 * 调用（{@code McpCallTool}）、发现与目录（{@code McpDiscoverTool} / {@code McpCatalog} /
 * {@code McpCatalogPagination}）、注册与包装（{@code McpRegisteredTool} / {@code McpToolWrapper}）、
 * 输入校验与提示（{@code McpInputSchemaValidator} / {@code McpOAuthPromptSupport}）与
 * 曝光/装配（{@code McpExposure} / {@code McpTooling}）。
 */
package com.ragagent.mcp.tool;
