package com.ragagent.session.api;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.session.domain.message.Message;
import com.ragagent.session.domain.message.MessageSuggestionSet;
import com.ragagent.session.dto.StopSessionRequest;
import com.ragagent.contract.session.EmbedReply;
import com.ragagent.contract.session.EmbedSessionPort;
import com.ragagent.contract.session.SseSink;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.session.controller.MessageController;
import com.ragagent.session.controller.MessageSuggestionController;
import com.ragagent.session.controller.SessionController;
import com.ragagent.session.qa.QaEntryRunner;

import com.ragagent.contract.session.MessageSuggestionView;
import com.ragagent.contract.session.MessageView;

/**
 * {@link EmbedSessionPort} 的会话域实现：逐参数转发到既有的四个端点方法
 * （与 embed 委托层原先的调用**逐字一致**——包括建议读取的 {@code idFallback=null}
 * 与 {@code beforeTime}/{@code limit} 的原始字符串形态），不新增校验、不改错误口径。
 */
@Component
public class EmbedSessionAdapter implements EmbedSessionPort {

    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private final QaEntryRunner qaEntryRunner;
    private final MessageController messageController;
    private final SessionController sessionController;
    private final MessageSuggestionController suggestionController;

    public EmbedSessionAdapter(QaEntryRunner qaEntryRunner,
                               MessageController messageController,
                               SessionController sessionController,
                               MessageSuggestionController suggestionController) {
        this.qaEntryRunner = qaEntryRunner;
        this.messageController = messageController;
        this.sessionController = sessionController;
        this.suggestionController = suggestionController;
    }

    @Override
    public void knowledgeQa(String sessionId, String rawBody, String resourceUrls,
            SseSink sink) throws IOException {
        qaEntryRunner.runKnowledgeQA(sessionId, rawBody, resourceUrls, sink);
    }

    @Override
    public void agentQa(String sessionId, String rawBody, String resourceUrls,
            SseSink sink) throws IOException {
        qaEntryRunner.runAgentQA(sessionId, rawBody, resourceUrls, sink);
    }

    @Override
    public EmbedReply<List<MessageView>> loadMessages(String sessionId, String limit,
            String beforeTime, String resourceUrls) {
        ResponseEntity<List<Message>> resp = messageController.loadMessages(sessionId, limit, beforeTime, resourceUrls);
        List<MessageView> views = resp.getBody() == null ? null
                : resp.getBody().stream().map(SessionQaAdapter::toView).toList();
        return new EmbedReply<>(resp.getStatusCode().value(), views);
    }

    @Override
    public void stopSession(String sessionId, String rawBody) {
        StopSessionRequest request = null;
        if (rawBody != null && !rawBody.isEmpty()) {
            try {
                request = MAPPER.readValue(rawBody, StopSessionRequest.class);
            } catch (Exception e) {
                throw new BizException(AppError.badRequest("invalid request body"));
            }
        }
        sessionController.stopSession(sessionId, request);
    }

    @Override
    public EmbedReply<MessageSuggestionView> getSuggestions(String sessionId, String messageId) {
        return toReply(suggestionController.get(sessionId, null, messageId));
    }

    @Override
    public EmbedReply<MessageSuggestionView> ensureSuggestions(String sessionId, String messageId,
            String rawBody) {
        return toReply(suggestionController.ensure(sessionId, messageId, rawBody));
    }

    /** 保状态码，仅把 body 换成线格式视图（无 body 情形逐字保留）。 */
    private static EmbedReply<MessageSuggestionView> toReply(
            ResponseEntity<MessageSuggestionSet> resp) {
        return new EmbedReply<>(resp.getStatusCode().value(), toView(resp.getBody()));
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
    public void recordSuggestionEvent(String sessionId, String rawBody) {
        suggestionController.recordEvent(sessionId, rawBody);
    }
}
