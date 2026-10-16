package com.ragagent.contract.retrieval;

/**
 * 向量索引行的领域级形状（原嵌在 {@code retrieval-engine} 的 {@code VectorStoreService} 内，
 * R3b 上提：业务域组装它、引擎写它）。
 *
 * <p>{@code source_type} 恒 0（{@code types.ChunkSourceType}）；{@code content} 是调用方
 * 组装好的索引文本。</p>
 */
public record IndexRow(
        String sourceId,
        String chunkId,
        String knowledgeId,
        String knowledgeBaseId,
        String content,
        boolean isEnabled,
        String tagId) {
}
