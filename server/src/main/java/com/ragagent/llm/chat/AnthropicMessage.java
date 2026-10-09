package com.ragagent.llm.chat;


/**
 * Anthropic 消息。
 *
 * <p>{@code content} 是多态：普通对话是字符串；带 tool_use / tool_result 时是
 * {@code List<AnthropicContentBlock>}。两者都恒输出。</p>
 */

public class AnthropicMessage {

    private String role = "";
    private Object content;

    public AnthropicMessage() {
    }

    public AnthropicMessage(String role, Object content) {
        this.role = role == null ? "" : role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String v) { role = v == null ? "" : v; }
    public Object getContent() { return content; }
    public void setContent(Object v) { content = v; }
}
