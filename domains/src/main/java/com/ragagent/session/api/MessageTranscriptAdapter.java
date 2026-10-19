package com.ragagent.session.api;

import com.ragagent.contract.session.MessageTranscriptPort;
import com.ragagent.contract.session.SessionMessageView;
import com.ragagent.session.repository.MessageRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/** {@link MessageTranscriptPort} 的会话域实现（委托既有仓储查询）。 */
@Component
public class MessageTranscriptAdapter implements MessageTranscriptPort {

    private final MessageRepository messages;

    public MessageTranscriptAdapter(MessageRepository messages) {
        this.messages = messages;
    }

    @Override
    public List<SessionMessageView> listAfterCursor(
            String sessionId, OffsetDateTime afterCreatedAt, String afterId, int limit) {
        return messages.listAfterCursor(sessionId, afterCreatedAt, afterId, limit);
    }

    @Override
    public List<SessionMessageView> listBeforeTime(
            String sessionId, OffsetDateTime beforeTime, int limit) {
        return messages.listBeforeTime(sessionId, beforeTime, limit);
    }
}
