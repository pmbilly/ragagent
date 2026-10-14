package com.ragagent.knowledge.index;

import com.ragagent.knowledge.repository.KnowledgeRepository;

import com.ragagent.knowledge.repository.KnowledgeBaseRepository;

import java.util.ArrayList;
import com.ragagent.knowledge.config.BatchEmbedProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.common.error.AppError;
import com.ragagent.common.error.BizException;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.common.knowledge.DocumentChunkMetadata;
import com.ragagent.common.knowledge.GeneratedQuestion;
import com.ragagent.knowledge.domain.KnowledgeBaseIndexingStrategy;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.model.domain.Model;
import com.ragagent.model.service.ModelRuntimeAssembler;
import com.ragagent.model.service.ModelService;
import com.ragagent.model.service.ModelService.ModelNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ragagent.embedding.Embedder;
import com.ragagent.retrieval.engine.CompositeRetrieveEngine;
import com.ragagent.retrieval.engine.EngineTypes;
import com.ragagent.retrieval.engine.EngineTypes.IndexInfo;
import com.ragagent.common.web.JsonMappers;
import com.ragagent.knowledge.client.EmbedderClient;
import com.ragagent.retrieval.engine.VectorStoreService;
import com.ragagent.retrieval.support.ChunkSearchUtil;
import com.ragagent.knowledge.support.KnowledgeIndexContent;

/**
 * chunk 向量行的重建执行体（2026-09-22 走查批：把「路由在、执行体占位」的两处
 * 索引缺口收敛到同一实现）：
 * <ul>
 *       （knowledge_process）：多 chunk 版（摘要 chunk 维护 / 问题重生成）；</li>
 *       （chunk）：单 chunk 版（chunk 编辑链路；disabled 块删旧不重建）。</li>
 * </ul>
 * <p><b>source_id 契约</b>：chunk 行 = chunkID（无前缀）；生成问题行 =
 * {@code generatedQuestionSourceId}（chunkID-qID；超 64 字节折叠
 * {@code chunkID-q<sha256 前 12 字节 hex>}）。索引文本 =
 * {@code title + "\n" + EmbeddingContent}（KnowledgeIndexContent.build）。</p>
 */
@Service
public class ChunkVectorIndexer {

    private static final Logger log = LoggerFactory.getLogger(ChunkVectorIndexer.class);

    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeRepository knowledgeRepository;
    private final ModelService modelService;
    private final EmbedderClient embedder;
    private final VectorStoreService vectorStore;
    private final KnowledgeVectorWrites vectorWrites;
    private final ModelRuntimeAssembler modelRuntimeAssembler;
    /** 批量向量化批大小（属性绑定）。 */
    private final BatchEmbedProperties batchEmbedProperties;

    public ChunkVectorIndexer(KnowledgeBaseRepository kbRepository,
                              KnowledgeRepository knowledgeRepository,
                              ModelService modelService,
                              EmbedderClient embedder,
                              VectorStoreService vectorStore,
                              KnowledgeVectorWrites vectorWrites,
                              ModelRuntimeAssembler modelRuntimeAssembler,
                              BatchEmbedProperties batchEmbedProperties) {
        this.kbRepository = kbRepository;
        this.knowledgeRepository = knowledgeRepository;
        this.modelService = modelService;
        this.embedder = embedder;
        this.vectorStore = vectorStore;
        this.vectorWrites = vectorWrites;
        this.modelRuntimeAssembler = modelRuntimeAssembler;
        this.batchEmbedProperties = batchEmbedProperties;
    }

    /**
     * （含生成问题行）→ 批量 embedding → 插入 chunk 行与问题行。KB 缺失 →
     * 404 "knowledge base not found"（知识库链路语义）。
     */
    public void updateChunkVector(String kbId, List<Chunk> chunks) {
        KnowledgeBase kb = findKbRow(kbId);
        if (kb == null) {
            throw BizException.notFound("knowledge base not found");
        }
        if (!needsEmbeddingServiceLayer(kb)) {
            return;
        }
        indexAndStore(kb, resolveEmbeddingModel(kb), chunks);
    }

