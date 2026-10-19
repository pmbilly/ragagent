package com.ragagent.datasource.dto;

import com.ragagent.datasource.domain.Resource;
import java.time.OffsetDateTime;
import java.util.Map;

/** 数据源可同步资源响应：字段名/顺序对齐实体既有 JSON。 */
public record ResourceResponse(
        String externalId,
        String name,
        String type,
        String description,
        String url,
        OffsetDateTime modifiedAt,
        String parentId,
        boolean hasChildren,
        Map<String, Object> metadata) {

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getExternalId(),
                resource.getName(),
                resource.getType(),
                resource.getDescription(),
                resource.getUrl(),
                resource.getModifiedAt(),
                resource.getParentId(),
                resource.isHasChildren(),
                resource.getMetadata());
    }
}
