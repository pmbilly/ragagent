package com.ragagent.knowledge.task;

import com.ragagent.knowledge.domain.QuestionBatchPayload;
import com.ragagent.knowledge.domain.QuestionGenerationConsumer;
import com.ragagent.platform.task.InProcessTaskQueue;
import com.ragagent.platform.task.RetryPolicy;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * 进程内问题生成队列：载荷序列化 + handler 交给 {@link InProcessTaskQueue}，
 * 重试预算 3 次、退避公式与 finalAttempt 语义由平台内核统一。
 */
@Component
public class InProcessQuestionGenerationTaskQueue implements QuestionGenerationTaskQueue {

    static final int MAX_RETRY = 3;

    private final InProcessTaskQueue queue;

    public InProcessQuestionGenerationTaskQueue(QuestionGenerationConsumer service) {
        this.queue = new InProcessTaskQueue(
                "question-generation",
                new RetryPolicy(MAX_RETRY),
                (body, finalAttempt) -> service.handleJson(body, finalAttempt));
    }

    @Override
    public void enqueue(QuestionBatchPayload payload) {
        String body = payload.toJson();
        try {
            queue.enqueue(body);
        } catch (RuntimeException e) {
            // 关闭中：投递失败必须上抛，让 fan-out 释放该批的 finalizing 槽
            throw new IllegalStateException("question generation queue is shutting down", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        queue.shutdown();
    }
}
