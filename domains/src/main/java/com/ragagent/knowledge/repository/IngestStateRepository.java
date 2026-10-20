package com.ragagent.knowledge.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.mapper.KnowledgeMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 入库状态迁移：{@code knowledge.parse_status} 的 CAS 与终态推进集中在这里。
 *
 * <p>与 {@link KnowledgeRepository} 的读模型分开：本类只写状态列（parse/enable/
 * summary/processed_at/error），返回影响行数；0 行表示"这一行已不归本次尝试"。
 * 入库链路的 worker / finalizing 只经本类改状态，避免状态 SQL 散落在读仓储里。</p>
 */
@Component
public class IngestStateRepository {

    private final KnowledgeMapper knowledgeMapper;

    public IngestStateRepository(KnowledgeMapper knowledgeMapper) {
        this.knowledgeMapper = knowledgeMapper;
    }

    /** CAS：{@code pending → processing}（被抢/取消则 0 行）。 */
    public int casPendingToProcessing(String id, OffsetDateTime now) {
        return knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getParseStatus, Knowledge.PARSE_PENDING)
                .set(Knowledge::getParseStatus, Knowledge.PARSE_PROCESSING)
                .set(Knowledge::getUpdatedAt, now));
    }

    /** CAS：{@code processing → finalizing} 并写子任务计数（0 行 = 行已被 cancel/delete 抢走）。 */
    public int promoteToFinalizing(String id, int pendingSubtasks, OffsetDateTime now) {
        return knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getParseStatus, Knowledge.PARSE_PROCESSING)
                .set(Knowledge::getParseStatus, Knowledge.PARSE_FINALIZING)
                .set(Knowledge::getPendingSubtasksCount, pendingSubtasks)
                .set(Knowledge::getUpdatedAt, now));
    }

    /** 带守卫的晋升：{@code finalizing} 且 {@code pending_subtasks_count = 0} 时置 completed。 */
    public int promoteFinalizingToCompleted(String id, OffsetDateTime now) {
        return knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getParseStatus, Knowledge.PARSE_FINALIZING)
                .eq(Knowledge::getPendingSubtasksCount, 0)
                .set(Knowledge::getParseStatus, Knowledge.PARSE_COMPLETED)
                .set(Knowledge::getErrorMessage, "")
                .set(Knowledge::getProcessedAt, now)
                .set(Knowledge::getUpdatedAt, now));
    }

    /** CAS：只有仍由本次尝试占用的行（{@code processing}/{@code finalizing}）才许翻终态 completed。 */
    public int completeIfOwned(String id, boolean resetSummaryStatus, OffsetDateTime now) {
        LambdaUpdateWrapper<Knowledge> uw = new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .in(Knowledge::getParseStatus, List.of(
                        Knowledge.PARSE_PROCESSING, Knowledge.PARSE_FINALIZING))
                .set(Knowledge::getParseStatus, Knowledge.PARSE_COMPLETED)
                .set(Knowledge::getEnableStatus, "enabled")
                .set(Knowledge::getProcessedAt, now)
                .set(Knowledge::getUpdatedAt, now);
        if (resetSummaryStatus) {
            uw.set(Knowledge::getSummaryStatus, "none");
        }
        return knowledgeMapper.update(null, uw);
    }

    /** CAS：只有仍 {@code processing} 的行才许翻 failed 并写 error_message。 */
    public int failIfProcessing(String id, String errorMessage, OffsetDateTime now) {
        return knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .eq(Knowledge::getParseStatus, Knowledge.PARSE_PROCESSING)
                .set(Knowledge::getParseStatus, Knowledge.PARSE_FAILED)
                .set(Knowledge::getErrorMessage, errorMessage)
                .set(Knowledge::getUpdatedAt, now));
    }

    /** 索引完成的无条件推进：{@code enable_status=enabled} + processed_at + updated_at。 */
    public int markIndexedEnabled(String id, OffsetDateTime now) {
        return knowledgeMapper.update(null, new LambdaUpdateWrapper<Knowledge>()
                .eq(Knowledge::getId, id)
                .set(Knowledge::getEnableStatus, "enabled")
                .set(Knowledge::getProcessedAt, now)
                .set(Knowledge::getUpdatedAt, now));
    }
}
