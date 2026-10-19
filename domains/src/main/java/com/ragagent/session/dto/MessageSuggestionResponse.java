package com.ragagent.session.dto;

import com.ragagent.contract.session.SuggestionItem;
import com.ragagent.session.domain.message.MessageSuggestionSet;
import java.time.OffsetDateTime;
import java.util.List;

/** 消息建议集合响应：字段名/顺序对齐实体既有 JSON。 */
public record MessageSuggestionResponse(
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

    public static MessageSuggestionResponse from(MessageSuggestionSet set) {
        return new MessageSuggestionResponse(
                set.getId(),
                set.getTenantId(),
                set.getSessionId(),
                set.getAssistantMessageId(),
                set.getAgentId(),
                set.getPlacement(),
                set.getConfigHash(),
                set.getLocale(),
                set.getStatus(),
                set.isAllowRegenerate(),
                set.getSuppressionReason(),
                set.getQuestions(),
                set.getModelId(),
                set.getPromptTokens(),
                set.getCompletionTokens(),
                set.getLatencyMs(),
                set.getErrorCode(),
                set.getGeneratedAt(),
                set.getCreatedAt(),
                set.getUpdatedAt());
    }
}
