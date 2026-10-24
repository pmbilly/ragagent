package com.ragagent.session.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ragagent.contract.event.EventBusFactory;
import com.ragagent.contract.event.EventBusInterface;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.session.SseSink;
import com.ragagent.platform.storage.support.StreamRewriter;
import com.ragagent.platform.stream.StreamBatch;
import com.ragagent.platform.stream.StreamEvent;
import com.ragagent.platform.stream.StreamManager;
import com.ragagent.platform.web.JsonMappers;
import com.ragagent.session.controller.sse.SseFrameWriter;
import com.ragagent.session.conversation.MessageService;
import com.ragagent.session.conversation.SessionService;
import com.ragagent.session.service.QaSupport.SseStreamContext;
import com.ragagent.session.service.sse.StreamEventEmitter;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * SSE 推送主循环的终态契约。
 *
 * <p>核心不变量：{@code error}+done 是终态元素，写出后必须退出轮询；
 * 否则 HTTP 线程会在 {@code while(true)} 里 100ms 一次地空转，直到客户端断开。</p>
 */
class QaSseOrchestratorTest {

    private static final String SESSION_ID = "sess-1";
    private static final String MESSAGE_ID = "msg-1";
    private static final String REQUEST_ID = "req-1";

    private StreamManager streamManager;
    private QaSseOrchestrator orchestrator;
    private final List<String> frames = new ArrayList<>();

    @BeforeEach
    void setUp() {
        streamManager = mock(StreamManager.class);
        SseFrameWriter frameWriter = new SseFrameWriter(JsonMappers.lenient());
        orchestrator = new QaSseOrchestrator(
                streamManager,
                new StreamEventEmitter(frameWriter),
                mock(SessionService.class),
                mock(MessageService.class),
                frameWriter,
                mock(QaTurnFinalizer.class),
                mock(EventBusFactory.class));
    }

    private SseSink sink() {
        return new SseSink() {
            @Override
            public void applyHeaders() {
            }

            @Override
            public void writeFrame(String frame) {
                frames.add(frame);
            }
        };
    }

    private static StreamEvent event(String id, ResponseType type, String content, boolean done) {
        StreamEvent evt = new StreamEvent(id, type, content, done);
        evt.setTimestamp(OffsetDateTime.now());
        return evt;
    }

    private static StreamBatch batch(List<StreamEvent> events, int nextOffset) {
        return new StreamBatch(new ArrayList<>(events), nextOffset);
    }

    /** 收到 {@code error}+done 后不再读下一个 offset。 */
    @Test
    void stopsPollingOnErrorDone() throws Exception {
        when(streamManager.getEvents(anyString(), anyString(), eq(0)))
                .thenReturn(batch(List.of(event("e1", ResponseType.ANSWER, "part1", false)), 1));
        when(streamManager.getEvents(anyString(), anyString(), eq(1)))
                .thenReturn(batch(List.of(event("e2", ResponseType.ERROR, "boom", true)), 2));
        // 修复前会继续读 offset=2；修复后不应触碰
        when(streamManager.getEvents(anyString(), anyString(), eq(2)))
                .thenReturn(batch(List.of(event("e3", ResponseType.COMPLETE, "", true)), 3));

        orchestrator.handleAgentEventsForSSE(
                sink(), SESSION_ID, MESSAGE_ID, REQUEST_ID,
                new SseStreamContext(), false, new StreamRewriter(null));

        verify(streamManager, never()).getEvents(anyString(), anyString(), eq(2));
        assertThat(frames).anyMatch(frame -> frame.contains("boom"));
    }

    /** STOP 的取消只由 stop watcher 发出，SSE 循环不再重复 emit。 */
    @Test
    void stopFrameWrittenWithoutEmittingStopEventTwice() throws Exception {
        when(streamManager.getEvents(anyString(), anyString(), eq(0)))
                .thenReturn(batch(List.of(event("s1", ResponseType.STOP, "", true)), 1));
        SseStreamContext ctx = new SseStreamContext();
        EventBusInterface bus = mock(EventBusInterface.class);
        ctx.eventBus = bus;

        orchestrator.handleAgentEventsForSSE(
                sink(), SESSION_ID, MESSAGE_ID, REQUEST_ID,
                ctx, false, new StreamRewriter(null));

        assertThat(frames).anyMatch(frame -> frame.contains("Generation stopped by user"));
        verify(bus, never()).emit(org.mockito.ArgumentMatchers.any());
    }
}
