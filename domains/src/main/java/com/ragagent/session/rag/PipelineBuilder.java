package com.ragagent.session.rag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管线装配器与预设管线。
 */
public final class PipelineBuilder {

    private final List<PipelineEventType> stages = new ArrayList<>();

    public static PipelineBuilder builder() {
        return new PipelineBuilder();
    }

    /** 无条件追加。 */
    public PipelineBuilder add(PipelineEventType... stages) {
        for (PipelineEventType stage : stages) {
            this.stages.add(stage);
        }
        return this;
    }

    /** 条件追加。 */
    public PipelineBuilder addIf(boolean cond, PipelineEventType... stages) {
        if (cond) {
            return add(stages);
        }
        return this;
    }

    /** 输出最终事件列表（builder 不复用）。 */
    public List<PipelineEventType> build() {
        return new ArrayList<>(stages);
    }

    /** 预设管线。 */
    public static Map<String, List<PipelineEventType>> presets() {
        Map<String, List<PipelineEventType>> p = new LinkedHashMap<>();
        p.put("chat", List.of(PipelineEventType.CHAT_COMPLETION));
        p.put("chat_stream", List.of(PipelineEventType.CHAT_COMPLETION_STREAM));
        p.put("chat_history_stream",
                List.of(PipelineEventType.LOAD_HISTORY, PipelineEventType.CHAT_COMPLETION_STREAM));
        p.put("rag", List.of(PipelineEventType.CHUNK_SEARCH, PipelineEventType.CHUNK_RERANK,
                PipelineEventType.CHUNK_MERGE, PipelineEventType.INTO_CHAT_MESSAGE,
                PipelineEventType.CHAT_COMPLETION));
        p.put("rag_stream", List.of(
                PipelineEventType.LOAD_HISTORY,
                PipelineEventType.QUERY_UNDERSTAND,
                PipelineEventType.CHUNK_SEARCH_PARALLEL,
                PipelineEventType.CHUNK_RERANK,
                PipelineEventType.CHUNK_MERGE,
                PipelineEventType.FILTER_TOP_K,
                PipelineEventType.DATA_ANALYSIS,
                PipelineEventType.INTO_CHAT_MESSAGE,
                PipelineEventType.CHAT_COMPLETION_STREAM));
        return p;
    }
}
