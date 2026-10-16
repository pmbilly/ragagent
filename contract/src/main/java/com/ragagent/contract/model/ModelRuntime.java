package com.ragagent.contract.model;

import com.ragagent.contract.model.ModelCatalogPort.ModelRowView;

/**
 * 模型运行时端口（契约面）：按模型 id 造出可直接调用的能力客户端。
 *
 * <p><b>为什么在契约面</b>：返回类型（{@code LlmChatClient} / {@code Embedder} / {@code Reranker} /
 * {@code VlmConfig}）与入参（{@link ModelRowView}）全是契约词汇；消费方（知识、会话、初始化、
 * 记忆、wiki、mcp 等域）需要的是"按 id 拿到客户端"，不是模型域的实体与服务。</p>
 *
 * <p><b>实现</b>：model-runtime 的装配器实现本端口，按类型注入即可（端口与实现同为一个 bean）。</p>
 */
public interface ModelRuntime {

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
