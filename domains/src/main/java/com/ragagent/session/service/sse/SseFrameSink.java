package com.ragagent.session.service.sse;

import com.ragagent.contract.model.StreamResponse;
import com.ragagent.contract.session.SseSink;
import java.io.IOException;

/**
 * 帧写出端口：把 {@link StreamResponse} 写成既定 SSE 帧。
 *
 * <p>编排层（{@code session.qa}）只依赖本端口，不依赖 Servlet 的
 * {@code controller.sse.SseFrameWriter}——方向从"编排 → HTTP 传输"倒过来，
 * 由 controller 的实现类提供 Servlet 直写能力。</p>
 */
public interface SseFrameSink {

    /** 组帧（序列化 + {@code event:}/{@code data:} 行）。 */
    String frameOf(StreamResponse payload) throws IOException;

    /** 写一帧到 {@link SseSink}。 */
    void write(SseSink sink, StreamResponse payload) throws IOException;
}
