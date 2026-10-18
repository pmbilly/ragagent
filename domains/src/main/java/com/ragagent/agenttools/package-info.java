/**
 * Agent 工具实现面：把工具调用翻译为业务域公开用例，并把结果格式化为模型可读输出。
 * 按能力分族：{@code wiki/}、{@code knowledge/}、{@code sql/}、{@code data/}、{@code web/}。
 *
 * <p><b>种类</b>：产品能力<br>
 * <b>入口</b>：无 HTTP 面；由 Agent 工具注册表装配<br>
 * <b>对外契约面</b>：无（实现 {@code contract.tool.AgentTool}）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 *
 * <p>方向固定为“实现面 → 运行面”：工具契约、注册表、schema 校验与执行预算在
 * {@code agent-engine} 的 {@code agent.tool} 面；本域只实现具体工具，不反向依赖运行面。</p>
 */
package com.ragagent.agenttools;
