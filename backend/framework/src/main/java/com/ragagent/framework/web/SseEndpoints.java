package com.ragagent.framework.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 端点的统一构造面：超时、响应头与流收束。
 *
 * <p><b>设头时机是硬约束</b>：四头必须在所有前置校验<b>之后</b>、首帧之前设置——
 * 一旦 {@code Content-Type: text/event-stream} 先落，前置校验失败就再也退不回
 * 普通 400/404 JSON（蓝图 5.6 原文警示；实测教训：先设头会让 BizException 逃逸
 * 异常解析器，MockMvc 直接 ServletException）。</p>
 */
public final class SseEndpoints {

    /** 与流轮询上限对齐（SessionStreamController 既有口径）：两小时。 */
    public static final long STREAM_TIMEOUT_MILLIS = 2 * 60 * 60 * 1000L;

    private SseEndpoints() {
    }

    /** 统一超时的 emitter（头<b>未</b>设——控制器在 prelude 成功后调 {@link #setHeaders}）。 */
    public static SseEmitter newStream() {
        return new SseEmitter(STREAM_TIMEOUT_MILLIS);
    }

    /** 四头（值逐字同 {@link SseContract}）。必须在 prelude 成功后、首帧前调用一次。 */
    public static void setHeaders(HttpServletResponse response) {
        SseContract.setSSEHeaders(response);
    }

    /** 流收尾：幂等 complete。 */
    public static void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 已完成/超时后 complete 是幂等噪音
        }
    }
}
