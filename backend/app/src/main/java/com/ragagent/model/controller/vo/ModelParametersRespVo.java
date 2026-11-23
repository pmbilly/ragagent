package com.ragagent.model.controller.vo;

import java.util.Map;

/**
 * 模型参数的 HTTP 响应体（除 apiKey/appSecret 外的全部参数）。
 *
 * <p>可空字段显式输出 null；组件声明顺序即线格式。</p>
 */
public record ModelParametersRespVo(
        String baseUrl,
        String interfaceType,
        EmbeddingParametersRespVo embeddingParameters,
        String parameterSize,
        String provider,
        Map<String, String> extraConfig,
        Map<String, String> customHeaders,
        boolean supportsVision,
        Integer contextWindow,
        Integer maxOutputTokens,
        Integer maxConcurrency,
        String appId) {

    /** 向量维度参数。 */
    public record EmbeddingParametersRespVo(
            int dimension,
            int truncatePromptTokens,
            boolean supportsDimensionOverride) {
    }
}
