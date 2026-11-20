package com.ragagent.im.runtime;

import com.ragagent.platform.redis.RedisPubSub;
import com.ragagent.platform.redis.SpringRedisPubSub;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * IM 的 Redis 运行面装配：按 {@code im.redis-enabled} 决定是否创建存储与广播。
 *
 * <p>启动即验连通性——配置成 Redis 却连不上时不静默退化（同 {@code StreamManagerConfig}
 * 的口径）。装配细节收在这里，编排器不再直接接触 {@link StringRedisTemplate}。</p>
 */
@Component
public class ImRedisRuntime {

    private final ImRedisStore store;
    private final RedisPubSub pubSub;

    public ImRedisRuntime(ObjectProvider<StringRedisTemplate> templates,
            @Value("${im.redis-enabled:false}") boolean enabled) {
        if (!enabled) {
            this.store = null;
            this.pubSub = null;
            return;
        }
        StringRedisTemplate template = templates.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException(
                    "im.redis-enabled=true but no Redis connection is configured");
        }
        try {
            template.getConnectionFactory().getConnection().ping();
        } catch (RuntimeException e) {
            throw new IllegalStateException("failed to connect to Redis: " + e.getMessage(), e);
        }
        this.store = new ImRedisStore(template);
        this.pubSub = new SpringRedisPubSub(template.getConnectionFactory());
    }

    /** 存储面（{@code im.redis-enabled=false} 时为 null）。 */
    public ImRedisStore store() {
        return store;
    }

    /** 广播面（{@code im.redis-enabled=false} 时为 null）。 */
    public RedisPubSub pubSub() {
        return pubSub;
    }
}
