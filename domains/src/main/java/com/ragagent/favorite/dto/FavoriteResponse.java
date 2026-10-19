package com.ragagent.favorite.dto;

import com.ragagent.favorite.domain.UserResourceFavorite;
import java.time.OffsetDateTime;

/** 用户收藏响应：字段名/顺序对齐实体既有 JSON。 */
public record FavoriteResponse(
        String userId,
        Long tenantId,
        String resourceType,
        String resourceId,
        OffsetDateTime createdAt) {

    public static FavoriteResponse from(UserResourceFavorite favorite) {
        return new FavoriteResponse(
                favorite.getUserId(),
                favorite.getTenantId(),
                favorite.getResourceType(),
                favorite.getResourceId(),
                favorite.getCreatedAt());
    }
}
