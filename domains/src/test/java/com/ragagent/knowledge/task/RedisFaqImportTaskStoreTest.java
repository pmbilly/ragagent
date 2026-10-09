package com.ragagent.knowledge.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.OffsetDateTime;
import java.util.List;

import com.ragagent.knowledge.dto.faq.FaqImportProgress;
import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RedisFaqImportTaskStore} 的真 Redis 语义：进度与 running 锁跨实例共享
 * （含 OffsetDateTime 的 JSON 往返）、clearRunningInfoIfMatches 的匹配条件
 * 逐条对照进程内实现、创建互斥的获取/释放。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisFaqImportTaskStoreTest {

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

    private RedisFaqImportTaskStore store() {
        return new RedisFaqImportTaskStore(redis.template());
    }

    private static FaqImportProgress progress(String taskId) {
        return new FaqImportProgress(taskId, "kb-1", "k-1", "pending", 0, 0, 0, 0, 0, 0, 0,
                List.of(), "", List.of(), List.of(), List.of(), 0, 0, List.of(), "", "",
                0L, 0L, false, "", OffsetDateTime.now(), "", 0L);
    }

    @Test
    void progressRoundTripsAcrossInstances() {
        RedisFaqImportTaskStore a = store();
        RedisFaqImportTaskStore b = store();

        a.saveProgress(progress("t-1"));
        FaqImportProgress seen = b.getProgress("t-1");
        assertThat(seen).as("进度跨实例可见（进程内实现下别副本查不到）").isNotNull();
        assertThat(seen.taskId()).isEqualTo("t-1");
        assertThat(seen.importedAt()).as("OffsetDateTime 走 ISO-8601 往返").isNotNull();
        assertThat(b.getProgress("nope")).isNull();
    }

    @Test
    void runningLockIsSharedAcrossInstances() {
        RedisFaqImportTaskStore a = store();
        RedisFaqImportTaskStore b = store();

        assertThat(b.getRunningTaskId("kb-1")).isEmpty();
        a.setRunningInfo("kb-1", new FaqImportTaskStore.RunningInfo("t-1", 100L, "inst-a"));
        assertThat(b.getRunningTaskId("kb-1"))
                .as("running 锁跨实例可见：多副本下第二个导入会被拦截").isEqualTo("t-1");
    }

    @Test
    void clearRunningInfoMatchesLikeInProcess() {
        RedisFaqImportTaskStore s = store();

        // taskId 不匹配 → 不清
        s.setRunningInfo("kb-1", new FaqImportTaskStore.RunningInfo("t-1", 100L, "inst-a"));
        s.clearRunningInfoIfMatches("kb-1", "t-2", "inst-a", 100L);
        assertThat(s.getRunningTaskId("kb-1")).isEqualTo("t-1");

        // instanceId 不匹配 → 不清
        s.clearRunningInfoIfMatches("kb-1", "t-1", "inst-b", 100L);
        assertThat(s.getRunningTaskId("kb-1")).isEqualTo("t-1");

        // 全匹配 → 清
        s.clearRunningInfoIfMatches("kb-1", "t-1", "inst-a", 100L);
        assertThat(s.getRunningTaskId("kb-1")).isEmpty();

        // instanceId 空的一方视为通配 → 清
        s.setRunningInfo("kb-1", new FaqImportTaskStore.RunningInfo("t-9", 200L, ""));
        s.clearRunningInfoIfMatches("kb-1", "t-9", "inst-c", 200L);
        assertThat(s.getRunningTaskId("kb-1")).isEmpty();
    }

    @Test
    void createGuardIsMutuallyExclusiveAndReleasable() {
        RedisFaqImportTaskStore a = store();
        RedisFaqImportTaskStore b = store();

        assertThat(a.acquireCreateGuard("faq:create:1:kb-1:abc")).isTrue();
        assertThat(b.acquireCreateGuard("faq:create:1:kb-1:abc"))
                .as("同 key 的创建互斥跨实例成立").isFalse();
        a.releaseCreateGuard("faq:create:1:kb-1:abc");
        assertThat(b.acquireCreateGuard("faq:create:1:kb-1:abc")).isTrue();
    }
}
