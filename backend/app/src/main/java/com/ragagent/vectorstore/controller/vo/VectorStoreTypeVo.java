package com.ragagent.vectorstore.controller.vo;

import java.util.List;

/**
 * 向量库类型元数据的 HTTP 响应体：条目、字段顺序与 {@code default} 的有无都是既定契约。
 */
public record VectorStoreTypeVo(
        String type,
        String displayName,
        List<FieldInfoVo> connectionFields,
        List<FieldInfoVo> indexFields) {

    /** 单个配置字段的描述：十个键恒输出（min/max/enumValues 无约束时为 null）。 */
    public record FieldInfoVo(
            String name,
            String type,
            boolean required,
            boolean sensitive,
            Object defaultValue,
            String description,
            boolean immutable,
            Double min,
            Double max,
            List<String> enumValues) {
    }
}
