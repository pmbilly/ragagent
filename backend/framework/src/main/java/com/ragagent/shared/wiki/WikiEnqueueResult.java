package com.ragagent.shared.wiki;

/**
 * Wiki 摄取入队结果：{@code accepted=false} 时 {@code error} 说明原因（调用方据此决定是否记日志）。
 *
 * <p>原在 {@code wiki.service.ingest.support}，随 {@link WikiIngestPort} 上提——knowledge 侧要
 * 消费该结果，而契约不能反向依赖域内包。</p>
 */
public record WikiEnqueueResult(boolean accepted, Exception error) {
}
