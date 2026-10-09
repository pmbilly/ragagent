package com.ragagent.knowledge.task;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.ragagent.knowledge.dto.faq.FaqImportProgress;
import org.springframework.stereotype.Component;

/**
 * {@link FaqImportTaskStore} 的进程内实现（缺省/单实例语义）。
 *
 * <p>通常作为覆盖内容注入。三个并发面的线程安全由 {@link ConcurrentHashMap}
 * 与并发键集保证。</p>
 *
 * <p><b>⚠️ 多实例差异</b>：进度、running 锁、创建互斥都只在写了它的 JVM 里可见——
 * 多副本下"同一知识库已有导入任务"的拦截会失效（两个副本可各启动一次导入），
 * 进度查询被路由到别的副本会 404。多实例部署用
 * {@link RedisFaqImportTaskStore}（{@code knowledge.redis-enabled=true}）。</p>
 *
 * <p><b>重启即清空</b>：这是既有语义（导入任务本身是进程内的），不是缺陷。</p>
 */
@Component
public class InProcessFaqImportTaskStore implements FaqImportTaskStore {

    private final ConcurrentHashMap<String, FaqImportProgress> progress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RunningInfo> running = new ConcurrentHashMap<>();
    private final Set<String> createGuards = ConcurrentHashMap.newKeySet();

    @Override
    public FaqImportProgress getProgress(String taskId) {
        return progress.get(taskId);
    }

    @Override
    public void saveProgress(FaqImportProgress p) {
        progress.put(p.taskId(), p);
    }

    @Override
    public String getRunningTaskId(String kbId) {
        RunningInfo info = running.get(kbId);
        return info == null ? "" : info.taskId();
    }

    @Override
    public void setRunningInfo(String kbId, RunningInfo info) {
        running.put(kbId, info);
    }

    @Override
    public void clearRunningInfoIfMatches(String kbId, String taskId, String instanceId, long enqueuedAt) {
        RunningInfo info = running.get(kbId);
        if (info != null && info.taskId().equals(taskId)
                && (info.instanceId().isEmpty() || instanceId.isEmpty()
                || info.instanceId().equals(instanceId))) {
            running.remove(kbId);
        }
    }

    @Override
    public boolean acquireCreateGuard(String key) {
        return createGuards.add(key);
    }

    @Override
    public void releaseCreateGuard(String key) {
        createGuards.remove(key);
    }
}
