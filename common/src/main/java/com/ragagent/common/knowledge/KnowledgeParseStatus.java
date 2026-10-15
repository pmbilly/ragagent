package com.ragagent.common.knowledge;

/** 解析状态词汇（L1）：实体 `Knowledge` 的 PARSE_* 常量已迁此并委托，
 * 供跨域方（数据源建行时置 pending 等）与域内共用，避免为了一个字符串常量依赖域实体。 */
public final class KnowledgeParseStatus {

    public static final String PARSE_PENDING = "pending";
    public static final String PARSE_PROCESSING = "processing";
    public static final String PARSE_FINALIZING = "finalizing";
    public static final String PARSE_COMPLETED = "completed";
    public static final String PARSE_FAILED = "failed";
    public static final String PARSE_DELETING = "deleting";
    public static final String PARSE_CANCELLED = "cancelled";

    private KnowledgeParseStatus() {
    }
}
