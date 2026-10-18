/**
 * 嵌入渠道 · 支撑件：错误文案（{@link com.ragagent.embedchannel.support.EmbedError}）、
 * 令牌工具（{@link com.ragagent.embedchannel.support.EmbedTokens}）与限流
 * （{@link com.ragagent.embedchannel.support.EmbedRateLimiter}）。
 *
 * <p>控制器 filter 与 service 都依赖本包，根包只留装配（{@code EmbedWiring}）——
 * 这样三者不构成包级环。</p>
 */
package com.ragagent.embedchannel.support;
