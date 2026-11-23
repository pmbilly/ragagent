package com.ragagent.session.controller.vo;

import com.ragagent.channel.api.SuggestionItem;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 消息建议集合的 HTTP 响应体：字段名与顺序对齐实体既有 JSON。
 *
 * <p>{@code questions} 的元素是跨域 api 形状（{@code channel/api}），按判据直出。</p>
 */
public record MessageSuggestionRespVo(
        String id,
        Long tenantId,
        String sessionId,
        String assistantMessageId,
        String agentId,
        String placement,
        String configHash,
        String locale,
        String status,
        boolean allowRegenerate,
        String suppressionReason,
        List<SuggestionItem> questions,
        String modelId,
        int promptTokens,
        int completionTokens,
        long latencyMs,
        String errorCode,
        OffsetDateTime generatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
