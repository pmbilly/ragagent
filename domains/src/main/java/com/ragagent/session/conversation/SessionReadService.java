package com.ragagent.session.conversation;

import com.ragagent.contract.session.SessionView;
import com.ragagent.session.domain.conversation.Session;
import com.ragagent.session.domain.message.Message;
import com.ragagent.session.dto.SessionViewMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 会话控制面读模型：把实体装配/映射收在这里，控制器只拿契约视图。 */
@Service
public class SessionReadService {

    private final SessionService sessionService;

    public SessionReadService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public SessionView create(Long tenantId, String title, String description, String ownerId) {
        Session session = new Session();
        session.setTenantId(tenantId);
        session.setTitle(title == null ? "" : title);
        session.setDescription(Session.sanitizeClientSessionDescription(
                description == null ? "" : description, ""));
        if (ownerId != null && !ownerId.isEmpty()) {
            session.setUserId(ownerId);
        }
        return SessionViewMapper.from(sessionService.createSession(session));
    }

    public SessionView get(String id) {
        return SessionViewMapper.from(sessionService.getSession(id));
    }

    public SessionView update(String id, Long tenantId, String title, String description) {
        Session session = new Session();
        session.setId(id);
        session.setTenantId(tenantId);
        session.setTitle(title == null ? "" : title);
        session.setDescription(description == null ? "" : description);
        sessionService.updateSession(session);
        return SessionViewMapper.from(sessionService.getSession(id));
    }

    public String generateTitleForOwnedSession(String sessionId, List<Message> messages) {
        return sessionService.generateTitle(
                sessionService.getOwnedSession(sessionId), messages, "");
    }
}
