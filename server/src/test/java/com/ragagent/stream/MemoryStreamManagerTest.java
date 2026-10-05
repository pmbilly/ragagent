package com.ragagent.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ragagent.common.llm.ResponseType;
import org.junit.jupiter.api.Test;

/**
 * 内存流管理器的语义。
 *
 * <p>重点钉两类行为：</p>
 * <ul>
 *   <li><b>值语义</b>：{@code AppendEvent} 收的是值，补时间戳不该写回调用方的对象
 *       （引用语义下靠 {@code copy()} 保证）。</li>
 *   <li><b>live-run 的排他性</b>：{@code SetLiveRun} 是第二个引擎的闸门，
 *       接手的后续轮次只能走 {@code ClaimLiveRun}。</li>
 * </ul>
 */
class MemoryStreamManagerTest {

    private final MemoryStreamManager manager = new MemoryStreamManager();

    // ── live run ────────────────────────────────────────────────────────────

    @Test
    void getLiveRunIsEmptyWhenNothingIsMarked() {
        LiveRun run = manager.getLiveRun("sess-missing");
        assertEquals(LiveRun.NONE, run);
        assertFalse(run.isPresent());
    }

    @Test
    void setLiveRunRejectsADifferentAssistantButClaimOverwrites() {
        manager.setLiveRun("sess-1", "assist-1", "req-1");

        assertThrows(LiveRunExistsException.class,
                () -> manager.setLiveRun("sess-1", "assist-2", "req-2"));
        // 被拒绝后标记仍是第一条
        assertEquals("assist-1", manager.getLiveRun("sess-1").assistantMessageId());

        // 接手路径覆盖
        manager.claimLiveRun("sess-1", "assist-2", "req-2");
        LiveRun run = manager.getLiveRun("sess-1");
        assertEquals("assist-2", run.assistantMessageId());
        assertEquals("req-2", run.requestId());
    }

    @Test
    void setLiveRunIsIdempotentForTheSameAssistant() {
        manager.setLiveRun("sess-1", "assist-1", "req-1");
        manager.setLiveRun("sess-1", "assist-1", "req-1");
        assertEquals("assist-1", manager.getLiveRun("sess-1").assistantMessageId());
    }

    @Test
    void clearLiveRunOnlyDropsTheMarkerWhenItStillPointsAtThatRun() {
        // 后续轮次可能已经抢到了会话——此时拆掉旧轮次不能把新标记一起删了
        manager.setLiveRun("sess-1", "assist-1", "req-1");

        manager.clearLiveRun("sess-1", "assist-2");
        assertEquals("assist-1", manager.getLiveRun("sess-1").assistantMessageId());

        manager.clearLiveRun("sess-1", "assist-1");
        assertFalse(manager.getLiveRun("sess-1").isPresent());
    }

    // ── 事件流 ──────────────────────────────────────────────────────────────

    @Test
    void appendEventStampsTheTimestampWithoutMutatingTheCaller() {
        // AppendEvent 收值，补时间戳不写回调用方（copy() 保证）
        StreamEvent event = new StreamEvent("e-1", ResponseType.ANSWER, "chunk", false);
        assertNull(event.getTimestamp(), "调用方的对象不该被补上时间戳");

        manager.appendEvent("sess-1", "msg-1", event);
        assertNull(event.getTimestamp(), "调用方的对象仍不该被改写");

        StreamBatch batch = manager.getEvents("sess-1", "msg-1", 0);
        assertEquals(1, batch.events().size());
        assertNotNull(batch.events().get(0).getTimestamp());
    }

    @Test
    void getEventsReturnsOnlyNewEventsAndAdvancesTheOffset() {
        manager.appendEvent("s", "m", new StreamEvent("e1", ResponseType.ANSWER, "a", false));
        manager.appendEvent("s", "m", new StreamEvent("e2", ResponseType.ANSWER, "b", false));

        StreamBatch first = manager.getEvents("s", "m", 0);
        assertEquals(2, first.events().size());
        assertEquals(2, first.nextOffset());

        // 没有新事件时是空批，offset 不动
        StreamBatch again = manager.getEvents("s", "m", first.nextOffset());
        assertTrue(again.events().isEmpty());
        assertEquals(2, again.nextOffset());

        manager.appendEvent("s", "m", new StreamEvent("e3", ResponseType.ANSWER, "c", true));
        StreamBatch third = manager.getEvents("s", "m", 2);
        assertEquals(1, third.events().size());
        assertEquals("e3", third.events().get(0).getId());
        assertEquals(3, third.nextOffset());
    }

    @Test
    void getEventsOnAnUnknownStreamIsEmpty() {
        assertEquals(StreamBatch.empty(7), manager.getEvents("nope", "nope", 7));
    }

    // ── steer 控制面 ────────────────────────────────────────────────────────

