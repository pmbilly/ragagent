package com.ragagent.im.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IM Redis 面的真 Redis 语义：Lua 闸门 CAS（含超限回滚）、per-user 计数配平、
 * stop marker 一次性消费、inflight 映射往返。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class ImRedisStoreTest {

    private EmbeddedRedis redis;
    private ImRedisStore store;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
        store = new ImRedisStore(redis.template());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    @Test
    void globalGateAcquiresUpToLimitAndRollsBackOverflow() {
        String key = ImRedisKeys.GLOBAL_GATE;
        assertTrue(store.tryAcquireGlobalGate(key, 2, 300), "槽位 1");
        assertTrue(store.tryAcquireGlobalGate(key, 2, 300), "槽位 2");
        assertFalse(store.tryAcquireGlobalGate(key, 2, 300), "第三个超限");
        assertFalse(store.tryAcquireGlobalGate(key, 2, 300), "超限尝试必须回滚（计数仍为 2）");
        store.releaseGlobalGate(key);
        assertTrue(store.tryAcquireGlobalGate(key, 2, 300), "释放后可再拿");
    }

    @Test
    void perUserCounterIncrementsWithTtlAndDecrements() {
        String key = ImRedisKeys.QUEUE_USER_PREFIX + "u1";
        assertEquals(1L, store.incrWithTtl(key, 300));
        assertEquals(2L, store.incrWithTtl(key, 300));
        store.decr(key);
        // decr 后回到 1，再自增为 2（若 decr 未生效这里会是 3）
        assertEquals(2L, store.incrWithTtl(key, 300));
        Long ttl = redis.template().getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0, "计数应带 TTL");
    }

    @Test
    void stopMarkerIsOneShot() {
        String userKey = "ch-1:u1:chat-1:";
        assertFalse(store.checkAndClearStopMarker(userKey), "无标记");
        store.setStopMarker(userKey, 30);
        assertTrue(store.checkAndClearStopMarker(userKey), "命中");
        assertFalse(store.checkAndClearStopMarker(userKey), "一次性消费（清除后不再命中）");
    }

    @Test
    void dedupSetIfAbsentIsOneShot() {
        String key = ImRedisKeys.DEDUP_PREFIX + "m-1";
        assertEquals(Boolean.TRUE, store.setIfAbsent(key, "1", 300), "首次写入");
        assertEquals(Boolean.FALSE, store.setIfAbsent(key, "1", 300), "重复消息被拒");
        Long ttl = redis.template().getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl > 0, "去重标记应带 TTL");
    }

    @Test
    void slidingWindowRateLimitBlocksBeyondBudget() {
        String key = ImRedisKeys.RATE_LIMIT_PREFIX + "rl:ch:u";
        assertEquals(Boolean.TRUE, store.rateLimitAllow(key, 60, 2));
        assertEquals(Boolean.TRUE, store.rateLimitAllow(key, 60, 2));
        assertEquals(Boolean.FALSE, store.rateLimitAllow(key, 60, 2), "第三次超预算");
        assertEquals(Boolean.TRUE, store.rateLimitAllow(key, 60, 3), "预算变化按新值判定");
    }

    @Test
    void leaderLockAcquireRenewReleaseWithOwnershipCas() {
        String key = ImRedisKeys.LEADER_PREFIX + "ch-1";
        assertTrue(store.tryAcquireLeader(key, "inst-a", 15), "首次抢锁");
        assertFalse(store.tryAcquireLeader(key, "inst-b", 15), "他人持有抢不到");
        assertTrue(store.renewLeader(key, "inst-a", 15), "持有者可续期");
        assertFalse(store.renewLeader(key, "inst-b", 15), "非持有者续期失败");
        store.releaseLeader(key, "inst-b");
        assertFalse(store.tryAcquireLeader(key, "inst-b", 15), "非持有者释放无效（CAS）");
        store.releaseLeader(key, "inst-a");
        assertTrue(store.tryAcquireLeader(key, "inst-b", 15), "持有者释放后可易主");
    }

    @Test
    void inflightMappingRoundTrip() {
        String userKey = "ch-1:u1:chat-1:";
        assertNull(store.loadInflight(userKey), "无映射");
        store.storeInflight(userKey, "sess-1", "msg-1", 600);
        assertArrayEquals(new String[]{"sess-1", "msg-1"}, store.loadInflight(userKey));
        store.clearInflight(userKey);
        assertNull(store.loadInflight(userKey), "清除后读不到");
    }
}
