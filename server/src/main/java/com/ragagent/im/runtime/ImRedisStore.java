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

    private final StringRedisTemplate template;

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
}
