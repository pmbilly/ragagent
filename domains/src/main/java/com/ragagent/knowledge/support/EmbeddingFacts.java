package com.ragagent.knowledge.support;

import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelFacts;

/** 模型行 → 嵌入端口载荷（端口只认模型事实，不认实体/配置对象）。 */
public final class EmbeddingFacts {

    private EmbeddingFacts() {
    }

    public static ModelFacts of(ModelRowView model) {
        var p = model.parameters();
        return new ModelFacts(
                model.id(),
                model.name(),
                p == null || p.getBaseUrl() == null ? "" : p.getBaseUrl(),
                p == null || p.getApiKey() == null ? "" : p.getApiKey());
    }
}
