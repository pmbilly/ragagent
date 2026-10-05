package com.ragagent.llm.limiter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 分布式（Redis）并发信号量：按 key（通常 model ID）限制<b>跨实例</b>在途调用数。
 *
 * <p>实现是"自愈租约 ZSET"，逐行对齐 Go {@code internal/models/limiter/limiter.go}：</p>
 * <ul>
 *   <li>每个持槽 = ZSET 成员（唯一 token），score = 租约到期时刻；</li>
 *   <li>取槽脚本一次原子完成「清过期租约 → 计数 → 未满则登记并给键续 TTL」；</li>
 *   <li>心跳每 ttl/3（默认 10 秒）续租，并同步推 ZSET 键自身 TTL——否则长期饱和、
 *       无换手的信号量会在 ttl*2 后整键过期、放行超限；</li>
 *   <li>崩溃进程持有的租约自然过期（默认 30 秒）后被回收；</li>
 *   <li><b>所有后端错误 fail OPEN</b>：限流器/Redis 故障绝不允许阻断模型流量。</li>
 * </ul>
 *
 * <p>等待者以 {@code pollInterval}（默认 200ms）轮询取槽；等待中被<b>线程中断</b>
 * 即 fail open（对齐 Go 的 ctx.Done → fail open，Java 的取消语义是中断）。</p>
 *
 * <p><b>装配</b>：普通类（<b>不是</b> {@code @Component}），由
 * {@code config.ModelConcurrencyGovernorWiring} 在
 * {@code llm.limiter.redis-enabled=true} 时装配；缺省装配 {@link LocalLimiter}。</p>
 */
public class RedisLimiter implements ModelConcurrencyLimiter, RuntimeInspectable {

    private static final Logger log = LoggerFactory.getLogger(RedisLimiter.class);

    /** 崩溃恢复窗口（不是请求超时）：活跃调用每 ttl/3 续租，长请求也保得住槽位。 */
    static final long DEFAULT_LEASE_TTL_MILLIS = 30_000L;

    /** 等待者重试间隔：够小保持灵敏，够大避免争用时打爆 Redis。 */
    static final long DEFAULT_POLL_MILLIS = 200L;

    /** 信号量 ZSET 键前缀（与 Go 的 {@code keyPrefix} 同值）。 */
    static final String KEY_PREFIX = "weknora:modelsem:";

    /**
     * 取槽脚本：清过期 → 计数 → 未满则登记（score=到期时刻）并续键 TTL。
     * KEYS[1]=信号量键；ARGV[1]=now 毫秒；ARGV[2]=limit；ARGV[3]=token；ARGV[4]=ttl 毫秒。
     */
    private static final RedisScript<Long> ACQUIRE = new DefaultRedisScript<>(
            "redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])\n"
            + "local count = redis.call('zcard', KEYS[1])\n"
            + "if count < tonumber(ARGV[2]) then\n"
            + "  redis.call('zadd', KEYS[1], tonumber(ARGV[1]) + tonumber(ARGV[4]), ARGV[3])\n"
            + "  redis.call('pexpire', KEYS[1], tonumber(ARGV[4]) * 2)\n"
            + "  return 1\n"
            + "end\n"
            + "return 0\n",
            Long.class);

    private final StringRedisTemplate template;
    private final long ttlMillis;
    private final long pollMillis;

    /** model ID → 跟踪快照（观测用；与限流判定无关）。 */
    private final ConcurrentHashMap<String, Tracked> tracked = new ConcurrentHashMap<>();

