package com.ragagent.im.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * QA 队列行为（语义面：队满/每用户限额/Remove/排空退出/全局闸门）。
 * 前半组走无 Redis 的进程内分支；后半组用 {@link FakeRedis} 覆盖跨实例分支
 * （全局 per-user 计数 + 并发闸门）。
 */
class QaQueueTest {

    @Test
    void enqueueRejectsWhenFullAndPerUserLimited() {
        QaQueue q = new QaQueue(1, 3, 1, req -> {
        });
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "m1")));
        QaQueue.RejectedException perUser = assertThrows(QaQueue.RejectedException.class,
                () -> q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "m2"))));
        assertTrue(perUser.getMessage().contains("per-user queue limit reached (1/1)"));
        q.enqueue(new QaQueue.QaRequest("u2", IncomingMessage.of("wecom", "u2", "m1")));
        q.enqueue(new QaQueue.QaRequest("u3", IncomingMessage.of("wecom", "u3", "m1")));
        QaQueue.RejectedException full = assertThrows(QaQueue.RejectedException.class,
                () -> q.enqueue(new QaQueue.QaRequest("u4", IncomingMessage.of("wecom", "u4", "m1"))));
        assertTrue(full.getMessage().contains("queue full (3/3)"));
        assertEquals(2, q.metrics().totalRejected());
        q.stop();
    }

    @Test
    void removeCancelsQueuedRequestAndFreesUserSlot() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch firstSeen = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        QaQueue q = new QaQueue(1, 5, 3, req -> {
            firstSeen.countDown();
            try {
                block.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            processed.incrementAndGet();
        });
        q.start();
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "busy")));
        assertTrue(firstSeen.await(5, TimeUnit.SECONDS), "worker 应该取走首个请求");
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "queued")));
        assertEquals(1, q.metrics().depth());
        assertTrue(q.remove("u1"), "Remove 应命中排队请求");
        assertEquals(0, q.metrics().depth());
        // Remove 之后同一用户的限额已释放
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "again")));
        block.countDown();
        q.stop();
    }

    @Test
    void workerDrainsQueueAndExitsOnStop() throws Exception {
        CountDownLatch done = new CountDownLatch(2);
        QaQueue q = new QaQueue(2, 5, 3, req -> done.countDown());
        q.start();
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "a")));
        q.enqueue(new QaQueue.QaRequest("u2", IncomingMessage.of("wecom", "u2", "b")));
        assertTrue(done.await(5, TimeUnit.SECONDS), "两个请求都应被处理");
        q.stop();
        assertEquals(2, q.metrics().totalProcessed());
    }

    @Test
    void cancelWhileQueuedCountsAsTimeout() throws Exception {
        CountDownLatch block = new CountDownLatch(1);
        CountDownLatch firstSeen = new CountDownLatch(1);
        AtomicInteger processed = new AtomicInteger();
        QaQueue q = new QaQueue(1, 5, 3, req -> {
            firstSeen.countDown();
            try {
                block.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            processed.incrementAndGet();
        });
        q.start();
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "busy")));
        assertTrue(firstSeen.await(5, TimeUnit.SECONDS));
        QaQueue.QaRequest queued = new QaQueue.QaRequest("u2", IncomingMessage.of("wecom", "u2", "drop"));
        q.enqueue(queued);
        queued.cancel();
        block.countDown();
        Thread.sleep(200);
        assertEquals(1, q.metrics().totalTimeout(), "取消的排队请求按 timeout 计");
        assertEquals(1, processed.get());
        q.stop();
    }

    // ── Redis 面（跨实例计数 / 全局闸门） ────────────────────────────────────

    /** 假 Redis：计数与闸门都在内存里，可预置状态、可观察配平。 */
    private static final class FakeRedis implements QaQueue.RedisPort {
        final Map<String, Integer> counters = new ConcurrentHashMap<>();
        final AtomicInteger gateCount = new AtomicInteger();

        @Override
        public Long incrWithTtl(String key, int ttlSeconds) {
            return (long) counters.merge(key, 1, Integer::sum);
        }

        @Override
        public void decr(String key) {
            counters.merge(key, -1, Integer::sum);
        }

        @Override
        public boolean tryAcquireGlobalGate(String key, int maxWorkers, int ttlSeconds) {
            int c = gateCount.incrementAndGet();
            if (c <= maxWorkers) {
                return true;
            }
            gateCount.decrementAndGet();
            return false;
        }

        @Override
        public void releaseGlobalGate(String key) {
            gateCount.decrementAndGet();
        }

        int count(String key) {
            return counters.getOrDefault(key, 0);
        }
    }

    @Test
    void globalPerUserLimitRejectsWhenRedisCounterExceeds() {
        FakeRedis redis = new FakeRedis();
        String key = ImRedisKeys.QUEUE_USER_PREFIX + "u1";
        redis.counters.put(key, 3);   // 其他实例已排队 3（maxPerUser=3）
        QaQueue q = new QaQueue(1, 5, 3, req -> {
        }, redis, 0, null);

        QaQueue.RejectedException e = assertThrows(QaQueue.RejectedException.class,
                () -> q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "m"))));
        assertTrue(e.getMessage().contains("global per-user queue limit reached (4/3)"));
        // 拒绝时回滚自增（4 → 3，不吞别人的额度）
        assertEquals(3, redis.count(key));
        q.stop();
    }

    @Test
    void enqueueReleasesGlobalCountOnQueueFull() {
        FakeRedis redis = new FakeRedis();
        QaQueue q = new QaQueue(1, 1, 3, req -> {
        }, redis, 0, null);
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "a")));
        assertEquals(1, redis.count(ImRedisKeys.QUEUE_USER_PREFIX + "u1"));

        // u2 队满被拒：自增过的计数必须回滚
        assertThrows(QaQueue.RejectedException.class,
                () -> q.enqueue(new QaQueue.QaRequest("u2", IncomingMessage.of("wecom", "u2", "b"))));
        assertEquals(0, redis.count(ImRedisKeys.QUEUE_USER_PREFIX + "u2"));
        q.stop();
    }

    @Test
    void workerReleasesGlobalCountAfterProcessing() throws Exception {
        FakeRedis redis = new FakeRedis();
        CountDownLatch done = new CountDownLatch(1);
        QaQueue q = new QaQueue(1, 5, 3, req -> done.countDown(), redis, 0, null);
        q.start();
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "a")));
        assertTrue(done.await(5, TimeUnit.SECONDS));

        // worker 在 handler 之后的 finally 里释放计数：轮询等它归零
        String key = ImRedisKeys.QUEUE_USER_PREFIX + "u1";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (redis.count(key) != 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(0, redis.count(key));
        assertEquals(0, redis.gateCount.get());
        q.stop();
    }

    @Test
    void globalGateLimitsConcurrentHandlers() throws Exception {
        FakeRedis redis = new FakeRedis();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);
        QaQueue q = new QaQueue(2, 5, 3, req -> {
            maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            active.decrementAndGet();
            done.countDown();
        }, redis, 1, null);   // 跨实例并发上限 1
        q.start();
        q.enqueue(new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "a")));
        q.enqueue(new QaQueue.QaRequest("u2", IncomingMessage.of("wecom", "u2", "b")));

        assertTrue(done.await(10, TimeUnit.SECONDS), "两个请求都应在闸门轮转后完成");
        assertEquals(1, maxActive.get(), "全局闸门=1 时 handler 不得并发");
        q.stop();
    }

    @Test
    void globalGateWaitCancelledDropsRequest() throws Exception {
        FakeRedis redis = new FakeRedis();
        redis.gateCount.set(1);   // 模拟其他实例占满（上限 1）
        AtomicInteger handled = new AtomicInteger();
        QaQueue q = new QaQueue(1, 5, 3, req -> handled.incrementAndGet(), redis, 1, null);
        q.start();
        QaQueue.QaRequest req = new QaQueue.QaRequest("u1", IncomingMessage.of("wecom", "u1", "a"));
        q.enqueue(req);
        Thread.sleep(150);        // worker 已取走并进入闸门等待
        req.cancel();
        Thread.sleep(800);        // 跨过一个 500ms 重试周期

        assertEquals(0, handled.get(), "等待中被取消的请求不应进入 handler");
        assertEquals(1, q.metrics().totalTimeout(), "按 timeout 计");
        q.stop();
    }
}
