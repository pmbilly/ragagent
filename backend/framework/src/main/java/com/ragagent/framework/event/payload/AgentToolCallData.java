package com.ragagent.framework.event.payload;

import java.util.Map;


/**
 * 工具调用通知数据。
 * emit 点：ThinkPhase（pending / progress）/ ActPhase（hint）。
 *
 * <p>{@code arguments} null 与空 map 都省略；{@code hint} 空串省略
 * （人可读提示，如 {@code web_search("query")}）。</p>
 */

public class AgentToolCallData {

    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }


    /** 工具调用 ID（追踪用） */
    private String toolCallId = "";

    private String toolName = "";

    /** null 或空省略 */
    private Map<String, Object> arguments;

    private int iteration;

    /** 人可读的工具提示，如 {@code web_search("query")}；空串省略 */
    private String hint = "";

    public AgentToolCallData() {
    }

    public AgentToolCallData(String toolCallId, String toolName, Map<String, Object> arguments,
                             int iteration, String hint) {
        this.toolCallId = orEmpty(toolCallId);
        this.toolName = orEmpty(toolName);
        this.arguments = arguments;
        this.iteration = iteration;
        this.hint = orEmpty(hint);
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String v) {
        this.toolCallId = orEmpty(v);
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String v) {
        this.toolName = orEmpty(v);
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> v) {
        this.arguments = v;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int v) {
        this.iteration = v;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String v) {
        this.hint = orEmpty(v);
    }
}
