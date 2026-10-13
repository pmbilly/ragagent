/**
 * agent 工具**实现**面：把 Agent 的工具调用翻译为业务域公开用例，并把结果格式化为模型可读输出。
 *
 * <p>按能力分族：{@code wiki/}（页面工具与视图）、{@code knowledge/}（知识检索工具）、
 * {@code sql/}（SQL 守卫与 database_query）、{@code data/}（DuckDB 分析）、{@code web/}（网页两件）。</p>
 *
 * <p>**不在此面**（B260 已迁出，归 Agent 运行面的工具框架 {@code com.ragagent.agent.tool}）：
 * 工具基类与契约（{@code AgentTool}/{@code BaseTool}）、注册表（{@code ToolRegistry}）、
 * schema/参数校验（{@code ToolDefinitions}/{@code ParamValidator}…）、执行上下文与预算、
 * 输出持久化、流式修正（{@code agent.tool.runtime}）。方向固定为
 * **实现面 → 运行面**（{@code agenttools → agent.tool}），反向由
 * {@code ArchitectureRulesTest}「B260」零容忍守卫。</p>
 */
package com.ragagent.agenttools;
