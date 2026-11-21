package com.ragagent.knowledge.controller.vo;

import com.ragagent.knowledge.api.kb.KnowledgeBaseResponse;

/**
 * {@code DuplicateKnowledgeBaseResponse} 的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：Jackson 按声明顺序输出。</p>
 */
public record DuplicateKnowledgeBaseRespVo(
        String sourceId,
        String targetId,
        KnowledgeBaseResponse knowledgeBase) {
}
