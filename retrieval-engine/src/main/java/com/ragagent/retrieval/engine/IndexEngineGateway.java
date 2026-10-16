package com.ragagent.retrieval.engine;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ragagent.contract.model.Embedder;
import com.ragagent.contract.retrieval.IndexDocument;
import com.ragagent.contract.retrieval.IndexEngine;
import com.ragagent.contract.retrieval.IndexEngineResolver;
import com.ragagent.contract.retrieval.TenantStoreOwnership;

/**
 * 索引引擎解析 + 适配（R3b 契约化）：把内部 {@link CompositeRetrieveEngine} 包成契约面
 * {@link IndexEngine}，并把业务域的 {@link IndexDocument} 映射回内部形状
 * {@link EngineTypes.IndexInfo}。
 *
 * <p><b>为什么需要适配层</b>：内部形状（含公有可变字段的 {@code EngineTypes.IndexInfo}）是各引擎适配器共用的
 * 实现细节，按 §2.1 载体红线不上提契约；域侧只认领域级载体，映射收在本层一处。</p>
 *
 * <p>解析口径与迁出前的 domain 侧网关逐字一致：{@code createForKb(registry, ownership, tenantId,
 * storeId, fallbackEngineType=null)}（内含跨租户归属校验 + 失败分类）。</p>
 */
@Component
public class IndexEngineGateway implements IndexEngineResolver {

    private final RetrieveEngineRegistry retrieveEngineRegistry;
    private final TenantStoreOwnership storeOwnership;

    public IndexEngineGateway(RetrieveEngineRegistry retrieveEngineRegistry,
                              TenantStoreOwnership storeOwnership) {
        this.retrieveEngineRegistry = retrieveEngineRegistry;
        this.storeOwnership = storeOwnership;
    }

    @Override
    public IndexEngine resolveFor(long tenantId, String vectorStoreId) {
        CompositeRetrieveEngine engine = RetrieveEngineFactories.createForKb(
                retrieveEngineRegistry, storeOwnership, tenantId, vectorStoreId, null);
        return engine == null ? null : new Adapter(engine);
    }

    /** 内部形状映射 + 转发（一个句柄一个绑定引擎）。 */
    private static final class Adapter implements IndexEngine {

        private final CompositeRetrieveEngine engine;

        Adapter(CompositeRetrieveEngine engine) {
            this.engine = engine;
        }

        /** 领域级载体 → 内部形状（引擎侧生成字段：id 留空由引擎填、推荐位直传）。 */
        private static EngineTypes.IndexInfo toIndexInfo(IndexDocument doc) {
            EngineTypes.IndexInfo info = new EngineTypes.IndexInfo();
            info.content = doc.content();
            info.sourceId = doc.sourceId();
            info.sourceType = doc.sourceType();
            info.chunkId = doc.chunkId();
            info.knowledgeId = doc.knowledgeId();
            info.knowledgeBaseId = doc.knowledgeBaseId();
            info.knowledgeType = doc.knowledgeType();
            info.tagId = doc.tagId();
            info.isEnabled = doc.enabled();
            info.isRecommended = doc.recommended();
            return info;
        }

        private static List<EngineTypes.IndexInfo> toIndexInfos(List<IndexDocument> docs) {
            return docs == null ? List.of() : docs.stream().map(Adapter::toIndexInfo).toList();
        }

        @Override
        public void index(Embedder embedder, IndexDocument document) throws Exception {
            engine.index(embedder, toIndexInfo(document));
        }

        @Override
        public void batchIndex(Embedder embedder, List<IndexDocument> documents) throws Exception {
            engine.batchIndex(embedder, toIndexInfos(documents));
        }

        @Override
        public void deleteByChunkIdList(List<String> chunkIds, int dimension, String knowledgeType)
                throws Exception {
            engine.deleteByChunkIdList(chunkIds, dimension, knowledgeType);
        }

        @Override
        public void deleteBySourceIdList(List<String> sourceIds, int dimension, String knowledgeType)
                throws Exception {
            engine.deleteBySourceIdList(sourceIds, dimension, knowledgeType);
        }

        @Override
        public void deleteByKnowledgeIdList(List<String> knowledgeIds, int dimension,
                                            String knowledgeType) throws Exception {
            engine.deleteByKnowledgeIdList(knowledgeIds, dimension, knowledgeType);
        }

        @Override
        public void copyIndices(String sourceKnowledgeBaseId, Map<String, String> sourceToTargetKbIdMap,
                                Map<String, String> sourceToTargetChunkIdMap, String targetKnowledgeBaseId,
                                int dimension, String knowledgeType) throws Exception {
            engine.copyIndices(sourceKnowledgeBaseId, sourceToTargetKbIdMap, sourceToTargetChunkIdMap,
                    targetKnowledgeBaseId, dimension, knowledgeType);
        }

        @Override
        public void moveKnowledgeIndices(String sourceKnowledgeBaseId, String targetKnowledgeBaseId,
                                         String knowledgeId, List<String> chunkIds, int dimension,
                                         String knowledgeType) throws Exception {
            engine.moveKnowledgeIndices(sourceKnowledgeBaseId, targetKnowledgeBaseId, knowledgeId,
                    chunkIds, dimension, knowledgeType);
        }

        @Override
        public void batchUpdateChunkEnabledStatus(Map<String, Boolean> chunkStatusMap) throws Exception {
            engine.batchUpdateChunkEnabledStatus(chunkStatusMap);
        }

        @Override
        public void batchUpdateChunkTagID(Map<String, String> chunkTagMap) throws Exception {
            engine.batchUpdateChunkTagID(chunkTagMap);
        }

        @Override
        public long estimateStorageSize(Embedder embedder, List<IndexDocument> documents) {
            return engine.estimateStorageSize(embedder, toIndexInfos(documents));
        }
    }
}
