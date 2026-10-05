package com.ragagent.auth.domain.tenantconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 检索配置段。
 *
 * <p>前六个字段恒输出；rrf_* 三个空值（0 / 0.0）省略键。浮点走 Jackson 默认形态（0.0）。</p>
 */

public class RetrievalConfig {

    @JsonProperty("embedding_top_k")
    private int embeddingTopK;


    @JsonProperty("vector_threshold")
    private double vectorThreshold;


    @JsonProperty("keyword_threshold")
    private double keywordThreshold;

    @JsonProperty("rerank_top_k")
    private int rerankTopK;


    @JsonProperty("rerank_threshold")
    private double rerankThreshold;

    @JsonProperty("rerank_model_id")
    private String rerankModelId = "";

    /** 数值 0 省略键 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonProperty("rrf_k")
    private int rrfK;

    /** 0.0 省略键（NON_DEFAULT 对 primitive double 即 0.0） */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonProperty("rrf_vector_weight")
    private double rrfVectorWeight;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @JsonProperty("rrf_keyword_weight")
    private double rrfKeywordWeight;

    public int getEmbeddingTopK() { return embeddingTopK; }
    public void setEmbeddingTopK(int v) { embeddingTopK = v; }
    public double getVectorThreshold() { return vectorThreshold; }
    public void setVectorThreshold(double v) { vectorThreshold = v; }
    public double getKeywordThreshold() { return keywordThreshold; }
    public void setKeywordThreshold(double v) { keywordThreshold = v; }
    public int getRerankTopK() { return rerankTopK; }
    public void setRerankTopK(int v) { rerankTopK = v; }
    public double getRerankThreshold() { return rerankThreshold; }
    public void setRerankThreshold(double v) { rerankThreshold = v; }
    public String getRerankModelId() { return rerankModelId; }
    public void setRerankModelId(String v) { rerankModelId = v == null ? "" : v; }
    public int getRrfK() { return rrfK; }
    public void setRrfK(int v) { rrfK = v; }
    public double getRrfVectorWeight() { return rrfVectorWeight; }
    public void setRrfVectorWeight(double v) { rrfVectorWeight = v; }
    public double getRrfKeywordWeight() { return rrfKeywordWeight; }
    public void setRrfKeywordWeight(double v) { rrfKeywordWeight = v; }
}
