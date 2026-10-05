package com.ragagent.im.runtime;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * IM 域 Redis 面的 Spring 实现（{@link QaQueue.RedisPort}）。
 *
 * <p>故障语义与 Go 版一致：任何 Redis 异常都不阻塞主流程——
 * 计数面按「跳过全局检查」处理（{@code null} / {@code true}），
 * 由键 TTL 自愈；键名见 {@link ImRedisKeys}（与 Go 版共用字面量）。</p>
 */
public class ImRedisStore implements QaQueue.RedisPort {

    private static final Logger log = LoggerFactory.getLogger(ImRedisStore.class);

    /**
     * 全局闸门 CAS：INCR + PEXPIRE 后判断，超限回滚。
     * KEYS[1]=闸门键；ARGV[1]=并发上限；ARGV[2]=TTL 毫秒。拿到槽位返回 1，超限返回 0。
     */
    private static final String GLOBAL_GATE_LUA = """
            local count = redis.call('INCR', KEYS[1])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            if count <= tonumber(ARGV[1]) then
              return 1
            end
            redis.call('DECR', KEYS[1])
            return 0
            """;

    private static final RedisScript<Long> GLOBAL_GATE =
            new DefaultRedisScript<>(GLOBAL_GATE_LUA, Long.class);

    /**
     * 滑动窗口限流（对齐 Go internal/ratelimit 的脚本）：清过期成员 → 判数量 →
     * 未满则记录本次。KEYS[1]=计数键；ARGV[1]=now 毫秒；ARGV[2]=窗口毫秒；ARGV[3]=上限；
     * ARGV[4]=成员。放行返回 1，超限返回 0。
     */
    private static final String RATE_LIMIT_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local maxReq = tonumber(ARGV[3])
            local member = ARGV[4]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
            local count = redis.call('ZCARD', key)
            if count < maxReq then
              redis.call('ZADD', key, now, member)
              redis.call('PEXPIRE', key, window + 1000)
              return 1
            end
            return 0
            """;

    private static final RedisScript<Long> RATE_LIMIT =
            new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);

    private final StringRedisTemplate template;
    /** 限流成员序号：防同一毫秒多次命中共用 ZSET 成员（Go 用 instanceID+毫秒）。 */
    private final java.util.concurrent.atomic.AtomicLong rateLimitSeq =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * leader 续期（仅当仍持有）：KEYS[1]=锁键；ARGV[1]=instanceId；ARGV[2]=TTL 毫秒。
     * 仍持有并续期成功返回 1，否则 0。
     */
    private static final String RENEW_LEADER_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
              return 1
            end
            return 0
            """;

    private static final RedisScript<Long> RENEW_LEADER =
            new DefaultRedisScript<>(RENEW_LEADER_LUA, Long.class);

    /** leader 释放（仅当持有，CAS DEL）。 */
    private static final String RELEASE_LEADER_LUA = """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """;

    private static final RedisScript<Long> RELEASE_LEADER =
            new DefaultRedisScript<>(RELEASE_LEADER_LUA, Long.class);

    public ImRedisStore(StringRedisTemplate template) {
        this.template = template;
    }

