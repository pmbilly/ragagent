package com.ragagent.session.service;

import com.ragagent.contract.model.ResponseType;
import com.ragagent.platform.stream.StreamEvent;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 一轮问答的生命周期：把"是否已收尾"从各处局部标志收敛成一个原子状态。
 *
 * <p>同一件事若由三处各自判断（SSE 推送循环认 {@code complete}/{@code error+done}、
 * 续传循环再判断一次、事件总线收尾用局部标志防重入），口径一旦漂移就出现
 * "错误帧已写出但连接不关"或"收尾执行两次"。这里只做一件事：
 * <b>终态只有一个赢家，且终态不可逆</b>。</p>
 *
 * <p>状态迁移：{@code STREAMING → FINALIZING → DONE|FAILED|CANCELLED}；
 * 也允许 {@code STREAMING → DONE|FAILED|CANCELLED}（无收尾体的短路路径）。
 * {@link #finish(State)} 返回 true 表示本次调用赢下了终态。</p>
 *
 * <p><b>谁可以写终态</b>：只有轮次的所有者（{@code QaTurnExecutor} 的收尾回调）调用
 * {@link #finish(State)}。SSE 推送/续传循环是消费者，只用
 * {@link #isTerminalEvent(StreamEvent)} 判断退出——它们若抢先写 DONE，会把真正负责
 * 落库的收尾路径挡在外面。</p>
 */
public final class TurnLifecycle {

    public enum State { STREAMING, FINALIZING, DONE, FAILED, CANCELLED }

    private final AtomicReference<State> state = new AtomicReference<>(State.STREAMING);

    public State state() {
        return state.get();
    }

    public boolean isTerminal() {
        State current = state.get();
        return current == State.DONE || current == State.FAILED || current == State.CANCELLED;
    }

    /** 进入收尾段；只有第一次返回 true。 */
    public boolean beginFinalize() {
        return state.compareAndSet(State.STREAMING, State.FINALIZING);
    }

    /** 写终态；首次调用返回 true，已终态返回 false（幂等收尾）。 */
    public boolean finish(State terminal) {
        if (terminal != State.DONE && terminal != State.FAILED && terminal != State.CANCELLED) {
            throw new IllegalArgumentException("not a terminal state: " + terminal);
        }
        while (true) {
            State current = state.get();
            if (current == State.DONE || current == State.FAILED || current == State.CANCELLED) {
                return false;
            }
            if (state.compareAndSet(current, terminal)) {
                return true;
            }
        }
    }

    /** 流事件的终态口径（与推送/续传循环的退出条件一致）。 */
    public static boolean isTerminalEvent(StreamEvent event) {
        if (event == null) {
            return false;
        }
        return event.getType() == ResponseType.COMPLETE
                || (event.getType() == ResponseType.ERROR && event.isDone());
    }
}
