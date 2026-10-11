package com.ragagent.agent.tools.spi;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.ragagent.agent.domain.ToolCallTarget;
import com.ragagent.llm.domain.ChatMessage;

/**
 * MCP 工具编排端口（引擎侧接口）。
 *
 * <p>引擎只声明"MCP 需要做什么"，实现放在 MCP 适配层（那里才允许依赖
 * {@code com.ragagent.mcp.*} 与审批栈）。这样 agent 引擎对业务域的 import 归零，
 * 成为可独立成模块的纯引擎域。</p>
 *
 * <p>方向：{@code agent} 定义 + 持有引用；MCP 适配层实现；装配期由 session 注入
 * （见 {@code AgentEngineAssembler}）。</p>
 */
public interface McpToolingGateway {

    /**
     * 启动期准备：按需广告来源，随后加载完整函数定义。
     *
     * @param direct 完整暴露的兼容路径（true = 不做 describe 才发布的限制）
     */
    void prepare(boolean direct);

    /** 在模型请求之间发布就绪定义（不做网络发现）；只在并行工具执行空闲时调用。 */
    void refresh();

    /** 把本会话已 describe / 调用过的工具重新发布，新引擎无需再 describe 一轮。 */
    void rememberHistory(List<ChatMessage> messages);

    /**
     * 展示层的调用目标解析（原始调用名、参数、provider 元数据保持可回放）。
     *
     * @return null = 不是 MCP 代理调用 / 展示层不得提前鉴权
     */
    ToolCallTarget callTarget(String name, JsonNode raw);

    /** 目录是否已 Prepare 过。 */
    boolean isPrepared();

    /** 完整暴露是否开启。 */
    boolean isDirect();

    /** 目录里是否存在该服务（提示词绑定显式服务提及用）。 */
    boolean hasServer(String serviceId);
}
