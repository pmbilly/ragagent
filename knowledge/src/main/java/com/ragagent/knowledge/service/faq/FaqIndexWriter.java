package com.ragagent.knowledge.service.faq;

import com.ragagent.contract.knowledge.FaqChunkMetadata;
import com.ragagent.contract.model.Embedder;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelRuntime;
import com.ragagent.contract.retrieval.IndexDocument;
import com.ragagent.contract.retrieval.IndexEngine;
import com.ragagent.contract.retrieval.IndexRow;
import com.ragagent.contract.retrieval.SourceTypes;
import com.ragagent.contract.retrieval.VectorIndexStore;
import com.ragagent.contract.tenant.TenantStorageQuotaPort;
import com.ragagent.knowledge.client.EmbedderClient;
import com.ragagent.knowledge.config.BatchEmbedProperties;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.index.ChunkVectorIndexer;
import com.ragagent.knowledge.index.KnowledgeVectorWrites;
import com.ragagent.knowledge.repository.ChunkRepository;
import com.ragagent.knowledge.repository.KnowledgeRepository;
import com.ragagent.platform.access.TenantContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * FAQ 条目的落库与向量索引写路径：FAQ 容器 knowledge 的查找/惰性创建、嵌入模型
 * 门槛、存储配额检查、索引写入与删除（绑定检索引擎的 KB 走引擎口，其余走内置
 * VectorIndexStore）、删除时的配额回退。
 *
 * <p>错误形态不变量：模型门槛与配额超限抛 {@link IllegalStateException}
 * （plain 500 形态，不进 BizException 信封）；配额累加在写入成功后进行，
 * 回退时 storage_used 与 knowledge.storage_size 均下钳 0。</p>
 */
@Component
public class FaqIndexWriter {

    private final KnowledgeRepository knowledgeRepository;
    private final ChunkRepository chunkRepository;
    private final TenantStorageQuotaPort tenantStorage;
    private final KnowledgeVectorWrites vectorWrites;
    private final VectorIndexStore vectorStore;
    private final EmbedderClient embedder;
    private final ModelRuntime modelRuntimeAssembler;
    private final ModelCatalogPort modelCatalog;
    /** 批量向量化批大小（属性绑定）。 */
    private final BatchEmbedProperties batchEmbedProperties;

    public FaqIndexWriter(KnowledgeRepository knowledgeRepository,
                          ChunkRepository chunkRepository,
                          TenantStorageQuotaPort tenantStorage,
                          KnowledgeVectorWrites vectorWrites,
                          VectorIndexStore vectorStore,
                          ModelCatalogPort modelCatalog,
                          EmbedderClient embedder,
                          ModelRuntime modelRuntimeAssembler,
                          BatchEmbedProperties batchEmbedProperties) {
        this.knowledgeRepository = knowledgeRepository;
        this.chunkRepository = chunkRepository;
        this.tenantStorage = tenantStorage;
        this.vectorWrites = vectorWrites;
        this.vectorStore = vectorStore;
        this.embedder = embedder;
        this.modelRuntimeAssembler = modelRuntimeAssembler;
        this.modelCatalog = modelCatalog;
        this.batchEmbedProperties = batchEmbedProperties;
    }

    private static long tenantId() {
        Long tid = TenantContext.currentTenantId();
        return tid == null ? 0 : tid;
    }

    /**
     * 查找 KB 下的 FAQ 容器 knowledge（每 KB 至多一条 type='faq'），无则 null。
     */
    public Knowledge findFAQKnowledge(long tenantId, String kbId) {
        List<Knowledge> knowledges = knowledgeRepository.listByTenantAndKb(tenantId, kbId);
        for (Knowledge knowledge : knowledges) {
            if ("faq".equals(knowledge.getType())) {
                return knowledge;
            }
        }
        return null;
    }

