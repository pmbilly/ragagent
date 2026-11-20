/**
 * wiki 摄取管线：批量/单篇入库的门面与协作者
 * （{@link com.ragagent.wiki.service.ingest.WikiIngestService}、
 * {@link com.ragagent.wiki.service.ingest.WikiIngestBatchHandler}，map/reduce/finalize/index 四阶段）。
 *
 * <p>队列/锁/限流/墓碑的端口与实现见 {@code com.ragagent.wiki.repository.ingest}；
 * 共享形状与常量见 {@code com.ragagent.wiki.domain.ingest}；批上下文与无状态工具在
 * {@link com.ragagent.wiki.service.ingest.support}。页面读写/链接/别名在
 * {@link com.ragagent.wiki.service.page}。</p>
 */
package com.ragagent.wiki.service.ingest;
