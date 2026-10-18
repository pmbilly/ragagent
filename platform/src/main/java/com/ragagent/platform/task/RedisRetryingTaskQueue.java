package com.ragagent.platform.task;

import com.ragagent.platform.web.JsonMappers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 重试队列：把 {@link RedisTaskQueueCore} 的租约/续期/重投与
 * {@link RetryPolicy} 的重试预算拼成一个可复用的执行循环。
 *
 * <p>成员 JSON 固定为 {@code {"body":"...","attempt":1}}；业务队列只提供
 * key 前缀、任务名、重试预算与 handler。多实例语义由 core 保证（至少一次）。</p>
 */
public final class RedisRetryingTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisRetryingTaskQueue.class);
    private static final ObjectMapper MAPPER = JsonMappers.lenient();

    @FunctionalInterface
    public interface Handler {
        void handle(String body, boolean finalAttempt);
    }

    private final RedisTaskQueueCore core;
    private final String taskName;
    private final RetryPolicy policy;
    private final Handler handler;
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler;

    /** 测试钩子：覆盖重试延迟（秒）；{@code null} = 用 policy 公式。 */
    private volatile Long retryDelayOverrideSeconds;

    public RedisRetryingTaskQueue(StringRedisTemplate template, String keyPrefix, String taskName,
            RetryPolicy policy, Handler handler) {
        this.core = new RedisTaskQueueCore(template, keyPrefix);
        this.taskName = taskName;
        this.policy = policy;
        this.handler = handler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, taskName.replace(' ', '-') + "-renew");
            thread.setDaemon(true);
            return thread;
        });
        this.core.start(this::accept);
    }

    public void submit(String body) {
        submit(body, 0L);
    }

    public void submit(String body, long delayMillis) {
        long readyAt = System.currentTimeMillis() + Math.max(delayMillis, 0L);
        core.submit(toJson(new QueueMember(body, 1)), readyAt);
    }

    /** 供测试覆盖重试延迟（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        this.retryDelayOverrideSeconds = seconds;
    }

    /** 停掉调度、执行与续期资源（幂等）。 */
    public void shutdown() {
        core.shutdown();
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    private void accept(String member) {
        QueueMember queued;
        try {
            queued = MAPPER.readValue(member, QueueMember.class);
        } catch (Exception e) {
            log.warn("{} task: dropping corrupt member", taskName, e);
            core.complete(member);
            return;
        }
        worker.execute(() -> run(member, queued));
    }

    private void run(String member, QueueMember queued) {
        ScheduledFuture<?> renew = scheduler.scheduleAtFixedRate(
                () -> core.renew(member),
                core.renewIntervalMillis(),
                core.renewIntervalMillis(),
                TimeUnit.MILLISECONDS);
        RuntimeException failure = null;
        try {
            handler.handle(queued.body(), policy.isFinalAttempt(queued.attempt()));
        } catch (RuntimeException e) {
            failure = e;
        } finally {
            renew.cancel(false);
        }

        if (failure == null) {
            core.complete(member);
            return;
        }
        if (policy.isFinalAttempt(queued.attempt())) {
            log.warn("{} task gave up after {} attempts", taskName, queued.attempt(), failure);
            core.complete(member);
            return;
        }
        long delaySeconds = delaySeconds(queued.attempt());
        log.warn("{} task failed (attempt {}/{}), retrying in {}s",
                taskName, queued.attempt(), policy.maxRetry() + 1, delaySeconds, failure);
        core.requeue(member, toJson(new QueueMember(queued.body(), queued.attempt() + 1)),
                System.currentTimeMillis() + RedisTaskQueueCore.secondsToMillis(delaySeconds));
    }

    private long delaySeconds(int attempt) {
        Long override = retryDelayOverrideSeconds;
        return override != null ? Math.max(override, 0L) : policy.delaySeconds(attempt);
    }

    private static String toJson(QueueMember member) {
        try {
            return MAPPER.writeValueAsString(member);
        } catch (Exception e) {
            throw new IllegalArgumentException("task queue: marshal: " + e.getMessage(), e);
        }
    }

    /** 队列成员：任务体（已 JSON 化的载荷） + 当前尝试次数。 */
    record QueueMember(String body, int attempt) {
    }
}
