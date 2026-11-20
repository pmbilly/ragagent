package com.ragagent.knowledge.controller.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * {@code ChunkResponse} 的 HTTP 响应体。
 *
 * <p>字段声明顺序即线格式：Jackson 按 record 组件顺序输出。</p>
 */
public record ChunkRespVo(
        String id,
        Long seqId,
        String knowledgeId,
        String knowledgeBaseId,
        String tagId,
        String content,
        int contentRevision,
        String indexStatus,
        String lastEditorId,
        int chunkIndex,
        boolean enabled,
        int flags,
        int status,
        int startAt,
        int endAt,
        String preChunkId,
        String nextChunkId,
        String chunkType,
        String parentChunkId,
        JsonNode relationChunks,
        JsonNode indirectRelationChunks,
        JsonNode metadata,
        String contentHash,
        String imageInfo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
