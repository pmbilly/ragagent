package com.ragagent.platform.stream;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** {@code close()} 的 close-once 语义（幂等 + 钩子异常不穿透），两种队列句柄共用。 */
final class StreamLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StreamLifecycle.class);

    private final AtomicBoolean closed = new AtomicBoolean();
    private final Runnable hook;

    StreamLifecycle(Runnable onClose) {
        this.hook = onClose == null ? () -> { } : onClose;
    }

    boolean isClosed() {
        return closed.get();
    }

    /** @return true = 本次调用触发了关闭（首次）；false = 已关过（幂等 no-op） */
    boolean closeOnce() {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        try {
            hook.run();
        } catch (RuntimeException e) {
            // 关流钩子（关连接 / 中断转发 / 收观测）失败不能让调用方的取消路径炸掉。
            log.warn("chat stream close hook failed: {}", e.toString(), e);
        }
        return true;
    }
}
