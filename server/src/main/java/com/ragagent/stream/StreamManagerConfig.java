package com.ragagent.stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.config.StreamProperties;

/**
 * 选择流管理器实现。
 *
 * <p>选 redis 时**启动即 Ping 一次 Redis**，连不上服务起不来——否则"配置成 redis 但连不上"
 * 会静默退化成运行期才炸。默认（{@code STREAM_MANAGER_TYPE} 为空或非 {@code redis}）
 * 走内存实现，不碰 Redis。</p>
 */
@Configuration
public class StreamManagerConfig {

    @Bean
    public StreamManager streamManager(StreamProperties props, ObjectProvider<StringRedisTemplate> templates) {
        if (!props.useRedis()) {
            return new MemoryStreamManager();
        }
        StringRedisTemplate template = templates.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("STREAM_MANAGER_TYPE=redis but no Redis connection is configured");
        }
        try {
            template.getConnectionFactory().getConnection().ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException("failed to connect to Redis: " + e.getMessage(), e);
        }
        return new RedisStreamManager(template, props.prefix(), props.ttl());
    }
}
