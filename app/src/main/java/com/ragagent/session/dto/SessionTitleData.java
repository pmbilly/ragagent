package com.ragagent.session.dto;




/**
 * 会话标题更新数据。
 * 两字段全部恒输出：零值输出 {@code {"sessionId":"","title":""}}。
 */

public class SessionTitleData {

    private String sessionId = "";

    private String title = "";

    public SessionTitleData() {
    }

    public SessionTitleData(String sessionId, String title) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.title = title == null ? "" : title;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String v) {
        this.sessionId = v == null ? "" : v;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String v) {
        this.title = v == null ? "" : v;
    }
}
