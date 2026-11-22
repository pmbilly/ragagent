package com.ragagent.knowledge.controller.vo;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * FAQ 导入进度的 HTTP 响应体。
 *
 * <p>字段名与声明顺序即线格式：Jackson 按声明顺序输出，任何调序都会改变响应字节。</p>
 */
public record FaqImportProgressRespVo(
        String taskId,
        String kbId,
        String knowledgeId,
        String status,
        int progress,
        int total,
        int processed,
        int successCount,
        int failedCount,
        int partialFailedCount,
        int skippedCount,
        List<FaqFailedEntryVo> failedEntries,
        String failedEntriesUrl,
        List<FaqSuccessEntryVo> successEntries,
        List<Integer> validEntryIndices,
        List<Integer> mergeEntryIndices,
        int mergedCount,
        int addedCount,
        List<FaqMergeDetailVo> mergeDetails,
        String message,
        String error,
        long createdAt,
        long updatedAt,
        boolean dryRun,
        String importMode,
        OffsetDateTime importedAt,
        String displayStatus,
        long processingTime) {

    /** 导入失败条目：失败原因与原条目内容。 */
    public record FaqFailedEntryVo(
            int index,
            String reason,
            String failureType,
            boolean partialFailure,
            String tagName,
            String standardQuestion,
            List<String> similarQuestions,
            List<String> negativeQuestions,
            List<String> answers,
            boolean answerAll,
            boolean disabled,
            List<String> removedSimilarQuestions,
            List<String> removedNegativeQuestions) {
    }

    /** 导入成功条目：序号与命中标签。 */
    public record FaqSuccessEntryVo(
            int index,
            long seqId,
            long tagId,
            String tagName,
            String standardQuestion) {
    }

    /** 合并明细：命中的标准问及变化量。 */
    public record FaqMergeDetailVo(
            int index,
            String standardQuestion,
            boolean answerChanged,
            int newSimilarCount,
            int newNegativeCount) {
    }
}
