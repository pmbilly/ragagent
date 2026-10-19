/**
 * 管线 SPI：会话域管线（{@code domains/session/rag}）消费、业务域实现的端口
 * （MCP 工具网关/按需注册、工具注册表、网页检索执行器）。
 *
 * <p>实现散在 mcp / knowledge / agenttools 等域，消费方是管线本体；
 * 放实现包里会把整条管线拖成跨域依赖。契约面只留端口，域侧只认契约。</p>
 *
 * <p>{@code KnowledgeQaEventPort} 不在本包：其签名取管线上下文 {@code ChatManage}（内部件），
 * 与本体一起留在实现侧。</p>
 */
package com.ragagent.contract.pipeline;
