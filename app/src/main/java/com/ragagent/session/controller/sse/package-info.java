/**
 * 会话 · SSE 传输面：{@code SseContract}（头部契约）与 {@code SseFrameWriter}
 * （Servlet 直写 + 帧组装）。
 *
 * <p>传输无关的事件→响应组装在 {@code session.service.sse}
 * （{@code StreamEventEmitter} / {@code StreamResponseBuilder} / {@code SseFrameSink}）；
 * 本包只保留与 Servlet 强绑定的两端，供控制器使用。编排层依赖
 * {@code SseFrameSink} 端口，不反向依赖本包。</p>
 */
package com.ragagent.session.controller.sse;
