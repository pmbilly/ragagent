package com.ragagent.session.service;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.security.LogSanitizer;
import com.ragagent.platform.stream.StreamEvent;
import com.ragagent.platform.stream.StreamManager;
import com.ragagent.session.domain.conversation.Session;
import com.ragagent.session.domain.message.Message;
import com.ragagent.session.dto.StopSessionRequest;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 会话停止用例：校验（消息可见性 + 会话 owner 范围 + 租户匹配）后向流管理器写 stop 事件。
 *
 * <p>HTTP 端点（{@code SessionController}）与 embed 委托面
 * （{@code session.api.EmbedSessionAdapter}）共用本类——两个面语义恒一致；
 * 原先 embed 面直接回调控制器，方向倒挂（api → controller），已改经本层。</p>
 *
 * <p>⚠️ 错误形态与组内其他端点不同：错误体是 {@code {"error": "…"}} 纯字符串信封
 * （**不是** AppError 信封），状态码有 400/401/403/404 四种（文案逐字保留）。
 * 停止事件经 StreamManager 落存储（跨语言键空间），事件 type 是
 * {@code types.ResponseType(event.EventStop)} 的字符串强转 "stop"。</p>
 */
@Service
public class SessionStopService {

    private final MessageService messageService;
    private final SessionService sessionService;
    private final StreamManager streamManager;

    public SessionStopService(MessageService messageService, SessionService sessionService,
            StreamManager streamManager) {
        this.messageService = messageService;
        this.sessionService = sessionService;
        this.streamManager = streamManager;
    }

    /** 停止一轮运行中的会话消息；已结束的消息是幂等的成功（不回 message 文案，§1.17）。 */
    public void stopSession(String sessionId, StopSessionRequest request) {
        String sid = LogSanitizer.sanitize(sessionId);
        if (sid == null || sid.isEmpty()) {
            throw new BizException(AppError.ofHttpStatus(400, "Session ID is required"));
        }
        if (request == null || isBlankStr(request.messageId())) {
            throw new BizException(AppError.ofHttpStatus(400, "message_id is required"));
        }
        String assistantMessageId = LogSanitizer.sanitize(request.messageId());

        Long tenantId = TenantContext.currentTenantId();
        if (tenantId == null) {
            throw new BizException(AppError.ofHttpStatus(401, "Unauthorized"));
        }

        // 消息可见性走 GetMessage（读路径），会话走严格 owner 范围（写路径）
        Message message;
        try {
            message = messageService.getMessage(sid, assistantMessageId);
        } catch (RuntimeException e) {
            throw new BizException(AppError.ofHttpStatus(404, "Message not found"));
        }
        if (message.getSessionId() == null || !message.getSessionId().equals(sid)) {
            throw new BizException(AppError.ofHttpStatus(403, "Message does not belong to this session"));
        }
        Session session;
        try {
            session = sessionService.getOwnedSession(sid);
        } catch (RuntimeException e) {
            throw new BizException(AppError.ofHttpStatus(404, "Session not found"));
        }
        if (session.getTenantId() == null || session.getTenantId().longValue() != tenantId) {
            // ⚠️ Long 比较必须拆箱/equals（租户 id 超出 Integer 缓存区间时
            // 引用比较恒不等 → 会误判 "Access denied"）
            throw new BizException(AppError.ofHttpStatus(403, "Access denied"));
        }
        if (message.isCompleted()) {
            // 已经结束的消息是幂等的成功（不回 message 文案，§1.17）
            return;
        }

        StreamEvent stopEvent = new StreamEvent(
                "stop-" + System.nanoTime(), ResponseType.STOP,
                "", true);
        stopEvent.setTimestamp(OffsetDateTime.now());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sid);
        data.put("message_id", assistantMessageId);
        data.put("reason", "user_requested");
        stopEvent.setData(data);
        try {
            streamManager.appendEvent(sid, assistantMessageId, stopEvent);
        } catch (RuntimeException e) {
            throw new BizException(AppError.ofHttpStatus(500, "Failed to write stop event"));
        }
    }

    private static boolean isBlankStr(String v) {
        return v == null || v.trim().isEmpty();
    }
}
