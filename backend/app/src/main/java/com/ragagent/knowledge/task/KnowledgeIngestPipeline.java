package com.ragagent.knowledge.task;

/**
 * 入库主链路的阶段协作者集合：取文本 → 分块落库 → 索引 → 收口。
 *
 * <p>只承载阶段对象，不携带仓储/租户状态；由 {@link KnowledgeProcessWiring} 构造，
 * Worker 只按阶段推进。</p>
 */
public record KnowledgeIngestPipeline(
        KnowledgeTextExtractor textExtractor,
        KnowledgeChunkWriter chunkWriter,
        KnowledgeIndexStage indexStage,
        KnowledgeFinalizingStage finalizingStage) {
}