    @Override
    public Long incrWithTtl(String key, int ttlSeconds) {
        try {
            Long count = template.opsForValue().increment(key);
            template.expire(key, Duration.ofSeconds(ttlSeconds));
            return count;
        } catch (DataAccessException e) {
            // 故障：跳过全局检查，回落到本地限额（对齐 Go 的 err → nil）
            log.warn("[IM] Redis INCR failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void decr(String key) {
        try {
            template.opsForValue().decrement(key);
        } catch (DataAccessException e) {
            log.warn("[IM] Redis DECR failed for {}: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean tryAcquireGlobalGate(String key, int maxWorkers, int ttlSeconds) {
        try {
            Long result = template.execute(GLOBAL_GATE, List.of(key),
                    String.valueOf(maxWorkers), String.valueOf(ttlSeconds * 1000L));
            return result == null || result == 1L;
        } catch (DataAccessException e) {
            // 故障：跳过全局限制，避免阻塞 worker（对齐 Go）
            log.warn("[IM] Redis global gate failed (proceeding without limit): {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void releaseGlobalGate(String key) {
        decr(key);
    }

    // ── 跨实例 /stop（stop marker + inflight 映射） ──────────────────────────

    /** 执行前 /stop 标记：写入（TTL 兜底；供尚未创建 assistant message 的请求）。 */
    public void setStopMarker(String userKey, int ttlSeconds) {
        try {
            template.opsForValue().set(ImRedisKeys.STOP_PREFIX + userKey, "1",
                    Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("[IM] Redis SET stop marker failed for {}: {}", userKey, e.getMessage());
        }
    }

    /** 执行前检查并清除 stop 标记（一次性消费）；命中返回 true。 */
    public boolean checkAndClearStopMarker(String userKey) {
        try {
            return Boolean.TRUE.equals(template.delete(ImRedisKeys.STOP_PREFIX + userKey));
        } catch (DataAccessException e) {
            log.warn("[IM] Redis DEL stop marker failed for {}: {}", userKey, e.getMessage());
            return false;
        }
    }

    /** 在途映射：userKey → {@code sessionId:messageId}（跨实例 /stop 查 IDs 用）。 */
    public void storeInflight(String userKey, String sessionId, String messageId, int ttlSeconds) {
        try {
            template.opsForValue().set(ImRedisKeys.INFLIGHT_PREFIX + userKey,
                    sessionId + ":" + messageId, Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException e) {
            log.warn("[IM] Redis SET inflight failed for {}: {}", userKey, e.getMessage());
        }
    }

    /** 清除在途映射。 */
    public void clearInflight(String userKey) {
        try {
            template.delete(ImRedisKeys.INFLIGHT_PREFIX + userKey);
        } catch (DataAccessException e) {
            log.warn("[IM] Redis DEL inflight failed for {}: {}", userKey, e.getMessage());
        }
    }

    /** 读取在途映射（{@code [sessionId, messageId]}）；无则 null。 */
    public String[] loadInflight(String userKey) {
        String val;
        try {
            val = template.opsForValue().get(ImRedisKeys.INFLIGHT_PREFIX + userKey);
        } catch (DataAccessException e) {
            log.warn("[IM] Redis GET inflight failed for {}: {}", userKey, e.getMessage());
            return null;
        }
        if (val == null) {
            return null;
        }
        int sep = val.indexOf(':');
        if (sep <= 0 || sep == val.length() - 1) {
            return null;
        }
        return new String[]{val.substring(0, sep), val.substring(sep + 1)};
    }

    // ── 消息入口面（去重 / 限流） ────────────────────────────────────────────

    /**
     * 去重标记：SET NX EX。新写入返回 true，已存在返回 false；
     * Redis 故障返回 null（调用方按 fail-closed 处理——宁可丢一条可重发的，也不重复跑 LLM）。
     */
    public Boolean setIfAbsent(String key, String value, int ttlSeconds) {
        try {
            return Boolean.TRUE.equals(template.opsForValue()
                    .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds)));
        } catch (DataAccessException e) {
            log.warn("[IM] Redis SETNX failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 滑动窗口限流：放行返回 true，超限返回 false，Redis 故障返回 null
     * （调用方回落到进程内滑窗）。
     */
    public Boolean rateLimitAllow(String key, long windowSeconds, int maxRequests) {
        if (maxRequests <= 0) {
            return true;
        }
        long nowMs = System.currentTimeMillis();
        long windowMs = Math.max(windowSeconds, 1) * 1000L;
        String member = nowMs + "-" + rateLimitSeq.incrementAndGet();
        try {
            Long result = template.execute(RATE_LIMIT, List.of(key),
                    String.valueOf(nowMs), String.valueOf(windowMs),
                    String.valueOf(maxRequests), member);
            return result == null || result == 1L;
        } catch (DataAccessException e) {
            log.warn("[IM] Redis rate limit failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    // ── WS 长连接选主（leader 锁） ──────────────────────────────────────────

    /**
     * 抢 leader 锁（SET NX EX）。拿到返回 true；已被别的实例持有返回 false；
     * Redis 故障也返回 false（本轮不启动、等下一轮重试——对齐 Go 的失败语义）。
     */
    public boolean tryAcquireLeader(String key, String instanceId, int ttlSeconds) {
        try {
            Boolean ok = template.opsForValue()
                    .setIfAbsent(key, instanceId, Duration.ofSeconds(ttlSeconds));
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException e) {
            log.warn("[IM] Redis leader acquire failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    /** 续期（仅当仍持有，Lua GET+PEXPIRE）；仍持有返回 true，丢失或故障返回 false。 */
    public boolean renewLeader(String key, String instanceId, int ttlSeconds) {
        try {
            Long result = template.execute(RENEW_LEADER, List.of(key),
                    instanceId, String.valueOf(ttlSeconds * 1000L));
            return result != null && result == 1L;
        } catch (DataAccessException e) {
            log.warn("[IM] Redis leader renew failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    /** 释放（仅当持有，Lua CAS DEL）。 */
    public void releaseLeader(String key, String instanceId) {
        try {
            template.execute(RELEASE_LEADER, List.of(key), instanceId);
        } catch (DataAccessException e) {
            log.warn("[IM] Redis leader release failed for {}: {}", key, e.getMessage());
        }
    }
}
