package com.ragagent.knowledge.controller.vo;

/**
 * {@code KnowledgeMoveProgress} 的 HTTP 响应体。
 *
 * <p>组件声明顺序即线格式：Jackson 按声明顺序输出。</p>
 */
public record KnowledgeMoveProgressRespVo(
        String taskId,
        String sourceKbId,
        String targetKbId,
        String status,
        int progress,
        int total,
        int processed,
        int failed,
        String message,
        String error,
        long createdAt,
        long updatedAt) {
}