    /**
     * 取 FAQ 容器 knowledge，不存在则创建（解析状态直接 completed、启用态 enabled，
     * 继承 KB 的嵌入模型）。容器行由本方法独占管理，调用方不得自行插入。
     */
    public Knowledge ensureFAQKnowledge(long tenantId, KnowledgeBase kb) {
        Knowledge existing = findFAQKnowledge(tenantId, kb.getId());
        if (existing != null) {
            return existing;
        }
        Knowledge knowledge = new Knowledge();
        knowledge.setId(UUID.randomUUID().toString());
        knowledge.setTenantId(tenantId);
        knowledge.setKnowledgeBaseId(kb.getId());
        knowledge.setType("faq");
        knowledge.setChannel("web");
        String name = FaqChunkMetadata.trimSpace(kb.getName());
        knowledge.setTitle(name.isEmpty() ? "FAQ" : name);
        knowledge.setDescription("FAQ 条目容器");
        knowledge.setSource("faq");
        knowledge.setParseStatus("completed");
        knowledge.setEnableStatus("enabled");
        knowledge.setEmbeddingModelId(kb.getEmbeddingModelId());
        OffsetDateTime now = OffsetDateTime.now();
        knowledge.setCreatedAt(now);
        knowledge.setUpdatedAt(now);
        knowledgeRepository.insert(knowledge);
        return knowledge;
    }

    /**
     * 嵌入模型门槛：KB 未配置或模型行缺失 → IllegalStateException（500 plain）。
     */
    public ModelRowView requireEmbeddingModel(KnowledgeBase kb) {
        String modelId = kb.getEmbeddingModelId() == null ? "" : kb.getEmbeddingModelId();
        if (modelId.isEmpty()) {
            throw new IllegalStateException("failed to get embedding model: model ID cannot be empty");
        }
        ModelRowView model = modelCatalog.findModelRow(tenantId(), modelId);
        if (model == null) {
            throw new IllegalStateException("failed to get embedding model: record not found");
        }
        return model;
    }

