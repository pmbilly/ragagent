package com.ragagent.common.knowledge;

import java.time.OffsetDateTime;

/**
 * 知识行收尾端口（B98/C2）：wiki 子任务归零时的递减与晋升。
 *
 * <p>实现留在 {@code knowledge} 侧（原 {@code DefaultWikiKnowledgeFinalizer} 的两条
 * {@code LambdaUpdateWrapper} 语句，含 try/catch 与告警日志），语义逐字保留：</p>
 * <ol>
 *   <li><b>原子递减</b>：{@code pending_subtasks_count = pending_subtasks_count - 1}
 *       且 {@code > 0}（钳在零）；</li>
 *   <li><b>带守卫的晋升</b>：仅当 {@code parse_status = finalizing} 且计数已为 0 时置为
 *       {@code completed}、清空错误、写 {@code processed_at}/{@code updated_at}。</li>
 * </ol>
 *
 * <p>两步均"无条件尝试、看影响行数"——它同时是并发正确性来源（多个子任务同时归零时只有
 * 一个能匹配晋升的 WHERE）。异常按步骤吞掉并降级返回，不抛出。</p>
 */
public interface KnowledgeFinalizePort {

    /** 一次收尾的结果：{@code decremented} = 递减命中；{@code promoted} = 晋升命中。 */
    record Result(boolean decremented, boolean promoted) {
    }

    Result finalizeSubtask(String knowledgeId, OffsetDateTime now);
}
