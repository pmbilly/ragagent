package com.ragagent.session.qa;


import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.contract.event.Event;
import com.ragagent.contract.event.EventBusInterface;
import com.ragagent.platform.event.EventBus;
import com.ragagent.platform.event.EventType;
import com.ragagent.contract.event.payload.AgentToolCallData;
import com.ragagent.contract.event.payload.AgentToolResultData;
import com.ragagent.contract.model.ResponseType;
import com.ragagent.contract.agent.AgentStep;
import com.ragagent.session.domain.message.Message;
import com.ragagent.session.service.AgentStreamBridge;
import com.ragagent.session.service.MessageService;
import com.ragagent.session.service.QaSupport.QaMode;
import com.ragagent.session.service.QaSupport.QaRequestContext;
import com.ragagent.session.service.QaSupport.SseStreamContext;
import com.ragagent.session.service.SessionService;
import com.ragagent.session.service.SteerSinkBridge;
import com.ragagent.session.controller.sse.SseContract;
import com.ragagent.session.controller.sse.StreamEventEmitter;
import com.ragagent.platform.storage.support.StreamRewriter;
import com.ragagent.platform.stream.StreamBatch;
import com.ragagent.platform.stream.StreamEvent;
import com.ragagent.platform.stream.StreamManager;
import com.ragagent.contract.session.SseSink;
import com.ragagent.contract.agent.AgentToolCall;
import com.ragagent.contract.tool.ToolResult;
import com.ragagent.platform.access.TenantContextSnapshot;
import com.ragagent.contract.event.payload.StopData;
import com.ragagent.llm.domain.StreamResponse;
import com.ragagent.session.controller.sse.SseFrameWriter;
import com.ragagent.platform.stream.LiveRunExistsException;

/**
 * {@code KnowledgeQaController} 的**SSE 编排簇**：建立 SSE 流上下文、写
 * {@code agent_query} 请求帧、看门狗线程（流结束/断连即收尾）、把 agent 事件转发进 SSE，
 * 以及 quick answer 时间线的录制（步进/工具调用）。
 *
 * <p>共享行为：{@code runWithTenant} / {@code completeAssistantMessage} 在
 * {@link QaTurnFinalizer}，本类**持有它**转发（不回调控制器）；{@code ensureQuickAnswerStep}
 * 是 static 且执行簇也在用 → 留控制器，按类名引用。</p>
 */
public final class QaSseOrchestrator {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(QaSseOrchestrator.class);

    private final StreamManager streamManager;
    private final StreamEventEmitter emitter;
    private final SessionService sessionService;
    private final MessageService messageService;
    private final SseFrameWriter sseFrameWriter;
    private final QaTurnFinalizer turnFinalizer;

    public QaSseOrchestrator(StreamManager streamManager, StreamEventEmitter emitter, SessionService sessionService, MessageService messageService, SseFrameWriter sseFrameWriter, QaTurnFinalizer turnFinalizer) {
        this.streamManager = streamManager;
        this.emitter = emitter;
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.sseFrameWriter = sseFrameWriter;
        this.turnFinalizer = turnFinalizer;
    }

