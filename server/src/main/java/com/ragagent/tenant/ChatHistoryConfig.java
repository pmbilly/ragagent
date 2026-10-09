package com.ragagent.tenant;


/**
 * 聊天历史配置段。
 * 三个字段都恒输出。knowledgeBaseId 由后端自动管理（隐藏 KB），
 * 客户端 PUT 携带的值会被丢弃（controller 重建对象）。
 */

public class ChatHistoryConfig {

    private boolean enabled;

    private String embeddingModelId = "";

    private String knowledgeBaseId = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { enabled = v; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String v) { embeddingModelId = v == null ? "" : v; }
    public String getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(String v) { knowledgeBaseId = v == null ? "" : v; }
}
