package com.ragagent.knowledge.support;

import java.util.ArrayList;
import java.util.List;

import com.ragagent.common.knowledge.KnowledgeBaseRowView;
import com.ragagent.knowledge.domain.KnowledgeBase;

/**
 * 知识库实体 → 行视图的**域内单一映射**（供端口适配器、KB 控制器与 L1 dto 调用点共用）。
 *
 * <p>实体已归位 `knowledge/domain`；L1 的 {@code KnowledgeCatalogPort} 知识库面、
 * {@code KnowledgeBaseResponse} 线格式与 {@code KnowledgeBaseViews} 投影都改吃视图 ⇒
 * 域内所有"喂实体"的点统一经本类（避免映射逻辑多处漂移，同 {@code ChunkRowViews}/{@code TagViews} 先例）。</p>
 */
public final class KnowledgeBaseRowViews {

    private KnowledgeBaseRowViews() {
    }

    /** 实体 → 行视图（逐字段搬运；null 透传）。 */
    public static KnowledgeBaseRowView rowView(KnowledgeBase e) {
        if (e == null) {
            return null;
        }
        KnowledgeBaseRowView v = new KnowledgeBaseRowView();
        v.setId(e.getId());
        v.setName(e.getName());
        v.setType(e.getType());
        v.setIsTemporary(e.isIsTemporary());
        v.setDescription(e.getDescription());
        v.setTenantId(e.getTenantId());
        v.setCreatorId(e.getCreatorId());
        v.setChunkingConfig(e.getChunkingConfig());
        v.setImageProcessingConfig(e.getImageProcessingConfig());
        v.setEmbeddingModelId(e.getEmbeddingModelId());
        v.setSummaryModelId(e.getSummaryModelId());
        v.setVlmConfig(e.getVlmConfig());
        v.setAsrConfig(e.getAsrConfig());
        v.setStorageProviderConfig(e.getStorageProviderConfig());
        v.setStorageBackendId(e.getStorageBackendId());
        v.setStorageConfig(e.getStorageConfig());
        v.setVectorStoreId(e.getVectorStoreId());
        v.setExtractConfig(e.getExtractConfig());
        v.setFaqConfig(e.getFaqConfig());
        v.setQuestionGenerationConfig(e.getQuestionGenerationConfig());
        v.setAutoTagConfig(e.getAutoTagConfig());
        v.setWikiConfig(e.getWikiConfig());
        v.setIndexingStrategy(e.getIndexingStrategy());
        v.setCreatedAt(e.getCreatedAt());
        v.setUpdatedAt(e.getUpdatedAt());
        v.setDeletedAt(e.getDeletedAt());
        v.setIsPinned(e.isIsPinned());
        v.setPinnedAt(e.getPinnedAt());
        v.setKnowledgeCount(e.getKnowledgeCount());
        v.setChunkCount(e.getChunkCount());
        v.setIsProcessing(e.isIsProcessing());
        v.setProcessingCount(e.getProcessingCount());
        v.setShareCount(e.getShareCount());
        v.setCreatorName(e.getCreatorName());
        return v;
    }

    /** 批量（null 安全）。 */
    public static List<KnowledgeBaseRowView> rowViews(List<KnowledgeBase> rows) {
        if (rows == null) {
            return List.of();
        }
        List<KnowledgeBaseRowView> out = new ArrayList<>(rows.size());
        for (KnowledgeBase e : rows) {
            out.add(rowView(e));
        }
        return out;
    }

