/**
 * 审批机制域：审批门（Gate）、待审请求/决议消息、Redis pubsub 适配与工具策略。载荷键名与 mcp 侧消费方成对。
 *
 * <p><b>为什么是独立域而不是 {@code common}</b>：本包曾因"mcp 的工具审批也要用"（mcp 反向依赖 agent 的
 * 唯一来源）被搬进 {@code common.approval}。但它是<b>有行为、有状态、有外部 I/O</b> 的子系统（Gate 决策机、
 * Redis pub/sub、HTTP 待审请求），放 common 会让"共享内核"长实现（B101 的 R6 就是为盯这类痕迹）。
 * 独立成域同样解环：{@code mcp/agent/im → approval ← common}，出向依赖**只有 common**（B102 验证），
 * 因此不引入任何环。</p>
 */
package com.ragagent.approval;
