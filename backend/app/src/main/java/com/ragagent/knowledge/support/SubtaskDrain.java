package com.ragagent.knowledge.support;

import org.slf4j.Logger;

/**
 * 富化子任务的收口递减（问题生成 / 图谱抽取共用）。
 *
 * <p>递减失败是 best-effort：行由 housekeeping sweep 兜底，不破坏任务语义。
 * 收 {@link Runnable} 而不是具体 finalizer 类型，避免把知识域的重复逻辑
 * 变成一条新的 knowledge→wiki 跨域依赖。</p>
 */
public final class SubtaskDrain {

    private SubtaskDrain() {
    }

    public static void drain(String knowledgeId, String source, Runnable decrement, Logger log) {
        if (knowledgeId == null || knowledgeId.isEmpty()) {
            return;
        }
        try {
            decrement.run();
        } catch (RuntimeException e) {
            log.warn("finalize subtask decrement failed source={} knowledge={}", source, knowledgeId, e);
        }
    }
}
