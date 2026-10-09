package com.ragagent.knowledge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ragagent.common.knowledge.EmbeddingModelPort;
import com.ragagent.knowledge.client.EmbedderClient;
import com.ragagent.model.domain.Model;
import com.ragagent.model.service.ModelService;

/**
 * {@link EmbeddingModelPort} 的 knowledge 侧实现（B98/C2）。
 *
 * <p>逻辑搬自 {@code DefaultWikiModelResolver#getEmbeddingModel}：取模型 → embedding 类型闸门 →
 * {@code EmbedderClient.configFrom} → 返回 {@code embedBatch} 的函数式客户端。
 * 类型闸门语义不变（非 embedding 模型抛 {@link IllegalStateException}）。</p>
 */
@Component
public class EmbeddingModelAdapter implements EmbeddingModelPort {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelAdapter.class);

    /** model.parameters.type 里表示 embedding 模型的取值 */
    static final String MODEL_TYPE_EMBEDDING = "Embedding";

    private final ModelService modelService;
    private final EmbedderClient embedderClient;

    public EmbeddingModelAdapter(ModelService modelService, EmbedderClient embedderClient) {
        this.modelService = modelService;
        this.embedderClient = embedderClient;
    }

    @Override
    public Embedder embedderFor(String modelId) {
        Model model = modelService.getModelByID(modelId);
        String type = model.getType();
        if (!MODEL_TYPE_EMBEDDING.equals(type)) {
            // 类型闸门：非 embedding 模型直接报错，
            // 让调用方回落到"喂全部目录"的降级路径，而不是发一次注定失败的请求。
            throw new IllegalStateException(
                    "model " + modelId + " is not an embedding model (type=" + type + ")");
        }
        EmbedderClient.EmbedConfig config = EmbedderClient.configFrom(model);
        log.debug("wiki ingest: resolved embedding model {} (base={})", modelId, config.baseUrl());
        return texts -> embedderClient.embedBatch(config, texts);
    }
}
