package com.ragagent.contract.model;

/**
 * 模型配置结构校验的输入：只携带厂商规则需要的字段，不含落库/加密细节。
 *
 * <p>{@code provider} 是原始字符串（{@code models.parameters.provider}）；空串或未知值
 * 表示没有厂商规则（本地 Ollama、自定义 OpenAI 兼容端点），校验器按"无语义"处理。</p>
 */
public record ModelConfigDraft(
        String provider,
        String baseUrl,
        String apiKey,
        String modelName) {

    public ModelConfigDraft {
        provider = provider == null ? "" : provider;
        baseUrl = baseUrl == null ? "" : baseUrl;
        apiKey = apiKey == null ? "" : apiKey;
        modelName = modelName == null ? "" : modelName;
    }
}
