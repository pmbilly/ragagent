package com.ragagent.stream;

import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ragagent.common.llm.ResponseType;

/**
 * 流停止事件轮询（跨实例 /stop 的消费侧）：周期读本轮次的流事件，
 * 发现 {@link ResponseType#STOP} 即触发取消信号后退出；{@code alive} 转 false
 * （轮次结束）亦退出。
 *
 * <p>IM 侧用它实现跨实例停止检测（对齐 Go internal/im 的
 * {@code watchStreamManagerStop}，间隔 500ms 同 Go 的 stopPollInterval）；
 * web 侧的 SSE 检测循环有其专用实现（{@code QaSseOrchestrator.startStopWatcher}），
 * 暂未合并——两者的事件消费语义不同（web 版还要发 EVENT_STOP 并收流）。</p>
 */
public final class StreamStopWatcher {

    private static final Logger log = LoggerFactory.getLogger(StreamStopWatcher.class);

    /** 轮询间隔（Go IM 侧的 {@code stopPollInterval}）。 */
    static final long POLL_INTERVAL_MILLIS = 500;

    private StreamStopWatcher() {
    }

    /**
     * 启动 watcher（虚拟线程，立即返回）。
     *
     * @param alive  QA 存活探针；转 false 时退出（正常结束/已取消）
     * @param cancel 发现 stop 事件时触发（幂等；调用方负责把取消传导到 QA 引擎）
     */
    public static void start(StreamManager streamManager, String sessionId, String messageId,
            BooleanSupplier alive, Runnable cancel) {
        Thread.ofVirtual().name("stream-stop-watch-" + messageId).start(() -> {
            int offset = 0;
            while (alive.getAsBoolean()) {
                StreamBatch batch;
                try {
                    batch = streamManager.getEvents(sessionId, messageId, offset);
                } catch (RuntimeException e) {
                    // 瞬态读错误：跳过本轮（对齐 Go 的 err → continue）
                    log.warn("stop watcher poll failed: session={} message={}: {}",
                            sessionId, messageId, e.getMessage());
                    if (!sleepQuietly()) {
                        return;
                    }
                    continue;
                }
                for (StreamEvent evt : batch.events()) {
                    if (evt.getType() == ResponseType.STOP) {
                        log.info("stop event detected, cancelling: session={} message={}",
                                sessionId, messageId);
                        cancel.run();
                        return;
                    }
                }
                offset = batch.nextOffset();
                if (!sleepQuietly()) {
                    return;
                }
            }
        });
    }

    private static boolean sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
