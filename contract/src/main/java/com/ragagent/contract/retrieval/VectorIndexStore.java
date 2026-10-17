package com.ragagent.contract.retrieval;

import java.util.List;
import java.util.Map;

/**
 * 向量索引写面端口（业务域 → 向量店）。
 *
 * <p> 契约化：业务域（知识索引/迁移/克隆/FAQ 等）原先直注 {@code retention-engine} 的
 * {@code VectorStoreService} 实现类；这里只收"域侧真调用"的那几个方法，实现留在
 * retrieval-engine（本面只依赖 {@link IndexRow} 这一领域级形状）。</p>
 */
public interface VectorIndexStore {

    /** 写一批索引行与对应向量（长度须一致）。 */
    void saveIndexRows(List<IndexRow> rows, List<float[]> vectors);

    /** 按 chunk id 删。 */
    void deleteByChunkId(List<String> chunkIds);

    /** 按源 id 删。 */
    void deleteBySourceId(List<String> sourceIds);

    /** 按知识 id 删。 */
    void deleteByKnowledgeId(List<String> knowledgeIds);

    /** 批量改启用状态。 */
    void batchUpdateChunkEnabledStatus(Map<String, Boolean> chunkStatusMap);

    /** 批量改标签。 */
    void batchUpdateChunkTagId(Map<String, String> chunkTagMap);

    /** 估算存储体量（原实现里的静态方法，端口面按实例方法给出）。 */
    long estimateStorageSize(List<IndexRow> rows, int dimension);
}
