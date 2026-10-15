package com.ragagent.common.session.embed;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;


import com.ragagent.common.session.dto.StopSessionRequest;
import com.ragagent.platform.web.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * embed 渠道的**会话对外操作面**（L1）：embed 委托层不再直接持有会话域控制器。
 *
 * <p><b>方法签名刻意与既有 HTTP 端点一一对应**（含响应壳）</b>：委托层原先就是
 * 逐参数转发到这些端点的，端口保持同样的入参/出参形态即可**零逻辑重复、零行为变化**；
 * 两条 QA 通道按原语义**直接写 SSE 到 servlet 响应**（无壳）。</p>
 *
 * <p><b>不属于本端口的东西</b>：embed 自己的会话归属闸门（{@code ensureSession}）、
 * 渠道开关抑制（{@code suppressedIfChannelOff}）与文件代理（{@code fileProxyService}）
 * 留在 embed 侧——它们是渠道策略，不是会话能力。</p>
 *
 * <p><b>失败语义</b>与端点一致：参数/权限/可见性错误 ⇒ 各自既有的 {@code BizException}
 * 信封原样上抛（含 stop 端点的 400/401/403/404 纯字符串信封）。</p>
 */
public interface EmbedSessionPort {

    /** 知识问答通道（SSE 直写响应）。 */
    void knowledgeQa(String sessionId, String rawBody, String resourceUrls,
                     HttpServletResponse response) throws IOException;

    /** agent 问答通道（SSE 直写响应；agent 模式判定在实现内）。 */
    void agentQa(String sessionId, String rawBody, String resourceUrls,
                 HttpServletResponse response) throws IOException;

    /** 会话消息列表（含资源 URL 重写）。 */
    ResponseEntity<List<MessageView>> loadMessages(String sessionId, String limit,
            String beforeTime, String resourceUrls);

    /** 停止助手消息生成。 */
    ApiResponse<Void> stopSession(String sessionId, StopSessionRequest body);

    /** 读取建议集。 */
    ResponseEntity<MessageSuggestionView> getSuggestions(String sessionId, String messageId);

    /** 生成建议集（保证语义；生成中 ⇒ 202）。 */
    ResponseEntity<MessageSuggestionView> ensureSuggestions(String sessionId, String messageId,
            String rawBody);

    /** 记录建议事件。 */
    ApiResponse<Void> recordSuggestionEvent(String sessionId, String rawBody);
}
