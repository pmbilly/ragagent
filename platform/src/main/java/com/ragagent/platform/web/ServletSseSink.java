package com.ragagent.platform.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.ragagent.contract.session.SseSink;

import jakarta.servlet.http.HttpServletResponse;

/**
 * {@link SseSink} 的 servlet 实现（HTTP 边界）：四个 SSE 头 + 输出流逐帧写入并 flush。
 *
 * <p>头设置逻辑原在 {@code session.controller.sse.SseContract#setSSEHeaders}，自 P4f 起以本类为
 * 单一实现（SseContract 保留同名薄壳委托，兼容既有 servlet 路径）。</p>
 */
public final class ServletSseSink implements SseSink {

    private final HttpServletResponse response;

    public ServletSseSink(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public void applyHeaders() {
        response.setHeader("Content-Type", "text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");
    }

    @Override
    public void writeFrame(String frame) throws IOException {
        response.getOutputStream().write(frame.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
