package com.ragagent.knowledge.assembler;

import com.ragagent.knowledge.controller.vo.FaqImportProgressRespVo;
import com.ragagent.knowledge.dto.faq.FaqFailedEntry;
import com.ragagent.knowledge.dto.faq.FaqImportProgress;
import com.ragagent.knowledge.dto.faq.FaqMergeDetail;
import com.ragagent.knowledge.dto.faq.FaqSuccessEntry;
import java.util.List;

/** FAQ 导入进度到 HTTP 响应 VO 的装配。 */
public final class FaqImportAssembler {

    private FaqImportAssembler() {
    }

    /** 装配导入进度；三个明细列表逐元素换形。 */
    public static FaqImportProgressRespVo toVo(FaqImportProgress progress) {
        if (progress == null) {
            return null;
        }
        return new FaqImportProgressRespVo(
                progress.taskId(),
                progress.kbId(),
                progress.knowledgeId(),
                progress.status(),
                progress.progress(),
                progress.total(),
                progress.processed(),
                progress.successCount(),
                progress.failedCount(),
                progress.partialFailedCount(),
                progress.skippedCount(),
                toFailedVoList(progress.failedEntries()),
                progress.failedEntriesUrl(),
                toSuccessVoList(progress.successEntries()),
                progress.validEntryIndices(),
                progress.mergeEntryIndices(),
                progress.mergedCount(),
                progress.addedCount(),
                toMergeVoList(progress.mergeDetails()),
                progress.message(),
                progress.error(),
                progress.createdAt(),
                progress.updatedAt(),
                progress.dryRun(),
                progress.importMode(),
                progress.importedAt(),
                progress.displayStatus(),
                progress.processingTime());
    }

    private static List<FaqImportProgressRespVo.FaqFailedEntryVo> toFailedVoList(List<FaqFailedEntry> entries) {
        if (entries == null) {
            return null;
        }
        return entries.stream()
                .map(e -> new FaqImportProgressRespVo.FaqFailedEntryVo(
                        e.index(),
                        e.reason(),
                        e.failureType(),
                        e.partialFailure(),
                        e.tagName(),
                        e.standardQuestion(),
                        e.similarQuestions(),
                        e.negativeQuestions(),
                        e.answers(),
                        e.answerAll(),
                        e.disabled(),
                        e.removedSimilarQuestions(),
                        e.removedNegativeQuestions()))
                .toList();
    }

    private static List<FaqImportProgressRespVo.FaqSuccessEntryVo> toSuccessVoList(List<FaqSuccessEntry> entries) {
        if (entries == null) {
            return null;
        }
        return entries.stream()
                .map(e -> new FaqImportProgressRespVo.FaqSuccessEntryVo(
                        e.index(),
                        e.seqId(),
                        e.tagId(),
                        e.tagName(),
                        e.standardQuestion()))
                .toList();
    }

    private static List<FaqImportProgressRespVo.FaqMergeDetailVo> toMergeVoList(List<FaqMergeDetail> details) {
        if (details == null) {
            return null;
        }
        return details.stream()
                .map(d -> new FaqImportProgressRespVo.FaqMergeDetailVo(
                        d.index(),
                        d.standardQuestion(),
                        d.answerChanged(),
                        d.newSimilarCount(),
                        d.newNegativeCount()))
                .toList();
    }
}
