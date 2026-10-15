package com.ragagent.session.api;

import org.springframework.stereotype.Component;

import com.ragagent.common.session.domain.conversation.Session;
import com.ragagent.session.domain.message.Message;
import com.ragagent.common.session.embed.MessageView;
import com.ragagent.common.session.qa.QaRequestPayload;
import com.ragagent.common.session.qa.SessionQaPort;
import com.ragagent.platform.event.EventBus;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.QaSupport;
import com.ragagent.session.service.SessionAgentQaService;
import com.ragagent.session.service.SessionKnowledgeQaService;
import com.ragagent.session.service.SessionService;

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
    public Session createSession(Session session) {
        return sessionService.createSession(session);
    }

    @Override
    public void deleteSession(String sessionId) {
        sessionService.deleteSession(sessionId);
    }

    @Override
    public Session getSession(String sessionId) {
        return sessionService.getSession(sessionId);
    }

    @Override
    public void knowledgeQa(QaRequestPayload payload, EventBus eventBus) {
        knowledgeQaService.knowledgeQA(toContainer(payload), eventBus);
    }

    @Override
    public void agentQa(QaRequestPayload payload, EventBus eventBus) {
        agentQaService.agentQA(toContainer(payload), eventBus);
    }

    /** 载荷 → 运行容器：同名字段一一搬运（未列字段保持容器默认值）。 */
    private static QaSupport.QaRequest toContainer(QaRequestPayload payload) {
        QaSupport.QaRequest req = new QaSupport.QaRequest();
        req.session = payload.session;
        req.query = payload.query;
        req.assistantMessageId = payload.assistantMessageId;
        req.userMessageId = payload.userMessageId;
        req.agentId = payload.agentId;
        req.agentConfig = payload.agentConfig;
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
}
