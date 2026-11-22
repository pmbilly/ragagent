package com.ragagent.framework.web;

import com.ragagent.framework.vocabulary.SseSink;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@link SseSink} 的 servlet 直写实现（过渡：入口逐一切换 {@code SseEmitter} 后删除）。
 *
 * <p>帧骨架 {@code event:<name>\ndata:<json>\n\n}（冒号后无空格）与 {@code SseEmitter}
 * 实测逐字节一致。头部设置职责已上移控制器（{@code SseContract.setSSEHeaders}）。</p>
 */
public final class ServletSseSink implements SseSink {

    private final HttpServletResponse response;

    public ServletSseSink(HttpServletResponse response) {
        this.response = response;
    }

    @Override
    public void send(String eventName, String json) throws IOException {
        StringBuilder frame = new StringBuilder(json.length() + 32);
        frame.append("event:").append(eventName).append('\n');
        frame.append("data:").append(json).append('\n').append('\n');
        response.getOutputStream().write(frame.toString().getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
