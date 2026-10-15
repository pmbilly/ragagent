package com.ragagent.session.api;

import org.springframework.stereotype.Component;

import com.ragagent.common.session.domain.conversation.Session;
import com.ragagent.common.session.domain.message.Message;
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
    public Message createMessage(Message message) {
        return messageService.createMessage(message);
    }

    @Override
    public void updateMessage(Message message) {
        messageService.updateMessage(message);
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
}
