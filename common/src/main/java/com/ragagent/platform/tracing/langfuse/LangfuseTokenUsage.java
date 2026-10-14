package com.ragagent.platform.tracing.langfuse;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Langfuse 规范化用量 schema，作为 {@code langfuse.observation.usage_details}
 * 属性的 JSON 值。
 *
 * <p>零值字段省略：逐字段 {@code @JsonInclude(NON_DEFAULT)}（unit 走 NON_EMPTY）。</p>
 */
public final class LangfuseTokenUsage {

    @JsonProperty("input")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int input;

    @JsonProperty("output")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int output;

    @JsonProperty("total")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int total;

    @JsonProperty("cache_read_input_tokens")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int cacheRead;

    @JsonProperty("cache_creation_input_tokens")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int cacheWrite;

    @JsonProperty("cache_miss_input_tokens")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int cacheMiss;

    @JsonProperty("unit")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String unit = "";

    public LangfuseTokenUsage() {
    }

    /** 三值便利构造（input/output/total）。 */
    public static LangfuseTokenUsage of(int input, int output, int total) {
        LangfuseTokenUsage u = new LangfuseTokenUsage();
        u.input = input;
        u.output = output;
        u.total = total;
        return u;
    }
}