    /**
     * 索引前校验（K7）：KB 可解析 + 模型非空且存在——**写库前**调用，失败在事务内抛
     * （1007 "model ID cannot be empty" / 404 "Model not found" 文案与既有契约样例一致），
     * 借此把真正的外部索引（删旧行 + batchIndex）安全后移到事务提交之后。
     */
    public void assertIndexable(String kbId) {
        KnowledgeBase kb = findKbRow(kbId);
        if (kb == null) {
            throw BizException.notFound("knowledge base not found");
        }
        if (!needsEmbeddingServiceLayer(kb)) {
            return;
        }
        resolveEmbeddingModel(kb);
    }

    /** 模型解析（含既有错误映射）：空 ID → 1007 internal；模型缺失 → 404。 */
    private Model resolveEmbeddingModel(KnowledgeBase kb) {
        // handler 包 1007 internal 原文；契约样例 kg-image-update/again/mismatch 钉住）
        String modelId = kb.getEmbeddingModelId() == null ? "" : kb.getEmbeddingModelId();
        if (modelId.isEmpty()) {
            throw new BizException(AppError.internal("model ID cannot be empty"));
        }
        try {
            return modelService.getModelByID(modelId);
        } catch (ModelNotFoundException e) {
            throw new BizException(AppError.notFound("Model not found"));
        }
    }

    /**
     * 摄取路径（worker）入口：与 {@link #updateChunkVector(String, List)} **同一实现**
     * （K10b 收敛），只是调用方已持有 KB 行、不必按 id 再查；模型仍按 KB 配置解析。
     */
    public void indexChunks(KnowledgeBase kb, List<Chunk> chunks) {
        if (kb == null || chunks == null || chunks.isEmpty()) {
            return;
        }
        indexAndStore(kb, resolveEmbeddingModel(kb), chunks);
    }

    /**
     * KB 缺失/模型
     * 缺失的错误形态是 500 面（{@link IllegalStateException}，调用方
     * UpdateDocumentChunk 吞成 index_status=failed）；删除旧行后按 enabled 决定是否重建。
     */
    public void syncChunkIndex(Chunk chunk) {
        KnowledgeBase kb = findKbRow(chunk.getKnowledgeBaseId());
        if (kb == null) {
            throw new IllegalStateException("knowledge base not found");
        }
        if (!needsEmbeddingRepoLayer(kb)) {
            return;
        }
        String modelId = kb.getEmbeddingModelId() == null ? "" : kb.getEmbeddingModelId();
        if (modelId.isEmpty()) {
            throw new IllegalStateException("model ID cannot be empty");
        }
        Model embeddingModel;
        try {
            embeddingModel = modelService.getModelByID(modelId);
        } catch (ModelNotFoundException e) {
            throw new IllegalStateException("model not found");
        } catch (BizException e) {
            throw new IllegalStateException(e.appError().message());
        }
        indexAndStore(kb, embeddingModel, List.of(chunk));
    }

