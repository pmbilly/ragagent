package com.ragagent.retrieval.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** vectorstore / retrieval 载荷的 Jackson mixin（键名与省略策略由模型侧约定）。 */
public final class VectorJsonMixins {

    private VectorJsonMixins() {
    }

    public abstract static class SearchParamsMixin {
        @JsonProperty("query_text")
        private String queryText;

        @JsonProperty("query_embedding")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private float[] queryEmbedding;

        @JsonProperty("vector_threshold")
        private double vectorThreshold;

        @JsonProperty("keyword_threshold")
        private double keywordThreshold;

        @JsonProperty("match_count")
        private int matchCount;

        @JsonProperty("disable_keywords_match")
        private boolean disableKeywordsMatch;

        @JsonProperty("disable_vector_match")
        private boolean disableVectorMatch;

        @JsonProperty("knowledge_ids")
        private List<String> knowledgeIds;

        @JsonProperty("tag_ids")
        private List<String> tagIds;

        @JsonProperty("scope_tag_ids")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<String> scopeTagIds;

        @JsonProperty("only_recommended")
        private boolean onlyRecommended;

        @JsonProperty("knowledge_base_ids")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private List<String> knowledgeBaseIds;

        @JsonProperty("skip_context_enrichment")
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean skipContextEnrichment;

    }

    @JsonIgnoreProperties(value = {"endpoint"})
    public abstract static class ConnectionConfigMixin {
    }

    public abstract static class IndexConfigMixin {
    }
}
