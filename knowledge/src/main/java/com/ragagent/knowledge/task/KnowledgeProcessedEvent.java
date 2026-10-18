package com.ragagent.knowledge.task;

/**
 * 单个知识处理完成事件（worker finalize 后发布）。
 *
 * <p>M2 解环：worker 对摘要后处理的触发原本构造器直连 KnowledgeService
 * （门面 {@code @Lazy} 环的一角），改为领域事件解耦。订阅方
 * {@code KnowledgeSummaryService#onKnowledgeProcessed} 用<b>同步</b>
 * {@code @EventListener} 在发布线程内执行——与原直调语义逐字一致
 * （异常仍从发布调用方冒出，worker 侧 try/catch 兜底不变）。</p>
 */
public record KnowledgeProcessedEvent(String knowledgeId) {
}
