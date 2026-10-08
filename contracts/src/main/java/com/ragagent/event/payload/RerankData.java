package com.ragagent.event.payload;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 排序事件数据。
 */

public class RerankData {

    @JsonProperty("query")
    private String query = "";

    /** 输入的候选数量 */
    @JsonProperty("inputCount")
    private int inputCount;

    /** 输出的结果数量 */
    @JsonProperty("outputCount")
    private int outputCount;

    @JsonProperty("modelId")
    private String modelId = "";

    /** 同 RetrievalData.threshold：包装类型防 Jackson 原生 primitive 序列化器绕过 EventJson 的浮点格式 */
    @JsonProperty("threshold")
    private Double threshold = 0.0;

    /** null 或空省略 */
    @JsonProperty("results")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Object results;

    /** 排序耗时（毫秒）；0 省略 */
    @JsonProperty("durationMs")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long durationMs;

    /** null 或空省略 */
    @JsonProperty("extra")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> extra;

    public RerankData() {
    }

    public RerankData(String query, int inputCount, int outputCount, String modelId,
                      double threshold, Object results, long durationMs, Map<String, Object> extra) {
        this.query = QueryData.orEmpty(query);
        this.inputCount = inputCount;
        this.outputCount = outputCount;
        this.modelId = QueryData.orEmpty(modelId);
        this.threshold = threshold;
        this.results = results;
        this.durationMs = durationMs;
        this.extra = extra;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String v) {
        this.query = QueryData.orEmpty(v);
    }

    public int getInputCount() {
        return inputCount;
    }

    public void setInputCount(int v) {
        this.inputCount = v;
    }

    public int getOutputCount() {
        return outputCount;
    }

    public void setOutputCount(int v) {
        this.outputCount = v;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String v) {
        this.modelId = QueryData.orEmpty(v);
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double v) {
        this.threshold = v == null ? 0.0 : v;
    }

    @JsonProperty("results")
    public Object getResults() {
        return results;
    }

    public void setResults(Object v) {
        this.results = v;
    }

    @JsonProperty("durationMs")
    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long v) {
        this.durationMs = v;
    }

    @JsonProperty("extra")
    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> v) {
        this.extra = v;
    }
}
