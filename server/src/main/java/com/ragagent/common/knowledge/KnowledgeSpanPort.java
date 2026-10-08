package com.ragagent.common.knowledge;

import java.util.Map;

/**
 * 处理 span 端口（B98/C2）：wiki ingest 在知识处理追踪树上挂子 span。
 *
 * <p>迁移前为 wiki 侧 {@code WikiBatchSupport.WikiSpans} 对 {@code SpanTracker} 的包装——
 * 现在把<b>包装与 best-effort 语义整体搬到 knowledge 侧适配器</b>，wiki 只依赖本端口：
 * 五个方法<b>永不抛异常</b>（追踪绝不阻断业务），追踪器缺席时 {@code begin*} 返回 {@code null}、
 * 其余调用直接返回。</p>
 */
public interface KnowledgeSpanPort {

    /** 不透明句柄：wiki 只做传递与判空，不读其内部字段。 */
    interface SpanHandle {
    }

    /**
     * 在 {@code postprocess} 阶段 span 下开 {@code postprocess.wiki} 子 span。
     *
     * <p>实现内部：{@code latestAttempt} → {@code lookupStage(postprocess)} → {@code beginSubSpan}；
     * 任一步缺失返回 {@code null}（与迁移前一致）。</p>
     */
    SpanHandle beginWikiSubspan(String knowledgeId, Map<String, Object> input);

    /** 在父 span 下开子 span；父缺席或无追踪器 → {@code null}。 */
    SpanHandle beginSubSpan(SpanHandle parent, String name, Map<String, Object> input);

    /** 结束 span（best-effort）。 */
    void endSpan(SpanHandle span, Map<String, Object> output);

    /** 标记 span 失败（best-effort）。 */
    void failSpan(SpanHandle span, String errorCode, String errorMessage, Throwable error);

    /** 标记 span 跳过（best-effort）。 */
    void skipSpan(SpanHandle span, String reason);
}
