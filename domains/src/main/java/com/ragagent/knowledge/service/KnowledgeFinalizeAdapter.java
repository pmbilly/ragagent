package com.ragagent.knowledge.service;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.common.knowledge.KnowledgeFinalizePort;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.mapper.KnowledgeMapper;

/**
 * {@link KnowledgeFinalizePort} 的 knowledge 侧实现（B98/C2）。
 *
 * <p>两条语句、try/catch 与告警日志整体搬自 {@code DefaultWikiKnowledgeFinalizer}
 * （wiki 侧该类退化为薄壳，只做 {@code Result → Outcome} 映射）。</p>
 */
@Component
public class KnowledgeFinalizeAdapter implements KnowledgeFinalizePort {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFinalizeAdapter.class);

    private final KnowledgeMapper knowledgeMapper;

    public KnowledgeFinalizeAdapter(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    @Override
    public Result finalizeSubtask(String knowledgeId, OffsetDateTime now) {
        if (knowledgeId == null || knowledgeId.isEmpty()) {
            return new Result(false, false);
        }

        // 1) 原子递减、钳在零
        boolean decremented;
        try {
            int rows = knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                    .eq(Knowledge::getId, knowledgeId)
                    .gt(Knowledge::getPendingSubtasksCount, 0)
                    .setSql("pending_subtasks_count = pending_subtasks_count - 1")
                    .set(Knowledge::getUpdatedAt, now));
            decremented = rows > 0;
        } catch (RuntimeException e) {
            log.warn("finalize subtask decrement failed source=wiki knowledge={} err={}",
                    knowledgeId, e.getMessage());
            return new Result(false, false);
        }

        // 2) 带守卫的晋升（无条件尝试，见端口注释）
        boolean promoted;
        try {
            int rows = knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                    .eq(Knowledge::getId, knowledgeId)
                    .eq(Knowledge::getParseStatus, Knowledge.PARSE_FINALIZING)
                    .eq(Knowledge::getPendingSubtasksCount, 0)
                    .set(Knowledge::getParseStatus, Knowledge.PARSE_COMPLETED)
                    .set(Knowledge::getErrorMessage, "")
                    .set(Knowledge::getProcessedAt, now)
                    .set(Knowledge::getUpdatedAt, now));
            promoted = rows > 0;
        } catch (RuntimeException e) {
            log.warn("finalize subtask promote failed source=wiki knowledge={} err={}",
                    knowledgeId, e.getMessage());
            return new Result(decremented, false);
        }
        return new Result(decremented, promoted);
    }
}
