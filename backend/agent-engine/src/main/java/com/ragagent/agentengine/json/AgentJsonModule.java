package com.ragagent.agentengine.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ragagent.agentengine.api.AgentActionData;
import com.ragagent.agentengine.api.AgentCompleteData;
import com.ragagent.agentengine.api.AgentFinalAnswerData;
import com.ragagent.agentengine.api.AgentStep;
import com.ragagent.agentengine.api.AgentToolCall;
import com.ragagent.agentengine.api.AgentToolCallData;
import com.ragagent.agentengine.api.AgentToolResultData;
import com.ragagent.agentengine.api.ContextCompactedData;
import com.ragagent.agentengine.api.ErrorData;
import com.ragagent.agentengine.api.ToolCallTarget;
import com.ragagent.agentengine.api.UserMessageInjectedData;
import com.ragagent.framework.jsonbind.JsonModuleProvider;
import com.ragagent.framework.vocabulary.ToolResult;

/**
 * Agent 面载荷的 Jackson 装配。
 *
 * <p>经 {@link JsonModuleProvider} 由 {@code JsonMappers} 自动发现：framework 不必认识
 * agent 面载荷类型，线格式由本模块自己声明。</p>
 */
public final class AgentJsonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public AgentJsonModule() {
        super("ragagent-agent-json");
        setMixInAnnotation(AgentStep.class, AgentJsonMixins.AgentStepMixin.class);
        setMixInAnnotation(AgentToolCall.class, AgentJsonMixins.AgentToolCallMixin.class);
        setMixInAnnotation(ToolCallTarget.class, AgentJsonMixins.ToolCallTargetMixin.class);
        setMixInAnnotation(ToolResult.class, ToolResultMixin.class);
        // Agent 事件 payload 随载荷归位 agentengine.api，mixin 随迁（只追加）
        setMixInAnnotation(AgentActionData.class, AgentEventMixins.AgentActionDataMixin.class);
        setMixInAnnotation(AgentCompleteData.class, AgentEventMixins.AgentCompleteDataMixin.class);
        setMixInAnnotation(AgentFinalAnswerData.class, AgentEventMixins.AgentFinalAnswerDataMixin.class);
        setMixInAnnotation(AgentToolCallData.class, AgentEventMixins.AgentToolCallDataMixin.class);
        setMixInAnnotation(AgentToolResultData.class, AgentEventMixins.AgentToolResultDataMixin.class);
        setMixInAnnotation(ContextCompactedData.class, AgentEventMixins.ContextCompactedDataMixin.class);
        setMixInAnnotation(ErrorData.class, AgentEventMixins.ErrorDataMixin.class);
        setMixInAnnotation(UserMessageInjectedData.class, AgentEventMixins.UserMessageInjectedDataMixin.class);
    }

    /** ServiceLoader 入口。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new AgentJsonModule();
        }
    }
}
