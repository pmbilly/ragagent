package com.ragagent.contract.knowledge;

/**
 * 知识目录复合端口（L1）：按能力拆成窄端口，本接口只做聚合，供既有消费方按原类型注入。
 *
 * <p>能力面：{@link KnowledgeEntryReadPort}（知识读）、{@link ChunkFaqReadPort}
 * （分块/FAQ 读）、{@link KnowledgeBaseReadPort}（知识库读 + 闸门）、
 * {@link KnowledgeBaseWritePort}（知识库写 + 标签）、{@link KnowledgeIngestLookupPort}
 * （摄取查重）、{@link KnowledgeContentWritePort}（内容写/文件）、
 * {@link KnowledgeDeletePort}（删除）。新代码应按需注入窄端口。</p>
 */
public interface KnowledgeCatalogPort extends KnowledgeEntryReadPort, ChunkFaqReadPort,
        KnowledgeBaseReadPort, KnowledgeBaseWritePort, KnowledgeIngestLookupPort,
        KnowledgeContentWritePort, KnowledgeDeletePort {
}
