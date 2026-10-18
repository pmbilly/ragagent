package com.ragagent.knowledge.task;

import com.ragagent.knowledge.domain.ChunkExtractConsumer;
import com.ragagent.knowledge.domain.ExtractChunkPayload;
import com.ragagent.platform.task.InProcessTaskQueue;
import com.ragagent.platform.task.RetryPolicy;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * 进程内分块抽取队列：载荷序列化 + handler 交给 {@link InProcessTaskQueue}，
 * 重试预算 3 次、退避公式与 finalAttempt 语义由平台内核统一。
 */
@Component
public class InProcessChunkExtractTaskQueue implements ChunkExtractTaskQueue {

    static final int MAX_RETRY = 3;

    private final InProcessTaskQueue queue;

    public InProcessChunkExtractTaskQueue(ChunkExtractConsumer service) {
        this.queue = new InProcessTaskQueue(
                "chunk-extract",
                new RetryPolicy(MAX_RETRY),
                (body, finalAttempt) -> service.handleJson(body, finalAttempt));
    }

    @Override
    public void enqueue(ExtractChunkPayload payload) {
        String body = payload.toJson();
        try {
            queue.enqueue(body);
        } catch (RuntimeException e) {
            // 关闭中：投递失败必须上抛，让 fan-out 释放该分块的 finalizing 槽
            throw new IllegalStateException("chunk extract queue is shutting down", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        queue.shutdown();
    }
}
