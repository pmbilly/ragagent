package com.ragagent.common.memory;

import java.util.List;

/**
 * 记忆检索上下文（B106）：给查询改写器用的背景/兴趣/文档词汇 + 背后条目视图。
 *
 * <p>原在 {@code memory.service}，因 L2 {@code chatpipeline} 的端口签名要用它而下沉到 common；
 * 条目改为 {@link MemoryItemView}。</p>
 *
 * @param background 这个人的一段紧凑描述，给查询改写器用
 * @param interests  他反复回来的那些主题
 * @param documents  他依赖的文档标题，作为改写器的词汇
 * @param items      以上内容背后的记忆条目，好让界面能显示是什么影响了检索
 */
public record MemoryRetrievalContext(String background, List<String> interests,
                                     List<String> documents, List<MemoryItemView> items) {

    /** 零值。 */
    public static final MemoryRetrievalContext EMPTY =
            new MemoryRetrievalContext("", null, null, null);

    /**
     * 是否没有贡献。
     *
     * <p>注意判据<b>不含 items</b>：三个展示项都空就是"没有贡献"，哪怕条目列表非空
     * （实际不会发生，但口径如此——与迁移前一致）。</p>
     */
    public boolean empty() {
        return (background == null || background.isEmpty())
                && (interests == null || interests.isEmpty())
                && (documents == null || documents.isEmpty());
    }
}
