package com.ragagent.config.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** wiki / tenant 配置载荷的 Jackson mixin（键名与省略策略沿用既有 wire 契约）。 */
public final class AppJsonMixins {

    private AppJsonMixins() {
    }

    public abstract static class ExtractedItemMixin {
        @JsonProperty("name")
        private String name;

        @JsonProperty("slug")
        private String slug;

        @JsonProperty("aliases")
        private List<String> aliases;

        @JsonProperty("description")
        private String description;

        @JsonProperty("details")
        private String details;

        @JsonProperty("source_chunks")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private List<String> sourceChunks;

    }

    @JsonIgnoreProperties(value = {"retractType"})
    public abstract static class SlugUpdateMixin {
    }

    public abstract static class WebSearchConfigMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("provider")
        private String provider;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("api_key")
        private String apiKey;

        @JsonProperty("max_results")
        private int maxResults;

        @JsonProperty("include_date")
        private boolean includeDate;

        @JsonProperty("compression_method")
        private String compressionMethod;

        @JsonProperty("blacklist")
        private List<String> blacklist;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("embedding_model_id")
        private String embeddingModelId;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @JsonProperty("embedding_dimension")
        private int embeddingDimension;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("rerank_model_id")
        private String rerankModelId;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @JsonProperty("document_fragments")
        private int documentFragments;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("proxy_url")
        private String proxyUrl;

    }

    public abstract static class ParserEngineConfigMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("chat_parser_engine_rules")
        private List<Object> chatParserEngineRules;
        @JsonProperty("mineru_endpoint")
        private String mineruEndpoint;
        @JsonProperty("mineru_api_key")
        private String mineruApiKey;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("mineru_model")
        private String mineruModel;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("mineru_vlm_server_url")
        private String mineruVlmServerUrl;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mineru_enable_formula")
        private Boolean mineruEnableFormula;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mineru_enable_table")
        private Boolean mineruEnableTable;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("mineru_parse_method")
        private String mineruParseMethod;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mineru_enable_ocr")
        private Boolean mineruEnableOcr;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("mineru_language")
        private String mineruLanguage;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("mineru_cloud_model")
        private String mineruCloudModel;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mineru_cloud_enable_formula")
        private Boolean mineruCloudEnableFormula;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mineru_cloud_enable_table")
        private Boolean mineruCloudEnableTable;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("mineru_cloud_enable_ocr")
        private Boolean mineruCloudEnableOcr;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("mineru_cloud_language")
        private String mineruCloudLanguage;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("odl_hybrid")
        private String odlHybrid;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("odl_hybrid_url")
        private String odlHybridUrl;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("odl_hybrid_mode")
        private String odlHybridMode;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("odl_hybrid_fallback")
        private Boolean odlHybridFallback;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("odl_markdown_with_html")
        private Boolean odlMarkdownWithHtml;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("paddleocr_vl_endpoint")
        private String paddleOcrVlEndpoint;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("paddleocr_vl_use_seal_recognition")
        private Boolean paddleOcrVlUseSealRecognition;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("paddleocr_vl_use_chart_recognition")
        private Boolean paddleOcrVlUseChartRecognition;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("paddleocr_vl_cloud_token")
        private String paddleOcrVlCloudToken;
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("paddleocr_vl_cloud_model")
        private String paddleOcrVlCloudModel;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("paddleocr_vl_cloud_use_seal_recognition")
        private Boolean paddleOcrVlCloudUseSealRecognition;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("paddleocr_vl_cloud_use_chart_recognition")
        private Boolean paddleOcrVlCloudUseChartRecognition;
    }
}
