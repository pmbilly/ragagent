package com.ragagent.contract.knowledge;

import java.util.List;

/** 知识条目分页**视图**（L1，端口口径）：形状与 {@link ChunkPageView} 同形（items/total）。 */
public record KnowledgePageView(List<KnowledgeRowView> items, long total) {

    public KnowledgePageView {
        items = items == null ? List.of() : items;
    }
}
