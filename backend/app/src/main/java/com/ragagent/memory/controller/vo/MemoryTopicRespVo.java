package com.ragagent.memory.controller.vo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 话题计数的 HTTP 响应体。
 *
 * <p>{@code aliases} 可为 {@code null}（存量行未经投影时的形态），键仍恒输出。</p>
 */
public record MemoryTopicRespVo(
        String id,
        String topic,
        List<String> aliases,
        int hits,
        int threshold,
        OffsetDateTime lastSeenAt) {
}
