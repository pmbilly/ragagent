package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;


/**
 * 事件 payload（contract.event.payload）的 Jackson mixin。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsEvent {

    private ContractJsonMixinsEvent() {
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

    /** AgentPlanData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentPlanDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long durationMs;

    }

    /** AgentQueryData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentQueryDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String requestId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

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

    /** ChatData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ChatDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String response;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String streamChunk;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private int tokenCount;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long durationMs;

        @JsonProperty("isStream")
        private boolean isStream;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

        @JsonProperty("isStream")
        abstract public boolean isStream();

        @JsonProperty("isStream")
        abstract public void setStream(boolean v);

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

    /** MCPOAuthRequiredData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MCPOAuthRequiredDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String requestId;

    }

    /** MCPOAuthResolvedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MCPOAuthResolvedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String reason;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean timedOut;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean canceled;

    }

    /** MergeData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MergeDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object results;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long durationMs;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

    }

    /** QueryData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class QueryDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String rewrittenQuery;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String userId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

    }

    /** RerankData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class RerankDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object results;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long durationMs;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

    }

    /** RetrievalData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class RetrievalDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object results;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private long durationMs;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> extra;

    }

    /** StopData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class StopDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String reason;

    }

    /** ToolApprovalRequiredData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ToolApprovalRequiredDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object args;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String argsJson;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String requestId;

    }

    /** ToolApprovalResolvedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ToolApprovalResolvedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String reason;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean timedOut;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean canceled;

    }

    /** UserMessageInjectedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class UserMessageInjectedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String userMessageId;

    }

}
