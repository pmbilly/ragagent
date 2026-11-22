package com.ragagent.agentengine.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Agent 事件 payload 的 Jackson mixin（SSE 前端协议面，随载荷自 framework.jsonbind 迁入）。
 *
 * <p>载荷类在 {@code agentengine.api}（AgentCompleteData 等 9 类）；线格式由本文件承载，
 * 经 {@link AgentJsonModule} 注册，由所有读写契约载荷的 mapper 共用。</p>
 */
public final class AgentEventMixins {

    private AgentEventMixins() {
    }

    /** AgentActionData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentActionDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String error;

    }

    /** AgentCompleteData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentCompleteDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<Object> knowledgeRefs;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object agentSteps;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object usage;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String messageId;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String requestId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

    }

    /** AgentFinalAnswerData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentFinalAnswerDataMixin {
        @JsonProperty("isFallback")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean isFallback;

        @JsonProperty("isFallback")
        abstract public boolean isFallback();

        @JsonProperty("isFallback")
        abstract public void setIsFallback(boolean v);

        @JsonProperty("outcome")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String outcome;

    }

    /** AgentToolCallData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentToolCallDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> arguments;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String hint;

    }

    /** AgentToolResultData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentToolResultDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String error;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long durationMs;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> data;

    }

    /** ContextCompactedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ContextCompactedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean degraded;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean splitTurn;

    }

    /** ErrorData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ErrorDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String errorCode;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String query;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

    }

    /** UserMessageInjectedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class UserMessageInjectedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String userMessageId;

    }

}
