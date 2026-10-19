package com.ragagent.session.runtime;

import com.ragagent.platform.tracing.langfuse.LangfuseManager;
import com.ragagent.platform.tracing.langfuse.LangfuseTracing;
import java.util.Map;

/**
 * 会话域的观测门面：开 span、续接 traceparent。
 *
 * <p>返回自有 {@link SpanHandle} 而非框架类型——用例层不 import 观测实现包。</p>
 */
public final class SessionTrace {

    private SessionTrace() {
    }

    /** 开一个 span。 */
    public static SpanHandle start(String name, Map<String, Object> input, Map<String, Object> metadata) {
        var inner = LangfuseManager.get()
                .startSpan(new LangfuseManager.SpanOptions(name, input, metadata));
        return new SpanHandle() {
            @Override
            public void finish(Map<String, Object> output, Map<String, Object> finishMetadata, String error) {
                inner.finish(output, finishMetadata, error);
            }
        };
    }

    /** 续接上游 trace（空值/未启用时 no-op）。 */
    public static void attachTraceparent(String traceparent) {
        LangfuseTracing.attachTraceparent(traceparent);
    }

    /** 手动收敛的 span 句柄：必须 finish（调用点与业务分支一一对应）。 */
    public interface SpanHandle {

        /** 结束 span：{@code error} 非空时标记为错误。 */
        void finish(Map<String, Object> output, Map<String, Object> finishMetadata, String error);
    }
}
