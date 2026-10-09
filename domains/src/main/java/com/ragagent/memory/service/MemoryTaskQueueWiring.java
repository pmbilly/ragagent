package com.ragagent.memory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * memory 蒸馏队列的 Redis 面装配（多实例部署）。
 *
 * <p>{@code memory.redis-enabled=true} 时把投递端口切成跨实例实现
 * （{@link RedisMemoryExtractTaskQueue}），{@code @Primary} 覆盖
 * 无条件 {@code @Component} 的 {@link InProcessMemoryExtractTaskQueue}。
 * 开关关闭时本类整体不生效（单实例语义与接线前一致）；打开但 Redis
 * 连不上时<b>启动失败</b>（不静默退化，与 im/wiki/knowledge 的开关同口径）。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "memory", name = "redis-enabled", havingValue = "true")
public class MemoryTaskQueueWiring {

    private static final Logger log = LoggerFactory.getLogger(MemoryTaskQueueWiring.class);

    /** 启动即验：配置成 Redis 却连不上时不静默退化。 */
    public MemoryTaskQueueWiring(StringRedisTemplate template) {
        try (var connection = template.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "memory.redis-enabled=true but Redis is not reachable: " + e.getMessage(), e);
        }
        log.info("[MemoryRedis] memory 蒸馏队列已装配（多实例模式）：跨实例共享任务表");
    }

    /** 跨实例共享的蒸馏任务队列。 */
    @Bean
    @Primary
    public MemoryExtractTaskQueue redisMemoryExtractTaskQueue(StringRedisTemplate template,
            ObjectProvider<MemoryExtractionService> handlerProvider) {
        return new RedisMemoryExtractTaskQueue(template, handlerProvider);
    }
}
