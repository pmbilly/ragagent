package com.ragagent.knowledge.domain;


/**
 * 问题生成器需要的"分块问题"能力（B263㊱ 依赖反转；B263㊷ 落位 {@code domain}）。
 *
 * <p><b>为什么要有它</b>：{@code QuestionGenerationService} 原直接持有 {@code ChunkQuestionService}
 * （唯一一次调用 {@code generateAndStoreQuestionsForWorker}）。B263㊱ 反转出本端口（当时放 {@code service} 顶层，
 * 消费方在本包）；B263㊵ 消费方迁 {@code service.enrichment} 后本端口需要重新定位。</p>
 *
 * <p><b>为什么最终放 {@code domain}（B263㊷ 实测）</b>：停在任何一侧都要付环的代价——
 * 放 {@code service.chunk}（实现方）⇒ 消费边 {@code enrichment → chunk}，与既有的
 * {@code chunk → management}（ChunkEditService → 门面）和 {@code management → enrichment}
 * （门面 → KnowledgeSummaryService）组成**三元环** ✗（环守卫当场抓到：SCC 12 → 13 组）；
 * 放 {@code enrichment} 则实现方反向依赖消费方。放中立 {@code domain}（本接口签名本就**只用 domain 类型**）
 * ⇒ 消费方与实现方都单向依赖它，服务子面图保持 {@code chunk → management → enrichment} 的无环链。</p>
 *
 * <p>端口只声明**一个方法**——就是那条唯一的调用点（签名与实现逐字一致；实现方仅需放开为
 * {@code public}）。</p>
 */
public interface ChunkQuestionPort {

    /** 为单个 chunk 生成并落库问题，返回生成条数。 */
    int generateAndStoreQuestionsForWorker(KnowledgeBase kb, Knowledge knowledge, Chunk chunk,
                                           String prevContent, String nextContent, int questionCount,
                                           String customInstructions);
}
