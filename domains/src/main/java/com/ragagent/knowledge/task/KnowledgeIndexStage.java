package com.ragagent.knowledge.task;

import com.ragagent.contract.error.BizException;
import com.ragagent.contract.graph.NameSpace;
import com.ragagent.contract.model.Embedder;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.retrieval.IndexEngine;
import com.ragagent.contract.retrieval.RetrieveGraphRepository;
import com.ragagent.contract.retrieval.VectorIndexStore;
import com.ragagent.knowledge.client.EmbedderClient;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.domain.KnowledgeProcessingSpan;
import com.ragagent.knowledge.domain.span.SpanTracker;
import com.ragagent.knowledge.index.ChunkVectorIndexer;
import com.ragagent.knowledge.index.KnowledgeVectorWrites;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.service.image.ExtractedImageStore;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入库索引阶段：预清理、失败清场、向量化与向量/图谱清理。
 *
 * <p>从 {@link KnowledgeProcessWorker} 抽出；不变式保持：先删旧 chunk（无条件）+
 * 向量（仅启用且模型可用）+ 旧图谱（失败仅告警），失败清理只删本次尝试的 chunk。</p>
 */
final class KnowledgeIndexStage {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexStage.class);

    private final ChunkRepository chunkRepository;
    private final ExtractedImageStore extractedImageStore;
    private final VectorIndexStore vectorStore;
    private final ModelCatalogPort modelCatalog;
    private final KnowledgeVectorWrites vectorWrites;
    private final ChunkVectorIndexer chunkVectorIndexer;
    private final ModelRuntime modelRuntimeAssembler;
    private final RetrieveGraphRepository graphRepository;
    private final SpanTracker spanTracker;

    KnowledgeIndexStage(
            ChunkRepository chunkRepository,
            ExtractedImageStore extractedImageStore,
            VectorIndexStore vectorStore,
            ModelCatalogPort modelCatalog,
            KnowledgeVectorWrites vectorWrites,
            ChunkVectorIndexer chunkVectorIndexer,
            ModelRuntime modelRuntimeAssembler,
            RetrieveGraphRepository graphRepository,
            SpanTracker spanTracker) {
        this.chunkRepository = chunkRepository;
        this.extractedImageStore = extractedImageStore;
        this.vectorStore = vectorStore;
        this.modelCatalog = modelCatalog;
        this.vectorWrites = vectorWrites;
        this.chunkVectorIndexer = chunkVectorIndexer;
        this.modelRuntimeAssembler = modelRuntimeAssembler;
        this.graphRepository = graphRepository;
        this.spanTracker = spanTracker;
    }

    /** 失败清场（按尝试划界——只删本次产出的 chunks；清单未知时退回知识粒度兜底；向量仍知识粒度）。 */
    void cleanupFailedAttempt(String knowledgeId, List<String> attemptChunkIds,
            Knowledge k, KnowledgeBase kb, EmbedderClient.EmbedConfig embedConfig) {
        if (attemptChunkIds.isEmpty()) {
            log.warn("[KnowledgeProcess] no enumerable chunks; knowledge-wide cleanup for {}", knowledgeId);
            chunkRepository.deleteByKnowledgeId(knowledgeId);
        } else {
            chunkRepository.deleteByIds(attemptChunkIds);
        }
        deleteKnowledgeVectors(k, kb, embedConfig, knowledgeId);
    }

    /** 预清理：回收旧抽取图片 + 删旧 chunks 行（无条件）+ 向量行（仅启用且模型可用）+ 旧图谱（失败仅告警）。 */
    void preClean(Knowledge k, KnowledgeBase kb,
            EmbedderClient.EmbedConfig embedConfig, String knowledgeId) {
        extractedImageStore.releaseFor(k.getTenantId() == null ? 0L : k.getTenantId(), knowledgeId);
        chunkRepository.deleteByKnowledgeId(knowledgeId);
        deleteKnowledgeVectors(k, kb, embedConfig, knowledgeId);
        deleteGraphData(k.getKnowledgeBaseId(), knowledgeId);
    }

    /**
     * 向量化：绑定外部 store 的 KB 走引擎口（embedding 与分批重试由引擎承担，本地 pg
     * 直连只服务未绑定 KB）；index 内容 = title + chunk 的 embeddingContent；向量化
     * 未启用 → embedding 阶段记 skip。
     */
    void embedChunks(Knowledge k, KnowledgeBase kb, EmbedderClient.EmbedConfig embedConfig,
            List<Chunk> chunks, String knowledgeId, int attempt) throws Exception {
        if (embedConfig == null) {
            skipStageSpan(attempt, knowledgeId, KnowledgeProcessingSpan.STAGE_EMBEDDING, "skipped");
            return;
        }
        SpanTracker.SpanHandle embedSpan = beginStageSpan(attempt, knowledgeId,
                KnowledgeProcessingSpan.STAGE_EMBEDDING,
                Map.of(
                        "chunks_to_embed", chunks.size(),
                        "model_id", k.getEmbeddingModelId() == null
                                ? "" : k.getEmbeddingModelId()));
        try {
            chunkVectorIndexer.indexChunks(kb, chunks);
        } catch (BizException e) {
            throw new IllegalStateException(
                    e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
        endStageSpan(embedSpan, Map.of(
                "chunks_embedded", chunks.size()));
    }

    /** 清该知识在源 KB 命名空间下的旧图谱（失败仅告警——图里可能本来就没有这条知识）。 */
    private void deleteGraphData(String knowledgeBaseId, String knowledgeId) {
        try {
            graphRepository.delGraph(List.of(new NameSpace(
                    knowledgeBaseId, knowledgeId)));
        } catch (RuntimeException e) {
            log.warn("Failed to delete existing graph data (may not exist)", e);
        }
    }

    /**
     * 知识删除/重处理的向量行清理——写链改道：绑定 store 的 KB 走引擎口
     * （GetEmbeddingModel → DeleteByKnowledgeIDList）；未绑定保持 pg 直连。
     */
    private void deleteKnowledgeVectors(Knowledge k, KnowledgeBase kb,
            EmbedderClient.EmbedConfig embedConfig, String knowledgeId) {
        IndexEngine boundEngine = vectorWrites.boundEngine(kb, k.getTenantId());
        if (boundEngine != null) {
            try {
                Embedder emb = modelRuntimeAssembler.getEmbeddingModel(kb.getEmbeddingModelId());
                boundEngine.deleteByKnowledgeIdList(List.of(knowledgeId),
                        emb.getDimensions(), kb.getType());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            }
            return;
        }
        if (embedConfig != null) {
            vectorStore.deleteByKnowledgeId(List.of(knowledgeId));
        }
    }

    EmbedderClient.EmbedConfig resolveEmbedConfig(Knowledge k, KnowledgeBase kb) {
        if (!(kb.getIndexingStrategy().isVectorEnabled() || kb.getIndexingStrategy().isKeywordEnabled())) {
            return null;
        }
        String modelId = k.getEmbeddingModelId() == null || k.getEmbeddingModelId().isEmpty()
                ? kb.getEmbeddingModelId() : k.getEmbeddingModelId();
        if (modelId == null || modelId.isEmpty()) {
            throw new IllegalStateException("embedding model is not configured");
        }
        ModelRowView model = modelCatalog.findModelVisible(k.getTenantId(), modelId);
        if (model == null) {
            throw new IllegalStateException("embedding model not found: " + modelId);
        }
        return EmbedderClient.configFrom(model);
    }

    private SpanTracker.SpanHandle beginStageSpan(int attempt, String knowledgeId,
            String stage, Map<String, Object> input) {
        if (attempt <= 0) {
            return null;
        }
        return spanTracker.beginStage(knowledgeId, attempt, stage, input);
    }

    private void endStageSpan(SpanTracker.SpanHandle span, Map<String, Object> output) {
        if (span != null) {
            spanTracker.endSpan(span, output);
        }
    }

    /** 无 begin 记录时先合成一行再 skip（保 schema 不变量）。 */
    private void skipStageSpan(int attempt, String knowledgeId, String stage, String reason) {
        if (attempt <= 0) {
            return;
        }
        SpanTracker.SpanHandle span = spanTracker.lookupStage(knowledgeId, attempt, stage);
        if (span == null) {
            span = spanTracker.beginStage(knowledgeId, attempt, stage, null);
        }
        if (span != null) {
            spanTracker.skipSpan(span, reason);
        }
    }
}
