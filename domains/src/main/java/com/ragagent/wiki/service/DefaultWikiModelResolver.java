package com.ragagent.wiki.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ragagent.common.model.ModelCatalogPort;
import com.ragagent.common.model.ModelCatalogPort.ModelRowView;
import com.ragagent.common.model.ModelParameters;
import com.ragagent.contract.knowledge.EmbeddingModelPort;
import com.ragagent.llm.LlmChatClient;
import com.ragagent.llm.domain.ChatConfig;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.ollama.OllamaService;
import com.ragagent.modelruntime.application.ChatConfigs;
import com.ragagent.modelruntime.application.LlmChatClients;

/**
 * {@link WikiModelResolver} 的默认实现：用 Java 侧已有的部件拼出 chat / embedding 模型。
 *
 * <ul>
 *   <li>chat：{@code ModelCatalogPort.findModel}（含"downloading → 500"状态闸门，来自模型域）
 *       → {@link ChatConfigs}（模型行 → ChatConfig，L2）→ {@link LlmChatClients#create}
 *       （含并发闸门装饰器）。</li>
 *   <li>embedding：先校验模型类型确为 embedding，再复用
 *       {@link com.ragagent.contract.knowledge.EmbeddingModelPort}（L1 端口；实现侧仍是最小 OpenAI 兼容客户端）。</li>
 * </ul>
 *
 * <p>跨域口径与 {@code memory.service.DefaultMemoryModelResolver} 一致：只经 L1 端口
 * {@link ModelCatalogPort}（模型行视图）与 L2 门面 {@link ChatConfigs}，不出现模型域实体。</p>
 */
@Component
public class DefaultWikiModelResolver implements WikiModelResolver {

    private final ModelCatalogPort modelCatalog;
    private final ObjectProvider<OllamaService> ollamaService;
    private final ConcurrencyGovernor concurrencyGovernor;
    private final EmbeddingModelPort embeddingPort;

    public DefaultWikiModelResolver(ModelCatalogPort modelCatalog,
                                    ObjectProvider<OllamaService> ollamaService,
                                    ConcurrencyGovernor concurrencyGovernor,
                                    EmbeddingModelPort embeddingPort) {
        this.modelCatalog = modelCatalog;
        this.ollamaService = ollamaService;
        this.concurrencyGovernor = concurrencyGovernor;
        this.embeddingPort = embeddingPort;
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
    public WikiEmbeddingModel getEmbeddingModel(String modelId) {
        // embedding 类型闸门（非 embedding 模型抛 IllegalStateException）与
        // EmbedderClient 配置解析在 knowledge 侧端口内完成；wiki 只拿函数式客户端。
        EmbeddingModelPort.Embedder embedder = embeddingPort.embedderFor(modelId);
        return embedder::batchEmbed;
    }
}
