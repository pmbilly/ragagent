/**
 * 内置智能体面（B264① 从 {@code agentmanagement.service} 切出，对齐方案 §8.8.3）：
 * {@code BuiltinAgentRegistry}——内置智能体定义、类型与提示词的注册表。
 *
 * <p><b>切出依据</b>：它被用例层、控制器与其它域（{@code embedchannel} / {@code im} /
 * {@code agent.prompt}）读取，对 {@code agentmanagement.service} **零反向边**（已动前断言）
 * ⇒ 单向边 {@code service → service.builtin}。</p>
 */
package com.ragagent.agentmanagement.service.builtin;
