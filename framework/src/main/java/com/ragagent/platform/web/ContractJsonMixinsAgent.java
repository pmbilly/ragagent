package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ragagent.contract.agent.ToolCallTarget;
import java.util.List;
import java.util.Map;


/**
 * agent 面契约载荷的 Jackson mixin（AgentStep / AgentToolCall / ToolCallTarget）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsAgent {

    private ContractJsonMixinsAgent() {
    }

    /** AgentStep 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentStepMixin {

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<String> userMessagesBefore;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean intermediateAnswer;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String reasoningContent;

        @JsonIgnore
        public abstract List<String> getObservations();

    }

    /** AgentToolCall 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class AgentToolCallMixin {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private ToolCallTarget target;

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Map<String, Object> args;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String reflection;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> providerMetadata;

        @JsonIgnore
        public abstract String getExecutionName();

        @JsonIgnore
        public abstract Map<String, Object> getExecutionArgs();

    }

    /** ToolCallTarget 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ToolCallTargetMixin {

        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Map<String, Object> args;

    }
}
