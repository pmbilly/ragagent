package com.ragagent.knowledge.support;

/**
 * 索引源类型常量（原 {@code retrieval-engine} 的 {@code EngineTypes.SOURCE_TYPE_*}，
 * 上提：业务域组装 {@link com.ragagent.contract.retrieval.IndexDocument} 时要写这个字段）。
 *
 * <p>取值沿用既有 wire 语义（0 = 文件 chunk；1 = FAQ），不动。</p>
 */
public final class SourceTypes {

    /** 文件/chunk 源。 */
    public static final int FILE = 0;

    /** FAQ 源。 */
    public static final int FAQ = 1;

    private SourceTypes() {
    }
}
