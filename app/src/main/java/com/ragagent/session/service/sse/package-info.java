/**
 * 会话 · SSE 组装（传输无关）：事件 → 响应体（{@code StreamResponseBuilder}）、
 * 重写与写出（{@code StreamEventEmitter}）、帧写出端口（{@code SseFrameSink}）。
 *
 * <p>Servlet 直写与头部契约留在 {@code session.controller.sse}；本包不依赖
 * {@code jakarta.servlet}，供编排层与控制器共用。</p>
 */
package com.ragagent.session.service.sse;
