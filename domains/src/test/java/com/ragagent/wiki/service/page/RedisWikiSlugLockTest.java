package com.ragagent.wiki.service.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * {@link RedisWikiSlugLock} 的真 Redis 语义：同 slug 互斥、不同 slug 并行、
 * 交叉持锁独立释放（token 按锁键记账的回归钉桩）、Redis 故障 fail-open。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisWikiSlugLockTest {

    private EmbeddedRedis redis;
    private RedisWikiSlugLock lock;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
        lock = new RedisWikiSlugLock(redis.template());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    void mutualExclusionAndRelease() {
        assertThat(lock.tryLock("kb-1", "entity/a", Duration.ofSeconds(1))).isTrue();
        assertThat(lock.tryLock("kb-1", "entity/a", Duration.ofMillis(150)))
                .as("持有期间再次获取（同线程也不重入）应超时失败").isFalse();

        lock.unlock("kb-1", "entity/a");
        assertThat(redis.template().hasKey(WikiSlugLock.lockKey("kb-1", "entity/a"))).isFalse();
        assertThat(lock.tryLock("kb-1", "entity/a", Duration.ofSeconds(1)))
                .as("释放后可再取").isTrue();
        lock.unlock("kb-1", "entity/a");
    }

    @Test
    void differentSlugsDoNotBlockEachOther() {
        assertThat(lock.tryLock("kb-1", "entity/a", Duration.ofSeconds(1))).isTrue();
        assertThat(lock.tryLock("kb-1", "entity/b", Duration.ofMillis(300)))
                .as("不同 slug 直接可拿，无需等待").isTrue();
        lock.unlock("kb-1", "entity/b");
        lock.unlock("kb-1", "entity/a");
    }

    @Test
    void crossHeldLocksUnlockIndependently() {
        // 回归钉桩：token 按锁键记账——交叉释放必须各删各的键
        //（单值 ThreadLocal 会被后一把锁覆盖，导致第一把漏删、只能等 TTL）
        assertThat(lock.tryLock("kb-1", "entity/a", Duration.ofSeconds(1))).isTrue();
        assertThat(lock.tryLock("kb-1", "entity/b", Duration.ofSeconds(1))).isTrue();

        lock.unlock("kb-1", "entity/a");
        assertThat(redis.template().hasKey(WikiSlugLock.lockKey("kb-1", "entity/a")))
                .as("先持有的锁必须被删（不得被后一把的 token 覆盖）").isFalse();
        lock.unlock("kb-1", "entity/b");
        assertThat(redis.template().hasKey(WikiSlugLock.lockKey("kb-1", "entity/b"))).isFalse();
    }

    @Test
    void unlockWithoutHoldingIsNoOp() {
        lock.unlock("kb-1", "entity/never");   // 未持有 → 安全 no-op，不抛异常
    }

    @Test
    void redisErrorFailsOpen() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("boom"));

        RedisWikiSlugLock brokenLock = new RedisWikiSlugLock(broken);
        assertThat(brokenLock.tryLock("kb-1", "entity/a", Duration.ofMillis(100)))
                .as("Redis 故障必须 fail-open（running unlocked）").isTrue();
    }
}
