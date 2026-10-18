/**
 * 工具契约面：工具执行结果载荷与工具取消/能力词汇。
 *
 * <p>当前成员：{@link com.ragagent.contract.tool.ToolResult}（结果载荷）、
 * {@link com.ragagent.contract.tool.ToolCancellation}（取消探测，A③-⑨ 自 `agent.tool` 迁入）、
 * {@link com.ragagent.contract.tool.ToolCapabilities}（内建工具的 KB 能力词汇，与前端
 * {@code tool-capabilities.ts} 镜像，同为 A③-⑨ 迁入）；**审批协议（A③-⑪ 自 `approval/` 迁入）**：
 * {@link com.ragagent.contract.tool.ToolApprovalGate}（闸门 SPI，实现唯一＝`approval.Gate`）、
 * {@link com.ragagent.contract.tool.Cancellation}、{@link com.ragagent.contract.tool.Decision}、
 * {@link com.ragagent.contract.tool.PendingRequest}、{@link com.ragagent.contract.tool.OAuthPendingRequest}、
 * {@link com.ragagent.contract.tool.ApprovalEventBus}、{@link com.ragagent.contract.tool.ApprovalEvent}。</p>
 *
 * <p>工具 SPI：{@link com.ragagent.contract.tool.AgentTool} / {@link com.ragagent.contract.tool.BaseTool}
 * （纯 JDK 基类，schema 为 {@code Object}）/ {@code ToolRequest} / {@code ToolExecContext} /
 * {@code ToolRegistryPort}。{@code ToolDefinitions} 仍在 {@code agent-engine}（定义与注册逻辑）。</p>
 */
package com.ragagent.contract.tool;
