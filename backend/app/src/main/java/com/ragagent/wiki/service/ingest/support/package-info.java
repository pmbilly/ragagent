/**
 * wiki 摄取 · 支撑：批上下文、去重工具与预览等无状态叶子类型
 * （{@code WikiBatchContext}、{@code WikiCleanupScope}、{@code WikiIngestPreviews} 等）。
 *
 * <p>管线阶段在 {@code com.ragagent.wiki.service.ingest} 根包；队列/锁/限流的端口与实现
 * 在 {@code com.ragagent.wiki.repository.ingest}。通用并发原语（{@code SingleFlight}）在
 * {@code platform.concurrent}。</p>
 */
package com.ragagent.wiki.service.ingest.support;