    public SseStreamContext setupSSEStream(QaRequestContext reqCtx, boolean generateTitle, QaMode mode) {
        SseStreamContext streamCtx = new SseStreamContext();
        streamCtx.assistantMessage = reqCtx.assistantMessage;
        streamCtx.tenantSnapshot = TenantContextSnapshot.capture();

        EventBusInterface eventBus = new EventBus();
        streamCtx.eventBus = eventBus;

        // Mid-run steering：仅 agent 模式有引擎排空点
        if (mode == QaMode.AGENT && reqCtx.agentConfig != null) {
            SteerSinkBridge sink = new SteerSinkBridge(reqCtx.sessionId, reqCtx.requestId,
                    messageService, streamManager,
                    TenantContextSnapshot.capture());
            streamCtx.steerSink = sink;
            reqCtx.steerSink = sink;
            try {
                streamManager.setLiveRun(reqCtx.sessionId, reqCtx.assistantMessage.getId(), reqCtx.requestId);
            } catch (RuntimeException e) {
                log.error("SetLiveRun failed for session {}: {}", reqCtx.sessionId, e.toString());
                streamCtx.liveRunFailed = true;
                streamCtx.liveRunErr = e.getMessage();
                streamCtx.liveRunExists = e instanceof LiveRunExistsException;
                return streamCtx;
            }
        }

        // agent_query 事件写流
        writeAgentQueryEvent(reqCtx);

        // stop 事件处理器
        long sessionTenantId = reqCtx.session.getTenantId();
        // A5：处理器可能跑在 stop watcher 的虚拟线程上（那里身份为空）——注册时在本线程
        // （身份完整）捕获快照，触发时用它补齐；否则收尾的索引/建议/记忆 SessionNotFound。
        TenantContextSnapshot stopIdentity = TenantContextSnapshot.capture();
        eventBus.on(EventType.EVENT_STOP, evt -> {
            log.info("Received stop event, cancelling async operations for session: {}", reqCtx.sessionId);
            streamCtx.cancelled = true;
            // 停止时保住已流出内容；用 session 租户落库
            turnFinalizer.runWithTenant(sessionTenantId, stopIdentity, () -> turnFinalizer.completeAssistantMessage(
                    streamCtx.assistantMessage, "", "", sessionTenantId));
        });

        // 独立 stop watcher（自终止：complete/终态错误）
        startStopWatcher(reqCtx.sessionId, reqCtx.assistantMessage.getId(), eventBus);

        // AgentStreamBridge 订阅（16 种事件）
        AgentStreamBridge bridge = new AgentStreamBridge(reqCtx.sessionId, reqCtx.assistantMessage.getId(),
                reqCtx.requestId, OffsetDateTime.now(), reqCtx.assistantMessage,
                streamManager, eventBus);
        bridge.subscribe();

        // title 生成（GenerateTitleAsync：session title 为空时）
        if (generateTitle && (reqCtx.session.getTitle() == null || reqCtx.session.getTitle().isEmpty())) {
            String modelId = "";
            if (reqCtx.agentConfig != null) {
                modelId = reqCtx.agentConfig.path("modelId").asText("");
            }
            log.info("Session has no title, starting async title generation, session ID: {}, model: {}",
                    reqCtx.sessionId, modelId);
            sessionService.generateTitleAsync(reqCtx.session, reqCtx.query, modelId, eventBus);
        }
        return streamCtx;
    }
    private void writeAgentQueryEvent(QaRequestContext reqCtx) {
        String assistantMessageId = reqCtx.assistantMessage == null ? "" : reqCtx.assistantMessage.getId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", reqCtx.sessionId);
        data.put("assistantMessageId", assistantMessageId);
        if (!reqCtx.userMessageID.isEmpty()) {
            data.put("userMessageId", reqCtx.userMessageID);
        }
        if (reqCtx.userCreatedAt != null) {
            data.put("userCreatedAt", reqCtx.userCreatedAt.toInstant().toString());
        }
        if (reqCtx.assistantMessage != null && reqCtx.assistantMessage.getCreatedAt() != null) {
            data.put("assistantCreatedAt", reqCtx.assistantMessage.getCreatedAt().toInstant().toString());
        }
        StreamEvent evt = new StreamEvent();
        evt.setId("query-" + System.nanoTime());
        evt.setType(ResponseType.AGENT_QUERY);
        evt.setContent("");
        evt.setDone(true);
        evt.setTimestamp(OffsetDateTime.now());
        evt.setData(data);
        try {
            streamManager.appendEvent(reqCtx.sessionId, assistantMessageId, evt);
        } catch (RuntimeException e) {
            log.error("write agent query event failed session={} message={}: {}",
                    reqCtx.sessionId, assistantMessageId, e.toString());
        }
    }
    private void startStopWatcher(String sessionId, String assistantMessageId, EventBusInterface eventBus) {
        // A5：watcher 跑在自己的虚拟线程上（ThreadLocal 不跨线程，身份为空）——读流事件与
        // 会话范围判定都需要完整身份，因此在本线程 capture、watcher 里 replay 后清理。
        TenantContextSnapshot identity = TenantContextSnapshot.capture();
        Thread.ofVirtual().start(() -> {
            identity.replay();
            try {
                watchStopEvents(sessionId, assistantMessageId, eventBus);
            } finally {
                TenantContext.clear();
            }
        });
    }

