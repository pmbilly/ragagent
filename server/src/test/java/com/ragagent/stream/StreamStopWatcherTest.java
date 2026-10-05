package com.ragagent.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.ragagent.common.llm.ResponseType;
import org.junit.jupiter.api.Test;

/**
 * 停止事件轮询：发现 {@link ResponseType#STOP} 即触发取消；
 * 普通事件不取消；存活探针转 false 后退出（退出后的 stop 不再触发）。
 */
class StreamStopWatcherTest {

    private final MemoryStreamManager manager = new MemoryStreamManager();

    @Test
    void stopEventTriggersCancel() throws Exception {
        AtomicBoolean alive = new AtomicBoolean(true);
        CountDownLatch cancelled = new CountDownLatch(1);
        StreamStopWatcher.start(manager, "s-1", "m-1", alive::get, cancelled::countDown);

        manager.appendEvent("s-1", "m-1", new StreamEvent("stop-1", ResponseType.STOP, "", true));

        assertTrue(cancelled.await(3, TimeUnit.SECONDS), "stop 事件应触发取消");
    }

    @Test
    void nonStopEventsDoNotCancelAndAliveFalseExits() throws Exception {
        AtomicBoolean alive = new AtomicBoolean(true);
        AtomicInteger cancelled = new AtomicInteger();
        StreamStopWatcher.start(manager, "s-2", "m-2", alive::get, cancelled::incrementAndGet);

        manager.appendEvent("s-2", "m-2", new StreamEvent("e-1", ResponseType.ANSWER, "hi", false));
        Thread.sleep(200);
        assertEquals(0, cancelled.get(), "普通事件不触发取消");

        // QA 结束（alive=false）→ watcher 应在下一轮退出；之后的 stop 事件不再被消费
        alive.set(false);
        Thread.sleep(200);
        manager.appendEvent("s-2", "m-2", new StreamEvent("stop-2", ResponseType.STOP, "", true));
        Thread.sleep(700);
        assertEquals(0, cancelled.get(), "退出后的 stop 事件不再触发取消");
    }
}
