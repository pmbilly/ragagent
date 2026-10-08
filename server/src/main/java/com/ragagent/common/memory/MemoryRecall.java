package com.ragagent.common.memory;

import java.util.List;

/**
 * 记忆召回结果（B106）：{@code prompt}（注入用的记忆文本）+ 命中的条目视图。
 *
 * <p>原在 {@code memory.service}，因 L2 {@code chatpipeline} 的端口签名要用它而<b>下沉到 common</b>
 * （消费域 → 端口 ← 记忆域）；条目改为 {@link MemoryItemView}，实体不再越层。</p>
 */
public record MemoryRecall(String prompt, List<MemoryItemView> items) {

    /** 零值（无记忆命中）。 */
    public static final MemoryRecall EMPTY = new MemoryRecall("", null);
}
