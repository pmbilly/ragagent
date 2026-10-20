/**
 * 观测技术面：Langfuse/OTLP 适配与 trace 载体注入。
 *
 * <p>业务域只经 {@code TraceInjector} 取载体，不直连 vendor 实现。</p>
 */
package com.ragagent.platform.tracing;
