package com.ragagent.wiki.runtime;

import com.ragagent.platform.access.TracingContext;
import com.ragagent.platform.tracing.langfuse.LangfuseTracing;

/** Wiki 的观测门面：把 trace 载体注入入库任务载荷。 */
public final class WikiTrace {

    private WikiTrace() {
    }

    /** 当前 trace 上下文（未启用时返回空载体）。 */
    public static TracingContext inject() {
        return LangfuseTracing.inject();
    }
}
