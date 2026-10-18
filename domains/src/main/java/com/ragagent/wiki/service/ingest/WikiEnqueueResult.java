package com.ragagent.wiki.service.ingest;

/** 入队结果：{@code accepted=false} 时 {@code error} 说明原因（调用方据此决定是否记日志）。 */
public record WikiEnqueueResult(boolean accepted, Exception error) {
}
