package com.ragagent.knowledge.task;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.knowledge.domain.QuestionBatchPayload;
import com.ragagent.knowledge.service.QuestionGenerationService;

/**
 * {@link QuestionGenerationTaskQueue} 的 <b>Redis</b> 实现（跨实例共享任务表）。
 *
 * <p>{@code knowledge.redis-enabled=true} 时由 {@code KnowledgeTaskQueueWiring}
 * 装配（{@code @Primary} 覆盖 {@link InProcessQuestionGenerationTaskQueue}）。
 * 语义细则与取舍见 {@link AbstractRedisKnowledgeTaskQueue}。</p>
 */
public class RedisQuestionGenerationTaskQueue extends AbstractRedisKnowledgeTaskQueue
        implements QuestionGenerationTaskQueue {

    private static final String KEY_PREFIX = "knowledge:task:qgen";

    private final QuestionGenerationService service;

    public RedisQuestionGenerationTaskQueue(StringRedisTemplate template,
            QuestionGenerationService service) {
        super(template, KEY_PREFIX);
        this.service = service;
    }

    @Override
    public void enqueue(QuestionBatchPayload payload) {
        try {
            submit(payload.toJson());
        } catch (RuntimeException e) {
            // 投递失败必须上抛，让 fan-out 释放该批占用的 finalizing 槽
            throw new IllegalStateException("question generation queue submit failed: " + e.getMessage(), e);
        }
    }

    @Override
    void execute(String body, boolean finalAttempt) {
        service.handleJson(body, finalAttempt);
    }

    @Override
    String taskName() {
        return "question generation";
    }
}
