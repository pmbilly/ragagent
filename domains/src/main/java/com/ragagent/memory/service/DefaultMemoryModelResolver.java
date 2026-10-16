package com.ragagent.memory.service;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.contract.model.ModelParameters;
import com.ragagent.contract.model.ModelFacts;
import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.contract.model.chat.ChatConfig;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.ollama.OllamaService;
import com.ragagent.modelruntime.application.ChatConfigs;
import com.ragagent.modelruntime.application.LlmChatClients;
import com.ragagent.platform.model.EmbeddingGateway;

/**
 * {@link MemoryModelResolver} 的默认实现：按模型 id 取运行时的聊天 / 嵌入模型实例
 * （与 {@code wiki.service.DefaultWikiModelResolver} 同款）。
 *
 * <p><b>目录读取走 L1 端口 {@link ModelCatalogPort}</b>（模型行视图 = id/name/type/status/参数），
 * 不再直接依赖模型域服务与实体。</p>
 *
 * <p><b>chat 配置走 L2 门面 {@link ChatConfigs}</b>（映射不依赖模型域）；
 * <b>embedding 走 L1 能力端口 {@link EmbeddingGateway}</b>（按模型事实嵌入），单条嵌入包一层。</p>
 */
@Component
public class DefaultMemoryModelResolver implements MemoryModelResolver {

    /** embedding 模型的 type 值。 */
    static final String MODEL_TYPE_EMBEDDING = "Embedding";

    private final ModelCatalogPort modelCatalog;
    private final ObjectProvider<OllamaService> ollamaService;
    private final ConcurrencyGovernor concurrencyGovernor;
    private final EmbeddingGateway embeddingGateway;

    public DefaultMemoryModelResolver(ModelCatalogPort modelCatalog,
                                      ObjectProvider<OllamaService> ollamaService,
                                      ConcurrencyGovernor concurrencyGovernor,
                                      EmbeddingGateway embeddingGateway) {
        this.modelCatalog = modelCatalog;
        this.ollamaService = ollamaService;
        this.concurrencyGovernor = concurrencyGovernor;
        this.embeddingGateway = embeddingGateway;
    }

    @Override
    public LlmChatClient getChatModel(String modelId) {
        ModelRowView model = modelCatalog.findModel(modelId);
        ModelParameters p = model.parameters();
        ChatConfig config = ChatConfigs.chatConfig(model,
                p == null ? null : p.getAppId(),
                p == null ? null : p.getAppSecret());
        return LlmChatClients.create(config, ollamaService.getIfAvailable(), concurrencyGovernor);
    }

    @Override
    public float[] embed(String modelId, String text) throws Exception {
        ModelRowView model = modelCatalog.findModel(modelId);
        String type = model.type();
        if (!MODEL_TYPE_EMBEDDING.equals(type)) {
            // 类型闸门：非 embedding 模型直接报错，
            // 让调用方回落到字面匹配，而不是发一次注定失败的请求。
            throw new IllegalStateException(
                    "model " + modelId + " is not an embedding model (type=" + type + ")");
        }
        ModelParameters p = model.parameters();
        List<float[]> vectors = embeddingGateway.embed(new ModelFacts(
                model.id(), model.name(),
                p == null || p.getBaseUrl() == null ? "" : p.getBaseUrl(),
                p == null || p.getApiKey() == null ? "" : p.getApiKey()), List.of(text));
        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalStateException("embedding response carried no vector");
        }
        return vectors.get(0);
    }

    @Override
    public List<ModelRowView> listModels() {
        return modelCatalog.listModels();
    }
}
