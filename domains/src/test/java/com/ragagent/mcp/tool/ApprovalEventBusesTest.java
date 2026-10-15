package com.ragagent.mcp.tool;

import com.ragagent.contract.event.payload.ToolApprovalRequiredData;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.tool.ApprovalEvent;
import com.ragagent.contract.tool.ApprovalEventBus;
import com.ragagent.contract.event.Event;
import com.ragagent.platform.event.EventBus;
import com.ragagent.platform.event.EventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link ApprovalEventBuses} 的总线桥：闸门事件（data 已是 {@code contract.event.payload}
 * 线格式 DTO——Gate 侧直接构造）必须原样到达平台总线。SSE 转发层
 * （AgentStreamBridge）只认 payload 形态，失配即审批卡在聊天流里静默消失（曾实测断链）。
 */
class ApprovalEventBusesTest {

    private Event emitViaBridge(Object data) {
        EventBus bus = new EventBus();
        AtomicReference<Event> received = new AtomicReference<>();
        bus.on(EventType.EVENT_TOOL_APPROVAL_REQUIRED, received::set);
        ApprovalEventBus bridged = ApprovalEventBuses.toEventBus(bus);
        bridged.emit(new ApprovalEvent(
                "pending-1-approval-required",
                ResponseType.TOOL_APPROVAL_REQUIRED,
                "sess-1",
                data,
                Map.of("assistantMessageId", "am-1"),
                "req-1"));
        return received.get();
    }

    @Test
    void payloadDataPassesThroughWithEnvelope() {
        ToolApprovalRequiredData data = new ToolApprovalRequiredData(
                "pending-1", 7L, "sess-1", "am-1", "svc-1", "Service", "tool_a", "reg_tool",
                "does dangerous things", Map.of("path", "/etc"), "{\"path\":\"/etc\"}", 600,
                1759500000L, "call-1", "req-1");

        Event evt = emitViaBridge(data);

        assertSame(data, evt.getData());
        assertEquals("pending-1", ((ToolApprovalRequiredData) evt.getData()).getPendingId());
        assertEquals("pending-1-approval-required", evt.getId());
        assertEquals("sess-1", evt.getSessionId());
        assertEquals("req-1", evt.getRequestId());
        assertEquals("am-1", evt.getMetadata().get("assistantMessageId"));
    }

    @Test
    void unknownDataPassesThroughUnchanged() {
        Object unknown = List.of("raw");
        Event evt = emitViaBridge(unknown);
        assertSame(unknown, evt.getData());
    }

    @Test
    void nullBusReturnsNull() {
        assertNull(ApprovalEventBuses.toEventBus(null));
    }
}
