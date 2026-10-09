package com.ragagent.event.payload;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 聊天生成事件数据。
 *
 * <p>零值输出 {@code {"query":"","modelId":"","isStream":false}}——
 * {@code is_stream} 恒输出，false 也输出。</p>
 */

public class ChatData {

    private String query = "";

    private String modelId = "";

    /** 空串省略 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private String response = "";

    /** 空串省略 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private String streamChunk = "";

    /** 0 省略 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int tokenCount;

    /** 0 省略 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long durationMs;

    /** false 恒输出 */
    @JsonProperty("isStream")
    private boolean isStream;

    /** null 或空省略 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> extra;

    public ChatData() {
    }

    public ChatData(String query, String modelId, String response, String streamChunk,
                    int tokenCount, long durationMs, boolean isStream, Map<String, Object> extra) {
        this.query = QueryData.orEmpty(query);
        this.modelId = QueryData.orEmpty(modelId);
        this.response = QueryData.orEmpty(response);
        this.streamChunk = QueryData.orEmpty(streamChunk);
        this.tokenCount = tokenCount;
        this.durationMs = durationMs;
        this.isStream = isStream;
        this.extra = extra;
    }

    public void setQuery(String v) {
        this.query = QueryData.orEmpty(v);
    }

    public String getQuery() {
        return query;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String v) {
        this.modelId = QueryData.orEmpty(v);
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String v) {
        this.response = QueryData.orEmpty(v);
    }

    public String getStreamChunk() {
        return streamChunk;
    }

    public void setStreamChunk(String v) {
        this.streamChunk = QueryData.orEmpty(v);
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int v) {
        this.tokenCount = v;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long v) {
        this.durationMs = v;
    }

    /** getter 也标注同名列：否则 Jackson 会把 isStream() 拆成多余的 "stream" 属性（实测踩过） */
    @JsonProperty("isStream")
    public boolean isStream() {
        return isStream;
    }

    @JsonProperty("isStream")
    public void setStream(boolean v) {
        this.isStream = v;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> v) {
        this.extra = v;
    }
}
