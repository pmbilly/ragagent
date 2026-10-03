package com.ragagent.agent.tools;

import com.ragagent.event.Event;
import com.ragagent.event.EventBus;

/**
 * tools 包 ↔ common.approval 包的类型桥（两个包面之间的显式适配）。
 *
 * <ul>
 *   <li>{@code ToolCancellation}（ctx.Err() 语义）→ {@code approval.Cancellation}
 *       （isCancelled + onCancel 最小面；工具侧没有"取消时回调"的注册点，实现为 no-op）；</li>
 *   <li>{@code event.EventBus / event.Event} → {@code approval.EventBus / approval.Event}
 *       （gate 只发不订——逐字段转投真实总线）；</li>
 *   <li>{@code approval.*} 门内 DTO → {@code event.payload.*} 线格式 DTO（见 {@link #toPayloadData}）。</li>
 * </ul>
 */
public final class ApprovalBridge {

    private ApprovalBridge() {
    }

    public static com.ragagent.common.approval.Cancellation toCancellation(ToolCancellation cancellation) {
        if (cancellation == null) {
            return com.ragagent.common.approval.Cancellation.none();
        }
        return new com.ragagent.common.approval.Cancellation() {
            @Override
            public boolean isCancelled() {
                return cancellation.cancellationError() != null;
            }

            /**
             * 工具侧没有"取消时回调"的注册点，曾实现为 no-op——代价是用户点停止后
             * 10 分钟的人工审批等待照跑（烧 token/占沙箱）。这里用探测线程桥接
             * 轮询式 ToolCancellation → 回调式 onCancel：轮询到取消即触发 action，
             * 注册被 close（finally）后停止观察。
             */
            @Override
            public AutoCloseable onCancel(Runnable action) {
                if (action == null) {
                    return () -> {
                    };
                }
                java.util.concurrent.atomic.AtomicBoolean settled =
                        new java.util.concurrent.atomic.AtomicBoolean();
                Thread.ofVirtual().name("approval-cancel-watch").start(() -> {
                    while (!settled.get()) {
                        if (cancellation.cancellationError() != null) {
                            if (settled.compareAndSet(false, true)) {
                                try {
                                    action.run();
                                } catch (RuntimeException ignored) {
                                    // 取消回调失败不外泄（gate 自己的 deliver 会兜底）
                                }
                            }
                            return;
                        }
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                });
                return () -> settled.set(true);
            }
        };
    }

    static com.ragagent.common.approval.EventBus toEventBus(EventBus bus) {
        if (bus == null) {
            return null;
        }
        return approvalEvent -> bus.emit(new Event(
                approvalEvent.id(),
                approvalEvent.type() == null ? "" : approvalEvent.type().value(),
                approvalEvent.sessionId(),
                toPayloadData(approvalEvent.data()),
                approvalEvent.metadata(),
                approvalEvent.requestId()));
    }

    /**
     * common.approval 的门内 DTO → event.payload 的线格式 DTO。
     * SSE 转发层（AgentStreamBridge）只认 event.payload 形态——缺了这层映射，
     * 审批请求/决议事件会因 instanceof 失配被静默丢弃，聊天流里永远不出现审批卡。
     * 未知形态原样透传（向后兼容）。
     */
    private static Object toPayloadData(Object data) {
        if (data instanceof com.ragagent.common.approval.ToolApprovalRequiredData d) {
            return new com.ragagent.event.payload.ToolApprovalRequiredData(
                    d.pendingId(), d.tenantId(), d.sessionId(), d.assistantMessageId(),
                    d.serviceId(), d.serviceName(), d.mcpToolName(), d.registeredToolName(),
                    d.description(), d.args(), d.argsJson(), d.timeoutSeconds(),
                    d.requestedAtUnix(), d.toolCallId(), d.requestId());
        }
        if (data instanceof com.ragagent.common.approval.ToolApprovalResolvedData d) {
            return new com.ragagent.event.payload.ToolApprovalResolvedData(
                    d.pendingId(), d.approved(), d.reason(), d.timedOut(), d.canceled());
        }
        if (data instanceof com.ragagent.common.approval.McpOauthRequiredData d) {
            return new com.ragagent.event.payload.MCPOAuthRequiredData(
                    d.pendingId(), d.tenantId(), d.sessionId(), d.assistantMessageId(),
                    d.serviceId(), d.serviceName(), d.mcpToolName(), d.timeoutSeconds(),
                    d.requestedAtUnix(), d.toolCallId(), d.requestId());
        }
        if (data instanceof com.ragagent.common.approval.McpOauthResolvedData d) {
            return new com.ragagent.event.payload.MCPOAuthResolvedData(
                    d.pendingId(), d.serviceId(), d.authorized(), d.reason(),
                    d.timedOut(), d.canceled());
        }
        return data;
    }
}
