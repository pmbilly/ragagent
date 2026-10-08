package com.ragagent.common.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 展示列表合并（B106 由 {@code memory.domain.MemoryText#mergeUsedMemories} 下沉到 L1）。
 *
 * <p>为什么下沉：L2 {@code chatpipeline} 的 {@code MemoryUsedMemories} 也要用它来合并"已展示的记忆"，
 * 而它是**与领域无关的纯函数**（泛型 + 键函数）——留在记忆域会让管线依赖该域（L2→L3）。</p>
 */
public final class ListMerges {

    private ListMerges() {
    }

    /**
     * 合并两份"展示给用户的"列表，每个键只保留**第一次**出现。
     *
     * <p>一条记忆可以影响一轮两次——一次塑造检索、一次被引在答案里——而用户应该只看到它列一次。</p>
     *
     * <p>两个细节：{@code additional} 为空时**原样返回 existing**（同一个列表对象，不是副本）；
     * 键为空的条目**不参与去重**、一律追加。</p>
     */
    public static <T> List<T> mergeDistinctByKey(List<T> existing, List<T> additional,
                                                 Function<T, String> keyOf) {
        if (additional == null || additional.isEmpty()) {
            return existing;
        }
        List<T> base = existing == null ? new ArrayList<>() : existing;
        Set<String> seen = new HashSet<>();
        List<T> merged = new ArrayList<>(base.size() + additional.size());
        for (List<T> list : List.of(base, additional)) {
            for (T item : list) {
                String key = keyOf.apply(item);
                if (key != null && !key.isEmpty()) {
                    if (!seen.add(key)) {
                        continue;
                    }
                }
                merged.add(item);
            }
        }
        return merged;
    }
}
