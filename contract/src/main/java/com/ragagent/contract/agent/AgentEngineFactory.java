package com.ragagent.contract.agent;

import com.ragagent.contract.event.EventBusInterface;
import com.ragagent.contract.model.LlmChatClient;

/**
 * agent 引擎工厂（装配权下沉到 agent-engine）。
 *
 * <p><b>为什么把装配下沉</b>：原先域侧的 {@code AgentEngineAssembler} 负责建 ToolRegistry、
 * 注册内置工具（思考/待办/技能读取）、装载技能目录与提示词模板、构造引擎——这些都是
 * <b>引擎自己的知识</b>，域侧却因此直引引擎实现类。现在域侧只准备 {@link AgentEngineRequest}
 * （外部工具 + 本轮输入），装配由实现侧完成。</p>
 *
 * <p>失败以运行时异常表达；实现唯一（agent-engine 的工厂）。</p>
 */
public interface AgentEngineFactory {

    /** 装配并创建一个引擎实例（每轮对话一个）。 */
    AgentEngineHandle create(AgentEngineRequest request, LlmChatClient chatModel,
                             EventBusInterface eventBus);
}
