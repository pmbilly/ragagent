package com.ragagent.knowledge.service.faq;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.knowledge.FaqChunkMetadata;
import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.repository.FaqChunkRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FAQ 问句重复校验：本地规则（相似问/反例问题互相冲突）→ 一条 DB 查询 → 具体报错。
 *
 * <p>纯规则与一次查询，不依赖 HTTP；从命令服务抽出后可单测。</p>
 */
final class FaqDuplicateChecker {

    private final FaqChunkRepository faqChunkRepository;
    private final FaqChunkCodec faqChunkCodec;

    FaqDuplicateChecker(FaqChunkRepository faqChunkRepository, FaqChunkCodec faqChunkCodec) {
        this.faqChunkRepository = faqChunkRepository;
        this.faqChunkCodec = faqChunkCodec;
    }

    /** 1-3 步本地判定、4 步一条 DB 查询、5-7 步报错语义。 */
    void check(long tenantId, String kbId, String excludeChunkId, FaqChunkMetadata meta) {
        List<String> similar = meta.similarQuestions == null ? List.of() : meta.similarQuestions;
        List<String> negative = meta.negativeQuestions == null ? List.of() : meta.negativeQuestions;

        for (String q : similar) {
            if (q.equals(meta.standardQuestion)) {
                throw new BizException(AppError.badRequest("相似问「" + q + "」不能与标准问相同"));
            }
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String q : similar) {
            if (!seen.add(q)) {
                throw new BizException(AppError.badRequest("相似问「" + q + "」重复"));
            }
        }
        Set<String> positiveQuestions = new LinkedHashSet<>();
        positiveQuestions.add(meta.standardQuestion);
        positiveQuestions.addAll(similar);
        Set<String> negativeSeen = new LinkedHashSet<>();
        for (String q : negative) {
            if (q.isEmpty()) {
                continue;
            }
            if (q.equals(meta.standardQuestion)) {
                throw new BizException(AppError.badRequest("反例问题「" + q + "」不能与标准问相同"));
            }
            if (positiveQuestions.contains(q)) {
                throw new BizException(AppError.badRequest("反例问题「" + q + "」不能与相似问相同"));
            }
            if (!negativeSeen.add(q)) {
                throw new BizException(AppError.badRequest("反例问题「" + q + "」重复"));
            }
        }

        List<String> allQuestions = new ArrayList<>();
        allQuestions.add(meta.standardQuestion);
        allQuestions.addAll(similar);

        Chunk dupChunk = faqChunkRepository.findFAQChunkWithDuplicateQuestion(
                tenantId, kbId, excludeChunkId, allQuestions);
        if (dupChunk == null) {
            return;
        }
        FaqChunkMetadata existingMeta = faqChunkCodec.sanitizedFaqMetadata(dupChunk);
        if (existingMeta == null) {
            throw new BizException(AppError.badRequest("标准问或相似问与已有条目重复"));
        }
        Set<String> existingSimilarSet = new LinkedHashSet<>();
        if (existingMeta.similarQuestions != null) {
            for (String q : existingMeta.similarQuestions) {
                if (!q.isEmpty()) {
                    existingSimilarSet.add(q);
                }
            }
        }
        if (!meta.standardQuestion.isEmpty()) {
            if (meta.standardQuestion.equals(existingMeta.standardQuestion)
                    || existingSimilarSet.contains(meta.standardQuestion)) {
                throw new BizException(AppError.badRequest("标准问「" + meta.standardQuestion + "」已存在"));
            }
        }
        for (String q : similar) {
            if (q.isEmpty()) {
                continue;
            }
            if (q.equals(existingMeta.standardQuestion) || existingSimilarSet.contains(q)) {
                throw new BizException(AppError.badRequest("相似问「" + q + "」已存在"));
            }
        }
        throw new BizException(AppError.badRequest("标准问或相似问与已有条目重复"));
    }
}
