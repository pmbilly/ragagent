package com.ragagent.stream;

/**
 * 流存储读写失败（底层 I/O / Redis 错误的包装）。
 *
 * <p>非受检异常——由全局异常处理器兜成 500 信封。</p>
 */
public class StreamStoreException extends RuntimeException {

    public StreamStoreException(String message) {
        super(message);
    }

    public StreamStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