    @Test
    void appendSteerEventsDeduplicatesClientIds() throws Exception {
        // 客户端 ID 去重（内存分支）
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                tasks.add(() -> {
                    // 每个线程自己的实例——列表元素是各自独立的副本
                    StreamEvent evt = new StreamEvent("same-client-id", ResponseType.STEER, "do this next",
                            false);
                    evt.setData(Map.of("delivery", "after"));
                    manager.appendSteerEvents("session", "run", List.of(evt));
                    return null;
                });
            }
            for (Future<Void> f : pool.invokeAll(tasks)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        List<StreamEvent> events = manager.getSteerEvents("session", "run", 0).events();
        assertEquals(1, events.size(), "同一个客户端 ID 只该进列一次");

        // 标记 consumed 之后再灌同一个 ID，仍然只有一条，且 consumed 保留
        assertTrue(manager.updateSteerEventData("session", "run", "same-client-id",
                Map.of("consumed", true)));
        manager.appendSteerEvents("session", "run",
                List.of(new StreamEvent("same-client-id", ResponseType.STEER, "do this next", false)));

        List<StreamEvent> after = manager.getSteerEvents("session", "run", 0).events();
        assertEquals(1, after.size());
        assertEquals(Boolean.TRUE, after.get(0).getData().get("consumed"));
    }

    @Test
    void updateSteerEventDataReplacesTheMapInsteadOfMutatingIt() {
        // GetSteerEvents 交出去的是浅拷贝，调用方可能还握着旧的 map —— 改动必须是替换
        StreamEvent evt = new StreamEvent("s1", ResponseType.STEER, "queued", true);
        evt.setData(new java.util.LinkedHashMap<>(Map.of("delivery", "after")));
        manager.appendSteerEvents("session", "run", List.of(evt));

        StreamEvent handedOut = manager.getSteerEvents("session", "run", 0).events().get(0);
        Map<String, Object> oldMap = handedOut.getData();

        assertTrue(manager.updateSteerEventData("session", "run", "s1", Map.of("consumed", true)));
        // 旧 map 原封不动
        assertNull(oldMap.get("consumed"));
        assertEquals(Boolean.TRUE,
                manager.getSteerEvents("session", "run", 0).events().get(0).getData().get("consumed"));
    }

    @Test
    void updateSteerEventDataOnAMissingEventIsFalse() {
        assertFalse(manager.updateSteerEventData("session", "run", "nope", Map.of("consumed", true)));
    }

    @Test
    void deleteSteerEventRefusesAConsumedEvent() {
        manager.appendSteerEvents("session", "run",
                List.of(new StreamEvent("s1", ResponseType.STEER, "queued", true)));

        assertTrue(manager.deleteSteerEvent("session", "run", "s1"));
        assertTrue(manager.getSteerEvents("session", "run", 0).events().isEmpty());

        manager.appendSteerEvents("session", "run",
                List.of(new StreamEvent("s2", ResponseType.STEER, "queued", true)));
        manager.updateSteerEventData("session", "run", "s2", Map.of("consumed", true));

        assertFalse(manager.deleteSteerEvent("session", "run", "s2"),
                "已并入运行轮次的消息不能被浮层撤回");
        assertEquals(1, manager.getSteerEvents("session", "run", 0).events().size());
    }

    @Test
    void deleteSteerEventOnAMissingStreamIsFalse() {
        assertFalse(manager.deleteSteerEvent("nope", "nope", "s1"));
    }

    // ── 过期清扫（Redis 键 TTL 的内存等价物） ────────────────────────────────

    @Test
    void expiredStreamsAreSweptOnTheNextWrite() throws Exception {
        // ttl=100ms → 节流下限 100ms：250ms 后的写入触发全扫，过期流被整体删除
        MemoryStreamManager shortTtl = new MemoryStreamManager(Duration.ofMillis(100));
        shortTtl.appendSteerEvents("stale", "run",
                List.of(new StreamEvent("s1", ResponseType.STEER, "queued", true)));

        Thread.sleep(250);
        shortTtl.appendEvent("fresh", "run", new StreamEvent("e1", ResponseType.ANSWER, "a", false));

        // 过期流已不存在（流缺失时更新返回 false；本场景若流还在，事件就在、会返回 true）
        assertFalse(shortTtl.updateSteerEventData("stale", "run", "s1", Map.of("consumed", true)));
        // 同一轮清扫不误伤新写入的流
        assertEquals(1, shortTtl.getEvents("fresh", "run", 0).events().size());
    }

    @Test
    void streamsWithinTtlSurviveTheSweep() throws Exception {
        // ttl=300ms → 节流 150ms：200ms 后的写入触发全扫，但 live 仍在窗口内
        MemoryStreamManager shortTtl = new MemoryStreamManager(Duration.ofMillis(300));
        shortTtl.appendSteerEvents("live", "run",
                List.of(new StreamEvent("s1", ResponseType.STEER, "queued", true)));

        Thread.sleep(200);
        shortTtl.appendEvent("other", "run", new StreamEvent("e1", ResponseType.ANSWER, "a", false));

        assertTrue(shortTtl.updateSteerEventData("live", "run", "s1", Map.of("consumed", true)));
    }
}
