package com.ragagent.vectorstore.controller.vo;

import com.ragagent.retrieval.api.ConnectionConfig;
import com.ragagent.retrieval.api.IndexConfig;
import java.time.OffsetDateTime;

/**
 * 向量库的 HTTP 响应体：键恒输出（{@code deletedAt} 恒 null）。
 *
 * <p>{@code connectionConfig}/{@code indexConfig} 是跨模块 api 形状，按判据直出；
 * 敏感字段的掩码已由源 DTO 完成。</p>
 */
public record VectorStoreRespVo(
        String id,
        long tenantId,
        String name,
        String engineType,
        ConnectionConfig connectionConfig,
        IndexConfig indexConfig,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        String source,
        boolean readOnly) {
}