    /** 共享主体：ids 全删 → （enabled 且非 parent_text 的）chunk 行 + 问题行重建。 */
    private void indexAndStore(KnowledgeBase kb, Model embeddingModel, List<Chunk> chunks) {
        // updateChunkVector 的 CreateRetrieveEngineForKB → DeleteByChunkIDList →
        // BatchIndex；未绑定保持 pg 直连，契约样例锁定行为不变）
        CompositeRetrieveEngine boundEngine =
                vectorWrites.boundEngine(kb, TenantContext.currentTenantId());
        if (boundEngine != null) {
            indexAndStoreViaEngine(kb, boundEngine, chunks);
            return;
        }
        EmbedderClient.EmbedConfig cfg = EmbedderClient.configFrom(embeddingModel);
        List<VectorStoreService.IndexRow> rows = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        Map<String, Knowledge> knowledgeCache = new HashMap<>();
        for (Chunk chunk : chunks) {
            if (chunk.getKnowledgeBaseId() == null
                    || !chunk.getKnowledgeBaseId().equals(kb.getId())) {
                log.warn("Knowledge base ID mismatch: {} != {}", chunk.getKnowledgeBaseId(), kb.getId());
                continue;
            }
            ids.add(chunk.getId());
            if (!chunk.isIsEnabled() || "parent_text".equals(chunk.getChunkType())) {
                continue;
            }
            Knowledge knowledge = knowledgeCache.get(chunk.getKnowledgeId());
            if (knowledge == null) {
                knowledge = knowledgeRepository.findById(chunk.getTenantId(), chunk.getKnowledgeId());
                if (knowledge == null) {
                    throw BizException.notFound("record not found");
                }
                knowledgeCache.put(chunk.getKnowledgeId(), knowledge);
            }
            rows.add(new VectorStoreService.IndexRow(chunk.getId(), chunk.getId(),
                    chunk.getKnowledgeId(), chunk.getKnowledgeBaseId(),
                    KnowledgeIndexContent.build(knowledge, chunk.embeddingContent()),
                    chunk.isIsEnabled(), ""));
            DocumentChunkMetadata meta = chunkDocumentMetadata(chunk);
            if (meta != null && meta.getGeneratedQuestions() != null) {
                for (GeneratedQuestion question : meta.getGeneratedQuestions()) {
                    if (question.getQuestion() == null
                            || ChunkRepository.trimSpace(question.getQuestion()).isEmpty()) {
                        continue;
                    }
                    rows.add(new VectorStoreService.IndexRow(
                            ChunkSearchUtil.generatedQuestionSourceId(chunk.getId(), question.getId()),
                            chunk.getId(), chunk.getKnowledgeId(), chunk.getKnowledgeBaseId(),
                            KnowledgeIndexContent.build(knowledge, question.getQuestion()), true, ""));
                }
            }
        }
        vectorStore.deleteByChunkId(ids);
        int embedBatch = embedBatchSize(batchEmbedProperties.embedSize());
        for (int from = 0; from < rows.size(); from += embedBatch) {
            int to = Math.min(from + embedBatch, rows.size());
            List<VectorStoreService.IndexRow> batchRows = rows.subList(from, to);
            List<String> texts = new ArrayList<>(batchRows.size());
            for (VectorStoreService.IndexRow row : batchRows) {
                texts.add(row.content());
            }
            List<float[]> vectors;
            try {
                vectors = embedder.embedBatch(cfg, texts);
            } catch (Exception e) {
                throw new BizException(AppError.badRequest(
                        e.getMessage() == null ? e.toString() : e.getMessage()));
            }
            vectorStore.saveIndexRows(batchRows, vectors);
        }
    }

    /**
     * {@code engine.DeleteByChunkIDList(ids, embedder.GetDimensions(), kb.Type)} →
     * {@code engine.BatchIndex(embedder, items)}。items 的形状照 IndexInfo 逐字段
     * （chunk 行 SourceID=chunkID、问题行 SourceID=GeneratedQuestionSourceID、
     * KnowledgeType=kb.Type）；嵌入与分批/退避由 KV 引擎服务承担（40/10 分批 +
     */
    private void indexAndStoreViaEngine(KnowledgeBase kb,
                                        CompositeRetrieveEngine engine,
                                        List<Chunk> chunks) {
        Embedder embedderRuntime =
                modelRuntimeAssembler.getEmbeddingModel(kb.getEmbeddingModelId());
        List<String> ids = new ArrayList<>();
        List<IndexInfo> items = new ArrayList<>();
        Map<String, Knowledge> knowledgeCache = new HashMap<>();
        for (Chunk chunk : chunks) {
            if (chunk.getKnowledgeBaseId() == null
                    || !chunk.getKnowledgeBaseId().equals(kb.getId())) {
                log.warn("Knowledge base ID mismatch: {} != {}", chunk.getKnowledgeBaseId(), kb.getId());
                continue;
            }
            ids.add(chunk.getId());
            if (!chunk.isIsEnabled() || "parent_text".equals(chunk.getChunkType())) {
                continue;
            }
            Knowledge knowledge = knowledgeCache.get(chunk.getKnowledgeId());
            if (knowledge == null) {
                knowledge = knowledgeRepository.findById(chunk.getTenantId(), chunk.getKnowledgeId());
                if (knowledge == null) {
                    throw BizException.notFound("record not found");
                }
                knowledgeCache.put(chunk.getKnowledgeId(), knowledge);
            }
            items.add(indexInfo(kb, knowledge, chunk.getId(), chunk.getId(),
                    KnowledgeIndexContent.build(knowledge, chunk.embeddingContent()),
                    chunk.isIsEnabled()));
            DocumentChunkMetadata meta = chunkDocumentMetadata(chunk);
            if (meta != null && meta.getGeneratedQuestions() != null) {
                for (GeneratedQuestion question : meta.getGeneratedQuestions()) {
                    if (question.getQuestion() == null
                            || ChunkRepository.trimSpace(question.getQuestion()).isEmpty()) {
                        continue;
                    }
                    items.add(indexInfo(kb, knowledge,
                            ChunkSearchUtil.generatedQuestionSourceId(chunk.getId(), question.getId()),
                            chunk.getId(),
                            KnowledgeIndexContent.build(knowledge, question.getQuestion()),
                            true));
                }
            }
        }
        try {
            engine.deleteByChunkIdList(ids, embedderRuntime.getDimensions(), kb.getType());
            engine.batchIndex(embedderRuntime, items);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
        }
    }

