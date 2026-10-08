package com.ragagent.datasource;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.ragagent.common.taskqueue.RedisTaskQueueCore;
import com.ragagent.datasource.domain.DataSourceSyncPayload;
import com.ragagent.tracing.langfuse.LangfuseTaskScope;

/**
 * {@link DataSourceSyncTaskQueue} 的 <b>Redis</b> 实现：跨实例共享任务表 +
 * <b>全局 TaskID 去重</b>。
 *
 * <p>{@code datasource.redis-enabled=true} 时由 {@code DataSourceTaskQueueWiring}
 * 装配（{@code @Primary} 覆盖 {@link InProcessDataSourceSyncTaskQueue}）。</p>
 *
 * <h2>保真的语义（与端口契约一致）</h2>
 * <ul>
 *   <li><b>TaskID 全局去重</b>：{@code SETNX datasource:sync:id:<taskId>} 跨实例唯一——
 *       调度 cron 按绝对墙钟触发，多副本会在同一分钟一起触发，TaskID 是"只有第一个赢"
 *       的那一层（进程内实现下每个副本各自触发一次同步，是已知的多实例缺陷）；</li>
 *   <li><b>重试预算 + 退避</b>：失败按 {@code n^4 + 15 + rand(30)*(n+1)} 秒重排回队列，
 *       {@code attempt > maxRetry} 后放弃（与进程内实现的循环重试等价，只是把
 *       "同线程 sleep 退避"换成"队列重排"，对调用方不可见）；</li>
 *   <li><b>任务超时</b>：独立线程执行 + {@code Future.get(timeout)}，
 *       到点中断执行线程（见 {@link Connector#sleep} 的取消语义）；</li>
 *   <li><b>失败要能报出来</b>：投递失败抛 {@link DataSourceSyncEnqueueException}
 *       （调用方把 sync_log 落成 failed + "enqueue failed: ..."）。</li>
 * </ul>
 *
 * <h2>与进程内实现的两点差异（有意）</h2>
 * <ul>
 *   <li>执行中的任务带租约（默认 3 分钟，每 1 分钟续期）；取走任务的实例崩溃后
 *       租约过期即被其它实例回收重投——至少一次语义（同步本身在 sync_log 与
 *       第一层 {@code hasRunningSync} 之外还有幂等设计）；</li>
 *   <li>进程下线时（执行线程被中断）不清理、不重排：任务留在执行中集合等租约回收，
 *       而不是就地放弃。</li>
 * </ul>
 */
