/**
 * 管线 SPI（R4b 契约化）：`chatpipeline` 消费、业务域实现的端口（MCP 工具网关/按需注册、
 * 工具注册表、网页检索执行器）。
 *
 * <p><b>为什么在契约面</b>：它们的实现散在 mcp / knowledge / evaluation / agenttools 等域，
 * 而消费方是管线本体——放实现包里会把整条管线拖成"跨域依赖"。留在这里，域侧只认契约。</p>
 *
 * <p>{@code KnowledgeQaEventPort} 未随迁：其签名取管线上下文 {@code ChatManage}（内部件），
 * 与本体一起留在实现侧。</p>
 */
package com.ragagent.contract.pipeline;
