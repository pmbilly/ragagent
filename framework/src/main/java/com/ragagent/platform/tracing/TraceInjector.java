package com.ragagent.platform.tracing;

import com.ragagent.platform.access.TracingContext;
import com.ragagent.platform.tracing.langfuse.LangfuseTracing;

/**
 * 观测载体注入的技术门面：把当前 trace 上下文带进任务载荷。
 *
 * <p>各业务域共用；未启用时返回空载体。此前 datasource / wiki 各有一个逐字相同的
 * 16 行门面，合并到这里（技术适配，不含业务词）。</p>
 */
public final class TraceInjector {

    private TraceInjector() {
    }

    public static TracingContext inject() {
        return LangfuseTracing.inject();
    }
}
