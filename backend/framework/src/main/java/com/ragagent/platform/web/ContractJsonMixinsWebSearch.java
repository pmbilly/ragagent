package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * websearch 面契约载荷的 Jackson mixin（{@code ImageInfo}）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 *
 * <p>其余三件（{@code WebSearchResult} / {@code WebSearchRequest} / {@code WebSearchFilters}）
 * 键名即字段名，不需要 mixin。</p>
 */
public final class ContractJsonMixinsWebSearch {

    private ContractJsonMixinsWebSearch() {
    }

    /** ImageInfo 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ImageInfoMixin {

        @JsonProperty("url")
        private String url;

        @JsonProperty("original_url")
        private String originalUrl;

        @JsonProperty("start_pos")
        private int startPos;

        @JsonProperty("end_pos")
        private int endPos;

        @JsonProperty("caption")
        private String caption;

        @JsonProperty("ocr_text")
        private String ocrText;

    }
}
