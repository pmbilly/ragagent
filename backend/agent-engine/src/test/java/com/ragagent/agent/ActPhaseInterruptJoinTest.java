package com.ragagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A4（中断语义）：父线程 join 被打断时，必须**取消子线程并等齐**再往下走。
 *
 * <p>原先中断后直接跳过 join 继续收集结果——子线程还在跑，"本轮收集 / 下一批启动"的写
 * 与它们并发（跨越写屏障）。这里的契约：中断 ⇒ 取消全部子线程 + 等齐 + 恢复中断标志。</p>
 */
class ActPhaseInterruptJoinTest {

    @Test
    @DisplayName("join 被打断：取消全部子线程、等齐后才返回，并恢复中断标志")
    void interruptedJoinCancelsAndWaitsForChildren() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        List<Thread> children = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            children.add(Thread.ofVirtual().unstarted(() -> {
                started.countDown();
                try {
                    Thread.sleep(60_000); // 只有被 interrupt 才会退出
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        children.forEach(Thread::start);
        assertThat(started.await(5, TimeUnit.SECONDS)).as("子线程已起来").isTrue();

        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean childAliveAtReturn = new AtomicBoolean();
        Thread parent = Thread.ofVirtual().start(() -> {
            interrupted.set(ToolBatchExecutor.joinAllCancellingOnInterrupt(children));
            childAliveAtReturn.set(children.stream().anyMatch(Thread::isAlive));
        });
        parent.interrupt(); // join 被打断（中断状态先置上，join 立即抛出）
        parent.join(10_000);

        assertThat(parent.isAlive()).as("父线程必须能返回（不得卡死）").isFalse();
        assertThat(interrupted).as("必须感知到中断").isTrue();
        assertThat(childAliveAtReturn)
                .as("返回时必须已等齐子线程——否则后续的写会与它们并发")
                .isFalse();
        assertThat(children.stream().noneMatch(Thread::isAlive)).as("子线程已取消").isTrue();
    }
}
