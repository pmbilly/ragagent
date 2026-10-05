package com.ragagent.im.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 有界、按用户限额的 QA 请求队列 + 固定 worker 池（虚拟线程 worker，锁/条件协调）。
 *
 * <p><b>Redis 面</b>：{@link RedisPort} 缺席时每用户限额与并发只在进程内生效
 * （单实例形态）；接入后启用跨实例全局 per-user 计数与全局并发闸门
 * （对齐 Go internal/im/qaqueue.go 的 redis 分支）。
 * 有 Redis 时本地计数不再维护/检查——全局计数已覆盖（Go 同一口径）。</p>
 */
public final class QaQueue {

    private static final Logger log = LoggerFactory.getLogger(QaQueue.class);

    /** 队列里最多排队数。 */
    public static final int DEFAULT_MAX_QUEUE_SIZE = 50;
    /** 单用户最多排队数。 */
    public static final int DEFAULT_MAX_PER_USER = 3;
    /** 默认并发 worker 数。 */
    public static final int DEFAULT_WORKERS = 5;
    /** 请求在队列里最多等这么久。 */
    public static final long QUEUE_TIMEOUT_SECONDS = 60;
    /** 全局 per-user 计数的 TTL（Go {@code redisQueueUserTTL}）：实例崩溃后计数自愈。 */
    static final int REDIS_QUEUE_USER_TTL_SECONDS = 300;
    /** 全局闸门计数器的 TTL（Go {@code globalGateTTL}）：全体实例崩溃后的安全网。 */
    static final int GLOBAL_GATE_TTL_SECONDS = 300;
    /** 全局闸门满时的重试间隔（Go {@code globalGateRetryInterval}）。 */
    static final long GLOBAL_GATE_RETRY_MILLIS = 500;

    /** 可选的 Redis 面（跨实例部署接入；单实例传 null）。 */
    public interface RedisPort {
        /** INCR+EXPIRE；返回自增后计数；Redis 故障返回 null（跳过全局检查）。 */
        Long incrWithTtl(String key, int ttlSeconds);

        /** DECR。 */
        void decr(String key);

        /**
         * 全局并发闸门（Lua CAS：INCR+PEXPIRE，超限回滚）。拿到槽位返回 true；
         * 超限返回 false；Redis 故障返回 true（跳过全局限制，避免阻塞 worker）。
         */
        boolean tryAcquireGlobalGate(String key, int maxWorkers, int ttlSeconds);

        /** 释放全局槽位（DECR）。 */
        void releaseGlobalGate(String key);
    }

    /** 排队的 QA 请求。 */
    public static final class QaRequest {
        final String userKey;
        final long enqueuedAtNanos = System.nanoTime();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        public final IncomingMessage msg;

        /** 业务束（ImService.QaTask；队列层不解释）。 */
        private Object attach;

        public QaRequest(String userKey, IncomingMessage msg) {
            this.userKey = userKey;
            this.msg = msg;
        }

        public <T> T attach() {
            @SuppressWarnings("unchecked")
            T t = (T) attach;
            return t;
        }

        public void attach(Object attach) {
            this.attach = attach;
        }

        public String userKey() {
            return userKey;
        }

