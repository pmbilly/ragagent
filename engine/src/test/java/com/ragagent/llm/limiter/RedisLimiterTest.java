package com.ragagent.llm.limiter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link RedisLimiter} 的真 Redis 语义：上限与等待、幂等释放、
 * 跨实例共享、等待可中断（fail open）、心跳续租、Redis 故障 fail open、
 * 观测快照。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisLimiterTest {

    private EmbeddedRedis redis;
    private RedisLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
        limiter = new RedisLimiter(redis.template());
    }

    @AfterEach
    void tearDown() {
        if (limiter != null) {
            limiter.shutdown();
        }
        if (redis != null) {
            redis.close();
        }
    }

    private static String zkey(String modelId) {
        return RedisLimiter.KEY_PREFIX + modelId;
    }

    @Test
    void acquireUpToLimitThenSecondWaitsUntilRelease() throws Exception {
        Release first = limiter.acquire("m1", 1);
        assertThat(first).isNotSameAs(Release.NOOP);

        AtomicReference<Release> second = new AtomicReference<>();
        Thread waiter = Thread.ofVirtual().start(() -> second.set(limiter.acquire("m1", 1)));
        Thread.sleep(350);   // 跨过一个 poll 周期（200ms）：第二个拿不到
        assertThat(second.get()).as("占满时第二个必须等待").isNull();

        first.close();
        waiter.join(3000);
        assertThat(second.get()).as("释放后等待者拿到槽位").isNotNull();
        second.get().close();
    }

    @Test
    void releaseIsIdempotentAndImmediatelyFreesSlot() throws Exception {
        Release first = limiter.acquire("m1", 1);
        first.close();
        first.close();   // 幂等：重复释放不得多放槽位

        AtomicReference<Release> again = new AtomicReference<>();
        Thread t = Thread.ofVirtual().start(() -> again.set(limiter.acquire("m1", 1)));
        t.join(2000);
        assertThat(again.get()).as("释放后槽位立即可用").isNotNull();
        again.get().close();
    }

    @Test
    void capIsSharedAcrossInstances() throws Exception {
        RedisLimiter second = new RedisLimiter(redis.template(), 30_000L, 50L);
        try {
            Release first = limiter.acquire("m1", 1);
            AtomicReference<Release> got = new AtomicReference<>();
            Thread t = Thread.ofVirtual().start(() -> got.set(second.acquire("m1", 1)));
            Thread.sleep(250);
            assertThat(got.get()).as("跨实例共享上限：另一实例占满即等待").isNull();

            first.close();
            t.join(3000);
            assertThat(got.get()).isNotNull();
            got.get().close();
        } finally {
            second.shutdown();
        }
    }

    @Test
    void waitInterruptedFailsOpen() throws Exception {
        Release first = limiter.acquire("m1", 1);
        AtomicReference<Release> got = new AtomicReference<>();
        Thread t = Thread.ofVirtual().start(() -> got.set(limiter.acquire("m1", 1)));
        Thread.sleep(150);   // 让它进入等待
        t.interrupt();
        t.join(2000);
        assertThat(got.get()).as("等待被中断 → fail open（NOOP）").isSameAs(Release.NOOP);
        first.close();
    }

    @Test
    void heartbeatExtendsLease() throws Exception {
        // 短租约（300ms，心跳每 100ms）验证续租把 score 往前推
        RedisLimiter fast = new RedisLimiter(redis.template(), 300L, 50L);
        try {
            Release r = fast.acquire("m1", 1);
            var members = redis.template().opsForZSet().range(zkey("m1"), 0, -1);
            assertThat(members).hasSize(1);
            String token = members.iterator().next();
            Double before = redis.template().opsForZSet().score(zkey("m1"), token);

            Thread.sleep(250);   // 跨过至少两次心跳
            Double after = redis.template().opsForZSet().score(zkey("m1"), token);
            assertThat(after).as("心跳必须把租约推到未来").isGreaterThan(before);
            r.close();
        } finally {
            fast.shutdown();
        }
    }

    @Test
    void redisErrorFailsOpen() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        doThrow(new RuntimeException("boom")).when(broken)
                .execute(org.mockito.ArgumentMatchers.<RedisScript<Object>>any(), anyList(),
                        any(), any(), any(), any());
        RedisLimiter brokenLimiter = new RedisLimiter(broken);
        try {
            assertThat(brokenLimiter.acquire("m1", 5))
                    .as("限流器故障必须放行（fail open）").isSameAs(Release.NOOP);
        } finally {
            brokenLimiter.shutdown();
        }
    }

    @Test
    void runtimeStatsReflectsActiveAndLimit() {
        Release r = limiter.acquire("m1", 2);
        limiter.setModelName("m1", "gpt-x");

        List<RuntimeStat> stats = limiter.runtimeStats();
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).modelId()).isEqualTo("m1");
        assertThat(stats.get(0).name()).isEqualTo("gpt-x");
        assertThat(stats.get(0).active()).as("持槽中 active=1").isEqualTo(1);
        assertThat(stats.get(0).limit()).isEqualTo(2);

        r.close();
        assertThat(limiter.runtimeStats().get(0).active()).as("释放后 active=0").isEqualTo(0);
    }
}
