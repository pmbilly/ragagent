package com.ragagent.knowledge.service;

import com.ragagent.knowledge.domain.Chunk;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeBase;

/**
 * 问题生成器需要的"分块问题"能力（B263㊱ 依赖反转）。
 *
 * <p><b>为什么要有它</b>：{@code QuestionGenerationService} 原直接持有 {@code ChunkQuestionService}
 * （唯一一次调用 {@code generateAndStoreQuestionsForWorker}）⇒ 这把 {@code ChunkQuestionService}
 * 钉在 {@code service} 包里：一旦它迁往 {@code service.chunk}，就会出现
 * {@code service → service.chunk}（本类）与 {@code service.chunk → service}（另三个 chunk 类）**双向边**
 * ⇒ 包级环、SCC 扩大（B263㉟ 因此把它"暂留"）。反转后：消费方只认本端口（在本包内），
 * 实现在 {@code service.chunk}「向下」实现它 ⇒ 方向固定为 {@code service.chunk → service}，单向。</p>
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
