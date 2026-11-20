package com.ragagent.agentengine.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** {@code ToolResult} 的 Jackson mixin：空 data 不上线。 */
@JsonIgnoreProperties(value = {"outputFiles"})
public abstract class ToolResultMixin {
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> data;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String error;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> images;

}
