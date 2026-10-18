/**
 * 嵌入渠道域：对外暴露的嵌入问答渠道、渠道配置与令牌，以及渠道鉴权过滤器。
 * 与 {@code model-runtime} 的 embedding 客户端无关：本域是业务渠道。
 *
 * <p><b>种类</b>：集成适配<br>
 * <b>入口</b>：{@code EmbedChannelController}<br>
 * <b>对外契约面</b>：无（会话能力走 {@code EmbedSessionPort}）<br>
 * <b>依赖</b>：{@code contract}、{@code platform}。</p>
 */
package com.ragagent.embedchannel;
