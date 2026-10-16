package com.ragagent.contract.agent;

import java.util.List;

/** 最近加入的文档的简要信息。 */
public record RecentDocInfo(
        String chunkId,
        String knowledgeBaseId,
        String knowledgeId,
        String title,
        String description,
        String fileName,
        long fileSize,
        String type,
        String createdAt,
        String faqStandardQuestion,
        List<String> faqSimilarQuestions,
        List<String> faqAnswers) {

    public RecentDocInfo {
        chunkId = chunkId == null ? "" : chunkId;
        knowledgeBaseId = knowledgeBaseId == null ? "" : knowledgeBaseId;
        knowledgeId = knowledgeId == null ? "" : knowledgeId;
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        fileName = fileName == null ? "" : fileName;
        type = type == null ? "" : type;
        createdAt = createdAt == null ? "" : createdAt;
        faqStandardQuestion = faqStandardQuestion == null ? "" : faqStandardQuestion;
        faqSimilarQuestions = faqSimilarQuestions == null ? List.of() : faqSimilarQuestions;
        faqAnswers = faqAnswers == null ? List.of() : faqAnswers;
    }

    /** 全默认（零值）。 */
    public static RecentDocInfo empty() {
        return new RecentDocInfo(null, null, null, null, null, null, 0, null, null, null, null, null);
    }
}
