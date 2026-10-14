/**
 * 富化面（B263㊵ 从 {@code service} 切出的子面）：处理管线完成后的 LLM 富化——摘要与问题生成。
 *
 * <p>成员：{@code KnowledgeSummaryService}（知识摘要的触发、逐块生成与落库）、
 * {@code QuestionGenerationService}（分块问题生成任务的消费者，{@code implements}
 * {@code domain.QuestionGenerationConsumer}，对分块面只经 {@code domain.ChunkQuestionPort} 端口反查）。</p>
 *
 * <p><b>切出依据</b>：{@code QuestionGenerationService} 在 {@code service} 包内**主代码引用为零**
 * （队列与装配侧已由 B263㉛ 端口反转，只剩测试的一处 import）；{@code KnowledgeSummaryService} 的
 * 唯一代码引用者是门面 {@code management.KnowledgeService} ⇒ 切出后新增的包间边只有
 * {@code management → enrichment} 与 {@code enrichment → service}（访问助手 / 文件服务 / 端口），
 * 均为单向（对齐方案 §8.8.2 目标树 {@code service.enrichment/}），SCC 不扩大。</p>
 */
package com.ragagent.knowledge.service.enrichment;
