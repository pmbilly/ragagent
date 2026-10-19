package com.ragagent.memory.runtime;

import com.ragagent.memory.domain.MemoryExtractHandler;
import com.ragagent.memory.domain.MemoryExtractPayload;
import com.ragagent.memory.domain.MemoryExtractTaskQueue;
import com.ragagent.platform.task.RedisRetryingTaskQueue;
import com.ragagent.platform.task.RetryPolicy;
import com.ragagent.platform.tracing.langfuse.LangfuseTaskScope;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link MemoryExtractTaskQueue} 的 <b>Redis</b> 实现（跨实例共享任务表）。
 *
 * <p>{@code memory.redis-enabled=true} 时由 {@code MemoryTaskQueueWiring}
 * 装配（{@code @Primary} 覆盖 {@link InProcessMemoryExtractTaskQueue}）。
 * 租约/续期/重投与重试循环由 {@link RedisRetryingTaskQueue} 统一承载；
 * 本类只保留载荷编解码、延迟换算与 tracing handler。</p>
 *
 * <p>跨实例语义由 {@code RedisTaskQueueCore} 提供；重复触发不会导致重复蒸馏——
 * 主体侧 {@code claimPendingSessions} 的租约保证同一时刻只有一个 worker 持有会话。</p>
 */
public class RedisMemoryExtractTaskQueue implements MemoryExtractTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisMemoryExtractTaskQueue.class);

    /** 每个任务最多重试 2 次，之后放弃（与进程内实现同值）。 */
    static final int MAX_RETRY = 2;

    /** 任务类型，也是任务观测的 span/根名。 */
    static final String TASK_TYPE_MEMORY_EXTRACT = "memory:extract";

    private static final String KEY_PREFIX = "memory:task:extract";

    private final RedisRetryingTaskQueue queue;
    private final ObjectProvider<MemoryExtractHandler> handlerProvider;

    public RedisMemoryExtractTaskQueue(StringRedisTemplate template,
            ObjectProvider<MemoryExtractHandler> handlerProvider) {
        this.handlerProvider = handlerProvider;
        this.queue = new RedisRetryingTaskQueue(
                template,
                KEY_PREFIX,
                "memory:extract",
                new RetryPolicy(MAX_RETRY),
                this::run);
    }

    /** 供测试覆盖重试延迟（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        queue.setRetryDelayOverrideSeconds(seconds);
    }

    @Override
    public void enqueue(MemoryExtractPayload payload, Duration delay) {
        long delayMillis = delay == null ? 0L : Math.max(delay.toMillis(), 0L);
        try {
            queue.submit(payload.toJson(), delayMillis);
        } catch (RuntimeException e) {
            // 投递失败要报给调用方，让它释放在途槽位（契约）
            throw new IllegalStateException("memory extract queue submit failed: " + e.getMessage(), e);
        }
    }

    /** 供测试/关停使用：停止调度并释放资源。 */
    public void shutdown() {
        queue.shutdown();
    }

    private void run(String body, boolean finalAttempt) {
        MemoryExtractHandler handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            log.warn("memory: no extraction handler bean, dropping task");
            return;
        }
        MemoryExtractPayload payload = MemoryExtractPayload.fromJson(body);
        // 任务侧观测——负载带 traceparent 就续接上游 trace，否则以任务类型开独立根。
        try (LangfuseTaskScope scope =
                LangfuseTaskScope.start(
                        TASK_TYPE_MEMORY_EXTRACT,
                        payload.tracing(),
                        java.util.Map.of(
                                "subject_id", payload.subjectId(),
                                "message_id", payload.messageId()),
                        LangfuseTaskScope.previewPayload(body))) {
            handler.handle(payload);
        }
    }

    /** 测试种子形状：与 {@code RedisRetryingTaskQueue} 的 member JSON 一致。 */
    record QueueMember(String body, int attempt) {
    }
}
