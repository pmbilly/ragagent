package com.ragagent.knowledge.task;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import com.ragagent.knowledge.dto.kb.KBCloneProgress;
import com.ragagent.knowledge.dto.doc.KnowledgeMoveProgress;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeTaskProgressStore} 的进程内实现（缺省/单实例语义）。
 *
 * <p>TTL 在读路径检查（惰性过期）；多副本部署无跨进程进度可见性
 * （轮询被路由到非执行副本会 404）——多实例用
 * {@link RedisKnowledgeTaskProgressStore}（键级 TTL 等价惰性过期）。</p>
 */
@Component
public class InProcessKnowledgeTaskProgressStore implements KnowledgeTaskProgressStore {

    private static final long TTL_SECONDS = 24 * 3600L;

    private final ConcurrentHashMap<String, KnowledgeMoveProgress> moveProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, KBCloneProgress> cloneProgress = new ConcurrentHashMap<>();

    @Override
    public void saveMoveInitial(KnowledgeMoveProgress progress) {
        moveProgress.putIfAbsent(progress.taskId(), progress);
    }

    @Override
    public void saveMove(KnowledgeMoveProgress progress) {
        moveProgress.put(progress.taskId(), progress);
    }

    @Override
    public KnowledgeMoveProgress getMove(String taskId) {
        KnowledgeMoveProgress p = moveProgress.get(taskId);
        if (p == null) {
            return null;
        }
        if (expired(p.updatedAt())) {
            moveProgress.remove(taskId);
            return null;
        }
        return p;
    }

    @Override
    public void saveCloneInitial(KBCloneProgress progress) {
        cloneProgress.putIfAbsent(progress.taskId(), progress);
    }

    @Override
    public void saveClone(KBCloneProgress progress) {
        cloneProgress.put(progress.taskId(), progress);
    }

    @Override
    public KBCloneProgress getClone(String taskId) {
        KBCloneProgress p = cloneProgress.get(taskId);
        if (p == null) {
            return null;
        }
        if (expired(p.updatedAt())) {
            cloneProgress.remove(taskId);
            return null;
        }
        return p;
    }

    private static boolean expired(long updatedAt) {
        if (updatedAt <= 0) {
            return false;
        }
        return Instant.now().getEpochSecond() - updatedAt > TTL_SECONDS;
    }
}
