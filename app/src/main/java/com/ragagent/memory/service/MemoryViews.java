package com.ragagent.memory.service;

import com.ragagent.contract.memory.MemoryItemView;
import com.ragagent.memory.domain.item.MemoryItem;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆实体 → L1 视图的投影：跨层出口只输出 {@link MemoryItemView}（id/kind/content），
 * 实体本身（embedding、衰减、目录亲和等）不出记忆域。
 */
public final class MemoryViews {

    private MemoryViews() {
    }

    public static MemoryItemView toView(MemoryItem item) {
        if (item == null) {
            return null;
        }
        return new MemoryItemView(item.getId(), item.getKind(), item.getContent());
    }

    public static List<MemoryItemView> toViews(List<MemoryItem> items) {
        List<MemoryItemView> out = new ArrayList<>(items == null ? 0 : items.size());
        if (items != null) {
            for (MemoryItem item : items) {
                out.add(toView(item));
            }
        }
        return out;
    }
}
