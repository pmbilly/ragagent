package com.ragagent.session.rag;

/**
 * 状态段 · 查询计划：改写后的查询、意图、图像描述、引用上下文、系统提示词覆盖与记忆段落。
 *
 * <p>写入口集中在 {@code QUERY_UNDERSTAND}（改写/意图/图像/引用）与
 * {@code MEMORY_RECALL}（记忆段落）；下游检索与生成段只读。</p>
 */
public final class QueryPlan {

    private String rewriteQuery = "";
    private String intent = "";
    private String imageDescription = "";
    private String quotedContext = "";
    private String systemPromptOverride = "";
    private String memoryPrompt = "";

    public String getRewriteQuery() {
        return rewriteQuery;
    }

    public void setRewriteQuery(String v) {
        rewriteQuery = v == null ? "" : v;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String v) {
        intent = v == null ? "" : v;
    }

    public String getImageDescription() {
        return imageDescription;
    }

    public void setImageDescription(String v) {
        imageDescription = v == null ? "" : v;
    }

    public String getQuotedContext() {
        return quotedContext;
    }

    public void setQuotedContext(String v) {
        quotedContext = v == null ? "" : v;
    }

    public String getSystemPromptOverride() {
        return systemPromptOverride;
    }

    public void setSystemPromptOverride(String v) {
        systemPromptOverride = v == null ? "" : v;
    }

    public String getMemoryPrompt() {
        return memoryPrompt;
    }

    public void setMemoryPrompt(String v) {
        memoryPrompt = v == null ? "" : v;
    }
}
