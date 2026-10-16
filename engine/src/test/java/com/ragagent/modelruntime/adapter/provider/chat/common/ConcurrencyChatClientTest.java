package com.ragagent.modelruntime.adapter.provider.chat.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.ragagent.llm.LlmChatClient;
import com.ragagent.contract.model.chat.ChatMessage;
import com.ragagent.contract.model.chat.ChatOptions;
import com.ragagent.contract.model.chat.ChatResponse;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.llm.ChatStream;
import com.ragagent.llm.domain.StreamResponse;
import com.ragagent.llm.limiter.BackgroundTaskContext;
import com.ragagent.llm.limiter.ConcurrencyGovernor;
import com.ragagent.llm.limiter.LocalLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 并发装饰器的验收基准（两条硬契约）。
 *
 * 这两条是并发语义的硬契约：
 * 1. 交互式调用完全绕过 governor（即使 limit=1 也不排队）——用户延迟不排在信号量后面
 * 2. 消费者放弃流后，持有的槽位必须释放而不是泄漏
 */
class ConcurrencyChatClientTest {

    @AfterEach
    void resetGovernor() {
        BackgroundTaskContext.clear();
    }

    /** 流式持续产出直到被放弃的最小 fake。 */
    private static class FakeChat implements LlmChatClient {
        private final String id;
        private final boolean streamForever;
        /** 上游被关（A3：close 必须传播到 provider 侧）。 */
        final CountDownLatch aborted = new CountDownLatch(1);

        FakeChat(String id, boolean streamForever) {
            this.id = id;
            this.streamForever = streamForever;
        }

        @Override
        public ChatResponse chat(List<ChatMessage> messages, ChatOptions options) {
            return new ChatResponse();
        }

        @Override
        public ChatStream chatStream(List<ChatMessage> messages, ChatOptions options) {
            QueuedChatStream q = new QueuedChatStream(aborted::countDown);
            Thread.ofVirtual().start(() -> {
                try {
                    if (streamForever) {
                        // 持续产出非终态块（模拟思考/长输出）。必须有限：无限生产会与
                        // 转发线程的 drain() 组成永动自旋对，偷走 CPU 拖慢后续所有测试
                        // （实测：满负载下 bcrypt 重的契约类慢 3 倍、疑似挂死）。
                        for (int i = 0; i < 20_000; i++) {
                            q.put(StreamResponse.of(ResponseType.ANSWER, "chunk", false));
                        }
                    }
                    q.put(StreamResponse.of(ResponseType.ANSWER, "done", true));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return q;
        }

        @Override
        public String getModelName() {
            return id;
        }

        @Override
        public String getModelId() {
            return id;
        }
    }

    /** 交互式调用在 limit=1 下也不被节流。 */
    @Test
    void interactiveCallsAreNotGated() {
        ConcurrencyGovernor governor = new ConcurrencyGovernor();
        governor.setGovernor(new LocalLimiter(), 1);
        ConcurrencyChatClient w = new ConcurrencyChatClient(new FakeChat("model-x", false), 0, governor);

        // 无后台标记：两次调用都不该被节流（若被节流，第二次会卡在信号量上）
        ChatResponse first = w.chat(List.of(), new ChatOptions());
        ChatResponse second = w.chat(List.of(), new ChatOptions());
        assertEquals("", first.getContent());
        assertEquals("", second.getContent());
    }

    /**
     * 消费者停止读取后，持有的槽位必须释放。
     *
     * 释放由"发送阻塞超过放弃阈值"触发（测试里设成 1 秒）。
     */
    @Test
    void streamReleasesSlotOnAbandon() throws Exception {
        ConcurrencyGovernor governor = new ConcurrencyGovernor();
        governor.setGovernor(new LocalLimiter(), 1);
        ConcurrencyChatClient w = new ConcurrencyChatClient(
                new FakeChat("model-y", true), 0, governor, 1 /* abandonTimeoutSeconds */);

        BlockingQueue<StreamResponse> out;
        try (BackgroundTaskContext.Scope ignored = BackgroundTaskContext.mark()) {
            out = w.chatStream(List.of(), new ChatOptions());
        }

        // 消费一块，让转发线程跑起来并持有槽位
        assertTrue(out.poll(5, TimeUnit.SECONDS) != null, "应能取到第一块");

        // 槽位已被持有：后台获取必须阻塞
        CountDownLatch acquired = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> {
            try (BackgroundTaskContext.Scope ignored = BackgroundTaskContext.mark()) {
                governor.gate("model-y").close();
                acquired.countDown();
            }
        });
        assertFalse(acquired.await(500, TimeUnit.MILLISECONDS), "槽位应由活跃流持有，后台获取不该成功");

        // 放弃：不再读取 out。转发线程应在放弃阈值后释放槽位并排干内层队列
        assertTrue(acquired.await(15, TimeUnit.SECONDS),
                "流槽位泄漏：消费者放弃后未释放");
    }

    /**
     * A3：句柄 {@code close()} 后槽位**立即**归还（不等放弃阈值），且关闭传播到上游。
     */
    @Test
    void closeReleasesSlotImmediatelyAndAbortsUpstream() throws Exception {
        // 记录型 limiter：直接观察 permit 归还时机，避免真实信号量 + 线程调度引起的假红。
        CountDownLatch permitReleased = new CountDownLatch(1);
        ConcurrencyGovernor governor = new ConcurrencyGovernor();
        governor.setGovernor((key, limit) -> permitReleased::countDown, 1);
        FakeChat inner = new FakeChat("model-z", true);
        // 放弃阈值刻意设大：归还必须来自 close，而不是"猜消费者已走"的超时兜底
        ConcurrencyChatClient w = new ConcurrencyChatClient(inner, 0, governor, 60);

        ChatStream out;
        try (BackgroundTaskContext.Scope ignored = BackgroundTaskContext.mark()) {
            out = w.chatStream(List.of(), new ChatOptions());
        }
        assertTrue(out.poll(5, TimeUnit.SECONDS) != null, "应能取到第一块");

        out.close();

        // 阈值 60s：15s 内归还即可判定"来自 close 而非超时兜底"。
        assertTrue(permitReleased.await(15, TimeUnit.SECONDS),
                "close 后槽位必须立即归还（否则要等 60s 放弃阈值）");
        assertTrue(inner.aborted.await(15, TimeUnit.SECONDS), "close 必须传播到上游 provider 侧");
        assertTrue(out.isClosed(), "句柄状态可查（幂等 close 的依据）");
    }
}
