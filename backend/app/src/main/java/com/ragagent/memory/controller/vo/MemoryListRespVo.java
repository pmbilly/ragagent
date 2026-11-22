package com.ragagent.memory.controller.vo;

import java.util.List;

/**
 * 记忆管理器三类列表共用的分页响应体：{@code {items, page, pageSize, total}}。
 *
 * <p>本类型只承载分页外壳，元素形状由装配层给定的 VO 决定。</p>
 */
public record MemoryListRespVo<T>(
        List<T> items,
        long page,
        long pageSize,
        long total) {
}
