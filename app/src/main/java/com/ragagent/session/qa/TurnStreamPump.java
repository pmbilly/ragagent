package com.ragagent.session.qa;

import com.ragagent.contract.event.Event;
import com.ragagent.contract.event.payload.StopData;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.model.StreamResponse;
import com.ragagent.contract.session.SseSink;
import com.ragagent.platform.access.TenantContextSnapshot;
import com.ragagent.platform.access.TenantScope;
import com.ragagent.platform.event.EventType;
import com.ragagent.platform.storage.support.StreamRewriter;
import com.ragagent.platform.stream.StreamBatch;
import com.ragagent.platform.stream.StreamEvent;
import com.ragagent.platform.stream.StreamManager;
import com.ragagent.session.service.QaSupport.SseStreamContext;
import com.ragagent.session.service.TurnLifecycle;
import com.ragagent.session.service.sse.SseFrameSink;
import com.ragagent.session.service.sse.StreamEventEmitter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一轮问答的流泵：唯一的读流循环。
 *
 * <p>有 SSE 客户端时每 100ms 读一次并把事件写成帧；客户端断开后**降级为无头监控**
 * （每 300ms），继续检测 STOP/终态并发出 {@code EVENT_STOP} 取消引擎——取消能力不随
 * 连接消失。STOP 的取消事件只由本类发出一次。</p>
 *
 * <p>这样 stop watcher 与 SSE 轮询合成一个 owner；终态判定、stop 帧、取消事件不再各写一份。</p>
 */
final class TurnStreamPump {

    private static final Logger log = LoggerFactory.getLogger(TurnStreamPump.class);
    private static final long MAX_DURATION_MILLIS = 2 * 60 * 60 * 1000L;
    private static final long SSE_INTERVAL_MILLIS = 100;
    private static final long HEADLESS_INTERVAL_MILLIS = 300;
    private static final long TITLE_WAIT_MILLIS = 3000;
    private static final long TITLE_POLL_MILLIS = 50;

    private final StreamManager streamManager;
    private final StreamEventEmitter emitter;
    private final SseFrameSink frameWriter;

    TurnStreamPump(StreamManager streamManager, StreamEventEmitter emitter,
            SseFrameSink frameWriter) {
        this.streamManager = streamManager;
        this.emitter = emitter;
        this.frameWriter = frameWriter;
    }

    /** 无 SSE 客户端（steer follow-up 等）：虚拟线程里只盯 STOP/终态。 */
    void startHeadless(String sessionId, String assistantMessageId, SseStreamContext streamCtx) {
        // 虚拟线程没有 ThreadLocal：读流与会话范围判定需要完整身份
        TenantContextSnapshot identity = TenantContextSnapshot.capture();
        Thread.ofVirtual().start(() -> TenantScope.run(identity, () ->
                run(null, sessionId, assistantMessageId, "", streamCtx, false, null)));
    }

