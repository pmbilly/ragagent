package com.ragagent.framework.tracing.langfuse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一条已记录的 span 数据（OTLP 导出的载体内核）。
 *
 * <p>属性值恒为 JSON 字符串（见 {@link LangfuseAttributes#jsonAttrValue}）；
 * {@code exception*} 两字段承载 exception 事件
 * （name="exception"，attributes: exception.type/exception.message）。</p>
 */
public final class RecordedSpan {

    /** 32 位十六进制 trace id。 */
    public final String traceIdHex;
    /** 16 位十六进制 span id。 */
    public final String spanIdHex;
    /** 16 位十六进制父 span id；根 span 为 null。 */
    public final String parentSpanIdHex;
    public final String name;
    /** OTLP span kind 的缺省值（INTERNAL=1）。 */
    public final long startNanos;

    public long endNanos;
    /** 属性（键 → JSON 字符串值），写入顺序稳定。 */
    public final Map<String, String> attributes = new LinkedHashMap<>();
    /** 非 null → Status{code=ERROR, message=...}。 */
    public String statusMessage;
    /** 非 null → exception 事件（exceptionType/exceptionMessage 两字段）。 */
    public String exceptionType;
    public String exceptionMessage;

    public RecordedSpan(String traceIdHex, String spanIdHex, String parentSpanIdHex,
                 String name, long startNanos) {
        this.traceIdHex = traceIdHex;
        this.spanIdHex = spanIdHex;
        this.parentSpanIdHex = parentSpanIdHex;
        this.name = name == null ? "" : name;
        this.startNanos = startNanos;
    }

    /** 属性写入（仅非空值）。 */
    void putAttribute(String key, String jsonValue) {
        LangfuseAttributes.putIfPresent(attributes, key, jsonValue);
    }

    /** 记录异常事件（exception.* 两字段）。 */
    void recordError(String type, String message) {
        this.exceptionType = type == null ? "" : type;
        this.exceptionMessage = message == null ? "" : message;
    }

    /** 置错误状态（Status{code=ERROR, message}）。 */
    void setErrorStatus(String message) {
        this.statusMessage = message == null ? "" : message;
    }
}
