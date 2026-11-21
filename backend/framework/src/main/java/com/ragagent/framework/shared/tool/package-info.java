/**
 * 工具契约面（跨模块共享部分）：工具请求 / 执行上下文 / 结果载荷。
 *
 * <p>当前成员：{@link com.ragagent.framework.shared.tool.ToolRequest}、
 * {@link com.ragagent.framework.shared.tool.ToolExecContext}、
 * {@link com.ragagent.framework.shared.tool.ToolResult}、
 * {@link com.ragagent.framework.shared.tool.ToolCancellation}、
 * {@link com.ragagent.framework.shared.tool.ToolOutput}。</p>
 *
 * <p>{@code AgentTool} / {@code BaseTool} 已归 {@code agent-engine} 的
 * {@code com.ragagent.agentengine.api}；本包只保留被 app 与引擎共同引用的载荷。</p>
 */
package com.ragagent.framework.shared.tool;
