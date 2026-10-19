package com.ragagent.knowledge.runtime;

import com.ragagent.platform.access.TracingContext;
import com.ragagent.platform.tracing.langfuse.LangfuseTaskScope;
import java.util.Map;

/**
 * 知识库任务的观测门面：开任务侧 span、续接上游 trace、生成负载预览。
 *
 * <p>返回自有 {@link Scope} 而非框架类型——用例层不 import 观测实现包。</p>
 */
public final class KnowledgeTrace {

    private KnowledgeTrace() {
    }

    /**
     * 开一个任务 span。
     *
     * @param taskType       任务类型（span 名/根名）
     * @param tracing        上游 trace 载体
     * @param input          观测输入
     * @param payloadPreview 负载预览
     */
    public static Scope startTask(String taskType, TracingContext tracing,
            Map<String, Object> input, Object payloadPreview) {
        LangfuseTaskScope inner = LangfuseTaskScope.start(taskType, tracing, input, payloadPreview);
        return new Scope() {
            @Override
            public void close() {
                inner.close();
            }
        };
    }

    /** 负载预览（截断后的 JSON 文本）。 */
    public static Object previewPayload(String json) {
        return LangfuseTaskScope.previewPayload(json);
    }

    /** 任务观测作用域：关闭即结束 span。 */
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
