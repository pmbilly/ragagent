package com.ragagent.session.api;

import com.ragagent.contract.event.EventBusInterface;
import com.ragagent.contract.session.MessageView;
import com.ragagent.contract.session.QaRequestPayload;
import com.ragagent.contract.session.SessionQaPort;
import com.ragagent.contract.session.SessionView;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.session.domain.conversation.Session;
import com.ragagent.session.domain.message.Message;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.QaSupport;
import com.ragagent.session.service.SessionAgentQaService;
import com.ragagent.session.service.SessionKnowledgeQaService;
import com.ragagent.session.service.SessionService;
import org.springframework.stereotype.Component;

/**
 * {@link SessionQaPort} 的会话域实现：消息落库与会话读取逐字转发；
 * 两条 QA 管线先把 L1 载荷**逐字段**搬到运行容器 {@code QaSupport.QaRequest}
 * （其余字段保持容器默认值——与装配面原先直建容器的行为一致），再分发。
 */
@Component
public class SessionQaAdapter implements SessionQaPort {

    private final MessageService messageService;
    private final SessionService sessionService;
    private final SessionKnowledgeQaService knowledgeQaService;
    private final SessionAgentQaService agentQaService;

    public SessionQaAdapter(MessageService messageService,
                            SessionService sessionService,
                            SessionKnowledgeQaService knowledgeQaService,
                            SessionAgentQaService agentQaService) {
        this.messageService = messageService;
        this.sessionService = sessionService;
        this.knowledgeQaService = knowledgeQaService;
        this.agentQaService = agentQaService;
    }

    @Override
    public MessageView createMessage(MessageView message) {
        Message saved = messageService.createMessage(toEntity(message));
        copyInto(message, saved);
        return toView(saved);
    }

    @Override
    public void updateMessage(MessageView message) {
        Message saved = toEntity(message);
        messageService.updateMessage(saved);
        copyInto(message, saved);
    }

    @Override
    public SessionView createSession(SessionView session) {
        Session saved = sessionService.createSession(toEntity(session));
        copyInto(session, saved);
        return toView(saved);
    }

    @Override
    public void deleteSession(String sessionId) {
        sessionService.deleteSession(sessionId);
    }

    @Override
    public SessionView getSession(String sessionId) {
        return toView(sessionService.getSession(sessionId));
    }

    @Override
    public void knowledgeQa(QaRequestPayload payload, EventBusInterface eventBus) {
        knowledgeQaService.knowledgeQA(toContainer(payload), eventBus);
    }

    @Override
    public void agentQa(QaRequestPayload payload, EventBusInterface eventBus) {
        agentQaService.agentQA(toContainer(payload), eventBus);
    }

    /** 载荷 → 运行容器：同名字段一一搬运（未列字段保持容器默认值）。 */
    private static QaSupport.QaRequest toContainer(QaRequestPayload payload) {
        QaSupport.QaRequest req = new QaSupport.QaRequest();
        req.session = toEntity(payload.session);
        req.query = payload.query;
        req.assistantMessageId = payload.assistantMessageId;
        req.userMessageId = payload.userMessageId;
        req.agentId = payload.agentId;
        // 载荷侧纯 JDK 值 → 域容器 JsonNode（边界归一，）
        req.agentConfig = JsonMappers.toObjectNode(payload.agentConfig);
        req.webSearchEnabled = payload.webSearchEnabled;
        req.quotedContext = payload.quotedContext;
        req.cancellationProbe = payload.cancellationProbe;
        req.attachments = payload.attachments;
        req.imageUrls = payload.imageUrls;
        return req;
    }

    /** 实体 → 线格式视图（域内投影；null 透传）。 */
    static MessageView toView(Message m) {
        if (m == null) {
            return null;
        }
        MessageView v = new MessageView();
        v.setId(m.getId());
        v.setSessionId(m.getSessionId());
        v.setRequestId(m.getRequestId());
        v.setContent(m.getContent());
        v.setRole(m.getRole());
        v.setKnowledgeReferences(m.getKnowledgeReferences());
        v.setAgentSteps(m.getAgentSteps());
        v.setMentionedItems(m.getMentionedItems());
        v.setImages(m.getImages());
        v.setAttachments(m.getAttachments());
        v.setArtifacts(m.getArtifacts());
        v.setCompleted(m.isCompleted());
        v.setFallback(m.isFallback());
        v.setAgentDurationMs(m.getAgentDurationMs());
        v.setUsage(m.getUsage());
        v.setRenderedContent(m.getRenderedContent());
        v.setChannel(m.getChannel());
        v.setAgentId(m.getAgentId());
        v.setAgentTenantId(m.getAgentTenantId());
        v.setModelId(m.getModelId());
        v.setExecutionContext(m.getExecutionContext());
        v.setKnowledgeId(m.getKnowledgeId());
        v.setUsedMemories(m.getUsedMemories());
        v.setCreatedAt(m.getCreatedAt());
        v.setUpdatedAt(m.getUpdatedAt());
        v.setDeletedAt(m.getDeletedAt());
        return v;
    }

