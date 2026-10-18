/**
 * wiki 摄取管线：批量/单篇入库的门面与协作者
 * （{@link com.ragagent.wiki.service.ingest.WikiIngestService}、
 * {@link com.ragagent.wiki.service.ingest.WikiIngestBatchHandler}，map/reduce/finalize/index 四阶段），
 * 连同队列/锁/幂等/去重的实现。
 *
 * <p>无回边的任务/队列/锁/限流/去重 SPI、批上下文与清理工具在
 * {@link com.ragagent.wiki.service.ingest.support}。页面读写/链接/别名在
 * {@link com.ragagent.wiki.service.page}，跨切面端口与 LLM 适配留在
 * {@code com.ragagent.wiki.service} 根包。</p>
 */
package com.ragagent.wiki.service.ingest;
