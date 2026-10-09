package com.ragagent.im.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ragagent.im.domain.ImChannelEntity;
import com.ragagent.im.runtime.ImFormat;
import com.ragagent.im.runtime.ImTypes;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.im.runtime.QaQueue;
import com.ragagent.session.domain.Session;

/**
 * {@link ImStopOps} 的语义钉子：B123 自 {@code ImService} 外提时逐字搬迁，
 * 这里把原来只写在代码注释里的三条口径固化成断言——
 * <ul>
 *   <li>{@code doLocalStop} 第 4 步「本地没命中 ⇒ 写标记兜底」（给尚未创建 assistant
 *       message 的请求）；</li>
 *   <li>标记是一次性消费（{@code checkAndClearStopMarker} 命中即清除）；</li>
 *   <li>thread 会话形态的停止键带 {@code threadId}（与 {@code ImFormat.makeUserKey} 同口径）。</li>
 * </ul>
 *
 * <p>覆盖的是<b>单实例形态</b>（{@code redisStore == null}）：Redis 分支的行为已由
 * {@code ImRedisStoreTest} 覆盖，本类只钉门面侧的编排口径。</p>
 */
class ImStopOpsTest {

    /** 可变的空在途表（{@code doLocalStop} 会 {@code remove}，不可用 Map.of）。 */
    private static Map<String, ImService.InflightEntry> emptyInflight() {
        return new ConcurrentHashMap<>();
    }

    private static ImStopOps singleInstance(Map<String, ImService.InflightEntry> inflight) {
        QaQueue queue = new QaQueue(1, 10, 1, req -> { });
        return new ImStopOps(null, queue, inflight, () -> null);   // null = 未启用 Redis 面
    }

    private static ImChannelEntity channel(String id, String sessionMode) {
        ImChannelEntity channel = new ImChannelEntity();
        channel.setId(id);
        channel.setSessionMode(sessionMode);
        return channel;
    }

    private static IncomingMessage message(String userId, String chatId, String threadId) {
        IncomingMessage msg = new IncomingMessage();
        msg.userId = userId;
        msg.chatId = chatId;
        msg.threadId = threadId;
        return msg;
    }

    @Test
    @DisplayName("单实例：本地无在途无队列时走「标记兜底」，且标记一次性消费")
    void localStopFallsBackToMarkerAndConsumesOnce() {
        ImStopOps ops = singleInstance(emptyInflight());
        String key = ImFormat.makeUserKey("ch1", "u1", "c1", "");

        assertFalse(ops.checkAndClearStopMarker(key), "停止前不应有标记");

        ops.doLocalStop(channel("ch1", "user"), message("u1", "c1", ""), null);

        assertTrue(ops.checkAndClearStopMarker(key), "本地未命中 ⇒ 应写标记兜底");
        assertFalse(ops.checkAndClearStopMarker(key), "标记命中即清除（一次性消费）");
    }

    @Test
    @DisplayName("thread 形态：停止键带 threadId，不带 threadId 的键不受影响")
    void threadChannelKeysIncludeThreadId() {
        ImStopOps ops = singleInstance(emptyInflight());

        ops.doLocalStop(channel("ch1", ImTypes.SESSION_MODE_THREAD),
                message("u1", "c1", "t1"), null);

        String threadKey = ImFormat.makeUserKey("ch1", "u1", "c1", "t1");
        String plainKey = ImFormat.makeUserKey("ch1", "u1", "c1", "");
        assertTrue(ops.checkAndClearStopMarker(threadKey), "thread 会话的键应带 threadId");
        assertFalse(ops.checkAndClearStopMarker(plainKey), "非 thread 键不应被误伤");
    }

    @Test
    @DisplayName("在途命中：触发取消标志，并在本实例未知 session/message 时仍写标记兜底")
    void inflightHitTriggersCancelAndStillWritesMarker() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Map<String, ImService.InflightEntry> inflight = new ConcurrentHashMap<>();
        String key = ImFormat.makeUserKey("ch1", "u1", "c1", "");
        inflight.put(key, new ImService.InflightEntry(null, () -> {
            cancelled.set(true);
            return true;
        }));
        ImStopOps ops = singleInstance(inflight);

        ops.doLocalStop(channel("ch1", "user"), message("u1", "c1", ""), null);

        assertTrue(cancelled.get(), "在途条目应被取消");
        assertNull(inflight.get(key), "在途条目应被移除");
        assertTrue(ops.checkAndClearStopMarker(key), "本地已取消但无 session ⇒ 仍写标记兜底");
    }

    @Test
    @DisplayName("在途登记：Redis 面缺席时只更新 entry（不抛、不进 watcher）")
    void bindInflightWithoutRedisOnlyUpdatesEntry() {
        ImStopOps ops = singleInstance(emptyInflight());
        ImService.InflightEntry entry = new ImService.InflightEntry(null, () -> false);
        Session session = new Session();
        session.setId("sess-1");
        ImService.QaAttach attach = new ImService.QaAttach(null, session, null, null,
                channel("ch1", "user"), "ch1", "ch1:u1:c1", entry);

        ops.bindInflight(attach, "msg-9");

        assertEquals("sess-1", entry.sessionId);
        assertEquals("msg-9", entry.assistantMessageId);
    }
}
