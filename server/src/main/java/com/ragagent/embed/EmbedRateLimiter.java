package com.ragagent.embed;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * embed 公开面的滑动窗口限流器（Redis 优先 + 进程内回落）。
 *
 * <p><b>两条路径</b>：</p>
 * <ul>
 *   <li>{@code embed.redis-enabled=true}（多副本部署）→ 判定走 Redis ZSET 滑窗
 *       （Lua 原子：清过期 → 计数 → 未满登记），预算跨实例共享；Redis 故障时
 *       <b>回落</b>进程内滑窗；</li>
 *   <li>缺省（单实例）→ 只有进程内滑窗，判定与接线前完全一致。</li>
 * </ul>
 *
 * <p><b>窗口维度</b>：Redis 键与本地桶键都带窗口长度
 * （{@code embed:ratelimit:{windowMs}:{key}}）——同 key 的 1 分钟窗与 24 小时窗
 * 互不污染。</p>
 *
 * <p><b>成员唯一性</b>：ZSET 成员为 {@code nowMs-序号}——防同一毫秒多次命中时
 * 共用成员互相覆盖、同毫秒连击少计一次。</p>
 */
@Component
public class EmbedRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(EmbedRateLimiter.class);

    private static final long CLEANUP_EVERY_CALLS = 4096;

    /** Redis 键前缀（其后拼窗口毫秒与 key）。 */
    static final String REDIS_KEY_PREFIX = "embed:ratelimit:";

    /**
     * 滑窗判定脚本：
     * 清过期成员 → 计数 → 未满则登记。
     * KEYS[1]=键；ARGV[1]=now 毫秒；ARGV[2]=窗口毫秒；ARGV[3]=max；ARGV[4]=member。
     */
    private static final RedisScript<Long> ALLOW = new DefaultRedisScript<>(
            "redis.call('zremrangebyscore', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2]))\n"
            + "local count = redis.call('zcard', KEYS[1])\n"
            + "if count < tonumber(ARGV[3]) then\n"
            + "  redis.call('zadd', KEYS[1], ARGV[1], ARGV[4])\n"
            + "  redis.call('pexpire', KEYS[1], tonumber(ARGV[2]) + 1000)\n"
            + "  return 1\n"
            + "end\n"
            + "return 0\n",
            Long.class);

    /** Redis 面缺席（单实例 / 未开开关）时为 null，全部走本地滑窗。 */
    private final StringRedisTemplate template;

    /** 限流成员序号：防同一毫秒多次命中共用 ZSET 成员。 */
    private final AtomicLong memberSeq = new AtomicLong();

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private long calls;

    @Autowired
    public EmbedRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplates,
            @Value("${embed.redis-enabled:false}") boolean redisEnabled) {
        if (!redisEnabled) {
            this.template = null;
            return;
        }
        StringRedisTemplate t = redisTemplates.getIfAvailable();
        if (t == null) {
            throw new IllegalStateException(
                    "embed.redis-enabled=true but no Redis connection is configured");
        }
        // 启动即验：配置成 Redis 却连不上时不静默退化（同 im/wiki 开关的口径）
        try (var connection = t.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "embed.redis-enabled=true but Redis is not reachable: " + e.getMessage(), e);
        }
        this.template = t;
    }

    /** 供测试：直接指定后端（null = 纯本地滑窗，不经过开关）。 */
    EmbedRateLimiter(StringRedisTemplate template) {
        this.template = template;
    }

    /** 窗口内命中数 &lt; max 才放行并记录本次。 */
    public boolean allow(String key, long windowMillis, int max) {
        if (max <= 0) {
            return true;
        }
        String windowKey = windowMillis + ":" + key;
        if (template != null) {
            Boolean viaRedis = allowRedis(windowKey, windowMillis, max);
            if (viaRedis != null) {
                return viaRedis;
            }
            // Redis 故障 → 回落进程内滑窗
        }
        return allowLocal(windowKey, windowMillis, max);
    }

    /** Redis 路径：true/false = 判定结果；null = 后端故障（调用方回落本地）。 */
    private Boolean allowRedis(String windowKey, long windowMillis, int max) {
        long now = System.currentTimeMillis();
        String member = now + "-" + memberSeq.incrementAndGet();
        try {
            Long result = template.execute(ALLOW, List.of(REDIS_KEY_PREFIX + windowKey),
                    String.valueOf(now), String.valueOf(windowMillis),
                    String.valueOf(max), member);
            if (result == null) {
                return null;
            }
            return result == 1L;
        } catch (RuntimeException e) {
            log.warn("embed rate limit: Redis failed for {}: {} (falling back to local)",
                    windowKey, e.toString());
            return null;
        }
    }

    private synchronized boolean allowLocal(String windowKey, long windowMillis, int max) {
        if (++calls % CLEANUP_EVERY_CALLS == 0) {
            sweepAll(windowMillis);
        }
        long now = System.currentTimeMillis();
        Deque<Long> deque = hits.computeIfAbsent(windowKey, k -> new ArrayDeque<>());
        while (!deque.isEmpty() && deque.peekFirst() <= now - windowMillis) {
            deque.pollFirst();
        }
        if (deque.size() < max) {
            deque.addLast(now);
            return true;
        }
        return false;
    }

    private void sweepAll(long windowMillis) {
        long now = System.currentTimeMillis();
        hits.values().removeIf(deque -> {
            while (!deque.isEmpty() && deque.peekFirst() <= now - windowMillis) {
                deque.pollFirst();
            }
            return deque.isEmpty();
        });
    }

    /** 窗口常量。 */
    public static final long MINUTE_MILLIS = 60_000L;
    public static final long DAY_MILLIS = 24L * 60 * 60_000L;

    /** 全局每分钟预算 = per-IP × 20，下限 120。 */
    public static int globalPerMinute(int perIp) {
        int budget = perIp * 20;
        return Math.max(budget, 120);
    }

    /** 仅测试用：清空窗口。 */
    public void reset() {
        hits.clear();
        calls = 0;
    }
}
