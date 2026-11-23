package com.ragagent.session.controller.vo;

import java.time.OffsetDateTime;

/**
 * 产物元数据的 HTTP 响应体（会话 / 消息两个 artifacts 端点的元素）。
 *
 * <p>不含存储 URL；可取句柄由 {@code handle} 表达（不可取时为 null）。</p>
 */
public record ArtifactRespVo(
        int index,
        String handle,
        String fileName,
        String fileType,
        long fileSize,
        String sourcePath,
        OffsetDateTime modTime,
        OffsetDateTime createdAt) {
}
