package com.ragagent.session.qa;

import com.ragagent.session.service.QaSupport.QaMode;
import com.ragagent.session.service.TurnLifecycle;
import io.micrometer.core.instrument.Metrics;
import java.util.Locale;

/**
 * 一轮问答的终态计数：{@code ragagent.qa.terminal{mode,outcome}}。
 *
 * <p>只由 {@link TurnLifecycle#finish} 的赢家调用，天然不会重复计数。</p>
 */
final class QaTerminalMetrics {

    private QaTerminalMetrics() {
    }

    static void record(QaMode mode, TurnLifecycle.State outcome) {
        Metrics.counter("ragagent.qa.terminal",
                        "mode", mode.name().toLowerCase(Locale.ROOT),
                        "outcome", outcome(outcome))
                .increment();
    }

    private static String outcome(TurnLifecycle.State state) {
        return switch (state) {
            case DONE -> "done";
            case FAILED -> "failed";
            case CANCELLED -> "cancelled";
            default -> state.name().toLowerCase(Locale.ROOT);
        };
    }
}
