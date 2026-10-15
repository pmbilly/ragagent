package com.ragagent.mcp.tool;

import com.ragagent.contract.tool.ApprovalEventBus;
import com.ragagent.contract.event.Event;
import com.ragagent.platform.event.EventBus;

/**
 * 平台事件总线 → 审批门的事件总线（{@link ApprovalEventBus}）。
 *
 * <p>闸门发出的事件 data 已是 {@code contract.event.payload} 线格式 DTO（Gate 侧直接构造），
 * 这里只换信封：逐字段转投真实总线。SSE 转发层（AgentStreamBridge）只认 payload 形态——
 * 失配即审批卡在聊天流里静默消失（曾实测断链）。</p>
 */
final class ApprovalEventBuses {

    private ApprovalEventBuses() {
    }

    static ApprovalEventBus toEventBus(EventBus bus) {
        if (bus == null) {
            return null;
        }
        return event -> bus.emit(new Event(
                event.id(),
                event.type() == null ? "" : event.type().value(),
                event.sessionId(),
                event.data(),
                event.metadata(),
                event.requestId()));
    }
}
