package com.ragagent.knowledge.controller.vo;

import java.time.OffsetDateTime;

/**
 * {@code ChunkRevisionResponse} 的 HTTP 响应体。
 *
 * <p>字段声明顺序即线格式：Jackson 按 record 组件顺序输出。</p>
 */
public record ChunkRevisionRespVo(
        String id,
        String knowledgeBaseId,
        String knowledgeId,
        String chunkId,
        int revision,
        String content,
        boolean enabled,
        String editorId,
        String editSource,
        OffsetDateTime editedAt,
        OffsetDateTime createdAt) {
}
