package com.ragagent.common.knowledge.support;

import com.ragagent.contract.knowledge.KnowledgeBaseLookup;
import com.ragagent.contract.knowledge.KnowledgeBaseView;
import com.ragagent.contract.knowledge.KnowledgeBaseRowView;

/**
 * 知识库实体 → L1 视图的公用投影（自 knowledge 域适配器的静态助手原样搬入）：
 * 装配层与管线面都要把 KB 交给消费方，而实体不许越层 ⇒ 复用同一份投影，
 * 避免映射逻辑两处漂移（消费方见 {@link KnowledgeBaseLookup}）。
 */
public final class KnowledgeBaseViews {

    private KnowledgeBaseViews() {
    }

    /** 实体 → 视图；null 透传。 */
    public static KnowledgeBaseView view(KnowledgeBaseRowView kb) {
        if (kb == null) {
            return null;
        }
        KnowledgeBaseView v = new KnowledgeBaseView();
        v.setId(kb.getId());
        v.setTenantId(kb.getTenantId() == null ? 0L : kb.getTenantId());
        v.setName(kb.getName());
        v.setType(kb.getType());
        v.setDescription(kb.getDescription());
        v.setCreatorId(kb.getCreatorId());
        v.setSummaryModelId(kb.getSummaryModelId());
        v.setEmbeddingModelId(kb.getEmbeddingModelId());
        v.setWikiEnabled(kb.getIndexingStrategy() != null && kb.getIndexingStrategy().isWikiEnabled());
        v.setWikiConfig(kb.getWikiConfig());
        v.setVectorEnabled(kb.getIndexingStrategy() != null && kb.getIndexingStrategy().isVectorEnabled());
        v.setKeywordEnabled(kb.getIndexingStrategy() != null && kb.getIndexingStrategy().isKeywordEnabled());
        v.setExtractConfig(kb.getExtractConfig());
        v.setCreatedAt(kb.getCreatedAt());
        v.setUpdatedAt(kb.getUpdatedAt());
        return v;
    }
}
