package com.ragagent.session.controller.vo;

import java.util.List;

/** {@code GET /api/v1/sessions} 的分页响应体：items / page / pageSize / total。 */
public record SessionListRespVo(
        List<SessionListItemVo> items,
        int page,
        int pageSize,
        long total) {
}
