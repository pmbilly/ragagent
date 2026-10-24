package com.ragagent.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 终态矩阵：成功/失败竞争、取消后到达的成功、并发收尾，只有一个赢家。
 */
class TurnTerminalMatrixTest {

    @Test
    void failedThenDoneRemainsFailed() {
        TurnLifecycle lifecycle = new TurnLifecycle();
        assertThat(lifecycle.finish(TurnLifecycle.State.FAILED)).isTrue();
        assertThat(lifecycle.finish(TurnLifecycle.State.DONE)).isFalse();
        assertThat(lifecycle.state()).isEqualTo(TurnLifecycle.State.FAILED);
    }

    @Test
    void finalizeCannotStartAfterTerminal() {
        TurnLifecycle lifecycle = new TurnLifecycle();
        lifecycle.finish(TurnLifecycle.State.DONE);
        assertThat(lifecycle.beginFinalize()).isFalse();
    }

    @Test
    void concurrentFinishesHaveExactlyOneWinner() throws Exception {
        for (int round = 0; round < 20; round++) {
            TurnLifecycle lifecycle = new TurnLifecycle();
            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            try {
                for (int i = 0; i < threads; i++) {
                    boolean done = i % 2 == 0;
                    results.add(pool.submit(() -> {
                        start.await();
                        return lifecycle.finish(done
                                ? TurnLifecycle.State.DONE : TurnLifecycle.State.FAILED);
                    }));
                }
                start.countDown();
                int winners = 0;
                for (Future<Boolean> f : results) {
                    if (Boolean.TRUE.equals(f.get(5, TimeUnit.SECONDS))) {
                        winners++;
                    }
                }
                assertThat(winners).as("每轮只能有一个终态赢家").isEqualTo(1);
                assertThat(lifecycle.isTerminal()).isTrue();
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
