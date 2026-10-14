/**
 * 分块面（B263㉟ 从 {@code service} 切出的第二个子面）：分块的编辑、抽取与读取。
 *
 * <p>成员：{@code ChunkEditService}（编辑/重生成）、{@code ChunkExtractService}（图抽取 fan-out 的消费者）、
 * {@code ChunkReadService}（摘要重载等只读取行）。{@code ChunkQuestionService} **暂留** {@code service}
 * ——它被 {@code QuestionGenerationService} 引用，单独搬会形成 {@code service ⇄ service.chunk} 环；
 * 待那条边反转后再迁入。</p>
 *
 * <p><b>切出依据</b>：三者在 {@code service} 包内**入度为零**（无人引用），出边单向指向
 * {@code KnowledgeService}/{@code QuestionGenerationService} ⇒ 切出后只有 {@code service.chunk → service}
 * 一个方向，SCC 不扩大。</p>
 */
package com.ragagent.knowledge.service.chunk;
