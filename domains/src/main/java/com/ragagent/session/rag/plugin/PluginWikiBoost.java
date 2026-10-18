package com.ragagent.session.rag.plugin;

import com.ragagent.contract.knowledge.KnowledgeBaseView;
import com.ragagent.contract.pipeline.PipelinePorts;
import com.ragagent.contract.retrieval.ChunkTypes;
import com.ragagent.contract.retrieval.SearchResult;
import com.ragagent.contract.retrieval.SearchTarget;
import com.ragagent.session.rag.ChatManage;
import com.ragagent.session.rag.PipelineEventType;
import java.util.List;

/**
 * CHUNK_RERANK 附加插件：rerank 链之后的
 * wiki_page 分数加成（×1.3），确认至少一个检索目标确实是 wiki KB 才生效。
 */
public final class PluginWikiBoost implements Plugin {

    private static final double WIKI_BOOST_FACTOR = 1.3;

    private final PipelinePorts.KnowledgeBaseService kbService;

    public PluginWikiBoost(PipelinePorts.KnowledgeBaseService kbService) {
        this.kbService = kbService;
    }

    @Override
    public PipelineEventType[] activationEvents() {
        return new PipelineEventType[] {PipelineEventType.CHUNK_RERANK};
    }

    @Override
    public PluginError onEvent(PipelineEventType eventType, ChatManage chatManage, Plugin.Chain next) {
        PluginError err = next.next();
        if (err != null) {
            return err;
        }

        List<SearchResult> rerankResult = chatManage.getRerankResult();
        if (rerankResult == null || rerankResult.isEmpty()) {
            return null;
        }

        // 快路径：结果集没有 wiki_page 块就不查 KB
        boolean hasWikiChunk = false;
        for (SearchResult r : rerankResult) {
            if (ChunkTypes.WIKI_PAGE.equals(r.getChunkType())) {
                hasWikiChunk = true;
                break;
            }
        }
        if (!hasWikiChunk) {
            return null;
        }

        // 确认至少一个目标是 wiki KB
        boolean hasWikiKB = false;
        for (SearchTarget target : chatManage.getSearchTargets()) {
            if (target == null || target.knowledgeBaseId().isEmpty()) {
                continue;
            }
            KnowledgeBaseView kb;
            try {
                kb = kbService.getKnowledgeBaseByIdUnscoped(target.knowledgeBaseId());
            } catch (RuntimeException e) {
                kb = null;
            }
            if (kb != null && kb.isWikiEnabled()) {
                hasWikiKB = true;
                break;
            }
        }
        if (!hasWikiKB) {
            return null;
        }

        int boostedCount = 0;
        for (SearchResult r : rerankResult) {
            if (ChunkTypes.WIKI_PAGE.equals(r.getChunkType())) {
                r.setScore(r.getScore() * WIKI_BOOST_FACTOR);
                boostedCount++;
            }
        }

        if (boostedCount > 0) {
            rerankResult.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        }
        return null;
    }
}
