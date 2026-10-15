package com.ragagent.session.api;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.ragagent.common.session.domain.message.Message;
import com.ragagent.common.session.domain.message.MessageSuggestionSet;
import com.ragagent.common.session.dto.StopSessionRequest;
import com.ragagent.common.session.embed.EmbedSessionPort;
import com.ragagent.platform.web.ApiResponse;
import com.ragagent.session.controller.KnowledgeQaController;
import com.ragagent.session.controller.MessageController;
import com.ragagent.session.controller.MessageSuggestionController;
import com.ragagent.session.controller.SessionController;

import jakarta.servlet.http.HttpServletResponse;

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
    public ResponseEntity<List<Message>> loadMessages(String sessionId, String limit,
            String beforeTime, String resourceUrls) {
        return messageController.loadMessages(sessionId, limit, beforeTime, resourceUrls);
    }

    @Override
    public ApiResponse<Void> stopSession(String sessionId, StopSessionRequest body) {
        return sessionController.stopSession(sessionId, body);
    }

    @Override
    public ResponseEntity<MessageSuggestionSet> getSuggestions(String sessionId, String messageId) {
        return suggestionController.get(sessionId, null, messageId);
    }

    @Override
    public ResponseEntity<MessageSuggestionSet> ensureSuggestions(String sessionId, String messageId,
            String rawBody) {
        return suggestionController.ensure(sessionId, messageId, rawBody);
    }

    @Override
    public ApiResponse<Void> recordSuggestionEvent(String sessionId, String rawBody) {
        return suggestionController.recordEvent(sessionId, rawBody);
    }
}
