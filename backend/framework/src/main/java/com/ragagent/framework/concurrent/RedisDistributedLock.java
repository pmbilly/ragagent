package com.ragagent.framework.concurrent;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * {@link DistributedLock} 的 Redis 实现。
 *
 * <p>取锁：SET key token NX EX ttl（token 是本次持有的唯一标识）。
 * 释放：Lua 脚本 {@code if get(key)==token then del(key)}——TTL 到期后锁被
 * 别的持有者重建时，原持有者的 close 不会删掉新持有者的锁。</p>
 *
 * <p>续期：{@code EXPIRE key ttl}（长任务防锁过期；不校验 token——续期只延寿命，
 * 即使锁已易主也只是把别人的锁续长，与旧 RedisWikiFinalizeLock 的续期语义一致）。</p>
 */
public class RedisDistributedLock implements DistributedLock {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate template;

    public RedisDistributedLock(StringRedisTemplate template) {
        this.template = template;
    }

    @Override
    public Optional<LockHandle> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = template.opsForValue().setIfAbsent(key, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        return Optional.of(new RedisLockHandle(key, token, ttl));
    }

    @Override
    public boolean markOnce(String key, String value, Duration ttl) {
        return Boolean.TRUE.equals(template.opsForValue().setIfAbsent(key, value, ttl));
    }

    private class RedisLockHandle implements LockHandle {

        private final String key;
        private final String token;
        private final Duration ttl;

        RedisLockHandle(String key, String token, Duration ttl) {
            this.key = key;
            this.token = token;
            this.ttl = ttl;
        }

        @Override
        public void renew() {
            template.expire(key, ttl);
        }

        @Override
        public void close() {
            template.execute(new DefaultRedisScript<>(UNLOCK_LUA, Boolean.class),
                    Collections.singletonList(key), token);
        }
    }
}
