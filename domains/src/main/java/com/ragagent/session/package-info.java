/**
 * 会话域：会话与消息读写、两条问答入口（知识库问答 / Agent 问答）、SSE 流式桥与
 * 会话侧管线装配（{@code rag/}：14 个阶段、17 个插件）。
 *
 * <p><b>种类</b>：产品能力<br>
 * <b>入口</b>：{@code KnowledgeQaController}、{@code SessionController}、{@code SessionStreamController}<br>
 * <b>对外契约面</b>：{@code SessionQaPort}、{@code EmbedSessionPort}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}、{@code model-runtime}（仅 modelcontext 终态边）。</p>
 *
 * <p>对外只暴露 controller 的 HTTP 面与 contract.session 的载荷端口；memory 蒸馏、
 * 问答管线往返都用载荷而非本域实体。</p>
 */
package com.ragagent.session;
