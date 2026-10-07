package com.ragagent.stream;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 进程内的流管理器。
 *
 * <p><b>只适合单副本部署</b>：live-run 标记是本进程的 map，多副本下
 * {@code /steer} 会被路由到没有这一轮的副本。此时应经
 * {@code STREAM_MANAGER_TYPE=redis} 切到 {@link RedisStreamManager}。</p>
 *
 * <p><b>过期清理（Redis 键 TTL 的内存等价物）</b>：写入路径按 {@code ttl/2}
 * （下限 100ms）节流触发一次全扫，删除 {@code lastUpdated} 超过 {@code ttl} 的流，
 * 避免长期运行的实例无限累积；ttl 与 Redis 后端同源
 * （{@code weknora.stream.ttl}，默认 1h）。live-run 标记是「当前生成中」的短命状态，
 * 正常流程显式清除，不参与清扫。</p>
 *
 * <p>锁结构：外层一把读写锁护住两张表（streams / liveRuns），
 * 每条流自己一把读写锁护住事件列表。加锁顺序恒为「先外后内」。</p>
 */
public class MemoryStreamManager implements StreamManager {

    /** 一条流的事件与 steer 子列表。 */
    private static final class StreamData {
        final List<StreamEvent> events = new ArrayList<>();
        final List<StreamEvent> steerEvents = new ArrayList<>();
        /** 最后写入时刻；过期清扫据此判定（见类注释）。 */
        volatile OffsetDateTime lastUpdated = OffsetDateTime.now();
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    }

    /** 会话当前正在生成的那一轮。 */
    private record LiveRunMarker(String assistantMessageId, String requestId) {
    }

    /** sessionId -> messageId -> 流数据 */
    private final Map<String, Map<String, StreamData>> streams = new ConcurrentHashMap<>();
    /** sessionId -> 当前生成中的一轮 */
    private final Map<String, LiveRunMarker> liveRuns = new HashMap<>();
    private final ReentrantReadWriteLock mu = new ReentrantReadWriteLock();

    /** 流的过期阈值（与 Redis 后端的键 TTL 同源）。 */
    private final Duration ttl;
    /** 两次全扫的最小间隔（写入路径节流）。 */
    private final long sweepIntervalNanos;
    /** 上次全扫时刻（{@link System#nanoTime()} 基准）。 */
    private volatile long lastSweepNanos = System.nanoTime();
    /** 同一时刻只允许一个线程执行全扫。 */
    private final AtomicBoolean sweeping = new AtomicBoolean();

    /** 默认 1h 过期（与 application.yml 的 {@code weknora.stream.ttl} 默认一致）。 */
    public MemoryStreamManager() {
        this(Duration.ofHours(1));
    }

    public MemoryStreamManager(Duration ttl) {
        this.ttl = (ttl == null || ttl.isZero() || ttl.isNegative())
                ? Duration.ofHours(1) : ttl;
        this.sweepIntervalNanos = Math.max(this.ttl.dividedBy(2).toNanos(),
                Duration.ofMillis(100).toNanos());
    }

    // ── 内部取用 ────────────────────────────────────────────────────────────

    private StreamData getOrCreateStream(String sessionId, String messageId) {
        mu.writeLock().lock();
        try {
            Map<String, StreamData> sessionMap = streams.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
            return sessionMap.computeIfAbsent(messageId, k -> new StreamData());
        } finally {
            mu.writeLock().unlock();
        }
    }

    private StreamData getStream(String sessionId, String messageId) {
        mu.readLock().lock();
        try {
            Map<String, StreamData> sessionMap = streams.get(sessionId);
            return sessionMap == null ? null : sessionMap.get(messageId);
        } finally {
            mu.readLock().unlock();
        }
    }

    // ── 过期清扫（Redis 键 TTL 的内存等价物） ────────────────────────────────

    /** 节流触发全扫；间隔不足 {@code ttl/2} 或已有线程在扫时直接返回。 */
    private void maybeSweep() {
        long now = System.nanoTime();
        if (now - lastSweepNanos < sweepIntervalNanos) {
            return;
        }
        if (!sweeping.compareAndSet(false, true)) {
            return;
        }
        try {
            lastSweepNanos = now;
            sweepExpired();
        } finally {
            sweeping.set(false);
        }
    }

