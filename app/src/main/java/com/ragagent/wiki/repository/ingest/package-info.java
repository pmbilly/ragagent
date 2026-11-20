/**
 * wiki 摄取的存储与并发协调：任务队列、finalize 锁、身份认领、在途限流、删除墓碑。
 *
 * <p>五个接口是 service 层依赖的端口，Redis 与进程内两套实现都在本包；
 * 任务分派与死信归档的公共逻辑在 {@code WikiIngestTaskRunner}。
 * 形状与常量见 {@code com.ragagent.wiki.domain.ingest}。</p>
 */
package com.ragagent.wiki.repository.ingest;
