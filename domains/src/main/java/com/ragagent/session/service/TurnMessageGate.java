package com.ragagent.session.service;

/**
 * 一轮问答内 assistant 行的唯一写入监视器。
 *
 * <p>此前同一行消息由三处用不同监视器保护（事件桥的内部锁、最终答复回调的
 * {@code synchronized (am)}、快答时间线的 {@code synchronized (msg)}）——互不排斥，
 * 并发写仍可能交错。现在三者共用本轮 {@link #monitor()}。</p>
 */
public final class TurnMessageGate {

    private final Object monitor = new Object();

    /** 监视器对象；调用方以 {@code synchronized (gate.monitor())} 进入临界区。 */
    public Object monitor() {
        return monitor;
    }
}
