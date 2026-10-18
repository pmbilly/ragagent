/**
 * wiki 摄取 · 支撑与 SPI：任务/队列/锁/限流/去重的契约、批上下文与无状态工具
 * （{@code WikiBatchContext}、{@code WikiCleanupScope}、{@code WikiIngestPreviews} 等）。
 *
 * <p>管线阶段与实现留在 {@code com.ragagent.wiki.service.ingest} 根包；本包只放
 * 无回边、可独立复用的叶子类型。通用并发原语（{@code SingleFlight}）在
 * {@code platform.concurrent}。</p>
 */
package com.ragagent.wiki.service.ingest.support;
