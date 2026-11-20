package com.ragagent.knowledge.controller.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.knowledge.domain.EnableStatus;
import com.ragagent.knowledge.domain.ParseStatus;
import com.ragagent.knowledge.domain.SummaryStatus;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * {@code KnowledgeResponse} 的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：Jackson 按声明顺序输出。</p>
 */
public record KnowledgeRespVo(
        String id,
        String knowledgeBaseId,
        List<JsonNode> tags,
        String type,
        String title,
        String description,
        String source,
        String channel,
        ParseStatus parseStatus,
        int pendingSubtasksCount,
        SummaryStatus summaryStatus,
        EnableStatus enableStatus,
        String embeddingModelId,
        String fileName,
        String folderPath,
        String fileType,
        Long fileSize,
        String fileHash,
        long storageSize,
        JsonNode metadata,
        JsonNode customMetadata,
        JsonNode lastFaqImportResult,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime processedAt,
        String errorMessage,
        String knowledgeBaseName) {
}
