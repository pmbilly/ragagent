package com.ragagent.knowledge.api;

import com.ragagent.knowledge.repository.IngestStateRepository;
import com.ragagent.knowledge.repository.KnowledgeRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link KnowledgeFinalizeAdapter} 的 knowledge 侧实现。
 *
 * <p>两条语句、try/catch 与告警日志整体搬自 {@code DefaultWikiKnowledgeFinalizer}
 * （wiki 侧该类退化为薄壳，只做 {@code FinalizeResult → Outcome} 映射）。</p>
 */
@Component
public class KnowledgeFinalizeAdapter {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFinalizeAdapter.class);

    private final KnowledgeRepository knowledgeRepository;
    private final IngestStateRepository ingestStateRepository;

    public KnowledgeFinalizeAdapter(KnowledgeRepository knowledgeRepository,
                                    IngestStateRepository ingestStateRepository) {
        this.knowledgeRepository = knowledgeRepository;
        this.ingestStateRepository = ingestStateRepository;
    }

    public FinalizeResult finalizeSubtask(String knowledgeId, OffsetDateTime now) {
        if (knowledgeId == null || knowledgeId.isEmpty()) {
            return new FinalizeResult(false, false);
        }

        // 1) 原子递减、钳在零
        boolean decremented;
        try {
            int rows = knowledgeRepository.decrementPendingSubtasks(knowledgeId, now);
            decremented = rows > 0;
        } catch (RuntimeException e) {
            log.warn("finalize subtask decrement failed source=wiki knowledge={}", knowledgeId, e);
            return new FinalizeResult(false, false);
        }

        // 2) 带守卫的晋升（无条件尝试，见端口注释）
        boolean promoted;
        try {
            int rows = ingestStateRepository.promoteFinalizingToCompleted(knowledgeId, now);
            promoted = rows > 0;
        } catch (RuntimeException e) {
            log.warn("finalize subtask promote failed source=wiki knowledge={}", knowledgeId, e);
            return new FinalizeResult(decremented, false);
        }
        return new FinalizeResult(decremented, promoted);
    }
}
