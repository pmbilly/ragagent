package com.ragagent.session.rag;

import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.contract.session.PipelineMessageAttachmentView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 请求段快照：一次执行内不再变化的配置（检索范围、模型参数、生成策略、输入载荷）。
 *
 * <p>由 {@link ChatManage#freeze()} 在 setup 结束时生成；并发分支通过 {@code fork()}
 * 共享同一份快照，不再逐字段复制。列表/映射在构造时做一次防御性拷贝。</p>
 */
public record TurnInputSnapshot(
        String query,
        String sessionId,
        String userId,
        int maxRounds,
        List<String> knowledgeBaseIds,
        List<String> knowledgeIds,
        List<SearchTarget> searchTargets,
        double vectorThreshold,
        double keywordThreshold,
        int embeddingTopK,
        String vectorDatabase,
        String rerankModelId,
        int rerankTopK,
        String chatModelId,
        SummaryConfig summaryConfig,
        String fallbackStrategy,
        String fallbackResponse,
        String fallbackPrompt,
        Boolean citationEnabled,
        boolean enableRewrite,
        boolean enableQueryExpansion,
        String rewritePromptSystem,
        String rewritePromptUser,
        String queryUnderstandModelId,
        boolean faqPriorityEnabled,
        double faqDirectAnswerThreshold,
        double faqScoreBoost,
        boolean dataAnalysisEnabled,
        List<String> images,
        String vlmModelId,
        boolean chatModelSupportsVision,
        List<PipelineMessageAttachmentView> attachments,
        Map<String, String> intentPromptOverrides,
        long tenantId,
        boolean webSearchEnabled,
        String webSearchProviderId,
        int webSearchMaxResults,
        boolean webFetchEnabled,
        int webFetchTopN,
        String language) {

    public TurnInputSnapshot {
        knowledgeBaseIds = knowledgeBaseIds == null ? new ArrayList<>() : new ArrayList<>(knowledgeBaseIds);
        knowledgeIds = knowledgeIds == null ? new ArrayList<>() : new ArrayList<>(knowledgeIds);
        searchTargets = searchTargets == null ? new ArrayList<>() : new ArrayList<>(searchTargets);
        summaryConfig = summaryConfig == null ? new SummaryConfig() : summaryConfig.copy();
        images = images == null ? null : new ArrayList<>(images);
        attachments = attachments == null ? null : new ArrayList<>(attachments);
        intentPromptOverrides = intentPromptOverrides == null
                ? null : new LinkedHashMap<>(intentPromptOverrides);
    }
}
