package com.ragagent.session.repository;

import java.time.OffsetDateTime;

/**
 * 记忆蒸馏读会话消息时的最小视图（四字段）。
 *
 * <p>只暴露蒸馏需要的列，避免把 {@code Message} 实体交给记忆域。</p>
 */
public record SessionMessageView(String id, String role, String content, OffsetDateTime createdAt) {
}
