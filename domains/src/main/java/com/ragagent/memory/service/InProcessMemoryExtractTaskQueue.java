package com.ragagent.memory.service;

import com.ragagent.platform.task.InProcessTaskQueue;
import com.ragagent.platform.task.RetryPolicy;
import com.ragagent.platform.tracing.langfuse.LangfuseTaskScope;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@link MemoryExtractTaskQueue} 的进程内实现：延迟投递、重试预算 2 次、
 * 退避与 finalAttempt 语义交给 {@link InProcessTaskQueue}。
 *
 * <p>多实例下队列只在单 JVM 内；重复触发由主体侧的租约认领兜底。
 * handler 用 {@link ObjectProvider} 惰性取用，避免与 {@code MemoryExtractionService} 成环。</p>
 */
@Component
public class InProcessMemoryExtractTaskQueue implements MemoryExtractTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(InProcessMemoryExtractTaskQueue.class);

    /** 每个任务最多重试 2 次，之后放弃。 */
    static final int MAX_RETRY = 2;

    /** 任务类型，也是任务观测的 span/根名。 */
    static final String TASK_TYPE_MEMORY_EXTRACT = "memory:extract";

    private final ObjectProvider<MemoryExtractionService> handlerProvider;
    private final InProcessTaskQueue queue;

    public InProcessMemoryExtractTaskQueue(ObjectProvider<MemoryExtractionService> handlerProvider) {
        this.handlerProvider = handlerProvider;
        this.queue = new InProcessTaskQueue("memory-extract", new RetryPolicy(MAX_RETRY), this::run);
    }

    @Override
    public void enqueue(MemoryExtractPayload payload, Duration delay) {
        String body = payload.toJson();
        long delayMillis = delay == null ? 0L : Math.max(delay.toMillis(), 0L);
        try {
            queue.enqueue(body, delayMillis);
        } catch (RuntimeException e) {
            // 调度器已关停（应用正在下线）：把失败报给调用方，让它释放在途槽位。
            throw new IllegalStateException("memory extract queue is shut down", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        queue.shutdown();
    }

    private void run(String body, boolean finalAttempt) {
        MemoryExtractionService handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            log.warn("memory: no extraction handler bean, dropping task");
            return;
        }
        MemoryExtractPayload payload = MemoryExtractPayload.fromJson(body);
        // 任务侧观测：负载带 traceparent 就续接上游 trace，否则以任务类型开独立根。
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
}
