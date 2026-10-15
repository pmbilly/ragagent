package com.ragagent.common.knowledge;

import java.util.List;

/** 分块分页**视图**（L1，端口口径）：形状与域内 `ChunkPage` 同形（items/total）。 */
public record ChunkPageView(List<ChunkRowView> items, long total) {

    public ChunkPageView {
        items = items == null ? List.of() : items;
    }
}
