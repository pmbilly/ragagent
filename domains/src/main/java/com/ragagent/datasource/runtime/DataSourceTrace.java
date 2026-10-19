package com.ragagent.datasource.runtime;

import com.ragagent.platform.access.TracingContext;
import com.ragagent.platform.tracing.langfuse.LangfuseTracing;

/** 数据源同步的观测门面：把 trace 载体注入任务载荷。 */
public final class DataSourceTrace {

    private DataSourceTrace() {
    }

    /** 当前 trace 上下文（未启用时返回空载体）。 */
    public static TracingContext inject() {
        return LangfuseTracing.inject();
    }
}
