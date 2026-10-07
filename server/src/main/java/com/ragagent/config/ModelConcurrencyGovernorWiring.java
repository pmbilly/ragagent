package com.ragagent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.limiter.LocalLimiter;
import com.ragagent.llm.limiter.ModelConcurrencyLimiter;
import com.ragagent.llm.limiter.RedisLimiter;
import com.ragagent.system.service.SystemSettingService;

import jakarta.annotation.PostConstruct;

/**
 * 后台并发闸门的启动装配：limit = model.max_concurrency（DB → {@code WEKNORA_MODEL_MAX_CONCURRENCY}
 * env → 缺省 32）。
 *
 * <h2>后端选择</h2>
 * <ul>
 *   <li>缺省：{@link LocalLimiter}（进程内信号量，单实例语义）；</li>
 *   <li>{@code llm.limiter.redis-enabled=true}：{@link RedisLimiter}
 *       （跨实例分布式信号量：ZSET 租约 + 心跳 + fail-open）。
 *       开关打开但 Redis 连不上时
 *       <b>启动失败</b>（不静默退化，与 im/wiki 的开关同口径）。</li>
 * </ul>
 *
 * <p>limit ≤ 0 = 关闭治理（所有调用放行）。</p>
 */
@Configuration
public class ModelConcurrencyGovernorWiring {

    private static final Logger log = LoggerFactory.getLogger(ModelConcurrencyGovernorWiring.class);

    /** 缺省上限。 */
    private static final int DEFAULT_MODEL_MAX_CONCURRENCY = 32;

    private final ConcurrencyGovernor governor;
    private final SystemSettingService systemSettingService;
    private final ObjectProvider<StringRedisTemplate> redisTemplates;
    private final boolean redisEnabled;

    public ModelConcurrencyGovernorWiring(ConcurrencyGovernor governor,
            SystemSettingService systemSettingService,
            ObjectProvider<StringRedisTemplate> redisTemplates,
            @Value("${llm.limiter.redis-enabled:false}") boolean redisEnabled) {
        this.governor = governor;
        this.systemSettingService = systemSettingService;
        this.redisTemplates = redisTemplates;
        this.redisEnabled = redisEnabled;
    }

    @PostConstruct
    void install() {
        long limit;
        try {
            limit = systemSettingService.getInt("model.max_concurrency",
                    "WEKNORA_MODEL_MAX_CONCURRENCY", DEFAULT_MODEL_MAX_CONCURRENCY);
        } catch (RuntimeException e) {
            // 设置面不可用时按缺省装配（尽力而为），
            // 闸门失效退化为"未装配"形态（全部放行），不得阻断应用启动。
            log.warn("[ModelLimiter] resolve model.max_concurrency failed, "
                    + "governor left unwired (all calls pass): {}", e.toString());
            return;
        }

        ModelConcurrencyLimiter limiter;
        if (redisEnabled) {
            StringRedisTemplate template = redisTemplates.getIfAvailable();
            if (template == null) {
                throw new IllegalStateException(
                        "llm.limiter.redis-enabled=true but no Redis connection is configured");
            }
            // 启动即验：配置成 Redis 却连不上时不静默退化
            try (var connection = template.getConnectionFactory().getConnection()) {
                connection.ping();
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "llm.limiter.redis-enabled=true but Redis is not reachable: "
                                + e.getMessage(), e);
            }
            limiter = new RedisLimiter(template);
        } else {
            limiter = new LocalLimiter();
        }
        governor.setGovernor(limiter, (int) limit);

        if (limit <= 0) {
            log.info("[ModelLimiter] background concurrency governor DISABLED "
                    + "(model.max_concurrency<=0)");
            return;
        }
        log.info("[ModelLimiter] background model concurrency governed per-model, limit={} ({})",
                limit, redisEnabled ? "redis, distributed" : "in-process, lite mode");
    }
}
