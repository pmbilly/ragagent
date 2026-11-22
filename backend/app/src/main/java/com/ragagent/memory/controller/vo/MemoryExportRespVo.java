package com.ragagent.memory.controller.vo;

import java.util.List;

/**
 * 记忆导出的 HTTP 响应体：{@code {items, total, truncated}}。
 *
 * <p>空仓库时 {@code items} 为 {@code null} 而非 {@code []}——这是既有契约，装配层不得兜底。</p>
 */
public record MemoryExportRespVo(
        List<MemoryItemRespVo> items,
        long total,
        boolean truncated) {
}
