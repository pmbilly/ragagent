package com.ragagent.session.api;

import com.ragagent.contract.session.EmbedSessionOps;
import com.ragagent.session.conversation.SessionService;
import com.ragagent.session.domain.conversation.EmbedVisitorSessionRow;
import com.ragagent.session.domain.conversation.Session;
import com.ragagent.session.domain.conversation.SessionListQuery;
import com.ragagent.session.domain.conversation.SessionOwnerIds;
import com.ragagent.session.domain.conversation.SessionPage;
import com.ragagent.session.repository.SessionRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link EmbedSessionOps} 的会话域实现：owner 串规则、描述标记与查询口径都留在会话域内。
 *
 * <p>语义与迁移前 embedchannel 内的三处调用逐一对齐（会话归属查询、建访客会话 + 补 owner、
 * 渠道会话计数、访客会话投影映射）。</p>
 */
@Component
public class EmbedSessionOpsAdapter implements EmbedSessionOps {

    private final SessionService sessionService;
    private final SessionRepository sessionRepository;

    public EmbedSessionOpsAdapter(SessionService sessionService, SessionRepository sessionRepository) {
        this.sessionService = sessionService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public OwnershipView ownership(long tenantId, String sessionId) {
        Session sess;
        try {
            sess = sessionService.getSessionById(tenantId, sessionId);
        } catch (RuntimeException e) {
            return null;
        }
        if (sess == null) {
            return null;
        }
        return new OwnershipView(sess.getTenantId(), sess.getDescription(), sess.getUserId());
    }

    @Override
    public String createVisitorSession(long tenantId, String channelId, String marker, String visitorId) {
        Session session = new Session();
        session.setTenantId(tenantId);
        session.setTitle("");
        session.setDescription(marker);
        session.setEmbedVisitorId(visitorId);
        Session created = sessionService.createSession(session);
        attachOwner(tenantId, channelId, created.getId());
        return created.getId();
    }

    @Override
    public void attachOwner(long tenantId, String channelId, String sessionId) {
        String owner = ownerOf(tenantId, channelId, sessionId);
        try {
            sessionRepository.setOwnerId(tenantId, sessionId, owner);
        } catch (RuntimeException e) {
            // 失败仅记 warn 后继续（与迁移前 embedchannel 的内联 try/catch 同语义）
        }
    }

    @Override
    public long countSessions(long tenantId, String channelId) {
        SessionListQuery query = SessionListQuery.of(null, "embed:" + channelId, null, 1, 1);
        SessionPage items = sessionRepository.queryPaged(query.withScope(tenantId, ""));
        return items.total();
    }

    @Override
    public List<VisitorSessionRow> visitorSessions(long tenantId, String marker, String visitorId, int limit) {
        List<EmbedVisitorSessionRow> rows =
                sessionRepository.listEmbedVisitorSessions(tenantId, marker, visitorId, limit);
        List<VisitorSessionRow> out = new ArrayList<>(rows.size());
        for (EmbedVisitorSessionRow row : rows) {
            out.add(new VisitorSessionRow(row.getId(), row.getTitle(), row.getUpdatedAt()));
        }
        return out;
    }

    /** owner 串规则（{@code EMBED_SESSION_PREFIX + tenantId:channelId:sessionId}）只在本域成形。 */
    private static String ownerOf(long tenantId, String channelId, String sessionId) {
        return SessionOwnerIds.EMBED_SESSION_PREFIX + tenantId + ":" + channelId + ":" + sessionId;
    }
}
