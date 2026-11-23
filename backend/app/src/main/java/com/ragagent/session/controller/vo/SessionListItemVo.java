package com.ragagent.session.controller.vo;

import com.ragagent.channel.api.SessionLastRequestState;
import java.time.OffsetDateTime;

/**
 * 会话列表行的 HTTP 响应体：会话字段 + IM 来源字段平铺。
 *
 * <p>平铺而非继承：键序＝声明序才受控，继承会让两个 {@code imPlatform} 撞成重复键。</p>
 */
public record SessionListItemVo(
        String id,
        String title,
        String description,
        Long tenantId,
        String userId,
        boolean pinned,
        OffsetDateTime pinnedAt,
        SessionLastRequestState lastRequestState,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt,
        String imPlatform,
        String imChatId,
        String imThreadId,
        String imUserId,
        String imAgentId,
        String imChannelId) {
}
