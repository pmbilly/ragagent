package com.ragagent.knowledge.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ragagent.knowledge.dto.kb.KBCloneProgress;
import com.ragagent.knowledge.dto.doc.KnowledgeMoveProgress;
import com.ragagent.stream.EmbeddedRedis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link RedisKnowledgeTaskProgressStore} 的真 Redis 语义：跨实例进度可见、
 * {@code save*Initial} 的 NX（只落一次）与 {@code save*} 的覆写、
 * move 与 clone 的键隔离。
 *
 * <p>没有 redis-server（且未配 {@code REDIS_TEST_ADDR}）时整体跳过——见 {@link EmbeddedRedis}。</p>
 */
class RedisKnowledgeTaskProgressStoreTest {

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

    private RedisKnowledgeTaskProgressStore store() {
        return new RedisKnowledgeTaskProgressStore(redis.template());
    }

    private static KnowledgeMoveProgress move(String taskId, String status, int progress) {
        return new KnowledgeMoveProgress(taskId, "src", "dst", status, progress, 10, progress,
                0, "", "", 0L, 0L);
    }

    private static KBCloneProgress clone(String taskId, String status, int progress) {
        return new KBCloneProgress(taskId, "src", "dst", status, progress, 10, progress,
                "", "", 0L, 0L);
    }

    @Test
    void moveProgressIsSharedAcrossInstances() {
        RedisKnowledgeTaskProgressStore a = store();
        RedisKnowledgeTaskProgressStore b = store();

        a.saveMoveInitial(move("t-1", "pending", 0));
        assertThat(b.getMove("t-1")).as("进度跨实例可见（轮询路由到任意副本都读得到）").isNotNull();
        assertThat(b.getMove("t-1").status()).isEqualTo("pending");
        assertThat(b.getMove("nope")).isNull();
    }

    @Test
    void saveMoveInitialDoesNotOverwrite() {
        RedisKnowledgeTaskProgressStore s = store();

        // NX 语义 = putIfAbsent：第二条 initial 不得覆盖
        s.saveMoveInitial(move("t-1", "pending", 0));
        s.saveMoveInitial(move("t-1", "pending", 99));
        assertThat(s.getMove("t-1").progress()).isZero();
    }

    @Test
    void saveMoveOverwrites() {
        RedisKnowledgeTaskProgressStore s = store();

        s.saveMoveInitial(move("t-1", "pending", 0));
        s.saveMove(move("t-1", "running", 5));
        assertThat(s.getMove("t-1").status()).isEqualTo("running");
        assertThat(s.getMove("t-1").progress()).isEqualTo(5);
    }

    @Test
    void moveAndCloneKeysAreIsolated() {
        RedisKnowledgeTaskProgressStore a = store();
        RedisKnowledgeTaskProgressStore b = store();

        a.saveCloneInitial(clone("t-1", "pending", 0));
        assertThat(b.getClone("t-1")).isNotNull();
        assertThat(b.getMove("t-1")).as("move 与 clone 同 taskId 不互相污染").isNull();
    }
}
