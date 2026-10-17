/**
 * 同步任务面（方案 的 {@code task/}）：同步任务队列（{@code DataSourceSyncTaskQueue} +
 * InProcess/Redis 双实现）、任务处理器（{@code DataSourceSyncHandler}）、装配
 * （{@code DataSourceTaskQueueWiring}）与调度器（{@code Scheduler}）。
 *
 * <p>本面对 {@code domain.connector} 与根包（{@code DataSourceSyncEnqueueException}）单向引用，
 * 反向零 ⇒ 不成环。</p>
 */
package com.ragagent.datasource.task;
