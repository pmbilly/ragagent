/**
 * 工具结果载荷（跨模块剩余部分）：{@code ToolResult}。
 *
 * <p>工具请求、执行上下文与取消语义（{@code ToolRequest} / {@code ToolExecContext} /
 * {@code ToolCancellation} / {@code ToolOutput}）以及 {@code AgentTool} / {@code BaseTool}
 * 已归 {@code agent-engine} 的 {@code com.ragagent.agentengine.api}。</p>
 *
 * <p>{@code ToolResult} 留 framework 的原因：模型运行时（tool_call 结果回填）与
 * agent-engine 两侧都要用它，而两者平级、互不可见。</p>
 */
package com.ragagent.framework.shared.tool;
