package com.ragagent.event.payload;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 查询相关事件数据。
 *
 * <p>字段按声明序输出；标 {@code NON_DEFAULT}/{@code NON_EMPTY} 的字段
 * （0/空/false/null）省略，其余恒输出（零值也输出）。</p>
 */

public class QueryData {

    private String originalQuery = "";

    /** 空串省略 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private String rewrittenQuery = "";

    private String sessionId = "";

    /** 空串省略 */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private String userId = "";

    /** null 或空 map 都省略 */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> extra;

    public QueryData() {
    }

    public QueryData(String originalQuery, String rewrittenQuery, String sessionId,
                     String userId, Map<String, Object> extra) {
        this.originalQuery = orEmpty(originalQuery);
        this.rewrittenQuery = orEmpty(rewrittenQuery);
        this.sessionId = orEmpty(sessionId);
        this.userId = orEmpty(userId);
        this.extra = extra;
    }

    static String orEmpty(String v) {
        return v == null ? "" : v;
    }

    public String getOriginalQuery() {
        return originalQuery;
    }

    public void setOriginalQuery(String v) {
        this.originalQuery = orEmpty(v);
    }

    public String getRewrittenQuery() {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String v) {
        this.rewrittenQuery = orEmpty(v);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String v) {
        this.sessionId = orEmpty(v);
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String v) {
        this.userId = orEmpty(v);
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> v) {
        this.extra = v;
    }
}