    /**
     * 读流直到终态。{@code sink} 为空即无头模式；写失败时自动降级，不丢取消。
     */
    void run(SseSink initialSink, String sessionId, String assistantMessageId, String requestId,
            SseStreamContext streamCtx, boolean waitForTitle, StreamRewriter resourceRewriter) {
        SseSink sink = initialSink;
        AtomicBoolean clientGone = new AtomicBoolean(false);
        StreamEventEmitter.ClientState client = clientGone::get;
        AtomicBoolean stopEmitted = new AtomicBoolean(false);

        int offset = 0;
        long deadline = System.currentTimeMillis() + MAX_DURATION_MILLIS;
        long interval = sink == null ? HEADLESS_INTERVAL_MILLIS : SSE_INTERVAL_MILLIS;

        if (sink != null) {
            sink.applyHeaders();
        }

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            StreamBatch batch;
            try {
                batch = streamManager.getEvents(sessionId, assistantMessageId, offset);
            } catch (RuntimeException e) {
                log.warn("Failed to get events from stream session={} message={}",
                        sessionId, assistantMessageId, e);
                continue;
            }
            offset = batch.nextOffset();
            boolean streamCompleted = false;
            boolean titleReceived = false;
            for (StreamEvent evt : batch.events()) {
                if (evt.getType() == ResponseType.STOP) {
                    streamCtx.cancelled = true;
                    if (stopEmitted.compareAndSet(false, true)) {
                        emitStop(sessionId, assistantMessageId, streamCtx);
                    }
                    if (sink != null) {
                        try {
                            emitter.flushHeldStreamContent(sink, requestId, resourceRewriter, client);
                            writeStopFrame(sink, requestId);
                        } catch (IOException e) {
                            clientGone.set(true);
                            sink = null;
                        }
                    }
                    return;
                }
                if (evt.getType() == ResponseType.COMPLETE) {
                    streamCompleted = true;
                }
                if (evt.getType() == ResponseType.SESSION_TITLE) {
                    titleReceived = true;
                }
                if (sink != null) {
                    try {
                        emitter.emitStreamEvent(sink, evt, requestId, resourceRewriter, client);
                    } catch (IOException e) {
                        // 客户端断开：降级为无头监控，继续盯 STOP/终态
                        clientGone.set(true);
                        sink = null;
                        interval = HEADLESS_INTERVAL_MILLIS;
                    }
                }
                if (TurnLifecycle.isTerminalEvent(evt) && evt.getType() != ResponseType.COMPLETE) {
                    log.info("Stream terminated with error, session={}, message={}",
                            sessionId, assistantMessageId);
                    return;
                }
            }
            if (streamCompleted) {
                if (sink != null && waitForTitle && !titleReceived) {
                    offset = waitForTitle(sink, sessionId, assistantMessageId, requestId,
                            resourceRewriter, client, offset);
                }
                return;
            }
        }
        // 超时不是"记日志退出"：走同一 STOP 协议让执行体到取消点、由 finalizer 提交终态
        log.warn("Stream pump exceeded max duration, cancelling, session={}, message={}",
                sessionId, assistantMessageId);
        streamCtx.cancelled = true;
        if (stopEmitted.compareAndSet(false, true)) {
            emitStop(sessionId, assistantMessageId, streamCtx);
        }
    }

    private int waitForTitle(SseSink sink, String sessionId, String assistantMessageId,
            String requestId, StreamRewriter resourceRewriter,
            StreamEventEmitter.ClientState client, int offset) {
        long titleDeadline = System.currentTimeMillis() + TITLE_WAIT_MILLIS;
        while (System.currentTimeMillis() < titleDeadline) {
            try {
                Thread.sleep(TITLE_POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return offset;
            }
            StreamBatch batch;
            try {
                batch = streamManager.getEvents(sessionId, assistantMessageId, offset);
            } catch (RuntimeException e) {
                return offset;
            }
            if (batch.events().isEmpty()) {
                continue;
            }
            for (StreamEvent evt : batch.events()) {
                try {
                    emitter.emitStreamEvent(sink, evt, requestId, resourceRewriter, client);
                } catch (IOException e) {
                    return batch.nextOffset();
                }
                if (evt.getType() == ResponseType.SESSION_TITLE) {
                    return batch.nextOffset();
                }
            }
            offset = batch.nextOffset();
        }
        return offset;
    }

    private void emitStop(String sessionId, String assistantMessageId, SseStreamContext streamCtx) {
        if (streamCtx.eventBus == null) {
            return;
        }
        log.info("Stop requested, cancelling generation session={} message={}",
                sessionId, assistantMessageId);
        Event stopEvt = new Event();
        stopEvt.setType(EventType.EVENT_STOP);
        stopEvt.setSessionId(sessionId);
        StopData data = new StopData();
        data.setSessionId(sessionId);
        data.setMessageId(assistantMessageId);
        data.setReason("user_requested");
        stopEvt.setData(data);
        try {
            streamCtx.eventBus.emit(stopEvt);
        } catch (RuntimeException e) {
            log.warn("Emit stop event failed session={} message={}", sessionId, assistantMessageId, e);
        }
    }

    private void writeStopFrame(SseSink sink, String requestId) throws IOException {
        StreamResponse stopResp = new StreamResponse();
        stopResp.setId(requestId);
        stopResp.setResponseType(ResponseType.STOP);
        stopResp.setContent("Generation stopped by user");
        stopResp.setDone(true);
        frameWriter.write(sink, stopResp);
    }
}
