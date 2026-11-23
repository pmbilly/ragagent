package com.ragagent.channel.controller.vo;

import java.time.OffsetDateTime;

/**
 * IM 渠道列表行的 HTTP 响应体：凭据不出行，只出 {@code credentialsConfigured}。
 */
public record ImChannelSummaryRespVo(
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
        boolean credentialsConfigured,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