    /**
     * 批量索引 FAQ chunks：组装索引行 → （adjustStorage 时）配额预检 → 删旧行 →
     * 分批嵌入写库 → 配额累加 → 刷新 knowledge 的 processed_at。
     * 绑定检索引擎的 KB 走引擎口（嵌入与分批由引擎服务承担），否则走内置
     * VectorIndexStore 分批路径。
     */
    public void indexFAQChunks(KnowledgeBase kb, Knowledge knowledge, List<Chunk> chunks,
                               ModelRowView embeddingModel, boolean adjustStorage) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        long tid = tenantId();
        List<IndexRow> rows = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        for (Chunk chunk : chunks) {
            rows.addAll(FaqIndexRows.build(kb, chunk));
            chunkIds.add(chunk.getId());
        }
        int dimensions = embeddingDimensions(embeddingModel);
        long size = 0;
        if (adjustStorage) {
            size = vectorStore.estimateStorageSize(rows, dimensions);
            TenantStorageQuotaPort.TenantQuotaView tenantInfo = tenantStorage.quotaView(tid);
            long quota = tenantInfo == null ? 0 : tenantInfo.quota();
            long used = tenantInfo == null ? 0 : tenantInfo.used();
            if (quota > 0 && used + size > quota) {
                throw new IllegalStateException("Storage quota exceeded");
            }
        }
        IndexEngine boundEngine =
                vectorWrites.boundEngine(kb, tid);
        if (boundEngine != null) {
            try {
                Embedder emb =
                        modelRuntimeAssembler.getEmbeddingModel(embeddingModel.id());
                boundEngine.deleteByChunkIdList(chunkIds, emb.getDimensions(), kb.getType());
                boundEngine.batchIndex(emb, faqIndexInfos(kb, rows));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            }
            if (adjustStorage && size > 0) {
                tenantStorage.adjustUsed(tid, size);
                knowledge.setStorageSize(knowledge.getStorageSize() + size);
            }
            OffsetDateTime nowIndexed = OffsetDateTime.now();
            knowledge.setUpdatedAt(nowIndexed);
            knowledge.setProcessedAt(nowIndexed);
            knowledgeRepository.updateById(knowledge);
            return;
        }
        vectorStore.deleteByChunkId(chunkIds);
        EmbedderClient.EmbedConfig cfg = EmbedderClient.configFrom(embeddingModel);
        int batchSize = ChunkVectorIndexer.embedBatchSize(batchEmbedProperties.embedSize());
        for (int from = 0; from < rows.size(); from += batchSize) {
            int to = Math.min(from + batchSize, rows.size());
            List<IndexRow> batchRows = rows.subList(from, to);
            List<String> texts = new ArrayList<>(batchRows.size());
            for (IndexRow row : batchRows) {
                texts.add(row.content());
            }
            List<float[]> vectors;
            try {
                vectors = embedder.embedBatch(cfg, texts);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
            }
            vectorStore.saveIndexRows(batchRows, vectors);
        }
        if (adjustStorage && size > 0) {
            tenantStorage.adjustUsed(tid, size);
            knowledge.setStorageSize(knowledge.getStorageSize() + size);
        }
        OffsetDateTime now = OffsetDateTime.now();
        knowledge.setUpdatedAt(now);
        knowledge.setProcessedAt(now);
        knowledgeRepository.updateById(knowledge);
    }

    /**
     * 删除 chunks 的向量索引并回退存储配额（storage_used 钳 0 语义在
     * 租户域 TenantStorageQuotaAdapter 内；knowledge.storage_size 本方法下钳 0）。
     */
    public void deleteFAQChunkVectors(KnowledgeBase kb, Knowledge knowledge, List<Chunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        ModelRowView embeddingModel = requireEmbeddingModel(kb);
        long tid = tenantId();
        List<IndexRow> rows = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();
        for (Chunk chunk : chunks) {
            rows.addAll(FaqIndexRows.build(kb, chunk));
            chunkIds.add(chunk.getId());
        }
        long size = vectorStore.estimateStorageSize(rows, embeddingDimensions(embeddingModel));
        IndexEngine boundEngine =
                vectorWrites.boundEngine(kb, tid);
        if (boundEngine != null) {
            try {
                Embedder emb =
                        modelRuntimeAssembler.getEmbeddingModel(embeddingModel.id());
                boundEngine.deleteByChunkIdList(chunkIds, emb.getDimensions(), kb.getType());
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(
                        e.getMessage() == null ? String.valueOf(e) : e.getMessage(), e);
            }
        } else {
            vectorStore.deleteByChunkId(chunkIds);
        }
        if (size > 0) {
            tenantStorage.adjustUsed(tid, -size);
            knowledge.setStorageSize(Math.max(0, knowledge.getStorageSize() - size));
        }
        knowledge.setUpdatedAt(OffsetDateTime.now());
        knowledgeRepository.updateById(knowledge);
    }

    /**
     * 批量落库 chunk 行。时间戳由调用方显式赋值（insert 不自动生成时间戳）；
     * null 字段省列（seq_id 省列时由 DB 序列给默认值）。
     */
    public void createChunks(List<Chunk> chunks) {
        for (Chunk c : chunks) {
            chunkRepository.insert(c);
        }
    }

    /** 索引行 → 检索引擎的 IndexInfo（SourceType 固定 FILE）。 */
    private static List<IndexDocument> faqIndexInfos(
            KnowledgeBase kb, List<IndexRow> rows) {
        List<IndexDocument> items =
                new ArrayList<>(rows.size());
        for (IndexRow row : rows) {
            items.add(new IndexDocument(row.sourceId(), SourceTypes.FILE,
                    row.chunkId(), row.knowledgeId(), row.knowledgeBaseId(), kb.getType(),
                    row.tagId(), row.content(), row.isEnabled()));
        }
        return items;
    }

    /** 嵌入维度取模型参数 embedding_parameters.dimension，缺省 0。 */
    private static int embeddingDimensions(ModelRowView model) {
        if (model == null || model.parameters() == null
                || model.parameters().getEmbeddingParameters() == null) {
            return 0;
        }
        return model.parameters().getEmbeddingParameters().getDimension();
    }

}
