package com.ragagent.wiki.service.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RedisWikiIdentityClaimStore} 的真 Redis 语义：权威覆盖、既有认领获胜并续期、
 * 脏值替换、release 后重新认领、页面类型隔离。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisWikiIdentityClaimStoreTest {

    private EmbeddedRedis redis;
    private RedisWikiIdentityClaimStore store;

    @BeforeEach
    void setUp() {
        redis = EmbeddedRedis.tryStart();
        assumeTrue(redis != null, "redis-server 不可用，跳过");
        redis.flushAll();
        store = new RedisWikiIdentityClaimStore(redis.template());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            redis.close();
        }
    }

    private static String key(String kbId, String pageType, String identity) {
        return WikiIngestConstants.identityClaimKey(kbId, pageType, identity);
    }

    @Test
    void firstClaimStoresProposalWithTtl() {
        String slug = store.claim("kb-1", "entity", "孔子", "entity/kongzi", false, "entity/");
        assertThat(slug).isEqualTo("entity/kongzi");
        assertThat(redis.template().getExpire(key("kb-1", "entity", "孔子")))
                .as("认领必须带 TTL（崩溃自愈）").isPositive();
    }

    @Test
    void validExistingClaimWinsAndRenews() {
        store.claim("kb-1", "entity", "孔子", "entity/kongzi", false, "entity/");
        // 另一个批次（或另一个实例）为同一标题提出了不同 slug——必须收敛到既有认领
        String second = store.claim("kb-1", "entity", "孔子", "entity/confucius", false, "entity/");
        assertThat(second).as("有效既有认领获胜").isEqualTo("entity/kongzi");
        assertThat(redis.template().getExpire(key("kb-1", "entity", "孔子")))
                .as("获胜的既有认领被续期").isPositive();
    }

    @Test
    void authoritativeClaimOverwritesExisting() {
        store.claim("kb-1", "entity", "孔子", "entity/kongzi", false, "entity/");
        String overwritten =
                store.claim("kb-1", "entity", "孔子", "entity/kongzi-v2", true, "entity/");
        assertThat(overwritten).as("权威认领（精确既有页解析）无条件覆盖").isEqualTo("entity/kongzi-v2");
    }

    @Test
    void valueWithWrongPrefixIsReplaced() {
        // 脏值：另一个页面类型的残留（前缀不符）→ 不得在脏键上分叉
        redis.template().opsForValue().set(key("kb-1", "entity", "孔子"), "concept/whatever");
        String claim = store.claim("kb-1", "entity", "孔子", "entity/fresh", false, "entity/");
        assertThat(claim).isEqualTo("entity/fresh");
    }

    @Test
    void releaseAllowsFreshClaim() {
        store.claim("kb-1", "entity", "孔子", "entity/kongzi", false, "entity/");
        store.release("kb-1", "entity", "孔子");
        assertThat(redis.template().hasKey(key("kb-1", "entity", "孔子"))).isFalse();

        String fresh = store.claim("kb-1", "entity", "孔子", "entity/fresh", false, "entity/");
        assertThat(fresh).as("释放后新提议生效").isEqualTo("entity/fresh");
    }

    @Test
    void sameTitleDifferentPageTypesDoNotCollide() {
        String entity = store.claim("kb-1", "entity", "孔子", "entity/kongzi", false, "entity/");
        String concept = store.claim("kb-1", "concept", "孔子", "concept/kongzi", false, "concept/");
        assertThat(entity).isEqualTo("entity/kongzi");
        assertThat(concept).isEqualTo("concept/kongzi");
    }
}
