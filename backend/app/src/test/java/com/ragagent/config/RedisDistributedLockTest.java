package com.ragagent.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.framework.concurrent.DistributedLock;
import com.ragagent.framework.concurrent.RedisDistributedLock;
import com.ragagent.framework.stream.EmbeddedRedis;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/** {@link RedisDistributedLock}：互斥、防误删、续期、去重标记。 */
class RedisDistributedLockTest {

    private EmbeddedRedis redis;
    private StringRedisTemplate template;
    private RedisDistributedLock lock;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        org.junit.jupiter.api.Assumptions.assumeTrue(redis != null, "无 redis-server，跳过");
        template = redis.template();
        lock = new RedisDistributedLock(template);
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    void sameKeyRunsOneAtATime() {
        Optional<DistributedLock.LockHandle> first = lock.tryLock("k", Duration.ofSeconds(10));
        assertThat(first).isPresent();
        assertThat(lock.tryLock("k", Duration.ofSeconds(10))).isEmpty();
        first.get().close();
        assertThat(lock.tryLock("k", Duration.ofSeconds(10))).isPresent();
    }

    @Test
    void closeDoesNotDeleteOthersLock() throws Exception {
        // RedisWikiFinalizeLock 旧实现的缺陷：TTL 到期后锁被另一个持有者重建，
        // 原持有者 DEL 会删掉新持有者的锁。统一端口用 token 比对 DEL 防住。
        Optional<DistributedLock.LockHandle> first = lock.tryLock("k", Duration.ofMillis(100));
        assertThat(first).isPresent();
        Thread.sleep(200);
        Optional<DistributedLock.LockHandle> second = lock.tryLock("k", Duration.ofSeconds(10));
        assertThat(second).isPresent();
        first.get().close();
        assertThat(template.hasKey("k")).isTrue();
        second.get().close();
        assertThat(template.hasKey("k")).isFalse();
    }

    @Test
    void renewExtendsTtl() {
        Optional<DistributedLock.LockHandle> handle = lock.tryLock("k", Duration.ofSeconds(10));
        assertThat(handle).isPresent();
        handle.get().renew();
        Long ttl = template.getExpire("k");
        assertThat(ttl).isNotNull().isGreaterThan(0);
        handle.get().close();
    }

    @Test
    void markOnceIsIdempotent() {
        assertThat(lock.markOnce("dedup", Duration.ofSeconds(10))).isTrue();
        assertThat(lock.markOnce("dedup", Duration.ofSeconds(10))).isFalse();
    }
}
