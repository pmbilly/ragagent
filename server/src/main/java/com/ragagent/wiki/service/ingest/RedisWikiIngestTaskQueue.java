package com.ragagent.wiki.service.ingest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link WikiIngestTaskQueue} 的 <b>Redis</b> 实现：跨实例共享的任务表
 * （对齐 Go 端 asynq 队列的角色）。{@code wiki.redis-enabled=true} 时由
 * {@code WikiRedisWiring} 装配（{@code @Primary} 覆盖
 * {@link InProcessWikiIngestTaskQueue}）。
 *
 * <h2>Redis 结构</h2>
 * <ul>
 *   <li>{@code wiki:task:ready}（ZSET）：成员 = 任务 JSON，score = 可执行时刻；</li>
 *   <li>{@code wiki:task:proc}（ZSET）：成员 = 任务 JSON，score = 租约到期时刻
 *       （执行中集合，崩溃回收的依据）；</li>
 *   <li>{@code wiki:task:id:<taskId>}（STRING NX EX）：TaskID 去重（跨实例合并）。</li>
 * </ul>
 *
 * <h2>保真的语义（与 {@link WikiIngestTaskQueue} 的契约一致）</h2>
 * <ul>
 *   <li><b>延迟投递</b>：ready 的 score 即"最早可执行时刻"；</li>
 *   <li><b>TaskID 合并（跨实例）</b>：SETNX 全局唯一——finalize 的"窗口内合并成一次"
 *       在多副本下仍然成立（进程内实现会退化成每副本一次）；终态主动删键，
 *       实例崩溃时由 TTL（单次超时 + 30 分钟，下限 1 小时）兜底释放；</li>
 *   <li><b>重试</b>：失败按 {@link WikiIngestTaskRunner#retryDelaySeconds} 重排回 ready；
 *       耗尽后归档死信（逻辑与进程内实现共用 {@link WikiIngestTaskRunner}）；</li>
 *   <li><b>超时</b>：到点中断执行线程（本地看门狗）；</li>
 *   <li><b>崩溃恢复</b>：执行中的任务是带租约的（默认 3 分钟，每 1 分钟续期）；
 *       取任务的实例崩溃后，租约过期即被其它实例回收重投——至少一次语义，
 *       重复执行由 {@code task_pending_ops} 的行级认领兜住（空转不重复处理文档）。</li>
 * </ul>
 *
 * <h2>与进程内实现的两点差异（有意）</h2>
 * <ul>
 *   <li>调度是 500ms 轮询而非精确 schedule：延迟任务最多晚 500ms 触发——对
 *       30 秒防抖的 ingest 无感；</li>
 *   <li>同刻到期的任务执行顺序不保证 FIFO（ZSET 按 member 字典序），
 *       wiki 任务之间没有顺序依赖。</li>
 * </ul>
 */
public class RedisWikiIngestTaskQueue implements WikiIngestTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisWikiIngestTaskQueue.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 就绪集合：成员 = 任务 JSON，score = 可执行时刻（毫秒）。 */
    static final String KEY_READY = "wiki:task:ready";

    /** 执行中集合：成员 = 任务 JSON，score = 租约到期（毫秒）。 */
    static final String KEY_PROC = "wiki:task:proc";

    /** TaskID 去重键前缀。 */
    static final String ID_PREFIX = "wiki:task:id:";

    /** 调度轮询间隔。 */
    static final long DISPATCH_INTERVAL_MILLIS = 500L;

    /** 执行租约：取走任务的实例崩溃后，最多挂这么久被回收。 */
    static final long LEASE_MILLIS = 180_000L;

    /** 租约续期间隔（必须远小于租约，容忍偶发漏续期）。 */
    static final long LEASE_RENEW_MILLIS = 60_000L;

    /** 单轮回收上限。 */
    static final int RECLAIM_BATCH = 100;

    /** 取一个到期任务：ready → proc（原子；无则返回 null）。 */
    private static final RedisScript<String> CLAIM = new DefaultRedisScript<>("""
            local due = redis.call('zrangebyscore', KEYS[1], '-inf', ARGV[1], 'limit', 0, 1)
            if #due == 0 then return false end
            redis.call('zrem', KEYS[1], due[1])
            redis.call('zadd', KEYS[2], ARGV[2], due[1])
            return due[1]
            """, String.class);

    /** 回收租约过期的执行中任务：proc → ready（now 立即可执行）。 */
    private static final RedisScript<Long> RECLAIM = new DefaultRedisScript<>("""
            local stale = redis.call('zrangebyscore', KEYS[1], '-inf', ARGV[1], 'limit', 0, ARGV[2])
            for i = 1, #stale do
              redis.call('zrem', KEYS[1], stale[i])
              redis.call('zadd', KEYS[2], ARGV[1], stale[i])
            end
            return #stale
            """, Long.class);

    /**
     * 重试重排：仅当旧成员仍在 proc（未被回收）时，原子替换为新成员进 ready；
     * 已被回收（返回 0）则放弃——任务已在 ready 里等着，不得重复入队。
     */
    private static final RedisScript<Long> REQUEUE = new DefaultRedisScript<>("""
            if redis.call('zscore', KEYS[1], ARGV[1]) == false then return 0 end
            redis.call('zrem', KEYS[1], ARGV[1])
            redis.call('zadd', KEYS[2], ARGV[2], ARGV[3])
            return 1
            """, Long.class);

    /** 续租：仅当成员仍在 proc 时更新 score（不在则不复活幽灵成员）。 */
    private static final RedisScript<Long> RENEW = new DefaultRedisScript<>("""
            if redis.call('zscore', KEYS[1], ARGV[1]) == false then return 0 end
            redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])
            return 1
            """, Long.class);

    /** 终态清理：移出 proc + 释放 TaskID 键（ARGV[2] 为空 = 无 TaskID）。 */
    private static final RedisScript<Long> COMPLETE = new DefaultRedisScript<>("""
            redis.call('zrem', KEYS[1], ARGV[1])
            if ARGV[2] ~= '' then
              redis.call('del', ARGV[2])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate template;
    private final WikiIngestTaskRunner runner;

    /** 调度线程：回收过期 + 取到期任务投给执行池（本身不跑业务代码）。 */
    private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wiki-ingest-redis-dispatcher");
        t.setDaemon(true);
        return t;
    });

    /** 本地调度器：超时看门狗与租约续期。 */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wiki-ingest-redis-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** 执行器：每个任务一个虚拟线程；并发总量由 KB 的在途上限约束。 */
    private final ExecutorService worker = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 测试钩子：覆盖重试延迟（秒）。{@code null} = 用
     * {@link WikiIngestTaskRunner#retryDelaySeconds} 的默认公式
     * （与 {@link InProcessWikiIngestTaskQueue} 同口径）。
     */
    private volatile Long retryDelayOverrideSeconds;

    public RedisWikiIngestTaskQueue(StringRedisTemplate template,
            ObjectProvider<WikiIngestTaskHandler> handlerProvider,
            ObjectProvider<com.ragagent.wiki.mapper.TaskDeadLetterRepository> deadLetterProvider,
            ObjectProvider<WikiIngestService> ingestServiceProvider) {
        this.template = template;
        this.runner = new WikiIngestTaskRunner(handlerProvider, deadLetterProvider, ingestServiceProvider);
        this.dispatcher.submit(this::dispatchLoop);
    }

    /** 供测试覆盖重试延迟（秒）。 */
    public void setRetryDelayOverrideSeconds(Long seconds) {
        this.retryDelayOverrideSeconds = seconds;
    }

    @Override
    public boolean enqueue(WikiIngestTask task) {
        String idKey = task.hasTaskId() ? ID_PREFIX + task.taskId() : "";
        if (task.hasTaskId()) {
            Boolean ok = template.opsForValue()
                    .setIfAbsent(idKey, "1", Duration.ofSeconds(taskIdTtlSeconds(task)));
            if (!Boolean.TRUE.equals(ok)) {
                // 已有同 id 的任务在排队/运行 → 跨实例合并（TaskID 冲突语义）
                log.debug("wiki task queue: coalesced {} (taskId={})", task.type(), task.taskId());
                return false;
            }
        }
        String member = toJson(QueuedTask.of(task, 1));
        long readyAt = System.currentTimeMillis() + Math.max(0, task.processIn().toMillis());
        try {
            template.opsForZSet().add(KEY_READY, member, (double) readyAt);
        } catch (RuntimeException e) {
            // 入队失败：释放刚占的 TaskID，让重试可以再入队（投递失败要抛——契约）
            if (task.hasTaskId()) {
                try {
                    template.delete(idKey);
                } catch (RuntimeException ignored) {
                    // 删不掉就等 TTL 兜底
                }
            }
            throw e;
        }
        return true;
    }

    /** 供测试/关停使用：停止调度并释放资源。 */
    public void shutdown() {
        dispatcher.shutdownNow();
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    // ═══════════════════════════════════════════════════════════════
    // 调度 / 执行 / 重试
    // ═══════════════════════════════════════════════════════════════

    private void dispatchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                reclaimStale();
                String member;
                while ((member = claimDue()) != null) {
                    final String m = member;
                    QueuedTask queued;
                    try {
                        queued = fromJson(m);
                    } catch (RuntimeException e) {
                        // 损坏成员：丢弃（留在 proc 会永远回收重试）
                        log.warn("wiki task queue: dropping corrupt task member: {}", e.toString());
                        completeMember(m, "");
                        continue;
                    }
                    worker.execute(() -> run(queued, m));
                }
            } catch (Throwable t) {
                log.warn("wiki task queue: dispatch loop error: {}", t.toString());
            }
            try {
                Thread.sleep(DISPATCH_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String claimDue() {
        long now = System.currentTimeMillis();
        return template.execute(CLAIM, List.of(KEY_READY, KEY_PROC),
                Long.toString(now), Long.toString(now + LEASE_MILLIS));
    }

    private void reclaimStale() {
        Long n = template.execute(RECLAIM, List.of(KEY_PROC, KEY_READY),
                Long.toString(System.currentTimeMillis()), Integer.toString(RECLAIM_BATCH));
        if (n != null && n > 0) {
            log.warn("wiki task queue: reclaimed {} stale task(s) from crashed worker(s)", n);
        }
    }

    /**
     * 执行一次任务（attempt 从 1 起），失败则按重试策略重排。
     */
    private void run(QueuedTask queued, String member) {
        WikiIngestTask task = queued.toTask();
        int attempt = queued.attempt();
        Throwable failure = null;
        Thread workerThread = Thread.currentThread();
        long timeoutMillis = task.timeout() == null ? 0 : task.timeout().toMillis();

        // 超时看门狗 + 租约续期（本地调度器；续期让长跑任务不会在 proc 里"过期"）
        ScheduledFuture<?> watchdog = null;
        if (timeoutMillis > 0) {
            watchdog = scheduler.schedule(() -> {
                log.warn("wiki task queue: {} exceeded timeout {}s, interrupting",
                        task.type(), timeoutMillis / 1000);
                workerThread.interrupt();
            }, timeoutMillis, TimeUnit.MILLISECONDS);
        }
        ScheduledFuture<?> renew = scheduler.scheduleAtFixedRate(
                () -> renewMember(member),
                LEASE_RENEW_MILLIS, LEASE_RENEW_MILLIS, TimeUnit.MILLISECONDS);

        try {
            // 终态（成功 / handler 缺失的丢弃）返回 null；失败返回原因，走重试/死信
            failure = runner.dispatchOnce(task);
        } finally {
            if (watchdog != null) {
                watchdog.cancel(false);
            }
            renew.cancel(false);
            // 中断位可能被看门狗置上；清掉它，避免污染后续复用本线程的调度任务
            Thread.interrupted();
        }

        if (failure == null) {
            completeMember(member, idKeyOf(task));
            return;
        }

        int retriesUsed = attempt - 1;
        if (retriesUsed >= task.maxRetry()) {
            log.warn("wiki task queue: {} exhausted {} retries, archiving to dead letters",
                    task.type(), task.maxRetry(), failure);
            runner.archive(task, failure, attempt);
            completeMember(member, idKeyOf(task));
            return;
        }

        Long override = retryDelayOverrideSeconds;
        long delaySeconds = override != null
                ? override
                : WikiIngestTaskRunner.retryDelaySeconds(retriesUsed + 1, failure);
        log.warn("wiki task queue: {} failed (attempt {}/{}), retrying in {}s",
                task.type(), attempt, task.maxRetry() + 1, delaySeconds, failure);
        requeue(member, QueuedTask.of(task, attempt + 1), delaySeconds);
    }

    private void requeue(String member, QueuedTask next, long delaySeconds) {
        long readyAt = System.currentTimeMillis() + Math.max(0, delaySeconds) * 1000L;
        try {
            Long ok = template.execute(REQUEUE, List.of(KEY_PROC, KEY_READY),
                    member, Long.toString(readyAt), toJson(next));
            if (ok == null || ok != 1L) {
                // 已被回收：任务已在 ready 里（旧成员）等待，不得重复入队
                log.debug("wiki task queue: requeue skipped (member already reclaimed)");
            }
        } catch (RuntimeException e) {
            // 重排失败：proc 中的成员等租约过期被回收重跑（重试计数偏差一次，可接受）
            log.warn("wiki task queue: requeue failed: {}", e.toString());
        }
    }

    private void renewMember(String member) {
        try {
            template.execute(RENEW, List.of(KEY_PROC),
                    member, Long.toString(System.currentTimeMillis() + LEASE_MILLIS));
        } catch (RuntimeException e) {
            // best-effort：漏续期只让任务被提前回收重投（至少一次语义容忍）
            log.debug("wiki task queue: lease renew failed: {}", e.toString());
        }
    }

    private void completeMember(String member, String idKey) {
        try {
            template.execute(COMPLETE, List.of(KEY_PROC), member, idKey);
        } catch (RuntimeException e) {
            // 清理失败：proc 成员等租约过期回收后重跑一次（DB 行级认领兜住重复）；
            // TaskID 键靠 TTL 释放
            log.warn("wiki task queue: complete failed: {}", e.toString());
        }
    }

    private static String idKeyOf(WikiIngestTask task) {
        return task.hasTaskId() ? ID_PREFIX + task.taskId() : "";
    }

    /**
     * TaskID 去重键的 TTL：终态会主动删键；TTL 是实例崩溃时的兜底释放——
     * 否则该 TaskID 的合并会被一个幽灵任务永久占住。
     * 取"单次超时 + 30 分钟"且不低于 1 小时。
     */
    static long taskIdTtlSeconds(WikiIngestTask task) {
        long timeoutSeconds = task.timeout() == null ? 0 : task.timeout().toSeconds();
        return Math.max(3600, timeoutSeconds + 1800);
    }

    private static String toJson(QueuedTask queued) {
        try {
            return MAPPER.writeValueAsString(queued);
        } catch (Exception e) {
            throw new IllegalArgumentException("wiki task queue: marshal task: " + e.getMessage(), e);
        }
    }

    private static QueuedTask fromJson(String member) {
        try {
            return MAPPER.readValue(member, QueuedTask.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("wiki task queue: unmarshal member: " + e.getMessage(), e);
        }
    }

    /**
     * 队列成员：任务本体扁平化为原始字段（避免 JSON 时间类型模块依赖，
     * 键名与字节形变受控）+ 当前尝试次数。
     */
    record QueuedTask(String type, String payload, long processInMillis, int maxRetry,
                      long timeoutMillis, String taskId, int attempt) {

        static QueuedTask of(WikiIngestTask task, int attempt) {
            return new QueuedTask(task.type(), task.payload(),
                    task.processIn() == null ? 0 : task.processIn().toMillis(),
                    task.maxRetry(),
                    task.timeout() == null ? 0 : task.timeout().toMillis(),
                    task.taskId(), attempt);
        }

        WikiIngestTask toTask() {
            return new WikiIngestTask(type, payload, Duration.ofMillis(processInMillis),
                    maxRetry, Duration.ofMillis(timeoutMillis), taskId);
        }
    }
}
