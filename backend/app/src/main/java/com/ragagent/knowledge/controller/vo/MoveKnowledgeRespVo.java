package com.ragagent.knowledge.controller.vo;

/**
 * {@code MoveKnowledgeResponse} 的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：Jackson 按声明顺序输出。</p>
 */
public record MoveKnowledgeRespVo(
        String taskId,
        String sourceKbId,
        String targetKbId,
        int knowledgeCount) {
}
