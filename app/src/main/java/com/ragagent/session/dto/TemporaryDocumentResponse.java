package com.ragagent.session.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.ragagent.session.domain.document.TemporaryDocument;
import java.time.OffsetDateTime;

/** 会话附件响应：字段名/顺序与可见字段对齐实体既有 JSON。 */
public record TemporaryDocumentResponse(
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

    public static TemporaryDocumentResponse from(TemporaryDocument document) {
        return new TemporaryDocumentResponse(
                document.getId(),
                document.getTenantId(),
                document.getSessionId(),
                document.getFileName(),
                document.getFileType(),
                document.getMimeType(),
                document.getFileSize(),
                document.getStatus(),
                document.getImageRefs(),
                document.getMetadata(),
                document.getTokenCount(),
                document.getChunkCount(),
                document.getErrorMessage(),
                document.getExpiresAt(),
                document.getStartedAt(),
                document.getReadyAt(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }
}
