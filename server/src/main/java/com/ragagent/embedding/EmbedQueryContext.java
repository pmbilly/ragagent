package com.ragagent.embedding;

/**
 * 标记「本次 embedding 输入是查询侧而非文档侧」，NVIDIA embedder 用它切换
 * {@code input_type}=query/passage。
 *
 * <p>以 ThreadLocal 承载（同 {@code llm.limiter.BackgroundTaskContext}
 * 的先例；虚拟线程内独立、不跨线程传递）。
 */
public final class EmbedQueryContext {

    private static final ThreadLocal<Boolean> QUERY = new ThreadLocal<>();

    private EmbedQueryContext() {
    }

    /** 未标记时 isQuery=false。 */
    public static boolean isQuery() {
        return Boolean.TRUE.equals(QUERY.get());
    }

    public static Scope markQuery() {
        Boolean previous = QUERY.get();
        QUERY.set(Boolean.TRUE);
        return () -> {
            if (previous == null) {
                QUERY.remove();
            } else {
                QUERY.set(previous);
            }
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
