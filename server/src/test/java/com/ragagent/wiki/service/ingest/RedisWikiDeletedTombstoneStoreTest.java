package com.ragagent.wiki.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RedisWikiDeletedTombstoneStore} 的真 Redis 语义：墓碑跨实例可见、
 * 清除生效、未知文档返回 false。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisWikiDeletedTombstoneStoreTest {

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

    private RedisWikiDeletedTombstoneStore store() {
        return new RedisWikiDeletedTombstoneStore(redis.template());
    }

    @Test
    void markAndExistsAcrossInstances() {
        RedisWikiDeletedTombstoneStore a = store();
        RedisWikiDeletedTombstoneStore b = store();

        assertThat(b.exists("kb-1", "doc-1")).isFalse();
        a.markDeleted("kb-1", "doc-1");
        assertThat(b.exists("kb-1", "doc-1"))
                .as("墓碑跨实例可见：删除后其它副本的在途任务也走快路径").isTrue();
        assertThat(b.exists("kb-1", "doc-2")).isFalse();
        assertThat(b.exists("kb-2", "doc-1")).isFalse();
    }

    @Test
    void clearRemovesTombstone() {
        RedisWikiDeletedTombstoneStore a = store();
        RedisWikiDeletedTombstoneStore b = store();

        a.markDeleted("kb-1", "doc-1");
        a.clear("kb-1", "doc-1");
        assertThat(b.exists("kb-1", "doc-1")).isFalse();
    }

    @Test
    void tombstoneHasTtl() {
        RedisWikiDeletedTombstoneStore a = store();

        a.markDeleted("kb-1", "doc-1");
        Long ttl = redis.template().getExpire(WikiIngestConstants.deletedTombstoneKey("kb-1", "doc-1"));
        assertThat(ttl).as("墓碑必须带 TTL（与进程内实现同源）").isBetween(1L, 3600L);
    }
}
