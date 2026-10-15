package com.ragagent.common.session.qa;

import com.ragagent.common.session.embed.SessionView;
import com.ragagent.common.session.embed.MessageView;
import com.ragagent.platform.event.EventBus;

/**
 * 会话问答面端口（L1）：装配面（IM 等）触发会话 QA、读写会话与消息所需的窄面。
 *
 * <p><b>为什么是端口</b>：触发链路（消息落库 → 容器构造 → 两条 QA 管线）与会话内部状态
 * （{@code QaSupport.QaRequest} / agent 实体解析）都在会话域；装配面只该给出载荷与事件总线。
 * 端口实现（会话侧适配器）负责"载荷 → 容器"的逐字段搬运与两条管线分发。</p>
 *
 * <p><b>失败语义</b>与域实现一致：异常原样上抛（装配面按既有 catch 处理）。</p>
 */
public interface SessionQaPort {

    /** 消息落库（返回落库后的行；口径同会话域 {@code MessageService#createMessage}）。 */
    MessageView createMessage(MessageView message);

    /** 更新消息行（口径同会话域 {@code MessageService#updateMessage}）。 */
    void updateMessage(MessageView message);

    /** 按 id 取会话（不可见 / 不存在 ⇒ null，口径同域实现）。 */
    SessionView getSession(String sessionId);

    /** 建会话（口径同会话域 {@code SessionService#createSession}）。 */
    SessionView createSession(SessionView session);

    /** 删会话（口径同会话域 {@code SessionService#deleteSession}）。 */
    void deleteSession(String sessionId);

    /** 触发知识问答管线（内部按事件总线推送）。 */
    void knowledgeQa(QaRequestPayload payload, EventBus eventBus);

    /** 触发 agent 问答管线（内部按事件总线推送）。 */
    void agentQa(QaRequestPayload payload, EventBus eventBus);
}
