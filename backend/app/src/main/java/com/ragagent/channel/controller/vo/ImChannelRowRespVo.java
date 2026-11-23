package com.ragagent.channel.controller.vo;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/**
 * IM 渠道资源行的 HTTP 响应体（create / update / toggle）。
 *
 * <p>键名与声明顺序即线格式；{@code deletedAt} 恒输出 null。</p>
 */
public record ImChannelRowRespVo(
        String id,
        Long tenantId,
        String agentId,
        String platform,
        String name,
        boolean enabled,
        String mode,
        String outputMode,
        String knowledgeBaseId,
        String botIdentity,
        String sessionMode,
        JsonNode credentials,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Object deletedAt) {
}
