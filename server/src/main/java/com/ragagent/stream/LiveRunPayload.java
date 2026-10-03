package com.ragagent.stream;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * live-run 标记在 Redis 里的存储形态。
 *
 * <p>字段序固定。两个键恒输出。</p>
 */
@JsonPropertyOrder({"assistant_message_id", "request_id"})
public record LiveRunPayload(
        @JsonProperty("assistant_message_id") String assistantMessageId,
        @JsonProperty("request_id") String requestId) {
}