    /** 照 types.IndexInfo 的字段集（SourceType=ChunkSourceType=0；TagID 零值 ""）。 */
    private static IndexInfo indexInfo(
            KnowledgeBase kb, Knowledge knowledge, String sourceId, String chunkId,
            String content, boolean enabled) {
        IndexInfo item =
                new IndexInfo();
        item.sourceId = sourceId;
        item.sourceType = EngineTypes.SOURCE_TYPE_FILE;
        item.chunkId = chunkId;
        item.knowledgeId = knowledge.getId();
        item.knowledgeBaseId = kb.getId();
        item.knowledgeType = kb.getType();
        item.tagId = "";
        item.content = content;
        item.isEnabled = enabled;
        return item;
    }

    /**
     * <b>服务层</b>判定
     * 翻成 Default（vector+keyword 开））。{@link #updateChunkVector} 走这里。
     * <p>证据：契约样例 kg-image-update/again/mismatch 的 KB 是显式全 false 策略
     * 向量分支，即全 false 策略经服务层读法被翻成 Default。</p>
     */
    private static boolean needsEmbeddingServiceLayer(KnowledgeBase kb) {
        KnowledgeBaseIndexingStrategy strategy = kb.getIndexingStrategy();
        if (strategy == null || strategy.isZero()) {
            strategy = KnowledgeBaseIndexingStrategy.defaultStrategy();
        }
        return strategy.isVectorEnabled() || strategy.isKeywordEnabled();
    }

    /**
     * <b>repo 层</b>判定——索引策略为 null 时返回 Default（NULL 列 → vector+keyword 开）；
     * 显式全 false / 空 JSON 保持全 false（IsZero 不翻）。{@link #syncChunkIndex} 走这里
     * （2026-09-22 走查批实证：套钩子会让全 false 策略的 KB 误走进真实出站，13 测试红）。
     */
    private static boolean needsEmbeddingRepoLayer(KnowledgeBase kb) {
        KnowledgeBaseIndexingStrategy strategy = kb.getIndexingStrategy();
        if (strategy == null) {
            strategy = KnowledgeBaseIndexingStrategy.defaultStrategy();
        }
        return strategy.isVectorEnabled() || strategy.isKeywordEnabled();
    }

    private KnowledgeBase findKbRow(String kbId) {
        return kbRepository.findByIdOnly(kbId);
    }

    private static DocumentChunkMetadata chunkDocumentMetadata(Chunk chunk) {
        JsonNode meta = chunk.getMetadata();
        if (meta == null || meta.isNull()) {
            return null;
        }
        try {
            return MAPPER.treeToValue(meta, DocumentChunkMetadata.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
 * 解析批量向量化批大小（空/null → 5；非法值 → {@code IllegalStateException}）。
 *
 * <p> 本类由 `service/` 归位 `index/` 后，{@code service.FaqIndexWriter} 仍需要同一份
 * 解析语义 ⇒ 提为 public（纯静态解析，无状态；两处调用共用一份规则，不复制）。</p>
 */
    public static int embedBatchSize(String raw) {
        String env = raw;
        if (env == null || env.isEmpty()) {
            return 5;
        }
        try {
            return Integer.parseInt(env.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "strconv.Atoi: parsing \"" + env + "\": invalid syntax");
        }
    }
}
