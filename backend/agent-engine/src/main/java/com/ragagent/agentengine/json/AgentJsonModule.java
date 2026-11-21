package com.ragagent.agentengine.json;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ragagent.framework.vocabulary.AgentStep;
import com.ragagent.framework.vocabulary.AgentToolCall;
import com.ragagent.framework.vocabulary.ToolCallTarget;
import com.ragagent.framework.vocabulary.ToolResult;
import com.ragagent.framework.web.JsonModuleProvider;

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
    }

    /** ServiceLoader 入口。 */
    public static final class Provider implements JsonModuleProvider {

        @Override
        public Module module() {
            return new AgentJsonModule();
        }
    }
}
