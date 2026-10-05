package com.ragagent.common.taskqueue;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis 任务队列骨架（多实例部署的共享任务表）。
 *
 * <p>被 knowledge / memory / datasource 的队列 Redis 实现复用
 * （wiki 域因有 TaskID 合并与死信语义自成一派，见
 * {@code wiki.service.ingest.RedisWikiIngestTaskQueue}）。</p>
 *
 * <h2>Redis 结构</h2>
 * <ul>
 *   <li>{@code {prefix}:ready}（ZSET）：成员 = 任务 JSON，score = 可执行时刻；</li>
 *   <li>{@code {prefix}:proc}（ZSET）：成员 = 任务 JSON，score = 租约到期时刻。</li>
 * </ul>
 *
 * <h2>语义</h2>
 * <ul>
 *   <li><b>延迟</b>：ready 的 score 即最早可执行时刻（提交时给未来即可）；</li>
 *   <li><b>崩溃回收</b>：执行中的任务是带租约的（默认 3 分钟，每 1 分钟续期）；
 *       取任务的实例崩溃后，租约过期即被其它实例回收重投——至少一次语义；</li>
 *   <li><b>原子性</b>：取任务 / 回收 / 重排 / 续租 / 终态清理全部走 Lua，
 *       多实例并发下不重不漏；</li>
 *   <li><b>调度</b>：每实例一个 dispatcher 线程按 {@code dispatchInterval} 轮询，
 *       到期成员交给调用方的 sink（通常投进本地虚拟线程执行池）。</li>
 * </ul>
 */
