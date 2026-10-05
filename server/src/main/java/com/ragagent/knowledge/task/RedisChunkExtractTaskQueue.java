package com.ragagent.knowledge.task;

import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.knowledge.domain.ExtractChunkPayload;
import com.ragagent.knowledge.service.ChunkExtractService;

/**
 * {@link ChunkExtractTaskQueue} 的 <b>Redis</b> 实现（跨实例共享任务表）。
 *
 * <p>{@code knowledge.redis-enabled=true} 时由 {@code KnowledgeTaskQueueWiring}
 * 装配（{@code @Primary} 覆盖 {@link InProcessChunkExtractTaskQueue}）。
 * 语义细则与取舍见 {@link AbstractRedisKnowledgeTaskQueue}。</p>
 */
public class RedisChunkExtractTaskQueue extends AbstractRedisKnowledgeTaskQueue
        implements ChunkExtractTaskQueue {

    private static final String KEY_PREFIX = "knowledge:task:chunk";

    private final ChunkExtractService service;

    public RedisChunkExtractTaskQueue(StringRedisTemplate template, ChunkExtractService service) {
        super(template, KEY_PREFIX);
        this.service = service;
    }

    @Override
    public void enqueue(ExtractChunkPayload payload) {
        try {
            submit(payload.toJson());
        } catch (RuntimeException e) {
            // 投递失败必须上抛，让 fan-out 释放该分块占用的 finalizing 槽
            throw new IllegalStateException("chunk extract queue submit failed: " + e.getMessage(), e);
        }
    }

    @Override
    void execute(String body, boolean finalAttempt) {
        service.handleJson(body);
    }

    @Override
    String taskName() {
        return "chunk extract";
    }
}
