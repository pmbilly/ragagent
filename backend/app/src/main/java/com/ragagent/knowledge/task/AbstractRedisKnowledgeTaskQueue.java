package com.ragagent.knowledge.task;

import com.ragagent.platform.task.RedisRetryingTaskQueue;
import com.ragagent.platform.task.RetryPolicy;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * knowledge 两个 Redis 任务队列（分块抽取 / 问题生成）的共享骨架。
 *
 * <p>两者契约同形：入队即异步执行、失败按 {@code n^4 + 15 + rand(30)*(n+1)} 秒
 * 退避、超过 {@link #MAX_RETRY} 次尝试后放弃并记日志、投递失败上抛。
 * 租约/续期/重投与重试循环由 {@link RedisRetryingTaskQueue} 统一承载；
 * 本类只保留任务名与执行体。</p>
 */
abstract class AbstractRedisKnowledgeTaskQueue {

    static final int MAX_RETRY = 3;

    private final RedisRetryingTaskQueue queue;

    AbstractRedisKnowledgeTaskQueue(StringRedisTemplate template, String keyPrefix) {
        this.queue = new RedisRetryingTaskQueue(
                template,
                keyPrefix,
                taskName(),
                new RetryPolicy(MAX_RETRY),
                this::execute);
    }

    /** 执行体（子类实现）；{@code finalAttempt} = 本次是最后一次尝试。 */
    abstract void execute(String body, boolean finalAttempt);

    /** 任务类别名（日志用）。 */
    abstract String taskName();

    /** 提交一个任务体（投递失败上抛，由子类决定包装方式）。 */
    void submit(String body) {
        queue.submit(body);
    }

    /** 供测试覆盖重试延迟（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        queue.setRetryDelayOverrideSeconds(seconds);
    }

    /** 停掉调度与执行资源（容器按 @Bean 推断的 shutdown 调用）。 */
    public void shutdown() {
        queue.shutdown();
    }

    /** 测试种子形状：与 {@code RedisRetryingTaskQueue} 的 member JSON 一致。 */
    record QueueMember(String body, int attempt) {
    }
}