    /**
     * 心跳调度器。共享一个守护线程：单次心跳极轻（每 10 秒一次 ZADD+PEXPIRE），
     * 与 RedisWikiFinalizeLock 的"共享调度器"取舍一致。
     */
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "model-sem-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public RedisLimiter(StringRedisTemplate template) {
        this(template, DEFAULT_LEASE_TTL_MILLIS, DEFAULT_POLL_MILLIS);
    }

    public RedisLimiter(StringRedisTemplate template, long ttlMillis, long pollMillis) {
        this.template = template;
        this.ttlMillis = ttlMillis;
        this.pollMillis = pollMillis;
    }

    @Override
    public Release acquire(String key, int limit) {
        if (limit <= 0 || key == null || key.isEmpty()) {
            return Release.NOOP;
        }
        String zkey = KEY_PREFIX + key;
        Tracked t = tracked.computeIfAbsent(key, k -> new Tracked());
        t.limit.set(limit);
        t.waiting.incrementAndGet();
        try {
            String token = UUID.randomUUID().toString();
            while (true) {
                Long res;
                try {
                    res = template.execute(ACQUIRE, List.of(zkey),
                            Long.toString(System.currentTimeMillis()),
                            Integer.toString(limit),
                            token,
                            Long.toString(ttlMillis));
                } catch (RuntimeException e) {
                    // Fail open：限流器故障绝不允许阻断模型流量
                    log.warn("[ModelLimiter] acquire failed for key={}, failing open: {}",
                            key, e.toString());
                    return Release.NOOP;
                }
                if (res != null && res == 1L) {
                    return hold(zkey, token);
                }
                if (Thread.currentThread().isInterrupted()) {
                    // 等待被中断 → fail open（保住中断标志，让内层调用自己观察取消）
                    return Release.NOOP;
                }
                try {
                    Thread.sleep(pollMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Release.NOOP;
                }
            }
        } finally {
            t.waiting.decrementAndGet();
        }
    }

    /**
     * 开始心跳（每 ttl/3 续租 + 推键 TTL），返回幂等 release：
     * 停心跳并删除自己的成员。
     */
    private Release hold(String zkey, String token) {
        ScheduledFuture<?> hb = heartbeats.scheduleAtFixedRate(() -> {
            try {
                long now = System.currentTimeMillis();
                template.opsForZSet().add(zkey, token, (double) (now + ttlMillis));
                template.expire(zkey, Duration.ofMillis(ttlMillis * 2));
            } catch (RuntimeException e) {
                // best-effort：刷新失败只让租约提前被回收，上限本身容忍
                log.debug("[ModelLimiter] lease refresh failed for {}: {}", zkey, e.toString());
            }
        }, ttlMillis / 3, ttlMillis / 3, TimeUnit.MILLISECONDS);

        AtomicBoolean released = new AtomicBoolean();
        return () -> {
            if (released.compareAndSet(false, true)) {
                hb.cancel(false);
                try {
                    template.opsForZSet().remove(zkey, token);
                } catch (RuntimeException e) {
                    // 释放失败只让槽位等到租约过期，不阻断调用方
                    log.debug("[ModelLimiter] release failed for {}: {}", zkey, e.toString());
                }
            }
        };
    }

    @Override
    public List<RuntimeStat> runtimeStats() {
        List<RuntimeStat> stats = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (var entry : tracked.entrySet()) {
            String modelId = entry.getKey();
            Tracked t = entry.getValue();
            Long active;
            try {
                // 租约未过期的成员数 = 当前在途持槽（Go 用 ZCount(now+1, +inf)）
                active = template.opsForZSet().count(KEY_PREFIX + modelId,
                        (double) (now + 1), Double.POSITIVE_INFINITY);
            } catch (RuntimeException e) {
                log.warn("[ModelLimiter] runtime stats failed for {}: {}", modelId, e.toString());
                continue;
            }
            stats.add(new RuntimeStat(modelId, t.name.get(),
                    active == null ? 0 : active, t.waiting.get(), t.limit.get()));
        }
        stats.sort(Comparator.comparing(RuntimeStat::modelId));
        return stats;
    }

    @Override
    public void setModelName(String modelId, String name) {
        if (modelId == null || modelId.isEmpty() || name == null || name.isEmpty()) {
            return;
        }
        tracked.computeIfAbsent(modelId, k -> new Tracked()).name.set(name);
    }

    /** 关停心跳线程（测试收尾用）。 */
    public void shutdown() {
        heartbeats.shutdownNow();
    }

    /** 观测快照：limit / waiting / name 都是进程内状态。 */
    private static final class Tracked {
        final AtomicInteger limit = new AtomicInteger();
        final AtomicInteger waiting = new AtomicInteger();
        final AtomicReference<String> name = new AtomicReference<>("");
    }

    /** 仅供测试：暴露心跳周期语义（ttl/3）。 */
    static long heartbeatPeriodMillis(long ttlMillis) {
        return ttlMillis / 3;
    }
}
