package com.ragagent.platform.tracing.langfuse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 追踪测试支撑：把导出的 span 收敛成最小只读视图，供跨模块测试断言。
 *
 * <p>装饰器随模型面迁出 framework 后，其测试仍在同一套追踪装置上跑；
 * 本类把包私有的 manager / 上下文操作包成公开入口，避免为测试放宽内部类的可见性。</p>
 */
public final class LangfuseTestSupport {

    private LangfuseTestSupport() {
    }

    /** 已导出 span 的最小视图（测试只关心名字与属性）。 */
    public record SpanView(String name, Map<String, String> attributes) {
    }

    /** 一次录制会话：安装记录型 manager，关闭时还原 no-op 并清理上下文。 */
    public static final class Recording implements AutoCloseable {

        private final List<SpanView> spans = new ArrayList<>();
        private final DefaultLangfuseManager manager;

        Recording(LangfuseConfig config) {
            this.manager = new DefaultLangfuseManager(config, batch -> batch.forEach(span ->
                    spans.add(new SpanView(span.name, Map.copyOf(span.attributes)))));
            LangfuseContext.clear();
            LangfuseRegistry.installForTest(manager);
        }

        /** 实时列表：断言时读取，随导出追加。 */
        public List<SpanView> spans() {
            return spans;
        }

        @Override
        public void close() {
            manager.shutdown();
            LangfuseRegistry.installForTest(NoopLangfuseManager.INSTANCE);
            LangfuseContext.clear();
        }
    }

    /** 安装 no-op 管理器（模拟"未配置 langfuse"）。 */
    public static void installNoop() {
        LangfuseRegistry.installForTest(NoopLangfuseManager.INSTANCE);
    }

    /** 开始一次录制。 */
    public static Recording start(LangfuseConfig config) {
        return new Recording(config);
    }
}