        /** 取消即超时/剔除。 */
        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            cancelled.set(true);
        }
    }

    /** 可观测队列状态。 */
    public record QueueMetrics(int depth, long activeWorkers, long totalEnqueued,
            long totalProcessed, long totalRejected, long totalTimeout) {
    }

    private final ReentrantLock mu = new ReentrantLock();
    private final Condition notEmpty = mu.newCondition();
    private final List<QaRequest> queue = new ArrayList<>();
    private final int maxSize;
    private final int maxPerUser;
    private final int workers;
    /** userKey → 排队数（未接 Redis 时的本地计数）。 */
    private final Map<String, Integer> perUser = new HashMap<>();
    private boolean closed;

    private final RedisPort redis;
    /** 跨实例并发上限（0=不限）；仅在 redis 接入时生效。 */
    private final int globalMaxWorkers;

    // 指标（atomic 计数器）。
    private final AtomicLong activeWorkers = new AtomicLong();
    private final AtomicLong totalEnqueued = new AtomicLong();
    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalRejected = new AtomicLong();
    private final AtomicLong totalTimeout = new AtomicLong();

    private final java.util.function.Consumer<QaRequest> handler;
    /** 超时兜底回复的送达通道。 */
    private final java.util.function.BiConsumer<QaRequest, ReplyMessage> replySink;
    private final List<Thread> workerThreads = new ArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);

    public QaQueue(int workers, int maxSize, int maxPerUser,
            java.util.function.Consumer<QaRequest> handler) {
        this(workers, maxSize, maxPerUser, handler, null, 0, null);
    }

    public QaQueue(int workers, int maxSize, int maxPerUser,
            java.util.function.Consumer<QaRequest> handler, RedisPort redis, int globalMaxWorkers,
            java.util.function.BiConsumer<QaRequest, ReplyMessage> replySink) {
        ((ArrayList<QaRequest>) this.queue).ensureCapacity(maxSize);
        this.maxSize = maxSize;
        this.maxPerUser = maxPerUser;
        this.workers = workers;
        this.handler = handler;
        this.redis = redis;
        this.globalMaxWorkers = globalMaxWorkers;
        this.replySink = replySink;
    }

    /** 启动 worker 线程；指标周期日志随 stop 一并退出。 */
    public synchronized void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        for (int i = 0; i < workers; i++) {
            int id = i;
            Thread t = Thread.ofVirtual().name("im-qa-worker-" + id).unstarted(() -> runWorker(id));
            workerThreads.add(t);
            t.start();
        }
    }

    /** 通知所有 worker 在排空后退出。 */
    public void stop() {
        mu.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            mu.unlock();
        }
    }

    /**
     * 入队。返回 0 基位置；队满或超每用户限额抛 {@link RejectedException}。
     */
    public int enqueue(QaRequest req) {
        // 有 Redis：先查全局 per-user 计数（在本地锁之前，对齐 Go），
        // 超限即拒并回滚自增；Redis 故障（null）跳过检查、回落到本地限额。
        if (redis != null) {
            Long count = redis.incrWithTtl(ImRedisKeys.QUEUE_USER_PREFIX + req.userKey,
                    REDIS_QUEUE_USER_TTL_SECONDS);
            if (count != null && count > maxPerUser) {
                redis.decr(ImRedisKeys.QUEUE_USER_PREFIX + req.userKey);
                totalRejected.incrementAndGet();
                throw new RejectedException(
                        "global per-user queue limit reached (" + count + "/" + maxPerUser + ")");
            }
        }
        mu.lock();
        try {
            if (closed) {
                redisDecrUser(req.userKey);
                throw new RejectedException("queue is closed");
            }
            if (queue.size() >= maxSize) {
                redisDecrUser(req.userKey);
                totalRejected.incrementAndGet();
                throw new RejectedException("queue full (" + queue.size() + "/" + maxSize + ")");
            }
            // 本地计数只在无 Redis 时维护/检查（有 Redis 时全局计数已覆盖，对齐 Go）
            if (redis == null) {
                int mine = perUser.getOrDefault(req.userKey, 0);
                if (mine >= maxPerUser) {
                    totalRejected.incrementAndGet();
                    throw new RejectedException(
                            "per-user queue limit reached (" + mine + "/" + maxPerUser + ")");
                }
            }
            queue.add(req);
            if (redis == null) {
                perUser.merge(req.userKey, 1, Integer::sum);
            }
            totalEnqueued.incrementAndGet();
            notEmpty.signal();
            return queue.size() - 1;
        } finally {
            mu.unlock();
        }
    }

    /** 按 userKey 取消并移除排队请求；有命中返回 true。 */
    public boolean remove(String userKey) {
        mu.lock();
        try {
            for (int i = 0; i < queue.size(); i++) {
                QaRequest req = queue.get(i);
                if (req.userKey.equals(userKey)) {
                    req.cancel();
                    queue.remove(i);
                    decrementPerUser(userKey);
                    redisDecrUser(userKey);
                    return true;
                }
            }
            return false;
        } finally {
            mu.unlock();
        }
    }

    public QueueMetrics metrics() {
        mu.lock();
        int depth;
        try {
            depth = queue.size();
        } finally {
            mu.unlock();
        }
        return new QueueMetrics(depth, activeWorkers.get(), totalEnqueued.get(),
                totalProcessed.get(), totalRejected.get(), totalTimeout.get());
    }

    /** 本地计数只在无 Redis 时维护（有 Redis 时全局计数接管，对齐 Go）。 */
    private void decrementPerUser(String userKey) {
        if (redis != null) {
            return;
        }
        int left = perUser.merge(userKey, -1, Integer::sum);
        if (left <= 0) {
            perUser.remove(userKey);
        }
    }

    /** 释放全局 per-user 计数的一个槽位（Redis 缺席时为空操作，对齐 Go 的无条件调用）。 */
    private void redisDecrUser(String userKey) {
        if (redis != null) {
            redis.decr(ImRedisKeys.QUEUE_USER_PREFIX + userKey);
        }
    }

    private void runWorker(int id) {
        while (true) {
            QaRequest req = dequeue();
            if (req == null) {
                return; // 队列已关
            }
            // 排队期间被取消/超时的请求直接跳过（并释放全局计数）。
            if (req.isCancelled()) {
                totalTimeout.incrementAndGet();
                redisDecrUser(req.userKey);
                continue;
            }
            long waitedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - req.enqueuedAtNanos);
            if (waitedSeconds > QUEUE_TIMEOUT_SECONDS) {
                totalTimeout.incrementAndGet();
                redisDecrUser(req.userKey);
                log.warn("[IM] Queue timeout: user={} waited={}s, discarding",
                        req.msg == null ? "" : req.msg.userId, waitedSeconds);
                if (replySink != null) {
                    try {
                        replySink.accept(req, new ReplyMessage("您的消息等待超时，请重新发送。", false, true));
                    } catch (RuntimeException e) {
                        // 兜底回复发送失败：吞掉（超时路径已无可为）
                    }
                }
                req.cancel();
                continue;
            }

            // 全局并发闸门：满则等待（500ms 轮询），等待中被取消即丢弃。
            if (!acquireGlobalGate(req)) {
                totalTimeout.incrementAndGet();
                redisDecrUser(req.userKey);
                log.warn("[IM] Global gate wait cancelled: worker={} user={}", id,
                        req.msg == null ? "" : req.msg.userId);
                req.cancel();
                continue;
            }

            activeWorkers.incrementAndGet();
            try {
                handler.accept(req);
            } catch (RuntimeException e) {
                log.error("[IM] QA handler panicked: {}", e.getMessage(), e);
            } finally {
                activeWorkers.decrementAndGet();
                releaseGlobalGate();
                redisDecrUser(req.userKey);
            }
            totalProcessed.incrementAndGet();
        }
    }

    /**
     * 全局并发闸门：拿到槽位返回 true；等待中被取消返回 false；
     * 未启用（globalMaxWorkers=0 / 无 Redis）直接放行。
     */
    private boolean acquireGlobalGate(QaRequest req) {
        if (globalMaxWorkers <= 0 || redis == null) {
            return true;
        }
        while (true) {
            if (redis.tryAcquireGlobalGate(ImRedisKeys.GLOBAL_GATE, globalMaxWorkers,
                    GLOBAL_GATE_TTL_SECONDS)) {
                return true;
            }
            if (req.isCancelled()) {
                return false;
            }
            try {
                Thread.sleep(GLOBAL_GATE_RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    /** 释放全局闸门槽位（未启用时空操作）。 */
    private void releaseGlobalGate() {
        if (globalMaxWorkers > 0 && redis != null) {
            redis.releaseGlobalGate(ImRedisKeys.GLOBAL_GATE);
        }
    }

    private QaRequest dequeue() {
        mu.lock();
        try {
            while (queue.isEmpty() && !closed) {
                notEmpty.awaitUninterruptibly();
            }
            if (closed && queue.isEmpty()) {
                return null;
            }
            QaRequest req = queue.remove(0);
            decrementPerUser(req.userKey);
            return req;
        } finally {
            mu.unlock();
        }
    }

    /** 入队被拒（队满/超限/已关）。 */
    public static final class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }
}
