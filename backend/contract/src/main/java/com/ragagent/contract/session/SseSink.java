package com.ragagent.contract.session;

import java.io.IOException;

/**
 * SSE 帧出口（会话流的写面抽象）。
 *
 * <p>帧为已编码字符串（{@code event:} / {@code data:} 行与空行由写入侧组装）；响应头、字节编码与
 * flush 归实现（servlet 实现见 {@code platform.web.ServletSseSink}；原生端点亦经它收敛写面）。</p>
 */
public interface SseSink {

    /** 设 SSE 响应头（必须在任何帧写出前调用一次）。 */
    void applyHeaders();

    /** 写一帧并 flush。 */
    void writeFrame(String frame) throws IOException;
}
