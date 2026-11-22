/**
 * 观测技术面：Langfuse/OTLP 适配与 trace 载体注入。
 *
 * <p>业务域只经 {@code TraceInjector} 取载体，不直连 vendor 实现。请求级链路上下文（TracingContext，W3C traceparent 跨进程携带）归入本包。</p>
 */
package com.ragagent.framework.tracing;
