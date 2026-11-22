package com.ragagent.agentmanagement.controller.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;

/**
 * 智能体的 HTTP 响应体。
 *
 * <p>键序由 {@link JsonPropertyOrder} 固定；{@code deletedAt} 恒输出 null，
 * {@code creatorName} 为空则整键省略——只有它挂 {@link JsonInclude}。</p>
 *
 * <p>{@code createdAt}/{@code updatedAt} 是 {@link Object}：零值时间输出
 * {@code "0001-01-01T00:00:00Z"} 常量字符串，正常时间输出 {@link java.time.OffsetDateTime}，
 * 两种形态都必须保留。</p>
 */
@JsonPropertyOrder({
    "id", "name", "description", "avatar", "builtin", "tenantId", "createdBy",
    "config", "createdAt", "updatedAt", "deletedAt", "creatorName"
})
public record AgentViewRespVo(
        String id,
        String name,
        String description,
        String avatar,
        boolean builtin,
        long tenantId,
        String createdBy,
        Map<String, Object> config,
        Object createdAt,
        Object updatedAt,
        Object deletedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String creatorName) {
}
