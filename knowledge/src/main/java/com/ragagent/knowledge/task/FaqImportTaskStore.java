package com.ragagent.knowledge.task;

import com.ragagent.knowledge.dto.faq.FaqImportProgress;

/**
 * FAQ 导入任务的进度与并发锁端口。
 *
 * <p><b>三个并发面</b>：按 taskId 的进度快照、按 kbId 的 running 锁（含实例标识，
 * 多实例部署时区分持有者）、按 key 的创建互斥。</p>
 *
 * <p><b>两种实现</b>：进程内（{@link InProcessFaqImportTaskStore}，缺省/单实例语义，
 * 重启即清空）；Redis（{@link RedisFaqImportTaskStore}，
 * {@code knowledge.redis-enabled=true} 时以 {@code @Primary} 生效——进度与 running 锁
 * 跨实例共享，"同一知识库已有导入任务"的拦截在多副本下仍然成立）。</p>
 */
public interface FaqImportTaskStore {

    /** running 锁的持有者信息；instanceId 为空表示不区分实例。 */
    record RunningInfo(String taskId, long enqueuedAt, String instanceId) {
    }

    FaqImportProgress getProgress(String taskId);

    void saveProgress(FaqImportProgress p);

    String getRunningTaskId(String kbId);

    void setRunningInfo(String kbId, RunningInfo info);

    void clearRunningInfoIfMatches(String kbId, String taskId, String instanceId, long enqueuedAt);

    boolean acquireCreateGuard(String key);

    void releaseCreateGuard(String key);
}
