package com.ragagent.wiki.service.ingest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.ragagent.wiki.mapper.TaskDeadLetterRepository;

/**
 * {@link WikiIngestTaskQueue} 的<b>进程内</b>实现：虚拟线程队列
 * （与 {@code KnowledgeProcessWorker} 同模式）。
 *
 * <p>保留以下行为：</p>
 * <ul>
 *   <li><b>延迟投递</b>：{@link java.util.concurrent.ScheduledExecutorService}。</li>
 *   <li><b>TaskID 合并</b>：{@code activeTaskIds} 的 {@code putIfAbsent}。
 *       条目从入队一直持有到<b>任务执行完毕</b>（含重试）——ID 被 pending
 *       或 active 的任务占用，同 ID 的后续入队一律合并。finalize 的 {@code scheduleFinalize}
 *       正依赖这一点：运行中的 finalize 仍占着 ID，因此 {@code scheduleFinalizeRetry}
 *       刻意不带 TaskID（否则唯一的重试会被自己合并掉）。</li>
 *   <li><b>重试</b>：失败后按 {@link WikiIngestTaskRunner#retryDelaySeconds} 重排，直到
 *       {@code maxRetry} 次用尽；耗尽后写入死信档案。</li>
 *   <li><b>超时</b>：到点<b>中断执行线程</b>；中止与脱钩清理对应
 *       {@link WikiIngestService#cleanupContext}。</li>
 * </ul>
 *
 * <p><b>⚠️ 多实例差异（必须知道）</b>：任务表、TaskID 合并、重试全部只在<b>单个 JVM</b>
 * 内；{@link RedisWikiIngestTaskQueue} 才有跨副本共享的任务表与全局唯一 TaskID
 * （{@code wiki.redis-enabled=true} 时装配）。因此：</p>
 * <ul>
 *   <li>本实现下多副本部署会各自触发各自的批次——但因为 ingest 的待办队列是
 *       <b>数据库</b>里的 {@code task_pending_ops} 且认领靠条件更新，
 *       重复触发只会让后到的那个看到空队列而空转，不会重复处理文档；</li>
 *   <li>finalize 的"窗口内合并成一次索引重建"在多副本下会退化成"每副本一次"，
 *       即索引重建次数变多（结果仍收敛，只是更贵）。</li>
 * </ul>
 * <p>执行/归档的传输无关逻辑在 {@link WikiIngestTaskRunner}（与 Redis 实现共用）。</p>
 */
