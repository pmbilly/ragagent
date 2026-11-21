/**
 * 工具契约面：工具 SPI 与执行结果载荷。
 *
 * <p>当前成员：{@link com.ragagent.framework.shared.tool.AgentTool} / {@link com.ragagent.framework.shared.tool.BaseTool}
 * （纯 JDK 基类，schema 为 {@code Object}）、{@code ToolDefinitions}（已归位到模型运行时的 api 包）、
 * {@link com.ragagent.framework.shared.tool.ToolRequest}、{@link com.ragagent.framework.shared.tool.ToolExecContext}、
 * {@link com.ragagent.framework.shared.tool.ToolResult}、{@link com.ragagent.framework.shared.tool.ToolCancellation}、
 * {@link com.ragagent.framework.shared.tool.ToolOutput}。</p>
 *
 * <p>只在引擎内使用的工具词汇（能力描述、MCP 审批操作面、内建工具基类）已随实现归位到
 * {@code agent-engine} 的 {@code com.ragagent.agentengine.api}；本包只保留跨模块共享的部分。</p>
 */
package com.ragagent.framework.shared.tool;
