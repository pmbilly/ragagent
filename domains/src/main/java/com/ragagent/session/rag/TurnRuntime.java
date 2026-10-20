package com.ragagent.session.rag;

import com.ragagent.contract.event.EventBusInterface;

/**
 * 一轮问答的运行时句柄：事件总线与消息 id。
 *
 * <p>这些字段是<b>逐执行句柄</b>，不参与 {@link ChatManage#cloneChatManage()} 的复制面；
 * 独立成类后，"哪些状态可复制、哪些是运行时"在类型上就分开了，不再靠注释提醒。</p>
 */
public final class TurnRuntime {

    private EventBusInterface eventBus;
    private String messageId = "";
    private String userMessageId = "";

    public EventBusInterface getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBusInterface v) {
        eventBus = v;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String v) {
        messageId = v == null ? "" : v;
    }

    public String getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(String v) {
        userMessageId = v == null ? "" : v;
    }
}
