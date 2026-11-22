package com.ragagent.framework.event.payload;

import java.util.Map;


/**
 * 工具执行结果数据。
 * emit 点：ActPhase（{@code <toolCallID>-tool-result}）。
 *
 * <p>零值输出
 * {@code {"toolCallId":"","toolName":"","output":"","success":false,"iteration":0}}；
 * {@code error}/{@code duration_ms}/{@code data} 空则省略。</p>
 */

public class AgentToolResultData {

    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }


    /** 工具调用 ID（追踪用） */
    private String toolCallId = "";

    private String toolName = "";

    private String output = "";

    /** 空串省略 */
    private String error = "";

    /** false 恒输出 */
    private boolean success;

    /** 0 省略 */
    private long durationMs;

    private int iteration;

    /** 工具结果的结构化数据（display_type、格式化结果等）；null 或空省略 */
    private Map<String, Object> data;

    public AgentToolResultData() {
    }

    public AgentToolResultData(String toolCallId, String toolName, String output, String error,
                               boolean success, long durationMs, int iteration,
                               Map<String, Object> data) {
        this.toolCallId = orEmpty(toolCallId);
        this.toolName = orEmpty(toolName);
        this.output = orEmpty(output);
        this.error = orEmpty(error);
        this.success = success;
        this.durationMs = durationMs;
        this.iteration = iteration;
        this.data = data;
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

    public String getOutput() {
        return output;
    }

    public void setOutput(String v) {
        this.output = orEmpty(v);
    }

    public String getError() {
        return error;
    }

    public void setError(String v) {
        this.error = orEmpty(v);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean v) {
        this.success = v;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long v) {
        this.durationMs = v;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int v) {
        this.iteration = v;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> v) {
        this.data = v;
    }
}