@Component
public class InProcessWikiIngestTaskQueue implements WikiIngestTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(InProcessWikiIngestTaskQueue.class);

    /** 调度器：只负责"到点把任务丢出去"，本身不跑业务代码。 */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wiki-ingest-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** 执行器：每个任务一个虚拟线程；并发总量由 KB 的在途上限约束。 */
    private final java.util.concurrent.ExecutorService worker =
            Executors.newVirtualThreadPerTaskExecutor();

    /** TaskID → 占用标记；在任务终态（成功/重试耗尽）时移除。 */
    private final Map<String, Boolean> activeTaskIds = new ConcurrentHashMap<>();

    /** 传输无关的执行/归档逻辑（与 Redis 队列共用）。 */
    private final WikiIngestTaskRunner runner;

    /**
     * 测试钩子：覆盖重试延迟（秒）。{@code null} = 用
     * {@link WikiIngestTaskRunner#retryDelaySeconds} 的默认公式。
     *
     * <p>存在的理由很实际：默认退避是 {@code n^4 + 15 + rand(30)*(n+1)} 秒，
     * 第一次重试就要等 15–45 秒。若不覆盖，重试与死信归档这两条路径在单测里
     * 根本跑不动，于是只能靠代码审阅——那等于没测。</p>
     */
    private volatile Long retryDelayOverrideSeconds;

    /** 供测试覆盖重试延迟（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        this.retryDelayOverrideSeconds = seconds;
    }

    public InProcessWikiIngestTaskQueue(
            ObjectProvider<WikiIngestTaskHandler> handlerProvider,
            ObjectProvider<TaskDeadLetterRepository> deadLetterProvider,
            ObjectProvider<WikiIngestService> ingestServiceProvider) {
        this.runner = new WikiIngestTaskRunner(handlerProvider, deadLetterProvider, ingestServiceProvider);
    }

    @Override
    public boolean enqueue(WikiIngestTask task) {
        if (task.hasTaskId() && activeTaskIds.putIfAbsent(task.taskId(), Boolean.TRUE) != null) {
            // 已有同 id 的任务在排队/运行 → 合并（TaskID 冲突语义）
            log.debug("wiki task queue: coalesced {} (taskId={})", task.type(), task.taskId());
            return false;
        }
        // 队列没有优先级概念；这里 FIFO + 单线程调度，
        // 因此"同时到期的多个任务"的执行顺序与入队顺序一致。
        try {
            if (task.processIn().isZero() || task.processIn().isNegative()) {
                worker.execute(() -> run(task, 1));
            } else {
                scheduler.schedule(() -> worker.execute(() -> run(task, 1)),
                        task.processIn().toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 调度器被关停（应用正在下线）：释放 TaskID，让重启后的新进程可以重新调度
            releaseTaskId(task);
            log.warn("wiki task queue: rejected {} during shutdown", task.type(), e);
            return false;
        }
        return true;
    }

    /** 供测试/关停使用：停止调度并释放资源。 */
    public void shutdown() {
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    /** 当前占用的 TaskID 数（测试/可观测）。 */
    public int activeTaskIdCount() {
        return activeTaskIds.size();
    }

    // ═══════════════════════════════════════════════════════════════
    // 执行 / 重试
    // ═══════════════════════════════════════════════════════════════

    /**
     * 执行一次任务，失败则按重试策略重排。
     *
     * @param attempt 第几次尝试（从 1 起）。{@code attempt-1} 即已重试次数，
     *                作为 {@link WikiIngestTaskRunner#retryDelaySeconds} 的
     *                {@code alreadyRetried} 传入。
     */
    private void run(WikiIngestTask task, int attempt) {
        Throwable failure = null;
        Thread workerThread = Thread.currentThread();
        long timeoutMillis = task.timeout() == null ? 0 : task.timeout().toMillis();

        // 超时看门狗：到点中断执行线程
        ScheduledFuture<?> watchdog = null;
        if (timeoutMillis > 0) {
            watchdog = scheduler.schedule(() -> {
                log.warn("wiki task queue: {} exceeded timeout {}s, interrupting",
                        task.type(), timeoutMillis / 1000);
                workerThread.interrupt();
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        }

        try {
            // 终态（成功 / handler 缺失的丢弃）返回 null；失败返回原因，走重试/死信
            failure = runner.dispatchOnce(task);
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            // 中断位可能被看门狗置上；清掉它，避免污染后续复用本线程的调度任务
            Thread.interrupted();
        }

        if (failure == null) {
            releaseTaskId(task);
            return;
        }

        int retriesUsed = attempt - 1;
        if (retriesUsed >= task.maxRetry()) {
            log.warn("wiki task queue: {} exhausted {} retries, archiving to dead letters",
                    task.type(), task.maxRetry(), failure);
            runner.archive(task, failure, attempt);
            releaseTaskId(task);
            return;
        }

        Long override = retryDelayOverrideSeconds;
        long delaySeconds = override != null
                ? override
                : WikiIngestTaskRunner.retryDelaySeconds(retriesUsed + 1, failure);
        log.warn("wiki task queue: {} failed (attempt {}/{}), retrying in {}s",
                task.type(), attempt, task.maxRetry() + 1, delaySeconds, failure);
        scheduler.schedule(() -> worker.execute(() -> run(task, attempt + 1)),
                delaySeconds, TimeUnit.SECONDS);
    }

    private void releaseTaskId(WikiIngestTask task) {
        if (task.hasTaskId()) {
            activeTaskIds.remove(task.taskId());
        }
    }

    /**
     * 重试延迟：锁冲突走固定的 15 秒，其余走默认退避公式。
     * 公式与理由见 {@link WikiIngestTaskRunner#retryDelaySeconds}（测试经此入口钉桩）。
     */
    static long retryDelaySeconds(int alreadyRetried, Throwable failure) {
        return WikiIngestTaskRunner.retryDelaySeconds(alreadyRetried, failure);
    }
}
