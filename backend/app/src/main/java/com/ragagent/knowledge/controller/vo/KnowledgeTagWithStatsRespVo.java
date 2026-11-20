package com.ragagent.knowledge.controller.vo;

import java.time.OffsetDateTime;

/**
 * {@code KnowledgeTagWithStats} 的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：Jackson 按声明顺序输出。</p>
 */
public record KnowledgeTagWithStatsRespVo(
        String id,
        long seqId,
        String knowledgeBaseId,
        String name,
        String color,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        long knowledgeCount,
        long chunkCount) {
}
