package com.ragagent.session.rag;

import com.ragagent.contract.model.ChatResponse;

/**
 * 状态段 · 答复：组装好的用户消息、渲染上下文与模型回复。
 *
 * <p>写入点只有两处：{@code INTO_CHAT_MESSAGE} 写 {@code userContent} /
 * {@code renderedContexts}，{@code CHAT_COMPLETION} 写 {@code chatResponse}；
 * 下游插件只读。独立成类后，"这一组状态归谁写、谁只读"在类型上就分开了。</p>
 */
public final class AnswerState {

    private String userContent = "";
    private String renderedContexts = "";
    private ChatResponse chatResponse;

    public String getUserContent() {
        return userContent;
    }

    public void setUserContent(String v) {
        userContent = v == null ? "" : v;
    }

    public String getRenderedContexts() {
        return renderedContexts;
    }

    public void setRenderedContexts(String v) {
        renderedContexts = v == null ? "" : v;
    }

    public ChatResponse getChatResponse() {
        return chatResponse;
    }

    public void setChatResponse(ChatResponse v) {
        chatResponse = v;
    }
}
