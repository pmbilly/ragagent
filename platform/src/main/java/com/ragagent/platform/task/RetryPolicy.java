package com.ragagent.platform.task;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 任务重试预算与退避：{@code maxRetry} 次重试（共 {@code maxRetry + 1} 次尝试），
 * 默认退避 {@code n^4 + 15 + rand(30) * (n + 1)} 秒。
 *
 * <p>测试可传 {@code overrideSeconds} 覆盖退避；{@code null} = 用默认公式。
 * 业务队列只声明预算，不再各自复制公式。</p>
 */
public final class RetryPolicy {

    private final int maxRetry;
    private final Long overrideSeconds;

    public RetryPolicy(int maxRetry) {
        this(maxRetry, null);
    }

    public RetryPolicy(int maxRetry, Long overrideSeconds) {
        if (maxRetry < 0) {
            throw new IllegalArgumentException("maxRetry must be >= 0");
        }
        this.maxRetry = maxRetry;
        this.overrideSeconds = overrideSeconds;
    }

    public int maxRetry() {
        return maxRetry;
    }

    /** {@code attempt} 从 1 开始；返回 true = 这是最后一次尝试。 */
    public boolean isFinalAttempt(int attempt) {
        return attempt > maxRetry;
    }

    /** 第 {@code attempt} 次失败后的等待秒数。 */
    public long delaySeconds(int attempt) {
        if (overrideSeconds != null) {
            return Math.max(overrideSeconds, 0L);
        }
        long n = Math.max(attempt, 1);
        return n * n * n * n + 15 + (long) ThreadLocalRandom.current().nextInt(30) * (n + 1);
    }
}
