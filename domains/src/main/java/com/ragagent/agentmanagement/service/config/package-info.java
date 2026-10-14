/**
 * 智能体配置面（从 {@code agentmanagement.service} 切出，对齐方案 §8.8.3）：
 * agent config jsonb 读写（{@code AgentConfigJson}）、占位符（{@code AgentPlaceholders}）、
 * 类型预设（{@code AgentTypePresets}）。
 *
 * <p><b>切出依据</b>：三类都是**无状态配置/模板读写**——被用例层（{@code CustomAgentService}）、
 * 控制器与其它域读取；它们对 {@code agentmanagement.service} **零反向边**（动前断言 + 编译实测）
 * ⇒ 只有 {@code service → service.config} 单向边，包级 SCC 不扩大。</p>
 *
 * <p><b>为什么 {@code AgentSuggestedQuestions} 没进来</b>：它反向持有 {@code CustomAgentService}
 * （推荐问题的 KB scope 解析要读 agent 定义）⇒ 放本面会造 {@code service ⇄ service.config} 环；
 * 它本质是**用例**（编排核心服务），留 {@code service}。</p>
 */
package com.ragagent.agentmanagement.service.config;
