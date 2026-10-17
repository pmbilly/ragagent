package com.ragagent.contract.agent;

import java.util.List;

/**
 * agent 运行时配置的**读面**（原为 {@code agent.AgentConfig} 的字段读接口）。
 *
 * <p>为什么是接口而不是 record：该配置在域侧是<b>从 agent 配置 jsonb 反序列化并逐项填充</b>的
 * （{@code QaAgentConfig} 带 Jackson 注解并继承/自持全部字段），而契约面零依赖、不能带 Jackson；
 * 同时引擎**只读**这些字段（实测：引擎内无任何 {@code config.setXxx} 调用）。故契约收读面接口，
 * 两侧各自持有可变实现：agent-engine 的 {@code AgentConfig}（JSON 绑定用）与域侧的
 * {@code QaAgentConfig}（会话参数 + 引擎字段）。</p>
 *
 * <p>{@code UNLIMITED_MAX_ITERATIONS} 原为 {@code AgentConfig} 的常量，随读面上提。</p>
 */
public interface AgentRunSpec {

    /** ReAct 轮次上限哨兵：负数 = 无上限。 */
    int UNLIMITED_MAX_ITERATIONS = -1;

    // ---- 引擎消费的持久化字段 ----
    int getMaxIterations();

    List<String> getAllowedTools();

    double getTemperature();

    boolean isWebSearchEnabled();

    boolean isMultiTurnEnabled();

    Boolean getThinking();

    Boolean getCitationEnabled();

    boolean isRetainRetrievalHistory();

    int getLlmCallTimeout();

    int getMaxCompletionTokens();

    int getMaxToolOutputChars();

    int getMaxContextTokens();

    int getCompactionKeepRecentTokens();

    boolean isParallelToolCalls();

    // ---- 运行时字段（不持久化）----
    boolean isChatModelSupportsVision();

    String getVlmModelId();

    /** ReAct 循环是否无轮次上限（派生判定）。 */
    default boolean unlimitedIterations() {
        return getMaxIterations() < 0;
    }

    /** 引用输出开关；旧运行时配置没有该字段（null）时默认开。 */
    default boolean citationsEnabled() {
        return getCitationEnabled() == null || getCitationEnabled();
    }
}
