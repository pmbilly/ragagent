package com.ragagent.knowledge.api.kb;

import com.ragagent.knowledge.api.chunk.KnowledgeRowView;
import java.util.List;

/** 知识条目分页**视图**：形状与 {@code ChunkPageView} 同形（items/total）。 */
public record KnowledgePageView(List<KnowledgeRowView> items, long total) {

    public KnowledgePageView {
        items = items == null ? List.of() : items;
    }
}
