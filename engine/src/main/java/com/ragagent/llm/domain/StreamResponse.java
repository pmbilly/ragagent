package com.ragagent.llm.domain;

import com.ragagent.common.llm.TokenUsage;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ragagent.common.web.SortedMapSerializer;
import com.ragagent.common.retrieval.SearchResult;
import com.ragagent.common.llm.ResponseType;

/**
 * 流式响应。
 *
 * JSON 字段序 = 声明序。
 * id/response_type/content/done 恒输出；其余为空时省略（NON_EMPTY）。
 *
 * 线上契约：这是 SSE 事件体的核心结构，`response_type` 取值见 {@link ResponseType}。
 */

public class StreamResponse {

    private String id = "";
    private ResponseType responseType;
    private String content = "";
    private boolean done;
    /**
     * 检索引用（{@link SearchResult} 列表）。
     *
     * <p>chat 包本身从不设置该字段，只有 SSE 契约层（{@code session.sse}）会填。</p>
     *
     * <p>为空时整键省略。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<SearchResult> knowledgeReferences;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String sessionId;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String assistantMessageId;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ToolCall> toolCalls;
    /**
     * 附加数据（map，为空时省略）。
     *
     * <p>键序由 {@link SortedMapSerializer} 递归按字母序归一——产出方（chat / agent 引擎）
     * 大多用 {@code LinkedHashMap} 按写入序；
     * 嵌套的 {@code arguments} 之类更是直接来自模型返回的 JSON，外层排不掉。</p>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> data;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private TokenUsage usage;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String finishReason;

    /** 流在 provider 发出 finish_reason 之前中断（读错误/超时/停滞）时使用。 */
    public static final String FINISH_REASON_INCOMPLETE = "incomplete";

    public StreamResponse() {
    }

    /** 常用构造：类型 + 内容 + 完成标记 */
    public static StreamResponse of(ResponseType type, String content, boolean done) {
        StreamResponse r = new StreamResponse();
        r.responseType = type;
        r.content = content == null ? "" : content;
        r.done = done;
        return r;
    }

    public String getId() { return id; }
    public void setId(String v) { id = v == null ? "" : v; }
    public ResponseType getResponseType() { return responseType; }
    public void setResponseType(ResponseType v) { responseType = v; }
    public String getContent() { return content; }
    public void setContent(String v) { content = v == null ? "" : v; }
    public boolean isDone() { return done; }
    public void setDone(boolean v) { done = v; }
    public List<SearchResult> getKnowledgeReferences() { return knowledgeReferences; }
    public void setKnowledgeReferences(List<SearchResult> v) { knowledgeReferences = v; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String v) { sessionId = v; }
    public String getAssistantMessageId() { return assistantMessageId; }
    public void setAssistantMessageId(String v) { assistantMessageId = v; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> v) { toolCalls = v; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> v) { data = v; }
    public TokenUsage getUsage() { return usage; }
    public void setUsage(TokenUsage v) { usage = v; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String v) { finishReason = v; }
}
