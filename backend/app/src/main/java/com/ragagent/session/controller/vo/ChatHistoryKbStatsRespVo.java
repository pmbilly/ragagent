package com.ragagent.session.controller.vo;

/**
 * 聊天历史知识库统计的 HTTP 响应体：六个键全部恒输出（空串写 ""，0/false 照写）。
 */
public record ChatHistoryKbStatsRespVo(
        boolean enabled,
        String embeddingModelId,
        String knowledgeBaseId,
        String knowledgeBaseName,
        long indexedMessageCount,
        boolean hasIndexedMessages) {
}
