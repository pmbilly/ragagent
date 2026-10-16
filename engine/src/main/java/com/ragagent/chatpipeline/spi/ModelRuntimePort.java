package com.ragagent.chatpipeline.spi;

import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;
import com.ragagent.embedding.Embedder;
import com.ragagent.contract.model.LlmChatClient;
import com.ragagent.rerank.Reranker;
import com.ragagent.retrieval.vlm.VlmClient.VlmConfig;

/**
 * 模型运行时装配端口（引擎侧接口）：按模型 id 造出可直接调用的能力客户端。
 *
 * <p><b>为什么在 engine</b>：返回类型（{@code LlmChatClient} / {@code Embedder} / {@code Reranker} /
 * {@code VlmConfig}）都属引擎模块，L1（{@code common}）看不到它们；而消费方（知识、会话、初始化等域）
 * 需要的是"按 id 拿到客户端"，不是模型域的实体与服务。</p>
 *
 * <p><b>实现</b>：模型域的运行时装配器（{@code ModelRuntimePort}）实现本端口；
 * 装配按类型注入即可（端口与实现同为一个 bean）。附带的 {@link ModelRowView} 是 L1 载荷，
 * 供 VLM/ASR 这类"还要看模型行属性"的场景使用，避免消费方引模型域实体。</p>
 */
public interface ModelRuntimePort {

    /** 聊天客户端（按 id 装配：密钥/基址/来源等取自模型行）。 */
    LlmChatClient getChatModel(String modelId);

    /** 嵌入客户端（维度等能力从客户端读）。 */
    Embedder getEmbeddingModel(String modelId);

    /** 重排客户端。 */
    Reranker getRerankModel(String modelId);

    /** VLM 模型行（调用方再取配置 / 拼请求；不再向消费方暴露模型实体）。 */
    ModelRowView getVlmModel(String modelId);

    /**
     * VLM 客户端配置：构造 + 非 ollama 的基址 SSRF 校验。
     * <p>失败抛 {@link RuntimeException}，message 即对外错误文案。</p>
     */
    VlmConfig vlmConfigFor(ModelRowView model);

    /** ASR 模型行。 */
    ModelRowView getAsrModel(String modelId);

    /** 构造期基址校验（SSRF）。空值直接返回。 */
    void validateVlmBaseUrl(String baseUrl);
}
