package com.ragagent.knowledge.api;

import com.ragagent.contract.knowledge.EmbeddingModelPort;
import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link EmbeddingModelPort} 的 knowledge 侧实现。
 *
 * <p>取模型 → embedding 类型闸门 → 交给 {@link ModelRuntime} 按模型 id 装配 provider-aware
 * 客户端（与问答/索引同一条路径）。类型闸门语义不变（非 embedding 模型抛
 * {@link IllegalStateException}，调用方据此降级为"喂全部目录"）。</p>
 */
@Component
public class EmbeddingModelAdapter implements EmbeddingModelPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelAdapter.class);

    /** model.parameters.type 里表示 embedding 模型的取值 */
    static final String MODEL_TYPE_EMBEDDING = "Embedding";

    private final ModelCatalogPort modelCatalog;
    private final ModelRuntime modelRuntime;

    public EmbeddingModelAdapter(ModelCatalogPort modelCatalog, ModelRuntime modelRuntime) {
        this.modelCatalog = modelCatalog;
        this.modelRuntime = modelRuntime;
    }

    @Override
    public Embedder embedderFor(String modelId) {
        ModelRowView model = modelCatalog.findModel(modelId);
        String type = model.type();
        if (!MODEL_TYPE_EMBEDDING.equals(type)) {
            // 类型闸门：非 embedding 模型直接报错，
            // 让调用方回落到"喂全部目录"的降级路径，而不是发一次注定失败的请求。
            throw new IllegalStateException(
                    "model " + modelId + " is not an embedding model (type=" + type + ")");
        }
        log.debug("wiki ingest: resolved embedding model {}", modelId);
        return modelRuntime.getEmbeddingModel(modelId)::batchEmbed;
    }
}
