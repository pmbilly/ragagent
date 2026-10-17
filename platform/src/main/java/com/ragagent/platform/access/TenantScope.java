package com.ragagent.platform.access;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 在指定租户快照下执行一段代码，并在退出时恢复调用线程原有上下文。
 *
 * <p>虚拟线程不继承 ThreadLocal；跨线程必须显式 {@link TenantContextSnapshot#capture()}
 * / replay。手写 save-replay 容易漏 finally，或在池化线程上把身份泄漏给下一个任务。
 * 本类把这对操作收成一个作用域：进入时 replay，退出时先清空再恢复原值。</p>
 */
public final class TenantScope {

    private TenantScope() {
    }

    /** 无返回值版本。 */
    public static void run(TenantContextSnapshot snapshot, Runnable body) {
        Objects.requireNonNull(snapshot, "snapshot");
        TenantContextSnapshot previous = TenantContextSnapshot.capture();
        snapshot.replay();
        try {
            body.run();
        } finally {
            restore(previous);
        }
    }

    /** 有返回值版本。 */
    public static <T> T supply(TenantContextSnapshot snapshot, Supplier<T> body) {
        Objects.requireNonNull(snapshot, "snapshot");
        TenantContextSnapshot previous = TenantContextSnapshot.capture();
        snapshot.replay();
        try {
            return body.get();
        } finally {
            restore(previous);
        }
    }

    /** 供 executor.submit(...) 使用的 Callable 包装（保留受检异常）。 */
    public static <T> Callable<T> callable(TenantContextSnapshot snapshot, Callable<T> body) {
        Objects.requireNonNull(snapshot, "snapshot");
        return () -> {
            TenantContextSnapshot previous = TenantContextSnapshot.capture();
            snapshot.replay();
            try {
                return body.call();
            } finally {
                restore(previous);
            }
        };
    }

    /** 供 Thread.ofVirtual().start(...) 使用的 Runnable 包装。 */
    public static Runnable runnable(TenantContextSnapshot snapshot, Runnable body) {
        return () -> run(snapshot, body);
    }

    /**
     * 先 clear 再恢复：{@code TenantContext} 未来新增字段时，也不会把本作用域
     * 写进去的值留给下一个任务。
     */
    private static void restore(TenantContextSnapshot previous) {
        TenantContext.clear();
        previous.replay();
    }
}
