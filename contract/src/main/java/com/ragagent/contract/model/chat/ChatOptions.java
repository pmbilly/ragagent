package com.ragagent.contract.model.chat;

import java.util.List;

/**
 * 聊天选项。
 *
 * <p>JSON 字段序 = 声明序。恒输出键包括 thinking——Boolean 为 null 时输出
 * "thinking":null，故用 ALWAYS 包含。线格式注解由
 * {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。</p>
 */
public class ChatOptions {

    private double temperature;
    private double topP;
    private int seed;
    /**
     * MaxTokens 与 MaxCompletionTokens 是同一个补全预算的别名，调用方可设任一个；
     * {@link #completionBudget()} 优先取 MaxCompletionTokens。
     * 出站的 Chat Completions JSON **恰好**携带 max_tokens / max_completion_tokens 之一，
     * 按 provider 选择（见 WireCompletionTokenField）。
     */
    private int maxTokens;
    private int maxCompletionTokens;
    private double frequencyPenalty;
    private double presencePenalty;
    /** null = 由模型默认决定 */
    private Boolean thinking;
    private List<ChatTool> tools;
    /** "auto" / "required" / "none" / 具体工具名 */
    private String toolChoice;
    /** null = 由模型决定 */
    private Boolean parallelToolCalls;
    /**
     * 非空时强制 JSON object，并把 schema 拼到最后一条 message 的 content 尾部。
     *
     * <p>持**不透明 JSON**（契约面以 {@code Object} 承载，运行时是 {@code JsonNode} 或纯 JDK 值）；
     * 需要树形操作的一侧经 {@code JsonMappers.toJsonNode} 一转即回。</p>
     */
    private Object format;

    /** provider 路由键（OpenAI prompt_cache_key）。空则回退到调用上下文里的 session ID。 */
    private String promptCacheKey;
    /** provider prompt 缓存 TTL 控制；none 关闭缓存标记，空/short 为默认 5 分钟，long 请求 1h/24h。 */
    private CacheRetention cacheRetention;

    /** 补全预算：MaxCompletionTokens 优先，否则 MaxTokens。 */
    public int completionBudget() {
        return maxCompletionTokens > 0 ? maxCompletionTokens : maxTokens;
    }

    public double getTemperature() { return temperature; }
    public void setTemperature(double v) { temperature = v; }
    public double getTopP() { return topP; }
    public void setTopP(double v) { topP = v; }
    public int getSeed() { return seed; }
    public void setSeed(int v) { seed = v; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int v) { maxTokens = v; }
    public int getMaxCompletionTokens() { return maxCompletionTokens; }
    public void setMaxCompletionTokens(int v) { maxCompletionTokens = v; }
    public double getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(double v) { frequencyPenalty = v; }
    public double getPresencePenalty() { return presencePenalty; }
    public void setPresencePenalty(double v) { presencePenalty = v; }
    public Boolean getThinking() { return thinking; }
    public void setThinking(Boolean v) { thinking = v; }
    public List<ChatTool> getTools() { return tools; }
    public void setTools(List<ChatTool> v) { tools = v; }
    public String getToolChoice() { return toolChoice; }
    public void setToolChoice(String v) { toolChoice = v; }
    public Boolean getParallelToolCalls() { return parallelToolCalls; }
    public void setParallelToolCalls(Boolean v) { parallelToolCalls = v; }
    public Object getFormat() { return format; }
    public void setFormat(Object v) { format = v; }
    public String getPromptCacheKey() { return promptCacheKey; }
    public void setPromptCacheKey(String v) { promptCacheKey = v; }
    public CacheRetention getCacheRetention() { return cacheRetention; }
    public void setCacheRetention(CacheRetention v) { cacheRetention = v; }
}
