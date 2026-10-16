package com.ragagent.contract.model.chat;

import java.util.List;

/**
 * 聊天消息。
 *
 * <p>JSON 字段序 = 声明序；role/content 恒输出，其余为空时省略（NON_EMPTY）——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。</p>
 */
public class ChatMessage {

    /** 引擎合成消息的标记。 */
    public static final String KIND_COMPACTION_SUMMARY = "compaction_summary";

    /** 角色：system / user / assistant / tool */
    private String role = "";
    private String content = "";
    /** 多内容消息（文本+图片混排） */
    private List<MessageContentPart> multiContent;
    /** 工具名（tool 角色用） */
    private String name;
    /** 工具调用 ID（tool 角色用） */
    private String toolCallId;
    /** 工具调用列表（assistant 角色用） */
    private List<ToolCall> toolCalls;
    /** 图片 URL（仅当前 user 消息；转 OpenAI 时降级为 MultiContent） */
    private List<String> images;
    /**
     * assistant 推理类模型上一轮输出的思考内容。部分供应商（MiMo、DeepSeek V3.2/V4
     * thinking 模式）要求多轮对话中把 assistant 的 reasoning_content 原样回传，
     * 否则以 400 拒绝；不要求的供应商会忽略未知字段，无副作用。
     */
    private String reasoningContent;
    /**
     * 引擎内部记账，**不上线**（mixin 里 {@code @JsonIgnore}）：某些端点会拒绝未知消息字段。
     * 用于让压缩逻辑把自己的摘要与真实用户轮次区分开（否则摘要会被再次喂进摘要轮，
     * 退化成"摘要的摘要"）。
     */
    private String kind;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role == null ? "" : role;
        this.content = content == null ? "" : content;
    }

    /** 便捷构造：system 消息 */
    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    /** 便捷构造：user 消息 */
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    /** 便捷构造：tool 结果消息 */
    public static ChatMessage tool(String toolCallId, String name, String content) {
        ChatMessage m = new ChatMessage("tool", content);
        m.toolCallId = toolCallId;
        m.name = name;
        return m;
    }

    public String getRole() { return role; }
    public void setRole(String v) { role = v == null ? "" : v; }
    public String getContent() { return content; }
    public void setContent(String v) { content = v == null ? "" : v; }
    public List<MessageContentPart> getMultiContent() { return multiContent; }
    public void setMultiContent(List<MessageContentPart> v) { multiContent = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String v) { toolCallId = v; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> v) { toolCalls = v; }
    public List<String> getImages() { return images; }
    public void setImages(List<String> v) { images = v; }
    public String getReasoningContent() { return reasoningContent; }
    public void setReasoningContent(String v) { reasoningContent = v; }
    public String getKind() { return kind; }
    public void setKind(String v) { kind = v; }

    /** 派生方法，**不上线**（mixin 里 {@code @JsonIgnore}）。 */
    public boolean isCompactionSummary() {
        return KIND_COMPACTION_SUMMARY.equals(kind);
    }
}
