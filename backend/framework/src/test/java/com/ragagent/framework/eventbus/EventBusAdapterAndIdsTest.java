package com.ragagent.framework.eventbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ragagent.framework.event.Event;
import com.ragagent.framework.event.EventBusException;
import com.ragagent.framework.event.EventBusInterface;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * 事件总线适配器 + ID 生成的行为断言。
 */
class EventBusAdapterAndIdsTest {

    // ===== EventBusAdapter =====

    @Test
    void adapterOnThenEmitDispatches() {
        // EventBusAdapter.On 注册到包装的总线、
        // Emit 走底层总线。恒等转换：
        // 接口注册 → 底层 on；接口发射 → 底层 emit（含 ID 自动生成）。
        EventBus bus = new EventBus();
        EventBusInterface iface = new EventBusAdapter(bus);
        AtomicReference<String> seen = new AtomicReference<>();
        iface.on("evt", e -> seen.set(e.getId()));

        assertEquals(1, bus.getHandlerCount("evt"), "adapter must register on the wrapped bus");

        Event caller = new Event("", "evt", "s-1", null, null, "");
        iface.emit(caller);
        assertEquals(36, seen.get().length(), "underlying bus ID autogen must apply");

        // asEventBusInterface 返回同一适配器形态
        EventBusInterface viaMethod = bus.asEventBusInterface();
        assertTrue(viaMethod instanceof EventBusAdapter);
    }

    @Test
    void adapterFailurePropagatesAsEventBusException() {
        EventBus bus = new EventBus();
        EventBusInterface iface = new EventBusAdapter(bus);
        iface.on("evt", e -> {
            throw new IllegalStateException("boom");
        });
        EventBusException ex = assertThrowsEventBusException(() ->
                iface.emit(new Event("", "evt", "", null, null, "")));
        assertEquals("event handler failed for evt: boom", ex.getMessage());
    }

    private static EventBusException assertThrowsEventBusException(Runnable r) {
        try {
            r.run();
        } catch (EventBusException e) {
            return e;
        }
        throw new AssertionError("expected EventBusException");
    }

    // ===== EventIds =====

    @Test
    void generateEventIdMatchesGoShape() {
        // 形如 "<uuid 前 8 位 hex>-<suffix>"
        // 录制 sample => 286fbbe5-thinking
        String id = EventIds.generateEventId("thinking");
        assertTrue(id.matches("^[0-9a-f]{8}-thinking$"),
                "must be 8 lowercase hex chars + '-' + suffix, got: " + id);
    }

    @Test
    void generateEventIdSuffixPreservedVerbatim() {
        assertTrue(EventIds.generateEventId("answer").endsWith("-answer"));
        assertTrue(EventIds.generateEventId("thinking-tool").matches("^[0-9a-f]{8}-thinking-tool$"));
        assertTrue(EventIds.generateEventId("x").matches("^[0-9a-f]{8}-x$"));
    }

    @Test
    void generateEventIdIsUniqueAcrossCalls() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            ids.add(EventIds.generateEventId("complete"));
        }
        assertEquals(64, ids.size());
    }
}
