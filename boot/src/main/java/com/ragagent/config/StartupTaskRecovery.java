package com.ragagent.config;

import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.repository.KnowledgeSpanRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动恢复。
 *
 * <p>应用重启后把卡在处理态的任务复位为失败，让前端不再显示永远转圈的行：</p>
 * <ol>
 *   <li><b>知识解析</b>（仅未装配知识 Redis 面时）：parse_status ∈
 *       {pending, processing, finalizing, deleting} 的行 → failed +
 *       "Task interrupted due to application restart" + pending_subtasks_count=0。
 *       分布式实现另有「finalizing 且唯一未决子槽是持久化 wiki op」的 NOT-EXISTS 排除
 *       （分布式队列持久化、重启后能收尾）——单机形态下该理由不成立，
 *       故 Lite 路径一并复位（详见 {@link #listStuckKnowledgeIds(boolean)}）；复位
 *       成功后按行取消孤儿 span（LatestAttempt + CancelAllOpenSpans，errorCode=SERVER_RESTART）。</li>
 *   <li><b>摘要生成</b>（仅 Lite）：summary_status ∈ {pending, processing} → failed。</li>
 *   <li><b>数据源同步日志</b>（两种模式都跑）：status=running → failed +
 *       "Sync interrupted due to application restart" + finished_at=now；分布式模式
 *       加 30 分钟陈旧窗——持久化队列里可能还有
 *       未开 span 的积压任务，只有明显陈旧的才敢判死。</li>
 * </ol>
 *
 * <p><b>知识/摘要行的跳过判据 = {@code knowledge.redis-enabled}</b>（K6：与
 * {@code KnowledgeRedisWiring} 同一开关）。旧实现用 {@code REDIS_ADDR} 非空判断：
 * 只把 Redis 用于 IM/embed 等其它面的部署会被误判成分布式，卡住的知识行**永不复位**
 * （前端永远转圈，HousekeepingService 只判 failed 不重投）。同理数据源同步日志按
 * {@code datasource.redis-enabled} 判断——三个开关各管各的任务基础设施。</p>
 *
 * <p>判死仍需谨慎：知识 Redis 面装配时另一副本可能仍在处理同一知识，启动钩子无法区分
 * 孤儿与积压——那是 HousekeepingService 的职责（检查 span 活动与真队列）。</p>
 *
 * <p>失败只 WARN 不阻塞启动。此处写下的
 * "Task interrupted due to application restart" 文案即 {@code KnowledgeService}
 * 重启文案映射的消费对象——此前该映射只读不写、错误码永不可产生。</p>
 */
@Component
public class StartupTaskRecovery {

    private static final Logger log = LoggerFactory.getLogger(StartupTaskRecovery.class);

    /** 陈旧窗：30 分钟。 */
    static final long STALE_WINDOW_MINUTES = 30;

    /** 知识/摘要行复位时的 error_message 文案。 */
    public static final String RESTART_INTERRUPTED_MESSAGE =
            "Task interrupted due to application restart";

    /** 数据源同步日志复位时的 error_message 文案。 */
    public static final String SYNC_INTERRUPTED_MESSAGE =
            "Sync interrupted due to application restart";

    private final JdbcTemplate jdbc;
    private final KnowledgeSpanRepository spanRepository;
    /** 知识 Redis 面是否装配（= {@code KnowledgeRedisWiring} 的同一开关）。 */
    private final boolean knowledgeRedisEnabled;
    /** 数据源任务队列是否装配 Redis 版（= {@code DataSourceTaskQueueWiring} 的同一开关）。 */
    private final boolean datasourceRedisEnabled;

    public StartupTaskRecovery(JdbcTemplate jdbc, KnowledgeSpanRepository spanRepository,
            @Value("${knowledge.redis-enabled:false}") boolean knowledgeRedisEnabled,
            @Value("${datasource.redis-enabled:false}") boolean datasourceRedisEnabled) {
        this.jdbc = jdbc;
        this.spanRepository = spanRepository;
        this.knowledgeRedisEnabled = knowledgeRedisEnabled;
        this.datasourceRedisEnabled = datasourceRedisEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resetPendingTasks() {
        resetPendingTasks(knowledgeRedisEnabled, datasourceRedisEnabled);
    }

    /** 测试便捷重载：两面同值（生产路径走 {@link #resetPendingTasks()} 的注入开关）。 */
    void resetPendingTasks(boolean distributed) {
        resetPendingTasks(distributed, distributed);
    }

    /**
     * 三种复位各自按**自己守护的任务基础设施**决定是否跳过（K6：口径必须与装配一致）。
     *
     * @param knowledgeDistributed 知识 Redis 面已装配（{@code knowledge.redis-enabled}）——
     *                             此时另一副本可能仍在处理同一知识，启动钩子不敢判死
     * @param syncDistributed      数据源同步队列已装配 Redis 版（{@code datasource.redis-enabled}）
     */
    void resetPendingTasks(boolean knowledgeDistributed, boolean syncDistributed) {
        try {
            resetStuckKnowledge(knowledgeDistributed);
        } catch (RuntimeException e) {
            log.warn("resetPendingTasks: reset stuck knowledge failed: {}", e.getMessage());
        }
        try {
            resetStuckSummary(knowledgeDistributed);
        } catch (RuntimeException e) {
            log.warn("resetPendingTasks: reset pending summary tasks failed: {}", e.getMessage());
        }
        try {
            resetStuckSyncLogs(syncDistributed);
        } catch (RuntimeException e) {
            log.warn("resetPendingTasks: reset pending data source sync tasks failed: {}",
                    e.getMessage());
        }
    }

    /**
     * 分布式查询条件：可复位状态 + wiki 独槽排除（NOT-EXISTS 子查询）。
     *
     * <p>Lite（{@code distributed=false}）<b>不排除</b> wiki 独槽行：分布式侧的排除理由是
     * 「wiki ingest 独立落库，重启后能收尾」，而单机形态下队列在进程内、随重启消失，
     * <b>没有任何组件会重建触发器</b>（重启后该 wiki op 会一直躺着，文档永远停在
     * finalizing、卡片一直显示「优化中」）。复位为失败让用户可手动重试（重试即重新触发 ingest）。</p>
     */
    private List<String> listStuckKnowledgeIds(boolean distributed) {
        if (!distributed) {
            return jdbc.queryForList("SELECT id FROM knowledges WHERE parse_status IN (?, ?, ?, ?)",
                    String.class,
                    Knowledge.PARSE_PENDING, Knowledge.PARSE_PROCESSING,
                    Knowledge.PARSE_FINALIZING, Knowledge.PARSE_DELETING);
        }
        return jdbc.queryForList("SELECT id FROM knowledges WHERE parse_status IN (?, ?, ?, ?) "
                + "AND NOT (parse_status = ? AND pending_subtasks_count = 1 AND EXISTS ("
                + "SELECT 1 FROM task_pending_ops "
                + "WHERE task_pending_ops.task_type = ? "
                + "AND task_pending_ops.scope = ? "
                + "AND task_pending_ops.dedup_key = knowledges.id "
                + "AND task_pending_ops.op = ?))",
                String.class,
                Knowledge.PARSE_PENDING, Knowledge.PARSE_PROCESSING,
                Knowledge.PARSE_FINALIZING, Knowledge.PARSE_DELETING,
                Knowledge.PARSE_FINALIZING,
                "wiki:ingest", "knowledge_base", "ingest");
    }

    private void resetStuckKnowledge(boolean distributed) {
        if (distributed) {
            return;
        }
        List<String> stuckIds = listStuckKnowledgeIds(distributed);
        if (stuckIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(stuckIds.size(), "?"));
        Object[] args = new Object[stuckIds.size() + 2];
        args[0] = Knowledge.PARSE_FAILED;
        args[1] = RESTART_INTERRUPTED_MESSAGE;
        for (int i = 0; i < stuckIds.size(); i++) {
            args[i + 2] = stuckIds.get(i);
        }
        int reset = jdbc.update("UPDATE knowledges SET parse_status = ?, error_message = ?, "
                + "pending_subtasks_count = 0 WHERE id IN (" + placeholders + ")", args);
        if (reset <= 0) {
            return;
        }
        log.info("Reset {} stuck knowledge parsing tasks to failed state (distributed=false)",
                reset);
        // 行复位为终态后才取消孤儿 span（防止 UI 在后续手动重试时出现重复的
        // running 子 span；重读成功复位的行，SELECT/UPDATE 缝隙里状态变化的行不误取消）
        Object[] recheckArgs = new Object[stuckIds.size() + 2];
        for (int i = 0; i < stuckIds.size(); i++) {
            recheckArgs[i] = stuckIds.get(i);
        }
        recheckArgs[stuckIds.size()] = Knowledge.PARSE_FAILED;
        recheckArgs[stuckIds.size() + 1] = RESTART_INTERRUPTED_MESSAGE;
        List<String> resetIds = jdbc.queryForList(
                "SELECT id FROM knowledges WHERE id IN (" + placeholders + ") "
                        + "AND parse_status = ? AND error_message = ?",
                String.class, recheckArgs);
        for (String knowledgeId : resetIds) {
            int attempt = spanRepository.latestAttempt(knowledgeId);
            if (attempt <= 0) {
                continue;
            }
            long cancelled = spanRepository.cancelAllOpenSpans(knowledgeId, attempt,
                    "SERVER_RESTART", RESTART_INTERRUPTED_MESSAGE);
            if (cancelled > 0) {
                log.info("resetPendingTasks: cancelled {} open span(s) for knowledge {} attempt {}",
                        cancelled, knowledgeId, attempt);
            }
        }
    }

    private void resetStuckSummary(boolean distributed) {
        if (distributed) {
            return;
        }
        int reset = jdbc.update("UPDATE knowledges SET summary_status = ? "
                + "WHERE summary_status IN (?, ?)", "failed", "pending", "processing");
        if (reset > 0) {
            log.info("Reset {} stuck summary generation tasks to failed state (distributed=false)",
                    reset);
        }
    }

    private void resetStuckSyncLogs(boolean distributed) {
        Object cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(STALE_WINDOW_MINUTES);
        int reset = distributed
                ? jdbc.update("UPDATE sync_logs SET status = 'failed', error_message = ?, "
                        + "finished_at = ? WHERE status = 'running' AND started_at < ?",
                        SYNC_INTERRUPTED_MESSAGE, OffsetDateTime.now(ZoneOffset.UTC), cutoff)
                : jdbc.update("UPDATE sync_logs SET status = 'failed', error_message = ?, "
                        + "finished_at = ? WHERE status = 'running'",
                        SYNC_INTERRUPTED_MESSAGE, OffsetDateTime.now(ZoneOffset.UTC));
        if (reset > 0) {
            log.info("Reset {} stuck data source sync tasks to failed state (distributed={})",
                    reset, distributed);
        }
    }
}
