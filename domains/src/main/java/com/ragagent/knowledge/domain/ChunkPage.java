package com.ragagent.knowledge.domain;

import java.util.List;

/** chunk 分页结果：行与总数一并返回（调用方一次拿到两份）。 */
public record ChunkPage(List<Chunk> items, long total) {
    public ChunkPage {
        items = items == null ? List.of() : items;
    }
}
