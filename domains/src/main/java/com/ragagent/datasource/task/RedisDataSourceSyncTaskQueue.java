package com.ragagent.datasource.task;

import com.ragagent.datasource.DataSourceSyncEnqueueException;
import com.ragagent.datasource.domain.DataSourceSyncHandler;
import com.ragagent.datasource.domain.DataSourceSyncPayload;
import com.ragagent.datasource.domain.DataSourceSyncTaskQueue;
import com.ragagent.platform.task.RedisRetryingTaskQueue;
import com.ragagent.platform.task.RetryPolicy;
import com.ragagent.platform.tracing.langfuse.LangfuseTaskScope;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link DataSourceSyncTaskQueue} 的 <b>Redis</b> 实现：跨实例共享任务表 +
 * <b>全局 TaskID 去重</b>。
 *
 * <p>{@code datasource.redis-enabled=true} 时由 {@code DataSourceTaskQueueWiring}
 * 装配（{@code @Primary} 覆盖 {@link InProcessDataSourceSyncTaskQueue}）。</p>
 *
 * <p>队列循环、租约/续期、重试预算、超时中断与幂等键都交给
 * {@link RedisRetryingTaskQueue}；本类只剩三件事：投递语义（TaskID 全局去重）、
 * 载荷解码、以及把执行包进任务级 trace。</p>
 *
 * <h2>保真的语义（与端口契约一致）</h2>
 * <ul>
 *   <li><b>TaskID 全局去重</b>：{@code SETNX datasource:sync:id:<taskId>} 跨实例唯一——
 *       调度 cron 按绝对墙钟触发，多副本会在同一分钟一起触发，TaskID 是"只有第一个赢"
 *       的那一层；</li>
 *   <li><b>重试预算 + 退避</b>：失败按 {@code n^4 + 15 + rand(30)*(n+1)} 秒重排回队列，
 *       {@code attempt > maxRetry} 后放弃；</li>
 *   <li><b>任务超时</b>：独立虚拟线程执行 + {@code Future.get(timeout)}，到点中断；</li>
 *   <li><b>失败要能报出来</b>：投递失败抛 {@link DataSourceSyncEnqueueException}。</li>
 * </ul>
 */
public class RedisDataSourceSyncTaskQueue implements DataSourceSyncTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(RedisDataSourceSyncTaskQueue.class);

    /** 任务类型名（trace span 名）。 */
    static final String TASK_TYPE_DATASOURCE_SYNC = "datasource:sync";

    private static final String KEY_PREFIX = "datasource:sync";

    /** TaskID 去重键前缀。 */
    static final String ID_PREFIX = "datasource:sync:id:";

    private final RedisRetryingTaskQueue queue;
    private final ObjectProvider<DataSourceSyncHandler> handlerProvider;

    public RedisDataSourceSyncTaskQueue(StringRedisTemplate template,
                                        ObjectProvider<DataSourceSyncHandler> handlerProvider) {
        this.handlerProvider = handlerProvider;
        this.queue = new RedisRetryingTaskQueue(template, KEY_PREFIX, TASK_TYPE_DATASOURCE_SYNC,
                new RetryPolicy(0), this::run);
    }

    @Override
    public void setRetryDelayOverrideSeconds(Long seconds) {
        queue.setRetryDelayOverrideSeconds(seconds);
    }

    @Override
    public Outcome enqueue(DataSourceSyncPayload payload, String taskId, int maxRetry,
                           Duration timeout) {
        long timeoutMillis = timeout == null || timeout.isNegative() ? 0L : timeout.toMillis();
        try {
            RedisRetryingTaskQueue.SubmitOutcome outcome = queue.submit(
                    payload.toJson(), 0L,
                    RedisRetryingTaskQueue.TaskSpec.idempotent(
                            maxRetry, timeoutMillis, taskId, ID_PREFIX, null));
            return outcome == RedisRetryingTaskQueue.SubmitOutcome.DUPLICATE
                    ? Outcome.TASK_ID_CONFLICT : Outcome.ENQUEUED;
        } catch (RuntimeException e) {
            throw new DataSourceSyncEnqueueException(
                    "sync queue submit failed: " + e.getMessage(), e);
        }
    }

    /** 供测试/关停使用：停止调度并释放资源。 */
    public void shutdown() {
        queue.shutdown();
    }

    @Override
    public void stop() {
        shutdown();
    }

    /** TaskID 去重键 TTL（与基座的幂等 TTL 同一口径）。 */
    static long taskIdTtlSeconds(int maxRetry, long timeoutMillis) {
        return RedisRetryingTaskQueue.idempotencyTtlSeconds(maxRetry, timeoutMillis);
    }

    private void run(String body, boolean finalAttempt) {
        DataSourceSyncPayload payload;
        try {
            payload = DataSourceSyncPayload.fromJson(body);
        } catch (RuntimeException e) {
            // 载荷坏掉是确定性的失败：重试多少次都一样，直接放弃
            log.error("[DataSourceSyncQueue] undecodable payload", e);
            return;
        }

        DataSourceSyncHandler handler = handlerProvider.getIfAvailable();
        if (handler == null) {
            log.error("[DataSourceSyncQueue] no DataSourceSyncHandler bean registered; dropping task");
            return;
        }

        try (LangfuseTaskScope scope = LangfuseTaskScope.start(
                TASK_TYPE_DATASOURCE_SYNC, payload.tracing(),
                Map.of("data_source_id", payload.dataSourceId(),
                        "sync_log_id", payload.syncLogId()),
                LangfuseTaskScope.previewPayload(body))) {
            handler.handle(payload);
        }
    }
}
