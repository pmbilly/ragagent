package com.ragagent.knowledge.wiki.service.ingest;

import com.ragagent.knowledge.api.KnowledgeView;
import com.ragagent.knowledge.api.chunk.ChunkPort;
import com.ragagent.knowledge.api.chunk.KnowledgeSpanPort;
import com.ragagent.knowledge.api.kb.KnowledgeBaseLookup;
import com.ragagent.knowledge.wiki.service.page.WikiPageService;
import com.ragagent.llm.api.ChunkView;
import java.util.List;

/**
 * 摄取各阶段共享的依赖上下文。
 *
 * <p><b>为什么单独成类</b>：{@code WikiIngestBatchHandler} 原先既当"处理器"又当"依赖容器"——
 * map / reduce / finalize / run 四个阶段通过 {@code handler.xxx} 取依赖，语义上把
 * "流程编排者"和"上下文"混成了一个角色。抽出本类后，阶段只依赖上下文，
 * 读代码时 {@code ctx.spans} 一眼就是"阶段共享的追踪面"。</p>
 *
 * <p>只放<b>依赖与薄查询</b>；带状态的业务方法（{@code newWikiBatchContext} /
 * {@code scheduleFollowUp}）仍留在 handler。</p>
 */
final class WikiIngestStageContext {

    final WikiIngestService ingestService;
    final WikiPageService wikiService;
    final WikiIngestCitePipeline citePipeline;
    final WikiIngestDedupService dedupService;

    /** wiki 批次 span 门面（knowledge 侧适配真实上报，追踪器缺席时 no-op）。 */
    final KnowledgeSpanPort spans;

    private final ChunkPort chunkPort;
    private final KnowledgeBaseLookup kbLookup;

    WikiIngestStageContext(WikiIngestService ingestService,
                           WikiPageService wikiService,
                           WikiIngestCitePipeline citePipeline,
                           WikiIngestDedupService dedupService,
                           KnowledgeSpanPort spans,
                           ChunkPort chunkPort,
                           KnowledgeBaseLookup kbLookup) {
        this.ingestService = ingestService;
        this.wikiService = wikiService;
        this.citePipeline = citePipeline;
        this.dedupService = dedupService;
        this.spans = spans;
        this.chunkPort = chunkPort;
        this.kbLookup = kbLookup;
    }

    /** 按知识文档读取文本 chunk（薄委托，原在 handler）。 */
    List<ChunkView> listTextChunksByKnowledgeId(long tenantId, String knowledgeId) {
        return chunkPort.textChunks(tenantId, knowledgeId);
    }

    /** 按 id 取知识视图（薄委托，原在 handler）。 */
    KnowledgeView getKnowledgeByIdOnly(String knowledgeId) {
        if (knowledgeId == null || knowledgeId.isEmpty()) {
            return null;
        }
        return kbLookup.knowledgeById(knowledgeId);
    }
}
