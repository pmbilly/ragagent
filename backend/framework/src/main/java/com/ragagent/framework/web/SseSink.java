package com.ragagent.framework.web;

import java.io.IOException;

/**
 * SSE 帧出口（会话流的写面抽象，载荷级）。
 *
 * <p>实现负责把 {@code (eventName, json)} 组成规范帧写出：servlet 直写（过渡实现，见
 * {@code framework.web.ServletSseSink}）与 {@code SseEmitter}（{@code session.controller.sse.EmitterSseSink}）
 * 两种出口产出的帧字节一致（实测钉于 {@code EmitterFrameBytesTest}）。响应头由控制器在首帧前设置，
 * 不属本接口。</p>
 */
public interface SseSink {

    /** 写一帧（事件名 + 已序列化 JSON）并 flush。 */
    void send(String eventName, String json) throws IOException;

    /** 流收尾（幂等；servlet 直写实现为空操作）。 */
    default void complete() {
    }

    /** 以错误收尾（异步错误派发渲染错误体；幂等）。 */
    default void completeError(Throwable t) {
        complete();
    }
}
