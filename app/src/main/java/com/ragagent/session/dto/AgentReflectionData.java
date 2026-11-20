package com.ragagent.session.dto;




/**
 * Agent 反思数据。
 *
 * <p>四字段全部恒输出：零值输出
 * {@code {"toolCallId":"","content":"","iteration":0,"done":false}}。</p>
 */

public class AgentReflectionData {

    /** 工具调用 ID（追踪用） */
    private String toolCallId = "";

    private String content = "";

    private int iteration;

    /** 流式是否完成 */
    private boolean done;

    public AgentReflectionData() {
    }

    public AgentReflectionData(String toolCallId, String content, int iteration, boolean done) {
        this.toolCallId = toolCallId == null ? "" : toolCallId;
        this.content = content == null ? "" : content;
        this.iteration = iteration;
        this.done = done;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String v) {
        this.toolCallId = v == null ? "" : v;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String v) {
        this.content = v == null ? "" : v;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int v) {
        this.iteration = v;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean v) {
        this.done = v;
    }
}
