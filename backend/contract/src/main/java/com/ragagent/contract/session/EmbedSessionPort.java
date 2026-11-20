package com.ragagent.contract.session;

import java.io.IOException;
import java.util.List;

/**
 * embed 渠道的**会话对外操作面**（契约面）：embed 委托层不再直接持有会话域控制器。
 *
 * <p><b>签名已中性化</b>：两条 QA 通道经 {@link SseSink} 写帧（servlet 包装归边界）；
 * 结果面用 {@link EmbedReply}（状态码 + 载荷——状态码是对外线格式的一部分，如 202 = 生成中）。
 * 失败语义与端点一致：参数/权限/可见性错误 ⇒ 各自既有的 {@code BizException} 信封原样上抛
 * （含 stop 端点的 400/401/403/404 纯字符串信封）。</p>
 *
 * <p><b>不属于本端口的东西</b>：embed 自己的会话归属闸门（{@code ensureSession}）、
 * 渠道开关抑制（{@code suppressedIfChannelOff}）与文件代理（{@code fileProxyService}）
 * 留在 embed 侧——它们是渠道策略，不是会话能力。</p>
 */
public interface EmbedSessionPort {

    /** 知识问答通道（SSE 帧经 sink 写出）。 */
    void knowledgeQa(String sessionId, String rawBody, String resourceUrls, SseSink sink)
            throws IOException;

    /** agent 问答通道（agent 模式判定在实现内）。 */
    void agentQa(String sessionId, String rawBody, String resourceUrls, SseSink sink)
            throws IOException;

    /** 会话消息列表（含资源 URL 重写）。 */
    EmbedReply<List<MessageView>> loadMessages(String sessionId, String limit,
            String beforeTime, String resourceUrls);

    /** 停止助手消息生成（收渠道边界透传的原始 body；解析归会话域侧，线格式与原生端点一致）。 */
    void stopSession(String sessionId, String rawBody);

    /** 读取建议集。 */
    EmbedReply<MessageSuggestionView> getSuggestions(String sessionId, String messageId);

    /** 生成建议集（保证语义；生成中 status=202）。 */
    EmbedReply<MessageSuggestionView> ensureSuggestions(String sessionId, String messageId,
            String rawBody);

    /** 记录建议事件。 */
    void recordSuggestionEvent(String sessionId, String rawBody);
}