public final class RedisTaskQueueCore {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskQueueCore.class);

    /** 执行租约：取走任务的实例崩溃后，最多挂这么久被回收。 */
    public static final long DEFAULT_LEASE_MILLIS = 180_000L;

    /** 租约续期间隔（必须远小于租约，容忍偶发漏续期）。 */
    public static final long DEFAULT_RENEW_MILLIS = 60_000L;

    /** 调度轮询间隔。 */
    public static final long DEFAULT_DISPATCH_INTERVAL_MILLIS = 500L;

    /** 单轮回收上限。 */
    static final int RECLAIM_BATCH = 100;

    /** 取一个到期任务：ready → proc（原子；无则返回 null）。 */
    private static final RedisScript<String> CLAIM = new DefaultRedisScript<>("""
            local due = redis.call('zrangebyscore', KEYS[1], '-inf', ARGV[1], 'limit', 0, 1)
            if #due == 0 then return false end
            redis.call('zrem', KEYS[1], due[1])
            redis.call('zadd', KEYS[2], ARGV[2], due[1])
            return due[1]
            """, String.class);

    /** 回收租约过期的执行中任务：proc → ready（now 立即可执行）。 */
    private static final RedisScript<Long> RECLAIM = new DefaultRedisScript<>("""
            local stale = redis.call('zrangebyscore', KEYS[1], '-inf', ARGV[1], 'limit', 0, ARGV[2])
            for i = 1, #stale do
              redis.call('zrem', KEYS[1], stale[i])
              redis.call('zadd', KEYS[2], ARGV[1], stale[i])
            end
            return #stale
            """, Long.class);

    /**
     * 重排：仅当旧成员仍在 proc（未被回收）时，原子替换为新成员进 ready；
     * 已被回收（返回 0）则放弃——任务已在 ready 里等着，不得重复入队。
     */
    private static final RedisScript<Long> REQUEUE = new DefaultRedisScript<>("""
            if redis.call('zscore', KEYS[1], ARGV[1]) == false then return 0 end
            redis.call('zrem', KEYS[1], ARGV[1])
            redis.call('zadd', KEYS[2], ARGV[2], ARGV[3])
            return 1
            """, Long.class);

    /** 续租：仅当成员仍在 proc 时更新 score（不在则不复活幽灵成员）。 */
    private static final RedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('zscore', KEYS[1], ARGV[1]) == false then return 0 end
            redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])
            return 1
            """, Long.class);

    /** 终态清理：把成员移出 proc。 */
    private static final RedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            redis.call('zrem', KEYS[1], ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate template;
    private final String readyKey;
    private final String procKey;
    private final long leaseMillis;
    private final long renewMillis;
    private final long dispatchIntervalMillis;

    /** 调度线程：回收过期 + 取到期任务交给 sink（本身不跑业务代码）。 */
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "taskqueue-dispatcher");
        t.setDaemon(true);
        return t;
    });

    public RedisTaskQueueCore(StringRedisTemplate template, String keyPrefix) {
        this(template, keyPrefix, DEFAULT_LEASE_MILLIS, DEFAULT_RENEW_MILLIS,
                DEFAULT_DISPATCH_INTERVAL_MILLIS);
    }

    public RedisTaskQueueCore(StringRedisTemplate template, String keyPrefix,
            long leaseMillis, long renewMillis, long dispatchIntervalMillis) {
        this.template = template;
        this.readyKey = keyPrefix + ":ready";
        this.procKey = keyPrefix + ":proc";
        this.leaseMillis = leaseMillis;
        this.renewMillis = renewMillis;
        this.dispatchIntervalMillis = dispatchIntervalMillis;
    }

    public String readyKey() {
        return readyKey;
    }

    public String procKey() {
        return procKey;
    }

    public long leaseMillis() {
        return leaseMillis;
    }

    /** 提交一个成员（member 为调用方序列化的 JSON）；Redis 故障时抛出让调用方感知。 */
    public void submit(String member, long readyAtMillis) {
        template.opsForZSet().add(readyKey, member, (double) readyAtMillis);
    }

    /** 执行中成员的续期（best-effort；漏续期只让任务被提前回收重投）。 */
    public void renew(String member) {
        try {
            template.execute(RENEW, List.of(procKey),
                    member, Long.toString(System.currentTimeMillis() + leaseMillis));
        } catch (RuntimeException e) {
            log.debug("task queue: lease renew failed: {}", e.toString());
        }
    }

    /** 终态清理（best-effort；失败时 proc 成员等租约回收后重跑一次，由业务侧的认领兜住）。 */
    public void complete(String member) {
        try {
            template.execute(COMPLETE, List.of(procKey), member);
        } catch (RuntimeException e) {
            log.warn("task queue: complete failed: {}", e.toString());
        }
    }

    /** 重排（原子；返回 false = 已被回收，调用方不得重复入队）。 */
    public boolean requeue(String oldMember, String nextMember, long readyAtMillis) {
        try {
            Long ok = template.execute(REQUEUE, List.of(procKey, readyKey),
                    oldMember, Long.toString(readyAtMillis), nextMember);
            return ok != null && ok == 1L;
        } catch (RuntimeException e) {
            // 重排失败：proc 中的成员等租约回收重跑（重试计数偏差一次，可接受）
            log.warn("task queue: requeue failed: {}", e.toString());
            return false;
        }
    }

    /**
     * 启动调度循环：回收过期 → 取到期成员 → 交给 {@code sink}（通常投进本地执行池）。
     * 每实例调用一次。
     */
    public void start(Consumer<String> sink) {
        dispatcher.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    reclaimStale();
                    String member;
                    while ((member = claimDue()) != null) {
                        sink.accept(member);
                    }
                } catch (Throwable t) {
                    log.warn("task queue: dispatch loop error: {}", t.toString());
                }
                try {
                    Thread.sleep(dispatchIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    /** 停掉调度线程（bean 销毁时由容器推断的 shutdown 调用）。 */
    public void shutdown() {
        dispatcher.shutdownNow();
    }

    private String claimDue() {
        long now = System.currentTimeMillis();
        return template.execute(CLAIM, List.of(readyKey, procKey),
                Long.toString(now), Long.toString(now + leaseMillis));
    }

    private void reclaimStale() {
        Long n = template.execute(RECLAIM, List.of(procKey, readyKey),
                Long.toString(System.currentTimeMillis()), Integer.toString(RECLAIM_BATCH));
        if (n != null && n > 0) {
            log.warn("task queue: reclaimed {} stale task(s) from crashed worker(s)", n);
        }
    }

    /** 心跳周期（租约的三分之一；暴露给调用方做续期调度）。 */
    public long renewIntervalMillis() {
        return renewMillis;
    }

    /** 秒表工具：把秒转毫秒（调用方重试延迟用）。 */
    public static long secondsToMillis(long seconds) {
        return TimeUnit.SECONDS.toMillis(Math.max(0, seconds));
    }
}
