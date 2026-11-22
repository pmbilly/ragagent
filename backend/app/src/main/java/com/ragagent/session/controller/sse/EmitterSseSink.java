package com.ragagent.session.controller.sse;

import com.ragagent.framework.vocabulary.SseSink;
import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link SseSink} 的 {@link SseEmitter} 实现：控制器返回 emitter 即释放请求线程，
 * 流泵在虚拟线程上串行调用 {@link #send}。
 *
 * <p>帧字节与 servlet 直写实现一致（{@code EmitterFrameBytesTest} 实测钉：
 * {@code event:<name>\ndata:<json>\n\n}）。断连由 emitter 的
 * onCompletion/onError/onTimeout 回调感知（控制器接线），写失败以 IOException 上抛。</p>
 */
public final class EmitterSseSink implements SseSink {

    private final SseEmitter emitter;

    public EmitterSseSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String eventName, String json) throws IOException {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            // emitter 已 complete/超时后 send 抛 IllegalStateException——按"客户端已走"的写失败语义上抛
            throw new IOException("sse emitter send failed: " + e.getMessage(), e);
        }
    }
}
