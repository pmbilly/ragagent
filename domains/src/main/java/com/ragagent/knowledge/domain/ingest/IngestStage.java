package com.ragagent.knowledge.domain.ingest;

import java.util.Arrays;
import java.util.List;

/**
 * 入库的 5 个 canonical 阶段名。
 *
 * <p>阶段名的唯一来源：span 表的 {@code stage} 列、进度视图与任务日志都取 {@link #code()}。
 * 枚举顺序只是展示顺序，不是依赖顺序；依赖关系见 {@code SpanTracker} 的阶段表。</p>
 *
 * <p>MULTIMODAL 不是被 worker 推进的独立阶段：图片 OCR 在 DOC_READER 内部完成，
 * 只借用该阶段的 span 名。</p>
 */
public enum IngestStage {

    DOC_READER("docreader"),
    CHUNKING("chunking"),
    EMBEDDING("embedding"),
    MULTIMODAL("multimodal"),
    POST_PROCESS("postprocess");

    private final String code;

    IngestStage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(IngestStage::code).toList();
    }
}
