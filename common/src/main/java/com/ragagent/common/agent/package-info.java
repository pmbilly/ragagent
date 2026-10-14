/**
 * agent 目录 L1 端口面：跨域读 agent 元信息 / 配置 / 推荐问题（实现由 agentmanagement 提供）。
 *
 * <p>归属：L1 底座（{@code common.<domain>} 端口形态，与 {@code common/knowledge}、{@code common/session}
 * 同族）。方案 §8.1 的 {@code contract.agent}（纯 JDK typed records）是终态；本面在「{@code JsonNode}
 * 载体」K 级重设计后上提契约面。</p>
 */
package com.ragagent.common.agent;
