package com.ragagent.memory.service;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.common.taskqueue.RedisTaskQueueCore;
import com.ragagent.tracing.langfuse.LangfuseTaskScope;

/**
 * {@link MemoryExtractTaskQueue} 的 <b>Redis</b> 实现（跨实例共享任务表）。
 *
 * <p>{@code memory.redis-enabled=true} 时由 {@code MemoryTaskQueueWiring}
 * 装配（{@code @Primary} 覆盖 {@link InProcessMemoryExtractTaskQueue}）。
 * 保留与进程内实现相同的语义：延迟投递、重试预算（最多重试
 * {@link #MAX_RETRY} 次）、退避公式 {@code n^4 + 15 + rand(30)*(n+1)} 秒、
 * "一个任务一个虚拟线程"的执行模型、投递失败上抛（调用方释放在途槽位）。</p>
 *
 * <p>跨实例语义由 {@link RedisTaskQueueCore} 提供：任务表共享；取走任务的实例
 * 崩溃后租约过期即被回收重投。重复触发不会导致重复蒸馏——主体侧
 * {@code claimPendingSessions} 的租约保证同一时刻只有一个 worker 持有会话
 * （后到者看到空队列或租约忙而空转）。</p>
 */
public class RedisMemoryExtractTaskQueue implements MemoryExtractTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisMemoryExtractTaskQueue.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 每个任务最多重试 2 次，之后放弃（与进程内实现同值）。 */
    static final int MAX_RETRY = 2;

    /** 任务类型，也是任务观测的 span/根名。 */
    static final String TASK_TYPE_MEMORY_EXTRACT = "memory:extract";

    private static final String KEY_PREFIX = "memory:task:extract";

    private final RedisTaskQueueCore core;

    private final ObjectProvider<MemoryExtractionService> handlerProvider;

    /** 执行器：每个任务一个虚拟线程。 */
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    /** 续期调度器（长跑任务不被误回收）。 */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "memory-extract-redis-renew");
                t.setDaemon(true);
                return t;
            });

    /** 测试钩子：覆盖重试延迟（秒）。{@code null} = 用默认公式。 */
    private volatile Long retryDelayOverrideSeconds;

    public RedisMemoryExtractTaskQueue(StringRedisTemplate template,
            ObjectProvider<MemoryExtractionService> handlerProvider) {
        this.core = new RedisTaskQueueCore(template, KEY_PREFIX);
        this.handlerProvider = handlerProvider;
        this.core.start(this::accept);
    }

    /** 供测试覆盖重试延迟（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        this.retryDelayOverrideSeconds = seconds;
    }

    @Override
    public void enqueue(MemoryExtractPayload payload, Duration delay) {
        long delayMillis = delay == null ? 0L : Math.max(delay.toMillis(), 0L);
        try {
            core.submit(toJson(new QueueMember(payload.toJson(), 1)),
                    System.currentTimeMillis() + delayMillis);
        } catch (RuntimeException e) {
            // 投递失败要报给调用方，让它释放在途槽位（契约）
            throw new IllegalStateException("memory extract queue submit failed: " + e.getMessage(), e);
        }
    }

    /** 供测试/关停使用：停止调度并释放资源。 */
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
            log.warn("memory: dropping corrupt task member: {}", e.toString());
            core.complete(member);
            return;
        }
        worker.execute(() -> run(member, queued));
    }

    /**
     * 跑一次任务，失败按退避公式重排，直到重试预算用尽。
     *
     * <p>handler 正常返回（不抛异常）时任务即算完成，重试只针对异常。
     * 注意 handler 内部的"正常返回"（例如"负载没有作用域"、"主体被禁用"、
     * "租约忙"）都<b>不是</b>异常，所以不会触发重试。</p>
     */
    private void run(String member, QueueMember queued) {
        MemoryExtractionService handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            log.warn("memory: no extraction handler bean, dropping task");
            core.complete(member);
            return;
        }
        ScheduledFuture<?> renew = scheduler.scheduleAtFixedRate(() -> core.renew(member),
                core.renewIntervalMillis(), core.renewIntervalMillis(), TimeUnit.MILLISECONDS);
        Exception failure = null;
        try {
            MemoryExtractPayload payload = MemoryExtractPayload.fromJson(queued.body());
            // 任务侧观测——负载带 traceparent 就续接
            // 上游 trace，否则以任务类型开独立根；处理体包在同名 span 内。
            try (LangfuseTaskScope scope =
                         LangfuseTaskScope.start(
                                 TASK_TYPE_MEMORY_EXTRACT, payload.tracing(),
                                 java.util.Map.of("subject_id", payload.subjectId(),
                                         "message_id", payload.messageId()),
                                 LangfuseTaskScope
                                         .previewPayload(queued.body()))) {
                handler.handle(payload);
            }
        } catch (Exception e) {
            failure = e;
        } finally {
            renew.cancel(false);
        }

        if (failure == null) {
            core.complete(member);
            return;
        }
        if (queued.attempt() > MAX_RETRY) {
            log.warn("memory: extraction task gave up after {} attempts: {}",
                    queued.attempt(), failure.toString());
            core.complete(member);
            return;
        }
        long delaySeconds = retryDelaySeconds(queued.attempt());
        log.warn("memory: extraction task failed (attempt {}/{}), retrying in {}s: {}",
                queued.attempt(), MAX_RETRY + 1, delaySeconds, failure.toString());
        core.requeue(member, toJson(new QueueMember(queued.body(), queued.attempt() + 1)),
                System.currentTimeMillis() + RedisTaskQueueCore.secondsToMillis(delaySeconds));
    }

    /**
     * 默认重试延迟 {@code n^4 + 15 + rand(30)*(n+1)} 秒
     * （{@code n} 是从 1 开始的第几次失败；与进程内实现同公式）。
     */
    long retryDelaySeconds(int attempt) {
        Long override = retryDelayOverrideSeconds;
        if (override != null) {
            return override;
        }
        long n = attempt;
        return n * n * n * n + 15 + ThreadLocalRandom.current().nextLong(30) * (n + 1);
    }

    private static String toJson(QueueMember member) {
        try {
            return MAPPER.writeValueAsString(member);
        } catch (Exception e) {
            throw new IllegalArgumentException("memory task queue: marshal: " + e.getMessage(), e);
        }
    }

    /** 队列成员：任务体（已 JSON 化的载荷） + 当前尝试次数。 */
    record QueueMember(String body, int attempt) {
    }
}
