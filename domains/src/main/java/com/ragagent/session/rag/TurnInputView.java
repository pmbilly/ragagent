package com.ragagent.session.rag;

import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.contract.session.PipelineMessageAttachmentView;
import java.util.List;
import java.util.Map;

/**
 * 一轮问答的<b>请求段只读视图</b>：检索范围、模型参数、生成策略与输入载荷。
 *
 * <p>这些字段在一次执行内不再变化；状态段（检索结果/重排结果/历史/答复）不在视图里。
 * 只读辅助方法（模型准备、目标归一）收这个类型，插件仍用 {@link ChatManage} 读写状态——
 * 等到状态段也拆成独立对象后，插件签名再一起改。</p>
 */
public interface TurnInputView {

    String getQuery();

    String getSessionId();

    String getUserId();

    int getMaxRounds();

    List<String> getKnowledgeBaseIds();

    List<String> getKnowledgeIds();

    List<SearchTarget> getSearchTargets();

    double getVectorThreshold();

    double getKeywordThreshold();

    int getEmbeddingTopK();

    String getVectorDatabase();

    String getRerankModelId();

    int getRerankTopK();

    double getRerankThreshold();

    String getChatModelId();

    SummaryConfig getSummaryConfig();

    String getFallbackStrategy();

    String getFallbackResponse();

    String getFallbackPrompt();

    Boolean getCitationEnabled();

    boolean isEnableRewrite();

    boolean isEnableQueryExpansion();

    String getRewritePromptSystem();

    String getRewritePromptUser();

    String getQueryUnderstandModelId();

    boolean isFaqPriorityEnabled();

    double getFaqDirectAnswerThreshold();

    double getFaqScoreBoost();

    boolean isDataAnalysisEnabled();

    List<String> getImages();

    String getVlmModelId();

    boolean isChatModelSupportsVision();

    List<PipelineMessageAttachmentView> getAttachments();

    Map<String, String> getIntentPromptOverrides();

    long getTenantId();

    boolean isWebSearchEnabled();

    String getWebSearchProviderId();

    int getWebSearchMaxResults();

    boolean isWebFetchEnabled();

    int getWebFetchTopN();

    String getLanguage();
}
