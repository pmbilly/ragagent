package com.ragagent.contract.retrieval;

import java.util.List;



/**
 * 混合检索参数。
 *
 * <p>管线侧载体：HybridSearch seam（{@code com.ragagent.contract.pipeline.PipelinePorts.KnowledgeBaseService}）的入参。</p>
 */
public final class SearchParams {

    /** 检索词。query_embedding 提供且两种匹配未被禁用时可为空。 */
    private String queryText = "";
    /** 查询向量（null = 由实现侧现算）。 */
    private float[] queryEmbedding;
    private double vectorThreshold;
    private double keywordThreshold;
    private int matchCount;
    private boolean disableKeywordsMatch;
    private boolean disableVectorMatch;
    /** 文档级过滤。 */
    private List<String> knowledgeIds;
    /** 标签过滤（FAQ 优先过滤用）。 */
    private List<String> tagIds;
    /** 用户选择的逻辑标签范围（文档库仅作 tracing）。 */
    private List<String> scopeTagIds;
    private boolean onlyRecommended;
    /** 覆盖 HybridSearch 的单个 KB ID 参数，实现一次跨多 KB 检索。 */
    private List<String> knowledgeBaseIds;
    /** 跳过实现侧的父块/邻块/关系块补全（chat 管线的上下文组装在 merge 阶段）。 */
    private boolean skipContextEnrichment;

    public String getQueryText() { return queryText; }
    public void setQueryText(String v) { queryText = v == null ? "" : v; }
    public float[] getQueryEmbedding() { return queryEmbedding; }
    public void setQueryEmbedding(float[] v) { queryEmbedding = v; }
    public double getVectorThreshold() { return vectorThreshold; }
    public void setVectorThreshold(double v) { vectorThreshold = v; }
    public double getKeywordThreshold() { return keywordThreshold; }
    public void setKeywordThreshold(double v) { keywordThreshold = v; }
    public int getMatchCount() { return matchCount; }
    public void setMatchCount(int v) { matchCount = v; }
    public boolean isDisableKeywordsMatch() { return disableKeywordsMatch; }
    public void setDisableKeywordsMatch(boolean v) { disableKeywordsMatch = v; }
    public boolean isDisableVectorMatch() { return disableVectorMatch; }
    public void setDisableVectorMatch(boolean v) { disableVectorMatch = v; }
    public List<String> getKnowledgeIds() { return knowledgeIds; }
    public void setKnowledgeIds(List<String> v) { knowledgeIds = v; }
    public List<String> getTagIds() { return tagIds; }
    public void setTagIds(List<String> v) { tagIds = v; }
    public List<String> getScopeTagIds() { return scopeTagIds; }
    public void setScopeTagIds(List<String> v) { scopeTagIds = v; }
    public boolean isOnlyRecommended() { return onlyRecommended; }
    public void setOnlyRecommended(boolean v) { onlyRecommended = v; }
    public List<String> getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public void setKnowledgeBaseIds(List<String> v) { knowledgeBaseIds = v; }
    public boolean isSkipContextEnrichment() { return skipContextEnrichment; }
    public void setSkipContextEnrichment(boolean v) { skipContextEnrichment = v; }
}
