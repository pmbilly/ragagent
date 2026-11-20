/**
 * 工具审批域：审批门（{@code Gate}）、待审请求/决议消息、Redis pub/sub 适配与工具策略。
 * 载荷键名与 MCP 侧消费方成对。
 *
 * <p><b>种类</b>：策略库<br>
 * <b>入口</b>：无 HTTP 面；由 MCP / Agent 工具链经契约调用<br>
 * <b>对外契约面</b>：无（消费方按 {@code contract.tool} 的审批载荷交互）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}（Redis pub/sub）。</p>
 *
 * <p>本域有行为、有状态、有外部 I/O（审批决策机、Redis pub/sub、待审请求），
 * 因此独立成域而不是放进平台内核；出向依赖只有 contract / platform。</p>
 *
 * <p><b>数据归属</b>：审批记录与待审数据落在 {@code mcp} 域（{@code mcp_tool_approvals}），
 * 本域只持决策机与执行策略——这是「策略库」而非「数据域」的刻意分工。</p>
 */
package com.ragagent.mcp.approval;
