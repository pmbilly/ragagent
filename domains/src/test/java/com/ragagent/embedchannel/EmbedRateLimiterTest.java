package com.ragagent.embedchannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link EmbedRateLimiter} 的双路径语义：缺省进程内滑窗（单实例）、
 * Redis 面跨实例共享预算与窗口隔离、Redis 故障回落本地。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class EmbedRateLimiterTest {

    private EmbeddedRedis redis;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    void localOnlyAllowDenyAndZeroMax() {
        EmbedRateLimiter limiter = new EmbedRateLimiter((StringRedisTemplate) null);
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 2)).isTrue();
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 2)).isTrue();
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 2)).isFalse();
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 0))
                .as("max<=0 一律放行").isTrue();
    }

    @Test
    void localWindowsAreIsolated() {
        EmbedRateLimiter limiter = new EmbedRateLimiter((StringRedisTemplate) null);
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 1)).isTrue();
        assertThat(limiter.allow("k", EmbedRateLimiter.DAY_MILLIS, 1))
                .as("同 key 的分钟窗与日窗互不污染").isTrue();
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 1)).isFalse();
    }

    @Test
    void redisPathSharesBudgetAcrossInstances() {
        EmbedRateLimiter a = new EmbedRateLimiter(redis.template());
        EmbedRateLimiter b = new EmbedRateLimiter(redis.template());
        assertThat(a.allow("ch1:1.2.3.4", EmbedRateLimiter.MINUTE_MILLIS, 2)).isTrue();
        assertThat(b.allow("ch1:1.2.3.4", EmbedRateLimiter.MINUTE_MILLIS, 2))
                .as("另一实例的第二命中计入同一预算（2/2）").isTrue();
        assertThat(a.allow("ch1:1.2.3.4", EmbedRateLimiter.MINUTE_MILLIS, 2))
                .as("第三命中跨实例超限").isFalse();
    }

    @Test
    void redisWindowsAreIsolated() {
        EmbedRateLimiter a = new EmbedRateLimiter(redis.template());
        assertThat(a.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 1)).isTrue();
        assertThat(a.allow("k", EmbedRateLimiter.DAY_MILLIS, 1))
                .as("同 key 的分钟窗与日窗互不污染").isTrue();
        assertThat(a.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 1)).isFalse();
    }

    @Test
    void redisFailureFallsBackToLocal() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("boom")).when(broken)
                .execute(org.mockito.ArgumentMatchers.<RedisScript<Object>>any(), anyList(),
                        any(), any(), any(), any());
        EmbedRateLimiter limiter = new EmbedRateLimiter(broken);

        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 1))
                .as("Redis 故障 → 回落本地滑窗（放行）").isTrue();
        assertThat(limiter.allow("k", EmbedRateLimiter.MINUTE_MILLIS, 1))
                .as("本地滑窗继续计数").isFalse();
    }

    /**
     * 回归：成员唯一性必须**跨实例**成立。只带"毫秒+实例内序号"时，两个副本在同一毫秒
     * 会算出同一个 ZSET 成员，{@code zadd} 变成改分 ⇒ 共享预算少计一次
     * （这正是 {@code redisPathSharesBudgetAcrossInstances} 偶发红的原因）。
     */
    @Test
    void redisMembersAreUniqueAcrossInstances() {
        EmbedRateLimiter a = new EmbedRateLimiter(redis.template());
        EmbedRateLimiter b = new EmbedRateLimiter(redis.template());
        long sameMs = System.currentTimeMillis();
        assertThat(a.memberFor(sameMs))
                .as("同毫秒的两实例成员必须不同")
                .isNotEqualTo(b.memberFor(sameMs));
        assertThat(a.memberFor(sameMs))
                .as("同实例同毫秒的连续命中也不能共用成员")
                .isNotEqualTo(a.memberFor(sameMs));
    }
}
