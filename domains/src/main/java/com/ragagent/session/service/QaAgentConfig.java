package com.ragagent.session.service;

import com.ragagent.contract.agent.AgentRunSpec;
import com.ragagent.contract.retrieval.SearchTarget.SearchTargets;
import java.util.ArrayList;
import java.util.List;

/**
 * 装配期 AgentConfig 扩展。
 *
 * <p>{@code contract.agent.AgentRunSpec} 只收引擎消费字段；本子类携带
 * 装配路径的其余消费字段——引擎经父类 getter 读取，不受影响；
 * 本类只新增字段与存取器，零行为覆盖。</p>
 */
public class QaAgentConfig implements AgentRunSpec {

    // ---- 引擎消费字段（起自持：原继承自 agent.AgentConfig 的字段；
    // 契约面读接口 AgentRunSpec 把它们与域侧解耦，Jackson 注解留在此处供 JSON 绑定）----
    private int maxIterations;
    private List<String> allowedTools;
    private double temperature;
    private boolean webSearchEnabled;
    private boolean multiTurnEnabled;
    private Boolean thinking;
    private Boolean citationEnabled;
    private boolean retainRetrievalHistory;
    private int llmCallTimeout;
    private int maxCompletionTokens;
    private int maxToolOutputChars;
    private int maxContextTokens;
    private int compactionKeepRecentTokens;
    private boolean parallelToolCalls;
    private boolean chatModelSupportsVision;
    private String vlmModelId = "";


    private int webSearchMaxResults;
    private String webSearchProviderId = "";
    private int historyTurns;
    private boolean memoryEnabled;
    private String mcpSelectionMode = "";
    private List<String> mcpServices = new ArrayList<>();
    private int mcpAuthWaitTimeout;
    private boolean retrieveKbOnlyWhenMentioned;
    private boolean sharedAgentReadOnly;
    private List<String> knowledgeBases = new ArrayList<>();
    private List<String> knowledgeIds = new ArrayList<>();
    private SearchTargets searchTargets;
    private boolean useCustomSystemPrompt;
    private String systemPrompt = "";
    private boolean skillsEnabled;
    private List<String> allowedSkills;
    private List<String> pinnedSkillNames;
    private List<String> pinnedMcpServiceIds;

    public int getWebSearchMaxResults() { return webSearchMaxResults; }
    public void setWebSearchMaxResults(int v) { webSearchMaxResults = v; }
    public String getWebSearchProviderId() { return webSearchProviderId; }
    public void setWebSearchProviderId(String v) { webSearchProviderId = v == null ? "" : v; }
    public int getHistoryTurns() { return historyTurns; }
    public void setHistoryTurns(int v) { historyTurns = v; }
    public boolean isMemoryEnabled() { return memoryEnabled; }
    public void setMemoryEnabled(boolean v) { memoryEnabled = v; }
    public String getMcpSelectionMode() { return mcpSelectionMode; }
    public void setMcpSelectionMode(String v) { mcpSelectionMode = v == null ? "" : v; }
    public List<String> getMcpServices() { return mcpServices; }
    public void setMcpServices(List<String> v) { mcpServices = v == null ? new ArrayList<>() : v; }
    public int getMcpAuthWaitTimeout() { return mcpAuthWaitTimeout; }
    public void setMcpAuthWaitTimeout(int v) { mcpAuthWaitTimeout = v; }
    public boolean isRetrieveKbOnlyWhenMentioned() { return retrieveKbOnlyWhenMentioned; }
    public void setRetrieveKbOnlyWhenMentioned(boolean v) { retrieveKbOnlyWhenMentioned = v; }
    public boolean isSharedAgentReadOnly() { return sharedAgentReadOnly; }
    public void setSharedAgentReadOnly(boolean v) { sharedAgentReadOnly = v; }
    public List<String> getKnowledgeBases() { return knowledgeBases; }
    public void setKnowledgeBases(List<String> v) { knowledgeBases = v == null ? new ArrayList<>() : v; }
    public List<String> getKnowledgeIds() { return knowledgeIds; }
    public void setKnowledgeIds(List<String> v) { knowledgeIds = v == null ? new ArrayList<>() : v; }
    public SearchTargets getSearchTargets() { return searchTargets; }
    public void setSearchTargets(SearchTargets v) { searchTargets = v; }
    public boolean useCustomSystemPrompt() { return useCustomSystemPrompt; }
    public void setUseCustomSystemPrompt(boolean v) { useCustomSystemPrompt = v; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String v) { systemPrompt = v == null ? "" : v; }
    public boolean isSkillsEnabled() { return skillsEnabled; }
    public void setSkillsEnabled(boolean v) { skillsEnabled = v; }
    public List<String> getAllowedSkills() { return allowedSkills; }
    public void setAllowedSkills(List<String> v) { allowedSkills = v; }
    public List<String> getPinnedSkillNames() { return pinnedSkillNames; }
    public void setPinnedSkillNames(List<String> v) { pinnedSkillNames = v; }
    public List<String> getPinnedMcpServiceIds() { return pinnedMcpServiceIds; }
    public void setPinnedMcpServiceIds(List<String> v) { pinnedMcpServiceIds = v; }

    // ══════════════ 引擎消费字段的存取（原继承自 AgentConfig）══════════════

    @Override public int getMaxIterations() { return maxIterations; }
    public void setMaxIterations(int v) { maxIterations = v; }
    @Override public List<String> getAllowedTools() { return allowedTools; }
    public void setAllowedTools(List<String> v) { allowedTools = v; }
    @Override public double getTemperature() { return temperature; }
    public void setTemperature(double v) { temperature = v; }
    @Override public boolean isWebSearchEnabled() { return webSearchEnabled; }
    public void setWebSearchEnabled(boolean v) { webSearchEnabled = v; }
    @Override public boolean isMultiTurnEnabled() { return multiTurnEnabled; }
    public void setMultiTurnEnabled(boolean v) { multiTurnEnabled = v; }
    @Override public Boolean getThinking() { return thinking; }
    public void setThinking(Boolean v) { thinking = v; }
    @Override public Boolean getCitationEnabled() { return citationEnabled; }
    public void setCitationEnabled(Boolean v) { citationEnabled = v; }
    @Override public boolean isRetainRetrievalHistory() { return retainRetrievalHistory; }
    public void setRetainRetrievalHistory(boolean v) { retainRetrievalHistory = v; }
    @Override public int getLlmCallTimeout() { return llmCallTimeout; }
    public void setLlmCallTimeout(int v) { llmCallTimeout = v; }
    @Override public int getMaxCompletionTokens() { return maxCompletionTokens; }
    public void setMaxCompletionTokens(int v) { maxCompletionTokens = v; }
    @Override public int getMaxToolOutputChars() { return maxToolOutputChars; }
    public void setMaxToolOutputChars(int v) { maxToolOutputChars = v; }
    @Override public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int v) { maxContextTokens = v; }
    @Override public int getCompactionKeepRecentTokens() { return compactionKeepRecentTokens; }
    public void setCompactionKeepRecentTokens(int v) { compactionKeepRecentTokens = v; }
    @Override public boolean isParallelToolCalls() { return parallelToolCalls; }
    public void setParallelToolCalls(boolean v) { parallelToolCalls = v; }
    @Override public boolean isChatModelSupportsVision() { return chatModelSupportsVision; }
    public void setChatModelSupportsVision(boolean v) { chatModelSupportsVision = v; }
    @Override public String getVlmModelId() { return vlmModelId; }
    public void setVlmModelId(String v) { vlmModelId = v == null ? "" : v; }
}
