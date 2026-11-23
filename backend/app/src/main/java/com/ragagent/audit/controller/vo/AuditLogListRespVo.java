package com.ragagent.audit.controller.vo;

import java.util.List;

/**
 * 审计列表的 HTTP 响应体（游标分页：{@code {items, nextCursor}}）。
 *
 * <p>空页 {@code items} 是 {@code []}、{@code nextCursor} 是 0。</p>
 */
public record AuditLogListRespVo(
        List<AuditLogItemVo> items,
        long nextCursor) {
}
