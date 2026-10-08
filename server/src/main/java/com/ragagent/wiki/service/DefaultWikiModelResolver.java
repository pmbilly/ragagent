package com.ragagent.wiki.service;


import com.ragagent.common.knowledge.EmbeddingModelPort;
import com.ragagent.llm.LlmChatClient;
import com.ragagent.llm.chat.LlmChatClients;
import com.ragagent.llm.domain.ChatConfig;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.ollama.OllamaService;
import com.ragagent.model.domain.Model;
import com.ragagent.model.domain.ModelParameters;
import com.ragagent.model.service.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.ragagent.model.service.ModelRuntimeConfigs;

/**
 * {@link WikiModelResolver} 的默认实现：用 Java 侧已有的部件拼出 chat / embedding 模型。
 *
 * <ul>
 *   <li>chat：{@code ModelService.getModelByID}（含"downloading → 500"状态闸门）
 *       → {@link ChatConfig#fromModel} → {@link LlmChatClients#create}
 *       （含并发闸门装饰器）。</li>
 *   <li>embedding：先校验模型类型确为 embedding，再复用
 *       {@link EmbedderClient}（最小 OpenAI 兼容客户端）。</li>
 * </ul>
 */
@Component
public class DefaultWikiModelResolver implements WikiModelResolver {

    private static final Logger log = LoggerFactory.getLogger(DefaultWikiModelResolver.class);

    private final ModelService modelService;
    private final ObjectProvider<OllamaService> ollamaService;
    private final ConcurrencyGovernor concurrencyGovernor;
    private final EmbeddingModelPort embeddingPort;

    public DefaultWikiModelResolver(ModelService modelService,
                                    ObjectProvider<OllamaService> ollamaService,
                                    ConcurrencyGovernor concurrencyGovernor,
                                    EmbeddingModelPort embeddingPort) {
        this.modelService = modelService;
        this.ollamaService = ollamaService;
        this.concurrencyGovernor = concurrencyGovernor;
        this.embeddingPort = embeddingPort;
    }

    @Override
    public LlmChatClient getChatModel(String modelId) {
        Model model = modelService.getModelByID(modelId);
        ModelParameters p = model.getParameters();
        ChatConfig config = ModelRuntimeConfigs.chatConfig(model,
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
