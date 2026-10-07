package com.ragagent.knowledge.task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.common.taskqueue.RedisTaskQueueCore;

/**
 * knowledge 两个 Redis 任务队列（分块抽取 / 问题生成）的共享骨架。
 *
 * <p>两者契约同形：入队即异步执行、失败按 {@code n^4 + 15 + rand(30)*(n+1)} 秒
 * 退避、超过 {@link #MAX_RETRY} 次尝试后放弃并记日志、投递失败上抛。
 * Redis 版把任务表放到跨实例共享的 ready/proc 双 ZSET
 * （见 {@link RedisTaskQueueCore}）：任何实例都可执行，取走任务的实例崩溃后
 * 租约过期即被回收重投（至少一次语义——重复执行由下游的 finalizing 槽与
 * Housekeeping 判死兜底）。</p>
 */
abstract class AbstractRedisKnowledgeTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(AbstractRedisKnowledgeTaskQueue.class);

    static final int MAX_RETRY = 3;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RedisTaskQueueCore core;

    /** 执行器：每个任务一个虚拟线程（与进程内实现同款纪律）。 */
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    /** 续期调度器（长跑任务不被误回收）。 */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "knowledge-task-renew");
                t.setDaemon(true);
                return t;
            });

    /** 测试可覆盖重试延迟（秒）。 */
    volatile Long retryDelayOverrideSeconds;

    AbstractRedisKnowledgeTaskQueue(StringRedisTemplate template, String keyPrefix) {
        this.core = new RedisTaskQueueCore(template, keyPrefix);
        this.core.start(this::accept);
    }

    /** 执行体（子类实现）；{@code finalAttempt} = 本次是最后一次尝试。 */
    abstract void execute(String body, boolean finalAttempt);

    /** 任务类别名（日志用）。 */
    abstract String taskName();

    /** 提交一个任务体（投递失败上抛，由子类决定包装方式）。 */
    void submit(String body) {
        core.submit(toJson(new QueueMember(body, 1)), System.currentTimeMillis());
    }

    /** 供测试覆盖重试延迟（秒）。 */
    void setRetryDelayOverrideSeconds(Long seconds) {
        this.retryDelayOverrideSeconds = seconds;
    }

    /** 停掉调度与执行资源（容器按 @Bean 推断的 shutdown 调用）。 */
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
            log.warn("{} task: dropping corrupt member: {}", taskName(), e.toString());
            core.complete(member);
            return;
        }
        worker.execute(() -> run(member, queued));
    }

    private void run(String member, QueueMember queued) {
        ScheduledFuture<?> renew = scheduler.scheduleAtFixedRate(() -> core.renew(member),
                core.renewIntervalMillis(), core.renewIntervalMillis(), TimeUnit.MILLISECONDS);
        Throwable failure = null;
        try {
            execute(queued.body(), queued.attempt() > MAX_RETRY);
        } catch (Throwable t) {
            failure = t;
        } finally {
            renew.cancel(false);
        }

        if (failure == null) {
            core.complete(member);
            return;
        }
        if (queued.attempt() > MAX_RETRY) {
            log.warn("{} task gave up after {} attempts: {}",
                    taskName(), queued.attempt(), failure.toString());
            core.complete(member);
            return;
        }
        long delaySeconds = retryDelaySeconds(queued.attempt());
        log.warn("{} task failed (attempt {}/{}), retrying in {}s: {}",
                taskName(), queued.attempt(), MAX_RETRY + 1, delaySeconds, failure.toString());
        core.requeue(member, toJson(new QueueMember(queued.body(), queued.attempt() + 1)),
                System.currentTimeMillis() + RedisTaskQueueCore.secondsToMillis(delaySeconds));
    }

    /** 与进程内实现同款退避公式（测试可覆盖）。 */
    long retryDelaySeconds(int attempt) {
        if (retryDelayOverrideSeconds != null) {
            return retryDelayOverrideSeconds;
        }
        long base = (long) Math.pow(attempt, 4) + 15;
        long jitter = (long) (ThreadLocalRandom.current().nextDouble() * 30 * (attempt + 1));
        return base + jitter;
    }

    private static String toJson(QueueMember member) {
        try {
            return MAPPER.writeValueAsString(member);
        } catch (Exception e) {
            throw new IllegalArgumentException("knowledge task queue: marshal: " + e.getMessage(), e);
        }
    }

    /** 队列成员：任务体（已 JSON 化的载荷） + 当前尝试次数。 */
    record QueueMember(String body, int attempt) {
    }
}
