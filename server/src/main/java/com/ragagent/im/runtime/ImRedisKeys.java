package com.ragagent.im.runtime;

/**
 * IM 域的 Redis 键前缀（集中定义，避免字面量散落）。
 *
 * <p>字面量与 Go 版共用（多实例部署与共存期读写同一批键），
 * 注释里的 Go 常量名即出处（internal/im/service.go 的 const 块）。
 * 本类只收录已接线用到的键；去重 / 限流 / 渠道配置广播等其余键
 * 仍走进程内实现，接线时再引入。</p>
 */
public final class ImRedisKeys {

    private ImRedisKeys() {
    }

    /** + userKey——全局 per-user 排队计数（Go {@code RedisKeyQueueUser}）。 */
    public static final String QUEUE_USER_PREFIX = "im:queue:user:";

    /** 全局并发 worker 计数器（Go {@code RedisKeyGlobalGate}）。 */
    public static final String GLOBAL_GATE = "im:global:active";

    /** + userKey——执行前 /stop 标记（Go {@code RedisKeyStop}）。 */
    public static final String STOP_PREFIX = "im:stop:";

    /** + userKey——userKey → {@code sessionId:messageId} 的在途映射（Go {@code RedisKeyInflight}）。 */
    public static final String INFLIGHT_PREFIX = "im:inflight:";

    /** + messageID——消息去重标记（Go {@code RedisKeyDedup}）。 */
    public static final String DEDUP_PREFIX = "im:dedup:";

    /** + 限流 key——滑动窗口计数（Go {@code RedisKeyRateLimit}）。 */
    public static final String RATE_LIMIT_PREFIX = "im:ratelimit:";
}
