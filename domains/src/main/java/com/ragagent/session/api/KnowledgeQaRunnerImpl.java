package com.ragagent.session.api;

import com.ragagent.contract.pipeline.KnowledgeQaRunRequest;
import com.ragagent.contract.pipeline.KnowledgeQaRunResult;
import com.ragagent.contract.pipeline.KnowledgeQaRunner;
import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.session.rag.ChatManage;
import com.ragagent.session.rag.PipelineBuilder;
import com.ragagent.session.rag.SummaryConfig;
import com.ragagent.session.rag.spi.KnowledgeQaEventPort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RAG 预设运行端口实现：把契约请求还原成管线运行时上下文，跑 rag 预设，回带产物。
 *
 * <p>迁移前这段逻辑在评估域（{@code EvaluationService#buildChatManage + knowledgeQAByEvent}）；
 * 契约化后评估域只构造请求、读产物，不再认识 {@code ChatManage}/{@code PipelineBuilder}。
 * 字段映射与消费面逐字对齐（同一子集、同一 setter 顺序无关）。</p>
 */
@Component
public class KnowledgeQaRunnerImpl implements KnowledgeQaRunner {

    private final KnowledgeQaEventPort knowledgeQaEvents;

    public KnowledgeQaRunnerImpl(KnowledgeQaEventPort knowledgeQaEvents) {
        this.knowledgeQaEvents = knowledgeQaEvents;
    }

    @Override
    public KnowledgeQaRunResult runRag(KnowledgeQaRunRequest request) {
        ChatManage chatManage = toChatManage(request);
        knowledgeQaEvents.knowledgeQAByEvent(chatManage, PipelineBuilder.presets().get("rag"));
        return new KnowledgeQaRunResult(chatManage.getSearchOutcome().getResults(), chatManage.getRerank().getResults(),
                chatManage.getAnswer().getChatResponse());
    }

    /** 契约请求 → 运行时上下文（字段子集与迁移前逐字一致）。 */
    private static ChatManage toChatManage(KnowledgeQaRunRequest request) {
        ChatManage cm = new ChatManage();
        cm.setMaxRounds(request.maxRounds());
        cm.setVectorThreshold(request.vectorThreshold());
        cm.setKeywordThreshold(request.keywordThreshold());
        cm.setEmbeddingTopK(request.embeddingTopK());
        cm.setRerankModelId(request.rerankModelId());
        cm.setRerankTopK(request.rerankTopK());
        cm.setRerankThreshold(request.rerankThreshold());
        cm.setChatModelId(request.chatModelId());
        cm.setSummaryConfig(toSummaryConfig(request.summary()));
        cm.setFallbackResponse(request.fallbackResponse());
        cm.setRewritePromptSystem(request.rewritePromptSystem());
        cm.setRewritePromptUser(request.rewritePromptUser());

        cm.setQuery(request.question());
        cm.getQueryPlan().setRewriteQuery(request.question());
        cm.setKnowledgeBaseIds(new ArrayList<>(List.of(request.knowledgeBaseId())));
        cm.setSearchTargets(new ArrayList<>(
                List.of(SearchTarget.wholeKb(request.knowledgeBaseId(), request.tenantId()))));
        return cm;
    }

    /** 摘要子集（其余字段取运行时缺省，与迁移前一致）。 */
    private static SummaryConfig toSummaryConfig(KnowledgeQaRunRequest.SummarySpec spec) {
        SummaryConfig sc = new SummaryConfig();
        if (spec == null) {
            return sc;
        }
        sc.setRepeatPenalty(spec.repeatPenalty());
        sc.setPrompt(spec.prompt());
        sc.setContextTemplate(spec.contextTemplate());
        sc.setNoMatchPrefix(spec.noMatchPrefix());
        sc.setTemperature(spec.temperature());
        sc.setMaxCompletionTokens(spec.maxCompletionTokens());
        return sc;
    }
}
