/**
 * 知识库异步任务：队列、进度与执行。
 * <ul>
 *   <li><b>队列</b>：ChunkExtractTaskQueue / QuestionGenerationTaskQueue（接口）+
 *       Redis 与 InProcess 两套实现；开启 Redis 队列时由 {@code KnowledgeRedisWiring}
 *       以 {@code @Primary} 选出 Redis 版，InProcess 版是单实例回退；</li>
 *   <li><b>进度</b>：KnowledgeTaskProgressStore / FaqImportTaskStore 同样是 Redis 与
 *       InProcess 两套实现，选用规则与队列一致；</li>
 *   <li><b>执行</b>：KnowledgeProcessWorker（解析主链路 worker）+
 *       KnowledgeTaskExecutor（子任务调度）；任务 id 规则在
 *       {@code knowledge.support.KnowledgeTaskIdCodec}。</li>
 * </ul>
 */
package com.ragagent.knowledge.task;