    /** 行视图 → 实体（写面用；经实体 setter ⇒ 保留归一语义；null 透传）。 */
    public static KnowledgeBase toEntity(KnowledgeBaseRowView v) {
        if (v == null) {
            return null;
        }
        KnowledgeBase e = new KnowledgeBase();
        e.setId(v.getId());
        e.setName(v.getName());
        e.setType(v.getType());
        e.setIsTemporary(v.isIsTemporary());
        e.setDescription(v.getDescription());
        e.setTenantId(v.getTenantId());
        e.setCreatorId(v.getCreatorId());
        e.setChunkingConfig(v.getChunkingConfig());
        e.setImageProcessingConfig(v.getImageProcessingConfig());
        e.setEmbeddingModelId(v.getEmbeddingModelId());
        e.setSummaryModelId(v.getSummaryModelId());
        e.setVlmConfig(v.getVlmConfig());
        e.setAsrConfig(v.getAsrConfig());
        e.setStorageProviderConfig(v.getStorageProviderConfig());
        e.setStorageBackendId(v.getStorageBackendId());
        e.setStorageConfig(v.getStorageConfig());
        e.setVectorStoreId(v.getVectorStoreId());
        e.setExtractConfig(v.getExtractConfig());
        e.setFaqConfig(v.getFaqConfig());
        e.setQuestionGenerationConfig(v.getQuestionGenerationConfig());
        e.setAutoTagConfig(v.getAutoTagConfig());
        e.setWikiConfig(v.getWikiConfig());
        e.setIndexingStrategy(v.getIndexingStrategy());
        e.setCreatedAt(v.getCreatedAt());
        e.setUpdatedAt(v.getUpdatedAt());
        e.setDeletedAt(v.getDeletedAt());
        e.setIsPinned(v.isIsPinned());
        e.setPinnedAt(v.getPinnedAt());
        e.setKnowledgeCount(v.getKnowledgeCount());
        e.setChunkCount(v.getChunkCount());
        e.setIsProcessing(v.isIsProcessing());
        e.setProcessingCount(v.getProcessingCount());
        e.setShareCount(v.getShareCount());
        e.setCreatorName(v.getCreatorName());
        return e;
    }

    /** 把落库后的实体字段回填到传入视图（保持"同对象被填充"的旧语义；null 安全）。 */
    public static void copyInto(KnowledgeBaseRowView v, KnowledgeBase e) {
        if (v == null || e == null) {
            return;
        }
        KnowledgeBaseRowView fresh = rowView(e);
        v.setId(fresh.getId());
        v.setName(fresh.getName());
        v.setType(fresh.getType());
        v.setIsTemporary(fresh.isIsTemporary());
        v.setDescription(fresh.getDescription());
        v.setTenantId(fresh.getTenantId());
        v.setCreatorId(fresh.getCreatorId());
        v.setChunkingConfig(fresh.getChunkingConfig());
        v.setImageProcessingConfig(fresh.getImageProcessingConfig());
        v.setEmbeddingModelId(fresh.getEmbeddingModelId());
        v.setSummaryModelId(fresh.getSummaryModelId());
        v.setVlmConfig(fresh.getVlmConfig());
        v.setAsrConfig(fresh.getAsrConfig());
        v.setStorageProviderConfig(fresh.getStorageProviderConfig());
        v.setStorageBackendId(fresh.getStorageBackendId());
        v.setStorageConfig(fresh.getStorageConfig());
        v.setVectorStoreId(fresh.getVectorStoreId());
        v.setExtractConfig(fresh.getExtractConfig());
        v.setFaqConfig(fresh.getFaqConfig());
        v.setQuestionGenerationConfig(fresh.getQuestionGenerationConfig());
        v.setAutoTagConfig(fresh.getAutoTagConfig());
        v.setWikiConfig(fresh.getWikiConfig());
        v.setIndexingStrategy(fresh.getIndexingStrategy());
        v.setCreatedAt(fresh.getCreatedAt());
        v.setUpdatedAt(fresh.getUpdatedAt());
        v.setDeletedAt(fresh.getDeletedAt());
        v.setIsPinned(fresh.isIsPinned());
        v.setPinnedAt(fresh.getPinnedAt());
        v.setKnowledgeCount(fresh.getKnowledgeCount());
        v.setChunkCount(fresh.getChunkCount());
        v.setIsProcessing(fresh.isIsProcessing());
        v.setProcessingCount(fresh.getProcessingCount());
        v.setShareCount(fresh.getShareCount());
        v.setCreatorName(fresh.getCreatorName());
    }
}
