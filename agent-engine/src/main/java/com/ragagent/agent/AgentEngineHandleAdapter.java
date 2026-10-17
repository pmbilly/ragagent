package com.ragagent.agent;

import com.ragagent.contract.agent.AgentEngineHandle;
import com.ragagent.contract.agent.AgentExecutionException;
import com.ragagent.contract.agent.SteerSink;
import com.ragagent.contract.model.chat.ChatMessage;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link AgentEngineHandle} 的适配器（R5c）：把域侧的 4 个调用转发给 {@link AgentEngine}。
 *
 * <p>为什么需要适配器而不是让 {@code AgentEngine} 直接实现端口：引擎的 {@code execute}
 * 返回 {@code AgentState}（引擎内部形状），而契约句柄声明为 void（域侧并不使用返回值）
 * ——适配器吸收这处形状差异，也让引擎类本身不必依赖契约端口。</p>
 */
public final class AgentEngineHandleAdapter implements AgentEngineHandle {

    private final AgentEngine engine;

    public AgentEngineHandleAdapter(AgentEngine engine) {
        this.engine = engine;
    }

    @Override
    public void setMemoryPrompt(String prompt) {
        engine.setMemoryPrompt(prompt);
    }

    @Override
    public void setSteerSink(SteerSink sink) {
        engine.setSteerSink(sink);
    }

    @Override
    public void setCancellationSource(Supplier<String> source) {
        engine.setCancellationSource(source);
    }

    @Override
    public void execute(String sessionId, String messageId, String query, List<ChatMessage> llmContext,
                        List<String> imageUrls) {
        try {
            engine.execute(sessionId, messageId, query, llmContext, imageUrls);
        } catch (AgentEngineException e) {
            // message 逐字保留（引擎的 message 即契约：error 事件的 error 字段与日志原文）
            throw new AgentExecutionException(e.getMessage(), e);
        }
    }
}
