package com.ragagent.session.controller.vo;

import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.OffsetDateTime;

/**
 * 会话附件的 HTTP 响应体。
 *
 * <p>{@code imageRefs}/{@code metadata} 是已序列化的 JSON 文本，靠 {@link JsonRawValue}
 * 原样嵌入——这两个注解属于线格式，不能丢。</p>
 */
public record TemporaryDocumentRespVo(
        String id,
        Long tenantId,
        String sessionId,
        String fileName,
        String fileType,
        String mimeType,
        Long fileSize,
        String status,
        @JsonRawValue String imageRefs,
        @JsonRawValue String metadata,
        Integer tokenCount,
        Integer chunkCount,
        String errorMessage,
        OffsetDateTime expiresAt,
        OffsetDateTime startedAt,
        OffsetDateTime readyAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
