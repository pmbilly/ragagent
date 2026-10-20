package com.ragagent.contract.pagination;

import java.util.List;

/**
 * 统一分页结果。
 *
 * <p>HTTP 响应、域内仓储、跨域端口共用一种形状：{@code items / total / page / pageSize}。
 * 只关心"有哪些、共几条"的内部场景用两参工厂，页号与页大小填 0。</p>
 *
 * <p>纯 JDK：Jackson 按 record 组件序列化，不需要 mixin。</p>
 */
public record PageResult<T>(List<T> items, long total, int page, int pageSize) {

    public PageResult {
        items = items == null ? List.of() : items;
    }

    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        return new PageResult<>(items, total, page, pageSize);
    }

    /** 内部/端口场景：不带页号与页大小。 */
    public static <T> PageResult<T> of(List<T> items, long total) {
        return new PageResult<>(items, total, 0, 0);
    }
}
