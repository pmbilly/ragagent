/**
 * 平台任务面：Redis 任务队列核心（{@code RedisTaskQueueCore}）、
 * Redis 重试执行循环（{@code RedisRetryingTaskQueue}）、进程内执行队列
 * （{@code InProcessTaskQueue}）、重试策略（{@code RetryPolicy}）与
 * 公共契约（{@code TaskQueue} / {@code RetryDelayOverride}）。
 *
 * <p>业务域只保留队列名、载荷序列化与 handler 薄适配；跨实例语义走 Redis core，
 * 单机语义走进程内队列。各域队列接口继承 {@code TaskQueue} 取得命名、启停与
 * 测试钩子的统一契约；投递方法保留自己的语义（taskId / 重试上限 / 超时 / 延迟）。
 * 域特有行为经 {@code RedisRetryingTaskQueue.FailurePolicy} 注入
 * （重试延迟公式、终态死信回调），不在域里再写一套循环。</p>
 */
package com.ragagent.platform.task;