public class RedisDataSourceSyncTaskQueue implements DataSourceSyncTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisDataSourceSyncTaskQueue.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 任务观测的 span/根名。 */
    static final String TASK_TYPE_DATASOURCE_SYNC = "datasource:sync";

    private static final String KEY_PREFIX = "datasource:sync";

    /** TaskID 去重键前缀。 */
    static final String ID_PREFIX = "datasource:sync:id:";

    private final RedisTaskQueueCore core;
    private final StringRedisTemplate template;
    private final ObjectProvider<DataSourceSyncHandler> handlerProvider;

    /** 执行器：每个尝试一个虚拟线程。 */
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    /** 续期调度器（长跑任务不被误回收）。 */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "datasource-sync-renew");
                t.setDaemon(true);
                return t;
            });

    /** 测试钩子：覆盖重试退避（秒）。{@code null} = 用默认公式。 */
    private volatile Long retryDelayOverrideSeconds;

    public RedisDataSourceSyncTaskQueue(StringRedisTemplate template,
            ObjectProvider<DataSourceSyncHandler> handlerProvider) {
        this.core = new RedisTaskQueueCore(template, KEY_PREFIX);
        this.template = template;
        this.handlerProvider = handlerProvider;
        this.core.start(this::accept);
    }

    /** 供测试覆盖重试退避（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        this.retryDelayOverrideSeconds = seconds;
    }

    @Override
    public Outcome enqueue(DataSourceSyncPayload payload, String taskId, int maxRetry,
                           Duration timeout) {
        long timeoutMillis = timeout == null || timeout.isNegative() ? 0L : timeout.toMillis();
        String idKey = ID_PREFIX + taskId;
        // 跨实例 TaskID 去重（调度器第 2 层去重的全部）
        Boolean reserved;
        try {
            reserved = template.opsForValue().setIfAbsent(idKey, "1",
                    Duration.ofSeconds(taskIdTtlSeconds(maxRetry, timeoutMillis)));
        } catch (RuntimeException e) {
            throw new DataSourceSyncEnqueueException(
                    "sync queue taskId reserve failed: " + e.getMessage(), e);
        }
        if (!Boolean.TRUE.equals(reserved)) {
            return Outcome.TASK_ID_CONFLICT;
        }
        String member = toJson(new QueueMember(payload.toJson(), taskId, maxRetry,
                timeoutMillis, 1));
        try {
            core.submit(member, System.currentTimeMillis());
        } catch (RuntimeException e) {
            // 入队失败：释放刚占的 TaskID，让调用方的重试可以再入队
            releaseTaskId(taskId);
            throw new DataSourceSyncEnqueueException(
                    "sync queue submit failed: " + e.getMessage(), e);
        }
        return Outcome.ENQUEUED;
    }

    /** 供测试/关停使用：停止调度并释放资源。 */
    public void shutdown() {
        core.shutdown();
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    // ═══════════════════════════════════════════════════════════════
    // 执行 / 重试
    // ═══════════════════════════════════════════════════════════════

    private void accept(String member) {
        QueueMember queued;
        try {
            queued = MAPPER.readValue(member, QueueMember.class);
        } catch (Exception e) {
            log.warn("[DataSourceSyncQueue] dropping corrupt task member: {}", e.toString());
            core.complete(member);
            return;
        }
        worker.execute(() -> run(member, queued));
    }

    private void run(String member, QueueMember queued) {
        ScheduledFuture<?> renew = scheduler.scheduleAtFixedRate(() -> core.renew(member),
                core.renewIntervalMillis(), core.renewIntervalMillis(), TimeUnit.MILLISECONDS);
        Attempt outcome;
        try {
            outcome = runOnce(queued);
        } finally {
            renew.cancel(false);
        }

        if (outcome == Attempt.SUCCESS) {
            core.complete(member);
            releaseTaskId(queued.taskId());
            return;
        }
        if (outcome == Attempt.INTERRUPTED) {
            // 进程下线：不清理、不重排——留在执行中集合等租约回收，由其它实例重投
            return;
        }
        // Attempt.FAILED
        if (queued.attempt() > queued.maxRetry()) {
            log.error("[DataSourceSyncQueue] task {} gave up after {} attempts",
                    queued.taskId(), queued.attempt());
            core.complete(member);
            releaseTaskId(queued.taskId());
            return;
        }
        long backoffSeconds = retryDelaySeconds(queued.attempt());
        log.info("[DataSourceSyncQueue] task {} attempt {}/{} failed, retrying in {}s",
                queued.taskId(), queued.attempt(), queued.maxRetry() + 1, backoffSeconds);
        core.requeue(member, toJson(new QueueMember(queued.body(), queued.taskId(),
                        queued.maxRetry(), queued.timeoutMillis(), queued.attempt() + 1)),
                System.currentTimeMillis() + RedisTaskQueueCore.secondsToMillis(backoffSeconds));
    }

    /** 一次尝试的结果：成功 / 可重试失败 / 被要求停止。 */
    private enum Attempt {
        SUCCESS, FAILED, INTERRUPTED
    }

    private Attempt runOnce(QueueMember queued) {
        DataSourceSyncPayload payload;
        try {
            payload = DataSourceSyncPayload.fromJson(queued.body());
        } catch (RuntimeException e) {
            // 载荷坏掉是确定性的失败：重试多少次都一样，直接放弃
            log.error("[DataSourceSyncQueue] undecodable payload: {}", e.getMessage());
            return Attempt.SUCCESS;
        }

        DataSourceSyncHandler handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            log.error("[DataSourceSyncQueue] no DataSourceSyncHandler bean registered; dropping task");
            return Attempt.SUCCESS;
        }

        // 独立线程执行，这样超时能靠中断取消
        String body = queued.body();
        Future<?> future;
        try {
            // 任务侧观测：在 worker 线程上续接上游
            // trace（无则开独立根），处理体包在 asynq.<type> span 内
            future = worker.submit(() -> {
                try (LangfuseTaskScope scope =
                             LangfuseTaskScope.start(
                                     TASK_TYPE_DATASOURCE_SYNC, payload.tracing(),
                                     java.util.Map.of("data_source_id", payload.dataSourceId(),
                                             "sync_log_id", payload.syncLogId()),
                                     LangfuseTaskScope
                                             .previewPayload(body))) {
                    handler.handle(payload);
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("[DataSourceSyncQueue] executor shut down; dropping task");
            return Attempt.SUCCESS;
        }

        try {
            if (queued.timeoutMillis() <= 0L) {
                future.get();
            } else {
                future.get(queued.timeoutMillis(), TimeUnit.MILLISECONDS);
            }
            return Attempt.SUCCESS;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("[DataSourceSyncQueue] task timed out after {}ms", queued.timeoutMillis());
            return Attempt.FAILED;
        } catch (ExecutionException e) {
            log.error("[DataSourceSyncQueue] task failed: {}",
                    e.getCause() == null ? e.getMessage() : e.getCause().getMessage());
            return Attempt.FAILED;
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return Attempt.INTERRUPTED;   // 被要求停止：不清理、不重排
        }
    }

    private void releaseTaskId(String taskId) {
        try {
            template.delete(ID_PREFIX + taskId);
        } catch (RuntimeException e) {
            // 删不掉就等 TTL 兜底
            log.warn("[DataSourceSyncQueue] release taskId failed: {}", e.toString());
        }
    }

    /**
     * TaskID 去重键的 TTL：终态会主动删键；TTL 是实例崩溃时的兜底释放。
     * 覆盖全部重试的总时长上界：{@code (maxRetry+1) * 单次超时 + 30 分钟}，下限 1 小时。
     */
    static long taskIdTtlSeconds(int maxRetry, long timeoutMillis) {
        long timeoutSeconds = Math.max(0, timeoutMillis / 1000);
        return Math.max(3600, (Math.max(0, maxRetry) + 1) * timeoutSeconds + 1800);
    }

    /**
     * 默认重试退避 {@code n^4 + 15 + rand(30)*(n+1)} 秒
     * （wiki / memory 两份实现用的是同一公式）。
     */
    private long retryDelaySeconds(int attempt) {
        Long override = retryDelayOverrideSeconds;
        if (override != null) {
            return Math.max(override, 0L);
        }
        long n = attempt;
        return n * n * n * n + 15 + ThreadLocalRandom.current().nextInt(30) * (n + 1);
    }

    private static String toJson(QueueMember member) {
        try {
            return MAPPER.writeValueAsString(member);
        } catch (Exception e) {
            throw new IllegalArgumentException("datasource sync queue: marshal: " + e.getMessage(), e);
        }
    }

    /** 队列成员：任务体 + 去重与执行参数 + 当前尝试次数。 */
    record QueueMember(String body, String taskId, int maxRetry, long timeoutMillis, int attempt) {
    }
}
