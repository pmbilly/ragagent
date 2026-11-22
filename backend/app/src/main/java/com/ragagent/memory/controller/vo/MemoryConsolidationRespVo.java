package com.ragagent.memory.controller.vo;

/**
 * 一次整仓回顾结果的 HTTP 响应体：六个字段恒输出。
 *
 * <p>{@code skipped} 未跳过时为 {@code null}；零值表示仓库本来就整洁，不是失败。</p>
 */
public record MemoryConsolidationRespVo(
        int merged,
        int demoted,
        int expired,
        int reviewed,
        int candidates,
        String skipped) {
}
