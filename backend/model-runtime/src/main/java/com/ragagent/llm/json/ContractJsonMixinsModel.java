package com.ragagent.llm.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragagent.framework.vocabulary.SearchResult;
import com.ragagent.framework.web.SortedMapSerializer;
import com.ragagent.llm.api.PromptCacheStatus;
import com.ragagent.llm.api.RankResult;
import com.ragagent.llm.api.TokenUsage;
import com.ragagent.llm.api.ToolCall;
import java.util.List;
import java.util.Map;


/**
 * 模型面契约载荷的 Jackson mixin（{@code StreamResponse}）。
 *
 * <p>为什么独立成文件：契约面（{@code contract.*}）要求<b>纯 JDK</b>——载荷类的线格式由
 * 这里的 mixin 承载（Jackson 只在读写时把注解套上去），契约类本身零框架引用。</p>
 *
 * <p>键序由 {@link SortedMapSerializer} 在序列化侧递归归一（map 按字母序），
 * 与 mixin 无关。</p>
 */
public final class ContractJsonMixinsModel {

    private ContractJsonMixinsModel() {
    }

    /** RankResult 的 mixin（注解逐条对位；index/document 是同名标注，relevance_score 是真改名）。 */
    public abstract static class RankResultMixin {

        @JsonProperty("index")
        private int index;

        @JsonProperty("document")
        private RankResult.DocumentInfo document;

        @JsonProperty("relevance_score")
        private double relevanceScore;

        /** 内嵌文档形状：恒输出 {@code {"text":"..."}}。 */
        public abstract static class DocumentInfoMixin {

            private String text;

        }
    }

    /** StreamResponse 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class StreamResponseMixin {

        /** 为空时整键省略。 */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<SearchResult> knowledgeReferences;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String sessionId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String assistantMessageId;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<ToolCall> toolCalls;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Map<String, Object> data;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private TokenUsage usage;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private String finishReason;

    }

    /** TokenUsage 的 mixin：键名与省略策略逐条对位。 */
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
}
