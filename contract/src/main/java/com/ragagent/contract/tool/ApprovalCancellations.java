package com.ragagent.contract.tool;

/**
 * 审批取消源的桥：工具侧轮询式 {@link ToolCancellation} → 契约回调式 {@link Cancellation}。
 *
 * <p>工具侧没有"取消时回调"的注册点（{@code ToolCancellation} 只暴露 {@code cancellationError}），
 * 而闸门等待（{@link ToolApprovalGate#requestAndWait}）需要回调式 {@code onCancel}——
 * 这里用探测线程桥接：轮询到取消即触发 action，注册被 close（finally）后停止观察。
 * 少了它，用户点停止后的人工审批等待照跑（烧 token / 占连接）。</p>
 */
public final class ApprovalCancellations {

    private ApprovalCancellations() {
    }

    public static Cancellation toCancellation(ToolCancellation cancellation) {
        if (cancellation == null) {
            return Cancellation.none();
        }
        return new Cancellation() {
            @Override
            public boolean isCancelled() {
                return cancellation.cancellationError() != null;
            }

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
                                    // 取消回调失败不外泄（闸门自己的 deliver 会兜底）
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
}
