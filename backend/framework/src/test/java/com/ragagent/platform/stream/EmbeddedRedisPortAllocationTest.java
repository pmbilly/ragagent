package com.ragagent.platform.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 端口分配探针：并发启动多个实例必须拿到互不相同的端口。
 *
 * <p>这是 {@code EmbeddedRedis} 那道文件锁的**红态探针**——旧实现用
 * {@code new ServerSocket(0)} 探端口，并发下会撞同一端口，进而出现"借用别人的实例、
 * 对方关进程后连接全断"。改回探测式分配时本用例会红。</p>
 */
class EmbeddedRedisPortAllocationTest {

    private static final int CONCURRENCY = 4;

    @Test
    @DisplayName("并发启动的实例端口互不相同（文件锁互斥）")
    void concurrentStartsGetDistinctPorts() throws Exception {
        assumeTrue(System.getenv("REDIS_TEST_ADDR") == null,
                "外部共享实例模式下不分配端口，本探针不适用");

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<EmbeddedRedis>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < CONCURRENCY; i++) {
                futures.add(pool.submit(() -> {
                    gate.await();
                    return EmbeddedRedis.tryStart();
                }));
            }
            gate.countDown();

            List<EmbeddedRedis> instances = new ArrayList<>();
            try {
                for (Future<EmbeddedRedis> future : futures) {
                    EmbeddedRedis instance = future.get(30, TimeUnit.SECONDS);
                    assumeTrue(instance != null, "本机没有 redis-server，跳过");
                    instances.add(instance);
                }
                assertThat(instances).hasSize(CONCURRENCY);

                Set<Integer> ports = new HashSet<>();
                for (EmbeddedRedis instance : instances) {
                    LettuceConnectionFactory factory =
                            (LettuceConnectionFactory) instance.template().getConnectionFactory();
                    ports.add(factory.getPort());
                }
                assertThat(ports)
                        .as("并发实例必须各占一个端口；重复即端口分配存在竞态")
                        .hasSize(CONCURRENCY);
            } finally {
                instances.forEach(EmbeddedRedis::close);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
