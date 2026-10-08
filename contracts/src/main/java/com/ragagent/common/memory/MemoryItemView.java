package com.ragagent.common.memory;

/**
 * 记忆条目**只读视图**（B106）：供 L2（{@code chatpipeline}）与各域消费，替代 {@code memory.domain.MemoryItem} 实体。
 *
 * <p>字段取管线的实际读取面（展示用）：{@code id} / {@code kind} / {@code content}。
 * 记忆域的实体（含 embedding、时间衰减、目录亲和等）不出现在本层。</p>
 */
public final class MemoryItemView {

    private final String id;
    private final String kind;
    private final String content;

    public MemoryItemView(String id, String kind, String content) {
        this.id = id;
        this.kind = kind;
        this.content = content;
    }

    public String getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getContent() {
        return content;
    }
}
