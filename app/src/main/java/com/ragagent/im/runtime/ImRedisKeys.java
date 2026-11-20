package com.ragagent.im.runtime;

/**
 * IM 域的 Redis 键前缀（集中定义，避免字面量散落）。
 *
 * <p>键字面量是部署面契约：多实例间与滚动升级共存期读写同一批键，不随版本改名。
 * 本类只收录已接线用到的键；去重 / 限流 / 渠道配置广播等其余键
 * 仍走进程内实现，接线时再引入。</p>
 */
public final class ImRedisKeys {

    private ImRedisKeys() {
    }

    /** + userKey——全局 per-user 排队计数。 */
    public static final String QUEUE_USER_PREFIX = "im:queue:user:";

    /** 全局并发 worker 计数器。 */
    public static final String GLOBAL_GATE = "im:global:active";

    /** + userKey——执行前 /stop 标记。 */
    public static final String STOP_PREFIX = "im:stop:";

    /** + userKey——userKey → {@code sessionId:messageId} 的在途映射。 */
    public static final String INFLIGHT_PREFIX = "im:inflight:";

    /** + messageID——消息去重标记。 */
    public static final String DEDUP_PREFIX = "im:dedup:";

    /** + 限流 key——滑动窗口计数。 */
    public static final String RATE_LIMIT_PREFIX = "im:ratelimit:";

    /** + channelID——WS 长连接渠道的跨实例选主锁。 */
    public static final String LEADER_PREFIX = "im:ws:leader:";

    /** 渠道配置变更广播频道（载荷键为 channel_id/source_instance）。 */
    public static final String CHANNEL_CONFIG_CHANNEL = "im:channel:config";
}