    /** 全扫：删除 {@code lastUpdated} 超过 ttl 的流（清空后的会话条目一并移除）。 */
    private void sweepExpired() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(ttl);
        mu.writeLock().lock();
        try {
            var it = streams.entrySet().iterator();
            while (it.hasNext()) {
                Map<String, StreamData> sessionMap = it.next().getValue();
                sessionMap.entrySet().removeIf(e -> e.getValue().lastUpdated.isBefore(cutoff));
                if (sessionMap.isEmpty()) {
                    it.remove();
                }
            }
        } finally {
            mu.writeLock().unlock();
        }
    }

    // ── 事件流 ──────────────────────────────────────────────────────────────

    @Override
    public void appendEvent(String sessionId, String messageId, StreamEvent event) {
        maybeSweep();
        StreamData stream = getOrCreateStream(sessionId, messageId);
        stream.lock.writeLock().lock();
        try {
            // 存入前拷贝：这里补时间戳不会写回调用方的对象。
            StreamEvent stored = event.copy();
            if (stored.getTimestamp() == null) {
                stored.setTimestamp(OffsetDateTime.now());
            }
            stream.events.add(stored);
            stream.lastUpdated = OffsetDateTime.now();
        } finally {
            stream.lock.writeLock().unlock();
        }
    }

    @Override
    public StreamBatch getEvents(String sessionId, String messageId, int fromOffset) {
        StreamData stream = getStream(sessionId, messageId);
        if (stream == null) {
            // 流还不存在
            return StreamBatch.empty(fromOffset);
        }
        stream.lock.readLock().lock();
        try {
            if (fromOffset >= stream.events.size()) {
                return StreamBatch.empty(fromOffset);
            }
            List<StreamEvent> out = new ArrayList<>(stream.events.size() - fromOffset);
            for (StreamEvent e : stream.events.subList(fromOffset, stream.events.size())) {
                // 元素级拷贝（data 仍是共享引用）
                out.add(e.copy());
            }
            return new StreamBatch(out, stream.events.size());
        } finally {
            stream.lock.readLock().unlock();
        }
    }

    // ── steer 控制面 ────────────────────────────────────────────────────────

    @Override
    public void appendSteerEvents(String sessionId, String messageId, List<StreamEvent> events) {
        maybeSweep();
        StreamData stream = getOrCreateStream(sessionId, messageId);
        stream.lock.writeLock().lock();
        try {
            Set<String> seen = new HashSet<>(stream.steerEvents.size());
            for (StreamEvent e : stream.steerEvents) {
                seen.add(e.getId());
            }
            for (StreamEvent event : events) {
                if (!event.getId().isEmpty() && seen.contains(event.getId())) {
                    continue;
                }
                seen.add(event.getId());
                // 就地补时间戳到入参对象上：调用方对自己对象的这一改动**看得见**。
                if (event.getTimestamp() == null) {
                    event.setTimestamp(OffsetDateTime.now());
                }
                // 但存进管理器的是**拷贝**——之后调用方再改 id/content 不会影响已入列的事件（data 仍是共享引用）。
                stream.steerEvents.add(event.copy());
            }
            stream.lastUpdated = OffsetDateTime.now();
        } finally {
            stream.lock.writeLock().unlock();
        }
    }

    @Override
    public StreamBatch getSteerEvents(String sessionId, String messageId, int fromOffset) {
        StreamData stream = getStream(sessionId, messageId);
        if (stream == null) {
            return StreamBatch.empty(fromOffset);
        }
        stream.lock.readLock().lock();
        try {
            if (fromOffset >= stream.steerEvents.size()) {
                return StreamBatch.empty(fromOffset);
            }
            List<StreamEvent> out = new ArrayList<>(stream.steerEvents.size() - fromOffset);
            for (StreamEvent e : stream.steerEvents.subList(fromOffset, stream.steerEvents.size())) {
                out.add(e.copy());
            }
            return new StreamBatch(out, stream.steerEvents.size());
        } finally {
            stream.lock.readLock().unlock();
        }
    }

    @Override
    public boolean updateSteerEventData(String sessionId, String messageId, String eventId, Map<String, Object> data) {
        StreamData stream = getStream(sessionId, messageId);
        if (stream == null) {
            return false;
        }
        stream.lock.writeLock().lock();
        try {
            for (StreamEvent event : stream.steerEvents) {
                if (!event.getId().equals(eventId)) {
                    continue;
                }
                // 先拷贝再改：GetSteerEvents 交出去的是浅拷贝，调用方可能还握着旧的 Data map。
                event.mergeData(data);
                stream.lastUpdated = OffsetDateTime.now();
                return true;
            }
            return false;
        } finally {
            stream.lock.writeLock().unlock();
        }
    }

    @Override
    public boolean deleteSteerEvent(String sessionId, String messageId, String eventId) {
        StreamData stream = getStream(sessionId, messageId);
        if (stream == null) {
            return false;
        }
        stream.lock.writeLock().lock();
        try {
            for (int i = 0; i < stream.steerEvents.size(); i++) {
                StreamEvent event = stream.steerEvents.get(i);
                if (!event.getId().equals(eventId)) {
                    continue;
                }
                Map<String, Object> d = event.getData();
                if (d != null && Boolean.TRUE.equals(d.get("consumed"))) {
                    return false;
                }
                stream.steerEvents.remove(i);
                stream.lastUpdated = OffsetDateTime.now();
                return true;
            }
            return false;
        } finally {
            stream.lock.writeLock().unlock();
        }
    }

    // ── live run ────────────────────────────────────────────────────────────

    @Override
    public void setLiveRun(String sessionId, String assistantMessageId, String requestId) {
        mu.writeLock().lock();
        try {
            LiveRunMarker existing = liveRuns.get(sessionId);
            if (existing != null && !existing.assistantMessageId().equals(assistantMessageId)) {
                throw new LiveRunExistsException();
            }
            liveRuns.put(sessionId, new LiveRunMarker(assistantMessageId, requestId));
        } finally {
            mu.writeLock().unlock();
        }
    }

    @Override
    public void claimLiveRun(String sessionId, String assistantMessageId, String requestId) {
        mu.writeLock().lock();
        try {
            liveRuns.put(sessionId, new LiveRunMarker(assistantMessageId, requestId));
        } finally {
            mu.writeLock().unlock();
        }
    }

    @Override
    public LiveRun getLiveRun(String sessionId) {
        mu.readLock().lock();
        try {
            LiveRunMarker marker = liveRuns.get(sessionId);
            return marker == null ? LiveRun.NONE : new LiveRun(marker.assistantMessageId(), marker.requestId());
        } finally {
            mu.readLock().unlock();
        }
    }

    @Override
    public void clearLiveRun(String sessionId, String assistantMessageId) {
        mu.writeLock().lock();
        try {
            LiveRunMarker marker = liveRuns.get(sessionId);
            if (marker != null && marker.assistantMessageId().equals(assistantMessageId)) {
                liveRuns.remove(sessionId);
            }
        } finally {
            mu.writeLock().unlock();
        }
    }
}
