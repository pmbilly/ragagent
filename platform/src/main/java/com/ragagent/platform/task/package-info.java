/**
 * 平台任务面：Redis 任务队列核心（{@code RedisTaskQueueCore}）、
 * Redis 重试执行循环（{@code RedisRetryingTaskQueue}）、进程内执行队列
 * （{@code InProcessTaskQueue}）与统一重试策略（{@code RetryPolicy}）。
 *
 * <p>业务域只保留队列名、载荷序列化与 handler 薄适配；跨实例语义走 Redis core，
 * 单机语义走进程内队列。</p>
 */
package com.ragagent.platform.task;
