package com.ragagent.session.api;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.ragagent.session.domain.message.Message;
import com.ragagent.session.domain.message.MessageSuggestionSet;
import com.ragagent.common.session.dto.StopSessionRequest;
import com.ragagent.common.session.embed.EmbedSessionPort;
import com.ragagent.platform.web.ApiResponse;
import com.ragagent.session.controller.KnowledgeQaController;
import com.ragagent.session.controller.MessageController;
import com.ragagent.session.controller.MessageSuggestionController;
import com.ragagent.session.controller.SessionController;

import jakarta.servlet.http.HttpServletResponse;
import com.ragagent.contract.session.MessageSuggestionView;
import com.ragagent.common.session.embed.MessageView;

/**
 * {@link EmbedSessionPort} 的会话域实现：逐参数转发到既有的四个端点方法
 * （与 embed 委托层原先的调用**逐字一致**——包括建议读取的 {@code idFallback=null}
 * 与 {@code beforeTime}/{@code limit} 的原始字符串形态），不新增校验、不改错误口径。
 */
@Component
public class EmbedSessionAdapter implements EmbedSessionPort {

    private final KnowledgeQaController knowledgeQaController;
    private final MessageController messageController;
    private final SessionController sessionController;
    private final MessageSuggestionController suggestionController;

    public EmbedSessionAdapter(KnowledgeQaController knowledgeQaController,
                               MessageController messageController,
                               SessionController sessionController,
                               MessageSuggestionController suggestionController) {
        this.knowledgeQaController = knowledgeQaController;
        this.messageController = messageController;
        this.sessionController = sessionController;
        this.suggestionController = suggestionController;
    }

    @Override
    public void knowledgeQa(String sessionId, String rawBody, String resourceUrls,
            HttpServletResponse response) throws IOException {
        knowledgeQaController.knowledgeQA(sessionId, rawBody, resourceUrls, response);
    }

    @Override
    public void agentQa(String sessionId, String rawBody, String resourceUrls,
            HttpServletResponse response) throws IOException {
        knowledgeQaController.agentQA(sessionId, rawBody, resourceUrls, response);
    }

    @Override
    public ResponseEntity<List<MessageView>> loadMessages(String sessionId, String limit,
            String beforeTime, String resourceUrls) {
        ResponseEntity<List<Message>> resp = messageController.loadMessages(sessionId, limit, beforeTime, resourceUrls);
        List<MessageView> views = resp.getBody() == null ? null
                : resp.getBody().stream().map(SessionQaAdapter::toView).toList();
        return ResponseEntity.status(resp.getStatusCode()).headers(resp.getHeaders()).body(views);
    }

    @Override
    public ApiResponse<Void> stopSession(String sessionId, StopSessionRequest body) {
        return sessionController.stopSession(sessionId, body);
    }

    @Override
    public ResponseEntity<MessageSuggestionView> getSuggestions(String sessionId, String messageId) {
        return toView(suggestionController.get(sessionId, null, messageId));
    }

    @Override
    public ResponseEntity<MessageSuggestionView> ensureSuggestions(String sessionId, String messageId,
            String rawBody) {
        return toView(suggestionController.ensure(sessionId, messageId, rawBody));
    }

    /** 保状态码与响应头，仅把 body 换成线格式视图（404 等无 body 情形逐字保留）。 */
    private static ResponseEntity<MessageSuggestionView> toView(
            ResponseEntity<MessageSuggestionSet> resp) {
        return ResponseEntity.status(resp.getStatusCode())
                .headers(resp.getHeaders())
                .body(toView(resp.getBody()));
    }

    /** 实体 → 线格式视图（域内投影：吃实体，字段逐字搬运；null 透传）。 */
    static MessageSuggestionView toView(MessageSuggestionSet s) {
        if (s == null) {
            return null;
        }
        MessageSuggestionView v = new MessageSuggestionView();
        v.setId(s.getId());
        v.setTenantId(s.getTenantId());
        v.setSessionId(s.getSessionId());
        v.setAssistantMessageId(s.getAssistantMessageId());
        v.setAgentId(s.getAgentId());
        v.setPlacement(s.getPlacement());
        v.setConfigHash(s.getConfigHash());
        v.setLocale(s.getLocale());
        v.setStatus(s.getStatus());
        v.setAllowRegenerate(s.isAllowRegenerate());
        v.setSuppressionReason(s.getSuppressionReason());
        v.setQuestions(s.getQuestions());
        v.setModelId(s.getModelId());
        v.setPromptTokens(s.getPromptTokens());
        v.setCompletionTokens(s.getCompletionTokens());
        v.setLatencyMs(s.getLatencyMs());
        v.setErrorCode(s.getErrorCode());
        v.setGeneratedAt(s.getGeneratedAt());
        v.setCreatedAt(s.getCreatedAt());
        v.setUpdatedAt(s.getUpdatedAt());
        return v;
    }

    @Override
    public ApiResponse<Void> recordSuggestionEvent(String sessionId, String rawBody) {
        return suggestionController.recordEvent(sessionId, rawBody);
    }
}
