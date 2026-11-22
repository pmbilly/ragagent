package com.ragagent.config.json;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 会话停止与 MCP 审批事件 payload 的 Jackson mixin（SSE 前端协议面，随载荷自 framework.jsonbind 迁入）。
 *
 * <p>载荷类在 {@code session.api.StopData} 与 {@code mcp.api}（审批/OAuth 4 类）；
 * 线格式由本文件承载，经 {@link AppJsonModule} 注册。</p>
 */
public final class EventJsonMixins {

    private EventJsonMixins() {
    }

    /** StopData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class StopDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String reason;

    }

    /** ToolApprovalRequiredData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ToolApprovalRequiredDataMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        private Object args;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String argsJson;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String requestId;

    }

    /** ToolApprovalResolvedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class ToolApprovalResolvedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String reason;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean timedOut;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean canceled;

    }

    /** MCPOAuthRequiredData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MCPOAuthRequiredDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String requestId;

    }

    /** MCPOAuthResolvedData 的 mixin（注解逐条对位，行为与搬迁前一致）。 */
    public abstract static class MCPOAuthResolvedDataMixin {
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private String reason;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean timedOut;

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        private boolean canceled;

    }

}
