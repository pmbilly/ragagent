package com.ragagent.wiki.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RedisWikiInflightLimiter} 的真 Redis 语义：上限挡回、跨实例共享上限、
 * 过期槽位自愈、释放恢复、续期推租约、上限未配置放行。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisWikiInflightLimiterTest {

    private EmbeddedRedis redis;
    private RedisWikiInflightLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
        limiter = new RedisWikiInflightLimiter(redis.template());
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

    private static String key(String kbId) {
        return WikiIngestConstants.INFLIGHT_PREFIX + kbId;
    }

    @Test
    void reserveUpToLimitThenDenyAndRecoverAfterRelease() {
        var r1 = limiter.reserve("kb-1", 2);
        var r2 = limiter.reserve("kb-1", 2);
        assertThat(r1.granted()).isTrue();
        assertThat(r2.granted()).isTrue();
        var r3 = limiter.reserve("kb-1", 2);
        assertThat(r3.granted()).as("到上限拒绝").isFalse();
        assertThat(r3.release()).as("被拒的预留没有 release（接口约定）").isNull();

        r1.releaseQuietly();
        var r4 = limiter.reserve("kb-1", 2);
        assertThat(r4.granted()).as("释放后额度恢复").isTrue();
    }

    @Test
    void capIsSharedAcrossInstances() {
        RedisWikiInflightLimiter second = new RedisWikiInflightLimiter(redis.template());
        try {
            assertThat(limiter.reserve("kb-1", 1).granted()).isTrue();
            assertThat(second.reserve("kb-1", 1).granted())
                    .as("跨实例共享上限：另一实例占满即拒绝").isFalse();
        } finally {
            second.shutdown();
        }
    }

    @Test
    void expiredSlotsAreSelfHealedOnReserve() {
        // 模拟崩溃 worker 遗留的过期槽位：score 在过去
        redis.template().opsForZSet().add(key("kb-1"), "dead-token",
                System.currentTimeMillis() - 1000);
        var r = limiter.reserve("kb-1", 1);
        assertThat(r.granted()).as("过期槽位不得占用额度").isTrue();
    }

    @Test
    void zeroMaxAlwaysAllows() {
        var r = limiter.reserve("kb-1", 0);
        assertThat(r.granted()).isTrue();
        r.releaseQuietly();   // no-op 槽位释放安全
    }

    @Test
    void renewPushesLeaseForward() {
        var r = limiter.reserve("kb-1", 1);
        var tokens = redis.template().opsForZSet().range(key("kb-1"), 0, -1);
        assertThat(tokens).hasSize(1);
        String token = tokens.iterator().next();

        // 把租约改到过去（模拟接近过期），续期必须推回未来
        redis.template().opsForZSet().add(key("kb-1"), token, System.currentTimeMillis() - 1000);
        limiter.renewAll();
        assertThat(redis.template().opsForZSet().score(key("kb-1"), token))
                .isGreaterThan((double) System.currentTimeMillis());
        r.releaseQuietly();
    }
}
