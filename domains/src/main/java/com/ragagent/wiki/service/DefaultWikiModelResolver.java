package com.ragagent.wiki.service;

import org.springframework.stereotype.Component;

import com.ragagent.contract.model.ModelCatalogPort;
import com.ragagent.contract.knowledge.EmbeddingModelPort;
import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.contract.model.ModelRuntime;

/**
 * {@link WikiModelResolver} 的默认实现：用 Java 侧已有的部件拼出 chat / embedding 模型。
 *
 * <ul>
 *   <li>chat：{@code ModelCatalogPort.findModel}（含"downloading → 500"状态闸门，来自模型域）
 *       → {@link ModelRuntime#chatClientFor}（构造与并发闸门装饰收在 model-runtime 一处）。</li>
 *   <li>embedding：先校验模型类型确为 embedding，再复用
 *       {@link com.ragagent.contract.knowledge.EmbeddingModelPort}（L1 端口；实现侧仍是最小 OpenAI 兼容客户端）。</li>
 * </ul>
 *
 * <p>跨域口径与 {@code memory.service.DefaultMemoryModelResolver} 一致：只经契约面
 * （{@link ModelCatalogPort} 取行、{@link ModelRuntime} 造客户端），不出现模型族实现类型。</p>
 */
@Component
public class DefaultWikiModelResolver implements WikiModelResolver {

    private final ModelCatalogPort modelCatalog;
    private final ModelRuntime modelRuntime;
    private final EmbeddingModelPort embeddingPort;

    public DefaultWikiModelResolver(ModelCatalogPort modelCatalog,
                                    ModelRuntime modelRuntime,
                                    EmbeddingModelPort embeddingPort) {
        this.modelCatalog = modelCatalog;
        this.modelRuntime = modelRuntime;
        this.embeddingPort = embeddingPort;
    }

    @Override
    public LlmChatClient getChatModel(String modelId) {
        return modelRuntime.chatClientFor(modelCatalog.findModel(modelId));
    }

    @Override
    public WikiEmbeddingModel getEmbeddingModel(String modelId) {
        // embedding 类型闸门（非 embedding 模型抛 IllegalStateException）与
        // EmbedderClient 配置解析在 knowledge 侧端口内完成；wiki 只拿函数式客户端。
        EmbeddingModelPort.Embedder embedder = embeddingPort.embedderFor(modelId);
        return embedder::batchEmbed;
    }
}
