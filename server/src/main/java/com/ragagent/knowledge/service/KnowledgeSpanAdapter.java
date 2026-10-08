package com.ragagent.knowledge.service;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ragagent.common.knowledge.KnowledgeSpanPort;
import com.ragagent.knowledge.domain.KnowledgeProcessingSpan;

/**
 * {@link KnowledgeSpanPort} 的 knowledge 侧实现（B98/C2）。
 *
 * <p>逻辑整体搬自 wiki 侧的 {@code WikiBatchSupport.WikiSpans}（含
 * {@code latestAttempt → lookupStage(postprocess) → beginSubSpan} 的三步与 best-effort 语义），
 * 只是把 {@code SpanHandle} 收成不透明包装。</p>
 */
@Component
public class KnowledgeSpanAdapter implements KnowledgeSpanPort {

    private final ObjectProvider<SpanTracker> trackerProvider;

    public KnowledgeSpanAdapter(ObjectProvider<SpanTracker> trackerProvider) {
        this.trackerProvider = trackerProvider;
    }

    @Override
    public SpanHandle beginWikiSubspan(String knowledgeId, Map<String, Object> input) {
        SpanTracker tracker = tracker();
        if (tracker == null || knowledgeId == null || knowledgeId.isEmpty()) {
            return null;
        }
        try {
            int attempt = tracker.latestAttempt(knowledgeId);
            if (attempt <= 0) {
                return null;
            }
            SpanTracker.SpanHandle parent = tracker.lookupStage(knowledgeId, attempt,
                    KnowledgeProcessingSpan.STAGE_POST_PROCESS);
            if (parent == null) {
                return null;
            }
            return wrap(tracker.beginSubSpan(parent, "postprocess.wiki",
                    KnowledgeProcessingSpan.KIND_SUB_SPAN, input));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public SpanHandle beginSubSpan(SpanHandle parent, String name, Map<String, Object> input) {
        SpanTracker tracker = tracker();
        SpanTracker.SpanHandle parentHandle = unwrap(parent);
        if (tracker == null || parentHandle == null || name == null || name.isEmpty()) {
            return null;
        }
        try {
            return wrap(tracker.beginSubSpan(parentHandle, name,
                    KnowledgeProcessingSpan.KIND_SUB_SPAN, input));
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void endSpan(SpanHandle span, Map<String, Object> output) {
        SpanTracker tracker = tracker();
        SpanTracker.SpanHandle handle = unwrap(span);
        if (tracker == null || handle == null) {
            return;
        }
        try {
            tracker.endSpan(handle, output);
        } catch (RuntimeException e) {
            // best-effort：追踪失败不阻断批次
        }
    }

    @Override
    public void failSpan(SpanHandle span, String errorCode, String errorMessage, Throwable error) {
        SpanTracker tracker = tracker();
        SpanTracker.SpanHandle handle = unwrap(span);
        if (tracker == null || handle == null) {
            return;
        }
        try {
            tracker.failSpan(handle, errorCode, errorMessage, error);
        } catch (RuntimeException e) {
            // best-effort
        }
    }

    @Override
    public void skipSpan(SpanHandle span, String reason) {
        SpanTracker tracker = tracker();
        SpanTracker.SpanHandle handle = unwrap(span);
        if (tracker == null || handle == null) {
            return;
        }
        try {
            tracker.skipSpan(handle, reason);
        } catch (RuntimeException e) {
            // best-effort
        }
    }

    private SpanTracker tracker() {
        return trackerProvider == null ? null : trackerProvider.getIfAvailable();
    }

    private static SpanHandle wrap(SpanTracker.SpanHandle handle) {
        return handle == null ? null : new Handle(handle);
    }

    private static SpanTracker.SpanHandle unwrap(SpanHandle span) {
        return span instanceof Handle h ? h.delegate : null;
    }

    /** 不透明句柄：只在本类内与 {@link SpanTracker.SpanHandle} 互相转换。 */
    private static final class Handle implements SpanHandle {

        private final SpanTracker.SpanHandle delegate;

        Handle(SpanTracker.SpanHandle delegate) {
            this.delegate = delegate;
        }
    }
}
