package com.ragagent.im.service;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.agent.management.domain.CustomAgentEntity;
import com.ragagent.agent.management.service.AgentConfigJson;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.im.runtime.ImFormat;
import com.ragagent.im.service.ImService.InflightEntry;
import com.ragagent.session.domain.Message;
import com.ragagent.session.domain.Session;
import com.ragagent.session.service.QaSupport;
import com.ragagent.session.domain.MessageAttachment;

/**
 * QA 管线的共享底座：agent 模式判定、IM 请求构造（含 agent config 缺省补全）、
 * 用户/助手消息落库。runQA（同步）与流式管线共用。
 */
final class ImQaRequests {

    private final ImService service;

    ImQaRequests(ImService service) {
        this.service = service;
    }


    static boolean isAgentMode(CustomAgentEntity agent) {
        // agentMode == "smart-reasoning" 即智能推理
        if (agent == null || agent.getConfig() == null || agent.getConfig().isEmpty()) {
            return false;
        }
        try {
            JsonNode cfg = ImService.JSON.readTree(agent.getConfig());
            return "smart-reasoning".equals(cfg.path("agentMode").asText(""));
        } catch (Exception e) {
            return false;
        }
    }

    /** 构造 QA 请求（含 agent config 缺省补全与 /stop 取消探针）。 */
    QaSupport.QaRequest buildIMQARequest(Session session, String query,
            String assistantMessageId, String userMessageId, CustomAgentEntity agent,
            IncomingMessage.QuotedMessage quote, InflightEntry inflight) {
        QaSupport.QaRequest req = new QaSupport.QaRequest();
        req.session = session;
        req.query = query;
        req.assistantMessageId = assistantMessageId;
        req.userMessageId = userMessageId;
        req.agentRow = agent;
        if (agent != null && agent.getConfig() != null && !agent.getConfig().isEmpty()) {
            try {
                req.agentConfig = (com.fasterxml.jackson.databind.node.ObjectNode)
                        ImService.JSON.readTree(agent.getConfig());
                AgentConfigJson.ensureDefaults(req.agentConfig);
            } catch (Exception ignored) {
                req.agentConfig = null;
            }
        }
        req.webSearchEnabled = agent != null && req.agentConfig != null
                && req.agentConfig.path("webSearchEnabled").asBoolean(false);
        req.quotedContext = ImFormat.formatQuotedContext(quote);
        if (inflight != null) {
            // IM /stop → 引擎取消：探针读队列层的取消标志（与 web 面 QaTurnExecutor 同契约，
            // 贯穿 think/act/审批等待三条路）。
            req.cancellationProbe = () -> inflight.queueReq.isCancelled()
                    ? "context canceled" : null;
        }

        return req;
    }

    /** 用户消息落库（带附件元数据；无附件传空表）。 */
    Message createUserMessage(String sessionId, String content, String requestId,
            List<MessageAttachment> attachments) {
        Message m = new Message();
        m.setSessionId(sessionId);
        m.setRole("user");
        m.setContent(content);
        m.setRequestId(requestId);
        m.setCompleted(true);
        m.setChannel("im");
        if (attachments != null && !attachments.isEmpty()) {
            m.setAttachments(attachments);
        }
        return service.messageService.createMessage(m);
    }

    /** 助手消息落库。 */
    Message createAssistantMessage(String sessionId, String requestId) {
        Message m = new Message();
        m.setSessionId(sessionId);
        m.setRole("assistant");
        m.setRequestId(requestId);
        m.setChannel("im");
        return service.messageService.createMessage(m);
    }
}
