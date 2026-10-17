package com.ragagent.contract.pipeline;

/**
 * 以 RAG 预设跑一次 QA 的请求（契约化：评估路径用）。
 *
 * <p>字段是**评估路径实际赋值的那一子集**（对齐运行时 ChatManage 的评估用法）：评估侧从自己的
 * 参数快照构造本记录，管线侧据此还原运行时上下文。其余部署级字段由管线零值兜底。</p>
 */
public record KnowledgeQaRunRequest(
        String knowledgeBaseId,
        long tenantId,
        String question,
        int maxRounds,
        double vectorThreshold,
        double keywordThreshold,
        int embeddingTopK,
        String rerankModelId,
        int rerankTopK,
        double rerankThreshold,
        String chatModelId,
        SummarySpec summary,
        String fallbackResponse,
        String rewritePromptSystem,
        String rewritePromptUser) {

    /** 摘要配置子集（对齐运行时 SummaryConfig 的评估用字段）。 */
    public record SummarySpec(double repeatPenalty, String prompt, String contextTemplate,
                              String noMatchPrefix, double temperature, int maxCompletionTokens) {
    }
}
