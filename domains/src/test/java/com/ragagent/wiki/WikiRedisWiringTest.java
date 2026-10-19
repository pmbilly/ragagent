package com.ragagent.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ragagent.platform.stream.EmbeddedRedis;
import com.ragagent.wiki.domain.page.WikiSlugLock;
import com.ragagent.wiki.repository.ingest.InProcessWikiFinalizeLock;
import com.ragagent.wiki.repository.ingest.InProcessWikiIdentityClaimStore;
import com.ragagent.wiki.repository.ingest.InProcessWikiInflightLimiter;
import com.ragagent.wiki.repository.ingest.RedisWikiFinalizeLock;
import com.ragagent.wiki.repository.ingest.RedisWikiIdentityClaimStore;
import com.ragagent.wiki.repository.ingest.RedisWikiInflightLimiter;
import com.ragagent.wiki.repository.ingest.WikiFinalizeLock;
import com.ragagent.wiki.repository.ingest.WikiIdentityClaimStore;
import com.ragagent.wiki.repository.ingest.WikiInflightLimiter;
import com.ragagent.wiki.repository.page.InProcessWikiSlugLock;
import com.ragagent.wiki.repository.page.RedisWikiSlugLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link WikiRedisWiring} 的装配验收：开关打开时四个协调端口切到 Redis 实现
 * （{@code @Primary} 压过无条件 {@code @Component} 的 InProcess 实现）、开关缺省
 * 时不注册 Redis 版、开关打开但 Redis 不可达时启动失败（不静默退化）。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class WikiRedisWiringTest {

    private EmbeddedRedis redis;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(WikiRedisWiring.class)
                .withBean(StringRedisTemplate.class, () -> redis.template())
                .withBean(InProcessWikiSlugLock.class)
                .withBean(InProcessWikiFinalizeLock.class)
                .withBean(InProcessWikiIdentityClaimStore.class)
                .withBean(InProcessWikiInflightLimiter.class);
    }

    /** 开关开 + 双实现共存：@Primary 的 Redis 版胜出。 */
    @Test
    void enabledSwitchWiresRedisImplementationsAsPrimary() {
        runner()
                .withPropertyValues("wiki.redis-enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(WikiSlugLock.class)).isInstanceOf(RedisWikiSlugLock.class);
                    assertThat(ctx.getBean(WikiFinalizeLock.class))
                            .isInstanceOf(RedisWikiFinalizeLock.class);
                    assertThat(ctx.getBean(WikiIdentityClaimStore.class))
                            .isInstanceOf(RedisWikiIdentityClaimStore.class);
                    assertThat(ctx.getBean(WikiInflightLimiter.class))
                            .isInstanceOf(RedisWikiInflightLimiter.class);
                });
    }

    /** 开关缺省：不注册 Redis 版，InProcess 是唯一实现（单实例语义）。 */
    @Test
    void defaultSwitchKeepsInProcessImplementations() {
        runner()
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(RedisWikiSlugLock.class);
                    assertThat(ctx).doesNotHaveBean(RedisWikiFinalizeLock.class);
                    assertThat(ctx.getBean(WikiSlugLock.class))
                            .isInstanceOf(InProcessWikiSlugLock.class);
                    assertThat(ctx.getBean(WikiIdentityClaimStore.class))
                            .isInstanceOf(InProcessWikiIdentityClaimStore.class);
                    assertThat(ctx.getBean(WikiInflightLimiter.class))
                            .isInstanceOf(InProcessWikiInflightLimiter.class);
                });
    }

    /** 开关开但 Redis 不可达：启动失败（fail fast，不静默退化成单机语义）。 */
    @Test
    void unreachableRedisFailsStartup() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.getConnectionFactory()).thenThrow(new RuntimeException("no redis"));

        new ApplicationContextRunner()
                .withUserConfiguration(WikiRedisWiring.class)
                .withBean(StringRedisTemplate.class, () -> broken)
                .withPropertyValues("wiki.redis-enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
