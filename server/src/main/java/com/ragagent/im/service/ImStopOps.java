package com.ragagent.im.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ragagent.common.llm.ResponseType;
import com.ragagent.im.domain.ChannelSessionEntity;
import com.ragagent.im.domain.ImChannelEntity;
import com.ragagent.im.runtime.ImFormat;
import com.ragagent.im.runtime.ImRedisStore;
import com.ragagent.im.runtime.ImTypes;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.im.runtime.QaQueue;
import com.ragagent.stream.StreamEvent;
import com.ragagent.stream.StreamManager;
import com.ragagent.stream.StreamStopWatcher;

/**
 * 跨实例 /stop 全链路：本地出队/在途取消、stop 事件写入 StreamManager、标记兜底、
 * 在途（inflight）映射的绑定与退场。
 *
 * <p>B123 自 {@link ImService} 原样外提（原 907~1012 行，逐字搬迁，仅改依赖取用方式）。
 * 这段是一条**完整的请求取消链路**（用户在一次问答的不同阶段按 /stop，会分别命中
 * 出队、在途、标记三条路径），与渠道生命周期、消息处理主管道同处一类只是历史堆积。</p>
 *
 * <p>依赖面刻意只收**显式四项**：Redis 面（可为 {@code null} = 单实例形态）、队列、
 * 在途表（与门面**共享同一引用**，{@code ImQaRunner} 仍在读写它）、以及 StreamManager
 * 的**延迟供应**——后者由门面 {@code setStreamManager} 在装配后注入（避免与 stream 包的
 * 装配环），故用 {@link Supplier} 而非直接引用；每次读到的都是当时的 volatile 值，
 * 与搬迁前语义一致。</p>
 */
final class ImStopOps {

    private static final Logger log = LoggerFactory.getLogger(ImStopOps.class);

    /** 执行前 /stop 标记的 TTL。 */
    private static final int STOP_MARKER_TTL_SECONDS = 30;
    /** 跨实例在途映射的 TTL（10 分钟）。 */
    private static final int INFLIGHT_TTL_SECONDS = 600;

    private final ImRedisStore redisStore;
    private final QaQueue qaQueue;
    private final Map<String, ImService.InflightEntry> inflight;
    private final Supplier<StreamManager> streamManager;

    /** 跨实例 /stop 标记的本地等价物。 */
    private final Map<String, Long> stopMarkers = new ConcurrentHashMap<>();

    ImStopOps(ImRedisStore redisStore, QaQueue qaQueue,
            Map<String, ImService.InflightEntry> inflight, Supplier<StreamManager> streamManager) {
        this.redisStore = redisStore;
        this.qaQueue = qaQueue;
        this.inflight = inflight;
        this.streamManager = streamManager;
    }

    void doLocalStop(ImChannelEntity channel, IncomingMessage msg,
            ChannelSessionEntity channelSession) {
        String stopThreadId = ImTypes.SESSION_MODE_THREAD.equals(channel.getSessionMode())
                ? msg.threadId : "";
        String inflightKey = ImFormat.makeUserKey(channel.getId(), msg.userId, msg.chatId,
                stopThreadId);
        // 1. 本地取消：出队或在途取消（在途时同时拿到本实例已知的 session/message）。
        boolean localStopped = qaQueue.remove(inflightKey);
        ImService.InflightEntry entry = localStopped ? null : inflight.remove(inflightKey);
        String sessionId = "";
        String messageId = "";
        if (entry != null) {
            entry.cancel.getAsBoolean();
            localStopped = true;
            sessionId = entry.sessionId;
            messageId = entry.assistantMessageId;
        }
        // 2. 跨实例：本地 inflight 没命中时，查 Redis 在途映射拿 IDs。
        if ((sessionId.isEmpty() || messageId.isEmpty()) && redisStore != null) {
            String[] pair = redisStore.loadInflight(inflightKey);
            if (pair != null) {
                sessionId = pair[0];
                messageId = pair[1];
            }
        }
        // 3. 写 stop 事件到 StreamManager（与本仓 web /stop 同契约）：
        //    本轮的 stop watcher 与跨实例的引擎据此取消。
        if (!sessionId.isEmpty() && !messageId.isEmpty()) {
            writeStopEvent(sessionId, messageId);
            log.info("[IM] Wrote stop event to StreamManager: session={} message={}",
                    sessionId, messageId);
        }
        // 4. 标记兜底：给尚未创建 assistant message 的请求（执行前检查消费）。
        if (redisStore != null) {
            redisStore.setStopMarker(inflightKey, STOP_MARKER_TTL_SECONDS);
        } else {
            stopMarkers.put(inflightKey, System.currentTimeMillis());
        }
        if (!localStopped && sessionId.isEmpty()) {
            log.info("[IM] Set stop marker (no inflight found): key={}", inflightKey);
        }
    }

    /** 写 stop 事件到本轮次的流（形状与本仓 web /stop 一致，另带 {@code source=im}）。 */
    private void writeStopEvent(String sessionId, String messageId) {
        StreamManager sm = streamManager.get();
        if (sm == null) {
            log.warn("[IM] StreamManager not wired; stop event skipped: session={} message={}",
                    sessionId, messageId);
            return;
        }
        StreamEvent stopEvent = new StreamEvent("stop-" + System.nanoTime(), ResponseType.STOP, "", true);
        stopEvent.setTimestamp(OffsetDateTime.now());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("message_id", messageId);
        data.put("reason", "user_requested");
        data.put("source", "im");
        stopEvent.setData(data);
        try {
            sm.appendEvent(sessionId, messageId, stopEvent);
        } catch (RuntimeException e) {
            log.warn("[IM] Failed to write stop event to StreamManager: {}", e.getMessage());
        }
    }

    /** 执行前 /stop 检查：本地标记（单实例）或 Redis 标记（跨实例）命中即清除并返回 true。 */
    boolean checkAndClearStopMarker(String userKey) {
        Long local = stopMarkers.remove(userKey);
        boolean hit = local != null
                && (System.currentTimeMillis() - local) < STOP_MARKER_TTL_SECONDS * 1000L;
        if (redisStore != null && redisStore.checkAndClearStopMarker(userKey)) {
            hit = true;
        }
        return hit;
    }

    /**
     * 在途登记（assistant message 创建后调用）：绑定 entry 的 session/message、
     * 写跨实例 inflight 映射，并启动 StreamManager stop watcher（跨实例 /stop 的消费侧）。
     */
    void bindInflight(ImService.QaAttach attach, String assistantMessageId) {
        ImService.InflightEntry entry = attach.inflight();
        if (entry == null) {
            return;
        }
        String sessionId = attach.session().getId();
        entry.sessionId = sessionId;
        entry.assistantMessageId = assistantMessageId;
        if (redisStore != null) {
            redisStore.storeInflight(attach.userKey(), sessionId, assistantMessageId,
                    INFLIGHT_TTL_SECONDS);
        }
        StreamManager sm = streamManager.get();
        if (sm != null && entry.queueReq != null) {
            QaQueue.QaRequest req = entry.queueReq;
            StreamStopWatcher.start(sm, sessionId, assistantMessageId,
                    () -> !req.isCancelled(), req::cancel);
        }
    }

    /** 在途映射退场（QA 结束调用；Redis 未启用时空操作）。 */
    void unbindInflight(String userKey) {
        if (redisStore != null) {
            redisStore.clearInflight(userKey);
        }
    }
}
