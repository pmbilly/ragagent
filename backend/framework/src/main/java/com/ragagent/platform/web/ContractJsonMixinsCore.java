package com.ragagent.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragagent.shared.model.PromptCacheStatus;


/**
 * 非事件面契约载荷的 Jackson mixin（error/model/retrieval/tenant/tool/vectorstore/wiki）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 */
public final class ContractJsonMixinsCore {

    private ContractJsonMixinsCore() {
    }

    /** AppError 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public abstract static class AppErrorMixin {
    }

    /** TokenUsage 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class TokenUsageMixin {
        @JsonProperty("prompt_tokens")
        private int promptTokens;

        @JsonProperty("completion_tokens")
        private int completionTokens;

        @JsonProperty("total_tokens")
        private int totalTokens;

        @JsonProperty("cached_tokens")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private int cachedTokens;

        @JsonProperty("cache_read_tokens")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private int cacheReadTokens;

        @JsonProperty("cache_write_tokens")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private int cacheWriteTokens;

        @JsonProperty("cache_miss_tokens")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private int cacheMissTokens;

        @JsonProperty("cache_reported")
        private boolean cacheReported;

        @JsonProperty("cache_status")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private PromptCacheStatus cacheStatus;

    }

    /** SearchParams 的 mixin（注解逐条对位，行为与搬迁前一致）。 */


    /** WebSearchConfig 的 mixin（注解逐条对位，行为与搬迁前一致）。 */


    /** ConnectionConfig 的 mixin（注解逐条对位，行为与搬迁前一致）。 */


    /** IndexConfig 的 mixin（注解逐条对位，行为与搬迁前一致）。 */


    /** ExtractedItem 的 mixin（注解逐条对位，行为与搬迁前一致）。 */


    /** SlugUpdate 的 mixin（注解逐条对位，行为与搬迁前一致）。 */



    /** 解析引擎配置段（ParserEngineConfig）线格式（去框架化契约类的注解载体）。 */


}
