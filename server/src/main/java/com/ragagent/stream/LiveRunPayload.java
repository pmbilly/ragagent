package com.ragagent.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * live-run 标记在 Redis 里的存储形态。
 *
 * <p>字段序固定。两个键恒输出。</p>
 */
@JsonPropertyOrder({"assistantMessageId", "requestId"})
public record LiveRunPayload(
        @JsonProperty("assistantMessageId") String assistantMessageId,
        @JsonProperty("requestId") String requestId) {
}
