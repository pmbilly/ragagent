package com.ragagent.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 中间件行为断言（执行序与文案由固定录制钉住）。
 */
class EventMiddlewareTest {

    @Test
    void chainAppliesFirstListedAsOutermost() throws Exception {
        // 录制期望：chainOrder => [first-in second-in core second-out first-out]
        List<String> order = new ArrayList<>();
        EventMiddleware first = next -> event -> {
            order.add("first-in");
            try {
                next.handle(event);
            } finally {
                order.add("first-out");
            }
        };
        EventMiddleware second = next -> event -> {
            order.add("second-in");
            try {
                next.handle(event);
            } finally {
                order.add("second-out");
            }
        };
        EventHandler h = EventMiddleware.applyMiddleware(
                event -> order.add("core"), first, second);
        h.handle(new Event("", "evt", "", null, null, ""));
        assertEquals(List.of("first-in", "second-in", "core", "second-out", "first-out"), order);
    }

    @Test
    void withRecoveryConvertsPanicToPanicError() {
        // 录制期望：panicError => "panic in event handler: recovered-panic"
        EventHandler h = EventMiddleware.withRecovery().apply(event -> {
            throw new IllegalStateException("recovered-panic");
        });
        PanicError err = assertThrows(PanicError.class,
                () -> h.handle(new Event("", "evt", "", null, null, "")));
        assertEquals("panic in event handler: recovered-panic", err.getMessage());
    }

    @Test
    void withRecoveryPassesSuccessThrough() throws Exception {
        List<String> calls = new ArrayList<>();
        EventHandler h = EventMiddleware.withRecovery().apply(event -> calls.add("ok"));
        h.handle(new Event("", "evt", "", null, null, ""));
        assertEquals(List.of("ok"), calls);
    }

    @Test
    void withTimingWritesDurationIntoSharedMetadata() throws Exception {
        // 录制期望：timingSharedMetadata => callerSees=5——metadata map 跨值拷贝共享
        EventHandler h = EventMiddleware.withTiming().apply(event -> {
        });
        Event event = new Event("", "evt", "", null, new java.util.LinkedHashMap<>(), "");
        h.handle(event);
        Object ms = event.getMetadata().get("duration_ms");
        assertInstanceOf(Long.class, ms);
        assertTrue((Long) ms >= 0);
    }

    @Test
    void withTimingCreatesMetadataWhenAbsent() throws Exception {
        // metadata 为 null 时先初始化再写入
        EventHandler h = EventMiddleware.withTiming().apply(event -> {
        });
        Event event = new Event("", "evt", "", null, null, "");
        h.handle(event);
        assertTrue(event.getMetadata() != null && event.getMetadata().containsKey("duration_ms"));
    }

    @Test
    void withTimingPreservesHandlerException() {
        EventHandler h = EventMiddleware.withTiming().apply(event -> {
            throw new IllegalStateException("boom");
        });
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> h.handle(new Event("", "evt", "", null, null, "")));
        assertEquals("boom", ex.getMessage());
    }

    @Test
    void withLoggingPassesResultThrough() throws Exception {
        // withLogging 返回原异常 / 原成功——异常透传（日志内容不作为契约断言）
        List<String> calls = new ArrayList<>();
        EventHandler ok = EventMiddleware.withLogging().apply(event -> calls.add("ok"));
        ok.handle(new Event("", "evt", "s-1", null, null, "r-1"));
        assertEquals(List.of("ok"), calls);

        EventHandler failing = EventMiddleware.withLogging().apply(event -> {
            throw new IllegalStateException("handler err");
        });
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> failing.handle(new Event("", "evt", "s-1", null, null, "r-1")));
        assertEquals("handler err", ex.getMessage());
    }

    @Test
    void chainEqualsApplyMiddleware() throws Exception {
        // ApplyMiddleware(handler, mws...) 等价于 Chain(mws...)(handler)
        List<String> order = new ArrayList<>();
        EventMiddleware mw = next -> event -> {
            order.add("mw");
            next.handle(event);
        };
        EventHandler core = event -> order.add("core");
        EventMiddleware.chain(mw).apply(core).handle(new Event("", "evt", "", null, null, ""));
        EventMiddleware.applyMiddleware(core, mw).handle(new Event("", "evt", "", null, null, ""));
        assertEquals(List.of("mw", "core", "mw", "core"), order);
    }

    @Test
    void recoveryErrorInsideEmitIsWrappedLikeGo() {
        // 组合语义：WithRecovery 返回的 PanicError 经 Emit 包装 =>
        // "event handler failed for evt: panic in event handler: kaboom"
        EventBus bus = new EventBus();
        bus.on("evt", EventMiddleware.withRecovery().apply(event -> {
            throw new IllegalArgumentException("kaboom");
        }));
        EventBusException ex = assertThrows(EventBusException.class,
                () -> bus.emit(new Event("", "evt", "", null, null, "")));
        assertEquals("event handler failed for evt: panic in event handler: kaboom",
                ex.getMessage());
        assertInstanceOf(PanicError.class, ex.getCause());
    }
}
