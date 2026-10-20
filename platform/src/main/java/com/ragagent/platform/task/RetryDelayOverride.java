package com.ragagent.platform.task;

/**
 * 测试用重试延迟覆盖：{@code null} = 用队列自己的退避公式。
 *
 * <p>各队列的默认退避公式不同（指数、四次方+随机、锁冲突固定 15 秒），
 * 但"有覆盖值时用它、且钳到非负"这一步是相同的——收在这里，
 * 队列实现只负责提供 fallback。</p>
 */
public final class RetryDelayOverride {

    private volatile Long seconds;

    /** 覆盖重试延迟（秒）；{@code null} 恢复默认公式。 */
    public void set(Long seconds) {
        this.seconds = seconds;
    }

    /** 有覆盖值时返回覆盖值（钳到 ≥0），否则返回 fallback。 */
    public long resolve(long fallbackSeconds) {
        Long override = seconds;
        return override != null ? Math.max(override, 0L) : fallbackSeconds;
    }
}
