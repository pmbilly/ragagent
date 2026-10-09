package com.ragagent.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 检索配置段。
 *
 * <p>前六个字段恒输出；rrfK / rrfVectorWeight / rrfKeywordWeight 三个空值（0 / 0.0）省略键。浮点走 Jackson 默认形态（0.0）。</p>
 */

public class RetrievalConfig {

    private int embeddingTopK;


    private double vectorThreshold;


    private double keywordThreshold;

    private int rerankTopK;


    private double rerankThreshold;

    private String rerankModelId = "";

    /** 数值 0 省略键 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int rrfK;

    /** 0.0 省略键（NON_DEFAULT 对 primitive double 即 0.0） */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private double rrfVectorWeight;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
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
