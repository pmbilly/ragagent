package com.ragagent.framework.event.payload;


/**
 * 危险 MCP 工具即将执行时的"请求批准"事件体。
 * emit 点：common/approval/Gate（{@code <pendingID>-approval-required}）。
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

    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }


    private String pendingId = "";

    /** 0 恒输出 */
    private long tenantId;

    private String sessionId = "";

    private String assistantMessageId = "";

    private String serviceId = "";

    private String serviceName = "";

    private String mcpToolName = "";

    private String registeredToolName = "";

    private String description = "";

    /** null 或空省略 */
    private Object args;

    /** 空串省略 */
    private String argsJson = "";

    private int timeoutSeconds;

    /** 请求发出时间（unix 秒）；0 恒输出 */
    private long requestedAtUnix;

    private String toolCallId = "";

    /** 空串省略 */
    private String requestId = "";

    public ToolApprovalRequiredData() {
    }

    public ToolApprovalRequiredData(String pendingId, long tenantId, String sessionId,
                                    String assistantMessageId, String serviceId, String serviceName,
                                    String mcpToolName, String registeredToolName, String description,
                                    Object args, String argsJson, int timeoutSeconds,
                                    long requestedAtUnix, String toolCallId, String requestId) {
        this.pendingId = orEmpty(pendingId);
        this.tenantId = tenantId;
        this.sessionId = orEmpty(sessionId);
        this.assistantMessageId = orEmpty(assistantMessageId);
        this.serviceId = orEmpty(serviceId);
        this.serviceName = orEmpty(serviceName);
        this.mcpToolName = orEmpty(mcpToolName);
        this.registeredToolName = orEmpty(registeredToolName);
        this.description = orEmpty(description);
        this.args = args;
        this.argsJson = orEmpty(argsJson);
        this.timeoutSeconds = timeoutSeconds;
        this.requestedAtUnix = requestedAtUnix;
        this.toolCallId = orEmpty(toolCallId);
        this.requestId = orEmpty(requestId);
    }

    public String getPendingId() {
        return pendingId;
    }

    public void setPendingId(String v) {
        this.pendingId = orEmpty(v);
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
        this.sessionId = orEmpty(v);
    }

    public String getAssistantMessageId() {
        return assistantMessageId;
    }

    public void setAssistantMessageId(String v) {
        this.assistantMessageId = orEmpty(v);
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String v) {
        this.serviceId = orEmpty(v);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String v) {
        this.serviceName = orEmpty(v);
    }

    public String getMcpToolName() {
        return mcpToolName;
    }

    public void setMcpToolName(String v) {
        this.mcpToolName = orEmpty(v);
    }

    public String getRegisteredToolName() {
        return registeredToolName;
    }

    public void setRegisteredToolName(String v) {
        this.registeredToolName = orEmpty(v);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String v) {
        this.description = orEmpty(v);
    }

    public Object getArgs() {
        return args;
    }

    public void setArgs(Object v) {
        this.args = v;
    }

    public String getArgsJson() {
        return argsJson;
    }

    public void setArgsJson(String v) {
        this.argsJson = orEmpty(v);
    }

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
        this.toolCallId = orEmpty(v);
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String v) {
        this.requestId = orEmpty(v);
    }
}
