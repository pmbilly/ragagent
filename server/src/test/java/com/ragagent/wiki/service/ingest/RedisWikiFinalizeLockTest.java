package com.ragagent.wiki.service.ingest;

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
 * {@link RedisWikiFinalizeLock} 的真 Redis 语义：独占往返（ACQUIRED/BUSY）、
 * 空 KB 拒绝、Redis 故障 fail-CLOSED。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisWikiFinalizeLockTest {

    private EmbeddedRedis redis;
    private RedisWikiFinalizeLock lock;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
        lock = new RedisWikiFinalizeLock(redis.template());
    }

    @AfterEach
    void tearDown() {
        if (lock != null) {
            lock.release("kb-1");   // 清掉可能挂着的续期任务
        }
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    void acquireBusyThenRecoverAfterRelease() {
        assertThat(lock.tryAcquire("kb-1")).isEqualTo(WikiFinalizeLock.AcquireResult.ACQUIRED);
        assertThat(lock.tryAcquire("kb-1"))
                .as("第二个持有者（跨实例语义）得到 BUSY")
                .isEqualTo(WikiFinalizeLock.AcquireResult.BUSY);

        lock.release("kb-1");
        assertThat(redis.template().hasKey(WikiFinalizeLock.lockKey("kb-1"))).isFalse();
        assertThat(lock.tryAcquire("kb-1"))
                .as("释放后可再取").isEqualTo(WikiFinalizeLock.AcquireResult.ACQUIRED);
    }

    @Test
    void blankKbIsBusy() {
        assertThat(lock.tryAcquire("")).isEqualTo(WikiFinalizeLock.AcquireResult.BUSY);
        assertThat(lock.tryAcquire(null)).isEqualTo(WikiFinalizeLock.AcquireResult.BUSY);
    }

    @Test
    void redisErrorFailsClosed() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("boom"));

        RedisWikiFinalizeLock brokenLock = new RedisWikiFinalizeLock(broken);
        assertThat(brokenLock.tryAcquire("kb-1"))
                .as("无锁放行会让两个 finalize 重复重建索引页 → 必须 fail-closed")
                .isEqualTo(WikiFinalizeLock.AcquireResult.FAILED);
    }
}
