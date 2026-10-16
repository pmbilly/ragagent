/**
 * 流事件基础设施：Redis / 内存两种流管理器（{@link com.ragagent.platform.stream.RedisStreamManager}）与事件载体
 * （{@link com.ragagent.platform.stream.StreamEvent}），服务于实时回答与运行态回放。
 * **注意**：流事件与旧版服务共享同一批 Redis 键，其字节形态是 §11 登记边界，勿按"历史残留"清理。
 *
 * <p>另含契约 {@code contract.model.ChatStream} 的队列实现（{@code QueuedChatStream}/
 * {@code SyncChatStream} 与公用的 {@code StreamLifecycle}）：模型运行时侧建流、追踪装饰器侧转发，
 * 都只依赖契约接口。</p>
  */
package com.ragagent.platform.stream;
