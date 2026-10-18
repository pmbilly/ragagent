package com.ragagent.platform.task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内任务队列：一个任务一个虚拟线程，失败按 {@link RetryPolicy} 退避，支持延迟投递。
 *
 * <p>业务域只保留三样东西：队列名、载荷序列化、handler；重试/调度/执行器生命周期在本类。
 * 多实例语义不在这里——需要跨副本共享任务表时用 Redis 队列。</p>
 *
 * <p>关停语义：{@link #enqueue} 在 executor 已关停时抛 {@link RejectedExecutionException}，
 * 调用方据此释放在途槽位；延迟任务与重试在关停时记录日志并丢弃。</p>
 */
public final class InProcessTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(InProcessTaskQueue.class);

    @FunctionalInterface
    public interface Handler {
        void handle(String body, boolean finalAttempt);
    }

    private final String name;
    private final RetryPolicy policy;
    private final Handler handler;
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler;

    public InProcessTaskQueue(String name, RetryPolicy policy, Handler handler) {
        this.name = name;
        this.policy = policy;
        this.handler = handler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, name + "-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 立即投递；executor 已关停时抛 {@link RejectedExecutionException}。 */
    public void enqueue(String body) {
        worker.execute(() -> run(body, 1));
    }

    /** 延迟投递；{@code delayMillis <= 0} 等价于立即投递。 */
    public void enqueue(String body, long delayMillis) {
        if (delayMillis <= 0L) {
            enqueue(body);
            return;
        }
        scheduler.schedule(() -> dispatchScheduled(body, 1), delayMillis, TimeUnit.MILLISECONDS);
    }

    /** 停掉执行器与调度器（幂等）。 */
    public void shutdown() {
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    private void run(String body, int attempt) {
        try {
            handler.handle(body, policy.isFinalAttempt(attempt));
        } catch (RuntimeException e) {
            if (policy.isFinalAttempt(attempt)) {
                log.warn("{} task gave up after {} attempts", name, attempt, e);
                return;
            }
            long delaySeconds = policy.delaySeconds(attempt);
            log.warn("{} task failed (attempt {}/{}), retrying in {}s",
                    name, attempt, policy.maxRetry() + 1, delaySeconds, e);
            try {
                scheduler.schedule(() -> dispatchScheduled(body, attempt + 1),
                        delaySeconds, TimeUnit.SECONDS);
            } catch (RejectedExecutionException rejected) {
                log.warn("{} retry rejected during shutdown", name);
            }
        }
    }

    private void dispatchScheduled(String body, int attempt) {
        try {
            worker.execute(() -> run(body, attempt));
        } catch (RejectedExecutionException e) {
            log.warn("{} enqueue rejected during shutdown", name);
        }
    }
}
