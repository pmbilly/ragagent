package com.ragagent.contract.knowledge.domain;


/** VLM（图像描述）配置视图——**不含** {@code apiKey}。 */
public record VlmConfigView(
        boolean enabled,
        String modelId,
        String descriptionLanguage,
        String customInstructions,
        String modelName,
        String baseUrl,
        String interfaceType) {
}
