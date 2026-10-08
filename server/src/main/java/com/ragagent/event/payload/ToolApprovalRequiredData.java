package com.ragagent.event.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 危险 MCP 工具即将执行时的"请求批准"事件体。
 * emit 点：common/approval/Gate（{@code <pendingID>-approval-required}），见包注释 emit 表 #10。
 *
 * <p>minimal 形态：{@code args}/{@code args_json}/{@code request_id} 空则省略；其余恒输出——零值时
 * {@code {"pendingId":"","tenantId":0,"sessionId":"","assistantMessageId":"","serviceId":"",
 * "service_name":"","mcp_tool_name":"","registered_tool_name":"","description":"",
 * "timeout_seconds":0,"requestedAtUnix":0,"toolCallId":""}}。</p>
 *
 * <p>{@code args} 是解析后的 JSON 对象（给 UI 渲染表单），{@code args_json} 是原始
 * JSON 串（给回填），两者都提供。</p>
 */

public class ToolApprovalRequiredData {

    @JsonProperty("pendingId")
    private String pendingId = "";

    /** 0 恒输出 */
    @JsonProperty("tenantId")
    private long tenantId;

    @JsonProperty("sessionId")
    private String sessionId = "";

    @JsonProperty("assistantMessageId")
    private String assistantMessageId = "";

    @JsonProperty("serviceId")
    private String serviceId = "";

    @JsonProperty("serviceName")
    private String serviceName = "";

    @JsonProperty("mcpToolName")
    private String mcpToolName = "";

    @JsonProperty("registeredToolName")
    private String registeredToolName = "";

    @JsonProperty("description")
    private String description = "";

    /** null 或空省略 */
    @JsonProperty("args")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Object args;

    /** 空串省略 */
    @JsonProperty("argsJson")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private String argsJson = "";

    @JsonProperty("timeoutSeconds")
    private int timeoutSeconds;

    /** 请求发出时间（unix 秒）；0 恒输出 */
    @JsonProperty("requestedAtUnix")
    private long requestedAtUnix;

    @JsonProperty("toolCallId")
    private String toolCallId = "";

    /** 空串省略 */
    @JsonProperty("requestId")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private String requestId = "";

    public ToolApprovalRequiredData() {
    }

    public ToolApprovalRequiredData(String pendingId, long tenantId, String sessionId,
                                    String assistantMessageId, String serviceId, String serviceName,
                                    String mcpToolName, String registeredToolName, String description,
                                    Object args, String argsJson, int timeoutSeconds,
                                    long requestedAtUnix, String toolCallId, String requestId) {
        this.pendingId = QueryData.orEmpty(pendingId);
        this.tenantId = tenantId;
        this.sessionId = QueryData.orEmpty(sessionId);
        this.assistantMessageId = QueryData.orEmpty(assistantMessageId);
        this.serviceId = QueryData.orEmpty(serviceId);
        this.serviceName = QueryData.orEmpty(serviceName);
        this.mcpToolName = QueryData.orEmpty(mcpToolName);
        this.registeredToolName = QueryData.orEmpty(registeredToolName);
        this.description = QueryData.orEmpty(description);
        this.args = args;
        this.argsJson = QueryData.orEmpty(argsJson);
        this.timeoutSeconds = timeoutSeconds;
        this.requestedAtUnix = requestedAtUnix;
        this.toolCallId = QueryData.orEmpty(toolCallId);
        this.requestId = QueryData.orEmpty(requestId);
    }

    public String getPendingId() {
        return pendingId;
    }

    public void setPendingId(String v) {
        this.pendingId = QueryData.orEmpty(v);
    }

    public long getTenantId() {
        return tenantId;
    }

    public void setTenantId(long v) {
        this.tenantId = v;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String v) {
        this.sessionId = QueryData.orEmpty(v);
    }

    public String getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(String v) {
        this.assistantMessageId = QueryData.orEmpty(v);
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String v) {
        this.serviceId = QueryData.orEmpty(v);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String v) {
        this.serviceName = QueryData.orEmpty(v);
    }

    public String getMcpToolName() {
        return mcpToolName;
    }

    public void setMcpToolName(String v) {
        this.mcpToolName = QueryData.orEmpty(v);
    }

    public String getRegisteredToolName() {
        return registeredToolName;
    }

    public void setRegisteredToolName(String v) {
        this.registeredToolName = QueryData.orEmpty(v);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = QueryData.orEmpty(v);
    }

    @JsonProperty("args")
    public Object getArgs() {
        return args;
    }

    public void setArgs(Object v) {
        this.args = v;
    }

    @JsonProperty("argsJson")
    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String v) {
        this.argsJson = QueryData.orEmpty(v);
    }

    @JsonProperty("timeoutSeconds")
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int v) {
        this.timeoutSeconds = v;
    }

    public long getRequestedAtUnix() {
        return requestedAtUnix;
    }

    public void setRequestedAtUnix(long v) {
        this.requestedAtUnix = v;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String v) {
        this.toolCallId = QueryData.orEmpty(v);
    }

    @JsonProperty("requestId")
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String v) {
        this.requestId = QueryData.orEmpty(v);
    }
}