    /** stop watcher 主循环（独立虚拟线程；身份由 {@link #startStopWatcher} 显式传递）。 */
    private void watchStopEvents(String sessionId, String assistantMessageId, EventBusInterface eventBus) {
        int offset = 0;
        long deadline = System.currentTimeMillis() + 2 * 60 * 60 * 1000L; // stopWatcherMaxDuration
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            StreamBatch batch;
            try {
                batch = streamManager.getEvents(sessionId, assistantMessageId, offset);
            } catch (RuntimeException e) {
                continue; // 瞬态读错误，下一 tick 重试
            }
            offset = batch.nextOffset();
            for (StreamEvent evt : batch.events()) {
                if (evt.getType() == ResponseType.STOP) {
                    log.info("Stop watcher detected stop event, cancelling generation for session={}, message={}",
                            sessionId, assistantMessageId);
                    Event stopEvt = new Event();
                    stopEvt.setType(EventType.EVENT_STOP);
                    stopEvt.setSessionId(sessionId);
                    StopData data = new StopData();
                    data.setSessionId(sessionId);
                    data.setMessageId(assistantMessageId);
                    data.setReason("user_requested");
                    stopEvt.setData(data);
                    eventBus.emit(stopEvt);
                    return;
                }
                if (evt.getType() == ResponseType.COMPLETE) {
                    return;
                }
                if (evt.getType() == ResponseType.ERROR && evt.isDone()) {
                    return;
                }
            }
        }
    }
    public void handleAgentEventsForSSE(SseSink sink, String sessionId,
            String assistantMessageId, String requestId, SseStreamContext streamCtx,
            boolean waitForTitle, StreamRewriter resourceRewriter) throws IOException {
        sink.applyHeaders();
        AtomicBoolean clientGone = new AtomicBoolean(false);
        StreamEventEmitter.ClientState client = clientGone::get;

        int lastOffset = 0;
        log.info("Starting pull-based SSE streaming for session={}, message={}", sessionId, assistantMessageId);

        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            StreamBatch batch;
            try {
                batch = streamManager.getEvents(sessionId, assistantMessageId, lastOffset);
            } catch (RuntimeException e) {
                log.warn("Failed to get events from stream: {}", e.toString());
                continue;
            }
            boolean streamCompleted = false;
            boolean titleReceived = false;
            for (StreamEvent evt : batch.events()) {
                if (evt.getType() == ResponseType.STOP) {
                    log.info("Detected stop event, session={}", sessionId);
                    if (streamCtx.eventBus != null) {
                        Event stopEvt = new Event();
                        stopEvt.setType(EventType.EVENT_STOP);
                        stopEvt.setSessionId(sessionId);
                        StopData data = new StopData();
                        data.setSessionId(sessionId);
                        data.setMessageId(assistantMessageId);
                        data.setReason("user_requested");
                        stopEvt.setData(data);
                        streamCtx.eventBus.emit(stopEvt);
                    }
                    try {
                        emitter.flushHeldStreamContent(sink, requestId, resourceRewriter, client);
                    } catch (IOException ignored) {
                        // 客户端已断开
                    }
                    // 写出 stop 事件帧
                    StreamResponse stopResp = new StreamResponse();
                    stopResp.setId(requestId);
                    stopResp.setResponseType(ResponseType.STOP);
                    stopResp.setContent("Generation stopped by user");
                    stopResp.setDone(true);
                    sseFrameWriter.write(sink, stopResp);
                    return;
                }
                if (evt.getType() == ResponseType.COMPLETE) {
                    streamCompleted = true;
                }
                if (evt.getType() == ResponseType.SESSION_TITLE) {
                    titleReceived = true;
                }
                try {
                    emitter.emitStreamEvent(sink, evt, requestId, resourceRewriter, client);
                } catch (IOException e) {
                    clientGone.set(true);
                    log.info("Connection closed during event sending, stopping");
                    return;
                }
            }
            lastOffset = batch.nextOffset();
            if (streamCompleted) {
                if (waitForTitle && !titleReceived) {
                    log.info("Stream completed, waiting for title event");
                    long titleDeadline = System.currentTimeMillis() + 3000;
                    titleWait:
                    while (System.currentTimeMillis() < titleDeadline) {
                        StreamBatch titleBatch;
                        try {
                            titleBatch = streamManager.getEvents(sessionId, assistantMessageId, lastOffset);
                        } catch (RuntimeException e) {
                            break titleWait;
                        }
                        if (!titleBatch.events().isEmpty()) {
                            for (StreamEvent evt : titleBatch.events()) {
                                try {
                                    emitter.emitStreamEvent(sink, evt, requestId, resourceRewriter, client);
                                } catch (IOException e) {
                                    clientGone.set(true);
                                    return;
                                }
                                if (evt.getType() == ResponseType.SESSION_TITLE) {
                                    break titleWait;
                                }
                            }
                            lastOffset = titleBatch.nextOffset();
                        }
                    }
                }
                SseContract.sendCompletionEvent(sink, requestId);
                return;
            }
        }
    }
    private static final Set<String> QUICK_ANSWER_TIMELINE_TOOLS = Set.of(
            "query_understand", "knowledge_search", "attachment_parsing", "image_analysis");
    public void registerQuickAnswerTimelineRecorder(EventBusInterface bus, Message msg) {
        if (bus == null || msg == null) {
            return;
        }
        Map<String, Map<String, Object>> pending = new LinkedHashMap<>();
        Map<String, Long> startedAt = new LinkedHashMap<>();
        bus.on(EventType.EVENT_AGENT_TOOL_CALL, evt -> {
            if (evt.getData() instanceof AgentToolCallData data
                    && QUICK_ANSWER_TIMELINE_TOOLS.contains(data.getToolName())
                    && !data.getToolCallId().isEmpty()) {
                synchronized (msg) {
                    pending.put(data.getToolCallId(), data.getArguments());
                    startedAt.put(data.getToolCallId(), System.currentTimeMillis());
                }
            }
        });
        bus.on(EventType.EVENT_AGENT_TOOL_RESULT, evt -> {
            if (evt.getData() instanceof AgentToolResultData data
                    && QUICK_ANSWER_TIMELINE_TOOLS.contains(data.getToolName())
                    && !data.getToolCallId().isEmpty()) {
                synchronized (msg) {
                    Map<String, Object> args = pending.remove(data.getToolCallId());
                    Long started = startedAt.remove(data.getToolCallId());
                    long duration = data.getDurationMs();
                    if (duration == 0 && started != null) {
                        duration = System.currentTimeMillis() - started;
                    }
                    AgentToolCall call = new AgentToolCall();
                    call.setId("pipeline:" + data.getToolCallId());
                    call.setName(data.getToolName());
                    call.setArgs(args);
                    ToolResult result = new ToolResult();
                    result.setSuccess(data.isSuccess());
                    result.setOutput(data.getOutput());
                    result.setError(data.getError());
                    result.setData(data.getData());
                    call.setResult(result);
                    call.setDuration(duration);
                    appendQuickAnswerToolCall(msg, call);
                }
            }
        });
    }
    private static void appendQuickAnswerToolCall(Message msg, AgentToolCall call) {
        AgentStep step = QaTurnSupport.ensureQuickAnswerStep(msg);
        if (step.getToolCalls() != null) {
            for (int i = 0; i < step.getToolCalls().size(); i++) {
                if (step.getToolCalls().get(i).getId().equals(call.getId())) {
                    step.getToolCalls().set(i, call);
                    return;
                }
            }
        }
        if (step.getToolCalls() == null) {
            step.setToolCalls(new ArrayList<>());
        }
        step.getToolCalls().add(call);
    }
}
