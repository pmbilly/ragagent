/**
 * <b>种类</b>：集成适配
 * <b>入口</b>：渠道回调适配与挂件会话端口（{@code channel.<vendor>} 各厂商适配器、{@code api.EmbedSessionPort}）
 * <b>对外契约面</b>：会话视图、QA 请求载荷、问答端口、挂件会话端口与消息附件（供 session 编排与挂件/IM 消费）
 * <b>依赖</b>：framework、agent-engine
 *
 * <p>IM 长连接渠道与挂件渠道的合并域（蓝图渠道内部重组项）；URL 与线格式对前端零变化。</p>
 */
package com.ragagent.channel;
