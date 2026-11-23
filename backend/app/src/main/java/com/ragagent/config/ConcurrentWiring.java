package com.ragagent.config;

import com.ragagent.framework.concurrent.DistributedLock;
import com.ragagent.framework.concurrent.RedisDistributedLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 并发原语装配：分布式锁与去重标记的统一端口（{@link DistributedLock}）。
 *
 * <p>供需要注入共享实例的域使用；各锁实现类（RedisWikiFinalizeLock 等）
 * 也可直接 {@code new RedisDistributedLock(template)}——无状态，实例等价。</p>
 */
@Configuration(proxyBeanMethods = false)
public class ConcurrentWiring {

    @Bean
    public DistributedLock distributedLock(StringRedisTemplate template) {
        return new RedisDistributedLock(template);
    }
}
