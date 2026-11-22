package com.ragagent.agentengine.api;


/**
 * 运行中用户消息注入报告。
 * emit 点：SteerIntake（{@code generateEventId("injected")}）。
 *
 * <p>用户在生成期间追加的消息被并入运行中的轮次：一行 user-role 消息已按该轮的
 * request ID 落库、文本已追加进 agent 的消息列表，下一次 LLM 调用即可见。
 * 前四字段恒输出，仅 {@code user_message_id} 空串省略。</p>
 */

public class UserMessageInjectedData {

    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }


    /** 与排队的 steer 事件关联 */
    private String steerId = "";

    /** 注入的文本（与发给模型的一致） */
    private String content = "";

    /** 该轮的持久 assistant 消息 ID */
    private String messageId = "";

    /** 空串省略 */
    private String userMessageId = "";

    public UserMessageInjectedData() {
    }

    public UserMessageInjectedData(String steerId, String content, String messageId,
                                   String userMessageId) {
        this.steerId = orEmpty(steerId);
        this.content = orEmpty(content);
        this.messageId = orEmpty(messageId);
        this.userMessageId = orEmpty(userMessageId);
    }

    public String getSteerId() {
        return steerId;
    }

    public void setSteerId(String v) {
        this.steerId = orEmpty(v);
    }

    public String getContent() {
        return content;
    }

    public void setContent(String v) {
        this.content = orEmpty(v);
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String v) {
        this.messageId = orEmpty(v);
    }

    public String getUserMessageId() {
        return userMessageId;
    }

    public void setUserMessageId(String v) {
        this.userMessageId = orEmpty(v);
    }
}
