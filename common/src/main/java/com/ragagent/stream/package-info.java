/**
 * 流事件基础设施：Redis / 内存两种流管理器（{@link com.ragagent.stream.RedisStreamManager}）与事件载体
 * （{@link com.ragagent.stream.StreamEvent}），服务于实时回答与运行态回放。
 * **注意**：流事件与旧版服务共享同一批 Redis 键，其字节形态是 §11 登记边界，勿按"历史残留"清理。
  */
package com.ragagent.stream;
