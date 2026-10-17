package com.ragagent.session.service.rag;

/**
 * RAG 管线阶段的事件键（编译期注册表）。
 *
 * <p>枚举常量是代码内唯一标识；{@link #key()} 是日志与录制样本里的下划线名字。
 * 新增阶段时在这里加一个值——{@code PipelineRegistryCompletenessTest} 会要求
 * {@code QaWiring} 为它注册至少一个插件。</p>
 */
public enum PipelineEventType {
    LOAD_HISTORY("load_history"),
    MEMORY_RECALL("memory_recall"),
    QUERY_UNDERSTAND("query_understand"),
    CHUNK_SEARCH("chunk_search"),
    CHUNK_SEARCH_PARALLEL("chunk_search_parallel"),
    ENTITY_SEARCH("entity_search"),
    CHUNK_RERANK("chunk_rerank"),
    WEB_FETCH("web_fetch"),
    CHUNK_MERGE("chunk_merge"),
    DATA_ANALYSIS("data_analysis"),
    INTO_CHAT_MESSAGE("into_chat_message"),
    CHAT_COMPLETION("chat_completion"),
    CHAT_COMPLETION_STREAM("chat_completion_stream"),
    FILTER_TOP_K("filter_top_k");

    private final String key;

    PipelineEventType(String key) {
        this.key = key;
    }

    /** 日志/录制样本里的阶段名（小写下划线）。 */
    public String key() {
        return key;
    }

    @Override
    public String toString() {
        return key;
    }
}
