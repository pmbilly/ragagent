package com.ragagent.contract.model;

import java.util.Map;

/**
 * VLM 消费面配置（视觉语言模型的接入形状）。
 *
 * <p>{@code interfaceType} 空时按 {@code source} 推断：{@code local} → ollama，否则 openai
 * ——与 {@code ModelRuntimeConfigs#vlmConfig} 的构造口径一致。</p>
 */
public record VlmConfig(String source, String baseUrl, String modelName, String apiKey,
        String modelId, String interfaceType, String provider,
        Map<String, String> extra) {

    /** 缺省温度（extra 未给或解析失败时）。 */
    public static final double DEFAULT_TEMP = 0.1;

    public double temperature() {
        String v = extra == null ? null : extra.get("temperature");
        if (v != null) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException ignored) {
                // 解析失败保持缺省
            }
        }
        return DEFAULT_TEMP;
    }

    public boolean isOllama() {
        // ConfigFromModel：interfaceType 空时 source==local → ollama，否则 openai
        String ifType = interfaceType == null || interfaceType.isEmpty()
                ? ("local".equals(source) ? "ollama" : "openai")
                : interfaceType;
        return "ollama".equals(ifType);
    }
}
