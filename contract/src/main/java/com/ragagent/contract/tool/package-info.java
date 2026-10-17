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
 * <p><b>尚未迁入（登记）</b>：工具 SPI 主体（{@code AgentTool}/{@code BaseTool}/{@code ToolDefinitions}/
 * {@code ToolRequest}/{@code ToolExecContext}/{@code ToolRegistryPort}）——其签名带 Jackson
 * {@code JsonNode}、{@code platform.event.EventBus}、{@code llm.domain.ChatMessage} ⇒ 属 A② 登记的
 * 「JsonNode 载体」K 级重设计；解决后 {@code mcp→agent} 边（9 类）随之清零。</p>
 */
package com.ragagent.contract.tool;
