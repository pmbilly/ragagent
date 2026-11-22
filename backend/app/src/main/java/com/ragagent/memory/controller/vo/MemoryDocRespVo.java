package com.ragagent.memory.controller.vo;

import java.time.OffsetDateTime;

/**
 * 文档亲和度的 HTTP 响应体：六个字段恒输出。
 */
public record MemoryDocRespVo(
        String id,
        String knowledgeId,
        String knowledgeBaseId,
        String title,
        int hits,
        OffsetDateTime lastUsedAt) {
}
