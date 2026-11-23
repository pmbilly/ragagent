package com.ragagent.session.controller.vo;

import java.time.OffsetDateTime;

/**
 * 消息搜索结果里的合并 Q&amp;A 对。
 *
 * <p>{@code score} 走 Jackson 默认 double 序列化（{@code 1.0} 形态）。</p>
 */
public record MessageSearchGroupItemVo(
        String requestId,
        String sessionId,
        String sessionTitle,
        String queryContent,
        String answerContent,
        double score,
        String matchType,
        OffsetDateTime createdAt) {
}
