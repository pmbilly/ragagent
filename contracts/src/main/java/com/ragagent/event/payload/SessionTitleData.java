package com.ragagent.event.payload;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * 会话标题更新数据。
 * 两字段全部恒输出：零值输出 {@code {"sessionId":"","title":""}}。
 */

public class SessionTitleData {

    @JsonProperty("sessionId")
    private String sessionId = "";

    @JsonProperty("title")
    private String title = "";

    public SessionTitleData() {
    }

    public SessionTitleData(String sessionId, String title) {
        this.sessionId = QueryData.orEmpty(sessionId);
        this.title = QueryData.orEmpty(title);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String v) {
        this.sessionId = QueryData.orEmpty(v);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = QueryData.orEmpty(v);
    }
}