    /** 视图 → 实体（写面用；null 透传）。 */
    static Message toEntity(MessageView v) {
        if (v == null) {
            return null;
        }
        Message m = new Message();
        m.setId(v.getId());
        m.setSessionId(v.getSessionId());
        m.setRequestId(v.getRequestId());
        m.setContent(v.getContent());
        m.setRole(v.getRole());
        m.setKnowledgeReferences(v.getKnowledgeReferences());
        m.setAgentSteps(v.getAgentSteps());
        m.setMentionedItems(v.getMentionedItems());
        m.setImages(v.getImages());
        m.setAttachments(v.getAttachments());
        m.setArtifacts(v.getArtifacts());
        m.setCompleted(v.isCompleted());
        m.setFallback(v.isFallback());
        m.setAgentDurationMs(v.getAgentDurationMs());
        m.setUsage(v.getUsage());
        m.setRenderedContent(v.getRenderedContent());
        m.setChannel(v.getChannel());
        m.setAgentId(v.getAgentId());
        m.setAgentTenantId(v.getAgentTenantId());
        m.setModelId(v.getModelId());
        m.setExecutionContext(v.getExecutionContext());
        m.setKnowledgeId(v.getKnowledgeId());
        m.setUsedMemories(v.getUsedMemories());
        m.setCreatedAt(v.getCreatedAt());
        m.setUpdatedAt(v.getUpdatedAt());
        m.setDeletedAt(v.getDeletedAt());
        return m;
    }

    /** 把落库后的实体字段回填到传入视图（恢复"同对象被填充"的旧语义；null 安全）。 */
    static void copyInto(MessageView v, Message m) {
        if (v == null || m == null) {
            return;
        }
        MessageView fresh = toView(m);
        v.setId(fresh.getId());
        v.setSessionId(fresh.getSessionId());
        v.setRequestId(fresh.getRequestId());
        v.setContent(fresh.getContent());
        v.setRole(fresh.getRole());
        v.setKnowledgeReferences(fresh.getKnowledgeReferences());
        v.setAgentSteps(fresh.getAgentSteps());
        v.setMentionedItems(fresh.getMentionedItems());
        v.setImages(fresh.getImages());
        v.setAttachments(fresh.getAttachments());
        v.setArtifacts(fresh.getArtifacts());
        v.setCompleted(fresh.isCompleted());
        v.setFallback(fresh.isFallback());
        v.setAgentDurationMs(fresh.getAgentDurationMs());
        v.setUsage(fresh.getUsage());
        v.setRenderedContent(fresh.getRenderedContent());
        v.setChannel(fresh.getChannel());
        v.setAgentId(fresh.getAgentId());
        v.setAgentTenantId(fresh.getAgentTenantId());
        v.setModelId(fresh.getModelId());
        v.setExecutionContext(fresh.getExecutionContext());
        v.setKnowledgeId(fresh.getKnowledgeId());
        v.setUsedMemories(fresh.getUsedMemories());
        v.setCreatedAt(fresh.getCreatedAt());
        v.setUpdatedAt(fresh.getUpdatedAt());
        v.setDeletedAt(fresh.getDeletedAt());
    }

    /** 实体 → 线格式视图（域内投影；null 透传）。 */
    static SessionView toView(Session s) {
        if (s == null) {
            return null;
        }
        SessionView v = new SessionView();
        v.setId(s.getId());
        v.setTitle(s.getTitle());
        v.setDescription(s.getDescription());
        v.setTenantId(s.getTenantId());
        v.setUserId(s.getUserId());
        v.setEmbedVisitorId(s.embedVisitorIdValue());
        v.setPinned(s.isPinned());
        v.setPinnedAt(s.getPinnedAt());
        v.setLastRequestState(s.getLastRequestState());
        v.setCreatedAt(s.getCreatedAt());
        v.setUpdatedAt(s.getUpdatedAt());
        v.setDeletedAt(s.getDeletedAt());
        v.setImPlatform(s.getImPlatform());
        return v;
    }

    /** 视图 → 实体（无损：字段逐一搬运；null 透传）。QA 容器需要的是**完整会话**，
     * 故载荷只携视图、在域内回填为实体（不做 id 重载，避免"未落库会话"丢失）。 */
    static Session toEntity(SessionView v) {
        if (v == null) {
            return null;
        }
        Session e = new Session();
        e.setId(v.getId());
        e.setTitle(v.getTitle());
        e.setDescription(v.getDescription());
        e.setTenantId(v.getTenantId());
        e.setUserId(v.getUserId());
        e.setEmbedVisitorId(v.embedVisitorIdValue());
        e.setPinned(v.isPinned());
        e.setPinnedAt(v.getPinnedAt());
        e.setLastRequestState(v.getLastRequestState());
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        e.setDeletedAt(v.getDeletedAt());
        e.setImPlatform(v.getImPlatform());
        return e;
    }

    /** 把落库后的实体字段回填到传入视图（恢复"同对象被填充"的旧语义；null 安全）。 */
    static void copyInto(SessionView v, Session s) {
        if (v == null || s == null) {
            return;
        }
        SessionView fresh = toView(s);
        v.setId(fresh.getId());
        v.setTitle(fresh.getTitle());
        v.setDescription(fresh.getDescription());
        v.setTenantId(fresh.getTenantId());
        v.setUserId(fresh.getUserId());
        v.setEmbedVisitorId(fresh.embedVisitorIdValue());
        v.setPinned(fresh.isPinned());
        v.setPinnedAt(fresh.getPinnedAt());
        v.setLastRequestState(fresh.getLastRequestState());
        v.setCreatedAt(fresh.getCreatedAt());
        v.setUpdatedAt(fresh.getUpdatedAt());
        v.setDeletedAt(fresh.getDeletedAt());
        v.setImPlatform(fresh.getImPlatform());
    }
}
