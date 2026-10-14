/**
 * session 的 SSE 传输面（由 {@code session.sse} 迁入 {@code controller.sse}，对齐方案 §8.8.1）：
 * {@code SseContract}（帧契约）、{@code SseFrameWriter}（帧写出）、{@code StreamEventEmitter}
 * （事件 → SSE 帧）、{@code StreamResponseBuilder}（响应组装 + Redis 回放引用重建）。
 *
 * <p><b>切出/迁入依据</b>：四类对 session 域内包**零依赖**（出边只到 {@code common}/{@code llm}/
 * {@code storage}/{@code platform}），消费者是控制器（{@code SessionStreamController} /
 * {@code KnowledgeQaController}）与 {@code qa} 编排 ⇒ 迁入后只有 {@code controller → controller.sse}
 * 与 {@code qa → controller.sse} 的单向边，包级 SCC 不扩大。</p>
 */
package com.ragagent.session.controller.sse;
