package com.ragagent.agent.tools;

import com.ragagent.event.Event;
import com.ragagent.event.EventBus;
import com.ragagent.event.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ragagent.approval.McpOauthRequiredData;
import com.ragagent.approval.McpOauthResolvedData;
import com.ragagent.approval.ToolApprovalRequiredData;
import com.ragagent.approval.ToolApprovalResolvedData;
import com.ragagent.common.llm.ResponseType;
import com.ragagent.event.payload.MCPOAuthRequiredData;
import com.ragagent.event.payload.MCPOAuthResolvedData;

/**
 * ApprovalBridge 的总线桥：gate 发出的 approval 域 DTO 必须以
 * event.payload 线格式形态到达真实总线——SSE 转发层（AgentStreamBridge）
 * 只认后者，失配即审批卡在聊天流里静默消失（曾实测断链）。
 */
class ApprovalBridgeTest {


    private Event emitViaBridge(Object data) {
        EventBus bus = new EventBus();
        AtomicReference<Event> received = new AtomicReference<>();
        bus.on(EventType.EVENT_TOOL_APPROVAL_REQUIRED, received::set);
        com.ragagent.approval.EventBus bridged = ApprovalBridge.toEventBus(bus);
        bridged.emit(new com.ragagent.approval.Event(
                "pending-1-approval-required",
                ResponseType.TOOL_APPROVAL_REQUIRED,
                "sess-1",
                data,
                Map.of("assistant_message_id", "am-1"),
                "req-1"));
        return received.get();
    }

    @Test
    void toolApprovalRequiredArrivesAsPayloadShape() {
        Object args = Map.of("path", "/etc");
        Event evt = emitViaBridge(new ToolApprovalRequiredData(
                "pending-1", 7L, "sess-1", "am-1", "svc-1", "Service", "tool_a", "reg_tool",
                "does dangerous things", args, "{\"path\":\"/etc\"}", 600,
                1759500000L, "call-1", "req-1"));

        var data = assertInstanceOf(
                com.ragagent.event.payload.ToolApprovalRequiredData.class, evt.getData());
        assertEquals("pending-1", data.getPendingId());
        assertEquals(7L, data.getTenantId());
        assertEquals("sess-1", data.getSessionId());
        assertEquals("svc-1", data.getServiceId());
        assertEquals("tool_a", data.getMcpToolName());
        assertEquals(args, data.getArgs());
        assertEquals(600, data.getTimeoutSeconds());
        assertEquals("call-1", data.getToolCallId());
        assertEquals("req-1", evt.getRequestId());
        assertEquals(Map.of("assistant_message_id", "am-1"), evt.getMetadata());
    }

    @Test
    void resolvedOauthRequiredAndOauthResolvedConvertToPayloadShapes() {
        // resolved
        EventBus bus = new EventBus();
        AtomicReference<Event> resolved = new AtomicReference<>();
        bus.on(EventType.EVENT_TOOL_APPROVAL_RESOLVED, resolved::set);
        ApprovalBridge.toEventBus(bus).emit(new com.ragagent.approval.Event(
                "pending-1-approval-resolved",
                ResponseType.TOOL_APPROVAL_RESOLVED,
                "sess-1",
                new ToolApprovalResolvedData(
                        "pending-1", true, "ok", false, false),
                Map.of(),
                "req-2"));
        var resolvedData = assertInstanceOf(
                com.ragagent.event.payload.ToolApprovalResolvedData.class, resolved.get().getData());
        assertTrue(resolvedData.isApproved());
        assertFalse(resolvedData.isTimedOut());

        // oauth required
        AtomicReference<Event> oauth = new AtomicReference<>();
        bus.on(EventType.EVENT_MCP_OAUTH_REQUIRED, oauth::set);
        ApprovalBridge.toEventBus(bus).emit(new com.ragagent.approval.Event(
                "pending-2-mcp-oauth",
                ResponseType.MCP_OAUTH_REQUIRED,
                "sess-1",
                new McpOauthRequiredData(
                        "pending-2", 7L, "sess-1", "am-1", "svc-1", "Service", "tool_a",
                        0, 1759500000L, "call-2", "req-3"),
                Map.of(),
                "req-3"));
        assertInstanceOf(
                MCPOAuthRequiredData.class, oauth.get().getData());

        // oauth resolved（approval 侧叫 approved，payload 侧叫 authorized）
        AtomicReference<Event> oauthResolved = new AtomicReference<>();
        bus.on(EventType.EVENT_MCP_OAUTH_RESOLVED, oauthResolved::set);
        ApprovalBridge.toEventBus(bus).emit(new com.ragagent.approval.Event(
                "pending-2-mcp-oauth-resolved",
                ResponseType.MCP_OAUTH_RESOLVED,
                "sess-1",
                new McpOauthResolvedData(
                        "pending-2", "svc-1", true, "", false, false),
                Map.of(),
                "req-4"));
        var oauthResolvedData = assertInstanceOf(
                MCPOAuthResolvedData.class, oauthResolved.get().getData());
        assertTrue(oauthResolvedData.isAuthorized());
    }

    @Test
    void unknownPayloadPassesThroughUnchanged() {
        EventBus bus = new EventBus();
        AtomicReference<Event> received = new AtomicReference<>();
        bus.on(EventType.EVENT_TOOL_APPROVAL_REQUIRED, received::set);
        Object unknown = List.of("raw");
        ApprovalBridge.toEventBus(bus).emit(new com.ragagent.approval.Event(
                "id", ResponseType.TOOL_APPROVAL_REQUIRED,
                "sess-1", unknown, Map.of(), "req-5"));
        assertSame(unknown, received.get().getData());
    }

    @Test
    void nullBusReturnsNull() {
        assertNull(ApprovalBridge.toEventBus(null));
    }
}
