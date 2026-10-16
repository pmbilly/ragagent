package com.ragagent.platform.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.ragagent.contract.model.chat.CacheRetention;
import com.ragagent.contract.model.chat.ChatTool;
import com.ragagent.contract.model.chat.FunctionCall;
import com.ragagent.contract.model.chat.ImageUrl;
import com.ragagent.contract.model.chat.MessageContentPart;
import com.ragagent.contract.model.chat.ToolCall;


/**
 * 模型聊天面契约载荷的 Jackson mixin（ChatMessage / ChatOptions / ChatResponse /
 * ChatTool / FunctionDef / ImageUrl / MessageContentPart / ToolCall）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 *
 * <p>无注解的三件（{@code CacheRetention} / {@code ChatConfig} / {@code FunctionCall}）
 * 键名即字段名，不需要 mixin。</p>
 */
public final class ContractJsonMixinsModelChat {

    private ContractJsonMixinsModelChat() {
    }

    /** ChatMessage 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    @JsonPropertyOrder({
            "role", "content", "multi_content", "name", "tool_call_id",
            "tool_calls", "images", "reasoning_content"
    })
    public abstract static class ChatMessageMixin {

        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        @JsonProperty("multi_content")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<MessageContentPart> multiContent;

        @JsonProperty("name")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String name;

        @JsonProperty("tool_call_id")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String toolCallId;

        @JsonProperty("tool_calls")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ToolCall> toolCalls;

        @JsonProperty("images")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<String> images;

        @JsonProperty("reasoning_content")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String reasoningContent;

        @JsonIgnore
        private String kind;

        @JsonIgnore
        public abstract boolean isCompactionSummary();

    }

    /** ChatOptions 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ChatOptionsMixin {

        @JsonProperty("temperature")
        private double temperature;

        @JsonProperty("top_p")
        private double topP;

        @JsonProperty("seed")
        private int seed;

        @JsonProperty("max_tokens")
        private int maxTokens;

        @JsonProperty("max_completion_tokens")
        private int maxCompletionTokens;

        @JsonProperty("frequency_penalty")
        private double frequencyPenalty;

        @JsonProperty("presence_penalty")
        private double presencePenalty;

        /** 恒输出（含 null）——默认包含策略即 ALWAYS。 */
        @JsonProperty("thinking")
        private Boolean thinking;

        @JsonProperty("tools")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ChatTool> tools;

        @JsonProperty("tool_choice")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String toolChoice;

        @JsonProperty("parallel_tool_calls")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Boolean parallelToolCalls;

        @JsonProperty("format")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object format;

        @JsonIgnore
        private String promptCacheKey;

        @JsonIgnore
        private CacheRetention cacheRetention;

    }

    /** ChatResponse 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ChatResponseMixin {

        @JsonProperty("content")
        private String content;

        @JsonProperty("reasoning_content")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String reasoningContent;

        @JsonProperty("tool_calls")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ToolCall> toolCalls;

        @JsonProperty("finish_reason")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String finishReason;

        @JsonProperty("usage")
        private Object usage;

        @JsonIgnore
        private boolean answerStreamed;

        @JsonIgnore
        private String answerEventId;

    }

    /** ChatTool 的 mixin（类级字段序；字段键名即字段名）。 */
    @JsonPropertyOrder({"type", "function"})
    public abstract static class ChatToolMixin {
    }

    /** FunctionDef 的 mixin（类级字段序；字段键名即字段名）。 */
    @JsonPropertyOrder({"name", "description", "parameters"})
    public abstract static class FunctionDefMixin {
    }

    /** ImageUrl 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    @JsonPropertyOrder({"url", "detail"})
    public abstract static class ImageUrlMixin {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String detail;

    }

    /** MessageContentPart 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    @JsonPropertyOrder({"type", "text", "image_url"})
    public abstract static class MessageContentPartMixin {

        @JsonProperty("type")
        private String type;

        @JsonProperty("text")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String text;

        @JsonProperty("image_url")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private ImageUrl imageUrl;

    }

    /** ToolCall 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ToolCallMixin {

        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("function")
        private FunctionCall function;

        @JsonProperty("provider_metadata")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> providerMetadata;

        @JsonIgnore
        private String modelArguments;

        @JsonIgnore
        private String argumentResolution;

        @JsonIgnore
        private List<String> unresolvedHandles;

        @JsonIgnore
        public abstract String getToolName();

    }
}
