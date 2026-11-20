package com.ragagent.platform.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InProcessTaskQueueTest {

    @Test
    void retriesUntilSuccessAndMarksFinalAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        List<Boolean> finalFlags = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        InProcessTaskQueue queue = new InProcessTaskQueue(
                "test",
                new RetryPolicy(2, 0L),
                (body, finalAttempt) -> {
                    finalFlags.add(finalAttempt);
                    if (attempts.incrementAndGet() < 3) {
                        throw new RuntimeException("transient");
                    }
                    done.countDown();
                });

        try {
            queue.enqueue("{}");
            assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(3);
            assertThat(finalFlags).containsExactly(false, false, true);
        } finally {
            queue.shutdown();
        }
    }

    @Test
    void enqueueAfterShutdownIsRejected() {
        InProcessTaskQueue queue = new InProcessTaskQueue("test", new RetryPolicy(0), (body, last) -> {
        });
        queue.shutdown();

        assertThatThrownBy(() -> queue.enqueue("{}"))
                .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    void retryPolicyUsesFinalAttemptAndOverride() {
        RetryPolicy policy = new RetryPolicy(2, 0L);

        assertThat(policy.isFinalAttempt(1)).isFalse();
        assertThat(policy.isFinalAttempt(2)).isFalse();
        assertThat(policy.isFinalAttempt(3)).isTrue();
        assertThat(policy.delaySeconds(1)).isZero();
    }
}
