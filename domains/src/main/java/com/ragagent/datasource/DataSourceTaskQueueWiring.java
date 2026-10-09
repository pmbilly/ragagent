package com.ragagent.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * datasource 同步队列的 Redis 面装配（多实例部署）。
 *
 * <p>{@code datasource.redis-enabled=true} 时把投递端口切成跨实例实现
 * （{@link RedisDataSourceSyncTaskQueue}：全局 TaskID 去重 + 共享任务表），
 * {@code @Primary} 覆盖无条件 {@code @Component} 的
 * {@link InProcessDataSourceSyncTaskQueue}。开关关闭时本类整体不生效
 * （单实例语义与接线前一致）；打开但 Redis 连不上时<b>启动失败</b>
 * （不静默退化，与其余域开关同口径）。</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "datasource", name = "redis-enabled", havingValue = "true")
public class DataSourceTaskQueueWiring {

    private static final Logger log = LoggerFactory.getLogger(DataSourceTaskQueueWiring.class);

    /** 启动即验：配置成 Redis 却连不上时不静默退化。 */
    public DataSourceTaskQueueWiring(StringRedisTemplate template) {
        try (var connection = template.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "datasource.redis-enabled=true but Redis is not reachable: " + e.getMessage(), e);
        }
        log.info("[DataSourceRedis] 数据源同步队列已装配（多实例模式）："
                + "TaskID 全局去重 + 跨实例共享任务表");
    }

    /** 跨实例同步队列（TaskID 全局去重）。 */
    @Bean
    @Primary
    public DataSourceSyncTaskQueue redisDataSourceSyncTaskQueue(StringRedisTemplate template,
            ObjectProvider<DataSourceSyncHandler> handlerProvider) {
        return new RedisDataSourceSyncTaskQueue(template, handlerProvider);
    }
}
