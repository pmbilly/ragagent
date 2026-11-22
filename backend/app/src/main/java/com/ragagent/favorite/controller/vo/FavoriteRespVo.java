package com.ragagent.favorite.controller.vo;

import java.time.OffsetDateTime;

/**
 * 收藏项的 HTTP 响应体。
 *
 * <p>字段名与声明顺序即线格式：Jackson 按声明顺序输出。</p>
 */
public record FavoriteRespVo(
        String userId,
        Long tenantId,
        String resourceType,
        String resourceId,
        OffsetDateTime createdAt) {
}
