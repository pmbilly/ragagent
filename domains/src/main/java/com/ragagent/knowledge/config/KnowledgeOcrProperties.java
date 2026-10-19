package com.ragagent.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 入库图片 OCR 服务配置。
 *
 * <p>{@code endpoint} 为空即关闭（默认关闭，行为与未接入时一致）；{@code maxPages}
 * 限制单篇文档送入 OCR 的图片张数——扫描件每页一张图，不设上限会随页数线性放大耗时。</p>
 */
@ConfigurationProperties(prefix = "knowledge.ocr")
public record KnowledgeOcrProperties(String endpoint, Integer maxPages,
                                     Integer timeoutSeconds, Integer concurrency) {

    private static final int DEFAULT_MAX_PAGES = 30;
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_CONCURRENCY = 2;

    /** 是否配置了 OCR 服务地址。 */
    public boolean enabled() {
        return endpoint != null && !endpoint.isBlank();
    }

    /** 服务基地址（去尾部斜杠）。 */
    public String baseUrl() {
        String value = endpoint == null ? "" : endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public int maxPagesOrDefault() {
        return maxPages == null || maxPages <= 0 ? DEFAULT_MAX_PAGES : maxPages;
    }

    public int timeoutSecondsOrDefault() {
        return timeoutSeconds == null || timeoutSeconds <= 0
                ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

    public int concurrencyOrDefault() {
        return concurrency == null || concurrency <= 0 ? DEFAULT_CONCURRENCY : concurrency;
    }
}
