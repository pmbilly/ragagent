package com.ragagent.session.domain;

import java.time.OffsetDateTime;

/**
 * embed 访客历史会话的列表行：只含列表渲染所需的列
 * （{@code SessionMapper.listEmbedVisitorSessions} 的投影）。
 */
public class EmbedVisitorSessionRow {

    private String id;
    private String title;
    private OffsetDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
