package com.ragagent.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.limiter.LocalLimiter;
import com.ragagent.llm.limiter.ModelConcurrencyLimiter;
import com.ragagent.llm.limiter.RedisLimiter;
import com.ragagent.stream.EmbeddedRedis;
import com.ragagent.system.service.SystemSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link ModelConcurrencyGovernorWiring} 的装配验收：开关开 → {@link RedisLimiter}、
 * 缺省 → {@link LocalLimiter}、Redis 不可达 → 启动失败、设置面故障 → 不装配（全放行）。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class ModelConcurrencyGovernorWiringTest {

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

    private static SystemSettingService settings(long limit) {
        SystemSettingService s = mock(SystemSettingService.class);
        when(s.getInt(anyString(), anyString(), anyLong())).thenReturn(limit);
        return s;
    }

    @Test
    void enabledSwitchWiresRedisLimiter() {
        ConcurrencyGovernor gov = spy(new ConcurrencyGovernor());
        new ApplicationContextRunner()
                .withUserConfiguration(ModelConcurrencyGovernorWiring.class)
                .withBean(ConcurrencyGovernor.class, () -> gov)
                .withBean(SystemSettingService.class, () -> settings(32))
                .withBean(StringRedisTemplate.class, () -> redis.template())
                .withPropertyValues("llm.limiter.redis-enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    ArgumentCaptor<ModelConcurrencyLimiter> captor =
                            ArgumentCaptor.forClass(ModelConcurrencyLimiter.class);
                    verify(gov).setGovernor(captor.capture(), eq(32));
                    assertThat(captor.getValue()).isInstanceOf(RedisLimiter.class);
                    ((RedisLimiter) captor.getValue()).shutdown();
                });
    }

    @Test
    void defaultSwitchWiresLocalLimiter() {
        ConcurrencyGovernor gov = spy(new ConcurrencyGovernor());
        new ApplicationContextRunner()
                .withUserConfiguration(ModelConcurrencyGovernorWiring.class)
                .withBean(ConcurrencyGovernor.class, () -> gov)
                .withBean(SystemSettingService.class, () -> settings(32))
                .withBean(StringRedisTemplate.class, () -> redis.template())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    ArgumentCaptor<ModelConcurrencyLimiter> captor =
                            ArgumentCaptor.forClass(ModelConcurrencyLimiter.class);
                    verify(gov).setGovernor(captor.capture(), eq(32));
                    assertThat(captor.getValue()).isInstanceOf(LocalLimiter.class);
                });
    }

    @Test
    void unreachableRedisFailsStartup() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.getConnectionFactory()).thenThrow(new RuntimeException("no redis"));

        new ApplicationContextRunner()
                .withUserConfiguration(ModelConcurrencyGovernorWiring.class)
                .withBean(ConcurrencyGovernor.class)
                .withBean(SystemSettingService.class, () -> settings(32))
                .withBean(StringRedisTemplate.class, () -> broken)
                .withPropertyValues("llm.limiter.redis-enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void settingsFailureLeavesGovernorUnwired() {
        SystemSettingService bad = mock(SystemSettingService.class);
        when(bad.getInt(anyString(), anyString(), anyLong()))
                .thenThrow(new RuntimeException("db down"));
        ConcurrencyGovernor gov = spy(new ConcurrencyGovernor());

        new ApplicationContextRunner()
                .withUserConfiguration(ModelConcurrencyGovernorWiring.class)
                .withBean(ConcurrencyGovernor.class, () -> gov)
                .withBean(SystemSettingService.class, () -> bad)
                .withBean(StringRedisTemplate.class, () -> redis.template())
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    verify(gov, never()).setGovernor(any(), anyInt());
                });
    }
}
