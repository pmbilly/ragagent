package com.ragagent.knowledge.controller.vo;

import java.util.List;

/**
 * {@code KnowledgeListResponse} 的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：items / page / pageSize / total。</p>
 */
public record KnowledgeListRespVo(
        List<KnowledgeRespVo> items,
        long page,
        long pageSize,
        long total) {
}
