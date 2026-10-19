package com.ragagent.im.repository;

import com.ragagent.im.domain.ChannelSessionEntity;
import com.ragagent.im.mapper.ChannelSessionMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 渠道会话映射仓储门面（方法为 {@link ChannelSessionMapper} 的薄转发）。 */
@Repository
public class ChannelSessionRepository {

    private final ChannelSessionMapper mapper;

    public ChannelSessionRepository(ChannelSessionMapper mapper) {
        this.mapper = mapper;
    }

    public ChannelSessionEntity findUserSession(String platform, String userId, String chatId,
            long tenantId, String agentId) {
        return mapper.findUserSession(platform, userId, chatId, tenantId, agentId);
    }

    public ChannelSessionEntity findThreadSession(String platform, String chatId, String threadId,
            long tenantId, String agentId) {
        return mapper.findThreadSession(platform, chatId, threadId, tenantId, agentId);
    }

    public ChannelSessionEntity findBySessionId(String sessionId) {
        return mapper.findBySessionId(sessionId);
    }

    public int insert(ChannelSessionEntity entity, OffsetDateTime now) {
        return mapper.insert(entity, now);
    }

    public int softDelete(String id, OffsetDateTime now) {
        return mapper.softDelete(id, now);
    }

    public List<String> findUserSessionIds(String platform, String userId, String chatId,
            long tenantId, String agentId) {
        return mapper.findUserSessionIds(platform, userId, chatId, tenantId, agentId);
    }
}
