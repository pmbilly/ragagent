package com.ragagent.shared.error;

/** 管线 seam 调用失败通道：失败一律抛本异常，成功走返回值。 */
public final class PipelinePortException extends RuntimeException {

    public PipelinePortException(String message) {
        super(message);
    }

    public PipelinePortException(String message, Throwable cause) {
        super(message, cause);
    }
}
